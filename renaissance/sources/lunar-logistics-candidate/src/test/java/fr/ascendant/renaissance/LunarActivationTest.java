package fr.ascendant.renaissance;

import java.nio.file.Files;
import net.neoforged.fml.loading.FMLPaths;

/** Fresh JVM per case; only loader path utilities and the plugin, no Minecraft server. */
public final class LunarActivationTest {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        var game = Files.createTempDirectory("lunar-activation-" + mode + "-");
        FMLPaths.loadAbsolutePaths(game);
        var file = FMLPaths.CONFIGDIR.get().resolve(LunarConfig.FILE);
        Files.createDirectories(file.getParent());
        if (!mode.equals("missing")) Files.writeString(file, "enabled=" + mode);
        System.setProperty("ascendant.renaissance.lab", "true");
        System.setProperty("ascendant.lunar.logistics.enabled", "true");
        if (mode.equals("invalid")) {
            try { new LunarMixinPlugin(); }
            catch (ExceptionInInitializerError expected) {
                if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected;
                System.out.println("ACTIVATION PASS invalid file fails plugin initialization; no server started");
                return;
            }
            throw new AssertionError("Invalid config must fail loading");
        }
        var plugin = new LunarMixinPlugin();
        boolean expected = mode.equals("true");
        if (plugin.shouldApplyMixin("native.Target", "candidate.Hook") != expected)
            throw new AssertionError("Wrong live plugin opt-in " + mode);
        Files.writeString(file, "enabled=" + !expected);
        if (plugin.shouldApplyMixin("native.Target", "candidate.Hook") != expected)
            throw new AssertionError("Changing config must not hot-toggle hooks");
        System.out.println("ACTIVATION PASS " + mode + ": actual plugin selection=" + expected
            + "; JVM flags ignored; immutable until process restart; no server started");
    }
}
