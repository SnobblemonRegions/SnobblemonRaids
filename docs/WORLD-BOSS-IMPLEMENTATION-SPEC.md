# World Boss implementation specification

Selected for final review — November 16, 2026. Authority: [experience design](WORLD-BOSS-EXPERIENCE-2026-11-16.md), preserving all owner-confirmed rules. The owner delegated remaining choices. This is a concrete implementation contract, not working code or test evidence.

## 1. Decision status and limits

All Q01-Q18 policy decisions now have a selected direction below. Actual deployed versions, arena geometry/coordinates, asset files, storage guarantees and load results remain facts to verify. A prerequisite failing is a release blocker, not permission to invent a replacement silently.

| Audit decision | Selected resolution | Evidence still required |
| --- | --- | --- |
| Q01 | One authoritative Minecraft server; event transitions and participant state mutate only on its server thread. | Enforced thread assertions and stale-event rejection. |
| Q02 | 30 typical, 50 occupied participant slots, FIFO overflow; 25 observer admission/render budget. Count pending starts and reserved breathers. | Burst, sustained and real-client tests at 50 fighters plus 25 observers; verify production player cap. |
| Q03 | Stable character per manually activated season; new character starts I. Natural victories advance it once. Historical records persist; archived cosmetic projects can progress later. | Migration and duplicate-outcome tests. |
| Q04 | Target the checkout's Minecraft 1.21.1, Cobblemon 1.8.2, Fabric Loader 0.17.2 and Java 21 baseline; server features plus authored resource pack, no mandatory new client mod. | Exact installed mod/client/pack versions and concrete capability tests; no blanket compatibility promise. |
| Q05 | One required, validated arena with a true 3D enrollment sphere, linked spawn/entrances, external observation terrace and safe return spawn. No random fallback. | Build, IDs, coordinates, permissions, hazard and chunk preflight. |
| Q06 | Noon/20:00 America/New_York; five-minute preparation; announcements T-10/T-5/T-1 minutes; one entrance cue T-15 seconds. Persistent occurrence IDs; no automatic missed-slot catch-up. | DST, clock-jump, restart and recurrence tests. |
| Q07 | Fifteen active minutes; pause for downtime/regroup. Recover up to 30 minutes single and cumulative outage, at most twice; two-minute regroup. At/after deadline rejects new winning actions. Otherwise staff hold. | Conservative outage measurement, forced-kill and exact ordering tests. |
| Q08 | At least five ready starting participants; N <= 50 fixes HP. Linear reference-DPM formula calibrated to eight combat minutes; no later pool growth or shrink. | Measured DPM and duration distribution. No guessed production HP coefficient. |
| Q09 | Automatic bubble enrollment, separate readiness/occupancy/qualification; 100-Merit starting model, approximately three-minute effort target. Locked qualification. | Real action attribution, duplicate rejection, final-hit and novice route tests. |
| Q10 | First/between-duel full recovery; 15-second chain cooldown; pause finishes current duel and persists for this event; queue-tail resume. No bag items, rentals or party replacement. | Pokemon identity/cleanup safety; held-item and party access verification. |
| Q11 | Initial 200 base Raid Points, 50 timed-failure, 200 qualified system-fault compensation; no placement cash bonus. +10% ordinary-raid blessing for 60 real minutes, extension horizon 120. | Actual economy compatibility and once-only settlement. |
| Q12 | Account-bound Worldheart; 12-step guarantee; provisional 2% current-character early unlock; styles at 24/40; one attuned supported Pokemon; archive project access. | Cosmetic composition/assets, ownership/trade/evolution and deterministic reward tests. |
| Q13 | Authored licensed English voice files, matching subtitles, volume/mute preference and reduced effects; text/vanilla fallback; optional client enhancements only. | Actual pack playback/preferences, timing and volume interaction on real clients. |
| Q14 | Core outcome, rewards, mastery, legacy, history and blessing are mandatory internal effects with durable replay. Existing listeners are optional notifications, not settlement authority. | Receipt/outbox recovery; optional consumer failures cannot block core completion. |
| Q15 | Extend current owned storage through a durable outcome journal plus atomic balance-and-receipt recipient settlement; no distributed infrastructure. Verified writer contract mandatory. | Storage spike, failed-write/forced-kill/restore evidence. Existing SavedData/AtomicFiles are not assumed sufficient. |
| Q16 | Operator level 2 baseline; audited stage/preflight/activate/inspect/hold/resume/cancel/retry. Tests isolate live economics. Staged rollout with coordinated backups and forward-compatible migrations. | Permissions, repair rehearsal, manifest and release evidence. |
| Q17 | Two 45-second nonoverlapping ward windows; keep active duel damage; suppress fresh admission during windows, no mandatory global drain or HP barriers. Combat can charge wards. | No deadlock, damage loss, retroactive battle modification or required overworld input during duels. |
| Q18 | Floor blessing bonus on base ordinary-raid points at committed victory; no recursive boosts. Balance-cap remainder remains durably owed, visible and claimable after space opens. | Arithmetic, spend-then-claim, snapshot and replay tests. |

