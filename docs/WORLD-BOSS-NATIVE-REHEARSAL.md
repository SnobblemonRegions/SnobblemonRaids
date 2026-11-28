# World Boss disposable native rehearsal

The current admission diagnosis, v16 early spawn filter, and explicitly rejected
or experimental dispatcher trials are recorded in
[WORLD-BOSS-ADMISSION-DIAGNOSIS-2026-11-19.md](WORLD-BOSS-ADMISSION-DIAGNOSIS-2026-11-19.md).
`--require-natural-spawn-guard` leaves Pokémon spawning enabled and records both
late entity refusals and early protected-position matching passes. Positive
evidence from either layer is required. A prototype's nominal timing success
does not approve production capacity; the reporter rejects its diagnostic flag.

The Windows runner creates a new installation under a supplied output root. It
copies launcher/runtime files, dependency JARs and an already accepted `eula.txt`.
It excludes old CobbleRaids JARs and never copies source worlds, configuration,
economy data, recovery heads, operators or player data. Its fresh configuration
has no live season. The pilot invokes only `worldboss test start`, whose live
rewards and outcome listeners are disabled.

Use the built runtime JAR and cached runtime directory. The launcher can download
its own dependencies: capture actual loader/mod versions from `logs/latest.log`;
copying runtime libraries alone does not establish an exact production modpack.

```powershell
python validation/smoke/worldboss_rehearsal.py prepare `
  --runtime L:/path/to/cached-test-runtime `
  --jar build/libs/CobbleRaids-0.8.210-projector-follow.jar `
  --root build --game-port 25741 --rcon-port 25742
```

Use the unique directory printed by prepare as `<rig>` below. Ports are examples;
both must be free. Game/RCON bind to loopback. A generated RCON password stays in
the disposable `server.properties`; do not publish that file.

To test the compatible AscensionLib source build, add
`--ascensionlib-jar L:/AscensionLib/ascensionlib/build/libs/AscensionLib-1.0.0+fabric.jar`
to prepare. Only the new rig receives that replacement. Launch checks all recorded
dependency hashes as well as the main mod hash. Add `--require-integration-v2` to
the pilot to require the actual native registration diagnostic. This does not
invoke economic callbacks, which test encounters deliberately suppress.

```powershell
python validation/smoke/worldboss_rehearsal.py launch --rig <rig> `
  --java 'C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe'
# Inspect console.out.log and wait for startup completion before the next command.
python validation/smoke/worldboss_native_pilot.py --rig <rig>
python validation/smoke/worldboss_rehearsal.py command --rig <rig> 'cr worldboss stop'
python validation/smoke/worldboss_rehearsal.py command --rig <rig> 'stop'
```

The runner records its Java PID and absolute launcher path. Before each command,
it checks that the PID still runs that executable/launcher and owns both private
listeners. Unknown, reused, missing or nonprivate targets fail before RCON connects.
It rejects changed identity/port/world settings and a changed prepared mod JAR at
launch. This is an accidental-target safety check, not an authentication boundary
against another local administrator. Leave the private manifest unchanged.

The pilot builds a flat stone fixture with a 96-block arena, dispersed arrivals,
return platform and observer terrace. It preloads 406 fixture chunks. These are
technical test values, not an approved production layout, Dref or total native
chunk/memory budget. Its five fighters and one observer use six cached Mineflayer
clients (`validation/smoke/node_modules/mineflayer`); install compatible bot
dependencies separately if absent. The client driver answers actual move prompts.
It verifies connection, native battle starts, observer separation, move responses
and hold cleanup, then requests recovery. It always cleans up its spawned bots;
the server remains running for inspection until explicitly stopped as above.

Results live in `<rig>/pilot-*/report.json`, `commands.json`, per-client stdout and
stderr, plus the native server logs. Failed checks exit nonzero and preserve
evidence. Review native logs independently for contained faults and integration
warnings. A functional pilot pass does not imply a clean modpack or performance
pass. The `/tick query` response describes one rolling 100-tick window; it is not
a whole-run percentile and cannot prove maximum stall, memory or client FPS.

## First native trial, November 18, 2026

