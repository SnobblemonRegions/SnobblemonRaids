# World Boss engineering readiness and failure analysis

Audit date: November 16, 2026. Companion to [World Boss Experience](WORLD-BOSS-EXPERIENCE-2026-11-16.md).

**Status: design audit, not implementation approval.** No runtime fixes were made, no test suite was executed, and no production incident was reproduced in this audit. Source-confirmed behavior, predicted consequences, unimplemented requirements and unresolved decisions are distinguished below. This register is a starting baseline for implementation and must evolve when requirements or evidence change. No finite review establishes the absence of all defects.

## Current policy status after delegated design completion

The owner authorized the agent to select the remaining policies for review together. The current [experience design](WORLD-BOSS-EXPERIENCE-2026-11-16.md) separates owner-confirmed rules from selected choices, and the [implementation specification](WORLD-BOSS-IMPLEMENTATION-SPEC.md) resolves Q01-Q18 with concrete contracts and verification prerequisites. No further preference questionnaire is required for this design package.

The audit below preserves source-era findings and failure hypotheses. No finding is closed simply because a policy was selected. Earlier references to undecided participation counts, mandatory global drains, hard phase-HP barriers, completion-count qualification or unselected cosmetic ownership are historical context; the current documents supersede them. Remaining unknown deployed versions, coordinates/assets, storage behavior, economy values and performance are factual verification inputs.

## 1. Scope, evidence and uncertainty

Inspected the world boss service, pool, scaling, rules, configuration, persisted state, commands, arena markers/placement, music/dome presentation, encounter callbacks, damage instructions, Raid Points award/persistence paths, pending rewards, startup/shutdown registration, existing world boss tests and ADRs. The checkout was at `L:\SnobblemonRaids`; the supplied C: project path was absent.

Source baseline declares Minecraft 1.21.1, Cobblemon 1.8.2, Fabric Loader 0.17.2 and Java 21 in `build.gradle`. These are repository declarations, not verification of the deployed server or clients. No compatibility with other versions or mod combinations is established.

Short source labels in findings, relative to `src/main/java/com/cobbleraids/`:

- WB = `worldboss/WorldBossService.java`.
- State, Pool, Rules, Scaling, Config, Music, Dome = corresponding `worldboss/WorldBoss*.java` files.
- Records = `catching/RaidPlayerRecords.java`.
- Encounter = `encounter/EncounterService.java`.
- Pending = `reward/PendingRewardStore.java`.

Line references describe this inspected baseline. Key SHA-256 hashes:

| File | SHA-256 |
| --- | --- |
| WB | `5998CF79F4E50AFA5ADA57082715A758A446BD2D8B3364EC21F3E73D813FD797` |
| State | `7EFD4A3487976DA95FD0D151F1636ED761DC91BA9926699D7508948951969B0B` |
| Pool | `E37F0A9878AE478A63E584761C16B4B23ADF45DC2987DB5623B70D8A0B08BEE9` |
| Records | `9BE385414EEDCD79212B703C886A529E36FE2A7024F3710A9C7825DE6E00285F` |

Confidence labels:

- **S — source-confirmed:** inspected code establishes the stated behavior. Predicted incidents still need executable reproduction.
- **D — design gap:** future feature lacks a complete rule or implementation contract.
- **V — verification gap:** environment/runtime behavior has not been demonstrated.
- **Q — decision required:** product or operating policy is unknown; recommendations are not accepted decisions.

Priority: **P0** blocks public release because it can lose/duplicate earned value, corrupt legacy, affect another event or damage unrelated gameplay. **P1** blocks the affected feature because it can strand, mislead, exclude or stall players. **P2** concerns quality, fairness, operability or maintainability. Classification is proposed engineering triage, not evidence of incident frequency.

Owner-confirmed operating rules and subsequently delegated selections are now consolidated in the experience design and implementation specification. Numerical tuning remains provisional until measured. Worldheart ownership, failure rewards, blessing scope and qualification now have selected policies for review; their implementation and assets are not verified facts.

## 2. Source-confirmed weaknesses and concrete reproductions to add

### F01 — P0 / S: payout completion is recorded before individual awards

WB:654-675 sets and flushes `rewardsPaid` before iterating players. If the process exits after that flush but before all awards, recovery skips the whole batch. Add a hard-kill test before the first award and after each award. Required contract: durable, replayable per-recipient entitlements and an idempotent settlement operation; a single batch flag is insufficient.

### F02 — P0 / S: the balance has a different durability boundary

`CobbleRaidsPoints.award -> RaidPointsStore.award -> Records.addPoints` (Records:142-146) updates LIVE and marks SavedData dirty through `persist`; it does not immediately flush. Even after every award returns, a crash before player-record autosave can retain the boss's paid flag but lose credits. An extra flush alone does not make two stores atomic. Persist balance mutation and its deduplication receipt together, or select a transactional settlement design before implementation.

### F03 — P0 / S: save failure does not prevent later irreversible effects

WB:715-723 logs IOException and returns no failure result; callers continue spawning, paying and notifying. `SavedDataFlush` delegates actual writes to Minecraft SavedData. The audit has not established that delegate's atomic replacement, failure signaling or power-loss durability. Simulate read-only data directories, disk-full, interrupted writes and underlying swallowed write errors. Do not advertise secured rewards until the chosen persistence layer confirms the defined durability boundary. A saving fault needs an explicit recoverable/quarantined state.

### F04 — P0 / S: damage callback discards encounter identity