## 2. Small implementation surface

Apply the codebase-design skill: concentrate rules behind a few testable interfaces; reuse existing owned encounters. Names here are design labels, not a mandate to create a class per row.

| Module | Interface responsibility | Internal concerns |
| --- | --- | --- |
| World Boss coordinator | Accept validated commands, presence facts, ticks and bound encounter events; expose immutable status. | Event transition reducer, slots, queue, intent, timing, ward/merit updates. |
| Durable event store | Commit/recover ordered event records and outcome; return explicit durable success or failure. | Versioned journal, checksums, snapshot/compaction, recovery and corruption quarantine. |
| Raid Points settlement | Apply a batch of keyed credits once and return credited/pending/overflow receipts. | Existing balances, durable receipts, checked arithmetic, serialized writes and ordinary shop compatibility. |
| Presentation | Observe immutable state and event-scoped cues; cancel/reset exact owned effects. | Per-viewer assets/audio/displays, subtitle transcript, preferences and budgets. |

Do not add generic distributed schedulers, interchangeable databases, a plugin framework or an event bus for rules that have one implementation. Injection of clock/storage/encounter adapters is justified for deterministic tests and real side effects. Keep direct Minecraft/Pokemon manipulation at the adapter seam.

Every event record carries world/server namespace, event UUID, schedule occurrence (or authorized replacement), character ID, season ID, awakening snapshot, schema/rules/asset/reward versions and monotonically increasing state generation. Display labels can change without changing these identities.

Every encounter binding includes event UUID, encounter UUID, player UUID and reservation generation. Every action receipt includes event and stable action identity. Every economic operation includes event/player/reward-kind. Never deduplicate only by player, name, species or awakening number.

## 3. Lifecycle and clocks

Selected combat lifecycle:

`PREPARING -> MUSTER -> COMBAT -> OUTCOME_COMMITTING -> OUTCOME_COMMITTED -> CLOSING -> CLOSED`

A ward window is a combat substate with its own serial index/deadline, not a second global battle lifecycle. RECOVERY_REGROUP and STAFF_HOLD suspend combat admission. QUARANTINED blocks mutation pending repair. PREPARING validates and reserves an occurrence before any showpiece or music is published.

Terminal reason is one of NATURAL_VICTORY, TIMED_ESCAPE, SYSTEM_CANCELLED, STAFF_CANCELLED or TEST_COMPLETION. Voluntary staff cancellation uses no automatic compensation unless explicitly classified as a service fault; record staff reason and provenance. An escape cannot overwrite a committed victory. A failed storage commit has not finalized a result, regardless of a client animation.

Schedule time uses zoned real time. Combat elapsed time uses monotonic real elapsed intervals while the server is operating, excluding only confirmed outage/regroup/hold intervals. A slow-but-running server does not quietly gain unlimited combat time. Persist remaining active milliseconds and lifecycle state; never compare unrelated dimension game times. Rituals and breathers count toward active time.

Heartbeat every two real seconds while an event is live; explicit durable stop marker on clean shutdown. For abrupt failure, conservatively measure interruption from the last durable heartbeat to validated recovery. This can overestimate downtime by a checkpoint interval; never understate it or invent precision. A backward wall-clock jump, contradictory timestamps or duration uncertainty crossing the 30-minute boundary puts the encounter on hold.

On recovery: restore state/HP/pause/qualified records, sweep only owned orphan entities, clear invalid active battle bindings and reconstruct eligible present players. Recreate no mid-turn state. Supply two minutes of regroup and recover only if five ready players are present when it ends; otherwise hold for staff. Preserve frozen HP, not recompute from the recovered roster.

