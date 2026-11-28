# Native acquisition and idle autosave stalls

This continues the [native recovery repair](WORLD-BOSS-NATIVE-CRASH-2026-11-21.md).
Nothing here deploys a server, enables a season or approves live rewards.

## Reproduced failures

The retained fifty-fighter recording in rig `8da47e5752efe317` contains a
3,500.747 ms setup tick (4825). Three bounded samples show Main in native
compressed player-file writing through the synchronous autosave path before an
active event. Separate 1.2-second setup ticks show SQLite commit through
`AscendWiring.finishAcquisitions`, `ProfileService.acquire` and
`ProgressionStore.acquire`. Samples identify blocking paths, not every
millisecond of those ticks.

Native baseline rig `c3830a36f962b043`, gameplay SHA `7332065b...`, fails the new
pre-event queued-player-save assertion before any boss starts. Its failed report
and raw recording remain in campaign trial 48; missing encounter phases are
correctly INCOMPLETE, never counted as capacity approval. The process stopped;
its one journal frame matches the external head.

The deterministic `AcquisitionQueueTest` held the real SQLite acquisition at
`BEFORE_COMMIT`. A worker sharing the primary store caused a canonical read to
time out at 200 ms. Using a separate WAL writer connection makes the same test
pass: the read returns the previous committed state while the receipt remains
incomplete. SQLite retains `synchronous=FULL`; no durability setting was relaxed.

## Repair contracts

Native automatic, silent, non-flushing/non-forced dedicated-server saves now
qualify for the existing detached player/world NBT queues and paced advancement
saving even without an active boss. Native serialization and hooks remain on
Main. The existing queue/byte limits, fallback behavior, failure reporting and
load/forced-save/backup/shutdown barriers remain. Direct and critical native
saves may still block; this change addresses ordinary autosave eligibility.

AscensionLib automatic acquisition captures immutable Pokemon identity, origin,
types, level, tier and private randomness on Main. One bounded writer (128
accepted jobs) uses its own SQLite WAL connection, preserving the authority,
once-only operation key and post-commit telemetry. The canonical reader keeps
its own connection and does not share the writer's Java monitor. Aggregate
wallet readers now end their read transactions too, preventing stale WAL
snapshots from breaking a later write.

Main polls completed receipts without waiting, publishes at most two per tick,
rechecks server/service/owner identity, projects the latest canonical revision
and then sends the existing acquisition notice. No native Pokemon or server
objects reach worker jobs. Projection acknowledgement writes use the same
bounded writer; a refused acknowledgement leaves canonical authority intact.
The pending request/receipt maps are bounded to 1,024 combined entries. Writer
backpressure retains pending requests; shutdown drains accepted work before
closing either connection. Direct synchronous API acquisitions, crafts,
milestones, rewards and critical barriers remain synchronous and may contend
with the writer. This is not a universal asynchronous economy implementation.

A schema-1 `ascensionlib_pending_acquisition` marker travels with the native
Pokemon until canonical projection. It retains subject, validated origin,
optional acquisition identity, rarity policy and tier. Login and received-Pokemon
scans retry only marked requests; old unprofiled Pokemon are not retroactively
granted profiles. Saved markers preserve requests through restart; already
committed operations replay without another roll. Owner changes, failed commits,
malformed markers and queue saturation cannot invent a committed profile. This
uses vanilla Pokemon persistence, not a new forced cross-mod journal: a request
not yet present in either durable store or a saved Pokemon still has no new
power-loss guarantee. The exact interrupted native-save boundary remains a
separate native rehearsal.

## Verification and artifacts

Full builds/tests pass: 1,127 gameplay/client tests, 561 configured AscensionLib
tests, 22 probe tests and 37 Python harness tests. Eight new AscensionLib tests
cover nonblocking reads at a stalled commit, post-commit publication,
deduplication/no reroll, aggregate-read/write visibility, rollback/retry,
bounded admission/draining shutdown, saved-marker retry across database reopen
before/after commit, and pending-marker validation. All 71
packaged gameplay mixin fault guards pass.

Paired gameplay SHA-256:
`48c3f2c7a9013aef1b68779d76ddcae85cea9c1f637144a98530dc1c71f70e53`.
AscensionLib SHA-256:
`cb48273c366f1269fb75b163cbfeec20359586769845dcfef1e9e7790dffcfe5`.
Probe SHA-256:
`5fb6b28bf27eadecefb21f6252d6adab08fa4a1360ffa135ca349b2f414bbaa7`.

Fresh paired-build fifty-fighter revalidation passed in rig `9b11cda149e12814`.
The pilot now explicitly records initialization (all clients connected, before
party grants through confirmed native profile notices and pre-event autosave)
in addition to gathering/combat/recovery. The original p95/p99/body/start-interval
limits apply to this additional phase too. Setup/joins/world-building remain in
the raw recording and are never silently removed. Recovery-only, traced,
prototype or restarted processes remain excluded from capacity approval.