WB:509-525 receives `encounterId` but forwards only player/amount to the current global state. WB:687-692 ends the event without aborting all active duels; WB:318-319 clears indexes at the next start. Construct: A is defeated while another A duel remains active; B starts; an A hit arrives. This service has no event-membership guard preventing it damaging B. Require an immutable binding of encounter ID, event ID, participant and lifecycle generation for every callback; reject stale or unknown bindings.

### F05 — P0 / S: cleanup identifies victims by proximity, not ownership

WB:792-809 discards any raid-marked Pokemon inside a six-block horizontal box spanning the world's height. An unrelated raid in that volume matches. The comment that ordinary raids are safe is stronger than the predicate. Require event-specific ownership tags and recorded entity IDs. Test an ordinary raid above, below and beside the showpiece. Legacy untagged cleanup must be a separate narrowly controlled operator repair, not normal event cleanup.

### F06 — P1 / S: leave does not cancel gathering or future chaining from an active duel

WB:425-426 removes only CHAIN. A player in GATHERED remains queued, and an active duel's later WB:533-543 callback can enqueue them again. Add gather-join/leave/open and active-duel/leave/end scenarios. Store persistent-per-event opt-out intent, remove queued admission, and consult it before every automatic re-entry. Explain whether leave cancels the current duel or only future admission; do not silently choose.

### F07 — P1 / S: manual join bypasses the breather

WB:393 removes CHAIN and WB:400 immediately starts a duel; no cooldown gate is consulted. A player can issue join as soon as a duel ends instead of waiting. If breathers are balancing rules, enforce them for all entry routes with one admission policy. If they are only automatic-chain convenience, document that explicitly and tune throughput accordingly.

### F08 — P1 / S: failed starts can still increase the event pool

WB:443-450 counts the player and grows HP before position resolution and Encounter.start success at 453-467. Test a refused encounter and an unusable duel location. Apply attendance/scaling changes only after admission commits, or remove live growth under a decided fixed-pool design.

### F09 — P1 / S: an enabled arena can still use random terrain

WB:292-308 treats no markers as open-world placement. `pickArena` checks dimension existence, not a validated designated build. Add explicit arena-required preflight and refuse that scheduled start without consuming the schedule slot or advancing legacy. Preserve any deliberate random-world mode as a separately selected behavior.

### F10 — P1 / S: entrances are dimension-wide and unvalidated

WB:950-959 selects any entrance in the dimension, without checking its arena association, distance, fluid, headroom or current safety. Two builds in one dimension can send players to the wrong one. Arena IDs and bounded linked entrances are needed. Revalidate destination at teleport time, including collision, hazards, border, build height and loaded chunks; do not trust marker presence alone.

### F11 — P1 / S: return fallback can reuse coordinates from a missing dimension

WB:968 removes the origin before teleport. WB:975-977 substitutes the overworld when the original level is missing but reuses its coordinates. No current destination safety check appears on this path. Test removed dimensions, changed terrain and failed/cancelled teleports. Preserve a pending return until arrival succeeds; use a verified safe spawn when the original destination cannot be used.

### F12 — P1 / S: return/queue statics have incomplete lifecycle cleanup

RETURN_TO is memory-only and lost on restart. WB:onServerStopped clears neither RETURN_TO nor GATHERED. Integrated-server world switching in the same JVM can retain stale return coordinates or queue entries. Persist origins scoped by server/world identity; clear transient maps symmetrically. Test disconnect/rejoin, multiple warps, restart and world switch.

### F13 — P1 / S: active rules and rewards are read from mutable current config

WB:386, 546, 649 and other paths use WorldBossConfigManager.get during the event. Changing reward amount, qualification, duel duration, radius or chain settings can change behavior after players have committed. Snapshot gameplay/reward configuration and versions at event start. Define which presentation-only fields can reload safely. Validate the entire replacement before swapping; malformed reload must retain last good configuration.

### F14 — P1 / S: eligibility is a moving average; scaling intentionally adds HP

Rules.qualifies depends on average damage at payout. Pool.growTo adds the max difference to current HP; Scaling intentionally grows quadratically before its duration cap, following an older owner brief. These are existing design choices, not accidental arithmetic bugs. They conflict with the new proposal's locked eligibility and frozen health. The owner has now approved locking maximum HP at combat start, superseding live pool growth for this design. The numerical formula/roster and qualification policy remain open. Update existing dynamic-growth tests when implementing the approved change, rather than describing the old deliberate curve as an arithmetic defect. Never call a player qualified before that status is irrevocable.

### F15 — P1 / S: listener failures can be treated as completion

WB:681-685 guards each callback, then marks the batch announced even when a listener threw. The state is subsequently finished. Deduplicating a handler does not ensure a failed delivery will retry. Add listener-specific durable delivery state for mandatory integrations, with bounded retry and operator repair. Optional analytics can remain best effort. Classify each consumer; do not promise guaranteed community benefits through the current callback alone.

### F16 — P1 / S: persisted data permits inconsistent states

State.load can leave `active=true` with no usable pool, trusts contribution values and UUID entries, and has no explicit world-boss schema version. Pool restoration does not validate all finite values or reconcile totals; its constructor accepts positive infinity. A zero-current, not-defeated restored pool is not normalized by startup. These are malformed/migration-state risks, not proven normal-save outcomes. Load validation must quarantine invalid records without resetting them into fresh payable events. Test NaN/infinity, missing keys, duplicates, invalid UUIDs and inconsistent totals.

