package fr.ascendant.lunar.travel;

import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Structural checks only: no launch, Mixin transformation, or class initialization. */
public final class HookShapeTest {
    private static int hooks, sites, accessors;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
    }
    private static ClassNode target(String name) throws Exception {
        try (var in = HookShapeTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (in == null) throw new AssertionError("Missing target: " + name);
            return read(in.readAllBytes());
        }
    }
    private static Map<String, Object> values(AnnotationNode annotation) {
        var result = new HashMap<String, Object>();
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        return result;
    }
    private static List<AnnotationNode> annotations(MethodNode method) {
        var result = new ArrayList<AnnotationNode>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0], "fr/ascendant/lunar/travel/mixin");
        try (var paths = Files.list(directory)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                ClassNode mixin = read(Files.readAllBytes(file));
                AnnotationNode declaration = mixin.invisibleAnnotations.stream()
                    .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
                var metadata = values(declaration);
                String targetName = metadata.containsKey("value")
                    ? ((List<Type>) metadata.get("value")).getFirst().getClassName()
                    : ((List<String>) metadata.get("targets")).getFirst();
                ClassNode nativeClass = target(targetName);
                for (FieldNode field : mixin.fields) {
                    if (field.visibleAnnotations == null) continue;
                    if (field.visibleAnnotations.stream().noneMatch(a -> a.desc.endsWith("/Shadow;"))) continue;
                    check(nativeClass.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)),
                        "Missing shadow field " + targetName + "." + field.name);
                }
                for (MethodNode handler : mixin.methods) for (AnnotationNode annotation : annotations(handler))
                    if (annotation.desc.endsWith("/Shadow;"))
                        check(nativeClass.methods.stream().anyMatch(m -> m.name.equals(handler.name) && m.desc.equals(handler.desc)),
                            "Missing shadow method " + targetName + "." + handler.name);
                for (MethodNode handler : mixin.methods) for (AnnotationNode annotation : annotations(handler)) {
                    if (annotation.desc.endsWith("/Accessor;")) {
                        String field = (String) values(annotation).get("value");
                        check(Type.getArgumentTypes(handler.desc).length == 0, "Cargo accessor must be read-only");
                        check(nativeClass.fields.stream().anyMatch(f -> f.name.equals(field)
                            && Type.getType(f.desc).equals(Type.getReturnType(handler.desc))), "Missing/mismatched accessor " + field);
                        accessors++;
                        continue;
                    }
                    boolean redirect = annotation.desc.endsWith("/Redirect;");
                    if (!redirect && !annotation.desc.endsWith("/Inject;")) continue;
                    var data = values(annotation);
                    int require = (Integer) data.get("require");
                    List<String> selectors = (List<String>) data.get("method");
                    Object atValue = data.get("at");
                    AnnotationNode at = atValue instanceof List<?> list ? (AnnotationNode) list.getFirst() : (AnnotationNode) atValue;
                    var point = values(at);
                    int matches = 0;
                    for (String selector : selectors) {
                        List<MethodNode> methods = nativeClass.methods.stream().filter(m ->
                            selector.equals(m.name) || selector.equals(m.name + m.desc)).toList();
                        check(methods.size() == 1, "Ambiguous/missing target " + targetName + "." + selector);
                        MethodNode method = methods.getFirst();
                        if (!redirect) {
                            check("HEAD".equals(point.get("value")), "Expected HEAD guard");
                            check(Boolean.TRUE.equals(data.get("cancellable")) || Set.of("lunar$pending", "lunar$saveReceipt", "lunar$readReceipt").contains(handler.name), "Veto not cancellable");
                            check((method.access & Opcodes.ACC_STATIC) == (handler.access & Opcodes.ACC_STATIC), "Static mismatch");
                            Type[] nativeArgs = Type.getArgumentTypes(method.desc), hookArgs = Type.getArgumentTypes(handler.desc);
                            check(hookArgs.length == nativeArgs.length + 1, "Callback arguments: " + handler.name);
                            for (int i = 0; i < nativeArgs.length; i++)
                                check(nativeArgs[i].equals(hookArgs[i]), "Hook arg mismatch: " + handler.name);
                            matches++;
                        } else {
                            String invocation = (String) point.get("target");
                            for (var insn : method.instructions) if (insn instanceof MethodInsnNode call
                                    && invocation.equals("L" + call.owner + ";" + call.name + call.desc)) {
                                var expected = new ArrayList<Type>();
                                if (call.getOpcode() != Opcodes.INVOKESTATIC) expected.add(Type.getObjectType(call.owner));
                                expected.addAll(List.of(Type.getArgumentTypes(call.desc)));
                                check(expected.equals(List.of(Type.getArgumentTypes(handler.desc))), "Redirect args: " + handler.name);
                                check(Type.getReturnType(call.desc).equals(Type.getReturnType(handler.desc)), "Redirect result: " + handler.name);
                                check((method.access & Opcodes.ACC_STATIC) == (handler.access & Opcodes.ACC_STATIC), "Redirect static mismatch");
                                matches++;
                            }
                        }
                    }
                    check(matches == require, "Unexpected injection cardinality " + handler.name + ": " + matches + " != " + require);
                    hooks++; sites += matches;
                }
            }
        }
        for (String name : List.of("net.minecraft.world.entity.Entity", "net.minecraft.server.level.ServerPlayer")) {
            boolean found = false;
            for (MethodNode method : target(name).methods) if (method.name.equals("changeDimension"))
                for (var insn : method.instructions) if (insn instanceof MethodInsnNode call
                        && call.owner.equals("net/neoforged/neoforge/common/CommonHooks") && call.name.equals("onTravelToDimension")) found = true;
            check(found, "Patched NeoForge dimension event missing: " + name);
        }
        check(hooks >= 25, "Unexpectedly few guards");
        MethodNode dimension = target("net.minecraft.server.level.ServerPlayer").methods.stream()
            .filter(m -> m.name.equals("changeDimension")).findFirst().orElseThrow();
        var firstCalls = new ArrayList<String>();
        MethodInsnNode decision = null;
        for (var instruction : dimension.instructions) if (instruction instanceof MethodInsnNode call) {
            firstCalls.add(call.name);
            if (call.name.equals("onTravelToDimension")) { decision = call; break; }
        }
        check(firstCalls.equals(List.of("newLevel", "dimension", "onTravelToDimension")),
            "Native changeDimension mutates or calls another API before final veto");
        var branch = decision.getNext();
        while (branch.getOpcode() < 0) branch = branch.getNext();
        check(branch.getOpcode() == Opcodes.IFNE, "Native veto branch changed");
        var nullResult = branch.getNext();
        while (nullResult.getOpcode() < 0) nullResult = nullResult.getNext();
        check(nullResult.getOpcode() == Opcodes.ACONST_NULL && nullResult.getNext().getOpcode() == Opcodes.ARETURN,
            "Native veto no longer returns without side effects");
        boolean deferredHistory = false;
        for (MethodNode method : target("fr.ascendant.lunar.travel.NativeFlight").methods) {
            int nativeLand = -1, history = -1, consume = -1, index = 0;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.endsWith("/ModUtils") && call.name.equals("land")) nativeLand = index;
                    if (call.owner.endsWith("/LaunchingDimensionHandler") && call.name.equals("addSpawnLocation")) history = index;
                    if (call.owner.endsWith("/FlightTicket") && call.name.equals("consume")) consume = index;
                }
                index++;
            }
            if (nativeLand >= 0 && history >= 0 && consume >= 0) {
                check(history > nativeLand && consume > nativeLand, "History/ticket consumed before native landing");
                deferredHistory = true;
            }
        }
        check(deferredHistory, "Native landing success commit ordering not found");
        check(accessors == 7, "Expected seven read-only cargo accessors");
        for (String name : List.of("fr.ascendant.lunar.travel.CargoScan", "fr.ascendant.lunar.travel.CargoVerifier", "fr.ascendant.lunar.travel.PersonalGear"))
            for (MethodNode method : target(name).methods) for (var insn : method.instructions)
                if (insn instanceof MethodInsnNode call) check(!Set.of("getDropStack", "extract", "extractItem", "drain", "setItem",
                    "setStackInSlot", "removeItem", "set", "clearContent", "discard", "handleInvalidStacks").contains(call.name),
                    "Mutating cargo operation: " + name + "." + method.name + " calls " + call.name);
        System.out.println("PASS bytecode shape: " + hooks + " hooks, " + sites + " sites, " + accessors
            + " cargo accessors; scanner mutation-call audit; native veto-before-mutation and deferred history/ticket order checked. Mixin runtime NOT tested.");
    }
}
