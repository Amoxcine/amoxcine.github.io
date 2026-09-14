# Cargo A: Finite Implemented Verifier

Version 0.0.5 implements real read-only stack inspection, replacing the old launch
stub. No client snapshot, operator assertion, wildcard tag, bypass, grant or
deletion. Registered-stack and runtime inventory checks remain parent qualification.

Current gem defaults fix/artifact: [FREEZE-0.0.5.md](FREEZE-0.0.5.md).
Recovery protocol: [FREEZE-0.0.3.md](FREEZE-0.0.3.md), using the new artifact.
Clean dimension-event refusal now releases its witnessed intact reservation, and
the original paid server-receipt rocket can resume after reconnect/restart.

## Exact Supported Cargo

Concrete survival contract: `../lunar-survival-candidate/STARTER_KIT.json` and
`FIRST_LOOP.md`. The build checks the JSON raw/personal caps against travel caps.

| Category | Aggregate bound across every scanned surface |
|---|---|
| First outbound raw only | coal160, redstone22, copper_ingot9, smooth_stone3, cobblestone8, oak_log8, oak_sapling4, dirt4; exact minecraft IDs |
| Repeatable food | Shared 32-item cap across PersonalGear's explicit vanilla/Farmer's Delight prepared foods; unchanged native food effects retained, only plain-bowl conversion permitted |
| Repeatable personal oxygen | 2 ad_astra:large_gas_tank, native oxygen only, <=3000 mB EACH, no fluid resource patches |
| Return reserve | At most 3 ad_astra:fuel_bucket/minecraft:bucket COMBINED; exact registered ResourcefulBucketItem and immutable ad_astra:fuel fluid, 1000 mB per fuel bucket |
| Worn basic suit | One each ad_astra:space_helmet, space_suit, space_pants, space_boots in correct real armor slots; chest native oxygen <=1000 mB |
| Other worn armor | Exact vanilla ArmorItem implementation class regardless of item namespace; correct slot, supported components |
| Tools | Exact vanilla PickaxeItem, AxeItem, ShovelItem, HoeItem, SwordItem, BowItem, ShieldItem, ShearsItem, FishingRodItem implementation regardless of item namespace; one per class, <=8 total |
| Apotheosis | Affix scalar data retained; <=64 affixes; <=8 declared sockets, each occupied slot one native GemItem with only explicit gem/scalar/display components; industrial/opaque socket payload refused |
| Vehicle | Native tier-1 rocket, actual inventory scanned; one fuel compartment, <=3000 mB native fuel only |

The reference diamond pickaxe/axe are included. Arbitrary custom tool/armor subclasses,
jet/netherite suits, crossbows, ammunition, loose suit pieces and arbitrary foods
are not classified in this subset. No existing gear is deleted or replaced.
Socketed GemItem also permits its exact inherited vanilla defaults:
ENCHANTMENTS equal ItemEnchantments.EMPTY, REPAIR_COST equal Integer zero,
ATTRIBUTE_MODIFIERS equal ItemAttributeModifiers.EMPTY. Modified values reject;
all unknown gem components, including container/custom_data/nested sockets, reject.
This narrowly fixes the parent 0.0.4 default-gem refusal; no opaque wildcard added.
These are upper cargo limits, not checks of minimum survival kit, tool durability
or adequate oxygen. User-passed suit oxygen remains that gameplay observation;
this module changes neither oxygen behavior nor survival recipes/resources.

Machines, components, storage blocks, sacks, backpacks, cells, batteries, captured
entities and generic tanks are refused even empty. Exact oxygen tanks/return
buckets above are narrow exceptions. Nonempty container/bundle/custom-data payload
rejects even on allowed gear. Unknown effective components reject. Unknown item
types are rejected BEFORE capability lookup; allowed items also reject when a
detached-copy lookup exposes an opaque NeoForge item handler.

## Real Surfaces And Bounds

- All 36 main/hotbar, 4 armor, 1 offhand slots; actual rocket VehicleContainer;
  carried cursors and extra menu slots, excluding aliases of player/rocket slots.
- Curios 9.5.1 actual CurioInventory: all normal/cosmetic groups and invalid stacks,
  including hidden groups. Pending deserialization or missing capability refuses.
- Accessories 1.1.0-beta.53 underlying holder: ALL slot containers, not the filtered
  valid view; normal/cosmetic and invalid stacks. Pending load refuses.
