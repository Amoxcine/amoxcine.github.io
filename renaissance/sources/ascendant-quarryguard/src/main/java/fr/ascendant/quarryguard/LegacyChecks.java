package fr.ascendant.quarryguard;

import com.yogpc.qp.machine.MachineStorage;
import com.yogpc.qp.machine.PowerEntity;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

/** Malformed/legacy fixtures are saved and reloaded by the real chunk machinery. */
public final class LegacyChecks {
    private static final String OWNER = "ascendant_quarryguard_owner";
    private static final String QUARANTINE = "ascendant_quarryguard_quarantine";
    private static final String FORMAT = "legacy3-v2";
    private static final int COUNT = 6;
    private LegacyChecks() { }

    public static String run(MinecraftServer server, boolean prepare) throws Exception {
        Path world = LabSupport.requireLab(server);
        Path path = world.resolve("quarryguard-legacy-test.properties");
        Properties record = new Properties();
        if (Files.exists(path)) try (var in = Files.newInputStream(path)) { record.load(in); }
        var level = server.overworld();
        if (prepare) {
            LabSupport.check(record.isEmpty() || "checked".equals(record.getProperty("stage")), "unfinished legacy fixture; preserve it for diagnosis");
            for (int i = 0; i < COUNT; i++) verifyEmpty(level, pos(i));
            var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
            var actor = LabSupport.actor(server, teams, "QGLegacyOwner");
            record.clear();
            record.setProperty("format", FORMAT);
            record.setProperty("stage", "preparing");
            record.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            record.setProperty("knownTestOwner", actor.getUUID().toString());
            save(path, record);
            for (int i = 0; i < COUNT; i++) {
                BlockPos pos = pos(i);
                record.setProperty(i + ".owned", "true");
                save(path, record);
                var machine = (PowerEntity) LabSupport.place(level, actor, pos, i < 3 ? "quarry" : "adv_quarry");
                machine.setEnergy(machine.getMaxEnergy(), false);
                storage(machine).addItem(new ItemStack(Items.DIAMOND, 7));
                storage(machine).addFluid(Fluids.WATER, 3L * MachineStorage.ONE_BUCKET);
                machine.getPersistentData().putString("phase3_sentinel", "preserve-unrelated-data");
                CompoundTag tag = machine.saveWithFullMetadata(level.registryAccess());
                int kind = i % 3;
                if (kind == 0) {
                    tag.remove(OWNER);
                    tag.getCompound("NeoForgeData").remove(OWNER);
                } else if (kind == 1) {
                    var area = LabSupport.area(machine);
                    tag.putIntArray("targetPos", new int[]{area.maxX() + 32, 64, area.minZ()});
                } else {
                    CompoundTag area = tag.getCompound("area");
                    area.putInt("maxX", area.getInt("minX"));
                }
                // Native load, not a direct call to GuardHooks.loadOwner.
                machine.loadWithComponents(tag, level.registryAccess());
                verifySuspended(level, machine, kind);
                CompoundTag frozen = machine.saveWithFullMetadata(level.registryAccess());
                for (int tick = 0; tick < 25; tick++) LabSupport.tick(machine);
                LabSupport.check(frozen.equals(machine.saveWithFullMetadata(level.registryAccess())), "legacy fixture changed before restart: " + i);
                verifyEmptyAround(level, pos);
                machine.setChanged();
                record.setProperty(i + ".tag", frozen.toString());
                save(path, record);
            }
            record.setProperty("stage", "prepared");
            save(path, record);
            return "legacy fixtures prepared: six native NBT loads, two quarry types x missing owner/out-of-area target/degenerate area; nonempty item+fluid storage and energy preserved; restart required";
        }

        LabSupport.check(FORMAT.equals(record.getProperty("format")) && "prepared".equals(record.getProperty("stage")), "missing compatible prepared legacy fixture");
        LabSupport.check(!Long.toString(ProcessHandle.current().pid()).equals(record.getProperty("pid")), "legacy check requires another JVM");
        for (int i = 0; i < COUNT; i++) {
            LabSupport.check("true".equals(record.getProperty(i + ".owned")), "legacy position not owned by fixture");
            BlockEntity machine = level.getBlockEntity(pos(i));
            LabSupport.check(GuardHooks.isQuarry(machine), "legacy machine missing after restart: " + i);
            verifySuspended(level, machine, i % 3);
            CompoundTag expected = TagParser.parseTag(record.getProperty(i + ".tag"));
            CompoundTag actual = machine.saveWithFullMetadata(level.registryAccess());
            for (String key : new String[]{OWNER, QUARANTINE, "area", "targetPos", "state", "energy", "storage", "NeoForgeData"}) {
                LabSupport.check(Objects.equals(expected.get(key), actual.get(key)), "legacy saved field changed: " + i + " / " + key);
            }
            for (int tick = 0; tick < 25; tick++) LabSupport.tick(machine);
            LabSupport.check(actual.equals(machine.saveWithFullMetadata(level.registryAccess())), "legacy native tick changed suspended machine after restart: " + i);
            verifyEmptyAround(level, pos(i));
        }
        for (int i : new int[]{0, 3}) {
            exerciseAdoption(server, level.getBlockEntity(pos(i)), UUID.fromString(record.getProperty("knownTestOwner")));
        }
        record.setProperty("stage", "verified");
        save(path, record);
        // All six cases must pass before any persisted evidence is removed.
        for (int i = 0; i < COUNT; i++) {
            BlockPos pos = pos(i);
            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2);
            LabSupport.check(level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null
                && level.getBlockState(pos.below()).isAir(), "legacy fixture cleanup incomplete");
        }
        record.setProperty("stage", "checked");
        save(path, record);
        return "legacy PASS across JVMs: six cases, 25 blocked native ticks each; no auto-adoption, quarantine owner/reason preserved, 7 diamonds + 3 buckets + energy per machine unchanged; two explicit test-only owner assignments/hostile denials/NBT rollbacks, forced chunks unchanged; only owned blocks removed";
    }

    private static void exerciseAdoption(MinecraftServer server, BlockEntity machine, UUID knownTestOwner) throws Exception {
        var level = server.overworld();
        var claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var enemy = LabSupport.actor(server, teams, "QGAdoptionEnemy");
        var hostile = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        hostile.setExtraClaimChunks(8);
        hostile.updateLimits();
        hostile.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        var area = LabSupport.area(machine);
        var claimPos = new ChunkDimPos(level.dimension(), area.minX() >> 4, area.minZ() >> 4);
        LabSupport.check(claims.getChunk(claimPos) == null, "adoption test claim occupied");
        CompoundTag original = machine.saveWithFullMetadata(level.registryAccess());
        var forced = new HashSet<>(level.getForcedChunks());
        boolean ownsClaim = false;
        try {
            // Identity comes from this test's recorded native placer, never from a claim.
            CompoundTag assigned = original.copy();
            assigned.putUUID(OWNER, knownTestOwner);
            machine.loadWithComponents(assigned, level.registryAccess());
            CompoundTag authorized = machine.saveWithFullMetadata(level.registryAccess());
            LabSupport.check(authorized.hasUUID(OWNER) && authorized.getUUID(OWNER).equals(knownTestOwner)
                && GuardHooks.mayWork(machine), "explicit owner assignment not restored/authorized");
            CompoundTag nativeOnly = authorized.copy();
            nativeOnly.remove(OWNER);
            nativeOnly.getCompound("NeoForgeData").remove(OWNER);
            LabSupport.check(nativeOnly.equals(original), "assignment changed more than owner metadata");
            ownsClaim = true;
            LabSupport.check(hostile.claim(server.createCommandSourceStack().withSuppressedOutput(), claimPos, false).isSuccess(), "adoption hostile claim failed");
            LabSupport.check(!GuardHooks.mayWork(machine), "assigned identity bypassed hostile claim");
            for (int tick = 0; tick < 25; tick++) LabSupport.tick(machine);
            LabSupport.check(authorized.equals(machine.saveWithFullMetadata(level.registryAccess())), "blocked adopted machine changed");
            LabSupport.check(hostile.unclaim(server.createCommandSourceStack().withSuppressedOutput(), claimPos, false, true).isSuccess(), "adoption unclaim failed");
            ownsClaim = false;
            LabSupport.check(GuardHooks.mayWork(machine), "assigned quarry not authorized after unclaim");
        } finally {
            if (ownsClaim) {
                var current = claims.getChunk(claimPos);
                if (current != null && current.getTeamData().getTeamId().equals(hostile.getTeamId())) claims.unregisterClaim(claimPos);
            }
            machine.loadWithComponents(original, level.registryAccess());
            machine.setChanged();
        }
        LabSupport.check(!GuardHooks.mayWork(machine) && original.equals(machine.saveWithFullMetadata(level.registryAccess())), "owner rollback did not restore original suspended NBT");
        LabSupport.check(forced.equals(new HashSet<>(level.getForcedChunks())), "assignment or rollback changed global forced chunks");
        verifyEmptyAround(level, machine.getBlockPos());
    }

    private static void verifySuspended(ServerLevel level, BlockEntity machine, int kind) throws Exception {
        CompoundTag tag = machine.saveWithFullMetadata(level.registryAccess());
        LabSupport.check(!GuardHooks.mayWork(machine), "legacy/quarantined machine authorized");
        LabSupport.check(tag.hasUUID(OWNER) == (kind != 0), "legacy owner fabricated or quarantined owner lost");
        if (kind != 0) LabSupport.check(tag.getString(QUARANTINE).equals(kind == 1
            ? "restored_target_outside_area" : "restored_area_without_valid_interior"), "quarantine reason missing");
        LabSupport.check(((PowerEntity) machine).getEnergy() > 0, "suspension test requires stored energy");
        LabSupport.check(storage(machine).getItemCount(Items.DIAMOND, DataComponentPatch.EMPTY) == 7
            && storage(machine).getFluidCount(Fluids.WATER) == 3L * MachineStorage.ONE_BUCKET, "legacy items or fluids lost");
        LabSupport.check(machine.getPersistentData().getString("phase3_sentinel").equals("preserve-unrelated-data"), "unrelated persistent data lost");
        LabSupport.check(GuardHooks.status().contains("ready=true"), "one bad machine disabled global protection");
    }

    private static MachineStorage storage(BlockEntity machine) throws Exception {
        var field = (machine instanceof QuarryEntity ? QuarryEntity.class : AdvQuarryEntity.class).getDeclaredField("storage");
        field.setAccessible(true);
        return (MachineStorage) field.get(machine);
    }

    private static BlockPos pos(int i) { return new BlockPos(4500 + i * 64, 64, 4500); }

    private static void verifyEmpty(ServerLevel level, BlockPos center) {
        var claims = FTBChunksAPI.api().getManager();
        for (int x = (center.getX() - 20) >> 4; x <= (center.getX() + 20) >> 4; x++) {
            for (int z = (center.getZ() - 20) >> 4; z <= (center.getZ() + 20) >> 4; z++) {
                LabSupport.check(claims.getChunk(new ChunkDimPos(level.dimension(), x, z)) == null, "legacy fixture already claimed");
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-20, -4, -20), center.offset(20, 6, 20))) {
            LabSupport.check(level.getBlockState(p).isAir() && level.getBlockEntity(p) == null, "legacy fixture volume occupied at " + p);
        }
    }

    private static void verifyEmptyAround(ServerLevel level, BlockPos center) {
        LabSupport.check(level.getBlockState(center.below()).is(Blocks.STONE), "legacy support changed");
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-20, -4, -20), center.offset(20, 6, 20))) {
            if (!p.equals(center) && !p.equals(center.below())) {
                LabSupport.check(level.getBlockState(p).isAir() && level.getBlockEntity(p) == null, "suspended fixture changed surrounding block " + p);
            }
        }
    }

    private static void save(Path path, Properties record) throws Exception {
        try (var out = Files.newOutputStream(path)) { record.store(out, "Disposable legacy/quarantine fixture; never a production migration"); }
    }
}
