package fr.ascendant.quarryguard;

import com.mojang.authlib.GameProfile;
import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.marker.ChunkMarkerEntity;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Explicitly invoked, synchronous checks for the disposable laboratory world only. */
public final class LabChecks {
    private static final BlockPos MACHINE = new BlockPos(100, 64, 100);
    private static final BlockPos MARKER = MACHINE.south();
    private static final String OWNER_KEY = "ascendant_quarryguard_owner";
    private final MinecraftServer server;
    private final ServerLevel level;
    private final ClaimedChunkManagerImpl claims;
    private final TeamManagerImpl teams;
    private final CommandSourceStack console;
    private final ChunkDimPos interior;
    private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
    private final List<String> passed = new ArrayList<>();
    private ServerPlayer owner;
    private ChunkTeamDataImpl personal;
    private ChunkTeamDataImpl hostile;
    private ChunkTeamDataImpl partyData;
    private PartyTeam party;
    private boolean ownsInterior;

    private LabChecks(MinecraftServer server) {
        this.server = server;
        level = server.overworld();
        claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        console = server.createCommandSourceStack().withSuppressedOutput();
        interior = new ChunkDimPos(level.dimension(), 8, 8);
    }

    public static String run(MinecraftServer server) {
        require(Boolean.getBoolean("ascendant.quarryguard.lab"), "lab system property required");
        require(server.isSameThread(), "invoke on the server thread");
        require("127.0.0.1".equals(server.getLocalIp()), "lab must bind 127.0.0.1");
        require(server.getPlayerCount() == 0, "disconnect all network players before testing");
        try {
            Path world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            require(world.getFileName().toString().equals("quarryguard-lab-world")
                    && (world.getParent().getFileName().toString().equals("runtime") || world.getParent().getFileName().toString().equals("full-runtime"))
                    && world.getParent().getParent().getFileName().toString().equals("quarryguard-lab"),
                    "refusing non-laboratory world: " + world);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot verify laboratory world path", error);
        }
        require(server.overworld().getChunkSource().getGenerator() instanceof FlatLevelSource,
                "flat scratch world required");
        require(FTBChunksAPI.api().isManagerLoaded(), "FTB Chunks is not ready");
        require(GuardHooks.status().contains("ready=true"), "ServerStarted guard initialization missing");
        LabChecks test = new LabChecks(server);
        Throwable failure = null;
        try {
            test.prepare();
            test.exercise("quarry");
            test.exercise("adv_quarry");
        } catch (Throwable error) {
            failure = error;
            throw new IllegalStateException("QuarryGuard lab failed after " + test.passed + ": "
                    + error.getMessage(), error);
        } finally {
            try { test.cleanup(); }
            catch (RuntimeException error) {
                if (failure != null) failure.addSuppressed(error);
                else throw error;
            }
        }
        return "PASS QuarryGuard in-engine checks: " + String.join(", ", test.passed)
                + ". Fixtures removed; offline test teams remain in disposable lab world. " + GuardHooks.status();
    }

