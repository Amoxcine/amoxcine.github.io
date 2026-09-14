package local.lunar;

import com.google.gson.JsonParser;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.util.TraceClassVisitor;

/** Reads native bytecode without loading Minecraft classes or starting a game. */
public final class ContractTest {
    private static int checks;
    static Path dump;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static ClassNode read(String name) throws Exception {
        String resource = name.replace('.', '/') + ".class";
        try (var in = ContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            check(in != null, "Missing native class " + name);
            var reader = new ClassReader(in);
            var node = new ClassNode();
            reader.accept(node, 0);
            var text = new StringWriter();
            reader.accept(new TraceClassVisitor(new PrintWriter(text)), 0);
            Files.writeString(dump.resolve(name.replace('/', '.').replace('$', '_') + ".txt"), text.toString());
            return node;
        }
    }

    static AnnotationNode annotation(List<AnnotationNode> annotations, String name) {
        if (annotations == null) return null;
        return annotations.stream().filter(a -> a.desc.endsWith('/' + name + ";")).findFirst().orElse(null);
    }

    static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    static MethodNode method(ClassNode node, String selector) {
        var found = node.methods.stream().filter(m -> selector.contains("(")
                ? (m.name + m.desc).equals(selector) : m.name.equals(selector)).toList();
        check(found.size() == 1, "Ambiguous/missing native method " + node.name + "." + selector);
        return found.getFirst();
    }

