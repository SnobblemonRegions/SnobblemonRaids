# World boss performance investigation — November 18, 2026

Performance approval remains open. Functional success, a short combat average,
and successful AscensionLib API registration do not constitute capacity approval.
The authoritative working tree is `L:\SnobblemonRaids`; recordings below are in
`C:\Users\Facel\SnobblemonRaids\.rehearsals`. No production deployment or season
activation forms part of this investigation.

The November 19 source-level continuation is recorded in
[WORLD-BOSS-ADMISSION-DIAGNOSIS-2026-11-19.md](WORLD-BOSS-ADMISSION-DIAGNOSIS-2026-11-19.md).
It identifies skipped packed-team/effect startup paths and invalid native Team
Preview choices. The v11/v12 fixes passed functional checks but still failed
admission maxima (316.5848/333.0817 ms). Diagnostic JavaScript traces cannot
approve capacity. The later entry there records the v13 native Java host-call
warmup, the rejected v14 server-thread experiment, and retained v15. V15's
five-fighter cold entry still fails at 755.6243 ms; the original admission
blocker remains unresolved. Earlier hashes/results below are
historical, not approval of the current artifact.

Further November 19 work separates native simulation from Java dispatch using
boundary-only wall/CPU measurements, and records offline runtime replay plus a
serial-worker experiment. The worker's five-fighter gathering maximum was
75.6695 ms; its first fifty-fighter run still failed gathering/combat/recovery
limits. That run verified all live simulator IDs cleaned up and identified
natural-spawn condition CPU plus vanilla `PlayerDataStorage.save`/`saveAll` I/O.
Policy v16 adds scoped early Pokémon spawn predicates without changing global
spawning or save durability. Its synchronous native control passes fourteen
functional checks but still fails admission at 604.8614 ms. The linked diagnosis
contains exact hashes, source boundaries, raw files and the subsequent combined
comparison. Production performance approval remains blocked.
The latest combined v16 + worker experiment met all nominal event-phase limits
with fifty fighters plus one observer (maxima 157.8319/58.5357/45.1771 ms), verified
all live simulator battles cleaned up, and retained all setup/save spikes. Its
official gate remains INCOMPLETE because it uses the diagnostic dispatcher;
there is no production deployment or automatic calibration promotion.

## Reproduced failures

The original 949 ms subsystem maximum was reproduced as a failing assertion.
A separate rehearsal-only Fabric probe now surrounds the complete native
`tickServer` invocation, including Fabric listeners. It retains individual ticks,
actual player counts, start intervals and bounded stall stacks. The reporting
gate uses all samples in explicitly selected phases; a single tick or start
interval above 250 ms fails. At least 100 samples and the declared population are
required, with p95 below 50 ms and p99 below 100 ms. Setup peaks remain visible.
See `validation/perf-probe/README.md` for scope and reproduction.

Selected raw results (milliseconds; gathering and combat p95 / p99 / maximum):

| Rig suffix | Fighters + observer | Gathering | Combat | Outcome |
| --- | ---: | --- | --- | --- |
| `073ef8cd9766409f` | 5 + 1, cold | 27.61 / 47.06 / 1005.07 | 23.68 / 28.99 / 40.59 | FAIL |
| `073ef8cd9766409f` | 30 + 1, warm | 40.81 / 112.57 / 753.02 | 40.87 / 62.22 / 105.75 | FAIL |
| `073ef8cd9766409f` | 50 + 1, warm | 60.75 / 114.41 / 833.34 | 54.54 / 69.18 / 81.78 | FAIL |
| `17b3ea86714fe981` | 30 + 1, simple startup warmup | 41.45 / 86.55 / 817.87 | 45.76 / 59.22 / 192.60 | FAIL |
| `99e1939467cb1d2d` | 30 + 1, local raid-stream warmup | 41.25 / 90.89 / 687.75 | 44.15 / 66.22 / 489.37 | FAIL |

Each listed trial started real native battles and submitted moves. These are
headless, single-Glaceon fixtures, not six-Pokemon parties or graphical clients.
Actual simultaneous duels vary as encounters finish and restart. The 50-fighter
fixture does not assert 50 simultaneous duels throughout its combat window.

