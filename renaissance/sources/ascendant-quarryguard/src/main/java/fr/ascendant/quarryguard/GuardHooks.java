package fr.ascendant.quarryguard;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.QuarryFakePlayerCommon;
import com.yogpc.qp.machine.advquarry.AdvQuarryBlock;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.marker.QuarryMarker;
import com.yogpc.qp.machine.quarry.QuarryBlock;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import fr.ascendant.quarryguard.core.ChunkRect;
import fr.ascendant.quarryguard.core.ClaimIndex;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;

/** Server-thread authority. Cached geometry is never a cached permission. */
public final class GuardHooks {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String OWNER = "ascendant_quarryguard_owner";
    private static final String QUARANTINE = "ascendant_quarryguard_quarantine";
    static final String ADOPTION = "ascendant_quarryguard_adoption";
    private static final ClaimIndex INDEX = new ClaimIndex();
    private static final Map<UUID, ChunkTeamData> TEAMS = new HashMap<>();
    private static final Map<BlockEntity, Coverage> COVERAGE = new WeakHashMap<>();
    private static final Map<BlockEntity, Long> LAST_WARNING = new WeakHashMap<>();
    private static final ThreadLocal<Placement> PLACEMENT = new ThreadLocal<>();
    private static MinecraftServer server;
    private static ClaimedChunkManager manager;
    private static boolean ready;
    private static Object authorityEpoch = new Object();
    private static int mutations;
    private static long queries, cacheHits, checks, denied, checkNanos;

    private record Selection(Area area, QuarryMarker.Link link, BlockEntity marker, CompoundTag markerData, List<BlockPos> removed) { }
    private record Placement(Level level, BlockPos pos, UUID author, BlockState state, Selection selection, boolean confirmed) { }
    private record Coverage(String dimension, ChunkRect rectangle, int machineX, int machineZ,
                            long revision, Set<UUID> owners) { }

    private GuardHooks() { }

