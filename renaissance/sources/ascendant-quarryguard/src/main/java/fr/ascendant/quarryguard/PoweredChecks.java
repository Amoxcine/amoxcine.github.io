package fr.ascendant.quarryguard;

import com.yogpc.qp.PlatformAccess;
import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.MachineStorage;
import com.yogpc.qp.machine.PowerEntity;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;

/** Explicit, destructive, powered integration checks for a disposable lab world. */
public final class PoweredChecks {
    private static final Path LAB = Path.of(
            "C:\\Users\\avets\\.codex\\.chatgpt-projects\\g-p-6a767ae879f881919b1fc768a5c811f7\\quarryguard-lab");
    private static final BlockPos MACHINE = new BlockPos(228, 65, 223);
    private static final Area AREA = new Area(224, 65, 224, 232, 69, 232, Direction.NORTH);
    // Includes QuarryPlus' +/-5 collection query around every interior column.
    private static final AABB EFFECTS = new AABB(218, 58, 218, 239, 75, 239);
    private static final ChunkDimPos PROTECTED = new ChunkDimPos(
            net.minecraft.world.level.Level.OVERWORLD, 14, 14);
    private static final ChunkDimPos NEIGHBOR = new ChunkDimPos(
            net.minecraft.world.level.Level.OVERWORLD, 13, 14);
    private static final String OWNER_KEY = "ascendant_quarryguard_owner";
    private static final int DEFAULT_MAX_TICKS = 2_000;
    private static final long DEFAULT_MAX_MILLIS = 3_000L;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final ClaimedChunkManagerImpl claims;
    private final TeamManagerImpl teams;
    private final CommandSourceStack console;
    private final int maxTicks;
    private final long maxNanos;
    private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
    private final List<ItemEntity> fixtureEntities = new ArrayList<>();
    private final List<String> passed = new ArrayList<>();
    private final Set<BlockPos> footprint = new java.util.LinkedHashSet<>();
    private final Map<ChunkPos, Visibility> originalVisibility = new LinkedHashMap<>();
    private PersistentEntitySectionManager<Entity> entityManager;
    private ServerPlayer owner;
    private ChunkTeamDataImpl hostile;
    private boolean ownsClaim;
    private boolean ownsNeighbor;
    private boolean fixtureActive;
    private int ticks;
    private int caseTicks;
    private long deadline;
    private String phase;
    private long spent;

