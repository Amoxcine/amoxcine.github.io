package fr.ascendant.etrionic;

import com.mojang.logging.LogUtils;
import earth.terrarium.adastra.common.blockentities.machines.EtrionicBlastFurnaceBlockEntity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

@Mod("ascendant_etrionic_guard")
public final class EtrionicGuard {
    private static final String PIN = "C631DEEE98B7A0149C5CD07648EFFE78B6488ADD255B6CC7B23FA12EB91B1E81";
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue DISABLE_BLASTING;
    private static volatile MinecraftServer activeServer;
    private static long tickVetoes, craftVetoes;
    static {
        var b = new ModConfigSpec.Builder();
        DISABLE_BLASTING = b.comment("Restart required. Disable Etrionic BLASTING execution in all dimensions; native ALLOYING and other furnaces untouched.")
            .define("disableEtrionicBlasting", false);
        SPEC = b.build();
    }
    public EtrionicGuard(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "ascendant-etrionic-guard.toml");
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> {
            activeServer = null; tickVetoes = 0; craftVetoes = 0;
            boolean enabled = DISABLE_BLASTING.get();
            if(enabled) {
                verifyBinary();
                activeServer = e.getServer();
            }
            LogUtils.getLogger().warn("Etrionic guard active={} target=1.16.24 mode=BLASTING_ONLY alloying=NATIVE runtimeQualification=PENDING", enabled);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            if(activeServer == e.getServer()) activeServer = null;
            tickVetoes = 0; craftVetoes = 0;
        });
        NeoForge.EVENT_BUS.addListener(EtrionicGuard::commands);
    }
    private static void verifyBinary() {
        try {
            var path = ModList.get().getModFileById("ad_astra").getFile().getFilePath();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try(var in=Files.newInputStream(path)) {
                byte[] buffer=new byte[65536]; int read;
                while((read=in.read(buffer))!=-1) digest.update(buffer,0,read);
            }
            if(!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(PIN))
                throw new IllegalStateException("Loaded Ad Astra differs from pinned 1.16.24; requalification required");
        } catch(Exception e) { throw new IllegalStateException("Cannot enable Etrionic guard on unverified binary",e); }
    }
    public static boolean active(EtrionicBlastFurnaceBlockEntity machine) {
        return machine.getLevel() instanceof ServerLevel level && level.getServer() == activeServer;
    }
    public static void recordTickVeto() { if(tickVetoes<Long.MAX_VALUE) tickVetoes++; }
    public static void recordCraftVeto() { if(craftVetoes<Long.MAX_VALUE) craftVetoes++; }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("etrionic_guard").requires(s -> s.hasPermission(4))
            .then(Commands.literal("status").executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal("Etrionic guard active="+(c.getSource().getServer()==activeServer)
                    +" configured="+DISABLE_BLASTING.get()+" restartRequiredForChange=true tickVetoes="+tickVetoes+" craftVetoes="+craftVetoes
                    +" scope=ALL_DIMENSIONS_ETRIONIC_BLASTING_ONLY"),false); return 1;
            }))
            .then(Commands.literal("inspect").then(Commands.argument("position",BlockPosArgument.blockPos()).executes(c -> {
                var pos=BlockPosArgument.getLoadedBlockPos(c,"position");
                if(!(c.getSource().getLevel().getBlockEntity(pos) instanceof EtrionicBlastFurnaceBlockEntity f)) {
                    c.getSource().sendFailure(Component.literal("Not an Etrionic furnace")); return 0;
                }
                StringBuilder slots=new StringBuilder();
                for(int i=0;i<f.getContainerSize();i++) slots.append(i).append('=').append(f.getItem(i)).append(';');
                c.getSource().sendSuccess(() -> Component.literal("Etrionic active="+active(f)+" mode="+f.mode()+" FE="+f.getEnergyStorage().getStoredAmount()
                    +" cook="+f.cookTime()+"/"+f.cookTimeTotal()+" slots="+slots+" gameTime="+c.getSource().getLevel().getGameTime()),false);
                return 1;
            }))));
    }
}
