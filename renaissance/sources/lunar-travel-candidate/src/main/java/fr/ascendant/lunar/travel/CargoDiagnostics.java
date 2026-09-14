package fr.ascendant.lunar.travel;

import earth.terrarium.adastra.common.entities.vehicles.Rocket;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class CargoDiagnostics {
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lunar_cargo")
            .requires(source -> source.hasPermission(2) && LunarTravel.enabled(source.getServer()))
            .then(Commands.literal("gear_report").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    if (slot == net.minecraft.world.entity.EquipmentSlot.BODY) continue;
                    var stack = player.getItemBySlot(slot);
                    if (stack.isEmpty()) continue;
                    var keys = new java.util.ArrayList<String>();
                    for (var component : stack.getComponents()) {
                        if (keys.size() == 64) { keys.add("OVER_BUDGET"); break; }
                        keys.add(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type())));
                    }
                    String line = slot + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                        + " class=" + stack.getItem().getClass().getName() + " components=" + keys;
                    context.getSource().sendSuccess(() -> Component.literal(line), false);
                }
                return 1;
            }))
            .then(Commands.literal("check").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                if (!(player.getVehicle() instanceof Rocket rocket)) {
                    context.getSource().sendFailure(Component.literal("Mount the native rocket to scan its actual cargo.")); return 0;
                }
                String refusal = CargoVerifier.refusal(player, rocket);
                if (refusal != null) { context.getSource().sendFailure(Component.literal(refusal)); return 0; }
                context.getSource().sendSuccess(() -> Component.literal("CARGO SCAN ALLOW: current supported surfaces only; no travel performed."), false);
                return 1;
            }))
            .then(Commands.literal("fixture").executes(context -> {
                try {
                    int count = CargoFixture.run(context.getSource().getServer().registryAccess());
                    context.getSource().sendSuccess(() -> Component.literal("CARGO NATIVE FIXTURE PASS " + count
                        + " cases: real kit+4suit allowed; repeat raw/industrial bag denied; fixture stacks unchanged. No player/world mutation."), false);
                    return 1;
                } catch (Exception | LinkageError | AssertionError failure) {
                    context.getSource().sendFailure(Component.literal("CARGO FIXTURE FAIL: " + failure)); return 0;
                }
            })));
    }
}
