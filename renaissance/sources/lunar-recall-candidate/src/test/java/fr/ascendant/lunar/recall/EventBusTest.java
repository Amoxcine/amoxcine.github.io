package fr.ascendant.lunar.recall;

import com.b1n_ry.yigd.events.YigdEvents.GraveClaimEvent;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.event.MekanismTeleportEvent;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.EventPriority;

/** Real NeoForge bus and native event objects; fake predicates avoid a Minecraft server. */
public final class EventBusTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (boolean denied : new boolean[] {false,true}) {
            var bus = BusBuilder.builder().build();
            AtomicInteger nativeClaimCosts = new AtomicInteger();
            RecallEvents.register(bus, event -> denied, event -> denied);
            bus.addListener(EventPriority.NORMAL, false, GraveClaimEvent.class, event -> {
                nativeClaimCosts.incrementAndGet();
                event.setCanClaim(true);
            });
            var claim = new GraveClaimEvent(null,null,null,null,null);
            bus.post(claim);
            check(claim.isCanceled() == denied, "native claim cancellation");
            check(claim.allowClaim() != denied, "native allowClaim flag, cancellation alone insufficient");
            check(nativeClaimCosts.get() == (denied ? 0 : 1), "normal priority cost handler skipped before mutation");
            var move = new MekanismTeleportEvent.GlobalTeleport(null,0,0,0,null);
            bus.post(move);
            check(move.isCanceled() == denied, "native movement event cancellation");
            AtomicInteger entityMutation = new AtomicInteger(), debit = new AtomicInteger();
            if (!move.isCanceled()) { entityMutation.incrementAndGet(); debit.incrementAndGet(); }
            check(entityMutation.get() == (denied ? 0 : 1) && debit.get() == (denied ? 0 : 1), "native-style post-event gate simulation");
        }
        var bus = BusBuilder.builder().build();
        RecallEvents.register(bus, event -> true, event -> true);
        bus.addListener(EventPriority.NORMAL, true, GraveClaimEvent.class, event -> event.setCanClaim(true));
        var attemptedOverride = new GraveClaimEvent(null,null,null,null,null);
        bus.post(attemptedOverride);
        check(attemptedOverride.isCanceled() && !attemptedOverride.allowClaim(), "lowest priority reasserts protected claim veto");
        var outsideBus = BusBuilder.builder().build();
        RecallEvents.register(outsideBus, event -> false, event -> false);
        var cancelled = new MekanismTeleportEvent.GlobalTeleport(null,0,0,0,null);
        cancelled.setCanceled(true);
        outsideBus.post(cancelled);
        check(cancelled.isCanceled(), "never un-cancel other restrictions");
        System.out.println("NATIVE EVENT BUS PASS " + checks + " assertions; event objects and dispatch tested, no gameplay/stock trial");
    }
}
