package local.lunar.mixin;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import local.lunar.LunarExtraRestrictions;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PortalTrackProvider.class, remap = false)
public interface CreatePortalProviderMixin {
    @Inject(method = "getOtherSide", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$beforeProvider(ServerLevel level, BlockFace face, CallbackInfoReturnable<PortalTrackProvider.Exit> cir) {
        if (LunarExtraRestrictions.shared(level)) cir.setReturnValue(null);
    }
}
