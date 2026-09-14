package fr.ascendant.quarryguard;

import com.yogpc.qp.PlatformAccess;
import com.yogpc.qp.machine.misc.QuarryChunkLoader;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native loader API checks, not a simulation of unloaded-world ticking. */
public final class SharedChunkChecks {
    private SharedChunkChecks() { }

    public static String run(MinecraftServer server) throws Exception {
        LabSupport.requireLab(server);
        LabSupport.check(PlatformAccess.config().enableChunkLoader(), "native chunk loader disabled");
        var level = server.overworld();
        var claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var initialForced = new LongOpenHashSet(level.getForcedChunks());
        List<String> evidence = new ArrayList<>();
        for (int variant = 0; variant < 2; variant++) {
            String id = variant == 0 ? "quarry" : "adv_quarry";
            BlockPos first = new BlockPos(6802 + 64 * variant, 65, 6802);
            BlockPos second = first.offset(6, 0, 0);
            ChunkPos chunk = new ChunkPos(first);
            LabSupport.check(chunk.equals(new ChunkPos(second)), "fixtures must share one chunk");
            LabSupport.check(!initialForced.contains(chunk.toLong()), "fixture chunk already forced");
            for (BlockPos p : BlockPos.betweenClosed(first.offset(-20, -2, -20), first.offset(26, 15, 20))) {
                LabSupport.check(level.getBlockState(p).isAir() && level.getBlockEntity(p) == null,
                        "shared chunk fixture not empty at " + p);
                LabSupport.check(claims.getChunk(new ChunkDimPos(level.dimension(), p.getX() >> 4, p.getZ() >> 4)) == null,
                        "shared chunk fixture already claimed");
            }
            var owner = LabSupport.actor(server, teams, "QGChunkOwner");
            var enemy = LabSupport.actor(server, teams, "QGChunkEnemy");
            var hostile = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
            hostile.setExtraClaimChunks(4);
            hostile.updateLimits();
            hostile.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
            ChunkDimPos claimPos = new ChunkDimPos(level.dimension(), chunk.x, chunk.z);
            boolean ownClaim = false;
            List<BlockPos> owned = new ArrayList<>();
            try {
                // Reserve only these known-empty positions; no volume cleanup or global ticket clear.
                owned.add(first); owned.add(first.below());
                BlockEntity a = LabSupport.place(level, owner, first, id);
                owned.add(second); owned.add(second.below());
                BlockEntity b = LabSupport.place(level, owner, second, id);
                LabSupport.check(!level.getForcedChunks().contains(chunk.toLong()), "placement unexpectedly forced chunk");
                var loadA = QuarryChunkLoader.of(level, first);
                LabSupport.check(loadA instanceof QuarryChunkLoader.Load, "first native loader should own flag");
                loadA.makeChunkLoaded(level);
                LabSupport.check(level.getForcedChunks().contains(chunk.toLong()), "authorized native loader failed");
                var loadB = QuarryChunkLoader.of(level, second);
                LabSupport.check(loadB instanceof QuarryChunkLoader.None, "native existing-flag behavior changed");
                loadB.makeChunkLoaded(level);
                var forcedWithA = new LongOpenHashSet(level.getForcedChunks());
                LabSupport.check(hostile.claim(server.createCommandSourceStack().withSuppressedOutput(), claimPos, false).isSuccess(),
                        "hostile native claim failed");
                ownClaim = true;
                CompoundTag beforeA = a.saveWithFullMetadata(level.registryAccess());
                CompoundTag beforeB = b.saveWithFullMetadata(level.registryAccess());
                for (int i = 0; i < 25; i++) { LabSupport.tick(a); LabSupport.tick(b); }
                LabSupport.check(!GuardHooks.mayWork(a) && !GuardHooks.mayWork(b), "hostile claim not denying both machines");
                LabSupport.check(beforeA.equals(a.saveWithFullMetadata(level.registryAccess()))
                        && beforeB.equals(b.saveWithFullMetadata(level.registryAccess())), "suspension changed native NBT");
                LabSupport.check(forcedWithA.equals(level.getForcedChunks()), "suspension removed a shared/global forced flag");
                loadB.makeChunkUnLoaded(level);
                LabSupport.check(forcedWithA.equals(level.getForcedChunks()), "native None unexpectedly removed flag");

                // This deliberately observes the upstream single-flag limitation, not a guard success.
                loadA.makeChunkUnLoaded(level);
                LabSupport.check(!level.getForcedChunks().contains(chunk.toLong()), "native owning loader should remove flag");
                loadA.makeChunkLoaded(level);
                LabSupport.check(!level.getForcedChunks().contains(chunk.toLong()), "denied loader created new forced flag");
                claims.unregisterClaim(claimPos);
                ownClaim = false;
                loadA.makeChunkLoaded(level);
                LabSupport.check(level.getForcedChunks().contains(chunk.toLong()), "unclaim did not restore loader permission");
                loadA.makeChunkUnLoaded(level);

                // A pre-existing external vanilla flag must not be claimed or cleared by None.
                level.setChunkForced(chunk.x, chunk.z, true);
                var external = QuarryChunkLoader.of(level, first);
                LabSupport.check(external instanceof QuarryChunkLoader.None, "external flag treated as owned");
                external.makeChunkUnLoaded(level);
                LabSupport.check(level.getForcedChunks().contains(chunk.toLong()), "external flag removed by native None");
                evidence.add(id + ": 25 denied ticks/NBT preserved/shared flag retained; denied new force refused; external flag preserved"
                        + "; OBSERVATION native owning Load removal clears shared flag (no reference counting)");
            } finally {
                if (ownClaim) {
                    var claim = claims.getChunk(claimPos);
                    LabSupport.check(claim != null && claim.getTeamData().getTeamId().equals(hostile.getTeamId()),
                            "refusing cleanup of unowned claim");
                    claims.unregisterClaim(claimPos);
                }
                for (BlockPos p : owned) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                level.setChunkForced(chunk.x, chunk.z, false);
                GuardHooks.afterPlace();
            }
            LabSupport.check(initialForced.equals(level.getForcedChunks()), "global forced chunks not restored exactly");
        }
        return "shared chunk loader API checks: " + String.join("; ", evidence)
                + ". No unload/restart, actual shared lifecycle, FTB ticket or distant-player proof.";
    }
}
