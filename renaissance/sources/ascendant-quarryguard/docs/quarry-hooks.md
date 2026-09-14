# QuarryPlus security hooks: pinned laboratory integration

Date: 2026-09-04. Targets: AdditionalEnchantedMiner 21.1.162, Minecraft
1.21.1 official names, NeoForge 21.1.248. This is NOT production-ready and
does not establish an atomic security boundary for arbitrary mod callbacks.
Only the authorized mixin directory and this report were changed here.
No production writes or game/server launches were performed by this task.

## Registration and build handoff

If the mixin JSON package is `fr.ascendant.quarryguard.mixin`, register all
of these common/server-capable entries in `mixins`, not `client`:

```json
[
  "quarry.BlockItemPlacementMixin",
  "quarry.QuarryBlockMixin",
  "quarry.AdvQuarryBlockMixin",
  "quarry.QuarryEntityMixin",
  "quarry.AdvQuarryEntityMixin",
  "quarry.AdvActionSyncMessageMixin",
  "quarry.AdvActionActionMessageMixin",
  "quarry.QuarryChunkLoaderLoadMixin",
  "quarry.MiningNeoForgeMixin"
]
```

If the configured package already ends in `.quarry`, omit the `quarry.`
prefix. `AdvQuarryStateAccessor` was deleted and MUST NOT be registered.
Configuration must use `required: true`, `injectors.defaultRequire: 1`,
and Java 21 compatibility. Every injection has explicit `require >= 1`;
all target classes and injections have `remap = false`. Do not relax a
failed match to `require = 0`. Method-array injection requirements count
matches collectively; the static per-selector check and runtime audit
remain necessary to detect partial drift.

No access transformer is needed. QuarryState, AdvQuarryState and the
packet Action enum are package-private. State-transition handlers use an
exact descriptor plus `@Coerce Object`, then inspect `Enum.name()`.
Reconfiguration-only checks read the concrete entity's public
`toClientTag(new CompoundTag(), level.registryAccess()).getString("state")`.
There is no state-tag serialization in the tick/work guards. Iterator,
position, skipped-set and search-energy shadows retain their exact types.

NeoForge's exact universal archive contains
`META-INF/jarjar/mixinextras-neoforge-0.5.3.jar`; its jarjar metadata reports
artifact version 0.5.3 and range `[0.5.3,)`. The main build must include this
embedded dependency on the compilation classpath. Keep `WrapMethod` for
exception cleanup. Do not replace it with RETURN-only cleanup to compile.
Main reported successful compilation and a successful minimal native
runtime load before the focused approved-link and collection follow-up.
Those new redirects and the advanced collection scope require a fresh
main build and transformed-runtime check. This task has not run the
compiler or launched a transformed runtime itself. No new mixin class or
registration entry was added by the follow-up.

## Placement and token lifetime

`BlockItem.placeBlock(BlockPlaceContext, BlockState): boolean` has a HEAD
guard. It uses the actual updated placement context and actual selected
state passed to that method, not the outer `place` argument or a guessed
position. Only `QuarryItem` (including its NeoForge subclass) and
`AdvQuarryItem` are intercepted. Client prediction is left unchanged.
A refusal returns false before the original block placement.

`BlockItem.place(BlockPlaceContext): InteractionResult` is wrapped with a
thread-local scope and `try/finally`. Nested server Quarry placements
return FAIL without entering the original method or clearing the outer
policy token. Other item types and client calls do not own/clear this
scope. Direct unscoped calls to the protected `placeBlock` are denied for
Quarry items. `afterPlace()` is called at outer entry, the original
method's RETURN, and in the wrapper's finally; it MUST be idempotent and
only clear per-thread placement state. The finally also removes the scope
even if `afterPlace()` throws. An exception in placement is not swallowed.

Both block `setPlacedBy(Level, BlockPos, BlockState, LivingEntity,
ItemStack): void` implementations call `confirmPlacement` at HEAD, before
their superclass call, energy setup, marker lookup, inventory credits,
marker removal, UI packet and WAITING state. False cancels the entire
Quarry-specific callback. For direct/non-item placement the block may
already exist: this guard does NOT roll it back or refund anything.

Main must match level identity, immutable position, actual block/state,
trusted real placer UUID, exact Area and chosen marker-link identity and
all consumed marker positions. Confirmation must be one-shot, not merely
an indefinitely reusable matching position until RETURN. Nested
`setPlacedBy` calls that bypass `BlockItem.place` must not reuse a token.

