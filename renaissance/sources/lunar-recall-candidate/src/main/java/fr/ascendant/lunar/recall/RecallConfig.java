package fr.ascendant.lunar.recall;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Read once per server start. An invalid existing config aborts, never silently disables. */
public record RecallConfig(boolean enabledSolo, boolean enabledDedicated) {
    public static final String FILE = "ascendant-lunar-recall.properties";
    public static final RecallConfig OFF = new RecallConfig(false, false);
    public boolean enabled(boolean dedicated) { return dedicated ? enabledDedicated : enabledSolo; }

    public static RecallConfig read(Path file) {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return OFF;
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 4096) throw new IOException("Invalid config file/size");
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read " + file, failure);
        }
    }

    public static RecallConfig parse(String content) {
        Map<String, Boolean> values = new HashMap<>();
        for (String raw : content.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] pair = line.split("=", -1);
            if (pair.length != 2 || !Set.of("enabledSolo", "enabledDedicated").contains(pair[0].strip())
                || !(pair[1].strip().equals("true") || pair[1].strip().equals("false"))
                || values.putIfAbsent(pair[0].strip(), Boolean.valueOf(pair[1].strip())) != null)
                throw new IllegalArgumentException(FILE + ": invalid/unknown/duplicate setting " + line);
        }
        if (values.size() != 2) throw new IllegalArgumentException(FILE + ": both mode settings are required");
        return new RecallConfig(values.get("enabledSolo"), values.get("enabledDedicated"));
    }
}
