package local.lunar.mixin;

import local.lunar.LunarExtraRestrictions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerEnderChestContainer.class, remap = false)
public abstract class EnderValidityMixin {
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$staleMenu(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (LunarExtraRestrictions.shared(player.level())) cir.setReturnValue(false);
    }
}
