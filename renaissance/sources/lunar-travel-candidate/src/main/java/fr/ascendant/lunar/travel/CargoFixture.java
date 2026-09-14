package fr.ascendant.lunar.travel;

import earth.terrarium.adastra.common.registry.ModDataManagers;
import earth.terrarium.common_storage_lib.fluid.util.FluidStorageData;
import earth.terrarium.common_storage_lib.resources.fluid.FluidResource;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.nbt.CompoundTag;

/** Parent-run fixture on registered native ItemStacks only. Never inserted into a player or world. */
public final class CargoFixture {
    private record StackSlot(ItemStack stack, EquipmentSlot slot) {}
    private static ItemStack item(String id, int count) {
        var key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new AssertionError("Missing native fixture item: " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(key), count);
    }
    private static ItemStack oxygen(String item, String fluid, long amount) {
        ItemStack stack = item(item, 1);
        var key = ResourceLocation.parse(fluid);
        if (!BuiltInRegistries.FLUID.containsKey(key)) throw new AssertionError("Missing fluid: " + fluid);
        stack.set(ModDataManagers.FLUID_CONTENTS.componentType(), new FluidStorageData(
            List.of(FluidResource.of(BuiltInRegistries.FLUID.get(key)).toStack(amount))));
        return stack;
    }
    private static void expect(List<StackSlot> stacks, boolean raw, boolean allow) {
        expect(stacks, raw, allow, null);
    }
    private static void expect(List<StackSlot> stacks, boolean raw, boolean allow, String refusal) {
        var before = stacks.stream().map(s -> s.stack().copy()).toList();
        boolean accepted = false;
        try {
            CargoScan scan = new CargoScan(raw);
            for (int i = 0; i < stacks.size(); i++) scan.stack(stacks.get(i).stack(), stacks.get(i).slot(), "fixture:" + i);
            accepted = true;
        } catch (CargoScan.Denied denied) {
            if (allow) throw new AssertionError("Expected native kit allow: " + denied.getMessage());
            if (refusal != null && !denied.getMessage().startsWith(refusal))
                throw new AssertionError("Expected " + refusal + ", got " + denied.getMessage());
        } finally {
            for (int i = 0; i < stacks.size(); i++)
                if (!ItemStack.matches(before.get(i), stacks.get(i).stack())) throw new AssertionError("Scanner mutated stack " + i);
        }
        if (accepted != allow) throw new AssertionError("Unexpected cargo approval");
    }
    public static int run(RegistryAccess registries) {
        int tests = 0;
        var personal = new ArrayList<StackSlot>();
        personal.add(new StackSlot(item("ad_astra:space_helmet", 1), EquipmentSlot.HEAD));
        personal.add(new StackSlot(oxygen("ad_astra:space_suit", "ad_astra:oxygen", 1000), EquipmentSlot.CHEST));
        personal.add(new StackSlot(item("ad_astra:space_pants", 1), EquipmentSlot.LEGS));
        personal.add(new StackSlot(item("ad_astra:space_boots", 1), EquipmentSlot.FEET));
        for (int i = 0; i < 2; i++) personal.add(new StackSlot(oxygen("ad_astra:large_gas_tank", "ad_astra:oxygen", 3000), null));
        for (int i = 0; i < 3; i++) personal.add(new StackSlot(item("ad_astra:fuel_bucket", 1), null));
        personal.add(new StackSlot(item("minecraft:cooked_beef", 32), null));
        personal.add(new StackSlot(item("minecraft:diamond_pickaxe", 1), null));
        personal.add(new StackSlot(item("minecraft:diamond_axe", 1), null));
        var kit = new ArrayList<>(personal);
        for (var entry : CargoPolicy.RAW.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                int size = Math.min(64, remaining);
                kit.add(new StackSlot(item(entry.getKey(), size), null)); remaining -= size;
            }
        }
        expect(kit, true, true); tests++;
        expect(kit, false, false); tests++;
        expect(personal, false, true); tests++;
        expect(List.of(new StackSlot(item("minecraft:bread", 16), null),
            new StackSlot(item("minecraft:cooked_chicken", 16), null)), false, true); tests++;
        expect(List.of(new StackSlot(item("minecraft:bread", 17), null),
            new StackSlot(item("minecraft:cooked_chicken", 16), null)), false, false); tests++;
        expect(List.of(new StackSlot(item("farmersdelight:chicken_soup", 1), null)), false, true); tests++;
        ItemStack affixed = item("minecraft:diamond_pickaxe", 1);
        affixed.set(dev.shadowsoffire.apotheosis.Apoth.Components.AFFIXES, dev.shadowsoffire.apotheosis.affix.ItemAffixes.EMPTY);
        affixed.set(dev.shadowsoffire.apotheosis.Apoth.Components.SOCKETS, 1);
        ItemStack defaultGem = item("apotheosis:gem", 1);
        if (!ItemEnchantments.EMPTY.equals(defaultGem.get(DataComponents.ENCHANTMENTS))
            || !Integer.valueOf(0).equals(defaultGem.get(DataComponents.REPAIR_COST))
            || !ItemAttributeModifiers.EMPTY.equals(defaultGem.get(DataComponents.ATTRIBUTE_MODIFIERS)))
            throw new AssertionError("Native GemItem common defaults changed");
        affixed.set(dev.shadowsoffire.apotheosis.Apoth.Components.SOCKETED_GEMS,
            ItemContainerContents.fromItems(List.of(defaultGem)));
        expect(List.of(new StackSlot(affixed, null)), false, true); tests++;
        var modifiedGems = new LinkedHashMap<String, ItemStack>();
        ItemStack repairGem = defaultGem.copy();
        repairGem.set(DataComponents.REPAIR_COST, 1);
        modifiedGems.put("minecraft:repair_cost", repairGem);
        ItemStack enchantedGem = defaultGem.copy();
        var enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(registries.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 1);
        enchantedGem.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());
        modifiedGems.put("minecraft:enchantments", enchantedGem);
        ItemStack attributeGem = defaultGem.copy();
        attributeGem.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().add(Attributes.ARMOR,
            new AttributeModifier(ResourceLocation.parse("ascendant_lunar:fixture"), 1, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.ANY).build());
        modifiedGems.put("minecraft:attribute_modifiers", attributeGem);
        ItemStack containerGem = defaultGem.copy();
        containerGem.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(item("ad_astra:coal_generator", 1))));
        modifiedGems.put("minecraft:container", containerGem);
        ItemStack opaqueGem = defaultGem.copy();
        var gemTag = new CompoundTag(); gemTag.putString("backing_inventory", "opaque-id");
        opaqueGem.set(DataComponents.CUSTOM_DATA, CustomData.of(gemTag));
        modifiedGems.put("minecraft:custom_data", opaqueGem);
        ItemStack nestedGem = defaultGem.copy();
        nestedGem.set(dev.shadowsoffire.apotheosis.Apoth.Components.SOCKETED_GEMS, ItemContainerContents.fromItems(List.of(defaultGem)));
        modifiedGems.put("apotheosis:socketed_gems", nestedGem);
        for (var modified : modifiedGems.entrySet()) {
            affixed.set(dev.shadowsoffire.apotheosis.Apoth.Components.SOCKETED_GEMS, ItemContainerContents.fromItems(List.of(modified.getValue())));
            expect(List.of(new StackSlot(affixed, null)), false, false,
                "CARGO_APOTH_GEM_PAYLOAD:socket=0:component=" + modified.getKey() + " @"); tests++;
        }
        affixed.set(dev.shadowsoffire.apotheosis.Apoth.Components.SOCKETED_GEMS,
            ItemContainerContents.fromItems(List.of(item("ad_astra:coal_generator", 1))));
        expect(List.of(new StackSlot(affixed, null)), false, false); tests++;
        var overCap = new ArrayList<>(kit); overCap.add(new StackSlot(item("minecraft:coal", 1), null));
        expect(overCap, true, false); tests++;
        var foodOver = new ArrayList<>(personal); foodOver.add(new StackSlot(item("minecraft:cooked_beef", 1), null));
        expect(foodOver, false, false); tests++;
        for (String id : List.of("ad_astra:coal_generator", "minecraft:chest", "minecraft:shulker_box", "sophisticatedbackpacks:backpack")) {
            ItemStack bag = item(id, 1);
            expect(List.of(new StackSlot(bag, null)), true, false); tests++;
            bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(item("ad_astra:coal_generator", 1))));
            expect(List.of(new StackSlot(bag, null)), true, false); tests++;
        }
        ItemStack stuffedSuit = oxygen("ad_astra:space_suit", "ad_astra:oxygen", 1000);
        stuffedSuit.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(item("ad_astra:coal_generator", 1))));
        expect(List.of(new StackSlot(stuffedSuit, EquipmentSlot.CHEST)), true, false); tests++;
        ItemStack opaqueTool = item("minecraft:diamond_pickaxe", 1);
        var tag = new CompoundTag(); tag.putString("backing_inventory", "opaque-id");
        opaqueTool.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        expect(List.of(new StackSlot(opaqueTool, null)), true, false); tests++;
        ItemStack convertingFood = item("minecraft:cooked_beef", 1);
        var food = convertingFood.get(DataComponents.FOOD);
        convertingFood.set(DataComponents.FOOD, new net.minecraft.world.food.FoodProperties(
            food.nutrition(), food.saturation(), food.canAlwaysEat(), food.eatSeconds(),
            Optional.of(item("ad_astra:coal_generator", 1)), List.of()));
        expect(List.of(new StackSlot(convertingFood, null)), true, false); tests++;
        for (long amount : new long[] {-1, 3001}) {
            expect(List.of(new StackSlot(oxygen("ad_astra:large_gas_tank", "ad_astra:oxygen", amount), null)), true, false); tests++;
        }
        expect(List.of(new StackSlot(oxygen("ad_astra:large_gas_tank", "ad_astra:fuel", 1000), null)), true, false); tests++;
        expect(List.of(new StackSlot(oxygen("ad_astra:space_suit", "ad_astra:oxygen", 1001), EquipmentSlot.CHEST)), true, false); tests++;
        var extraTank = new ArrayList<>(personal);
        extraTank.add(new StackSlot(oxygen("ad_astra:large_gas_tank", "ad_astra:oxygen", 1), null));
        expect(extraTank, false, false); tests++;
        CargoScan bounded = new CargoScan(false);
        for (int i = 0; i < CargoPolicy.MAX_SLOTS; i++) bounded.stack(ItemStack.EMPTY, null, "budget");
        try { bounded.stack(ItemStack.EMPTY, null, "overflow"); throw new AssertionError("slot budget ignored"); }
        catch (CargoScan.Denied expected) { tests++; }
        return tests;
    }
}
