package fr.ascendant.renaissance.mekanism;

import fr.ascendant.renaissance.LunarConfig;
import mekanism.api.heat.ISidedHeatHandler;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class MekEndpointGate {
    private MekEndpointGate() { }
    public static boolean isRenaissance(BlockEntity endpoint) {
        return endpoint.getLevel() != null
            && LunarConfig.isProtected(endpoint.getLevel().dimension().location().toString());
    }
    public static boolean allowed(BlockEntity endpoint) {
        if (endpoint == null || endpoint.getLevel() == null) return false;
        return endpoint.getLevel().isClientSide() || !isRenaissance(endpoint);
    }
    public static boolean closedHeatHandler(ISidedHeatHandler handler) {
        return handler instanceof TileEntityQuantumEntangloporter qe && !allowed(qe);
    }
    public static void authorizationChanged(TileEntityQuantumEntangloporter endpoint) {
        if (!isRenaissance(endpoint) || !(endpoint.getLevel() instanceof ServerLevel level)) return;
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        endpoint.invalidateCapabilitiesFull();
    }
}