### F17 — P1 / S: some entry paths accept an expired event until the periodic tick ends it

WB.join and strike check active state but not the wall-clock deadline; tick checks expiry once per second. Exact boundary behavior can depend on call order. Define one authoritative deadline predicate and tie-break for a final hit at the cutoff; apply it to commands, callbacks and objectives, not just the periodic timer.

### F18 — P1 / S: scene/rebuild cleanup can orphan client displays

Dome.ensureBuilt:160-174 clears SHOWN/FLASHES and replaces entity references without removing previously sent display IDs. Changing geometry or starting another event before clear can leave ghosts until the client resets its world. Remove old per-viewer IDs before replacing them; test geometry reload, rapid stop/start, dimension changes and reconnect with the same UUID.

### F19 — P2 / S: music stop uses the new configuration's sound ID

Music.Playing records track kind and start tick, while stop resolves the sound from current config. After an ID reload it may stop the new ID and leave the old recording playing. Track the exact sound/channel sent, stop it before switching, and restore music state on failure/exit. Verify interaction with Cobblemon battle music and other server audio; a server slider alone cannot provide independent per-client voice control without an actual preference mechanism.

### F20 — P1 / S: current forced wins are indistinguishable from natural wins to listeners

WB.forceWin:617-638 fills remaining HP for participants and uses normal defeat callbacks. The API does not carry an admin/test outcome reason. A future legacy listener would advance the chapter or issue prestige rewards unless provenance is added. Distinguish natural clear, timed escape, cancellation, admin test and operator recovery. Never infer legitimacy from damage totals or staff presence.

### F21 — P1 / S: numeric parsing can silently change integer values

Config.fromJson reads numeric fields through float `num` and casts them to int/long. Finite JSON values are already checked; do not claim all nonfinite JSON is accepted. The actual gap is precision loss, fractional truncation and insufficient practical upper bounds for some fields. Use typed exact parsing, reject fractional integers, bound work/resource costs and validate cross-field relationships. Test values around float precision boundaries and numeric type mismatches.

### F22 — P2 / S: an award failure can still produce a success message

WB:665 guards the award, then increments paid and tells the player they earned points regardless of whether it threw. The balance API also clamps at Integer.MAX_VALUE in RaidPlayerRecord.withPoints, so nominal amount can exceed actual credited amount. Settlement must report durable credited/pending status, retain unpaid entitlement and define balance-cap overflow policy.

### F23 — P1 / S: automatic placement has gates unrelated to placed arena markers

WB:225-226 requires night and a player in config.spawnDimension before start selects an arena. With a separate arena dimension, these checks can suppress a valid arena start. Validate whether scheduling follows the arena's clock, real-world schedule, or configured spawn world. Test an occupied arena with an empty configured spawn dimension.

### F24 — P1 / S: full state transitions are not protected by the pool's synchronization

Service entry points rely on server-thread usage; synchronizing Pool methods does not make state, callback maps and external side effects atomic. Encounter.start asserts server thread, but the public world-boss start surface does not enforce that before mutating state. Add fail-fast thread ownership or server-thread dispatch at boundaries, immutable listener payloads and reentrancy protection. The same mutable damage-map copy is passed to successive listeners in finishDefeat, so one listener can alter what later listeners receive. Test off-thread and reentrant consumer behavior without assuming future integrations obey comments.

Other evidence gaps: `sweepLeftovers` and showpiece creation synchronously request chunks; worst-case stall is unmeasured. `openGathered` starts all waiting duels in one tick; admission burst cost is unmeasured. Existing pure tests and historical bot runs do not establish transaction recovery, current real-client presentation or capacity for the new design.

## 3. Required correctness contracts

These are proposed engineering requirements to ratify before implementation:

1. One authoritative writer owns an event. Every accepted operation names that event and expected lifecycle generation. A shared multi-server event requires an actual ownership/coordination design; JVM-local maps are not that design.
2. An event has exactly one immutable terminal outcome. Victory, expiry, cancellation and test completion cannot overwrite one another. Command/callback ordering and deadline equality have explicit rules.
3. Only a valid active duel bound to this event and participant may change its World Pool. Repeated damage delivery needs an upstream exactly-once contract or a durable sequence/token; blindly trusting amount/player is insufficient.
4. Shared HP stays finite, within bounds, and follows the decided scaling policy. Applied shared damage and credited shared damage reconcile within a documented numeric tolerance.
5. Event rules, character/awakening, reward version and scene selection are snapshots. Reloads cannot reinterpret earned history.
6. Qualification is monotonic once awarded, with a defined cutoff and evidence. The final contributor must not lose eligibility because their duel is still completing.
7. At most one active/starting duel exists per player. Leave intent, cooldown and phase admission rules apply equally to commands, right-click, chain, reconnect and API calls.
8. Every committed reward is recoverably owed until durably settled. Balance mutation and its deduplication receipt share a durability boundary. No external callback completion is inferred from lack of an uncaught exception.
9. Legacy advancement is derived once from a qualifying committed outcome. The cleared chapter remains the chapter shown on its monument/rewards; next awakening is a separate value.
10. Presentation cannot decide rewards, legacy or outcome. Missing assets, muted sound, skipped scenes or client disconnect cannot lose earned value.
11. Every event-created entity/display/task/chunk ticket/audio instance has an owner and an idempotent cleanup path. Unrelated entities and global world settings are preserved.
12. Teleports validate destinations and retain a recoverable origin until success. Players always have a safe exit independent of event activity.
13. Recovery chooses a valid resumable state, a committed-result repair path or an explicit quarantine. It never guesses success from inconsistent data.
14. Persistence failures are observable and cannot be silently converted into success. Backups and rollback recovery include economy, legacy and claims together.
15. Work and storage are bounded by configured limits. No command, client packet, failed integration or scheduler retry creates unbounded work on the tick thread.

