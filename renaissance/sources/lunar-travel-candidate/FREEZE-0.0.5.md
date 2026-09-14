# Frozen Gem Defaults Fix 0.0.5

Artifact: `build/20260913232401302/ascendant-lunar-travel-0.0.5-candidate.jar`

SHA-256: `A416643D2ABE2E94348B86FEA7C1FABCB43140A08B511C2C5F199B0D655A649F`

Accepted by parent for RC packaging freeze after native fixture PASS 33.
Real-flight manual qualification remains open. Older frozen JARs remain unchanged.
No installation or Minecraft boot by this worker; parent alone integrates.

## Native Evidence: PASS 33, Not A Flight

Parent-run evidence, read directly from
`../lunar-native-lab/runtime-bootstrap/smoke-20260913-233045-754.log`:

- Line 320: loaded Ascendant Lunar Travel Candidate 0.0.5-candidate.
- Line 1125: `active=true cargo=FINITE_SCAN firstArrival=SERVER_LEDGER nativeRoundTrip=UNQUALIFIED`.
- Line 1168: `SMOKE_COMMAND: lunar_cargo fixture`.
- Line 1170, local time 2026-09-14 01:31:25:

```text
CARGO NATIVE FIXTURE PASS 33 cases: real kit+4suit allowed; repeat raw/industrial bag denied; fixture stacks unchanged. No player/world mutation.
```

All 33 cases listed below completed with their expected allow/deny result. This
includes the default socketed native gem that failed in 0.0.4, the six precise
gem-payload regressions, native fuel buckets, kit/suit/food cases and detached
stack before/after equality checks. The native fixture blocker is resolved.

Evidence boundary: these are registered native ItemStacks passed directly to the
bounded CargoScan. Armor slots are supplied by the fixture, not equipped on a live
player. First/repeat raw eligibility is a scanner input, not a flight or ledger
transition. The PASS therefore does NOT prove real player/menu/Curios/Accessories
or rocket inventory enumeration, fuel consumption, takeoff, Moon landing, return,
first-arrival commit, late-veto preservation or restart/reconnect recovery.
Those real-flight/manual checks remain for the parent under FREEZE-0.0.3.md.
It is not universal modded-cargo or transport-isolation certification.

Documentation-only evidence update; no code, dependency, config, build or JAR
change after the frozen artifact above. Parent is packaging that same 0.0.5.

## Observed Failure And Exact Change

Parent log `../lunar-native-lab/runtime-bootstrap/smoke-20260913-230733-203.log`
reports 0.0.4 failure at the socketed-gem pickaxe case:
`CARGO_APOTH_GEM_PAYLOAD @ fixture:0 [minecraft:diamond_pickaxe]`.
The fixture had progressed beyond the bucket failure; no overall native PASS.

Pinned Apotheosis 8.7.0 registers GemItem using its unmodified Item.Properties
constructor. Minecraft 1.21.1 COMMON_ITEM_COMPONENTS supplies three previously
omitted effective components, even on a new, unmodified GemItem. The scanner now
permits only these exact native values inside a socketed gem:

- `minecraft:enchantments` equal to `ItemEnchantments.EMPTY`.
- `minecraft:repair_cost` equal to Integer zero.
- `minecraft:attribute_modifiers` equal to `ItemAttributeModifiers.EMPTY`.

These are typed comparisons, not a namespace, arbitrary-default or opaque-payload
allowlist. Nonempty enchantments/attributes and nonzero repair cost remain denied
on gems. This does not change the existing rules for the enclosing personal tool.
Unrecognized gem components still reject, with socket index AND component ID in
the diagnostic. Existing exact GemItem class/count, eight-socket and 24-component
bounds remain. No stripping, serialization, recursive opaque inventory lookup,
item grants, fuel change, travel hook change or ledger change.

Evidence: `inspect-gem.ps1`, `evidence/gem/`, and GemDefaultsContractTest.
The local test reads native class bytes only; it does not initialize Minecraft.

