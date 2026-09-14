# Lunar encounter 0.1.2

Implementation scope complete; native qualification remains with the parent.
No boot, installation, admin command or world modification was performed here.
All writes stay under `lunar-encounter-candidate/`.

## Artifact and Changes

Use `dist/ascendant-lunar-encounter-0.1.2-candidate.jar`. Exact SHA256 and build
evidence path are in `dist/artifact-0.1.2.json`. The 0.1.1 jar, manifest and handoff
are preserved separately, not overwritten. Never install both versions together.
Stage-Lab.ps1 remains additive, default dry-run and refuses another encounter jar;
the parent alone selects the version in the stopped lab, preserving prior evidence.

Current SHA256: `34B08C74099E6EA3EC4F9CA65BEFED0CBBB6C34DB8E76B61D9DEAD1BC814FD5E`.
Evidence: `build/20260913223700926/`; all twelve suites pass, including 15
success-only ledger checks and 8 no-custom-payout bytecode/negative-control checks.

Removed: `/lunar_encounter claim`, physical payout/receipt methods, custom item
insertion, inventory inspection, item receipt tags, playerdata/level.dat reads,
and all `saveEverything`/`saveAll` calls. No alternative Java/JS copper grant or
drop exists in the encounter. No encounter action forces a native server save.

Kept: durable victory, frozen FTB team/roster/leader, exact actor ownership,
repeatable combat and zero initial item escrow. Normal FTB rewards and Minecraft
inventory/save behavior are accepted. No global exactly-once crash guarantee is
claimed. Eight-copper reconciliation and physical REVIEW are NOT RC blockers.

## Native FTB First Reward

The separate quest task owns and stages this definition, not the encounter jar:

| Field | Exact value |
|---|---|
| Quest | `6C13020000000013` |
| Advancement task | `6C1303000000001A` |
| Advancement / criterion | `ascendant_lunar_encounter:relay_complete` / `durable_success` |
| Reward ID | `6C13040000000001` |
| Type / item / count | native `item` / `minecraft:copper_ingot` / `8` |
| Sharing / claiming | `team_reward: true` / `auto: disabled` |

First lot shared by the team: one member claims it through the quest book. No
repeat/reset/custom cooldown. Another victory or `observe` does not reset the
advancement or quest. Parent stages both candidates; do not synthesize success
with a checkbox or command reward. Native FTB parsing, sharing and inventory
delivery must be tested by the parent; they are not certified by this build.

The advancement still uses `minecraft:impossible` and is awarded only AFTER
durable settlement. Read-only `LunarEncounterApi.completion(ServerPlayer, UUID)`
authenticates the live player, roster, current FTB team and result. It returns
chapter `renaissance_lunar`, encounter `lunar_relay`, site, run/team UUIDs and
participants. Non-OP `observe <runUUID>` restores that authenticated presentation
after an offline participant/lost saved hint; it pays no item and resets no quest.

## Ledger Compatibility

The world-local `data/ascendant-lunar-encounter-v1/` journal/site seal and database
format stay compatible with 0.1.1 for current-lab tests. New runtime transitions
are RESERVED -> AVAILABLE (victory), or REFUNDABLE (failed/aborted, zero escrow).
AVAILABLE now represents successful completion, not an unpaid Java item lot.
Historical copper fields in the seal are compatibility fields, not reward policy.

Generic old withdrawal statuses/helpers remain serialized-format compatible,
but native handlers never call begin/confirm. New 0.1.2 attempts create no physical
CLAIMING/REVIEW. An old fixture REVIEW record does not block new combat or demand
a reconciliation feature. Do not reset/delete/import journals to change behavior.
The FINAL world is fresh, so old fixture payments do not define its economy.

## Parent Console Protocol: Current Lab

Use the CURRENT `aventure-20260905/lunar-native-lab` for tests. At read time its
level-name is `renaissance-lab`, simulation/view distance 4. This is QA state,
not the final shipping world. Parent owns all actions below; none were run here.

