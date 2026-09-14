# Minimal Qualified Scope And Blockers

Date: 2026-09-14, frozen version 0.0.5. **Offline tests and native cargo fixture PASS; accepted for RC packaging freeze, actual flight NOT qualified.**

Parent native 0.0.3 load/activation succeeded; its fixture FAILED the fuel bucket
class check. 0.0.4 fixed that check but FAILED the socketed default GemItem case.
0.0.5 admits only native empty enchantments/attributes and zero repair cost on
gems. Ten new bytecode contracts pass; parent native fixture PASS 33 is recorded
in smoke-20260913-233045-754.log:1170. Detached scanner inputs are not live inventory
enumeration or first-arrival/flight/recovery evidence. Manual flight remains open.
Current artifact and rerun instructions: FREEZE-0.0.5.md.
No installation, server/client startup, world editing or recipe/config deployment.
No source changes outside `lunar-travel-candidate`. Old travel/controller runtime
and all synced `sources/` are untouched. Parent owns integration and installation.

Full native recovery protocol: [FREEZE-0.0.3.md](FREEZE-0.0.3.md), with the new JAR.
Clean native dimension-event veto no longer mutates mount/position/history or burns
the first-kit right. Same paid source rocket can resume after reconnect/restart;
19 arrival +14 receipt tests and native ordering checks pass. This supersedes the
0.0.2 permanent-pending/volatile-ticket recovery restriction, not all callback gaps.

## Evidence Basis

- Read old `travel-prototype` sources and its native bytecode evidence, then
  `lunar-integration-20260913/TRANSPORT_AUDIT.md`.
- Independently disassembled actual `rc-lab/mods` copies of Ad Astra 1.16.24,
  Waystones 21.1.41, Draconic Evolution 3.1.4.632 and Brandon's Core 3.2.1.309.
  Evidence is in this candidate's `evidence/` directory. These four SHA-256 values
  also match the new `lunar-qualification/TRANSPORT_INVENTORY.json` Prism audit.
- Compile uses pinned native jars first on the classpath and the local NeoForge
  21.1.248 dependency cache. No old Ascendant runtime is a compile dependency.
- Policy tests exercise route matrix, exact identity/thread/direction binding,
  token replay, launch ticket expiry, passenger/rocket/server replacement, nested
  scopes, exception/Error cleanup and finite kit cap constants. Real registered
  stacks are tested by the compiled parent fixture, not these pure tests.
- Actual Curios, Accessories and Common Storage cargo classes were disassembled
  locally; seven read-only accessor fields/types are bytecode checked. The real
  scanner has no fake verified-facts snapshot or constant launch-denial stub.
- Filesystem tests cover first-arrival reservation/commit separation, restart,
  replay, corrupt records, write failure and concurrent exclusive reservation.
  A read-only JSON contract test matches the concrete survival manifest caps.
- ASM tests read native bytecode without initializing Minecraft: guard target
  signatures/staticness and exact call-site cardinalities; both patched Entity
  and ServerPlayer must contain NeoForge's dimension-travel hook.
- These tests do NOT run the Mixin transformer or validate client synchronization,
  real fuel, landing, item conservation in game, or all mod callers. No passing
  unit test is substituted for the C03/C10-C14 runtime tests below.

## Implemented Narrow Surface

| Surface | Candidate behavior | Evidence ceiling |
|---|---|---|
| Activation | Independent COMMON config opt-ins for solo/dedicated, both false; snapshot at server startup; no LAB/IP/save-name restriction; required mods/mixins and incompatible old travel LAB | Compilation/static inspection only; config load/correction/lifecycle still needs runtime tests |
| Protected dimensions | Moon surface AND Moon orbit prohibit covered teleports in/out and locally; no surface/orbit transit exception | Pure route matrix + hook shapes, not a full logistics boundary |
| Other planets/orbits | Other native planets and their orbits remain entry-reserved. Earth orbit also remains entry-reserved; station landing/construction packets all vetoed | No proof of relay-safe existing Earth stations, so this optional opening is deferred |
| Native rocket | Only Overworld <-> Moon tier-1 route, one real server-player passenger, correct live rocket/launch/menu/altitude/tier, launch-history destination recheck | Real finite scanner can approve; no real roundtrip proof |
| Native scope | Ticket issued after successful native fuel call; tied to server/player/rocket/source with 12000-tick lifetime; one-use landing; only the packet's land call opens scope; only land's transfer call authorizes one exact dimension event; finally cleanup | No broad player permit, TTL teleport window, OP bypass, grant, or portal exception. Unit tests for primitives, not native transaction atomicity |
| Generic movement | Entity and ServerPlayer dimension event; ServerLevel teleport overloads; both coordinate-only/relative teleport methods | Movement veto only. Not a transaction veto for every caller, not entity-recreation prevention |
| Waystones | Sync, async, force entry checks; resolved/batch/individual checks and cost recheck; uses native failure objects | Native pre-side-effect entry guards statically present; late callback changes, batch/leash and consumption semantics not runtime-proven |
| Dislocators | Basic/bound inherited use/dislocate/hit; Advanced teleport/blink/hit before fuel; pedestal before notification; receptacle admission and pending-queue recheck before traversal side effects | No global Brandon's Core utility hook. Bound-target resolution, delayed callbacks and all return semantics still need preservation tests |
| Narrative portals | No registration, construction or link-admission implementation; no approval token or string can open lunar movement | Zero approved links. This is not a portal formation/rebinding or arbitrary mod-portal transaction adapter |
| Cargo A | Actual main/armor/offhand/menu, rocket/fuel, Curios/Accessories; finite kit; explicit Apotheosis affixes/gem slots and shared prepared-food cap | Parent native detached-stack fixture PASS 33; real inventory enumeration/flight unqualified; Draconic/Iron custom gear remain targeted schema gaps |
| First arrival/recovery | Server UUID arrival record plus paid native player/rocket/source receipt; witnessed intact veto releases pending only; original source rocket may resume; completed flight never replays | 19 arrival +14 receipt assertions; not a world-backup transaction; native witness still needs parent run |

