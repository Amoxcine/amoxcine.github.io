package fr.ascendant.renaissance;

import ascendant.renaissance.TransportPolicy;
import fr.ascendant.renaissance.powah.PowahPolicy;
import fr.ascendant.renaissance.qio.QioScope;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class LunarPolicyTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static void rejected(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        var policy = new TransportPolicy(LunarConfig.DIMENSIONS);
        check(LunarConfig.DIMENSIONS.equals(Set.of("ad_astra:moon", "ad_astra:moon_orbit")), "Exact protected pair, no implicit orbit alias");
        UUID team = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String[] dimensions = {LunarConfig.DIMENSION, "minecraft:overworld", "ascendant:renaissance",
            LunarConfig.ORBIT, "ad_astra:mars", "ad_astra:earth_orbit"};
        for (UUID owner : new UUID[] {null, team}) {
            for (Set<UUID> grants : java.util.List.of(Set.<UUID>of(), Set.of(team))) {
                for (String first : dimensions) {
                    var a = new TransportPolicy.Endpoint(first, owner);
                    check(policy.sharedPool(a, grants).allowed() != LunarConfig.isProtected(first),
                        "QE/global pools never honor absent, current, or restored grants");
                    for (String second : dimensions) {
                        boolean expected = first.equals(second)
                            || !(LunarConfig.isProtected(first) || LunarConfig.isProtected(second));
                        check(policy.directLink(a, new TransportPolicy.Endpoint(second, owner), grants).allowed() == expected,
                            "symmetric AE2 boundary including old-world grants");
                        check(PowahPolicy.allowsDirectCharge(first, second) == expected, "Powah source AND recipient boundary");
                    }
                }
            }
        }
        BooleanSupplier bomb = () -> { throw new AssertionError("Permanent deny must not consult grant"); };
        for (BooleanSupplier grant : new BooleanSupplier[] {null, () -> true, () -> false, bomb}) {
            for (String dimension : LunarConfig.DIMENSIONS) {
                check(QioScope.denyAutomatic(false, dimension, grant), "QIO automatic permanent");
                check(QioScope.denyManual(false, dimension, true, grant, grant), "QIO manual permanent");
                check(!PowahPolicy.allows(false, dimension, () -> true, grant), "Powah pool permanent");
            }
        }
        check(QioScope.denyAutomatic(false, null, () -> true), "unknown QIO dimension fails closed");
        check(QioScope.denyManual(false, "minecraft:overworld", true, () -> true, () -> false), "remote lunar dashboard refused");
        check(QioScope.denyManual(false, "minecraft:overworld", false, () -> true, null), "stale session refused");
        check(!QioScope.denyManual(false, "minecraft:overworld", true, bomb, null), "outside portable native");
        check(!QioScope.denyAutomatic(true, LunarConfig.DIMENSION, bomb), "QIO client not authority");
        check(PowahPolicy.allows(true, LunarConfig.DIMENSION, bomb, bomb), "Powah client not authority");
        check(!PowahPolicy.allows(false, LunarConfig.DIMENSION, () -> false, () -> true), "stale Ender endpoint");
        check(PowahPolicy.allows(false, "minecraft:overworld", () -> true, bomb), "outside Ender native");
        check(!PowahPolicy.allowsDirectCharge(null, LunarConfig.DIMENSION), "unknown source denied");
        check(!PowahPolicy.allowsDirectCharge(LunarConfig.DIMENSION, null), "unknown recipient denied");
        check(!LunarConfig.parse("enabled=false\n"), "explicit default off");
        check(LunarConfig.parse("# solo and dedicated, no world guard\n enabled = true\n"), "explicit on");
        for (String invalid : new String[] {"", "# empty", "enabled=TRUE", "enabled=tru", "enabled=",
            "enable=true", "enabled=true\nenabled=false", "enabled=true\ndimension=missing:world", "enabled=true=x"}) {
            rejected(() -> LunarConfig.parse(invalid), "invalid configuration rejected");
        }
        for (String dimension : LunarConfig.DIMENSIONS) {
            LunarConfig.validateDimension(false, dimension, false);
            LunarConfig.validateDimension(true, dimension, true);
            rejected(() -> LunarConfig.validateDimension(true, dimension, false), "missing protected dimension aborts startup");
        }
        check(!QioScope.denyAutomatic(false, "ad_astra:earth_orbit", bomb), "Earth orbit QIO remains native");
        check(PowahPolicy.allows(false, "ad_astra:earth_orbit", () -> true, bomb), "Earth orbit Ender remains native");
        var temp = Files.createTempDirectory("lunar-config-test-");
        var file = temp.resolve(LunarConfig.FILE);
        check(!LunarConfig.read(file), "missing config is OFF");
        Files.writeString(file, "enabled=true");
        check(LunarConfig.read(file), "config file enabled");
        Files.writeString(file, "enabled=false");
        check(!LunarConfig.read(file), "config file disabled next load");
        Files.writeString(file, "enabled=invalid");
        rejected(() -> LunarConfig.read(file), "existing malformed file aborts");
        Files.writeString(file, "x".repeat(4097));
        rejected(() -> LunarConfig.read(file), "oversized file aborts");
        rejected(() -> LunarConfig.read(temp), "directory is not configuration");
        System.out.println("LUNAR POLICY/CONFIG PASS " + checks + " assertions; no server or real world journal opened");
    }
}
