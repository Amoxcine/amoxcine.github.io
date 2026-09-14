package fr.ascendant.quarryguard;

import com.yogpc.qp.PlatformAccess;
import com.yogpc.qp.QuarryDataComponents;
import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.MachineStorage;
import com.yogpc.qp.machine.PowerEntity;
import com.yogpc.qp.machine.QpItem;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.misc.FrameBlock;
import com.yogpc.qp.machine.module.ModuleInventory;
import com.yogpc.qp.machine.module.QuarryModule;
import com.yogpc.qp.machine.module.QuarryModuleProvider;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/** Synchronous native QuarryPlus 21.1.162 module checks; no runtime hook replacement. */
public final class ModuleChecks {
    private static final Path LAB = Path.of(
            "C:\\Users\\avets\\.codex\\.chatgpt-projects\\g-p-6a767ae879f881919b1fc768a5c811f7\\quarryguard-lab");
    private static final String OWNER_KEY = "ascendant_quarryguard_owner";
    private static final int MIN_Y = 64, MAX_TICKS = 2_000, DENIED_TICKS = 25;
    private static final long MAX_NANOS = 3_000_000_000L, SOURCE_UNITS = 81_000L;
    private static final List<Fixture> FIXTURES = fixtures();
    // Both breakBlockModuleOverride implementations require minBuildHeight < y < minBuildHeight + 5
    // in the overworld. Bedrock at y=64 is SKIPPED, not a useful module test. Never dig natural bedrock.
    private static final String EXCLUSIONS = "Bedrock utility excluded: y=64 is outside the native bottom band; "
            + "Adv Quarry modules excluded from final PASS: Pump is rejected natively and the XP case has a "
            + "multi-step item-accounting path not modeled safely by this synchronous oracle; "
            + "no deep terrain/Nether, ore-generated XP, flowing/waterlogged fluids, filter GUI, "
            + "module combinations, transports, restart or mid-callback claim mutation tested";

    private final MinecraftServer server;
    private final ServerLevel level;
    private final ClaimedChunkManagerImpl claims;
    private final TeamManagerImpl teams;
    private final CommandSourceStack console;
    private final Map<ChunkPos, Visibility> visibility = new LinkedHashMap<>();
    private final Map<Class<?>, Access> access = new LinkedHashMap<>();
    private final List<ExperienceOrb> ownedOrbs = new ArrayList<>();
    private final List<String> passed = new ArrayList<>();
    private PersistentEntitySectionManager<Entity> entityManager;
    private ServerPlayer owner;
    private ChunkTeamDataImpl hostile;
    private Fixture current;
    private PowerEntity machine;
    private ModuleInventory inventory;
    private MachineStorage storage;
    private boolean active, placementAttempted, claimAttempted;
    private int caseTicks, totalTicks, injectedXp;
    private long deadline, spent, usefulSpent;
    private String phase = "preflight";

