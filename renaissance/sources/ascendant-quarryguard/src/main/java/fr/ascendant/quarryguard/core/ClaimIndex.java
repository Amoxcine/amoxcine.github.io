package fr.ascendant.quarryguard.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Sparse claims by dimension, X, then Z. No Minecraft access or authorization cache.
 * Confine all calls to one thread, or externally serialize them, including callbacks.
 * An empty result only describes this index: the caller must establish FTB readiness,
 * synchronization, ownership and any extra machine/marker policy separately.
 */
public final class ClaimIndex {
    private final Map<String, TreeMap<Integer, TreeMap<Integer, UUID>>> dimensions = new HashMap<>();
    private long revision;
    private int queryDepth;

    public record Witness(String dim, int x, int z, UUID team) {
        public Witness {
            requireDimension(dim);
            Objects.requireNonNull(team, "team");
        }
    }

    /** Returns true only when a claim was inserted or its team changed. */
    public boolean put(String dim, int x, int z, UUID team) {
        requireDimension(dim);
        Objects.requireNonNull(team, "team");
        requireMutable();
        var rows = dimensions.get(dim);
        var row = rows == null ? null : rows.get(x);
        if (row != null && team.equals(row.get(z))) {
            return false;
        }
        long next = Math.incrementExact(revision);
        if (rows == null) {
            rows = new TreeMap<>();
            dimensions.put(dim, rows);
        }
        if (row == null) {
            row = new TreeMap<>();
            rows.put(x, row);
        }
        row.put(z, team);
        revision = next;
        return true;
    }

    /** Returns true only when a claim existed; removes empty rows/dimensions. */
    public boolean remove(String dim, int x, int z) {
        requireDimension(dim);
        requireMutable();
        var rows = dimensions.get(dim);
        var row = rows == null ? null : rows.get(x);
        if (row == null || !row.containsKey(z)) {
            return false;
        }
        long next = Math.incrementExact(revision);
        row.remove(z);
        if (row.isEmpty()) {
            rows.remove(x);
        }
        if (rows.isEmpty()) {
            dimensions.remove(dim);
        }
        revision = next;
        return true;
    }

    /**
     * Returns the first denied claim in ascending signed X, then Z order.
     * Calls the live predicate only for intersecting claims, stopping on denial.
     * Predicate exceptions propagate. Mutating this index inside it is prohibited.
     * No result or per-team decision is retained between or within queries.
     */
    public Optional<Witness> queryDenied(String dim, ChunkRect area, Predicate<UUID> allowedTeam) {
        requireDimension(dim);
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(allowedTeam, "allowedTeam");
        var rows = dimensions.get(dim);
        if (rows == null) {
            return Optional.empty();
        }
        queryDepth++;
        try {
            for (var x : rows.subMap(area.minX(), true, area.maxX(), true).entrySet()) {
                for (var z : x.getValue().subMap(area.minZ(), true, area.maxZ(), true).entrySet()) {
                    if (!allowedTeam.test(z.getValue())) {
                        return Optional.of(new Witness(dim, x.getKey(), z.getKey(), z.getValue()));
                    }
                }
            }
            return Optional.empty();
        } finally {
            queryDepth--;
        }
    }

    /** Effective clears advance the revision once; clearing an empty index is a no-op. */
    public void clear() {
        requireMutable();
        if (!dimensions.isEmpty()) {
            long next = Math.incrementExact(revision);
            dimensions.clear();
            revision = next;
        }
    }

    /** Local claim mutations only. NOT a revision of alliances, ownership or FTB state. */
    public long revision() {
        return revision;
    }

    private void requireMutable() {
        if (queryDepth != 0) {
            throw new IllegalStateException("Cannot mutate ClaimIndex from a query predicate");
        }
    }

    private static void requireDimension(String dim) {
        Objects.requireNonNull(dim, "dim");
        if (dim.isBlank()) {
            throw new IllegalArgumentException("Blank dimension");
        }
    }
}
