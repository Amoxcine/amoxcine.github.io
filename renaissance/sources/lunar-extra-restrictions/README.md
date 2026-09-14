# Lunar Extra Restrictions: Standalone Candidate

Status: **offline-built candidate, not native-qualified and not full RC closure**. No Minecraft boot, installation, world load/save or client join was performed. All writes for this implementation are under `lunar-extra-restrictions/`; the previous audit and travel/logistics scopes are unchanged by this task.

## Artifact

- Jar: `dist/lunar-extra-restrictions-0.1.0-candidate.jar`.
- SHA-256: `50FEC7DD7732CDABE5C9EA113B3F8D60E432A63CC36A2CB0F4FAC2D9F2A35B10`.
- Machine-readable build record: `dist/result.json`.
- Verified build: `build/20260913214423473/`. Contains compiler logs, test logs, source hashes and `native-contracts/*.txt` ASM disassemblies of the actual target classes and candidate hooks.
- 220 pure policy/no-loss checks; 519 native API/bytecode contract checks; 15 packaged files compared byte-for-byte to tested outputs. There are 14 cancellable HEAD injections and two native pre-event subscriptions.
- **Not performed:** Mixin transformation in a loader, native gameplay, loaded-world persistence, mixed-client join, mod-order conflict testing. Static checks do not substitute for these.

## Activation And Packaging

`lunar_extra_restrictions-common.toml` is a normal NeoForge **COMMON** config in the instance/server `config/` directory. The only option is `enabled`, whose native spec default is **false**. The parent can opt in by setting `enabled = true` before starting its qualification server. This task did not create or modify any live config.

The value is latched at `ServerAboutToStartEvent` for that exact `MinecraftServer` and cleared at `ServerStoppedEvent`. Reloading config while running does not change the session's policy; restart to apply it. There is no LAB flag, world-name check, loopback condition, external controller dependency or temporary unlock stage. Dedicated and integrated servers use the same server-side checks. A client connected to a remote server does not enforce a local client config against that server.

There are no packets, registry additions, items, blocks, menus, recipes, datapacks or saved data. The jar is intended for **server-only deployment with existing pack clients**, or installed in the client instance hosting a solo integrated server. Optional remote-client joining is **an untested compatibility expectation**, not a verified result. No legacy `displayTest` flag is relied on: the inspected current loader/universal jars do not contain that setting. Clients may show ordinary prediction or spell animations for an action the server rejects.

Required versions are pinned in native metadata: Minecraft 1.21.1, NeoForge 21.1.248, Draconic Evolution 3.1.4.632, AE2 19.2.17, AdvancedAE **1.6.12-1.21.1**, bundled AE2AddonLib **1.0.3-1.21.1**, Ars Nouveau 5.13.0, Iron's Spells **1.21.1-3.16.3**, BuildingGadgets2 1.3.9. Version strings come from jar metadata, not just filenames. Target jars and relevant runtime hashes are pinned in `pins.json`. No vendor library is bundled in the candidate.

## Boundary

The protected worlds are `ad_astra:moon` and `ad_astra:moon_orbit`. Both identifiers are present in the current `adastra-1.21.1-1.16.24-neoforge.jar!/data/ad_astra/planets/moon.json`. Orbit was explicitly included following the parent's BuildingGadgets request; surface and orbit remain distinct worlds. A link from Moon to Moon orbit is therefore cross-world and denied.

- Endpoint-aware link: deny differing worlds when either endpoint is protected. Same-world links remain allowed. Missing target on a protected source is denied without resolution.
- Shared inventory/spatial access: deny on either protected world, since a shared inventory has no trustworthy local endpoint and spatial storage is itself a separate-world transition.
- AdvancedAE armor remote grid: deny if source **or linked anchor** is protected, including same-world lunar anchors. A same-world anchor cannot prove the attached grid/storage is local. This is the requested **lunar remote closed fallback**, not a claim of successful local-grid provenance analysis.
- Disabled config and all-nonlunar cases retain native behavior. No whole mod is disabled.

## Implemented Coverage