## 4. Selected lifecycle and required proof

The current specification selects PREPARING -> MUSTER -> COMBAT -> OUTCOME_COMMITTING -> OUTCOME_COMMITTED -> CLOSING -> CLOSED, with RECOVERY_REGROUP, STAFF_HOLD and QUARANTINED handling. Two bounded, nonoverlapping ward windows are combat substates. They pause new admissions, not existing shared damage; no global drain or hard threshold HP barrier is selected.

One authoritative outcome freezes reason/time, recipients, last-action Merit/qualification, character/awakening, reward/asset versions, roll results, blessing and history evidence. It commits before economic effects or defeat presentation. Event-scoped encounter bindings reject stale callbacks; receipt-aware balance settlement and mandatory effects replay from durable evidence. Presentation remains best effort and cannot decide economic results.

The selected storage direction is an outcome journal plus atomic balance-and-deduplication-receipt settlement extending the existing Raid Points authority. It still requires a storage/compatibility spike. The existing SavedData and AtomicFiles paths are not accepted as proof of physical durability or cross-store atomicity. In particular, AtomicFiles currently has a non-atomic rename fallback and no explicit durable force.

Test transition guards, event ownership, final-hit qualification, actual write-failure acknowledgment, balance/receipt replay, old duels after a new event, slot reconciliation and independent scene recovery. A state enum or a selected policy is not a correctness proof. See implementation specification sections 2-8 for the executable contract.

## 5. Future failure register

Each item is a D/V risk requiring a decided rule, implementation guard and acceptance scenario, not a statement that the unbuilt feature already fails.

### Battle phases and participation

| ID | Failure to anticipate | Required prevention or proof |
| --- | --- | --- |
| D01 | Several duels cross a ritual threshold in the same tick; multi-hit/recoil follows it. | Serialize/clamp shared damage once; transition once; preserve private-duel completion; reject post-barrier shared credit. |
| D02 | A draining duel stalls, disconnects or exceeds timeout; early finishers wait indefinitely. | Bound draining and classify timeout outcomes; never rely solely on a callback that might be missing. Reconcile encounter registry and event registry. |
| D03 | Phase barriers suppress the positive shared damage needed for qualification. | Decide separate active-duel evidence vs shared-damage credit and display why shared HP is paused. |
| D04 | The kill happens inside the player's qualifying second duel, before onEnded. | Decide qualification finalization at outcome, including valid in-flight actions; freeze a bounded closing record. Never settle too early then omit the killer. |
| D05 | A strong group skips through the whole boss before newcomers can qualify. | Calibrate duration/opportunity; no admission promise without enough remaining activity. Do not fabricate damage or undo the victory. |
| D06 | Players force quick losses/timeouts to farm completed-duel counts. | Require meaningful validated activity; define surrender, disconnect, timeout and artificial inactivity rules. |
| D07 | A ritual needs more contributors than remain online. | Snapshot quorum, define departures/fallback and bound minimum/maximum; do not silently resize midway to manipulate outcome. |
| D08 | Newcomers need energy from battles they cannot win; objective-only route is circular. | Design an independent meaningful novice source and prove its opportunity budget, route and accessibility. |
| D09 | Moving pylons, simultaneous clicks, offhand packets, replayed interactions or logout duplicate energy. | Validate event/window, range, player state and one-time action identity; spend energy and grant objective credit as one operation. |
| D10 | Leaving/rejoining resets caps, energy, cooldowns or AFK checks. | Scope records to UUID plus event, not connection or entity instance. |
| D11 | Player teleports, dies, rides, flies or changes dimension during admission. | Revalidate at actual start; bound vertical participation as well as horizontal radius; handle teleport cancellation/races. |
| D12 | Party health/PP and held items differ between real state and simulation at cleanup. | Verify carryover, fainted-party recovery, item consumption and disconnect grace with the actual modpack. |
| D13 | Destiny Bond, Perish Song, OHKO, percentage HP, status, drain, reflect, multi-hit or custom abilities bypass intended scoring. | Create a mechanics compatibility matrix against existing raid patches. Test each supported mechanic; explicitly disclose restrictions. |
| D14 | Weather/status damage has no attributable contributor; support is invisible. | Decide unattributed pool damage and objective evidence; never credit boss self-damage to a random player. |
| D15 | Private copies crowd/occlude the showpiece or a hidden copy makes targets unclear. | Real-client layout/targeting tests at capacity; safe reserved spaces; no overlapping/zero-distance entities. |
| D16 | Healing/PC access becomes a free general-purpose exploit or the only viable strategy. | Decide event healing scope, cost and timing; keep novice recovery viable; test external healing/rental/PC mods. |

### Rewards, economy and chase-item extension

