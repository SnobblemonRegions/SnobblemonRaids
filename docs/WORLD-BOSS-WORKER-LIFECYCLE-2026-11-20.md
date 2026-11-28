# Simulator worker lifecycle hardening — 2026-11-20

The gameplay artifact and policy v16 remain unchanged. This continuation hardens
the nonce-gated diagnostic worker in `validation/perf-probe`; it does not promote
that worker into the gameplay mod or approve production performance.

Gameplay SHA-256:
`0f7859861c3f5a68d1c93e66491fb4c4ad0acae70755d3f814bcb1308d74aea7`.
Hardened probe SHA-256:
`f672db51969f0c65813987d256ceb6009222ef79368e51376868187b1147c495`.
Build command: `gradlew.bat -p validation/perf-probe --offline test build --console=plain`
with JDK 21; 20 tests pass with no failures or errors. Gameplay was not rebuilt.

## Reproduced defects and changes

The existing output boundary was extracted into `SimulatorCallbackGate`, then
tested before correcting its behavior. The focused Gradle invocation
`-p validation/perf-probe --offline test --tests com.cobbleraids.validation.perf.SimulatorCallbackGateTest`
ran seven tests: six failed, reproducing partial engine output, callbacks after
shutdown, delivery continuing after callback failure, and missing discard counts.
The ordered successful batch test passed.

The corrected boundary publishes a batch only after its engine operation returns
successfully. Engine exceptions clear the capture scope and discard partial output.
Shutdown invalidates the gate before draining the worker. Pending batches check
gate validity and engine health before delivery and before each callback. A
callback failure closes delivery and poisons the queue; later messages and pending
batches stop. Wrong-thread delivery fails closed. Counts distinguish posted,
successfully applied and discarded callbacks. A callback that throws is counted
as discarded, although side effects before its exception cannot be rolled back.

`NativeAsyncPrototype` uses this tested boundary directly. Calls arriving after
shutdown reject explicitly instead of accidentally taking the synchronous
startup path. A second server in the same JVM is rejected: this disposable
prototype has no supported restart/context migration protocol. Synchronous
bootstrap is retained before the first server starts.

A separate regression reproduced the queue incorrectly poisoning itself when
rejecting a new call after normal shutdown. Normal closed-queue rejection now
leaves the engine failure flag clear; saturation while accepting work still
poisons the queue. Missing the two-second drain deadline or interruption is
reported as a failure. Queue statistics expose shutdown and termination separately;
shutdown alone does not prove the worker stopped.

The offline probe build passes 20 tests, including nine callback-boundary tests,
five serial queue tests and six existing recorder/trace tests. Tests include the
actual queue-to-callback boundary: an engine failure invalidates previously posted
output as well as discarding the failing operation's partial output. A deadline
test confirms an interruptible blocked worker reports a timeout. Scheduling
rejection, exception scope cleanup, close during execution, close inside delivery,
FIFO, saturation and failure propagation are covered without timing-performance
assertions.

Discarded callbacks include partial output never posted to the server as well as
scheduled output later invalidated. Consequently the discard count is not merely
the difference between posted and applied. Statistics during active work are
observations, not an atomic snapshot; cleanup is checked after the queue drains.

## Native verification and newly measured persistence blocker

Fresh nonce-owned rig `worldboss-rehearsal-c1d6d1d7b9309c5c`, PID 35584,
used the exact hashes above, compatible AscensionLib and normal client physics.
The five-fighter/one-observer pilot passed all 14 functional assertions. All five
fighters had credited damage; Hold/recovery preserved 35,870/40,000 HP. Global
Pokémon spawning stayed enabled. Protection recorded 17,193 early skipped
position passes and 770 late entity refusals.

All 38 submitted jobs completed, queue depth returned to zero and none were
discarded or failed. All 67 posted callbacks applied, with zero discarded
callbacks. Peak queue depth was five; maximum queue wait 817.0920 ms, worker
execution 792.6781 ms, callback wait 17.5297 ms and callback batch 1.6314 ms.
The worker therefore still has substantial tail latency despite correct output.
After stopping the encounter, querying the actual native map found no live or
unclassified battles. Only the recorded startup dummy remained:
`1a67cb08-ae3c-4ce0-9906-c02e3db9202c`.

| Phase | Ticks | p95 ms | p99 ms | Maximum ms | Maximum interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gathering | 1,033 | 38.1873 | 304.7142 | 579.8025 | 584.2668 |
| Combat | 284 | 29.1283 | 295.7591 | 1,361.3166 | 1,365.4077 |
| Recovery | 232 | 25.1989 | 147.5788 | 305.7291 | 305.9106 |

Every selected phase fails the unchanged nominal limits. The full record retains
3,220 ticks, zero dropped ticks, 328 stack samples and zero dropped stack samples.
Setup maximum is 650.2014 ms; all-record maximum interval is 3,067.2109 ms.
The official reporter returns **INCOMPLETE** because the runtime prototype flag
is preserved. Earlier successful prototype timings are not acceptance evidence
for this changed probe or proof that the current host meets the limits.

At the gathering maximum, tick 1702, 17 of 18 stack samples are inside
`FileDispatcherImpl.force0`, under `WorldBossJournal.commit` and/or
`RecoveryWitness.force`; the sampled callers include arrival/travel commits.
All 48 samples from the combat maximum, tick 2724, and all seven from the
recovery maximum, tick 3095, are inside `force0` under the periodic world-boss
journal commit chain. These observations identify synchronous durable persistence
as a further measured blocker; they do not establish why storage latency varied
between separate rigs or prove callback changes caused the difference.

Do not remove forced writes, weaken witness guarantees or move mutable server
state to an I/O thread. A future persistence design needs immutable snapshots,
strict ordered commits, main-thread acknowledgments, bounded admission and
failure/restart semantics before replacing these synchronous durability barriers.
Crash and economic correctness checks must accompany any such change.

Evidence under the rig:
`pilot-1795189633640440300/report.json`,
`logs/wbperf-fighters5-3ac525ec-c29f-41e2-941a-e390a1655f48.json`, adjacent
`.gate.json` / `.comparison.json`, and `logs/worker-cleanup-stats.json`.
The runtime emitted 18 fresh-account missing `.old` warnings; the retained
five-fighter v16 control also has 18 and the earlier 51-client rig has 153.
They are not treated as new dispatcher exceptions or a clean-runtime signoff.
No prototype failure marker or escaped-callback exception was observed.
The test encounter and owned server were stopped gracefully; world saving and
RCON listener shutdown completed. No live deployment, season activation, commit
or push occurred.

## Remaining production requirements

These changes do not implement persisted Hold on worker failure, settle partial
admission ownership, or roll back native actor side effects. Metadata/context
access and reload still require serialization with gameplay. Battle-specific
closing acknowledgments, stale input rules, other mods' synchronous expectations,
actual reward settlement, full parties, real clients and autosave-inclusive events
remain unverified. A successful native pilot cannot replace those checks.

The earlier five-fighter native gameplay control still fails at 604.8614 ms;
the earlier fifty-fighter worker timings remain diagnostic evidence only. No
performance samples or rejection flags are removed to approve this continuation.
