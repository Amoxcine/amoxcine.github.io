package fr.ascendant.lunar.encounter;

import java.util.*;
import static fr.ascendant.lunar.encounter.RaidMachine.*;
import static fr.ascendant.lunar.encounter.RewardLedger.Status;

public final class RaidMachineTest {
    private static int checks;
    private static long nextId;
    private static final UUID BENEFICIARY = id(), DELEGATE = id(), OUTSIDER = id();
    private static final Rules RULES = new Rules(160, 400, 60, 18_000, 1200, 100, 200);
    public static void main(String[] args) {
        construction(); allIdentitiesAndSizes(); regulator(); time(); economy(); fuzz();
        System.out.println("PASS raid model: " + checks + " assertions; pure JVM, no Minecraft integration");
    }
    private static UUID id() { return new UUID(0, ++nextId); }
    private static Group group(int size) {
        List<UUID> roster = new ArrayList<>();
        for (int i = 0; i < size; i++) roster.add(id());
        return new Group(BENEFICIARY, Set.of(DELEGATE), roster);
    }
    private static RaidMachine raid(Identity identity, int size) { return new RaidMachine(id(), identity, group(size), RULES, nextId); }
    private static UUID player(RaidMachine r) { return r.view().group().roster().getFirst(); }
    private static Set<UUID> present(RaidMachine r) { return Set.copyOf(r.view().group().roster()); }
    private static void check(boolean test) { checks++; if (!test) throw new AssertionError("assertion " + checks); }
    private static void reject(Runnable action) {
        checks++;
        try { action.run(); throw new AssertionError("Expected rejection at " + checks); }
        catch (IllegalArgumentException | IllegalStateException | SecurityException expected) { }
    }
    private static void construction() {
        reject(() -> group(0)); reject(() -> group(9));
        reject(() -> new Group(BENEFICIARY, Set.of(DELEGATE), List.of(DELEGATE, DELEGATE)));
        reject(() -> new Group(BENEFICIARY, Set.of(), List.of(DELEGATE)));
        List<UUID> source = new ArrayList<>(List.of(id())); Set<UUID> delegates = new HashSet<>(Set.of(DELEGATE));
        Group g = new Group(BENEFICIARY, delegates, source); source.clear(); delegates.clear();
        check(g.roster().size() == 1 && g.delegates().size() == 1);
        reject(() -> new Rules(0, 1, 1, 10, 10, 1, 1));
        reject(() -> new Rules(1, 1, 1, 10, 10, Double.NaN, 1));
        reject(() -> new Rules(1, 1, 1, 10, 10, 1, Double.POSITIVE_INFINITY));
        RaidMachine r = raid(Identity.REGULATOR, 1);
        check(r.view().signals().size() == 1);
        reject(() -> r.advance(-1, present(r)));
    }
    private static void open(RaidMachine r) {
        check(r.acknowledgeReading(present(r), r.token()) == Result.APPLIED);
        check(r.view().phase() == Phase.ROUTING);
    }
    private static void route(RaidMachine r) {
        List<Signal> signals = r.view().signals();
        for (Signal s : signals) {
            check(r.defenderInterrupted(r.token(), s.task()) == Result.APPLIED);
            check(r.interact(player(r), r.token(), s.task(), s.receiver(), s.action()) == Result.APPLIED);
        }
        check(r.view().phase() == Phase.EXPOSED);
    }
    private static void finish(RaidMachine r) {
        while (!r.terminal()) {
            switch (r.view().phase()) {
                case READING -> open(r);
                case ROUTING -> route(r);
                case EXPOSED, CORE_EXPOSED -> check(r.damage(player(r), r.token(), 1e9) == Result.APPLIED);
                case FINAL_WARNING -> r.advance(RULES.warningTicks(), present(r));
                default -> throw new AssertionError("Unexpected phase");
            }
        }
        check(r.view().phase() == Phase.SUCCESS);
    }
    private static void allIdentitiesAndSizes() {
        for (Identity identity : Identity.values()) for (int size = 1; size <= 8; size++) {
            RaidMachine r = raid(identity, size);
            Group frozen = r.view().group();
            check(r.view().signals().size() == (size == 1 ? 1 : size <= 4 ? 2 : 3));
            check(r.damage(player(r), r.token(), 1e9) == Result.IGNORED);
            RewardLedger ledger = new RewardLedger(100);
            ledger.reserve(r, identity == Identity.REGULATOR ? 12 : 0);
            finish(r);
            check(r.view().group().equals(frozen)); check(ledger.settle(r)); check(!ledger.settle(r));
            check(ledger.get(r.view().run()).lot().count() == (identity == Identity.SEALED_GREENHOUSE ? 8 : identity == Identity.REGULATOR ? 4 : 1));
            check(ledger.get(r.view().run()).status() == Status.AVAILABLE);
            Token token = r.token(); r.advance(1_000_000, present(r)); r.abort(); r.actorLost(); r.serverRestart();
            check(r.token().equals(token)); check(r.view().phase() == Phase.SUCCESS);
            check(r.damage(player(r), token, 1e9) == Result.IGNORED);
        }
    }
    private static void regulator() {
        RaidMachine r = raid(Identity.REGULATOR, 1);
        Token old = r.token(); open(r); Signal s = r.view().signals().getFirst();
        check(r.interact(player(r), old, 0, s.receiver(), s.action()) == Result.IGNORED);
        check(r.interact(OUTSIDER, r.token(), 0, s.receiver(), s.action()) == Result.IGNORED);
        check(r.interact(player(r), r.token(), 0, s.receiver(), s.action()) == Result.IGNORED);
        route(r);
        check(r.damage(player(r), r.token(), Double.NaN) == Result.IGNORED);
        check(r.damage(player(r), r.token(), Double.POSITIVE_INFINITY) == Result.IGNORED);
        check(r.damage(player(r), r.token(), -10) == Result.IGNORED);
        check(r.damage(player(r), r.token(), 25) == Result.APPLIED);
        check(r.view().remainingHealth() == 75);
        old = r.token(); r.advance(400, present(r));
        check(r.view().phase() == Phase.READING && r.view().remainingHealth() == 75 && r.view().circuit() == 0);
        check(r.damage(player(r), old, 1000) == Result.IGNORED);
        open(r); route(r); r.damage(player(r), r.token(), 1e9);
        check(r.view().circuit() == 1 && r.view().phase() == Phase.READING);
        check(r.view().signals().size() == 2);
        check(r.view().signals().get(0).receiver() != r.view().signals().get(1).receiver());
        open(r);
        check(r.defenderInterrupted(r.token(), 1) == Result.IGNORED); // Solo: one active task.
        old = r.token(); route(r);
        check(r.interact(player(r), old, 0, 0, Action.IDENTIFY) == Result.IGNORED);
        check(r.view().errors() == 0);
        r.damage(player(r), r.token(), 1e9);
        check(r.view().phase() == Phase.FINAL_WARNING);
        check(r.damage(player(r), r.token(), 1e9) == Result.IGNORED);
        r.advance(60, present(r));
        r.damage(player(r), r.token(), 1e9);
        check(r.view().phase() == Phase.SUCCESS && r.view().circuit() == 2);
        RaidMachine errors = raid(Identity.REGULATOR, 4);
        for (int i = 1; i <= 3; i++) {
            open(errors); Signal wrong = errors.view().signals().getFirst(); old = errors.token();
            check(errors.interact(player(errors), old, 0, (wrong.receiver() + 1) % 3, wrong.action()) == Result.WRONG_ROUTE);
            check(errors.view().errors() == i);
            check(errors.sabotageCompleted(old, 0) == Result.IGNORED);
        }
        check(errors.view().phase() == Phase.FAILED && errors.view().stop() == Stop.STABILITY);
        RaidMachine sabotage = raid(Identity.REGULATOR, 8); open(sabotage);
        Token sabotageToken = sabotage.token();
        check(sabotage.sabotageCompleted(sabotageToken, 0) == Result.APPLIED);
        check(sabotage.sabotageCompleted(sabotageToken, 0) == Result.IGNORED);
        check(sabotage.view().errors() == 1);
    }
    private static void time() {
        RaidMachine r = raid(Identity.SEALED_GREENHOUSE, 4);
        r.advance(159, present(r)); check(r.view().phase() == Phase.READING);
        check(r.acknowledgeReading(Set.of(player(r)), r.token()) == Result.IGNORED);
        r.advance(1, present(r)); check(r.view().phase() == Phase.ROUTING);
        r.advance(100, Set.of(OUTSIDER));
        check(r.view().suspended() && r.view().activeTicks() == 160);
        Signal s = r.view().signals().getFirst();
        check(r.interact(player(r), r.token(), 0, s.receiver(), s.action()) == Result.IGNORED);
        r.advance(1, Set.of(player(r))); check(!r.view().suspended() && r.view().group().roster().size() == 4);
        r.advance(1199, Set.of()); check(!r.terminal());
        r.advance(1, Set.of()); check(r.view().stop() == Stop.ABSENCE);
        RaidMachine timeout = raid(Identity.REGULATOR, 1);
        timeout.advance(18_000, present(timeout));
        check(timeout.view().stop() == Stop.TIMEOUT && timeout.view().phase() == Phase.ABORTED);
        RaidMachine restart = raid(Identity.REGULATOR, 1); restart.serverRestart();
        check(restart.view().phase() == Phase.ABORTED && restart.view().stop() == Stop.RESTART);
        RaidMachine actor = raid(Identity.CONSERVATOR, 1); actor.actorLost();
        check(actor.view().stop() == Stop.ACTOR_LOST);
        RaidMachine chunked = raid(Identity.REGULATOR, 1);
        RaidMachine single = new RaidMachine(chunked.view().run(), Identity.REGULATOR, chunked.view().group(), RULES, nextId);
        for (int i = 0; i < 18_000; i++) chunked.advance(1, present(chunked));
        single.advance(18_000, present(single));
        check(chunked.view().equals(single.view()));
    }
    private static void economy() {
        RaidMachine r = raid(Identity.REGULATOR, 4);
        RewardLedger l = new RewardLedger(10);
        reject(() -> l.reserve(r, 0)); l.reserve(r, 12); reject(() -> l.reserve(r, 12));
        check(!l.settle(r)); finish(r); check(l.settle(r)); UUID run = r.view().run();
        reject(() -> l.beginWithdrawal(run, OUTSIDER, id()));
        UUID operation = id();
        RewardLedger.Entry intent = l.beginWithdrawal(run, DELEGATE, operation);
        check(intent.reservedCoolant() == 12 && intent.lot().count() == 4 && intent.status() == Status.CLAIMING);
        reject(() -> l.beginWithdrawal(run, DELEGATE, id()));
        reject(() -> l.confirmWithdrawal(run, id()));
        RewardLedger recovered = RewardLedger.recover(10, l.snapshot());
        check(recovered.get(run).status() == Status.REVIEW);
        reject(() -> recovered.beginWithdrawal(run, DELEGATE, id()));
        reject(() -> recovered.confirmWithdrawal(run, operation));
        l.confirmWithdrawal(run, operation); check(l.get(run).status() == Status.CLAIMED);
        reject(() -> l.confirmWithdrawal(run, operation));
        check(RewardLedger.recover(10, l.snapshot()).get(run).status() == Status.CLAIMED);
        RaidMachine abort = raid(Identity.REGULATOR, 1); l.reserve(abort, 12); abort.abort(); check(l.settle(abort));
        UUID refundRun = abort.view().run(), refundOp = id();
        check(l.get(refundRun).status() == Status.REFUNDABLE);
        check(l.beginWithdrawal(refundRun, DELEGATE, refundOp).status() == Status.REFUNDING);
        check(RewardLedger.recover(10, l.snapshot()).get(refundRun).status() == Status.REVIEW);
        l.confirmWithdrawal(refundRun, refundOp); check(l.get(refundRun).status() == Status.REFUNDED);
        RaidMachine crashed = raid(Identity.REGULATOR, 1); l.reserve(crashed, 12);
        check(RewardLedger.recover(10, l.snapshot()).get(crashed.view().run()).status() == Status.REFUNDABLE);
        List<RewardLedger.Entry> duplicate = new ArrayList<>(l.snapshot()); duplicate.add(duplicate.getFirst());
        reject(() -> RewardLedger.recover(10, duplicate)); reject(() -> RewardLedger.recover(1, l.snapshot()));
        RewardLedger full = new RewardLedger(1); RaidMachine first = raid(Identity.SEALED_GREENHOUSE, 1);
        full.reserve(first, 0); first.abort(); full.settle(first);
        reject(() -> full.reserve(raid(Identity.SEALED_GREENHOUSE, 1), 0));
        // Restart never forgets a consumed run ID, even when a caller recreates its state machine.
        RaidMachine reused = new RaidMachine(run, r.view().identity(), r.view().group(), RULES, 42);
        reject(() -> RewardLedger.recover(10, l.snapshot()).reserve(reused, 12));
    }
    private static void fuzz() {
        Random random = new Random(817);
        for (int iteration = 0; iteration < 100; iteration++) {
            RaidMachine r = raid(Identity.values()[iteration % 3], 1 + iteration % 8);
            RewardLedger l = new RewardLedger(1); l.reserve(r, 12);
            Phase terminal = null;
            for (int i = 0; i < 1000; i++) {
                Token stale = new Token(r.view().run(), Math.max(0, r.view().generation() - 1));
                switch (random.nextInt(9)) {
                    case 0 -> r.advance(random.nextInt(500), random.nextBoolean() ? present(r) : Set.of());
                    case 1 -> r.acknowledgeReading(present(r), r.token());
                    case 2 -> r.defenderInterrupted(r.token(), random.nextInt(4));
                    case 3 -> r.interact(player(r), r.token(), random.nextInt(4), random.nextInt(3), Action.values()[random.nextInt(3)]);
                    case 4 -> r.damage(player(r), r.token(), random.nextInt(500));
                    case 5 -> r.interact(player(r), stale, 0, 0, Action.IDENTIFY);
                    case 6 -> r.sabotageCompleted(stale, 0);
                    case 7 -> { if (i > 900) r.serverRestart(); }
                    default -> l.settle(r);
                }
                View v = r.view();
                check(v.errors() <= 3 && v.errors() >= 0);
                check(v.remainingHealth() >= 0 && Double.isFinite(v.remainingHealth()));
                check(v.activeTicks() <= RULES.maxActiveTicks());
                check(v.group().roster().size() == 1 + iteration % 8);
                if (terminal != null) check(v.phase() == terminal);
                if (r.terminal()) terminal = v.phase();
                check(l.get(v.run()).status() != Status.AVAILABLE || v.phase() == Phase.SUCCESS);
            }
        }
    }
}