    private void prepare() throws Exception {
        for (int x = 6; x <= 10; x++) {
            for (int z = 6; z <= 10; z++) {
                require(claims.getChunk(new ChunkDimPos(level.dimension(), x, z)) == null,
                        "scratch footprint already claimed at " + x + "," + z);
            }
        }
        // Never replace an existing machine/marker or its persistent contents.
        for (BlockPos pos : List.of(MACHINE, MARKER, MACHINE.below(), MARKER.below())) {
            require(level.getBlockEntity(pos) == null, "existing scratch block entity at " + pos);
            require(level.getBlockState(pos).isAir(), "scratch fixture positions must be air: " + pos);
            originals.put(pos, level.getBlockState(pos));
        }
        level.setBlock(MACHINE.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(MARKER.below(), Blocks.STONE.defaultBlockState(), 3);
        owner = actor("QGLabOwner");
        ServerPlayer enemy = actor("QGLabEnemy");
        ServerPlayer leader = actor("QGLabLeader");
        personal = data(teams.getPlayerTeamForPlayerID(owner.getUUID()).orElseThrow());
        hostile = data(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        party = teams.createParty(leader.getUUID(), null, "QGLab-" + UUID.randomUUID(), null, null);
        partyData = data(party);
        require(party.getRankForPlayer(leader.getUUID()).isMemberOrBetter(), "party owner fixture");
        require(!claims.getBypassProtection(owner.getUUID()), "test owner unexpectedly has bypass");
        passed.add("offline personal/party fixtures");
    }

    private ServerPlayer actor(String name) {
        UUID id = UUID.randomUUID();
        // Null is explicitly handled by FTB's offline creation path: no login/sync packet target.
        teams.playerLoggedIn(null, id, name);
        teams.getPersonalTeamForPlayerID(id).setOnline(false);
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(id, name),
                ClientInformation.createDefault()) {
            @Override
            public TextFilter getTextFilter() { return TextFilter.DUMMY; }
        };
        player.connection = new SilentLabListener(server, player);
        player.setPos(100.5, 64, 97.5);
        player.setYRot(0); // South; quarry faces north and discovers the south marker.
        player.setXRot(0);
        require(player.connection instanceof SilentLabListener
                && !player.connection.getConnection().isConnected() && !player.isCreative(),
                "non-networked survival actor required");
        require(server.getPlayerList().getPlayer(id) == null, "actor must not be registered as online");
        return player;
    }

    private static final class SilentLabListener extends ServerGamePacketListenerImpl {
        private SilentLabListener(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet) {
            // Native placement may send UI packets; this fixture has no client or channel.
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            // Do not queue packets or run transport callbacks for an unconnected test actor.
        }
    }

    private ChunkTeamDataImpl data(Team team) {
        ChunkTeamDataImpl data = claims.getOrCreateData(team);
        data.setExtraClaimChunks(64);
        data.updateLimits();
        team.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        return data;
    }

    private void marker() {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("quarryplus:chunk_marker"));
        require(block != Blocks.AIR, "missing quarryplus:chunk_marker");
        level.setBlock(MARKER, block.defaultBlockState(), 3);
        require(level.getBlockEntity(MARKER) instanceof ChunkMarkerEntity, "chunk marker entity missing");
        ChunkMarkerEntity marker = (ChunkMarkerEntity) level.getBlockEntity(MARKER);
        marker.init(Direction.AxisDirection.POSITIVE, Direction.AxisDirection.POSITIVE);
        marker.changeSize(63, 64, 68);
        Area area = marker.getLink().orElseThrow().area();
        require(area.minX() >> 4 < 8 && area.maxX() >> 4 > 8
                && area.minZ() >> 4 < 8 && area.maxZ() >> 4 > 8, "claim must be strictly interior");
    }

