package fr.ascendant.lunar.travel;

import dev.shadowsoffire.apotheosis.affix.ItemAffixes;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Explicit component schemas, not mod namespace approval. Installed effects are not stripped. */
final class PersonalGear {
    private static final Set<String> FOOD = Set.of(
        "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken", "minecraft:cooked_mutton",
        "minecraft:cooked_rabbit", "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:bread",
        "minecraft:baked_potato", "minecraft:pumpkin_pie", "minecraft:cookie", "minecraft:dried_kelp",
        "minecraft:mushroom_stew", "minecraft:rabbit_stew", "minecraft:beetroot_soup", "minecraft:golden_apple",
        "minecraft:enchanted_golden_apple", "minecraft:golden_carrot",
        "farmersdelight:vegetable_soup", "farmersdelight:fish_stew", "farmersdelight:beef_stew",
        "farmersdelight:chicken_soup", "farmersdelight:fried_rice", "farmersdelight:pumpkin_soup",
        "farmersdelight:baked_cod_stew", "farmersdelight:noodle_soup", "farmersdelight:pasta_with_meatballs",
        "farmersdelight:pasta_with_mutton_chop", "farmersdelight:roasted_mutton_chops", "farmersdelight:steak_and_potatoes",
        "farmersdelight:vegetable_noodles", "farmersdelight:squid_ink_pasta", "farmersdelight:grilled_salmon",
        "farmersdelight:roast_chicken", "farmersdelight:stuffed_pumpkin", "farmersdelight:hamburger",
        "farmersdelight:chicken_sandwich", "farmersdelight:egg_sandwich", "farmersdelight:bacon_sandwich");
    private static final Set<String> APOTH_SCALARS = Set.of("apotheosis:rarity", "apotheosis:affix_name",
        "apotheosis:durability_bonus", "apotheosis:from_chest", "apotheosis:from_trader", "apotheosis:from_boss",
        "apotheosis:from_mob", "apotheosis:stoneforming_target", "apotheosis:malice_marker", "apotheosis:touched_by_malice");
    static boolean food(String id) { return FOOD.contains(id); }
    static void foodComponent(ItemStack stack) {
        var food = stack.get(DataComponents.FOOD);
        if (!food.equals(stack.getItem().components().get(DataComponents.FOOD))) CargoScan.deny("CARGO_MODIFIED_FOOD");
        food.usingConvertsTo().ifPresent(remainder -> {
            if (!ItemStack.matches(remainder, new ItemStack(Items.BOWL))) CargoScan.deny("CARGO_FOOD_CONTAINER_PAYLOAD");
        });
    }
    static boolean apotheosis(ItemStack stack, String name, Object value, boolean gear) {
        if (!gear) return false;
        if (APOTH_SCALARS.contains(name)) return true;
        if (name.equals("apotheosis:affixes")) {
            if (!(value instanceof ItemAffixes affixes) || affixes.size() > 64) CargoScan.deny("CARGO_APOTH_AFFIX_SHAPE");
            return true;
        }
        if (name.equals("apotheosis:sockets")) {
            if (!(value instanceof Integer n) || n < 0 || n > 8) CargoScan.deny("CARGO_APOTH_SOCKET_BUDGET");
            return true;
        }
        if (!name.equals("apotheosis:socketed_gems")) return false;
        if (!(value instanceof ItemContainerContents)) CargoScan.deny("CARGO_APOTH_SOCKET_SHAPE");
        var gems = (ItemContainerContents) value;
        int installedSockets = stack.getOrDefault(dev.shadowsoffire.apotheosis.Apoth.Components.SOCKETS, 0);
        if (installedSockets < 0 || installedSockets > 8 || gems.getSlots() > installedSockets) CargoScan.deny("CARGO_APOTH_SOCKET_BUDGET");
        for (int i = 0; i < gems.getSlots(); i++) {
            ItemStack gem = gems.getStackInSlot(i);
            if (gem.isEmpty()) continue;
            if (gem.getCount() != 1 || !gem.getItem().getClass().getName().equals("dev.shadowsoffire.apotheosis.socket.gem.GemItem"))
                CargoScan.deny("CARGO_APOTH_SOCKET_NOT_GEM");
            int components = 0;
            for (var component : gem.getComponents()) {
                if (++components > 24) CargoScan.deny("CARGO_APOTH_GEM_BUDGET");
                // GemItem inherits these vanilla defaults; modified values are not gem payload authority.
                if (component.type() == DataComponents.ENCHANTMENTS && ItemEnchantments.EMPTY.equals(component.value())) continue;
                if (component.type() == DataComponents.REPAIR_COST && Integer.valueOf(0).equals(component.value())) continue;
                if (component.type() == DataComponents.ATTRIBUTE_MODIFIERS && ItemAttributeModifiers.EMPTY.equals(component.value())) continue;
                var key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
                if (key == null || !Set.of("apotheosis:gem", "apotheosis:purity", "minecraft:max_stack_size",
                    "minecraft:rarity", "minecraft:custom_name", "minecraft:item_name", "minecraft:lore",
                    "minecraft:enchantment_glint_override").contains(key.toString()))
                    CargoScan.deny("CARGO_APOTH_GEM_PAYLOAD:socket=" + i + ":component=" + key);
            }
        }
        return true;
    }
    static String targetedBlock(String className) {
        if (className.startsWith("com.brandon3055.draconicevolution.items.equipment.Modular"))
            return "CARGO_DRACONIC_MODULE_READER_REQUIRED:tree_inventory_and_host_cache";
        if (className.startsWith("io.redspace.ironsspellbooks.")) return "CARGO_IRONS_GEAR_READER_REQUIRED:spell_container";
        return "CARGO_GEAR_CLASS_READER_REQUIRED:" + className;
    }
}
