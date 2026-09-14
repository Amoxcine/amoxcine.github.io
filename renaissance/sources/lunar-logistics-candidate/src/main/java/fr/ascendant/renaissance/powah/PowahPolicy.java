package fr.ascendant.renaissance.powah;

import java.util.function.BooleanSupplier;

/** Pure policy; subject identity and authority are supplied live, never cached here. */
public final class PowahPolicy {
    public static final String POWAH_SHA256 =
        "0e604a7356111c1dd44a00ea42fc1aa960d9faeb978261349df1138fcee4d0b4";

    private PowahPolicy() { }

    public static boolean allowsDirectCharge(String source, String recipient) {
        if (source == null || recipient == null) return false;
        return new ascendant.renaissance.TransportPolicy(fr.ascendant.renaissance.LunarConfig.DIMENSIONS).directLink(
            new ascendant.renaissance.TransportPolicy.Endpoint(source, null),
            new ascendant.renaissance.TransportPolicy.Endpoint(recipient, null), java.util.Set.of()).allowed();
    }

    public static boolean allows(boolean client, String dimension, BooleanSupplier current,
                                 BooleanSupplier authority) {
        if (client) return true;
        try {
            if (dimension == null || current == null || !current.getAsBoolean()) return false;
            if (!fr.ascendant.renaissance.LunarConfig.isProtected(dimension)) return true;
            return false;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
