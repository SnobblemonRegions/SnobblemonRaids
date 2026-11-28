# World boss cold admission: source-level diagnosis

**Current result: blocked.** Current v16 passes the build and functional pilot,
but its fresh five-fighter cold admission still reaches 604.8614 ms against the
unchanged 250 ms limit. Neither the original stall nor overall capacity is fixed.
The latest **rehearsal-only combined worker + v16 filter** experiment met all
nominal event-phase timing limits at fifty fighters plus one observer
(gathering/combat/recovery maxima 157.8319/58.5357/45.1771 ms). The worker has not
been promoted into the gameplay JAR. Production lifecycle hardening and
autosave-inclusive validation remain open; diagnostic evidence cannot approve it.

The original v10 fifty-fighter recording retains a 365.2732 ms gathering tick
under native `GraalShowdownService.startBattle`. Replaying its raw gate still
fails the unchanged 250 ms limit. A short passing combat window did not resolve
that first-admission failure.

## Finding

The startup warmup did not exercise the same bootstrap as real native battles:

| Input | Previous warmup | Captured native battle |
| --- | --- | --- |
| Team representation | Arrays of Pokemon sets | Cobblemon's modified packed strings |
| Team decoder | `Teams.unpack` returns arrays immediately | Packed-field decoding and dex lookups |
| Format | Synthetic id/name, no `gen` or `effectType` | `gen:9`, `effectType:Format` |
| Integration effects | No `ascensionFx` | Nonempty UUID-bound v1 effects for both Pokemon |
| Bootstrap commands | Start + two player definitions | Those three plus both `team 1` choices |
| Entropy path | Explicit fixed seed | Native unseeded startup |

The real effects module's `Battle.start` wrapper returns without parsing/applying
effects when `format.ascensionFx` is absent. The previous warmup therefore skipped
that parser and its installed per-battle hooks, even though the module was loaded.
This is a warmup coverage bug, not evidence that valid Ascension effects should
be removed from players or that the effects module alone consumed the full stall.

## Source trace

A separate disposable probe attaches Graal's actual execution listener to the
first native battle. It captures root names, source locations, nested timings,
the native input messages, direct host callback wall time and GC collection-time
delta. Bounded nesting and overflow have regression tests. The diagnostic code
is not part of the production mod.

Fresh rig: `worldboss-rehearsal-9affc73dd1d1185a` under the workspace's
`.rehearsals` directory. Five fighters plus one observer reproduced the native
bootstrap; all fourteen functional assertions passed. Recording:
`logs/wbperf-fighters5-0515e900-d9d0-4ad6-8cc3-bc88c9f0feb8.json`, with
`pilot-1795088137769071500/report.json`.

Selected traced roots:

| Root | Source line | Inclusive ms |
| --- | --- | ---: |
| `startBattle` | native index, evaluated as `Unnamed:23` | 251.3325 |
| Effects `Battle.start` wrapper | `ext-ascensionlib-fx.js:1692` | 138.9091 |
| Base/start chain beneath that wrapper | `raid-patch.js:376` | 116.4596 |
| `Teams.unpack` | `sim/teams.js:100` | 31.4320 |
| Effects `apply` | `ext-ascensionlib-fx.js:231` | 20.0595 |
| Effects `parse` | `ext-ascensionlib-fx.js:192` | 13.8319 |

The trace counted 12,288 roots, maximum depth 32, no depth overflow and balanced
returns. Seven host callbacks totalled 51.8141 ms, maximum 24.1291 ms; the GC
collection-time delta was zero. These observations did not support GC as the
dominant cause in this capture. Host callback wall time can include nested work.

**These are instrumented diagnostic timings.** Inclusive subtrees overlap and
must not be added. The effects wrapper includes the base startup underneath it.
The 497.7427 ms traced wall measurement also includes instrumentation setup and
teardown; it is not a decomposition of the original uninstrumented 365 ms tick.
The reporting gate now rejects every recording from a process armed for source
tracing, even after tracing stops. The diagnostic gate correctly returns
INCOMPLETE rather than capacity approval.

## First candidate: representative warmup (v11)

