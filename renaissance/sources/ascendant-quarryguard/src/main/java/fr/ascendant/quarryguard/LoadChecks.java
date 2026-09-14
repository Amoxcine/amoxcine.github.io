package fr.ascendant.quarryguard;

import com.google.gson.GsonBuilder;
import com.yogpc.qp.machine.MachineStorage;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Equivalent bounded native workloads, not populated-server MSPT. */
public final class LoadChecks {
    private static final int COUNT = 16, WARM = 200, SAMPLES = 200, MIN_Y = 50;
    private LoadChecks() { }
    private record Checkpoint(int remaining, long drops, String state, String target) { }
    private record Phase(String mode, List<Checkpoint> before, List<Checkpoint> after,
                         List<Long> energySpent, long[] nanos, long queries, long revisions) { }

    public static String run(MinecraftServer server) throws Exception {
        var world = LabSupport.requireLab(server);
        for (String name : List.of("quarryguard-restart-test.properties", "quarryguard-restart-phase3-test.properties", "quarryguard-legacy-test.properties", "quarryguard-adoption-test.properties")) {
            var restart = world.resolve(name);
            if (Files.exists(restart)) {
                Properties properties = new Properties();
                try (var in = Files.newInputStream(restart)) { properties.load(in); }
                LabSupport.check("checked".equals(properties.getProperty("stage")), "finish persistence fixtures before load test");
            }
        }
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var owner = LabSupport.actor(server, teams, "QGLoadOwner");
        boolean baseline = Boolean.getBoolean("ascendant.quarryguard.baseline");
        Phase stable = phase(server, owner, baseline, false);
        Phase invalidated = phase(server, owner, baseline, true);
        LabSupport.check(stable.before.equals(invalidated.before) && stable.after.equals(invalidated.after)
            && stable.energySpent.equals(invalidated.energySpent), "stable/invalidation native work diverged");
        var results = world.getParent().getParent().resolve("results");
        String name = "load-" + (baseline ? "baseline-" : "guarded-") + ProcessHandle.current().pid() + "-" + System.currentTimeMillis();
        Files.createDirectories(results);
        Files.writeString(results.resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(List.of(stable, invalidated)));
        try (var csv = Files.newBufferedWriter(results.resolve(name + ".csv"))) {
            csv.write("phase,sample,batch16_nanos\n");
            for (Phase phase : List.of(stable, invalidated)) {
                for (int i = 0; i < phase.nanos.length; i++) csv.write(phase.mode + "," + i + "," + phase.nanos[i] + "\n");
            }
        }
        int mined = 0;
        for (int i = 0; i < COUNT; i++) mined += stable.before.get(i).remaining - stable.after.get(i).remaining;
        return (baseline ? "BASELINE_NO_QUARRYGUARD_MIXINS" : "GUARDED")
            + ": two recreated 16-quarry wilderness fixtures, each 200 warm + 200 measured batches; mined=" + mined
            + " per measured phase, all 16 progressing, equal work/state/energy across phases; stable " + percentiles(stable.nanos)
            + "; invalidated " + percentiles(invalidated.nanos) + "; queries=" + stable.queries + "/" + invalidated.queries
            + "; raw evidence=" + name + ".json/.csv; includes energy refill, excludes mutation/setup/oracles/cleanup; NOT server MSPT or eight players";
    }

