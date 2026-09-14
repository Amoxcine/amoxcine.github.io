package local.lunar.mixin;

import io.redspace.ironsspellbooks.block.portal_frame.PortalFrameBlockEntity;
import local.lunar.LunarExtraRestrictions;
import local.lunar.PortalGuards;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PortalFrameBlockEntity.class, remap = false)
public abstract class IronFrameMixin {
    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeCooldown(Entity entity, CallbackInfo ci) {
        if (LunarExtraRestrictions.enabled(entity.level()) && (LunarExtraRestrictions.shared(entity.level())
                || PortalGuards.iron(entity.level(), ((PortalFrameBlockEntity) (Object) this).getUUID()))) ci.cancel();
    }
}
