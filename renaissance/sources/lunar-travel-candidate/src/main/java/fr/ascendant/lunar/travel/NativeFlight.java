package fr.ascendant.lunar.travel;

import earth.terrarium.adastra.api.planets.PlanetApi;
import earth.terrarium.adastra.common.config.AdAstraConfig;
import earth.terrarium.adastra.common.entities.vehicles.Rocket;
import earth.terrarium.adastra.common.entities.vehicles.Lander;
import earth.terrarium.adastra.common.handlers.LaunchingDimensionHandler;
import earth.terrarium.adastra.common.menus.PlanetsMenu;
import earth.terrarium.adastra.common.utils.ModUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class NativeFlight {
    private static final NativeScope<Landing> LANDING = new NativeScope<>();
    private static final NativeScope<ExactTransfer> TRANSFER = new NativeScope<>();
    private static final class Landing {
        final ServerPlayer player;
        final ServerLevel target;
        final Vec3 position;
        final DepartureSnapshot departure;
        boolean entered, transferred, accepted, historyCommit;
        Landing(ServerPlayer player, ServerLevel target, Vec3 position, DepartureSnapshot departure) {
            this.player = player; this.target = target; this.position = position;
            this.departure = departure;
        }
    }
    private static final class Refused extends RuntimeException {
        Refused() { super("Native dimension event refused", null, false, false); }
    }
    private NativeFlight() {}
    static void clear() { LANDING.clear(); TRANSFER.clear(); }

    static boolean reservedFirstArrival(ServerPlayer player, Rocket rocket) {
        FlightTicket ticket = ((RocketState) rocket).lunar$ticket();
        try {
            if (LunarTravel.ledger(player.server).completed(player.getUUID())) return false;
        } catch (java.io.IOException failure) { return false; }
        return ticket != null && ticket.firstArrivalReserved() && rocket.hasLaunched()
            && ticket.matches(player.server, player, rocket, TravelPolicy.EARTH, TravelPolicy.MOON,
                player.level().getGameTime());
    }

    public static boolean holdUnattended(Rocket rocket) {
        if (!LunarTravel.active(rocket) || !rocket.hasLaunched() || rocket.getY() < AdAstraConfig.atmosphereLeave
                || !rocket.getPassengers().isEmpty()) return false;
        var owner = ((RocketState) rocket).lunar$owner();
        if (owner == null) return false;
        try {
            var receipt = LunarTravel.flights(rocket.level().getServer()).read(owner);
            return receipt != null && receipt.phase() != FlightJournal.Phase.COMPLETE
                && receipt.matches(owner, rocket.getUUID(), rocket.level().dimension().location().toString());
        } catch (java.io.IOException invalid) { return true; }
    }

    // Rebind only the original paid, still-mounted live rocket. No vehicle/item recreation or fuel refund.
    static boolean resume(ServerPlayer player, Rocket rocket) throws java.io.IOException {
        if (!LunarTravel.active(player) || !player.server.isSameThread() || player.getVehicle() != rocket
                || rocket.getControllingPassenger() != player || rocket.getPassengers().size() != 1
                || !rocket.hasLaunched() || rocket.isRemoved() || rocket.level() != player.level()
                || rocket.getY() < AdAstraConfig.atmosphereLeave || !player.isAlive()) return false;
        var journal = LunarTravel.flights(player.server);
        var receipt = journal.read(player.getUUID());
        if (receipt == null || receipt.phase() == FlightJournal.Phase.COMPLETE
                || !receipt.matches(player.getUUID(), rocket.getUUID(), player.level().dimension().location().toString())) return false;
        // A live source rider on the original source rocket has not completed this transfer.
        // Independent restoration of inconsistent world/player backups is outside this recovery rule.
        if (receipt.phase() == FlightJournal.Phase.TRANSFERRING) journal.phase(receipt, FlightJournal.Phase.READY);
        var ticket = new FlightTicket(player.server, player, rocket, receipt.source(), player.level().getGameTime());
        if (receipt.source().equals(TravelPolicy.EARTH) && !LunarTravel.ledger(player.server).completed(player.getUUID())
                && !LunarTravel.ledger(player.server).fresh(player.getUUID())) ticket.reserveFirstArrival();
        ((RocketState) rocket).lunar$ticket(ticket);
        return true;
    }

    public static boolean rejectLaunch(Rocket rocket) {
        if (!LunarTravel.active(rocket)) return false;
        if (!(rocket.getControllingPassenger() instanceof ServerPlayer player)) return true;
        String refusal = CargoVerifier.refusal(player, rocket);
        if (refusal != null || rocket.getPassengers().size() != 1 || player.getVehicle() != rocket
                || !TravelPolicy.launchWorld(rocket.level().dimension().location().toString())) {
            if (player.tickCount % 20 == 0)
                player.displayClientMessage(Component.literal("Lunar travel blocked: "
                    + (refusal == null ? "INVALID_NATIVE_PASSENGER_OR_WORLD" : refusal)), true);
            return true;
        }
        return false;
    }

    public static boolean beforeSequence(Rocket rocket) {
        if (!LunarTravel.active(rocket)) return false;
        ((RocketState) rocket).lunar$ticket(null);
        if (rocket.getControllingPassenger() instanceof ServerPlayer player) {
            try {
                var receipt = LunarTravel.flights(player.server).read(player.getUUID());
                if (receipt != null && receipt.phase() != FlightJournal.Phase.COMPLETE
                        && !LunarTravel.ledger(player.server).fresh(player.getUUID())
                        && !LunarTravel.ledger(player.server).completed(player.getUUID())) return true;
            } catch (java.io.IOException failure) { return true; }
        }
        return rejectLaunch(rocket) || rocket.isLaunching() || rocket.hasLaunched() || !rocket.hasEnoughFuel();
    }

    // Called only from the consumeFuel invocation in initiateLaunchSequence, after native success.
    public static void fuelConsumed(Rocket rocket, boolean success) {
        if (!LunarTravel.active(rocket) || !success || !rocket.isLaunching()
                || !(rocket.getControllingPassenger() instanceof ServerPlayer player)) return;
        try {
            LunarTravel.flights(player.server).paid(player.getUUID(), rocket.getUUID(), rocket.level().dimension().location().toString());
            ((RocketState) rocket).lunar$owner(player.getUUID());
            ((RocketState) rocket).lunar$ticket(new FlightTicket(player.server, player, rocket,
                rocket.level().dimension().location().toString(), rocket.level().getGameTime()));
        } catch (java.io.IOException failure) {
            player.displayClientMessage(Component.literal("Native fuel paid but flight receipt could not be saved; stop and check world storage."), false);
        }
    }

    private static Rocket authenticate(ServerPlayer player, ResourceKey<Level> target) {
        if (!player.server.isSameThread() || player.hasDisconnected() || !player.isAlive()
                || !(player.getVehicle() instanceof Rocket rocket) || rocket.isRemoved()
                || rocket.level() != player.level() || rocket.getControllingPassenger() != player
                || rocket.getPassengers().size() != 1 || !rocket.hasLaunched()
                || rocket.getY() < AdAstraConfig.atmosphereLeave || !(player.containerMenu instanceof PlanetsMenu))
            return null;
        try {
            var receipt = LunarTravel.flights(player.server).read(player.getUUID());
            if (receipt == null || receipt.phase() == FlightJournal.Phase.COMPLETE
                    || !receipt.matches(player.getUUID(), rocket.getUUID(), player.level().dimension().location().toString())) return null;
            if (receipt.phase() == FlightJournal.Phase.TRANSFERRING && !resume(player, rocket)) return null;
        } catch (java.io.IOException failure) { return null; }
        FlightTicket ticket = ((RocketState) rocket).lunar$ticket();
        if (ticket == null || !ticket.matches(player.server, player, rocket, player.level().dimension().location().toString(),
                target == null ? "" : target.location().toString(), player.level().getGameTime())) {
            try { if (!resume(player, rocket)) return null; }
            catch (java.io.IOException failure) { return null; }
            ticket = ((RocketState) rocket).lunar$ticket();
        }
        if (ticket == null || target == null || !ticket.matches(player.server, player, rocket,
                player.level().dimension().location().toString(), target.location().toString(),
                player.level().getGameTime()) || CargoVerifier.refusal(player, rocket) != null) return null;
        return rocket;
    }

    public static boolean rejectPacket(Player actor, ResourceKey<Level> requested, boolean previous) {
        if (!LunarTravel.active(actor)) return false;
        if (!(actor instanceof ServerPlayer player)) return true;
        var planet = PlanetApi.API.getPlanet(requested);
        if (planet == null || planet.isSpace()) return true;
        Rocket rocket = authenticate(player, planet.dimension());
        if (rocket == null || rocket.tier() < planet.tier()) return true;
        ResourceKey<Level> actual = planet.dimension();
        if (previous) {
            var old = LaunchingDimensionHandler.getSpawningLocation(player, player.serverLevel(), planet);
            if (old.isPresent()) actual = old.get().dimension();
        }
        // History may name a different dimension than the requested planet. Never authorize that redirect.
        if (!actual.equals(planet.dimension()) || authenticate(player, actual) == null
                || player.server.getLevel(actual) == null || !ModUtils.canTeleportToPlanet(player, planet)) return true;
        // Durable reservation BEFORE the native packet changes launch history or calls land.
        // Only this in-memory flight may reuse its own reservation for the immediate cargo recheck.
        if (actual.location().toString().equals(TravelPolicy.MOON)) {
            try {
                if (LunarTravel.ledger(player.server).begin(player.getUUID()))
                    ((RocketState) rocket).lunar$ticket().reserveFirstArrival();
            } catch (java.io.IOException error) {
                player.displayClientMessage(Component.literal("Lunar arrival record unavailable; travel refused."), false);
                return true;
            }
        }
        return false;
    }

    // Redirect of ONLY ServerboundLandPacket.Type's native ModUtils.land invocation.
    public static void landFromPacket(ServerPlayer player, ServerLevel target, Vec3 position) {
        if (!LunarTravel.active(player)) { ModUtils.land(player, target, position); return; }
        Rocket rocket = authenticate(player, target.dimension());
        if (rocket == null) return;
        final Landing landing;
        try { landing = new Landing(player, target, position, new DepartureSnapshot(player, rocket)); }
        catch (Exception failure) { player.displayClientMessage(Component.literal("Departure snapshot refused: " + failure.getMessage()), false); return; }
        LANDING.call(landing, () -> {
            try { ModUtils.land(player, target, position); }
            catch (Refused veto) {
                try {
                    if (landing.accepted || !landing.departure.intact()) throw new IllegalStateException("Refusal changed source state; reservation not released");
                    if (target.dimension().location().toString().equals(TravelPolicy.MOON)
                            && !LunarTravel.ledger(player.server).completed(player.getUUID())) {
                        LunarTravel.ledger(player.server).refusedIntact(player.getUUID());
                        ((RocketState) rocket).lunar$ticket().releaseFirstArrival();
                    }
                    TravelDiagnostics.refused(player, true);
                    player.displayClientMessage(Component.literal("Landing refused. Mount, position, cargo and history unchanged; retry this native flight. Kit right retained."), false);
                } catch (Exception failure) { throw new IllegalStateException("Cannot certify refused flight", failure); }
                return null;
            }
            if (player.level() != target || !rocket.isRemoved() || !(player.getVehicle() instanceof Lander))
                throw new IllegalStateException("Incomplete native landing; receipt retained, do not replay cargo");
            ((RocketState) rocket).lunar$ticket().consume();
            landing.historyCommit = true;
            try { LaunchingDimensionHandler.addSpawnLocation(player, landing.departure.source); }
            finally { landing.historyCommit = false; }
            try {
                var journal = LunarTravel.flights(player.server);
                journal.phase(journal.read(player.getUUID()), FlightJournal.Phase.COMPLETE);
            } catch (java.io.IOException failure) { throw new IllegalStateException("Flight receipt commit unavailable", failure); }
            if (target.dimension().location().toString().equals(TravelPolicy.MOON)) {
                try { LunarTravel.ledger(player.server).arrived(player.getUUID()); }
                catch (java.io.IOException failure) {
                    throw new IllegalStateException("Landing completed but arrival commit failed; pending record retains denial", failure);
                }
            }
            return null;
        });
    }

    public static boolean rejectLand(ServerPlayer player, ServerLevel target, Vec3 position) {
        if (!LunarTravel.active(player)) return false;
        Landing landing = LANDING.current();
        if (landing == null || landing.entered || landing.player != player || landing.target != target
                || !landing.position.equals(position)) return true;
        landing.entered = true;
        return false;
    }

    // Redirect of ONLY ModUtils.land's native teleportToDimension invocation.
    public static Entity transferFromLand(Entity entity, ServerLevel target) {
        if (!LunarTravel.active(entity)) return ModUtils.teleportToDimension(entity, target);
        Landing landing = LANDING.current();
        if (landing == null || !landing.entered || landing.transferred || landing.player != entity
                || landing.target != target) throw new IllegalStateException("Unauthenticated native transfer");
        landing.transferred = true;
        Entity result = TRANSFER.call(new ExactTransfer(target.getServer(), entity,
            entity.level().dimension(), target.dimension()), () -> ModUtils.teleportToDimension(entity, target));
        // Native land otherwise dereferences null and may still begin lander creation after another mod veto.
        if (result == null && !landing.accepted) throw new Refused();
        if (result != entity || entity.level() != target)
            throw new IllegalStateException("Native transfer failed; refusing lander inventory transfer/discard");
        return result;
    }

    public static void packetHistory(Player player, ServerLevel source) {
        if (!LunarTravel.active(player)) LaunchingDimensionHandler.addSpawnLocation(player, source);
    }
    public static net.minecraft.core.BlockPos historyPosition(Player player) {
        var landing = LANDING.current();
        return landing != null && landing.player == player && landing.historyCommit
            ? net.minecraft.core.BlockPos.containing(landing.departure.position) : player.blockPosition();
    }
    public static boolean deferMutation(ServerPlayer player) {
        var landing = LANDING.current();
        return LunarTravel.active(player) && landing != null && landing.player == player && landing.entered;
    }
    public static Vec3 transferPosition(Entity entity) {
        var landing = LANDING.current();
        return landing != null && landing.player == entity && landing.transferred && TRANSFER.current() != null
            ? landing.position : entity.position();
    }
    public static void afterDecision(Entity entity, ResourceKey<Level> target, boolean allowed) {
        var landing = LANDING.current();
        if (!allowed || landing == null || landing.player != entity || !landing.target.dimension().equals(target)
                || TRANSFER.current() == null || landing.accepted) return;
        try {
            var journal = LunarTravel.flights(landing.player.server);
            var receipt = journal.read(entity.getUUID());
            if (receipt == null || receipt.phase() != FlightJournal.Phase.READY
                    || !receipt.matches(entity.getUUID(), landing.departure.rocket.getUUID(), landing.departure.source.dimension().location().toString()))
                throw new java.io.IOException("Native receipt mismatch");
            journal.phase(receipt, FlightJournal.Phase.TRANSFERRING);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot persist transfer intent; source untouched", failure); }
        landing.accepted = true;
        landing.player.stopRiding();
    }

    static boolean consumeTransition(Entity entity, ResourceKey<Level> target) {
        ExactTransfer transfer = TRANSFER.current();
        return transfer != null && entity.level() instanceof ServerLevel level
            && transfer.consume(level.getServer(), entity, level.dimension(), target);
    }
    static boolean testingNativeTransfer(Entity entity) {
        return LANDING.current() != null && LANDING.current().player == entity && TRANSFER.current() != null;
    }
}
