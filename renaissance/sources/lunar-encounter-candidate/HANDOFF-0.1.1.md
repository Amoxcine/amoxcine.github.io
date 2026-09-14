# Lunar encounter candidate 0.1.1

Status: implemented and built candidate, NOT native-qualified RC. No Minecraft
boot, mod installation, world modification or administrative command was run by
this task. All writes were under `lunar-encounter-candidate/`.

## Deliverable

- `dist/ascendant-lunar-encounter-0.1.1-candidate.jar`
- SHA256 and authoritative build evidence: `dist/artifact.json`.
- Current SHA256: `D5114D500B623FE92A8BB59DDD14990EB0828579D32823B87F7CDDF4507825A0`.
- Current evidence directory: `build/20260913222733450/`.
- Version 0.1.1 adds native non-destructive site bootstrap and player site lookup.
  Version 0.1.0 in an earlier build is obsolete and must not be installed.
- `Build.ps1` compiles against cached NeoForge 21.1.248 / Minecraft 1.21.1
  libraries and exact PRE-RC FTB Teams 2101.1.10, Chunks 2101.1.21, Library
  2101.1.35, PlayerRevive 2.1.2, CreativeCore 2.13.44 jars. Inputs are hashed.
- Ad Astra 1.16.24 is a declared required runtime dependency. No Ad Astra API is
  changed or overridden. KubeJS 377 need not load an encounter script.
- SQLite JDBC 3.53.1.0 is embedded by JarJar, with its existing notice and pinned
  SHA256. No standalone JDBC mod installation is required.

The scoped copy reuses v005 RaidMachine, journal/fence/ownership code and its
tests. Its native adapter is lunar-specific. Package
`fr.ascendant.lunar.encounter`, mod ID `ascendant_lunar_encounter`, actor markers,
database application ID and data directory are independent. It does not call
the old controller, encounter/campaign runtime, MMR or custom campaign items.
Old StoreGate is absent and the legacy migration helper is test-only, not shipped.

## Integration, Parent Only

1. Stop the isolated `aventure-20260905/lunar-native-lab` server. Keep a backup.
2. Review `Stage-Lab.ps1`, then run `-Apply -ServerStopped` as the parent. Without
   `-Apply` it is read-only. It adds only this jar to that exact lab, refuses
   replacement and does not create/edit configs or worlds. Do not install any
   earlier jars in `build/`.
3. Use a NEW world, e.g. `level-name=lunar-rc-clean-20260914`, not a copy of any
   previous `renaissance-lab`, old encounter world, campaign state or QA fixture.
   No world is shipped in this candidate. The parent alone creates/boots that
   new world and arranges the ordinary rocket/landing route.
4. With encounter config absent/disabled, load candidate Moon chunks normally.
   OP/console **`/lunar_encounter preflight <x> <y> <z>`** reads only that 65x65x5
   volume and at most 25 chunk claims. It works while disabled, makes zero writes,
   refuses missing chunks, claims, obstructed clearance, unsafe floor or block
   entities, and reports the number of air-only additions (maximum 4228).
   Try coordinates BEFORE the first activation seals the immutable site ID.
5. After a successful preflight, stop this new world. Copy
   `integration/lunar-encounter.properties.example` to its own
   `serverconfig/lunar-encounter.properties`; set the preflighted center,
   explicit site ID, `enabled=true`, and **`allowSiteBootstrap=true`**.
   No JVM lab/loopback flag is required. Unknown/duplicate keys or invalid values
   refuse activation. Parent restarts this new world, with those chunks loaded.
6. Parent OP/console runs **`/lunar_encounter build_site confirm`** ONCE. This
   redoes the complete native preflight before any edit, preserves every existing
   floor block, adds native floor/letter blocks only in air gaps, and adds only
   the three missing copper receivers. It never clears, destroys or replaces a
   non-air block, loads chunks, imports a world, touches inventory or spawns mobs.
   Every placement rechecks air/no block entity/chunk loaded. Maximum 4228 writes
   in one bounded bootstrap command. If a placement fails, retain partial additions
   for inspection; rerun preflight, do not delete/rollback terrain blindly.
7. OP `/lunar_encounter validate` verifies the completed site. Set
   `allowSiteBootstrap=false` on the next stop/restart. Even if left true,
   bootstrap refuses once ANY run has been recorded. There is no force-win,
   reward-reset, air-grant or teleport command. Ordinary fights require no OP.

The unregistered `integration/RC_BLUEPRINT.mcfunction` is now only a visual
layout reference. Do NOT run its terrain-clearing fill commands; use the native
preflight/build_site helper instead. It is not part of the shipped jar.

