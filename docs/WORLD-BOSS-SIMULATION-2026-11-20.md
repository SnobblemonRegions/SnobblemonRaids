# Isolated world-boss simulation — November 20, 2026

The measured 739.4324 ms synchronous native Graal start is removed from the
world-boss tick path. World-boss duels use a private native Graal context on a
single bounded worker. Ordinary battles retain Cobblemon's original context and
dispatch path. This change is in the gameplay JAR, not the old diagnostic worker.
Overall production performance remains unapproved: automatic travel still forces
journal writes on the main thread, and the fifty-fighter trial has additional
budget failures and overflowing diagnostic stack samples.

Final candidate: policy v18, unchanged mod version `0.8.210-projector-follow`,
gameplay SHA-256
`18e5b46383356ad92baeeeab1137dc94201e4a38b3101ab5d9c523993f0eb9d5`.
The earlier five-fighter candidate was
`098411557522634e50ada03399270b0d6c8e6e56d2f2db1c5b4fdd4199a49c3d`;
later changes add diagnostics, stronger startup/failure accounting and another
output-budget regression. Results below identify which artifact was exercised.

## Ownership and compatibility

The native 1.8.2 Graal builder/boot implementation creates the private context
from the already patched simulator files. It never unbundles or rewrites shared
files during private initialization. Captured native registry operations replay
detached raw definitions, including JavaScript functions that a JSON registry
export would lose. Native species initialization and the bounded startup fixture
complete on that private context before it accepts a live duel.

The existing warmup helper is reused on the private context's owning worker;
the ordinary context still warms on its native species-ready caller. No live
actor participates in either warmup. The current modpack's mixin configurations
were inspected for Graal targets; the two found are CobbleRaids' own boot and
routing mixins. This is evidence for this dependency set, not a compatibility
promise for arbitrary future mods or direct mutations of Graal internals.

Routing requires the world-boss-owned raid factory scope, the raid format, and
its exact boss actor UUID. A scope can claim one native start only. Nested
ordinary factory scopes clear that intent. Only immutable native battle UUIDs,
copied message arrays and detached registry definitions reach the worker. No
`PokemonBattle`, player, entity, world or registry lookup crosses into simulation.
The private service object contains native simulator facilities, not actors.

Output is buffered per operation and published only after native success.
Server-thread polling applies it to the original native interpreter. Pending
admission becomes confirmed only when that exact start is acknowledged and its
encounter/token still belongs to the live event. Rejected starts abort native
ownership. Closing tickets suppress late output and inputs; FIFO end operations
verify the native battle-map entry was actually removed before emitting a close
acknowledgment. Closed UUIDs remain tombstones for the lifetime of that server so
stale traffic cannot fall through into the ordinary context or a successor duel.

## Failure, reload and resource rules

Engine/queue/output failure closes the lane and invalidates undelivered output.
The server holds active combat with `SIMULATOR_FAILURE`, using the existing forced
journal protocol. No owned failure retries on the ordinary simulator. An already
committed outcome retains the existing idempotent settlement rules. Storage
failure cannot be reported as a successful persisted Hold.

Data-pack reload or native registry changes invalidate the private snapshot.
World bosses require a server restart after such a reload; staff recovery is
refused while unavailable. Ordinary native registry reload remains operational.
There is no automatic in-process replacement context for an active occurrence.
Shutdown cancels publication, disposes the context on its owning worker and waits
up to ten seconds. Timeout or disposal failure is reported instead of claiming
clean shutdown. A prior lane failure is also surfaced by its close call; the
deliberate reload tests therefore produce an expected shutdown error report even
when diagnostics already show `terminated=true`.

Limits are explicit: 512 input commands and 512 output batches; eight MiB queued
input and eight MiB queued output; one MiB input per operation; 256 messages/two
MiB per output operation; 20,000 registry operations/32 MiB of raw replay data;
65,536 retained native identities per server lifetime. Exceeding an identity
budget requires restart rather than unbounded retention. Unacknowledged starts
older than ten seconds fail closed. Main-thread output polling processes at most
eight batches with a five-ms loop budget; an individual native interpreter call
cannot be preempted. A second simulator adds memory/CPU cost; no full production
memory or graphical-client signoff is inferred from these short bot trials.

## Regression and native evidence

The original synchronous seam failed both initial worker/cancellation regressions.
The final full offline JDK 21 build passes 1,063 root and eight client tests
(1,071 total). Coverage includes detached inputs, exact one-shot boss scope,
neutral ordinary scopes, cancellation before native start completion, FIFO close,
partial failed output, prior undelivered output invalidation, foreign output,
input saturation, output saturation, raw function retention and bounded replay.
The validation probe passes 20 tests. Logging, raid integrity and API checks pass;
the remapped gameplay JAR passes all 47 injection fault-barrier checks. Public
API signatures remain the same sixteen types. V18 invalidates prior calibration.

### Five fighters, original tick-only recorder

Rig `worldboss-rehearsal-10d3ef5fceac3205` exercised the earlier `09841155...`
candidate with five fighters and one observer, normal physics and global Pokémon
spawning enabled. All 14 functional checks pass. All five had credited damage;
Hold/recovery preserved 35,125/40,000 HP. Early spawn skips: 21,036; late refusals:
913. Neither a diagnostic trace nor rehearsal-worker marker was enabled.

