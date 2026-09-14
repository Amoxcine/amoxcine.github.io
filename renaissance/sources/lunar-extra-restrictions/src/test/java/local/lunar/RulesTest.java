package local.lunar;

import java.util.ArrayList;
import java.util.List;

public final class RulesTest {
    private static int checks;
    private static void check(boolean result) {
        checks++;
        if (!result) throw new AssertionError("Rule check " + checks);
    }

    public static void main(String[] args) {
        String[] dimensions = {null, "minecraft:overworld", "minecraft:the_nether", "ad_astra:moon", "ad_astra:moon_orbit", "ad_astra:mars"};
        for (String from : dimensions) for (String to : dimensions) {
            check(!Rules.crossing(false, from, to));
            check(!Rules.unprovenGrid(false, from, to));
            boolean lunarFrom = "ad_astra:moon".equals(from) || "ad_astra:moon_orbit".equals(from);
            boolean lunarTo = "ad_astra:moon".equals(to) || "ad_astra:moon_orbit".equals(to);
            check(Rules.crossing(true, from, to) == ((lunarFrom || lunarTo) && !java.util.Objects.equals(from, to)));
            check(Rules.unprovenGrid(true, from, to) == (lunarFrom || lunarTo));
            check(!Rules.sharedAccess(false, from));
            check(Rules.sharedAccess(true, from) == lunarFrom);
        }
        Object first = new Object(), second = new Object();
        var input = new ArrayList<>(List.of(first, second, first));
        var output = Rules.unchangedRemainder(input);
        check(output != input && output.size() == 3 && input.size() == 3);
        check(output.get(0) == first && output.get(1) == second && output.get(2) == first);
        output.clear();
        check(input.size() == 3);
        check(Rules.unchangedRemainder(List.of()).isEmpty());
        check(!Rules.lunarSpan(List.of("ad_astra:moon", "ad_astra:moon")));
        check(!Rules.lunarSpan(List.of("minecraft:overworld", "minecraft:the_nether")));
        check(Rules.lunarSpan(List.of("ad_astra:moon", "minecraft:overworld")));
        check(Rules.lunarSpan(List.of("ad_astra:moon", "ad_astra:moon_orbit")));
        check(!Rules.lunarSpan(List.of()));
        check(Rules.connectedTarget(null, "first", first, "second", second) == null);
        check(Rules.connectedTarget("first", "first", first, null, null) == null);
        check(Rules.connectedTarget("second", "first", first, "second", second) == first);
        check(Rules.connectedTarget("first", "first", first, "second", second) == second);
        check(Rules.connectedTarget("unknown", "first", first, "second", second) == null);
        System.out.println("PASS " + checks + " pure policy/no-loss checks; no Minecraft runtime initialized");
    }
}