The focused follow-up redirects the sole
`Stream.findAny(): Optional` invocation in each setPlacedBy (bytecode
offset 167) to `GuardHooks.approvedLink(): Optional<QuarryMarker.Link>`.
It never evaluates the replacement lookup stream. The original code
therefore obtains the preflight-selected link and frozen Area supplied by
main, rather than selecting another marker after authorization. Original
Area.assumeY, validation, drops, removal and state handling remain intact.

Main now reports approvedLink and mayCollect implemented, with exact
Area + BlockState + marker identity + NBT comparison and a single-use
confirmed token. Its updated contract retains the selected link and
supplies a frozen-area delegate for that link. This is main's reported
implementation status, not a new runtime certification by this task. Defaults
must also return a PRESENT approved StaticLink computed during preflight.
An empty approvedLink throws before the original orElseGet can calculate
an unapproved default; there is no fallback to the ignored stream. This
is a contract failure after physical placement, not a rollback mechanism.
No new default-area invoker is introduced here; main owns exact formulas
and any required invoker. The callback/delegate side effects still require
the tests below, especially marker mutation after confirmation.

## Entity work and area changes

The common QuarryEntity and AdvQuarryEntity classes declare concrete
`serverTick(Level, BlockPos, BlockState, <entity>): void` methods. HEAD
guards run before their repeat loops. Additional guards run on every
entry to their work methods, including recursive frame calls:

| Target | HEAD guards |
| --- | --- |
| QuarryEntity | waiting, breakInsideFrame, makeFrame, moveHead, breakBlock(), removeFluid, removeFluidAt, breakBlock(BlockPos), breakBlockModuleOverride, afterBreak |
| AdvQuarryEntity | waiting, startQuarryWork, makeFrame, breakBlock, cleanUp, removeFluidAt, removeEdgeFluid, removeFluidAtXZ, breakOneBlock, breakBlocks, cleanUpFluid, breakBlockModuleOverride, afterBreak |

Denied void work returns immediately; WorkResult methods return
`FAIL_EVENT`, never SUCCESS/SKIPPED; afterBreak returns
`BlockBreakEventResult.CANCELED`. Both afterBreak methods are CONCRETE
`protected final` platform delegates with bytecode, not abstract methods.
Normal `filler()` only transitions to FINISHED in this pin and is left
unguarded so shutdown is not blocked.

Additional redirects recheck permission immediately before direct frame,
normal fluid-replacement, module-replacement and advanced column/cleanup
block writes. Advanced column BucketPickup is checked before each pickup.
These redirects suppress the call on denial; they do not roll back
previous effects or rewind the caller's local variables/iterator.

### Collection footprint follow-up

All seven pinned entity-collection call sites are now redirected. The
invocation owner is `ServerLevel` even where the method is inherited:

```text
Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;
Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;
```

| Concrete method | Overload and pinned call count |
| --- | --- |
| QuarryEntity.breakBlock(BlockPos) | Predicate overload: 3 (items, XP, minecarts); require=3 |
| AdvQuarryEntity.breakBlocks(int,int) | Predicate overload: 1 (items); require=1 |
| AdvQuarryEntity.breakBlocks(int,int) | No-predicate overload: 2 (falling blocks, minecarts); require=2 |
| AdvQuarryEntity.lambda$breakBlocks$6(ServerLevel,AABB,ExpModule) | Predicate overload: 1 (XP); require=1 |

Each redirect performs the original query with the original overload and
predicate, copies its result to a mutable ArrayList, and removes entities
for which `GuardHooks.mayCollect(BlockEntity, Entity)` is false. This leaves
denied entities out of the original conversion, XP, inventory-clearing
and removal pipelines. Main's policy must validate the entity's bounding
box projected across all touched claim chunks, its dimension and the
trusted machine owner, not only the entity center or Quarry Area.

The XP query is in a concrete private STATIC compiler lambda. A
MixinExtras WrapMethod around advanced breakBlocks scopes its machine in
a ThreadLocal for that synchronous lambda. Finally restores the previous
machine for nested column work or removes the context for the outer call.
The static redirect returns an empty mutable list if the scope is missing
or its machine belongs to a different level. No machine ownership field,
global entity hook, or change to unrelated machines is introduced.

