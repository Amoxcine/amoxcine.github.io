# Qualification Boundary

Status: compiled bounded candidate, offline tests PASS, native qualification NOT RUN.
Artifact: `20260913215352635`, SHA256
`BE0BFD9D6BE1092BFFA38FFA5EE06C27BBF52205A797FB4F19214AE6189249B9`.

## Evidence Actually Obtained

- 94 policy/config assertions: full dimension matrix including null provenance,
  Moon versus Moon orbit, same-world restore versus teleport, independent mode
  switches, absent/invalid config, startup snapshot, inactive guard behavior.
- 12 native event-bus assertions: actual Mekanism/YIGD event objects dispatched
  through NeoForge's bus; early veto suppresses native-style NORMAL listeners;
  both YIGD flags are set; late reassertion; existing cancellation preserved.
  Subjects/predicates and debit counters are test harnesses, not gameplay entities.
- 262 static contracts: exact PRE hashes, cancellation branches and native call
  ordering, real versus simulated energy debit, actual grave lookup, YIGD listener
  priority, wrapper shrink behavior, exact required HEAD mixin descriptor, and
  absence of candidate inventory/entity/grave mutation calls.
- Main and test compilation, JAR packaging, full source/compiler hash manifests.

No live Mixin application, world startup, native item transfer, energy delta,
player teleport, Robit automatic return, grave restore, or restart persistence was
executed. The PRE YIGD loader-range blocker is recorded in `DEPENDENCIES.md`.

## Exact Limitations

1. Mekanism block teleport calls `markTeleported` and updates destination delay
   before posting its cancellable event. Portable teleport similarly updates the
   destination's `didTeleport`/`teleDelay` bookkeeping first. Refusal prevents the
   audited entity mutation and real energy debit, NOT every native tile write or
   transient delay. No broad method overwrite was introduced to undo bookkeeping.
2. Robit automatic-return cancellation does not freeze subsequent ordinary tick
   behavior such as energy-slot charging or recipe processing. Those are normal
   independent operations, not a refusal-induced deletion or inventory transfer.
3. YIGD claim safety relies on the exact pinned native NORMAL listener ignoring
   canceled events. An addon mutating before this HIGHEST listener, explicitly
   mutating canceled events, or overriding the final result after this LOWEST
   listener is not qualified. The native bus tests do not prove arbitrary addon
   coexistence or full-pack ordering.
4. Only DeathScroll's ordinary `useAction` path gets the outer non-consuming veto.
   `GraveComponent.claim` is also guarded, but an arbitrary third-party wrapper
   can have its own pre-event or post-FAIL costs. Commands, GUI/network restore
   handlers, direct `applyToPlayer`/inventory operations, and private-method
   reflective calls bypassing these entry points are not claimed closed.
5. `VIEW_CONTENTS` remains native. Subsequent GUI actions are not implicitly
   qualified by permitting a read-only view. Unknown grave provenance on a
   transfer action fails closed and can reduce functionality for malformed or
   unavailable grave records. It does not repair or migrate those records.
6. Meka-Tool teleport, other mods' recall/teleport routes, arbitrary entity moves,
   cargo transport, rockets, equipment/storage channels, and global pools are
   outside this candidate. T10/T11 closure is bounded to the listed entry points,
   not a claim of total lunar RC restrictions.
7. Travel's additional nonlunar restrictions can reject a final move after a
   caller left untouched by recall has charged its cost. Recall's cost boundary
   is the lunar pair only. Narrative/native-flight exceptions do not authorize
   these recall routes and no old grant interface is consulted.
8. Both modes default OFF. Mixins still resolve at load time. An incompatible
   target can fail loading even while inactive; default OFF is not a compatibility
   promise for other versions. Runtime metadata checks versions, not content hashes.

## Parent Native Acceptance Checklist

Use a disposable, fresh fixture only after resolving the documented dependency
metadata mismatch. This document is a checklist, not a request to start a server.
Keep old worlds, journals, completed logistics fixtures, and rc-lab untouched.

1. Confirm artifact and dependency hashes and required mixin application. Exercise
   missing/default-off config, each opt-in separately on integrated and dedicated
   servers, invalid existing config, restart behavior, and missing protected worlds.
2. Record entity UUID, dimension, coordinates, follow/motion state, inventory,
   real device/Robit energy, and destination tile bookkeeping before each trial.
   Separate ordinary tick changes from the attempted action.
3. For Robit manual/automatic return, block teleporter, and portable teleporter,
   exercise OW-to-Moon, Moon-to-OW, OW-to-Moon-orbit, reverse, Moon-to-Moon-orbit,
   reverse, and same-world Moon/Moon-orbit. All teleport actions must be denied
   without entity removal, recall-induced state mutation, or real energy debit.
   Confirm that the documented native tile bookkeeping may still change.
4. Positive controls: offworld local and offworld-to-offworld routes supported by
   the native mod; normal local Robit walking/following/processing on Moon; no
   newly forced permission where another mod/native security already refuses.
5. With both config-selected and item-overridden DeathScroll actions, test the
   same teleport matrix. Snapshot scroll count, inventory, cooldown, player
   position, grave UUID/status/items/XP/block, and any native consumable key/compass.
   A denied scroll must not claim/delete a grave, debit stock, or add its cooldown.
6. For RESTORE_CONTENTS and native grave claims, test all cross-lunar directions
   including Moon versus Moon orbit. Confirm inventory/grave/cost preservation.
   Same-world Moon and same-world Moon-orbit recovery must remain native, including
   ownership/security refusals. Test unavailable/mismatched provenance safely.
7. Qualify coexistence with lunar travel enabled: no late-only cancellation hiding
   earlier recall cost, no local teleport leak, and local grave recovery unaffected.
   Run addon event-order and GUI/command route checks separately before broadening
   any claim. Do not report these pending checks as passed.
