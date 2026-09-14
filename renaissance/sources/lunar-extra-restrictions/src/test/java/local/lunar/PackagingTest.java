package local.lunar;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

public final class PackagingTest {
    public static void main(String[] args) throws Exception {
        Path classes = Path.of(args[1]);
        int count = 0;
        try (var jar = new JarFile(args[0])) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var e = entries.nextElement();
                if (e.isDirectory() || e.getName().equals("META-INF/MANIFEST.MF")) continue;
                String name = e.getName();
                if (!(name.startsWith("local/lunar/") && name.endsWith(".class"))
                        && !name.equals("lunar-extra.mixins.json") && !name.equals("lunar-portals.mixins.json") && !name.equals("META-INF/neoforge.mods.toml"))
                    throw new AssertionError("Unexpected packaged resource: " + name);
                try (var in = jar.getInputStream(e)) {
                    if (!Arrays.equals(in.readAllBytes(), Files.readAllBytes(classes.resolve(name))))
                        throw new AssertionError("Untested packaged bytes: " + name);
                }
                count++;
            }
            try (var in = jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml"))) {
                var config = new TomlParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                List<Config> mods = config.get("mods");
                if (mods.size() != 1 || !"lunar_extra_restrictions".equals(mods.getFirst().get("modId")))
                    throw new AssertionError("Mod identity");
                List<Config> deps = config.get("dependencies.lunar_extra_restrictions");
                if (deps.size() != 10) throw new AssertionError("Pinned required dependencies");
                if (!"0.1.1-candidate".equals(mods.getFirst().get("version"))) throw new AssertionError("New version required");
                for (var dep : deps) {
                    if (!"required".equals(dep.get("type")) || !"BOTH".equals(dep.get("side")))
                        throw new AssertionError("Native dependency contract");
                }
            }
        }
        System.out.println("PASS " + count + " packaged entries identical to tested classes/resources; no bundled libraries, items, data, assets or test code");
        System.out.println("Server-only join is a native qualification test, not proven by packaging.");
    }
}
