package fr.ascendant.quarryguard;

import com.yogpc.qp.machine.MachineStorage;
import com.yogpc.qp.machine.PowerEntity;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

/** Persist real operator adoption, then cancel/resume only after a different JVM loads it. */
public final class AdoptionRestartChecks {
    private static final String OWNER = "ascendant_quarryguard_owner";
    private AdoptionRestartChecks() { }

    public static String run(MinecraftServer server, boolean prepare) throws Exception {
        Path world = LabSupport.requireLab(server);
        Path path = world.resolve("quarryguard-adoption-test.properties");
        Properties record = new Properties();
        if (Files.exists(path)) try (var in = Files.newInputStream(path)) { record.load(in); }
        var level = server.overworld();
        var op = server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        if (prepare) {
            LabSupport.check(record.isEmpty() || "checked".equals(record.getProperty("stage")), "unfinished adoption fixture");
            for (int i = 0; i < 2; i++) empty(level, pos(i), false);
            var actor = LabSupport.actor(server, (TeamManagerImpl)FTBTeamsAPI.api().getManager(), "QGAdoptionRestart");
            record.clear();
            record.setProperty("format", "adoption5-v1");
            record.setProperty("stage", "preparing");
            record.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            save(path, record);
            for (int i = 0; i < 2; i++) {
                record.setProperty(i + ".owned", "true");
                save(path, record);
                var machine = (PowerEntity)LabSupport.place(level, actor, pos(i), i == 0 ? "quarry" : "adv_quarry");
                machine.setEnergy(machine.getMaxEnergy(), false);
                var field = (i == 0 ? QuarryEntity.class : AdvQuarryEntity.class).getDeclaredField("storage");
                field.setAccessible(true);
                var storage = (MachineStorage)field.get(machine);
                storage.addItem(new ItemStack(Items.DIAMOND, 7));
                storage.addFluid(Fluids.WATER, 3L * MachineStorage.ONE_BUCKET);
                machine.getPersistentData().putString("adoption5_sentinel", "native-fields-must-survive");
                CompoundTag orphan = tag(machine);
                orphan.remove(OWNER);
                orphan.getCompound("NeoForgeData").remove(OWNER);
                machine.loadWithComponents(orphan, level.registryAccess());
                orphan = tag(machine);
                command(server, op, "inspect " + coordinates(i), true);
                command(server, op, "adopt " + coordinates(i) + " " + actor.getUUID(), true);
                LabSupport.check(orphan.equals(tag(machine)), "command preview changed NBT");
                var token = AdoptionService.preview(op, pos(i), actor.getUUID());
                command(server, op.withPermission(0), "confirm " + token, false);
                command(server, op, "confirm " + token, true);
                CompoundTag adopted = tag(machine);
                LabSupport.check(adopted.hasUUID(OWNER) && adopted.contains(GuardHooks.ADOPTION)
                    && AdoptionService.nativeOnly(orphan).equals(AdoptionService.nativeOnly(adopted)), "adoption changed native data");
                frozen(machine);
                machine.setChanged();
                record.setProperty(i + ".original", orphan.toString());
                record.setProperty(i + ".adopted", tag(machine).toString());
                save(path, record);
            }
            record.setProperty("stage", "prepared");
            save(path, record);
            return "operator adoption prepared: both native quarries held, inventory/fluids/energy preserved, 25 blocked ticks each; new JVM required";
        }
        LabSupport.check("adoption5-v1".equals(record.getProperty("format")) && "prepared".equals(record.getProperty("stage")), "missing compatible adoption preparation");
        LabSupport.check(!Long.toString(ProcessHandle.current().pid()).equals(record.getProperty("pid")), "adoption check requires another JVM");
        var forced = new HashSet<>(level.getForcedChunks());
        for (int i = 0; i < 2; i++) {
            LabSupport.check("true".equals(record.getProperty(i + ".owned")), "unowned adoption fixture");
            BlockEntity machine = level.getBlockEntity(pos(i));
            LabSupport.check(GuardHooks.isQuarry(machine), "adoption machine missing after restart");
            CompoundTag expected = TagParser.parseTag(record.getProperty(i + ".adopted"));
            LabSupport.check(expected.equals(tag(machine)), "adopted full NBT changed across JVM: " + i);
            frozen(machine);
            empty(level, pos(i), true);
        }
        for (int i = 0; i < 2; i++) {
            BlockEntity machine = level.getBlockEntity(pos(i));
            CompoundTag original = TagParser.parseTag(record.getProperty(i + ".original"));
            if (i == 0) {
                command(server, op, "cancel-adoption " + coordinates(i), true);
                LabSupport.check(!tag(machine).contains(OWNER) && !tag(machine).contains(GuardHooks.ADOPTION)
                    && !GuardHooks.mayWork(machine), "cancel after restart failed");
            } else {
                command(server, op, "resume " + coordinates(i), true);
                LabSupport.check(tag(machine).hasUUID(OWNER) && !tag(machine).contains(GuardHooks.ADOPTION)
                    && GuardHooks.mayWork(machine), "resume after restart failed");
            }
            LabSupport.check(AdoptionService.nativeOnly(original).equals(AdoptionService.nativeOnly(tag(machine))), "operator action changed native data after restart");
        }
        LabSupport.check(forced.equals(new HashSet<>(level.getForcedChunks())), "adoption changed global forced chunks");
        record.setProperty("stage", "verified");
        save(path, record);
        for (int i = 0; i < 2; i++) {
            level.removeBlockEntity(pos(i));
            level.setBlock(pos(i), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos(i).below(), Blocks.AIR.defaultBlockState(), 2);
            empty(level, pos(i), false);
        }
        record.setProperty("stage", "checked");
        save(path, record);
        return "operator adoption PASS across JVMs: full NBT equality, held ticks, cancellation/resume, inventory/fluids/energy and global forced chunks preserved; fixtures removed";
    }

