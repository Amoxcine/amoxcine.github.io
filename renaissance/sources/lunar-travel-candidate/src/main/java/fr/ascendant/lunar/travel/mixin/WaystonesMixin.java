package fr.ascendant.lunar.travel.mixin;

import fr.ascendant.lunar.travel.LunarTravel;
import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.core.WaystoneTeleportManager;
import net.minecraft.world.entity.Entity;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = WaystoneTeleportManager.class, remap = false)
public abstract class WaystonesMixin {
    @Unique private static boolean lunar$blocked(WaystoneTeleportContext context) {
        Entity entity = context.getEntity();
        if (!LunarTravel.active(entity)) return false;
        if (context.getTargetWaystone() == null) return true;
        var target = context.getTargetWaystone().getDimension();
        return LunarTravel.blocked(entity, target)
            || context.getAdditionalEntities().stream().anyMatch(e -> LunarTravel.blocked(e, target))
            || context.getLeashedEntities().stream().anyMatch(e -> LunarTravel.blocked(e, target));
    }
    @Unique private static WaystoneTeleportResult lunar$failed() {
        return WaystoneTeleportResult.failed(new WaystoneTeleportError.CancelledByEvent());
    }
    @Inject(method = {"teleport", "tryTeleport"}, at = @At("HEAD"), cancellable = true, require = 2)
    private static void lunar$sync(WaystoneTeleportContext context, CallbackInfoReturnable<WaystoneTeleportResult> cir) {
        if (lunar$blocked(context)) cir.setReturnValue(lunar$failed());
    }
    @Inject(method = {"forceTeleportAsync", "tryTeleportAsync"}, at = @At("HEAD"), cancellable = true, require = 2)
    private static void lunar$async(WaystoneTeleportContext context,
            CallbackInfoReturnable<CompletableFuture<WaystoneTeleportResult>> cir) {
        if (lunar$blocked(context)) cir.setReturnValue(CompletableFuture.completedFuture(lunar$failed()));
    }
    @Inject(method = "performTeleport", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$resolved(WaystoneTeleportContext context, TeleportDestination destination,
            CallbackInfoReturnable<WaystoneTeleportResult> cir) {
        if (lunar$blocked(context) || LunarTravel.blocked(context.getEntity(), destination.level().dimension()))
            cir.setReturnValue(lunar$failed());
    }
    @Inject(method = "consumeRequirements", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$cost(WaystoneTeleportContext context, CallbackInfo ci) {
        if (lunar$blocked(context)) ci.cancel();
    }
    @Inject(method = "teleportEntity", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$entity(WaystoneTeleportContext context, Entity entity, TeleportDestination destination,
            CallbackInfoReturnable<EntityTeleportResult> cir) {
        if (LunarTravel.blocked(entity, destination.level().dimension()))
            cir.setReturnValue(EntityTeleportResult.failed(entity, destination, new WaystoneTeleportError.CancelledByEvent()));
    }
}
