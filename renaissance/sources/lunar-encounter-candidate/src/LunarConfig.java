package fr.ascendant.lunar.encounter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

/** One explicitly registered, immutable site. Missing configuration means disabled. */
public record LunarConfig(boolean enabled, boolean allowSiteBootstrap, String site, int x, int y, int z,
                          long reading, long window, long warning, long timeout, long absence) {
    public static final String DIMENSION = "ad_astra:moon";
    // Frozen v1 identity fields only; native FTB Quests now owns the item reward.
    public static final String REWARD = "minecraft:copper_ingot";
    public static final int REWARD_COUNT = 8;
    private static final Set<String> KEYS = Set.of("enabled", "allowSiteBootstrap", "site", "x", "y", "z",
            "readingTicks", "windowTicks", "warningTicks", "timeoutTicks", "absenceTicks");
    public LunarConfig {
        if (!site.matches("[a-z][a-z0-9_]{0,47}") || Math.abs((long)x)>29_999_900
                || Math.abs((long)z)>29_999_900 || y < -1024 || y > 1024)
            throw new IllegalArgumentException("Invalid lunar site ID/coordinates");
        new RaidMachine.Rules(reading, window, warning, timeout, absence, 60, 100);
        if (reading<60 || window<100 || warning<40 || timeout>36_000 || absence>1_200)
            throw new IllegalArgumentException("Lunar timing outside RC bounds");
    }
    public RaidMachine.Rules rules() { return new RaidMachine.Rules(reading,window,warning,timeout,absence,60,100); }
    public static LunarConfig load(Path path) throws IOException {
        if (!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) return defaults(false);
        if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>4096)
            throw new IOException("Config must be a regular file <=4096 bytes");
        Properties p = new Properties() {
            @Override public synchronized Object put(Object k,Object v) {
                if (containsKey(k)) throw new IllegalArgumentException("Duplicate config key: "+k);
                return super.put(k,v);
            }
        };
        try (var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) { p.load(reader); }
        if (!KEYS.containsAll(p.stringPropertyNames())) throw new IOException("Unknown config key");
        String enabled=p.getProperty("enabled","false");
        if (!Set.of("true","false").contains(enabled)) throw new IOException("enabled must be true or false");
        String bootstrap=p.getProperty("allowSiteBootstrap","false");
        if (!Set.of("true","false").contains(bootstrap)) throw new IOException("allowSiteBootstrap must be true or false");
        if (enabled.equals("true") && !p.stringPropertyNames().containsAll(Set.of("site","x","y","z")))
            throw new IOException("Enabled site requires explicit site,x,y,z");
        try {
            return new LunarConfig(Boolean.parseBoolean(enabled),Boolean.parseBoolean(bootstrap),p.getProperty("site","lunar_relay_01"),
                integer(p,"x",0),integer(p,"y",100),integer(p,"z",0),integer(p,"readingTicks",160),
                integer(p,"windowTicks",400),integer(p,"warningTicks",100),integer(p,"timeoutTicks",12000),integer(p,"absenceTicks",600));
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid lunar config: "+ex.getMessage(),ex); }
    }
    private static int integer(Properties p,String key,int fallback) { return Integer.parseInt(p.getProperty(key,""+fallback)); }
    public static LunarConfig defaults(boolean enabled) { return new LunarConfig(enabled,false,"lunar_relay_01",0,100,0,160,400,100,12000,600); }
    public String identity() { return "lunar-site-v1\n"+site+"\n"+DIMENSION+"\n"+x+","+y+","+z+"\n"+REWARD+"*"+REWARD_COUNT+"\n"; }
    public void seal(Path data) throws IOException {
        Path seal=data.resolve("site.identity");
        byte[] expected=identity().getBytes(StandardCharsets.UTF_8);
        if (Files.exists(seal,LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(seal,LinkOption.NOFOLLOW_LINKS) || Files.size(seal)>1024
                    || !Arrays.equals(Files.readAllBytes(seal),expected)) throw new IOException("Site identity changed: explicit offline migration required");
        } else {
            if (Files.exists(data.resolve("ledger"),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Existing ledger without site identity");
            try (var out=FileChannel.open(seal,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer b=ByteBuffer.wrap(expected); while(b.hasRemaining())out.write(b); out.force(true);
            }
        }
    }
}
