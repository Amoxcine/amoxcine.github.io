package fr.ascendant.lunar.recall;

import com.b1n_ry.yigd.events.YigdEvents.GraveClaimEvent;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mekanism.api.event.MekanismTeleportEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

public final class RecallEvents {
    private RecallEvents() { }

    public static void register(IEventBus bus) {
        register(bus, event -> {
            if (!(event instanceof MekanismTeleportEvent.Robit || event instanceof MekanismTeleportEvent.Teleporter)) return false;
            var entity = event.getEntity();
            if (!LunarRecall.active(entity)) return false;
            return !LunarRecall.validThread(entity) || RecallPolicy.blocksTeleport(
                entity.level().dimension().location().toString(),
                event.getTargetDimension() == null ? null : event.getTargetDimension().location().toString());
        }, event -> YigdGate.deniesGrave(event.getPlayer(), event.getGrave(), false));
    }

    // Native event bus is injectable for offline ordering/cancellation tests, not a grant API.
    static void register(IEventBus bus, Predicate<MekanismTeleportEvent.GlobalTeleport> movement,
                         Predicate<GraveClaimEvent> claims) {
        Consumer<MekanismTeleportEvent.GlobalTeleport> teleport = event -> {
            if (movement.test(event)) event.setCanceled(true);
        };
        Consumer<GraveClaimEvent> claim = event -> {
            if (claims.test(event)) {
                // GraveComponent.claim checks allowClaim(), NOT isCanceled(). Both must be set.
                event.setCanClaim(false);
                event.setCanceled(true);
            }
        };
        // HIGHEST suppresses YIGD's normal-priority key/compass mutations on denied claims.
        bus.addListener(EventPriority.HIGHEST, true, MekanismTeleportEvent.GlobalTeleport.class, teleport);
        bus.addListener(EventPriority.HIGHEST, true, GraveClaimEvent.class, claim);
        // Reassert a denial after ordinary listeners; never undo someone else's cancellation.
        bus.addListener(EventPriority.LOWEST, true, MekanismTeleportEvent.GlobalTeleport.class, teleport);
        bus.addListener(EventPriority.LOWEST, true, GraveClaimEvent.class, claim);
    }
}