The first candidate sent the captured five-command bootstrap, packed teams with UUID,
HP/status and move-PP fields, native format fields and representative v1 optional
effects. It uses different synthetic Pokemon UUIDs across three bounded local
battles. Its private output sink, simulator-only battle IDs, maximum eight turns
per battle and cleanup rules remain. No Minecraft player/entity/battle is
registered and no economic outcome listener participates.

Cobblemon modifies `Teams.unpack` but its bundled generic `Teams.pack` still
emits the vanilla schema. The fixture deliberately uses the observed Cobblemon
wire format rather than that incompatible writer. It checks decoded Pokemon
identities before advancing turns. The optional effects data is an authored
fixture, not player storage; a server without the module ignores the field.

The packed-team/effects regression was observed failing against the old script
and then passing after the fix. All 1,040 Java tests and eight simulator-script
tests pass. Policy fingerprint v11 invalidates v10 calibration. Production mod
version remains unchanged.

Fresh uninstrumented rig `worldboss-rehearsal-78c44ccea14aae3c` uses the exact
original tick probe SHA-256
`450d2546b303638e28488d7ab85c2c4a61ab542851fb190b9f7c515181b7643a`.
Its console confirms **three actual effects applications before warmup
completion**, then successful warmup in 1,044 ms before players are admitted.
The main candidate SHA-256 is
`6c32e8dcc9f547f29110b22753a7798b1bec5173d44b47145f79855ad3a3543e`.

That candidate's uninstrumented fifty-fighter trial passed all fourteen functional
assertions but still failed gathering: p95/p99/max 29.2592/45.0317/316.5848 ms.
Combat (max 31.4365 ms) and recovery (max 24.0306 ms) passed. It did not close the
blocker. Evidence: `pilot-1795088931272770900/report.json` and
`logs/wbperf-fighters50-cf6ad255-8fce-4ff7-ba25-cc27bfba4046.json` plus its gate
report in that rig. The retained overall 327.50 ms setup maximum samples
CobbleTowers' cell warm-pool structure loading. No outlier was removed.

## Second finding: invalid preview choices and cold native request metadata

The 316.58 ms tick correlates with the very first admission, not a later player.
Its late stacks load native request data under `RequestInstruction.invoke:34`,
`AbilityInstruction` through interpreter parser lambda 2, and `ErrorInstruction`
through parser lambda 61. Matching native bytecode maps those callers precisely.

A second disposable source/output capture, rig
`worldboss-rehearsal-b8690647d599816f`, recorded the real error responses:

```text
sideupdate
p1
|error|[Invalid choice] Can't choose for Team Preview: You're not in a Team Preview phase
sideupdate
p2
|error|[Invalid choice] Can't choose for Team Preview: You're not in a Team Preview phase
```

The native registry unconditionally appended `>p1 team 1` and `>p2 team 1` to a
raid format with an explicitly empty ruleset. The battle already requested move
choices, so those preview choices were invalid. The error handling itself also
incurred cold native instruction work. These are legitimate errors to fix, not
messages to suppress in the logger or ignore in the gate.

This capture has balanced roots/callbacks, no depth overflow, 11,030 roots,
73.4661 ms of host callback wall time, 106.8484 ms of diagnostic setup and a
349.2109 ms instrumented native body. GC collection-time delta was 17 ms in this
capture; this differs from the first capture and is not an unprofiled baseline.
Evidence: `pilot-1795089800442173700/report.json` and
`logs/wbperf-fighters5-02f04b32-4bfc-4998-82c2-0dfd225d525a.json`.
Its first native output messages preserve the exact errors above. Both source
traces remain diagnostic-only and cannot pass the capacity reporter.

## Protocol and request preparation correction (v12)

`RaidBootstrap.withoutUnrequestedTeamChoices` removes preview-choice commands
only from the verified inline raid format with an explicitly empty ruleset.
It preserves player data and ordering, move choices, non-raid formats, explicit
custom rules (including Team Preview), malformed/unknown data and ambiguous
multi-start payloads. The guarded native registry adapter applies it after
actor rewriting and extension fields. The startup fixture now uses the same
corrected three-command bootstrap. It no longer reproduces the invalid preview
choices merely because the upstream registry emitted them.