    private static Phase phase(MinecraftServer server, ServerPlayer owner, boolean baseline, boolean invalidate) throws Exception {
        ServerLevel level = server.overworld();
        var claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var team = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(owner.getUUID()).orElseThrow());
        team.setExtraClaimChunks(16);
        team.updateLimits();
        var console = server.createCommandSourceStack().withSuppressedOutput();
        var remote = new ChunkDimPos(level.dimension(), 10000, 10000);
        LabSupport.check(claims.getChunk(remote) == null, "remote fixture already claimed");
        List<QuarryEntity> machines = new ArrayList<>();
        List<BlockPos> owned = new ArrayList<>();
        boolean ownsClaim = false;
        try {
            for (int m = 0; m < COUNT; m++) {
                BlockPos pos = new BlockPos(3000 + m * 32, 64, 3000);
                for (BlockPos p : new BlockPos[]{pos, pos.below()}) {
                    LabSupport.check(level.getBlockState(p).isAir() && level.getBlockEntity(p) == null, "occupied machine fixture");
                }
                owned.add(pos);
                owned.add(pos.below());
                var machine = (QuarryEntity) LabSupport.place(level, owner, pos, "quarry", !baseline);
                machines.add(machine);
                var area = machine.getArea();
                machine.digMinY.setMinY(MIN_Y);
                for (int x = area.minX() >> 4; x <= area.maxX() >> 4; x++) {
                    for (int z = area.minZ() >> 4; z <= area.maxZ() >> 4; z++) {
                        LabSupport.check(claims.getChunk(new ChunkDimPos(level.dimension(), x, z)) == null, "load fixture is not wilderness");
                    }
                }
                LabSupport.check(claims.getChunk(new ChunkDimPos(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4)) == null, "machine chunk claimed");
                List<BlockPos> bounds = new ArrayList<>();
                for (BlockPos block : BlockPos.betweenClosed(area.minX(), MIN_Y, area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
                    LabSupport.check(level.getBlockState(block).isAir() && level.getBlockEntity(block) == null, "occupied load-test footprint at " + block);
                    bounds.add(block.immutable());
                }
                owned.addAll(bounds);
                for (BlockPos block : bounds) if (block.getY() < 64) level.setBlock(block, Blocks.STONE.defaultBlockState(), 2);
            }
            for (int i = 0; i < WARM; i++) batch(machines, null);
            List<Checkpoint> before = checkpoints(level, machines);
            long queryStart = metric("geometryQueries"), revisionStart = metric("revision"), deniedStart = metric("denied");
            long[] samples = new long[SAMPLES], energy = new long[COUNT];
            for (int i = 0; i < SAMPLES; i++) {
                if (invalidate) {
                    if ((i & 1) == 0) {
                        ownsClaim = true;
                        LabSupport.check(team.claim(console, remote, false).isSuccess(), "remote claim failed");
                    } else {
                        LabSupport.check(team.unclaim(console, remote, false, true).isSuccess(), "remote unclaim failed");
                        ownsClaim = false;
                    }
                }
                long start = System.nanoTime();
                batch(machines, energy);
                samples[i] = System.nanoTime() - start;
            }
            long queries = metric("geometryQueries") - queryStart, revisions = metric("revision") - revisionStart;
            LabSupport.check(metric("denied") == deniedStart, "unexpected refusal in allowed workload");
            if (!baseline) {
                LabSupport.check(queries == (invalidate ? (long) COUNT * SAMPLES : 0), "geometry cache behavior differs from expected workload: " + queries);
                LabSupport.check(revisions == (invalidate ? SAMPLES : 0), "claim revision did not track mutations");
            }
            List<Checkpoint> after = checkpoints(level, machines);
            for (int i = 0; i < COUNT; i++) {
                int mined = before.get(i).remaining - after.get(i).remaining;
                LabSupport.check(mined > 0 && energy[i] > 0 && !after.get(i).state.equals("FINISHED"), "machine did no measured work or finished: " + i);
                LabSupport.check(after.get(i).drops - before.get(i).drops == mined, "mined blocks/drop delta differs: " + i);
            }
            return new Phase(invalidate ? "invalidated" : "stable", before, after,
                Arrays.stream(energy).boxed().toList(), samples, queries, revisions);
        } finally {
            if (ownsClaim) claims.unregisterClaim(remote);
            for (BlockPos block : owned) level.setBlock(block, Blocks.AIR.defaultBlockState(), 2);
            for (BlockPos block : owned) LabSupport.check(level.getBlockState(block).isAir() && level.getBlockEntity(block) == null, "fixture cleanup incomplete");
            GuardHooks.afterPlace();
        }
    }

    private static List<Checkpoint> checkpoints(ServerLevel level, List<QuarryEntity> machines) throws Exception {
        var storageField = QuarryEntity.class.getDeclaredField("storage");
        storageField.setAccessible(true);
        List<Checkpoint> result = new ArrayList<>();
        for (var machine : machines) {
            LabSupport.check(level.getBlockEntity(machine.getBlockPos()) == machine && machine.digMinY.getMinY(level) == MIN_Y, "workload machine changed");
            int remaining = 0;
            var a = machine.getArea();
            for (BlockPos p : BlockPos.betweenClosed(a.minX() + 1, MIN_Y, a.minZ() + 1, a.maxX() - 1, 63, a.maxZ() - 1)) {
                if (level.getBlockState(p).is(Blocks.STONE)) remaining++;
            }
            var storage = (MachineStorage) storageField.get(machine);
            long drops = storage.getItemCount(Items.COBBLESTONE, DataComponentPatch.EMPTY) + storage.getItemCount(Items.STONE, DataComponentPatch.EMPTY);
            var tag = machine.saveWithFullMetadata(level.registryAccess());
            String state = tag.getString("state");
            LabSupport.check(!state.isEmpty(), "missing native state");
            result.add(new Checkpoint(remaining, drops, state, String.valueOf(tag.get("targetPos"))));
        }
        return List.copyOf(result);
    }

    private static void batch(List<QuarryEntity> machines, long[] energy) {
        for (int i = 0; i < machines.size(); i++) {
            var machine = machines.get(i);
            machine.setEnergy(machine.getMaxEnergy(), false);
            long before = machine.getEnergy();
            LabSupport.tick(machine);
            if (energy != null) energy[i] += before - machine.getEnergy();
        }
    }

    private static long metric(String key) {
        var match = Pattern.compile("(?:^|[ ,])" + Pattern.quote(key) + "=(\\d+)").matcher(GuardHooks.status());
        LabSupport.check(match.find(), "missing metric " + key);
        return Long.parseLong(match.group(1));
    }

    private static String percentiles(long[] original) {
        long[] nanos = original.clone();
        Arrays.sort(nanos);
        return "p50=" + nanos[99] + "ns p95=" + nanos[189] + "ns p99=" + nanos[197] + "ns";
    }
}
