# 0.1.1 Native Portal And Track Candidate

**Ready for parent native qualification, not RC-qualified. Parent is sole booter.** No Minecraft installation, boot, world load/save or client join was performed here. Changes remain exclusively under `lunar-extra-restrictions/`; no parent KubeJS, travel, logistics or audit files were written.

## Exact Artifacts

- New jar: `dist/lunar-extra-restrictions-0.1.1-candidate.jar`.
- New SHA-256: **`387845AE8F70522C2954568C078B066BFEF42D732C2D50C3DCABC5FE58A033B9`**.
- New result: `dist/result-0.1.1.json`; successful build: `build/20260913220713438/`.
- Original `dist/lunar-extra-restrictions-0.1.0-candidate.jar` remains **`50FEC7DD7732CDABE5C9EA113B3F8D60E432A63CC36A2CB0F4FAC2D9F2A35B10`**. Original `dist/result.json`, original version's build outputs and `README.md` remain in place. Do not confuse the unversioned result with the new versioned result.
- The earlier, unreleased 0.1.1 attempt with hash `8777BFEA...` failed the subsequent preservation review and was removed from `dist` into its own build directory as `rejected-unreleased-0.1.1.jar`. **It is not the delivered candidate.**

0.1.1 **contains**, rather than supplements, 0.1.0's adapters. It has the same mod ID; parent must select one version, not load both. Native metadata adds exact Create **6.0.10** to the previous pinned dependencies. No vendor jars, items, blocks, packets, datapacks or new saved data are shipped. The existing server-only join expectation remains untested.

The same COMMON config `config/lunar_extra_restrictions-common.toml`, `enabled = false` by default, controls both old and new adapters. Enabling remains server-session/restart-latched, with dedicated and solo integrated servers supported by the same code. Graph/travelling-point callbacks without a Level argument additionally require the active native server thread; client graph rendering does not enforce the policy.

## Deliberate Policy Limits

Protected dimensions remain `ad_astra:moon` and `ad_astra:moon_orbit`.

**New native Ars/Iron portal and warp operations involving either protected world are closed, including local lunar operations.** Same-world coordinates or a copied scroll/frame are not treated as narrative authorization. No trusted fixed narrative endpoint list was supplied and this release does not invent one. Consequently an approved lunar narrative portal still needs an explicit, separately reviewed authorization integration before use. Merely labeling an item/frame cannot grant that exemption. Ordinary local rail movement remains allowed, including on the Moon; ordinary offworld magic and portal networks retain native behavior.

This is a conservative lunar-only candidate, not a global teleport ban. The parent's seven Iron teleport-spell, Ars Blink/EnderChest and NetherPortalSpawnEvent KubeJS preflights are untouched. No new native interception of those seven spell-cast methods, Blink, or NetherPortalSpawnEvent was added. The original 0.1.0 EnderChest adapters are retained byte-for-byte in their source files.

## T08: Ars

| Entry Point | Gate / Preservation |
|---|---|
| `WarpScroll.use` | HEAD rejects a protected source or protected bound dimension before packets, teleport, scroll shrink or recording changes. Returns FAIL with the original ItemStack. **Ordinary use has a coordinate-only path, not an asserted interdimensional exploit.** It is nevertheless closed on the Moon as an unapproved warp mechanism. |
| `WarpScroll.onEntityItemUpdate` | HEAD returns false before portal creation, source drain or stack shrink. StableWarpScroll's dropped-item override delegates to this exact method, verified in native code. ItemEntity remains available to ordinary item ticking; the hook does not discard it. |
| `StableWarpScroll.useOn` | HEAD fails before constructing/enqueuing `BuildPortalEvent`, building blocks or playing the creation sound. Its ordinary `use` only records a destination and is left native; a newly recorded lunar link still cannot be used to build/traverse a portal. |
| `RitualWarp.canStart` / `tick` | Protected-world start returns false; tick cancels before `incrementProgress`, coordinate teleport and `setFinished`. Native tick uses `Entity.teleportTo(DDD)` and does **not** change dimensions. Existing lunar rituals pause; consumed offerings/earlier ritual-system costs are not restored or claimed untouched. |
| `PortalBlock.trySpawnPortal` | HEAD returns false before the ordinary vertical/horizontal creation calls. This covers the inspected dropped-scroll creation caller, not an arbitrary external caller of every lower-level frame method. |
| `PortalTile.setFromScroll` | Rejects protected-world rebinding before this method's target-field writes when the tile has a server Level. Does not alter load/save serialization or retroactively sanitize unattached tiles/loaded NBT. Creation/use gates are still required. |
| `PortalTile.warp` / static `teleportEntityTo` | Direct warp rejected before destination resolution; the common static helper returns null before entity/passenger/position/dimension mutation. Existing tile tick calls the same helper directly, so its queued entities are covered too. Normal queue clearing remains intact, avoiding retained entity references. Tile tick can read the destination ServerLevel before the helper, but no teleport happens. |

