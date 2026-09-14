package fr.ascendant.quarryguard.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Test/benchmark alternatives, deliberately not shipped as a production strategy. */
final class Baselines {
    static final long NAIVE_CAP = 65_536;
    private final Map<String, Map<Point, UUID>> dimensions = new HashMap<>();

    record Point(int x, int z) {
        // Mix both coordinates: a record's default linear hash is poor for dense grids.
        @Override public int hashCode() {
            long v = ((long) x << 32) | (z & 0xffff_ffffL);
            v = (v ^ (v >>> 33)) * 0xff51afd7ed558ccdL;
            v = (v ^ (v >>> 33)) * 0xc4ceb9fe1a85ec53L;
            v ^= v >>> 33;
            return (int) (v ^ (v >>> 32));
        }
    }

    void put(String dim, int x, int z, UUID team) {
        dimensions.computeIfAbsent(dim, ignored -> new HashMap<>()).put(new Point(x, z), team);
    }

    void remove(String dim, int x, int z) {
        var claims = dimensions.get(dim);
        if (claims != null) {
            claims.remove(new Point(x, z));
            if (claims.isEmpty()) dimensions.remove(dim);
        }
    }

    void clear() {
        dimensions.clear();
    }

    Optional<ClaimIndex.Witness> naive(String dim, ChunkRect area, Predicate<UUID> allowed) {
        if (area.area() > NAIVE_CAP) {
            throw new IllegalArgumentException("Naive enumeration cap exceeded");
        }
        var claims = dimensions.get(dim);
        if (claims == null) return Optional.empty();
        // long induction variables avoid wraparound at Integer.MAX_VALUE.
        for (long x = area.minX(); x <= area.maxX(); x++) {
            for (long z = area.minZ(); z <= area.maxZ(); z++) {
                UUID team = claims.get(new Point((int) x, (int) z));
                if (team != null && !allowed.test(team)) {
                    return Optional.of(new ClaimIndex.Witness(dim, (int) x, (int) z, team));
                }
            }
        }
        return Optional.empty();
    }

    Optional<ClaimIndex.Witness> scan(String dim, ChunkRect area, Predicate<UUID> allowed) {
        var claims = dimensions.get(dim);
        if (claims == null) return Optional.empty();
        for (var entry : claims.entrySet()) {
            Point p = entry.getKey();
            if (area.contains(p.x(), p.z()) && !allowed.test(entry.getValue())) {
                return Optional.of(new ClaimIndex.Witness(dim, p.x(), p.z(), entry.getValue()));
            }
        }
        return Optional.empty();
    }
}
