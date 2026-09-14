package fr.ascendant.lunar.travel.mixin;

import fr.ascendant.lunar.travel.NativeFlight;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = ServerPlayer.class, remap = false)
public abstract class NativeDecisionMixin {
    @Redirect(method = "changeDimension", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/common/CommonHooks;onTravelToDimension(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/resources/ResourceKey;)Z"), require = 1)
    private boolean lunar$decision(Entity entity, ResourceKey<Level> target) {
        boolean allowed = CommonHooks.onTravelToDimension(entity, target);
        NativeFlight.afterDecision(entity, target, allowed);
        return allowed;
    }
}
