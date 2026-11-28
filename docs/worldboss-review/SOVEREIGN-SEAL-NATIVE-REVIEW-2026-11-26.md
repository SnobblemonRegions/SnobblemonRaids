# Sovereign Seal native review — 2026-11-26

Artifact: `0.8.211-worldboss-playtest`, f97654cd09bb0c193ca5eebd6f100f3070b69852de7444cdbaae71a1860b8b82256 `f97654cd09bb0c193ca5eebd6f100f3070b69852de7444cdbaae71a1860b8b82`.

## Results

The native Minecraft client loaded the generated pack and rendered the rotating band and bobbing crest. Gold motes and scarlet wisps were visible in full mode near the crest (about 35 blocks) and from the arena-floor viewpoint (about 75 blocks). They remain fine sparks at long distance, even at vanilla dust's maximum scale.

Minimal mode visibly reduced emissions. Off mode stopped them; existing particles cleared during the six-second observation interval while the seal continued moving. Explicit `/cr worldboss stop` removed the band, crest and boss, and no particles remained after the observation interval. A subsequent TEST start recreated the scene successfully. Earlier insufficient-ready cancellation also cleared the scene.

Testing found that the original 128-block particle cutoff excluded the far side of the 96-block arena from its observer platform. The final artifact uses 256 blocks. A regression test sweeps the full animation cycle against that platform and failed with the old cutoff. All 1,414 unit tests and complete Windows CI-equivalent checks passed (`build/seal-range-ci.log`). The graphical integration harness passed (`build/seal-range-graphics.log`, fixture `7ab7f7482b360755`).

Eight-chunk effective render distance obscured the distant crest and band with fog. The final private test used sixteen chunks on both server and client; the far crest and band were visible from the observer platform. Native `Options.getEffectiveRenderDistance` was checked: it uses the smaller server/client distance. Far-away motes still look subtle. Aesthetic approval of the complete Sovereign Seal remains open.

## Private fixture left running

`build/native-rehearsals/worldboss-rehearsal-ea1064a95e993488`, loopback game/RCON ports 25741/25742. WorldBossTester is a spectator with full scene effects, looking from the arena floor toward the crest. The reward-suppressed TEST was restarted after cleanup verification. Its private gathering window is 600 seconds; it will cancel without a ready fighter when that timer expires.

The private launcher now accepts `-GatheringSeconds 600 -ViewDistance 16`. Defaults remain 45 seconds and eight chunks. These options affect disposable fixtures and the private test client. Production configuration was not changed. The separate javaw Minecraft client was left alone.

## Evidence

* [Full effects near crest](seal-full-close-native.png)
* [Minimal effects](seal-minimal-close-native.png)
* [Effects off](seal-off-close-native.png)
* [Stop cleanup](seal-cleanup-final-native.png)
* [Observer platform at sixteen chunks](seal-observer-16-native.png)
* [Final arena-floor view](seal-floor-final-native.png)

Screenshots are unedited Minecraft captures. Animation means band placement differs between captures. Movement warnings during the review came from deliberate spectator camera teleports.

## Remaining verification

No fifty-player performance approval, reconnect/dimension cleanup check, native seal shader comparison, or production-runtime acceptance is claimed. The private runtime uses cached Fabric Loader 0.19.5; the source pin remains 0.19.3. Sixteen-chunk operation with fifty players still requires a load test before choosing production operating settings.
