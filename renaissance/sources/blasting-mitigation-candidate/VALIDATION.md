# Delivery and Validation

Final guard artifact:
`build/20260913222651273/ascendant-etrionic-guard-0.0.1-candidate.jar`

SHA256:
`097DE85FB38B7ACDB9F5E834B47D8AD5D861197E25D6AB5F3454F85C46DE232E`

## Completed Here

- Java 21 compile against pinned Ad Astra 1.16.24 and current local dependencies.
- 94 pure policy, actual pinned native call-shape and compiled Mixin bytecode
  assertions: PASS. Exact native targets exist once; the native blasting craft
  call site is in recipeTick; separate alloying dispatch remains distinct.
- Exactly two cancellable HEAD hooks, each required once and allowed once.
- Mixin bytecode has no input/output/FE/cache field writes or mutation calls;
  only active/mode reads, policy evaluation, diagnostic counters and cancellation.
- Default config is false; runtime opt-in is latched at server startup. Enabled
  startup requires exact loaded Ad Astra SHA256. OP4 status/inspect are read-only.
- No Ad Astra upgrade, recipe change, old fixture mutation, other-candidate edit,
  installation, Minecraft launch or world modification by this worker.

The first build test invocation had a Windows argument-file path escaping error;
it was rejected. The final build above completed all checks. Intermediate build
folders are not the selected delivery.

## Native Status

Parent reports `NATIVE222504-001` with the separate corrected QA helper
`DD08AA23744347EDC2092F3D20D13446F69BAB8F0994E2D7C346F72AACD2E423`:
Moon (2,220,2), COMPLETE_PASS=true; 1 Mekanism steel/8,000 FE remaining;
2 Create brass/2,000 FE remaining; poweredObserved and powerLossPASS true with
40 mB oxygen remaining; compression and six plate ingredient paths accepted.
The target chunk was released. This confirms the corrected baseline fixture,
**not this newly delivered guard's native Mixin application or mode veto**.

The earlier journal `1d8ca1c9-a299-43f0-9046-e8d54658819e` remains a partial result
with COMPLETE_PASS=false due to its former hardcoded pre-unification steel item
assertion. It was not edited, restarted or reseeded.

Final parent run `223347-602`: active=true; fresh BLASTING fixture preserved all
four raw-iron inputs and 10,000 FE over 159 ticks with 159 vetoes,
BLASTING_VETO_PASS=true. Fresh ALLOYING/habitat fixture in the same session:
COMPLETE_PASS=true. Fifteen strict steps passed, all 18 force-loads released,
cleanExit=true and forced=false. Evidence was inspected read-only at closeout.

The narrow isolated-RC mitigation and native nonregression gate are demonstrated.
Saved-state/reload edge cases and real-client GUI switching are not claimed as
tested by this run. Default-OFF elsewhere remains intentional. Full source paths,
hashes, resource checks and limits: `../NATIVE_QUALIFICATION_FINAL.md`.