**Residual:** No cancellation is inserted into already queued `BuildPortalEvent.tick` or its constructor. The inspected native queue is reached through guarded StableWarpScroll use; already queued/partially constructed events, external lower-level callers and custom narrative authorization are not qualified. Portal blocks/old bindings are not erased. A parent deployment with outstanding events needs its own preflight, not an assertion that saved artifacts were cleaned.

## T09: Iron

| Entry Point | Gate / Preservation |
|---|---|
| `PortalFrameBlock.entityInside` | Protected source rejected before native frame activation; otherwise read local frame UUID and manager metadata, reject protected destination before setting active state. PocketDimensionPortalFrameBlock inherits this method. No frame deletion, dye/item consumption or link rewriting. |
| `PortalFrameBlockEntity.teleport` | HEAD covers direct and ticking callers before `processDelayCooldown`, `addPortalCooldown`, sounds and final teleport. Resolves UUID through the native loaded-neighbor read and reads the manager's map only; does not call any cooldown method during rejection. |
| `PortalEntity.checkForEntitiesToTeleport` | HEAD before entity iteration and its per-entity cooldown, orientation, movement, loop tracking and dimension change. Covers players and eligible non-player entities according to this current native class. Portal lifetime/normal tick cleanup are not frozen. |

The native `PortalData.getConnectedPortalPos` uses `Optional.of` and assumes a complete pair. The guard deliberately does **not** call it early: it selects the other position using the public IDs/positions and a tested null-safe pure selector. Incomplete pairs therefore do not introduce an offworld exception before the native `canUsePortal` check. Frame UUID resolution and PortalManager lookup were inspected, rather than assumed mutation-free from their names.

**Residual:** This guards activation/traversal, not every frame placement, PortalData registration, spell cost, pocket-dimension generation or administrative rebind. Native pocket-frame `useItemOn` is only a PASS return in this version; its existence is not evidence that it creates a pocket world there. The parent owns spell preflight. Loaded portal/frame data is preserved; final usage is blocked when a protected endpoint is known. Unknown malformed/offworld metadata follows native failure behavior unless the protected source itself is sufficient to deny.

## T14: Create

