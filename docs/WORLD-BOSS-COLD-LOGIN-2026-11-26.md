# World Boss cold-login continuation — November 26

Continues [the November 24 evidence](WORLD-BOSS-COLD-LOGIN-2026-11-24.md).
No production deployment or overall performance approval is implied.

## Resumed startup-cache trial

The interrupted `29fcce40619cabad` process was no longer running on resume. It
had no native pilot or tick recording. Its one journal frame still matches the
external head; that is file consistency evidence, not crash certification.

The unchanged `03bb1c3ea0435dc3c9c527182ba612e6da943dc4b7f89f94f63ff853f8103868`
gameplay JAR was tested in fresh rig `14464d2c1c7d17d4`, evidence
`pilot-1795669444808323300`, recording
`wbperf-fighters5-9b59c900-c75d-4981-9c28-8ff742d0d8ee.json`.
All six clients join exactly once, with 30 directory preparations, six command
copy maps and seven packet maps (one packet was prepared at startup). Natural
Pokemon spawning remains enabled. All startup-cache flags are true.

The unchanged cold-login gate **FAILS**: 664 raw ticks, p95 4.8795, p99 26.5854,
max body 236.5734 and max start interval 257.5605 ms. All zero-player rows remain
included. The first-login body samples class loading in `MoLangFunctions` while
initializing the entity/living/player function holders for the native login event.
The large reduction versus earlier runs is not an isolated causal comparison:
the host/process state changed during the interruption. This remains a fresh
six-player trial of this exact JAR, not fifty-player capacity evidence.

After timing, native command checks pass permission levels 0/4, distinct alias
targets through packet reconstruction and zero command execution. Listener checks
pass collision/deduplication/reload/removal across all 5,145 loaded criteria.
The process is stopped and its one-frame journal matches the external head.

## Native rolling-log output

The November 24 trace also captured Main blocked inside native Log4j rolling-file
writes. A new regression uses an isolated LoggerContext and a real native
RollingRandomAccessFileAppender. Before the change it fails because the file's
layout/write path executes on the caller thread.

The existing managed native console dispatcher now also supports filter-free
native rolling-file appenders with declared AppenderRefs. It freezes messages
and context on the caller, retains reference levels/filters on the caller, and
uses native bounded AsyncAppenders for output. Sink filters and undeclared
programmatic bindings remain native; the latter can carry thresholds/filters
that cannot be reconstructed from AppenderRef metadata. Native Log4j still owns
rotation, drain and shutdown ordering. The 2,048-event lossless queues can apply
backpressure when saturated. These are diagnostic logs, not the forced World
Boss journal or SDK WAL; queued diagnostics are not a crash-durability receipt.

The regression now passes: 64 messages are written in order and drained on
shutdown, actor formatting stays on the caller, a custom sink filter stays on
the caller, and declared/programmatic reference thresholds still exclude DEBUG.
The full gameplay/client suite passes 1,148 tests with no failures/skips.

## Function-holder initialization

The native `EntityMoLangFunctions`, `LivingEntityMoLangFunctions` and
`PlayerMoLangFunctions` singleton constructors were inspected: each only calls
the abstract holder constructor, which allocates a local custom-provider list.
Startup now touches these holders without attaching to an actor or invoking
any native/custom function provider. It creates no player/entity/store.

Candidate gameplay SHA-256:
`9619b04081a1b3f8c5325941dae5059442f9176d6b7899df0e773df5099c4483`.
Probe remains
`e2d6ded187732e092997119a4767465e57a9e6cf5cbbb988bceee7ac864721f4`.
Rig `e7436cf94e29fbba`, evidence `pilot-1795669848121256800`, recording
`wbperf-fighters5-102d11fe-83fb-4433-96b0-37512342cdd7.json` confirms one managed
console, one managed rolling log, all startup flags, six exact-once admissions
and 30 directories. Cold login still **FAILS**: 384 ticks, p95 10.0898, p99 40.1624,
max 534.7158, start interval 534.9102 ms. The largest tick has six players; both
104 and 308 ms samples show Main waiting on SectionStorage's native POI column
read inside SaccharineLog prospecting. This is a distinct synchronous I/O cost.
Both post-timing graph/listener checks pass. The stopped rig's one-frame journal
matches its external head.

## Bounded native POI read ahead