    private PoweredChecks(MinecraftServer server) {
        this.server = server;
        level = server.overworld();
        claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        console = server.createCommandSourceStack().withSuppressedOutput();
        maxTicks = Integer.getInteger("ascendant.quarryguard.powered.maxTicks", DEFAULT_MAX_TICKS);
        long maxMillis = Long.getLong("ascendant.quarryguard.powered.maxMillis", DEFAULT_MAX_MILLIS);
        require(maxTicks >= 1 && maxTicks <= DEFAULT_MAX_TICKS, "powered tick budget must be 1..2000");
        require(maxMillis >= 250 && maxMillis <= DEFAULT_MAX_MILLIS,
                "powered time budget must be 250..3000 ms per case");
        maxNanos = maxMillis * 1_000_000L;
        footprint.add(MACHINE);
        footprint.add(MACHINE.below());
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(224, 64, 224), new BlockPos(232, 69, 232))) {
            footprint.add(pos.immutable());
        }
    }

    public static String run(MinecraftServer server) {
        verifyWorld(server);
        require(FTBChunksAPI.api().isManagerLoaded(), "FTB Chunks is not ready");
        require(!PlatformAccess.config().noEnergy(), "powered checks require native noEnergy=false");

        PoweredChecks test = new PoweredChecks(server);
        Throwable failure = null;
        try {
            test.prepare();
            test.exercise("quarry");
            test.exercise("adv_quarry");
        } catch (Throwable error) {
            failure = error;
            throw new IllegalStateException("QuarryGuard powered lab failed after " + test.passed + ": "
                    + error.getMessage(), error);
        } finally {
            try {
                test.cleanup();
            } catch (RuntimeException error) {
                if (failure != null) failure.addSuppressed(error);
                else throw error;
            } finally {
                test.restoreVisibility();
            }
        }
        return "PASS QuarryGuard powered native checks: " + String.join(", ", test.passed)
                + ". ticks=" + test.ticks + ", fixture restored; offline FTB test teams remain.";
    }

    private static void verifyWorld(MinecraftServer server) {
        try {
            Path actual = LabSupport.requireLab(server);
            Path lab = LAB.toRealPath();
            Path runtime = lab.resolve("runtime/quarryguard-lab-world");
            Path fullRuntime = lab.resolve("full-runtime/quarryguard-lab-world");
            require(actual.equals(runtime) || actual.equals(fullRuntime),
                    "refusing non-laboratory world: " + actual);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot verify exact laboratory world path", error);
        }
    }

    private void prepare() {
        for (int x = 13; x <= 14; x++) {
            for (int z = 13; z <= 14; z++) {
                require(claims.getChunk(new ChunkDimPos(level.dimension(), x, z)) == null,
                        "powered effect volume already claimed at " + x + "," + z);
            }
        }
        BlockPos min = BlockPos.containing(EFFECTS.minX, EFFECTS.minY, EFFECTS.minZ);
        BlockPos max = BlockPos.containing(EFFECTS.maxX - 1, EFFECTS.maxY - 1, EFFECTS.maxZ - 1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos key = pos.immutable();
            BlockState state = level.getBlockState(key);
            require(state.isAir() && level.getBlockEntity(key) == null,
                    "powered fixture effect volume must be empty: " + key);
            originals.put(key, state);
        }
        makeFixtureEntitiesVisible();
        require(level.getEntities((Entity) null, EFFECTS).isEmpty(), "powered fixture effect volume contains entities");
        owner = actor("QGPowerOwner");
        ServerPlayer enemy = actor("QGPowerEnemy");
        hostile = data(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        data(teams.getPlayerTeamForPlayerID(owner.getUUID()).orElseThrow());
        require(!claims.getBypassProtection(owner.getUUID()), "test owner unexpectedly has bypass");
        passed.add("exact lab path/empty effect volume/offline actors");
    }

    @SuppressWarnings("unchecked")
    private void makeFixtureEntitiesVisible() {
        // Console-driven native ticks have no nearby player to activate entity tracking.
        // Promote only the four fixture chunks; this is test setup, not a quarry bypass.
        try {
            Field managerField = ServerLevel.class.getDeclaredField("entityManager");
            managerField.setAccessible(true);
            entityManager = (PersistentEntitySectionManager<Entity>) managerField.get(level);
            Field visibilityField = PersistentEntitySectionManager.class.getDeclaredField("chunkVisibility");
            visibilityField.setAccessible(true);
            var visibility = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<Visibility>) visibilityField.get(entityManager);
            for (int x = 13; x <= 14; x++) {
                for (int z = 13; z <= 14; z++) {
                    ChunkPos chunk = new ChunkPos(x, z);
                    originalVisibility.put(chunk, visibility.getOrDefault(chunk.toLong(), Visibility.HIDDEN));
                    entityManager.updateChunkStatus(chunk, Visibility.TRACKED);
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("pinned entity tracking test API unavailable", error);
        }
    }

    private void restoreVisibility() {
        if (entityManager != null) originalVisibility.forEach(entityManager::updateChunkStatus);
        originalVisibility.clear();
    }

    private ServerPlayer actor(String name) {
        ServerPlayer player = LabSupport.actor(server, teams, name);
        require(!player.connection.getConnection().isConnected() && !player.isCreative(),
                "non-networked survival actor required");
        require(server.getPlayerList().getPlayer(player.getUUID()) == null, "actor must not be registered online");
        return player;
    }

    private ChunkTeamDataImpl data(Team team) {
        ChunkTeamDataImpl data = claims.getOrCreateData(team);
        data.setExtraClaimChunks(8);
        data.updateLimits();
        team.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        return data;
    }

    private void exercise(String id) throws Exception {
        deadline = System.nanoTime() + maxNanos;
        caseTicks = 0;
        spent = 0;
        phase = id + " setup";
        fixtureActive = true;
        buildLayer();
        PowerEntity machine = place(id);
        configure(machine, id.equals("adv_quarry"));
        machine.setEnergy(machine.getMaxEnergy(), false);
        require(machine.getEnergy() == machine.getMaxEnergy() && machine.getEnergy() > 0,
                id + " native PowerEntity energy injection failed");

        int frameBefore = frameCount();
        tickUntil(machine, () -> frameCount() > frameBefore,
                id + " did not start/build its native frame");
        require(state(machine).equals("MAKE_FRAME") && spent > 0, id + " frame cycle is not actively powered");
        int framePaused = frameCount();
        MachineSnapshot frameSnapshot = snapshot(machine);
        Map<BlockPos, BlockState> frameBlocks = protectedBlocks();
        claim();
        deniedTicks(machine, frameSnapshot, frameBlocks, 8, id + " hostile claim during frame");
        clearClaim();
        tickUntil(machine, () -> frameCount() > framePaused,
                id + " did not resume frame work after unclaim");
        passed.add(id + " powered frame pause/resume");

        require(claims.getChunk(NEIGHBOR) == null, "neighbor claim unexpectedly occupied");
        ownsNeighbor = true;
        require(hostile.claim(console, NEIGHBOR, false).isSuccess(), "native neighbor claim rejected");
        require(GuardHooks.mayWork(machine), "off-area neighbor claim stopped allowed mining");
        // Center is in the allowed chunk, but the item box overlaps the hostile one.
        ItemEntity boundary = spawnAt(Items.DIAMOND, 7, 224.05, 63.5, 225.5);
        require(boundary.getBoundingBox().minX < 224 && boundary.getX() >= 224,
                "item does not straddle claim boundary");
        require(!GuardHooks.mayCollect(machine, boundary), "boundary item was authorized");
        ItemEntity emeralds = spawn(Items.EMERALD, 3);
        int goldBefore = goldCount();
        require(goldBefore == 49, "frame unexpectedly touched the 7x7 mining layer");
        long energyBeforeMining = spent;
        tickUntil(machine,
                () -> goldCount() < goldBefore && stored(machine, Items.GOLD_BLOCK) > 0
                        && stored(machine, Items.EMERALD) == 3 && !emeralds.isAlive(),
                id + " did not natively mine, store drops and collect item entities");
        require(goldCount() > 0 && !state(machine).equals("FINISHED"), id + " cycle already finished before denial");
        require(spent > energyBeforeMining, id + " mining did not consume native energy");
        require(stored(machine, Items.GOLD_BLOCK) == 49 - goldCount(), id + " mined drops lost or duplicated");

        ItemEntity pearls = spawn(Items.ENDER_PEARL, 5);
        MachineSnapshot miningSnapshot = snapshot(machine);
        Map<BlockPos, BlockState> protectedSnapshot = protectedBlocks();
        int goldPaused = goldCount();
        claim();
        deniedTicks(machine, miningSnapshot, protectedSnapshot, 8, id + " hostile claim during mining");
        require(pearls.isAlive() && pearls.getItem().getCount() == 5
                        && stored(machine, Items.ENDER_PEARL) == 0,
                id + " collected/altered denied item entity");
        clearClaim();
        tickUntil(machine,
                () -> goldCount() < goldPaused && stored(machine, Items.ENDER_PEARL) == 5 && !pearls.isAlive(),
                id + " did not resume mining and collection after unclaim");
        require(stored(machine, Items.GOLD_BLOCK) == 49 - goldCount()
                && stored(machine, Items.EMERALD) == 3, id + " post-resume drops lost or duplicated");
        require(boundary.isAlive() && boundary.getItem().getCount() == 7
                && stored(machine, Items.DIAMOND) == 0, id + " collected hostile boundary item");
        verifyOutsideFixture();
        passed.add(id + " hostile boundary item preserved during allowed mining");
        passed.add(id + " powered mining/storage/collection pause/resume, ticks=" + caseTicks
                + ", mined=" + (49 - goldCount()) + ", spentInternal=" + spent);
        cleanup();
    }

    private void buildLayer() {
        require(level.getBlockState(MACHINE).isAir(), "machine fixture was not cleaned");
        for (int x = AREA.minX() + 1; x < AREA.maxX(); x++) {
            for (int z = AREA.minZ() + 1; z < AREA.maxZ(); z++) {
                require(level.setBlock(new BlockPos(x, 64, z), Blocks.GOLD_BLOCK.defaultBlockState(), 3),
                        "could not create mining layer");
            }
        }
    }

    private PowerEntity place(String id) {
        ItemStack old = owner.getItemInHand(InteractionHand.MAIN_HAND);
        try {
            BlockEntity placed = LabSupport.place(level, owner, MACHINE, id);
            require(placed instanceof PowerEntity && GuardHooks.isQuarry(placed), id + " machine entity missing");
            require(placed.getPersistentData().hasUUID(OWNER_KEY)
                            && placed.getPersistentData().getUUID(OWNER_KEY).equals(owner.getUUID()),
                    id + " trusted owner missing after native placement");
            return (PowerEntity) placed;
        } finally {
            owner.setItemInHand(InteractionHand.MAIN_HAND, old);
        }
    }

    private void configure(PowerEntity machine, boolean advanced) {
        require(state(machine).equals("WAITING"), "native placement must enter WAITING before fixture configuration");
        if (advanced) {
            CompoundTag tag = machine.saveWithFullMetadata(level.registryAccess());
            CompoundTag config = new CompoundTag();
            config.putBoolean("startImmediately", true);
            config.putBoolean("placeAreaFrame", true);
            config.putBoolean("chunkByChunk", false);
            tag.put("workConfig", config);
            machine.loadWithComponents(tag, level.registryAccess());
        }
        if (machine instanceof QuarryEntity quarry) {
            quarry.setArea(AREA);
            quarry.digMinY.setMinY(64);
        } else if (machine instanceof AdvQuarryEntity quarry) {
            quarry.setArea(AREA);
            quarry.digMinY.setMinY(64);
        } else {
            throw new IllegalStateException("unexpected powered machine type " + machine.getClass());
        }
        require(area(machine).equals(AREA), "native bounded Area configuration was rejected");
        require(state(machine).equals("WAITING"), "machine must remain WAITING before powered ticks");
    }

    private void deniedTicks(PowerEntity machine, MachineSnapshot expected,
                             Map<BlockPos, BlockState> blocks, int count, String description) {
        phase = description;
        require(machine.getEnergy() > 0, description + " denial must be tested with stored energy");
        Map<ItemEntity, CompoundTag> entities = new LinkedHashMap<>();
        for (ItemEntity item : fixtureEntities) {
            if (item.isAlive()) entities.put(item, item.saveWithoutId(new CompoundTag()));
        }
        for (int i = 0; i < count; i++) {
            nativeTick(machine, false);
            require(snapshot(machine).equals(expected), description + " progressed energy/inventory/state at tick " + i);
            require(protectedBlocks().equals(blocks), description + " changed a protected block at tick " + i);
            for (var entry : entities.entrySet()) {
                require(entry.getKey().isAlive()
                                && entry.getValue().equals(entry.getKey().saveWithoutId(new CompoundTag())),
                        description + " altered a protected item entity at tick " + i);
            }
        }
        verifyOutsideFixture();
    }

    private void tickUntil(PowerEntity machine, Condition done, String failure) {
        phase = failure;
        while (!done.get()) {
            require(!state(machine).equals("FINISHED"), failure + " (machine finished prematurely; goldLeft=" + goldCount()
                + ", storedGold=" + stored(machine, Items.GOLD_BLOCK) + ", emerald=" + stored(machine, Items.EMERALD)
                + ", visibleItems=" + level.getEntitiesOfClass(ItemEntity.class, EFFECTS).size() + ", ticks=" + caseTicks + ")");
            nativeTick(machine, true);
        }
    }

    private void nativeTick(PowerEntity machine, boolean supply) {
        checkBudget();
        require(level.getBlockEntity(MACHINE) == machine, "native machine was replaced during test");
        require(AREA.equals(area(machine)), "machine escaped bounded fixture area");
        int minY = machine instanceof QuarryEntity quarry ? quarry.digMinY.getMinY(level)
                : ((AdvQuarryEntity) machine).digMinY.getMinY(level);
        require(minY == 64, "native minimum dig height escaped fixture");
        require(((Set<?>) field(machine, "modules")).isEmpty(), "fixture must not use repeat/pump/converter modules");
        Object target = field(machine, "targetPos");
        require(target == null || footprint.contains(target), "native target escaped fixture: " + target);
        // Test-only supply; never refill or otherwise repair state during denied ticks.
        if (supply) machine.setEnergy(machine.getMaxEnergy(), false);
        long before = machine.getEnergy();
        caseTicks++;
        ticks++;
        LabSupport.tick(machine);
        if (supply) spent += Math.max(0, before - machine.getEnergy());
        require(System.nanoTime() <= deadline, phase + " (time budget exceeded in native ticker, ticks=" + caseTicks + ")");
    }

    private void checkBudget() {
        require(caseTicks < maxTicks, phase + " (per-case native tick budget exhausted: " + caseTicks + "/" + maxTicks + ")");
        require(System.nanoTime() <= deadline, phase + " (per-case time budget exhausted, ticks=" + caseTicks + ")");
    }

    private ItemEntity spawn(Item item, int count) {
        return spawnAt(item, count, 228.5, 63.5, 228.5);
    }

    private ItemEntity spawnAt(Item item, int count, double x, double y, double z) {
        ItemEntity entity = new ItemEntity(level, x, y, z, new ItemStack(item, count));
        require(level.addFreshEntity(entity), "could not add fixture item entity " + item);
        fixtureEntities.add(entity);
        require(level.getEntitiesOfClass(ItemEntity.class, EFFECTS).contains(entity), "fixture item not visible to native entity queries");
        return entity;
    }

    private MachineSnapshot snapshot(PowerEntity machine) {
        CompoundTag tag = machine.saveWithFullMetadata(level.registryAccess());
        return new MachineSnapshot(machine.getEnergy(), tag.getString("state"), tag.get("storage").copy(), tag.copy());
    }

    private long stored(PowerEntity machine, Item item) {
        try {
            Class<?> declaring = machine instanceof QuarryEntity ? QuarryEntity.class : AdvQuarryEntity.class;
            Field storage = declaring.getDeclaredField("storage");
            storage.setAccessible(true);
            return ((MachineStorage) storage.get(machine)).getItemCount(item, DataComponentPatch.EMPTY);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("pinned QuarryPlus storage field unavailable", error);
        }
    }

    private static Object field(PowerEntity machine, String name) {
        try {
            Class<?> declaring = machine instanceof QuarryEntity ? QuarryEntity.class : AdvQuarryEntity.class;
            Field field = declaring.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(machine);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("pinned QuarryPlus field unavailable: " + name, error);
        }
    }

    private int frameCount() {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(AREA.minX(), 65, AREA.minZ()), new BlockPos(AREA.maxX(), AREA.maxY(), AREA.maxZ()))) {
            if (!level.getBlockState(pos).isAir() && !pos.equals(MACHINE)) result++;
        }
        return result;
    }

    private int goldCount() {
        int result = 0;
        for (int x = AREA.minX(); x <= AREA.maxX(); x++) {
            for (int z = AREA.minZ(); z <= AREA.maxZ(); z++) {
                if (level.getBlockState(new BlockPos(x, 64, z)).is(Blocks.GOLD_BLOCK)) result++;
            }
        }
        return result;
    }

    private Map<BlockPos, BlockState> protectedBlocks() {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(AREA.minX(), 64, AREA.minZ()), new BlockPos(AREA.maxX(), AREA.maxY(), AREA.maxZ()))) {
            result.put(pos.immutable(), level.getBlockState(pos));
        }
        return result;
    }

    private void claim() {
        require(claims.getChunk(PROTECTED) == null, "protected fixture claim unexpectedly occupied");
        ownsClaim = true;
        require(hostile.claim(console, PROTECTED, false).isSuccess(), "native hostile claim rejected");
        require(claims.getChunk(PROTECTED) != null, "native hostile claim absent");
    }

    private void clearClaim() {
        if (claims.getChunk(PROTECTED) == null) { ownsClaim = false; return; }
        require(ownsClaim, "refusing to remove a non-fixture claim");
        require(hostile.unclaim(console, PROTECTED, false, true).isSuccess(), "native unclaim rejected");
        require(claims.getChunk(PROTECTED) == null, "native unclaim left stale entry");
        ownsClaim = false;
    }

    private void removeFixtureMachine() {
        for (ItemEntity entity : fixtureEntities) entity.discard();
        fixtureEntities.clear();
        level.setBlock(MACHINE, Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos pos : protectedBlocks().keySet()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(MACHINE.below(), Blocks.AIR.defaultBlockState(), 3);
        GuardHooks.afterPlace();
    }

    private void verifyOutsideFixture() {
        Map<BlockPos, BlockState> fixture = protectedBlocks();
        fixture.put(MACHINE, level.getBlockState(MACHINE));
        fixture.put(MACHINE.below(), level.getBlockState(MACHINE.below()));
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            if (!fixture.containsKey(entry.getKey())) {
                require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                        "native work changed block outside fixture: " + entry.getKey());
            }
        }
    }

    private void cleanup() {
        if (!fixtureActive) return;
        if (owner != null) claims.setBypassProtection(owner.getUUID(), false);
        if (ownsClaim) claims.unregisterClaim(PROTECTED);
        ownsClaim = false;
        if (ownsNeighbor) claims.unregisterClaim(NEIGHBOR);
        ownsNeighbor = false;
        for (ItemEntity entity : fixtureEntities) entity.discard();
        BlockEntity machine = level.getBlockEntity(MACHINE);
        if (machine instanceof QuarryEntity || machine instanceof AdvQuarryEntity) {
            level.setBlock(MACHINE, Blocks.AIR.defaultBlockState(), 3);
        }
        for (BlockPos pos : footprint) {
            BlockState original = originals.get(pos);
            require(original != null, "cleanup position was not verified during setup");
            level.setBlock(pos, original, 3);
        }
        // This volume was empty before this synchronous test, including all item entities.
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, EFFECTS)) drop.discard();
        fixtureEntities.clear();
        fixtureActive = false;
        GuardHooks.afterPlace();
    }

    private static Area area(PowerEntity machine) {
        if (machine instanceof QuarryEntity quarry) return quarry.getArea();
        if (machine instanceof AdvQuarryEntity quarry) return quarry.getArea();
        return null;
    }

    private static String state(PowerEntity machine) {
        var registries = machine.getLevel().registryAccess();
        if (machine instanceof QuarryEntity quarry) {
            return quarry.toClientTag(new CompoundTag(), registries).getString("state");
        }
        if (machine instanceof AdvQuarryEntity quarry) {
            return quarry.toClientTag(new CompoundTag(), registries).getString("state");
        }
        throw new IllegalStateException("unexpected powered machine type " + machine.getClass());
    }

    private record MachineSnapshot(long energy, String state, net.minecraft.nbt.Tag storage, CompoundTag all) { }

    @FunctionalInterface
    private interface Condition { boolean get(); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
