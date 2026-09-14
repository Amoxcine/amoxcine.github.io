package local.lunar.mixin;

import com.hollingsworth.arsnouveau.common.block.PortalBlock;
import com.hollingsworth.arsnouveau.common.items.data.WarpScrollData;
import local.lunar.PortalGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PortalBlock.class, remap = false)
public abstract class ArsPortalBlockMixin {
    @Inject(method = "trySpawnPortal", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeCreation(Level level, BlockPos pos, WarpScrollData data, String name, CallbackInfoReturnable<Boolean> cir) {
        if (PortalGuards.ars(level, data)) cir.setReturnValue(false);
    }
}
