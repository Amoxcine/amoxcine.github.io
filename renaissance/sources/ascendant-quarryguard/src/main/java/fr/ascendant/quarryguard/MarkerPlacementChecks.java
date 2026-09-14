package fr.ascendant.quarryguard;

import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.MachineStorage;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.marker.FlexibleMarkerEntity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Native placement regression for a physical marker outside its selected area. */
public final class MarkerPlacementChecks {
    private static final BlockPos MACHINE = new BlockPos(6015, 64, 6008);
    private static final BlockPos MARKER = MACHINE.east();
    private static final BlockPos MIN = new BlockPos(6002, 64, 6002);
    private static final BlockPos MAX = new BlockPos(6012, 68, 6012);
    private static final Area AREA = new Area(MIN, MAX, Direction.NORTH);
    private static final AABB DROPS = new AABB(MACHINE).inflate(3.0);

    private MarkerPlacementChecks() { }

    public static String run(MinecraftServer server) throws Exception {
        LabSupport.requireLab(server);
        var claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var owner = LabSupport.actor(server, teams, "QGMarkerOwner");
        var enemy = LabSupport.actor(server, teams, "QGMarkerEnemy");
        var hostile = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        hostile.setExtraClaimChunks(1);
        hostile.updateLimits();
        hostile.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        LabSupport.check(!claims.getBypassProtection(owner.getUUID()) && !owner.getAbilities().instabuild,
            "marker actor must consume items and have no protection bypass");
        try (var tracking = new DropTracking(server.overworld())) {
            tracking.enable();
            for (String id : new String[]{"quarry", "adv_quarry"}) {
                exercise(server, claims, hostile, owner, id);
            }
        }
        return "2 native BlockItem.place refusals (normal/advanced): private neighboring physical marker, free selected area; "
            + "stack, marker NBT and drops intact; 2 native placements after unclaim, one stored marker each; bounded fixture restored";
    }