Native SectionStorage has no public prefetch API. Its private column loader
joins an asynchronous SimpleRegionStorage read, then applies the native decoder
and section-load hooks on Main. The new seam reuses that I/O/decoder pipeline.
After native admission finishes, the actual player's position and actual
Cobblemon spawn-distance/zone settings determine a conservative POI column area.
It includes Minecraft's extra outer chunk ring, not only the geometric range.
Unsupported/invalid configurations retain native loading rather than claim
partial coverage. No spawn attempt, selector, timer, gamerule or influence is
skipped or disabled. This is admission read ahead, not a new spawn policy.

Main queues at most 1,024 column jobs, starts at most eight per tick with at most
four native reads outstanding, and retains at most 32 futures for ten seconds.
Up to four completed columns are published per tick under a 2 ms start budget;
one native decode cannot be interrupted once it begins. No new I/O worker is
created. Only already-successful futures can reach Main's native column decoder.
Pending, failed, cancelled, expired and invalidated reads use the native fallback
with native retry/error handling. Background completion updates detached counters
only. Columns with any resident section are not proactively overwritten.

Native dirty marking and column writes invalidate read-ahead data. A synchronized
publication claim prevents expiry/invalidation racing the completed-future join.
Shutdown drops queued/cached work and does not cancel native I/O or permit late
publication. Eight cache tests cover bounded admission, pending/error fallback,
exact-once consumption, invalidation races, expiry during publication and shutdown.
Two geometry tests cover negative/boundary coordinates and unsupported settings.

The private native fixture runs only after tick recording stops and before any
client disconnects. It requires drained, healthy read-ahead with positive reads,
publications/hits and unowned natural Pokemon. It adds/removes a temporary POI in
a prepared column, checks native type visibility and both dirty/write invalidation
hooks, then restores the original empty record in finally. Any fixture failure
fails the pilot; disabling natural spawning cannot enable this check. Running
the fixture prevents further timing in that process.

Candidate gameplay SHA-256:
`0ab9384e7f8d9fd5c80156884ce64868c4d7609f64fbec5b21ff32d8203263d7`.
Probe SHA-256:
`5740545f0851e4443947dd80986c42c1297ea9e2380999be0da742b5a55e7216`.
The full gameplay/client suite passes 1,158 tests; 42 Python tests pass. Packaged
guards pass 78 injection points; target shadowing passes for 42 targets/14,247
dependency classes. Fresh validation is underway in `010ef10b88a01a01`.
No performance approval yet; full phases, peak attendance and recovery checks
must describe the final artifact rather than inherit earlier artifacts' passes.

## Profile-cache file opens

The read-ahead candidate's fresh recording (`pilot-1795671108502981000`,
`wbperf-fighters5-65ca17c0-009e-4275-8299-55f3b09838fd.json`) fails: 406 ticks,
p95 7.9123, p99 28.9572, max body 2419.7686, max interval 2432.7439 ms.
Repeated Main samples show native GameProfileCache.save opening its writer in
FileOutputStream.open0. The post-timing native POI fixture passes with seven
natural wild Pokemon, 269 reads/publications/hits, no failed reads or skipped
jobs, and successful dirty/write invalidation and restoration.

Startup native cache priming plus serialized native save/load does not fix this:
gameplay `86a0e62bfa54c0a401840e1a64bb2c111d7866f078bfefba3e85c2327901b012`,
rig `eb350901d21e40bf`, evidence `pilot-1795671961952692200`, recording
`wbperf-fighters5-9a859cbf-20bf-4801-8f7e-fb58f5560cde.json`: 425 ticks,
p95 9.4864, p99 33.8029, max 1503.0346, interval 1515.4956 ms (FAIL).
All three slow-tick samples remain in FileOutputStream.open0. Native checks
verify six profiles are actually persisted, seven natural Pokemon, and healthy
POI loading. Graph/listener checks pass. Both rigs are stopped; their one-frame
journals match their external heads (file consistency, not power-loss proof).

The next candidate reuses one writable native profile-cache file channel while
retaining synchronous native JSON construction and writer flush/close. It adds
no asynchronous persistence, worker, fake profile or journal policy change.
Native save/load and lifecycle close share the native cache monitor. Every save
checks file identity; replacement closes/reopens the channel. Missing files or
unsupported identity retain the original native writer. BasicFileAttributes has
no file key on this Windows runtime, so Windows uses a metadata-only handle and
GetFileInformationByHandleEx(FileIdInfo), comparing volume serial and 128-bit
file ID; the temporary metadata handle always closes. API reference:
https://learn.microsoft.com/windows/win32/api/winbase/ns-winbase-file_id_info

