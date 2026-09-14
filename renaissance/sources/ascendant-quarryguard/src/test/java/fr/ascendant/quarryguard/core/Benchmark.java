package fr.ascendant.quarryguard.core;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/** Bounded exploratory harness; not JMH and not a game/server benchmark. */
public final class Benchmark {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    private static final String DIM = "minecraft:overworld";
    private static final Predicate<UUID> ALL = team -> true, OWN = A::equals;
    private static final int REPEATS = 5, SAMPLES = 513;
    private static final long WARMUP_NS = 150_000_000L;
    private static volatile long sink;

    private record Claim(String dim, int x, int z, UUID team) {}
    private record Query(String dim, ChunkRect rect, Predicate<UUID> allowed) {}
    private record Scenario(String name, List<Claim> claims, Query[] queries) {}
    @FunctionalInterface private interface Search {
        Optional<ClaimIndex.Witness> run(Query query);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: Benchmark output-directory fork-id");
        int fork = Integer.parseInt(args[1]);
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        long started = System.nanoTime();
        System.out.printf(Locale.ROOT, "Fork %d: %s; %s %s; %s %s; processors=%d; maxHeap=%d%n", fork,
                Instant.now(), System.getProperty("java.vendor"), System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory());
        System.out.printf("warmup=%d ms/algorithm/scenario; repeats=%d; individual samples/repeat=%d; naive cap=%d%n",
                WARMUP_NS / 1_000_000, REPEATS, SAMPLES, Baselines.NAIVE_CAP);
        try (PrintWriter summary = new PrintWriter(Files.newBufferedWriter(output.resolve("benchmark-" + fork + ".csv"), StandardCharsets.UTF_8));
             PrintWriter raw = new PrintWriter(Files.newBufferedWriter(output.resolve("samples-" + fork + ".csv"), StandardCharsets.UTF_8))) {
            summary.println("fork,scenario,algorithm,total_claims,area_min,area_max,samples,p50_ns,p95_ns,p99_ns,predicate_calls_min,predicate_calls_max,build_ns,status");
            raw.println("fork,scenario,algorithm,repeat,sample,elapsed_ns");
            long[] timer = new long[10_001];
            for (int i = 0; i < timer.length; i++) { long t = System.nanoTime(); timer[i] = System.nanoTime() - t; }
            Arrays.sort(timer);
            System.out.println("Back-to-back timer baseline p50=" + percentile(timer, 0.50) + " ns (not subtracted)");
            int scenarioNumber = 0;
            for (Scenario scenario : scenarios()) {
                if (System.nanoTime() - started > 90_000_000_000L) throw new IllegalStateException("90s harness budget exceeded");
                ClaimIndex index = new ClaimIndex();
                Baselines baseline = new Baselines();
                long t = System.nanoTime();
                for (Claim c : scenario.claims()) baseline.put(c.dim(), c.x(), c.z(), c.team());
                long baseBuild = System.nanoTime() - t;
                t = System.nanoTime();
                for (Claim c : scenario.claims()) index.put(c.dim(), c.x(), c.z(), c.team());
                long indexBuild = System.nanoTime() - t;
                String[] names = {"naive", "scan", "sparse"};
                Search[] searches = {q -> baseline.naive(q.dim(), q.rect(), q.allowed()),
                        q -> baseline.scan(q.dim(), q.rect(), q.allowed()),
                        q -> index.queryDenied(q.dim(), q.rect(), q.allowed())};
                long minArea = Arrays.stream(scenario.queries()).mapToLong(q -> q.rect().area()).min().orElseThrow();
                long maxArea = Arrays.stream(scenario.queries()).mapToLong(q -> q.rect().area()).max().orElseThrow();
                // Validate all inputs before timing, including witness identity where ordered.
                for (Query q : scenario.queries()) {
                    var expected = baseline.scan(q.dim(), q.rect(), q.allowed());
                    var actual = index.queryDenied(q.dim(), q.rect(), q.allowed());
                    if (expected.isPresent() != actual.isPresent()) throw new AssertionError(scenario.name());
                    if (q.rect().area() <= Baselines.NAIVE_CAP && !actual.equals(baseline.naive(q.dim(), q.rect(), q.allowed()))) {
                        throw new AssertionError("Witness mismatch: " + scenario.name());
                    }
                }
                // Rotate execution order across scenarios and fresh JVM forks.
                for (int offset = 0; offset < names.length; offset++) {
                    int a = (offset + fork + scenarioNumber) % names.length;
                    if (a == 0 && maxArea > Baselines.NAIVE_CAP) {
                        summary.printf(Locale.ROOT, "%d,%s,naive,%d,%d,%d,0,,,,,,%d,SKIP_AREA_CAP%n",
                                fork, scenario.name(), scenario.claims().size(), minArea, maxArea, baseBuild);
                        continue;
                    }
                    Search search = searches[a];
                    int cursor = 0;
                    long checksum = 0;
                    long warmStart = System.nanoTime();
                    do {
                        for (int i = 0; i < 32; i++) checksum += consume(search.run(scenario.queries()[cursor++ & 63]));
                    } while (System.nanoTime() - warmStart < WARMUP_NS);
                    sink = checksum;
                    long[] samples = new long[REPEATS * SAMPLES];
                    for (int repeat = 0; repeat < REPEATS; repeat++) {
                        for (int i = 0; i < SAMPLES; i++) {
                            Query query = scenario.queries()[cursor++ & 63];
                            long before = System.nanoTime();
                            Optional<ClaimIndex.Witness> result = search.run(query);
                            long elapsed = System.nanoTime() - before;
                            samples[repeat * SAMPLES + i] = elapsed;
                            checksum += consume(result);
                        }
                    }
                    sink = checksum;
                    // Output and instrumentation are outside timed sections.
                    for (int repeat = 0; repeat < REPEATS; repeat++) for (int i = 0; i < SAMPLES; i++) {
                        raw.printf(Locale.ROOT, "%d,%s,%s,%d,%d,%d%n", fork, scenario.name(), names[a], repeat, i, samples[repeat * SAMPLES + i]);
                    }
                    Arrays.sort(samples);
                    int minCalls = Integer.MAX_VALUE, maxCalls = 0;
                    for (Query q : scenario.queries()) {
                        int[] calls = {0};
                        search.run(new Query(q.dim(), q.rect(), team -> { calls[0]++; return q.allowed().test(team); }));
                        minCalls = Math.min(minCalls, calls[0]); maxCalls = Math.max(maxCalls, calls[0]);
                    }
                    summary.printf(Locale.ROOT, "%d,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,OK%n", fork,
                            scenario.name(), names[a], scenario.claims().size(), minArea, maxArea, samples.length,
                            percentile(samples, .50), percentile(samples, .95), percentile(samples, .99),
                            minCalls, maxCalls, a == 2 ? indexBuild : baseBuild);
                    System.out.printf(Locale.ROOT, "%s %-6s p50/p95/p99=%d/%d/%d ns; calls=%d..%d%n", scenario.name(), names[a],
                            percentile(samples, .50), percentile(samples, .95), percentile(samples, .99), minCalls, maxCalls);
                }
                summary.flush(); raw.flush(); scenarioNumber++;
            }
        }
        System.out.printf(Locale.ROOT, "Completed fork %d in %.3fs; checksum=%d. No Minecraft MSPT measured.%n",
                fork, (System.nanoTime() - started) / 1e9, sink);
    }

