package fr.ascendant.quarryguard.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

public final class CoreTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final Predicate<UUID> ALL = team -> true;
    private static final Predicate<UUID> NONE = team -> false;
    private static final String DIM = "minecraft:overworld";
    private static int assertions;
    private record Key(String dim, int x, int z) {}

    public static void main(String[] args) throws Exception {
        geometry();
        fixedClaims();
        contracts();
        randomized();
        System.out.println("PASS: " + assertions + " assertions; fixed seeds; 10000 exact-area cases; "
                + "2000 block-projection cases; 8000 mutation/query steps.");
        System.out.println("Pure Java 21 only. No Minecraft integration, authorization cache or MSPT measurement.");
    }

    private static void geometry() {
        int[] coords = {-33, -32, -17, -16, -15, -1, 0, 1, 15, 16, 17, 31, 32};
        int[] chunks = {-3, -2, -2, -1, -1, -1, 0, 0, 0, 1, 1, 1, 2};
        for (int i = 0; i < coords.length; i++) {
            eq(new ChunkRect(chunks[i], chunks[i], chunks[i], chunks[i]),
                    ChunkRect.fromBlocks(coords[i], coords[i], coords[i], coords[i]), "floor division");
        }
        eq(9L, ChunkRect.fromBlocks(-1, -1, 16, 16).area(), "advanced quarry border");
        eq(16L, ChunkRect.fromBlocks(0, 0, 255, 0).area(), "aligned 256 blocks");
        eq(17L, ChunkRect.fromBlocks(1, 0, 256, 0).area(), "unaligned 256 blocks");
        eq(1L << 56, ChunkRect.fromBlocks(Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE).area(), "full int block projection");
        eq(1L << 32, new ChunkRect(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0).area(), "full X axis");
        eq(9_223_372_032_559_808_512L,
                new ChunkRect(Integer.MIN_VALUE, 1, Integer.MAX_VALUE, Integer.MAX_VALUE).area(),
                "largest height under overflow for full width");
        fails(IllegalArgumentException.class,
                () -> new ChunkRect(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, Integer.MAX_VALUE));
        fails(IllegalArgumentException.class,
                () -> new ChunkRect(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        fails(IllegalArgumentException.class, () -> new ChunkRect(1, 0, 0, 0));
        fails(IllegalArgumentException.class, () -> new ChunkRect(0, 1, 0, 0));
        fails(IllegalArgumentException.class, () -> ChunkRect.fromBlocks(1, 0, 0, 0));
        fails(IllegalArgumentException.class, () -> ChunkRect.fromBlocks(-1, -1, -1, -2));
        Random r = new Random(0xCAFE21L);
        for (int i = 0; i < 10_000; i++) {
            int a = r.nextInt(), b = r.nextInt(), c = r.nextInt(), d = r.nextInt();
            int x0 = Math.min(a, b), x1 = Math.max(a, b), z0 = Math.min(c, d), z1 = Math.max(c, d);
            BigInteger expected = BigInteger.valueOf(x1).subtract(BigInteger.valueOf(x0)).add(BigInteger.ONE)
                    .multiply(BigInteger.valueOf(z1).subtract(BigInteger.valueOf(z0)).add(BigInteger.ONE));
            if (expected.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                fails(IllegalArgumentException.class, () -> new ChunkRect(x0, z0, x1, z1));
            } else {
                ChunkRect rect = new ChunkRect(x0, z0, x1, z1);
                eq(expected.longValueExact(), rect.area(), "BigInteger area oracle");
                check(rect.contains(x0, z0) && rect.contains(x1, z1), "inclusive corners");
            }
        }
        for (int i = 0; i < 2_000; i++) {
            int x0 = r.nextInt(401) - 200, z0 = r.nextInt(401) - 200;
            int x1 = x0 + r.nextInt(40), z1 = z0 + r.nextInt(40);
            ChunkRect rect = ChunkRect.fromBlocks(x0, z0, x1, z1);
            var touched = new java.util.HashSet<Baselines.Point>();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    int cx = (int) Math.floor(x / 16.0), cz = (int) Math.floor(z / 16.0);
                    check(rect.contains(cx, cz), "block coverage");
                    touched.add(new Baselines.Point(cx, cz));
                }
            }
            eq((long) touched.size(), rect.area(), "no extra chunks");
        }
    }

    private static void fixedClaims() {
        ClaimIndex index = new ClaimIndex();
        ChunkRect rect = new ChunkRect(-2, -2, 2, 2);
        Predicate<UUID> unexpected = team -> { throw new AssertionError("Unrelated predicate call"); };
        check(index.queryDenied(DIM, rect, unexpected).isEmpty(), "empty dimension");
        index.put(DIM, 0, 0, B);
        eq(new ClaimIndex.Witness(DIM, 0, 0, B), index.queryDenied(DIM, rect, NONE).orElseThrow(), "center claim");
        for (int[] p : new int[][]{{-2,-2},{-2,2},{2,-2},{2,2}}) {
            index.clear();
            index.put(DIM, p[0], p[1], B);
            check(index.queryDenied(DIM, rect, NONE).isPresent(), "border claim");
        }
        index.clear();
        for (int i = 0; i < 5_000; i++) {
            index.put(DIM, i - 2_500, 10_000, B);
            index.put("minecraft:the_nether", i - 2_500, 0, B);
        }
        check(index.queryDenied(DIM, rect, unexpected).isEmpty(), "unrelated Z and dimensions");
        index.put(DIM, 0, 0, A);
        int[] calls = {0};
        check(index.queryDenied(DIM, rect, t -> { calls[0]++; return true; }).isEmpty(), "allowed center");
        eq(1, calls[0], "predicate only intersects");
        index.clear();
        for (int x = -32; x < 32; x++) for (int z = -32; z < 32; z++) index.put(DIM, x, z, A);
        calls[0] = 0;
        ChunkRect dense = new ChunkRect(-32, -32, 31, 31);
        check(index.queryDenied(DIM, dense, t -> { calls[0]++; return true; }).isEmpty(), "dense allowed");
        eq(4096, calls[0], "no per-team authorization cache");
        index.put(DIM, 31, 31, B);
        eq(new ClaimIndex.Witness(DIM, 31, 31, B), index.queryDenied(DIM, dense, A::equals).orElseThrow(), "last denial");
        calls[0] = 0;
        eq(new ClaimIndex.Witness(DIM, -32, -32, A), index.queryDenied(DIM, dense, t -> { calls[0]++; return false; }).orElseThrow(), "first denial");
        eq(1, calls[0], "early exit");
        index.clear();
        index.put(DIM, Integer.MAX_VALUE, 0, B);
        index.put(DIM, Integer.MIN_VALUE, 0, A);
        ChunkRect huge = new ChunkRect(Integer.MIN_VALUE, -1, Integer.MAX_VALUE, 1);
        eq(new ClaimIndex.Witness(DIM, Integer.MAX_VALUE, 0, B), index.queryDenied(DIM, huge, A::equals).orElseThrow(), "huge sparse X");
        index.put(DIM, 0, Integer.MIN_VALUE, A);
        index.put(DIM, 0, Integer.MAX_VALUE, B);
        eq(new ClaimIndex.Witness(DIM, 0, Integer.MAX_VALUE, B), index.queryDenied(DIM,
                new ChunkRect(0, Integer.MIN_VALUE, 0, Integer.MAX_VALUE), A::equals).orElseThrow(), "huge sparse Z");
        Baselines base = new Baselines();
        base.put(DIM, Integer.MAX_VALUE, Integer.MAX_VALUE, B);
        check(base.naive(DIM, new ChunkRect(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), NONE).isPresent(), "naive MAX terminates");
        check(base.naive(DIM, new ChunkRect(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), ALL).isEmpty(), "naive MAX allowed terminates");
        fails(IllegalArgumentException.class, () -> base.naive(DIM, huge, ALL));
        // Policy composes the machine as a separate singleton, not a bounding-box union.
        index.clear();
        index.put(DIM, 20, 20, B);
        check(index.queryDenied(DIM, rect, NONE).isEmpty(), "machine not silently in area");
        check(index.queryDenied(DIM, new ChunkRect(20, 20, 20, 20), NONE).isPresent(), "separate machine check");
        index.put(DIM, -1, -1, A);
        index.put(DIM, 0, 0, A);
        index.put(DIM, 1, 1, B);
        var owners = new java.util.HashSet<UUID>();
        long beforeCollection = index.revision();
        check(index.queryDenied(DIM, rect, team -> { owners.add(team); return true; }).isEmpty(), "collect owners traverses all");
        eq(java.util.Set.of(A, B), owners, "distinct intersecting owners");
        eq(beforeCollection, index.revision(), "collection is read-only");
        index.remove(DIM, 1, 1);
        owners.clear();
        index.queryDenied(DIM, rect, team -> { owners.add(team); return true; });
        eq(java.util.Set.of(A), owners, "owner coverage changes after removal");
        check(index.revision() != beforeCollection, "coverage cache revision invalidated");
    }

    private static void contracts() throws Exception {
        ClaimIndex index = new ClaimIndex();
        ChunkRect one = new ChunkRect(0, 0, 0, 0);
        fails(NullPointerException.class, () -> index.put(null, 0, 0, A));
        fails(IllegalArgumentException.class, () -> index.put(" ", 0, 0, A));
        fails(NullPointerException.class, () -> index.put(DIM, 0, 0, null));
        fails(NullPointerException.class, () -> index.remove(null, 0, 0));
        fails(IllegalArgumentException.class, () -> index.remove("", 0, 0));
        fails(NullPointerException.class, () -> index.queryDenied(DIM, null, ALL));
        fails(NullPointerException.class, () -> index.queryDenied(DIM, one, null));
        fails(NullPointerException.class, () -> index.queryDenied(null, one, ALL));
        fails(IllegalArgumentException.class, () -> index.queryDenied("\t", one, ALL));
        fails(NullPointerException.class, () -> new ClaimIndex.Witness(DIM, 0, 0, null));
        fails(IllegalArgumentException.class, () -> new ClaimIndex.Witness("", 0, 0, A));
        eq(0L, index.revision(), "invalid input no mutation");
        index.clear();
        check(!index.remove(DIM, 0, 0), "absent remove");
        eq(0L, index.revision(), "empty no-ops");
        check(index.put(DIM, 0, 0, A), "insert");
        check(!index.put(DIM, 0, 0, new UUID(0, 1)), "equal team no-op");
        eq(1L, index.revision(), "revision unchanged by no-op");
        check(index.put(DIM, 0, 0, B), "replace");
        eq(2L, index.revision(), "replace revision");
        long rev = index.revision();
        boolean[] allowed = {true};
        check(index.queryDenied(DIM, one, t -> allowed[0]).isEmpty(), "live initially allowed");
        allowed[0] = false;
        check(index.queryDenied(DIM, one, t -> allowed[0]).isPresent(), "live denial without claim mutation");
        eq(rev, index.revision(), "claims not alliance revision");
        RuntimeException sentinel = new IllegalStateException("predicate failure");
        try {
            index.queryDenied(DIM, one, t -> { throw sentinel; });
            throw new AssertionError("Exception swallowed");
        } catch (RuntimeException actual) { check(actual == sentinel, "propagate original failure"); }
        for (Runnable mutation : List.<Runnable>of(() -> index.put(DIM, 1, 1, A),
                () -> index.put(DIM, 0, 0, A), () -> index.remove(DIM, 0, 0), index::clear)) {
            fails(IllegalStateException.class, () -> index.queryDenied(DIM, one, t -> { mutation.run(); return true; }));
            eq(rev, index.revision(), "reentrant mutation rejected before change");
        }
        check(index.queryDenied(DIM, one, t -> index.queryDenied(DIM, one, ALL).isEmpty()).isEmpty(), "nested reads");
        check(index.remove(DIM, 0, 0), "last claim cleanup after exceptions");
        eq(3L, index.revision(), "remove revision");
        index.put(DIM, 0, 0, A);
        index.put("mod:other", 0, 0, B);
        index.clear();
        eq(6L, index.revision(), "clear one revision for all dimensions");
        check(index.queryDenied("mod:other", one, NONE).isEmpty(), "clear all dimensions");
        // Force an otherwise unreachable counter boundary without expanding public API.
        var revisionField = ClaimIndex.class.getDeclaredField("revision");
        revisionField.setAccessible(true);
        index.put(DIM, 0, 0, A);
        revisionField.setLong(index, Long.MAX_VALUE);
        fails(ArithmeticException.class, () -> index.put(DIM, 0, 0, B));
        fails(ArithmeticException.class, () -> index.remove(DIM, 0, 0));
        fails(ArithmeticException.class, index::clear);
        fails(ArithmeticException.class, () -> index.put("other", 1, 1, B));
        eq(new ClaimIndex.Witness(DIM, 0, 0, A), index.queryDenied(DIM, one, NONE).orElseThrow(), "overflow atomic");
        check(!index.put(DIM, 0, 0, A), "overflow no-op allowed");
        check(!index.remove("other", 1, 1), "overflow no-op remove");
        eq(Long.MAX_VALUE, index.revision(), "no revision wrap");
    }

    private static void randomized() {
        Random random = new Random(0x51A7E21L);
        ClaimIndex index = new ClaimIndex();
        Baselines base = new Baselines();
        Map<Key, UUID> oracle = new HashMap<>();
        String[] dims = {DIM, "minecraft:the_nether", "mod:moon"};
        long expectedRevision = 0;
        for (int step = 0; step < 8_000; step++) {
            String dim = dims[random.nextInt(dims.length)];
            int x = random.nextInt(81) - 40, z = random.nextInt(81) - 40;
            Key key = new Key(dim, x, z);
            int op = random.nextInt(100);
            if (op < 60) {
                UUID team = random.nextBoolean() ? A : B;
                boolean changed = !team.equals(oracle.put(key, team));
                eq(changed, index.put(dim, x, z, team), "random put");
                base.put(dim, x, z, team);
                if (changed) expectedRevision++;
            } else if (op < 95) {
                boolean changed = oracle.remove(key) != null;
                eq(changed, index.remove(dim, x, z), "random remove");
                base.remove(dim, x, z);
                if (changed) expectedRevision++;
            } else if (op == 99 && step % 137 == 0) {
                if (!oracle.isEmpty()) expectedRevision++;
                index.clear(); base.clear(); oracle.clear();
            }
            eq(expectedRevision, index.revision(), "random revision");
            int xx = random.nextInt(81) - 40, zz = random.nextInt(81) - 40;
            ChunkRect rect = new ChunkRect(Math.min(x, xx), Math.min(z, zz), Math.max(x, xx), Math.max(z, zz));
            Predicate<UUID> allowed = switch (step % 3) { case 0 -> ALL; case 1 -> NONE; default -> A::equals; };
            Optional<ClaimIndex.Witness> expected = oracle.entrySet().stream()
                    .filter(e -> e.getKey().dim().equals(dim) && rect.contains(e.getKey().x(), e.getKey().z()) && !allowed.test(e.getValue()))
                    .map(e -> new ClaimIndex.Witness(dim, e.getKey().x(), e.getKey().z(), e.getValue()))
                    .min(Comparator.comparingInt(ClaimIndex.Witness::x).thenComparingInt(ClaimIndex.Witness::z));
            eq(expected, index.queryDenied(dim, rect, allowed), "sorted independent oracle");
            eq(expected, base.naive(dim, rect, allowed), "naive oracle");
            var scanned = base.scan(dim, rect, allowed);
            eq(expected.isPresent(), scanned.isPresent(), "scan decision");
            scanned.ifPresent(w -> {
                eq(oracle.get(new Key(w.dim(), w.x(), w.z())), w.team(), "scan witness exists");
                check(rect.contains(w.x(), w.z()) && !allowed.test(w.team()), "scan witness valid");
            });
        }
        List<Map.Entry<Key, UUID>> shuffled = new ArrayList<>(oracle.entrySet());
        java.util.Collections.shuffle(shuffled, random);
        ClaimIndex rebuilt = new ClaimIndex();
        for (var e : shuffled) rebuilt.put(e.getKey().dim(), e.getKey().x(), e.getKey().z(), e.getValue());
        for (String dim : dims) eq(index.queryDenied(dim, new ChunkRect(-40, -40, 40, 40), NONE),
                rebuilt.queryDenied(dim, new ChunkRect(-40, -40, 40, 40), NONE), "insertion-independent witness");
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void eq(Object expected, Object actual, String message) {
        check(java.util.Objects.equals(expected, actual), message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void fails(Class<? extends Throwable> type, Runnable operation) {
        assertions++;
        try { operation.run(); } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getName(), failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