Runtime: Minecraft 1.21.1, Fabric Loader 0.19.5, Cobblemon 1.8.2,
CobbleRaids 0.8.210-projector-follow, Java 21.0.12. Source runtime was the historical
integration rig; only the newly created installation was booted/driven. The older
world was not used. Actual runtime and evidence paths are recorded in the handoff.

The first attempt reached five simultaneous owned battles, a locked roster of
five, fixed test HP of 40,000 and damage from all five fighters, with one separate
terrace observer. It failed the move-telemetry assertion because the old bot did
not emit its documented `SENT` move events. That omission was fixed; prompt and
decoded request events are now logged too. An initially unloaded return platform
was corrected and the runner now rejects native command errors. A second attempt
failed on a same-block fixture no-op; placement now uses `execute unless block`
so reruns do not misclassify that no-op. Failed evidence is retained.

The corrected pilot passed its six functional assertions: six clients connected,
five native battles started, the observer received no battle, five clients sent
actual move responses, and all five received battle-end events after staff Hold.
Server status independently confirmed five damage contributors and zero owned
duels while HELD. Recover entered REGROUP with the same 37,953/40,000 HP. One
100-tick combat window reported average 20.0 ms, p95 35.2 ms and p99 95.5 ms;
this does not erase earlier stalls or close performance signoff. A bounded JFR
capture was requested for further investigation.

The completed 90-second capture is `worldboss-pilot.jfr`, with extracted events
and `profile-summary.json` beside it. It contains 498 execution samples, 66 sampled
file writes and eight GC events. Forty sampled World Boss cache writes ran on the
server thread, with durations approximately 10–44.35 ms. This identifies a
specific synchronous persistence cost to investigate; it does not prove the
cause of the earlier 339 ms subsystem run. Most sampled CPU stacks were native
Minecraft work. The 102.02 ms slowest captured write belonged to Cobblemon's
background player-data executor, so it must not be reported as a World Boss
server-thread stall. A separate `runtime-inventory.json` records actual JAR hashes.
The disposable server was stopped cleanly and both rehearsal ports were released.

Release issues exposed by the real runtime:

- Cached AscensionLib 1.0.0 expects World Boss API v1 and disabled its external
  reward integration against v2. A compatible integration build and verification
  are required; do not weaken API version checks to hide the mismatch.
- Native logs contain slow World Boss runs, including a 339.1 ms subsystem run.
  A later 100-tick query had p99 841.3 ms. Neither is a whole-run distribution or
  causal profile. Performance signoff remains open even with only six clients.
- Cobblemon logged missing/corrupt `.old` first-login data for newly generated test
  accounts. Loot-table validation also warned about absent optional integrations.
  Preserve and investigate these separately; no existing player data was copied.
- Recorded voice assets remain 0/9. No graphical client FPS, full 25-observer,
  10/30/50-fighter, recovery-after-kill, reward/Worldheart delivery or production
  protection-plugin verification has been completed by this pilot.

Run `python -m unittest discover -s validation/smoke -p test_worldboss_rehearsal.py -v`
for the target-isolation safety tests. The wider release matrix remains in
[WORLD-BOSS-ARRIVALS-AND-AUDIENCE.md](WORLD-BOSS-ARRIVALS-AND-AUDIENCE.md).

## Persistence and integration fixes

The duplicate NBT cache rewrite was reproduced by a failing regression against
the actual journal-backed `WorldBossState.commitRoot` path. Normal commits now
force only the authoritative journal and external head; startup and the clean
World Boss stopping checkpoint refresh the rebuildable NBT projection. Four new
tests verify absent/stale cache recovery, explicit refresh, failed cache refresh
without loss of acknowledged state, and immutable secured recipient publication.
The existing journal failure/restore/compaction/process-kill tests still pass.

AscensionLib's optional bridge now accepts checked v1/v2 contracts. V1 preserves
its old damage policy; v2 requires the additive `qualifiedPlayers(UUID)` API query
and uses the exact committed recipients. This includes support players with zero
damage and excludes unqualified damage dealers. Seven contract tests cover that
policy and rejection of unknown, missing, malformed and unsecured contracts. The
existing essence amounts and idempotency keys are unchanged. Earlier v2 builds
without the eligibility query fail closed rather than paying by an old rule.