The warmup also resolves the real registry's Gson adapter for
`ShowdownActionRequest` before players join, and loads the observed ability
instruction type without running its initializer. Native bytecode confirms
that registry construction creates codec/cache state and an empty battle map.
Adapter preparation only resolves metadata: no request is deserialized,
sanitized or dispatched, and no actor/battle is created. The native registry's
existing Gson instance is used so the actual request path benefits from its
cache. Preparation participates in the existing once-per-context failure cache.

The captured protocol regression was run red against the unchanged passthrough
behavior (five commands where three were required), then green after filtering.
Four tests cover command preservation/filtering, format scope and ownership of
the input array. A fifth regression uses a shared registry codec whose read/write
methods throw, verifying metadata preparation reuses the shared cache without
parsing or serializing a request. All 1,045 main/client Java tests and eight
warmup script tests pass. Six diagnostic-probe tests and fourteen Python
rehearsal/reporting tests pass. Main source, callback, API and mixin checks pass.
Policy v12 invalidates v10/v11 calibration.

Fresh verification rig `worldboss-rehearsal-3b9681a182f5a839` uses the original
uninstrumented tick probe and main artifact SHA-256
`2426d575a8991f47043e39e2bae9298962a119421c614a728fef95f0796fe412`.
Native startup preparation succeeded in 1,094 ms, with three actual effects
applications before warmup completion. The fifty-fighter raw-tick gate remains
the acceptance check; neither diagnostic tracing nor unit results approve it.

The v12 retest passed all fourteen functional assertions but failed gathering:
p95 30.7095 ms, p99 50.9803 ms, maximum 333.0817 ms and maximum start interval
333.649 ms. Combat and recovery passed their selected-phase thresholds. A
separate 259.8699 ms gathering tick included player autosave work; it remains
in the recording. Evidence is `pilot-1795090435649709700/report.json` and
`logs/wbperf-fighters50-1fd14ceb-8c92-484e-ab16-a66c11c423c2.json`.
The protocol/metadata corrections did not resolve the admission gate.

## Actual Java host entry warmup (v13)

The former fixture called the simulator from JavaScript with a JavaScript sink.
Native admission instead calls Graal `Value.execute` from Java with the actual
service object and Java `String[]` packets. Startup now prepares wire data in
JavaScript, then invokes the real `startBattle`, `sendBattleMessage` and
`endBattle` functions from Java using that same host-call shape. Three battles,
at most eight player moves each, run before admission; every attempted battle
is ended in a `finally` block. Startup checks actual turn progress and private
output, and caches failures without retrying during live reloads.

Output isolation is limited to the owning startup thread, the exact service
instance and three exact non-UUID private simulator IDs. The native output
adapter consumes only that scope; valid live battle UUIDs and foreign services
pass through. Scope and temporary bridge bindings close on failure. Private
simulator errors prevent a success marker. This creates no native battle,
entity, player or reward dispatch. Regression tests cover actual argument
shape, bounded calls, cleanup after native-start failure, cached failure,
private errors and live-message passthrough.

Candidate SHA-256:
`f8b305dd06d40da59bac153450d6ea37f1312aeb0a1635a9ce367d15264d78b4`.
All 1,039 root plus eight client Java tests pass, with no failures/errors/skips;
eight script tests and fourteen Python safety/reporting tests pass. Source,
raid integrity, 53 callback barriers, logging, API and 35 mixin guards pass.
Fresh rig `worldboss-rehearsal-3ca066c2096b058e` completed native startup warmup
in 985 ms with the original tick-only probe. Its fifty-fighter gate, rather
than startup success or unit tests, determines the performance result.

That v13 retest passed fourteen functional checks, with all fifty fighters
dealing damage and 6,838 protected natural spawn attempts refused. Hold ended
all owned battles; recovery preserved 358,885/400,000 HP. Gathering still failed:
p95/p99/max 29.3266/48.6478/350.3495 ms, maximum start interval 351.2604 ms.
Combat passed (22.787/27.6474/34.2422 ms), as did recovery
(20.5544/28.152/84.1078 ms). Setup retained a 362.9567 ms maximum.
Evidence: `pilot-1795091675444767600/report.json` and
`logs/wbperf-fighters50-a00b9404-f02c-44e1-bb4f-0e494798f682.json`.
No causal improvement is established by the host-call change.

