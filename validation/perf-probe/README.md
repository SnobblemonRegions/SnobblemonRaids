# Disposable full-tick performance probe

This standalone Fabric mod is a validation tool. It is not a subproject of the
production build and must not be deployed. It refuses startup without the fresh
rehearsal launcher's `rehearsal.json`. No game behavior or task dispatch is replaced.

The November 20 arrival rehearsal probe bounds diagnostic stack walks at three
per slow tick (first after 100 ms, then at least 200 ms apart). Raw tick durations
and start intervals remain complete. Recordings declare `stallSamplingPolicy`;
the 512-stack cap, dropped-sample counter and overflow rejection remain active.
Probe unit tests: 22. This policy change does not relax acceptance thresholds.

The v25 pilot option `--require-party-profiles` waits for actual AscensionLib
assignment receipts from every synthetic party and two observed native tick
boundaries (`wbperf tick-id`) before gathering starts. Setup stays in the raw
recording. This scopes the trial to initialized parties; concurrent SDK acquisition
latency remains a separate risk. Explicit autosave pressure checks that both
player and world NBT queues finish healthy, with submitted/completed counts equal.

Post-timing `wbperf world-save-check` uses actual native SavedData and level
metadata entry points with isolated compatibility clients. It checks Main-thread
serialization, read barriers, later dirty mutations, forced latest writes and
`level.dat_old` ordering. It changes a disposable gamerule temporarily and restores
it in a finally block. Like the other compatibility fixtures it permanently
taints that process for subsequent capacity recordings.

The v26 post-timing player-save check also verifies native advancement pacing,
flush-before-reload and forced-save barriers. Timing pilots require the separate
advancement queue to finish all scheduled or superseded method invocations.
Those counters do not add file durability guarantees to vanilla advancement saves.

JFR activity now taints a process's performance records for capacity approval,
even if profiling stops before the raw recording ends. The raw validator rejects
`diagnosticJfrRecording:true`. Use a fresh, unprofiled process for acceptance.

Post-timing fixtures include `wbperf spawn-area-check WBCompat_name` (native
protected/outside/mixed/reloaded/non-player/custom-generator routing) and the
empty-server TEST-only showpiece interruption harness
`validation/smoke/worldboss_showpiece_reentry.py`. The latter stops an occurrence
from its native entity-load hook and checks actor disposal and tracking. Arming
it during timing is forbidden; a process that has used it cannot begin another
performance recording. These fixtures never belong in a deployed modpack.

```powershell
.\gradlew.bat -p validation/perf-probe --offline test build --console=plain
```

Give `worldboss_rehearsal.py prepare` an explicit
`--probe-jar validation/perf-probe/build/libs/worldboss-perf-probe-1.0.0.jar`
along with the built main/integration JARs and the fresh fast-storage output root.
Use the identity-checked launcher. The native mixin requires exactly one matching
`runServer` call to `tickServer`; missing/remapped targets fail startup rather than
producing an empty green recording. Its injection points surround the caller and
therefore include Fabric tick HEAD/RETURN listeners in the timed span.

The console-only commands are `wbperf start <label>`, `wbperf mark <phase>` and
`wbperf stop`. Labels are bounded filename-safe words. Stop disables recording
before writing the JSON evidence to a unique `logs/wbperf-*.json` file. The recorded
tick path writes only to bounded memory. At 24,000 samples, overflow is reported
and cannot pass the gate. A daemon samples the recorded server thread every 25 ms
once a tick has exceeded 100 ms, up to 512 captured stacks. Superseded samples are
discarded; overflow is explicit. Sampling introduces diagnostic overhead.
Each stack preserves the first 128 frames and, when deeper, the last 32 outer
caller frames with an explicit omission marker. This retains native admission
and autosave callers below deep simulator stacks.

