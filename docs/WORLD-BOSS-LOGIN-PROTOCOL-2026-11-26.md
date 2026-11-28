# World boss native protocol readiness — November 26

Continues [the cold-login evidence](WORLD-BOSS-COLD-LOGIN-2026-11-26.md).
Nothing deployed; performance approval remains pending native validation.

## CPU/wall diagnosis

Gameplay baseline `7db4e3ee6099f7bea3b7156dd46be13dbd564aac7647579ec89e992197243853`
and probe `e4dab259de63efa576c6219f6f3b7112c73cba11ec397cbf05f3046dd1b1ea60`
run on C: in `6bd7749dccdc486d`. Evidence `pilot-1795695607293171300`, recording
`wbperf-fighters50-1a43067b-150a-40c2-9662-787b1fb03ee2.json`. Every one of the
5,076 complete wall-time ticks has an available, ID-matched Main CPU sample.
Windows CPU accounting is quantized to 15.625 ms; it is diagnostic and does not
replace wall time or relax gates. The CPU probe changes no gameplay code.

Cold login FAILS: 353 ticks, p95 37.5173, p99 219.7719, max body 851.0853,
max start interval 895.1564 ms. All 51 clients remain. Initialization, gathering,
combat and staff recovery pass the unchanged gates, and all native functional
checks pass. Profile/POI fixture: 51 healthy prepared/opened/used/released handles,
51 persisted profiles, 210 natural Pokemon, 169 successful POI publications.

| Cold tick | Wall ms | Main CPU ms | Captured work |
| --- | ---: | ---: | --- |
| 876 | 219.7719 | 218.750 | Party-store MoLang function initialization |
| 877 | 156.9220 | 109.375 | Native player tick lambda linkage |
| 892 | 851.0853 | 31.250 | Netty promise wait during outbound protocol setup |
| 902 | 362.3330 | 31.250 | Same Netty wait |
| 906 | 187.7850 | 31.250 | Same Netty wait |
| 925 | 394.7770 | 15.625 | Same Netty wait |

Repeated samples in the largest tick show DefaultPromise.awaitUninterruptibly →
Connection.syncAfterConfigurationChange → setupOutboundProtocol. The old empty
event-loop marker completed before the actual later protocol write was submitted;
it therefore did not prove that the native protocol change could finish without
blocking Main. GC observations do not explain that repeated explicit promise wait.
There is also real first-use CPU work; neither sole CPU cost nor solely slow
storage explains all remaining cold ticks. Client-startup contention is not yet
isolated and must not be presented as established causation.

## Actual native protocol preparation

Candidate gameplay `05702042194a2fb7c1f13af129684c0ba58c502bf706efe67d588b4d8f586bfe`.
The final empty JoinWorldTask can prepare the real native clientbound PLAY protocol
on the channel's event loop. Its completion joins directory/profile readiness;
Main never waits on that future. The original configuration-finished handler then
runs once on Main, reusing the exact prepared binding and consuming the already-
completed switch once. Native duplicate-login/permission checks, player creation,
join callbacks and remaining admission work retain their original Main execution.
No player/Pokemon is fabricated and no native handler is replayed.

Scope is conservative: exact native connection/listener types, final empty join
task, unchanged frozen-registry identity, channel-owned pipeline executors and a
single native outbound-method wrapper. Other wrappers, subclasses or delegated
executors retain the native path and are reported as fallbacks. Task/registry or
binding changes fail before actor admission. Closed/failed channels cannot release
the continuation. Native Connection.setupOutboundProtocol performs its own codec,
bundler, flow and failure handling on its event loop; no pipeline is hand-written.
Compatibility reflection/mapping is initialized during startup, not first login.

Seven regression tests cover real-setup completion versus an empty marker,
event-loop execution, failure/rejection, exact binding/one-shot consumption,
wrong-thread access, late completion across server resets and extra-wrapper
fallback. Existing queue/resource tests remain. The pilot additionally requires
exact queued/prepared/consumed counts with zero protocol failures/fallbacks.
Root/client 1,185, probe 22 and Python 45 tests pass; packaged guards cover 84
points and shadow validation covers 42 targets/14,247 dependency classes.

Rig `07f3a997995527b6` was stopped before any clients or timing after moving the
compatibility preflight to startup. It has no performance verdict. Fresh native
validation is starting in C: rig `a763ed33152fc2ce`; no approval yet.

The small native trial (`pilot-1795697775841140600`, recording
`wbperf-fighters5-e27dd21a-3e18-4713-904e-0b7150e4a355.json`) confirms six
queued/prepared/consumed protocol switches, zero failures/fallbacks, six exact-once
Main admissions and six healthy profile handles with persisted profiles. POI,
native command and advancement-listener checks pass. Cold gate still FAILS:
380 ticks, p95 12.0455, p99 50.624899, max body 231.481801, interval 279.5568 ms.
The body meets 250 ms, but the start interval does not; no early rows are omitted.
The process is stopped. Fresh full peak validation of the identical artifact is
completed in `790f7519bc2afb62`, requiring all 51 actual switch completions.

## Full peak protocol candidate result

Gameplay `05702042194a2fb7c1f13af129684c0ba58c502bf706efe67d588b4d8f586bfe`,
probe `e4dab259de63efa576c6219f6f3b7112c73cba11ec397cbf05f3046dd1b1ea60`.
Evidence: `pilot-1795698032632042600`, recording
`wbperf-fighters50-e19884fe-2f4f-4991-829a-0ff1b093655d.json`.

