package fr.ascendant.quarryguard;

import com.mojang.logging.LogUtils;
import com.yogpc.qp.machine.misc.FrameBlock;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Native onRemove matrix: traversal is scoped to the starting frame's team, not the actor. */
public final class FrameChecks {
    private static final BlockPos SOURCE = new BlockPos(6991, 65, 7000);
    private static final BlockPos MIN = SOURCE.offset(-2, -2, -2);
    private static final BlockPos MAX = SOURCE.offset(20, 3, 3);

    private enum Zone { WILDERNESS, OWN, OTHER }

    private record Scenario(String name, Zone source, Zone target, boolean diagonal,
                            boolean traverse, boolean directRefusal) { }

    private record ClaimIdentity(ClaimedChunkImpl chunk, UUID teamId) { }

    private FrameChecks() { }

    public static String run(MinecraftServer server) throws Exception {
        LabSupport.requireLab(server);
        var level = server.overworld();
        var claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var actor = LabSupport.actor(server, teams, "QGFrameActor");
        var enemy = LabSupport.actor(server, teams, "QGFrameEnemy");
        var personal = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(actor.getUUID()).orElseThrow());
        var hostile = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        for (var data : List.of(personal, hostile)) {
            data.setExtraClaimChunks(8);
            data.updateLimits();
            data.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        }
        LabSupport.check(!personal.getTeamId().equals(hostile.getTeamId()), "distinct fixture teams required");
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("quarryplus:frame"));
        LabSupport.check(block instanceof FrameBlock, "native frame block unavailable");
        actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        LabSupport.check(actor.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
            && !claims.getBypassProtection(actor.getUUID()), "survival actor without bypass required");
        List<String> observed = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        var scenarios = List.of(
            new Scenario("wilderness->hostile face", Zone.WILDERNESS, Zone.OTHER, false, false, true),
            new Scenario("wilderness->hostile diagonal", Zone.WILDERNESS, Zone.OTHER, true, false, true),
            new Scenario("wilderness->own refused", Zone.WILDERNESS, Zone.OWN, false, false, false),
            new Scenario("own->own traversed", Zone.OWN, Zone.OWN, false, true, false),
            new Scenario("own->wilderness traversed", Zone.OWN, Zone.WILDERNESS, false, true, false),
            new Scenario("wilderness->wilderness traversed", Zone.WILDERNESS, Zone.WILDERNESS, false, true, false),
            new Scenario("own->other PUBLIC refused", Zone.OWN, Zone.OTHER, false, false, false));
        for (var scenario : scenarios) {
            hostile.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE,
                scenario.source() == Zone.OWN && scenario.target() == Zone.OTHER ? PrivacyMode.PUBLIC : PrivacyMode.PRIVATE);
            probe(server, claims, actor, block, observed, failures, scenario.name(), fixture -> {
                BlockPos target = SOURCE.offset(1, scenario.diagonal() ? 1 : 0, scenario.diagonal() ? 1 : 0);
                BlockPos end = target.east();
                LabSupport.check(!fixture.chunk(SOURCE).equals(fixture.chunk(target)), "fixture must cross a chunk boundary");
                fixture.claimZone(SOURCE, scenario.source(), personal, hostile);
                fixture.claimZone(target, scenario.target(), personal, hostile);
                for (BlockPos p : List.of(SOURCE, target, end)) fixture.place(p, block);
                if (scenario.directRefusal()) {
                    LabSupport.check(!actor.gameMode.destroyBlock(target), "FTB direct destruction was not refused");
                    for (BlockPos p : List.of(SOURCE, target, end)) fixture.expect(p, block, "direct FTB refusal");
                }
                fixture.destroySource();
                Block expected = scenario.traverse() ? Blocks.AIR : block;
                fixture.expect(target, expected, "first neighbor");
                fixture.expect(end, expected, "second neighbor");
            });
        }

        hostile.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        probe(server, claims, actor, block, observed, failures, "wilderness->hostile chunk->wilderness barrier (19 frames)", fixture -> {
            fixture.claim(SOURCE.east(), hostile);
            for (int i = 0; i <= 18; i++) {
                BlockPos p = SOURCE.east(i);
                var current = claims.getChunk(fixture.chunk(p));
                LabSupport.check(i == 0 || i >= 17 ? current == null
                    : current != null && current.getTeamData().getTeamId().equals(hostile.getTeamId()),
                    "incorrect barrier topology at " + p);
                fixture.place(p, block);
            }
            fixture.destroySource();
            for (int i = 1; i <= 18; i++) fixture.expect(SOURCE.east(i), block,
                i <= 16 ? "hostile barrier" : "wilderness beyond barrier");
        });

        for (boolean atSource : new boolean[]{true, false}) {
            probe(server, claims, actor, block, observed, failures,
                "native adjacent liquid at " + (atSource ? "source" : "neighbor"), fixture -> {
                    BlockPos target = SOURCE.east();
                    BlockPos end = SOURCE.east(2);
                    BlockPos dryBranch = SOURCE.west();
                    BlockPos water = (atSource ? SOURCE : target).above();
                    for (BlockPos p : List.of(SOURCE, target, end, dryBranch)) fixture.place(p, block);
                    fixture.place(water, Blocks.WATER);
                    LabSupport.check(!level.getFluidState(water).isEmpty(), "native liquid fixture missing");
                    fixture.destroySource();
                    fixture.expect(target, block, "liquid must stop native traversal");
                    fixture.expect(end, block, "native traversal must not jump liquid-adjacent frame");
                    fixture.expect(dryBranch, atSource ? block : Blocks.AIR,
                        "only liquid at the source may stop the dry branch");
                    fixture.expect(water, Blocks.WATER, "liquid preserved");
                });
        }

        probe(server, claims, actor, block, observed, failures, "initial frame replaced by STONE", fixture -> {
            for (int i = 0; i <= 2; i++) fixture.place(SOURCE.east(i), block);
            // Exercise native onRemove with a non-air replacement, without calling a guard helper.
            fixture.placed.put(SOURCE, Blocks.STONE);
            LabSupport.check(level.setBlock(SOURCE, Blocks.STONE.defaultBlockState(), 2), "stone replacement failed");
            fixture.expect(SOURCE, Blocks.STONE, "initial replacement must survive cleanup");
            fixture.expect(SOURCE.east(), Blocks.AIR, "replacement must still trigger native chain cleanup");
            fixture.expect(SOURCE.east(2), Blocks.AIR, "replacement chain tail");
        });

        String evidence = String.join("; ", observed);
        LogUtils.getLogger().info("QG-FRAME-EVIDENCE: {}", evidence);
        LabSupport.check(failures.isEmpty(), "Native frame matrix failed: " + String.join("; ", failures));
        return "frame protection PASS (" + observed.size() + " scenarios): " + evidence;
    }

    private static void probe(MinecraftServer server, ClaimedChunkManagerImpl claims, ServerPlayer actor,
                              Block block, List<String> observed, List<String> failures,
                              String name, Consumer<Fixture> test) {
        String outcome;
        try (var fixture = new Fixture(server, claims, actor, block)) {
            test.accept(fixture);
            fixture.checkClaimIdentities();
            outcome = name + ": PASS";
        } catch (RuntimeException failure) {
            outcome = name + ": FAIL " + failure.getMessage();
            for (Throwable suppressed : failure.getSuppressed()) outcome += " [cleanup: " + suppressed.getMessage() + "]";
            failures.add(outcome);
        }
        observed.add(outcome);
        LogUtils.getLogger().info("QG-FRAME-EVIDENCE: {}", outcome);
    }

    private static final class Fixture implements AutoCloseable {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final ClaimedChunkManagerImpl claims;
        private final ServerPlayer actor;
        private final Block frame;
        private final Map<BlockPos, Block> placed = new LinkedHashMap<>();
        private final Map<ChunkDimPos, ClaimIdentity> ownedClaims = new LinkedHashMap<>();

        private Fixture(MinecraftServer server, ClaimedChunkManagerImpl claims, ServerPlayer actor, Block frame) {
            this.server = server;
            this.level = server.overworld();
            this.claims = claims;
            this.actor = actor;
            this.frame = frame;
            requireEmpty();
            actor.setPos(SOURCE.getX() + 0.5, SOURCE.getY(), SOURCE.getZ() - 1.5);
            actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        }

        private ChunkDimPos chunk(BlockPos p) {
            return new ChunkDimPos(level.dimension(), p.getX() >> 4, p.getZ() >> 4);
        }

        private void requireEmpty() {
            for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) {
                LabSupport.check(level.getBlockState(p).isAir() && level.getBlockEntity(p) == null,
                    "frame probe volume occupied at " + p);
                LabSupport.check(claims.getChunk(chunk(p)) == null, "frame probe volume already claimed at " + p);
            }
        }

        private void claimZone(BlockPos p, Zone zone, ChunkTeamDataImpl personal, ChunkTeamDataImpl hostile) {
            if (zone != Zone.WILDERNESS) claim(p, zone == Zone.OWN ? personal : hostile);
            var current = claims.getChunk(chunk(p));
            LabSupport.check(zone == Zone.WILDERNESS ? current == null
                : current != null && current.getTeamData().getTeamId().equals(
                    (zone == Zone.OWN ? personal : hostile).getTeamId()), "incorrect claim zone at " + p);
        }

        private void claim(BlockPos p, ChunkTeamDataImpl data) {
            var pos = chunk(p);
            LabSupport.check(claims.getChunk(pos) == null, "refusing to reuse existing claim at " + pos);
            try {
                LabSupport.check(data.claim(server.createCommandSourceStack().withSuppressedOutput(), pos, false).isSuccess(),
                    "frame probe claim failed at " + pos);
            } finally {
                // Capture identity even if an AFTER_CLAIM listener throws after registration.
                var current = claims.getChunk(pos);
                if (current != null && current.getTeamData().getTeamId().equals(data.getTeamId())) {
                    ownedClaims.put(pos, new ClaimIdentity(current, data.getTeamId()));
                }
            }
            LabSupport.check(ownedClaims.containsKey(pos), "created claim has unexpected owner at " + pos);
        }

        private void place(BlockPos p, Block block) {
            LabSupport.check(level.getBlockState(p).isAir() && level.getBlockEntity(p) == null,
                "refusing to overwrite existing block at " + p);
            placed.put(p.immutable(), block);
            LabSupport.check(level.setBlock(p, block.defaultBlockState(), 2), "fixture placement failed at " + p);
            expect(p, block, "fixture placement");
        }

        private void expect(BlockPos p, Block expected, String context) {
            LabSupport.check(level.getBlockState(p).is(expected) && level.getBlockEntity(p) == null,
                context + " at " + p + ": expected " + expected + ", actual " + level.getBlockState(p));
        }

        private void destroySource() {
            expect(SOURCE, frame, "source before destruction");
            LabSupport.check(actor.gameMode.destroyBlock(SOURCE), "native source destruction failed");
            expect(SOURCE, Blocks.AIR, "source after native destruction");
        }

        private void checkClaimIdentities() {
            for (var entry : ownedClaims.entrySet()) {
                var current = claims.getChunk(entry.getKey());
                LabSupport.check(current == entry.getValue().chunk()
                    && current.getTeamData().getTeamId().equals(entry.getValue().teamId()),
                    "fixture claim identity changed; refusing cleanup at " + entry.getKey());
            }
        }

        @Override
        public void close() {
            checkClaimIdentities();
            for (var entry : placed.entrySet()) {
                var state = level.getBlockState(entry.getKey());
                LabSupport.check(level.getBlockEntity(entry.getKey()) == null
                    && (state.isAir() || state.is(entry.getValue())),
                    "unexpected fixture replacement; refusing cleanup at " + entry.getKey());
            }
            try {
                // Keep the real claims and liquid in place while removing residual frames.
                for (Block block : List.of(frame, Blocks.WATER, Blocks.STONE)) {
                    for (var entry : placed.entrySet()) {
                        if (entry.getValue() == block && level.getBlockState(entry.getKey()).is(block)) {
                            level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            } finally {
                for (var entry : ownedClaims.entrySet()) {
                    var current = claims.getChunk(entry.getKey());
                    LabSupport.check(current == entry.getValue().chunk()
                        && current.getTeamData().getTeamId().equals(entry.getValue().teamId()),
                        "fixture claim identity changed; refusing unclaim at " + entry.getKey());
                    claims.unregisterClaim(entry.getKey());
                    LabSupport.check(claims.getChunk(entry.getKey()) == null, "fixture claim cleanup failed at " + entry.getKey());
                }
            }
            requireEmpty();
        }
    }
}