- Shared 512-slot budget including empty slots, <=64 components/item, <=256 slots
  per container component, <=64 accessory groups. Payload rejects; no recursive
  opaque backing-store exploration or claim that an opaque inventory is empty.
- Actual Common Storage fluid records, including raw stored amounts: the public
  amount accessor normalizes malformed negatives/blank fluids. Oxygen permits at
  most one compartment, native oxygen only and no resource component patch.

Scanner calls no extraction/insertion, serialization, cleanup, clear, discard or
simulated transfer. Never call Rocket.getDropStack during inspection: exact native
bytecode TRANSFERS FUEL OUT. Successful native landing still performs its normal
transfer. Read APIs and accessor coexistence need parent runtime checks.

## One-Time Server Authority

Per-world directory `data/ascendant_lunar_arrivals/`, keyed by player UUID, not
team/quest/client data. No reset or issuance API.

1. Fresh UUIDs may carry raw cargo only outbound from Overworld. Launch eligibility
   and sequence inspect real inventories before native fuel consumption.
2. Landing packet validates actual native destination/history, launch ticket,
   passenger and cargo before history/landing changes. A fresh Moon arrival
   creates an exclusive CREATE_NEW `.pending` record using synced file writes.
3. Only that exact in-memory flight ticket can reuse its reservation during the
   immediate landing recheck. No persistent pending-flight bypass after restart.
4. Only after completed native landing, target Moon level, original rocket removal
   and native lander passenger are checked does `.arrived` get written. Pending
   alone is not an arrival claim. The pending file is retained after success too.
5. Arrived always denies another raw shipment. Pending only reserves the right:
   a clean witnessed veto releases it; the original paid source rocket may resume
   the same uncompleted first flight using its server receipt. No replacement kit,
   reset command, quest/team/death grant. First arrival without raw still uses it.

Records are bounded and validated; corrupt/unreadable files or write failure deny.
Raw resources are also refused on return. Filesystem tests cover reload, replay,
corruption, write failure and concurrent reservation, not an in-game roundtrip.

## Hard Recovery Limits

No global transaction with arbitrary callbacks or independently restored world/player
backups is claimed. Server receipts preserve original player/rocket/source identity
and paid-flight phase. A clean intact veto is retryable without another fuel debit
or consumption of first-kit entitlement; a live original source rider can resume an
interrupted receipt. Do not manually remove pending/arrived records as a reset.
Existing visitor migration, missing physical vehicles and corrupt records are not
automatically repaired or replaced. See the exact frozen recovery protocol.

The two native pre-transfer move/dismount calls are now deferred in the exact landing
scope. Only the final accepted NeoForge decision permits dismount; target position
is supplied to the native transition without moving the source rider first. Native
history and ticket consumption are deferred until successful landing. A native
refusal witness compares mount/position/rotation/velocity/cargo/fuel/history before
releasing pending. Runtime qualification is still required; arbitrary post-acceptance
entity admission, mount callbacks and interrupted cargo-copy are separate gaps.

## Remaining Contract

This is NOT full cargo inspection across mods. Every additional inventory surface
or allowed mod gear needs a pinned read-only adapter, bounded hidden/invalid-slot
enumeration, backing-store nonmutation evidence, unknown-state refusal and native
allow/deny/conservation tests. Remote modules, AE/QIO/QE, Ender Chest, Powah/Draconic
links, grave restore, spatial I/O, trains and entity recreation require the extra
worker and transport adapters; this JAR does not claim those are gated. Moon orbit
must share their protected boundary. Station admission alone is not freight isolation.

Parent `/lunar_cargo fixture`: 33 detached registered-stack cases PASS in native
smoke-20260913-233045-754.log:1170. Includes actual kit+4suit allow, second raw deny, personal-only
later allow, industrial block/bag deny, filled suit, opaque tool, converting food,
malformed/wrong/overcap fluids and slot-budget denial, with before/after comparisons.
Six gem regressions assert precise component/socket denial for modified inherited
defaults, industrial container, opaque data and nested sockets; see FREEZE-0.0.5.md.
This supplies armor slots and raw eligibility directly to CargoScan; it does not
exercise actual equipment enumeration, a flight or the durable arrival transition.
Then `/lunar_cargo check` must test a real rider and actual inventories, split caps
across surfaces, cosmetics/invalid stacks and missing capability refusal. Record
real first landing, return, repeat denial, restart and late-veto preservation.