Automatic recovery permits at most two interruption recoveries and 30 minutes total conservative offline duration. A third restart, longer cumulative duration, or a single outage above 30 minutes means hold. Staff may resume safely with a fresh two-minute regroup, cancel or repair; no unlogged timer/HP reset.

A held encounter reserves the single-event slot. Conflicting scheduled starts are skipped rather than stacked; notify players locally and staff operationally. Staff can replace an unplayed skipped occurrence explicitly, but one occurrence permits at most one live rewarded result. Rehearsals otherwise remain dry-run. No unlimited manual minting path.

Schedule occurrence key: season ID + configured schedule revision + local date + local clock slot + zone. Store resolved instant/offset too. Generate each intended local slot once through zone rules, never infer identity solely from observed UTC hour. Noon/20:00 avoid typical clock-transition hours but still test arbitrary future schedule configuration. A schedule edit applies only to future unreserved occurrences.

If a server boot/recovery misses any part of the five-minute preparation for a new event, skip it. A running server may start on its first tick at/after the combat instant within ten seconds; outside that tolerance skip as scheduler fault. Do not fire a backlog of announcements at boot. Old entrance/preparation cues never restart globally for a late viewer.

At a gameplay callback: validate event/binding, sample the active deadline, reject at/after zero, apply valid damage, award Merit, lock qualification if earned, then adjudicate zero HP. Commit that combined outcome evidence. Timer-tick/command callbacks use the same ordered coordinator. This resolves the final-hit/qualification race without waiting for onEnded.

## 4. Arena, participants and queue

The enrollment sphere and its presentation share one geometry definition. Use a 3D distance test in the correct dimension, radius stored in the event snapshot, and epsilon defined by numeric tests. No infinite cylinder that enrolls players far above/below the arena. Inward join boundary uses radius R; existing members retain presence until R+1 block to avoid edge flicker. The one-block grace does not admit a new outside player.

Preflight verifies linked arena markers, safe floors/clearance, world border/build height, loaded bounded chunks, battle placement spacing, allowed teleport routes, observation terrace, safe spawn and protected build. Fail closed when the required arena is missing. Use only event-owned chunk tickets, release them exactly once, and never synchronously generate distant random terrain.

Prevent new unowned Cobblemon SpawnAction entities from spawning into the protected arena without disabling Pokemon spawning across the server or deleting existing Pokemon. Preserve party send-outs and event-owned boss creation. Existing occupants and entities entering from outside still require safe placement checks and arena preparation; the spawn guard is not a substitute for these checks.

Bubble membership is not qualification. Ready players satisfy the owner-approved rule; gameplay spectators, paused, busy or empty-party players remain unready. Fainted party members do not disqualify. At start, take the first up to 50 ready players by stable arrival sequence/UUID tie-break; require at least five and freeze N.

At most 50 slots can be RESERVED, STARTING, DUELLING or BREATHER across the event. An admitted participant in a breather/ritual can perform scored ward work using that same slot; paused, queued or observer players cannot earn new ward units/Merit until admitted again. Preserve their earlier earned records. No separate uncounted support capacity. Queue membership does not consume one. Observer render/admission budget is 25; failed observer warp gives a clear capacity message and retains the origin. This budget is not permission to ignore other players in network/load testing. Validate that world protection and external teleports cannot bypass the dedicated area's admission controls.

Ready queue ordering is FIFO by coordinator-issued sequence. Multiple packets in a tick have a deterministic sequence, not wall-clock timestamp ties. Entering busy/empty does not reserve priority: join the ready tail when actually available. If a waiter becomes unavailable, remove it from the ready queue and retain enrollment; rejoin at tail on readiness. Refresh UI with a rank from the authoritative queue; never accept a client rank or repeatedly sort the entire queue every tick.

Reserve a slot before calling Encounter.start, revalidate readiness/phase, and bind the returned encounter. Refusal releases only the matching reservation. Don't increase HP, count a duel as completed, apply score or clear an earned record. Reentrant callbacks must see an initialized STARTING binding or be safely deferred until commit. Stale callbacks cannot remove a newer slot/binding.

