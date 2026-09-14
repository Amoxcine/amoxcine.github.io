package fr.ascendant.lunar.travel;

import java.util.Map;

/** Exact caps from STARTER_KIT.json; no client-supplied inspection facts. */
public final class CargoPolicy {
    public static final Map<String, Integer> RAW = Map.of(
        "minecraft:coal", 160, "minecraft:redstone", 22, "minecraft:copper_ingot", 9,
        "minecraft:smooth_stone", 3, "minecraft:cobblestone", 8, "minecraft:oak_log", 8,
        "minecraft:oak_sapling", 4, "minecraft:dirt", 4);
    public static final Map<String, Integer> PERSONAL = Map.of(
        "minecraft:cooked_beef", 32, "ad_astra:large_gas_tank", 2,
        "ad_astra:fuel_bucket", 3, "minecraft:bucket", 3);
    public static final int MAX_SLOTS = 512, MAX_COMPONENTS = 64, MAX_CONTAINER_SLOTS = 256;
    private CargoPolicy() {}
}

