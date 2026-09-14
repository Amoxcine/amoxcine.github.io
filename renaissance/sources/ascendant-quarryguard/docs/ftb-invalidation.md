# FTB Claim Index Synchronization and Live Authorization

Audit: 2026-09-04. Exact NeoForge binaries: Chunks 2101.1.21, Teams 2101.1.10, Library 2101.1.35. Scope: research only; no integration/core changes.

## Decision

**Approve the proposed cache shape:** immutable geometric coverage `Set<UUID>` per dimension + inclusive chunk rectangle + index revision + manager generation. Cache no allow/deny, rank, ally status, bypass, or BLOCK_EDIT value. Reevaluate live policy on every `mayWork`, immediately before work. Therefore team membership/alliance/property/privilege changes need **no QuarryGuard permission-cache invalidation hooks**.

**Minimal claim-mutation interception: three method targets**, all `RETURN`, plus lifecycle readiness/identity checks below. They cover all writes to the private claim map and the mutable owner reference found in these binaries. Public claim events alone are incomplete.

## Exact Minimal Targets

Use `remap = false` for FTB targets/selectors and `require = 1`. All are public, return void, and have one normal return in this version.

| Target class | Exact Mixin method selector | Action after normal return |
|---|---|---|
| `dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl` | `registerClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;Ldev/ftb/mods/ftbchunks/api/ClaimedChunk;)V` | Re-read `this.getChunk(pos)`; replace index entry with actual current claim identity/team ID, or remove if absent; increment revision. |
| Same | `unregisterClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;)V` | Remove/reconcile index entry for `pos`; increment revision. |
| `dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl` | `setTeamData(Ldev/ftb/mods/ftbchunks/data/ChunkTeamDataImpl;)V` | Reconcile current owner at `this.getPos()`; increment revision even if coordinates are unchanged. |

