# Exact Coverage Limits

This is a bounded logistics candidate for Moon and Moon orbit, not full lunar RC
qualification. Source compilation and static bytecode matching do not demonstrate
that every mixin successfully transforms alongside all 147 baseline mods.

## Guarded Surfaces

- AE2 19.2.17 `QuantumCluster.updateStatus`: exact partner lookup, before native
  existing-link return; denial follows native connection destroy/clear logic.
- Mekanism QE: `getFreq`, existing native endpoint tick capability invalidation,
  global frequency ejection, dynamic containers, chemical facade and retained heat
  proxy resistance hooks. Shared pool is always closed at protected endpoints.
- QIO importer/exporter (including return), Applied Mekanistics QIO adapter,
  viewer subscriptions and frequency access, portable/block menu validation,
  native crafting/clear/transfer packet paths. A separate guard prevents protected
  endpoints registering drives with `QIOFrequency.update` and `addDrive`.
- Powah Ender native energy receive/extract/can-use, extender insertion/absorption,
  item charging, neighbor credit; Player Transmitter exact charge call with live
  source/recipient dimension and identity checks before credit and native debit.

## Not Covered Or Not Qualified

- Raw QE `InventoryFrequency`, nested item/fluid/energy/heat objects, arbitrary
  third-party retained container references, computer/reflection/direct-internal
  calls are NOT universally mediated. The existing chemical facade only covers
  the copied, audited call paths. Diagnostic raw frequency reads are deliberately
  OP-only observations of the fresh private fixture, never production authority.
- Raw QIO frequency/drive APIs obtained outside guarded endpoint/menu paths remain
  unmediated. Unknown non-block drive holders are conservatively refused by the
  new registration hook, including outside lunar worlds. This may affect addons.
  Native drive stock preservation/publication/unload/reload trials are pending.
- Raw Powah `Energy` / `EnderNetwork` references and external direct mutations are
  unmediated. The charge redirect does not create an atomic rollback mechanism for
  reentrant third-party callbacks once native recipient credit has begun.
- ExtendedAE wireless/quantum/network mechanisms, SchematicEnergistics and other
  AE2 addons are NOT comprehensively audited or intercepted by an AE2 quantum
  bridge hook. Same-world AE2 permission proves only that particular direct pair,
  not the absence of another transport in the whole network.
- Ender IO interdimensional mechanisms, vanilla Ender Chest global inventories,
  any other shared storage, wireless links, remote APIs, block-moving systems,
  inventory automation, scripted or privileged transport are outside this build.
  No claim is made that every such mechanism exists/enables transfer in this pack.
- Mekanism teleporters/other wireless equipment, Waystones, Draconic Evolution
  dislocators, generic teleportation, rockets/passengers, personal cargo filtering,
  portals, inventories moved by travel and permissions on other planets belong to
  other modules. No travel mixin, positioning alias, quarry or habitat change here.
- Human GUI interactions, prepared recipes during dimension changes, menu reopening,
  dead/disconnected players, cached capabilities before/after removal, moving blocks
  between worlds, real saved grants, native save/restart, team changes and actual
  item/fluid/chemical/energy/heat conservation have no fresh native result from this
  agent. Test plans and the OP diagnostic are not evidence they passed.
- Existing outside QIO valid-session hardening and fail-closed unknown/stale endpoint
  checks are inherited in scope; these are not an assertion that every outside GUI
  subclass or addon behaves identically. With opt-in OFF no mixins are selected.
- No benchmark or multiplayer-load qualification. Runtime guards are endpoint-local;
  no world-wide per-tick scan is added. OP preparation preflights two bounded
  400-position volumes, and probe only reads its known devices.

## Persistence And Release Boundary

No old controller, world/runtime directory, old grant data or world journal was
changed. The copied mod contains no old SavedData classes, relay registries or
grant commands. No migration/remap is offered. Denials do not intentionally clear
stock or frequency ownership. That design is not a substitute for fresh native
stock-conservation tests on the final candidate.

The old controller cannot co-load with this copied implementation. Enabling or
disabling requires a full process restart. Do not treat this JAR as an installed
fix or universal protection until the remaining mechanisms are independently
closed or explicitly excluded by the parent RC acceptance scope.
