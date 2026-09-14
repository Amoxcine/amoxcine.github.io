package local.lunar.mixin;

import io.redspace.ironsspellbooks.entity.spells.portal.PortalEntity;
import local.lunar.PortalGuards;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PortalEntity.class, remap = false)
public abstract class IronPortalEntityMixin {
    @Inject(method = "checkForEntitiesToTeleport", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeCandidates(CallbackInfo ci) {
        var portal = (PortalEntity) (Object) this;
        if (PortalGuards.iron(portal.level(), portal.getUUID())) ci.cancel();
    }
}
