# World boss arrivals and audience

Implementation: November 18, 2026. This stage supplies entry, spectator and presentation controls. It does not build live terrain, install production settings or establish native multiplayer performance.

## Player flow

- `/cr worldboss fight`: explicitly join the fight. A nonempty party is required. Inside the enrollment bubble it resumes participation; outside it queues a dispersed battle landing. It clears both saved Watch/pause intent and the live admission pause after the change is acknowledged.
- `/cr worldboss observe`: queue a spectator landing. Processing durably selects Watch and pauses future battles; already-earned Merit, contributions and reward promises remain intact. An active duel or unrelated battle must finish first.
- `/cr worldboss warp`: bounded legacy entry command. Already-paused players, players without a party and closed newcomers are directed toward Watch. It does not silently resume a saved pause.
- `/cr worldboss return`: cancel a queued arrival and use the existing durable return flow. Pause also cancels a queued arrival. Successful arrival sends clickable Join Fight, Watch and Return controls.

The arrival queue holds at most 75 account-owned requests and processes at most two per server tick. These are technical limits, not measured production tuning. Existing participant admission remains FIFO with at most fifty occupied duel/breather slots and its own two-starts-per-tick bound. Those two budgets are independent.

Stand still while queued: movement beyond half a block, dimension change, a new player instance after reconnect, death, mounting, another battle, event replacement or a thirty-real-second deadline can cancel entry. Repeating the same request does not extend its deadline; a changed mode/viewer instance moves to the tail. Pending requests temporarily suppress automatic duel/focus/relay starts. They are unacknowledged runtime requests, not restored as pending teleports after a restart. No original origin is changed until travel is actually prepared.

Watch/Fight intent is acknowledged before native travel. A refused teleport can therefore leave the requested participation setting active; it does not imply that a saved return was consumed. An I/O acknowledgement failure can also leave a complete journal frame; the existing recovery contract, rather than a guessed rollback, governs restart behavior. Watch intent is per occurrence, not a permanent account setting, and clears on a new boss occurrence or explicit resume.

## Building the arena layout

Keep the configured boss spawn marker and entrance marker(s). A new marker item is available to staff:

`/give @s cobbleraids:world_boss_observer`

Place it at the **feet/arrival height** on a solid, clear spectator terrace, facing the boss through the warp orientation. Its actual three-dimensional distance from the boss must be strictly greater than `R + 2` and at most `R + 12`, where `R` is the event's enrollment radius. The entire audience volume is the three-dimensional sphere `R + 16`. The spectator band therefore stays outside enrollment and its one-block retention hysteresis. Ensure native build height, world border, protection and chunks also cover the terrace.

Live preflight requires one to fifty entrance markers, one to twenty-five observer markers with at least one valid safe observer anchor, the existing ward/relay layout, and a safe loaded shared spawn outside the audience volume. Having a marker does not prove twenty-five safe occupied spectator positions or fifty native battle spaces. The scene/dome packets do not substitute for a built platform or clear sightlines.

An **unvalidated pilot layout** can place a spectator anchor about `(R + 8, +4, 0)` relative to the boss, with a generous flat platform around it. The observer candidate grid extends nine blocks in each horizontal direction from an anchor, at three-block intervals, and is clipped to the spectator band. Put railings/decoration beyond candidate body clearance, then verify the actual safe positions with native clients. This is an authoring starting point, not an installed schematic, accepted world coordinate, or validated capacity/height requirement. Adapt it to the arena terrain, Pokemon scale and player avatars.

Participant entry uses marker-authored heights and a bounded eighteen-block grid around the boss. At most 96 unique candidates are considered per request; no heightmap guess or unchecked terrain fallback is used. A candidate must have a clear supported technical battle corridor and must avoid current battle reservations, other arrival corridors and nearby living/collidable entities. The farthest minimum clearance from existing visitors is tried first. Spectator landings validate the actual player body and spacing. Own following Pokemon remain with the existing native owner/recall behavior; other nearby entities can make a landing unavailable.

Arrival corridors remain reserved while the owner stands at the landing before its duel, including gathering. They are released on movement, role switch, disconnect, native duel ownership, event replacement or shutdown, with at most 75 staging entries. Native duel selection/revalidation also respects other staged corridors. Runtime staging is not restored as a running battle after restart. Actual party clones/forms/send-out positions still require exact-modpack tests.

## Spectator budget and entry routes

Twenty-five seats cover **current observers plus pending Watch arrivals**. Existing admitted viewers keep priority while present; duplicate/native nested teleport checks do not double-count an account. Cancelled/absent claims are reconciled away. A newcomer that cannot yet qualify, a spectator-mode player or an explicit Watch visitor needs an observer place. Ready queued participants and previously admitted participants resting under an ordinary pause retain participant status; explicitly choosing Watch uses an observer place. None of these labels grants new Merit without the normal admission/action evidence.