    private static CompoundTag tag(BlockEntity machine) { return machine.saveWithFullMetadata(machine.getLevel().registryAccess()); }

    private static void command(MinecraftServer server, net.minecraft.commands.CommandSourceStack source,
                                String command, boolean expected) throws Exception {
        int result;
        try { result = server.getCommands().getDispatcher().execute("quarryguard " + command, source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            if (expected) throw error;
            result = 0;
        }
        LabSupport.check((result > 0) == expected, "command result unexpected: " + command);
    }

    private static void frozen(BlockEntity machine) {
        CompoundTag before = tag(machine);
        LabSupport.check(!GuardHooks.mayWork(machine), "adopted machine not held");
        for (int tick = 0; tick < 25; tick++) LabSupport.tick(machine);
        LabSupport.check(before.equals(tag(machine)), "held native tick changed machine");
    }

    private static BlockPos pos(int i) { return new BlockPos(5200 + 64 * i, 64, 5200); }

    private static String coordinates(int i) { BlockPos p = pos(i); return p.getX() + " " + p.getY() + " " + p.getZ(); }

    private static void empty(ServerLevel level, BlockPos center, boolean machinePresent) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-20, -4, -20), center.offset(20, 6, 20))) {
            LabSupport.check(FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level.dimension(), p.getX() >> 4, p.getZ() >> 4)) == null, "adoption fixture claimed");
            if (machinePresent && p.equals(center)) continue;
            if (machinePresent && p.equals(center.below())) { LabSupport.check(level.getBlockState(p).is(Blocks.STONE), "support changed"); continue; }
            LabSupport.check(level.getBlockState(p).isAir() && level.getBlockEntity(p) == null, "adoption fixture volume changed at " + p);
        }
    }

    private static void save(Path path, Properties record) throws Exception {
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (var out = Files.newOutputStream(temp)) { record.store(out, "Disposable adoption fixture; never run on a player world"); }
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
