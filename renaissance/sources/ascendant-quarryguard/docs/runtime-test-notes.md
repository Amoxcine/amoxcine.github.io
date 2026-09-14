# QuarryGuard In-Engine Lab Checks

Entry point: `public static String LabChecks.run(MinecraftServer server)`. Caller invokes synchronously on the server thread after ServerStarted and prints the returned summary. Assertions use explicit exceptions, independent of `-ea`; a failure throws with completed stages and the underlying cause. Runtime results below are attributed to their executor; no server was launched by this subtask.

## Required Setup

- `-Dascendant.quarryguard.lab=true`; bind exactly `127.0.0.1`; no connected players.
- World real path must end in `quarryguard-lab/runtime/quarryguard-lab-world`, matching the prepared runtime. Flat generator required. Production paths are rejected before fixture writes.
- QuarryGuard must already report `ready=true`; the test does not call GuardHooks.start or manually update its index, which would hide missing lifecycle/mutation hooks.
- Exact pinned FTB/QuarryPlus dependencies and the main integration Mixins must be loaded. Main owns console-command registration/build/deployment. This file does not register commands or launch a server.
- Four fixture blocks at (100,64,100), (100,64,101), and their supports must initially be air without block entities. Chunks x/z 6..10 must initially be unclaimed. Existing fixtures/claims cause a setup failure, never automatic destructive clearing.
- FTB must allow claiming in the overworld. Explicit allies must be usable (`ALLY_MODE` must not force none). Test-team extra claim allowance is set to 64; no global config changes.

## Expected Assertions

Both `quarryplus:quarry` and `quarryplus:adv_quarry` use actual `BlockItem.place(BlockPlaceContext)`, survival item stacks, and a ServerPlayer (not FakePlayer) with a silent, unconnected test listener, never added to the player list or level. Native chunk-marker APIs create a large footprint; chunk (8,8) is strictly interior, distinct from the machine/corners.

1. Offline FTB personal teams are created by `TeamManagerImpl.playerLoggedIn(null, uuid, name)`; a real FTB party is created by `createParty(uuid, null, name, null, null)`. Public native APIs only; no reflection or FTB map injection.
2. Claim simulation succeeds without registering anything. Actual hostile interior claim rejects native placement before item consumption, machine/block creation, support change, or marker removal. Simulated unclaim preserves the original claim object.
3. Wilderness native placement succeeds, consumes exactly one item, adopts the marker area, and persists the real owner's UUID. Warm `mayWork` succeeds. A subsequent hostile claim makes it false without manual cache invalidation.
4. `ClaimedChunkImpl.setTeamData` to personal/hostile data changes authorization immediately, including a restored machine. Explicit bypass grants and its revocation denies.
5. Native `saveWithFullMetadata(registries)` and `loadWithComponents(tag, registries)` restore owner/area onto a fresh, unattached block entity with the same level/position/state. It must pass `mayWork` under the owner's PRIVATE claim. Removing the owner tag must deny. No direct GuardHooks save/load calls stand in for native serialization.
6. On unchanged claim geometry/revision: PUBLIC enemy denied; explicit ally PUBLIC allowed; PRIVATE ally denied; ALLIES ally allowed; remove ally denied; PRIVATE member allowed; remove member denied. Status metrics must show no new geometry query or revision during these rights-only changes. Native unclaim resumes work.
7. Native placement on personal, party-member, and allied claims succeeds. FakePlayer with the very same owner's UUID must still be rejected before item consumption.
8. Normal authorized quarry only: 5,000 warm-up and 10,000 timed real `GuardHooks.mayWork` calls with one PRIVATE personal interior claim. Every call must allow and timed calls must not rebuild geometry. Sorted samples return nearest-rank p50/p95/p99 in nanoseconds. This is warm authorization latency, not ticks/MSPT, throughput, a large-claim benchmark, or a before/after performance result. Fixture geometry is fixed; unique random UUIDs isolate runs and are not a seeded workload generator.

## Cleanup and Limits

