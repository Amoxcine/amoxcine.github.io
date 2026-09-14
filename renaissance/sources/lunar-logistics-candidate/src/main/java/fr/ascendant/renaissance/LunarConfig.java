package fr.ascendant.renaissance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Strict launch-time configuration; no world name, address, JVM flag or grant. */
public final class LunarConfig {
    public static final String FILE = "ascendant-lunar-logistics.properties";
    public static final String DIMENSION = "ad_astra:moon";
    public static final String ORBIT = "ad_astra:moon_orbit";
    public static final java.util.Set<String> DIMENSIONS = java.util.Set.of(DIMENSION, ORBIT);
    private LunarConfig() { }

    public static boolean isProtected(String dimension) {
        return dimension != null && DIMENSIONS.contains(dimension);
    }

    public static boolean read(Path file) {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return false;
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 4096)
                throw new IOException("Not a regular config file or larger than 4096 bytes");
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read lunar logistics config " + file, failure);
        }
    }

    public static boolean parse(String text) {
        Map<String, String> values = new HashMap<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] pair = line.split("=", -1);
            if (pair.length != 2 || !pair[0].strip().equals("enabled")
                || values.putIfAbsent(pair[0].strip(), pair[1].strip()) != null)
                throw new IllegalArgumentException(FILE + ": unknown, duplicate or malformed setting: " + line);
        }
        String value = values.get("enabled");
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new IllegalArgumentException(FILE + ": existing file must contain enabled=true or enabled=false");
    }

    public static void validateDimension(boolean enabled, String dimension, boolean present) {
        if (enabled && !present) throw new IllegalStateException(
            "Lunar logistics enabled but " + dimension + " is missing; refusing startup");
    }
}