Seven cache tests now pass, including actual Windows replacement, fifty saves
without reopening the data channel, complete UTF-8 visibility/shrinking after
each close, identity changes, fallback, and shutdown serialization. Full root/
client suite: 1,168 passed, no failures/errors/skips. Packaged checks: 81 guarded
injection points, 42 shadow targets across 14,247 classes. Candidate gameplay
`99c8f6fe5351f802a6b4b9605070adb9f2c5e439731c54df94051a4cd4c71441`, probe
`5ad8a211f4a008bb7a04fd40ec8e31b9fedf60b4d5decf3aae9fd80fc8f61bae`.
Fresh rig `2a6e6a29fa784432`, evidence `pilot-1795673020526721300`, recording
`wbperf-fighters5-11739432-735e-4b98-8ada-2e1b5c866894.json`: cold login PASS,
387 ticks including the initial zero-player rows, p95 9.684299, p99 82.950401,
max 172.2361, interval 185.496299 ms; all six clients retained. The native check
confirms six persisted profiles, one data-channel open, seven reuses, zero
fallbacks/errors, four natural wild Pokemon and 269 healthy POI reads/publications/
hits. Native command/listener checks pass. The process is stopped and its
one-frame journal matches the external head. This is a small cold-login pass,
not full-event/peak-attendance approval. Fresh full-event rig `17d73ae2d88bb583`
will exercise 50 fighters plus one observer with the same artifact.

## First peak full-event result

Rig `17d73ae2d88bb583`, evidence `pilot-1795673204859010700`, recording
`wbperf-fighters50-3bd784c4-1797-4e81-ad15-05ddcce9cf4c.json` retains all 51
clients and passes the 25 functional pilot checks. All 50 real battles begin,
clients submit native moves, automatic saves drain, and durable hold/recover
cleans up owned duels. Native POI/profile checks pass (223 natural wild Pokemon,
51 persisted profiles, one channel open/52 reuses, no cache I/O errors or read-
ahead failures). Command/listener, ordinary compatibility (19) and showpiece
re-entry (9) checks pass. Stopped journal: 110 frames/671,491 bytes, external
head matches. Timing still FAILS:

| Phase | Ticks | p95 ms | p99 ms | Max body ms | Max interval ms | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| cold login | 185 | 71.884499 | 168.420799 | 365.5726 | 415.2691 | FAIL |
| initialization | 1906 | 8.275 | 13.219601 | 118.1477 | 479.9402 | FAIL |
| gathering | 1293 | 13.9912 | 19.921499 | 69.7693 | 69.8914 | PASS |
| combat | 686 | 14.020299 | 18.5038 | 24.1225 | 75.019201 | PASS |
| recovery | 225 | 9.7063 | 12.035099 | 12.5642 | 51.3736 | PASS |

The first-login samples show class definition in Fabric's advancement-owner
FakePlayer instanceof check and the native relative-teleport flags. The next
tick links chunk-batch packet types. The first automatic-save command reports
430.826699 ms between ticks; the old sampler only observes tickServer bodies,
so it cannot identify this gap's internal operation. An unrelated 118 ms body
sample shows an SDK fusion read during acquisition announcement; retain that
finding rather than attributing the save gap to it without a trace.

Next gameplay `90491a4261d632f88d61ab88262a30a7feb20748d8ca38406321d238a412baf8`
links the native Fabric type (without initialization/instantiation), relative
flags and unsent chunk-batch start/finish DTOs at startup. Native owner checks,
teleport policy, packet delivery and permission predicates remain unchanged.
Probe `f71c8153031e3450b09866dd64c77f4fae063f0ff4b793cf83d049d3fb2b9112`
extends the same bounded sampler to explicit native automatic-save calls;
negative sample IDs identify operations and are never inserted into raw tick
rows. All complete tick/body/interval measurements and gate limits remain.
Builds and 1,168 root/client plus 22 probe tests pass; guards remain 81/42.
Fresh rig `762f7b860b5cf4f9` is starting. No approval for the new artifact yet.