Filtering is authorization at query return, not per subsequent effect.
An earlier consumer may trigger a callback changing claims or moving a
later approved entity before it is consumed. Converter/module effects
and such reentrant consumption remain outside this follow-up's guarantee.

`setState(<package-private enum>, BlockState): void` checks mayWork before
every server non-FINISHED transition, including WAITING. FINISHED always
remains available, as do `setRemoved` and `makeChunkUnLoaded`. No guard
attempts to transition state recursively when mayWork returns false.

Both `setArea(Area): void` methods reject server reconfiguration unless
the actual state is FINISHED or WAITING and `maySetArea` accepts the
proposal. Null level is rejected. Accepted normal Quarry changes clear
`PickIterator<BlockPos> targetIterator`, `BlockPos targetPos`, the
`Set<BlockPos> skipped` set and reset head/targetHead to the machine.
Advanced changes clear iterator/target and `boolean searchEnergyConsumed`.
Active reconfiguration is denied even when the proposed area is equal:
resetting only an active target can leave BREAK_BLOCK/REMOVE_FLUID with
invalid state prerequisites. Client area synchronization is not denied.

`maySetArea` must authorize the proposed Area, not the current Area. It
must not call setArea, tick or setState recursively. At placement,
confirmPlacement must establish trusted ownership before this hook runs.

## Network boundary

Both receivers have the exact concrete signature
`onReceive(Level, Player): void`, not an IPayloadContext parameter. Pinned
NeoForge PacketHandler dispatch passes its authenticated player after
enqueueWork. HEAD guards require a ServerPlayer, identical player/handler
level, matching packet dimension, an already-loaded machine chunk,
AdvQuarryEntity, enabled status and mayConfigure(sender, machine).
The mayConfigure implementation owns proximity and owner/bypass policy.
The explicit hasChunkAt check avoids loading a remote chunk just to
authorize a packet. No client packet-sync branch is denied.

AdvActionSyncMessage shadows exact final fields pos, dim, area, syncArea.
It rejects active state changes even for workConfig-only packets
(`syncArea == false`), since workConfig controls iterator construction.
It validates proposed/current Area before the first mutation, so a denied
area cannot merely cancel setArea and still apply workConfig. WorkConfig
is not assigned by this mixin.

The Action enum contains ONLY `QUICK_START` and `MODULE_INV`. There is no
STOP/FINISH packet action in 21.1.162. All actions require mayConfigure.
Only QUICK_START requires mayWork: a second cancellable injection precedes
the sole `PUTFIELD AdvQuarryEntity.workConfig:WorkConfig` in onReceive
(bytecode offset 157), before startQuarryWork at offset 162. This also
prevents setting startImmediately on denial. MODULE_INV remains available
to the authorized owner of a suspended machine. No inaccessible Action
enum shadow or ordinal assumption is used.

## Chunk forcing, ownership and fake players

`QuarryChunkLoader.Load.makeChunkLoaded(ServerLevel): void` shadows the
record's exact `BlockPos pos`, checks the chunk is already loaded, resolves
its BE and calls mayWork for QuarryEntity/AdvQuarryEntity before force.
An identified non-Quarry BE follows the original code. Missing BE or
unloaded position is denied: Load has no caller/owner identity, so absence
cannot safely identify an unrelated machine. This can change behavior for
other users of Load when no BE is present; caller-aware scoping would be
needed to avoid that tradeoff entirely. makeChunkUnLoaded is untouched.
Existing persistent forced chunks are not removed by this interception.
Suspending work does not itself release an existing ticket. Restart must
validate restored ownership/targets and reconcile already-forced chunks;
a successful minimal runtime load does not establish correct ticket
release or authorized work resumption.

