package fr.ascendant.lunar.travel;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod("ascendant_lunar_travel")
public final class LunarTravel {
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue SOLO, DEDICATED;
    private static volatile MinecraftServer activeServer;
    private static ArrivalLedger arrivals;
    private static FlightJournal flights;
    static {
        var builder = new ModConfigSpec.Builder();
        SOLO = builder.comment("Restart required. Finite first-arrival cargo and native rocket restrictions.")
            .define("enabledSolo", false);
        DEDICATED = builder.comment("Restart required. No LAB/IP/world-name requirement.")
            .define("enabledDedicated", false);
        SPEC = builder.build();
    }

    public LunarTravel(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(CargoDiagnostics::register);
        NeoForge.EVENT_BUS.addListener(TravelDiagnostics::register);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, true, TravelDiagnostics::veto);
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "ascendant-lunar-travel.toml");
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            activeServer = null;
            arrivals = null;
            flights = null;
            var server = event.getServer();
            boolean enabled = server.isDedicatedServer() ? DEDICATED.get() : SOLO.get();
            if (enabled && ModList.get().isLoaded("ascendant_travel_lab"))
                throw new IllegalStateException("Remove old travel LAB before opting into lunar candidate.");
            if (enabled) {
                arrivals = new ArrivalLedger(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/ascendant_lunar_arrivals"));
                flights = new FlightJournal(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/ascendant_lunar_flights"));
                activeServer = server;
            }
            LogUtils.getLogger().warn("Lunar travel candidate active={} cargo=FINITE_SCAN firstArrival=SERVER_LEDGER nativeRoundTrip=UNQUALIFIED", enabled);
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            if (activeServer == event.getServer() && activeServer.getLevel(key(TravelPolicy.MOON)) == null)
                throw new IllegalStateException("Lunar restrictions enabled but ad_astra:moon is missing.");
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            if (activeServer == event.getServer()) { activeServer = null; arrivals = null; flights = null; }
            NativeFlight.clear();
            TravelDiagnostics.clear();
        });
        NeoForge.EVENT_BUS.addListener((EntityTravelToDimensionEvent event) -> {
            var entity = event.getEntity();
            if (blocked(entity, event.getDimension()) && !NativeFlight.consumeTransition(entity, event.getDimension()))
                event.setCanceled(true);
        });
    }

    public static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
    }

    static ArrivalLedger ledger(MinecraftServer server) {
        if (server != activeServer || arrivals == null) throw new IllegalStateException("Arrival ledger unavailable");
        return arrivals;
    }
    static FlightJournal flights(MinecraftServer server) {
        if (server != activeServer || flights == null) throw new IllegalStateException("Flight journal unavailable");
        return flights;
    }
    static boolean enabled(MinecraftServer server) { return server != null && server == activeServer; }

    public static boolean active(Entity entity) {
        return entity != null && entity.level() instanceof ServerLevel level && level.getServer() == activeServer;
    }

    public static boolean moon(Entity entity) {
        return active(entity) && TravelPolicy.protectedWorld(entity.level().dimension().location().toString());
    }

    public static boolean blocked(Entity entity, ResourceKey<Level> target) {
        return active(entity) && TravelPolicy.blocksTeleport(entity.level().dimension().location().toString(),
            target == null ? null : target.location().toString());
    }
}