Stall stacks identify expensive Graal/Truffle simulator bootstrap during native
`BattleRegistry.startShowdown`. Simple direct simulator warmup did not remove
that cost. Separate 425 ms and 489 ms combat stalls include vanilla bulk player
statistics/advancement autosave file writes. These remain counted; autosaves and
durable world boss journal forcing have not been disabled to improve results.
Previous storage measurements also found a 598 ms journal force on L:, while the
same force path on C: peaked at 1.324 ms in a separate capture. Storage directory
and host conditions are part of the evidence, not assumed production facts.

## Candidate changes under native verification

- Native duel admission attempts are paced to one per 250 ms, with no catch-up
  burst after a delayed tick. FIFO admission, the fifty-player cap and durable
  ownership checks remain. Fifty initial attempts therefore take approximately
  12.5 seconds; this is an intentional queue behavior change requiring calibration.
- A once-per-context startup warmup exercises three bounded simulator-only raid
  battles through the native JavaScript bootstrap and output pump, using private
  output sinks and fixed-size host message arrays. It creates no Minecraft
  entities or native Java battle registrations and calls no economic listeners.
  Native host access restrictions are retained. Temporary bindings and simulator
  entries are removed. Failure is reported and cached rather than retried during
  a live reload; fatal JVM/linkage errors are not swallowed.
- Opening policy fingerprint v7 includes the pacing and startup-warmup policy.
  Previous v6 calibration cannot approve this behavior. No replacement Dref or
  production health value is inferred from this technical fixture.

Native warmup failures found during development were rejected before bots joined:
missing PP metadata, an automatic boss choice ending before a second input,
the deliberately absent Java namespace, and a host factory method blocked by
explicit host access. These failed boots are evidence, not capacity trials.

## Remaining release evidence

The final candidate must retain its exact JAR hashes and raw phase recordings.
Even a passing short trial would not approve a production launch: the actual
deployment host/storage/modpack, long events including autosaves, full parties
and forced switches, audience load, real client frame rates, voice assets,
all ward outcomes, event clear/recovery and actual external reward delivery
remain separate checks. The rehearsal probe is diagnostic software and must
not be deployed or used to approve a production calibration fingerprint.

## Native candidate results

Fresh rig `worldboss-rehearsal-d06ec47b4a17acb2` successfully completed the native
startup warmup in 2,192 ms before accepting players. The first attendance trial
was fifty fighters plus one observer, with fresh account names. All twelve pilot
assertions passed, including actual API-v2 registration, native battles and move
responses, observer separation, Hold battle cleanup and journal advancement
without rewriting the rebuildable NBT cache. All fifty players dealt damage.
Combat status snapshots showed 26, 33 and 39 simultaneous duels; other players
were between rounds or queued. Hold then Recover preserved HP at 324,259/400,000.

The performance gate **failed**:

| Phase | Raw ticks | p95 ms | p99 ms | Max ms | Max start interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gathering | 2,084 | 60.4112 | 105.7352 | 792.5869 | 812.7082 |
| Combat | 636 | 60.7382 | 72.9101 | 101.3306 | 171.3049 |
| Recovery | 234 | 40.9203 | 46.9936 | 52.5195 | 118.8499 |

Every phase retained 51 actual players, with no tick-recording overflow. The
792.59 ms gathering tick still samples Graal/Truffle beneath native
`GraalShowdownService.startBattle`, reached from world boss admission. A separate
554.22 ms gathering tick samples vanilla advancement path resolution and entity
save work beneath autosave. Successful startup warmup therefore does **not**
establish that native admission is warm or that performance improved. Do not
promote this optimization on that assumption. The 100-tick vanilla command
reported combat p95 37.8 ms; the complete combat phase p95 was 60.74 ms. This
difference demonstrates why the short rolling window cannot approve the event.
The slow admission's retained samples also include native `sendFromShowdown`
and JVM class definition/loading. The private JavaScript sink deliberately does
not exercise that Java callback. This is a concrete coverage gap to investigate;
increasing simulator warmup repetitions alone is not a verified remedy.

Evidence within this rig:

- `pilot-1795061535970311100/report.json`
- `logs/wbperf-fighters50-81bdc215-ba40-473c-b383-2b925fc03e80.json`
- `logs/gate-wbperf-fighters50-81bdc215-ba40-473c-b383-2b925fc03e80.json`

