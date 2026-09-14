package fr.ascendant.lunar.recall;

import java.util.Set;

public final class RecallPolicy {
    public static final Set<String> PROTECTED = Set.of("ad_astra:moon", "ad_astra:moon_orbit");
    private RecallPolicy() { }

    public static boolean blocksTeleport(String source, String target) {
        return source == null || target == null || PROTECTED.contains(source) || PROTECTED.contains(target);
    }

    public static boolean blocksRestore(String recipient, String grave) {
        return recipient == null || grave == null
            || !recipient.equals(grave) && (PROTECTED.contains(recipient) || PROTECTED.contains(grave));
    }
}
