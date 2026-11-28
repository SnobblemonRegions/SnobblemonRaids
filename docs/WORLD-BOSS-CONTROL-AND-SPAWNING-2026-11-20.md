# Acknowledged controls and protected spawn work — November 20, 2026

Policy v21 addresses the remaining measured start/recovery disk stalls and
unproductive natural-spawn work around the arena. Mod version remains
`0.8.210-projector-follow`. Historical guarded v21 gameplay SHA-256:
`476507391c231370fbc0a35fc499ddf92b83b1567e6432eee8e0a6a4062ab22d`.
Nothing has been deployed or calibrated for a live season.

## Measured diagnosis

The preceding fifty-fighter trial retained a 631.4465 ms start interval,
75.6282 ms combat p95 and 276.1528 ms recovery maximum. The unchanged validator
reproduces its failed gates from the retained raw recording.

A separate JFR diagnostic rig, `worldboss-rehearsal-1819496e456f0df4`, records
`wbperf-fighters5-79311695-6f81-4b70-a635-1d5167598647.json` and
`logs/worldboss-gap.jfr`. Correlation in `logs/gap-profile-summary.json` captures
`FileDispatcherImpl.force0` through both `WorldBossJournal.commit` and
`RecoveryWitness.force` inside the 330.155 ms preparation gap. Recovery gaps
also sample journal force calls. This identifies actual blocking work between
tick bodies rather than assigning an interval to a nearby tick stack.

That diagnostic uses an intermediate v20 artifact
`a670ce6981f780d5fe7bc3aa8dab3c2e370f3a6fdbf95c08ab187ea5e6671e7b`.
Its first whole-zone guard produces zero skips because the real modpack includes
2,000 herd entries alongside individual Pokemon and empty entries. It is not
presented as a speedup. All 14 functional checks pass; its gate is deliberately
INCOMPLETE because JFR was active. The new probe detects JFR for the process
lifetime and the validator refuses such recordings for capacity approval.
Campaign trial 33 preserves that experiment. After graceful stop, all 74 journal
frames and their external head pass the file-consistency audit.

## Control save and publication contract

Start and staff recovery use `WorldBossControlCommit`, one server-thread
continuation at a time. It waits non-blockingly for an earlier checkpoint, then
captures detached NBT using the existing bounded journal writer. Native standing
boss creation, opening announcements and recovery publication wait for that
specific forced journal/witness receipt. Submission is never reported as saved.
The staff commands explicitly say preparation/recovery is queued; status and
preflight report `World Boss control save: pending ...` or `none`.

Automatic encounter adapters and private simulator actor-output delivery yield
while control work is pending. Native engine failure and admission deadlines
continue to be checked. Combat cannot open through an explicit join while a
control save is pending. The continuation revalidates state identity, event UUID,
phase, pool, close state, quarantine and simulator readiness before publication.
Its callbacks execute at most once, including after callback failure.

Hold, Stop and shutdown cancel unpublished continuations. Accepted disk work is
still drained by the existing critical save/shutdown path. Storage failure or the
existing two-second overdue limit cancels control publication and retains the
storage Hold/quarantine rules. A held scheduled preparation without a frozen
roster still requires cancellation rather than staff regroup recovery. No journal
format, forced-write requirement, payout protocol or recovery witness was removed.

Standing-boss creation also checks ownership after native entity hooks and scale
hooks. An actor created for an event that was stopped or replaced is discarded
through the existing deferred-discard service. The old callback cannot announce
the new event or retain that actor as its showpiece. An interrupted native setup
cannot end a successor event through the old failure path.

Staff Hold/Stop, scheduled campaign reservation/cues, combat-opening commits,
qualification and economic outcomes retain their critical save paths. This change
does not assert that every main-thread disk operation has been removed.

## Natural spawning contract

The early area guard applies only to the exact native player spawner, native
generator, default native resolver and explicitly recognized native area
calculators. It requires every corner of the entire positive-sized area to lie
inside one protected column. Cross-boundary areas, other dimensions, invalid
sizes, custom implementations and pools containing NPC/custom entries retain
native area routing. Integer arithmetic cannot wrap an enormous area into a
small protected one.

The live pool is checked every time; there is no stale Pokemon-only cache after
reload or mutation. Individual Pokemon, native herds and empty/no-spawn entries
can use the fast path. Inspection of Cobblemon 1.8.2's herd implementation verifies
that it constructs individual Pokemon spawn actions at selected native positions;
it does not scatter members outside those positions. The native player-spawner
timer still runs and resets normally. Rejected whole zones do not generate their
native random draws or empty-action objects. This is a protected-zone optimization,
not deterministic random-sequence preservation or a global spawning disable.

