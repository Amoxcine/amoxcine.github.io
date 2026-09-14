package fr.ascendant.quarryguard;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** Tests claim/manager invalidation between traversal and deletion, not player simulation. */
public final class FrameAuthorityChecks {
    private FrameAuthorityChecks() { }

    public static String run(MinecraftServer server) throws Exception {
        LabSupport.requireLab(server);
        var level = server.overworld();
        var claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var actor = LabSupport.actor(server, teams, "QGFramePolicy");
        var enemy = LabSupport.actor(server, teams, "QGFramePolicyOther");
        var own = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(actor.getUUID()).orElseThrow());
        var other = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        for (var data : new ChunkTeamDataImpl[]{own, other}) {
            data.setExtraClaimChunks(8);
            data.updateLimits();
        }
        BlockPos source = new BlockPos(7039, 65, 7100), target = source.east();
        var sourceChunk = new ChunkDimPos(level.dimension(), source.getX() >> 4, source.getZ() >> 4);
        var targetChunk = new ChunkDimPos(level.dimension(), target.getX() >> 4, target.getZ() >> 4);
        for (var chunk : new ChunkDimPos[]{sourceChunk, targetChunk}) {
            LabSupport.check(claims.getChunk(chunk) == null, "frame policy fixture already claimed");
        }
        Map<ChunkDimPos, ChunkTeamData> owned = new LinkedHashMap<>();
        try {
            LabSupport.check(!GuardHooks.mayCleanFrame(null, level, target), "missing frame scope allowed");
            var wilderness = GuardHooks.beginFrameCleanup(level, source);
            LabSupport.check(GuardHooks.mayCleanFrame(wilderness, level, target), "wilderness policy denied");
            claim(server, own, targetChunk, owned);
            LabSupport.check(!GuardHooks.mayCleanFrame(wilderness, level, target), "new destination claim ignored");
            LabSupport.check(!GuardHooks.mayCleanFrame(wilderness, level, source), "mutated traversal not aborted");
            var outside = GuardHooks.beginFrameCleanup(level, source);
            LabSupport.check(!GuardHooks.mayCleanFrame(outside, level, target), "wilderness gained own-team privilege");
            claim(server, own, sourceChunk, owned);
            LabSupport.check(!GuardHooks.mayCleanFrame(outside, level, target), "new source claim expanded old authority");
            var sameTeam = GuardHooks.beginFrameCleanup(level, source);
            LabSupport.check(GuardHooks.mayCleanFrame(sameTeam, level, target), "same-team cleanup refused");
            claims.unregisterClaim(targetChunk);
            owned.remove(targetChunk);
            claim(server, other, targetChunk, owned);
            LabSupport.check(!GuardHooks.mayCleanFrame(sameTeam, level, target), "destination ownership change ignored");
            LabSupport.check(!GuardHooks.mayCleanFrame(GuardHooks.beginFrameCleanup(level, source), level, target), "other team allowed");
            claims.unregisterClaim(targetChunk);
            owned.remove(targetChunk);
            claim(server, own, targetChunk, owned);
            LabSupport.check(!GuardHooks.mayCleanFrame(sameTeam, level, target), "claim change-back revived stale traversal");
            var sourceChanged = GuardHooks.beginFrameCleanup(level, source);
            claims.unregisterClaim(sourceChunk);
            owned.remove(sourceChunk);
            LabSupport.check(!GuardHooks.mayCleanFrame(sourceChanged, level, target), "source unclaim ignored");
            claim(server, own, sourceChunk, owned);
            var beforeMutation = GuardHooks.beginFrameCleanup(level, source);
            GuardHooks.beginMutation();
            try {
                LabSupport.check(GuardHooks.beginFrameCleanup(level, source) == null, "scope created during mutation");
                LabSupport.check(!GuardHooks.mayCleanFrame(beforeMutation, level, target), "cleanup allowed during mutation");
            } finally {
                GuardHooks.changed(claims, sourceChunk);
            }
            var beforeRestart = GuardHooks.beginFrameCleanup(level, source);
            GuardHooks.stop();
            LabSupport.check(GuardHooks.beginFrameCleanup(level, source) == null, "scope created while stopped");
            LabSupport.check(!GuardHooks.mayCleanFrame(beforeRestart, level, target), "cleanup allowed while stopped");
            GuardHooks.start(server);
            LabSupport.check(!GuardHooks.mayCleanFrame(beforeRestart, level, target), "old scope revived after manager restart");
            LabSupport.check(GuardHooks.mayCleanFrame(GuardHooks.beginFrameCleanup(level, source), level, target), "new scope denied after restart");
            var invalidated = GuardHooks.beginFrameCleanup(level, source);
            GuardHooks.invalidateManager();
            LabSupport.check(!GuardHooks.mayCleanFrame(invalidated, level, target), "invalidated manager allowed cleanup");
            LabSupport.check(GuardHooks.beginFrameCleanup(level, source) == null, "invalidated manager created scope");
        } finally {
            GuardHooks.start(server);
            for (var entry : owned.entrySet()) {
                var current = claims.getChunk(entry.getKey());
                LabSupport.check(current != null && current.getTeamData().getTeamId().equals(entry.getValue().getTeam().getId()), "frame policy fixture claim changed unexpectedly");
                claims.unregisterClaim(entry.getKey());
            }
        }
        return "frame authority PASS: destination/source mutations, change-back, unavailable guard, restart epoch";
    }

    private static void claim(MinecraftServer server, ChunkTeamData data, ChunkDimPos pos,
                              Map<ChunkDimPos, ChunkTeamData> owned) {
        LabSupport.check(data.claim(server.createCommandSourceStack().withSuppressedOutput(), pos, false).isSuccess(), "frame policy claim failed");
        owned.put(pos, data);
    }
}
