package fr.ascendant.etrionic;

public final class GuardPolicy {
    private GuardPolicy() {}
    public static boolean cancelTick(boolean activeServer, boolean nativeAlloying) {
        return activeServer && !nativeAlloying;
    }
    public static boolean cancelBlastingCraft(boolean activeServer) {
        return activeServer;
    }
}