## Retained storage-hold failure and advancement I/O

That full event fails functionally: a periodic checkpoint exceeds its two-second
deadline and correctly holds combat with STORAGE_FAILURE; not all 50 fighters
receive battle initialization. No deadline was relaxed or recovery forced to
complete the pilot. Evidence `pilot-1795673784898833300`, recording
`wbperf-fighters50-e8fba279-5fe0-4d43-9955-4bc212e367c2.json`: cold login 190
ticks, p95 78.9286, p99 123.5396, max 329.9889, interval 349.6444 ms (FAIL).
Initialization max 422.5275/interval 423.4118; gathering max 1423.1169/interval
1425.1931 (FAIL). Combat/recovery phases were never reached; no passes implied.
The first automatic-save invocation takes 55.7272 ms, so the earlier between-
tick save gap does not reproduce. Its cause remains unproven. First-login stacks
instead show party-store MoLang lambda linkage and a native stats key-set class.
Repeated 1.42-second tick samples show native advancement JSON file opening/
truncation, despite the existing one-file-per-tick serialization pacing.
The profile/POI fixture still passes with 51 persisted profiles, one channel open/
52 reuses, 190 natural Pokemon and healthy invalidation/restoration. Stopped rig:
133 journal frames/744,544 bytes; external head matches.

Candidate `510096885989b83ab79c13c9e3e15a935666957bb3357513636ca0f6425c1c63`
captures only the native UTF-8 advancement writer inside an already-paced
automatic save. The original codec/Gson serialization and native save hooks run
on Main. A separate instance of the existing bounded detached-byte transport
writes those bytes on one daemon worker; no actor is read off Main. Bounds remain
128 queued writes, 16 MiB per snapshot and 64 MiB total. Oversized snapshots spill
to ordered native I/O; full/failed/unsupported captures retain the native path.
Only the actual advancements directory and original empty writer options qualify.

Per-file reload/direct-save barriers wait for accepted serialization and I/O;
forced saves drain all earlier writes before current native work. Shutdown drains
and joins the worker. Worker failures are retained, reported, disable new async
captures and stop boss combat; earlier failed I/O finishes before native recovery
is allowed to proceed. Profile-cache writes remain synchronous, and journals,
critical durability barriers and the checkpoint deadline are unchanged.
Completion diagnostics now distinguish serialization from disk I/O; the pilot
requires both queues to finish and tests reject a falsely complete disk queue.
Seven new I/O tests cover actual UTF-8 bytes, ordering, slow disk/read barriers,
forced latest state, interruption, unsupported paths/options, oversized fallback,
failure and shutdown. Root/client 1,175, probe 22 and Python 43 tests pass.
Packaged guards: 82 points; shadow targets: 42/14,247. Probe remains `f71c8153...`.
Fresh rig `43a4f26e3d8639bf` is starting for native verification; still unapproved.

Small full event (`pilot-1795674545780245800`, recording
`wbperf-fighters5-958b6f68-b0ec-48e7-bb1a-816655a6dad0.json`) passes functional
admission, battles, saves and durable hold/recover. All 12 advancement snapshots
actually reach the detached disk writer and complete; player/world writes also
drain without error. Native command/listener, ordinary compatibility (19) and
showpiece re-entry (9) checks pass. POI/profile checks pass with six persisted
profiles, one channel open/seven reuses, 139 natural Pokemon and 271 healthy
POI publications. Journal/head match after stop: 89 frames/205,387 bytes.

The full cold gate is INCOMPLETE because that short join phase has fewer than
100 ticks; it was not padded to manufacture a percentile. Separate event gates:
initialization FAIL, 297 ticks, p95 9.9488, p99 32.2727, max 483.4085, interval
484.1599 ms. Samples show a native POI read wait. Gathering PASS (max 64.4574,
interval 64.4964), combat PASS (p95 7.5116, max 26.6268, interval 72.9203),
recovery PASS (max 12.5818, interval 51.2684). Additional first-use stacks show
RaidPlayerRecords/title initialization, Accessories serialization and a native
Pokemon battle-task factory. Those remain findings, not approved fixes.