| ID | Failure to anticipate | Required prevention or proof |
| --- | --- | --- |
| D17 | Reward rules change while an event or claim survives a restart. | Persist reward contents or immutable versioned rules/seed; retain removed definitions needed by earned claims. |
| D18 | Inventory full, player offline, storage unavailable or a cosmetic registry entry is removed. | Durable mailbox/entitlement with explicit delivery state; never drop valuable items on the ground or discard a missing-definition claim. |
| D19 | Item delivery and consumption of its claim save in different files. | Define idempotent redemption and recovery; test inventory save, logout and process-kill order, not just a claim queue flag. |
| D20 | Guaranteed progress, rare drop and redemption all grant the same unlock concurrently. | One atomic ownership/progress rule; define duplicate compensation and progress after an early complete drop. |
| D21 | A supposedly bound relic is copied, traded, dropped, renamed, placed in storage, consumed or moved to a traded Pokemon. | If Worldheart is approved, choose entitlement vs physical item semantics and authoritative ownership; validate every transfer route. |
| D22 | Cosmetic attachment destroys shiny/form/gender/evolution state or another cosmetic. | Define composition, supported species, evolution/fusion/trade behavior, detach/recovery and asset fallback. Never rewrite a Pokemon wholesale for appearance. |
| D23 | Top damage bonuses incentivize weak-player exclusion or account farming. | Equal base progress, bounded bonuses, objective validation and audited issuance; do not claim automated multibox detection is solved. |
| D24 | Blessing applies twice, boosts itself, rounds inconsistently or exceeds point limits. | Snapshot eligible reward base/time; deduplicate each reward transaction; checked arithmetic and explicit rounding/overflow policy. |
| D25 | Blessing expires during a raid, outage or queued reward claim. | Decide eligibility at raid start, victory or claim, and real-time vs online duration. Replays cannot extend expiration. |
| D26 | Whole-world rollback restores a spent claim or loses an already-announced awakening. | Coordinated backup/restore and reconciliation policy; include all authoritative stores and external integrations. Absolute exactly-once across arbitrary partial rollbacks cannot be promised. |

### Legacy and scenes

| ID | Failure to anticipate | Required prevention or proof |
| --- | --- | --- |
| D27 | Same character starts on two servers/arenas and both use chapter III. | Decide global scope; reserve chapter under a single authoritative owner or use independent shard histories. |
| D28 | Character renamed, definition replaced, season reset or test force-win advances the real legacy. | Stable character ID and provenance; explicit season/lineage policy; audit migrations and admin actions. |
| D29 | Roman numerals become enormous; hardcoded dialogue claims the wrong number. | Bounded display format with Arabic fallback; reusable recorded lines and exact subtitle/transcript selection. |
| D30 | Voice lines conflict with recorded challenge outcome or player history. | Persist dialogue-selection inputs/variant; distinguish community legacy from individual participation. |
| D31 | Sound can be heard twice, not at all, or at a different point after lag/reconnect. | Best-effort per-viewer playback, event-scoped cue IDs and tested resync policy; never promise exactly-once client playback. |
| D32 | One skip vote skips everyone, or unskippable scenes prevent playing. | Per-viewer preference; server combat timing independent of individual skip/audio settings. |
| D33 | Server-only implementation cannot independently control voice, subtitles or duck only its own music. | Prototype actual protocol, preferences and pack behavior; record capability limits before promising UI controls. |
| D34 | Resource pack declines, fails, reloads or mixes versions; models/audio disagree. | Versioned asset manifest, capability-aware fallback, transcript, and no mandatory dependency on successful playback. |
| D35 | Display entities/audio/tasks survive logout, death, respawn, dimension switch or stop/start. | Session-aware per-viewer cleanup; exact IDs and sound instances; test new connection with same UUID. |
| D36 | Global sky/time/weather changes leak into ordinary play or overwrite another mod's settings. | Prefer scoped visuals; if global mutation is chosen, define ownership/restoration and conflict policy first. |
| D37 | Scene freezes, repeats on recovery, or follows an old boss after a new event starts. | Scene generation and cancellation tokens; stale callbacks cannot mutate current presentation. Result recovery is independent. |
| D38 | Voice assets are inaccessible or difficult to understand. | Legible timed subtitles, no color-only cues, adjustable effects, readable contrast, localization fallback and deliberate voice asset rights. |

### Operations, scheduling, compatibility and security

| ID | Failure to anticipate | Required prevention or proof |
| --- | --- | --- |
| D39 | DST repeats/skips an event; reboot catches up several missed events at once. | Persist occurrence ID; explicit ZoneId and missed-slot policy; test spring/fall transitions and clock changes. |
| D40 | Wall-clock deadlines disagree with tick-based cooldowns during lag, pause or dimension time differences. | Define separate schedule, active-combat and presentation clocks; boundary rules; persist restart-safe remaining time/deadlines. |
| D41 | No players, no eligible teams, no safe entrance, missing dimension or removed marker. | Preflight and defined cancel/retry/backoff; never consume legacy or silently relocate the designated event. |
| D42 | Mass warp/start/reward reveal causes a tick spike or client crash. | Target capacity, admission work budget, preloaded bounded arena, packet/display caps; test burst and sustained load. |
| D43 | One listener, corrupt state or missing definition causes a fault every tick indefinitely. | Explicit health/quarantine, bounded retries, alert once with useful context, operator inspect/repair; preserve evidence. |
| D44 | Commands or packets spoof objective success, test-win permission or arbitrary entity/player IDs. | Server-authoritative validation, permission checks on every admin branch/API, rate limits and bounded inputs. |
| D45 | Player disconnects while a warp is pending or duplicate requests allocate several duels. | Reservation/starting state with cancellation, per-player admission token and bounded pending queue. |
| D46 | Scheduled task/listener retains old server references across reload/stop. | Register/remove symmetrically, scope tasks by server generation, test integrated-server world switching. |
| D47 | Stored history, deduplication keys, scene records or pending claims grow forever. | Retention/archive/compaction policy preserving unsettled entitlements and deduplication safety; bounded UI queries. |
| D48 | A mod update compiles but invalidates mixin targets, protocol interpretation or Showdown patches. | Pin deployed versions, run existing validators and exact-modpack startup/mechanics tests; follow ADRs 0002-0004. |
| D49 | Operator fixes data manually and triggers another reward/history mutation. | Idempotent inspect/retry/reconcile tools, dry-run output and audited repair reason; no undocumented reset-everything command. |
| D50 | Crash after commit shows no celebration and players think the clear failed. | Result journal and guaranteed status lookup; truthful secured/pending messages; optional scene replay without replaying effects. |
| D51 | Important instructions disappear because broadcast is muted or another mod overwrites the action bar/boss bar. | Local event panel/transcript and notification hierarchy; test coexistence with other HUDs, scoreboards and localization. |
| D52 | External services hang the main thread or request player data unnecessarily. | No network work in tick/settlement callbacks; bounded asynchronous jobs with generation checks and minimal data. Scope any external integration explicitly. |