Partial zones retain generation. Their protected candidate cells now reject both
individual Pokemon and herd entries before construction. NPC entries remain
outside that predicate. The final entity-intersection guard remains in place for
boundary bodies and other natural spawn paths.

## Verification so far

The complete root/client suite passes 1,084 tests (1,076 root and eight client).
New tests cover control ordering, earlier-checkpoint waits, changed events/holds,
failed writes, callback failure, cancellation, whole-area geometry and actual
native Pokemon/herd/empty/NPC-style pool classification across live mutation.
Tests include the already-cached Kotlin standard library at test runtime so they
instantiate real Cobblemon details. The gameplay dependency set is unchanged.
All 48 mixin injection points pass fault-barrier validation. Logging (361 sources),
API boundaries (16 public types) and raid integrity pass. Probe tests pass 22;
the raw-record validator passes eight tests, including JFR rejection.

Fresh unprofiled five-fighter rig `worldboss-rehearsal-4f4a16440cc41598`, pilot
`1795227135253033200`, passes all 17 functional checks, with normal physics and
Pokemon spawning enabled. It uses the v21 artifact before the final native
re-entry ownership guards, SHA-256
`660a026f0d990b10c941400ff068d44906ebfd28bc02486f2118974e044649bb`.
Raw recording: `wbperf-fighters5-95f38c6f-b631-4ff4-9280-243e426a4496.json`.

| Phase | Ticks | p95 ms | p99 ms | Max tick ms | Max start interval ms | Gate |
|---|---:|---:|---:|---:|---:|---|
| Gathering | 1,044 | 20.2793 | 49.6540 | 211.7481 | 211.9513 | PASS |
| Combat | 684 | 13.4209 | 20.8534 | 31.9775 | 63.0690 | PASS |
| Recovery | 273 | 13.5798 | 21.1959 | 23.4799 | 129.0985 | PASS |

All raw ticks and diagnostic stacks are retained without drops. Setup still
retains two ticks over 250 ms; the whole recording maximum is 303.5388 ms. No
phase or acceptance threshold was relaxed. The spawn guard records 329 whole
area skips, 1,512 protected candidate skips and 24 final entity refusals. All five
fighters deal damage; final HP is 36,885/40,000. Native compatibility fixture
`ordinary-compat-1795227399490461800` passes 13 top-level checks, including the
six explicit protected/outside/mixed/reloaded/non-player/custom-generator area
routing assertions. Ordinary PvP still exchanges real moves and stops natively
without altering boss HP. The stopped rig's 143 journal frames match the external
recovery head. Campaign trial 34 retains this complete PASS.

The final guarded v21 artifact failed its maximum-attendance trial in
`worldboss-rehearsal-33d80d59c77a14ba`, pilot `1795227922685769600`.
All 17 functional checks and all 50 fighters' damage credit passed, but the
unchanged timing gate failed. Raw file:
`wbperf-fighters50-eec3fb27-2897-4457-8036-6b6e3c2494a2.json`.

| Phase | Ticks | p95 ms | p99 ms | Max tick ms | Max start interval ms |
|---|---:|---:|---:|---:|---:|
| Gathering | 1,404 | 52.5882 | 76.5562 | 264.7810 | 265.6886 |
| Combat | 794 | 54.1379 | 71.3175 | 138.4125 | 139.1801 |
| Recovery | 526 | 48.8724 | 89.6622 | 4,140.4034 | 4,152.1038 |

All 9,706 raw ticks and 73 diagnostic stacks are retained without drops.
The whole recording retains a 7,548.1146 ms setup maximum and 21 ticks over
250 ms. Native stack samples identify the opening's synchronous journal force
at tick 7,942 and vanilla player-data autosave disk writes at tick 9,599.
The latter includes kernel file resizing and compressed NBT writes inside
`PlayerList.saveAll`; player files average only 1,291 compressed bytes.
This is per-file disk latency, not evidence of oversized player NBT.
The run records 4,421 whole-zone skips. Ordinary PvP/area routing passes 13
compatibility checks; the separate native showpiece re-entry fixture passes
nine checks. After graceful shutdown, all 372 journal frames match the external
head. Campaign trial 35 remains a failure.