| Phase | Ticks | p95 ms | p99 ms | Maximum tick ms | Maximum interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gathering | 1,039 | 27.9185 | 83.7722 | 541.1716 | 570.5141 |
| Combat | 677 | 17.8655 | 24.1782 | 34.0245 | 75.9166 |
| Recovery | 233 | 19.6025 | 24.9312 | 29.2019 | 162.0372 |

The original gate remains FAIL for gathering. Its worst samples are forced
travel/settings journal commits. The first native battle-start region peaks at
170.9444 ms; 134 gathering ticks from one second before the first duel log
(13:00:21 America/New_York) through combat mark retain that maximum. This
admission-only window is diagnosis, not a substituted acceptance phase. Full raw
gathering remains in the gate. Setup maximum is 742.3876 ms and all-record maximum
interval 2,151.0974 ms; all 3,405 ticks remain.

Reload after the pilot preserved HP, placed the occurrence in persisted
`SIMULATOR_FAILURE` Hold and refused recovery until restart. The encounter and
owned server then stopped. Evidence: `pilot-1795197498761253800/report.json`,
`logs/wbperf-fighters5-a71f0d84-0840-4e8e-8943-6d594e2c44a3.json`, adjacent gate
and admission-window JSON, and `logs/simulator-reload-check.json`.

### Fifty fighters, final gameplay artifact

Rig `worldboss-rehearsal-21ad29d70e2ce463` exercised final `18e5b463...` with
fifty fighters plus one observer, normal physics, global spawning enabled and
30 seconds of combat. All 14 functional checks pass; all fifty had credited
damage. Hold/recovery preserved 356,946/400,000 HP. Early position skips: 136,948;
late entity refusals: 7,261. The probe is
`9150be978eabac08abcb22c5fd55c5d4a49c353038056ede8fb5fbbd260df07a`, adding
only an ordinary-PvP fixture command to the available diagnostic tools. No trace
was armed and no rehearsal-native worker marker was present; both rejection flags
are false. The gameplay private worker is the implementation under test.

| Phase | Ticks | p95 ms | p99 ms | Maximum tick ms | Maximum interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gathering | 1,116 | 64.6645 | 705.0780 | 3,467.4692 | 3,479.2466 |
| Combat | 704 | 56.2279 | 70.2811 | 90.1412 | 91.3543 |
| Recovery | 273 | 40.7547 | 53.0639 | 59.0920 | 312.6259 |

No combat/recovery tick exceeds 250 ms, but combat p95 exceeds the original 50-ms
budget and the recovery interval exceeds 250 ms. The recorder retains all 6,793
ticks, including setup maximum 4,613.1385 ms and maximum interval 4,617.5378 ms.
Its stack sampler retained 512 samples and dropped 1,259 after reaching its bound.
The official gate correctly returns **INCOMPLETE** for that diagnostic overflow.
These numbers cannot approve capacity. Remaining fifty-player stalls must not be
attributed solely to a call site whose stack was not sampled. An admission-only
window still includes slow gathering ticks; it is not used to claim a fifty-player
sub-250-ms admission gate or remove the remaining travel problem.

After native cleanup, preflight reports 53 closed private tickets (including
chained starts), zero pending/open/closing tickets and empty input/output queues.
Each closed acknowledgment follows native map-removal verification. Reload on
the final artifact also preserves HP, persists simulator Hold, refuses recovery
and reports `terminated=true`. Both context disposal and owned server shutdown
complete; all dimensions save and RCON closes.

Native evidence: `pilot-1795198271502726800/report.json`,
`logs/wbperf-fighters50-f25e5e4f-4567-4ad3-a9fd-c86e1b39f412.json`, adjacent
gate/admission-window JSON, `logs/private-simulator-cleanup.json` and
`logs/simulator-reload-check.json`.

### Ordinary native PvP compatibility

The validation-only command accepts only two synthetic `WBCompat_` accounts in
the disposable rig and invokes native `BattleBuilder.pvp1v1`. It confirms the
resulting ID is not world-boss-owned. Both clients receive native UI and submit
real moves. Native `stopbattle` cleanup reaches both; the held boss's HP is
unchanged and its private simulator remains ready. All twelve compatibility
assertions pass in `ordinary-compat-1795198847936538800/report.json`.

The preceding fixture attempt is retained at
`ordinary-compat-1795198716109289200`: it passed native routing/UI/moves but its
one-shot forfeit was sent while automatic replies continued and did not observe both end
packets. The successful rerun stops automatic replies and uses native
administrative cleanup; it does not claim a PvP victory/forfeit test. No production
ordinary-battle code was changed to make the fixture pass.

Post-stop byte audits validate all 192 journal frames and the matching external
head in the five-fighter rig, and all 414 frames/head in the final fifty-fighter
rig. Evidence is `logs/journal-post-stop-audit.json` in each rig. This is file
consistency evidence, not a physical power-loss test. Both complete timing trials
are retained in the campaign, now thirty actual trials; failed and overflowing
records were not discarded. All owned test servers and private listeners stopped.

## Remaining release work

Automatic arrivals/settings/outbound/arrival acknowledgments still force journal
I/O in ticks. Merit qualification and other critical transitions also retain
durability barriers. They need acknowledged asynchronous transaction workflows;
do not remove forcing or report success before acknowledgments. Fifty-player
CPU/p95, unsampled stalls and autosave-inclusive events remain unresolved. Full
parties, real client FPS and actual economic settlement remain separate checks.
No live season, reward grant, deployment, commit or push occurred.
