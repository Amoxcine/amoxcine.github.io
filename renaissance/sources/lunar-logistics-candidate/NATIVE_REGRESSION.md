# Fresh Native Regression Entrypoint

Prepared for the parent; NOT executed by this agent. No live process was contacted.
Use only a disposable lunar candidate after the parent has enabled the config and
restarted with the final JAR. The current native baseline boot without this JAR
cannot validate its mixins. OP4/server console required; commands unavailable when
the config is off. No old `renaissance_*_lab` command or journal is used.

## New Moon / Overworld Fixture

Choose fresh absolute coordinates in already loaded, empty space in each world:

```text
/lunarlogistics prepare <owX> <owY> <owZ> <moonX> <moonY> <moonZ>
```

Each center must have a fully loaded, air-only 16x5x5 volume: offsets
(-3,-2,-2) through (12,2,2), inclusive. Build height and world border are checked.
Both volumes are preflighted before placing anything; no chunk is force-loaded.
The command then creates 26 blocks in total: one paired AE2 quantum ring and creative
power source, one QE, one QIO importer and one starter Ender cell in each world.
QE gets a fresh private test frequency with a random UUID. No stock is seeded and
no grant is written. AE2 singularities are fresh, paired test items.

Allow native ticks for formation and power, then:

```text
/lunarlogistics probe
```

The probe re-reads the current loaded fixture on EVERY invocation. It checks:

- New per-block fixture UUIDs; no old completion flags or saved reports are trusted.
- AE2 chambers formed/powered, actionable nodes present, matching singularities,
  both actual partners registered, both connection wrappers cleared, and the live
  filter denying the pair in both directions. An unready/unpaired bridge cannot PASS.
- QE's underlying fresh native frequency is shared and valid, while the protected
  `getFreq()` returns null and Overworld's returns the native frequency. All six
  directional item/fluid/chemical/energy/heat container lists on the protected QE
  are empty. This exercises transformed getters; it does not transfer stock.
- QIO and Powah gate decisions on the two current endpoints. These are policy-gate
  observations only, not native QIO import/export or Powah transfer trials.
- Permanent shared/direct policy with both no grant and a synthetic granted UUID.
  The synthetic grant is NEVER persisted. Real old grant files remain untouched.

A successful command returns `BOUNDED DIAGNOSTIC PASS` with the fresh UUID and
explicit limitations. A failed precondition returns 0 and an explanatory refusal.
Repeated probe does not reuse a PASS; it reruns all checks.

## Moon Orbit / Overworld Fixture

After the first probe, forget ONLY its in-memory record, then choose NEW empty sites:

```text
/lunarlogistics forget
/lunarlogistics prepare_orbit <newOwX> <newOwY> <newOwZ> <orbitX> <orbitY> <orbitZ>
/lunarlogistics probe
```

`prepare_orbit` targets `ad_astra:moon_orbit`, not Earth orbit. Formation still needs
native ticks before probing. Moon <-> Moon-orbit and isolated local link behavior
are covered by offline policy tests, NOT by these two native crossdim fixtures.
Additional fresh native fixtures are needed for those actual transfer cases.

## Ownership And Limits

`forget` and server stop clear only this JVM's record. They never delete blocks,
frequencies or stock. No attach/resume operation exists; after restart, select NEW
empty locations. A partial preparation remains visible and is not silently retried,
repaired or rolled back. Parent owns later cleanup of these disposable test worlds.

This entrypoint does not claim actual stock conservation, zero transfer rates,
cached external handler safety, QIO drive publication, GUI/portable sessions,
Powah transmitter credit/debit, reconnect or restart proof. Those require separately
instrumented fresh-fixture trials. Do not convert its PASS into a whole-pack RC PASS.