## 6. Decisions that cannot be assumed

This register now mirrors the selected Q01-Q18 resolutions in the implementation specification. Owner-confirmed choices remain distinguished in the experience design; other policies were selected under delegated discretion for final review. The third column identifies evidence still required, not unanswered preference questions.

| ID | Required input | Why it blocks implementation or signoff |
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

## 7. Test strategy and acceptance matrix

Tests must verify observable contracts, not reproduce implementation branches. Pure tests alone cannot validate packet cleanup, inventories or power-loss durability. Existing world boss tests cover arithmetic/config/geometry/music-selection behavior, but are not evidence for the future transaction/scene system.

| Test family | Required scenarios | Evidence to retain |
| --- | --- | --- |
| State model | Every allowed/forbidden transition, repeated calls, cancel/expiry/victory same tick, no participants. | Transition table plus deterministic property/sequence-test results. |
| Event isolation | A duel hits after B starts; stale end/scene callback; duplicate hit; unknown participant/encounter. | B unchanged; ownership indexes reconcile. |
| Phases | Hits just above/on/below both thresholds, multi-hit, final overkill, several duels same tick. | One transition, valid damage sums, no skipped act. |
| Eligibility | Killer still in duel, late novice, objective-only route, short clear, surrender/timeout, reconnect, repeated objective. | Same locked entitlements before/after restart; explicit denial reason. |
| Save and settlement | Terminate before/after every durability boundary and every recipient credit. Disk-full/read-only/partial write. | No committed entitlement lost or duplicate credit; unresolved claims inspectable. |
| Recovery | Graceful stop, forced process kill, expired event at boot, missing definition/dimension, zero HP uncommitted, invalid schema. | Correct resume/outcome/quarantine; no guessed victory. |
| Legacy | Natural clear, escape, test win, admin cancel, retry callback, season/rename migration. | Counter advances exactly once only for allowed outcomes. |
| Listener delivery | Throw before/after consumer side effect, duplicate delivery, unavailable mandatory integration. | Idempotent effect plus durable retry/receipt. |
| Teleports | Repeated warp, unsafe entrance, removed dimension, changed terrain, dead/spectator, another teleport mod cancels. | Recoverable origin and safe exit; no unexpected world coordinates. |
| Leave/admission | Gather leave, active leave, manual rejoin, right-click spam, API/chain races, disconnected pending start. | Single duel per player; intent/cooldown honored. |
| Assets/audio | Pack accepted/declined/failed/stale, voice muted, scene skipped, music ID reload, reconnect/respawn. | Clear subtitles; no old sound/display instances or reward dependence. |
| Scenes | Players mid-battle at victory, late join, move away, death, skipped scene, restart at each cue. | Stable result and variant; no global retrigger or forced battle closure. |
| Mechanics | All supported banned/percentage/status/heal/multi-hit/ability interactions, ascension effects, item use. | Expected applied damage and valid simulation cleanup on exact modpack. |
| Performance | Approved capacity and burst, spectators, mass admission, objective spam, payout burst, repeated event cycles. | Tick p95/p99/max, heap/GC, packet bytes/display counts, client FPS and freeze duration. |
| Endurance | Repeated starts/stops/reloads/reconnects, world switch, archived history, backlog of offline claims. | Stable task/entity/map counts and bounded disk/queue growth. |
| Security | Every admin permission, malformed/replayed packet, stale UI, client-declared reward/phase, extreme config. | Rejection without mutation or server-thread stalls. |
| Restore/migration | Coordinated backup restore, partial restore detected, upgraded schema, unsupported downgrade, deleted cosmetics. | No silent reset/duplicate entitlement; documented repair route. |
| Usability | First-time novice, veteran, spectator, muted player, color-vision/accessibility needs. | Objective/reward/return understood without staff intervention. |

For pure numeric/persistence functions use boundary and property tests with recorded random seeds. Check sum conservation using an agreed numeric tolerance or selected integer unit. Include malformed load fixtures and real historical schema fixtures.

For server tests use isolated throwaway worlds and real clients as well as bots. Bots do not demonstrate voice intelligibility, visual cleanup, FPS or human understanding. Do not simulate ordinary healing every few seconds in a capacity test without recording how that differs from player gameplay.

