# Acknowledged arena arrivals — November 20, 2026

Policy v19 replaces the automatic arrival path's three forced saves per player
with acknowledged batches of at most eight players. Mod version remains
`0.8.210-projector-follow`. Gameplay SHA-256:
`1a2b32836edd3d3aed711fd8d27c672844b11a96235150005a82a31a28e7a989`.

## Ordering and player experience

The server plans at most two arrivals per tick, reserving fighter lanes and
spectator landing bodies. Settings and outbound return tickets are captured
together in a detached checkpoint. Its receipt becomes acknowledged only after
the existing journal frame and external witness have both been forced. Players
cannot teleport before that receipt is acknowledged.

The continuation then rechecks the occurrence, connected player instance,
position, party/role, admission window, exact outbound operation, and loaded,
clear landing. Native teleports remain on the server thread, at most two per
tick. Successful native moves update the matching arrival tickets. A second
detached checkpoint must be acknowledged before releasing staging reservations,
allowing automatic enrollment or publishing arrival success. Another batch
cannot start on the completion frame.

The queue plus prepared arrivals remains bounded at 75. Spectator admission
remains bounded at 25 including pending watchers. Cancellation or disconnection
before movement prevents the move; return requests during a save ask the player
to wait rather than consume the protected origin. Session and event checks
prevent stale callbacks from releasing a successor event's reservations.
Failed or overdue writes retain the existing storage Hold/quarantine behavior.
The batch cannot retry an already attempted teleport after a failed save.

Automatic adapters yield while a batch is outstanding; simulator actor-output
delivery yields during its actual disk waits so qualifying or phase-transition
commits do not immediately block on that write. Simulator admission timeout and
storage-overdue checks remain active. The worker receives detached NBT only.
Receipts are runtime generations, not a new journal or saved-data format.

Explicit manual commands, critical encounter transitions, Merit qualification,
and exceptional evacuation still use their existing critical save behavior.
This work does not establish that all main-thread disk I/O has been removed.

## Verification

The root/client suite passes 1,076 tests (1,068 root and eight client). New
regressions cover origin-before-teleport ordering, arrival-before-success
ordering, revalidation, two-move budgets, refused moves, and failed arrival
saves without replay. Checkpoint tests verify receipts remain unacknowledged
while a witness is blocked and forever unacknowledged after a failed witness.
Mixin guards pass all 47 injection points; logging (359 sources), API boundaries
(16 public types), and raid integrity pass.

The validation-only probe passes 22 tests, SHA-256
`b47a0a2addef2106643e768ec892cf2faea8b2679909f2667d687af8d6b6b468`.
Its diagnostic watcher now attempts at most three stack samples per slow tick,
first after 100 ms and subsequent samples at least 200 ms apart. All raw tick
durations and start intervals remain recorded. The 512-stack cap and rejection
of actual overflow remain unchanged. Raw records declare the sampling policy;
this improves stack coverage without relaxing any performance threshold.

## Native five-fighter result

Fresh rig `worldboss-rehearsal-3b09560db4e8b8ae`, pilot
`1795202301070171900`, normal physics and native Pokemon spawning enabled:
all 14 functional checks pass, all five fighters deal damage, and the observer
receives no private battle. Final HP is 36,247/40,000. Recording:
`wbperf-fighters5-da9badef-5ec9-4a0a-8b8d-f07f8c61ac49.json`.

| Phase | Ticks | p95 ms | p99 ms | Max tick ms | Max start interval ms | Gate |
|---|---:|---:|---:|---:|---:|---|
| Gathering | 1,064 | 21.5353 | 43.7652 | 195.5638 | 856.8783 | FAIL |
| Combat | 684 | 17.5736 | 24.4285 | 41.6594 | 63.1834 | PASS |
| Recovery | 245 | 15.4129 | 20.2036 | 25.5593 | 293.5496 | FAIL |

