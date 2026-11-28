# Cold native login investigation

This continues [initialization I/O](WORLD-BOSS-INITIALIZATION-IO-2026-11-21.md).
Current candidate builds are not approved for production or capacity. No server
has been deployed. The preceding five/fifty-fighter and 31-check recovery passes
apply to the earlier `48c3f2c7...` gameplay JAR, not these new candidates.

## Reproduction and full-loop evidence

The retained baseline contains a 1,233.7291 ms setup tick and a 5,726.1142 ms
tick-start interval. Its bounded sampler covers tick bodies, not all Main work
between ticks. A fresh diagnostic-only rig `6b2b8c3b26e0d7d2` reproduced a
6,318.0216 ms interval with 51 fresh native clients, before any Pokemon grant or
boss encounter. All clients stayed connected. Its JFR process is explicitly
excluded by the raw validator from capacity approval.

JFR identifies Main waiting 3,462.1112 ms and 829.2396 ms on Netty promises in
native `Connection.syncAfterConfigurationChange` / `setupOutboundProtocol` from
`ServerConfigurationPacketListenerImpl.handleConfigurationFinished`. Netty
threads encode Cobblemon registry/evolution packets, NBT and compressed packets
during that window. Machine CPU was around 45–47%, so overall host saturation
is not established as the cause. Towers warm-pool activity is also present;
its log alone does not explain that network wait.

Evidence is in `pilot-1795557479526839800/login.jfr`. A compact stack-only JFR
extract avoids exposing unrelated recording metadata. Source tools used:
`build/CompactJfr.java` and `build/inspect_login_jfr.py` in the staging workspace.
The stopped rig's one journal frame matches its external head.

## Native login continuation candidate

The final native login callback is deferred on Main until a FIFO Netty
event-loop barrier completes; at most one ready callback runs per server tick.
The original callback and all native admission checks, protocol changes, player
creation and other mods' callbacks remain on Main and run once. Native protocol
barriers themselves are retained. Stale listeners/connections cannot publish;
pending work is bounded to 75, has a 20-second readiness timeout, and is cleared
at shutdown. These are internal safety limits, not new season/balance rules.

Six focused tests cover pending/ready futures, a 51-callback burst, cancellation,
duplicate finish packets, failed barriers, timeout, capacity and reentrant stop.
The first native startup caught incorrect inherited `@Shadow` field access;
failed JAR `15a93065...` and rig `7c01060082675ca1` remain retained. No clients
joined it. The mixin now extends the actual native common-listener superclass.

Corrected candidate `d7517663c1b209b971f483863e7caa2bae5361e9c3fb2ca6259bea27caacaed6`
loaded natively. Rig `bbf146fe3b2ea9a7` applied exactly 51 callbacks, without
rejection, discard or duplication, but **failed** the unprofiled cold gate:
p95 76.1779 ms, p99 194.7721 ms, maximum body 5,048.7679 ms and maximum start
interval 5,069.8893 ms. The first large tick began with zero online players and
is retained. Moving callbacks into ticks did not solve their remaining costs.
The stopped rig's one journal frame matches its external head.

The validator now has an explicit `--joining-phase cold_login` mode. All raw
ticks, including zero-player first-login ticks, remain subject to the unchanged
p95 <50, p99 <100, body <=250 and start-interval <=250 ms limits. Requested
attendance must be reached and present at phase end. This population rule cannot
be applied to combat or other phases. Two tests lock down these distinctions.

## Smaller first-use trace and filesystem preparation

Diagnostic rig `695c0c5e5b2cc8ef` repeated six fresh logins with the same corrected
candidate. JFR captures first-login directory creation inside Cobblemon's
`FileBasedPlayerDataStoreBackend.loadWithFallback`. It separately captures a
509.0052 ms tick in Towers `SlicedBuild.start` / native structure-template parsing,
and later cold natural-spawn/entity construction. These are separate costs.
Its recording retains a 1,136.3970 ms body and 2,516.8839 ms start interval;
diagnostic timings do not approve capacity. All six callbacks applied once.
The stopped rig's one journal frame matches the external head.

The next candidate prepares only native file-backend directory parents before
the callback. Main obtains exact paths from supported native cached factories;
one bounded worker performs `Files.createDirectories`. No player data is read,
parsed, invented, backed up, overwritten or moved by this preparation. Native
load/default/cache behavior remains unchanged on Main. Custom backends and
paths outside the world/game roots retain their native behavior. Preparation
failure prevents publication; accepted directory work drains on shutdown.
Three tests verify existing data remains unchanged, slow filesystem work never
runs on the caller, admission is bounded, shutdown drains, and failures can retry.