The failing gathering tick 3558 starts at epoch 1795091855970. Samples show
simulator work through 254.8544 ms, `BattleMessage.<clinit>` at 280.8432 ms,
loading under `SwitchInstruction.<clinit>` at 307.317 ms, and loading at
`RaidFactory:97` (renown metadata) at 336.4471 ms. Sampling is sparse and does
not assign exact cost to each frame. Matching native bytecode confirms the
message initializer creates two Regex values and a companion; the switch
initializer creates only its companion, whose constructor calls Object.

## Startup execution thread and observed handler preparation (v14)

V13's separate five-fighter source trace, rig `worldboss-rehearsal-2decbd006ff835cd`,
again shows overlapping work under `Battle.start`/`go`; no single effects parser
accounts for the full delay. It recorded five host outputs, 69.4114 ms callback
wall time, 10,939 balanced roots, no depth overflow and zero GC collection-time
delta. Its 121.7069 ms listener attachment and subsequent source instrumentation
make the recording diagnostic-only. Native call entry was approximately 39 ms
after its enclosing tick began; wall-clock correlation has millisecond resolution.
Evidence: `pilot-1795092158554761600/report.json` and
`logs/wbperf-fighters5-514f6cf3-d45b-4e22-bb2a-5c2136b4e195.json`.

Startup logs also expose a thread mismatch: species-ready preparation runs on
`main`, while actual admission executes on `Server thread`. V14 prepares the
observed message/switch initializers and renown marker, and repeats bounded
simulator preparation from guarded `SERVER_STARTING`, before the tick loop,
on the actual server thread. Warmup tracks each successful context/thread once;
a failed context remains failed without retries on another thread or reload.
The ready service is held weakly. Output isolation remains thread-local.
One regression verifies that a second execution thread warms once and returning
to the original thread uses its cached result. Thread mismatch is an experimentally
tested hypothesis, not proof of the cause or a capacity approval.

V14 main artifact SHA-256:
`446cde470118b8a215202bcc0838602e27343599404cc5315d5e3e177f0c4ec5`.
All 1,040 root plus eight client tests pass, with no failures/errors/skips.
The added server lifecycle callback passes the registration guard (54 guarded
registrations); all 35 mixin injection guards and other required source checks
pass. Fresh tick-only rig `worldboss-rehearsal-db1ef87fc8a1d6a4` completed main
warmup in 2,776 ms and server-thread warmup in 757 ms, before world preparation.

**Experiment rejected:** the v14 fifty-fighter trial passed fourteen functional
checks but failed gathering at p95/p99/max 60.0206/125.9745/709.9721 ms, with a
711.8558 ms maximum start interval. Combat and recovery selected phases passed;
setup retained 928.8957 ms and the overall maximum start interval was 2715.6078 ms.
Native samples still show substantial interpreter work and later dynamically
linked native dispatch code. These timings do not establish that thread warmup
caused the regression, but they provide no basis for retaining its added work.
Evidence: `pilot-1795092551064999000/report.json` and
`logs/wbperf-fighters50-518bf6ab-9082-43ec-b5fb-00dda7c1231b.json`.
All fifty clients entered battles and sent moves; forty-nine had credited damage
at Hold. Spawn protection refused 6,904 attempts; recovery preserved 358,245 HP.

V15 removes the experimental second warmup, the additional server lifecycle
registration, the published service reference and the per-thread cache. It
retains the observed class preparation, corrected native protocol, shared
request-codec preparation, representative packed fixtures and native host entry.
Successful and failed contexts are each attempted only once, including calls
from another thread; the regressions explicitly check both cases. A smaller
fresh five-fighter tick-only trial tests cold admission first, without claiming
fifty-fighter capacity from that population.

V15 main SHA-256:
`7de1c19f02404d4efe92ec1e7bda27fea8da37ef2b7956993755d12ff4f4cc14`.
Full main/client build passes 1,039 + 8 Java tests with no failures/errors/skips.
The guarded callback count returns to 53; 35 mixin guards and all required
source/integrity/logging/API checks pass. Its fresh tick-only cold-entry rig is
`worldboss-rehearsal-0997bbf292fef1da`.