The validation-only showpiece interruption fixture is restricted to an empty
private server after a TEST event and after tick recording stops. Once it has
been armed, that process cannot begin another timing recording. It deliberately
stops a TEST event from the native entity-load callback, then verifies that the
unpublished actor is gone and no stale showpiece remains. It is separate from
performance evidence, live rewards, process-kill recovery and graphical-client QA.

## v22 follow-up under native validation

The next candidate queues combat opening behind its own journal receipt,
keeps admission/wards closed during that receipt, and starts the active clock
after acknowledgement. Stationary dome viewers reuse their previous visibility
set after session, audience and dimension checks; movement, rebuilds and reconnects
invalidate that cache. Flash expiry continues even when all viewers are stationary.

During an active arena event, only vanilla automatic, non-flushing, non-forced
dedicated-server player-save batches defer file I/O. Original player serialization
and save hooks run on Main. A bounded detached byte snapshot goes to a private
single writer that cannot inherit server thread-local contexts. The worker writes
vanilla GZIP NBT, preserves `.dat_old`, and verifies the replacement's raw bytes.
It has no player, world, registry or battle references.

Critical save/load operations wait for previously accepted writes for that player;
manual/forced/flushing batches and shutdown drain all accepted writes. Interrupts
do not cancel accepted disk work. Capacity or unsupported capture falls back to
ordered synchronous native file operations. I/O failure blocks further async
submission and holds boss combat; restart is required before another start/recovery.
These barriers can still block on a slow disk. The change preserves vanilla's
file-close semantics and does not add an fsync or power-loss guarantee for player
files. World-boss journal/witness durability remains separate.

The candidate passes 1,096 root/client tests, 22 probe tests, eight raw-validator
tests and 54 packaged mixin containment checks. Its native trials are pending;
performance approval remains blocked. First v22 candidate gameplay SHA-256:
`0ca3ca3c7aa424131109e48b2543a3422522fd2548b003f39a369c9a608e2512`.

Its fresh five-fighter trial `worldboss-rehearsal-7a9475be559b339a`, pilot
`1795231416314913600`, passes all 17 functional checks. The raw recording
`wbperf-fighters5-f6619511-cec5-4ab0-824d-fc662841570b.json` still fails gathering:
p95 17.2003 ms, p99 45.0778 ms, max 536.2318 ms, max interval 536.6988 ms.
Combat passes (p95 11.8131, p99 17.3937, max 26.1010, interval 51.4076 ms);
recovery passes (p95 11.7166, p99 20.0493, max 22.5166, interval 100.1916 ms).
All 3,811 ticks and stacks are retained without drops; whole-record maximum is
1,038.43 ms, including eight setup ticks over 250 ms and two gathering ticks.
The gathering outliers sample native Pokemon AI, then a small lane-reservation
operation. Those stacks alone do not establish which work accounts for the
entire stall. Campaign trial 36 retains the failed gate.

Post-timing compatibility `ordinary-compat-1795231623927669700` passes all 14
checks, including a real native automatic player-save batch followed by a forced
flushing save. Both connected clients retain both markers in current NBT and
only the earlier marker in `.dat_old`; the accepted queue is drained and healthy.
The separate native showpiece interruption fixture passes nine checks. After
graceful shutdown, all 126 journal frames match the external recovery head.

The next candidate additionally preserves the full combat budget when pending
opening is cancelled. Gameplay SHA-256:
`9b5333559c233dcc4e0a92951e732c25555331cc14fdecd73f7eec2228eae8d8`.
Its maximum-attendance trial in `worldboss-rehearsal-77897191751e7b2a`, pilot
`1795231974253384700`, passes all 17 functional checks but fails the raw gate.
Raw file: `wbperf-fighters50-90e57b31-ed69-44d3-a7d9-0179ef002863.json`.

| Phase | Ticks | p95 ms | p99 ms | Max tick ms | Max start interval ms | Gate |
|---|---:|---:|---:|---:|---:|---|
| Gathering | 1,345 | 42.1634 | 68.8088 | 173.1789 | 174.1051 | PASS |
| Combat | 721 | 40.1789 | 50.2928 | 515.4086 | 519.4019 | FAIL |
| Recovery | 361 | 26.2137 | 31.5102 | 40.5820 | 272.3103 | FAIL |

