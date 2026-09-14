package fr.ascendant.renaissance.qio;

import java.util.function.BooleanSupplier;

/** Dimension policy without loading Minecraft classes. */
public final class QioScope {
    public static final String LOADER_VERSION = "10.7.19";
    public static final String FILE_VERSION = "10.7.19.85";
    public static final String MEKANISM_SHA256 =
        "004dbc9f3106f4d192aeaa1ee1190dd16ec9ca8059ed3d093b80034f4c574f43";

    private QioScope() { }

    public static boolean denyManual(boolean clientSide, String dimension, boolean validSession,
                                     BooleanSupplier playerGrant, BooleanSupplier endpointGrant) {
        if (clientSide) return false;
        if (!validSession) return true;
        if (denyAutomatic(false, dimension, playerGrant)) return true;
        try { return endpointGrant != null && !endpointGrant.getAsBoolean(); }
        catch (RuntimeException unavailable) { return true; }
    }

    public static boolean denyAutomatic(boolean clientSide, String dimension, BooleanSupplier authorized) {
        if (clientSide) return false;
        if (dimension != null && !fr.ascendant.renaissance.LunarConfig.isProtected(dimension)) return false;
        return true;
    }
}
