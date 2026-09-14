package fr.ascendant.lunar.travel.mixin;

import earth.terrarium.adastra.common.utils.ModUtils;
import fr.ascendant.lunar.travel.NativeFlight;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModUtils.class, remap = false)
public abstract class AdAstraLandMixin {
    @Redirect(method = "land", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;stopRiding()V"), require = 1)
    private static void lunar$deferDismount(ServerPlayer player) {
        if (!NativeFlight.deferMutation(player)) player.stopRiding();
    }
    @Redirect(method = "land", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;moveTo(Lnet/minecraft/world/phys/Vec3;)V"), require = 1)
    private static void lunar$deferMove(ServerPlayer player, Vec3 position) {
        if (!NativeFlight.deferMutation(player)) player.moveTo(position);
    }
    @Redirect(method = "teleportToDimension", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"), require = 1)
    private static Vec3 lunar$nativeDestination(Entity entity) { return NativeFlight.transferPosition(entity); }
    @Inject(method = "land", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$guard(ServerPlayer player, ServerLevel target, Vec3 pos, CallbackInfo ci) {
        if (NativeFlight.rejectLand(player, target, pos)) ci.cancel();
    }
    @Redirect(method = "land", at = @At(value = "INVOKE",
        target = "Learth/terrarium/adastra/common/utils/ModUtils;teleportToDimension(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;"), require = 1)
    private static Entity lunar$transfer(Entity entity, ServerLevel target) {
        return NativeFlight.transferFromLand(entity, target);
    }
}
