package fr.ascendant.lunar.recall;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod("ascendant_lunar_recall")
public final class LunarRecall {
    private static volatile MinecraftServer activeServer;

    public LunarRecall() {
        RecallEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            activeServer = null;
            var config = RecallConfig.read(FMLPaths.CONFIGDIR.get().resolve(RecallConfig.FILE));
            var server = event.getServer();
            if (config.enabled(server.isDedicatedServer())) activeServer = server;
            LogUtils.getLogger().info("Lunar recall enabled={} dedicated={} scope=Mekanism-recall,YIGD-scroll/claim; native qualification pending",
                activeServer == server, server.isDedicatedServer());
        });
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
            if (activeServer != event.getServer()) return;
            for (String id : RecallPolicy.PROTECTED) {
                var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
                if (activeServer.getLevel(key) == null)
                    throw new IllegalStateException("Lunar recall enabled but protected dimension missing: " + id);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            if (activeServer == event.getServer()) activeServer = null;
        });
    }

    public static boolean active(Entity entity) {
        return entity != null && entity.level() instanceof ServerLevel level && level.getServer() == activeServer;
    }

    static boolean validThread(Entity entity) {
        return entity.level() instanceof ServerLevel level && level.getServer().isSameThread();
    }
}