| Stage | Gate / Preservation |
|---|---|
| Provider dispatch | `PortalTrackProvider.getOtherSide(ServerLevel, BlockFace)` returns null at HEAD for a protected source, before registry/provider calls. No attempt to invoke a provider to learn whether it is safe. |
| Known native dimension pair | `AllPortalTracks.fromPortal` checks source, worldA and worldB at HEAD. Protected involvement returns null before server destination lookup, temporary glue-entity construction or `Portal.getPortalDestination` (which may create a portal). |
| Entrance-track caller | `TrackBlock.connectToPortal` cancels at HEAD on protected source. A resolved Exit is checked before its first `level()` consumption and before block writes/binds. Twelve captured locals are pinned to this bytecode; exact argument/reference/array descriptors are verified offline and capture is FAILHARD. |
| Destructive null fallback | Native null-exit failure would call `destroyBlock` on the entrance track. A thread-local rejection marker from `fromPortal`, reset at connect HEAD, cancels **that** fallback before destruction. Ordinary offworld failure still follows the native path. Normal return consumes the marker; the next attempt's HEAD also clears stale state after exceptions. This assumes the inspected synchronous, non-recursive native provider path, not arbitrary future recursive providers. |
| New graph edge | `TrackGraph.connectNodes` rejects a cross-world lunar pair before creating edges, intersections, graph map changes and edge-added sync. Earlier discovery may already have created local graph nodes; this is an edge-operation gate, not rollback of the entire discovery workflow. No graph load/save filtering or old-edge deletion is performed. |
| Existing graph traversal | At HEAD of the six-argument `TravellingPoint.travel`, replace only its native `IPortalListener` argument with a short-circuit veto. Forward/reverse native veto branches are verified to stop before changing `node1`, `node2` or `edge` to the other world. Allowed paths still call the original listener. This also covers callers/scouts using the shorter overloads. |
| Already straddling train | `Train.earlyTick` and `tick` cancel before their bodies when that train's bogey node dimensions or existing carriage-dimension records span a protected boundary. `Carriage.updateContraptionAnchors` and `manageEntities` have the same pre-body quarantine, before passenger loadout changes, `createEntity`, or `removeAndSaveEntity`. The whole affected train is paused, not the entire track graph. |

The quarantine scan uses a read-only `@Accessor` for `CarriageBogey.points`. Native public `leading()`/`trailing()` **write `TravellingPoint.upsideDown`**, so they are not used in preflight. No convenience getter with those writes is called by the scan. Same-world lunar trains and entirely nonlunar trains do not trigger the straddling predicate.

**Important preservation distinction:** Native travel can update local position, signals or fuel before it reaches its portal-listener veto, and it uses its ordinary blocked-at-edge behavior. This is **before cross-world edge replacement**, not a zero-mutation simulation of the entire train tick. Existing straddling trains are frozen earlier; no carriage is deleted after recreation. Already-created offworld carriage entities/cargo are **not confiscated, rolled back, or made inaccessible to every independent inventory consumer**. A deployment containing such a train remains a blocker requiring parent quarantine/accounting; pausing its railway updates alone does not prove shared cargo isolation. Stale multi-dimension carriage records can also cause conservative quarantine until the parent reviews them.

## Actual Provider Evidence

`scan-providers.ps1` generated `build/inspection/provider-scan.json`: 174 current top-level jars and their first-level nested archives, **98,007 classes**, four class-name references to PortalTrackProvider, all in Create itself (TrackBlock, AllPortalTracks, provider interface and Exit). Full input hashes are recorded. Native `registerDefaults` registers Nether and conditional Aether/Aether II/BetterEnd integrations; those three optional mods are absent from this scanned jar set. No additional statically referenced provider was found in current addons.

This is not proof against reflective name construction or runtime registration. It does not claim arbitrary Ars/Iron portals natively accept train tracks, or that Moon-bound Create routes arise naturally in this pack. Guards for imported/cached links are still needed. A provider returning an unexpected protected Exit is rejected before track creation, **but its own earlier side effects cannot be undone by the resolved guard**. The current known `fromPortal` path has the earlier dimension-pair gate specifically to avoid that problem.

## Verification

- `RulesTest`: **230** pure policy, no-loss remainder, lunar-span and incomplete-pair checks.
- Original `ContractTest`: **519** API/ASM checks, retaining the 14 original entry guards and two native pre-events.
- `PortalContractTest`: **791** checks across **24 new injections plus one read-only accessor**. Native signatures/staticness/callbacks, required targets, exact captured locals, forward/reverse veto control flow, pre-cost call order, and no listed mutation calls in rejection handlers are checked. This is static bytecode analysis, **not a transformed-mixin execution test**.
- `PackagingTest`: **34** packaged files match tested bytes; no vendor libraries/assets/data/tests bundled. Source hashes/logs are in the successful build directory. All 243 shared dependency jars and nine targeted current Prism jars were hash-checked unchanged after build.
- New inspections are under `build/20260913220713438/portal-native-contracts/`; original-path inspections under that build's `native-contracts/`. Reuse of cached libraries is read-only; private copies/extracted Create dependencies remain inside this scope.