Test 1/5/10/20/30/50 participants, including the confirmed 50-participant maximum and a simultaneous arrival/admission burst. Q02 still needs spectator limits, burst budgets and admission-at-cap rules. The previous p95 < 50 ms target is provisional and insufficient alone: also approve p99/max stalls, client minimum performance and per-event overhead relative to the normal modpack baseline.

## 8. Diagnostics, operations and release gates

Every authoritative operation should be traceable using server/world identity, event ID, character ID, awakening snapshot, state generation and transaction/encounter ID where relevant. Log transitions, rejected stale callbacks, save acknowledgment/failure, pending settlement age, listener retries, mismatched cleanup and admin provenance. Avoid logging every tick or player coordinate unless needed for a bounded diagnostic session.

An operator status/inspect view needs combat phase/deadline, active/starting duels, qualification counts, snapshot version, pending reward/consumer deliveries, next legacy number and storage health. Repair should retry the same durable operation, not synthesize another win.

Before public release:

1. Resolve Q01-Q18 where relevant to the release slice; record each answer and owner. Deferred features stay off and do not silently choose defaults.
2. Reproduce P0/P1 source findings with meaningful regression tests and implement scoped fixes. Current pure tests passing would not close them.
3. Ratify lifecycle, durability, qualification and legacy invariants; select storage that can actually satisfy them.
4. Freeze the deployed version/modpack/pack manifest and run compilation, full applicable tests, existing source validators, startup checks and mechanics verification.
5. Pass forced-kill and failed-write tests, including at all settlement/legacy boundaries. Record permitted loss only for explicitly noncommitted progress.
6. Demonstrate isolation from ordinary raids, unrelated entities and other worlds; close admission/leave/teleport failures.
7. Prove supported-capacity burst/endurance and real-client scene quality with optional assets disabled too.
8. Exercise backup/restore, migration and rollback with outstanding rewards. A binary rollback is not sufficient if data schemas changed.
9. Rehearse operator inspect/retry/quarantine and document recovery for every persistent state.
10. Run a small invited player trial, measure comprehension, qualification opportunity and waiting time, then expand within verified capacity. Track unresolved risks with severity and explicit acceptance.

Required artifacts are the transition specification, decision log, risk-to-test mapping, persistence/settlement design, compatibility manifest, asset manifest, measured test report and recovery runbook. These are focused deliverables for the chosen release; this audit does not justify building a generic event framework or speculative infrastructure.

## 8a. Pause and queue handoff contract

Owner decisions now confirmed: Pause Battles lets an active duel finish normally, then suppresses automatic re-entry and releases that participant slot. Contribution and already-earned eligibility remain. Additional entrants beyond the 50-participant limit use a visible waiting queue. Resume places a paused player at the back of that queue, or admits them if there is capacity and no earlier eligible waiter.

This is a specification and test plan, not implemented or verified behavior. The current WB.leave/duelEnded combination does not satisfy pause intent (see F06). No claim of zero defects is justified before implementation and verification.

### Distinguish player intent, event records and occupancy

Maintain separate event-scoped records for pause intent, immutable earned contribution/eligibility, queue membership/order, slot reservation and bound encounter. Releasing a slot must not delete contribution or rewards. A player may be enrolled by bubble presence without occupying one of the 50 participating slots.

Use one server-thread admission coordinator for bubble entry, pause/resume controls, commands, automatic chaining and encounter callbacks. All routes consult the same state. Do not implement the cap by counting only completed battle starts or using independent per-entry-point checks.

Required invariants:

- At most 50 occupied slots, including admission reservations and starts in progress. Normal reserved breathers must not be invisible to occupancy accounting.
- At most one active/starting encounter and one queue entry per player per event.
- A paused player cannot be automatically admitted by a timer, bubble scan, command shortcut or duel-end callback.
- Every slot/encounter callback binds event ID, player UUID and a generation/token. A stale callback cannot release a later reservation or alter a later event.
- Slot release is conditional and idempotent. Calling it twice cannot increase capacity twice.
- Queue promotion reserves a slot before calling Encounter.start. On refusal/exception, roll back only that reservation; do not orphan an entity, poison the queue or inflate HP.
- Repeated Resume presses cannot create duplicates, move an existing waiter to the front or reset fairness/cooldown rules.
- Promotion revalidates bubble presence, event/phase, pause intent, capacity and actual battle availability immediately before starting.
- A new entrant or resumed player cannot bypass earlier eligible waiters merely because they arrived between release and the next queue tick.
- An outcome or ritual admission barrier wins over automatic promotion. Finishing a duel during a ritual does not launch another one.
- A player leaving/disconnecting mid-duel cannot release their slot for reuse while their previous duel is still capable of running as an active slot; terminate/reconcile it according to the decided disconnect policy.
- Full recovery happens on a validated completion after battle cleanup, with a single applicable recovery action per duel. Do not replace a player's party from an old snapshot, heal a different event's party, or let a duplicate callback mutate newly changed Pokemon.

### Expected handoffs