The v15 five-fighter retest passed all fourteen functional assertions. All five
clients entered native battles, sent moves and had credited damage by Hold;
the observer received no private battle. Native protection refused 637 spawn
attempts. Hold ended the battles, recovery preserved 37,552/40,000 HP, and
journal commits advanced without rewriting the NBT projection.

The raw three-phase gate still **FAILS**: gathering p95/p99/max
28.0637/71.9308/755.6243 ms, maximum start interval 759.0916 ms. Combat passes
(21.262/30.3153/31.7923 ms) and recovery passes
(17.8621/25.3293/58.2445 ms), with six actual clients throughout all selected
phases. These are five-fighter results, not fifty-fighter approval. All setup
samples remain; the all-recorded maximum start interval is 2390.7009 ms.
Evidence: `pilot-1795093154993434900/report.json` and
`logs/wbperf-fighters5-83fcd626-5c06-4139-8ade-869ad12c10c6.json`, with its
adjacent `.gate.json`.

Tick 2189 at epoch 1795093267570 contains the remaining first-admission stall.
Its sampled stack is inside Graal/Truffle execution at 124.4702, 265.1329 and
404.6323 ms, native `ShowdownInterpreter.interpret` at 529.619 ms, then dynamic
class generation under `PpUpdateInstruction.invoke` at 727.3889 ms. Another
236.7135 ms admission tick includes dynamic linkage through boost/action-effect
dispatch. The startup fixture consumes private output and therefore cannot
exercise real actor/packet dispatch. Preloading individual handler classes does
not execute those per-battle dispatch lambdas. Sparse stacks do not provide
exact exclusive cost, and the optional generated effects vary per account.

The demonstrated conclusions are the invalid preview protocol bug and the
startup coverage gaps, plus a persistent synchronous native simulator/dispatch
stall that also reproduces below capacity. No causal speedup is claimed.
Do not keep adding classes or increasing warmup rounds without a targeted
measurement. Further work needs an end-to-end native simulator/dispatch profile
and a separately reviewed way to reduce or stage that work without changing
battle ordering, admitting moves before startup, or losing cleanup/reward
ownership. The failed second-thread experiment stays removed. No thresholds,
durability forces, movement checks or live effects were disabled to pass a gate.

Longer
autosave-inclusive trials, real deployment hardware, complete parties, audience
load, client performance and actual reward/recovery remain separate release
requirements.

## Follow-up: boundary timing without a JavaScript listener

The unchanged v15 gameplay JAR was tested in fresh rig
`worldboss-rehearsal-cef50804186b0d2a` with `wbperf boundaries-next`.
No execution listener was attached: `sourceRoots:false`, zero root events,
balanced callbacks, no dropped stack samples. The timer records native-call
wall time, outer host-callback wall time, thread CPU counters when available,
and GC collection-time delta. Monitoring metadata is resolved when arming,
before the measured admission. Nested callbacks are counted but not double
counted in wall/CPU totals. This instrumentation remains diagnostic-only.

The first native call recorded:

| Measurement | Milliseconds |
| --- | ---: |
| Native call wall time | 460.0527 |
| Outside Java output callbacks | 373.2581 |
| Java output callbacks, five calls | 86.7946 |
| Time until first output callback | 371.1580 |
| Calling thread CPU time | 281.2500 |
| Output callback thread CPU time | 15.6250 |
| GC collection-time delta | 0 |

The wall decomposition reconciles exactly within rounding. Windows thread CPU
counter granularity is coarse; these values are not precise exclusive method
costs. Outside-callback wall time includes simulator/bridge/probe work, and
wall-minus-CPU can include scheduling and non-GC pauses. This capture supports
simulator work as the larger target, with a meaningful native dispatch cost;
it does not support declaring one universal cost split across randomized teams.
The complete gathering tick still reached 577.9817 ms. All fourteen functional
checks passed; recovery preserved 36,179/40,000 HP and protection refused 739
natural spawn attempts.

Evidence: `pilot-1795127093195298100/report.json` and
`logs/wbperf-fighters5-2b8107dd-a04a-4b1c-b2d6-9d6376c7c628.json`.
The reporter rejects the capture as INCOMPLETE for capacity approval, including
boundary-only mode. It is not a new passing capacity trial.

