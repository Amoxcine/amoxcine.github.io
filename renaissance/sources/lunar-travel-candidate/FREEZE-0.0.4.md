# Frozen Bucket Fix 0.0.4

Artifact: `build/20260913225244286/ascendant-lunar-travel-0.0.4-candidate.jar`

SHA-256: `AE9B26388FC6E0DBA4D34800923D56279EB9A0DA2EF7174879791A19C5FA60B0`

This file supersedes 0.0.3 for testing. All older JAR files remain unchanged.
Parent alone installs/boots; this worker only compiled and inspected local files.

## Confirmed Failure And Narrow Fix

Parent native log `../lunar-native-lab/runtime-bootstrap/smoke-20260913-224711-913.log`
line 1140 reports first-kit refusal: CARGO_CHANGED_BUCKET_CLASS, fixture:6,
ad_astra:fuel_bucket. The detached fixture lost no player objects. Loading 0.0.3
with active=true succeeded, but its cargo allow case did NOT pass.

Actual Ad Astra 1.16.24 ModItems registration constructs ResourcefulBucketItem
using ModFluids.FUEL_FLUID_TYPE, plain bucket craft remainder and stack size one.
Resourceful Lib 3.0.12 passes that fluid's still supplier to BucketItem's constructor.
NeoForge's BucketItem.content field is public final and can be read without a
fluid capability, extraction, serialization or simulated transfer.

The corrected scanner requires ALL of:

- Exact item ID ad_astra:fuel_bucket, already in the finite personal allowlist.
- Same registered item instance as ModItems.FUEL_BUCKET.get().
- Exact ResourcefulBucketItem class, NOT instanceof BucketItem or an arbitrary subclass.
- Native immutable fluid registry ID ad_astra:fuel.
- Existing full component/payload checks and combined fuel/empty bucket cap of 3.

No other bucket/fluid is newly approved. Raw allowance, recovery, transport hooks,
gear readers and fixture case list are unchanged. Resourceful Lib 3.0.12 is now
explicitly hash-pinned and required; actual native mod metadata version verified.

## Parent Rerun

Use the same dedicated config `config/ascendant-lunar-travel.toml`:

```toml
enabledSolo = false
enabledDedicated = true
```

After replacing only the old travel candidate JAR, run in server console:

```text
lunar_cargo fixture
```

Expected result remains **CARGO NATIVE FIXTURE PASS 27 cases**. This is an expected
result, NOT observed by this worker. Stop on another fixture error and retain its
exact class/component/location diagnostic. No grant or player inventory insertion.
Only after that pass, continue real-rider cargo check and native refusal/recovery
steps in FREEZE-0.0.3.md, substituting this new artifact/hash.

## Offline Verification

- 434 policy/auth/scope assertions; 19 arrival-ledger and 14 paid-receipt tests.
- 37 hook handlers / 41 native sites / 7 cargo accessors and mutation-call audit.
- 7 NEW bucket registration/class/fluid contracts, resolved from the native
  registration's actual invokedynamic supplier, not an assumed lambda number.
- Exact survival kit JSON contract unchanged; 27 native fixture cases compiled.
- 50 packaged entries, no test classes. Compared with frozen 0.0.3: only CargoScan
  and its generated nested class, plus mod metadata, change inside the JAR.
- Two optional JetBrains annotation warnings; no compile/test errors.

The parent must rerun the native fixture. This fix is not a new full-RC or full
mod-equipment/cargo qualification claim; existing targeted limits remain.