## Parent Native Preflight

For the dedicated LAB only, parent retains `config/ascendant-lunar-travel.toml`:

```toml
enabledSolo = false
enabledDedicated = true
```

Both shipped defaults remain false. Restart is needed for configuration changes.
After parent replaces only the previous travel candidate, run from console:

```text
lunar_cargo fixture
```

Observed in the parent log above: **CARGO NATIVE FIXTURE PASS 33 cases**.
All fixtures construct detached stacks, compare before/after inspection and never
insert objects into a player, rocket or world. The enchantment regression obtains
the real UNBREAKING holder from the server registry read-only.

| Case | Passed Result In Detached Native Fixture |
|---|---|
| 1 | First native kit + four suit pieces in supplied armor slots ALLOW |
| 2 | Same raw shipment with raw eligibility false DENY; no actual arrival performed |
| 3 | Later personal kit without raw ALLOW |
| 4 | Bread16 + chicken16 ALLOW |
| 5 | Bread17 + chicken16 DENY shared food cap |
| 6 | Native Farmer's Delight chicken soup ALLOW |
| 7 | Pickaxe with default native GemItem socket ALLOW; assert the three inherited defaults exactly |
| 8 | Socketed gem repair cost 1 DENY repair_cost |
| 9 | Socketed gem UNBREAKING I DENY enchantments |
| 10 | Socketed gem nonempty ARMOR modifier DENY attribute_modifiers |
| 11 | Socketed gem containing industrial block DENY container |
| 12 | Socketed gem opaque backing-store data DENY custom_data |
| 13 | Socketed gem containing another socketed gem DENY socketed_gems |
| 14 | Industrial block directly in tool socket DENY |
| 15 | First kit plus one coal DENY raw cap |
| 16 | Personal kit plus one food DENY food cap |
| 17-18 | Generator, plain then industrial-filled DENY both |
| 19-20 | Chest, plain then industrial-filled DENY both |
| 21-22 | Shulker box, plain then industrial-filled DENY both |
| 23-24 | Sophisticated backpack, plain then industrial-filled DENY both |
| 25 | Industrial-filled worn suit DENY |
| 26 | Opaque tool backing inventory DENY |
| 27 | Food converting to industrial block DENY |
| 28-29 | Oxygen tank amount -1 / 3001 DENY both |
| 30 | Personal oxygen tank carrying fuel DENY |
| 31 | Worn suit oxygen 1001 DENY |
| 32 | Third personal oxygen tank DENY |
| 33 | Scan slot-budget overflow DENY |

Cases 8-13 assert the exact gem-payload refusal including component and socket,
not merely any denial. Stop on any failure and retain the full diagnostic.
Only after PASS, continue `/lunar_cargo check` on a real native rocket rider,
then the native roundtrip, late-veto and durable recovery protocol in
`FREEZE-0.0.3.md`, substituting this artifact/hash. No player cargo is reissued.

## Offline Results And Limits

- Compilation and all local suites PASS: 434 policy assertions; 37 hooks / 41
  sites / 7 accessors; 7 fuel-bucket and 10 gem-default bytecode contracts;
  19 arrival-ledger and 14 paid-receipt assertions; exact starter manifest check.
- 33 native cases subsequently PASS in parent smoke-20260913-233045-754.log:1170.
  Two optional JetBrains annotation warnings during the unchanged local build.
- JAR inspected: 50 entries, no test classes. Compared with 0.0.4, only
  PersonalGear, CargoFixture (including generated StackSlot), CargoDiagnostics,
  and mod metadata differ. Transport and recovery class bytes are unchanged.
- Native fixture PASS is confirmed; real roundtrip and refusal conservation still
  require parent testing. Accepted for RC packaging freeze, not full gameplay qualification.
  Existing Draconic/Iron custom gear and C10-C14 gaps remain explicitly documented.
