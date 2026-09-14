package fr.ascendant.renaissance;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod("ascendant_lunar_logistics")
public final class LunarLogistics {
    public LunarLogistics() {
        LogUtils.getLogger().info("Lunar logistics candidate enabled={} (config file, restart required, solo/dedicated)",
            LunarMixinPlugin.ENABLED);
        NeoForge.EVENT_BUS.addListener(this::starting);
        NeoForge.EVENT_BUS.addListener(LunarDiagnostics::register);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> LunarDiagnostics.clear());
    }

    private void starting(ServerStartingEvent event) {
        for (String dimension : LunarConfig.DIMENSIONS) {
            var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
            LunarConfig.validateDimension(LunarMixinPlugin.ENABLED, dimension, event.getServer().getLevel(key) != null);
        }
        if (LunarMixinPlugin.ENABLED) LogUtils.getLogger().warn(
            "Lunar logistics active for ad_astra:moon and ad_astra:moon_orbit: permanent bounded restrictions; no grants or journal migration; runtime qualification pending");
    }
}