Campaign trial 49 passes all 25 functional checks and all four unchanged phase
gates with 51 clients retained throughout each measured phase:

| Phase | Ticks | p95 ms | p99 ms | Maximum ms | Maximum start interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Initialization | 4,445 | 30.5077 | 38.2945 | 245.8006 | 246.5416 |
| Gathering | 1,329 | 42.3408 | 64.8631 | 142.7507 | 146.7780 |
| Combat | 818 | 42.8513 | 55.2659 | 225.1634 | 226.3473 |
| Recovery | 372 | 21.2892 | 30.4434 | 36.8924 | 51.9379 |

The explicit pre-event save accepted all 51 snapshots and returned on Main in
60.3642 ms. The earlier natural automatic batch also used the queues, so the
explicit check advanced submitted snapshots from 51 to 102. Player/world and
advancement queues finished healthy. Initialization's maximum has little
headroom below 250 ms: this is one scoped pass, not a robustness guarantee.
Its retained near-limit samples show native statistics JSON and advancement
file operations on Main; those paths remain separate work.

After timing, all 19 native compatibility and nine showpiece checks passed.
The process stopped cleanly; all 133 final journal frames match the external
head (874,629 bytes). A read-only SDK database audit reports `quick_check=ok`,
53 canonical profiles, 53 ACQUIRE operations and zero projection backlog
(51 attendance clients plus two compatibility clients).

Overall server-wide performance is still unapproved. The complete recording
retains a 1,233.7291 ms setup tick (1314), with samples in cold Cobblemon MoLang
advancement class loading, native player processing and login checks. Another
314.8439 ms setup tick samples cold Pokemon entity construction and native
player movement. Setup's maximum tick-start interval is 5,726.1142 ms (1329);
the body-only sampler does not attribute that outer interval. No sample was
dropped. These cold-login/setup costs were not removed or relabeled as passes.
Retained GC evidence has no dropped notifications: the longest reported GC
duration in the recording is 72 ms. Nineteen notifications overlap that setup
interval, with 155 ms of reported durations in total (including concurrent
collector notifications). These do not attribute the remaining multi-second
gap; a full-loop diagnostic trace is still needed rather than assuming one
long GC pause explains it.
Live rewards, full parties, graphical clients, concurrent critical SDK writes
and native interrupted-acquisition persistence remain separate release gates.

Fresh five-fighter paired-build rig `58751363cbe7fa57` passes all 25 functional
checks and four phase gates in campaign trial 50:

| Phase | Ticks | p95 ms | p99 ms | Maximum ms | Maximum start interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Initialization | 543 | 10.1706 | 20.2486 | 89.3331 | 92.9847 |
| Gathering | 1,053 | 18.4706 | 31.8165 | 122.8381 | 122.8798 |
| Combat | 766 | 13.3828 | 23.1119 | 36.9473 | 100.0122 |
| Recovery | 330 | 14.2159 | 21.3514 | 24.5667 | 51.8168 |

Its complete recording still retains setup's 299.0357 ms body and 2,562.7017 ms
maximum start interval. The process stopped; all 75 final journal frames match
the external head (170,926 bytes). Logging (377 sources), API boundary (16 types)
and raid integrity validation pass. Both capacity rigs use the exact paired
artifacts above. Production approval remains open.

A fresh paired-build native crash/restart repetition passed all **31 checks** in
recovery-only rig `531de84b25506727`, evidence
`native-crash-1795314608409850900`. Its recovery inspector also captures read-only consistent
SDK profile/ACQUIRE receipt snapshots before crash and after login, to detect
rerolls or duplication. Two Python tests ensure missing stores cannot be created
by inspection and uncommitted rows are never reported as durable evidence.

The owned original JVM (PID 20220) was forcibly terminated with five active
duels. The replacement (PID 31468) replayed authority generation 118 (279,660
valid bytes, no partial tail in this run) without the rebuildable projection or
vanilla force-load tickets. Boss identity, fixed health, contribution units,
active budget, frozen rules/roster and travel origins matched; one standing boss
returned and old duel entities did not. All six native party identities and
marked seven-diamond stacks survived. All six canonical SDK profiles and all
six ACQUIRE receipts matched exactly, without rerolls or duplication.

Five fresh native encounters then opened after the full regroup, all five
clients submitted moves and every encounter ID differed from before the crash.
Staff hold acknowledged. The replacement stopped cleanly; all 185 final journal
frames match the external head (457,840 bytes). The stopped SDK database reports
`quick_check=ok`, six profiles, six ACQUIRE operations and zero projection
backlog. All test JVM generations are exited. Nothing was deployed.

This repeats the saved-baseline process-crash case on the exact performance-
tested paired artifacts. It does not exercise a native save interrupted while
an SDK acquisition is still uncommitted, a partial journal frame, a live payout
or physical power loss. Those boundaries and concurrent economic actions during
queued acquisition remain release gates, alongside the cold-login latency work.