    private static void exercise(MinecraftServer server, ClaimedChunkManagerImpl claims,
                                 ChunkTeamDataImpl hostile, ServerPlayer owner, String id) throws Exception {
        ServerLevel level = server.overworld();
        var console = server.createCommandSourceStack().withSuppressedOutput();
        var machineChunk = new ChunkDimPos(level.dimension(), MACHINE.getX() >> 4, MACHINE.getZ() >> 4);
        var markerChunk = new ChunkDimPos(level.dimension(), MARKER.getX() >> 4, MARKER.getZ() >> 4);
        LabSupport.check(claims.getChunk(machineChunk) == null && claims.getChunk(markerChunk) == null,
            id + ": marker fixture claims occupied");
        LabSupport.check((MIN.getX() >> 4) == (MACHINE.getX() >> 4) && (MAX.getX() >> 4) == (MACHINE.getX() >> 4)
            && (MIN.getZ() >> 4) == (MACHINE.getZ() >> 4) && (MAX.getZ() >> 4) == (MACHINE.getZ() >> 4)
            && MARKER.getX() > MAX.getX() && !markerChunk.equals(machineChunk) && GuardHooks.validArea(AREA),
            id + ": selected area must be valid, wholly free and outside the marker claim");
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        for (BlockPos pos : List.of(MACHINE, MARKER, MACHINE.below(), MARKER.below())) {
            BlockState state = level.getBlockState(pos);
            LabSupport.check(state.isAir() && level.getBlockEntity(pos) == null, id + ": occupied marker fixture at " + pos);
            original.put(pos, state);
        }
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST}) {
            LabSupport.check(level.getBlockEntity(MACHINE.relative(direction)) == null, id + ": competing adjacent block entity");
        }
        LabSupport.check(level.getEntitiesOfClass(ItemEntity.class, DROPS).isEmpty(), id + ": fixture contains loose items");
        ItemStack previousHand = owner.getItemInHand(InteractionHand.MAIN_HAND);
        Throwable failure = null;
        try {
            var probe = new ItemEntity(level, MARKER.getX() + 0.5, MARKER.getY() + 0.5, MARKER.getZ() + 0.5, new ItemStack(Blocks.STONE));
            try {
                LabSupport.check(level.addFreshEntity(probe) && level.getEntitiesOfClass(ItemEntity.class, DROPS).contains(probe),
                    id + ": native drop query cannot observe fixture items");
            } finally { probe.discard(); }
            level.setBlock(MACHINE.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(MARKER.below(), Blocks.STONE.defaultBlockState(), 3);
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("quarryplus:flexible_marker"));
            LabSupport.check(block != Blocks.AIR, "missing flexible marker block");
            level.setBlock(MARKER, block.defaultBlockState(), 3);
            LabSupport.check(level.getBlockEntity(MARKER) instanceof FlexibleMarkerEntity, "missing native flexible marker entity");
            var marker = (FlexibleMarkerEntity) level.getBlockEntity(MARKER);
            CompoundTag setup = new CompoundTag();
            setup.put("min", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, MIN).getOrThrow());
            setup.put("max", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, MAX).getOrThrow());
            setup.put("direction", Direction.CODEC.encodeStart(NbtOps.INSTANCE, Direction.NORTH).getOrThrow());
            marker.fromClientTag(setup, level.registryAccess());
            marker.setChanged();
            var link = marker.getLink().orElseThrow();
            LabSupport.check(link.area().equals(AREA) && MarkerFootprint.removedPositions(link).equals(List.of(MARKER)),
                id + ": native flexible link does not describe the fixture");
            List<ItemStack> drops = link.drops().stream().map(ItemStack::copy).toList();
            LabSupport.check(drops.size() == 1 && ItemStack.matches(drops.getFirst(), new ItemStack(block)),
                id + ": expected one native marker drop");
            LabSupport.check(hostile.claim(console, markerChunk, false).isSuccess(), id + ": native private marker claim failed");
            LabSupport.check(claims.getChunk(markerChunk) != null
                && claims.getChunk(markerChunk).getTeamData().getTeamId().equals(hostile.getTeamId())
                && claims.getChunk(machineChunk) == null, id + ": wrong claim geometry");
            BlockState markerState = level.getBlockState(MARKER);
            CompoundTag markerData = marker.saveWithFullMetadata(level.registryAccess()).copy();
            LabSupport.check(level.getEntitiesOfClass(ItemEntity.class, DROPS).isEmpty(), id + ": setup created loose drops");

            // The refusal must go through the native item path, not a guard predicate.
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("quarryplus:" + id));
            LabSupport.check(item instanceof BlockItem, "missing quarry block item " + id);
            ItemStack stack = new ItemStack(item, 3);
            ItemStack beforeStack = stack.copy();
            owner.setPos(MACHINE.getX() + 0.5, MACHINE.getY(), MACHINE.getZ() - 2.5);
            owner.setYRot(0);
            owner.setXRot(0);
            owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(MACHINE), Direction.UP, MACHINE.below(), false);
            var context = new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, stack, hit);
            LabSupport.check(context.getClickedPos().equals(MACHINE), id + ": wrong native placement target");
            var result = ((BlockItem) item).place(context);
            LabSupport.check(!result.consumesAction(), id + ": native placement accepted a foreign private marker");
            LabSupport.check(ItemStack.matches(beforeStack, stack)
                && ItemStack.matches(beforeStack, owner.getItemInHand(InteractionHand.MAIN_HAND)), id + ": refused placement consumed or changed stack");
            LabSupport.check(level.getBlockState(MACHINE).isAir() && level.getBlockEntity(MACHINE) == null,
                id + ": refused placement left a machine");
            LabSupport.check(level.getBlockState(MACHINE.below()).is(Blocks.STONE)
                && level.getBlockState(MARKER.below()).is(Blocks.STONE), id + ": refused placement changed supports");
            LabSupport.check(level.getBlockEntity(MARKER) == marker && level.getBlockState(MARKER).equals(markerState)
                && markerData.equals(marker.saveWithFullMetadata(level.registryAccess())), id + ": refused placement changed marker");
            List<ItemStack> afterDrops = marker.getLink().orElseThrow().drops();
            LabSupport.check(afterDrops.size() == drops.size() && ItemStack.matches(afterDrops.getFirst(), drops.getFirst())
                && level.getEntitiesOfClass(ItemEntity.class, DROPS).isEmpty(), id + ": refused placement changed drops");

            LabSupport.check(hostile.unclaim(console, markerChunk, false, true).isSuccess()
                && claims.getChunk(markerChunk) == null, id + ": native marker unclaim failed");
            // LabSupport.place owns creation of the machine support on the accepted path.
            level.setBlock(MACHINE.below(), original.get(MACHINE.below()), 2);
            BlockEntity machine = LabSupport.place(level, owner, MACHINE, id);
            LabSupport.check(LabSupport.area(machine).equals(AREA), id + ": accepted placement did not use the flexible area");
            LabSupport.check(machine.getPersistentData().getUUID("ascendant_quarryguard_owner").equals(owner.getUUID()),
                id + ": accepted placement has wrong owner");
            LabSupport.check(level.getBlockState(MARKER).isAir() && level.getBlockEntity(MARKER) == null,
                id + ": accepted placement did not remove the marker");
            LabSupport.check(stored(machine, block.asItem()) == 1 && level.getEntitiesOfClass(ItemEntity.class, DROPS).isEmpty(),
                id + ": accepted placement did not store exactly one marker without loose drops");
        } catch (Exception | Error error) {
            failure = error;
            throw error;
        } finally {
            owner.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
            try {
                cleanup(level, claims, hostile, console, markerChunk, original);
            } catch (Exception | Error error) {
                if (failure == null) throw error;
                failure.addSuppressed(error);
            }
        }
    }

    private static long stored(BlockEntity machine, Item item) throws ReflectiveOperationException {
        Class<?> type = machine instanceof QuarryEntity ? QuarryEntity.class : AdvQuarryEntity.class;
        Field field = type.getDeclaredField("storage");
        field.setAccessible(true);
        return ((MachineStorage) field.get(machine)).getItemCount(item, DataComponentPatch.EMPTY);
    }

    private static final class DropTracking implements AutoCloseable {
        private final PersistentEntitySectionManager<Entity> manager;
        private final Map<ChunkPos, Visibility> original = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        private DropTracking(ServerLevel level) throws ReflectiveOperationException {
            Field managerField = ServerLevel.class.getDeclaredField("entityManager");
            managerField.setAccessible(true);
            manager = (PersistentEntitySectionManager<Entity>) managerField.get(level);
            Field visibilityField = PersistentEntitySectionManager.class.getDeclaredField("chunkVisibility");
            visibilityField.setAccessible(true);
            var visibility = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<Visibility>) visibilityField.get(manager);
            for (ChunkPos chunk : List.of(new ChunkPos(MACHINE), new ChunkPos(MARKER))) {
                original.put(chunk, visibility.getOrDefault(chunk.toLong(), Visibility.HIDDEN));
            }
        }

        private void enable() {
            // No nearby client activates the two chunks in this synchronous lab command.
            original.keySet().forEach(chunk -> manager.updateChunkStatus(chunk, Visibility.TRACKED));
        }

        @Override public void close() {
            original.forEach(manager::updateChunkStatus);
        }
    }

    private static void cleanup(ServerLevel level, ClaimedChunkManagerImpl claims, ChunkTeamDataImpl hostile,
                                CommandSourceStack console, ChunkDimPos markerChunk, Map<BlockPos, BlockState> original) {
        try {
            var claim = claims.getChunk(markerChunk);
            if (claim != null) {
                LabSupport.check(claim.getTeamData().getTeamId().equals(hostile.getTeamId()), "refusing to remove a non-fixture claim");
                LabSupport.check(hostile.unclaim(console, markerChunk, false, true).isSuccess(), "marker fixture unclaim cleanup failed");
            }
        } finally {
            try {
                for (var entry : original.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
                for (var entry : original.entrySet()) {
                    LabSupport.check(level.getBlockState(entry.getKey()).equals(entry.getValue())
                        && level.getBlockEntity(entry.getKey()) == null, "marker fixture block cleanup failed at " + entry.getKey());
                }
            } finally {
                // Empty at entry; the synchronous lab command creates only its own drops here.
                for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, DROPS)) drop.discard();
                GuardHooks.afterPlace();
            }
        }
    }
}