Evidence: freshly extracted [manager bytecode](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/ftb-evidence/dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl.txt), [claim bytecode](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/ftb-evidence/dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl.txt). In-method offsets: register map put = 18, return = 24; unregister map remove = 5, return = 11; transfer teamData field write = 13, return = 16. The two calls before that owner write only clear old/new team claim caches; their bodies assign two null fields and return. No event dispatch or policy callback occurs inside these three mutators in the audited binaries. [Pinned upstream manager source](https://raw.githubusercontent.com/FTBTeam/FTB-Chunks/v2101.1.21/common/src/main/java/dev/ftb/mods/ftbchunks/data/ClaimedChunkManagerImpl.java).

Important implementation details:

- `registerClaim` silently ignores a `ClaimedChunk` that is not `ClaimedChunkImpl`. Do not blindly index its argument. Reading the post-mutation manager entry handles ignored calls and replacement at an existing position.
- `setTeamData` can run on an object no longer registered. Reconcile from the active manager, not blindly from this object's new owner. Only update a live index associated with that manager; otherwise mark dirty for its next rebuild.
- The cache key must include a nonreused generation or actual manager identity, not revision alone. Clearing/recreating an index must not accidentally make an old revision valid again.
- A UUID-to-ChunkTeamData reference map must refresh on ownership reassignment as well as claim insertion. Validate `data.getTeam()` by reference against the current `TeamManager.getTeamByID(id)` result, or fail closed; same-ID replacement during a same-manager team reload can otherwise retain old policy. If distinct claim data objects disagree for one UUID, do not authorize all claims from one arbitrarily selected reference.
- Incrementing revision on no-op calls is safe. Exact change detection is optional.
- If an index hook fails after FTB mutation, leave the index dirty/unready and deny. Never catch the failure and keep serving the previously valid snapshot.
- Server-thread only: no iteration or mutation on asynchronous workers. If a foreign mod transforms these bodies to add callbacks, this no-callback proof no longer applies.

### Minimal Handler Signatures

These are exact Java/Mixin handler parameter types; hook bodies belong to the implementation owner.

```java
// In @Mixin(value = ClaimedChunkManagerImpl.class, remap = false):
@Inject(method = "registerClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;Ldev/ftb/mods/ftbchunks/api/ClaimedChunk;)V",
        at = @At("RETURN"), require = 1, remap = false)
private void quarryguard$registered(ChunkDimPos pos, ClaimedChunk ignored,
                                   CallbackInfo ci) { /* reconcile pos */ }

@Inject(method = "unregisterClaim(Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;)V",
        at = @At("RETURN"), require = 1, remap = false)
private void quarryguard$unregistered(ChunkDimPos pos,
                                     CallbackInfo ci) { /* reconcile pos */ }

// In @Mixin(value = ClaimedChunkImpl.class, remap = false):
@Inject(method = "setTeamData(Ldev/ftb/mods/ftbchunks/data/ChunkTeamDataImpl;)V",
        at = @At("RETURN"), require = 1, remap = false)
private void quarryguard$transferred(ChunkTeamDataImpl newData,
                                     CallbackInfo ci) { /* reconcile getPos() */ }
```

Imports: `dev.ftb.mods.ftblibrary.math.ChunkDimPos`, `dev.ftb.mods.ftbchunks.api.ClaimedChunk`, `dev.ftb.mods.ftbchunks.data.{ClaimedChunkManagerImpl,ClaimedChunkImpl,ChunkTeamDataImpl}` (individual Java imports), `org.spongepowered.asm.mixin.Mixin`, `org.spongepowered.asm.mixin.injection.{At,Inject}`, `org.spongepowered.asm.mixin.injection.callback.CallbackInfo`. Signatures are bytecode-verified; a full mod compilation/Mixin application test was not run.

## Lifecycle and Completeness

**Startup-only minimum compatible with main's proposal:** set readiness only at NeoForge `net.neoforged.neoforge.event.server.ServerStartedEvent`, after building a complete snapshot; clear it on `ServerStoppingEvent`/`ServerStoppedEvent`. Every `mayWork` must check readiness, `FTBChunksAPI.api().isManagerLoaded()`, and reference identity `FTBChunksAPI.api().getManager() == indexedManager`. A null/unavailable/replaced manager is NOT wilderness: deny, drop coverage caches, and bootstrap the new generation only at a proven complete boundary. A replacement manager is not automatically ready just because it exists.

No constructor hook is necessary with this fail-closed barrier. A constructor-return snapshot is too early: the manager constructor creates empty maps; teams and claims load later. Exact optional FTB lifecycle selectors:
- `ClaimedChunkManagerImpl.init(Ldev/ftb/mods/ftbteams/api/TeamManager;)V`: static; RETURN invalidates prior generation, leaves unready. This assigns a newly constructed singleton.
- `ClaimedChunkManagerImpl.shutdown()V`: static; HEAD clears readiness/index before singleton nulling.
- `dev.ftb.mods.ftbteams.data.TeamManagerImpl.load()V`: HEAD enters a loading barrier; **every normal RETURN**, including early returns, exits loading and rebuilds before marking ready. An exceptional exit must remain unready. For automatically recovering from exceptions, use a genuine try/finally wrapper; HEAD+RETURN is not finally.

**For complete hot-load/reload support**, add the `TeamManagerImpl.load()V` barrier above; do not claim that a once-only ServerStarted flag covers a later same-manager load. The optional `init`/`shutdown` hooks close intra-lifecycle/reentrant windows; ordinary tick-time identity checks already detect their completed effects. If these extra hooks are omitted, unexpected replacement/reload must stay denied until explicit reinitialization, not silently rebuild an incompletely loaded manager.

Bootstrapping on the server thread:
1. Enter unready/building state and invalidate old rectangle coverage.
2. At ServerStarted or a successful team-load completion, ensure every current `TeamManager.getTeams()` entry has `claimedManager.getOrCreateData(team)`. This is startup/recovery work, not a per-tick scan; it also avoids depending on ordering of individual team-load listeners.
3. Build from `claimedManager.getAllClaimedChunks()` into a private new index, then publish atomically with a new generation/revision. Recheck manager identity and any mutation revision before publishing.
4. On query, a stale/unready/dirty index cannot grant work. Rebuild synchronously from the authoritative map or deny until a known complete boundary.

Coverage proof against audited [team-data bytecode](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/ftb-evidence/dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl.txt):
- Ordinary claims and SNBT loads both call `registerClaim`. `deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V` registers each loaded claim at offset 170, without AFTER_CLAIM.
- Ordinary unclaims, administrative/direct `ClaimedChunkImpl.unclaim(CommandSourceStack, boolean)`, and transfer overflow removals eventually call `unregisterClaim`.
- Party joining/leaving transfers claim ownership through `setTeamData`, not register/unregister. Team-owner transfer of a party keeps its team UUID: no geometric update is required; live rank policy sees the owner change.
- `ClaimedChunkImpl.unload(CommandSourceStack)` means **remove force-loading**, not unclaim. It does not remove protection. Minecraft chunk/dimension unload likewise does not imply unclaim; keep unloaded claims indexed.
- `ClaimedChunkManagerImpl.deleteTeam(Team)` removes team-data references/files, not the claim map. Mirror the map; do not invent implicit unclaims. Missing/invalid live teams should deny.
- `ChunkTeamDataImpl.deserializeNBT` does not remove previously registered positions omitted by a later NBT blob. Resync to FTB's actual map, not an assumed file replacement semantics.
- The claim map is private; `getAllClaimedChunks()` returns an unmodifiable **live view**, not a snapshot. All audited map writes are register/unregister plus initial construction. Reflective writes or other mods' Mixins are outside this proof.

There is no demonstrated general-purpose, atomic FTB claim reload API here. Calling `TeamManagerImpl.load()` on an existing instance is not proven equivalent to shutdown/recreate: existing maps are not comprehensively cleared. Treat canonical-team/claim-team identity inconsistencies as a denied/unready state, not as permission to repair FTB state. [Exact Teams lifecycle source](https://raw.githubusercontent.com/FTBTeam/FTB-Teams/v2101.1.10/common/src/main/java/dev/ftb/mods/ftbteams/FTBTeams.java).

### Dirty/Rebuild Alternative

A global `markClaimsDirty` with lazy rebuild is safe under the same barriers. On these three callback-free mutators, **RETURN dirty/revision bump** is the simplest option. A HEAD-only bump is unsafe if a nested query can rebuild before the mutation and then reuse that revision afterward.

For future callback-containing mutators: enter a depth-counted mutation barrier, invalidate before mutation, execute original in try/finally, invalidate again and leave barrier in finally. Queries during depth > 0 deny. Never let a finally block mark ready after a failed/partial load. Incremental updates avoid an O(total claims) rebuild after every claim edit but still invalidate geometric coverage; choose deliberately.

## Public Source-of-Truth API

Verified public signatures; no accessor Mixin required for enumeration:

```java
// ClaimedChunkManager:
Collection<? extends ClaimedChunk> getAllClaimedChunks();
ClaimedChunk getChunk(ChunkDimPos pos); // nullable
boolean getBypassProtection(UUID playerId);
ChunkTeamData getOrCreateData(Team team);

// ClaimedChunk:
ChunkDimPos getPos();
ChunkTeamData getTeamData();

// ChunkTeamData:
Team getTeam();
boolean isTeamMember(UUID playerId);
boolean isAlly(UUID playerId);
boolean canPlayerUse(ServerPlayer player, PrivacyProperty property);

// Team:
UUID getId();
TeamRank getRankForPlayer(UUID playerId);
<T> T getProperty(TeamProperty<T> property);

// ChunkDimPos is a Library record:
ResourceKey<Level> dimension();
int x();
int z();
ChunkPos chunkPos();
```

Enumeration:
```java
for (ClaimedChunk chunk : manager.getAllClaimedChunks()) {
    ChunkDimPos p = chunk.getPos();
    UUID claimTeamId = chunk.getTeamData().getTeam().getId();
    // Index (p.dimension(), p.x(), p.z()) -> claimTeamId.
}
```

**Do not use `ChunkTeamData.getTeamId()`: it is not in that interface.** `ChunkTeamDataImpl.getTeamId()Ljava/util/UUID;` is public on the implementation and delegates to `Team.getId()`. Also do not substitute `Team.getTeamId()`: `PlayerTeam` overrides it to return its effective party team's ID, unlike stable `getId()`.

Erased JVM descriptors: manager enumeration `()Ljava/util/Collection;`; claim position `()Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;`; claim team-data `()Ldev/ftb/mods/ftbchunks/api/ChunkTeamData;`; team-data team `()Ldev/ftb/mods/ftbteams/api/Team;`; dimension `()Lnet/minecraft/resources/ResourceKey;`; x/z `()I`. Concrete implementations also contain covariant-return bridges: do not target the wrong bridge.

## Live BLOCK_EDIT Without Public Enemies

Exact property: `FTBChunksProperties.BLOCK_EDIT_MODE` (PrivacyProperty). The protection constant is `Protection.EDIT_BLOCK`, not BLOCK_EDIT. Real-player semantics in `ChunkTeamDataImpl.canPlayerUse`: PUBLIC allows anyone; ALLIES calls `isAlly(UUID)`; PRIVATE requires `team.getRankForPlayer(UUID).isMemberOrBetter()`. There is no separate MEMBERS enum: members/officers/owner qualify for PRIVATE.

Use a **relation restriction AND FTB policy**, with explicitly enabled bypass as a separate exception:
- Resolve the actual persisted quarry owner's UUID, never the machine's shared fake-player UUID, player name, team UUID, or currently interacting player.
- Resolve each cached claim-team UUID against the live Teams manager using `getTeamByID(UUID)`. Missing, invalid, or mismatched team-data means deny/rebuild, not skip.
- Require a member-or-better rank or an explicit ALLY rank in the **claim owner's** team. Alliances are directional and player-ranked; do not reverse the relationship or infer alliance from a teammate.
- Then require live BLOCK_EDIT policy. A PRIVATE allied claim still denies. PUBLIC still needs the relation restriction, so unrelated/enemy teams never pass.
- `ChunkTeamData.isAlly` respects `ALLY_MODE`: FORCED_ALL permits everybody, FORCED_NONE denies nonmembers. Using it alone as the relation restriction would admit enemies under FORCED_ALL. Explicit rank restriction prevents that while the separate live FTB policy preserves FORCED_NONE.
- Read `manager.getBypassProtection(realOwnerUuid)` every authorization. This reads the player's **personal** team extra NBT `BypassFTBChunksProtection`; it is not equivalent to operator status. Never cache a previously enabled bypass. Do not grant bypass merely because the owner is op.
- Prefer a real, current ServerPlayer when using `canPlayerUse`; passing a fake player follows additional fake-player settings. Without an online real player, use UUID/rank/property logic below for this claim policy only, or deny if additional player-dependent protections cannot be evaluated.

Compile-ready claim-policy helper (caller handles manager readiness, fresh team resolution, and owner provenance):
```java
import java.util.UUID;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;

final class LiveClaimPolicy {
    static boolean allows(ClaimedChunkManager manager, ChunkTeamData data,
                          UUID realOwner) {
        if (realOwner == null || realOwner.equals(new UUID(0L, 0L))) return false;
        Team team = data.getTeam();
        if (!team.isValid()) return false;
        if (manager.getBypassProtection(realOwner)) return true;
        TeamRank rank = team.getRankForPlayer(realOwner);
        boolean member = rank.isMemberOrBetter();
        if (!member && rank != TeamRank.ALLY) return false;
        PrivacyMode mode = team.getProperty(FTBChunksProperties.BLOCK_EDIT_MODE);
        if (mode == PrivacyMode.PRIVATE) return member;
        if (mode == PrivacyMode.ALLIES) return data.isAlly(realOwner);
        return mode == PrivacyMode.PUBLIC;
    }
}
```

This intentionally implements a stricter QuarryGuard claim policy, not every branch of FTB's general interaction pipeline. `DISABLE_PROTECTION`, spectator allowances, fake-player whitelist/global overrides, and block/item whitelist policies must not bypass the enemy restriction. Leave normal block-level protections in place. The generic `shouldPreventInteraction` returns false for non-ServerPlayer actors and uses the actor's dimension, so null actor/fake UUID/wrong-dimension probes are not authorization evidence. [Pinned policy source](https://raw.githubusercontent.com/FTBTeam/FTB-Chunks/v2101.1.21/common/src/main/java/dev/ftb/mods/ftbchunks/data/ChunkTeamDataImpl.java), [local protection bytecode](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/chunks/dev.ftb.mods.ftbchunks.api.Protection.txt).

**Wilderness limitation:** a Set<team UUID> alone cannot tell whether part of the rectangle is unclaimed. If respecting FTB `no_wilderness`, additionally cache geometric claimed-count/full-coverage (union rectangles correctly), and evaluate the current player/dimension permission live. `FTBChunksWorldConfig.noWilderness(ServerPlayer)Z` calls dimension rules and `PermissionsHelper.getNoWilderness(ServerPlayer,boolean)Z`, which reads `ftbchunks.no_wilderness` from the current Library permission provider. Do not claim full FTB authorization from team IDs alone. Do not admit a partially claimed rectangle merely because its known claim owners pass.

## Events and Why Rights Hooks Are Unnecessary

| Event/path | Exact phase / limitation |
|---|---|
| `ClaimedChunkEvent.BEFORE_CLAIM` | Before registration; cancellable; simulation/failure may never mutate. Not an index update. |
| `AFTER_CLAIM` | After register in normal `ChunkTeamDataImpl.claim`; not emitted by SNBT register or raw register calls. |
| `BEFORE_UNCLAIM` | Wrapper-level cancellable preflight; direct `ClaimedChunkImpl.unclaim` does not need that wrapper. |
| `AFTER_UNCLAIM` | After unregister inside `ClaimedChunkImpl.unclaim`; raw unregister bypasses event. |
| Force-load/unforce-load events | Change loading state, not claim occupancy. |
| `TeamManagerEvent.CREATED` | Singleton Teams manager exists, before `load()`; FTB Chunks creates its empty manager here. |
| `TeamManagerEvent.LOADED` | Inside `load()`, after manager metadata but BEFORE individual team files/membership reconstruction; may be skipped for missing files. Not a ready event. |
| `TeamEvent.LOADED` | Dispatched at the end of AbstractTeam.deserializeNBT, including built-in NBT edits; FTB Chunks listener loads its claim data. PartyTeam sets its owner AFTER this superclass/event returns. Neither listener order nor this event is a final subclass/global completion guarantee. |
| `TeamManagerEvent.DESTROYED` | Dispatched before Teams singleton is nulled; FTB Chunks listener shuts its singleton down. |
| `TeamEvent.PLAYER_CHANGED` then JOINED/LEFT_PARTY | After membership/effective-team edits; FTB Chunks transfers claims inside party event listeners. A listener firing first cannot assume transfers finished. JOINED is omitted if no ServerPlayer. |
| `TeamEvent.ADD_ALLY`, `REMOVE_ALLY`, `OWNERSHIP_TRANSFERRED` | After normal command-side rank/owner writes; not a universal lower-level write notification. |
| `TeamEvent.PROPERTIES_CHANGED` | Emitted by settings/updatePropertiesFrom after changes; raw setProperty/property-value/NBT writes can bypass it. |

Event callback types: `ClaimedChunkEvent.Before.before(CommandSourceStack, ClaimedChunk)` returns `CompoundEventResult<ClaimResult>`; `After.after(CommandSourceStack, ClaimedChunk)` returns void. Team events are Architectury `Event<Consumer<...>>`, not NeoForge event-bus classes.

With **no rights-result cache**, direct writes below are reflected at the next policy read; there is no need to intercept them. If a future implementation caches authorization, this is the minimum audit warning list, **not a certified complete cache invalidator**:

| Actual write location | Exact selector(s) / gap |
|---|---|
| `AbstractTeamBase` membership API | `addMember(Ljava/util/UUID;Ldev/ftb/mods/ftbteams/api/TeamRank;)V`, `removeMember(Ljava/util/UUID;)V`, `invalidateTeam()V`. Party commands often bypass these. |
| `PartyTeam` direct ranks writes | `join(Lnet/minecraft/server/level/ServerPlayer;Lcom/mojang/authlib/GameProfile;)I`, `kick(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;)I`, `leave(Ljava/util/UUID;)I`, `promote(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/Collection;)I`, `demote(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/Collection;)I`, `addAlly(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;)I`, `removeAlly(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;)I`. Invitations also directly change ranks in a lambda. |
| `PartyTeam` owner | `transferOwnership(Lnet/minecraft/commands/CommandSourceStack;Lcom/mojang/authlib/GameProfile;)I`; also deserialize/creation field writes. Collection overload delegates. |
| `PlayerTeam` effective identity | `setEffectiveTeam(Ldev/ftb/mods/ftbteams/data/AbstractTeam;)V`. |
| Properties | `AbstractTeamBase.setProperty(Ldev/ftb/mods/ftbteams/api/property/TeamProperty;Ljava/lang/Object;)V`; deeper `TeamPropertyCollectionImpl.set(Ldev/ftb/mods/ftbteams/api/property/TeamProperty;Ljava/lang/Object;)V`, `updateFrom(Ldev/ftb/mods/ftbteams/api/property/TeamPropertyCollection;)V`, `read(Lnet/minecraft/nbt/CompoundTag;)V`, `collectProperties()V`; `TeamPropertyValue.setValue(Ljava/lang/Object;)V`. |
| NBT edits/reload | `AbstractTeam.deserializeNBT(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V` changes ranks/properties/extra data; PartyTeam override sets owner AFTER super returns. Built-in NBT editor invokes this path. |
| Bypass | `ClaimedChunkManagerImpl.setBypassProtection(Ljava/util/UUID;Z)V`; callers can also mutate the public returned extra `CompoundTag` directly, without that method or events. |
| World config | `FTBChunksWorldConfig.onConfigChanged(Z)V` is the normal post-config callback, not every write. Library `dev.ftb.mods.ftblibrary.snbt.config.BaseValue.set(Ljava/lang/Object;)V` is the direct stored-value mutator. Mutable lists can change in place. |
| External privileges | Library `PermissionProvider.getBooleanPermission(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;Z)Z` can change results independently of these FTB classes. Provider implementation/rank reload/op changes require their own audit if cached. |

A global revision bumped only from markDirty/events/addMember is therefore insufficient for complete rights caching. A global claim revision is sufficient for **geometry** when driven by the three exact claim mutations and lifecycle generations. [Pinned party implementation](https://raw.githubusercontent.com/FTBTeam/FTB-Teams/v2101.1.10/common/src/main/java/dev/ftb/mods/ftbteams/data/PartyTeam.java).

## Cost and Validation

Let C be total indexed claims, A the number of chunk cells in a quarry rectangle, k the intersecting claims, t the distinct intersecting claim-team UUIDs (t <= k).
- Startup/rebuild from the public collection: O(C), memory O(C).
- Per rectangle, cache miss: a simple sparse map scan is O(C), **not O(k)**; an actual orthogonal range index can reduce discovery cost, e.g. O(log^2 C + k) with a suitable range tree. Do not describe a hash map with a full scan as an output-sensitive range query.
- Warm `mayWork`: O(1 + t) expected map/rank/property operations, bounded by O(1 + k), with no A loop. Evaluate teams live even at an unchanged revision. With no claims this remains O(1), not literally O(0).
- Claim churn invalidates coverage. A global lazy rebuild can cost O(C) plus rectangle-query work after every change; the O(k) statement applies to warm authorization, not every tick under adversarial claim churn. Rights changes require no geometry rebuild.
- Prefer `team.getRankForPlayer(uuid).isMemberOrBetter()` over `data.isTeamMember(uuid)` in the hot loop: the latter uses `team.getMembers()`, and the common implementation constructs/filters a members map, adding O(team size). The provided helper follows the real-player PRIVATE branch without that scan.
- Same-thread immediate work is required; recheck the generation/revision before consuming coverage. Do not keep an authorization result across yielded tasks or arbitrary callbacks.

Required integration tests before shipping: cancelled/simulated claim; raw register/unregister; same-position replacement; ignored non-Impl registration; direct unclaim; preexisting unloaded claims at startup; join/leave owner reassignment without claim events; explicit owner transfer; manager shutdown/replacement; later same-manager load; PUBLIC enemy denial; PRIVATE ally denial; member kick and ally removal between two mayWork calls with unchanged geometry; bypass revoke; FORCED_ALL/FORCED_NONE; negative chunk coordinates; unclaimed gaps/no_wilderness; missing team/owner; mutation/index failure denies.

## Evidence and Remaining Limits

Original jars were read-only. SHA-256:
- Chunks: `2CC32A613DD04D9355B88E6F455235AAF00768C270489657C9949F23B9195588`
- Teams: `A656F2AA0E11A933AAB3BA697690AA0EB2041F65E6C5DAA448897EE0A6D37A6C`
- Library: `BE7B2BD0B5370A9951624A1D84FD802EFECAE88D5A0CD24552731F887430599D`

Existing API/event evidence: [ClaimedChunkManager](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/chunks/dev.ftb.mods.ftbchunks.api.ClaimedChunkManager.txt), [ChunkTeamData](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/chunks/dev.ftb.mods.ftbchunks.api.ChunkTeamData.txt), [FTBChunksProperties](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/refonte-ascendant-2026-09-04/quarry/evidence/chunks/dev.ftb.mods.ftbchunks.api.FTBChunksProperties.txt), [TeamManager load](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/ftb-evidence/dev.ftb.mods.ftbteams.data.TeamManagerImpl.txt), [Library coordinates](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/ftb-evidence/dev.ftb.mods.ftblibrary.math.ChunkDimPos.txt), [Library BaseValue](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/ftb-evidence/dev.ftb.mods.ftblibrary.snbt.config.BaseValue.txt), [permission delegation](C:/Users/avets/.codex/.chatgpt-projects/g-p-6a767ae879f881919b1fc768a5c811f7/quarryguard-lab/research/ftb-evidence/dev.ftb.mods.ftbchunks.integration.PermissionsHelper.txt).

Additional classes were extracted directly from these original jars and inspected with `javap -p -s -c`; the reproducible collector and extracted classes/text are confined to ftb-evidence. Pinned primary Chunks/Teams sources corroborate the bytecode. The exact Library source tag could not be fetched, so Library signature claims rely on the installed binary, not an assumed newer branch. No runtime server, Mixin apply test, benchmark, or full compilation was performed. External permission-provider internals and other mods' transformations are not certified. These are explicit verification gaps, not reasons to cache permissions speculatively.