Pace native duel admission attempts to at most one per 250 ms, with no accumulated catch-up burst after a delayed tick. This replaces the initial two-per-tick budget after native load recordings exposed costly starts. At the ideal rate, fifty attempts span about 12.25 seconds from first to last; slower ticks, placement refusals and failed starts can extend that wave. Include this staging in the active clock and show each waiting player's FIFO position. Retain the fifty-slot cap and reserved breathers; do not raise capacity to hide queue delay. Production calibration must use the current pacing policy and a passing native performance gate.

Normal breathers retain a participant's slot; otherwise a duel-end burst could evict all existing participants every round. Pausing during a breather releases immediately. Pausing during a duel sets intent first, lets cleanup finish, then releases. Resume removes pause and joins ready tail if available. It cannot shorten the existing 15 seconds since prior completion; queue/ritual time can satisfy that cooldown.

Leaving the bubble suppresses the next duel and relinquishes queue priority; active duels finish normally. An explicitly paused player remains paused through re-entry. A nonpaused returning player joins the ready tail. Disconnect retains an active duel for up to 30 seconds under a verified grace adapter, with no new user choices simulated; then finalize/abort safely and release. A disconnected breather/queue member releases immediately. Reconnect preserves progress and pause but receives no old queue position. Duplicate leave/end/timeout operations are idempotent.

Server recovery restores queue order from the checkpoint among present, ready, unpaused players; absent/unready entries are removed. No stale reservation survives as a running battle. Uncommitted presence/queue changes may be lost up to a checkpoint interval; earned qualified/reward acknowledgments require stronger durability.

Close new participant admission when 60 active seconds remain. Players already successfully admitted at least once may finish, resume or re-enter under normal queue/cooldown rules until outcome. Mere prior enrollment or queue membership does not bypass the newcomer cutoff. A queued newcomer not admitted before cutoff is told no new qualification opportunity is promised, and becomes an observer. The event panel distinguishes unavailable, queued, late-closed and explicitly paused.

## 5. Duels, healing and phases

Use existing EncounterService ownership and RaidSession machinery. Selected provisional duel limit: 90 seconds; typical target 45-75 seconds. Use a fixed level-70 boss baseline initially, not automatic rewriting of player Pokemon levels. Validate new players' ward route separately; no claim all low-level teams can beat it.

Only positive applied shared damage from a valid bound event encounter counts as that player's damage. Residual/unattributed damage has explicit adapter tests; do not assign boss self-damage to an arbitrary trainer. Test OHKO/percentage/destiny/perish/multi-hit/status/drain/reflection and ascension modifiers against actual raid patches. Supported restrictions are shown before combat, not silently applied as a blanket move ban.

Before first successful admission: revalidate party IDs/current storage and battle availability, perform allowed HP/PP/status/faint recovery through Cobblemon's supported mechanism and start the owned duel. If admission fails after a heal, log the operation as failed-start healing, don't restore an entire stale party snapshot, and throttle retry; a repeatedly failing adapter quarantines admission so it cannot become a free general healing endpoint.

After a bound duel is fully cleaned up: recover only still-owned/currently present admitted Pokemon identities, once per completed encounter; then begin its 15-second breather or release if paused/outside/disconnected. Never restore traded/deleted Pokemon, overwrite a new party or persist a transient clone as the player's real Pokemon. Allow party editing only outside an active/starting duel; block trade/PC mutations of battle-bound Pokemon through verified existing battle restrictions.

Recovery fields: current HP, PP, ordinary temporary status and faint state. Do not reset EVs/IVs/level/friendship, change held items or revert legitimate external progression. Ban trainer bag-item use in boss duels; held-item consumption follows verified raid behavior and is displayed. If a supported mod introduces permanent penalties, release must either exclude that mechanic in this event or provide a verified exact compensation; no assumption that full heal undoes everything.

Health formula: H = round(N * D_ref * 8), where D_ref is measured legitimate shared damage per active combat minute for the selected fixed boss/duel rules, inclusive of normal breathers but exclusive of ward pauses. Store all formula inputs and resulting H. Use checked bounded integer/fixed-point units with one conversion from the encounter's damage representation; reconcile accumulated fractional remainders. No arbitrary production D_ref default. Bounds must be derived from the tested five-to-50-player range; reject invalid configs rather than silently clamp into a different difficulty.

