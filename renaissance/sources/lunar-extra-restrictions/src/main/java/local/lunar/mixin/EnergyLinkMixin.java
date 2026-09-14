package local.lunar.mixin;

import com.brandon3055.draconicevolution.api.modules.entities.EnergyLinkEntity;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import java.util.Optional;
import local.lunar.LunarExtraRestrictions;
import net.minecraft.core.GlobalPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EnergyLinkEntity.class, remap = false)
public abstract class EnergyLinkMixin {
    @Shadow private Optional<GlobalPos> linkedPos;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeDebit(ModuleContext context, CallbackInfo ci) {
        if (context instanceof StackModuleContext stack && stack.getEntity() != null
                && LunarExtraRestrictions.crossing(stack.getEntity().level(), linkedPos.orElse(null))) ci.cancel();
    }
}