    private void exercise(String id) throws Exception {
        marker();
        require(hostile.claim(console, interior, true).isSuccess(), "claim simulation rejected");
        require(claims.getChunk(interior) == null, "simulation registered a claim");
        claim(hostile);
        place(id, owner, false);
        require(level.getBlockEntity(MARKER) instanceof ChunkMarkerEntity, "denial consumed marker");
        clearClaim();
        passed.add(id + " hostile interior preflight/no consumption + simulation");

        BlockEntity machine = place(id, owner, true);
        require(GuardHooks.mayWork(machine), id + " wilderness work denied");
        require(GuardHooks.mayWork(machine), id + " warm wilderness denied");
        claim(hostile);
        require(!GuardHooks.mayWork(machine), id + " new hostile claim did not invalidate coverage");
        ClaimedChunkImpl chunk = claims.getChunk(interior);
        chunk.setTeamData(personal);
        require(GuardHooks.mayWork(machine), id + " transfer to personal claim not reflected");
        if (id.equals("quarry")) benchmark(machine);
        BlockEntity restored = reload(machine, false);
        require(GuardHooks.mayWork(restored), id + " reloaded owner lost authorization");
        require(!GuardHooks.mayWork(reload(machine, true)), id + " missing owner was authorized");
        BlockEntity quarantined = reload(machine, false);
        CompoundTag badTarget = quarantined.saveWithFullMetadata(level.registryAccess());
        badTarget.putIntArray("targetPos", new int[]{30000, 64, 30000});
        GuardHooks.loadOwner(quarantined, badTarget);
        CompoundTag savedQuarantine = quarantined.saveWithFullMetadata(level.registryAccess());
        require(savedQuarantine.hasUUID(OWNER_KEY) && savedQuarantine.getUUID(OWNER_KEY).equals(owner.getUUID()),
                id + " quarantine destroyed owner evidence");
        require(savedQuarantine.contains("ascendant_quarryguard_quarantine") && !GuardHooks.mayWork(quarantined),
                id + " quarantined machine was authorized or reason lost");
        chunk.setTeamData(hostile);
        require(!GuardHooks.mayWork(machine) && !GuardHooks.mayWork(restored), id + " hostile transfer stale");
        claims.setBypassProtection(owner.getUUID(), true);
        try {
            require(GuardHooks.mayWork(machine), id + " explicit bypass denied");
        } finally { claims.setBypassProtection(owner.getUUID(), false); }
        require(!GuardHooks.mayWork(machine), id + " revoked bypass cached");
        passed.add(id + " claim mutation/owner transfer/NBT round-trip/bypass revoke");

        chunk.setTeamData(partyData);
        party.removeMember(owner.getUUID());
        party.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PUBLIC);
        require(!GuardHooks.mayWork(machine), id + " PUBLIC admitted enemy");
        long revision = metric("revision"), queries = metric("geometryQueries");
        party.addAlly(console, List.of(owner.getGameProfile()));
        require(GuardHooks.mayWork(machine), id + " PUBLIC explicit ally denied");
        party.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        require(!GuardHooks.mayWork(machine), id + " PRIVATE admitted ally");
        party.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.ALLIES);
        require(GuardHooks.mayWork(machine), id + " ALLIES explicit ally denied; check ALLY_MODE");
        party.removeAlly(console, List.of(owner.getGameProfile()));
        require(!GuardHooks.mayWork(machine), id + " removed ally remains authorized");
        party.addMember(owner.getUUID(), TeamRank.MEMBER);
        party.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        require(GuardHooks.mayWork(machine), id + " PRIVATE party member denied");
        party.removeMember(owner.getUUID());
        require(!GuardHooks.mayWork(machine), id + " removed member remains authorized");
        require(metric("revision") == revision && metric("geometryQueries") == queries,
                id + " rights test unexpectedly changed geometry/rebuilt coverage");
        clearClaim();
        require(GuardHooks.mayWork(machine), id + " unclaim did not resume work");
        removeMachine();
        passed.add(id + " live public/private/member/ally policy without geometry changes");

        claim(personal);
        marker();
        require(GuardHooks.mayWork(place(id, owner, true)), id + " personal placement denied");
        removeMachine();
        clearClaim();
        party.addMember(owner.getUUID(), TeamRank.MEMBER);
        party.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        claim(partyData);
        marker();
        require(GuardHooks.mayWork(place(id, owner, true)), id + " party-member placement denied");
        removeMachine();
        party.removeMember(owner.getUUID());
        party.addAlly(console, List.of(owner.getGameProfile()));
        party.setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.ALLIES);
        marker();
        require(GuardHooks.mayWork(place(id, owner, true)), id + " allied placement denied");
        removeMachine();
        marker();
        ServerPlayer fake = FakePlayerFactory.get(level, owner.getGameProfile());
        fake.setPos(owner.getX(), owner.getY(), owner.getZ());
        fake.setYRot(0);
        place(id, fake, false);
        clearClaim();
        level.setBlock(MARKER, Blocks.AIR.defaultBlockState(), 3);
        passed.add(id + " native personal/party/ally placements + denied fake actor");
    }

    private BlockEntity place(String id, ServerPlayer actor, boolean allowed) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("quarryplus:" + id));
        require(item instanceof BlockItem, "missing native BlockItem quarryplus:" + id);
        ItemStack stack = new ItemStack(item, 3);
        ItemStack previous = actor.getItemInHand(InteractionHand.MAIN_HAND);
        actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            BlockHitResult hit = new BlockHitResult(new Vec3(100.5, 64, 100.5),
                    Direction.UP, MACHINE.below(), false);
            BlockPlaceContext context = new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, stack, hit);
            require(context.getClickedPos().equals(MACHINE), "placement context targets wrong block");
            InteractionResult result = ((BlockItem) item).place(context);
            if (!allowed) {
                require(!result.consumesAction(), id + " denied placement returned success");
                require(stack.getCount() == 3, id + " denied placement consumed item");
                require(level.getBlockState(MACHINE).isAir() && level.getBlockEntity(MACHINE) == null,
                        id + " denied placement changed world");
                require(level.getBlockState(MACHINE.below()).is(Blocks.STONE), "support changed on denial");
                return null;
            }
            require(result.consumesAction() && stack.getCount() == 2, id + " native placement failed/consumption wrong");
            BlockEntity machine = level.getBlockEntity(MACHINE);
            require(GuardHooks.isQuarry(machine), id + " native machine entity missing");
            require(machine.getPersistentData().hasUUID(OWNER_KEY)
                    && machine.getPersistentData().getUUID(OWNER_KEY).equals(owner.getUUID()),
                    id + " native placement did not persist real owner");
            require(area(machine) != null && area(machine).minX() >> 4 < 8
                    && area(machine).maxX() >> 4 > 8, id + " marker area not adopted");
            return machine;
        } finally { actor.setItemInHand(InteractionHand.MAIN_HAND, previous); }
    }

    private BlockEntity reload(BlockEntity machine, boolean stripOwner) {
        CompoundTag tag = machine.saveWithFullMetadata(level.registryAccess());
        require(tag.hasUUID(OWNER_KEY), "native save omitted QuarryGuard owner");
        if (stripOwner) tag.remove(OWNER_KEY);
        BlockState state = machine.getBlockState();
        BlockEntity copy = ((EntityBlock) state.getBlock()).newBlockEntity(MACHINE, state);
        require(copy != null && copy != machine, "fresh block entity unavailable");
        copy.setLevel(level);
        copy.loadWithComponents(tag, level.registryAccess());
        require(area(copy) != null, "native load did not restore mining area");
        if (!stripOwner) require(copy.getPersistentData().getUUID(OWNER_KEY).equals(owner.getUUID()),
                "native load restored wrong owner UUID");
        return copy;
    }

    private static Area area(BlockEntity machine) {
        if (machine instanceof QuarryEntity quarry) return quarry.getArea();
        if (machine instanceof AdvQuarryEntity quarry) return quarry.getArea();
        return null;
    }

    private void claim(ChunkTeamDataImpl data) {
        require(claims.getChunk(interior) == null, "interior claim unexpectedly occupied");
        ownsInterior = true; // Also clean up if an AFTER_CLAIM callback throws after the write.
        require(data.claim(console, interior, false).isSuccess(), "native claim rejected for " + data.getTeamId());
        require(claims.getChunk(interior) != null, "successful claim absent from FTB manager");
    }

    private void clearClaim() {
        ClaimedChunkImpl chunk = claims.getChunk(interior);
        if (chunk == null) { ownsInterior = false; return; }
        require(ownsInterior, "refusing to unclaim a non-fixture claim");
        ChunkTeamDataImpl data = chunk.getTeamData();
        require(data.unclaim(console, interior, true, true).isSuccess(), "unclaim simulation failed");
        require(claims.getChunk(interior) == chunk, "unclaim simulation mutated state");
        require(data.unclaim(console, interior, false, true).isSuccess(), "native unclaim failed");
        require(claims.getChunk(interior) == null, "unclaim left stale FTB entry");
        ownsInterior = false;
    }

    private void removeMachine() { level.setBlock(MACHINE, Blocks.AIR.defaultBlockState(), 3); }

    private void benchmark(BlockEntity machine) {
        for (int i = 0; i < 5000; i++) require(GuardHooks.mayWork(machine), "benchmark warm-up denied");
        long[] nanos = new long[10000];
        long queries = metric("geometryQueries");
        for (int i = 0; i < nanos.length; i++) {
            long start = System.nanoTime();
            boolean allowed = GuardHooks.mayWork(machine);
            nanos[i] = System.nanoTime() - start;
            require(allowed, "benchmark authorization denied at iteration " + i);
        }
        require(metric("geometryQueries") == queries, "warm benchmark rebuilt geometry");
        Arrays.sort(nanos);
        passed.add("warm mayWork 5000+10000 calls, one personal interior claim, p50=" + nanos[4999]
                + "ns p95=" + nanos[9499] + "ns p99=" + nanos[9899] + "ns (not tick/MSPT)");
    }

    private void cleanup() {
        if (owner != null) claims.setBypassProtection(owner.getUUID(), false);
        if (ownsInterior) claims.unregisterClaim(interior);
        for (var entry : originals.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
        GuardHooks.afterPlace();
    }

    private static long metric(String key) {
        var matcher = Pattern.compile("(?:^|[ ,])" + Pattern.quote(key) + "=(\\d+)").matcher(GuardHooks.status());
        require(matcher.find(), "GuardHooks.status missing metric " + key);
        return Long.parseLong(matcher.group(1));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
