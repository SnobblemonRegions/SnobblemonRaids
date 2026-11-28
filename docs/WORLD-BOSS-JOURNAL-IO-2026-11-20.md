# Periodic journal I/O — November 20, 2026

This fixes the synchronous recurring-checkpoint path sampled in the previous
1,361.3166 ms combat tick. It does not eliminate all synchronous persistence or
approve overall production performance. The gameplay candidate is policy v17,
JAR SHA-256 `2421d66caee6cd0a1ec0d7d73c448127c4b7ce2cd9f5054667de8dc75400a4a5`.
The mod version remains `0.8.210-projector-follow`.

## Change and invariants

`WorldBossCheckpoints` accepts at most one captured snapshot, on its owning server
thread, and commits it on one dedicated worker. NBT is detached before dispatch;
immutable promise amounts are captured with it. No player, world, registry lookup
or mutable encounter state is read by the worker. Compression, file validation,
journal forcing and witness replacement happen there for recurring checkpoints.
The same journal format, checksum chain, forced writes, witness verification and
compaction/recovery logic remain in use.

The journal publishes an immutable acknowledged view only after the external
witness succeeds. Pending/failed generations cannot publish their promise list,
sequence, snapshot or projection as an acknowledged result. Main-thread polling
publishes the collection, blessing and scene views captured with that generation;
it cannot accidentally publish newer unsaved changes. Submission clears only the
captured dirty generation, and later mutations remain dirty. There is no unbounded
queue or caller-thread fallback for periodic I/O.

Critical commits drain any earlier periodic checkpoint before appending their own
generation, preserving promise and payout ordering. Autosaves retain the same
barrier. Shutdown drains accepted I/O before refreshing the rebuildable cache and
closing the writer. Interrupted waits fail closed; close still waits for accepted
I/O and restores the interrupt flag, preventing a subsequent server from sharing
the recovery set with an abandoned writer. Shutdown or critical transitions can
still wait for slow storage. No durable acknowledgment is fabricated to avoid that
wait.

If a periodic write is still outstanding after two seconds, the server places the
encounter in an in-memory STORAGE_FAILURE Hold and aborts its duels without waiting
on that write. Automatic world-boss tick adapters are deferred while it remains
overdue, preventing arrival/return or economic adapters from waiting on that
same pending write in the tick. Once storage completes, a later checkpoint can persist the held
state. If storage fails, the authority quarantines instead; it cannot claim to have
persisted the Hold. Failure is reported once, and later writes/payouts remain
blocked. A crash can restore unacknowledged combat progress only to the last
recoverable complete generation. This is not per-hit durable damage acknowledgment.

## Verification

Before implementation, all three initial checkpoint regressions failed against
the extracted synchronous behavior: witness forcing ran on the caller, submission
propagated disk failure synchronously, and close did not reject subsequent work.
After implementation, the regressions exercise the real journal and witness files.
Further coverage verifies a stalled witness cannot publish early, detached NBT
survives caller mutation, one outstanding write enforces backpressure, a fake
monotonic clock detects overdue writes, and state-level publication cannot expose
newer unsaved benefit settings or clear their dirty flag.

A separate interrupted-drain test failed before its shutdown fix, proving close
returned while a gated write was still active. It passes after close was made to
drain accepted I/O even when the owner thread was interrupted.

Full offline JDK 21 `test build` passes 1,050 root plus eight client tests (1,058
total), including the existing forced-child-JVM stop between frame and witness,
truncation, checksum corruption, compaction and immutable payout recovery tests.
Raid integrity, logging discipline and API boundary checks pass; the remapped JAR
passes all 37 mixin fault-barrier checks. Calibration fingerprints change to v17
so old measured policy approval cannot silently authorize this candidate.

The intermediate `cdde5116...` artifact was started in unused fresh rig
`worldboss-rehearsal-07ef64aaa2eab93c` only before the interrupted-shutdown fix.
It was stopped without running a pilot. It is not the final candidate or a trial
in the timing campaign.

Comparison rig `worldboss-rehearsal-8ad2a5b1947413ff` uses intermediate candidate
`c3ae41d05404109afb6d02ce7d1ce2582325e51552db4050b4cbff3e67f47e5a`, before
the final guard that defers automatic adapters while a checkpoint is overdue.
Its nonce-bound simulator probe is unchanged
from the failed previous trial: SHA-256
`f672db51969f0c65813987d256ceb6009222ef79368e51376868187b1147c495`.
That worker is still diagnostic and its recordings remain ineligible to approve
production capacity. The final artifact requires its own native control, recorded
below when complete. Results from the intermediate candidate are not silently
attributed to the final artifact.

### Intermediate diagnostic comparison

Rig `8ad2a5b1947413ff` passed all 14 native functional checks with five fighters,
one observer, normal client physics, global Pokémon spawning enabled and a
30-second combat window. All five fighters had credited damage; Hold/recovery
preserved 35,810/40,000 HP. The spawn guard recorded 21,723 early skipped position
passes and 1,028 late entity refusals.