Raw records contain phase, actual player count, complete `tickServer` call wall
time, and start-to-start intervals. The latter include outer queued tasks and idle;
they are not MSPT. Partial boundary ticks are not recorded. All setup samples are
retained. The pilot labels its physical fixture setup separately, then gathering,
combat and recovery. A phase starts when its console marker is processed, rather
than pretending to be an exact simulator state transition.

```powershell
python validation/smoke/worldboss_native_pilot.py --rig <fresh-rig> `
  --require-integration-v2 --require-startup-warmup --probe `
  --fighters 30 --combat-seconds 30
python validation/smoke/worldboss_perf_report.py <logs/wbperf-file.json> `
  --phase gathering --phase combat --phase recovery --min-players 31
```

The functional pilot result and raw performance result are separate. The latter
requires at least 100 raw ticks per selected phase, no recording overflow, the
declared actual population throughout, p95 below 50 ms, p99 below 100 ms, no tick
above 250 ms and no start interval above 250 ms. Percentiles are computed from
every raw sample using nearest rank. No average of rolling percentiles is used.
One extreme stall fails even when p99 looks healthy. Setup/build peaks remain in
the output and are not silently discarded.

Fighter choices are 5/10/30/50, each with one additional observer. Each account is
fresh per trial and receives one level-100 Glaceon. This does not test six-Pokemon
parties, graphical clients, every species/asset set, voice, ordinary protections,
economic callbacks or a full event clear. Bots and server share the host. Include
those limits, exact hashes, and actual concurrent duel counts when interpreting
results. Never promote a probe-bearing calibration fingerprint to production.

`--stationary-clients` is an explicit diagnostic comparison mode for the pilot.
It disables local Mineflayer gravity/collision simulation while retaining the
library's normal position updates and teleport acknowledgements. Reports record
`client_physics_enabled`; absence of this field in older reports means the
original enabled mode. This option did not eliminate movement warnings or close
the capacity gate in the recorded comparison. Do not present it as a server fix
or silently mix its results with the original fixture. Native Pokemon moves,
abilities, IVs and optional integration traits can vary across fresh accounts;
these trials do not establish an isolated causal speedup percentage.

Cobblemon's `doPokemonSpawning` rule is separate from vanilla `doMobSpawning`.
The pilot records the actual Pokemon spawning rule. Use `--no-wild-spawns` for
an explicitly controlled empty fixture, or `--require-natural-spawn-guard` to
leave Pokemon spawning enabled and require a positive native protected-arena
late-refusal or early position-evaluation counter. Reports retain both counters;
early evaluation counts are matching passes, not unique cells or created entities.
These options are mutually exclusive and affect only the
owned throwaway rig. Older recordings did not disable Cobblemon spawning: one
v9 trial correctly failed attendance because a wild Wooloo occupied a fighter's
placement space. Keep that run as incomplete evidence rather than removing the
Pokemon and relabeling the same trial as a pass.

## Source-level native bootstrap tracing (diagnostic only)

`wbperf trace-next` arms one capture per fresh process, before starting a pilot.
The next native Graal battle records JavaScript root names/source lines, bounded
nested inclusive/self timings, the synthetic account bootstrap messages, direct
Java output-callback wall time and GC collection-time delta. Results appear as
`bootstrapTrace` in the saved full-tick JSON. The probe loads Graal's existing
22.3.0 SDK at runtime; it does not bundle another SDK or alter the production mod.

The source listener changes execution cost. Root timing subtrees overlap;
inclusive totals must not be added, and self time includes listener overhead.
Only the slowest 64 root invocations by self time and by inclusive time are
retained; every child still contributes to parent subtraction. Maximum nesting
is 512 and overflow/unbalanced returns are explicit. Listener failures are
recorded instead of escaping into the native battle. Host callback wall time
may include nested work. A missing native return is marked incomplete.

Every recording from a process armed for this trace has
`diagnosticJavascriptTrace:true`. The capacity reporter rejects it, including
subsequent recordings after listener removal. Use a fresh rig and the original
uninstrumented tick probe for the actual performance gate. Never deploy this
diagnostic JAR. See the focused admission diagnosis in `docs` for the first trace.

`wbperf boundaries-next` instead records the first native call's wall time,
server-thread CPU time (when available), nested host-callback wall/CPU totals,
time to the first host callback and GC collection-time delta. It does not access
the context or attach a JavaScript execution listener. `sourceRoots:false`
distinguishes this mode; `rootCalls` remains zero. Time outside host callbacks
includes the simulator and bridge/probe overhead, so it is not exclusive
JavaScript CPU time. Windows thread CPU counters may have coarse resolution.
Nested callbacks are counted but only their outer duration contributes to totals.

Both modes use the historical `diagnosticJavascriptTrace:true` rejection flag.
Boundary-only captures also cannot approve capacity; use the original tick-only
probe on a fresh process for that gate. Monitoring metadata is resolved when
arming, before admission. Native-return absence is closed at the end of the tick
and explicitly marked incomplete. No disk output is written during capture.

`wbperf export-species` writes a uniquely named read-only species registry snapshot
in the private rig's logs. `validation/smoke/showdown_runtime_replay.js` uses this
with a captured native bootstrap for an offline Node experiment. It requires
matching gameplay artifacts and verifies both initial request JSON objects
exactly; it records source/species/script hashes and raw cold/repeat timings.
It creates no Minecraft actors, opens no network connection and awards nothing.
This experiment cannot approve a different runtime or native dispatch behavior.

## Serial Graal worker prototype — never production

The gameplay v18 isolated world-boss simulator is a separate implementation in
the main mod; do not enable this old shared-context prototype to test it.
`wbperf ordinary-duel <WBCompat_name> <WBCompat_name>` is a console-only fixture
for fresh synthetic clients in the nonce-owned rig. It invokes native PvP and
reports whether its ID was incorrectly world-boss-routed. The driver is
`validation/smoke/worldboss_simulator_compat.py`; it checks UI, moves, native
administrative cleanup and unchanged boss HP. It is not an economy/full-party
signoff. No trace or async-prototype marker is needed for this command.

`wbperf player-save-check <WBCompat_name> <WBCompat_name>` runs only after tick
recording, with two connected synthetic clients. Through the native server save
batch it queues automatic player snapshots, changes tags, forces a flushing save,
then reads native current/backup NBT to verify latest-state and previous-snapshot
ordering. It requires an active arena TEST fixture supplied by the compatibility
driver. Using it prevents another timing recording in that process. It is not a
power-loss test or proof of durability beyond vanilla's player-file contract.
The ordinary compatibility driver includes this check after native PvP and area
spawn routing; it does not inject extra autosaves into measured phases.

For explicit autosave pressure, add `--exercise-player-autosave` to the native
pilot. Its console-only `wbperf automatic-player-save` command requires a timed
recording, an idle fixture or active TEST encounter, and exclusively synthetic pilot accounts.
It calls the real native `saveEverything(true,false,false)` before the encounter and during combat and
checks one accepted player snapshot per online account. The driver later checks
that the queue drained without failure. Chunk-save work is included too. Every
tick and dispatch interval remains subject to the original performance gate.
The pilot also marks `initialization` after all native clients connect and before
party grants. Include `--phase initialization` alongside gathering/combat/recovery
when gating these new runs; it includes native profile assignment and the idle
autosave. Setup/world-building/joins remain in the raw recording. Earlier runs
without this phase cannot establish its performance.

`wbperf card-stat-check <WBCompat_name>` calls Cards' actual bonus calculation
without a drop event and reports the returned value plus scoped DEBUG-call delta.
The compatibility driver checks the same empty equipment outside and inside
the arena, requiring identical native bonus and the expected routing. This
post-timing fixture also prevents future timing in the same process.

The probe contains an explicitly gated experiment, not a gameplay feature.
Only a fresh rig with `wbperf-async-native.txt` containing its exact manifest
nonce enables it. The launcher still verifies the unchanged gameplay/dependency
hashes. Startup's native dummy battle stays synchronous. After SERVER_STARTED,
live start/send/end calls run FIFO on one bounded worker; copied message arrays
and complete output batches preserve call/message order. Batches are applied
on the server thread, with an assertion before native actor dispatch. Queue
saturation and task failure poison the queue instead of running work on callers.

The November 20 lifecycle continuation routes output through the tested
`SimulatorCallbackGate`: failed engine operations discard partial output;
shutdown closes delivery before worker drain; engine/callback failure invalidates
pending batches; each callback rechecks validity. Posted, applied and discarded
counts are separate. Shutdown/termination and drain timeouts are explicit.
Calls after shutdown reject rather than falling back to synchronous startup.
A second server in the same JVM is deliberately unsupported and rejected.

`wbperf simulator-stats`, used after the tick recording stops, reports queue
counts, failures, maximum queue/execution/delivery durations and real simulator
battle IDs. It distinguishes bootstrap IDs from live IDs: the native dummy
battle must not be mistaken for a leaked live duel or silently removed.
The query is performed on the worker with a two-second timeout. Raw tick
recordings include `diagnosticRuntimePrototype:true` and are rejected for
capacity approval even if nominal event-phase limits are met.

This prototype is limited to the disposable pilot. Metadata/reload/context
serialization, overload/restart failure recovery, partial admission ownership,
battle-specific closing/stale-input rules and other mods' synchronous expectations need a production
design and integration verification before any promotion. Do not use prototype
evidence or a probe-bearing fingerprint to activate a live season. Keep the original tick-only probe
for acceptance tests of an actual production implementation.

## Native process-crash inspection

`wbperf recovery-snapshot` is a read-only, console-only inspector for synthetic
accounts in an active TEST event. It saves runtime/journal identity, party and
marked inventory evidence to a unique JSON file under the private rig's logs.
It refuses to run during tick recording and permanently prevents subsequent
capacity recording in that process. Recovery-only manifests, restarted rigs and
rigs containing `logs/recovery-expected.nbt` are excluded from capacity recording
at initialization too. Do not use recovery inspection to obtain timing approval.

Prepare a fresh rig with `worldboss_rehearsal.py prepare --recovery-only` and the
normal explicit runtime/JAR arguments, launch it, then run
`python validation/smoke/worldboss_native_crash.py --rig <private-rig>`.
The driver initializes five fighters and an observer, saves a marked baseline,
forcibly terminates the birth-identified native JVM, preserves the journal/head,
omits the rebuildable projection and restarts the same world. It requires exact
replayed progress, restored party identities/marked stack counts, one standing
boss, no stale duels and five fresh battles after regroup. It stops the replacement
server afterward. Process termination is restricted to the nonce-owned rig and
the same held Windows process handle verified against its executable and birth.

See [the November 21 native crash evidence](../../docs/WORLD-BOSS-NATIVE-CRASH-2026-11-21.md)
for the caught startup chunk-loading bug, repair and 28-check pass. This saved
baseline test does not cover interrupted economic/native file writes or power
loss. That first recovery rehearsal used probe SHA-256
`8256bae24bb76b926a5b4c91335392ea3a5dd329862f0e2f4bb5ed66e2ead9f5`;
older capacity evidence using the earlier tick probe retains its original hash.
The later [paired initialization-I/O rehearsal](../../docs/WORLD-BOSS-INITIALIZATION-IO-2026-11-21.md)
uses probe `5fb6b28bf27eadecefb21f6252d6adab08fa4a1360ffa135ca349b2f414bbaa7`
and passes 31 native recovery checks, including read-only SDK profile/receipt
identity across restart. Those inspector calls remain excluded from timing.
