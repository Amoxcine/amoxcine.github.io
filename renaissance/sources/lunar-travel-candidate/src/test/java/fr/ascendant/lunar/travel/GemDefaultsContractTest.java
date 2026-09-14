package fr.ascendant.lunar.travel;

import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Inspects pinned native bytecode without loading or initializing Minecraft classes. */
public final class GemDefaultsContractTest {
    private static int checks;
    private static void check(boolean ok, String reason) { checks++; if (!ok) throw new AssertionError(reason); }
    private static ClassNode read(String name) throws Exception {
        try (var in = GemDefaultsContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new AssertionError("Missing pinned class: " + name);
            var node = new ClassNode(); new ClassReader(in.readAllBytes()).accept(node, 0); return node;
        }
    }
    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
    private static boolean field(AbstractInsnNode instruction, String owner, String name) {
        return instruction instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC
            && f.owner.equals(owner) && f.name.equals(name);
    }
    private static boolean call(AbstractInsnNode instruction, String owner, String name) {
        return instruction instanceof MethodInsnNode m && m.owner.equals(owner) && m.name.equals(name);
    }
    public static void main(String[] args) throws Exception {
        String gem = "dev/shadowsoffire/apotheosis/socket/gem/GemItem";
        String item = "net/minecraft/world/item/Item";
        String components = "net/minecraft/core/component/DataComponents";
        String builder = "net/minecraft/core/component/DataComponentMap$Builder";
        var gemClass = read(gem);
        check(gemClass.superName.equals(item), "GemItem inheritance changed");
        var ctor = gemClass.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        var code = new ArrayList<AbstractInsnNode>();
        for (var instruction : ctor.instructions) if (instruction.getOpcode() >= 0) code.add(instruction);
        check(code.size() == 4 && code.get(0) instanceof VarInsnNode a && a.var == 0
            && code.get(1) instanceof VarInsnNode b && b.var == 1
            && call(code.get(2), item, "<init>") && code.get(3).getOpcode() == Opcodes.RETURN,
            "GemItem no longer delegates unchanged Properties to Item");
        var registration = read("dev/shadowsoffire/apotheosis/Apoth$Items").methods.stream()
            .filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow();
        int registrations = 0;
        for (var instruction : registration.instructions) if (instruction instanceof LdcInsnNode literal && "gem".equals(literal.cst)) {
            registrations++;
            var factory = next(instruction);
            check(factory instanceof InvokeDynamicInsnNode dynamic && Arrays.stream(dynamic.bsmArgs).anyMatch(arg ->
                arg instanceof Handle h && h.getOwner().equals(gem) && h.getName().equals("<init>")
                    && h.getDesc().equals("(Lnet/minecraft/world/item/Item$Properties;)V")), "Gem constructor registration changed");
            check(next(factory) instanceof MethodInsnNode m && m.owner.equals("dev/shadowsoffire/placebo/registry/DeferredHelper")
                && m.name.equals("item") && m.desc.startsWith("(Ljava/lang/String;Ljava/util/function/Function;)"),
                "Gem registration now modifies Properties");
        }
        check(registrations == 1, "Expected one gem registration");
        var init = read(components).methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow();
        var expected = Map.of("ENCHANTMENTS", "net/minecraft/world/item/enchantment/ItemEnchantments",
            "ATTRIBUTE_MODIFIERS", "net/minecraft/world/item/component/ItemAttributeModifiers");
        var found = new HashSet<String>();
        boolean repair = false;
        for (var instruction : init.instructions) {
            for (var entry : expected.entrySet()) if (field(instruction, components, entry.getKey())) {
                check(field(next(instruction), entry.getValue(), "EMPTY") && call(next(next(instruction)), builder, "set"),
                    "Native common default changed: " + entry.getKey());
                found.add(entry.getKey());
            }
            if (field(instruction, components, "REPAIR_COST")) {
                var zero = next(instruction);
                check(zero.getOpcode() == Opcodes.ICONST_0 && call(next(zero), "java/lang/Integer", "valueOf")
                    && call(next(next(zero)), builder, "set"), "Native repair cost default is not exactly zero");
                repair = true;
            }
        }
        check(found.equals(expected.keySet()) && repair, "Missing inherited native gem defaults");
        var properties = read(item + "$Properties").methods.stream().filter(m -> m.name.equals("buildComponents")).findFirst().orElseThrow();
        boolean inherits = false;
        for (var instruction : properties.instructions) inherits |= field(instruction, components, "COMMON_ITEM_COMPONENTS");
        check(inherits, "Properties no longer inherits COMMON_ITEM_COMPONENTS");
        System.out.println("PASS native gem defaults: " + checks + " bytecode contracts; no game boot. Native scanner fixture still required.");
    }
}
