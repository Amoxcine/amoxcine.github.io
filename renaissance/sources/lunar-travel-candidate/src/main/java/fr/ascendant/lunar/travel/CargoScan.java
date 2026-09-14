package fr.ascendant.lunar.travel;

import com.teamresourceful.resourcefullib.common.fluid.ResourcefulBucketItem;
import earth.terrarium.adastra.common.registry.ModItems;
import earth.terrarium.common_storage_lib.fluid.util.FluidStorageData;
import earth.terrarium.common_storage_lib.resources.fluid.FluidResource;
import fr.ascendant.lunar.travel.mixin.ResourceStackAccessor;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Real-stack reader. Never traverses opaque storage or calls extraction/simulation APIs. */
public final class CargoScan {
    public static final class Denied extends RuntimeException {
        Denied(String reason) { super(reason, null, false, false); }
    }
    private static final Set<Class<?>> TOOLS = Set.of(PickaxeItem.class, AxeItem.class, ShovelItem.class,
        HoeItem.class, SwordItem.class, BowItem.class, ShieldItem.class, ShearsItem.class, FishingRodItem.class);
    private static final Set<String> SUITS = Set.of("ad_astra:space_helmet", "ad_astra:space_suit",
        "ad_astra:space_pants", "ad_astra:space_boots");
    private static final Set<String> SAFE_COMPONENTS = Set.of(
        "minecraft:max_stack_size", "minecraft:max_damage", "minecraft:damage", "minecraft:unbreakable",
        "minecraft:rarity", "minecraft:custom_name", "minecraft:item_name", "minecraft:lore",
        "minecraft:enchantments", "minecraft:attribute_modifiers", "minecraft:repair_cost", "minecraft:dyed_color",
        "minecraft:trim", "minecraft:hide_tooltip", "minecraft:hide_additional_tooltip", "minecraft:fire_resistant",
        "minecraft:tool", "minecraft:enchantment_glint_override");
    private final boolean firstOutbound;
    private final Map<String, Integer> counts = new HashMap<>();
    private int visited;
    private boolean raw;
    private final List<ItemStack> observations = new ArrayList<>();
    boolean sameStacks(CargoScan other) {
        if (observations.size() != other.observations.size()) return false;
        for (int i = 0; i < observations.size(); i++)
            if (!ItemStack.matches(observations.get(i), other.observations.get(i))) return false;
        return true;
    }

    CargoScan(boolean firstOutbound) { this.firstOutbound = firstOutbound; }
    public boolean containsRaw() { return raw; }
    public int visitedSlots() { return visited; }
    static void deny(String reason) { throw new Denied(reason); }
    void bound(int size) { if (size < 0 || size > CargoPolicy.MAX_SLOTS - visited) deny("CARGO_SLOT_BUDGET"); }
    private void count(String key, int amount, int cap) {
        int old = counts.getOrDefault(key, 0);
        if (amount <= 0 || amount > cap - old) deny("CARGO_CAP:" + key);
        counts.put(key, old + amount);
    }

    public void stack(ItemStack stack, EquipmentSlot wornSlot, String location) {
        bound(1); visited++;
        if (stack == null) deny("CARGO_NULL:" + location);
        observations.add(stack.copy());
        if (stack.isEmpty()) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        try {
            if (stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()) deny("CARGO_BAD_COUNT");
            Item item = stack.getItem();
            if (PersonalGear.food(id) && stack.get(DataComponents.FOOD) == null) deny("CARGO_FOOD_SCHEMA_MISSING");
            if (wornSlot != null) {
                boolean suit = SUITS.contains(id) && item.getClass().getName().equals(id.equals("ad_astra:space_suit")
                    ? "earth.terrarium.adastra.common.items.armor.SpaceSuitItem"
                    : "earth.terrarium.adastra.common.items.armor.base.CustomDyeableArmorItem");
                if (!(item instanceof ArmorItem) || (!suit && item.getClass() != ArmorItem.class)
                        || ((ArmorItem) item).getEquipmentSlot() != wornSlot) deny(PersonalGear.targetedBlock(item.getClass().getName()));
            } else if (!CargoPolicy.RAW.containsKey(id) && !CargoPolicy.PERSONAL.containsKey(id)
                    && !PersonalGear.food(id) && !TOOLS.contains(item.getClass())) {
                deny(PersonalGear.targetedBlock(item.getClass().getName()));
            }
            inspectComponents(stack, id, wornSlot != null || TOOLS.contains(item.getClass()));
            // Unknown item classes never reach capability providers, even on a detached copy.
            if (stack.copy().getCapability(Capabilities.ItemHandler.ITEM) != null) deny("CARGO_OPAQUE_ITEM_HANDLER");
            if (wornSlot != null) {
                count("armor:" + wornSlot.getName(), stack.getCount(), 1);
                return;
            }
            if (CargoPolicy.RAW.containsKey(id)) {
                if (!firstOutbound) deny("CARGO_RAW_FIRST_ARRIVAL_ONLY");
                raw = true;
                count(id, stack.getCount(), CargoPolicy.RAW.get(id));
            } else if (PersonalGear.food(id)) {
                count("personal-food", stack.getCount(), 32);
            } else if (CargoPolicy.PERSONAL.containsKey(id)) {
                count(id, stack.getCount(), CargoPolicy.PERSONAL.get(id));
                if (id.equals("ad_astra:fuel_bucket") || id.equals("minecraft:bucket"))
                    count("return-buckets", stack.getCount(), 3);
            } else if (TOOLS.contains(item.getClass())) {
                count("tool:" + item.getClass().getName(), stack.getCount(), 1);
                count("personal-tools", stack.getCount(), 8);
            } else deny("CARGO_ITEM_NOT_ALLOWED");
        } catch (Denied denied) { throw new Denied(denied.getMessage() + " @ " + location + " [" + id + "]"); }
    }