Calibrate for roughly 10-12 active minutes including two ward windows, leaving margin under 15. Sample representative novice/midgame/veteran compositions, not one perfect counter bot. Significant late arrivals can accelerate a fixed pool; don't undo their help. Monitor coordinated delayed-entry farming and calibrate rewards/mechanics without breaking the approved fixed-health rule.

Ward windows at 70/35% last 45 seconds, never overlap, suspend fresh duel admission and don't stop existing shared damage. Queue a crossed second window; zero HP wins immediately and cancels remaining optional cues. Preserve each window's serial, goal, deadline, contributors, consumed action tokens and result across recovery. If fewer players can act than the frozen goal, the window still times out; no infinite resize/wait cycle.

Require a current participant-slot binding for every scored ward action. Overworld work is permitted only outside an active/starting duel and within the ward window; the binding must still exist at completion. Relinquishing a slot cancels unfinished actions without deleting already-accepted credit. Combat-to-ward charge is based on server-validated meaningful turns, not client claims of move type. A relay gets an event/window-bound noninventory token at a fixed cache, traverses the prescribed short route and channels six seconds at a linked pylon. A focus is a separate six-second action at that pylon. Cancel invalid range/dimension/death/teleport interactions. Each completion is idempotent, bounded per window and cannot be repeated by offhand packets.

Choose q = clamp(ceil(N_ready / 10), 1, 5), target 3q ward units and q distinct contributors, frozen at opening. Combat turn = 1 unit, relay = 3, focus = 1; per-person cap 3 ward units. Merit is separate: relay 30 and focus 20 once each/window. Two windows can qualify a low-level participant. If no active group needs the relay, other admitted slot holders can still earn their legitimate per-window Merit; overscoring cannot add boss damage or bonus money.

Success removes the next act's temporary modifier. Failure initially permits at most a tested 10% boss offense adjustment for 90 active seconds on new duels only; this value is provisional and may be lowered. Do not patch a running Showdown simulator or create a new permanent client power mechanic to support the presentation.

## 6. Merit, outcome and delivery

The 50-slot limit is concurrent, not a cap on total unique qualifiers. Settlement iterates the immutable recipient list, not the current live roster; economy reporting uses unique qualifiers over the whole occurrence. A distinct authoritative action receipt awards at most ten combat Merit/turn and 50/duel. Positive applied shared damage is eligible. Instrument useful support effect categories explicitly, with at most one award per category/duel and no same-turn double credit. No guessed support score from move names alone. If a category cannot be verified, it stays disabled; damage and validated ward routes remain sufficient.

Qualification at 100 is monotonic and event-scoped. Apply action/receipt/Merit/qualification before final damage outcome in one reducer step. No requirement for two completed duels and no moving average. This supersedes the original provisional completion-count formula and resolves losing/killing during an unfinished qualifying duel.

Persist qualification before telling the player it is secured. On victory, snapshot recipients and outcome from accepted evidence, including the final valid action; no post-outcome damage can add qualification. Existing qualified players are paid offline, paused or outside. Late players see current progress and the admission cutoff.

Natural victory: 200 initial base Raid Points plus current mastery/possible early unlock and one appropriate legacy project step, no placement multiplier. Timed escape: 50 points for already-qualified players, no victory progress/blessing/advancement. Service cancellation after staff resolve a hold: 200 compensation to already-qualified players; not a fictional win. Staff/test replay cannot grant more than the same keyed entitlement.

The outcome freezes reason/time, character/awakening, rules/reward versions, eligible UUIDs, amounts, milestone/roll results, blessing bounds and history evidence. Generate any random early unlock once at outcome using a stable saved seed/result; retry or claim does not reroll it. Commit outcome before its effects or defeat scene.

Settlement design: a durable outcome outbox owns entitlement IDs. Recipient balance updates must store the deduplication receipt in the **same atomic durable record** as the credited balance. A read-check-award-write sequence across independent stores is forbidden. Batch the event's recipient credits when the existing storage shape supports it, avoiding a whole-record write for every recipient. Normal awards/spends, shop reads and retries must share a serialized mutation path so an async save cannot overwrite a newer balance.