Candidate SHA-256:
`a88a1825f9b2ecb88278a0682c9976b99c4108f96660670f6e715fe1b7d01b38`.
All 1,136 gameplay/client tests and 41 Python harness tests pass. The unchanged
SDK/probe retain their preceding 561/22 passing test results. Native directory-
preparation validation completed in rig `d415d191e015d051`: all six callbacks
applied once and 18 directory preparations completed, but cold login still failed
(p95 55.2278, p99 172.2512, max 491.9787, start interval 556.3365 ms).

## Native cache preparation and managed console output

Read-only startup preparation binds native game protocol codecs and loads up to
16 existing Towers structure templates through the native structure manager.
It allocates no tower cell and pastes no blocks. Candidate `58a50440...` loaded
six templates in rig `8375891e2e73a1e6`; six fresh native logins still failed
(p95 60.2684, p99 163.9398, max 558.9924, start interval 585.3422 ms).
Slow Main stacks include `FileOutputStream.writeBytes` / native Log4j console
flushes, as well as first-use Pokemon constructor classes. This supports fixing
console I/O separately; it does not establish that console output caused all
remaining latency.

Console candidate SHA-256:
`34f99b4995355dbfae97305b7f344307c1918f6dcb1f9d94d38cd619d8504aef`.
Only dedicated-server, filter-free native ConsoleAppenders are wrapped. Message
and context formatting happen on the caller before dispatch, preserving native
metadata. Native Log4j owns the bounded 2,048-event AsyncAppender and its shutdown
drain/reconfiguration order. Its bootstrap cannot inherit game thread context.
The queue is lossless and blocking on saturation: it cannot promise latency under
unbounded logging. Custom console filters/configuration reloads retain native
behavior. Two tests cover detached message/context metadata and bootstrap context.

Fresh unprofiled rig `ac4a539f43dc2d3c`, evidence
`pilot-1795562044798512400`, recording
`wbperf-fighters5-c0a010e5-3df4-4687-98aa-8b044249e99e.json` confirms one managed
console, six exact-once native callbacks, zero rejected/discarded callbacks and
18 directory preparations. **Cold login still FAILS:** 614 ticks, p95 63.2384,
p99 180.0135, max 519.6938, start interval 533.3150 ms. All zero-player rows
remain included. No console-write stack was captured in the three slowest ticks;
this is limited stack-sampling evidence, not proof of zero console cost.

The first login samples show native stats collection and Pokedex MoLang lambda
linkage; another sample shows `OneToOneFileStoreAdapter.getFile` creating party
directories outside the existing player-data backend preparation. The worst tick
samples show a native chunk/POI future wait from SaccharineLog prospecting and
first-use `OmniPathNodeMaker` loading during natural Pokemon construction.
Natural spawning stays enabled; no fake clients/entities are used for warmup.
The stopped rig's one journal frame matches its external head (file consistency,
not power-loss certification).

Candidate `0602db5b4982b986b0c14c04c4f52cf747607be7baeb5526604da85841c8f48a`
prepares an ownerless in-memory abstract dex function map
only when no custom Pokedex function providers are registered, plus an unbound
path node evaluator. Native constructors were inspected: neither registers a
player/Pokemon/store, schedules a task, reads player data or attaches a world.
Custom providers are skipped because they may require a real owner.
Fresh rig `5e50eae07827cfe1`, evidence `pilot-1795562493334934200`, recording
`wbperf-fighters5-383077fd-c69d-4aae-977a-55e85cf2f850.json` confirms both warmups,
six exact-once callbacks, 18 directories and one console. Cold login still FAILS:
586 ticks, p95 58.8966, p99 142.1634, max 447.1528, start interval 501.6326 ms.
The two slowest ticks sample first-use native connection/command classes and
the same party-directory `mkdirs` call during AscensionLib's join telemetry.
Natural spawning still samples cold Pokemon experience/entity classes and POI
future waits. The stopped rig has one journal frame matching its external head.

The next extension prepares party/PC directory parents from registered native
file factories. Read-only mixin accessors expose immutable adapter layout fields;
Main calculates detached parent paths, and the existing bounded worker creates
directories before the unchanged native login continuation. No party/PC object,
file content, backup, acquisition operation or store cache is created. Custom
backends and roots outside the world/game directory remain native. A parity test
compares calculated parents to the real native NBT adapter across every nested/
per-class layout, trailing slash variant, party/PC type and Turkish default locale.
This tests the dependency's actual path behavior instead of copying expected
strings. Kotlin's already-cached test dependency is now also on the test compile
classpath for this native fixture.

