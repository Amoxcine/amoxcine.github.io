package fr.ascendant.lunar.encounter;

import java.util.*;

/** Pure, bounded, server-thread model. World authentication belongs to the adapter. */
public final class RaidMachine {
    public enum Identity { SEALED_GREENHOUSE, REGULATOR, CONSERVATOR }
    public enum Phase { READING, ROUTING, EXPOSED, FINAL_WARNING, CORE_EXPOSED, SUCCESS, FAILED, ABORTED }
    public enum Result { APPLIED, IGNORED, WRONG_ROUTE }
    public enum Action { IDENTIFY, TRANSFER_CHARGE, DIVERT_OVERLOAD }
    public enum Stop { NONE, STABILITY, TIMEOUT, ABSENCE, MANUAL, RESTART, ACTOR_LOST }
    public record Rules(long readingTicks, long windowTicks, long warningTicks,
                        long maxActiveTicks, long absenceTicks, double moduleHealth, double coreHealth) {
        public Rules {
            if (readingTicks < 1 || windowTicks < 1 || warningTicks < 1 || maxActiveTicks < 1
                    || absenceTicks < 1 || maxActiveTicks > 1_000_000 || absenceTicks > 72_000
                    || readingTicks > maxActiveTicks || windowTicks > maxActiveTicks
                    || warningTicks > maxActiveTicks || !validHealth(moduleHealth) || !validHealth(coreHealth))
                throw new IllegalArgumentException("Invalid rules");
        }
        // Health is a test input, not a balance decision for V1 equipment.
        public static Rules prototype(double moduleHealth, double coreHealth) {
            return new Rules(160, 400, 60, 18_000, 1_200, moduleHealth, coreHealth);
        }
    }
    public record Group(UUID beneficiary, Set<UUID> delegates, List<UUID> roster) {
        public Group {
            Objects.requireNonNull(beneficiary);
            delegates = Set.copyOf(delegates);
            roster = List.copyOf(roster);
            if (roster.isEmpty() || roster.size() > 8 || new HashSet<>(roster).size() != roster.size()
                    || delegates.isEmpty() || delegates.size() > 64) throw new IllegalArgumentException("Invalid group");
        }
    }
    public record Token(UUID run, int generation) {}
    public record Signal(int task, int receiver, Action action, boolean completed, boolean defenderInterrupted) {}
    public record View(UUID run, Identity identity, Group group, Phase phase, Stop stop, int circuit,
                       int generation, List<Signal> signals, double remainingHealth, int errors,
                       long activeTicks, long absentTicks, long phaseTicks, boolean suspended) {
        public View { signals = List.copyOf(signals); }
    }

    private final UUID run;
    private final Identity identity;
    private final Group group;
    private final Rules rules;
    private final long seed;
    private final int tasks;
    private final boolean[] completed;
    private final boolean[] interrupted;
    private final int[] receivers;
    private Phase phase = Phase.READING;
    private Stop stop = Stop.NONE;
    private int circuit, generation = 1, errors;
    private long activeTicks, absentTicks, phaseTicks;
    private double remainingHealth;
    private boolean suspended;

    public RaidMachine(UUID run, Identity identity, Group group, Rules rules, long seed) {
        this.run = Objects.requireNonNull(run);
        this.identity = Objects.requireNonNull(identity);
        this.group = Objects.requireNonNull(group);
        this.rules = Objects.requireNonNull(rules);
        this.seed = seed;
        this.tasks = group.roster().size() == 1 ? 1 : group.roster().size() <= 4 ? 2 : 3;
        completed = new boolean[3]; interrupted = new boolean[3]; receivers = new int[3];
        remainingHealth = rules.moduleHealth();
        setSignals();
    }

    public Token token() { return new Token(run, generation); }
    public View view() {
        List<Signal> signals = new ArrayList<>(tasks);
        for (int i = 0; i < taskCount(); i++) signals.add(new Signal(i, receivers[i], action(i), completed[i], interrupted[i]));
        return new View(run, identity, group, phase, stop, circuit, generation, signals,
                remainingHealth, errors, activeTicks, absentTicks, phaseTicks, suspended);
    }
    public boolean terminal() { return phase == Phase.SUCCESS || phase == Phase.FAILED || phase == Phase.ABORTED; }

    /** Adapter calls once per scheduled tick batch; only the fixed roster is inspected. */
    public void advance(long ticks, Set<UUID> capablePresent) {
        if (ticks < 0 || ticks > 1_000_000) throw new IllegalArgumentException("Invalid elapsed ticks");
        Objects.requireNonNull(capablePresent);
        if (terminal() || ticks == 0) return;
        boolean present = group.roster().stream().anyMatch(capablePresent::contains);
        suspended = !present;
        if (!present) {
            absentTicks = Math.min(rules.absenceTicks(), absentTicks + ticks);
            if (absentTicks == rules.absenceTicks()) end(Phase.ABORTED, Stop.ABSENCE);
            return;
        }
        absentTicks = 0;
        // Loop crosses at most the bounded phase transitions inside this tick batch.
        while (ticks > 0 && !terminal()) {
            long deadline = switch (phase) {
                case READING -> rules.readingTicks();
                case EXPOSED -> rules.windowTicks();
                case FINAL_WARNING -> rules.warningTicks();
                default -> Long.MAX_VALUE;
            };
            long consume = Math.min(ticks, Math.min(rules.maxActiveTicks() - activeTicks, deadline - phaseTicks));
            activeTicks += consume; phaseTicks += consume; ticks -= consume;
            if (activeTicks == rules.maxActiveTicks()) { end(Phase.ABORTED, Stop.TIMEOUT); break; }
            if (phaseTicks == deadline) {
                if (phase == Phase.READING) transition(Phase.ROUTING);
                else if (phase == Phase.EXPOSED) beginReading();
                else if (phase == Phase.FINAL_WARNING) transition(Phase.CORE_EXPOSED);
            }
        }
    }

