# Etrionic BLASTING Mitigation Candidate

Final status: parent run `223347-602` passed native BLASTING veto and fresh
ALLOYING/powered-habitat nonregression with the guard active. See
`../NATIVE_QUALIFICATION_FINAL.md`; default-OFF and isolated-RC scope unchanged.

**Recommendation: disable native Etrionic BLASTING execution, not backport it.**
Implemented here as an independent, default-OFF module. No Ad Astra upgrade,
recipe replacement, new dependency or edits to another candidate. Parent alone
integrates and boots. Static qualification is not native qualification.

## Decision

| Option | Scope and Verification Cost | Decision |
| --- | --- | --- |
| Backport upstream 1.16.25 blasting fix | Replace multiple branches in `recipeTick` plus input-dependent recipe-cache invalidation. Must requalify four input/output slots, stale/missing/replaced inputs, completion timing, FE shortage, output capacity, reload and mode changes. | Plausible but larger behavioral patch than the RC requires. Not implemented. |
| Veto only Etrionic BLASTING execution | Two cancellable HEAD injections into exact existing descriptors; no inventory/cache/energy rewriting. Native ALLOYING entry and completion unchanged. | Implemented, recommended for bounded RC. |

Source: upstream commit
[`1789d5577ddd28efcd85d23253c62163bd58fe3d`](https://github.com/terrarium-earth/Ad-Astra/commit/1789d5577ddd28efcd85d23253c62163bd58fe3d).
Its blasting loop/cache changes do not change alloying. The local pinned bytecode
contains the old nested blasting completion loop. This choice removes that
capability rather than claiming to repair it. Ordinary furnace, vanilla blast
furnace, Mekanism/Create machines, and global blasting recipes are untouched.

## Exact Behavior

- Target only `EtrionicBlastFurnaceBlockEntity` in Ad Astra 1.16.24.
- When enabled on the running server, `recipeTick(ValueStorage)` is cancelled
  before any native recipe effect unless native mode is ALLOYING.
- Its separate `craft(BlastingRecipe,int)` is also cancelled before effects.
  This protects the blasting completion entry itself even if a stale cache or
  another caller reaches it. That method is not the alloying completion method.
- `alloyingRecipeTick`, `craftAlloying`, recipe definitions, ingredients, outputs,
  FE costs, tags, native oxygen, travel and world generation are untouched.
- No tick scan, inventory/FE deletion, cache surgery, mode rewriting, refund,
  automatic transfer, migration or refeed. Veto counters are diagnostic only.
- Existing machines saved in BLASTING remain in that mode and cannot process.
  The native GUI may still offer/display BLASTING and a stale progress/lit state.
  This is an execution veto, not a cosmetic removal of the mode button.
- Players can select ALLOYING normally. The pinned native mode packet calls
  `setMode`, `clearAlloyingRecipe` (resets progress) and `update`. That path is not
  intercepted. Do not use raw NBT mode changes as evidence of a normal GUI reset.
- Applies in **all dimensions** to this one machine mode. Moon-only enforcement
  would leave the same economy defect available elsewhere in the pack.
- Exact descriptors, required mixin and `require=1/allow=1` fail startup if the
  targeted injection cannot apply. Enabled server startup also hashes the loaded
  Ad Astra JAR; a different binary fails explicitly, even with the same version.

## Configuration and Installation Boundary

Build with `build.ps1`; use the latest successful `build/*/result.txt`. This reads
existing local dependencies, compiles, performs pure/bytecode tests, and writes
only this folder. It does not download, install, launch or modify a world.

Parent installs only the built guard JAR and independently manages the common
config `config/ascendant-etrionic-guard.toml`. Shipped/default value:

```toml
disableEtrionicBlasting = false
```

Parent must explicitly set it to `true` and restart the server for mitigation.
The value is latched at `ServerAboutToStartEvent` for solo or dedicated servers;
hot config reload does not change protection mid-run. Stopping clears the latch.
There is no runtime enable/reset command. `/etrionic_guard status` is OP4,
read-only and reports configured vs active state and veto counts. Inspect:

```text
execute in ad_astra:moon run etrionic_guard status
execute in ad_astra:moon run etrionic_guard inspect <x> <y> <z>
```

Inspect requires an already loaded native machine and reads mode, inventory,
FE, cook progress and game time without calling craft or mutating a stack.
Counters are server-wide since startup, not proof for a specific fixture alone.

**Default false, removing this module, or restarting with false restores the
original defective BLASTING behavior. Do not mark the RC blocker mitigated in
those states.** The module is independently removable, but reversal is not safe
economy policy until a separately qualified upstream fix/backport is integrated.
No claim is made that other mods or machine modes have no duplication defects.

## Parent Native Gate

Use disposable lab saves only. The separate nonshipping helper under
`../qualification/` can run from server/RCON console with its exact lab marker.
Keep its old partial manifest unchanged. Do not install its QA JAR in the RC.

1. Startup true: no Mixin failure; `/etrionic_guard status` says active=true and
   configured=true. Preserve the exact guard/Ad Astra hashes and server log.
2. Fresh ALLOYING fixture: use the updated AlmostUnified-aware helper. Steel must
   match its loaded recipe's captured item/count/components with 2,000 FE debit;
   brass two ingots with 8,000 FE debit; powered oxygen on/off unchanged. The
   helper also reports loaded Ad Astra steel-to-plate ingredient compatibility.
3. Fresh bounded BLASTING test machine: record known placements and one-shot test
   input/FE seed separately. Select BLASTING through the native mode control;
   test one then four native-accepted blastable input slots (e.g. raw iron with
   loaded vanilla iron blasting recipe). Wait at least a full native recipe
   duration plus update interval. With no battery, neighbor transfer or external
   supply, all input/output counts and stored FE must remain unchanged, with
   tickVetoes increasing. No forced craft call constitutes native ticking proof.
4. Parent console alternative for BLASTING only: after a fresh disposable fixture
   ALLOYING test, a separately recorded one-shot vanilla command seed/Mode=1b
   may exercise the saved-state guard (native enum ALLOYING=0, BLASTING=1).
   This is **saved-state/command-input evidence**, not native menu-acceptance or
   GUI-switch evidence. Keep those distinctions in the qualification record.
5. Reload a disposable BLASTING machine with cached/in-progress work, missing or
   replaced inputs and available FE. Confirm no output growth, input debit or
   recipe FE use while active. Test occupied outputs and removal/reinsertion too.
6. Return to ALLOYING through the native menu and repeat one paid batch with exact
   expected debit. Requalify a vanilla blast furnace as unaffected control.
7. In a separate disposable restart with false, confirm status inactive and
   normal original behavior is delegated; never enable defective blasting in RC
   merely to run this negative control. Do not retain generated test outputs.

Console fixture command seeds are bounded explicit QA actions, not pack recipes
or cargo exceptions. Parent must not classify a manually filled slot as native
menu acceptance. The diagnostic does not itself provide a BLASTING spawner or
seed command. Full GUI/input/reload controls remain native qualification tasks.

**Release gate:** the implementation is ready for parent qualification; the
economic blocker is only mitigated after active=true and native BLASTING veto +
ALLOYING nonregression are recorded. No boot was performed by this worker.

## Isolated RC and Player Message

Parent activation is restricted to the **isolated RC instance** for now. This
deliverable does not silently enable a production-wide policy: default is false,
no external instance/config is edited, and parent explicitly installs/enables it.
Within that opted-in instance the execution veto covers all dimensions, because
leaving Overworld Etrionic blasting enabled would retain the same economy defect.

Player-facing wording for the RC notes/quest description:

> Four Etrionic : alliages uniquement. Pour la cuisson, utilisez un haut fourneau vanilla.

The module does not alter the native GUI or broadcast unsolicited messages.
Parent/quest worker publishes the wording in the isolated RC's existing guidance.

The nonshipping QA helper now provides a console-native `lunar_blasting_fixture`
prepare/wait/inspect sequence: a fresh finite real BLASTING machine, native input
slot acceptance, 100 ordinary ticks, unchanged stocks/FE and increased guard
veto count. See `../qualification/README.md`, Console BLASTING Guard Fixture.
Then use the original ALLOYING fixture in a separate fresh volume in the same
session. The production guard JAR does not contain these seed/placement commands.