## Offline runtime replay

A player-free fresh rig, `worldboss-rehearsal-a71f7f389eb5f08d`, exported 1,541
species through the native read-only registry interface. The offline replay
uses the captured simulator files and exact three native bootstrap messages,
plus that species data. Gameplay JAR/dependency identities must match. Both
initial player/boss request JSON objects, including stats, HP, move metadata
and UUIDs, must match the native capture exactly. Temporary simulator battles
are always ended. No Minecraft actor, network operation or reward participates.

Local Node v26.12.1 replay matched both requests in every repetition. The first
fresh-process start took 274.4712 ms synchronously / 274.7446 ms through output
delivery; repetitions of the same captured identities took 1.8682–3.8952 ms
synchronously. This is simulator-only evidence with cheap local output sinks,
not native Java dispatch or capacity signoff. Process CPU includes other Node
threads and is not directly interchangeable with a Java thread CPU counter.
The cold result itself exceeds 250 ms, so an engine swap alone is not proven
to meet the full server-tick gate.

Reproducer: `validation/smoke/showdown_runtime_replay.js`.
Artifact in the capture rig:
`logs/wbperf-node-replay-9aff0cd8-8b01-4189-baa3-073a52a0749e.json`.
It records source, species and script hashes, Node/V8 versions and raw timings.
The bundled socket alternative was inspected: this build's service getter
hardwires Graal, and the bundled socket debug server uses an older registry
protocol. It was not enabled or presented as a production-ready replacement.

## Serial native worker experiment — rehearsal-only

The next experiment preserves Graal, all native JS extensions and the gameplay
JAR. It intercepts only live start/send/end calls, copies message arrays and
executes them FIFO on one dedicated platform thread. A complete call's output
is collected as one batch, then applied on the server thread in order. The
native callback performs its battle lookup on the server thread, rather than
capturing actor state on the worker. No world/entity/reward dispatch is executed
by the worker. The queue is bounded at 1,024 jobs and fails closed on saturation
or a task exception; it has no caller-runs fallback.

Three queue regressions were observed red before implementation, then green:
off-caller execution/FIFO/draining close; saturation without caller fallback or
silent loss; and failure poisoning that prevents later gameplay. Nine probe
tests and seven capacity-reporter tests pass. A separate reporter regression
rejects `diagnosticRuntimePrototype:true` even if nominal tick limits pass.

Initial prototype rig `worldboss-rehearsal-fbd6de2828e03516` failed bootstrap:
Cobblemon starts a dummy battle after BagItems data loading, before server
readiness. The experiment now preserves synchronous bootstrap behavior and
activates worker routing only after SERVER_STARTED. The failed process exited;
its logs remain evidence. Corrected fresh rig
`worldboss-rehearsal-f0e4113ee051c03b` booted successfully and reported an enabled,
empty queue before its first live test. This prototype exists only in the probe;
its nonce marker is scoped to a fresh private rig. Production code is unchanged.

The corrected five-fighter experiment passed all fourteen functional assertions.
Gathering p95/p99/max was 16.7575/25.0008/75.6695 ms, with a 77.9811 ms maximum
start interval. Combat max was 29.7458 ms; recovery max was 24.1948 ms. All three
event phases met the nominal tick/population limits. Setup retained 264.49 ms
and an all-recorded 1659.3413 ms maximum interval; nothing was discarded.
Queue counters showed 40 submitted/completed jobs, no discarded work or failure,
peak two queued jobs, and 69 posted/applied callbacks. Recovery preserved
36,553/40,000 HP; protection refused 539 spawn attempts.

The post-recording real simulator query returned one remaining battle. Its ID
was not yet classified in that probe version, so this run does **not** establish
zero leaked simulator battles. The subsequent probe tracks exact bootstrap and
live-start IDs to resolve this, rather than deleting the remaining entry or
assuming it is the dummy. It also records maximum queue/execution and callback
delivery/batch durations to expose work delayed outside the server tick.

