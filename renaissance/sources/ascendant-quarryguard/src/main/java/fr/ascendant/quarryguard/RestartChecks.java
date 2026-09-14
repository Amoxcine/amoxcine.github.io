package fr.ascendant.quarryguard;

import com.yogpc.qp.PlatformAccess;
import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.MachineStorage;
import com.yogpc.qp.machine.PowerEntity;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.lang.reflect.Field;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;

/** Two real JVMs, native mining on both sides, and a write-ahead fixture journal. */
public final class RestartChecks {
    private static final String OWNER = "ascendant_quarryguard_owner";
    private static final String SCHEMA = "native-mining-restart-3";
    private static final int MIN_Y = 64, GOLD = 49, BLOCKED_TICKS = 25, MAX_TICKS = 2_000;
    private static final long MAX_NANOS = 3_000_000_000L;
    // Never revisit the old (800/848,64,800) fixtures or their unrecorded frames.
    private static final List<Fixture> FIXTURES = List.of(fixture("quarry", 6000), fixture("adv_quarry", 6064));

    private final MinecraftServer server;
    private final ServerLevel level;
    private final Path world, path;
    private final ClaimedChunkManagerImpl claims;
    private final TeamManagerImpl teams;
    private final CommandSourceStack console;
    private final Properties record = new Properties();
    private final Map<ChunkPos, Visibility> visibility = new LinkedHashMap<>();
    private PersistentEntitySectionManager<Entity> entityManager;
    private boolean journalStarted;

    private record Fixture(String id, BlockPos pos, Area area, List<BlockPos> owned, AABB effects) { }
    private record Mining(int ticks, int mined, long drops, long spent) { }

    private RestartChecks(MinecraftServer server, Path world) {
        this.server = server;
        level = server.overworld();
        this.world = world;
        path = world.resolve("quarryguard-restart-phase3-test.properties");
        claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        console = server.createCommandSourceStack().withSuppressedOutput();
    }

    public static String run(MinecraftServer server, boolean prepare) throws Exception {
        Path world = LabSupport.requireLab(server);
        LabSupport.check(FTBChunksAPI.api().isManagerLoaded(), "FTB Chunks not ready");
        LabSupport.check(!PlatformAccess.config().noEnergy(), "restart mining requires native noEnergy=false");
        RestartChecks test = new RestartChecks(server, world);
        test.readRecord();
        try {
            return prepare ? test.prepare() : test.checkRestart();
        } catch (Exception error) {
            // An uncertain/partial fixture is evidence, not permission to erase terrain.
            if (test.journalStarted) {
                test.record.setProperty("failureAt", test.record.getProperty("phase", "unknown"));
                test.record.setProperty("failure", error.toString());
                test.record.setProperty("phase", "failed");
                test.record.setProperty("stage", "prepared");
                try { test.writeRecord(); }
                catch (Exception journalError) { error.addSuppressed(journalError); }
            }
            throw new IllegalStateException("Restart fixture failed; no automatic failure cleanup. Inspect "
                    + test.path + ": " + error.getMessage(), error);
        } finally {
            if (test.entityManager != null) test.visibility.forEach(test.entityManager::updateChunkStatus);
        }
    }

    private static Fixture fixture(String id, int x) {
        BlockPos pos = new BlockPos(x + 4, 65, 5999);
        Area area = new Area(x, 65, 6000, x + 8, 69, 6008, Direction.NORTH);
        List<BlockPos> owned = new ArrayList<>();
        owned.add(pos);
        owned.add(pos.below());
        for (BlockPos block : BlockPos.betweenClosed(x, MIN_Y, 6000, x + 8, 69, 6008)) owned.add(block.immutable());
        return new Fixture(id, pos, area, List.copyOf(owned), new AABB(x - 6, 58, 5994, x + 15, 75, 6015));
    }