All 8,583 ticks/stacks are retained without drops. The full recording retains
35 ticks over 250 ms and a 3,220.3922 ms setup maximum. All 50 fighters receive
damage credit; HP ends at 354,046/400,000. Whole-area skips reach 4,225.
Post-timing ordinary compatibility passes all 14 checks, including native
automatic/forced save ordering; showpiece re-entry passes nine. All 186 stopped
journal frames match the external head. Campaign trial 37 retains this failure.
The private probe also records bounded off-thread VM GC notifications for
correlation, without JFR or measured-tick callbacks. GC notification duration
describes the collector's event and must not be assumed to be entirely a
stop-the-world pause, especially for concurrent old-generation collections.
None overlaps the latest combat or recovery outlier: the nearby young GC is
14 ms and occurs after the combat stall. Two combat samples (108/326 ms) instead
show native Cards' `CardStatUtil.getPlayerDropBonus` blocked in Log4j's synchronous
`RandomAccessFile.writeBytes0`. The recovery gap ends exactly at the staff-hold
command's completion; its path still contains a synchronous journal flush.

## v23 follow-up under native validation

Staff hold now suspends combat and aborts owned duels immediately, then saves
through its own acknowledged control continuation. Acceptance is explicitly
queued; status must show no pending control and `HELD / STAFF` before durable
hold is claimed. Recovery refuses while the hold receipt is pending. Failed
storage remains held. Hold persistence does not depend on simulator readiness.

Two optional Cards mixins route only verbose stat/chance diagnostics to DEBUG
for viewers of an active designated arena. Outside that audience they invoke
the original INFO call. Successful-drop messages, warnings/errors, equipment
reads, bonus arithmetic, randomness and grants remain native. The mixins have
no hard Cards dependency; missing optional call sites leave native behaviour.
Preflight reports the routed-call count so a compatibility miss is observable.

The probe can exercise a real native automatic player-save batch inside timed
combat using `--exercise-player-autosave`; all existing tick/interval gates apply
to that extra work. It verifies one accepted snapshot per connected synthetic
account and later verifies that accepted writes drained without failure.
Post-timing Cards checks call the real bonus calculation outside and inside the
arena and compare returned bonus and logging-route counts, without a drop event.

All 1,096 root/client tests, 22 probe tests, eight raw validator tests and 56
packaged mixin containment checks pass. Native validation is pending. Gameplay
SHA-256: `7db3c061c26d52d5ced99f94ada5758580053fd8d933556769251a062029d26f`.
Probe SHA-256: `d17b8e150b0fa7649f12ab4b22e487705f6f58245f70db4a4e3dcae6429f27d7`.
Nothing deployed. Performance approval remains blocked.

The fresh v23 five-fighter autosave-pressure run in
`worldboss-rehearsal-7898c3fcc812c7cd`, pilot `1795233138174513400`, passes all
20 functional checks. Raw file:
`wbperf-fighters5-de136533-7ccf-46fa-8402-9e4446462212.json`.

| Phase | Ticks | p95 ms | p99 ms | Max tick ms | Max start interval ms | Gate |
|---|---:|---:|---:|---:|---:|---|
| Gathering | 1,074 | 19.5978 | 63.1888 | 285.0921 | 290.0183 | FAIL |
| Combat | 758 | 13.1100 | 19.9813 | 299.3994 | 665.9510 | FAIL |
| Recovery | 302 | 10.8338 | 17.7751 | 20.3219 | 51.1675 | PASS |

All 3,797 ticks/stacks are retained without drops; the whole-record maximum is
940.6981 ms. The explicit native autosave accepts and completes all six player
snapshots, but its Main call takes 611.4387 ms. A separate combat outlier samples
Cards' successful-card INFO message blocked in console output. Stat diagnostics
do route to DEBUG (six native calls), so removing those verbose messages alone
does not eliminate all Cards log I/O. The gathering outlier samples native
Pokemon brain iteration without a matching long GC event. Campaign trial 38
retains the failed timing gate.

Only after that clean recording stops, a separate eight-batch diagnostic runs
with six reconnected synthetic accounts and JFR enabled. Files:
`logs/worldboss-autosave.jfr`,
`wbperf-autosavediag-6b05fca8-61d3-4073-9dd9-5e640da746ab.json`, and
`logs/autosave-diagnostic/profile-summary.json`. The validator explicitly returns
INCOMPLETE because the process is JFR-tainted. Native batch calls range from
203.2822 to 1,457.7213 ms; player-file snapshots all complete successfully.
Multiple JVM samples show Main blocked in `WorldBossJournal.commit` through
`DurableSavedData.save`, alongside native SavedData and level-metadata file
writes. This confirms that the native autosave entry point still bypassed the
periodic journal worker. It is diagnostic evidence, not capacity approval.

Post-timing compatibility passes all 17 checks, including identical native
Cards bonus outside/inside the arena with INFO/DEBUG routing respectively.
The showpiece interruption fixture passes nine checks. After graceful shutdown,
all 300 journal frames match the external recovery head.

