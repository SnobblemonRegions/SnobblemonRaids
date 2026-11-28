# World Boss: The Concord Trials

Design for review — November 16, 2026.

The owner has delegated the remaining design choices for review as a complete package. This document separates **owner-confirmed rules**, **selected design choices**, **provisional tuning values**, and **verification prerequisites**. It does not claim that the proposed system is implemented, tested or production-ready.

Read this document first, then the [implementation specification](WORLD-BOSS-IMPLEMENTATION-SPEC.md). The [engineering audit](WORLD-BOSS-ENGINEERING-AUDIT-2026-11-16.md) preserves the 24 source findings, wider failure register and pause/queue regression scenarios. Where older proposals conflict, this document and the implementation specification define the current selected behavior; audit findings remain open until verified fixes close them.

Implementation has begun: see [implementation progress and verification](handoff/WORLD-BOSS-IMPLEMENTATION-PROGRESS.md) for what is actually coded, what passed, and what remains. The complete design below is not a claim of completed functionality.

## The experience

A world boss is a recurring adversary the community recognizes. Players gather at a permanent sanctuary, hear a named Pokemon challenge them, fight through short personal duels against one shared World Pool, help restore the sanctuary's wards, and witness a voiced defeat. Their victory improves the server and becomes part of its history.

Working name: **The Concord Trials**. Player promise: **A victory your whole server can see, benefit from, and remember.**

The experience must work for a player who reads little chat: enter the bubble, understand the current objective, battle automatically, see qualification progress, earn a clear reward, and return safely. Veterans get team-building depth and recognition; newcomers get useful ward work and attainable cosmetic progress. No battle-stat advantage or exclusive progression is reserved for damage leaders.

## Owner-confirmed rules

| Area | Confirmed decision |
| --- | --- |
| Server | One Minecraft server owns combat, rewards and legacy. |
| Attendance | Design for 30 typical participants and at most 50 participant slots. A visible overflow queue admits players when slots open. Capacity still needs proof. |
| Seasons | Staff activate each season manually; no date is set. Each season introduces a new boss identity starting at Awakening I. |
| Daily times | Combat begins at noon and 8 PM America/New_York, following DST. Strongest turnout is expected at 8 PM, not yet measured. |
| Preparation | Arena opens five minutes earlier, at 11:55 AM and 7:55 PM. The entrance scene precedes combat. |
| Active limit | Fifteen minutes including battles, breathers, rituals and transitions. Initial preparation, server downtime and recovery regroup are excluded. Expiry means escape, not awakening advancement. |
| Health | Snapshot the ready starting roster and lock maximum World Pool health for the encounter, including recovery. Later arrivals never add HP. |
| Minimum | At least five ready players at the scheduled start; otherwise skip without advancing the awakening. |
| Enrollment | Standing inside the bubble enrolls automatically. No signup or click-to-start. Available enrolled players enter battle automatically when combat is open. |
| Readiness | At least one Pokemon, no other battle, not a spectator and not paused. Fainted Pokemon count because admission heals them. Unavailable entrants remain enrolled without occupying slots or inflating the initial health/minimum. |
| Healing | Full HP/PP restoration, status clearing and faint revival immediately before the first duel and between later boss duels. |
| Re-entry | Automatic after a 15-second breather, while inside the bubble; suspended during ritual windows. |
| Pause | Finish the current duel normally, then suppress re-entry and release its slot. Contribution and earned qualification remain. |
| Pause persistence | For the current encounter, pause survives reconnect and bubble exit/re-entry until explicit Resume Battles. It does not carry to a different encounter. |
| Resume | Join the queue tail; admit when capacity is available without bypassing earlier eligible waiters. |
| Missed starts | A start missed while the server is offline is skipped. No automatic catch-up appearance. |
| Interrupted combat | Resume valid saved health/contributions after outages up to and including 30 minutes, with a two-minute regroup. Recreate duels; do not restore mid-turn. |
| Recovery timer | Pause remaining fight time throughout downtime and regrouping. Longer outages hold progress for staff review. Committed victories/rewards recover regardless of outage length. |
| Base rewards | Equal guaranteed base reward on victory for qualified players. Losing a personal duel can contribute. Presence, last hit and placement do not decide base access. Qualification survives pause/disconnect. |
| Qualification effort | Target roughly three minutes of meaningful activity. This is an effort/balance target, not an idle-time entitlement. |

