package fr.ascendant.renaissance.qio;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Lunar global-pool endpoints are closed without an installable authority. */
public final class QioEndpointGate {

    private QioEndpointGate() { }

    public static boolean denied(BlockEntity endpoint) {
        if (endpoint == null) return true;
        Level level = endpoint.getLevel();
        return QioScope.denyAutomatic(level != null && level.isClientSide,
            level == null ? null : level.dimension().location().toString(), null);
    }
}