## v24 follow-up under native validation

The real `SavedData.save(File, Provider)` entry point now has a narrow deferred
hook. Other durable data keeps its synchronous path. Only World Boss state uses
it, and only inside an eligible native automatic arena-event save scope. The
same existing journal/witness worker captures and acknowledges the full snapshot.
An older outstanding snapshot cannot clear later dirty mutations. Direct native
saves outside that scope drain accepted work even when the dirty flag is clear;
manual/forced save scopes drain it before native saving proceeds. Explicit
`flushStrict` remains critical and bypasses the deferred hook. Native metadata,
other SavedData and chunk I/O have not been moved to a worker by this change.

An actual regression test first fails because native autosave calls the witness
on its caller. After the fix, that same entry point captures off-thread force,
withholds acknowledged views, retains later dirty state, and lets a subsequent
critical native save persist the latest state. A second test covers the
clear-dirty-flag/pending-snapshot barrier.

Cards' successful-drop messages retain INFO and their text. For the active arena
audience, two optional wrappers detach strings and enqueue output FIFO on a
private worker without inherited server thread-local contexts. At most 128
messages are accepted at once, each bounded to 65,536 characters. Capacity,
unsupported arguments, closed/failed writer and outside-arena calls use native
synchronous INFO. Worker failure is reported once; pending/delivered/failure
counts are visible in preflight. Shutdown drains accepted output even when
interrupted. This queue is diagnostic output, not an economic receipt or a new
log durability guarantee. Actual card calculation, randomness, item grants and
warning/error logging remain native and on their original execution paths.

Two further regression tests first fail on inherited server contexts in the
older journal and private simulator workers. Their thread factories now reject
inheritable thread locals as well; the simulator's mod class loader is retained
explicitly and asserted. All four workers now follow the same isolation rule.

All 1,104 root/client tests pass, including four log-queue ordering/capacity/
failure/interruption tests. The native acceptance parser additionally passes
four tests proving that another queue's healthy flag cannot mask failed or
incomplete player saves. All 59 packaged mixin guards pass. Native validation
is pending in `worldboss-rehearsal-c5b68760ebc411a7`. Gameplay SHA-256:
`c35aeb932ab0a711c7d03cb7b20d4008c7475052404666c65de0613eab52ed62`.
Probe remains `d17b8e150b0fa7649f12ab4b22e487705f6f58245f70db4a4e3dcae6429f27d7`.
Performance approval remains blocked. Nothing deployed.

## November 21: v24 native results and v25 world NBT work

The clean v24 five-fighter run in `worldboss-rehearsal-c5b68760ebc411a7`
passes 20 functional checks. Gathering p95/max are 14.1407/160.6102 ms,
combat 11.4159/26.3812 ms, recovery 8.8945/21.9773 ms. Gathering and combat
start intervals still fail at 1,466.2526 and 604.0716 ms; recovery passes at
51.6118 ms. All 3,744 raw ticks are retained without sample loss. Campaign
trial 39 remains FAIL. The explicit autosave takes 588.6732 ms; all six player
snapshots finish, and all four accepted real Cards INFO messages are delivered.
All 17 compatibility and nine showpiece checks pass. After graceful shutdown,
7,103 journal frames (17,684,712 bytes) match the external head. The rig's long
idle period before shutdown is not capacity evidence.

Three retained Main samples in setup tick 1613 show AscensionLib's native SQLite
acquisition commit; its 1,465.157 ms body carries into gathering's first interval.
The fixture had issued the final party grant without waiting for the SDK's
five-tick gift grace and actual profile assignment. New v25 pilots can require
every synthetic client's real assignment receipt plus two observed native tick
boundaries before starting gathering. All setup ticks remain in the raw recording;
the earlier failed run is not reclassified. This measures initialized parties;
concurrent SDK acquisitions remain a separate server-wide performance risk.
AscensionLib's WAL/FULL durability is unchanged.

A separate JFR diagnostic remains ineligible for capacity. It finds no journal
force on Main after v24. Main file-write events instead identify dimension
`raids.dat`, `raids_end.dat`, `cobbletowers_diagnostics.dat` and native level
metadata temporaries. Candidate v25 therefore captures native SavedData and
level metadata NBT on Main and moves only detached compressed file writes to
one bounded worker (128 snapshots, 64 MiB total, 16 MiB per snapshot). No caller
runs worker work on saturation; ordered native synchronous saving remains the
fallback. Native hooks and tag creation stay on Main. Reads, forced/manual saves,
backups and shutdown drain accepted work. Later dirty mutations survive older
acknowledgments; failed writes restore dirty state on Main and hold the event
until restart. Metadata retains native `level.dat_old` ordering and verifies
replacement. Native saves gain no fsync or physical power-loss guarantee.