| Trigger | Required state transition | What must remain preserved |
| --- | --- | --- |
| Pause during active duel | Record pause intent immediately; keep that duel and its slot until valid duel-end cleanup. Then release once and promote the next eligible waiter. | Current combat flow, applied damage and earned eligibility. |
| Pause during breather | Cancel pending automatic start and release the reserved participant slot once. | Existing earned progress; no new duel. |
| Pause while queued | Remove queue membership and prevent automatic re-enqueue. No participant slot exists to release. | Earned progress from any previous duel. |
| Pause races a reserved start | Serialize on the event coordinator. Cancel a not-yet-committed start and release its matching reservation, or if the duel is already committed let it finish paused. | One clear winner; never two active starts. |
| Resume while paused | Clear pause intent through the explicit control and enqueue once at the tail. Start only after normal capacity/phase/availability checks. | Historical contribution and qualification. |
| Resume while already waiting/active | Return current status without adding another queue entry or battle. | Queue position and active encounter binding. |
| Duel ends after Pause | Check the bound encounter and current intent. Heal through the validated recovery path; release its slot instead of adding CHAIN. | Applied damage, earned eligibility and pause intent. |
| Old duel-end callback after another admission/event | Reject stale generation/binding; do not remove the newer duel or release its slot. | New event/encounter state. |
| Event outcome commits | Freeze result evidence and stop admissions/queue promotion. Close or drain existing encounters under the event closure rules. | Committed entitlements and history; no next-event callback leakage. |

Selected timing: Resume never shortens the 15-second cooldown; queue/ritual time can satisfy it without granting priority. All manual and automatic entry routes use this same rule. Readiness is now confirmed: require at least one Pokemon, no other battle, not spectator and not paused; fainted Pokemon remain eligible for admission healing. Unavailable bubble entrants are enrolled without occupying slots or counting toward the initial minimum/HP.

Pause persistence is now confirmed: for the current encounter it survives reconnect and bubble exit/re-entry until explicit Resume Battles. Store authoritative pause intent with the event so recovery/reconnect cannot silently clear it. A different encounter starts without the prior event's pause flag; never carry it across event, season or world identities.

### Required targeted regression scenarios

| Test | Scenario | Required observation |
| --- | --- | --- |
| PQ01 | Pause before the current duel's final action. | That action and cleanup occur normally; no automatic next duel; release exactly once. |
| PQ02 | Pause and duel-end are processed in both possible orders in one server tick. | Same final paused state; no leaked slot or unexpected chain. |
| PQ03 | Several players press Pause/Resume repeatedly. | One intent and queue record per player; occupancy stays within bounds. |
| PQ04 | Fifty occupied slots plus several entrants in the same tick. | No 51st reservation/start; visible queue order is consistent. |
| PQ05 | Resume while an earlier player waits and a slot has just opened. | Earlier eligible waiter is promoted first. |
| PQ06 | Pause during a 15-second breather immediately before its timer fires. | Timer cannot start a duel after pause takes effect. |
| PQ07 | Pause a queued player. | Only that queue entry is removed; occupancy does not decrement. |
| PQ08 | Encounter.start refuses, throws or fails to produce the expected bound encounter. | Matching reservation rolls back; no duplicate boss, lost participant record or permanent queue stall. |
| PQ09 | Duplicate/stale onEnded, or A callback after B starts. | No second release, duplicated healing, removed newer binding or new-event damage. |
| PQ10 | Head waiter disconnects, becomes busy or leaves the bubble before promotion. | Explicit admission policy applies without blocking everyone or starting an invalid battle. Selected policy removes unavailable waiters from ready priority and returns them at the tail once ready. |
| PQ11 | A ritual barrier or victory occurs after reservation but before start. | Admission cancels safely; no late duel crossing the barrier or outcome. |
| PQ12 | Pause, change party, then receive a delayed old callback. | No party replacement or stale healing mutation; contribution unchanged. |
| PQ13 | Player exits/re-enters or reconnects while paused. | Event-scoped persistent pause policy is honored; no implicit duplicate enrollment/start. |
| PQ14 | Restart with active, paused, breather and queued players. | Recover only valid event records; no orphaned slots; earned rewards persist; queue/pause recovery follows decided policy. |
| PQ15 | Same UUID reconnects or joins a different world/season. | Old connection UI/callbacks cannot control a new event or server generation. |
| PQ16 | All 50 players finish in a burst, some paused and some chaining. | Consistent occupancy, bounded healing/admission work, stable queue; no one treated as two participants. |
| PQ17 | Pause while the boss dies or its timer expires. | Outcome adjudication and final earned progress do not depend on the pause command's presentation timing. |
| PQ18 | Queued player cannot receive updates or uses a stale queue UI. | Authoritative state stays correct; refreshed UI gives current position rather than accepting client-declared admission. |

Run model-based/random operation sequences as well as these deterministic scenarios. Assert occupancy, unique membership, encounter ownership and progress preservation after every operation. Record seeds and failure traces. Test hard kills and real-client controls on an isolated server after implementation; unit tests alone cannot establish safe party mutation or reconnect presentation.

Selected completion rules: 25 observers, 30-second active-duel disconnect grace, temporarily unavailable waiters rejoin the ready tail, event-persisted pause intent, cooldown-preserving Resume, checkpoint queue recovery among present/ready players and final-action qualification before outcome. The implementation must now prove these through the regression scenarios; verified fixes are still outstanding.


## 9. Audit outcome

The policy design is consolidated for owner review. Production readiness still requires the storage/compatibility spikes, implementation and measured release evidence. Highest-priority correctness work remains reward durability/settlement (F01-F03), event callback isolation (F04), ownership-safe cleanup (F05) and explicit outcome provenance (F20). All 24 source findings remain open until verified fixes, irrespective of policy selection. The named legacy, voices and chase depend on those contracts.

This document adds analysis and release criteria only. Findings are not fixes, proposed tests are not passing tests, and illustrative creative choices are not final product decisions.
