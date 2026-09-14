# Frozen LAB Candidate 0.0.3

Frozen artifact, never overwritten:
`build/20260913223940194/ascendant-lunar-travel-0.0.3-candidate.jar`

SHA-256: `47807836CD893B89B3A150B96F0FC4D2AC6D43C401D9B0986AB2770B575CEAE2`

DO NOT use build `20260913223042879`, hash `09661488...`: its Farmer's Delight
dependency constraint was wrong. Frozen metadata matches actual Apotheosis 8.7.0
and Farmer's Delight 1.3.3, as well as the existing pinned native transport mods.
No install/boot performed by this worker. Parent owns every LAB action below.

## Configuration

Dedicated LAB, `config/ascendant-lunar-travel.toml` before startup:

```toml
enabledSolo = false
enabledDedicated = true
```

For a solo integrated test, reverse these two values. Shipped defaults are both
false. The selected setting is latched at server startup. Do not stack another
lunar-travel version or the incompatible old `ascendant_travel_lab` runtime.
Other workers' restrictions have their own config; this opt-in does not enable them.

## Native Preflight

Run from server console (omit slash there). Replace PLAYER with the test account.
`execute as PLAYER` retains the operator console's command permission.

1. `lunar_cargo fixture`
   Expect `CARGO NATIVE FIXTURE PASS 27 cases`. Detached registered stacks only:
   original kit+4suit, split prepared-food cap, Farmer's Delight soup, Apotheosis
   socket schema and payload refusal. The fixture grants nothing and modifies no
   player/world inventories. STOP on any missing item, component or adapter error.
2. `execute as PLAYER run lunar_cargo gear_report`
   Records held/worn actual item IDs, Java classes and component IDs, without
   stripping acquired upgrades. Keep the report for any targeted gear refusal.
3. Mount a fueled tier-1 rocket with the actual starter kit, four basic suit pieces,
   oxygen tanks and return reserve. Run `execute as PLAYER run lunar_cargo check`.
   Expect ALLOW; this also reads actual Curios/Accessories/cursors/rocket inventory.
4. Put an industrial block in an actual backpack, in player or rocket inventory.
   Try the NATIVE launch control. Expect refusal with unchanged 3000 mB fuel,
   unchanged items/counts/components, no sequence/sound/history change. Remove the
   bag manually for the positive test; the candidate must never confiscate it.
5. Launch normally. At the native planet screen, run
   `execute as PLAYER run lunar_travel veto_once`, then select Moon normally within
   60 seconds. This arms DENIAL only, at LOWEST event priority for this exact rider's
   authenticated transfer. It is not a permit or fabricated successful launch.
6. Expect `NATIVE REFUSAL WITNESS PASS`: same player/rocket/mount, source position,
   velocity, rotation, all scanned stacks, rocket fuel and native history. No lander
   or successful arrival record. Pending reservation is released only after this
   witness passes. Already-paid launch fuel is NOT refunded, nor charged again.
7. Disconnect/reconnect, then use `execute as PLAYER run lunar_travel resume` if
   needed. Also repeat across a normal parent-controlled save/stop/restart.
   The command resumes the SAME paid rocket using its server receipt, opens the
   native planet menu and grants no item, fuel, teleport or replacement vehicle.
   If the rider is dismounted, only that receipt's live source rocket within 8
   blocks can be mounted; an unattended paid rocket at altitude is held instead
   of natively exploding on disconnect. A replacement rocket must be refused.
8. Select Moon without rearming the veto. Verify one native lander, original rocket
   removed, exactly one returned rocket item, every original cargo stack accounted
   for once, and the correct source coordinates in native launch history. Expect
   `.arrived` in world/data/ascendant_lunar_arrivals and COMPLETE flight receipt.
9. Build/refuel the recovered rocket by the survival protocol and return natively.
   On a later outbound flight, one raw kit item must refuse BEFORE launch fuel;
   personal oxygen, supported prepared food and validated personal gear still pass.
   Death/reconnect/team/quest changes must not reset `.arrived`.

Capture the exact frozen hash, logs, source/destination/vehicle UUIDs and observed
item/fuel totals. The fixture's own pass line is not a whole roundtrip pass.

## Pending Recovery Semantics

READY is a durable server receipt written only after successful native fuel debit.
TRANSFERRING is persisted after the final dimension-event decision and BEFORE
dismount/movement. COMPLETE follows confirmed native landing. The original source
rider and original live source rocket may rebind a READY/interrupted TRANSFERRING
receipt after reload; no completed flight can be replayed. Any extant first-arrival
pending reservation is usable only by this native receipt, not by a replacement
rocket or a reset command. A clean witnessed veto releases pending and retains the
first-kit right. Successful arrival consumes the allowance, not merely reservation.

`lunar_travel resume` can reconcile a missing arrival marker when the receipt is
already COMPLETE and its player is on Moon in a native lander. It cannot invent a
successful landing, recover deleted entities, copy items or reset arrived records.
Corrupt receipts/storage errors refuse. The 0.0.3 command does not deliberately
crash the game or expose a synthetic pending grant. Pending-file/reload/identity
transitions have offline tests; mid-method interruption native testing needs the
parent's fault harness. Independent world/player backup rollback is not promised.

## Explicit Scope Limits

The normal native route and intact dimension-event veto now have executable code
and a concrete witness protocol, not the previous permanent launch stub. Native
results above remain NOT RUN by this worker. Mixin coexistence, target entity-join
or mount veto after accepted dimension transfer, arbitrary callback exceptions and
interrupted lander cargo-copy stages are NOT certified by the clean-veto witness.
Do not call all failure modes or the complete RC passed from test 1 alone.

Apotheosis affixes and <=8 actual sockets containing only bounded native gem stacks
are read without removing upgrades. Exact vanilla implementation classes now work
regardless of registry namespace; arbitrary custom subclasses do not. Food has a
shared 32-item cap across an explicit prepared-food list, not beef-only.
Draconic modular gear specifically awaits a reader for MODULE_ENTITIES, live host
cache and TREE_MODULE_INVENTORY; remote-link guards alone cannot prove physical
cargo empty. Iron's custom gear/spell-container schemas also remain targeted
blockers. `gear_report` exposes exact installed items needing those adapters.
These are real remaining acquired-equipment gaps, not approval to nerf/delete gear.

Offline frozen result: 434 policy assertions, 19 arrival-ledger assertions,
14 paid-receipt recovery assertions, survival JSON contract, 37 hooks / 41 sites /
7 read-only accessors, native veto-before-mutation and history/ticket ordering,
scanner mutation-call audit. Package: 50 entries, no test or old runtime classes.