All 1,114 root/client tests and 22 probe tests pass, as do eight raw-validator,
six completion-parser tests and 68 packaged mixin guards. The ten new tests
cover actual compressed files, detached snapshots, dirty generations, FIFO,
capacity, failures, interruption and metadata backups. The probe adds a post-timing
native save/read/forced-save compatibility check. Fresh native validation is
underway; performance approval remains blocked. Gameplay SHA-256:
`31d63dc28f61b3591513170411c3387cf279cb9db8c6c8e74a5569827fa0eb56`.
Probe SHA-256: `d975e953723a50874f9683a9f34b12fa9d924cb40eb13fdabcc1b2728c9156ab`.
That first five-fighter probe's new post-timing world-save fixture supplied a
null DataFixTypes argument. Vanilla bytecode requires a non-null type even for
current-version tags. The fixture is corrected to LEVEL (current version, no
migration); production code and timing hooks are unchanged. The corrected probe
SHA-256 is `58feb0f39bf33524568f8ad7fe4d5bee3df832956e05a4f8cd16e4ba198fb683`.
Its native compatibility check must run in a fresh process before signoff.

The first v25 native five-fighter trial (`worldboss-rehearsal-0e82cee035b66cb5`,
pilot `1795251362909415800`, raw `wbperf-fighters5-e661e571-0eaa-4663-b788-a85f53a47ce2.json`)
passes all 23 functional checks and the unchanged gathering/combat/recovery gates.
Gathering: 1,058 ticks, p95 19.2992, p99 39.7157, max 98.1842, start interval
98.3769 ms. Combat: 735 ticks, p95 16.5710, p99 20.7108, max 39.0436,
interval 168.9478 ms. Recovery: 290 ticks, p95 15.7267, p99 23.9258,
max 27.8754, interval 64.7582 ms. All phases retain six connected synthetic
clients. All 3,584 raw ticks are retained without drops; setup's 257.4510 ms
maximum and 2,427.5821 ms start interval remain visible outside the declared
event gates. The explicit automatic save takes 114.8246 ms; all six player and
seven native world snapshots complete healthy. Two accepted Cards INFO messages
finish. Campaign trial 40 records PASS for these phases only. The rig is stopped;
all 70 journal frames match the external head. No post-timing native compatibility
fixture ran in this initial process; corrected-probe native checks and fresh
fifty-fighter validation remain required. Production approval remains blocked.

The clean v25 fifty-fighter trial (`worldboss-rehearsal-a83957b19e229be4`,
pilot `1795251710059751600`, raw `wbperf-fighters50-285c48de-d88e-47a8-96b3-458c4b8ebbd2.json`)
passes all 23 functional checks, gathering and recovery. Gathering: 1,317 ticks,
p95 44.3758, p99 69.0292, max 158.5604, interval 159.3962 ms. Combat: 785 ticks,
p95 42.4408, p99 54.8587, max 63.6793, interval **279.5597 ms**, FAIL. Recovery:
335 ticks, p95 23.6717, p99 36.0878, max 56.5542, interval 105.7310 ms. All phases
retain 51 connected clients, and all 50 fighters receive damage credit. All 8,151
raw ticks are retained without drops. Setup max 2,888.6586 ms remains visible;
it does not become event capacity evidence. Explicit automatic saving takes
222.2305 ms, all 51 player and six world snapshots finish healthy, and all 39
accepted Cards INFO messages finish. The only failing event interval is tick
7071: preceding body 21.7570 ms plus outer-dispatch work, including the native
automatic save. Campaign trial 41 remains FAIL.

The corrected probe passes all 18 native compatibility and nine showpiece
checks, including actual world read barriers, later dirty mutations, forced
latest data and level metadata backup order. A separate, tainted fifty-client
diagnostic repeats eight saves (105.9987–148.2539 ms). No Main file-write event
exceeds the profile's threshold; this does not rule out shorter I/O. Repeated
Main samples instead show `AdvancementProgress.hasProgress` in native
`PlayerAdvancements.asData`, as well as player NBT serialization. After all
diagnostics the rig is stopped; all 247 journal frames match the external head.

## v26: pace native advancement serialization on Main