Each concrete entity's `loadAdditional(CompoundTag,
HolderLookup.Provider): void` calls loadOwner at RETURN, after superclass
persistent-data restoration. Each saveAdditional with the same arguments
calls saveOwner at HEAD, before superclass serialization. No ownership
fields are added: main uses BE.getPersistentData(). Loading ownership
does not grant work authorization. NBT can restore active state and old
targets without setArea, so runtime validation must handle that separately.

`MiningNeoForge.getQuarryFakePlayer(QpEntity, ServerLevel, BlockPos):
ServerPlayer` has a cancellable HEAD hook for only the two Quarry types.
It calls `GuardHooks.fakePlayer(BlockEntity, ServerLevel)`. A non-null
result is pointed DOWN using the original public helper and returned;
null leaves the original implementation intact. Other machine types are
unchanged. Main must use the trusted owner's UUID with FakePlayerFactory,
not a real connected player and not the shared [QuarryPlus] identity.
This preserves FTB's fake-player recognition and per-UUID checks; it does
not globally allow fake players or bypass BreakEvent. Unknown owners MUST
be denied at every work gate; null fallback is not authorization.

## Unresolved blockers and verification required

1. Test the new approved-link execution and main's single-use confirmation.
   Item BLOCK_STATE/BLOCK_ENTITY_DATA/component application occurs between
   placeBlock and setPlacedBy. A changed facing or restored owner/active
   state must not acquire permission just because its chunks coincide.
2. Cancellation of setPlacedBy after a state/plan mismatch is after the
   physical machine placement; no zero-effects promise for that case.
   No generic rollback/refund has been implemented.
3. Intra-method reentrancy is not a transaction. A BreakEvent, loot,
   converter, module, FluidDrain or neighbor callback can mutate claims
   after one guard. Entry guards and selected write redirects reduce the
   exposed paths but do not gate every inventory/energy/entity mutation
   or every mutation inside those dependencies. Direct writes that are
   suppressed can still be followed by local target advancement.
4. The seven direct collection queries now filter by entity bounding-box
   policy, including the static advanced XP lambda. Validate claim-border
   entities, own/allied/denied claims, and scope cleanup on nested calls
   and exceptions. Effects performed after query filtering or by other
   collection paths/modules are not made transactional by these hooks.
5. Restored or malicious NBT can contain a targetIterator/targetPos that
   does not correspond to Area. setArea resets do not protect loadAdditional,
   which assigns fields directly. Validate or quarantine restored targets
   before work. Malformed NBT can fail before the ownership RETURN hook.
6. Existing forced-chunk state, moved/cloned BEs, and spoofed Load.pos are
   not an ownership migration system. Missing owner remains quarantined;
   do not infer it from the current claim or shared fake player.
7. Required remaining tests: transformed injector audit, denied/allowed
   actual placements and markers, exception/nested token cleanup, item
   components, unknown owner, own-team per-UUID fake-player rules, hostile
   packet spoofing, active configuration denial, claim mutation inside a
   repeat tick and inside callbacks, restart targets, chunk-ticket release,
   inventory/no-duplication checks and performance at the maximum footprint.

The original static verification matched all 41 unique method selector
descriptors across nine target classes against javap declarations from the
extracted pinned classes and rejected any abstract/native target. The
follow-up adds one concrete selector, lambda$breakBlocks$6, for 42 total;
its static declaration and full descriptor were checked with javap. Exact
call-site counts checked for the follow-up were 1+1 Stream.findAny and
3+1+2+1 ServerLevel.getEntitiesOfClass. Additional
invocation targets and the QUICK_START PUTFIELD were cross-checked against
their method bytecode. Java compilation alone does not verify Mixin
transformation, cancellation placement or behavior in the complete modpack.
The main task owns runtime/smoke testing and all FTB index/policy code.

## Evidence

Primary local reference: `refonte-ascendant-2026-09-04/quarry/rapport-quarryguard.md`
and its `evidence/quarry`, `evidence/raw-quarry`, `evidence/minecraft` and
`evidence/raw-minecraft` directories. The original QuarryPlus SHA-256 is
`629157D43F7C966B7E3F04400B57F5974084E2FD483816FD33A792C8A97244CC`.
Concrete signatures and invocation owners are taken from those class files,
not guessed from generic Minecraft mappings or a rolling source branch.

The exact upstream tag was also consulted for work/iterator flow:
[QuarryEntity v21.1.162](https://raw.githubusercontent.com/Kotori316/QuarryPlus/v21.1.162/common/src/main/java/com/yogpc/qp/machine/quarry/QuarryEntity.java)
and [AdvQuarryEntity v21.1.162](https://raw.githubusercontent.com/Kotori316/QuarryPlus/v21.1.162/common/src/main/java/com/yogpc/qp/machine/advquarry/AdvQuarryEntity.java).
The local bytecode remains authoritative for this deliverable.
