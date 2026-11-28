# World boss arena protection and duel space

Implementation updated: November 19, 2026. Isolated headless native multiplayer trials exist; production deployment and graphical-client safety signoff remain open.

## Protected area

With the world boss system and designated arena enabled, the single configured spawn marker defines a horizontal protected region with radius `arena.radius + 16`, through the world's build height. The extra sixteen blocks cover presence hysteresis, the ten-block private boss placement ring and the diagonal corners of a six-block-wide battle lane. Enrollment remains the original three-dimensional sphere; this protection region does not enroll players.

An active arena event retains its saved center/dimension and never shrinks its cached protection radius during a configuration change. Its protection remains when the master switch is turned off mid-event. The configured marker region may coexist with that active region. Removing or invalidating markers retains the last valid configured region within the current process; active protection is derived again from the saved event after restart. The cached configured region is not a separate durable land claim. An invalid idle marker set on a fresh boot cannot define a new protected location, and live preflight remains blocked.

Creative players with permission level two may edit and interact for maintenance only while event state is known idle. Active and quarantined state deny that maintenance exemption. Living-entity native damage and environmental guards do not have a creative-maintenance exemption. When the system/arena is disabled while idle, configured protection clears. Use existing trusted staff controls for maintenance; no player bypass permission or purchase is introduced.

Region refresh occurs at startup, marker saves, event start and once per second. Native protection hot paths inspect at most two immutable cached regions without loading SavedData or terrain. The fire setter additionally uses a nonblocking loaded-chunk lookup to distinguish an existing fire's age update from new ignition. A malformed marker refresh cannot remove protection already derived from a valid saved event, nor abort the remainder of world boss startup.

## Guarded native paths

| Path | Scope |
| --- | --- |
| Player break/attack/use block | Denied when the actor, target block or placement-adjacent block intersects protection; idle creative staff maintenance may bypass |
| Item use | Denied inside protection; food remains usable except chorus fruit; special mod item semantics still need review |
| Player attacks | Denied when attacker or target intersects protection, subject to the maintenance rule |
| Item-frame/armor-stand interaction | Denied in protection, subject to the maintenance rule |
| Living-entity native damage | Denied in protection, including ordinary environmental/PvP damage; Showdown/Pokemon health application remains the existing battle path |
| Cobblemon unowned Pokemon SpawnAction | Cancelled before world insertion when the new entity body intersects protection; player-owned Pokemon are excluded |
| Explosion block finalization | Protected blocks removed from the affected list before native destruction/drop handling |
| Entity explosion response | Protected entities opt out through the native explosion-ignore method, covering the knockback path rather than relying only on cancelled damage |
| Piston block events | Cancelled within protection plus a fourteen-block guard, before extension/retraction side effects; twelve-block vanilla pushes cannot reach in from just outside |
| Flowing fluid tick/spread | Protected existing fluid ticks stop; source/target checks deny crossing into or out of protection |
| Fire consumption | Native checkBurnOut cannot consume a protected target; outside fire still ages and extinguishes normally |
| Native fire-tag block placement | New ignition denied inside protection through the server Level setter, including native lightning/fireball ignition that does not start as a player interaction; existing fire may age so it can extinguish |

The natural-Pokemon guard uses Cobblemon's cancellable `POKEMON_ENTITY_SPAWN`
event. Native 1.8.2 bytecode confirms that SpawnAction positions the new entity
and emits this event before insertion. The callback uses the same published
immutable region snapshot and contains runtime failures. `/cr worldboss arena
list` reports refused attempts for this server session. Ordinary party send-outs
and this mod's direct world-boss entity creation remain on their existing paths.
Other mods that deliberately route unowned spawns through SpawnAction receive
the protected-region rule as well.

Policy v16 also rejects native Pokémon spawn predicates early within a
`PlayerSpawner` matching pass when all four corners of the candidate block
already lie inside one protected column. The decision is scoped to that exact
candidate, including nested calls, and restored in `finally` if native matching
throws. Non-Pokémon detail types, non-player spawners and outside or partially
contained boundary cells retain their native matching behavior. The existing
pre-insertion entity guard remains for those other positions/paths. No global
spawning rule is changed and no entity is removed. Direct party/world-boss
creation continues through its existing path; other mods' custom placement or
spawn-event relocation still require compatibility verification.

`arena list` now reports both late entity refusals and early protected-position
evaluations skipped. The early count means matching passes in which at least
one Pokémon predicate was skipped, not unique physical cells or constructed
entities. The pilot records both counters and requires native evidence from
either layer with `doPokemonSpawning` still enabled. The v16 native control
exercised 19,338 early passes and 838 late refusals while all fourteen functional
checks passed. These counts demonstrate execution, not overall capacity approval.

