# Lunar Recall Candidate

Bounded T10/T11 implementation from `../lunar-qualification/TRANSPORT_COVERAGE.md`.
Only this directory was written for this task. No install, server boot, world access,
old-controller change, logistics change, journal migration, or inventory migration.
This is functional candidate code with offline tests, NOT native RC qualification.

## Artifact

- Build: `build/20260913215352635/ascendant-lunar-recall-0.1.0-candidate.jar`
- SHA256: `BE0BFD9D6BE1092BFFA38FFA5EE06C27BBF52205A797FB4F19214AE6189249B9`
- Mod ID: `ascendant_lunar_recall`; version: `0.1.0-candidate`.
- Packaged: candidate classes and resources only; no bundled dependency mods.
- Parent owns packaging and any subsequent installation or native qualification.

## Independent Activation

The runtime file is the instance/server's `config/ascendant-lunar-recall.properties`.
The template in this candidate's `config/` is default OFF:

```properties
enabledSolo=false
enabledDedicated=false
```

For an explicitly selected solo candidate, set `enabledSolo=true`. For an explicitly
selected dedicated candidate, set `enabledDedicated=true`. These switches are
independent. This is a server-side decision, including the integrated solo server;
no client-side permission, JVM flag, old-world name, grant, or travel-mod setting
activates it. There is no runtime dependency on another candidate.

The file is read once at server start. Restart a dedicated server, or leave and
reopen a solo world, for changes to take effect. Missing file means both OFF and
does not create a file. An existing file must contain exactly both boolean keys;
unknown/duplicate/malformed/unreadable/oversized configuration aborts startup,
rather than silently disabling enforcement. `#` comment lines are accepted.
Enabled startup requires both `ad_astra:moon` and `ad_astra:moon_orbit` to exist.
The mixin is registered even while OFF, but its runtime guard is inactive.

## Policy and Entry Points

| Route | When enabled | Mechanism |
| --- | --- | --- |
| Robit `goHome`, including automatic return | Deny teleport if either endpoint is Moon or Moon orbit | Native cancellable `MekanismTeleportEvent.Robit` |
| Block teleporter | Same | Native cancellable `MekanismTeleportEvent.Teleporter` |
| Portable teleporter | Same | Native cancellable `MekanismTeleportEvent.PortableTeleporter` |
| DeathScroll `TELEPORT_TO_LOCATION` | Same, including a same-world lunar teleport | Required, exact HEAD injection on outer `DeathScrollItem.useAction` |
| DeathScroll `RESTORE_CONTENTS` | Deny different-world transfer if recipient or actual grave is lunar | Same outer injection; restore is NOT treated as teleport |
| Other paths reaching `GraveComponent.claim` | Same cross-lunar restore rule | Native `GraveClaimEvent` veto before native claim listener |

Moon and Moon orbit are separate protected worlds: transfer between them is denied.
Same-world grave restoration remains native, subject to YIGD's ownership and
security rules. Offworld local and offworld-to-offworld actions remain native.
Ordinary Robit walking/following/processing is not disabled; lunar `goHome` is a
teleport, so even a local lunar return is denied to match travel policy.
`VIEW_CONTENTS` remains native; this is not a qualification of GUI restore buttons.
Unresolvable transfer action/provenance is refused while enabled. Authoritative
grave saved dimension and live world must agree and belong to the current server.
No universal position alias, old grant API, or bypass ticket is introduced.

## Before-Mutation Ordering

Mekanism's native cancellation branches precede Robit follow/motion/position
changes and teleporter entity movement and real energy extraction. Portable
energy checking before the event uses SIMULATE, with real debit deferred until
after cancellation. Block/portable native tile bookkeeping does occur before
the event; see `QUALIFICATION.md` for this important boundary.

YIGD's ordinary claim listener can consume a key/compass. HIGHEST cancellation
suppresses that NORMAL, non-receiveCanceled listener before it runs. The guard
sets BOTH event cancellation and `setCanClaim(false)`: pinned `GraveComponent.claim`
checks only `allowClaim()`. LOWEST reasserts a denied result after ordinary listeners.
The guard never un-cancels or forces permission for an allowed/local operation.

Cancelling only YIGD's inner teleport or claim is insufficient: the outer scroll
wrapper consumes even a FAIL result and adds a cooldown. Its narrow HEAD guard
returns FAIL with the original held stack before dispatch, cost, or cooldown.
No candidate code deletes a grave/entity or changes inventory on refusal.

## Complementary Travel Checks

Read-only review: `lunar-travel-candidate`'s `TravelPolicy`, `LunarTravel`,
`PlayerTravelMixin`, and `EntityTravelMixin`.

- Travel's coordinate/dimension teleport hooks and `EntityTravelToDimensionEvent`
  are final movement gates. Recall cancels the audited callers earlier, before
  Robit follow changes, energy debit, or scroll wrapper costs.
- A YIGD contents restore does not travel. Recall additionally gates grave claim
  and the scroll's inventory-transfer action using the actual grave dimension.
- Same-world lunar teleport denial is intentionally consistent with travel.
- Recall does not implement travel's reserved nonlunar destination rules, native
  rocket tickets, or narrative links. Travel can separately refuse an offworld
  route that recall leaves alone; early cost protection for that nonlunar case
  is outside this candidate's claim.
- Neither candidate's opt-in activates the other. No hook or source in travel
  or frozen logistics was edited.

## Build and Evidence

From this directory, `./build.ps1` compiles with the local Java 21 runtime and
read-only cached dependencies, checks exact current PRE target hashes, runs all
three offline suites, and creates a NEW timestamped build. No downloads or boot.
`./inspect.ps1` regenerates read-only native bytecode evidence in this directory.
Target dependencies and the known YIGD loader-range problem are in `DEPENDENCIES.md`.

Completed build results:

- Policy/config: 94 assertions PASS.
- Actual NeoForge event bus with native event objects: 12 assertions PASS.
- Pinned native bytecode and compiled adapter: 262 contracts PASS.

Logs, source/config hashes, and every compiler dependency hash are in the build
directory. The bus tests use injected predicates and counter harnesses, not real
players or stock. The exact mixin target/descriptor is checked statically; mixin
application, integrated/dedicated startup, gameplay, and live stock preservation
remain untested. See `QUALIFICATION.md` before any RC claim.