    public static void start(MinecraftServer current) {
        stop();
        server = current;
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            LOG.error("QuarryGuard: FTB not ready; all quarry operations remain suspended");
            return;
        }
        manager = FTBChunksAPI.api().getManager();
        for (var team : FTBTeamsAPI.api().getManager().getTeams()) manager.getOrCreateData(team);
        for (ClaimedChunk claim : manager.getAllClaimedChunks()) insert(claim);
        ready = true;
        LOG.info("QuarryGuard ready: {} claims", manager.getAllClaimedChunks().size());
    }

    public static void stop() {
        ready = false;
        authorityEpoch = new Object();
        server = null;
        manager = null;
        mutations = 0;
        INDEX.clear();
        TEAMS.clear();
        COVERAGE.clear();
        LAST_WARNING.clear();
        PLACEMENT.remove();
        AdoptionService.clear();
        queries = cacheHits = checks = denied = checkNanos = 0;
    }

    public static void beginMutation() {
        if (server != null && !server.isSameThread()) {
            ready = false;
            throw new IllegalStateException("FTB claim mutated outside the server thread");
        }
        mutations++;
    }

    public static void changed(ClaimedChunkManager source, ChunkDimPos pos) {
        try {
            if (ready && source == manager) {
                ClaimedChunk actual = source.getChunk(pos);
                if (actual != null) insert(actual);
                else INDEX.remove(pos.dimension().location().toString(), pos.x(), pos.z());
            }
        }
        catch (RuntimeException error) { ready = false; LOG.error("QuarryGuard index invalidated", error); }
        finally { mutations--; }
    }

    public static void invalidateManager() {
        ready = false;
        COVERAGE.clear();
    }

    private static void insert(ClaimedChunk chunk) {
        ChunkDimPos pos = chunk.getPos();
        ChunkTeamData team = chunk.getTeamData();
        UUID id = team.getTeam().getId();
        TEAMS.put(id, team);
        INDEX.put(pos.dimension().location().toString(), pos.x(), pos.z(), id);
    }

    private static boolean available(Level level) {
        return ready && mutations == 0 && level instanceof ServerLevel && server != null
            && level.getServer() == server && server.isSameThread()
            && FTBChunksAPI.api().isManagerLoaded() && FTBChunksAPI.api().getManager() == manager;
    }

    /** A cleanup inherits the initial frame's claim domain, never a guessed player. */
    public static final class FrameCleanup {
        private final Level level;
        private final BlockPos origin;
        private final UUID team;
        private final Object epoch;
        private final long revision;

        private FrameCleanup(Level level, BlockPos origin, UUID team) {
            this.level = level;
            this.origin = origin.immutable();
            this.team = team;
            this.epoch = authorityEpoch;
            this.revision = INDEX.revision();
        }
    }

    public static FrameCleanup beginFrameCleanup(Level level, BlockPos origin) {
        if (!available(level)) return null;
        try {
            return new FrameCleanup(level, origin, frameTeam(level, origin));
        } catch (RuntimeException error) {
            invalidateManager();
            LOG.error("QuarryGuard: frame cleanup suspended; invalid claim domain", error);
            return null;
        }
    }

    public static boolean mayCleanFrame(FrameCleanup cleanup, Level level, BlockPos target) {
        if (cleanup == null || cleanup.level != level || !available(level)
            || cleanup.epoch != authorityEpoch || cleanup.revision != INDEX.revision()) return false;
        try {
            if (!Objects.equals(cleanup.team, frameTeam(level, cleanup.origin))) return false;
            UUID destination = frameTeam(level, target);
            return destination == null || destination.equals(cleanup.team);
        } catch (RuntimeException error) {
            invalidateManager();
            LOG.error("QuarryGuard: frame cleanup suspended; invalid claim domain", error);
            return false;
        }
    }

    private static UUID frameTeam(Level level, BlockPos pos) {
        ClaimedChunk claim = manager.getChunk(new ChunkDimPos(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4));
        if (claim == null) return null;
        ChunkTeamData data = claim.getTeamData();
        var team = data.getTeam();
        if (!team.isValid() || data.getTeamManager().getTeamByID(team.getId()).orElse(null) != team) {
            throw new IllegalStateException("Noncanonical frame claim owner");
        }
        return team.getId();
    }

    public static boolean beforePlace(BlockPlaceContext context, BlockState state) {
        PLACEMENT.remove();
        if (context.getLevel().isClientSide()) return true;
        if (!(context.getPlayer() instanceof ServerPlayer player) || player instanceof FakePlayer) return false;
        try {
            Selection selection = select(context.getLevel(), context.getClickedPos(), state);
            ChunkRect rectangle = rectangle(selection.area);
            if (!check(context.getLevel(), context.getClickedPos(), rectangle, player.getUUID(), null)
                || !markerPermissions(context.getLevel(), context.getClickedPos(), player.getUUID(), selection)) {
                message(player, "Quarry refusee : emprise sur un claim non autorise, ou protection indisponible.");
                return false;
            }
            PLACEMENT.set(new Placement(context.getLevel(), context.getClickedPos().immutable(), player.getUUID(), state, selection, false));
            return true;
        } catch (RuntimeException error) {
            LOG.error("QuarryGuard: preflight rejected", error);
            return false;
        }
    }

    public static void afterPlace() { PLACEMENT.remove(); }

    public static boolean confirmPlacement(Level level, BlockPos pos, BlockState state, LivingEntity placer) {
        if (level.isClientSide()) return true;
        Placement plan = PLACEMENT.get();
        if (plan == null || plan.confirmed || plan.level != level || !plan.pos.equals(pos) || plan.state != state || placer == null
            || !plan.author.equals(placer.getUUID())) return false;
        BlockEntity machine = level.getBlockEntity(pos);
        if (!isQuarry(machine)) return false;
        Selection actual = select(level, pos, state);
        if (!actual.area.equals(plan.selection.area) || actual.marker != plan.selection.marker
            || !actual.markerData.equals(plan.selection.markerData)
            || !actual.removed.equals(plan.selection.removed)
            || !markerPermissions(level, pos, plan.author, actual)
            || !check(level, pos, rectangle(actual.area), plan.author, null)) return false;
        PLACEMENT.set(new Placement(level, pos.immutable(), plan.author, state, plan.selection, true));
        machine.getPersistentData().putUUID(OWNER, plan.author);
        machine.getPersistentData().remove(QUARANTINE);
        COVERAGE.remove(machine);
        return true;
    }

    public static Optional<QuarryMarker.Link> approvedLink() {
        Placement plan = PLACEMENT.get();
        if (plan == null || !plan.confirmed) throw new IllegalStateException("Missing approved placement plan");
        return Optional.of(new QuarryMarker.Link() {
            public Area area() { return plan.selection.area; }
            public List<ItemStack> drops() {
                return markerPermissions(plan.level, plan.pos, plan.author, plan.selection) ? plan.selection.link.drops() : List.of();
            }
            public void remove(Level level) {
                if (level == plan.level && markerPermissions(level, plan.pos, plan.author, plan.selection)) plan.selection.link.remove(level);
            }
        });
    }

    private static boolean markerPermissions(Level level, BlockPos machine, UUID author, Selection selection) {
        if (!available(level)) return false;
        try {
            if (!selection.removed.equals(MarkerFootprint.removedPositions(selection.link))
                || !selection.area.equals(selection.link.area())) return false;
            Set<Long> chunks = new HashSet<>();
            for (BlockPos pos : selection.removed) {
                int x = Math.floorDiv(pos.getX(), 16), z = Math.floorDiv(pos.getZ(), 16);
                if (chunks.add(ChunkPos.asLong(x, z)) && !check(level, machine, new ChunkRect(x, z, x, z), author, null)) return false;
            }
            return true;
        } catch (RuntimeException error) {
            LOG.warn("QuarryGuard: invalid marker removal footprint", error);
            return false;
        }
    }

    public static boolean mayCollect(BlockEntity machine, Entity entity) {
        UUID owner = owner(machine);
        if (owner == null || entity.level() != machine.getLevel()) return false;
        var box = entity.getBoundingBox();
        ChunkRect bounds = ChunkRect.fromBlocks((int)Math.floor(box.minX), (int)Math.floor(box.minZ),
            (int)Math.floor(box.maxX), (int)Math.floor(box.maxZ));
        return check(machine.getLevel(), machine.getBlockPos(), bounds, owner, null);
    }

    public static boolean mayWork(BlockEntity machine) {
        if (machine.getLevel() != null && machine.getLevel().isClientSide()) return true;
        UUID owner = owner(machine);
        Area area = area(machine);
        boolean result = false;
        try {
            result = owner != null && validArea(area) && !machine.getPersistentData().contains(QUARANTINE)
                && !machine.getPersistentData().contains(ADOPTION)
                && check(machine.getLevel(), machine.getBlockPos(), rectangle(area), owner, machine);
        } catch (RuntimeException error) {
            // Missing/stale external data never turns a denied action into wilderness.
            ready = false;
            LOG.error("QuarryGuard protection suspended after error", error);
        }
        if (!result) warn(machine, owner);
        else LAST_WARNING.remove(machine);
        return result;
    }

    public static boolean mayConfigure(ServerPlayer sender, BlockEntity machine) {
        if (machine.getPersistentData().contains(ADOPTION)) {
            message(sender, "Quarry en pause d'attribution : configuration verrouillee jusqu'a la reprise par un operateur.");
            return false;
        }
        if (!available(machine.getLevel()) || sender.level() != machine.getLevel()
            || sender.distanceToSqr(machine.getBlockPos().getCenter()) > 64.0) return false;
        UUID owner = owner(machine);
        boolean allowed = owner != null && (owner.equals(sender.getUUID()) || manager.getBypassProtection(sender.getUUID()));
        if (!allowed) message(sender, "Quarry refusee : seul son proprietaire peut la reconfigurer.");
        return allowed;
    }

    public static boolean maySetArea(BlockEntity machine, Area proposed) {
        COVERAGE.remove(machine);
        if (machine.getLevel() != null && machine.getLevel().isClientSide()) return true;
        if (machine.getPersistentData().contains(ADOPTION)) return false;
        if (proposed == null) return true;
        UUID owner = owner(machine);
        return owner != null && validArea(proposed) && check(machine.getLevel(), machine.getBlockPos(), rectangle(proposed), owner, null);
    }

    public static boolean withinArea(BlockEntity machine, int x, int z, boolean interior) {
        Area bounds = area(machine);
        boolean contained = validArea(bounds) && (interior
            ? x > bounds.minX() && x < bounds.maxX() && z > bounds.minZ() && z < bounds.maxZ()
            : x >= bounds.minX() && x <= bounds.maxX() && z >= bounds.minZ() && z <= bounds.maxZ());
        if (!contained) {
            machine.getPersistentData().putString(QUARANTINE, "work_target_outside_area");
            machine.setChanged();
        }
        return contained;
    }

    public static boolean mayWrite(BlockEntity machine, BlockPos pos) {
        return withinArea(machine, pos.getX(), pos.getZ(), false) && mayWork(machine);
    }

    public static ServerPlayer fakePlayer(BlockEntity machine, ServerLevel level) {
        UUID owner = owner(machine);
        if (owner == null) return null;
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(owner, "[AscendantQG]"));
        QuarryFakePlayerCommon.setDirection(player, Direction.DOWN);
        return player;
    }

    public static void loadOwner(BlockEntity machine, CompoundTag tag) {
        COVERAGE.remove(machine);
        machine.getPersistentData().remove(OWNER);
        machine.getPersistentData().remove(QUARANTINE);
        machine.getPersistentData().remove(ADOPTION);
        if (tag.contains(ADOPTION)) machine.getPersistentData().put(ADOPTION, tag.get(ADOPTION).copy());
        if (tag.hasUUID(OWNER)) machine.getPersistentData().putUUID(OWNER, tag.getUUID(OWNER));
        if (tag.contains(QUARANTINE)) machine.getPersistentData().putString(QUARANTINE, tag.getString(QUARANTINE));
        Area area = area(machine);
        if (area != null && !validArea(area)) {
            machine.getPersistentData().putString(QUARANTINE, "restored_area_without_valid_interior");
            return;
        }
        if (tag.contains("targetPos")) {
            var target = BlockPos.CODEC.parse(NbtOps.INSTANCE, tag.get("targetPos")).result();
            if (area == null || target.isEmpty() || target.get().getX() < area.minX() || target.get().getX() > area.maxX()
                || target.get().getZ() < area.minZ() || target.get().getZ() > area.maxZ()) {
                LOG.error("QuarryGuard: invalid restored target; machine quarantined at {}", machine.getBlockPos());
                machine.getPersistentData().putString(QUARANTINE, "restored_target_outside_area");
                return;
            }
        }
    }

    public static void saveOwner(BlockEntity machine, CompoundTag tag) {
        UUID owner = owner(machine);
        if (owner != null) tag.putUUID(OWNER, owner);
        if (machine.getPersistentData().contains(QUARANTINE)) tag.putString(QUARANTINE, machine.getPersistentData().getString(QUARANTINE));
        if (machine.getPersistentData().contains(ADOPTION)) tag.put(ADOPTION, machine.getPersistentData().get(ADOPTION).copy());
    }

    static boolean operatorReady(Level level) { return available(level); }

    static String adoptionDataIssue(BlockEntity machine) {
        if (!isQuarry(machine) || machine.isRemoved() || !available(machine.getLevel())) return "Protection indisponible ou quarry absente.";
        if (machine.getPersistentData().contains(QUARANTINE)) return "Quarry en quarantaine : aucune levee automatique.";
        Area bounds = area(machine);
        if (!validArea(bounds)) return "Emprise absente ou invalide.";
        CompoundTag tag = machine.saveWithFullMetadata(machine.getLevel().registryAccess());
        if (tag.contains("targetPos")) {
            var target = BlockPos.CODEC.parse(NbtOps.INSTANCE, tag.get("targetPos")).result();
            if (target.isEmpty() || target.get().getX() < bounds.minX() || target.get().getX() > bounds.maxX()
                || target.get().getZ() < bounds.minZ() || target.get().getZ() > bounds.maxZ()) return "Cible de travail hors emprise.";
        }
        return null;
    }

    static boolean mayResumeAdoption(BlockEntity machine) {
        UUID author = owner(machine);
        return author != null && adoptionDataIssue(machine) == null
            && check(machine.getLevel(), machine.getBlockPos(), rectangle(area(machine)), author, machine);
    }

    private static UUID owner(BlockEntity machine) {
        return machine.getPersistentData().hasUUID(OWNER) ? machine.getPersistentData().getUUID(OWNER) : null;
    }

    public static boolean isQuarry(BlockEntity machine) {
        return machine instanceof QuarryEntity || machine instanceof AdvQuarryEntity;
    }

    private static Area area(BlockEntity machine) {
        if (machine instanceof QuarryEntity quarry) return quarry.getArea();
        if (machine instanceof AdvQuarryEntity quarry) return quarry.getArea();
        return null;
    }

    static boolean validArea(Area area) {
        return area != null && area.minX() >= -30000000 && area.minZ() >= -30000000
            && area.maxX() < 30000000 && area.maxZ() < 30000000
            && (long) area.maxX() - area.minX() >= 2 && (long) area.maxZ() - area.minZ() >= 2;
    }

    private static ChunkRect rectangle(Area area) {
        if (!validArea(area)) throw new IllegalArgumentException("Quarry area must have a nonempty interior inside world bounds");
        return ChunkRect.fromBlocks(area.minX(), area.minZ(), area.maxX(), area.maxZ());
    }

    private static Selection select(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.FACING);
        for (Direction direction : new Direction[]{facing.getOpposite(), facing.getCounterClockWise(), facing.getClockWise()}) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof QuarryMarker marker) {
                var link = marker.getLink();
                if (link.isPresent()) {
                    BlockEntity markerEntity = (BlockEntity) marker;
                    return new Selection(link.get().area(), link.get(), markerEntity,
                        markerEntity.saveWithoutMetadata(level.registryAccess()).copy(), MarkerFootprint.removedPositions(link.get()));
                }
            }
        }
        if (state.getBlock() instanceof AdvQuarryBlock) {
            ChunkPos chunk = new ChunkPos(pos);
            Area area = new Area(chunk.getMinBlockX() - 1, pos.getY(), chunk.getMinBlockZ() - 1,
                chunk.getMaxBlockX() + 1, pos.getY() + 4, chunk.getMaxBlockZ() + 1, facing);
            return new Selection(area, new QuarryMarker.StaticLink(area), null, new CompoundTag(), List.of());
        }
        if (!(state.getBlock() instanceof QuarryBlock)) throw new IllegalArgumentException("Not a quarry");
        BlockPos base = pos.relative(facing.getOpposite());
        BlockPos a = base.relative(facing.getClockWise(), 5).above(4);
        BlockPos b = base.relative(facing.getCounterClockWise(), 5).relative(facing.getOpposite(), 10);
        Area area = new Area(a, b, facing.getOpposite());
        return new Selection(area, new QuarryMarker.StaticLink(area), null, new CompoundTag(), List.of());
    }

    private static boolean check(Level level, BlockPos machinePos, ChunkRect rectangle, UUID owner, BlockEntity machine) {
        long started = System.nanoTime();
        checks++;
        try {
            if (!available(level)) return false;
            if (manager.getBypassProtection(owner)) return true;
            String dimension = level.dimension().location().toString();
            int mx = Math.floorDiv(machinePos.getX(), 16), mz = Math.floorDiv(machinePos.getZ(), 16);
            Coverage coverage = machine == null ? null : COVERAGE.get(machine);
            if (coverage == null || coverage.revision != INDEX.revision() || !coverage.dimension.equals(dimension)
                || !coverage.rectangle.equals(rectangle) || coverage.machineX != mx || coverage.machineZ != mz) {
                Set<UUID> owners = new HashSet<>();
                INDEX.queryDenied(dimension, rectangle, id -> { owners.add(id); return true; });
                INDEX.queryDenied(dimension, new ChunkRect(mx, mz, mx, mz), id -> { owners.add(id); return true; });
                coverage = new Coverage(dimension, rectangle, mx, mz, INDEX.revision(), Set.copyOf(owners));
                if (machine != null) COVERAGE.put(machine, coverage);
                queries++;
            } else cacheHits++;
            for (UUID teamId : coverage.owners) {
                ChunkTeamData data = TEAMS.get(teamId);
                if (data == null || !allowed(data, owner)) { denied++; return false; }
            }
            return true;
        } finally { checkNanos += System.nanoTime() - started; }
    }

    private static boolean allowed(ChunkTeamData data, UUID author) {
        var team = data.getTeam();
        if (!team.isValid() || data.getTeamManager().getTeamByID(team.getId()).orElse(null) != team) return false;
        TeamRank rank = team.getRankForPlayer(author);
        boolean member = rank.isMemberOrBetter();
        if (!member && rank != TeamRank.ALLY) return false;
        PrivacyMode privacy = data.getTeam().getProperty(FTBChunksProperties.BLOCK_EDIT_MODE);
        if (privacy == PrivacyMode.PRIVATE) return member;
        if (privacy == PrivacyMode.ALLIES) return data.isAlly(author);
        return privacy == PrivacyMode.PUBLIC;
    }

    private static void warn(BlockEntity machine, UUID owner) {
        if (!(machine.getLevel() instanceof ServerLevel level)) return;
        long tick = level.getGameTime();
        Long last = LAST_WARNING.get(machine);
        if (last != null) return;
        LAST_WARNING.put(machine, tick);
        LOG.warn("QuarryGuard: suspended quarry at {} in {}; {}", machine.getBlockPos(), level.dimension().location(),
            owner == null ? "owner missing; manual migration required" : machine.getPersistentData().contains(ADOPTION)
                ? "operator adoption awaiting explicit resume" : "claim permission or readiness check failed");
        if (owner != null && server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) message(player, machine.getPersistentData().contains(ADOPTION)
                ? "Quarry en pause : un operateur doit confirmer sa reprise."
                : "Quarry suspendue : claim non autorise ou protection indisponible.");
        }
    }

    private static void message(ServerPlayer player, String text) {
        if (player.connection != null) player.displayClientMessage(Component.literal(text), false);
    }

    public static String status() {
        return "QuarryGuard ready=" + ready + ", mutations=" + mutations + ", revision=" + INDEX.revision()
            + ", geometryQueries=" + queries + ", geometryCacheHits=" + cacheHits + ", checks=" + checks
            + ", denied=" + denied + ", meanCheckUs=" + (checks == 0 ? 0 : checkNanos / checks / 1000.0);
    }
}