| Phase | Ticks | p95 ms | p99 ms | Max body ms | Max start interval ms | Gate |
|---|---:|---:|---:|---:|---:|---|
| Cold login | 147 | 104.7811 | 231.0498 | 316.8044 | 338.5850 | FAIL |
| Initialization | 1865 | 13.4737 | 17.4955 | 62.6280 | 98.2262 | PASS |
| Gathering | 1271 | 15.0233 | 22.2813 | 54.2341 | 58.5149 | PASS |
| Combat | 687 | 15.5116 | 20.6567 | 64.0946 | 128.3065 | PASS |
| Recovery | 217 | 10.3868 | 12.6284 | 14.5105 | 51.2976 | PASS |

All early zero-player cold rows remain. Actual protocol setup is 51 queued,
prepared and consumed, zero failures/fallbacks; profile handles all released and
51 profiles persisted. Natural spawning remains enabled (237 wild Pokemon),
271 POI read-ahead publications/hits, no failures/skipped reads. All 153 player
and advancement writes finish. Native command/listener fixtures, ordinary
compatibility (19) and showpiece re-entry (9) pass. Server stopped; post-stop
file audit finds 142 journal frames, 938,265 bytes, matching external head.
This audit is not a power-loss rehearsal.

No captured stall now contains the native Netty promise wait. The slowest cold
tick spends 250 ms CPU of 316.8044 ms wall in first-use EntityMoLangFunctions
lambda linkage. Other cold samples show WorldMoLangFunctions binding, native
player-data fallback deletion, chunk encoding and PokemonStoreMoLangFunctions.
Absence in bounded samples does not prove every wait is eliminated.

## Next measured change: local party/world function binding

Candidate `f4aadfb9d7573ad79d98167509261f6257f16c268d79d6a33fc9535aeab76bee`
prepares two further sampled binding factories at startup. Audited native 1.8.2
PlayerPartyStore/PartyStore/PokemonStore constructors allocate only local UUIDs,
empty slots/maps and an observable. A local empty DTO is discarded without
initialize, registration, owner lookup, file access or function execution.
Both PokemonStoreMoLangFunctions and PartyMoLangFunctions must have no custom
providers; otherwise the entire preparation is skipped. World bindings use the
actual overworld and likewise skip custom providers; no closures execute.
Entity/player binding is deliberately not warmed against a fabricated actor.

Three targeted tests prove native DTO preparation needs no running server and
that custom store/party providers are never invoked. Root/client 1,188 and Python
45 tests pass; mixin guards remain 84 points/42 targets. Fresh native trial starts
in C: rig `94f5897d8e52562e`. No performance approval is inferred from unit tests.

## Party/world candidate native result

Evidence `pilot-1795698639261998500`, recording
`wbperf-fighters50-4ef61357-fd4b-43a7-842f-39d66d684346.json`, same probe as above.
Native startup confirms partyFunctions/worldFunctions=true (669 ms total startup
preparation). No startup work is omitted from recording by relabeling login rows.

| Phase | Ticks | p95 ms | p99 ms | Max body ms | Max start interval ms | Gate |
|---|---:|---:|---:|---:|---:|---|
| Cold login | 185 | 57.6241 | 283.007701 | 495.8611 | 511.9711 | FAIL |
| Initialization | 1894 | 16.5956 | 20.9741 | 101.1381 | 101.2990 | PASS |
| Gathering | 1267 | 15.941899 | 23.9158 | 58.780901 | 58.914601 | PASS |
| Combat | 685 | 15.300399 | 18.555999 | 27.1503 | 65.219699 | PASS |
| Recovery | 216 | 10.7958 | 12.1393 | 16.7247 | 62.464899 | PASS |

All 51 native admissions/protocol switches complete once without failures or
fallbacks; all prepared profile handles are used/released and profiles persist.
All 102 measured player/advancement writes drain. Natural spawning remains on
(201 wild Pokemon), POI fixture passes 229 publications/hits. Native command and
listener fixtures, ordinary compatibility (19) and showpiece re-entry (9) pass.
The server is stopped and its PID is absent. Journal audit: 108 frames, 662,090
bytes, external head matches. Small protocol-only rig audit: one 480-byte frame,
matching head. These are file-consistency audits, not crash/power-loss coverage.

Cold diagnosis remains open, without claiming this candidate improved the max:

* First tick 700: 495.8611 ms wall / 203.125 ms CPU. Samples catch native player
  display-name lambda linkage and class definition from
  PokedexManager.scheduleFullSyncFromStores. Party/world binding no longer appears
  in the bounded stall samples, which alone cannot prove absence of all cost.
* Tick 740: 283.007701 ms wall / 31.25 ms CPU, now specifically waiting in native
  Connection.setupInboundProtocol, called by PlayerList.placeNewPlayer.
* Tick 701: 246.860699 ms wall / 62.5 ms CPU while native chunk sending executes.

The outbound fix does not cover this separate inbound wait. Audited native
setupInboundProtocol validates and assigns the actual listener on Main, then
writes an InboundConfigurationTask and waits. UnconfiguredPipelineHandler's
native inbound task also sets autoRead=true. Therefore simply preparing the
inbound task before the player listener exists would resume gameplay decoding
too early. That shortcut is not implemented. A safe continuation boundary (or a
verified alternative preserving listener publication, ordering and failures)
needs a native regression before altering this path. Co-located load-generator
contention is still a hypothesis; wall-minus-CPU is not proof of its cause.

Overall approval remains blocked. No current-artifact crash/power-loss run, live
deployment, balance calibration or graphical-client performance signoff is
claimed. Existing 250 ms limits and all cold rows remain unchanged.