Five-fighter evidence: `pilot-1795128865694899600/report.json` and
`logs/wbperf-fighters5-104d9168-0844-4abf-817b-9e5c7a768470.json` in the
corrected prototype rig. The gate remains INCOMPLETE for capacity because
`diagnosticRuntimePrototype:true`; this is an experimental result, not promotion
of the gameplay artifact. Fresh fifty-fighter rig
`worldboss-rehearsal-9043cdbcd94fad44` uses the extended latency/ID instrumentation.

That fifty-fighter worker experiment passed all fourteen functional assertions
but failed the nominal performance limits in every event phase. Gathering
p95/p99/max was 109.6024/189.1602/562.0211 ms; combat
85.1649/130.1584/165.0155 ms; recovery 62.3247/136.1833/171.8062 ms. All phases
retained 51 actual clients. Setup retained 1855.0787 ms and an all-recorded
1860.8025 ms maximum interval. No raw samples were dropped.

All fifty clients started native battles and sent moves; all fifty had credited
damage by Hold. Recovery preserved 345,291/400,000 HP. There were 78 live starts
including chained duels, 792 completed jobs, no queued/discarded work or failure,
and 1,298 posted/applied callbacks. Maximum queue wait was 459.4093 ms, execution
607.7765 ms, callback delivery wait 542.4887 ms and measured callback batch
2.7713 ms. These values expose delay rather than disguising it as a tick fix.

The real simulator map retained exactly the recorded bootstrap dummy ID
`bf428c6e-8e74-4d64-840e-5fff0b695c4e`: zero live IDs and zero unclassified IDs
remained. This confirms live-duel cleanup in this run; the earlier five-fighter
probe did not record the remaining ID and cannot retrospectively classify it.

Evidence: `pilot-1795129310186268400/report.json`,
`logs/wbperf-fighters50-9f5bbdba-4beb-441d-9150-4ffc5588124f.json` and
`logs/worker-cleanup-stats.json`. The prototype remains rejected for capacity
approval. Its failed load result is retained, not replaced by the smaller success.

## Additional blockers: rejected-spawn CPU and vanilla player autosave

The 562.0211 ms gathering tick 10771 sampled Windows file replacement/truncation
and subsequent entity serialization. Cached official 1.21.1 mappings resolve
`class_29.method_262` to `PlayerDataStorage.save`, reached through
`PlayerList.save` / `saveAll` in `MinecraftServer.tickServer`. This is vanilla
player autosave, not a World Boss journal/cache commit. Its save behavior and
durability were preserved. Resolving it requires separate save-latency work;
it is not fixed by moving simulator computation off the server thread.

Other long gathering/combat/recovery ticks sample native player-spawner biome,
tag and area condition evaluation, plus Pokémon entity construction. The late
entity-spawn cancellation guard prevents insertion but still pays those costs.
The early predicate correction below targets this wasted arena work while
keeping outside spawn candidates available and leaving global spawning enabled.

## Scoped early natural-spawn correction (v16)

Native 1.8.2 source/bytecode shows `BasicSpawner.getMatchingSpawns` evaluates
`SpawnDetail.isSatisfiedBy` before selection/entity construction; natural entity
placement uses candidate block X/Z plus 0.5. V16 resolves the protected-column
decision once per exact player-spawner candidate. Only Pokémon detail predicates
inside a fully contained candidate block are rejected early. An exception-safe
nested scope restores the previous candidate decision on return/failure;
non-Pokémon and nonmatching candidates cannot inherit the protected decision.
Native matching is executed once; its own exceptions are not retried or hidden.
The pre-insertion guard remains for border cells and other spawning paths.

Five new regressions were observed red, then green: whole-cell/dimension
boundaries, independent four-corner geometry, candidate identity, nested outside
isolation and restoration after a native failure. Full main/client build passes
1,044 + 8 tests with no failures/errors/skips. Source/integrity, 53 callback
registrations, logging across 352 sources, the 16-type API boundary and 37 mixin
guards pass. WrapMethod handlers are now included in the guard validator.
Policy v16 invalidates earlier performance calibration. Main artifact SHA-256:
`0f7859861c3f5a68d1c93e66491fb4c4ad0acae70755d3f814bcb1308d74aea7`.

