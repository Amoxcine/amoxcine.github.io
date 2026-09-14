package fr.ascendant.lunar.travel;

import earth.terrarium.adastra.common.entities.vehicles.Rocket;
import earth.terrarium.adastra.common.handlers.LaunchingDimensionHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Before/after conservation witness for a refused transfer, not a rollback inventory. */
final class DepartureSnapshot {
    final ServerPlayer player;
    final Rocket rocket;
    final ServerLevel source;
    final Vec3 position, velocity, rocketPosition, rocketVelocity;
    final float yaw, pitch;
    final CargoScan cargo;
    final Object fuel;
    final CompoundTag history;
    DepartureSnapshot(ServerPlayer player, Rocket rocket) throws Exception {
        this.player = player; this.rocket = rocket; source = player.serverLevel();
        position = player.position(); velocity = player.getDeltaMovement();
        rocketPosition = rocket.position(); rocketVelocity = rocket.getDeltaMovement();
        yaw = player.getYRot(); pitch = player.getXRot();
        cargo = CargoVerifier.scan(player, rocket);
        fuel = rocket.fluidContainer().getContents(0);
        history = history();
    }
    private CompoundTag history() {
        var tag = new CompoundTag(); LaunchingDimensionHandler.read(source).saveData(tag); return tag;
    }
    boolean intact() throws Exception {
        return player.level() == source && rocket.level() == source && !rocket.isRemoved() && !player.isRemoved()
            && player.getVehicle() == rocket && rocket.getControllingPassenger() == player && rocket.getPassengers().size() == 1
            && position.equals(player.position()) && velocity.equals(player.getDeltaMovement())
            && rocketPosition.equals(rocket.position()) && rocketVelocity.equals(rocket.getDeltaMovement())
            && yaw == player.getYRot() && pitch == player.getXRot()
            && fuel.equals(rocket.fluidContainer().getContents(0)) && history.equals(history())
            && cargo.sameStacks(CargoVerifier.scan(player, rocket));
    }
}