## Selected choices for final review

These choices use the owner's delegated discretion. Numerical balance values are starting configurations to test, not measured results.

| Topic | Selected behavior |
| --- | --- |
| Spectating | A separate safe observation terrace outside the enrollment bubble; up to 25 nonparticipants admitted through the event warp. Gameplay spectators never consume the 50 participant slots. Measure 50 participants plus 25 observers. |
| Bubble | A true three-dimensional configured sphere matching its visible boundary, with dimension and safe-floor checks. One designated arena; no silent random-terrain fallback. |
| Slot fairness | FIFO among ready waiters. Busy/empty-party entrants have no ready-queue position until available. Ordinary breathers retain their existing slot; pause or leaving gives it up after safe duel completion. |
| Disconnect | Retain a currently active duel/slot for up to 30 seconds of grace; then finalize it safely and release. Disconnected waiters give up queue position. Contributions, qualification and pause intent survive. |
| Resume timing | Never shorten the 15-second cooldown from a completed duel. Queue wait and ritual time may satisfy the cooldown; time paused cannot grant priority. |
| Scheduling | Announce at 10 minutes, five minutes and one minute before combat. Warp unlocks at five minutes. During the final 15 seconds, play the entrance once for the arena. |
| Scheduler precision | Start on the first server tick at/after the local scheduled instant, with at most 10 seconds of ordinary tick-delay tolerance. No shortened preparation after boot: if the full preparation window cannot be supplied, skip that occurrence. |
| Recovery safeguards | Permit at most two automatic recoveries and 30 minutes of cumulative offline time per event. A longer single or cumulative outage, a third interruption, corrupt state or uncertain clock puts it on staff hold. |
| Staff-held event | Blocks a new event and season activation until resolved. Skip conflicting schedule slots with an explicit announcement. Recovery never overwrites an unfinished encounter. |
| Deadline equality | A gameplay action observed at or after the remaining active time reaches zero cannot produce a new victory. Earlier accepted actions remain valid; one server-thread sequence decides the terminal outcome. |
| Season activation | Stage and validate next content, then explicitly activate only with no active/held encounter and no uncommitted prior outcome. Already-owed rewards remain in the delivery ledger and do not disappear when definitions change. |
| Competitive rewards | No damage-placement currency bonus in the first release. Honor genuine combat and ward contributions through titles, journal distinctions and presentation. |
| Timed failure | Qualified players receive 25% of normal base Raid Points; no victory cosmetic progress, blessing or awakening advancement. |
| System cancellation | Preserve progress on hold first. If staff end an unrecoverable encounter, qualified players receive the normal base Raid Points as service compensation, but no victory-only progress or false legacy clear. |
| Entry healing | Applies only to admitted event duels. Waiting, pausing or walking through the bubble is not a general healing service. |
| Battle supplies | Disable trainer bag-item use during boss duels. Held-item behavior follows verified raid rules; don't invent item duplication/restoration. No rentals or party replacement in the first release. |
| Rewards UI | Account-owned claims/collection; offline-safe, no valuable floor drops and no inventory-dependent cosmetic delivery. |
| Main chase | A Worldheart giving supported Pokemon a boss-themed cosmetic and signature send-out. It is earned, account-bound and stat-neutral. |
| Voice | Authored, licensed recorded English performances and matching subtitles. No generated runtime speech. Custom client features are optional enhancements, never required for rewards or understanding. |

## A complete event

At 7:50 PM, a restrained announcement names the adversary and previews rewards. At 7:55 PM, the warp opens. The entrance terrace overlooks the showpiece and the wards. The panel shows the countdown, counter advice, Pause Battles, queue status and the qualification meter.

Entering the sphere enrolls the player. Preparation never starts a battle. Pause works before combat, so players can choose ward duty or make party changes. The screen distinguishes Enrolled, Waiting, Fighting, Recovering, Paused and Qualified; none is conflated with another.

At 7:59:45 PM, the wards darken and the boss speaks. The cinematic is brief and does not take the camera. At 8 PM, the first ready roster is frozen, the minimum is checked, HP is fixed and available players enter duels in a bounded admission wave. Queue time consumes no personal merit and never counts as inactivity misconduct.

