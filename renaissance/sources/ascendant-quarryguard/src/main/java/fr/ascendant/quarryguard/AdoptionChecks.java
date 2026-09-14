package fr.ascendant.quarryguard;

import com.yogpc.qp.machine.MachineStorage;
import com.yogpc.qp.machine.PowerEntity;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;

/** Synchronous, disposable lab checks. Never tick an authorized machine. */
public final class AdoptionChecks {
    private static final String OWNER = "ascendant_quarryguard_owner";
    private static final String ADOPTION = "ascendant_quarryguard_adoption";
    private static final String QUARANTINE = "ascendant_quarryguard_quarantine";
    private static final String SENTINEL = "adoption_lab_sentinel";
    private static final String SENTINEL_VALUE = "preserve-unrelated-data";

    private AdoptionChecks() { }

    public static String run(MinecraftServer server) throws Exception {
        LabSupport.requireLab(server);
        var level = server.overworld();
        for (int i = 0; i < 2; i++) verifyVolume(level, pos(i), false);
        var forced = new HashSet<>(level.getForcedChunks());
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var claims = (ClaimedChunkManagerImpl) FTBChunksAPI.api().getManager();
        var placer = LabSupport.actor(server, teams, "QGAdoptPlacer");
        var enemy = LabSupport.actor(server, teams, "QGAdoptEnemy");
        var hostile = claims.getOrCreateData(teams.getPlayerTeamForPlayerID(enemy.getUUID()).orElseThrow());
        hostile.setExtraClaimChunks(8);
        hostile.updateLimits();
        hostile.getTeam().setProperty(FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PRIVATE);
        var console = server.createCommandSourceStack().withSuppressedOutput().withPermission(2);
        var nonop = console.withPermission(0);
        var other = console.withEntity(enemy);
        LabSupport.check(console.hasPermission(2) && !nonop.hasPermission(2)
            && other.getEntity() == enemy && other.hasPermission(2), "invalid command sources");
        for (int i = 0; i < 2; i++) {
            try (var fixture = new Fixture(server, claims, hostile, pos(i))) {
                fixture.place(placer, i == 0 ? "quarry" : "adv_quarry");
                exercise(fixture, console, nonop, other, teams);
            }
        }
        LabSupport.check(forced.equals(new HashSet<>(level.getForcedChunks())), "adoption changed forced chunks");
        for (int i = 0; i < 2; i++) verifyVolume(level, pos(i), false);
        return "adoption PASS: both quarry types; all non-OP operations refused; immutable previews; operator/dimension/BE/NBT-bound single-use tokens; "
            + "offline explicit UUID; pending hold across 25 native ticks and native NBT reconstruction/guard restart; "
            + "current hostile claims and unavailable guard denied; cancel rollback, explicit resume, no transfer; "
            + "7 diamonds + 3 buckets + energy + unrelated NBT preserved; readable SNBT audit before/planned snapshots; "
            + "only owned fixture blocks/claims cleaned, audit evidence retained. "
            + "Limits: offline teams retained; no ten-minute wall-clock expiry test, no disk/two-JVM restart, no authorized mining ticks or Brigadier dispatch.";
    }

