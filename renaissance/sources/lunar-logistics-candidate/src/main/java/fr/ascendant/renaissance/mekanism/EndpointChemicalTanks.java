package fr.ascendant.renaissance.mekanism;

import java.util.List;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** One cached singleton per endpoint, not a map retaining every previously selected frequency. */
public final class EndpointChemicalTanks {
    private final TileEntityQuantumEntangloporter endpoint;
    private InventoryFrequency cachedFrequency;
    private IChemicalTank cachedTank;
    private ServerLevel cachedLevel;
    private BlockPos cachedPosition;
    private List<IChemicalTank> cachedViews = List.of();

    public EndpointChemicalTanks(TileEntityQuantumEntangloporter endpoint) {
        this.endpoint = endpoint;
    }

    public List<IChemicalTank> views(InventoryFrequency frequency, List<IChemicalTank> tanks) {
        if (!(endpoint.getLevel() instanceof ServerLevel level) || !MekEndpointGate.isRenaissance(endpoint))
            return tanks;
        BlockPos position = endpoint.getBlockPos();
        if (frequency == null || !validEndpoint(level, position) || endpoint.getFreq() != frequency)
            return List.of();
        if (tanks.isEmpty()) return List.of();
        var nativeTanks = frequency.getChemicalTanks(null);
        if (tanks.size() != 1 || nativeTanks.size() != 1 || tanks.getFirst() != nativeTanks.getFirst())
            throw new IllegalStateException("Pinned QE chemical singleton contract changed");
        IChemicalTank tank = tanks.getFirst();
        if (cachedFrequency != frequency || cachedTank != tank || cachedLevel != level
                || !position.equals(cachedPosition)) {
            BlockPos boundPosition = position.immutable();
            // Capture values, never mutable cache fields: an old view must not reconnect to a new stock.
            cachedViews = List.of(new EndpointChemicalTank(tank, () -> {
                if (!validEndpoint(level, boundPosition) || endpoint.getFreq() != frequency) return false;
                var current = frequency.getChemicalTanks(null);
                return current.size() == 1 && current.getFirst() == tank;
            }));
            cachedFrequency = frequency;
            cachedTank = tank;
            cachedLevel = level;
            cachedPosition = boundPosition;
        }
        return cachedViews;
    }

    private boolean validEndpoint(ServerLevel level, BlockPos position) {
        // Check thread/chunk before looking up the tile; this must never load a chunk.
        return level.getServer().isSameThread() && endpoint.getLevel() == level
            && !endpoint.isRemoved() && endpoint.getBlockPos().equals(position)
            && level.hasChunkAt(position) && level.getBlockEntity(position) == endpoint
            && MekEndpointGate.allowed(endpoint);
    }
}