The selected direction is to extend the existing Raid Points record authority, not introduce a second balance. Add a receipt-aware operation such as creditOnceBatch, preserving existing public award/spend behavior. A failed batch returns pending with no false durable acknowledgment. Partial writes, balance caps and unknown prior acknowledgment are recoverable from authoritative receipt state. History/mastery/blessings are separately idempotent projections of the immutable outcome and retain per-effect receipts.

No file writer is accepted by assertion alone. The inspected AtomicFiles uses a temp/rename with a non-atomic fallback and no explicit durable force; SavedDataFlush delegates behavior and does not provide a multi-store transaction. Implement a versioned, checksummed, length-delimited outcome journal with acknowledged durable commit and atomic verified balance/receipt replacement. Storage must return actual failure, preserve prior committed generations and detect truncated tails; journal replay cannot accept a partial outcome. Do not use an unchecked rename fallback for economic commits.

Use an ordered persistence adapter with immutable snapshots; off-thread I/O may not touch Minecraft/Pokemon state. Publish secured outcomes/balances only after acknowledged commit and return callbacks to the server thread with generation checks. Concurrent ordinary economy/stat updates cannot be lost by stale whole-record snapshots. The persistence spike must demonstrate that this extension is safe on the deployed filesystem; otherwise this selected storage direction needs explicit engineering revision before implementation continues, not weakened guarantees.

Checkpoint in-progress damage and time every two real seconds or earlier when qualification/intent needs acknowledgment. Crash loss of at most one unacknowledged checkpoint interval is the selected target, subject to measured I/O. A paused/saving control must not falsely claim durable success. Committed rewards and acknowledged qualification use stronger guarantees. No blanket claim of physical-disk power-loss safety is made until storage/device behavior is verified.

Cap overflow: credit the amount that fits and atomically retain the rest in the entitlement. Claim remainder after balance space becomes available, once. Floor ordinary-raid blessing bonus at committed victory; don't round each intermediate step or change a frozen earned bonus when a later claim occurs.

Back up event journal, balance/receipts, Pokemon/player inventories where relevant, mastery/history, origins and active asset/rule snapshots as one documented recovery set. If only some stores are restored, quarantine and reconcile instead of auto-paying guessed history. Preserve an external backup manifest/generation; restoring every store to an old point may erase legitimate later activity and must be disclosed in the operator runbook.

## 7. Content, assets, history and safe controls

Preflight resolves immutable character, raid and asset versions. A missing essential raid definition blocks launch; a missing optional voice/model file uses fallback presentation but is reported for quality signoff. Keep every version referenced by unsettled outcomes/legacy projects; do not drop a claim because its current definition was removed.

Worldheart is an authoritative account entitlement with attunement to Pokemon UUID. A display item, if offered, only opens the collection and does not carry ownership. Detach on trade, ownership change, evolution into an unsupported appearance or missing asset; preserve the Pokemon's native data and cosmetic entitlement. One active attunement/account initially. No duplicate physical claim/redemption race.

Base at 12 current-character qualified wins; 2% early unlock on eligible natural current-character wins until owned; styles 24/40. Record roll outcomes and progress together with keyed grants. One chosen archived project receives one step per future qualifying win as well; no archived rare roll. Switching legacy projects preserves progress, can't apply one event twice to different projects, and does not rewrite that event's frozen selected project.

Compact immutable natural-clear history persists permanently. Full action/diagnostic logs rotate after 30 days by default; never prune unsettled entitlements, required deduplication records or receipt evidence solely to meet retention. Archive/compact settled records only with a verified watermark that stale jobs cannot cross. Paginate roster/collection queries and bound transcript lengths.

Voice and event music track exact asset IDs/channels/instance ownership. Use pack assets and per-viewer preference, no forced camera. A client with no independent custom slider can use event-menu voice volume/mute sent by the server; test the actual category/packet semantics before claiming independent ducking. Reduced mode reduces transmitted displays/particles, not only local opacity.

Scene cues carry event/generation/variant and viewer-session ID. Stop/remove old IDs before replacing geometry. A reconnect with the same UUID is a new viewer session. Cancel old phase tasks on outcome and season switch. Playback is best effort; no exactly-once client audio promise. Replay is local/presentation-only and cannot mutate journal or economy.

