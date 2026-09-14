package local.lunar.mixin;

import local.lunar.LunarExtraRestrictions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChestMenu.class, remap = false)
public abstract class EnderQuickMoveMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$directShiftClick(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (LunarExtraRestrictions.shared(player.level())
                && ((ChestMenu) (Object) this).getContainer() instanceof PlayerEnderChestContainer) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