The player path is concrete: the quest can name **`/lunar_encounter site`**, which
shows the actual configured site ID, Moon dimension and X/Y/Z to any real non-OP
player. The parent chooses a physically reachable route from rocket landing to
the preflighted site. Players travel normally, then ready/start/claim themselves.
The candidate does not discover terrain globally or teleport players to the site.

Use Java 21 / NeoForge 21.1.248 with the pinned versions above. Candidate is
server-side and registers no custom items, entities or packets; integrated solo
loads it in the test client. Dedicated/client handshake and HailWall policy still
require parent verification. Do not change the live PRE-RC instance for these tests.

## Site Contract

- Exactly one registered site and one active run per world. The config example
  uses `lunar_relay_01`, center `(1024,100,1024)` in **`ad_astra:moon`**.
- Center Y is player floor level. A solid full-block floor at Y-1 covers all
  integer X/Z offsets `[-32,32]`. No floor block entities.
- Air at Y..Y+3 throughout, except three copper blocks at center offsets
  A `(-12,0,0)`, B `(12,0,0)`, C `(0,0,12)`. Optional floor letters in blueprint.
- Runtime participants and actors stay at X/Z within 32 of center and
  Y in `[centerY,centerY+8]`. Leaving does not teleport or confiscate anything.
- All intersecting chunks (at most 25) must already be loaded and unclaimed,
  including same-team claims. The world border must contain the site. No chunk
  generation, forced loading or habitat registration is performed. The explicit
  parent-only bootstrap above is the sole block-placement path.
- Claims checked on start, every active second, circuit choice, incoming actor
  damage, final damage, settlement and withdrawal. Full local terrain validation
  is on start/completion/withdrawal, not every tick.
- The immutable site identity is sealed before creating the ledger. Changing
  coordinates/site ID after first enable is refused, not a silent migration.
  Timing values may change only by restart; no hot reload is advertised.

## Operational First Loop

Find the prebuilt site with non-OP `/lunar_encounter site`, travel there normally,
and bring native oxygen supplies:

1. Solo: `/lunar_encounter ready`, then `/lunar_encounter start`.
   Group: every participant, including leader, runs `ready <leaderName>`.
   Consent expires after 60 seconds. Same native FTB team, 1..8 actual players.
2. Read the displayed task/action and A/B/C receiver. Native iron-golem defenders
   spawn only after voluntary activation. Solo has 1 task, 2..4 players have 2,
   and 5..8 have 3. Health is not multiplied by group size.
3. Neutralize each defender, walk to the requested copper receiver, right-click
   it and explicitly choose the displayed action in chat. Range <=3 blocks and
   line of sight are checked again by the submitted command. Wrong route/action
   loses one of three stability margins; stale buttons lose none.
4. Active defenders warn of overload after 10 seconds and cost stability at 15
   seconds if still active. Correct routing exposes a 60-health module; hitting
   it cannot skip the final 5-second warning. The final 100-health core then
   becomes vulnerable. Gear power remains useful but cannot skip routing.
5. Durable success creates one group lot of **8 vanilla copper ingots**.
   The consented leader uses `/lunar_encounter claim` or `claim <runUUID>` at
   the site with one empty inventory slot. No input/escrow item is taken.
6. `last` reports your last run; leader `abort` abandons it. On failure, absence
   timeout, restart or manual abort, repeat ready/start for a fresh run. No OP
   reset is needed. Default active timeout 10 minutes, all-absent timeout 30s.

Actors cannot intentionally target or damage bystanders, changed-team players
or targets outside bounds. Native entity travel to another dimension is canceled
for owned actors; leaving bounds aborts and removes exact actors. No natural
zombie/creeper spawning rules are changed. Iron golems were chosen because the
actual Ad Astra jar tags `minecraft:iron_golem` as space-safe. That tag inspection
is NOT proof of modded AI/combat balance in the real pack.

Native Ad Astra oxygen/temperature applies unchanged to players. No air area,
effect, suit, shield, refill, healing, teleport or free infinite oxygen is supplied.
Death and graves remain native pack behavior; the encounter never clears items.

## Durable Result and Quest Observation

- Per-world storage: `data/ascendant-lunar-encounter-v1/site.identity` and
  `data/ascendant-lunar-encounter-v1/ledger/{ledger.sqlite,ack.fence,...}`.
- Native team UUID, consenting roster and leader are frozen server-side. No
  command accepts a beneficiary/team/owner UUID to assign rights.
- States: RESERVED -> AVAILABLE on durable success; withdrawal is
  CLAIMING -> CLAIMED. Interrupted runs become REFUNDABLE (zero escrow here),
  and ambiguous physical withdrawals become REVIEW, never automatically reissued.