| Phase | Ticks | p95 ms | p99 ms | Maximum tick ms | Maximum interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gathering | 1,040 | 25.5929 | 45.5054 | 563.9343 | 566.6270 |
| Combat | 682 | 18.3473 | 26.1016 | 139.0394 | 139.0680 |
| Recovery | 252 | 17.9135 | 20.9758 | 26.6333 | 807.8857 |

Combat meets the original nominal limits; gathering and recovery do not. The
gathering maximum samples the unchanged synchronous critical commit path under
`flushMechanics`. The combat maximum has one sampled stack in Log4j output, not
the recurring journal force path. The retained recovery maximum interval occurs
at tick 3299 (epoch 1795195188629 ms), coinciding with the staff Hold command's
completion (1795195188630.7292 ms); that tick itself is 9.2038 ms. This is
consistent with the synchronous staff barrier running outside the measured tick
body. It is not excluded or reclassified to approve the run.

The full record retains 3,523 ticks, including setup maximum 1,025.8938 ms and
all-record maximum start interval 2,163.9017 ms. The prototype reporter remains
INCOMPLETE. There are 54 completed simulator jobs, no queued/discarded work or
failure, all 87 callbacks applied, and no live/unclassified simulator battles.
Only the recorded startup ID `8d719e65-e5fc-41a3-9b42-bc914d4ec4c0` remains.
Maximum worker queue/execution times remain recorded at 256.4113/498.0017 ms.

Evidence: `pilot-1795195019628396600/report.json`,
`logs/wbperf-fighters5-c5e53c23-f058-4e59-bfc3-c39de67eaceb.json`, its adjacent
comparison/gate JSON, and `logs/worker-cleanup-stats.json`. The encounter and
owned server stopped gracefully after inspection. Separate cold rigs, RNG and
host activity prevent claiming an isolated percentage speedup against the prior
1,361 ms run.

### Final artifact native control

Rig `worldboss-rehearsal-8f2e89f86b0a5185` uses the final `2421d66c...` artifact
and the original tick-only recorder, with no simulator worker marker. This control
checks the gameplay persistence change independently of the diagnostic worker.

The final control passed all 14 functional assertions with five fighters plus
one observer, normal client physics, global Pokémon spawning enabled and a
30-second combat window. All five fighters had credited damage; Hold/recovery
preserved 35,989/40,000 HP. The guard recorded 22,069 early skipped position
passes and 948 late entity refusals.

| Phase | Ticks | p95 ms | p99 ms | Maximum tick ms | Maximum interval ms | Original phase gate |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Gathering | 1,029 | 29.4151 | 81.7431 | 739.4324 | 739.8423 | FAIL |
| Combat | 684 | 23.6783 | 31.2299 | 42.5255 | 124.4785 | PASS |
| Recovery | 241 | 22.8617 | 28.5804 | 31.1318 | 122.3967 | PASS |

The original reporter returns **FAIL overall** because gathering still exceeds
250 ms. This is actual gameplay code with the original tick-only probe, not a
successful diagnostic worker timing attributed to the gameplay JAR. All 3,456
raw ticks remain; setup maximum 1,029.2045 ms and all-record maximum interval
2,831.2776 ms are retained. There are 172 sampled stacks and none dropped.

At the gathering maximum, tick 2432, 21 of 22 samples include the native
`GraalShowdownService` path; none sample `force0`. The first sample includes
lambda/class initialization under native battle startup. This remaining
synchronous simulator-admission blocker is separate from the fixed recurring
journal path. Critical admission/transition writes remain as documented and are
not universally removed or approved by these combat numbers.

There were no runtime checkpoint-failure, overdue-write Hold, scheduling-failure,
shutdown-drain-failure or state-save-failure markers. The existing native fresh
account `.old` warnings remain (18); this is not a claim of an error-free modpack.
After stopping the encounter, status confirmed no active World Boss. The owned
server stopped gracefully with all dimensions saved and its RCON listener closed.
An offline post-stop audit verified all 104 complete journal frames, sequence
1 through 104, their checksums and chain, and the final external head matching
the final frame. This verifies observed file consistency, not physical power-loss
behavior. The existing crash-kill recovery regression still passes.

Final evidence under the rig:
`pilot-1795195363080377600/report.json`,
`logs/wbperf-fighters5-eccfdd92-72b9-4927-b7e9-b570024bb89f.json`, its adjacent
`.gate.json`, `logs/journal-control-cleanup.json` and
`logs/journal-post-stop-audit.json`. The timing campaign retains both actual
continuation trials, bringing the indexed campaign to 28 trials; the unused
earlier boot is not counted as a completed timing pilot.

## Remaining scope

Admission/travel, scene/economic transitions, staff commands, autosaves and shutdown
retain synchronous durability barriers. The simulator production implementation
and long autosave-inclusive/full-party/real-client verification remain open.
No change removes forcing, changes recovery formats, grants a live reward or
activates a season. No production deployment, commit or push is authorized by a
successful local pilot alone.