    /** Reading can end early only after the adapter obtained acknowledgement from EVERY participant. */
    public Result acknowledgeReading(Set<UUID> acknowledged, Token token) {
        Objects.requireNonNull(acknowledged);
        if (!current(token) || suspended || phase != Phase.READING
                || !acknowledged.containsAll(group.roster())) return Result.IGNORED;
        transition(Phase.ROUTING);
        return Result.APPLIED;
    }

    /** No raw entity death or terminal packet may call this without proximity/liveness checks. */
    public Result interact(UUID player, Token token, int task, int receiver, Action action) {
        if (!eligible(player, token) || phase != Phase.ROUTING || !activeTask(task)
                || completed[task] || receiver < 0 || receiver > 2 || action == null) return Result.IGNORED;
        if (receiver != receivers[task] || action != action(task)) {
            loseStability(); return Result.WRONG_ROUTE;
        }
        if (!interrupted[task]) return Result.IGNORED;
        completed[task] = true;
        boolean done = true;
        for (int i = 0; i < taskCount(); i++) done &= completed[i];
        if (done) transition(Phase.EXPOSED);
        return Result.APPLIED;
    }

    /** Trusted callback for the EXACT owned actor UUID and generation; machine kills may help. */
    public Result defenderInterrupted(Token token, int task) {
        if (!current(token) || suspended || phase != Phase.ROUTING || !activeTask(task)
                || interrupted[task]) return Result.IGNORED;
        interrupted[task] = true;
        return Result.APPLIED;
    }

    public Result sabotageCompleted(Token token, int task) {
        if (!current(token) || suspended || phase != Phase.ROUTING || !activeTask(task)
                || interrupted[task]) return Result.IGNORED;
        loseStability(); return Result.APPLIED;
    }

    /** Confirmed native damage, not a client-provided amount. Excess damage never skips a circuit. */
    public Result damage(UUID player, Token token, double acceptedDamage) {
        if (!eligible(player, token) || !Double.isFinite(acceptedDamage) || acceptedDamage <= 0
                || (phase != Phase.EXPOSED && phase != Phase.CORE_EXPOSED)) return Result.IGNORED;
        remainingHealth = Math.max(0, remainingHealth - acceptedDamage);
        if (remainingHealth == 0) {
            if (phase == Phase.CORE_EXPOSED) end(Phase.SUCCESS, Stop.NONE);
            else if (++circuit == circuits()) {
                remainingHealth = rules.coreHealth();
                transition(Phase.FINAL_WARNING);
            } else {
                remainingHealth = rules.moduleHealth();
                beginReading();
            }
        }
        return Result.APPLIED;
    }

    public void abort() { if (!terminal()) end(Phase.ABORTED, Stop.MANUAL); }
    public void actorLost() { if (!terminal()) end(Phase.ABORTED, Stop.ACTOR_LOST); }
    public void serverRestart() { if (!terminal()) end(Phase.ABORTED, Stop.RESTART); }

    private boolean current(Token token) { return !terminal() && token != null && token.equals(token()); }
    private boolean eligible(UUID player, Token token) {
        return !suspended && current(token) && group.roster().contains(player);
    }
    private void end(Phase phase, Stop stop) {
        this.stop = stop; transition(phase); suspended = false;
    }
    private void loseStability() {
        if (++errors >= 3) end(Phase.FAILED, Stop.STABILITY);
        else beginReading();
    }
    private void beginReading() {
        Arrays.fill(completed, false); Arrays.fill(interrupted, false);
        transition(Phase.READING);
        setSignals();
    }
    private void transition(Phase next) { phase = next; phaseTicks = 0; generation++; }
    private int circuits() { return switch (identity) { case SEALED_GREENHOUSE -> 1; case REGULATOR -> 2; case CONSERVATOR -> 3; }; }
    private Action action(int task) {
        return switch (identity) {
            case SEALED_GREENHOUSE -> Action.TRANSFER_CHARGE;
            case REGULATOR -> Action.DIVERT_OVERLOAD;
            case CONSERVATOR -> switch ((circuit + task) % 3) {
                case 0 -> Action.IDENTIFY;
                case 1 -> Action.TRANSFER_CHARGE;
                default -> Action.DIVERT_OVERLOAD;
            };
        };
    }
    private void setSignals() {
        SplittableRandom random = new SplittableRandom(seed ^ ((long) circuit << 32) ^ generation);
        int start = random.nextInt(3);
        for (int i = 0; i < taskCount(); i++) receivers[i] = (start + i) % 3;
    }
    private int taskCount() { return identity == Identity.REGULATOR && circuit == 1 ? Math.max(2, tasks) : tasks; }
    private boolean activeTask(int task) {
        if (task < 0 || task >= taskCount()) return false;
        if (group.roster().size() != 1) return true;
        for (int i = 0; i < task; i++) if (!completed[i]) return false;
        return true;
    }
    private static boolean validHealth(double value) { return Double.isFinite(value) && value > 0 && value <= 1e12; }
}