1. Stop the current native fixture normally and preserve its evidence. Select
   only the 0.1.2 encounter jar and matching FTB fragment. Java 21, Minecraft 1.21.1,
   NeoForge 21.1.248, Ad Astra 1.16.24, Teams 2101.1.10, Chunks 2101.1.21,
   Library 2101.1.35 and PlayerRevive 2.1.2 remain required. SQLite is embedded.
2. Read the current world's config/site identity. If already registered, keep
   those coordinates. Boot the lab and visit/load the Moon chunks normally.
   Console commands (without slash; replace X Y Z with real coordinates):

   ```text
   lunar_encounter status
   lunar_encounter preflight X Y Z
   ```

   Preflight works while encounter config is missing/disabled. It writes nothing,
   checks at most 25 loaded/unclaimed chunks and 65x65x5 cells, and refuses any
   claim, obstruction, block entity, unsafe floor or invalid border/build bounds.
   It never generates/force-loads chunks. Choose a site reachable from normal
   rocket landing, not an inaccessible platform. Select coordinates BEFORE sealing.
3. For a new registration, after PASS stop the lab, copy the properties example
   into the actual world's `serverconfig/lunar-encounter.properties`, set those
   exact coordinates/site, `enabled=true`, `allowSiteBootstrap=true`, then restart.
   With chunks loaded and zero historical runs, parent console:

   ```text
   lunar_encounter preflight X Y Z
   lunar_encounter build_site confirm
   lunar_encounter validate
   ```

   Build repeats full preflight before edits, preserves existing floor and places
   only in air: floor gaps and three copper receivers, maximum 4228 additions.
   No clearing/replacement, inventories, actor spawning, oxygen or teleport.
   Each placement checks loaded chunk/air/no block entity. Partial failure retains
   additions; inspect/re-preflight rather than deleting terrain. Do not run the
   old reference mcfunction's clearing commands. Disable bootstrap at next restart.
4. If historical runs exist, builder refuses by design. If `validate` passes,
   test the already prepared lab site. Otherwise use a separate disposable test
   save under this same lab on the next parent boot, not deletion of history or
   changing the sealed identity. No per-fight OP command is needed.
5. From NON-OP players: `site`, travel normally, `ready` then `start`. Groups
   each run `ready <leaderName>`, including leader. Test solo and small teams,
   correct/wrong A/B/C routing, failure/retry, death/reconnect and durable victory.
   Verify real advancement/task completion, one shared manual FTB first reward,
   then a repeat victory/observe without a second first-lot reward. Test full
   inventory through native FTB and record its actual behavior. No custom receipts.

## Final Fresh RC World

After current-lab testing, parent creates a genuinely NEW save, e.g.
`lunar-rc-clean-20260914`. Repeat preflight/bootstrap/validate once there. Carry
only reviewed mods/configs/quest artifacts, NOT renaissance-lab or another QA save,
player inventories/advancements, teams, graves, databases/WAL/fence files or fixture
state. No world is shipped by this task. Player path: normal rocket travel,
quest plus `/lunar_encounter site` coordinates, non-OP ready/start and native FTB
reward claiming. No old controller, campaign runtime, MMR or custom item is needed.

## Tests and Remaining Native Gates

Model/store/ownership/config/blueprint and packaged command/damage-boundary suites
are retained. New tests check repeatable success-only records with zero escrow,
and inspect the delivered native bytecode for absence of claim/item/inventory/
save/withdrawal paths. Preserved 0.1.1 must fail the latter as a negative control.
Legacy store fault/withdrawal tests are compatibility coverage, not current payout
requirements. Build logs and hashes accompany the artifact.

No custom physical-payout or reconciliation gate remains. Parent must still test
native bootstrap, AI/combat, oxygen, FTB protections and reward/inventory behavior,
PlayerRevive/graves, client handshake and multiplayer performance. JVM tests are
not native gameplay proof. Ledger writes remain local to run/actor/result changes;
there is no forced full Minecraft save and no promise of global crash exactly-once.
