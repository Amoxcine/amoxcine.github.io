package fr.ascendant.lunar.travel.mixin;

import earth.terrarium.adastra.common.entities.vehicles.Rocket;
import fr.ascendant.lunar.travel.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = Rocket.class, remap = false)
public abstract class RocketMixin implements RocketState {
    @Unique private FlightTicket lunar$flight;
    @Unique private java.util.UUID lunar$owner;
    public java.util.UUID lunar$owner() { return lunar$owner; }
    public void lunar$owner(java.util.UUID owner) { lunar$owner = owner; }
    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"), require = 1)
    private void lunar$saveReceipt(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
        if (lunar$owner != null) tag.putUUID("LunarNativeOwner", lunar$owner);
    }
    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"), require = 1)
    private void lunar$readReceipt(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
        lunar$owner = tag.hasUUID("LunarNativeOwner") ? tag.getUUID("LunarNativeOwner") : null;
        lunar$flight = null;
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$holdUnattended(CallbackInfo ci) {
        if (NativeFlight.holdUnattended((Rocket) (Object) this)) ci.cancel();
    }
    public FlightTicket lunar$ticket() { return lunar$flight; }
    public void lunar$ticket(FlightTicket ticket) { lunar$flight = ticket; }

    @Inject(method = "canLaunch", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$eligible(CallbackInfoReturnable<Boolean> cir) {
        if (NativeFlight.rejectLaunch((Rocket) (Object) this)) cir.setReturnValue(false);
    }
    @Inject(method = "initiateLaunchSequence", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeFuel(CallbackInfo ci) {
        if (NativeFlight.beforeSequence((Rocket) (Object) this)) ci.cancel();
    }
    @Redirect(method = "initiateLaunchSequence", at = @At(value = "INVOKE",
        target = "Learth/terrarium/adastra/common/entities/vehicles/Rocket;consumeFuel(Z)Z"), require = 1)
    private boolean lunar$fuel(Rocket rocket, boolean simulate) {
        boolean success = rocket.consumeFuel(simulate);
        if (!simulate) NativeFlight.fuelConsumed(rocket, success);
        return success;
    }
}
