package fr.ascendant.quarryguard.mixin;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import fr.ascendant.quarryguard.GuardHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClaimedChunkImpl.class, remap = false)
public abstract class ClaimOwnerMixin {
    @Inject(method = "setTeamData", at = @At("HEAD"), require = 1)
    private void beforeTransfer(ChunkTeamDataImpl team, CallbackInfo ci) { GuardHooks.beginMutation(); }

    @Inject(method = "setTeamData", at = @At("RETURN"), require = 1)
    private void afterTransfer(ChunkTeamDataImpl team, CallbackInfo ci) {
        ClaimedChunk claim = (ClaimedChunk) this;
        GuardHooks.changed(claim.getTeamData().getManager(), claim.getPos());
    }
}
