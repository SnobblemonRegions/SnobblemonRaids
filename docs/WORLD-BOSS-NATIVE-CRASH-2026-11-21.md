# Native process-crash and arena restoration

This continuation exercises a disposable, loopback-only Minecraft server. It
does not activate a live season or establish power-loss, economic-payout or
production approval. The earlier performance evidence remains attached to its
exact gameplay/probe hashes.

## Reproduced failures

The first rig, `worldboss-rehearsal-db4b5dd3656328eb`, ran five real battles,
saved marked inventory stacks and party identities, and was terminated through
a verified Windows process handle while five duels were ACTIVE. Its post-crash
journal/head and original projection were retained under
`native-crash-1795306416246659300`.

The restart harness initially queried the image path of an exited process whose
Windows process record still existed. Windows returned error 31. A real owned
child-process test reproduced that failure before the fix. Restart now checks
the stable handle's exit state first and handles exit during the identity query;
live process identities still require exact executable and creation FILETIME.
Termination uses the same verified handle, not a later PID-only lookup. The
failed attempt is retained separately.

The resumed native test recovered exact boss HP, contribution units, frozen
roster/rules, remaining combat budget, recovery count and travel origins without
the rebuildable projection. It then caught a gameplay bug: startup called the
preloaded-chunk lease before the arena had become FULL. The exception was
`Arena chunk -9, -9 must already be fully loaded`. Combat correctly held with
PRESENTATION_FAILURE, but no standing boss was recreated. That failed native
report and its snapshots remain evidence; it is not a passing recovery trial.

## Fix

Startup arena restoration is now a bounded Main-thread continuation. Already
loaded arenas can complete immediately. Otherwise, native asynchronous reads
verify saved FULL chunk data for the complete bounded dependency footprint
before recovery tickets may initiate loading. Missing/incomplete chunk data
fails closed; the controller does not request new terrain generation.

At most four verification reads are outstanding, at most two start per tick,
and at most two recovery tickets are added per tick. Temporary tickets renew
while needed and release on completion, cancellation, failure or shutdown.
The existing permanent lease is acquired before temporary tickets release.
Restoration times out after 120 seconds. Unexpected presentation failure holds
the original event without mutating a replacement event.

The continuation rechecks event ownership around native hooks. Arrivals and
admission wait for restoration, and the regroup clock starts after the arena
and standing boss are ready. No bulk synchronous chunk generation/load was
added. Combat rules and the persisted schema are unchanged; existing frozen
rule fingerprints are not invalidated by this startup repair.

Six new tests cover cold readiness, bounded reads, missing saved chunks,
timeout/cancellation, partial ticket side effects, cleanup retries and owner
replacement during readiness checks. The full root/client suite passes 1,127
tests. Thirteen runner safety tests include real Windows-handle termination;
five journal-evidence tests cover framing, partial tails and corruption.

## Native revalidation

The fixed gameplay SHA-256 is
`7332065b7f78e035fe6327670558d6bb7c93a6f05f54281ac2e5bdd7e2699601`.
The diagnostic probe SHA-256 is
`8256bae24bb76b926a5b4c91335392ea3a5dd329862f0e2f4bb5ed66e2ead9f5`.
Revalidation is in progress in `worldboss-rehearsal-ce0b7238fee903c3`.

This stronger case removes vanilla force-load tickets before saving/crashing,
omits only the preserved rebuildable projection, and requires journal replay,
one standing boss, no old duels, original party identities, exact marked-stack
counts and five resumed native battles with new encounter IDs and move replies.
Recovery-only manifests and restarted processes are refused for capacity
recording. Production release gates and interrupted-payout boundaries remain open.

The strengthened native rehearsal **passed all 28 checks**, with evidence in
`native-crash-1795308078358769300`. Both launch generations use the fixed gameplay
and diagnostic probe hashes above. The original PID 7116 was forcibly terminated;
the replacement PID was 30096. No graceful server-stop hooks ran at the crash.

