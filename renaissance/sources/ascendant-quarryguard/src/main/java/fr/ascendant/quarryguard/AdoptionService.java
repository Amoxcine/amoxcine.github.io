package fr.ascendant.quarryguard;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;

/** Explicit, two-step operator adoption. Never infers identity or restores native state. */
public final class AdoptionService {
    private static final String OWNER = "ascendant_quarryguard_owner";
    private static final long TTL_NANOS = 600_000_000_000L;
    private static final Map<UUID, Preview> PENDING = new HashMap<>();

    private record Preview(MinecraftServer server, ServerLevel level, BlockPos pos, BlockEntity machine,
                           CompoundTag before, UUID owner, String operator, long created) { }

    private AdoptionService() { }

    static void clear() { PENDING.clear(); }

    public static String inspect(CommandSourceStack source, BlockPos pos) {
        BlockEntity machine = machine(source, pos, false);
        CompoundTag tag = snapshot(machine);
        String owner = tag.hasUUID(OWNER) ? tag.getUUID(OWNER).toString() : "inconnu";
        String issue = GuardHooks.adoptionDataIssue(machine);
        return source.getLevel().dimension().location() + " " + pos.toShortString()
            + " | proprietaire=" + owner + " | attribution en pause=" + tag.contains(GuardHooks.ADOPTION)
            + " | quarantaine=" + tag.getString("ascendant_quarryguard_quarantine")
            + " | etat=" + tag.getString("state") + " | emprise=" + tag.get("area")
            + (issue == null ? "" : " | " + issue);
    }

    public static UUID preview(CommandSourceStack source, BlockPos pos, UUID owner) {
        BlockEntity machine = machine(source, pos, true);
        requireOrphan(machine);
        require(owner != null && !owner.equals(new UUID(0, 0)), "UUID de proprietaire invalide.");
        String operator = operator(source);
        long now = System.nanoTime();
        PENDING.entrySet().removeIf(e -> now - e.getValue().created >= TTL_NANOS
            || e.getValue().operator.equals(operator));
        require(PENDING.size() < 64, "Trop de confirmations en attente.");
        UUID token = UUID.randomUUID();
        PENDING.put(token, new Preview(source.getServer(), source.getLevel(), pos.immutable(), machine,
            snapshot(machine), owner, operator, now));
        return token;
    }

    public static void confirm(CommandSourceStack source, UUID token) throws IOException {
        assertOperator(source);
        Preview preview = PENDING.get(token);
        require(preview != null && preview.server == source.getServer()
            && preview.operator.equals(operator(source)), "Confirmation absente ou reservee a un autre operateur.");
        PENDING.remove(token);
        require(System.nanoTime() - preview.created < TTL_NANOS, "Confirmation expiree : refaire adopt.");
        require(source.getLevel() == preview.level, "Dimension differente : refaire adopt dans la bonne dimension.");
        BlockEntity machine = machine(source, preview.pos, true);
        require(machine == preview.machine, "La quarry a ete remplacee ou rechargee : refaire adopt.");
        requireOrphan(machine);
        CompoundTag before = snapshot(machine);
        require(before.equals(preview.before), "La quarry a change : inspecter puis refaire adopt.");
        UUID operation = UUID.randomUUID();
        CompoundTag hold = new CompoundTag();
        hold.putInt("schema", 1);
        hold.putUUID("owner", preview.owner);
        hold.putUUID("operation", operation);
        CompoundTag managed = machine.getPersistentData().copy();
        managed.putUUID(OWNER, preview.owner);
        managed.put(GuardHooks.ADOPTION, hold);
        commit(source, machine, "adopt", operation, before, managed);
    }

    public static void resume(CommandSourceStack source, BlockPos pos) throws IOException {
        BlockEntity machine = machine(source, pos, true);
        requirePendingAdoption(machine);
        require(GuardHooks.mayResumeAdoption(machine), "Reprise refusee : droits de claim, emprise ou quarantaine.");
        CompoundTag before = snapshot(machine);
        CompoundTag managed = machine.getPersistentData().copy();
        managed.remove(GuardHooks.ADOPTION);
        commit(source, machine, "resume", UUID.randomUUID(), before, managed);
    }

    public static void cancel(CommandSourceStack source, BlockPos pos) throws IOException {
        BlockEntity machine = machine(source, pos, true);
        requirePendingAdoption(machine);
        CompoundTag before = snapshot(machine);
        CompoundTag managed = machine.getPersistentData().copy();
        managed.remove(OWNER);
        managed.remove(GuardHooks.ADOPTION);
        commit(source, machine, "cancel-adoption", UUID.randomUUID(), before, managed);
    }

    private static void assertOperator(CommandSourceStack source) {
        require(source.hasPermission(2), "Commande reservee aux operateurs (niveau 2).");
        require(source.getServer().isSameThread(), "Operation uniquement sur le fil serveur.");
    }

    private static String operator(CommandSourceStack source) {
        return source.getEntity() == null ? "console:" + source.getTextName()
            : "entity:" + source.getEntity().getUUID();
    }