No single cancellable native event was verified to cover these complete native portal paths before their side effects. Ordinary player interaction events can cover clicks but not dropped items, direct tile calls or cached railway traversal; the parent's interaction/spell preflights remain its own scope. Narrow entry-point mixins cover those native operations, and Create's verified native portal-listener veto is composed rather than replacing its travel engine. Native Mixin application, mod-order interactions, persistence and mixed-client joining remain parent tests.

## Finite Parent Checklist

Keep the ongoing 0.1.0 N00 run on its original hash. Qualify 0.1.1 separately using the new hash above and one version per test installation. Record both worlds, enable state, stack/source/cargo counts, graph nodes/edges, entity IDs, passenger state and before/after costs.

- [ ] P00: Native application of both mixin configs, all 38 injections and accessor, especially interface static provider injection, listener ModifyVariable and Create FAILHARD local capture. Dedicated and solo; unchanged pack client joining without this jar remains unverified.
- [ ] P01: Default-off positive controls for all added families; enabled nonlunar controls, including normal Nether rail travel and ordinary failed portal-track connection behavior. Confirm no old 0.1.0 regression from the combined pack.
- [ ] P02: Ordinary WarpScroll use/drop, StableWarpScroll useOn/drop, native portal creation and existing portal traversal, both directions touching Moon/orbit. Reject without shrink/source drain/teleport; queued tile entities must be cleared normally without retention. Stable scroll recording remains available but confers no passage authorization.
- [ ] P03: New and already-running lunar RitualWarp. Start denial and paused progress/entity position; explicitly record earlier offerings/source costs, which are not refunded. Offworld coordinate-only ritual still works. A local lunar narrative request must remain denied until separately authorized.
- [ ] P04: Existing Ars tile loaded from NBT, rebinding attached tile, queued entities, and direct static teleport helper, including riders/items. No dimension/position/passenger mutation on denial. Check outstanding BuildPortalEvent separately; do not count this release as an event-queue scrubber.
- [ ] P05: Iron complete and incomplete pairs, frames (upper/lower and pocket subtype), portal entities, missing partner and unloaded partner. Protected source/destination denied before activation/use cooldown, entity rotation/movement or cross-world transfer; offworld incomplete pair does not crash in the new guard. Ordinary portal TTL/cleanup still runs.
- [ ] P06: Create source Moon/orbit `getOtherSide` must return null without executing a provider. `connectToPortal` leaves entrance track intact. Direct `fromPortal` with each protected worldA/worldB permutation must not call native destination creation.
- [ ] P07: Resolved protected Exit and inferred protected-pair rejection preserve source and target blocks, bound locations and items. Verify the policy-specific missing-exit marker cannot affect the next ordinary offworld attempt, including after a failed/exceptional probe.
- [ ] P08: Imported/cached lunar graph edge, forward and reverse freight train, multiple bogeys/carriages, player/non-player passengers and scouts. No point changes to the protected other-world edge; compare cargo and entity IDs. Expect native local stop/fuel/signal behavior, not a fully unchanged approach tick.
- [ ] P09: Already-straddling train and stale dimensional records at enable/reload. Train/carriage pre-body quarantine prevents new recreation, anchor/passenger changes and deletion. Independently inventory/quarantine pre-existing offworld entities and shared cargo access. **Do not mark this case closed solely because the train stops.** Same-world lunar trains and nonlunar trains on the same graph stay active.
- [ ] P10: New graph connection rejection before edge insertion/intersections/sync; old graph save data is not stripped. Save/restart under parent's control, compare cargo and graph data, and repeat with its KubeJS, travel and logistics candidates enabled.

## Remaining RC Blockers

Full RC remains blocked by the native checks above, existing straddling/shared cargo, unqualified queued/lower-level portal construction, placement/link/pocket-generation paths not covered by these traversal guards, runtime provider uncertainty, the parent's remaining unknown routes, and trusted local narrative authorization. There is no fake closure of all travel or material transfer mechanisms. No automatic inventory rollback, deletion, new global mod disable, or speculative future-provider compatibility is claimed.
