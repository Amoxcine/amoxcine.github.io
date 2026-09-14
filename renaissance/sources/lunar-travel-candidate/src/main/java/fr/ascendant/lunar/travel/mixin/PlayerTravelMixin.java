package fr.ascendant.lunar.travel.mixin;

import fr.ascendant.lunar.travel.LunarTravel;
import java.util.Set;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerPlayer.class)
public abstract class PlayerTravelMixin {
    @Inject(method = {"teleportTo(DDD)V", "teleportRelative(DDD)V"},
        at = @At("HEAD"), cancellable = true, require = 2)
    private void lunar$local(double x, double y, double z, CallbackInfo ci) {
        if (LunarTravel.moon((ServerPlayer) (Object) this)) ci.cancel();
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$direct(ServerLevel target, double x, double y, double z,
            Set<RelativeMovement> relative, float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        if (LunarTravel.blocked((ServerPlayer) (Object) this, target.dimension())) cir.setReturnValue(false);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$void(ServerLevel target, double x, double y, double z,
            float yaw, float pitch, CallbackInfo ci) {
        if (LunarTravel.blocked((ServerPlayer) (Object) this, target.dimension())) ci.cancel();
    }
}