## New Audit C10-C14: Still Open

The independent `lunar-qualification/TRANSPORT_COVERAGE.md` is authoritative as
an inventory of gaps, not evidence that this candidate has implemented those gaps.

- **C10 / T06-T07: PARTIAL CODE, UNQUALIFIED.** Added explicit Waystones and
  dislocator native guards, including local Moon/orbit use and asynchronous
  rechecks. Test every variant, already-bound target, force path, entity/leash,
  pedestal, queued/rebound receptacle, and off-world regression. Assert no charge,
  sound/arrival mutation, health/item damage, success message or entity loss on
  denial. A final generic movement veto is not enough. Brandon's shared TargetPos
  and TeleportUtils implementations remain unchanged for unrelated callers.
- **C11 / T08-T09: NOT IMPLEMENTED.** Ars portal creation/rebinding, RitualWarp,
  Iron's portals and pocket-frame callers need scoped native adapters before
  costs/source mutation. Coordinate/dimension hooks may veto their final move;
  that does not certify spell costs, scroll consumption, portal construction or
  fixed-link integrity. No narrative link is approved in this candidate.
- **C12 / T10-T13: NOT IMPLEMENTED.** Mekanism block/portable teleporter and Robit,
  YIGD teleport AND restore, colony scroll and entity recall need actual native
  pre-operation guards and correct failure results. Grave restore is not a
  teleport and is wholly outside these movement hooks. No recreation/deletion,
  fee, stock-transfer or cargo-preservation claim is made for these mechanisms.
- **C13 / T14: NOT IMPLEMENTED.** Native portal availability/formation and Create
  track providers/carriage reconstruction/cargo require transaction-level checks.
  A recreated entity can bypass changeDimension. Keep freight-train and spatial
  transport qualification blocked; generic teleport cancellation is insufficient.
- **C14 / PERMISSIONS: NOT AUDITED.** No special operator bypass is implemented,
  but actual PartyHUD/FTB/Xaero/colony/grave/quest commands and permissions are
  parent qualification work. Command delegation to a covered teleport method
  does not prove safe upstream side effects or absence of alternate methods.

## Other Release Blockers

1. **Cargo/remote storage.** Finite real scanner and one-time authority are now
   implemented, but only for the surfaces/gear listed in the cargo contract.
   Actual capability/accessor initialization and full inventory conservation need
   parent tests; unknown/missing surfaces fail closed. AE2 spatial I/O, QIO, QE,
   Powah, Draconic automatic modules, Ender Chest and remote armor paths are not
   protected by this module. Moon orbit must be included in their policy too.
   Closing station/player travel alone does not isolate stations or remote pools.
2. **Real native roundtrip.** Test fueled rocket, actual passenger, normal/history
   destinations, malformed/stale/replayed packets, cargo change after launch,
   and another mod cancelling the dimension event. In 0.0.3, source move/dismount
   is deferred until the final event decision, and history commits after successful
   landing. The native refusal witness checks source/item conservation before
   releasing pending. Parent must run it; arbitrary target entity-join/mount veto
   after accepted transfer and interrupted lander cargo-copy are separate unproven
   failure cases, not covered by that clean dimension-event witness.
3. **Lifecycle/recovery.** Arrived UUID records never reissue raw cargo. An intact
   prelanding veto releases pending, and server fuel receipts rebind the original
   physical source rocket/player UUID after reload without replacing cargo or fuel.
   An unattended paid airborne source rocket is held for reconnect. Corrupt records,
   missing vehicles, arbitrary partial landing exceptions and previous-visitor
   migration still need explicit handling/testing. No guarantee for independently
   restored world/player backups is claimed or required for this normal RC path.
   Decide safe recovery for
   already-airborne players and previously occupied Moon orbit. No generic admin
   teleport exception, respawn override, automatic evacuation or save migration.
   Missing Moon causes startup failure when enabled; missing/invalid config and
   mixed client/dedicated configurations still need installation tests.
4. **Hook application.** Required targets fail on changed shapes, but actual Mixin
   transformation/coexistence with other mods is not tested. Two optional
   JetBrains annotation warnings occur at compile time; no compilation errors.
5. **Direct APIs.** Raw setPos/moveTo/network-position operations, respawn placement,
   custom entity recreation and uninspected mod APIs are not universally caught.
   Blocking moveTo globally would also break native movement/landing. Do not
   claim that all 174 audited jars, all local teleports, or all cargo are covered.

Allowed summary: "isolated candidate builds; real bounded inventory scans and
one-time server arrival records implemented; offline rules/ledger/native shapes
pass; native detached kit fixture PASS 33; in-game transport closure and flight preservation unqualified."

Forbidden summary: "rockets work", "all cargo inspected", "all lunar transports
blocked", "Moon orbit isolated across all mods", "RC passed" or "ready to install".