Finally resets the test owner's bypass, removes only the test-owned interior claim, restores the four fixture blocks, and clears placement scope. Native placement stores marker drops in the quarry's storage; block-removal callbacks may drop items into the scratch world. Such dropped items and offline test teams/team metadata remain intentionally in the disposable lab world; no production world or connected users are touched. Failed cleanup throws or is attached as a suppressed exception. Run on a fresh disposable lab world if interrupted fixtures remain.

No mining ticks, powered machine actions, actual socket login, world restart, hot team-manager reload, cancelled third-party events, marker GUI packets, or 20,000-claim workload are covered. The persistence check is an in-process NBT round-trip, not a restart. PARTY JOIN/LEFT transfer events are not synthesized: ownership synchronization is exercised directly through the actual `setTeamData` mutator. Native FTB claim/unclaim and ally commands may expose additional integration prerequisites at first execution; such errors are test failures, not silently skipped passes.

Compilation status: PASSED, Java 21 (`javac --release 21 -proc:none`) against the integration/core sources present at that compilation and the installed dependency classpath. Classes/argument file were written only to a temporary compiler-output directory; the build/deployment output was not replaced. Initial sandboxed compilation failed on dependency ZIP-resource close permissions, then the approved unrestricted compile exited 0 with deprecation notes only. Runtime status: NOT RUN by this subtask; only the console run may produce a PASS summary or measured percentiles.

## Initial Handoff

Main reports a restored-targetPos check, approvedLink, and mayCollect. Existing placement/NBT assertions exercise their normal paths only where invoked naturally; they do not explicitly test a forged/out-of-area targetPos, approvedLink reuse/substitution, or mayCollect denial. No additional test scenarios have been added. Main owns deployment and runtime execution.

## First Runtime and Fixture Correction

Main's first run, independently confirmed in runtime/logs/latest.log at 2026-09-04 14:15:22, completed every normal-quarry stage and the advanced hostile-interior rejection stage. The overall run FAILED during the first legal advanced placement: PacketDistributor.sendToPlayer -> PacketHandler.sendToClientPlayer -> AdvQuarryBlock.setPlacedBy:142 dereferenced a null player.connection. It is not an advanced-suite pass. The normal-quarry warm samples from this partial run were p50=500ns, p95=700ns, p99=2300ns; these are authorization timings only, not MSPT or a completed-suite result.

User-authorized fixture-only correction: SilentLabListener extends ServerGamePacketListenerImpl, constructed with an unopened Connection(PacketFlow.SERVERBOUND) and CommonListenerCookie.createInitial(profile, false). Both send(Packet<?>) and send(Packet<?>, PacketSendListener) discard outbound packets without queuing or invoking transport callbacks. No socket, channel initialization, login, server-connection registration, or listener tick is used. The test ServerPlayer overrides getTextFilter() with TextFilter.DUMMY because the native listener constructor calls getTextFilter().join(); this avoids external filtering work. Actor still is not a FakePlayer, and native BlockItem.place, all assertions, and production hooks remain unchanged.

Constructors, both send overloads, getConnection/isConnected, and TextFilter.DUMMY/getTextFilter were verified with javap against the installed Minecraft 1.21.1 / NeoForge 21.1.248 jars. Correction compilation: PASSED (Java 21, exit 0, deprecation notes only), including the current integration/core sources, with output confined to a temporary directory. Corrected runtime: NOT RUN by this subtask; main must rebuild/deploy and rerun. Correction delivered; no modifications remain in progress.

## Final Status: Main-Executed PASS

Main reports the latest compilation PASSED and the corrected runtime achieved global PASS on 2026-09-04 at 14:18:50, with all normal and advanced quarry assertions passing. Evidence path supplied by main: `results/runtime-20260904-121838.log` (not independently reread for this final update). This supersedes the pending rerun status above. Clean shutdown: exit 0.

Main-reported warm authorization: p50=500ns, p95=1000ns, p99=1700ns. Final status: revision=22, geometryQueries=48, hits=15024, checks=15074, denied=18, meanCheckUs=0.933. Percentiles measure the documented normal-quarry authorization calls only, not ticks/MSPT. Coverage limits above still apply. No further code changes, compilation, research, or runtime execution by this subtask; delivery complete.
