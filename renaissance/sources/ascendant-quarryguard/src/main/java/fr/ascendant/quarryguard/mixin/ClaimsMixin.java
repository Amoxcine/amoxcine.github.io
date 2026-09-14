package fr.ascendant.quarryguard.mixin;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import fr.ascendant.quarryguard.GuardHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClaimedChunkManagerImpl.class, remap = false)
public abstract class ClaimsMixin {
    @Inject(method = "registerClaim", at = @At("HEAD"), require = 1)
    private void beforeRegister(ChunkDimPos pos, ClaimedChunk claim, CallbackInfo ci) { GuardHooks.beginMutation(); }

    @Inject(method = "registerClaim", at = @At("RETURN"), require = 1)
    private void afterRegister(ChunkDimPos pos, ClaimedChunk claim, CallbackInfo ci) {
        GuardHooks.changed((ClaimedChunkManager) this, pos);
    }

    @Inject(method = "unregisterClaim", at = @At("HEAD"), require = 1)
    private void beforeRemove(ChunkDimPos pos, CallbackInfo ci) { GuardHooks.beginMutation(); }

    @Inject(method = "unregisterClaim", at = @At("RETURN"), require = 1)
    private void afterRemove(ChunkDimPos pos, CallbackInfo ci) { GuardHooks.changed((ClaimedChunkManager) this, pos); }

    @Inject(method = "shutdown", at = @At("HEAD"), require = 1)
    private static void beforeShutdown(CallbackInfo ci) { GuardHooks.invalidateManager(); }
}
