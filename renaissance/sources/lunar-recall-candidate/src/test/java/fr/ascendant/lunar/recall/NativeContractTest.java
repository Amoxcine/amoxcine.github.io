package fr.ascendant.lunar.recall;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Exact PRE transaction ordering and compiled adapter contracts, not live Mixin application. */
public final class NativeContractTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static ClassNode read(byte[] data) {
        var node = new ClassNode(); new ClassReader(data).accept(node, 0); return node;
    }
    private static ClassNode read(ZipFile zip, String name) throws Exception {
        var entry = zip.getEntry(name + ".class");
        check(entry != null, "native class exists " + name);
        try (var in = zip.getInputStream(entry)) { return read(in.readAllBytes()); }
    }
    private static MethodNode method(ClassNode owner, String name) {
        var found = owner.methods.stream().filter(m -> m.name.equals(name)).toList();
        check(found.size() == 1, "unique native method " + owner.name + "." + name);
        return found.getFirst();
    }
    private static List<MethodInsnNode> calls(MethodNode method, String name) {
        List<MethodInsnNode> found = new ArrayList<>();
        for (var insn : method.instructions) if (insn instanceof MethodInsnNode call && call.name.equals(name)) found.add(call);
        return found;
    }
    private static int one(MethodNode method, String name) {
        var found = calls(method,name);
        check(found.size() == 1, "one call " + method.name + " -> " + name);
        return method.instructions.indexOf(found.getFirst());
    }
    private static AbstractInsnNode next(AbstractInsnNode insn) {
        do { insn = insn.getNext(); } while (insn != null && insn.getOpcode() < 0);
        return insn;
    }
    private static int cancelled(MethodNode method, boolean returns) {
        int post = one(method, "post"), cancelled = one(method, "isCanceled");
        check(post < cancelled, "native checks event result " + method.name);
        var branch = next(method.instructions.get(cancelled));
        check(branch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ, "false cancellation reaches allowed branch");
        var denial = next(branch);
        check(denial.getOpcode() == (returns ? Opcodes.RETURN : Opcodes.GOTO), "cancelled return/loop skip");
        if (!returns) check(method.instructions.indexOf(((JumpInsnNode) denial).label) < post, "block teleporter skips this entity and debit");
        return cancelled;
    }
    private static Set<String> fields(MethodNode method) {
        var result = new java.util.HashSet<String>();
        for (var insn : method.instructions) if (insn instanceof FieldInsnNode field) result.add(field.name);
        return result;
    }
    private static AnnotationNode annotation(List<AnnotationNode> list, String suffix) {
        if (list != null) for (var a : list) if (a.desc.endsWith(suffix)) return a;
        return null;
    }
    private static Object value(AnnotationNode a, String key) {
        if (a.values != null) for (int i=0; i<a.values.size(); i+=2) if (a.values.get(i).equals(key)) return a.values.get(i+1);
        return null;
    }
    private static void hash(Path file, String expected) throws Exception {
        check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))).equals(expected),
            "exact PRE SHA256 " + file.getFileName());
    }
    public static void main(String[] args) throws Exception {
        Path classes = Path.of(args[0]), deps = Path.of(args[1]);
        Path mekJar = deps.resolve("Mekanism-1.21.1-10.7.19.85.jar");
        Path yigdJar = deps.resolve("youre-in-grave-danger-neoforge-2.0.13.jar");
        hash(mekJar, "004dbc9f3106f4d192aeaa1ee1190dd16ec9ca8059ed3d093b80034f4c574f43");
        hash(yigdJar, "dd2142a3c6a9d5b990ab36220be482f7aa9f528755f93b8fef8996f509ddcda2");
        try (var mek = new ZipFile(mekJar.toFile()); var yigd = new ZipFile(yigdJar.toFile())) {
            var robit = method(read(mek,"mekanism/common/entity/EntityRobit"),"goHome");
            int robitCancel = cancelled(robit, true);
            for (String mutation : List.of("setFollowing","setDeltaMovement","teleportTo","changeDimension"))
                check(one(robit,mutation) > robitCancel, "Robit cancellation precedes " + mutation);
            check(calls(robit,"discard").isEmpty(), "Robit denied path has no explicit deletion");
            var target = read(mek,"mekanism/api/event/MekanismTeleportEvent$GlobalTeleport");
            check(target.fields.stream().anyMatch(f -> f.name.equals("targetDimension") && (f.access & Opcodes.ACC_FINAL) != 0), "native target dimension immutable during event dispatch");
            var robitEvent = read(mek,"mekanism/api/event/MekanismTeleportEvent$Robit");
            check(robitEvent.methods.stream().anyMatch(m -> !calls(m,"getHome").isEmpty()), "event resolves actual home, not client destination hint");
            var portable = method(read(mek,"mekanism/common/network/to_server/PacketPortableTeleporterTeleport"),"handle");
            int portableCancel = cancelled(portable,true);
            for (String mutation : List.of("run","closeContainer","stopRiding","teleportEntityTo"))
                check(one(portable,mutation) > portableCancel, "portable cancellation precedes " + mutation);
            check(calls(portable,"extract").size() == 1 && fields(portable).contains("SIMULATE") && !fields(portable).contains("EXECUTE"),
                "only simulation occurs before portable event; real debit is deferred");
            var portableClass = read(mek,"mekanism/common/network/to_server/PacketPortableTeleporterTeleport");
            check(portableClass.methods.stream().anyMatch(m -> m.name.startsWith("lambda$") && fields(m).contains("EXECUTE")
                && calls(m,"extract").size() == 1), "portable Runnable contains real debit");
            var block = method(read(mek,"mekanism/common/tile/TileEntityTeleporter"),"teleport");
            int blockCancel = cancelled(block,false);
            check(one(block,"teleportEntityTo") > blockCancel && one(block,"extract") > blockCancel, "block teleport/debit after cancellable event");
            check(one(block,"markTeleported") < blockCancel, "known native bookkeeping occurs before cancellation; do not claim total state immutability");

            var scroll = read(yigd,"com/b1n_ry/yigd/item/DeathScrollItem");
            var action = method(scroll,"useAction");
            int shrink = one(action,"shrink");
            for (String name : List.of("viewContent","restoreContent","teleport")) check(one(action,name) < shrink, "scroll action result precedes wrapper cost");
            check(fields(action).contains("PASS") && fields(action).contains("CONSUME") && !fields(action).contains("FAIL"),
                "native shrink skips PASS/CONSUME but NOT FAIL: low-level FAIL is insufficient");
            check(one(action,"addCooldown") > shrink, "outer HEAD refusal also bypasses cooldown");
            for (var m : scroll.methods) if (!m.name.equals("useAction")) check(calls(m,"shrink").isEmpty(), "no outer scroll consumption in " + m.name);
            var restore = method(scroll,"restoreContent");
            check(calls(restore,"claim").size() == 1 && calls(restore,"teleportTo").isEmpty(), "restore is inventory claim, NOT teleport");
            check(calls(method(scroll,"teleport"),"getWorld").size() == 2, "teleport uses actual authoritative grave world");
            var claim = method(read(yigd,"com/b1n_ry/yigd/components/GraveComponent"),"claim");
            int allow = one(claim,"allowClaim");
            check(one(claim,"post") < allow && calls(claim,"isCanceled").isEmpty(), "GraveComponent reads allowClaim, not cancellation bit");
            for (String mutation : List.of("handleRandomSpawn","applyToPlayer","dropAllGraveItems","setStatus","removeBlock"))
                check(one(claim,mutation) > allow, "claim veto before " + mutation);
            var nativeListener = method(read(yigd,"com/b1n_ry/yigd/events/YigdServerEventHandler"),"graveClaimEvent");
            var sub = annotation(nativeListener.visibleAnnotations,"/SubscribeEvent;");
            check(sub != null && value(sub,"priority") == null && value(sub,"receiveCanceled") == null,
                "pinned YIGD mutation listener is default NORMAL, not receiveCanceled");
            check(!calls(nativeListener,"shrink").isEmpty(), "native claim listener has consumable mutation: must be suppressed at HIGHEST");
            var map = method(read(yigd,"com/b1n_ry/yigd/data/DeathInfoManager"),"getGrave");
            check(calls(map,"get").size() == 1 && calls(map,"ofNullable").size() == 1, "grave lookup is a bounded in-memory map read");

            var mixin = read(Files.readAllBytes(classes.resolve("fr/ascendant/lunar/recall/mixin/DeathScrollMixin.class")));
            var mixinAnnotation = annotation(mixin.invisibleAnnotations,"/Mixin;");
            check(mixinAnnotation != null && Boolean.FALSE.equals(value(mixinAnnotation,"remap"))
                && List.of(Type.getObjectType(scroll.name)).equals(value(mixinAnnotation,"value")), "narrow exact YIGD target");
            var hook = method(mixin,"lunarRecall$beforeAction");
            var inject = annotation(hook.visibleAnnotations,"/Inject;");
            if (inject == null) inject = annotation(hook.invisibleAnnotations,"/Inject;");
            check(inject != null && List.of(action.name + action.desc).equals(value(inject,"method")), "exact useAction descriptor");
            check(Boolean.TRUE.equals(value(inject,"cancellable")) && Integer.valueOf(1).equals(value(inject,"require"))
                && Integer.valueOf(1).equals(value(inject,"allow")), "required single cancellable hook");
            var at = (AnnotationNode) ((List<?>) value(inject,"at")).getFirst();
            check("HEAD".equals(value(at,"value")), "veto before action dispatch and all wrapper costs");
            check(one(hook,"deniesScroll") < one(hook,"setReturnValue") && calls(hook,"fail").size() == 1, "explicit failed original-stack result");
        }
        var forbidden = Set.of("shrink","grow","setItem","removeItem","removeBlock","dropAllGraveItems","claim",
            "applyToPlayer","teleportTo","changeDimension","discard","setFollowing","extract","addCooldown","setStatus");
        try (var files = Files.walk(classes)) {
            for (var file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                var cls = read(Files.readAllBytes(file));
                check(cls.name.startsWith("fr/ascendant/lunar/recall/"), "only this candidate's classes packaged");
                for (var m : cls.methods) for (var insn : m.instructions) if (insn instanceof MethodInsnNode call)
                    check(!forbidden.contains(call.name), "guard never performs inventory/entity/grave/cost mutation: " + call.name);
            }
        }
        var gate = read(Files.readAllBytes(classes.resolve("fr/ascendant/lunar/recall/YigdGate.class")));
        var graveGuard = method(gate,"deniesGrave");
        check(!calls(graveGuard,"getWorldRegistryKey").isEmpty() && !calls(graveGuard,"getWorld").isEmpty(), "authoritative saved + live world provenance");
        var metadata = Files.readString(classes.resolve("META-INF/neoforge.mods.toml"));
        check(metadata.contains("modId=\"yigd\"") && metadata.contains("versionRange=\"[2.0.13]\""), "required YIGD metadata pin");
        System.out.println("NATIVE/ADAPTER STATIC PASS " + checks + " contracts; exact PRE ordering checked; mixin NOT applied and no game started");
    }
}