Persist warp origin before admitting the teleport, but acknowledge successful arrival separately. Failed/cancelled warp keeps a valid origin. Repeated arena entry does not replace the original return with an arena coordinate. Return validates dimension/safety/protection and uses a preflighted safe spawn if invalid; consume the origin only after success. Never substitute overworld with a missing dimension's coordinates.

Stage/activate is two steps. Activation requires valid content, no active/held event, no pending outcome commit and healthy settlement machinery. Durable already-owed deliveries may continue across a new season because their contents are snapshots; a failed mandatory settlement system blocks starting new rewarded events. Admin dry-runs get isolated namespace and never broadcast a false live awakening.

Permission level 2 on every administrative mutating branch, including replacement/test/retry and any connected control path. Player Pause/Resume/Return controls validate self-ownership, not operator privileges. Optional consumers receive immutable outcome payloads; reentrant external requests are queued after the current transition. Core modules do not rely on optional listeners to maintain correctness. No external network I/O in tick/settlement callbacks.

## 8. Implementation milestones and proof

1. **Storage and event isolation spike.** Reproduce F01-F05 and payout acknowledgment issues, prove receipt-aware credit durability with ordinary shop writes, then land scoped fixes. Stop if selected storage cannot meet its contract.
2. **Pure coordinator.** Event/participant state reducer, queue/cap/pause, clocks/occurrences, HP snapshot, Merit and outcome ordering. Property tests over random operation sequences and all 18 PQ scenarios.
3. **Owned-duel adapter.** Bind/reconcile starts/ends/damage, health/PP/status recovery, party identity and bag-item rules. No stale event mutations or unrelated cleanup.
4. **Arena/player flow.** Preflight, true sphere, warp/origins/return, ready admission wave, queue/paused UI, deadline/recovery and operator status.
5. **Vaelrath content.** Two nonblocking ward windows, authored scenes, exact legacy advancement and accessible novice path. Real clients first; bot movement alone is not proof.
6. **Rewards and collection.** Offline-safe equal base/consolation/compensation, blessing, guaranteed/early Worldheart unlocks, archive attunement, trade/evolution safety.
7. **Chaos/load and invited review.** Full exact-modpack tests, 50+25 load, repeated events/reloads/reconnects, crash/write-failure/backup restore, staff repair rehearsal. No public release until the chosen full experience passes.

Acceptance evidence must include: versions and asset hashes; measured tick p95 < 50 ms at full target load, p99 < 100 ms and no event-caused tick stall above 250 ms as initial gates; server heap/GC and client FPS/freeze measurements on a declared test machine; admission/queue delays; real qualification distribution; 5/10/30/50-player clear time and composition; journal/receipt recovery traces; and ordinary raid/shop regressions. If the baseline already exceeds a threshold, diagnose and revise capacity/performance scope explicitly rather than relabel a failing test.

Test cases must include the final damaging action granting qualification; two concurrent threshold crossings; end callback after event B; duplicate healing/release; paused reconnect; pending start cancellation; ritual during pending promotion; 50 simultaneous completions; full balance/inventory/offline reward; config deletion/reload; power/process loss at every outcome/receipt boundary; unsupported pack; partial backup restore; natural vs admin legacy; and failed observer/return teleports.

Fail a feature closed when ownership, storage or world safety cannot be established. Keep the rest of the server running, preserve diagnostic state, alert staff and show an honest player status. No silent reward loss, synthetic victory, retry storm or reset-everything recovery.

Release package: state/transition tests, fixed-source regression tests, compatibility/asset manifests, measured report, migration/rollback plan, operator runbook and this decision specification. These are required outputs of implementation; they have not been produced or passed merely by writing this document.

## 9. Inputs still to verify, without new preference questions

- Arena/world identity, center/radius, entrances/observation terrace and safe spawn; real chunk/protection setup.
- Deployed mod/client/proxy/resource-pack manifest and maximum connected-player configuration.
- Existing shop prices, economy budgets and ordinary-raid reward hook for the blessing.
- Pokemon/asset support, recorded voice files, licensing and subtitle/control feasibility.
- Measured D_ref, numerical HP bounds, qualification values, ward/offense values and actual performance.
- Durable write/rename/force/error behavior on the host filesystem; backup access and restore rehearsal.

Do not fabricate these facts or close a source audit finding without evidence. The owner can review all selected policies together now; implementation readiness follows the storage/compatibility spikes, not a promise that no bugs exist.