Fresh synchronous/native control rig `worldboss-rehearsal-f46473a7500d7421`
uses the original tick-only probe with no worker marker. All fourteen functional
checks pass. It records 19,338 early skipped position-matching passes and 838
late entity refusals with global Pokémon spawning enabled. Four players had
credited damage at Hold; recovery preserved 37,039/40,000 HP. No new guard fault
was logged. Its gathering gate still fails at 604.8614 ms (p95/p99
23.5378/49.8436 ms); combat/recovery selected phases pass. Setup retains
425.0875 ms and a 2593.7099 ms maximum interval.

Control evidence: `pilot-1795130971002266000/report.json` and
`logs/wbperf-fighters5-3ee009f0-1dff-4d9d-aebe-c229ed1083fe.json`.
The early filter is verified, but the synchronous simulator stall is not closed.
Fresh rig `worldboss-rehearsal-132a2543538557c0` combines v16 with the explicitly
gated worker experiment for a further fifty-fighter comparison. Production
dispatcher hardening, metadata/reload serialization, error/overload ownership,
shutdown and stale-output handling remain required before any promotion.

## Combined v16 + worker comparison at fifty fighters

Fresh rig `worldboss-rehearsal-132a2543538557c0` retained all optional effects,
normal client physics, `doPokemonSpawning:true`, the original thresholds and
every raw setup sample. It passed all fourteen functional assertions with fifty
native fighters plus one observer; all fifty had credited damage by Hold.
Recovery preserved 354,465/400,000 HP. The early predicate skipped 153,501
protected-position matching passes and the late guard refused 7,970 entities.
The episode included 55 live starts, accounting for chained duels.

| Phase | Ticks | p95 ms | p99 ms | Maximum ms | Maximum start interval ms | Actual clients |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Gathering, including first admission | 1,302 | 40.9705 | 59.5972 | 157.8319 | 159.0861 | 51 |
| Combat | 711 | 31.5232 | 39.1862 | 58.5357 | 58.7190 | 51 |
| Hold/recovery | 280 | 31.2192 | 40.5106 | 45.1771 | 50.7010 | 51 |

Every selected phase met the nominal limits, with no dropped ticks or stack
samples. The full record retained 9,189 ticks, including setup: setup maximum
542.3308 ms and all-recorded maximum start interval 1895.6086 ms. Setup tick
1662 includes cold class/entity/AI initialization. Another retained setup tick,
4184 (439.918 ms), samples file I/O under the vanilla `PlayerList.saveAll` chain;
cached mappings identify `class_3442.method_14912` as `ServerStatsCounter.save`.
The earlier vanilla player-data save stall remains separately recorded. This
short trial does not prove autosave-inclusive event performance or client FPS.

There were 646 completed jobs, no queued/discarded work or failures, and all
1,035 posted callbacks were applied. Peak queue depth was five; maximum queue
wait 116.0380 ms, worker execution 287.1452 ms, callback delivery wait 33.1613 ms,
and measured callback batch 1.6769 ms. Worker time is not server-tick time and
has not been deleted from the evidence. The actual simulator map retained only
the recorded bootstrap ID `310c820f-ccdf-4edc-a2fe-bca6490d38ab`, with zero live
and zero unclassified simulator battles after cleanup.

Evidence: `pilot-1795131472439651600/report.json`,
`logs/wbperf-fighters50-a7b023be-9e7b-49db-9c9e-e290a9adc65f.json`, its adjacent
`.comparison.json` / `.gate.json`, and `logs/worker-cleanup-stats.json`.
The official report remains **INCOMPLETE** because this is a runtime prototype.
Randomized properties and different host/workload conditions prevent claiming
an isolated causal speedup percentage between separate rigs. The result supports
this implementation direction; it does not close the production release gate.

Required promotion work is concrete: serialize metadata/reload/context access
with gameplay, define pending-start/closing acknowledgments and stale-input
rules, fail safely into persisted Hold on overload/engine failure, and drain or
reject work correctly on shutdown/restart without falsely reporting successful
cleanup. Other mods' synchronous start expectations and actual reward settlement
need integration verification. Then rerun the original tick-only gate with an
actual production dispatcher, full parties/audience, long autosave-inclusive
events, failure/recovery cases and real clients. No production deployment,
season activation, commit or push occurred during this continuation.