The final-minute cutoff is consulted at request and processing time. Newcomers cannot get a late qualification promise merely by having queued a warp earlier. Previously successfully admitted players keep their normal re-entry eligibility.

The existing ServerPlayer position/level/relative/dimension and packet-level teleport hooks now also check spectator entry. Exiting and in-place corrections remain allowed. A relative flag describes the outgoing client's packet encoding; these server methods receive **absolute coordinates**. The corrected existing teleport helper and regression test cover every X/Y/Z flag combination.

Presence reconciliation handles walk-ins and unsupported direct moves after entry. Event-owned dome/border/ward/scene/music/boss-bar delivery excludes unapproved viewers. At most one exceptional evacuation per second is attempted, separate from queued arrivals. A safe loaded original outside the venue can be used normally; otherwise a validated outside shared spawn can be used while keeping an existing original claimable. Failed/cancelled relocation retains the saved original. This path does not invent an outside origin for a walk-in.

Unrelated battles, spectator/mounted state, unavailable exits or protection-mod refusals can defer evacuation. These visitors receive no new owned event presentation, but the system does not abort unrelated battles, force a game-mode change or disconnect players to make a physical headcount claim. The 25-place budget is not a global Minecraft player/entity-visibility filter. Vanilla Pokemon, entities, other plugins and physical bystanders still count toward real client/network/server load. The fifty occupied participant slots are not a cap on every queued or resting participant present. Validate the actual production player cap and the full population.

## Presentation and recovery

Approved terrace viewers receive the shared gathering/regroup/fight boss bar, zone music and their Watch status. Existing battle queue/breather/ward guidance remains. Dome bookkeeping distinguishes actual ServerPlayer instances so a reconnect does not inherit an old client's presumed entity packets. New scene receipts freeze the whole `R + 16` audience radius; the victory/escape scene can finish for terrace viewers after the event closes. Scene playback remains optional, best effort and receipt-bound. Recorded voice performances are still absent.

Root domain schema 9 has an optional strict audience format 1: frozen radius plus Watch account IDs. Watch IDs must be unique and paused; malformed, orphaned or nonfinite metadata quarantines raw evidence. New live/test arena starts freeze the radius before the first event save. Enrollment/protection/arrival calculations use the saved radius across config reloads; changed configured geometry still invalidates calibrated rule matching. Old snapshots without this metadata remain readable without fabricated audience geometry. Their new arrival commands require staff review; ordinary original returns and secured reward recovery remain available.

Calibration policy is v6, with bounded arrivals, spectator capacity and frozen audience geometry. No Dref or production calibration was guessed. The exported listener API remains version 2; the queued entry commands are not methods of that public API.

## Native rehearsal gate

The checkout has `validation/smoke/README.md`, but no configured isolated `server.properties` or arena world was found. The existing harness installs a JAR and needs an explicitly prepared test-server directory/modpack. No live server was booted, altered or driven during this stage. Prepare an isolated exact-modpack rig with nonconflicting ports, a validated layout and test-only encounter controls before running these cases. Do not point the harness at production.

| Population/case | Required observations |
| --- | --- |
| 5 fighters | Gathering minimum, fixed roster/HP, distinct arrivals, real party placement and qualification |
| 10 fighters | Simultaneous requests, two-entry/two-start bounds, clear queues, pause/Watch/Fight switches |
| 30 fighters | 20:00-style surge, arrival and duel wait distributions, observer packets and script timing |
| 50 fighters | Full intended attendance, native actor/model/turn/clear times, memory/GC and client FPS |
| 25 observers, including pending | Twenty-sixth request denial, no first-come seat theft or double count; actual server capacity must permit the intended combined test population |
| Route bypasses | Walking, direct/relative/packet/dimension teleport, login inside, unsupported direct mod moves, in-place corrections and safe exit |
| Failure/recovery | Deadline/movement/reconnect/cancel, unsafe/occupied destination, redirected TP, commit failure, full terrace, outside/unloaded original, failed exit and event replacement |
| Final minute and finale | Newcomer closes while queued; known participants can re-enter; terrace viewers finish the defeat/escape scene; skip/pack/reconnect preferences work |
| Ordinary systems | Other worlds, ordinary raid teleports/UI/carryover, party/PC/trade, personal effects, unrelated battles and protection plugins |

Record build/mod/asset/rule hashes, actual concurrent population, ingress/admission p50/p95 waits, qualification and clear-time distribution, tick p95/p99/max, heap/GC and declared-client FPS/freeze evidence. Initial design gates remain tick p95 below 50 ms, p99 below 100 ms and no event-caused stall above 250 ms; revise capacity explicitly if baseline or trials cannot meet them. Include synchronous journal/points/warp cost. A default-grid geometry test and an offline green build do not close this gate.