    private static long percentile(long[] sorted, double p) {
        return sorted[(int) Math.ceil(p * sorted.length) - 1];
    }

    private static long consume(Optional<ClaimIndex.Witness> result) {
        if (result.isEmpty()) return 1;
        var w = result.orElseThrow();
        return 31L * w.x() + w.z() + w.team().getLeastSignificantBits();
    }

    private static List<Scenario> scenarios() {
        List<Scenario> result = new ArrayList<>();
        List<Claim> far = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) far.add(new Claim(DIM, 100_000 + i, 100_000 + i % 101, B));
        result.add(new Scenario("empty-dimension", far, queries("missing", 0, 0, 16, ALL)));
        result.add(new Scenario("unrelated-20k-small", far, queries(DIM, 0, 0, 16, ALL)));
        result.add(new Scenario("unrelated-20k-256", far, queries(DIM, 0, 0, 256, ALL)));
        List<Claim> sparse = new ArrayList<>(far);
        for (int i = 0; i < 32; i++) sparse.add(new Claim(DIM, i * 7, i * 7, A));
        result.add(new Scenario("sparse-all-allowed", sparse, queries(DIM, 0, 0, 256, ALL)));
        List<Claim> dense = new ArrayList<>();
        for (int x = -32; x < 32; x++) for (int z = -32; z < 32; z++) dense.add(new Claim(DIM, x, z, A));
        result.add(new Scenario("dense-all-allowed", dense, repeated(new Query(DIM, new ChunkRect(-32, -32, 31, 31), ALL))));
        result.add(new Scenario("dense-small-window", dense, queries(DIM, -24, -24, 8, ALL)));
        List<Claim> early = new ArrayList<>(dense);
        early.set(0, new Claim(DIM, -32, -32, B));
        result.add(new Scenario("dense-denied-first", early, repeated(new Query(DIM, new ChunkRect(-32, -32, 31, 31), OWN))));
        List<Claim> late = new ArrayList<>(dense);
        late.set(late.size() - 1, new Claim(DIM, 31, 31, B));
        result.add(new Scenario("dense-denied-last", late, repeated(new Query(DIM, new ChunkRect(-32, -32, 31, 31), OWN))));
        List<Claim> rows = new ArrayList<>();
        for (int i = -10_000; i < 10_000; i++) rows.add(new Claim(DIM, i, 10_000, A));
        result.add(new Scenario("wide-X-empty-Z", rows, repeated(new Query(DIM, new ChunkRect(-10_000, 0, 9_999, 0), ALL))));
        List<Claim> column = new ArrayList<>();
        for (int i = -10_000; i < 10_000; i++) column.add(new Claim(DIM, 0, i, A));
        result.add(new Scenario("one-X-many-Z", column, queries(DIM, 0, -16, 16, ALL)));
        result.add(new Scenario("huge-sparse-all-allowed", sparse, repeated(new Query(DIM,
                new ChunkRect(-1_000_000_000, -1_000_000_000, 1_000_000_000, 1_000_000_000), ALL))));
        result.add(new Scenario("full-X-axis", List.of(new Claim(DIM, Integer.MIN_VALUE, 0, A),
                new Claim(DIM, Integer.MAX_VALUE, 0, A)), repeated(new Query(DIM,
                new ChunkRect(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0), ALL))));
        List<Claim> mixed = new ArrayList<>();
        for (int d = 0; d < 8; d++) for (int i = 0; i < 2_500; i++) mixed.add(new Claim("dim:" + d, i, i, A));
        result.add(new Scenario("many-dimensions", mixed, queries("dim:3", 1_000, 1_000, 16, ALL)));
        return result;
    }

    private static Query[] queries(String dim, int x, int z, int size, Predicate<UUID> allowed) {
        Query[] queries = new Query[64];
        Random r = new Random(0xBEEFL);
        for (int i = 0; i < queries.length; i++) {
            int dx = r.nextInt(8), dz = r.nextInt(8);
            queries[i] = new Query(dim, new ChunkRect(x + dx, z + dz, x + dx + size - 1, z + dz + size - 1), allowed);
        }
        return queries;
    }

    private static Query[] repeated(Query query) {
        Query[] queries = new Query[64];
        Arrays.fill(queries, query);
        return queries;
    }
}