Only eligible automatic arena saves may queue the PlayerList-to-PlayerAdvancements
save invocation. One player's native save runs on each later Main tick. Its
original save hooks execute once, on that tick, and serialization remains on
Main. The queue holds at most 128 file keys; repeated pending saves coalesce to
the latest native object for that file. Queue refusal keeps the native direct
save. This is periodic current-state saving, not a frozen economic receipt.
With 51 players and normal 20 TPS, the nominal queue drains in 2.55 seconds;
lag or a slow individual native save can extend that, and a single job cannot
be preempted.

Direct player saves supersede their pending file job with current native state.
Native advancement load/reload flushes the pending predecessor before reading
or clearing progress. Manual/forced world saves, world backups and shutdown
drain accepted jobs. Off-thread native barriers wait for already accepted Main
work rather than serializing it on their caller. Unexpected hook failure stops
new scheduling and holds the boss until restart; other accepted jobs remain
drainable. Native advancement save's existing caught I/O errors remain native
logs; the new completion counters measure method execution, not fsync or a new
file-durability receipt.

Seven new tests exercise Main ownership, 51-player pacing, latest-state saving,
FIFO/coalescing, direct replacement, reload ordering, bounded capacity, failure
drain and interrupted off-thread waits. All 1,121 root/client tests, 22 probe
tests, nine completion-parser tests and 71 packaged mixin guards pass. The native
probe now checks pacing, reload and forced-save behavior through actual native
methods. Candidate SHA-256:
`9202e23cf062ed6a027076bc8cfc678c6209726aa18b0a9b862f0298d54e304c`.
Probe SHA-256: `6c1e296d67c2593c698aecfe3ae8475d36446f9cdc4c9c300aa00a07063dc3e8`.
Fresh native verification is underway in `worldboss-rehearsal-d6dc59150d0101fe`;
performance/production approval remains blocked. Nothing deployed.

The fresh v26 five-fighter run (`worldboss-rehearsal-d6dc59150d0101fe`,
pilot `1795252923684834400`, raw `wbperf-fighters5-1fe199fe-bdf8-465b-a41f-9465bc995633.json`)
passes all 24 functional checks and the unchanged event gates. Gathering:
1,039 ticks, p95 15.4481, p99 36.8877, max 112.8596, interval 112.9452 ms.
Combat: 738 ticks, p95 12.8986, p99 22.6095, max 34.9519, interval 100.9071 ms.
Recovery: 283 ticks, p95 11.3871, p99 19.0479, max 43.2807, interval 55.1930 ms.
Every phase retains all six clients; all 3,544 raw ticks remain without drops.
The 894.0355 ms setup maximum remains in the recording. The explicit save takes
83.4354 ms; all six player snapshots, eight world snapshots and six scheduled
native advancement jobs finish healthy. Pacing work executes during the recorded
combat ticks, not outside the event gates. All 19 native compatibility checks
pass, including actual advancement pacing, flush-before-reload and forced drain;
all nine showpiece checks pass. The rig is stopped, and all 122 journal frames
match the external head. Campaign trial 42 records PASS for these declared
initialized-party event phases. A fresh fifty-fighter run of the identical
gameplay/probe artifacts is next in `worldboss-rehearsal-ebd150b9e95d95d1`.

That v26 fifty-fighter run does **not** pass: only 49 native duels start before
the two-second journal acknowledgement watchdog holds combat and aborts them.
The pilot correctly fails its fifty-battle check. All 9,202 raw ticks remain,
with no drops; combat/recovery phases never start, so the complete gate is
INCOMPLETE, not PASS. The separate gathering gate fails: 3,840 ticks, p95 30.1403,
p99 49.5364, max 1,099.8868, interval 1,103.5058 ms. Raw recording:
`wbperf-fighters50-38448d8f-53a4-44a8-86eb-f0ebc4cfb6bc.json`; pilot
`1795253335296325900`. Campaign trial 43 retains ten passed checks followed by
the failed native-battle check. Explicit combat autosave pressure is not reached.
The stopped rig's 201 journal frames match the external head.

Three samples in tick 6544 (100/302/507 ms) identify synchronous Log4j console
`FileOutputStream.writeBytes` from our `EncounterService.startChecked` INFO trace.
Nearby GC pauses are 21/22 ms, not the 1.1-second stall. The journal watchdog
warning occurs about three seconds afterward; its worker's precise delay was
not sampled, so that warning is not attributed conclusively to the console stall.
Another gathering sample shows per-position `String.matches` regex compilation.

