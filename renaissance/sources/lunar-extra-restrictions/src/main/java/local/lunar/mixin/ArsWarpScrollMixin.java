package local.lunar.mixin;

import com.hollingsworth.arsnouveau.common.items.WarpScroll;
import local.lunar.PortalGuards;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WarpScroll.class, remap = false)
public abstract class ArsWarpScrollMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$use(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        var stack = player.getItemInHand(hand);
        if (PortalGuards.ars(level, stack)) cir.setReturnValue(InteractionResultHolder.fail(stack));
    }

    @Inject(method = "onEntityItemUpdate", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$drop(ItemStack stack, ItemEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (PortalGuards.ars(entity.level(), stack)) cir.setReturnValue(false);
    }
}
