package fr.ascendant.quarryguard.mixin;

import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import fr.ascendant.quarryguard.GuardHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TeamManagerImpl.class, remap = false)
public abstract class TeamsLoadMixin {
    @Inject(method = "load()V", at = @At("HEAD"), require = 1)
    private void beforeLoad(CallbackInfo ci) { GuardHooks.invalidateManager(); }
}