Exact candidate SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| CobbleRaids | `6667134fa0ffd9e4b28e4eeea9faa798f2c0a189dd4853e55de284d1d6ad17fc` |
| AscensionLib | `cf9bea4d3eef1fe015081ee984ea950d1ac9f8783a38e9a4b2c671ea46d1ce2f` |
| Rehearsal-only probe | `450d2546b303638e28488d7ab85c2c4a61ab542851fb190b9f7c515181b7643a` |

Verification of this candidate: offline main/client test and build passed
(1,029 root + 8 client tests, zero failures/errors/skips), four simulator-script
tests, thirteen rehearsal/reporting tests and three probe tests passed. Source
and resource validation, raid integrity, 53 callback barriers, logging across
348 sources, the 16-type API boundary and 34 mixin injection guards passed.
These checks establish their stated contracts, not an absence of all bugs.

The subsequent thirty-fighter/one-observer trial used new accounts on the same
already warmed process. All twelve functional assertions passed and all thirty
players had dealt damage by Hold. Combat snapshots showed 29, 16 and 12 active
duels. Hold and Recover preserved HP at 210,769/240,000.

| Phase | Raw ticks | p95 ms | p99 ms | Max ms | Max start interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gathering | 1,618 | 40.0634 | 64.6852 | 271.7324 | 274.7527 |
| Combat | 638 | 38.6305 | 54.2731 | 62.9489 | 107.0152 |
| Recovery | 196 | 32.7096 | 44.4660 | 52.5396 | 88.3198 |

All three phases retained 31 actual players and no tick-recording overflow.
Combat and recovery passed; gathering and the overall gate **failed**. The
271.73 ms tick's stacks show vanilla autosave player-data file creation,
Cobblemon entity serialization and level-data file creation. It is retained as
a failure. This warm-process trial does not establish cold-start capacity at
thirty players, and neither trial establishes capacity on production hardware.

Evidence in the same rig:

- `pilot-1795061940417840100/report.json`
- `logs/wbperf-fighters30-eb23da76-d43d-4ca9-87e4-b243035b8a88.json`
- `logs/gate-wbperf-fighters30-eb23da76-d43d-4ca9-87e4-b243035b8a88.json`

Both trials' bots exited, the test boss was stopped, and the disposable server
shut down cleanly. Ownership checks found its PID absent and no listeners on
25741/25742. No production deployment, live-season activation, economic reward,
commit or push occurred.

The next performance work must address the real Java output/bootstrap path,
measure sustained fifty-player tick cost independently of cold admission, and
verify autosave/storage behavior on the intended host. Moving native world or
party access off-thread, disabling saves/forced journal writes, discarding
outliers, or loosening the gate would not resolve these findings safely.

## Continuation: real host callback and observed lazy types

Inspection used the actual cached Cobblemon 1.8.2 bytecode, not an assumed host
policy. `GraalShowdownService.sendFromShowdown` parses the UUID and delegates to
`ShowdownInterpreter.interpretMessage`. That method ignores messages beginning
with `{"winner":"` before battle lookup or scheduling interpreter work. Startup
now exercises that exported callback once, using a valid nil UUID and exactly
`{"winner":""}`. No battle is registered or resolved by this ignored summary.
The service is exposed only through the temporary startup binding, which is
removed on both success and failure. Host access policy is unchanged.

The v8 callback candidate was tested first in fresh rig
`worldboss-rehearsal-0798435e89736be1`, fifty fighters plus one observer, with the
original client physics. All twelve functional assertions passed; all fifty
players dealt damage and Hold/Recover preserved 355,192/400,000 HP. Combat
snapshots showed 48, 27 and 18 simultaneous duels.

| Phase | Ticks | p95 ms | p99 ms | Max ms | Max start interval ms | Gate |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Gathering | 1,693 | 30.1190 | 54.4979 | 410.3613 | 412.2480 | FAIL |
| Combat | 640 | 24.8596 | 32.1273 | 38.1675 | 72.5568 | PASS |
| Recovery | 197 | 19.7333 | 38.1206 | 412.7428 | 57.5707 | FAIL |

All phases retained 51 players without recording overflow. Startup completed in
897 ms. The original native admission stall remains, including Graal execution
and lazy class loading. The recovery maximum includes vanilla bulk autosave.
The completed combat phase passes; this does not establish a causal speedup
from the callback change or approve overall capacity.