Personal duels should last about 45-75 seconds for a prepared team, with a provisional 90-second duel limit. A loss still contributes damage. Ward duty is available during the admitted participant's breather/ritual time; it does not require a second party or a separate class. After safe cleanup the admitted Pokemon recover and the next duel is eligible after 15 seconds. The shared timer continues during breathers.

**No requirement to move, dodge or click an overworld object while the battle interface is open.** Pause never aborts a live duel. Leaving the bubble finishes that duel normally and cancels subsequent admission; staying inside while paused never auto-resumes.

Late arrivals can join until the last 60 seconds of active time. After that they may watch or help from safe presentation areas but receive no misleading qualification promise. Prior qualification is preserved regardless of current location or connection. Late participants don't increase HP; fast legitimate victories are allowed rather than held back by arbitrary invulnerability.

The win is committed when valid shared damage reaches zero. Remaining battles finish safely within their existing duel bounds, with no further World Pool/reward mutation. A finale plays for players who are free; the journal preserves its transcript and replay for those still battling. Do not open the next event while old duels are still being reconciled.

## Signature encounter: Vaelrath, the Fractured Sovereign

Selected season-one creative direction: **Vaelrath**, a Giratina bound beneath the sanctuary. The name/art/scripts are authored concepts to review, not assets already present.

Display:

> **VAELRATH**
>
> The Fractured Sovereign · Giratina
>
> **Third Awakening · III**

The same character returns throughout its season. Each legitimate community victory advances the next awakening once. Escape, staff cancellation, admin test, restart and replayed callbacks do not. Every monument/reward records the cleared awakening before the next number advances. A new season starts a new character; archived bosses keep their history.

Do not multiply combat stats by awakening number. Major authored chapters can introduce new voice lines or a visual change while the combat difficulty stays in tested tiers. Beyond bespoke early chapters, use reusable lines and an exact numeral on the title. An archived character cannot silently become the next seasonal identity; a future special return must retain its original identity and next awakening.

### Voice and scene brief

Use a distinct telepathic delivery for Vaelrath: controlled, ancient and increasingly unsettled by the trainers' cooperation.

First entrance:

> "You built your sanctuary upon my prison. Did you believe I would never wake?"

First defeat:

> "Remember this victory, little trainers. I will remember every wound."

Third awakening entrance:

> "Twice you have sealed this gate. Twice you have mistaken survival for victory."

A respectful defeat:

> "That bond... I had forgotten its strength."

Escape:

> "Your wards are failing. Next time, bring more than courage."

Do not attach the escape line to a server fault or a skipped appearance. Select outcome-specific recorded variants from actual persisted results. Avoid speaking player names without authored assets or claiming individual history from community history.

Entrance budget: 10-15 seconds within preparation. Defeat budget: 8-12 seconds within a 60-second optional celebration. Use timed subtitles, event voice mute, reduced effects and a transcript. Muting/skipping never alters combat timing or eligibility. Duck only event-owned music; do not stop unrelated sounds. Clients without the correct pack receive readable text and simple vanilla cues.

### Ward windows: cooperation without a deadlock

At 70% and 35% remaining HP, queue a 45-second ward window. **This replaces the older mandatory global drain and hard HP-barrier proposal.** Active duels continue applying and receiving credit for legitimate shared damage. No hits are discarded merely for reaching a phase threshold.

Automatic new duel admission pauses during a ward window, as agreed. Players already battling help through validated combat actions; admitted players between duels can carry a seal fragment from a nearby cache and channel at a marked pylon. Battle actions and overworld support both charge the same visible ward goal. Only players holding a participant slot earn new combat or ward credit. Queued, paused and observer players retain earned progress but do not create an extra uncapped support group. They receive a clear Resume/waiting message instead of a silent failed interaction.

Ward windows don't overlap. If the second threshold is crossed during the first, its window follows at the next available phase boundary. If the boss dies first, the committed victory takes precedence and remaining ritual cues are cancelled. No artificial waiting to stage a ceremony after the boss has already been defeated.

Initial tuning: for N ready local participants at window opening, require q = clamp(ceil(N / 10), 1, 5) distinct contributors and 3q validated ward units. A meaningful combat turn grants one unit, a completed relay grants three, and a focus action grants one; cap each player at three units per window. Relay/focus interactions require server-validated range, route/token and time, not repeated clicks. Quorum/goal are frozen for the window.