    private static void exercise(Fixture f, CommandSourceStack op, CommandSourceStack nonop,
                                 CommandSourceStack other, TeamManagerImpl teams) throws Exception {
        UUID owner = UUID.randomUUID();
        LabSupport.check(teams.getPlayerTeamForPlayerID(owner).isEmpty()
            && f.server.getPlayerList().getPlayer(owner) == null, "chosen owner must be unknown/offline");
        reject(f, "already-owned preview", () -> AdoptionService.preview(op, f.pos, owner));
        reject(f, "already-owned cancel", () -> AdoptionService.cancel(op, f.pos));
        CompoundTag orphan = f.save();
        removeBoth(orphan, OWNER);
        f.load(orphan);
        orphan = f.save();
        absent(orphan, OWNER);
        absent(orphan, ADOPTION);
        LabSupport.check(!GuardHooks.mayWork(f.machine), "orphan was authorized");
        inventory(f.machine);

        reject(f, "non-OP preview", () -> AdoptionService.preview(nonop, f.pos, owner));
        reject(f, "non-OP inspect", () -> AdoptionService.inspect(nonop, f.pos));
        reject(f, "non-OP orphan resume", () -> AdoptionService.resume(nonop, f.pos));
        reject(f, "non-OP orphan cancel", () -> AdoptionService.cancel(nonop, f.pos));
        inspect(f, op);
        UUID token = preview(f, op, owner);
        reject(f, "non-OP confirm", () -> AdoptionService.confirm(nonop, token));
        reject(f, "another operator confirm", () -> AdoptionService.confirm(other, token));
        audited(f, op, "adopt", () -> AdoptionService.confirm(op, token));
        held(f, owner, orphan);
        configurationHeld(f, owner);
        reject(f, "used token confirm", () -> AdoptionService.confirm(op, token));
        reject(f, "non-OP held resume", () -> AdoptionService.resume(nonop, f.pos));
        reject(f, "non-OP held cancel", () -> AdoptionService.cancel(nonop, f.pos));
        inspect(f, op);
        frozenTicks(f);

        CompoundTag hold = f.save();
        // A new native BE drops Java object state; both metadata copies must restore the hold.
        GuardHooks.stop();
        try {
            f.reconstruct(hold);
        } finally {
            GuardHooks.start(f.server);
        }
        LabSupport.check(GuardHooks.status().contains("ready=true"), "guard restart failed");
        same(f, hold, "native reload/guard restart");
        held(f, owner, orphan);
        frozenTicks(f);

        f.claim();
        reject(f, "current hostile claim resume", () -> AdoptionService.resume(op, f.pos));
        held(f, owner, orphan);
        frozenTicks(f);
        audited(f, op, "cancel-adoption", () -> AdoptionService.cancel(op, f.pos));
        same(f, orphan, "cancel under hostile claim must restore orphan NBT");
        absent(f.save(), OWNER);
        absent(f.save(), ADOPTION);
        reject(f, "second cancel", () -> AdoptionService.cancel(op, f.pos));
        f.unclaim();

        UUID dimensionToken = preview(f, op, owner);
        var nether = f.server.getLevel(Level.NETHER);
        LabSupport.check(nether != null, "dimension-binding fixture requires the Nether");
        reject(f, "another dimension confirm", () -> AdoptionService.confirm(op.withLevel(nether), dimensionToken));

        UUID changed = preview(f, op, owner);
        f.machine.getPersistentData().putString(SENTINEL, "changed-after-preview");
        reject(f, "snapshot mutation confirm", () -> AdoptionService.confirm(op, changed));
        f.load(orphan);
        reject(f, "invalidated token after snapshot rollback", () -> AdoptionService.confirm(op, changed));

        UUID replaced = preview(f, op, owner);
        f.reconstruct(orphan);
        same(f, orphan, "identical-NBT replacement fixture");
        reject(f, "different BE object confirm", () -> AdoptionService.confirm(op, replaced));

        UUID unavailable = preview(f, op, owner);
        GuardHooks.invalidateManager();
        try {
            LabSupport.check(!GuardHooks.status().contains("ready=true"), "guard fixture still ready");
            reject(f, "unavailable guard preview", () -> AdoptionService.preview(op, f.pos, owner));
            reject(f, "unavailable guard confirm", () -> AdoptionService.confirm(op, unavailable));
            frozenTicks(f);
        } finally {
            GuardHooks.start(f.server);
        }
        same(f, orphan, "unavailable guard recovery");

        UUID assigned = preview(f, op, owner);
        CompoundTag owned = orphan.copy();
        owned.putUUID(OWNER, owner);
        owned.getCompound("NeoForgeData").putUUID(OWNER, owner);
        f.load(owned);
        reject(f, "owner appeared before confirm", () -> AdoptionService.confirm(op, assigned));
        reject(f, "cannot transfer existing owner", () -> AdoptionService.preview(op, f.pos, UUID.randomUUID()));
        reject(f, "cannot cancel native assignment", () -> AdoptionService.cancel(op, f.pos));
        f.load(orphan);

        UUID quarantined = preview(f, op, owner);
        CompoundTag quarantine = orphan.copy();
        quarantine.putString(QUARANTINE, "adoption_lab_quarantine");
        quarantine.getCompound("NeoForgeData").putString(QUARANTINE, "adoption_lab_quarantine");
        f.load(quarantine);
        LabSupport.check(f.save().contains(QUARANTINE) && !GuardHooks.mayWork(f.machine), "quarantine fixture missing");
        reject(f, "quarantine preview", () -> AdoptionService.preview(op, f.pos, owner));
        reject(f, "quarantine confirm", () -> AdoptionService.confirm(op, quarantined));
        frozenTicks(f);
        f.load(orphan);

        UUID invalid = preview(f, op, owner);
        CompoundTag malformed = orphan.copy();
        CompoundTag area = malformed.getCompound("area");
        LabSupport.check(!area.isEmpty(), "missing native area NBT");
        area.putInt("maxX", area.getInt("minX"));
        f.load(malformed);
        LabSupport.check(!GuardHooks.validArea(LabSupport.area(f.machine)), "invalid area fixture normalized unexpectedly");
        reject(f, "invalid area preview", () -> AdoptionService.preview(op, f.pos, owner));
        reject(f, "invalid area confirm", () -> AdoptionService.confirm(op, invalid));
        frozenTicks(f);
        f.load(orphan);

        UUID finalToken = preview(f, op, owner);
        audited(f, op, "adopt", () -> AdoptionService.confirm(op, finalToken));
        held(f, owner, orphan);
        GuardHooks.invalidateManager();
        try {
            reject(f, "unavailable guard held resume", () -> AdoptionService.resume(op, f.pos));
            frozenTicks(f);
        } finally {
            GuardHooks.start(f.server);
        }
        CompoundTag pending = f.save();
        CompoundTag reassigned = pending.copy();
        UUID differentOwner = UUID.randomUUID();
        reassigned.putUUID(OWNER, differentOwner);
        reassigned.getCompound("NeoForgeData").putUUID(OWNER, differentOwner);
        f.load(reassigned);
        reject(f, "stale hold cannot cancel a different owner", () -> AdoptionService.cancel(op, f.pos));
        reject(f, "stale hold cannot resume a different owner", () -> AdoptionService.resume(op, f.pos));
        f.load(pending);
        same(f, pending, "pending identity fixture restored");
        audited(f, op, "resume", () -> AdoptionService.resume(op, f.pos));
        CompoundTag expected = pending.copy();
        removeBoth(expected, ADOPTION);
        same(f, expected, "resume may only remove adoption metadata");
        owner(f.save(), owner);
        absent(f.save(), ADOPTION);
        LabSupport.check(AdoptionService.nativeOnly(f.save()).equals(AdoptionService.nativeOnly(orphan)), "resume changed native data");
        LabSupport.check(GuardHooks.mayWork(f.machine), "explicitly resumed offline owner cannot work in wilderness");
        LabSupport.check(GuardHooks.mayConfigure(configActor(f, owner), f.machine)
            && GuardHooks.maySetArea(f.machine, LabSupport.area(f.machine)), "configuration remained locked after resume");
        same(f, expected, "mayWork must not mutate resumed NBT");
        inventory(f.machine);
        reject(f, "second resume", () -> AdoptionService.resume(op, f.pos));
        reject(f, "cancel after resume", () -> AdoptionService.cancel(op, f.pos));
        reject(f, "preview after resume cannot transfer", () -> AdoptionService.preview(op, f.pos, UUID.randomUUID()));
        reject(f, "replay final token", () -> AdoptionService.confirm(op, finalToken));
        LabSupport.check(teams.getPlayerTeamForPlayerID(owner).isEmpty()
            && f.server.getPlayerList().getPlayer(owner) == null, "adoption fabricated an online/known identity");
        verifyVolume(f.level, f.pos, true);
    }