No raw ticks or diagnostic stacks were dropped. Gathering's tick maximum is
below the prior v18 five-fighter 541.1716 ms maximum, but the full gate remains
failed: start intervals include work outside the recorder's tick body. The
856.8783 ms interval ends at 14:19:42.320 America/New_York, around the TEST-start
command; the 293.5496 ms interval ends at 14:21:17.003, around staff recovery.
This timing correlation does not assign all elapsed time to a particular stack.
The longest sampled gathering body is native duel placement; a separate sampled
body builds dome item data. Setup retains three ticks over 250 ms and sampled
AscensionLib SQLite projection commits. None are reclassified or excluded from
the raw evidence. Overall recorded maximum is 524.0616 ms.

The rig stopped gracefully. A post-stop audit checks all 61 journal frames,
checksums, sequence links and matching external head. This verifies file
consistency after graceful stop, not a native kill/power-loss scenario. The
campaign retains this failed timing gate as trial 31.

## Native fifty-fighter result

Fresh rig `worldboss-rehearsal-13b40eb0f105a562`, pilot
`1795202625142339900`, same artifact and probe, normal physics and Pokemon
spawning enabled. All 14 functional checks pass. All 50 fighters receive an
acknowledged arrival, a native battle and damage credit; the observer receives
one acknowledged terrace arrival and no private battle. No client reports an
arrival expiry, stopped arrival or unsafe arrival failure. Final pilot HP is
354,732/400,000. Native spawn protection records 159,064 early protected-position
skips and 8,396 entity refusals without disabling Pokemon spawning.

Recording: `wbperf-fighters50-53135bff-c9d5-492f-b7df-8a8d7f9ae50d.json`.

| Phase | Ticks | p95 ms | p99 ms | Max tick ms | Max start interval ms | Gate |
|---|---:|---:|---:|---:|---:|---|
| Gathering | 1,342 | 90.1939 | 145.8866 | 208.0012 | 631.4465 | FAIL |
| Combat | 770 | 75.6282 | 113.3357 | 205.4554 | 205.9415 | FAIL |
| Recovery | 348 | 87.4397 | 113.3803 | 276.1528 | 276.6251 | FAIL |

All 8,251 raw ticks are retained. The watcher retains 131 diagnostic stacks
with zero drops; this is a complete FAIL, replacing no earlier evidence. Setup
retains 29 ticks over 250 ms, maximum 3,532.5296 ms and start interval 4,412.3792
ms. The campaign appends this trial as 32 and preserves all older failures.

Gathering's maximum falls below the v18 3,467.4692 ms maximum. This does **not**
establish overall improvement or production capacity: its p95 rises from the
prior 64.6645 ms to 90.1939 ms, combat tails worsen, and every fifty-player
phase gate fails. No thresholds, phases, population requirements or failure
classifications were relaxed.

Further samples identify actual remaining work rather than proving one complete
cause for each tick. Gathering includes native spawning habitat detection and
entity tracking/Netty wakeups. Two combat samples (ticks 7,681 and 7,791) spend
113/119 ms by sampling time inside synchronous console output from
`CobblemonCards`' `CardStatUtil.getPlayerDropBonus`, reached through the native
faint event. Recovery tick 7,984 samples native Pokemon spawn entity/MoLang
construction; an earlier recovery sample captures chained duel actor creation.
The 631.4465 ms gathering start gap ends at epoch 1795202930734, outside the
recorded tick body's 25.9968 ms. Critical commands and other work between tick
bodies still require investigation; the raw interval is kept failed.

After clients disconnect, the ordinary-PvP fixture
`ordinary-compat-1795203065934880600` passes all 12 checks: native PvP routing,
both real move exchanges, administrative native stop, unchanged boss HP and a
ready private simulator. This is not PvP victory/forfeit, six-Pokemon party,
economic-listener or graphical-client coverage. Cleanup records zero players,
zero pending/open/closing tickets, 66 acknowledged closed tickets and empty
input/output queues. The private simulator remains ready before graceful stop.
The server then stops cleanly; both private ports close. Its post-stop audit
verifies all 110 journal frames and the matching external recovery witness.

Production performance remains unapproved. Further work must address the
remaining p95/p99 workload and inter-tick critical commits without disabling
player physics, natural spawning, effects or recovery guarantees. Native
cancellation at each save boundary and native process-kill/restore testing
remain part of the broader release matrix. Nothing deployed.