Policy v27 routes routine per-player world-boss encounter start/end and detailed
placement traces to DEBUG. Other encounter owners keep INFO, as do world-boss
occurrence lifecycle, control acknowledgements and rewards. Warnings/errors are
unchanged. These traces are diagnostics, not persistence receipts. The dimension
validator now reuses one immutable compiled Pattern with a fresh Matcher per call,
preserving the exact grammar and finite-coordinate validation. Journal durability,
its two-second watchdog and all phase thresholds remain unchanged.
All 1,121 root/client tests and 71 packaged mixin guards pass; logging/API/integrity
checks pass. Gameplay SHA-256:
`c5a45aaa566c116e26e5e90e4945e33e82ad8929a7d5d0161ca315a4b16a5c63`.
Probe remains `6c1e296d67c2593c698aecfe3ae8475d36446f9cdc4c9c300aa00a07063dc3e8`.
Fresh fifty-fighter verification is underway in `worldboss-rehearsal-ed5fd77746b86f6f`.
Performance/production approval remains blocked. Nothing deployed.

The fresh v27 fifty-fighter run now passes all 24 functional checks and the
unchanged gathering/combat/recovery timing gates. Rig `worldboss-rehearsal-ed5fd77746b86f6f`,
pilot `1795254220888355400`, raw `wbperf-fighters50-9806b770-626c-4d06-9ca4-5cf0c29b3aaf.json`.
Gathering: 1,336 ticks, p95 38.4825, p99 56.3523, max 165.7318, interval
167.1622 ms. Combat: 783 ticks, p95 37.4135, p99 49.3006, max 223.8288,
interval 224.8207 ms. Recovery: 334 ticks, p95 22.5931, p99 29.1294, max
32.1635, interval 62.7411 ms. Every phase retains 51 connected clients. All fifty
native battles start without the checkpoint watchdog holding this occurrence,
and all fifty fighters receive damage credit. All 7,475 raw ticks remain without
drops; setup's 3,223.2860 ms maximum remains retained outside the event gates.

The explicit save takes 133.9380 ms; all 51 player snapshots, six world snapshots
and 51 paced native advancement jobs complete healthy. All 39 accepted Cards INFO
messages finish. Campaign trial 44 records PASS for these declared fixture phases.
All 19 native compatibility and nine showpiece checks pass on this final JAR.
After shutdown, all 139 journal frames match the external head. The earlier
watchdog's worker latency remains unattributed; this successful run does not
establish behavior under arbitrary storage stalls or concurrent SDK acquisitions.
A fresh five-fighter confirmation of these identical artifacts is underway in
`worldboss-rehearsal-aead33281f69920b`. Nothing deployed; broader release gates remain.

The final same-artifact five-fighter confirmation also passes all 24 functional
checks and all three timing gates. Pilot `1795254833786061300`, raw
`wbperf-fighters5-fb0131f9-4227-4552-b45f-6dde6006ac21.json`. Gathering:
1,060 ticks, p95 18.4188, p99 31.0113, max 127.4015, interval 127.4669 ms.
Combat: 729 ticks, p95 12.6507, p99 24.0543, max 59.1842, interval 161.2655 ms.
Recovery: 286 ticks, p95 12.4294, p99 16.1942, max 20.7286, interval 53.8297 ms.
Every phase retains all six clients; all 3,625 raw ticks remain without drops.
Setup's 1,237.2543 ms maximum remains visible. Explicit automatic saving takes
131.2791 ms; all six player snapshots, seven world snapshots and six paced
advancement jobs finish healthy. Campaign trial 45 records PASS. The rig is
stopped; all 75 journal frames match the external head. Both final trials use
the exact v27 gameplay JAR and unchanged probe hashes above.

## Current acceptance scope

The unchanged **declared event-phase performance gates pass at five and fifty
fighters** on this host/modpack, with one observer, initialized one-Pokemon
parties, natural spawning enabled, actual native battles and explicit automatic
save pressure. The final artifact also passes 19 native compatibility and nine
showpiece checks. All private rigs are stopped. No source change followed these
final runs, and no failed or incomplete historical trial was discarded.

This is not production approval. Remaining release work includes live calibrated
balance and reward/economy throughput, full-party and graphical-client trials,
concurrent SDK acquisitions, and native crash/restore plus storage-latency/fault
rehearsals. The earlier journal worker delay was not conclusively attributed;
its two-second fail-closed watchdog remains unchanged. Native player/world/
advancement files retain vanilla durability semantics, and a post-stop journal
audit is not a physical power-loss test. Nothing was deployed or published.