    private void readRecord() throws Exception {
        if (!Files.exists(path)) return;
        LabSupport.check(Files.isRegularFile(path) && !Files.isSymbolicLink(path), "unsafe restart manifest path");
        try (var input = Files.newInputStream(path)) { record.load(input); }
        LabSupport.check(SCHEMA.equals(record.getProperty("schema")),
                "incompatible restart manifest; retain it for operator review, do not clean old coordinates");
        LabSupport.check(world.toString().equals(required("world"))
                && level.dimension().location().toString().equals(required("dimension")), "manifest belongs to another world");
        for (int i = 0; i < FIXTURES.size(); i++) {
            Fixture f = FIXTURES.get(i);
            LabSupport.check(f.id.equals(required(i + ".id")) && f.pos.toString().equals(required(i + ".pos"))
                    && f.area.toString().equals(required(i + ".area"))
                    && ownedTag(f).equals(TagParser.parseTag(required(i + ".owned")))
                    && "minecraft:air".equals(required(i + ".original"))
                    && claimPos(f).toString().equals(required(i + ".claim")), "incompatible fixture layout " + i);
        }
        UUID.fromString(required("owner"));
        UUID.fromString(required("claimTeam"));
        required("process");
    }

    private String prepare() throws Exception {
        LabSupport.check(record.isEmpty() || ("checked".equals(required("stage"))
                && "complete".equals(required("phase"))), "existing restart fixture needs inspection/check, not replacement");
        // Validate BOTH fixtures before actor creation, placement, energy injection or claims.
        for (Fixture f : FIXTURES) {
            requireClaims(f, false);
            requireDefaultPlacementClaims(f);
            for (BlockPos pos : effectPositions(f)) requireAir(pos);
        }
        trackEntities();
        for (Fixture f : FIXTURES) requireNoEntities(f);
        ServerPlayer owner = LabSupport.actor(server, teams, "QGRestartOwner");
        ServerPlayer enemy = LabSupport.actor(server, teams, "QGRestartEnemy");
        LabSupport.check(!claims.getBypassProtection(owner.getUUID()) && !owner.isCreative()
                && server.getPlayerList().getPlayer(owner.getUUID()) == null, "offline survival owner without bypass required");
        var hostile = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        hostile.setExtraClaimChunks(16);
        hostile.updateLimits();
        hostile.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);