    private static UUID preview(Fixture f, CommandSourceStack source, UUID owner) throws Exception {
        CompoundTag before = f.save();
        Set<Path> audits = auditFiles(f);
        UUID token = AdoptionService.preview(source, f.pos, owner);
        LabSupport.check(token != null, "preview returned no token");
        same(f, before, "preview");
        LabSupport.check(audits.equals(auditFiles(f)), "preview wrote audit files");
        return token;
    }

    private static ServerPlayer configActor(Fixture f, UUID owner) {
        var player = new ServerPlayer(f.server, f.level, new com.mojang.authlib.GameProfile(owner, "QGHeldOwner"),
            net.minecraft.server.level.ClientInformation.createDefault());
        player.setPos(f.pos.getX() + 0.5, f.pos.getY() + 1, f.pos.getZ() + 0.5);
        return player;
    }

    private static void configurationHeld(Fixture f, UUID owner) {
        ServerPlayer player = configActor(f, owner);
        var area = LabSupport.area(f.machine);
        var changed = new com.yogpc.qp.machine.Area(area.minX() + 1, area.minY(), area.minZ() + 1,
            area.maxX() - 1, area.maxY(), area.maxZ() - 1, net.minecraft.core.Direction.NORTH);
        LabSupport.check(GuardHooks.validArea(changed) && !changed.equals(area), "configuration test area not distinct/valid");
        CompoundTag before = f.save();
        LabSupport.check(!GuardHooks.mayConfigure(player, f.machine) && !GuardHooks.maySetArea(f.machine, changed), "held configuration authorized");
        if (f.machine instanceof QuarryEntity quarry) quarry.setArea(changed);
        else ((AdvQuarryEntity)f.machine).setArea(changed);
        same(f, before, "held direct setArea");
        if (!(f.machine instanceof AdvQuarryEntity advanced)) return;
        String state = advanced.toClientTag(new CompoundTag(), f.level.registryAccess()).getString("state");
        LabSupport.check(advanced.enabled && (state.equals("WAITING") || state.equals("FINISHED")), "configuration packet control was already ineligible");
        for (boolean syncArea : new boolean[]{false, true}) {
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                buffer.writeBlockPos(f.pos);
                buffer.writeResourceKey(f.level.dimension());
                buffer.writeBoolean(true);
                buffer.writeJsonWithCodec(com.yogpc.qp.machine.Area.CODEC.codec(), changed);
                var config = new com.google.gson.JsonObject();
                config.addProperty("startImmediately", true);
                config.addProperty("placeAreaFrame", false);
                config.addProperty("chunkByChunk", true);
                buffer.writeUtf(config.toString());
                buffer.writeBoolean(syncArea);
                com.yogpc.qp.machine.advquarry.AdvActionSyncMessage.STREAM_CODEC.decode(buffer).onReceive(f.level, player);
            } finally { buffer.release(); }
            same(f, before, "held native configuration packet");
        }
    }

    private static void inspect(Fixture f, CommandSourceStack source) throws Exception {
        CompoundTag before = f.save();
        String result = AdoptionService.inspect(source, f.pos);
        LabSupport.check(result != null && !result.isBlank(), "empty inspect result");
        same(f, before, "inspect");
    }

    @FunctionalInterface
    private interface Operation { void run() throws Exception; }

    private static void reject(Fixture f, String label, Operation operation) throws Exception {
        CompoundTag before = f.save();
        Set<Path> audits = auditFiles(f);
        boolean refused = false;
        try {
            operation.run();
        } catch (IllegalStateException expected) {
            refused = true;
        }
        LabSupport.check(refused, label + " unexpectedly succeeded");
        same(f, before, label);
        LabSupport.check(audits.equals(auditFiles(f)), label + " unexpectedly wrote an audit");
    }

    private static Set<Path> auditFiles(Fixture f) throws Exception {
        Path directory = f.server.getWorldPath(LevelResource.ROOT).toRealPath().resolve("quarryguard-audit");
        if (!Files.exists(directory)) return Set.of();
        LabSupport.check(!Files.isSymbolicLink(directory) && Files.isDirectory(directory), "unsafe audit directory");
        try (var entries = Files.list(directory)) {
            return entries.collect(java.util.stream.Collectors.toSet());
        }
    }

    private static void audited(Fixture f, CommandSourceStack source, String action, Operation operation) throws Exception {
        CompoundTag before = f.save();
        Set<Path> existing = auditFiles(f);
        operation.run();
        CompoundTag after = f.save();
        Set<Path> current = new HashSet<>(auditFiles(f));
        LabSupport.check(current.containsAll(existing), "operation removed existing audit evidence");
        current.removeAll(existing);
        LabSupport.check(current.size() == 1, "expected exactly one " + action + " audit");
        Path file = current.iterator().next();
        LabSupport.check(!Files.isSymbolicLink(file) && Files.isRegularFile(file), "invalid audit file");
        CompoundTag audit = TagParser.parseTag(Files.readString(file));
        LabSupport.check(audit.getInt("schema") == 1 && audit.getString("stage").equals("intent-before-mutation")
            && action.equals(audit.getString("action")) && audit.hasUUID("operation")
            && file.getFileName().toString().equals(audit.getUUID("operation") + ".snbt"), "invalid audit envelope");
        String operator = source.getEntity() == null ? "console:" + source.getTextName()
            : "entity:" + source.getEntity().getUUID();
        LabSupport.check(operator.equals(audit.getString("operator"))
            && source.getLevel().dimension().location().toString().equals(audit.getString("dimension"))
            && Arrays.equals(audit.getIntArray("pos"), new int[]{f.pos.getX(), f.pos.getY(), f.pos.getZ()}), "audit attribution mismatch");
        java.time.Instant.parse(audit.getString("timeUtc"));
        LabSupport.check(before.equals(audit.getCompound("before")) && after.equals(audit.getCompound("planned")), "audit before/planned differs from native snapshots");
        LabSupport.check(AdoptionService.nativeOnly(before).equals(AdoptionService.nativeOnly(after)), "audited action changed native data");
        if (action.equals("adopt")) LabSupport.check(after.getCompound(ADOPTION).getUUID("operation").equals(audit.getUUID("operation")), "hold/audit operation mismatch");
        same(f, after, "audit verification");
    }

    private static void held(Fixture f, UUID id, CompoundTag orphan) throws Exception {
        CompoundTag actual = f.save();
        owner(actual, id);
        LabSupport.check(actual.contains(ADOPTION, Tag.TAG_COMPOUND)
            && actual.getCompound("NeoForgeData").contains(ADOPTION, Tag.TAG_COMPOUND)
            && actual.getCompound(ADOPTION).equals(actual.getCompound("NeoForgeData").getCompound(ADOPTION)),
            "pending compound missing or inconsistent between root and NeoForgeData");
        LabSupport.check(AdoptionService.nativeOnly(actual).equals(AdoptionService.nativeOnly(orphan)), "confirm changed non-adoption/owner NBT");
        LabSupport.check(!GuardHooks.mayWork(f.machine), "confirm/reload implicitly resumed quarry");
        same(f, actual, "held mayWork");
        inventory(f.machine);
    }

    private static void frozenTicks(Fixture f) throws Exception {
        CompoundTag before = f.save();
        LabSupport.check(!GuardHooks.mayWork(f.machine), "refusing to tick authorized fixture");
        for (int tick = 0; tick < 25; tick++) {
            LabSupport.tick(f.machine);
            same(f, before, "suspended native tick " + tick);
        }
        inventory(f.machine);
        verifyVolume(f.level, f.pos, true);
    }

    private static void owner(CompoundTag tag, UUID id) {
        LabSupport.check(tag.hasUUID(OWNER) && tag.getUUID(OWNER).equals(id)
            && tag.getCompound("NeoForgeData").hasUUID(OWNER)
            && tag.getCompound("NeoForgeData").getUUID(OWNER).equals(id), "explicit owner missing/changed");
    }

    private static void absent(CompoundTag tag, String key) {
        LabSupport.check(!tag.contains(key) && !tag.getCompound("NeoForgeData").contains(key), key + " still present");
    }

    private static void removeBoth(CompoundTag tag, String key) {
        tag.remove(key);
        tag.getCompound("NeoForgeData").remove(key);
    }

    private static void same(Fixture f, CompoundTag before, String label) {
        LabSupport.check(f.level.getBlockEntity(f.pos) == f.machine, label + " changed BE identity");
        LabSupport.check(before.equals(f.save()), label + " changed NBT at " + f.pos);
    }

    private static MachineStorage storage(BlockEntity machine) throws Exception {
        var field = (machine instanceof QuarryEntity ? QuarryEntity.class : AdvQuarryEntity.class).getDeclaredField("storage");
        field.setAccessible(true);
        return (MachineStorage) field.get(machine);
    }

    private static void inventory(BlockEntity machine) throws Exception {
        LabSupport.check(((PowerEntity) machine).getEnergy() > 0, "fixture lost energy");
        LabSupport.check(storage(machine).getItemCount(Items.DIAMOND, DataComponentPatch.EMPTY) == 7
            && storage(machine).getFluidCount(Fluids.WATER) == 3L * MachineStorage.ONE_BUCKET, "fixture items/fluids changed");
        LabSupport.check(SENTINEL_VALUE.equals(machine.getPersistentData().getString(SENTINEL)), "sentinel changed");
    }

    private static BlockPos pos(int i) { return new BlockPos(4800 + i * 64, 64, 4800); }

    private static void verifyVolume(ServerLevel level, BlockPos center, boolean occupied) {
        var claims = FTBChunksAPI.api().getManager();
        if (!occupied) {
            for (int x = (center.getX() - 20) >> 4; x <= (center.getX() + 20) >> 4; x++) {
                for (int z = (center.getZ() - 20) >> 4; z <= (center.getZ() + 20) >> 4; z++) {
                    LabSupport.check(claims.getChunk(new ChunkDimPos(level.dimension(), x, z)) == null, "fixture volume already claimed");
                }
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-20, -4, -20), center.offset(20, 6, 20))) {
            if (occupied && (p.equals(center) || p.equals(center.below()))) continue;
            LabSupport.check(level.getBlockState(p).isAir() && level.getBlockEntity(p) == null, "fixture volume changed/occupied at " + p);
        }
        if (occupied) LabSupport.check(level.getBlockState(center.below()).is(Blocks.STONE)
            && level.getBlockEntity(center.below()) == null, "fixture support changed");
    }

    private static final class Fixture implements AutoCloseable {
        final MinecraftServer server;
        final ServerLevel level;
        final ClaimedChunkManagerImpl claims;
        final ChunkTeamDataImpl hostile;
        final BlockPos pos;
        BlockEntity machine;
        BlockState state;
        boolean ownsSupport;
        ClaimedChunk claim;
        ChunkDimPos claimPos;

        Fixture(MinecraftServer server, ClaimedChunkManagerImpl claims, ChunkTeamDataImpl hostile, BlockPos pos) {
            this.server = server;
            this.level = server.overworld();
            this.claims = claims;
            this.hostile = hostile;
            this.pos = pos;
        }

        void place(ServerPlayer placer, String id) throws Exception {
            verifyVolume(level, pos, false);
            ownsSupport = true;
            try {
                machine = LabSupport.place(level, placer, pos, id);
            } finally {
                // Placement can throw after the native block was installed.
                BlockEntity actual = level.getBlockEntity(pos);
                if (GuardHooks.isQuarry(actual)) {
                    machine = actual;
                    state = level.getBlockState(pos);
                }
            }
            var power = (PowerEntity) machine;
            power.setEnergy(power.getMaxEnergy(), false);
            storage(machine).addItem(new ItemStack(Items.DIAMOND, 7));
            storage(machine).addFluid(Fluids.WATER, 3L * MachineStorage.ONE_BUCKET);
            machine.getPersistentData().putString(SENTINEL, SENTINEL_VALUE);
            inventory(machine);
        }

        CompoundTag save() { return machine.saveWithFullMetadata(level.registryAccess()); }

        void load(CompoundTag tag) {
            machine.loadWithComponents(tag.copy(), level.registryAccess());
            machine.setChanged();
        }

        void reconstruct(CompoundTag tag) {
            LabSupport.check(level.getBlockEntity(pos) == machine && level.getBlockState(pos).equals(state), "fixture replaced externally");
            BlockEntity next = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
            LabSupport.check(next != null && next != machine, "native BE construction failed");
            next.setLevel(level);
            next.loadWithComponents(tag.copy(), level.registryAccess());
            level.removeBlockEntity(pos);
            machine = next;
            level.setBlockEntity(next);
            next.setChanged();
        }

        void claim() {
            var area = LabSupport.area(machine);
            claimPos = new ChunkDimPos(level.dimension(), area.minX() >> 4, area.minZ() >> 4);
            LabSupport.check(claim == null && claims.getChunk(claimPos) == null, "refusing existing claim");
            try {
                LabSupport.check(hostile.claim(server.createCommandSourceStack().withSuppressedOutput(), claimPos, false).isSuccess(), "hostile claim failed");
            } finally {
                var current = claims.getChunk(claimPos);
                if (current != null && current.getTeamData().getTeamId().equals(hostile.getTeamId())) claim = current;
            }
            LabSupport.check(claim != null, "hostile claim identity missing");
        }

        void unclaim() {
            if (claim == null) return;
            var current = claims.getChunk(claimPos);
            LabSupport.check(current == claim && current.getTeamData().getTeamId().equals(hostile.getTeamId()), "claim identity changed; preserve it");
            LabSupport.check(hostile.unclaim(server.createCommandSourceStack().withSuppressedOutput(), claimPos, false, true).isSuccess(), "hostile unclaim failed");
            LabSupport.check(claims.getChunk(claimPos) == null, "hostile claim remains");
            claim = null;
        }

        @Override public void close() {
            try {
                if (machine != null) {
                    LabSupport.check(level.getBlockEntity(pos) == machine && level.getBlockState(pos).equals(state), "BE identity changed; refusing cleanup");
                    // Detach first: never drop storage or trigger mining to clean a fixture.
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                if (ownsSupport) {
                    LabSupport.check(level.getBlockState(pos.below()).isAir()
                        || (level.getBlockState(pos.below()).is(Blocks.STONE) && level.getBlockEntity(pos.below()) == null), "support replaced; refusing cleanup");
                    level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2);
                }
            } finally {
                unclaim();
            }
            verifyVolume(level, pos, false);
        }
    }

}