Both built mods were then run together in a fresh native pilot under
`L:/SnobblemonRaids/build/worldboss-rehearsal-1ae67a88d33c3bb4`. All ten functional
assertions passed, including actual v2 registration, native battle/hold/recover,
and an unchanged cache while the journal advanced. The JFR FileWrite selection
reported no cache writes. The 100-tick sample was average 13.9 ms, p95 18.9 ms,
p99 22.6 ms. These figures do **not** close performance signoff: later log review
found long tasks, including a World Boss subsystem maximum of 867.23 ms.

Full-stack JFR **FileForce** analysis then located server-thread journal forces
up to 598.16 ms. Counting FileWrite events alone misses fsync stalls. Retained
artifacts include `profile-events-full.json`, `profile-blocking.json` and
`profile-force-summary.json`. JFR CLI printing defaults to a short stack; use
`--stack-depth 128` when investigating callers. CPU samples also show background
native chunk and CobbleTowers construction work; GC's recorded longest pause was
37.54 ms. Neither those samples nor the good short tick window erase the journal
force latency.

`worldboss_storage_probe.py` provides a reproducible diagnostic using private,
automatically removed temporary files. It writes 4 KiB, flushes and calls OS fsync
for each sample. Its percentiles describe only those writes:

```powershell
python validation/smoke/worldboss_storage_probe.py `
  --directory L:/SnobblemonRaids/build `
  --directory C:/Users/Facel/SnobblemonRaids `
  --samples 32 --output build/worldboss-storage-baseline.json
```

The saved 32-write diagnostic measured L: median 25.31 ms / p95 36.20 ms versus C:
median 0.33 ms / p95 0.77 ms. It does not establish production hardware or whole-run
latency. A second fresh native rig on the faster path tests the same built mod pair
without moving any existing world or disabling forcing. Production storage must
be measured under the actual modpack's load. Removing durability to hide a slow
storage path is not an acceptable tuning option.

The faster-path trial completed in
`C:/Users/Facel/SnobblemonRaids/.rehearsals/worldboss-rehearsal-acd65467e6488f32`.
Its `pilot-1795051137799272000/report.json` passed all ten functional assertions
with the identical built mod pair. JFR enabled `jdk.FileForce#threshold=0ms` to
include fast forces: 64 journal forces measured median 0.630 ms and maximum
1.324 ms. The slowest of all 128 force events, including external head files,
was 4.738 ms. No runtime cache rewrite occurred. This isolates the slow-path
persistence cost; it is not a claim that all tasks or ticks meet the release gates.

The faster-path 100-tick combat sample reported average 19.7 ms, p95 34.0 ms,
p99 105.7 ms. The mod's reporting window retained a World Boss subsystem maximum
of 949.23 ms; its cause/timing has not been attributed to a specific operation.
It must not be silently excluded as startup, profiler attachment or another mod.
The longest recorded GC pause was 31.42 ms, and journal force latency in this
capture does not explain that subsystem maximum. Whole-run tick distributions,
timestamped slow-operation attribution and full attendance trials remain required.
Both new rigs saved their worlds, stopped cleanly and released ports 25741/25742.

The compatible build pair is identified by SHA-256 (versions were not bumped):

| Runtime artifact | SHA-256 |
| --- | --- |
| CobbleRaids-0.8.210-projector-follow.jar | `ad658e5ae7abc80d00d11d2809fdfad019a8651b078535b7286d5530290e1d63` |
| AscensionLib-1.0.0+fabric.jar | `cf9bea4d3eef1fe015081ee984ea950d1ac9f8783a38e9a4b2c671ea46d1ce2f` |

Verification: complete CobbleRaids/client build 1,029 tests; AscensionLib's four
configured modules 545 tests; eight target-safety tests; source/resources, raid
integrity, callback barriers, logging, compiled/remapped API and mixin guards.
All passed. No production deployment, season activation or economic callback
delivery occurred. Real external reward delivery remains an end-to-end gate even
though native API registration and eligibility contracts are verified.

The subsequent raw-tick capacity investigation, including reproduced 30/50-player
failures and the pacing/startup-warmup candidate, is recorded in
[WORLD-BOSS-PERFORMANCE-2026-11-18.md](WORLD-BOSS-PERFORMANCE-2026-11-18.md).
The build hashes above identify the earlier storage/integration trial, not the
new candidate. Short rolling tick samples above do not supersede the raw-tick gate.