The final I/O candidate also uses the same strict charset encoder as native
Files.newBufferedWriter; an eighth test verifies malformed-surrogate errors
instead of replacement characters. Root/client suite now passes 1,176 tests.
Gameplay `2af872c60c9c04c27180879fc7162b1f80b93673aef4f5291a408f502a9a66d0`,
probe still `f71c8153031e3450b09866dd64c77f4fae063f0ff4b793cf83d049d3fb2b9112`.
Fresh peak rig `03e5d7136283cfc8` is starting. No current-artifact peak, crash or
overall performance approval is implied by earlier artifacts' results.

## Peak disproves metadata-handle reuse approach

Rig `03e5d7136283cfc8`, evidence `pilot-1795674876578851400`, recording
`wbperf-fighters50-68fc5e18-4d86-4d1c-a485-bd9ffaea9dd2.json`: 26 functional
checks pass, all three captured combat status samples are ACTIVE/NONE, and all
102 advancement serializations and actual detached disk writes finish. Profile/
POI, command/listener, ordinary compatibility (19) and re-entry (9) checks pass.
Stopped journal/head: 102 frames/605,747 bytes, consistent. Timing still FAILS:

| Phase | Ticks | p95 ms | p99 ms | Max body ms | Max interval ms | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| cold login | 170 | 72.6511 | 370.9837 | 1250.8933 | 1270.6288 | FAIL |
| initialization | 1928 | 9.6664 | 14.1956 | 775.0137 | 775.9256 | FAIL |
| gathering | 1307 | 13.8386 | 19.823 | 459.3989 | 459.9791 | FAIL |
| combat | 685 | 14.0416 | 17.1296 | 71.8061 | 116.1067 | PASS |
| recovery | 257 | 10.3595 | 12.31 | 15.7471 | 51.4175 | PASS |

The worst cold tick repeatedly samples JNA CloseHandle inside the candidate's
metadata identity query. Reusing the data channel was insufficient: metadata
handle close can also block Main for 1.25 seconds. This falsifies that approach;
it is replaced, not approved based on the earlier small pass. Initialization
also samples SDK wallet undelivered-voucher SQLite reads (775 ms) and fusion
reads during acquisition announcement. Gathering samples native advancement
directory existence checking (459 ms), before the newly detached JSON writer.
Other cold samples include native configuration/behaviour synchronization and
first-use class/lambda linkage. These costs remain unresolved.

## Prepared profile-cache handles

Gameplay `7db4e3ee6099f7bea3b7156dd46be13dbd564aac7647579ec89e992197243853`,
probe `0891ca9d7a43be2e27225617fcbe935c0a10beb58a35c78781ce3f169b1f6da3`.
On Windows and the exact native cache class, the startup native writer reveals
the actual cache path. A bounded worker prepares a WRITE/NOSHARE_DELETE channel
before admission; readiness joins the existing nonblocking Main continuation.
Opening does not create or truncate a file. Windows pins the pathname against
replacement until release, so no per-save identity I/O is required on Main.
Each native callback still computes current JSON and truncates/writes/flushes
its native bytes synchronously under the cache monitor. The worker closes the
handle afterward; it never serializes or writes player/cache data. No fsync or
critical-journal policy changes. Unsupported platforms/custom cache classes
keep native I/O. An unsupported/failed preparation uses original native cache
creation/error handling and fails performance health diagnostics.

At most 75 tickets are retained through worker release, including cancelled or
not-yet-open tickets. Queue cleanup covers success, errors, disconnect/stale
owners, duplicates, timeouts, rejection and shutdown. Late opens after cancellation
close without truncating/writing. Shutdown drains/joins the worker. File replacement
works between released tickets; it is denied while a prepared native handle is held.
All seven cache tests and eight queue tests pass, covering worker-only open/close,
51 synchronous saves, actual Windows pinning/replacement, slow close, late cancellation,
missing-file native fallback, the 75-ticket cap and cleanup re-entry. Root/client
1,178, probe 22 and Python 44 tests pass; packaged guards remain 82/42.
The pilot now rejects non-ACTIVE combat samples; the preceding run's three samples
were explicitly rechecked with this stronger predicate. Fresh full-event rig
`fd821a144c254529` is starting; no native verdict for this artifact yet.

The L: run retains all 51 clients and passes the native profile/POI check: 51
prepared/opened/used/released handles, zero pending/errors, exactly two startup
native fallbacks, 51 persisted profiles, 224 natural Pokemon and 272 healthy
POI reads/publications. No profile-cache open/close stall remains in its captured
Main stacks. However, checkpoint storage again exceeds two seconds and correctly
holds combat before all 50 battles begin; the functional event fails. Evidence
`pilot-1795675866981967300`, recording
`wbperf-fighters50-e68d70f7-f1ad-42d3-893a-02ab797acd19.json`:

