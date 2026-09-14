package local.lunar.mixin;

import com.hollingsworth.arsnouveau.common.items.StableWarpScroll;
import local.lunar.PortalGuards;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StableWarpScroll.class, remap = false)
public abstract class ArsStableScrollMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeQueue(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (PortalGuards.ars(context.getLevel(), context.getItemInHand())) cir.setReturnValue(InteractionResult.FAIL);
    }
}
