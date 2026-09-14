package fr.ascendant.renaissance;

import appeng.me.cluster.implementations.QuantumCluster;
import ascendant.renaissance.TransportPolicy;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

/** Two live endpoint levels establish a direct local link. No SavedData access. */
public final class QuantumHooks {
    private static final TransportPolicy POLICY = new TransportPolicy(LunarConfig.DIMENSIONS);
    private QuantumHooks() { }

    public static Object filter(QuantumCluster self, Object candidate) {
        var center = self.getCenter();
        if (center == null || center.getLevel() == null) return null;
        if (center.getLevel().isClientSide()) return candidate;
        if (!(center.getLevel() instanceof ServerLevel local) || !local.getServer().isSameThread()) return null;
        if (!(candidate instanceof QuantumCluster other)) return null;
        var remoteCenter = other.getCenter();
        if (remoteCenter == null || !(remoteCenter.getLevel() instanceof ServerLevel remote)) return null;
        if (self.isDestroyed() || other.isDestroyed() || center.isRemoved() || remoteCenter.isRemoved()
            || local.getServer() != remote.getServer()
            || local.getServer().getLevel(local.dimension()) != local
            || local.getServer().getLevel(remote.dimension()) != remote) return null;
        var decision = POLICY.directLink(
            new TransportPolicy.Endpoint(local.dimension().location().toString(), null),
            new TransportPolicy.Endpoint(remote.dimension().location().toString(), null), Set.of());
        // Null follows AE2's native destruction/clear path, including existing links.
        return decision.allowed() ? candidate : null;
    }
}
