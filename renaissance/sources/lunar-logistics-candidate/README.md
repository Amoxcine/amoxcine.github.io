# Lunar Logistics Candidate

2026-09-13. Bounded implementation, not total-pack RC qualification.
All work and build output are confined to this directory. No installation, server
launch, process control, old-world journal access or migration was performed.
Parent owns distribution packaging and any later native run.

## Artifact

Use **build/20260913213522673/lunar-logistics-0.1.0-candidate.jar**.
SHA-256: `EFE98C6D60C38D6ACE59DC8482FBE43116677A8DBAC3AB0F0DB8F1AF44FAE5E5`.
Earlier build directories are superseded, including Moon-only intermediate builds.
This JAR contains the Moon AND Moon-orbit implementation and fresh-fixture commands.

## Activation

The external config is `config/ascendant-lunar-logistics.properties`, relative to
the game's directory (Prism instance `minecraft` in solo; server root in dedicated).
The provided sample under this directory is OFF:

```properties
enabled=false
```

For the parent's disposable candidate environment only, the explicit opt-in is
`enabled=true` in that file BEFORE starting the game/server process. This document
does not install the JAR or config. There are no JVM activation flags and no old
world-name, IP-address or dedicated-only guards. Integrated solo is supported by
the same selection logic. Actual native loading in either mode remains to be tested.

Missing file: no mixins selected, no transport restrictions applied. Existing empty,
malformed, unreadable, oversized, duplicate-key or unknown-key config: loading fails.
Only `enabled=true` or `enabled=false`, optional whitespace and `#` comments are
accepted. Selection is immutable for the entire JVM; stop/restart the process to
enable OR disable. Disabling restores native transport after restart, without
deleting inventory, grant data or other journals. No hot reload or mid-session toggle.

Enabled startup requires BOTH `ad_astra:moon` and `ad_astra:moon_orbit` to be loaded.
Missing either aborts startup before normal ticks. Identifiers are also present in
the local Ad Astra 1.16.24 archive. No fallback dimension, remapping or generic
position alias is added.

## Rules

| Mechanism | Moon / Moon orbit | Outside the protected pair |
|---|---|---|
| AE2 quantum bridge | Same actual dimension allowed; every different-dimension link touching either protected world denied | Native crossdim/local policy retained |
| Mekanism QE | Local endpoint denied: item/fluid/chemical/energy/heat global pool is not demonstrably local | Native endpoint access retained |
| QIO | Import/export/return and Applied Mekanistics adapter gated; dashboard/portable remote inventory gated; lunar drive registration/addition blocked | Native valid-session access retained |
| Powah Ender cell/gate | Global pool energy I/O, item charging and extender absorption guarded | Native current endpoint access retained |
| Powah Player Transmitter | Actual source and recipient dimensions checked before credit: same world allowed, crossing a protected boundary denied | Native charging retained |

Moon <-> Moon orbit is CROSSDIM and denied in BOTH directions. Moon orbit is not
an alias for Moon. Earth orbit (`ad_astra:earth_orbit`) and other offworld worlds
remain outside scope, including same-world local use and links wholly outside the
protected pair. QE/QIO/Ender pools remain denied even if every currently loaded
endpoint appears local: their membership/provenance cannot prove isolation.
Native local cables, machines, carried inventories/batteries are not globally banned.

Team membership, absent teams, current grants, restored grants and deletion of teams
cannot unlock these restrictions. No grant/revoke command or installable authority
exists. Policy tests explicitly exercise both empty and granted sets. No old NBT
grant journal is opened to establish that result; its persistence classes are not
bundled at all. No claims of actual restored-world execution are made.

## Dependencies And Rebuild

Minecraft 1.21.1; NeoForge 21.1.248; Java 21. Required exact mod versions in metadata:
AE2 19.2.17, Mekanism loader version 10.7.19 (file build 10.7.19.85), Powah 6.2.10,
Applied Mekanistics 1.6.3. Ad Astra supplies the two required worlds. No FTB dependency
or persistence integration is needed. `ascendant_renaissance` is explicitly
incompatible: this copied implementation retains some Java/mixin namespaces and
must NOT be co-installed with the old controller, even while disabled.

The four target JARs were hash-compared against CURRENT PRE-RC as authority, the
parent's lunar-native-lab, rc-lab and the historical build cache: all four match.
See `build/20260913213522673/dependency-comparison.csv` for exact SHA-256 values.
The parent's newer 147-JAR baseline and SchematicEnergistics addition do not change
those hashes; its successful baseline boot is NOT a runtime result for this JAR.
The complete old dependency cache is copied inside this candidate for compilation;
other mods in that cache are not declared newly qualified against current PRE-RC.

```powershell
./build.ps1
```

Run from this directory. Optional parameters: `-JavaHome` and `-PreRcMods`.
The script reads sibling sources/cache/native/rc-lab references only, checks pinned
target hashes, compiles, runs offline tests and produces a JAR inside a new build
directory. No downloads, installation or server launches. Windows sandbox Java
ZIP filesystem cleanup needed elevated filesystem access in this task; the same
build-only script succeeded with that permission. Compiler deprecation notes remain.
`reference/controller-build.ps1` is a read-only provenance copy, NOT an entrypoint.

## Verification

- 368 pure policy/config assertions PASS, including the exact protected pair,
  both crossdim directions, same-world controls, grants, invalid configuration,
  Earth-orbit controls and missing required dimensions.
- Four fresh-JVM activation cases PASS against the actual Mixin plugin: missing,
  false, true, invalid. Old/new JVM flags have no effect; changing the config after
  plugin loading does not hot-toggle selection. No Minecraft server is instantiated.
- Copied AE2 native bytecode contract PASS; 73 Mekanism bytecode contracts PASS.
- 1,715 candidate static assertions PASS: 19 mixins, 40 hook methods, 45 target
  selector checks, required hook cardinality settings and no journal/team/disk
  calls outside the config reader.
- Original controller sources, transport policy, adapters and original build script
  match their read-only snapshots after the build. JAR contains main classes and
  mixin metadata, not historical authority or persistence classes.

Logs, source hashes and dependency hashes are in the artifact's build directory.
Mixin application, gameplay, real stock transfers, saves/restarts/reconnections,
client GUI behavior and multiplayer load/performance have NOT been run by this agent.
See `NATIVE_REGRESSION.md` for the fresh-fixture OP entrypoint and `LIMITATIONS.md`
for exactly what remains uncovered.
