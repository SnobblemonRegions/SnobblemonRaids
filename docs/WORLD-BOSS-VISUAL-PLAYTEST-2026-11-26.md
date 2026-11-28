# World Boss visual playtest â€” 2026-11-26

Implemented in the isolated latest-main integration checkout, not deployed to production.

## Result

Latest preview: `2026-11-26_17.40.51.png` shows the stronger animated membrane in daylight during an active native duel. The downloaded client pack exactly matches all four final animation textures, and the client/server/built Raids JAR hashes match. Private copies remain hidden; the shared Giratina is visible. Prior combat-damage measurements below are historical checks from the earlier visual builds, not newly repeated load/performance certification.

Visual revision after the plain-dome review: the membrane now has stronger animated violet currents, narrow scarlet tears, moving broken gold seals and travelling glints. The revised artwork uses stronger opacity only on narrow scarlet rifts, broken gold seals and bright glints. Across all frames, the ordinary panels average below 95/255 opacity, and fewer than 15% of nontransparent pixels exceed 160/255 opacity. This permits readable highlights without making the whole barrier opaque; the transient crossing flash is assessed separately. This retains the same four models, 24 animation frames and existing panel/entity count; no new per-tick animation logic was added.

The final dome update removes the hard-coded AscensionLib ender-eye panel IDs. Four owned, dynamically allocated PAPER models now render a clearer Distortion membrane with gold seams and crimson fractures. All twelve model/texture/animation resources are present and exact in the generated server pack. Polymer assigned distinct model IDs 2–5 in this fixture, while the old Halloween models remain intact. The real graphical client shows no ghost faces or green wisps. The crossing-flash asset is packaged and tested; a timed live crossing-flash screenshot was not captured.

* Giratina now has an owned BossBarSkins theme: gold frame, dark recess, animated crimson energy and a gold name gradient. No Halloween art was removed or replaced in AscensionLib.
* The core claims only its World Boss bar as `cobbleraids`. Its logical `BLUE/PROGRESS` skin is declared in `bossbarskins.json`. BossBarSkins remains optional; without it the bar uses native blue. No client-only classes or new hard dependency were introduced.
* `visual.duel_boss_invisible` defaults to true. This reuses CobbleRaids' existing infinite, particle-free invisibility effect and attribute-reset restoration hook. Explicit false in existing configuration is preserved; existing installations must set the field true themselves. The test rig explicitly enables it.
* The shared showpiece remains visible, and its shared pool still receives damage from the player's native duel. The existing no-glow zone rule is retained. Hiding copies does not move attack animations, sound origins, hitboxes or the native battle camera to the shared boss; those are separate presentation work.
* The test server view distance is 8 chunks; simulation distance remains 4. This is a private visual fixture choice, not a new 50-player performance certification.

## Missing model diagnosis

The installed Cobblemon 1.8.2 JAR has Giratina species data and cry but lacks Giratina model/poser/resolver assets. That caused the substitute doll in the supplied screenshots. Increasing view distance cannot supply missing assets.

The private test client now enables `private-giratina-models.zip`, containing twelve unmodified Giratina resource files extracted from the user's existing `mega_showdown-fabric-1.3.1+1.8.2+1.21.1-release.jar`. Source hash and available license/notice files are recorded inside that local pack. The extraction is not part of our source snapshot or release JAR. A production client pack still needs the correctly licensed model assets; installing the full Mega Showdown gameplay mod is not required for this private visual check.

## Verification

* A resource regression test first failed because the owned skin declaration was absent. It now verifies namespaced asset paths, native dimensions and animation metadata.
* The visibility regression first failed with the old visible-copy default. It now passes and separately preserves explicit false configuration through serialization.
* Final JUnit results: 1,394 core tests and 8 optional radar-client tests; zero failures, errors or skips.
* `validation/ci_local.sh`: all checks passed for `0.8.211-worldboss-playtest`, including 86 guarded injection points and 43 verified mixin targets.
* Native server `bossbarskins list`: protected pink and notched_20 belong to AscensionLib; `cobbleraids:worldboss_distortion` asks for BLUE and is assigned BLUE.
* Generated Polymer pack byte-for-byte matches our background, animated progress and metadata. AscensionLib's pink sprite is retained. The client downloaded the server pack and rendered it without a BossBarSkins client installation.
* Real graphical client: correct giant Giratina model; gold/crimson World Boss bar; simultaneous vanilla purple and haunted pink bars retain their distinct art. Temporary comparison bars were removed afterward.
* Active native duel: only the private owned copy has `minecraft:invisibility`, infinite duration and no particles/icon. The showpiece has movement-lock slowness without invisibility. This was checked after initial entity ticks and again after a combat turn.
* Earlier bar-build check: Glaceon used Blizzard; the battle advanced to turn 2 and the shared World Boss pool fell from 8k to 7.6k (95%). The private copy remained invisible. In the final dome build, the first Blizzard missed normally and the battle advanced to turn 2; a subsequent Freeze-Dry reduced the shared pool to 7,849/8,000 (98%), with one contributing fighter and the private copy still hidden.
* Screenshots in the private client's `screenshots` folder: `2026-11-26_17.14.04.png` (duel), `2026-11-26_17.15.14.png` (three themes), `2026-11-26_17.16.29.png` (earlier turn 2). The final independent-dome gathering view is `2026-11-26_17.25.21.png`; active combat at turn 3 is `2026-11-26_17.27.26.png`.

The graphical fixture actually uses Fabric Loader 0.19.5 from its pre-existing cached launcher, while the repository pin is 0.19.3. These observations are not a certification of the pinned production runtime. The unrelated Iris/radarSmoke graphical suite, current-build 50-player load/recovery certification, real reward delivery and recorded-voice signoff were not rerun or approved by this visual check. Existing Cobblemon startup warnings and missing-new-player-backup messages remain in fixture logs; no claim of an error-free overall mod stack is made.

## Private handoff

* Game address: `127.0.0.1:25741`; private RCON port 25742.
* Rig: `C:/Users/Facel/SnobblemonRaids/build/native-rehearsals/worldboss-rehearsal-8808a04257b254d9`; server PID 19212 at handoff.
* Client: `C:/Users/Facel/SnobblemonRaids/build/latest-mods-2026-11-26/client`; PID 17532 at handoff; offline identity WorldBossTester.
* Raids JAR SHA256: `3894ecded078a05a0925de3505605de0731f85e09b7e264875b2bdea3048282a`.
* Supplied BossBarSkins JAR SHA256: `e63ca276446c5403353cb1cacdc11b027f0de8a3544805c93b769418b2f986eb`.
* Earlier rigs `ca0b3644424a8d63`, `b6dc2d3b43aa49b9`, `0b448b1b81b01bf0` and `a405aa6b52650807` were gracefully stopped and retained with its test save. The new rig is a fresh disposable fixture, not a migration. `da468b72bb20c5b0` was prepared before the final visibility change and never launched.
* The active event is TEST-only: no live rewards or external outcome delivery. The client is intentionally left open for review. Verify recorded process ownership before controlling either process.

The approval service briefly prevented rig preparation because its usage limit blocked review. After its stated retry time had passed, review succeeded and the private rig was prepared normally.
