package fr.ascendant.lunar.travel;
import java.nio.file.*;
import java.util.UUID;
import java.io.IOException;
public final class ArrivalLedgerTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        ArrivalLedger ledger = new ArrivalLedger(root);
        UUID player = UUID.randomUUID();
        check(ledger.fresh(player), "first arrival eligible");
        check(ledger.begin(player), "first reservation persisted");
        check(!Files.exists(root.resolve(player + ".arrived")), "reservation is not an arrival claim");
        check(!ledger.fresh(player), "pending cannot import again");
        check(!new ArrivalLedger(root).fresh(player), "pending survives process object replacement/crash");
        check(!ledger.begin(player), "replayed reservation denied");
        ledger.arrived(player);
        check(Files.isRegularFile(root.resolve(player + ".arrived")), "arrival committed separately");
        check(!new ArrivalLedger(root).fresh(player), "successful arrival survives restart/death/team changes");
        ledger.arrived(player);
        check(!ledger.fresh(player), "idempotent arrival never reissues");
        check(ledger.fresh(UUID.randomUUID()), "independent UUID eligible");
        UUID corrupt = UUID.randomUUID();
        Files.writeString(root.resolve(corrupt + ".pending"), "partial-write");
        try { ledger.fresh(corrupt); throw new AssertionError("corrupt reopened"); }
        catch (IOException expected) { check(true, "corrupt failclosed"); }
        UUID absent = UUID.randomUUID();
        try { ledger.arrived(absent); throw new AssertionError("arrival without reservation"); }
        catch (IOException expected) { check(true, "missing reservation denied"); }
        Path badRoot = root.resolve("not-a-directory"); Files.writeString(badRoot, "occupied");
        try { new ArrivalLedger(badRoot).begin(UUID.randomUUID()); throw new AssertionError("write failure ignored"); }
        catch (IOException expected) { check(true, "write failure denies travel"); }
        UUID raced = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicInteger winners = new java.util.concurrent.atomic.AtomicInteger();
        var attempts = new java.util.ArrayList<Thread>();
        for (int i = 0; i < 8; i++) attempts.add(new Thread(() -> {
            try { if (new ArrivalLedger(root).begin(raced)) winners.incrementAndGet(); }
            catch (IOException expectedContention) { /* CREATE_NEW loser or partial-write observation stays denied. */ }
        }));
        for (Thread thread : attempts) thread.start();
        for (Thread thread : attempts) thread.join();
        check(winners.get() == 1, "concurrent reservations only one winner");
        check(!new ArrivalLedger(root).fresh(raced), "race never reopens raw");
        UUID refused = UUID.randomUUID();
        ledger.begin(refused);
        ledger.refusedIntact(refused);
        check(new ArrivalLedger(root).fresh(refused), "proven intact veto retains first arrival right after restart");
        check(ledger.begin(refused), "refused first flight may reserve again");
        ledger.arrived(refused);
        try { ledger.refusedIntact(refused); throw new AssertionError("completed allowance reset"); }
        catch (IOException expected) { check(true, "completed arrival cannot be released by refusal"); }
        check(ledger.completed(refused) && !ledger.fresh(refused), "completed marker remains authoritative");
        System.out.println("PASS durable arrival ledger: " + checks + " filesystem assertions; no game/server.");
    }
}