| ID | Verified hook and rejection | Preserved behavior / limit |
|---|---|---|
| T01 | `EnergyLinkEntity.tick(ModuleContext)` HEAD: cancel lunar cross-world links before `updateConnection`, core lookup, connection charge and recipient/core transfer. Uses the existing `linkedPos` field and server-side stack entity. | Same-world energy links and all nonlunar links run natively; no link NBT is cleared. The policy only covers this EnergyLink entry point. |
| T02 | `EnderCollectionEntity.insertStacks` returns a new ordered list containing the **same unchanged stack references**; `insertStack` returns the original **remaining count**, not zero. Both HEAD guards run before energy debit or chest lookup/insertion. | Native callers retain all rejected items. Covers this module's vanilla EnderChest and EnderStorage branches; no attempt to rewrite or clear inventories. Actual gameplay item-count conservation remains a native test. |
| T03 | Verified AE2 `GridSpatialEvent.preventTransition()` on a protected spatial IO world; native event check precedes cell execution, real energy extraction and IO slot movement. `SpatialStorageCellItem.doSpatialTransition` also returns false at HEAD for direct callers, before plot allocation, cell metadata and region swap. | Nonlunar spatial IO unchanged. Existing cell contents/provenance are not migrated or erased. This does not inspect every possible addon implementation of `ISpatialStorageCell`; the native IO event covers the inspected port's calls. |
| T04 | Interface-default resolver `IGridLinkedItem.getLinkedGrid(ItemStack, Level, Consumer)` returns null at HEAD **only for `QuantumArmorBase` receivers** involving a protected world. Two-argument overload delegates to it. `QuantumHelmet`, `QuantumArmorBase`, and `IUpgradeableItem` do not override the guarded implementation. | Verified callers `UpgradeCards.autoFeed`, `autoStock`, `recharging` stop on null before storage/energy service access. Defenses and unrelated armor features remain. Local lunar remote armor functions are deliberately closed too; other addons using the library are not restricted by this hook. |
| T05 | Native `RightClickBlock` pre-event rejects protected-world vanilla EnderChest interaction before native menu creation. Ars `EffectEnderChest.onResolveEntity` and Iron `SummonEnderChestSpell.onCast` HEAD cancel before chest access/menu creation; Iron is also before `attemptRemoveScrollAfterCast`. `PlayerEnderChestContainer.stillValid` returns false and `ChestMenu.quickMoveStack` returns EMPTY for protected-world EnderChest menus. | Native container-click packet verifies `stillValid` before transaction and remote-slot mutation; already-open ordinary/spell ChestMenus cannot transact after arrival. No replacement of `getEnderChestInventory`, save serialization, cursor contents or stored items. Upstream spell mana/cooldown/animations are **not refunded or asserted unchanged**. Unknown third-party direct raw-container users are not proven covered. Automatic Draconic access is covered by T02. |
| T15a | `BuildingUtils.getHandlerFromBound(Player, GlobalPos, Direction)` HEAD returns null for the endpoint boundary before `getLevel`, `getBlockEntity`, capability acquisition. | Same-world binding allowed. No inventory or fluid-container mutation in rejection. |
| T15b | All four direct AE2 bypasses guarded at HEAD: `AE2Methods.checkAE2ForItems`, `checkAE2ForFluids`, `insertIntoAE2`, `insertFluidIntoAE2`. Cross-lunar rejection is a void return before remote world/grid lookup. | Item-demand list, ItemStack and FluidStack remain untouched; simulation and execution use the same predicate. Same-world AE anchors remain allowed. **A same-world anchor attached to a remote-backed AE network remains the parent's logistics responsibility**, not a network-locality proof here. |

## Evidence And Offline Checks

`ContractTest` reads native class resources as ASM bytecode without initializing Minecraft. It verifies exact target arguments/staticness, return callback type, shadow field descriptor, single unambiguous target per injection, HEAD/cancellable/require=1, class/interface kind, candidate bytecode with `BasicVerifier`, and absence of field writes or listed inventory/energy mutations in handlers. It checks native cancellation branches, not only event names: spatial cancellation skips cell/energy/slot changes; invalid chest menus skip click and remote-slot/carried updates.

The AdvancedAE nested library was extracted read-only from its pinned outer jar into this scope. Its resolver explicitly calls `server.getLevel(bound.dimension())`, then `Platform.getTickingBlockEntity`, then the wireless access point's `getGrid`. Native Mixin 0.8.7's interface-injection support was inspected; it enables interface injectors when the configured compatibility level supports default methods. Actual interface transformation is still a parent boot gate.

The BuildingGadgets evidence supplied by the parent was independently checked against the pinned current jar. Quarry/Schematicannon were **not retraced or modified** here. Their status belongs to the parent's supplement; this artifact does not claim to close those findings.

