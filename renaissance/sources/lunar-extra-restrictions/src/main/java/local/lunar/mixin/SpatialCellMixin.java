package local.lunar.mixin;

import appeng.items.storage.SpatialStorageCellItem;
import local.lunar.LunarExtraRestrictions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpatialStorageCellItem.class, remap = false)
public abstract class SpatialCellMixin {
    @Inject(method = "doSpatialTransition", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforePlot(ItemStack stack, ServerLevel level, BlockPos min, BlockPos max, int playerId,
            CallbackInfoReturnable<Boolean> cir) {
        if (LunarExtraRestrictions.shared(level)) cir.setReturnValue(false);
    }
}
