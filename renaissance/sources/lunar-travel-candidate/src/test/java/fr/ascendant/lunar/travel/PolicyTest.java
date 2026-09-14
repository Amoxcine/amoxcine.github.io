package fr.ascendant.lunar.travel;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PolicyTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var worlds = new ArrayList<>(TravelPolicy.RESERVED);
        worlds.addAll(List.of(TravelPolicy.EARTH, TravelPolicy.MOON, "minecraft:the_nether", "other:world"));
        for (String from : worlds) for (String to : worlds) {
            boolean moon = from.equals(TravelPolicy.MOON) || to.equals(TravelPolicy.MOON)
                || from.equals(TravelPolicy.MOON_ORBIT) || to.equals(TravelPolicy.MOON_ORBIT);
            boolean reserved = TravelPolicy.RESERVED.contains(to) && !from.equals(to);
            check(TravelPolicy.blocksTeleport(from, to) == (moon || reserved), "teleport " + from + " -> " + to);
            check(TravelPolicy.rocketRoute(from, to) == (
                from.equals(TravelPolicy.EARTH) && to.equals(TravelPolicy.MOON)
                || from.equals(TravelPolicy.MOON) && to.equals(TravelPolicy.EARTH)), "native route");
        }
        check(!TravelPolicy.RESERVED.contains(TravelPolicy.MOON), "Moon is no longer reserved");
        check(TravelPolicy.RESERVED.size() == 10, "all orbits stay reserved");
        check(TravelPolicy.blocksTeleport(null, TravelPolicy.EARTH), "unknown source");
        check(TravelPolicy.blocksTeleport(TravelPolicy.EARTH, null), "unknown target");
        check(!TravelPolicy.narrativeLinkApproved("operator-approved-string"), "no string grant");
        check(!TravelPolicy.narrativeLinkApproved(null), "no narrative implementation");

        Object server = new Object(), player = new Object(), rocket = new Object(), from = new Object(), to = new Object();
        var transfer = new ExactTransfer(server, player, from, to);
        check(!transfer.consume(new Object(), player, from, to), "server bound");
        check(!transfer.consume(server, new Object(), from, to), "player identity bound");
        check(!transfer.consume(server, player, to, from), "route direction bound");
        AtomicBoolean escaped = new AtomicBoolean(true);
        Thread thread = new Thread(() -> escaped.set(transfer.consume(server, player, from, to)));
        thread.start(); thread.join();
        check(!escaped.get(), "thread bound");
        check(transfer.consume(server, player, from, to), "one native event");
        check(!transfer.consume(server, player, from, to), "replay denied");

        for (String source : List.of(TravelPolicy.EARTH, TravelPolicy.MOON)) {
            String target = source.equals(TravelPolicy.EARTH) ? TravelPolicy.MOON : TravelPolicy.EARTH;
            FlightTicket ticket = new FlightTicket(server, player, rocket, source, 100);
            check(ticket.matches(server, player, rocket, source, target, 100), "native launch record");
            check(ticket.matches(server, player, rocket, source, target, 12100), "expiry inclusive");
            check(!ticket.matches(server, player, rocket, source, target, 12101), "expired");
            check(!ticket.matches(server, player, rocket, source, target, 99), "clock regression");
            check(!ticket.matches(server, new Object(), rocket, source, target, 101), "passenger swap");
            check(!ticket.matches(server, player, new Object(), source, target, 101), "rocket swap/reload");
            check(!ticket.matches(new Object(), player, rocket, source, target, 101), "server restart");
            check(!ticket.matches(server, player, rocket, target, source, 101), "history/source mismatch");
            check(!ticket.matches(server, player, rocket, source, "ad_astra:moon_orbit", 101), "orbit forbidden");
            ticket.consume();
            check(!ticket.matches(server, player, rocket, source, target, 101), "ticket spent");
        }
        NativeScope<Object> scope = new NativeScope<>();
        check(scope.current() == null, "empty scope");
        scope.call(player, () -> {
            check(scope.current() == player, "scoped identity");
            try { scope.call(rocket, () -> null); throw new AssertionError("nested scope allowed"); }
            catch (IllegalStateException expected) { check(scope.current() == player, "outer retained"); }
            return null;
        });
        check(scope.current() == null, "normal cleanup");
        try { scope.call(player, () -> { throw new IllegalArgumentException("injected failure"); }); }
        catch (IllegalArgumentException expected) { check(scope.current() == null, "exception cleanup"); }
        try { scope.call(player, () -> { throw new AssertionError("injected error"); }); }
        catch (AssertionError expected) { check(scope.current() == null, "error cleanup"); }
        check(scope.call(player, () -> 7) == 7, "subsequent flight not poisoned");

        check(CargoPolicy.RAW.size() == 8 && CargoPolicy.RAW.get("minecraft:coal") == 160, "actual raw manifest");
        check(CargoPolicy.PERSONAL.get("minecraft:cooked_beef") == 32, "personal food cap");
        check(!CargoPolicy.RAW.containsKey("ad_astra:coal_generator"), "machines not starter");
        System.out.println("PASS policy/auth/scope: " + checks + " assertions. No Minecraft runtime started.");
    }
}