Evidence in this rig: `pilot-1795062750006449400/report.json`,
`logs/wbperf-fighters50-9ffae19f-988e-4d1a-a163-7ec8f12e55c2.json` and its
`gate-` report. Main JAR SHA-256:
`36bcc595a2b4d4465f892ba4af050b8a928d867156e294c3b6af78aafc11b2bf`.

A fresh comparison rig `worldboss-rehearsal-9c39aa379378e617` used the same JARs
and fifty fighters plus one observer, with only local bot physics disabled via
the explicit `--stationary-clients` option. Installed Mineflayer source confirms
that vanilla position updates and teleport acknowledgements remain enabled.
All twelve functional assertions passed; all fifty players dealt damage.
Combat snapshots showed 30, 38 and 41 duels; Hold/Recover preserved
324,113/400,000 HP. This mode did not eliminate movement-correction warnings.

| Phase | Ticks | p95 ms | p99 ms | Max ms | Max start interval ms | Gate |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Gathering | 2,100 | 27.1363 | 49.3293 | 382.1484 | 384.0433 | FAIL |
| Combat | 639 | 25.3927 | 33.8340 | 369.5940 | 423.3507 | FAIL |
| Recovery | 200 | 19.2423 | 22.8525 | 32.5003 | 82.1813 | PASS |

All phases retained 51 players with no recording overflow. The gathering maximum
still samples native Graal admission; the combat maximum samples vanilla
advancement autosave. The overall recording's 685.92 ms maximum occurred in setup
and is retained. This comparison rejects the idea that simply disabling local
bot physics closes the performance gate. No server-side movement check was
disabled. Reports explicitly record `client_physics_enabled`; original mode
remains the default. Native Pokemon properties and optional integration traits
can vary across fresh accounts, so these are not identical-input microbenchmarks.

Evidence in the comparison rig: `pilot-1795063241248806200/report.json`,
`logs/wbperf-fighters50-30ac59bc-d111-448e-830c-c139ba4aab4e.json` and its
`gate-` report. Both rigs shut down cleanly before the next launch.

The callback trial's retained loading stacks identify `MathBuiltins.createNode`
line 147, which the matching cached Graal 22.3.0 source maps to `MinNodeGen`, plus
`CollectionsKt__CollectionsJVMKt.createListBuilder` beneath native request
sanitization and `BattleInitializePacket` line 70, which references its active
Pokemon DTO/companion. The v9 candidate therefore adds one pure `Math.min` startup
call and explicitly loads the list-builder and two observed DTO types with
`Class.forName(name, false, serviceLoader)`. It does not construct packets, run
their static initializers, create dummy players or scan whole packages. Missing
types report failed warmup and retain the existing once-per-context failure
cache. Policy v9 invalidates earlier v7/v8 calibration.

The native callback and math tests were observed failing before their respective
changes, then passing. Eight Java warmup tests now cover binding ownership,
failure caching, fatal linkage propagation, bounded packets, missing preloads
and loading without static initialization. The full v9 main/client build passes
1,032 + 8 = 1,040 Java tests with zero failures/errors/skips, plus seven simulator
script tests and thirteen rehearsal/reporting tests. Required source, integrity,
callback, logging, API and mixin checks pass. Native performance verification is
required before treating this preload candidate as an improvement.

## November 19: placement failure and scoped spawn protection

The first v9 preload rig, `worldboss-rehearsal-e3ba9545c6766911`, **failed** its
fifty-native-battles assertion: `WB50_d754_17` remained queued with repeated
clear-space refusals. Native entity inspection at its position
(-53.5, 65, 18.5) showed a wild Wooloo less than two blocks away. Vanilla
`doMobSpawning=false` had not disabled Cobblemon's separate
`doPokemonSpawning` rule. No Pokemon was removed and no placement safeguard was
bypassed to turn this recording green. The complete three-phase gate reports
**INCOMPLETE**, because combat/recovery measurement was never reached.

Evidence: `pilot-1795063767346196300/report.json`,
`logs/wbperf-fighters50-56d48ad2-cd6c-4ea5-8381-6c03cf7646c6.json`, its `gate-`
report, the player's log and the owned `commands.jsonl` entity inspection.
Main SHA-256 was
`16af9011e209070e38455b09abe1c4e398bf70ddfeadede84caba8b81bcb0934`.
Gathering still peaked at 475.06 ms in native Graal admission and a separate
462.89 ms tick included vanilla autosave. The previously identified lazy-math
and list-builder frames were not observed in the retained slow-tick samples;
that observation does not establish that first admission is fixed.