    private static BlockEntity machine(CommandSourceStack source, BlockPos pos, boolean requireReady) {
        assertOperator(source);
        ServerLevel level = source.getLevel();
        require(!requireReady || GuardHooks.operatorReady(level), "Protection indisponible : aucune modification.");
        require(level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4), "Chunk non charge : approchez la quarry.");
        BlockEntity machine = level.getBlockEntity(pos);
        require(GuardHooks.isQuarry(machine) && !machine.isRemoved(), "Aucune quarry normale ou avancee a cette position.");
        return machine;
    }

    private static void requireOrphan(BlockEntity machine) {
        require(!machine.getPersistentData().contains(OWNER), "Proprietaire deja present : aucun transfert automatique.");
        require(!machine.getPersistentData().contains(GuardHooks.ADOPTION), "Attribution deja en attente.");
        String issue = GuardHooks.adoptionDataIssue(machine);
        require(issue == null, issue);
    }

    private static void requirePendingAdoption(BlockEntity machine) {
        CompoundTag data = machine.getPersistentData();
        require(data.hasUUID(OWNER) && data.contains(GuardHooks.ADOPTION, Tag.TAG_COMPOUND), "Aucune attribution en pause a traiter.");
        CompoundTag hold = data.getCompound(GuardHooks.ADOPTION);
        require(hold.getInt("schema") == 1 && hold.hasUUID("operation") && hold.hasUUID("owner")
            && hold.getUUID("owner").equals(data.getUUID(OWNER)), "Metadonnees d'attribution incoherentes : aucune modification.");
    }

    private static CompoundTag snapshot(BlockEntity machine) {
        return machine.saveWithFullMetadata(machine.getLevel().registryAccess()).copy();
    }

    private static void commit(CommandSourceStack source, BlockEntity machine, String action, UUID operation,
                               CompoundTag before, CompoundTag managed) throws IOException {
        CompoundTag previous = machine.getPersistentData().copy();
        CompoundTag planned = before.copy();
        copyManaged(managed, planned);
        CompoundTag nested = planned.getCompound("NeoForgeData").copy();
        copyManaged(managed, nested);
        planned.put("NeoForgeData", nested);
        // A durable write-ahead record is required before changing the machine.
        Path audit = writeIntent(source, machine, action, operation, before, planned);
        require(machine(source, machine.getBlockPos(), true) == machine && before.equals(snapshot(machine)),
            "La quarry a change pendant la preparation : aucune modification.");
        if (action.equals("resume")) require(GuardHooks.mayResumeAdoption(machine), "Les droits de reprise ont change.");
        try {
            copyManaged(managed, machine.getPersistentData());
            machine.setChanged();
            CompoundTag after = snapshot(machine);
            require(nativeOnly(before).equals(nativeOnly(after)), "Verification du contenu echouee.");
            for (String key : new String[]{OWNER, GuardHooks.ADOPTION}) {
                require(java.util.Objects.equals(planned.get(key), after.get(key)), "Verification des metadonnees echouee.");
            }
        } catch (RuntimeException failure) {
            copyManaged(previous, machine.getPersistentData());
            machine.setChanged();
            throw failure;
        }
        LogUtils.getLogger().info("QuarryGuard operator action applied: action={}, operator={}, dimension={}, pos={}, audit={}",
            action, operator(source), source.getLevel().dimension().location(), machine.getBlockPos(), audit);
    }

    private static void copyManaged(CompoundTag from, CompoundTag to) {
        for (String key : new String[]{OWNER, GuardHooks.ADOPTION}) {
            to.remove(key);
            if (from.contains(key)) to.put(key, from.get(key).copy());
        }
    }

    static CompoundTag nativeOnly(CompoundTag tag) {
        CompoundTag copy = tag.copy();
        CompoundTag nested = copy.getCompound("NeoForgeData").copy();
        for (String key : new String[]{OWNER, GuardHooks.ADOPTION}) { copy.remove(key); nested.remove(key); }
        if (nested.isEmpty()) copy.remove("NeoForgeData");
        else copy.put("NeoForgeData", nested);
        return copy;
    }

    private static Path writeIntent(CommandSourceStack source, BlockEntity machine, String action, UUID operation,
                                    CompoundTag before, CompoundTag planned) throws IOException {
        Path world = source.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        Path directory = world.resolve("quarryguard-audit");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory);
        if (Files.isSymbolicLink(directory) || !directory.toRealPath().getParent().equals(world)) {
            throw new IOException("Dossier d'audit hors du monde : operation refusee.");
        }
        CompoundTag record = new CompoundTag();
        record.putInt("schema", 1);
        record.putString("stage", "intent-before-mutation");
        record.putString("action", action);
        record.putUUID("operation", operation);
        record.putString("operator", operator(source));
        record.putString("operatorName", source.getTextName());
        record.putString("timeUtc", Instant.now().toString());
        record.putString("dimension", source.getLevel().dimension().location().toString());
        record.putIntArray("pos", new int[]{machine.getBlockPos().getX(), machine.getBlockPos().getY(), machine.getBlockPos().getZ()});
        record.put("before", before.copy());
        record.put("planned", planned.copy());
        Path file = directory.resolve(operation + ".snbt");
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(record.toString());
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        return file;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