This is not a removal sweep or an impenetrable entity barrier. Existing wild
Pokemon, entities walking in from outside, commands and mods that bypass this
spawning pipeline can still occupy space. Placement checks keep rejecting unsafe
duels and tell the player to move or recall nearby Pokemon. Arena preparation
must inspect existing occupants; neither deployment nor recovery silently deletes
unrelated Pokemon. Vanilla mob spawning has its own rules. Cobblemon's
`doPokemonSpawning` is separate from `doMobSpawning`; isolated fixtures must
declare their spawning policy rather than assuming the latter disables both.

Pokemon interaction remains with the existing raid/showpiece and Cobblemon handlers; protected decoration interaction does not consume those packets. Mounting does not grant duel readiness: mounted players and vehicles with riders cannot begin a duel or focus ward; readiness/quorum excludes them. Existing walking-only relay checks remain in force.

This is scoped native protection, not a general land-claim implementation. Server commands, schematic/editing plugins, mods that mutate blocks through other APIs, arbitrary entity-interaction effects, growth/decay/snow, hopper/container automation and third-party damage/teleport mechanics require explicit compatibility verification and, where necessary, the server's protection plugin. Direct command/mod block writes are not generally denied. A fire block setter guard does not imply a universal mutation transaction. No blocks are replaced or restored by this feature. Bounded dispersed arrivals and spectator admission now have their own [arrival/audience guide](WORLD-BOSS-ARRIVALS-AND-AUDIENCE.md); native physical/render behavior still requires rehearsal.

## Private battle space

Before starting, the coordinator considers the existing twenty-four candidate points, six/eight/ten blocks around the player, at the player's current height. There is no airborne or unchecked terrain fallback. The player must be alive, on supported clear ground, unmounted and within the enrollment policy. Player bodies wider than four or taller than eight blocks are unsupported and do not become ready.

A candidate reserves the rectangle between player and boss, expanded three blocks horizontally at each side and eight blocks vertically. The server checks loaded FULL chunks before corridor terrain queries, build-height/world-border bounds, clear body/corridor collision, fluids, listed vanilla hazards, support beneath both bodies and every covered floor tile, plus nearby living/collidable entities. The player's own sent-out Pokemon are left to the existing native battle recall/cloning flow; other players, Pokemon and collidable vehicles block initial placement. The floor requirement is deliberately conservative and favors flat, open arena space.

Up to fifty pending/running reservations exist, counted before native battle creation. Other reservations in the same dimension may not intersect; the exact event/player/generation/encounter token owns each. Repeated same-token reserve is idempotent; stale end/failure callbacks cannot remove a successor's lane. Native ends, refused/exceptional starts, event replacement and server shutdown release space. A disconnect keeps its lease during the already-selected thirty-second reconnect grace; it does not create a second reservation after login.

After the native boss has spawned and its level is applied, an internal placement gate applies the configured scale, refreshes real entity dimensions and rechecks its observed position/body and current lane occupancy before battle creation. Unsupported boss bodies beyond four-by-eight hold combat for staff instead of generating endless content retries. Refusal discards the temporary boss before a session is registered. The public encounter API and ordinary consumers retain their existing start path.

While a duel is active, a lightweight per-tick check requires its player and indexed boss to remain in the reserved dimension/body bounds, unmounted, with the safe horizontal separation. Leaving/invalidating that space aborts the private duel through normal cleanup; still-owned party healing and the usual breather/admission policy apply. The player is told to stay in place at the start. There is no forced teleport or camera movement. A visitor walking through an already-running lane is not prevented by these checks; observer placement/control remains separate work.

Crowded or unsupported terrain produces a clear spread-out/recall message and the existing five-second failed-start retry, within the existing two-starts-per-tick budget. Fifty players clumped on one entrance do not receive overlapping duels. Arrival dispersion, observer controls and representative 5/10/30/50-player queue/clear-time trials remain required. A pure geometry test can fit fifty separated lanes in the default bubble; it does not establish native actor fit or target attendance throughput.

## Calibration and release gates

The protection/duel-space stage used fingerprint v5. The arrival/audience continuation now uses v6, adding bounded dispersed arrivals and frozen audience geometry. The runtime fingerprint also includes arena radius, private boss scale and showpiece scale. Configuration replacement invalidates the verification cache. Existing old fingerprints remain readable for recovery/settlement, but an old active fight whose runtime no longer matches holds for staff rather than silently retuning its measured damage reference. Do not claim that an earlier calibration remains valid for the changed policy. No production calibration values were installed.

Before enabling an occurrence, rehearse the exact build/modpack with its native actors, held items/forms, Pokemon hitboxes, battle UI, switches/faints/carryover, movement and reconnect, floor/slab/void/hazard cases, simultaneous starts/ends, outside-source fluid/fire/piston/explosion attempts, staff maintenance and protection plugins. Verify party clones and model visuals after the native battle starter repositions them; this stage does not prove every party species/model fits the technical corridor. Profile the bounded candidate/collision/floor scans and client load at 5/10/30/50 players. Confirm arrival layout, observer capacity and that unrelated raids/worlds behave normally. Automated models, compilation and concrete-target bytecode checks do not replace these rehearsals.