    private record Fixture(String id, String module, BlockPos pos, Area area,
                           Set<BlockPos> owned, Map<BlockPos, BlockState> layer, AABB effects) { }
    private record Access(Field inventory, Field modules, Field storage, Field target) {
        static Access of(Class<?> type) throws ReflectiveOperationException {
            return new Access(field(type, "moduleInventory"), field(type, "modules"),
                    field(type, "storage"), field(type, "targetPos"));
        }
        private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
            Field result = type.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        }
    }

    private ModuleChecks(MinecraftServer server) throws ReflectiveOperationException {
        this.server = server;
        level = server.overworld();
        claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        console = server.createCommandSourceStack().withSuppressedOutput();
        access.put(QuarryEntity.class, Access.of(QuarryEntity.class));
        access.put(AdvQuarryEntity.class, Access.of(AdvQuarryEntity.class));
    }

    public static String run(MinecraftServer server) {
        ModuleChecks test;
        try {
            Path actual = LabSupport.requireLab(server);
            Path lab = LAB.toRealPath();
            check(actual.equals(lab.resolve("runtime/quarryguard-lab-world"))
                    || actual.equals(lab.resolve("full-runtime/quarryguard-lab-world")), "exact lab path required");
            check(FTBChunksAPI.api().isManagerLoaded(), "FTB Chunks not ready");
            check(!PlatformAccess.config().noEnergy(), "native noEnergy=false required");
            test = new ModuleChecks(server);
        } catch (Exception error) {
            throw new IllegalStateException("Module checks refused before fixture mutation", error);
        }
        Throwable failure = null;
        try {
            test.prepare();
            for (Fixture fixture : FIXTURES) test.exercise(fixture);
        } catch (Throwable error) {
            failure = error;
            throw new IllegalStateException("Module checks failed at " + test.phase + ", ticks=" + test.caseTicks
                    + ", completed=" + test.passed + ": " + error.getMessage(), error);
        } finally {
            try {
                test.cleanup();
            } catch (Exception error) {
                if (failure != null) failure.addSuppressed(error);
                else throw new IllegalStateException("Module fixture cleanup incomplete", error);
            } finally {
                if (test.entityManager != null) test.visibility.forEach(test.entityManager::updateChunkStatus);
            }
        }
        return "PASS native module subset: " + String.join("; ", test.passed) + "; totalTicks=" + test.totalTicks
                + "; owned fixtures cleaned, visibility restored, offline FTB teams retained. EXCLUDED: " + EXCLUSIONS;
    }

    private static List<Fixture> fixtures() {
        List<Fixture> result = new ArrayList<>();
        List<String> modules = List.of("pump_module", "exp_module", "filter_module");
        for (int m = 0; m < modules.size(); m++) {
            for (int type = 0; type < 2; type++) {
                // Keep the final claim to the normal quarry subset actually covered by exact accounting.
                if (type == 1) continue;
                int x = 5600 + 64 * type, z = 5600 + 64 * m;
                Area area = new Area(x, 65, z, x + 8, 69, z + 8, Direction.NORTH);
                BlockPos pos = new BlockPos(x + 4, 65, z - 1);
                Set<BlockPos> owned = new LinkedHashSet<>();
                owned.add(pos);
                owned.add(pos.below());
                for (BlockPos p : BlockPos.betweenClosed(x, 63, z, x + 8, 69, z + 8)) owned.add(p.immutable());
                Map<BlockPos, BlockState> layer = new LinkedHashMap<>();
                for (int dx = 1; dx < 8; dx++) {
                    for (int dz = 1; dz < 8; dz++) {
                        Block block = Blocks.GOLD_BLOCK;
                        // Isolated sources prevent one native flood-fill from finishing the entire pump case.
                        if (m == 0 && dx % 2 == 1 && dz % 2 == 1) block = Blocks.WATER;
                        if (m == 2 && (dx + dz) % 2 == 1) block = Blocks.IRON_BLOCK;
                        layer.put(new BlockPos(x + dx, MIN_Y, z + dz), block.defaultBlockState());
                    }
                }
                result.add(new Fixture(type == 0 ? "quarry" : "adv_quarry", modules.get(m), pos, area,
                        Set.copyOf(owned), Map.copyOf(layer), new AABB(x - 6, 58, z - 6, x + 15, 75, z + 15)));
            }
        }
        return List.copyOf(result);
    }

    private void prepare() throws Exception {
        // Preflight every volume before creating teams, installing modules, or placing anything.
        for (Fixture f : FIXTURES) {
            requireClaims(f, false);
            ChunkPos center = new ChunkPos(f.pos);
            for (int x = center.x - 1; x <= center.x + 1; x++) {
                for (int z = center.z - 1; z <= center.z + 1; z++) {
                    check(claims.getChunk(new ChunkDimPos(level.dimension(), x, z)) == null,
                            "default placement area already claimed");
                }
            }
            for (BlockPos p : effectPositions(f)) requireAir(p);
            Item module = moduleItem(f.module);
            check(module instanceof QpItem qp && qp.isEnabled() && module instanceof QuarryModuleProvider.Item,
                    "native module missing/disabled: " + f.module);
        }
        trackEntities();
        for (Fixture f : FIXTURES) check(level.getEntities((Entity) null, f.effects).isEmpty(), "fixture contains entities");
        owner = LabSupport.actor(server, teams, "QGModuleOwner");
        ServerPlayer enemy = LabSupport.actor(server, teams, "QGModuleEnemy");
        for (ServerPlayer actor : List.of(owner, enemy)) {
            check(!actor.isCreative() && !actor.connection.getConnection().isConnected()
                    && server.getPlayerList().getPlayer(actor.getUUID()) == null, "offline survival actor required");
        }
        check(!claims.getBypassProtection(owner.getUUID()), "owner unexpectedly has protection bypass");
        claims.getOrCreateData(teams.getPlayerTeamForPlayerID(owner.getUUID()).orElseThrow());
        hostile = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        hostile.setExtraClaimChunks(8);
        hostile.updateLimits();
        hostile.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
    }

    private void exercise(Fixture f) throws Exception {
        current = f;
        phase = f.id + "/" + f.module + " setup";
        requireClaims(f, false);
        for (BlockPos p : effectPositions(f)) requireAir(p);
        check(level.getEntities((Entity) null, f.effects).isEmpty(), "fixture no longer empty");
        active = true;
        caseTicks = injectedXp = 0;
        spent = usefulSpent = 0;
        deadline = System.nanoTime() + MAX_NANOS;
        if (pump()) {
            for (int x = f.area.minX(); x <= f.area.maxX(); x++) {
                for (int z = f.area.minZ(); z <= f.area.maxZ(); z++) {
                    put(new BlockPos(x, 63, z), Blocks.GLASS.defaultBlockState());
                    if (x == f.area.minX() || x == f.area.maxX() || z == f.area.minZ() || z == f.area.maxZ()) {
                        put(new BlockPos(x, 64, z), Blocks.GLASS.defaultBlockState());
                    }
                }
            }
        }
        for (var entry : f.layer.entrySet()) put(entry.getKey(), entry.getValue());
        ItemStack oldHand = owner.getItemInHand(InteractionHand.MAIN_HAND);
        placementAttempted = true;
        try {
            machine = (PowerEntity) LabSupport.place(level, owner, f.pos, f.id);
        } finally {
            owner.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        inventory = (ModuleInventory) fields().inventory.get(machine);
        storage = (MachineStorage) fields().storage.get(machine);
        configure();
        check(inventory.isEmpty(), "new native module inventory not empty");
        ItemStack module = new ItemStack(moduleItem(f.module), 1);
        if (filter()) module.set(QuarryDataComponents.ITEM_KEY_LIST_COMPONENT,
                List.of(new MachineStorage.ItemKey(Items.GOLD_BLOCK, DataComponentPatch.EMPTY)));
        check(GuardHooks.mayConfigure(owner, machine) && inventory.canPlaceItem(0, module), "native module insertion rejected");
        // The real container invokes its own onChanged/updateModules callback. Never write the modules set.
        inventory.setItem(0, module);
        requireMachine();
        verifyAccounting();
        machine.setEnergy(machine.getMaxEnergy(), false);
        check(machine.getEnergy() > 0 && machine.getEnergy() == machine.getMaxEnergy(), "initial native energy supply failed");
        ExperienceOrb first = xp() ? spawnOrb(7) : null;
        until(() -> useful() && (!xp() || !first.isAlive()), "first useful module effect");
        verifyAccounting();
        check(usefulSpent > 0 && machine.getEnergy() > 0, "module effect not witnessed with native energy consumption");
        requireRemainingWork();
        int goldBefore = mined(Blocks.GOLD_BLOCK), ironBefore = mined(Blocks.IRON_BLOCK);
        long fluidBefore = water(), workEnergyBefore = usefulSpent;
        int xpBefore = heldXp();
        ExperienceOrb pending = xp() ? spawnOrb(11) : null;
        verifyEffects();
        CompoundTag paused = tag();
        Map<BlockPos, BlockState> terrain = blocks();
        Map<UUID, CompoundTag> entities = entities();
        long energy = machine.getEnergy();
        claimAttempted = true;
        check(hostile.claim(console, claimPos(f), false).isSuccess(), "native hostile claim failed");
        requireClaims(f, true);
        phase = f.id + "/" + f.module + " hostile claim";
        for (int i = 0; i < DENIED_TICKS; i++) {
            check(!GuardHooks.mayWork(machine), "guard did not reject hostile area claim");
            nativeTick();
            check(energy == machine.getEnergy() && paused.equals(tag()), "denied tick changed energy/NBT/state/target/storage/modules: " + i);
            check(terrain.equals(blocks()) && entities.equals(entities()), "denied tick changed terrain/fluid/entities: " + i);
            verifyAccounting();
            verifyEffects();
        }
        clearClaim();
        check(GuardHooks.mayWork(machine), "guard did not restore authority after native unclaim");
        // No refill, reload, target/state/iterator write, module change or setArea after the checkpoint.
        until(() -> pump() ? water() > fluidBefore
                : xp() ? heldXp() == xpBefore + 11 && !pending.isAlive() && mined(Blocks.GOLD_BLOCK) > goldBefore
                : mined(Blocks.GOLD_BLOCK) > goldBefore && mined(Blocks.IRON_BLOCK) > ironBefore,
                "useful module effect after unclaim");
        check(usefulSpent > workEnergyBefore && machine.getEnergy() < energy, "resumed effect consumed no native energy");
        verifyAccounting();
        verifyEffects();
        String evidence = f.id + "/" + f.module + " useful/pause(" + DENIED_TICKS + ")/resume; ticks=" + caseTicks
                + ", goldMined=" + mined(Blocks.GOLD_BLOCK) + ", ironMined=" + mined(Blocks.IRON_BLOCK)
                + ", waterUnits=" + water() + ", xp=" + heldXp() + ", spentInternal=" + spent;
        cleanup();
        passed.add(evidence);
    }

    private void configure() {
        check("WAITING".equals(tag().getString("state")), "native placement must be WAITING");
        if (machine instanceof AdvQuarryEntity) {
            CompoundTag initial = tag(), config = new CompoundTag();
            config.putBoolean("startImmediately", true);
            config.putBoolean("placeAreaFrame", true);
            config.putBoolean("chunkByChunk", false);
            initial.put("workConfig", config);
            machine.loadWithComponents(initial, level.registryAccess());
        }
        if (machine instanceof QuarryEntity quarry) {
            quarry.setArea(current.area);
            quarry.digMinY.setMinY(MIN_Y);
        } else if (machine instanceof AdvQuarryEntity quarry) {
            quarry.setArea(current.area);
            quarry.digMinY.setMinY(MIN_Y);
        } else throw new IllegalStateException("unexpected quarry type");
        check("WAITING".equals(tag().getString("state")), "configuration changed working state");
    }

    private void until(BooleanSupplier done, String description) throws Exception {
        phase = current.id + "/" + current.module + " " + description;
        while (!done.getAsBoolean()) {
            check(!"FINISHED".equals(tag().getString("state")), phase + " finished prematurely");
            check(GuardHooks.mayWork(machine), "allowed fixture unexpectedly denied");
            nativeTick();
        }
    }

    private void nativeTick() throws Exception {
        check(caseTicks < MAX_TICKS && System.nanoTime() <= deadline,
                phase + " bounded tick/time budget exhausted; state=" + tag().getString("state"));
        requireMachine();
        long before = machine.getEnergy(), effectBefore = effectCount();
        caseTicks++;
        totalTicks++;
        LabSupport.tick(machine);
        long after = machine.getEnergy();
        check(after >= 0 && after <= before, "native energy unexpectedly increased/negative");
        spent += before - after;
        if (effectCount() > effectBefore) usefulSpent += before - after;
        requireMachine();
        check(System.nanoTime() <= deadline, phase + " native ticker exceeded per-case time budget");
    }

    private boolean useful() {
        if (pump()) return water() > 0;
        if (xp()) return heldXp() == 7 && mined(Blocks.GOLD_BLOCK) > 0;
        return mined(Blocks.GOLD_BLOCK) > 0 && mined(Blocks.IRON_BLOCK) > 0;
    }

    private long effectCount() {
        return pump() ? water() : xp() ? heldXp() : mined(Blocks.GOLD_BLOCK);
    }

    private void requireRemainingWork() {
        check(!"FINISHED".equals(tag().getString("state")), "fixture finished before denial");
        if (pump()) check(mined(Blocks.WATER) < initialCount(Blocks.WATER), "no fluid left to protect/resume");
        else {
            check(mined(Blocks.GOLD_BLOCK) < initialCount(Blocks.GOLD_BLOCK) - 1, "too little gold left to resume");
            if (filter()) check(mined(Blocks.IRON_BLOCK) < initialCount(Blocks.IRON_BLOCK) - 1, "too little iron left to resume");
        }
    }

    private void requireMachine() throws ReflectiveOperationException {
        check(level.getBlockEntity(current.pos) == machine
                && (current.id.equals("quarry") ? machine instanceof QuarryEntity : machine instanceof AdvQuarryEntity), "fixture machine replaced");
        check(machine.getPersistentData().hasUUID(OWNER_KEY)
                && machine.getPersistentData().getUUID(OWNER_KEY).equals(owner.getUUID())
                && !claims.getBypassProtection(owner.getUUID()), "trusted owner changed/bypass enabled");
        check(current.area.equals(LabSupport.area(machine)), "native area escaped fixture");
        int min = machine instanceof QuarryEntity quarry ? quarry.digMinY.getMinY(level)
                : ((AdvQuarryEntity) machine).digMinY.getMinY(level);
        check(min == MIN_Y, "native dig depth escaped fixture");
        Object target = fields().target.get(machine);
        check(target == null || current.owned.contains(target), "native target escaped fixture: " + target);
        Object value = fields().modules.get(machine);
        check(value instanceof Set<?> modules && modules.size() == 1
                && modules.iterator().next() instanceof QuarryModule module
                && module.moduleId().equals(ResourceLocation.parse("quarryplus:" + current.module)), "unexpected/ineffective native modules");
        check(inventory.getItem(0).is(moduleItem(current.module)) && inventory.getItem(0).getCount() == 1, "module item lost/duplicated");
        for (int i = 1; i < inventory.getContainerSize(); i++) check(inventory.getItem(i).isEmpty(), "extra module inserted");
        if (filter()) check(List.of(new MachineStorage.ItemKey(Items.GOLD_BLOCK, DataComponentPatch.EMPTY))
                .equals(inventory.getItem(0).get(QuarryDataComponents.ITEM_KEY_LIST_COMPONENT)), "native filter configuration changed");
    }

    private void verifyAccounting() {
        for (var entry : current.layer.entrySet()) {
            BlockState now = level.getBlockState(entry.getKey());
            check(now.equals(entry.getValue()) || now.equals(Blocks.AIR.defaultBlockState()), "unexpected layer replacement: " + entry.getKey());
        }
        check(stored(Items.GOLD_BLOCK) == (filter() ? 0 : mined(Blocks.GOLD_BLOCK)), "gold drops lost/duplicated or filter ineffective");
        check(stored(Items.IRON_BLOCK) == mined(Blocks.IRON_BLOCK), "unfiltered iron drops lost/duplicated");
        check(water() == SOURCE_UNITS * mined(Blocks.WATER), "source-water/storage conservation failed");
        check(storage.getFluidCount(Fluids.LAVA) == 0, "unexpected lava in storage");
        int liveXp = 0;
        for (ExperienceOrb orb : ownedOrbs) if (orb.isAlive()) liveXp += orb.getValue();
        check(heldXp() + liveXp == injectedXp, "XP lost/duplicated between module and owned orbs");
    }

    private int initialCount(Block block) {
        return (int) current.layer.values().stream().filter(state -> state.is(block)).count();
    }

    private int mined(Block block) {
        int count = 0;
        for (var entry : current.layer.entrySet()) {
            if (entry.getValue().is(block) && level.getBlockState(entry.getKey()).isAir()) count++;
        }
        return count;
    }

    private long stored(Item item) { return storage.getItemCount(item, DataComponentPatch.EMPTY); }
    private long water() { return storage.getFluidCount(Fluids.WATER); }
    private int heldXp() { return inventory.getItem(0).getOrDefault(QuarryDataComponents.HOLDING_EXP_COMPONENT, 0); }
    private boolean pump() { return current.module.equals("pump_module"); }
    private boolean xp() { return current.module.equals("exp_module"); }
    private boolean filter() { return current.module.equals("filter_module"); }
    private CompoundTag tag() { return machine.saveWithFullMetadata(level.registryAccess()).copy(); }
    private Access fields() { return access.get(machine instanceof QuarryEntity ? QuarryEntity.class : AdvQuarryEntity.class); }
    private static Item moduleItem(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse("quarryplus:" + id)); }

    private ExperienceOrb spawnOrb(int value) {
        ExperienceOrb orb = new ExperienceOrb(level, current.area.minX() + 4.5, 63.5, current.area.minZ() + 4.5, value);
        ownedOrbs.add(orb);
        check(level.addFreshEntity(orb), "fixture XP orb creation failed");
        injectedXp += value;
        check(level.getEntitiesOfClass(ExperienceOrb.class, current.effects).contains(orb), "fixture orb invisible to native queries");
        return orb;
    }

    private Map<BlockPos, BlockState> blocks() {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (BlockPos p : effectPositions(current)) result.put(p.immutable(), level.getBlockState(p));
        return result;
    }

    private Map<UUID, CompoundTag> entities() {
        Map<UUID, CompoundTag> result = new LinkedHashMap<>();
        for (Entity entity : level.getEntities((Entity) null, current.effects)) {
            check(ownedOrbs.contains(entity), "unexpected entity; never discard unowned entities");
            result.put(entity.getUUID(), entity.saveWithoutId(new CompoundTag()).copy());
        }
        for (ExperienceOrb orb : ownedOrbs) if (orb.isAlive()) check(result.containsKey(orb.getUUID()), "owned orb escaped effect volume");
        return result;
    }

    private void verifyEffects() {
        for (BlockPos p : effectPositions(current)) {
            if (!current.owned.contains(p)) requireAir(p);
            else check(p.equals(current.pos) || level.getBlockEntity(p) == null, "unexpected block entity in owned fixture");
        }
        entities();
    }

    private ChunkDimPos claimPos(Fixture f) {
        return new ChunkDimPos(level.dimension(), Math.floorDiv(f.area.minX(), 16), Math.floorDiv(f.area.minZ(), 16));
    }

    private void requireClaims(Fixture f, boolean expected) {
        for (ChunkPos chunk : effectChunks(f)) {
            ChunkDimPos p = new ChunkDimPos(level.dimension(), chunk.x, chunk.z);
            var claim = claims.getChunk(p);
            if (expected && p.equals(claimPos(f))) {
                check(claim != null && claim.getTeamData().getTeamId().equals(hostile.getTeamId()), "hostile claim missing/replaced");
            } else check(claim == null, "foreign claim in fixture effect volume: " + p);
        }
    }

    private void clearClaim() {
        if (!claimAttempted) return;
        var claim = claims.getChunk(claimPos(current));
        if (claim != null) {
            check(claim.getTeamData().getTeamId().equals(hostile.getTeamId()), "refusing to remove foreign claim");
            check(hostile.unclaim(console, claimPos(current), false, true).isSuccess(), "native unclaim failed");
            check(claims.getChunk(claimPos(current)) == null, "native unclaim left stale entry");
        }
        claimAttempted = false;
    }

    private void put(BlockPos pos, BlockState state) {
        check(active && current.owned.contains(pos), "unowned fixture write");
        requireAir(pos);
        check(level.setBlock(pos, state, 2), "fixture placement failed at " + pos);
    }

    private void cleanup() throws Exception {
        if (!active) return;
        // Stop/remove only the exact placed machine, even when a subsequent assertion failed.
        BlockEntity present = level.getBlockEntity(current.pos);
        if (machine == null && placementAttempted && present instanceof PowerEntity power
                && GuardHooks.isQuarry(power) && power.getPersistentData().hasUUID(OWNER_KEY)
                && power.getPersistentData().getUUID(OWNER_KEY).equals(owner.getUUID())) machine = power;
        if (machine != null) {
            check(present == machine, "cleanup refuses a replaced machine");
            machine.setEnergy(0, false);
            ModuleInventory modules = (ModuleInventory) fields().inventory.get(machine);
            // Native onRemove drops moduleInventory. Empty only our disposable inventory first.
            modules.clearContent();
            check(level.setBlock(current.pos, Blocks.AIR.defaultBlockState(), 2), "owned machine removal failed");
        } else requireAir(current.pos);
        for (ExperienceOrb orb : ownedOrbs) orb.discard();
        clearClaim();
        // Unexpected effects are evidence, not permission to erase surrounding terrain/entities.
        verifyEffects();
        for (BlockPos p : current.owned) {
            BlockState state = level.getBlockState(p);
            BlockState originalLayer = current.layer.get(p);
            boolean basin = pump() && (p.getY() == 63 || (p.getY() == 64
                    && (p.getX() == current.area.minX() || p.getX() == current.area.maxX()
                    || p.getZ() == current.area.minZ() || p.getZ() == current.area.maxZ())));
            check(level.getBlockEntity(p) == null && (state.isAir() || state.equals(originalLayer)
                    || (p.equals(current.pos.below()) && state.is(Blocks.STONE))
                    || (basin && state.is(Blocks.GLASS))
                    || (p.getY() >= current.area.minY() && state.getBlock() instanceof FrameBlock)),
                    "cleanup refuses unexplained owned-position content at " + p);
        }
        // Drain remaining owned sources before removing containment; never tick the world/fluid scheduler.
        for (BlockPos p : current.layer.keySet()) {
            if (level.getBlockState(p).is(Blocks.WATER)) check(level.setBlock(p, Blocks.AIR.defaultBlockState(), 2), "owned water cleanup failed");
        }
        for (BlockPos p : current.owned) {
            if (!level.getBlockState(p).isAir()) check(level.setBlock(p, Blocks.AIR.defaultBlockState(), 2), "owned cleanup failed at " + p);
        }
        for (BlockPos p : effectPositions(current)) requireAir(p);
        check(level.getEntities((Entity) null, current.effects).isEmpty(), "unexpected entities after owned cleanup");
        ownedOrbs.clear();
        active = placementAttempted = false;
        machine = null;
        inventory = null;
        storage = null;
        GuardHooks.afterPlace();
    }

    @SuppressWarnings("unchecked")
    private void trackEntities() throws ReflectiveOperationException {
        Field manager = ServerLevel.class.getDeclaredField("entityManager");
        manager.setAccessible(true);
        entityManager = (PersistentEntitySectionManager<Entity>) manager.get(level);
        Field status = PersistentEntitySectionManager.class.getDeclaredField("chunkVisibility");
        status.setAccessible(true);
        var old = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<Visibility>) status.get(entityManager);
        for (Fixture f : FIXTURES) {
            for (ChunkPos chunk : effectChunks(f)) {
                if (visibility.containsKey(chunk)) continue;
                visibility.put(chunk, old.getOrDefault(chunk.toLong(), Visibility.HIDDEN));
                entityManager.updateChunkStatus(chunk, Visibility.TRACKED);
            }
        }
    }

    private static List<ChunkPos> effectChunks(Fixture f) {
        List<ChunkPos> result = new ArrayList<>();
        for (int x = Math.floorDiv((int) f.effects.minX, 16); x <= Math.floorDiv((int) f.effects.maxX - 1, 16); x++) {
            for (int z = Math.floorDiv((int) f.effects.minZ, 16); z <= Math.floorDiv((int) f.effects.maxZ - 1, 16); z++) {
                result.add(new ChunkPos(x, z));
            }
        }
        return result;
    }

    private static Iterable<BlockPos> effectPositions(Fixture f) {
        return BlockPos.betweenClosed(BlockPos.containing(f.effects.minX, f.effects.minY, f.effects.minZ),
                BlockPos.containing(f.effects.maxX - 1, f.effects.maxY - 1, f.effects.maxZ - 1));
    }

    private void requireAir(BlockPos pos) {
        check(level.getBlockState(pos).equals(Blocks.AIR.defaultBlockState()) && level.getBlockEntity(pos) == null,
                "fixture effect volume not pristine air at " + pos);
    }

    private static void check(boolean condition, String message) { LabSupport.check(condition, message); }
}