| Phase | Ticks | p95 ms | p99 ms | Max body ms | Max interval ms | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| cold login | 189 | 96.5533 | 172.5681 | 344.0956 | 365.2150 | FAIL |
| initialization | 1917 | 8.5465 | 13.4892 | 62.1483 | 104.0052 | PASS |
| gathering | 3601 | 10.2560 | 14.0030 | 465.8821 | 466.3202 | FAIL |

Combat/recovery were never measured. The long gathering tick again samples the
native advancement-directory existence check, before captured JSON I/O. Cold
samples show Towers chat tags, class definition and native advancement loading.
Command/listener, compatibility (19) and re-entry (9) checks pass. Stopped journal/
head match: 167 frames/730,883 bytes. Current-artifact crash/power-loss testing
has not been approved.

## Storage control and unchanged-artifact C: run

With the owned server stopped, a standalone Java 21 fixture tests only temporary
files in the two project build directories. All created files/directories are
removed afterward. Source: `C:/Users/Facel/SnobblemonRaids/build/FileIoBaseline.java`;
CSV: `C:/Users/Facel/SnobblemonRaids/build/file-io-baseline-2026-11-26.csv`.
Native OS inspection reports separate healthy fixed NTFS disks (C: disk 0,
L: disk 1). For 100 FileChannel.force(true) writes:

| Storage | p95 ms | p99 ms | Max ms | Over 250 ms |
| --- | ---: | ---: | ---: | ---: |
| C: | 0.4365 | 0.7076 | 1.7941 | 0 |
| L: | 34.2199 | 46.1143 | 343.4183 | 1 |

Idle existence/attribute/open/close tests are fast on both (all maxima under
3.061 ms). This demonstrates a durable-write latency difference in this bounded
idle fixture; it does not establish the cause of every under-load metadata wait.
No storage policy, security setting, durability guarantee or live server is changed.

The exact same gameplay/probe artifacts are tested in an actual, non-junction
directory on C: (`build/native-rehearsals/worldboss-rehearsal-2de07c5473fa9fbc`).
Evidence `pilot-1795676563416594100`, recording
`wbperf-fighters50-b7a620bd-68eb-48c3-8b40-fe0d7ef84b7b.json`: all 29 functional
checks pass, all 50 fighters submit moves, all three combat samples remain ACTIVE/
NONE, 102 advancement disk writes complete, and staff hold/recover acknowledges
durably. Profile/POI checks pass with all 51 handles released and profiles persisted,
222 natural Pokemon and 267 healthy POI reads/publications. No checkpoint storage
hold occurs. Native command/listener, ordinary compatibility (19) and re-entry (9)
checks pass. The process is stopped; journal/head match: 100 frames/604,500 bytes.

| Phase | Ticks | p95 ms | p99 ms | Max body ms | Max interval ms | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| cold login | 192 | 52.4179 | 145.3689 | 260.3110 | 276.7216 | FAIL |
| initialization | 1879 | 14.7624 | 18.4988 | 184.9541 | 185.4898 | PASS |
| gathering | 1267 | 14.7647 | 21.5430 | 59.7369 | 60.5401 | PASS |
| combat | 684 | 13.4849 | 18.0259 | 65.0861 | 91.8900 | PASS |
| recovery | 217 | 10.3047 | 12.5370 | 17.5745 | 51.2049 | PASS |

Overall performance remains FAIL at unchanged limits. Cold samples include SDK
join work, native ServerPlayer tick lambda linkage, and advancement loading/map
iteration. A later initialization sample shows native Pokemon battle-look task
construction. Sampling locations alone do not prove their total CPU cost: the
local load generator starts 51 Node processes on the same host. The next diagnosis
should distinguish Main CPU work, cold linkage and generator contention before
changing more gameplay callbacks. Never omit early cold rows, add idle padding,
relax deadlines or treat this one C: result as approval for the L: runtime.
The final artifact is built/tested, with 1,178 root/client, 22 probe and 44 Python
tests passing and 82 guarded injection points/42 shadow targets. Nothing deployed.
