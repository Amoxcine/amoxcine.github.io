package fr.ascendant.lunar.travel;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

public final class FlightJournalTest {
    private static int checks;
    private static void check(boolean ok, String reason) { checks++; if (!ok) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        var journal = new FlightJournal(root);
        UUID player = UUID.randomUUID(), rocket = UUID.randomUUID();
        check(journal.read(player) == null, "no synthetic launch authority");
        var paid = journal.paid(player, rocket, TravelPolicy.EARTH);
        check(paid.equals(new FlightJournal(root).read(player)), "native receipt survives process replacement");
        check(paid.matches(player, rocket, TravelPolicy.EARTH), "same paid physical rocket");
        check(!paid.matches(UUID.randomUUID(), rocket, TravelPolicy.EARTH), "different player denied");
        check(!paid.matches(player, UUID.randomUUID(), TravelPolicy.EARTH), "replacement rocket denied");
        check(!paid.matches(player, rocket, TravelPolicy.MOON), "wrong source denied");
        journal.phase(paid, FlightJournal.Phase.TRANSFERRING);
        var intent = new FlightJournal(root).read(player);
        check(intent.phase() == FlightJournal.Phase.TRANSFERRING, "intent persisted before movement");
        try { journal.phase(paid, FlightJournal.Phase.COMPLETE); throw new AssertionError("stale accepted"); }
        catch (IOException expected) { check(true, "stale journal transition denied"); }
        journal.phase(intent, FlightJournal.Phase.READY);
        var retry = journal.read(player);
        check(retry.nonce().equals(paid.nonce()) && retry.rocket().equals(rocket), "recovery retains same paid flight, not a new grant");
        journal.phase(retry, FlightJournal.Phase.TRANSFERRING);
        journal.phase(journal.read(player), FlightJournal.Phase.COMPLETE);
        var complete = new FlightJournal(root).read(player);
        check(complete.phase() == FlightJournal.Phase.COMPLETE, "completion durable");
        try { journal.phase(complete, FlightJournal.Phase.READY); throw new AssertionError("completed replay"); }
        catch (IOException expected) { check(true, "completed native flight cannot replay"); }
        UUID corrupt = UUID.randomUUID();
        Files.writeString(root.resolve(corrupt + ".flight"), "truncated");
        try { journal.read(corrupt); throw new AssertionError("corrupt receipt accepted"); }
        catch (IOException expected) { check(true, "corrupt native receipt refuses"); }
        var arrivals = new ArrivalLedger(root.resolve("arrivals"));
        arrivals.begin(player);
        arrivals.refusedIntact(player);
        check(arrivals.fresh(player), "prelanding veto does not burn raw entitlement");
        arrivals.begin(player); arrivals.arrived(player);
        journal.paid(player, UUID.randomUUID(), TravelPolicy.MOON);
        check(!new ArrivalLedger(root.resolve("arrivals")).fresh(player), "later paid flights never reissue raw");
        System.out.println("PASS native receipt recovery: " + checks + " filesystem/identity assertions; no world boot.");
    }
}