Success suppresses the next act's brief boss modifier. Failure applies a bounded, tested modifier to newly started duels for 90 seconds; no pool healing, progress deletion or unavoidable wipe. No modifiers are retroactively applied to an in-progress Showdown battle. If nobody can act, the window expires normally, never stalls the event.

This makes the sanctuary visibly respond to cooperation without making one slow or disconnected duel hold everyone hostage.

## Qualification and rewards

Confirmed principles: equal base victory reward, meaningful activity, losing-duel damage counts, no idle eligibility, and irrevocable earned qualification.

Selected starting qualification model: **100 Merit**, targeting roughly three minutes of normal activity. Merit is event evidence, not a currency and not proportional to other players' damage.

- Ten Merit per distinct validated battle turn with positive applied shared damage.
- A successful tactical turn can grant ten instead for a first useful support action of that kind in the duel; repeated self-buff/heal loops do not. Support kinds must be explicitly instrumented; unverified actions grant nothing.
- At most ten Merit per turn and 50 per duel. No requirement to finish/win that duel before its accepted actions count.
- A validated ward relay grants 30 Merit and its distinct focus action grants 20, once each per window. Two fully completed ward windows can qualify a novice without meaningful combat damage.
- Lock qualification as soon as the threshold is reached, including inside the final damaging callback before victory is adjudicated. Replays cannot grant the same Merit twice.
- No time-only credit for a battle screen, queue, pause, movement or proximity. Rate limits and duplicate-action receipts are event-scoped.
- Show "Qualified — reward secured on victory" only after qualification is durably recorded. If persistence is pending, say "Qualification saving."
- Values remain provisional. Test ten valid turns and two-window novice access on real clients; adjust action values rather than pretending three minutes guarantees qualification.

Initial economy configuration: **200 Raid Points per qualified victory**, 50 for a legitimate timed failure, 200 for qualified system-cancellation compensation. No separate ordinary loot lottery at launch. This keeps the chase focused on the exclusive collection and avoids inventing another spendable currency. The 200 value matches the inspected world boss default, but actual shop prices and aggregate issuance must be validated.

At 30 qualifiers and two daily wins, this emits 12,000 base points/day; at 50, 20,000/day, before blessings. These are examples for those qualifier/win counts, not actual turnout data or a hard daily issuance cap: slot turnover can produce more unique qualifiers than simultaneous slots. Monitor total unique qualifiers and account issuance separately; never withhold an earned entitlement to make the estimate appear correct. Staff tests and replayed callbacks never mint live rewards.

Victory grants a server-wide **+10% Raid Points blessing on ordinary raid wins for 60 real-world minutes**. Determine eligibility at the ordinary raid's committed victory, round bonus points down, and never boost world boss rewards, shop transactions or the blessing itself. Repeated boss victories extend expiry with a 120-minute horizon cap, using committed event timestamps. Replay cannot refresh it. Everyone benefits, including people who missed the boss.

If the integer balance cap prevents full credit, keep the unpaid remainder as a visible entitlement rather than silently discard it. Never clear a claim merely because a UI message was sent.

## The chase: Worldheart of the Distortion

The selected first chase is an account-owned cosmetic relic: a collectible Worldheart that can be attuned to one supported Pokemon at a time. Its centerpiece is a recognizable, short send-out effect; the persistent look stays tasteful and does not flood a crowded arena with particles.

- Each qualified natural victory grants one character-specific mastery step.
- The base Worldheart is guaranteed at 12 steps. Each earlier eligible victory has a provisional 2% early-unlock roll, evaluated once and persisted with the outcome.
- After an early unlock, steps still accumulate toward style upgrades. Initial style milestones: 24 and 40 total qualified victories against that boss. No extra damage or utility stats.
- No paid acquisition, trade, dropping, breeding inheritance or transfer with a traded Pokemon. The authoritative object is an account entitlement, not a physical item anyone can copy.
- Attunement changes presentation only and never replaces the Pokemon or its native shiny/form/gender data. Evolution/trade/unsupported species detach safely with the entitlement preserved.
- Initial artwork target: a small supported set, such as Lucario, Charizard, Gengar, Eevee, Gardevoir and Dragonite. Each species requires authored assets and real-client verification; this is not a compatibility claim.
- Show item name, boss, cleared awakening/date and earning trainer in collection provenance.
- Duplicates cannot consume/erase progress or grant a second base entitlement. Once owned, no redundant early-unlock rolls.
- Earned appearances never expire at season change. An unfinished archived base Worldheart can be selected as a Legacy Attunement and progressed on future qualified world boss victories, one archived project at a time alongside current-season progress. No rare early roll for an archived project. This avoids a permanent missed-season lockout.
- Advanced styles remain available through that boss's archived mastery project. Season-one history honors can remain historical without providing exclusive power.

