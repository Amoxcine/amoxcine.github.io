package local.lunar;

import appeng.api.networking.GridHelper;
import appeng.api.networking.events.GridSpatialEvent;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.LoggerFactory;

@Mod(LunarExtraRestrictions.ID)
public final class LunarExtraRestrictions {
    public static final String ID = "lunar_extra_restrictions";
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec SPEC;
    private record Session(MinecraftServer server, boolean enabled) {}
    private static volatile Session session;

    static {
        var builder = new ModConfigSpec.Builder();
        ENABLED = builder.comment("Standalone candidate. Opt in on the server; restart required.")
                .worldRestart().define("enabled", false);
        SPEC = builder.build();
    }

    public LunarExtraRestrictions(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, ID + "-common.toml");
        NeoForge.EVENT_BUS.addListener(this::start);
        NeoForge.EVENT_BUS.addListener(this::stop);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LunarExtraRestrictions::rightClick);
        GridHelper.addEventHandler(GridSpatialEvent.class, (grid, event) -> {
            if (shared(event.spatialIoLevel)) event.preventTransition();
        });
    }

    private void start(ServerAboutToStartEvent event) {
        boolean enabled = ENABLED.get();
        session = new Session(event.getServer(), enabled);
        LoggerFactory.getLogger(ID).info("Lunar extra restrictions candidate enabled={} (Moon + Moon orbit; restart-latched)", enabled);
    }

    private void stop(ServerStoppedEvent event) {
        Session current = session;
        if (current != null && current.server() == event.getServer()) session = null;
    }

    public static boolean enabled(Level level) {
        Session current = session;
        return current != null && current.enabled() && level instanceof ServerLevel serverLevel
                && current.server() == serverLevel.getServer();
    }

    public static boolean enabledOnServerThread() {
        Session current = session;
        return current != null && current.enabled() && current.server().isSameThread();
    }

    public static boolean nativePortal(Level level, String target) {
        return Rules.unprovenGrid(enabled(level), dimension(level), target);
    }

    private static String dimension(Level level) {
        return level == null ? null : level.dimension().location().toString();
    }

    public static boolean crossing(Level level, GlobalPos bound) {
        return Rules.crossing(enabled(level), dimension(level),
                bound == null ? null : bound.dimension().location().toString());
    }

    public static boolean shared(Level level) {
        return Rules.sharedAccess(enabled(level), dimension(level));
    }

    public static boolean unprovenGrid(Level level, GlobalPos bound) {
        return Rules.unprovenGrid(enabled(level), dimension(level),
                bound == null ? null : bound.dimension().location().toString());
    }

    private static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (shared(event.getLevel()) && event.getLevel().getBlockState(event.getPos()).is(Blocks.ENDER_CHEST)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }
}
