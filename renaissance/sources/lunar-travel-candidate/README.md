# Lunar Travel Candidate

Current artifact: [FREEZE-0.0.5.md](FREEZE-0.0.5.md), exact inherited native gem
defaults fix. Parent 0.0.4 fixture progressed beyond the bucket failure but failed
the socketed-gem pickaxe. Parent subsequently passed the 0.0.5 native fixture:
33 cases, smoke-20260913-233045-754.log:1170. Accepted for RC packaging freeze;
real-flight manual qualification remains open. Frozen JAR unchanged.

Version 0.0.3 adds intact late-veto handling and same-paid-rocket recovery to real bounded cargo inspection and a
server-owned first-arrival ledger. **Offline-tested candidate, not a qualified
playable RC.** No installation or server/client startup. Parent owns integration.
Sources and outputs remain separate from the old travel prototype/runtime.

Current frozen hash, config and fixture: [FREEZE-0.0.5.md](FREEZE-0.0.5.md).
Full native recovery protocol: [FREEZE-0.0.3.md](FREEZE-0.0.3.md), with the new JAR.
This supersedes the earlier recovery limitations
for a clean dimension-event veto, but is not a universal callback/rollback guarantee.

## Implemented Subset

- Scoped Ad Astra 1.16.24 tier-1 rocket sequence for Overworld <-> Moon, with
  exact player/rocket/server/destination authentication, not a global permit.
- Covered teleports involving Moon OR Moon orbit refused in both directions and
  locally. Explicit Waystones/dislocator guards; no approved narrative links.
- Other planets/orbits reserved. Earth-orbit stations also closed until relay
  safety is proven. Station landing/construction packets are refused.
- Real player main/armor/offhand, cursor/menu, rocket inventory/fuel, Curios and
  Accessories scans. Unknown items/components, opaque storage and filled payloads
  refuse travel without confiscation. Only the finite supported gear/kit is allowed.
- Concrete survival `STARTER_KIT.json` raw allowance once per player, committed
  only after successful native Moon landing. Bounded personal supplies remain
  allowed later; raw imports do not.

[CARGO_VERIFIER_CONTRACT.md](CARGO_VERIFIER_CONTRACT.md) describes exact limits and
recovery semantics. [QUALIFICATION.md](QUALIFICATION.md) lists C10-C14, remote
logistics, runtime and landing-preservation blockers. This is not full mod cargo
inspection or proof of lunar isolation across every transport.

## Configuration

COMMON `ascendant-lunar-travel.toml`, captured at server startup:

```toml
enabledSolo = false
enabledDedicated = false
```

Both modes default off; restart required. No LAB/IP/world-name dependency, cargo
bypass, OP exception, reset command or quest authority. Pinned required mods are
declared in `neoforge.mods.toml`. Old `ascendant_travel_lab` is incompatible.
No deployed configs, recipes, scripts, saves or runtime installations were changed.

## Build And Parent Checks

`./build.ps1` uses Java 21 and existing local dependencies only, verifies native
JAR hashes and writes timestamped output below this candidate. No downloads or
Minecraft startup. Main/test classes compile separately; tests are not packaged.
Windows Java JAR-close restrictions required approved local execution. Latest
artifact/hash and offline results: [BUILD_RESULT.md](BUILD_RESULT.md).

Parent-only enabled-mode diagnostics, OP level 2:

- `/lunar_cargo fixture`: registered native kit + four suit pieces allow, repeat
  raw shipment deny, personal-only later allow, industrial block/bag and hidden
  payload deny; detached stacks are compared before/after. Not a grant command.
- `/lunar_cargo check`: real player's equipped inventories and ridden rocket
  scanned without travel or ledger mutation. Run while sitting in a native rocket.
- `/lunar_cargo gear_report`: actual held/worn classes and components for targeted adapters.
- `/lunar_travel veto_once`: operator-only, one-shot native DENIAL witness.
- `/lunar_travel resume`: self-only recovery of the same paid physical rocket;
  no replacement, kit grant, fuel refund or teleport bypass.

The cargo fixture passed in the parent LAB; other diagnostics are not established
by that detached-stack test. A native roundtrip and refusal
preservation test are still required. `inspect.ps1` and `inspect-cargo.ps1`
reproduce local bytecode evidence without modifying the inspected JARs.