Build with `./lunar-extra-restrictions/build.ps1` from the workspace. It uses only existing JDK/dependencies, checks current Prism hashes, compiles Java 21 with annotation processing disabled, runs offline tests, packages reproducibly, and verifies all 243 shared cache jars and eight selected Prism jars unchanged afterward. No download/installer/boot command is included. The Windows sandbox caused JDK archive-close errors even on private copies; the successful run used the reviewed offline-build escalation. Private byte-identical compile copies remain under this scope. Never run the parent controller build to build this candidate.

## Parent Native Checklist

Record exact jar hash, enabled state, source/target world, loaded/unloaded target, before/after item/fluid/energy counts and relevant log result. Run enabled scenarios on a dedicated server and repeat a representative rejection/allow pair in a solo integrated server. These boxes are intentionally **not checked** by offline results.

- [ ] N00: All 14 injections apply with no missing-target, interface-injector or dependency errors. Neither current jar versions nor loader metadata are silently substituted.
- [ ] N01: Absent config/default false preserves representative EnergyLink, EnderCollection, spatial IO, both chest spells and BuildingGadgets behavior. Enabling requires restart; verify true session log. Stop/restart and open a second solo world to check session cleanup and correct new config latch.
- [ ] N02: Dedicated server with candidate accepts the unchanged pack client **without candidate**; also test both sides installed. Solo host with candidate behaves equivalently. Client prediction must not become authoritative mutation.
- [ ] N03: T01 core/player in Overworld/Moon, Moon/Overworld, Moon/orbit, orbit/Moon. Try uncharged/reconnecting links and loaded/unloaded cores. Denial must leave both energy balances and link charge unchanged. Same-Moon and same-orbit links still charge; Overworld/Nether remains unchanged.
- [ ] N04: T02 bulk/single collected items on Moon and orbit, with filters, nearly full/full chest, enough/insufficient module energy and repeated pickup. Count rejected stacks/world drops/player inventory and both chest/energy balances; no disappearance or duplicate stacks. Offworld collection still works.
- [ ] N05: T03 import/export on Moon and orbit, empty and previously bound cell, repeated redstone attempts, direct cell API caller. Check plot count, cell components, region blocks/entities, port input/output and real energy remain unchanged. Nonlunar transition still works. Save/restart the qualification world and compare.
- [ ] N06: T04 auto-stock withdrawal/surplus return, auto-feed and armor/inventory/Curios recharge. Test offworld anchor while lunar, lunar anchor while offworld, same-lunar anchor, missing/unloaded anchor. All protected cases must leave grid items/power unchanged. Nonlunar features and ordinary armor defenses remain usable. Same-lunar remote armor access is intentionally denied, not a regression against this candidate's policy.
- [ ] N07: T05 vanilla chest, Ars EnderChest glyph and Iron Summon EnderChest on Moon/orbit: no menu/open-counter/chest mutation; Iron scroll is not consumed inside onCast. Separately record upstream spell costs rather than assume refund. Open each chest menu offworld, enter the protected world, then normal/shift/number-key/drag/double-click; no chest transaction. Close/reconnect/save/restart without losing cursor/player/chest items. Ordinary local chests and offworld EnderChest access still work.
- [ ] N08: T15a bound capability insertion/removal and fluid-via-container-items, both directions involving Moon/orbit, loaded/unloaded destination. No remote lookup/transfer or inventory loss. Same-world bindings still work, including Moon and orbit independently.
- [ ] N09: T15b all four AE2 APIs in simulation and execution with partial/full demands, stack/fluid remainders and both lunar directions. Caller list/stack/fluid and grid counts must stay unchanged on rejection; same-world positive controls must work. Also test the combined pack with the parent's AE network isolation enabled.
- [ ] N10: Combine with parent travel/logistics restrictions, then run their independent remaining-path and rocket/local-narrative-portal tests. This jar neither implements nor changes those mechanisms.

## Integration And Remaining Blockers

No changes or new interfaces are required in the existing travel/logistics controller. This candidate's opt-in flag is independent: parent qualification must explicitly enable it. Its synchronous `Rules.crossing`/`sharedAccess`/`unprovenGrid` predicates are pure, while actual enforcement also checks native server identity. There is no LAB-only prerequisite and no shared inventory ledger.

**Full RC remains blocked** until the parent's remaining unknown paths, combined-policy behavior, and native checklist are qualified. This candidate does not close Draconic dislocators, other shared inventories, independent wireless/spatial/network integrations, or arbitrary raw inventory access merely because their mods are present. It does not prove that a same-world AE grid is entirely local, recover pre-existing lunar exports, rewrite saved data, or implement rockets-only travel. Keep the original audit open for everything outside the precisely listed paths.
