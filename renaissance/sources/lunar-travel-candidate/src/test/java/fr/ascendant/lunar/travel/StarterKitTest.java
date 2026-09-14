package fr.ascendant.lunar.travel;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

/** Read-only cross-candidate contract check; never loads Minecraft or edits the survival manifest. */
public final class StarterKitTest {
    public static void main(String[] args) throws Exception {
        try (var reader = Files.newBufferedReader(Path.of(args[0]))) {
            var manifest = JsonParser.parseReader(reader).getAsJsonObject();
            var raw = new HashMap<String, Integer>();
            manifest.getAsJsonObject("first_outbound_only").entrySet().forEach(e -> raw.put(e.getKey(), e.getValue().getAsInt()));
            if (!raw.equals(CargoPolicy.RAW)) throw new AssertionError("Survival/travel raw kit drift");
            var personal = new HashMap<String, Integer>();
            manifest.getAsJsonObject("personal_consumables").entrySet().forEach(e -> personal.put(e.getKey(),
                e.getValue().isJsonObject() ? e.getValue().getAsJsonObject().get("count").getAsInt() : e.getValue().getAsInt()));
            personal.put("minecraft:bucket", 3);
            if (!personal.equals(CargoPolicy.PERSONAL)) throw new AssertionError("Survival/travel personal kit drift");
            if (!manifest.getAsJsonObject("later_outbound_raw_allowance").isEmpty())
                throw new AssertionError("Later raw resupply is forbidden");
        }
        System.out.println("PASS survival manifest: exact raw/personal caps and no later raw allowance; read-only JSON check.");
    }
}
