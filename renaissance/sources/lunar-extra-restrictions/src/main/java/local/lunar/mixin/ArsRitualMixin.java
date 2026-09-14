package local.lunar.mixin;

import com.hollingsworth.arsnouveau.common.ritual.RitualWarp;
import local.lunar.LunarExtraRestrictions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RitualWarp.class, remap = false)
public abstract class ArsRitualMixin {
    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$start(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (LunarExtraRestrictions.shared(((RitualWarp) (Object) this).getWorld())) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeProgress(CallbackInfo ci) {
        if (LunarExtraRestrictions.shared(((RitualWarp) (Object) this).getWorld())) ci.cancel();
    }
}
