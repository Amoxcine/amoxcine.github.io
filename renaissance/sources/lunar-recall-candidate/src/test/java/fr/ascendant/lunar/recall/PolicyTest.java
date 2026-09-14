package fr.ascendant.lunar.recall;

import java.nio.file.Files;
import java.util.Set;

public final class PolicyTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static void reject(Runnable run) {
        try { run.run(); } catch (IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError("Expected config refusal");
    }
    public static void main(String[] args) throws Exception {
        check(RecallPolicy.PROTECTED.equals(Set.of("ad_astra:moon", "ad_astra:moon_orbit")), "exact protected pair");
        String[] worlds = {"minecraft:overworld", "ad_astra:moon", "ad_astra:moon_orbit", "ad_astra:earth_orbit", "ascendant:renaissance", null};
        for (String a : worlds) for (String b : worlds) {
            boolean unknown = a == null || b == null;
            boolean lunar = "ad_astra:moon".equals(a) || "ad_astra:moon_orbit".equals(a)
                || "ad_astra:moon".equals(b) || "ad_astra:moon_orbit".equals(b);
            check(RecallPolicy.blocksTeleport(a,b) == (unknown || lunar), "Robit/teleporter/scroll movement " + a + " -> " + b);
            check(RecallPolicy.blocksRestore(a,b) == (unknown || lunar && !a.equals(b)), "grave content boundary " + a + " <- " + b);
        }
        check(!RecallPolicy.blocksRestore("ad_astra:moon","ad_astra:moon"), "local grave recovery preserved");
        check(!RecallPolicy.blocksRestore("ad_astra:moon_orbit","ad_astra:moon_orbit"), "orbit recovery preserved");
        check(RecallPolicy.blocksTeleport("ad_astra:moon","ad_astra:moon"), "local teleport prohibited, not local industry");
        check(!RecallConfig.OFF.enabled(false) && !RecallConfig.OFF.enabled(true), "both modes default off");
        for (boolean solo : new boolean[] {false,true}) for (boolean dedicated : new boolean[] {false,true}) {
            var config = RecallConfig.parse("# candidate\nenabledSolo=" + solo + "\nenabledDedicated=" + dedicated);
            check(config.enabled(false) == solo && config.enabled(true) == dedicated, "independent opt-ins");
        }
        for (String invalid : new String[] {"", "enabledSolo=true", "enabledSolo=TRUE\nenabledDedicated=false",
            "enabledSolo=true\nenabledDedicated=false\nenabledSolo=false", "enabled=true", "enabledSolo=true\nenabledDedicated=false\nworld=old"})
            reject(() -> RecallConfig.parse(invalid));
        var root = Files.createTempDirectory("lunar-recall-config-");
        var file = root.resolve(RecallConfig.FILE);
        check(RecallConfig.read(file).equals(RecallConfig.OFF), "missing off");
        Files.writeString(file, "enabledSolo=true\nenabledDedicated=false");
        var snapshot = RecallConfig.read(file);
        Files.writeString(file, "enabledSolo=false\nenabledDedicated=true");
        check(snapshot.enabledSolo() && !snapshot.enabledDedicated(), "snapshot does not hot reload");
        check(RecallConfig.read(file).enabledDedicated(), "restart reads new mode selection");
        Files.writeString(file, "bad");
        reject(() -> RecallConfig.read(file));
        Files.writeString(file, "x".repeat(4097));
        reject(() -> RecallConfig.read(file));
        reject(() -> RecallConfig.read(root));
        check(!YigdGate.deniesScroll(null, null) && !YigdGate.deniesGrave(null,null,false), "actual gate inactive without opted-in server");
        System.out.println("POLICY/CONFIG PASS " + checks + " assertions; no world/player instantiated");
    }
}
