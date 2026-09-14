package fr.ascendant.lunar.travel;

import earth.terrarium.adastra.common.entities.vehicles.Rocket;
import earth.terrarium.common_storage_lib.resources.fluid.FluidResource;
import fr.ascendant.lunar.travel.mixin.*;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Supported runtime surfaces only; missing adapters and opaque payloads deny, never certify empty. */
public final class CargoVerifier {
    private CargoVerifier() {}
    public static String refusal(ServerPlayer player, Rocket rocket) {
        try { scan(player, rocket); return null; }
        catch (CargoScan.Denied denied) { return denied.getMessage(); }
        catch (Exception | LinkageError failure) { return "CARGO_SCAN_ERROR:" + failure.getClass().getSimpleName(); }
    }
    static CargoScan scan(ServerPlayer player, Rocket rocket) throws Exception {
        if (player == null || rocket == null || !player.server.isSameThread() || player.hasDisconnected()
                || rocket.level() != player.level() || rocket.isRemoved()) CargoScan.deny("CARGO_ACTOR_INVALID");
        boolean outbound = player.level().dimension().location().toString().equals(TravelPolicy.EARTH);
        boolean first = outbound && (LunarTravel.ledger(player.server).fresh(player.getUUID())
            || NativeFlight.reservedFirstArrival(player, rocket));
        CargoScan scan = new CargoScan(first);
        var inventory = player.getInventory();
        if (inventory.items.size() != 36 || inventory.armor.size() != 4 || inventory.offhand.size() != 1)
            CargoScan.deny("CARGO_PLAYER_INVENTORY_SHAPE");
        list(scan, inventory.items, "player");
        EquipmentSlot[] armor = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        for (int i = 0; i < 4; i++) scan.stack(inventory.armor.get(i), armor[i], "armor:" + armor[i]);
        list(scan, inventory.offhand, "offhand");
        container(scan, rocket.inventory(), "rocket");
        menus(scan, player, rocket);
        curios(scan, player);
        accessories(scan, player);
        // Never call Rocket.getDropStack(): exact native bytecode transfers fuel OUT of the rocket.
        var fuel = rocket.fluidContainer();
        if (fuel.size() != 1) CargoScan.deny("CARGO_ROCKET_FLUID_SHAPE");
        var contents = fuel.getContents(0);
        long fuelAmount = ((ResourceStackAccessor) (Object) contents).lunar$rawAmount();
        if (fuelAmount < 0 || fuelAmount > 3000 || contents.resource() == null
                || !contents.resource().equals(FluidResource.of(contents.resource().getType())))
            CargoScan.deny("CARGO_ROCKET_FUEL_PAYLOAD");
        if ((!contents.resource().isBlank() || fuelAmount != 0)
                && !BuiltInRegistries.FLUID.getKey(contents.resource().getType()).toString().equals("ad_astra:fuel"))
            CargoScan.deny("CARGO_ROCKET_FUEL_TYPE");
        if (!BuiltInRegistries.ENTITY_TYPE.getKey(rocket.getType()).toString().equals("ad_astra:tier_1_rocket"))
            CargoScan.deny("CARGO_UNSUPPORTED_ROCKET");
        return scan;
    }
    private static void list(CargoScan scan, List<ItemStack> stacks, String name) {
        if (stacks == null) CargoScan.deny("CARGO_MISSING_SURFACE:" + name);
        scan.bound(stacks.size());
        for (int i = 0; i < stacks.size(); i++) scan.stack(stacks.get(i), null, name + ":" + i);
    }
    private static void container(CargoScan scan, Container inventory, String name) {
        if (inventory == null) CargoScan.deny("CARGO_MISSING_SURFACE:" + name);
        int size = inventory.getContainerSize(); scan.bound(size);
        for (int i = 0; i < size; i++) scan.stack(inventory.getItem(i), null, name + ":" + i);
    }
    private static void menus(CargoScan scan, ServerPlayer player, Rocket rocket) {
        Set<AbstractContainerMenu> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        IdentityHashMap<Container, Set<Integer>> slots = new IdentityHashMap<>();
        for (var menu : List.of(player.inventoryMenu, player.containerMenu)) {
            if (!seen.add(menu)) continue;
            scan.stack(menu.getCarried(), null, "cursor");
            if (menu.slots.size() > CargoPolicy.MAX_SLOTS) CargoScan.deny("CARGO_MENU_BUDGET");
            for (var slot : menu.slots) {
                if (slot.container == player.getInventory() || slot.container == rocket.inventory()) continue;
                if (slots.computeIfAbsent(slot.container, ignored -> new HashSet<>()).add(slot.getContainerSlot()))
                    scan.stack(slot.getItem(), null, "menu:" + slot.index);
            }
        }
    }
    private static void curios(CargoScan scan, ServerPlayer player) {
        var capability = CuriosApi.getCuriosInventory(player).orElseThrow(() -> new CargoScan.Denied("CARGO_CURIOS_MISSING"));
        if (!(capability instanceof CuriosCapabilityAccessor access)) CargoScan.deny("CARGO_CURIOS_UNSUPPORTED");
        var inventory = ((CuriosCapabilityAccessor) capability).lunar$inventory();
        var data = (CuriosInventoryAccessor) inventory;
        if (data.lunar$pendingLoad()) CargoScan.deny("CARGO_CURIOS_PENDING_LOAD");
        var all = data.lunar$all();
        if (all == null || all.size() > 64) CargoScan.deny("CARGO_CURIOS_GROUP_BUDGET");
        for (var entry : all.entrySet()) {
            var pair = entry.getValue();
            for (var stacks : List.of(pair.getStacks(), pair.getCosmeticStacks())) {
                int size = stacks.getSlots(); scan.bound(size);
                for (int i = 0; i < size; i++) scan.stack(stacks.getStackInSlot(i), null, "curios:" + entry.getKey() + ":" + i);
            }
        }
        list(scan, data.lunar$invalid(), "curios-invalid");
    }
    private static void accessories(CargoScan scan, ServerPlayer player) {
        var capability = AccessoriesCapability.get(player);
        if (capability == null || !(capability.getHolder() instanceof AccessoriesHolderImpl))
            CargoScan.deny("CARGO_ACCESSORIES_MISSING");
        var holder = (AccessoriesHolderImpl) capability.getHolder();
        var access = (AccessoriesHolderAccessor) holder;
        if (access.lunar$pendingLoad()) CargoScan.deny("CARGO_ACCESSORIES_PENDING_LOAD");
        var all = access.lunar$all();
        if (all == null || all.size() > 64) CargoScan.deny("CARGO_ACCESSORIES_GROUP_BUDGET");
        for (var entry : all.entrySet()) {
            container(scan, entry.getValue().getAccessories(), "accessories:" + entry.getKey());
            container(scan, entry.getValue().getCosmeticAccessories(), "accessories-cosmetic:" + entry.getKey());
        }
        list(scan, holder.invalidStacks, "accessories-invalid");
    }
}
