package fr.ascendant.lunar.travel;

import earth.terrarium.adastra.common.entities.vehicles.Rocket;
import earth.terrarium.adastra.common.entities.vehicles.Lander;
import earth.terrarium.adastra.common.menus.base.PlanetsMenuProvider;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

/** Self-only recovery and a one-shot DENIAL fixture. No grant, free fuel, teleport or replacement entity. */
final class TravelDiagnostics {
    private static final HashMap<UUID, Long> VETO = new HashMap<>();
    static void clear() { VETO.clear(); }
    static void veto(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !NativeFlight.testingNativeTransfer(player)) return;
        Long expiry = VETO.remove(player.getUUID());
        if (expiry != null && player.level().getGameTime() <= expiry) event.setCanceled(true);
    }
    static void refused(ServerPlayer player, boolean intact) {
        player.sendSystemMessage(Component.literal("NATIVE REFUSAL WITNESS " + (intact ? "PASS" : "FAIL")
            + ": same mount/position/velocity/cargo/fuel/history; no landing or kit consumption."));
    }
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lunar_travel")
            .requires(source -> LunarTravel.enabled(source.getServer()))
            .then(Commands.literal("veto_once").requires(source -> source.hasPermission(2)).executes(context -> {
                var player = context.getSource().getPlayerOrException();
                boolean valid = false;
                try { valid = player.getVehicle() instanceof Rocket rocket && NativeFlight.resume(player, rocket); }
                catch (java.io.IOException failure) { context.getSource().sendFailure(Component.literal(failure.getMessage())); }
                if (!valid) {
                    context.getSource().sendFailure(Component.literal("Requires the original paid airborne rocket and its rider.")); return 0;
                }
                VETO.put(player.getUUID(), player.level().getGameTime() + 1200);
                context.getSource().sendSuccess(() -> Component.literal("One native landing veto armed for this rider, 60 seconds. Choose destination normally."), false);
                return 1;
            }))
            .then(Commands.literal("resume").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                try {
                    var receipt = LunarTravel.flights(player.server).read(player.getUUID());
                    if (receipt != null && receipt.phase() == FlightJournal.Phase.COMPLETE
                            && receipt.source().equals(TravelPolicy.EARTH)
                            && player.level().dimension().location().toString().equals(TravelPolicy.MOON)
                            && player.getVehicle() instanceof Lander) {
                        LunarTravel.ledger(player.server).arrived(player.getUUID());
                        context.getSource().sendSuccess(() -> Component.literal("Confirmed native arrival record reconciled. No cargo or allowance granted."), false); return 1;
                    }
                    Rocket rocket = player.getVehicle() instanceof Rocket r ? r : null;
                    if (rocket == null && receipt != null && receipt.phase() != FlightJournal.Phase.COMPLETE
                            && receipt.source().equals(player.level().dimension().location().toString())
                            && player.serverLevel().getEntity(receipt.rocket()) instanceof Rocket r
                            && r.getPassengers().isEmpty() && player.distanceToSqr(r) <= 64 && r.hasLaunched()
                            && !r.isRemoved() && player.isAlive()) {
                        if (player.startRiding(r)) rocket = r;
                    }
                    if (rocket == null || !NativeFlight.resume(player, rocket)) {
                        context.getSource().sendFailure(Component.literal("No original paid source rocket available nearby; no replacement or new raw allowance issued.")); return 0;
                    }
                    String cargo = CargoVerifier.refusal(player, rocket);
                    if (cargo != null) { context.getSource().sendFailure(Component.literal(cargo)); return 0; }
                    new PlanetsMenuProvider().openMenu(player);
                    context.getSource().sendSuccess(() -> Component.literal("Original native flight resumed. Existing cargo retained, no fuel recharge, no kit reissue."), false);
                    return 1;
                } catch (Exception failure) {
                    context.getSource().sendFailure(Component.literal("Recovery refused: " + failure.getMessage())); return 0;
                }
            })));
    }
}
