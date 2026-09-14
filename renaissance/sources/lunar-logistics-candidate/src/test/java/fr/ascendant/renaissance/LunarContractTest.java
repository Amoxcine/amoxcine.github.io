package fr.ascendant.renaissance;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Static contracts only: exact target selectors/cardinality, NOT Mixin application. */
public final class LunarContractTest {
    private static int checks, hooks, targetChecks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static ClassNode node(byte[] bytes) {
        ClassNode result = new ClassNode();
        new ClassReader(bytes).accept(result, 0);
        return result;
    }
    private static ClassNode compiled(Path classes, String name) throws Exception {
        return node(Files.readAllBytes(classes.resolve(name + ".class")));
    }
    private static ClassNode nativeClass(List<ZipFile> jars, String name) throws Exception {
        for (ZipFile jar : jars) {
            var entry = jar.getEntry(name + ".class");
            if (entry != null) try (var in = jar.getInputStream(entry)) { return node(in.readAllBytes()); }
        }
        throw new AssertionError("Missing native target " + name);
    }
    private static AnnotationNode annotation(List<AnnotationNode> values, String descriptor) {
        if (values != null) for (var value : values) if (value.desc.equals(descriptor)) return value;
        return null;
    }
    private static AnnotationNode hook(MethodNode method, String name) {
        String desc = "Lorg/spongepowered/asm/mixin/injection/" + name + ";";
        var found = annotation(method.visibleAnnotations, desc);
        return found != null ? found : annotation(method.invisibleAnnotations, desc);
    }
    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static List<MethodNode> methods(ClassNode node, String selector) {
        return node.methods.stream().filter(m -> (m.name + m.desc).equals(selector)).toList();
    }
    private static void inspectHook(List<ZipFile> jars, ClassNode target, MethodNode handler, AnnotationNode hook) {
        check(Integer.valueOf(1).equals(value(hook, "require")) && Integer.valueOf(1).equals(value(hook, "allow")),
            "Required single injection: " + handler.name);
        var selectors = (List<?>) value(hook, "method");
        for (Object selector : selectors) {
            var matches = methods(target, (String) selector);
            check(matches.size() == 1, "Exact declared target " + target.name + "." + selector);
            targetChecks++;
            var method = matches.getFirst();
            check((handler.access & Opcodes.ACC_STATIC) == (method.access & Opcodes.ACC_STATIC), "Staticness " + selector);
            Object points = value(hook, "at");
            List<?> ats = points instanceof List<?> list ? list : List.of(points);
            for (Object obj : ats) {
                AnnotationNode at = (AnnotationNode) obj;
                String kind = (String) value(at, "value");
                if (kind.equals("INVOKE")) {
                    String invocation = (String) value(at, "target");
                    long count = 0;
                    for (var insn : method.instructions) if (insn instanceof MethodInsnNode call
                        && invocation.equals("L" + call.owner + ";" + call.name + call.desc)) count++;
                    check(count == 1, "Unique invocation " + selector + " -> " + invocation);
                } else check(kind.equals("HEAD") || kind.equals("RETURN"), "Known injection kind " + kind);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Path classes = Path.of(args[0]);
        Path deps = Path.of(args[1]);
        List<ZipFile> jars = new ArrayList<>();
        try {
            for (String name : List.of("0107-appliedenergistics2-19.2.17.jar", "0178-Mekanism-1.21.1-10.7.19.85.jar",
                "0194-Powah-6.2.10.jar", "0106-Applied-Mekanistics-1.6.3.jar")) jars.add(new ZipFile(deps.resolve(name).toFile()));
            int mixins = 0;
            for (String config : List.of("ascendant-renaissance.mixins.json", "ascendant-renaissance-mekanism.mixins.json",
                "ascendant-renaissance-qio.mixins.json", "ascendant-renaissance-powah.mixins.json")) {
                var json = JsonParser.parseString(Files.readString(classes.resolve(config))).getAsJsonObject();
                check(json.get("required").getAsBoolean(), "required config");
                check(json.get("plugin").getAsString().equals("fr.ascendant.renaissance.LunarMixinPlugin"), "one opt-in for ALL adapters");
                check(json.get("injectors").getAsJsonObject().get("defaultRequire").getAsInt() == 1, "required injector default");
                for (var entry : json.getAsJsonArray("mixins")) {
                    mixins++;
                    String name = (json.get("package").getAsString() + "." + entry.getAsString()).replace('.', '/');
                    ClassNode mixin = compiled(classes, name);
                    check(mixin.version == Opcodes.V21, "Java 21 " + name);
                    var declaration = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
                    check(declaration != null && Boolean.FALSE.equals(value(declaration, "remap")), "Exact unmapped targets " + name);
                    List<String> targets = new ArrayList<>();
                    if (value(declaration, "value") instanceof List<?> list)
                        for (Object item : list) targets.add(((Type) item).getInternalName());
                    if (value(declaration, "targets") instanceof List<?> list)
                        for (Object item : list) targets.add(((String) item).replace('.', '/'));
                    check(!targets.isEmpty(), "Mixin has targets " + name);
                    for (MethodNode handler : mixin.methods) {
                        var injection = hook(handler, "Inject");
                        if (injection == null) injection = hook(handler, "Redirect");
                        if (injection != null) {
                            hooks++;
                            for (String target : targets) inspectHook(jars, nativeClass(jars, target), handler, injection);
                        }
                    }
                }
            }
            check(mixins == 19, "Exact mixin set includes drive registration and transmitter recipient guards");
            var plugin = compiled(classes, "fr/ascendant/renaissance/LunarMixinPlugin");
            var apply = methods(plugin, "shouldApplyMixin(Ljava/lang/String;Ljava/lang/String;)Z").getFirst();
            check(java.util.Arrays.stream(apply.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f
                && f.name.equals("ENABLED") && i.getOpcode() == Opcodes.GETSTATIC), "Default-off controls actual transformation selection");
            var bootstrap = compiled(classes, "fr/ascendant/renaissance/LunarLogistics");
            check(bootstrap.methods.stream().anyMatch(m -> m.desc.contains("ServerStartingEvent")), "Dimension checked before first server ticks");
            try (var paths = Files.walk(classes)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    ClassNode c = node(Files.readAllBytes(file));
                    check(!c.name.matches(".*(LogisticsData|LogisticsState|RelayRegistry|RelayCommands|RenaissanceController)$"),
                        "No old persistence/commands bundled");
                    for (MethodNode m : c.methods) for (var insn : m.instructions) if (insn instanceof MethodInsnNode call) {
                        check(!call.owner.contains("LogisticsData") && !call.owner.startsWith("dev/ftb/"), "No journal/grant/team access " + c.name);
                        if (!c.name.endsWith("LunarConfig")) check(!call.owner.equals("java/nio/file/Files")
                            && !call.owner.startsWith("java/io/"), "No disk IO outside config reader " + c.name);
                    }
                }
            }
            String metadata = Files.readString(classes.resolve("META-INF/neoforge.mods.toml"));
            check(metadata.contains("modId=\"ascendant_lunar_logistics\"") && metadata.contains("type=\"incompatible\""), "Distinct mod; old controller cannot co-load");
            System.out.println("LUNAR STATIC PASS " + checks + " assertions; " + mixins + " mixins, " + hooks
                + " hooks, " + targetChecks + " native selector checks. Mixins NOT APPLIED; native fixture command NOT RUN.");
        } finally { for (ZipFile jar : jars) jar.close(); }
    }
}