- Advancement **`ascendant_lunar_encounter:relay_complete`**, criterion
  **`durable_success`**, uses `minecraft:impossible` and is awarded by the mod only
  after durable success. FTB may observe it; do not synthesize or grant it from a
  manual checkbox. It is a presentation mirror, not payment authority.
- Read-only server-thread API:
  `LunarEncounterApi.completion(ServerPlayer, UUID)` -> `Optional<Completion>`.
  Fields: chapter `renaissance_lunar`, encounter `lunar_relay`, configured site ID,
  run UUID, FTB team UUID, immutable participants. Current native player, roster,
  team and journal success are authenticated. No arbitrary-player/team query.
- Online eligible participants receive the advancement at settlement. A saved
  successful-run hint retries presentation on login; hints are never trusted
  without the journal. An offline/downed participant or a crash before saving
  that hint may need **`/lunar_encounter observe <runUUID>`**. This validates the
  durable result and only restores its presentation, never grants an item.
- `last` is a convenience pointer, not an authoritative complete history. Retain
  the run UUID announced in chat for recovery or OP read-only inspection.

## Reward Safety and Known Limit

The candidate requires an empty slot before durable withdrawal intent, then
inserts a receipted vanilla stack. It requests **one synchronous native full
server save per payout**, verifies/forces the saved player receipt and, for the
integrated host, its `level.dat` Data.Player receipt before confirming the ledger.
This is normal native save work, not a custom world/entity scan, but its latency
is NOT bounded by the encounter and must be measured. No player's existing stack
is overwritten, removed, confiscated or dropped by the reward code.

There is deliberately no claim of automatic exactly-once physical delivery
across arbitrary process/storage failure. The durable ledger prevents automatic
duplicates; an interrupted intent may leave the earned lot in REVIEW without
delivery. There is currently **no reconciliation/reissue tool**. Preserve world,
player data, database, WAL and fence together for parent investigation. This is
an explicit blocker to unconditional crash-safe reward-delivery RC signoff.

Disable by setting enabled=false and restarting. Graceful stop aborts active
runs and removes known actors. On a disabled restart, marked actor admission is
suspended, new encounters and payouts are off, and the ledger is not erased.
Re-enable the same identity to recover/claim remaining records. Removing the mod
while an active run exists is unsupported; stop/abort first.

## Evidence and Remaining Gates

Passed in the delivered build:

| Suite | Assertions / scope |
|---|---|
| RaidMachineTest | 588408, copied engine identities and group sizes |
| DurableRaidJournalTest | 202, durable append/fault/recovery |
| JournalSemanticTest | 44, impossible-history rejection |
| EncounterSafetyTest | 1008, ownership, revocation, containment/budgets |
| JoinAdmissionTest | 56, lookup faults and forged/unknown markers |
| CircuitChoicesTest | 124, explicit A/B/C/action vocabulary and replay |
| SqliteJournalTest | 152, subprocess crashes, corruption, capacity, 100-run volume |
| LunarCandidateTest | 144, lunar config/seal/bounds, solo..8, reward replay/review, air-only blueprint planning and obstruction rejection |
| Packaged circuit methods | 44, actual choose/menu methods with world doubles |
| Packaged damage/target methods | 24, bounds/team/claims/bystanders/fail-closed |

These are JVM/model/store and extracted-bytecode tests, NOT native server/gameplay
proof. Native qualification is exclusively the parent's next step. In particular:

- No full native victory, actual AI pathing, Better Combat/spell damage, oxygen
  autonomy, PlayerRevive/YIGD lifecycle, FTB protection, client handshake, FTB
  advancement observation or native reward save/reload has been exercised here.
- The native bootstrap has compiled, and its pure planner is tested; actual
  preflight refusals, partial bootstrap/retry and final native validation remain
  parent test gates. Shipping only a jar is not a claim that a world was prepared.
- Native solo and dedicated 2/4/8-player loops, no-OP retries, actor cleanup after
  chunk unload/restart, claims changing mid-fight and concurrent teams remain gates.
- Kill the native test process around payout/save/confirmation and inspect actual
  player data and ledger together. Do not infer those outcomes from SQLite tests.
- Test command spam/capacity, real 4/8-player timings, save latency and chunk
  interaction with parent habitat protections. No TPS guarantee is asserted.
- The reward is a useful native copper lot, NOT the old membrane/multiblock unlock.
  Broader chapter progression, logistics, habitat and rocket return remain owned
  by the parent. This candidate implements the first encounter, not a whole campaign.