Directory-extension SHA-256:
`6780446687e33e6857846ff20ac809ebf3141137fb67059afa981d8b14f749af`.
Fresh unprofiled rig `d468683c8e17ea34`, evidence `pilot-1795563029465277200`,
recording `wbperf-fighters5-07008e12-6cf9-4337-a966-c974c662fbd9.json` confirms
six exact-once native callbacks, zero rejected/discarded callbacks and 30
directory preparations. Cold login still FAILS: 610 ticks, p95 65.6647, p99
160.2549, max 592.0231, start interval 600.0331 ms. The first login is 373.0844
ms (stats predicate hashing and native command-packet construction); its following
tick is 233.2502 ms and no party-directory mkdir stack was captured. This is
limited sampling evidence and cannot establish an isolated latency improvement.
The maximum tick occurs with six players, sampling natural spawn selection,
Pokemon navigation construction and AI-task lambda linkage. A separate 246.0929
ms tick samples native Towers cell block placement. No spawn rules or latency
limits were relaxed. This candidate does not inherit the old baseline's combat,
crash-recovery or capacity approvals.
The process exited after native `stop`; the final one-frame journal matches its
external head. All three new timing rigs are stopped. This audit verifies file
consistency only and does not recertify native crash recovery for this JAR.

Identity-cache writes, stats/other first-use classes and native POI waits remain
open. All 1,139 gameplay/client and 41 Python tests pass for the directory
extension; packaged checks pass at 72 injection points, and the existing
shadowing validator passes for 40 targets (accessor-only additions do not add
override targets).
The unchanged SDK/probe retain their preceding 561/22 results.

## Advancement listener and command graph hashing

Correction to earlier labels: `class_2985` is `PlayerAdvancements`, not player
statistics. Its listener enrollment called native record hashing on complete
criterion predicate trees for every player. The earlier raw stacks remain valid;
the interpretation of that part of the first-login cost is advancement setup.

Listener candidate SHA-256:
`48237d8460f54310035699a3b2ebc253c004a3a4330cad26d9c31152af4df187`.
A guarded native `CriterionTrigger.Listener.hashCode` wrapper buckets by holder
and criterion name. Native record `equals` is unchanged; distinct predicates can
collide and still coexist. Equal listeners necessarily have equal holder/name
hashes. No predicates, progress, event callbacks or listener ownership change,
and no cache retains old datapack objects. Before the fix, a regression fixture
observed 100 unwanted predicate-hash calls; after it, three tests verify zero
traversals, unequal-trigger collisions, deduplication, reconstructed removal,
reload holders and null fields. The full build passes 1,142 gameplay/client tests.

Fresh unprofiled rig `5848fbf05ee08db1`, evidence `pilot-1795564525851484700`,
recording `wbperf-fighters5-924eb6d4-57eb-45bd-aff7-af576c66dc0a.json` applies
all six logins once with 30 directory preparations. Cold login still FAILS:
579 ticks, p95 83.4808, p99 289.0811, max 459.2953, start interval 487.3102 ms.
The first-login stack now includes recursive Brigadier `CommandNode.hashCode`
inside the native command-copy map. A separate 426.0189 ms tick samples native
RollingRandomAccessFileAppender writes at both 130 and 348 ms. Later samples
include cold Pokemon construction and native pathfinding. No isolated latency
gain is inferred from a single trial or the absence of a sampled stack.

After recording stopped, `wbperf listener-hash-check` passes collision,
deduplication, reload and removal against the injected method and all 5,145
loaded native criteria. The fixture registers no listener and grants no player
progress. Subsequent recording is forbidden after any such functional fixture;
the rejected restart attempt created no new recording. The server is stopped;
its one-frame journal matches the external head. The probe was rebuilt for the
new post-timing command; prior probe/SDK hashes do not describe this candidate.

The next candidate replaces only the native command-copy local map with an
IdentityHashMap. Nodes are graph references, so structurally equal nodes must
retain distinct redirect targets. Native permission filtering, cloning and
packet construction/send are unchanged. A pair of real Brigadier-node tests
first reproduced recursive hashing and collapsed equal-node targets, then passed
with the identity map. Existing entries from another wrapper are preserved.
The packaged guard validator now also checks `@ModifyVariable` handlers.
A post-timing native fixture covers permission levels 0/4, denied nodes, two
equal-node redirect targets and zero command execution; it cannot run during
timing or be followed by another capacity recording in the same process.

Command-map candidate SHA-256:
`d892c27505e99ed3e9cb44f8cd195f800fa068bee794293c4a435c26bc17907d`.
Probe SHA-256:
`7460bbd9ea8e74e3d487c350e0526a19b48cd991916eb1a85170e4c5a0b42e72`.
Rig `aebe7788c6401126` was stopped before any clients or timing pilot ran: further
bytecode inspection found the same structural-key issue in native packet node
enumeration. Its stopped one-frame journal matches the external head. It has no
latency result and must not be listed as a timing pass or failure.

