# Exact Local Dependencies

Authority: current PRE at
`C:/Users/avets/AppData/Roaming/PrismLauncher/instances/Ascendant-Renaissance-PRE-RC/minecraft/mods`.
No rc-lab or filename-intersection assumption is used for these target pins.

| Target JAR | Native mod ID/version | SHA256 |
| --- | --- | --- |
| `Mekanism-1.21.1-10.7.19.85.jar` | `mekanism` / `10.7.19` | `004DBC9F3106F4D192AEAA1EE1190DD16EC9CA8059ED3D093B80034F4C574F43` |
| `youre-in-grave-danger-neoforge-2.0.13.jar` | `yigd` / `2.0.13` | `DD2142A3C6A9D5B990AB36220BE482F7AA9F528755F93B8FEF8996F509DDCDA2` |

Both native mod IDs/versions were read directly from each JAR's
`META-INF/neoforge.mods.toml`. Build checks both current PRE originals and local
copies. Metadata pins the loader-visible versions exactly. Native code evidence
is qualified against the full hashes; the runtime does NOT implement JAR hash
attestation. A same-version replacement must not be treated as qualified.

Compiler target: Java 21, Minecraft `1.21.1`, NeoForge `21.1.248`.
Default JDK: `C:/Users/avets/AppData/Roaming/PrismLauncher/java/java-runtime-delta`.
Other compiler libraries are reused read-only from
`../renaissance-controller/build/dependencies`; exact inventory and SHA256 values
are recorded in `build/20260913215352635/dependencies-sha256.csv`.
The target Mekanism/YIGD JARs replace any cached copies on the compiler classpath.
No old controller/candidate JAR is packaged or used as an implementation dependency.
Native dependency mods still require their own ordinary pack dependencies.

## Known Native Loader Blocker

The exact PRE YIGD `2.0.13` JAR declares Minecraft **`[1.21,1.21.1)`**, excluding
the intended Minecraft `1.21.1`. Current PRE `config/fml.toml` has
`dependencyOverrides = {}`. Compilation and offline event-bus tests do not
exercise loader dependency resolution and cannot establish native bootability.

This mismatch must be resolved explicitly by the parent before native qualification.
No override, replacement, metadata edit, or install was made here. Do not silently
replace YIGD or relax its metadata and call it the same qualified PRE artifact.
Any changed dependency requires an explicit qualification decision and rerun.
The candidate itself pins MC `1.21.1`, NeoForge `21.1.248`, Mekanism `10.7.19`,
and YIGD `2.0.13`; no claim that these metadata constraints boot together is made.