        record.clear();
        record.setProperty("schema", SCHEMA);
        record.setProperty("world", world.toString());
        record.setProperty("dimension", level.dimension().location().toString());
        record.setProperty("process", process());
        record.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        record.setProperty("owner", owner.getUUID().toString());
        record.setProperty("claimTeam", hostile.getTeamId().toString());
        // stage=prepared also covers partial work, preserving existing lab interlocks.
        record.setProperty("stage", "prepared");
        record.setProperty("phase", "reserved");
        for (int i = 0; i < FIXTURES.size(); i++) {
            Fixture f = FIXTURES.get(i);
            record.setProperty(i + ".id", f.id);
            record.setProperty(i + ".pos", f.pos.toString());
            record.setProperty(i + ".area", f.area.toString());
            record.setProperty(i + ".owned", ownedTag(f).toString());
            record.setProperty(i + ".original", "minecraft:air");
            record.setProperty(i + ".claim", claimPos(f).toString());
            record.setProperty(i + ".claimStatus", "absent");
        }
        writeRecord();
        journalStarted = true;
        List<String> evidence = new ArrayList<>();
        for (int i = 0; i < FIXTURES.size(); i++) {
            Fixture f = FIXTURES.get(i);
            phase("placing-" + i);
            for (BlockPos pos : f.owned) {
                if (isOre(f, pos)) LabSupport.check(level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 3), "gold placement failed");
            }
            PowerEntity machine = (PowerEntity) LabSupport.place(level, owner, f.pos, f.id);
            configure(machine, f, owner);
            machine.setEnergy(machine.getMaxEnergy(), false);
            LabSupport.check(machine.getEnergy() == machine.getMaxEnergy() && machine.getEnergy() > 0, "native energy injection failed");
            LabSupport.check(remaining(f) == GOLD && stored(machine) == 0, "initial mining layer/storage differs");
            phase("mining-before-restart-" + i);
            Mining before = mine(machine, f);
            evidence.add(f.id + " " + before);
            record.setProperty(i + ".before", before.toString());
            checkpoint(i + ".paused", machine, f);
            requireClaims(f, false);
            record.setProperty(i + ".claimStatus", "intent");
            phase("claiming-" + i);
            LabSupport.check(hostile.claim(console, claimPos(f), false).isSuccess(), "native hostile claim failed");
            record.setProperty(i + ".claimStatus", "owned");
            requireClaims(f, true);
            deniedTicks(machine, f);
            machine.setChanged();
            checkpoint(i + ".paused", machine, f);
            phase("paused-" + i);
        }
        phase("ready");
        return "restart fixtures prepared: " + String.join("; ", evidence)
                + "; native mining/storage/energy witnessed, 25 denied ticks each, journal=" + path
                + "; save and stop normally, then restart-check in a new JVM";
    }

    private String checkRestart() throws Exception {
        LabSupport.check("prepared".equals(required("stage")) && "ready".equals(required("phase")),
                "missing complete preparation; partial/failed records require operator inspection");
        LabSupport.check(!process().equals(required("process")), "restart-check requires a different JVM incarnation");
        List<PowerEntity> machines = new ArrayList<>();
        // Validate both persisted fixtures before the first unclaim.
        for (int i = 0; i < FIXTURES.size(); i++) {
            Fixture f = FIXTURES.get(i);
            LabSupport.check("owned".equals(required(i + ".claimStatus")), "missing recorded hostile claim");
            requireClaims(f, true);
            BlockEntity entity = level.getBlockEntity(f.pos);
            LabSupport.check(entity instanceof PowerEntity && GuardHooks.isQuarry(entity), "machine missing after restart: " + f.id);
            PowerEntity machine = (PowerEntity) entity;
            requireMachine(machine, f);
            CompoundTag expected = TagParser.parseTag(required(i + ".paused.tag"));
            CompoundTag actual = tag(machine);
            for (String key : List.of("id", "x", "y", "z", OWNER, "area", "state", "energy", "maxEnergy",
                    "storage", "targetPos", "digMinY", "moduleInventory", "workConfig")) {
                LabSupport.check(Objects.equals(expected.get(key), actual.get(key)), f.id + " changed across restart: " + key);
            }
            LabSupport.check(expected.hasUUID(OWNER) && expected.contains("storage") && expected.contains("targetPos")
                    && expected.getUUID(OWNER).equals(UUID.fromString(required("owner"))), "incomplete saved mining checkpoint");
            LabSupport.check(blocks(f).equals(TagParser.parseTag(required(i + ".paused.blocks"))), f.id + " terrain changed across restart");
            LabSupport.check(remaining(f) == Integer.parseInt(required(i + ".paused.remaining"))
                    && stored(machine) == Long.parseLong(required(i + ".paused.drops"))
                    && machine.getEnergy() == Long.parseLong(required(i + ".paused.energy")), "persisted mining counters differ");
            requireOutsideAir(f);
            machines.add(machine);
        }
        trackEntities();
        for (Fixture f : FIXTURES) requireNoEntities(f);
        journalStarted = true;
        record.setProperty("checkProcess", process());
        phase("checking-denied");
        for (int i = 0; i < machines.size(); i++) deniedTicks(machines.get(i), FIXTURES.get(i));
        phase("checking");
        List<String> evidence = new ArrayList<>();
        for (int i = 0; i < machines.size(); i++) {
            Fixture f = FIXTURES.get(i);
            PowerEntity machine = machines.get(i);
            requireClaims(f, true);
            var claim = claims.getChunk(claimPos(f));
            phase("unclaiming-" + i);
            LabSupport.check(claim.getTeamData().unclaim(console, claimPos(f), false, true).isSuccess(), "native fixture unclaim failed");
            record.setProperty(i + ".claimStatus", "removed");
            requireClaims(f, false);
            LabSupport.check(GuardHooks.mayWork(machine), "authorization not restored after native unclaim");
            phase("mining-after-restart-" + i);
            // No reload, setArea, state/target write, or energy refill after restart.
            Mining after = mine(machine, f);
            record.setProperty(i + ".after", after.toString());
            checkpoint(i + ".resumed", machine, f);
            evidence.add(f.id + " " + after);
            phase("resumed-" + i);
        }
        cleanup(machines);
        record.setProperty("stage", "checked");
        phase("complete");
        return "restart PASS: different JVM, both owners/areas/targets/storage/energy persisted; 25 mutation-free denied ticks each; "
                + "native mining after unclaim: " + String.join("; ", evidence)
                + "; only journal-owned positions cleaned; offline FTB teams retained";
    }

    private void configure(PowerEntity machine, Fixture f, ServerPlayer owner) {
        LabSupport.check("WAITING".equals(tag(machine).getString("state")) && GuardHooks.mayConfigure(owner, machine),
                "native placement must be configurable and WAITING");
        if (machine instanceof AdvQuarryEntity) {
            // PoweredChecks' initial user workConfig; never forge a working state/iterator.
            CompoundTag initial = tag(machine);
            CompoundTag config = new CompoundTag();
            config.putBoolean("startImmediately", true);
            config.putBoolean("placeAreaFrame", true);
            config.putBoolean("chunkByChunk", false);
            initial.put("workConfig", config);
            machine.loadWithComponents(initial, level.registryAccess());
        }
        if (machine instanceof QuarryEntity quarry) {
            quarry.setArea(f.area);
            quarry.digMinY.setMinY(MIN_Y);
        } else if (machine instanceof AdvQuarryEntity quarry) {
            quarry.setArea(f.area);
            quarry.digMinY.setMinY(MIN_Y);
        }
        requireMachine(machine, f);
        LabSupport.check("WAITING".equals(tag(machine).getString("state")), "configuration changed native state");
    }

    private Mining mine(PowerEntity machine, Fixture f) throws Exception {
        int before = remaining(f);
        long dropsBefore = stored(machine), energyBefore = machine.getEnergy();
        LabSupport.check(before > 1 && dropsBefore == GOLD - before && energyBefore > 0, "no bounded mining work/energy remains");
        long deadline = System.nanoTime() + MAX_NANOS;
        int ticks = 0;
        while (remaining(f) == before) {
            LabSupport.check(ticks < MAX_TICKS && System.nanoTime() <= deadline,
                    f.id + " native mining budget exhausted; ticks=" + ticks + ", state=" + tag(machine).getString("state"));
            requireMachine(machine, f);
            LabSupport.check(!"FINISHED".equals(tag(machine).getString("state")) && GuardHooks.mayWork(machine), "native machine stopped before useful work");
            long energy = machine.getEnergy();
            LabSupport.tick(machine);
            ticks++;
            LabSupport.check(machine.getEnergy() > 0 && machine.getEnergy() <= energy, "unexpected energy exhaustion/gain during mining");
        }
        requireMachine(machine, f);
        int left = remaining(f), mined = before - left;
        long drops = stored(machine) - dropsBefore, spent = energyBefore - machine.getEnergy();
        String state = tag(machine).getString("state");
        LabSupport.check(mined > 0 && left > 0 && drops == mined && stored(machine) == GOLD - left && spent > 0,
                f.id + " no exact mined-block/storage/energy delta: mined=" + mined + ", drops=" + drops + ", spent=" + spent);
        LabSupport.check(state.equals("BREAK_BLOCK") || (machine instanceof QuarryEntity && state.equals("MOVE_HEAD")),
                f.id + " not paused in native mining: " + state);
        LabSupport.check(tag(machine).contains("targetPos"), "native mining checkpoint lacks persisted target");
        requireOutsideAir(f);
        requireNoEntities(f);
        machine.setChanged();
        return new Mining(ticks, mined, drops, spent);
    }

    private void deniedTicks(PowerEntity machine, Fixture f) throws Exception {
        requireMachine(machine, f);
        CompoundTag expected = tag(machine), terrain = blocks(f);
        long energy = machine.getEnergy(), drops = stored(machine);
        LabSupport.check(energy > 0 && remaining(f) > 0 && drops > 0, "denial must interrupt powered useful mining");
        requireNoEntities(f);
        for (int t = 0; t < BLOCKED_TICKS; t++) {
            LabSupport.check(!GuardHooks.mayWork(machine), f.id + " hostile claim allowed work");
            LabSupport.tick(machine);
            LabSupport.check(expected.equals(tag(machine)) && energy == machine.getEnergy() && drops == stored(machine),
                    f.id + " denied tick mutated NBT/owner/state/target/storage/energy at " + t);
            LabSupport.check(terrain.equals(blocks(f)), f.id + " denied tick mutated fixture terrain at " + t);
            requireOutsideAir(f);
            requireNoEntities(f);
        }
    }

    private void requireMachine(PowerEntity machine, Fixture f) {
        LabSupport.check(level.getBlockEntity(f.pos) == machine
                && (f.id.equals("quarry") ? machine instanceof QuarryEntity : machine instanceof AdvQuarryEntity), "fixture machine replaced");
        CompoundTag tag = tag(machine);
        UUID owner = UUID.fromString(required("owner"));
        LabSupport.check(tag.hasUUID(OWNER) && tag.getUUID(OWNER).equals(owner)
                && machine.getPersistentData().hasUUID(OWNER) && machine.getPersistentData().getUUID(OWNER).equals(owner)
                && !claims.getBypassProtection(owner), "native owner lost/changed or bypass enabled");
        LabSupport.check(f.area.equals(LabSupport.area(machine)), "fixture area changed");
        int minY = machine instanceof QuarryEntity quarry ? quarry.digMinY.getMinY(level)
                : ((AdvQuarryEntity) machine).digMinY.getMinY(level);
        LabSupport.check(minY == MIN_Y && tag.getList("moduleInventory", 10).isEmpty(), "mining depth/modules changed");
    }

    private void cleanup(List<PowerEntity> machines) throws Exception {
        // Validate the pair first; the manifest never supplies arbitrary cleanup coordinates.
        for (int i = 0; i < machines.size(); i++) {
            Fixture f = FIXTURES.get(i);
            requireMachine(machines.get(i), f);
            requireClaims(f, false);
            requireOutsideAir(f);
            requireNoEntities(f);
            LabSupport.check(tag(machines.get(i)).equals(TagParser.parseTag(required(i + ".resumed.tag")))
                    && blocks(f).equals(TagParser.parseTag(required(i + ".resumed.blocks"))), "fixture changed before cleanup");
        }
        phase("cleaning-owned-positions");
        for (Fixture f : FIXTURES) {
            for (BlockPos pos : f.owned) {
                if (!level.getBlockState(pos).isAir()) {
                    LabSupport.check(level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3), "owned cleanup failed at " + pos);
                }
            }
            for (BlockPos pos : f.owned) requireAir(pos);
            requireOutsideAir(f);
            requireNoEntities(f);
        }
    }

    private void checkpoint(String prefix, PowerEntity machine, Fixture f) throws Exception {
        record.setProperty(prefix + ".tag", tag(machine).toString());
        record.setProperty(prefix + ".blocks", blocks(f).toString());
        record.setProperty(prefix + ".remaining", Integer.toString(remaining(f)));
        record.setProperty(prefix + ".drops", Long.toString(stored(machine)));
        record.setProperty(prefix + ".energy", Long.toString(machine.getEnergy()));
    }

    private CompoundTag tag(PowerEntity machine) { return machine.saveWithFullMetadata(level.registryAccess()); }

    private CompoundTag blocks(Fixture f) {
        CompoundTag result = new CompoundTag();
        for (BlockPos pos : f.owned) {
            LabSupport.check(pos.equals(f.pos) || level.getBlockEntity(pos) == null, "unexpected block entity at " + pos);
            result.put(Long.toString(pos.asLong()), NbtUtils.writeBlockState(level.getBlockState(pos)));
        }
        return result;
    }

    private static CompoundTag ownedTag(Fixture f) {
        CompoundTag result = new CompoundTag();
        result.putLongArray("positions", f.owned.stream().mapToLong(BlockPos::asLong).toArray());
        return result;
    }

    private int remaining(Fixture f) {
        int count = 0;
        for (BlockPos pos : f.owned) {
            if (!isOre(f, pos)) continue;
            BlockState state = level.getBlockState(pos);
            LabSupport.check(state.is(Blocks.GOLD_BLOCK) || state.isAir(), "mined gold replaced with a non-air block at " + pos);
            if (state.is(Blocks.GOLD_BLOCK)) count++;
        }
        return count;
    }

    private static boolean isOre(Fixture f, BlockPos pos) {
        return pos.getY() == MIN_Y && pos.getX() > f.area.minX() && pos.getX() < f.area.maxX()
                && pos.getZ() > f.area.minZ() && pos.getZ() < f.area.maxZ();
    }

    private static long stored(PowerEntity machine) throws ReflectiveOperationException {
        Class<?> type = machine instanceof QuarryEntity ? QuarryEntity.class : AdvQuarryEntity.class;
        Field field = type.getDeclaredField("storage");
        field.setAccessible(true);
        return ((MachineStorage) field.get(machine)).getItemCount(Items.GOLD_BLOCK, DataComponentPatch.EMPTY);
    }

    private ChunkDimPos claimPos(Fixture f) {
        return new ChunkDimPos(level.dimension(), Math.floorDiv(f.area.minX(), 16), Math.floorDiv(f.area.minZ(), 16));
    }

    private void requireClaims(Fixture f, boolean hostileExpected) {
        ChunkDimPos target = claimPos(f);
        for (int x = Math.floorDiv((int) f.effects.minX, 16); x <= Math.floorDiv((int) f.effects.maxX - 1, 16); x++) {
            for (int z = Math.floorDiv((int) f.effects.minZ, 16); z <= Math.floorDiv((int) f.effects.maxZ - 1, 16); z++) {
                ChunkDimPos pos = new ChunkDimPos(level.dimension(), x, z);
                var claim = claims.getChunk(pos);
                if (hostileExpected && pos.equals(target)) {
                    LabSupport.check(claim != null && claim.getTeamData().getTeamId().toString().equals(required("claimTeam")),
                            "hostile fixture claim missing/replaced at " + pos);
                } else LabSupport.check(claim == null, "foreign claim in fixture effect volume at " + pos);
            }
        }
    }

    private void requireDefaultPlacementClaims(Fixture f) {
        // The advanced default area precedes bounded setArea, but is never ticked.
        ChunkPos chunk = new ChunkPos(f.pos);
        for (int x = chunk.x - 1; x <= chunk.x + 1; x++) {
            for (int z = chunk.z - 1; z <= chunk.z + 1; z++) {
                LabSupport.check(claims.getChunk(new ChunkDimPos(level.dimension(), x, z)) == null, "default placement area already claimed");
            }
        }
    }

    private static Iterable<BlockPos> effectPositions(Fixture f) {
        return BlockPos.betweenClosed(BlockPos.containing(f.effects.minX, f.effects.minY, f.effects.minZ),
                BlockPos.containing(f.effects.maxX - 1, f.effects.maxY - 1, f.effects.maxZ - 1));
    }

    private void requireAir(BlockPos pos) {
        LabSupport.check(level.getBlockState(pos).equals(Blocks.AIR.defaultBlockState()) && level.getBlockEntity(pos) == null,
                "fixture position not pristine air: " + pos);
    }

    private void requireOutsideAir(Fixture f) {
        for (BlockPos pos : effectPositions(f)) {
            boolean owned = pos.equals(f.pos) || pos.equals(f.pos.below())
                    || (pos.getX() >= f.area.minX() && pos.getX() <= f.area.maxX()
                    && pos.getZ() >= f.area.minZ() && pos.getZ() <= f.area.maxZ()
                    && pos.getY() >= MIN_Y && pos.getY() <= f.area.maxY());
            if (!owned) requireAir(pos);
        }
    }

    private void requireNoEntities(Fixture f) {
        LabSupport.check(level.getEntities((Entity) null, f.effects).isEmpty(), "unexpected entity in restart effect volume; nothing will be discarded");
    }

    @SuppressWarnings("unchecked")
    private void trackEntities() throws ReflectiveOperationException {
        // PoweredChecks' console visibility, restored even on failure; no machine fields are written.
        Field manager = ServerLevel.class.getDeclaredField("entityManager");
        manager.setAccessible(true);
        entityManager = (PersistentEntitySectionManager<Entity>) manager.get(level);
        Field status = PersistentEntitySectionManager.class.getDeclaredField("chunkVisibility");
        status.setAccessible(true);
        var current = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<Visibility>) status.get(entityManager);
        for (Fixture f : FIXTURES) {
            for (int x = Math.floorDiv((int) f.effects.minX, 16); x <= Math.floorDiv((int) f.effects.maxX - 1, 16); x++) {
                for (int z = Math.floorDiv((int) f.effects.minZ, 16); z <= Math.floorDiv((int) f.effects.maxZ - 1, 16); z++) {
                    ChunkPos chunk = new ChunkPos(x, z);
                    visibility.putIfAbsent(chunk, current.getOrDefault(chunk.toLong(), Visibility.HIDDEN));
                    entityManager.updateChunkStatus(chunk, Visibility.TRACKED);
                }
            }
        }
    }

    private String required(String key) {
        String value = record.getProperty(key);
        LabSupport.check(value != null && !value.isBlank(), "missing restart manifest field: " + key);
        return value;
    }

    private static String process() {
        return ProcessHandle.current().pid() + "@" + ProcessHandle.current().info().startInstant().orElseThrow();
    }

    private void phase(String value) throws Exception {
        record.setProperty("phase", value);
        writeRecord();
    }

    private void writeRecord() throws Exception {
        Path temp = Files.createTempFile(world, "quarryguard-restart-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                record.store(Channels.newOutputStream(channel), "Owned disposable restart fixture; never infer ownership from an old manifest");
                channel.force(true);
            }
            // No truncate-in-place fallback: unsupported atomic replacement aborts safely.
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