    static int call(MethodNode method, String name) {
        for (int i = 0; i < method.instructions.size(); i++)
            if (method.instructions.get(i) instanceof MethodInsnNode c && c.name.equals(name)) return i;
        throw new AssertionError("Missing call " + method.name + " -> " + name);
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    @SuppressWarnings("unchecked")
    private static int injectionContracts(String name) throws Exception {
        ClassNode mixin = read("local.lunar.mixin." + name);
        var meta = annotation(mixin.invisibleAnnotations, "Mixin");
        check(Boolean.FALSE.equals(value(meta, "remap")), name + " must use pinned native names");
        Type type = ((List<Type>) value(meta, "value")).getFirst();
        ClassNode target = read(type.getInternalName());
        check((mixin.access & Opcodes.ACC_INTERFACE) == (target.access & Opcodes.ACC_INTERFACE), name + " target kind");
        for (FieldNode shadow : mixin.fields) {
            if (annotation(shadow.visibleAnnotations, "Shadow") != null || annotation(shadow.invisibleAnnotations, "Shadow") != null)
                check(target.fields.stream().anyMatch(f -> f.name.equals(shadow.name) && f.desc.equals(shadow.desc)), name + " shadow");
        }
        int count = 0;
        for (MethodNode handler : mixin.methods) {
            new Analyzer<>(new BasicVerifier()).analyze(mixin.name, handler);
            var inject = annotation(handler.visibleAnnotations, "Inject");
            if (inject == null) continue;
            count++;
            check(Boolean.TRUE.equals(value(inject, "cancellable")), name + " not cancellable");
            check(Integer.valueOf(1).equals(value(inject, "require")), name + " not fail-fast");
            var at = ((List<AnnotationNode>) value(inject, "at")).getFirst();
            check("HEAD".equals(value(at, "value")), name + " not before native side effects");
            var selectors = (List<String>) value(inject, "method");
            check(selectors.size() == 1, name + " unexpected targets");
            MethodNode nativeMethod = method(target, selectors.getFirst());
            Type[] args = Type.getArgumentTypes(nativeMethod.desc);
            Type[] hookArgs = Type.getArgumentTypes(handler.desc);
            check(hookArgs.length == args.length + 1, name + " argument count");
            check(Arrays.equals(args, Arrays.copyOf(hookArgs, args.length)), name + " exact native arguments");
            String callback = Type.getReturnType(nativeMethod.desc).equals(Type.VOID_TYPE)
                    ? "CallbackInfo" : "CallbackInfoReturnable";
            check(hookArgs[args.length].getClassName().endsWith('.' + callback), name + " callback type");
            check((handler.access & Opcodes.ACC_STATIC) == (nativeMethod.access & Opcodes.ACC_STATIC), name + " static mismatch");
            for (AbstractInsnNode instruction : handler.instructions) {
                check(instruction.getOpcode() != Opcodes.PUTFIELD && instruction.getOpcode() != Opcodes.PUTSTATIC,
                        name + " must not mutate fields");
                if (instruction instanceof MethodInsnNode c) {
                    check(!List.of("shrink", "setCount", "remove", "clear", "extractOP", "extractAEPower",
                            "getBlockEntity", "getCapability", "getLevel", "setItem", "openMenu").contains(c.name),
                            name + " forbidden mutating/resolving call " + c.name);
                }
            }
            System.out.println("HEAD contract " + name + " -> " + target.name + "." + nativeMethod.name + nativeMethod.desc);
        }
        return count;
    }

    private static void nativeOrdering() throws Exception {
        var port = method(read("appeng.blockentity.spatial.SpatialIOPortBlockEntity"), "lambda$transition$1");
        int prevented = call(port, "isTransitionPrevented");
        int transition = call(port, "doSpatialTransition");
        int mutation = call(port, "setItemDirect");
        var jump = nextCode(port.instructions.get(prevented));
        check(jump instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IFNE
                && port.instructions.indexOf(j.label) > mutation, "Spatial prevent must skip transition + mutations");
        check(call(port, "postEvent") < prevented && prevented < transition && transition < mutation, "Spatial ordering");
        var powers = new ArrayList<Integer>();
        for (int i = 0; i < port.instructions.size(); i++)
            if (port.instructions.get(i) instanceof MethodInsnNode c && c.name.equals("extractAEPower")) powers.add(i);
        check(powers.size() == 2 && powers.getFirst() < prevented && powers.getLast() > transition, "Spatial simulate/debit order");
        var event = read("appeng.api.networking.events.GridSpatialEvent");
        check(event.fields.stream().anyMatch(f -> f.name.equals("spatialIoLevel") && (f.access & Opcodes.ACC_PUBLIC) != 0), "Spatial public level");
        method(event, "preventTransition()V");
        method(read("appeng.api.networking.GridHelper"), "addEventHandler");

        var use = method(read("net.minecraft.server.level.ServerPlayerGameMode"), "useItemOn");
        int cancel = call(use, "isCanceled");
        check(call(use, "onRightClickBlock") < cancel && cancel < call(use, "openMenu"), "Native chest event before menu");
        var cancelBranch = nextCode(use.instructions.get(cancel));
        check(cancelBranch instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IFEQ, "Native canceled branch");
        boolean returns = false;
        for (var i = cancelBranch.getNext(); i != ((JumpInsnNode) cancelBranch).label; i = i.getNext())
            if (i.getOpcode() == Opcodes.ARETURN) returns = true;
        check(returns, "Canceled native interaction returns before opening");
        var packet = method(read("net.minecraft.server.network.ServerGamePacketListenerImpl"), "handleContainerClick");
        int valid = call(packet, "stillValid");
        check(call(packet, "ensureRunningOnSameThread") < valid && valid < call(packet, "clicked")
                && valid < call(packet, "setRemoteSlotNoCopy"), "Native stale-menu guard before transaction");
        var validBranch = nextCode(packet.instructions.get(valid));
        check(validBranch instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IFNE, "Native valid branch");
        boolean skipsTransaction = false;
        for (var i = validBranch.getNext(); i != ((JumpInsnNode) validBranch).label; i = i.getNext())
            if (i instanceof JumpInsnNode j && j.getOpcode() == Opcodes.GOTO
                    && packet.instructions.indexOf(j.label) > call(packet, "setRemoteCarried")) skipsTransaction = true;
        check(skipsTransaction, "Invalid menu must skip all packet transactions");
        var chest = read("net.minecraft.world.inventory.ChestMenu");
        call(method(chest, "stillValid"), "stillValid");

        var energy = method(read("com.brandon3055.draconicevolution.api.modules.entities.EnergyLinkEntity"), "tick");
        check(call(energy, "updateConnection") < call(energy, "modifyEnergyStored"), "Energy connection debit precedes transfer: HEAD required");
        var collection = read("com.brandon3055.draconicevolution.api.modules.entities.EnderCollectionEntity");
        call(method(collection, "insertStack"), "getCount");
        call(method(read("local.lunar.mixin.EnderCollectionMixin"), "lunar$single"), "getCount");
        call(method(read("local.lunar.mixin.EnderCollectionMixin"), "lunar$bulk"), "unchangedRemainder");
        var resolver = read("net.pedroksl.ae2addonlib.api.IGridLinkedItem");
        var three = method(resolver, "getLinkedGrid(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Ljava/util/function/Consumer;)Lappeng/api/networking/IGrid;");
        check(call(three, "getLevel") < call(three, "getTickingBlockEntity") && call(three, "getTickingBlockEntity") < call(three, "getGrid"), "AdvancedAE remote resolver contract");
        call(method(resolver, "getLinkedGrid(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/networking/IGrid;"), "getLinkedGrid");
        var armor = read("net.pedroksl.advanced_ae.common.items.armors.QuantumArmorBase");
        check(armor.methods.stream().noneMatch(m -> m.name.equals("getLinkedGrid")), "Armor must inherit guarded resolver");
        var upgrade = read("net.pedroksl.advanced_ae.common.items.armors.IUpgradeableItem");
        check(upgrade.interfaces.contains("net/pedroksl/ae2addonlib/api/IGridLinkedItem"), "Armor linked interface");
        check(upgrade.methods.stream().noneMatch(m -> m.name.equals("getLinkedGrid")), "No intermediate override");
        var helmet = read("net.pedroksl.advanced_ae.common.items.armors.QuantumHelmet");
        check(helmet.superName.equals(armor.name) && helmet.methods.stream().noneMatch(m -> m.name.equals("getLinkedGrid")), "Helmet inherits guarded resolver");
        var upgrades = read("net.pedroksl.advanced_ae.common.items.upgrades.UpgradeCards");
        for (String name : List.of("autoFeed", "autoStock", "recharging")) {
            var m = method(upgrades, name);
            int resolve = call(m, "getLinkedGrid");
            int service = call(m, name.equals("recharging") ? "getEnergyService" : "getStorageService");
            check(resolve < service, "Armor resolver before service access: " + name);
            boolean rejectsNull = false;
            for (int i = resolve + 1; i < service; i++) {
                if (m.instructions.get(i) instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IFNULL
                        && m.instructions.indexOf(j.label) > service) rejectsNull = true;
            }
            check(rejectsNull, "Armor caller rejects missing grid: " + name);
        }
        var interfaceSupport = read("org.spongepowered.asm.mixin.MixinEnvironment$Feature$1");
        call(method(interfaceSupport, "isEnabled"), "supports");
        var iron = method(read("io.redspace.ironsspellbooks.spells.ender.SummonEnderChestSpell"), "onCast");
        check(call(iron, "attemptRemoveScrollAfterCast") < call(iron, "getEnderChestInventory"), "Iron scroll consumed before menu: HEAD required");
        var bg = method(read("com.direwolf20.buildinggadgets2.util.BuildingUtils"), "getHandlerFromBound");
        check(call(bg, "getLevel") < call(bg, "getBlockEntity") && call(bg, "getBlockEntity") < call(bg, "getCapability"), "BuildingGadgets capability order");
        var ae = read("com.direwolf20.buildinggadgets2.integration.AE2Methods");
        for (String name : List.of("checkAE2ForItems", "checkAE2ForFluids", "insertIntoAE2", "insertFluidIntoAE2")) {
            var m = method(ae, name);
            check(call(m, "getLevel") < call(m, "getGrid"), "BuildingGadgets AE bypass " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        Path classes = Path.of(args[0]);
        dump = classes.getParent().resolve("native-contracts");
        Files.createDirectories(dump);
        var config = JsonParser.parseString(Files.readString(classes.resolve("lunar-extra.mixins.json"))).getAsJsonObject();
        check(config.get("required").getAsBoolean(), "Mixins required");
        check(config.getAsJsonObject("injectors").get("defaultRequire").getAsInt() == 1, "Default require");
        check("JAVA_21".equals(config.get("compatibilityLevel").getAsString()), "Java 21");
        int hooks = 0;
        for (var name : config.getAsJsonArray("mixins")) hooks += injectionContracts(name.getAsString());
        check(hooks == 14, "Expected all 14 HEAD injections");
        var toml = new TomlParser().parse(Files.readString(classes.resolve("META-INF/neoforge.mods.toml")));
        check("javafml".equals(toml.get("modLoader")), "Native mod metadata");
        var entry = read("local.lunar.LunarExtraRestrictions");
        call(method(entry, "<init>"), "registerConfig");
        call(method(entry, "<init>"), "addEventHandler");
        call(method(entry, "rightClick"), "setCanceled");
        call(method(entry, "start"), "getServer");
        var init = method(entry, "<clinit>");
        var define = init.instructions.get(call(init, "define"));
        var previous = define.getPrevious();
        while (previous.getOpcode() < 0) previous = previous.getPrevious();
        check(previous.getOpcode() == Opcodes.ICONST_0, "Opt-in config default must be false");
        check(entry.fields.stream().anyMatch(f -> f.name.equals("session") && (f.access & Opcodes.ACC_VOLATILE) != 0), "Server session visibility");
        nativeOrdering();
        System.out.println("PASS " + checks + " native/API/ASM contract checks; 14 HEAD hooks + 2 native pre-events");
        System.out.println("Static bytecode contracts only: no Mixin transformation, Minecraft boot, world mutation or join test.");
    }
}
