package ascendant.renaissance;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure policy only: does not inspect, connect or disconnect Minecraft networks. */
public final class TransportPolicy {
    public enum Decision {
        ALLOW_OUTSIDE, ALLOW_LOCAL, DENY_PERMANENT;

        public boolean allowed() {
            return this == ALLOW_OUTSIDE || this == ALLOW_LOCAL;
        }
    }

    /** Team must be resolved by trusted server-side ownership, never a client claim. */
    public record Endpoint(String dimension, UUID team) {
        public Endpoint {
            Objects.requireNonNull(dimension, "dimension");
            if (dimension.isBlank()) throw new IllegalArgumentException("Blank dimension");
        }
    }

    private final Set<String> protectedDimensions;

    public TransportPolicy(Set<String> dimensions) {
        if (dimensions.isEmpty()) throw new IllegalArgumentException("No protected dimensions");
        for (String dimension : dimensions) new Endpoint(dimension, null);
        this.protectedDimensions = Set.copyOf(dimensions);
    }

    /** A direct link, such as AE2's quantum bridge; both endpoint dimensions must be known. */
    public Decision directLink(Endpoint first, Endpoint second, Set<UUID> unlockedTeams) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(unlockedTeams, "unlockedTeams");
        boolean firstIn = protectedDimensions.contains(first.dimension());
        boolean secondIn = protectedDimensions.contains(second.dimension());
        if (!firstIn && !secondIn) return Decision.ALLOW_OUTSIDE;
        if (first.dimension().equals(second.dimension())) return Decision.ALLOW_LOCAL;
        return Decision.DENY_PERMANENT;
    }

    /**
     * A shared global pool cannot prove resource provenance. This deliberately gates
     * the lunar endpoint, including apparently same-dimension uses, permanently.
     */
    public Decision sharedPool(Endpoint local, Set<UUID> unlockedTeams) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(unlockedTeams, "unlockedTeams");
        if (!protectedDimensions.contains(local.dimension())) return Decision.ALLOW_OUTSIDE;
        return Decision.DENY_PERMANENT;
    }
}
