package local.lunar;

import static local.lunar.ContractTest.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.tree.analysis.BasicValue;

public final class PortalContractTest {
    private static final class TypedInterpreter extends BasicInterpreter {
        TypedInterpreter() { super(Opcodes.ASM9); }
        @Override public BasicValue newValue(Type type) {
            if (type != null && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)) return new BasicValue(type);
            return super.newValue(type);
        }
        @Override public BasicValue merge(BasicValue first, BasicValue second) {
            if (first.equals(second)) return first;
            if (first.getType() != null && first.getType().equals(Type.getObjectType("null"))) return second;
            if (second.getType() != null && second.getType().equals(Type.getObjectType("null"))) return first;
            return super.merge(first, second);
        }
        @Override public BasicValue binaryOperation(AbstractInsnNode instruction, BasicValue first, BasicValue second)
                throws org.objectweb.asm.tree.analysis.AnalyzerException {
            if (instruction.getOpcode() == Opcodes.AALOAD && first.getType() != null && first.getType().getSort() == Type.ARRAY)
                return newValue(Type.getType(first.getType().getDescriptor().substring(1)));
            return super.binaryOperation(instruction, first, second);
        }
    }
    private static int checks;
    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
    private static AbstractInsnNode next(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    @SuppressWarnings("unchecked")
    private static int hooks(String name) throws Exception {
        var mixin = read("local.lunar.mixin." + name);
        var meta = annotation(mixin.invisibleAnnotations, "Mixin");
        check(Boolean.FALSE.equals(value(meta, "remap")), name + " remap");
        var target = read(((List<Type>) value(meta, "value")).getFirst().getInternalName());
        if (!name.endsWith("Accessor")) check((target.access & Opcodes.ACC_INTERFACE) == (mixin.access & Opcodes.ACC_INTERFACE), "Interface kind " + name);
        int count = 0;
        for (var handler : mixin.methods) {
            var accessor = annotation(handler.visibleAnnotations, "Accessor");
            if (accessor != null) {
                check(target.fields.stream().anyMatch(f -> f.name.equals(value(accessor, "value"))
                        && Type.getType(f.desc).equals(Type.getReturnType(handler.desc))), "Exact read-only accessor field");
                check(Type.getArgumentTypes(handler.desc).length == 0, "Getter only, no setter");
                continue;
            }
            new Analyzer<>(new BasicVerifier()).analyze(mixin.name, handler);
            var annotation = annotation(handler.visibleAnnotations, "Inject");
            var modify = annotation(handler.visibleAnnotations, "ModifyVariable");
            if (annotation == null && modify == null) continue;
            count++;
            var inject = annotation == null ? modify : annotation;
            check(Integer.valueOf(1).equals(value(inject, "require")), "Fail hard " + name);
            var selector = ((List<String>) value(inject, "method")).getFirst();
            var nativeMethod = method(target, selector);
            check((nativeMethod.access & Opcodes.ACC_STATIC) == (handler.access & Opcodes.ACC_STATIC), "Staticness " + name);
            var args = Type.getArgumentTypes(nativeMethod.desc);
            var hookArgs = Type.getArgumentTypes(handler.desc);
            if (modify != null) {
                check(Boolean.TRUE.equals(value(modify, "argsOnly")), "Only native listener argument");
                check(hookArgs.length == 1 && hookArgs[0].equals(Type.getReturnType(handler.desc)), "Listener replacement descriptor");
                check(Arrays.stream(args).filter(a -> a.equals(hookArgs[0])).count() == 1, "Unique portal listener argument");
                check("HEAD".equals(value((AnnotationNode) value(modify, "at"), "value")), "Listener installed before mutation");
            } else {
                check(Arrays.equals(args, Arrays.copyOf(hookArgs, args.length)), "Native arguments " + name);
                check(hookArgs[args.length].getClassName().endsWith(Type.getReturnType(nativeMethod.desc).equals(Type.VOID_TYPE)
                        ? ".CallbackInfo" : ".CallbackInfoReturnable"), "Callback " + name);
                var at = ((List<AnnotationNode>) value(inject, "at")).getFirst();
                String point = (String) value(at, "value");
                if (!point.equals("RETURN")) check(Boolean.TRUE.equals(value(inject, "cancellable")), "Cancellable " + name);
                if (point.equals("INVOKE")) {
                    String targetCall = (String) value(at, "target");
                    int matches = 0;
                    for (var insn : nativeMethod.instructions) if (insn instanceof MethodInsnNode c
                            && targetCall.equals("L" + c.owner + ";" + c.name + c.desc)) matches++;
                    check(matches == 1, "Unique native invocation " + targetCall);
                } else check(point.equals("HEAD") || point.equals("RETURN"), "Narrow supported point");
            }
            for (var insn : handler.instructions) {
                check(insn.getOpcode() != Opcodes.PUTFIELD && insn.getOpcode() != Opcodes.PUTSTATIC, "No native field mutation " + name);
                if (insn instanceof MethodInsnNode c) check(!List.of("shrink", "discard", "removeAndSaveEntity", "createEntity",
                        "teleportTo", "changeDimension", "addPortalCooldown", "processDelayCooldown", "setBlock", "destroyBlock").contains(c.name),
                        "Rejection must not perform native operation " + name);
            }
            System.out.println("PORTAL contract " + name + " -> " + selector);
        }
        return count;
    }

    private static void ordering() throws Exception {
        var warp = read("com.hollingsworth.arsnouveau.common.items.WarpScroll");
        check(call(method(warp, "onEntityItemUpdate"), "trySpawnPortal") < call(method(warp, "onEntityItemUpdate"), "shrink"), "Scroll creation before shrink");
        call(method(read("com.hollingsworth.arsnouveau.common.items.StableWarpScroll"), "useOn"), "addEvent");
        var ritual = method(read("com.hollingsworth.arsnouveau.common.ritual.RitualWarp"), "tick");
        check(call(ritual, "incrementProgress") < call(ritual, "teleportTo"), "Ritual before progress");
        check(java.util.stream.StreamSupport.stream(ritual.instructions.spliterator(), false)
                .noneMatch(i -> i instanceof MethodInsnNode c && c.name.equals("changeDimension")), "Ritual must not be claimed cross-dimensional");
        var tile = read("com.hollingsworth.arsnouveau.common.block.tile.PortalTile");
        call(method(tile, "warp"), "teleportEntityTo");
        call(method(tile, "tick"), "teleportEntityTo");
        call(method(tile, "teleportEntityTo"), "changeDimension");
        call(method(tile, "tick"), "clear"); // Queue cleanup intentionally preserved.

        var frame = method(read("io.redspace.ironsspellbooks.block.portal_frame.PortalFrameBlockEntity"), "teleport");
        check(call(frame, "processDelayCooldown") < call(frame, "getConnectedPortalPos"), "Iron final callback is too late");
        var iron = read("io.redspace.ironsspellbooks.entity.spells.portal.PortalEntity");
        var each = method(iron, "lambda$checkForEntitiesToTeleport$2");
        check(call(each, "processDelayCooldown") < call(each, "getPortalData"), "Iron entity gate must precede candidate iteration");
        var manager = method(read("io.redspace.ironsspellbooks.capabilities.magic.PortalManager"),
                "getPortalData(Ljava/util/UUID;)Lio/redspace/ironsspellbooks/entity/spells/portal/PortalData;");
        call(manager, "get");
        check(java.util.stream.StreamSupport.stream(manager.instructions.spliterator(), false).noneMatch(i -> i.getOpcode() == Opcodes.PUTFIELD
                        || i instanceof MethodInsnNode c && List.of("put", "computeIfAbsent").contains(c.name)), "Pure portal lookup");
        var ironGuard = method(read("local.lunar.PortalGuards"), "iron");
        for (var insn : ironGuard.instructions) if (insn instanceof MethodInsnNode c)
            check(!List.of("getConnectedPortalPos", "of").contains(c.name), "Incomplete Iron pairs must not call Optional.of resolver");
        var tracker = read("local.lunar.TrackGuards");
        for (var m : tracker.methods) for (var insn : m.instructions) if (insn instanceof MethodInsnNode c)
            check(!List.of("leading", "trailing", "getLeadingPoint", "getTrailingPoint").contains(c.name), "Bogey convenience getters mutate orientation");

        var provider = read("com.simibubi.create.api.contraption.train.PortalTrackProvider");
        call(method(provider, "getOtherSide"), "findExit");
        var portals = method(read("com.simibubi.create.content.trains.track.AllPortalTracks"), "fromPortal");
        check(call(portals, "getLevel") < call(portals, "getPortalDestination"), "Create portal destination may have side effects");
        var track = read("com.simibubi.create.content.trains.track.TrackBlock");
        var connect = method(track, "connectToPortal");
        check(call(connect, "getOtherSide") < call(connect, "setBlock"), "Track exit before blocks");
        check(call(connect, "getOtherSide") < call(connect, "destroyBlock"), "Null exit destructive fallback is guarded");
        int levelCall = call(connect, "level");
        check(levelCall < call(connect, "setBlock") && levelCall < call(connect, "bind"), "Resolved guard before creation/bind");
        var frames = new Analyzer<>(new TypedInterpreter()).analyze(track.name, connect);
        var frameAt = frames[levelCall];
        check(frameAt.getLocals() >= 16, "Expected native captured locals");
        var resolved = method(read("local.lunar.mixin.CreateTrackConnectionMixin"), "lunar$resolved");
        var captured = Type.getArgumentTypes(resolved.desc);
        check(captured.length == 16, "Four callback args + twelve locals");
        for (int slot = 4; slot <= 15; slot++) {
            var nativeType = frameAt.getLocal(slot).getType();
            var declared = captured[slot];
            check(nativeType != null && (declared.equals(nativeType)
                    || nativeType.equals(Type.getObjectType("null")) && declared.getSort() == Type.OBJECT
                    || declared.getSort() == Type.BOOLEAN && nativeType.equals(Type.INT_TYPE)), "Exact captured local slot " + slot + ": " + nativeType + " vs " + declared);
        }

        var point = read("com.simibubi.create.content.trains.entity.TravellingPoint");
        var travel = point.methods.stream().filter(m -> m.name.equals("travel") && Type.getArgumentTypes(m.desc).length == 6).findFirst().orElseThrow();
        int vetoes = 0;
        for (var insn : travel.instructions) if (insn instanceof MethodInsnNode c && c.owner.endsWith("$IPortalListener") && c.name.equals("test")) {
            vetoes++;
            var branch = next(c);
            check(branch instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IFEQ, "Native portal veto branch");
            boolean skipsEdgeReplacement = false;
            for (var i = branch.getNext(); i != ((JumpInsnNode) branch).label; i = i.getNext()) {
                check(!(i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD && List.of("node1", "node2", "edge").contains(f.name)), "No boundary assignment on veto");
                if (i instanceof JumpInsnNode j && j.getOpcode() == Opcodes.GOTO
                        && travel.instructions.indexOf(j.label) > travel.instructions.indexOf(((JumpInsnNode) branch).label)) skipsEdgeReplacement = true;
            }
            check(skipsEdgeReplacement, "Veto exits before crossing in both directions");
        }
        check(vetoes == 2, "Forward/reverse portal veto coverage");
        var carriage = read("com.simibubi.create.content.trains.entity.Carriage");
        call(method(carriage, "manageEntities"), "createEntity");
        call(method(carriage, "manageEntities"), "removeAndSaveEntity");
        call(method(carriage, "updateContraptionAnchors"), "updatePassengerLoadout");
        call(method(read("com.simibubi.create.content.trains.graph.TrackGraph"), "connectNodes"), "putConnection");
    }

    public static void main(String[] args) throws Exception {
        Path classes = Path.of(args[0]);
        dump = classes.getParent().resolve("portal-native-contracts");
        Files.createDirectories(dump);
        var config = JsonParser.parseString(Files.readString(classes.resolve("lunar-portals.mixins.json"))).getAsJsonObject();
        check(config.get("required").getAsBoolean(), "Required config");
        int count = 0;
        for (var name : config.getAsJsonArray("mixins")) count += hooks(name.getAsString());
        check(count == 24, "24 new injections including listener replacement");
        ordering();
        System.out.println("PASS " + checks + " portal/track static checks; 24 new hooks. Native transformation/gameplay NOT run.");
    }
}