The complete graph fix also changes the native `ClientboundCommandsPacket`
enumeration map to a primitive Object2Int custom map with reference identity
hash/equality. Existing entries and the original primitive default return value
are retained. This avoids collapsing equal leaf nodes during packet construction,
after the first map already cloned them correctly. A third regression first
reproduced collapsed IDs, then passed with distinct IDs and zero node hash calls.
The post-timing native fixture now reconstructs a native command packet and
checks both alias endpoints again. No command runs and no fabricated player or
entity is constructed for this fixture. All 1,145 gameplay/client tests pass;
the packaged guard validator covers 75 injection points.

Complete graph candidate SHA-256:
`756b8ba391294f8f67012611f401e8e39408a9f8f55c17ba4aa6b7daaa134b9d`.
Probe SHA-256:
`e2d6ded187732e092997119a4767465e57a9e6cf5cbbb988bceee7ac864721f4`.
Rig `9ad2cd25130dbad2`, evidence `pilot-1795565329268623800`, recording
`wbperf-fighters5-8e46cdb7-e590-4f4e-8191-08012e511260.json` confirms six
exact-once logins, six native copy maps and six native packet maps. Cold login
still FAILS: 543 ticks, p95 51.9055, p99 126.0445, max 429.0567, start interval
450.5290 ms. The first-login sample shows native player constructor class loading
and concrete Pokedex MoLang lambda linkage; another 301.1511 ms tick shows first
navigation-context loading during natural Pokemon construction.

After timing, native command checks PASS: both mapping stages ran six times,
permission levels 0/4 and denied nodes match native filtering, both distinct
alias targets survive native packet reconstruction, and zero commands execute.
The listener check also passes all 5,145 loaded criteria. The stopped rig's
one-frame journal matches its external head. No cold, combat, recovery or capacity
approval is inferred from these functional fixtures.

## Correcting the native DTO warmup coverage

The abstract-manager warmup missed a real branch: `PokedexMoLangFunctions`
conditionally creates `player_id`, `see` and `catch` bindings only when its
argument is a concrete `PokedexManager`. The next candidate constructs an
unpublished concrete DTO with a zero UUID and local empty map; it never obtains a
player, registers a store, schedules synchronization or executes those functions.
Its inspected constructor calls the base initializer and retains only the UUID
and map. Custom providers still cause the warmup to be skipped. Native
`OmniPathNavigation.NavigationContext()` also prepares default no-op callbacks
without constructing navigation, a world or a Pokemon. Its inspected constructor
only stores callbacks/scalars.

DTO-warmup candidate SHA-256:
`eb6996adef216551897bb6c3433f5a094a5663e46002b7fd979001ee339e6d13`.
It passes the full 1,145-test gameplay/client build; the probe is unchanged from
the complete graph candidate. Rig `55789abfbacbcbfb`, evidence
`pilot-1795565725923826600`, recording
`wbperf-fighters5-36d69560-d7b1-4ad1-b1ab-14d817e58370.json` confirms both DTO
warmups and six exact-once logins. Cold login still FAILS: 548 ticks, p95 59.3265,
p99 164.9160, max 792.1989, start interval 807.6131 ms. The first login samples
native advancement data-codec initialization and a cold iterator loaded by the
new packet-map copy constructor. The largest tick samples natural spawn selection,
FindRestingPlaceTask factory lambda linkage, then native entity-map iterator
class loading. A separate 263.0993 ms tick samples Towers cell block placement.
The stopped rig's one-frame journal matches its external head.

The next candidate avoids `putAll`/iterator initialization when the incoming
packet map is empty, while retaining populated maps and their primitive defaults.
Startup now also builds an unsent empty native command packet, an unbound native
resting-place control, and an empty native integer-map copy. Bytecode inspection
shows the control factory creates behavior-builder closures; it receives no
entity/world and its action is never started. Zero range values are captured only
in that discarded control and are never applied to a Pokemon. These changes
create no player/Pokemon, register no store/listener and execute no command.

Candidate SHA-256:
`03bb1c3ea0435dc3c9c527182ba612e6da943dc4b7f89f94f63ff853f8103868`.
The full 1,145-test build passes; fresh validation is underway in rig
`29fcce40619cabad`. Overall performance approval remains blocked. Rolling-log
I/O, other cold Pokemon construction, native pathfinding/POI and Towers cell
placement remain separate targets.
