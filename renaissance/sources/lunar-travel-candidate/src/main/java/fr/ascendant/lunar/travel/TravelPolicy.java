package fr.ascendant.lunar.travel;

import java.util.Set;

public final class TravelPolicy {
    public static final String EARTH = "minecraft:overworld";
    public static final String MOON = "ad_astra:moon";
    public static final String MOON_ORBIT = "ad_astra:moon_orbit";
    public static final Set<String> RESERVED = Set.of(
        "ad_astra:earth_orbit", "ad_astra:moon_orbit", "ad_astra:mars", "ad_astra:mars_orbit",
        "ad_astra:mercury", "ad_astra:mercury_orbit", "ad_astra:venus", "ad_astra:venus_orbit",
        "ad_astra:glacio", "ad_astra:glacio_orbit");

    private TravelPolicy() {}

    public static boolean rocketRoute(String from, String to) {
        return EARTH.equals(from) && MOON.equals(to) || MOON.equals(from) && EARTH.equals(to);
    }

    public static boolean launchWorld(String from) { return EARTH.equals(from) || MOON.equals(from); }

    public static boolean protectedWorld(String id) { return MOON.equals(id) || MOON_ORBIT.equals(id); }

    public static boolean blocksTeleport(String from, String to) {
        return from == null || to == null || protectedWorld(from) || protectedWorld(to)
            || RESERVED.contains(to) && !to.equals(from);
    }

    // No narrative portal registry/adapter exists in this candidate. No grant can open it.
    public static boolean narrativeLinkApproved(String link) { return false; }
}