    private static void inspectComponents(ItemStack stack, String id, boolean gear) {
        int count = 0;
        for (var component : stack.getComponents()) {
            if (++count > CargoPolicy.MAX_COMPONENTS) deny("CARGO_COMPONENT_BUDGET");
            var key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
            if (key == null) deny("CARGO_UNREGISTERED_COMPONENT");
            String name = key.toString();
            if (name.equals("ad_astra:fluids")) {
                int cap = id.equals("ad_astra:space_suit") ? 1000 : id.equals("ad_astra:large_gas_tank") ? 3000 : 0;
                if (cap == 0 || !(component.value() instanceof FluidStorageData)) deny("CARGO_UNSUPPORTED_FLUID_CONTAINER");
                oxygen((FluidStorageData) component.value(), cap);
            } else if (component.type() == DataComponents.FOOD) {
                if (!PersonalGear.food(id)) deny("CARGO_UNEXPECTED_FOOD_COMPONENT");
                PersonalGear.foodComponent(stack);
            } else if (component.type() == DataComponents.CONTAINER) {
                var contents = stack.get(DataComponents.CONTAINER);
                if (contents.getSlots() > CargoPolicy.MAX_CONTAINER_SLOTS) deny("CARGO_CONTAINER_BUDGET");
                for (int i = 0; i < contents.getSlots(); i++)
                    if (!contents.getStackInSlot(i).isEmpty()) deny("CARGO_CONTAINER_PAYLOAD");
            } else if (component.type() == DataComponents.BUNDLE_CONTENTS) {
                if (!stack.get(DataComponents.BUNDLE_CONTENTS).isEmpty()) deny("CARGO_BUNDLE_PAYLOAD");
            } else if (component.type() == DataComponents.CUSTOM_DATA) {
                if (!stack.get(DataComponents.CUSTOM_DATA).isEmpty()) deny("CARGO_OPAQUE_CUSTOM_DATA");
            } else if (!SAFE_COMPONENTS.contains(name) && !PersonalGear.apotheosis(stack, name, component.value(), gear)) {
                deny("CARGO_UNSUPPORTED_COMPONENT:" + name);
            }
        }
        if (id.equals("ad_astra:large_gas_tank") && !stack.getItem().getClass().getName().equals(
                "earth.terrarium.adastra.common.items.GasTankItem")) deny("CARGO_CHANGED_TANK_CLASS");
        if (id.equals("ad_astra:fuel_bucket")) {
            Item item = stack.getItem();
            if (item != ModItems.FUEL_BUCKET.get() || item.getClass() != ResourcefulBucketItem.class)
                deny("CARGO_CHANGED_BUCKET_CLASS:" + item.getClass().getName());
            // NeoForge exposes the immutable native bucket fluid; no capability/extraction is needed.
            var fluid = ((ResourcefulBucketItem) item).content;
            if (fluid == null || !BuiltInRegistries.FLUID.getKey(fluid).toString().equals("ad_astra:fuel"))
                deny("CARGO_CHANGED_BUCKET_FLUID");
        }
    }

    static void oxygen(FluidStorageData data, long cap) {
        if (data.stacks() == null || data.stacks().size() > 1) deny("CARGO_OXYGEN_TANK_SHAPE");
        if (data.stacks().isEmpty()) return;
        var fluid = data.stacks().getFirst();
        if (fluid == null) deny("CARGO_OXYGEN_VOLUME");
        // ResourceStack.amount() normalizes malformed negatives/blank resources to zero; inspect stored value.
        long amount = ((ResourceStackAccessor) (Object) fluid).lunar$rawAmount();
        if (amount < 0 || amount > cap) deny("CARGO_OXYGEN_VOLUME");
        if (fluid.resource() == null || !fluid.resource().equals(FluidResource.of(fluid.resource().getType())))
            deny("CARGO_FLUID_COMPONENT_PAYLOAD");
        if (!fluid.resource().isBlank() && !BuiltInRegistries.FLUID.getKey(fluid.resource().getType()).toString().equals("ad_astra:oxygen"))
            deny("CARGO_NOT_NATIVE_OXYGEN");
        if (fluid.resource().isBlank() && amount != 0) deny("CARGO_INVALID_EMPTY_FLUID");
    }
}