The last complete captured journal generation was 118, with 277,734 valid bytes
and no partial tail in this particular run. After startup replay without the
projection or persistent force-load tickets, fixed health remained exactly
38,104,000 / 40,000,000 units. The active budget remained exactly 850,847 ms,
and the automatic recovery count advanced once. Frozen roster/rules, contribution
units, recovery installation and original travel locations matched the retained
authority. There were zero old owned duel entities and exactly one standing boss.

All six party UUID/species/level records and all six marked seven-diamond stacks
survived without duplication. Five new native encounters then opened after the
full regroup, all five clients submitted native move responses, and all encounter
IDs differed from the pre-crash duels. Staff hold acknowledged afterward. The
replacement process stopped cleanly; all 184 final journal frames matched the
external head in the post-stop audit.

This closes the basic saved-baseline process-crash/missing-projection/arena
restoration case only. This run did not land in a partial journal tail or
interrupt a live payout, native party save, inventory write or external points
replacement. Those boundaries, power loss, live reward throughput and broader
client/full-party coverage remain separate release work. Fresh capacity
revalidation uses separate unmarked processes; none of this recovery-only
evidence is counted as a performance pass.

## Fixed-build capacity revalidation

Fresh rig `8da47e5752efe317`, using the fixed gameplay and probe hashes above,
passes all 24 functional checks with 50 fighters and one observer, including
native battles/move replies, natural-spawn guards and an explicit native
automatic save batch. Campaign trial 46 retains its raw recording and unchanged
gate results. No runtime prototype, JS trace, recovery inspector or JFR recording
was enabled in the measured process.

| Phase | Ticks | p95 ms | p99 ms | Maximum ms | Maximum start interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gathering | 1,356 | 48.2199 | 74.5628 | 163.5210 | 164.3563 |
| Combat | 830 | 44.5223 | 57.7153 | 82.8967 | 194.1624 |
| Recovery | 410 | 26.9203 | 30.5152 | 50.9054 | 59.9593 |

These are initialized event-phase limits, not all-server latency approval.
The complete recording also includes fixture setup and reached 3,500.747 ms
outside those declared phases. Tick 4825's three bounded samples (117.9885,
320.4093 and 523.3203 ms) show Main in native compressed player-file writing,
through `WorldBossPlayerAutosaves.write`'s synchronous path and the automatic
native player-save batch, before an active boss event. The samples do not trace
the remaining three seconds. Separate setup ticks 2969 (1,237.5394 ms) and 4449
(1,221.0157 ms) sample SQLite commit through `ProgressionStore.acquire`,
`ProfileService.acquire` and `AscendWiring.finishAcquisitions`. These establish
observed blocking paths, not a full allocation of each tick's duration. Automatic
player saves outside active encounters and concurrent native SDK acquisition
remain separate operating risks; event-phase approval does not close either.
Each measured phase retains all 51 clients, no threshold is relaxed, and raw
recordings/failures from earlier trials remain in the campaign.

After timing, all 19 native compatibility checks and nine standing-boss reentry
checks passed on the same gameplay artifact. The process then stopped cleanly;
all 133 final journal frames match the external head (905,552 bytes).
Fresh rig `2f19a18b8f09b23c` also passes all 24 functional checks with five fighters
and one observer, using the same artifact hashes and fixture requirements.
Campaign trial 47 retains its recording and unchanged gates:

| Phase | Ticks | p95 ms | p99 ms | Maximum ms | Maximum start interval ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Gathering | 1,067 | 18.7970 | 35.0320 | 155.7713 | 156.0090 |
| Combat | 773 | 17.6127 | 26.0990 | 56.6897 | 128.7165 |
| Recovery | 336 | 15.6860 | 20.2589 | 33.9469 | 56.6886 |

The five-fighter process stopped cleanly; its 65 final journal frames match the
external head (147,698 bytes). The complete recording includes setup and reaches
276.9509 ms body / 2,797.0476 ms start interval outside the declared event phases;
neither is removed from the raw evidence. Both performance rigs and all crash
rehearsal generations are stopped. Capacity remains scoped to initialized
one-Pokemon parties, this host/modpack and TEST encounters. Full parties, live
reward throughput, graphical clients and concurrent acquisition/save pressure
outside active events remain unapproved. Nothing was deployed.