## History, access and operations

Keep compact immutable clear records permanently and materialize journal pages on demand. The monument shows the latest natural clear, awakening, participant/qualifier count and actual honors. Full qualifying rosters are paginated; no top-three-online-only history. Store UUID identity and a safe display-name snapshot; never use names as economic keys.

A visitor can inspect past bosses, hear/replay their authored scenes locally and see the next daily appearance. Replays are presentation only: no rewards, blessing, advancement or scene effects outside the viewing area.

Persist safe return origins when players warp in. Explicit Return remains available outside combat, during pause, after failure and after victory. Validate the original dimension and current destination safety; otherwise use a configured verified safe spawn. An event ending cannot strand someone or clear a pending return.

Staff can stage, preflight, activate, inspect, hold, resume, cancel and retry settlement. Test wins default to isolated dry-run outcomes that cannot pay live rewards or advance legacy. Natural and admin outcomes have explicit provenance. Permission level 2 is the initial operator baseline, with audit records for control actions.

Future dates are not required to stage a season. Activation validates its character, arena, definition snapshots, scenes, cosmetic registry, schedule and settlement health. It cannot silently erase old progress.

## Engineering and delivery

The implementation must provide one owner of event transitions and participant slots, one verified route from encounter damage to its owning event, a replayable durable outcome record, idempotent per-player settlement, and presentation that never controls economic correctness. Details and all selected Q01-Q18 resolutions are in the [implementation specification](WORLD-BOSS-IMPLEMENTATION-SPEC.md).

Retain the existing owned-encounter architecture. First repair reward durability, stale callback handling, cleanup ownership, leave/pause behavior and exact numeric validation. Do not create a generic distributed event platform for this single server.

Ship in internal milestones: correctness foundation; playable automatic loop; one polished voiced boss and ward mechanic; Worldheart collection; capacity/chaos verification; then invited-player review. The public launch requires the full chosen experience, not a half-working reward prototype.

No production deployment, voice commissioning, purchase or external publication is authorized by this design work.

## Research context

First-party pages establish advertised mechanics, not player satisfaction or proof of uniqueness:

| Server | Relevant advertised behavior |
| --- | --- |
| [Complex](https://www.mc-complex.com/forums/pixelmon/wiki-page/76/cobblemon-world-bosses-raid-times-and-rewards-2026/) | Personal singles victories feed shared HP, recurring timed bosses, damage-based reward brackets and saved offline rewards. |
| [Cobble Asia](https://www.cobbleasia.net/features/raids.html) | Shared HP, six tiers, shield/type mechanics and seasonal benefits. |
| [Conquest](https://cobblemonconquest.com/changelog/2026/11/04/53/feature-rework-and-new-additons) | Scheduled bosses with damage-related rewards and Event Points. |
| [Park Ball Gang](https://parkballgang.com/wiki/raids/raids) | Scheduled raids, individual arenas, participation scaling, catch phase and participant rewards. |
| [TalonMC](https://www.talonmc.net/blog/best-cobblemon-servers/) | Scheduled raids, legendary slots and streak recognition. |

Research checked November 16, 2026. Talon's richer raid-search snippet redirected to a comparison page; unsupported detailed mechanics were excluded. The selected differentiation is named seasonal adversaries, community memory, accessible support and a visible personal chase. Its success must be established through playtesting.

## Review and release status

**Policy design is consolidated for owner review. Runtime correctness and capacity are not established.**

Required factual inputs remain: final arena build/dimension/coordinates and safe spawn; deployed server/client/mod/pack manifest; authored/licensed voice/art assets; actual shop/economy values; filesystem durability evidence; and measured combat/client performance. These are verification inputs, not unanswered preferences.

The [audit](WORLD-BOSS-ENGINEERING-AUDIT-2026-11-16.md) remains an active risk register. No source finding is closed merely because this document chooses a rule. Tests must prove the selected behavior, including process-kill recovery, queue/pause adversarial sequences, final-hit qualification and 50 fighters plus 25 observers.
