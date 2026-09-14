package fr.ascendant.lunar.travel.mixin;

import earth.terrarium.adastra.common.handlers.LaunchingDimensionHandler;
import fr.ascendant.lunar.travel.NativeFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = LaunchingDimensionHandler.class, remap = false)
public abstract class LaunchHistoryMixin {
    @Redirect(method = "addSpawnLocation", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;blockPosition()Lnet/minecraft/core/BlockPos;"), require = 1)
    private static BlockPos lunar$departure(Player player) { return NativeFlight.historyPosition(player); }
}
