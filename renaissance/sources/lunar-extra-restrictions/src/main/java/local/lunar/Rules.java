package local.lunar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Rules {
    private Rules() {}

    public static boolean lunar(String dimension) {
        return "ad_astra:moon".equals(dimension) || "ad_astra:moon_orbit".equals(dimension);
    }

    public static boolean crossing(boolean enabled, String source, String target) {
        return enabled && (lunar(source) || lunar(target)) && !java.util.Objects.equals(source, target);
    }

    public static boolean sharedAccess(boolean enabled, String source) {
        return enabled && lunar(source);
    }

    // An anchor in the same world is not proof that its grid/storage is local.
    public static boolean unprovenGrid(boolean enabled, String source, String target) {
        return enabled && (lunar(source) || lunar(target));
    }

    public static <T> List<T> unchangedRemainder(Collection<T> input) {
        return new ArrayList<>(input);
    }

    public static boolean lunarSpan(Collection<String> dimensions) {
        return dimensions.stream().anyMatch(Rules::lunar) && dimensions.stream().distinct().count() > 1;
    }

    public static <T> T connectedTarget(Object id, Object firstId, T first, Object secondId, T second) {
        if (id == null) return null;
        if (id.equals(firstId)) return second;
        if (id.equals(secondId)) return first;
        return null;
    }
}
