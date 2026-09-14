package fr.ascendant.quarryguard;

import com.google.gson.JsonObject;
import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.WorkResult;
import com.yogpc.qp.machine.advquarry.AdvActionSyncMessage;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

/** Native packet codec/handler and malformed-column regression, only in the lab. */
public final class GeometryChecks {
    private GeometryChecks() { }

    public static String run(MinecraftServer server) throws Exception {
        LabSupport.requireLab(server);
        var level = server.overworld();
        var claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var owner = LabSupport.actor(server, teams, "QGGeometryOwner");
        var enemy = LabSupport.actor(server, teams, "QGGeometryEnemy");
        var hostile = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        hostile.setExtraClaimChunks(8);
        hostile.updateLimits();
        hostile.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        int packets = 0;
        for (int origin : new int[]{4000, -4016}) {
            BlockPos pos = new BlockPos(origin, 64, 4000);
            BlockPos witness = new BlockPos(origin + 16, 63, 4003);
            ChunkDimPos neighbor = new ChunkDimPos(level.dimension(), (origin + 16) >> 4, 250);
            LabSupport.check(claims.getChunk(neighbor) == null, "geometry claim occupied");
            for (BlockPos p : new BlockPos[]{pos, pos.below(), witness}) {
                LabSupport.check(level.getBlockState(p).isAir() && level.getBlockEntity(p) == null, "geometry fixture occupied");
            }
            boolean ownsClaim = false;
            try {
                var machine = (AdvQuarryEntity) LabSupport.place(level, owner, pos, "adv_quarry");
                Area valid = new Area(origin + 2, 64, 4002, origin + 12, 68, 4012, Direction.NORTH);
                machine.setArea(valid);
                LabSupport.check(machine.getArea().equals(valid), "valid area rejected");
                ownsClaim = true;
                LabSupport.check(hostile.claim(server.createCommandSourceStack().withSuppressedOutput(), neighbor, false).isSuccess(), "geometry claim failed");
                level.setBlock(witness, Blocks.WATER.defaultBlockState(), 2);
                machine.setEnergy(machine.getMaxEnergy(), false);
                owner.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                LabSupport.check(GuardHooks.mayConfigure(owner, machine) && machine.enabled, "geometry packet actor not eligible");
                for (boolean chunkByChunk : new boolean[]{false, true}) {
                    for (int span : new int[]{0, 1}) {
                        for (boolean thinX : new boolean[]{false, true}) {
                            Area malformed = thinX
                                ? new Area(origin + 15, 64, 4002, origin + 15 + span, 68, 4012, Direction.NORTH)
                                : new Area(origin + 2, 64, 4015, origin + 12, 68, 4015 + span, Direction.NORTH);
                            var before = machine.saveWithFullMetadata(level.registryAccess());
                            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                            try {
                                buffer.writeBlockPos(pos);
                                buffer.writeResourceKey(level.dimension());
                                buffer.writeBoolean(true);
                                buffer.writeJsonWithCodec(Area.CODEC.codec(), malformed);
                                JsonObject config = new JsonObject();
                                config.addProperty("startImmediately", true);
                                config.addProperty("placeAreaFrame", false);
                                config.addProperty("chunkByChunk", chunkByChunk);
                                buffer.writeUtf(config.toString());
                                buffer.writeBoolean(true);
                                AdvActionSyncMessage.STREAM_CODEC.decode(buffer).onReceive(level, owner);
                            } finally { buffer.release(); }
                            LabSupport.check(before.equals(machine.saveWithFullMetadata(level.registryAccess())), "malformed packet changed native machine");
                            machine.setArea(malformed);
                            LabSupport.check(before.equals(machine.saveWithFullMetadata(level.registryAccess())), "malformed direct setArea changed machine");
                            LabSupport.check(level.getBlockState(witness).is(Blocks.WATER), "hostile witness fluid changed");
                            packets++;
                        }
                    }
                }
                LabSupport.check(GuardHooks.validArea(new Area(origin, 64, 4000, origin + 2, 68, 4002, Direction.NORTH)), "three-block area has valid interior");
                var storage = machine.saveWithFullMetadata(level.registryAccess()).get("storage").copy();
                long energy = machine.getEnergy();
                var breakBlocks = AdvQuarryEntity.class.getDeclaredMethod("breakBlocks", int.class, int.class);
                breakBlocks.setAccessible(true);
                LabSupport.check(breakBlocks.invoke(machine, witness.getX(), witness.getZ()) == WorkResult.FAIL_EVENT, "native out-of-area column was not refused");
                var after = machine.saveWithFullMetadata(level.registryAccess());
                LabSupport.check(level.getBlockState(witness).is(Blocks.WATER) && machine.getEnergy() == energy
                    && after.get("storage").equals(storage), "out-of-area column changed fluid, energy or storage");
                LabSupport.check(after.contains("ascendant_quarryguard_quarantine") && !GuardHooks.mayWork(machine), "invalid column did not quarantine machine");
            } finally {
                if (ownsClaim) claims.unregisterClaim(neighbor);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(witness, Blocks.AIR.defaultBlockState(), 2);
                GuardHooks.afterPlace();
            }
        }
        return packets + " malformed native packet codec/handler and direct-area refusals; positive/negative borders; both traversals; foreign-fluid column intact, local quarantine; no real network clients";
    }
}