Added scoped protection through Cobblemon's cancellable
`POKEMON_ENTITY_SPAWN` event. Native `SingleEntitySpawnAction` bytecode confirms
the event occurs after positioning and before world insertion. New unowned
Pokemon whose bodies intersect a protected arena are refused. The callback
uses the published immutable cached regions and contains runtime failures.
Owned Pokemon are excluded. Existing entities are not swept or deleted; the
ordinary party and world-boss creation paths remain. Staff arena diagnostics
report a session-scoped counter. See [arena safety](WORLD-BOSS-ARENA-SAFETY.md)
for limits, including entities walking in and spawners bypassing this event.

The pilot now records the actual native Pokemon spawning rule. Explicit
`--no-wild-spawns` and `--require-natural-spawn-guard` modes are mutually
exclusive; the latter keeps native spawning enabled and requires real protected
spawn refusals. Older recordings did not control Cobblemon spawning and must not
be described as empty-wildlife fixtures. Policy v10 includes spawn protection
and invalidates previous v7/v8/v9 calibration.

Fresh final rig `worldboss-rehearsal-50017d4ec7209d4c` used the original client
physics, fifty fighters plus one observer, and
`--require-natural-spawn-guard`. Native Pokemon spawning was explicitly enabled
and queried as true. Startup warmup completed in 736 ms. **All fourteen functional
assertions passed**, including actual native spawn-guard exercise: the staff
counter reported 6,802 refused attempts. All fifty fighters entered battles,
submitted moves and eventually dealt damage; two snapshots showed all fifty
simultaneously duelling, with a later snapshot showing forty-three. The observer
received no private battle. Hold closed all owned duels and Recover preserved
359,840/400,000 HP. The journal advanced without NBT-cache rewriting.

| Phase | Raw ticks | p95 ms | p99 ms | Max ms | Max start interval ms | Gate |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Gathering | 1,268 | 32.2512 | 48.0127 | 365.2732 | 365.8233 | FAIL |
| Combat | 654 | 25.7306 | 29.1417 | 35.8330 | 74.2640 | PASS |
| Recovery | 189 | 25.1073 | 31.7977 | 33.0272 | 55.4253 | PASS |

All event phases retained 51 actual players and no recording overflow. The
365.27 ms tick still samples native Graal bootstrap beneath world boss admission,
including property/array-iterator work. Its exact JavaScript operation is not
fully attributed. The retained overall maximum of 661.61 ms occurred in setup
with a native chunk-processing stack and one player. It is not an event-phase
pass or an excluded production cost. Previous autosave failures remain valid
even though this short final combat/recovery window passed. No forced journal
write or autosave was disabled, and the gate thresholds did not change.

Evidence in the final rig:

- `pilot-1795064679653188000/report.json`
- `logs/wbperf-fighters50-4542e134-9ff8-41c7-bff2-6e2737cbb764.json`
- `logs/gate-wbperf-fighters50-4542e134-9ff8-41c7-bff2-6e2737cbb764.json`

Final candidate main SHA-256:
`da8b628c3a07f21817d6d90d46746c04293d90a1d9dbb66951e870e958c752c2`.
AscensionLib and the separate probe retain the hashes recorded above. The final
offline build passes 1,040 Java tests; seven script and thirteen Python regression
tests pass. Source/resources, raid integrity, 53 Fabric/receiver callback barriers,
logging, the 16-type API boundary and 34 mixin guards pass. The new Cobblemon
observable has its own guarded callback and was exercised natively.

All trial bots and disposable servers stopped cleanly. No production deployment,
season activation, World Boss payout, commit or push occurred. **Overall
performance approval remains open.** Complete native bootstrap profiling/replay,
long autosave-inclusive trials, actual deployment hardware/storage, real clients,
full parties/audience/event outcomes and real external reward/recovery delivery
remain necessary. The private simulator-only warmup is not equivalent to the
complete native battle lifecycle; adding more speculative preloads is not a
verified solution to the remaining admission stall.
