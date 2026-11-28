Latest build: [theatre and material pass](SOVEREIGN-SEAL-THEATRE-MATERIALS-2026-11-27.md).

Latest result: [native review and remaining limits](SOVEREIGN-SEAL-NATIVE-REVIEW-2026-11-26.md).

# Sovereign Seal particle pass

The TEST-only Sovereign Seal now emits gold motes along its thirteen intact band sections and scarlet wisps around the crest. Particle positions follow the same item-display transformations, including Minecraft's native Y half-turn. A six-second pulse varies their size without flashing the screen or adding sounds.

Full scene effects use sixteen emission points every sixteen game ticks, with three dust particles per point. Minimal uses four points; off sends no particles. Points farther than 256 blocks from a viewer are skipped. The whole scene is capped at 800 particle packets per emission, with a rotating first viewer if attendance exceeds that budget. These are local limits for this seal, not a measured server-wide performance guarantee.

The pass adds no entities, assets, dependencies or production activation path. It remains behind the prototype JVM flag and TEST encounter gate. Existing scene preferences apply through `/cr worldboss scene effects full|minimal|off`.

Validation and native appearance are recorded after the build below. The earlier running preview contains the previous artifact until restarted with the newly built JAR.

Final unit results: 1,413 tests, zero failures, errors or skips. Complete Windows CI-equivalent checks passed (`build/seal-effects-ci.log`). Regression coverage includes native facing, motion looping, bounded density/positions, long uptimes and non-aligned border-update timing.

Artifact SHA256: `27d829f39d14255f5b1aaf9882832a336059bd1dda768f7733c2166041e75ef3`.

Graphical integration: `GRAPHICAL HOLOGRAM: PASS` with Iris/Complementary in fresh fixture `acfff72ae747c599` (`build/seal-effects-graphics.log`). The first attempt joined successfully but timed out at stage 0 because the map probe expected Raid Radar while the server defaulted to Raidio; the successful run used the documented `-Dcobbleraids.raidio=off` setting. The harness stopped its own client and server. This validates the existing map companion path, not the Sovereign particles themselves. Native particle appearance, preference changes during a seal scene, cleanup and load performance remain unverified.

## Distance visibility revision

Player feedback from the native preview found the initial dust too small at distance. Dust now pulses between scale 3 and 4, up from 0.7–1.3, and cluster spread is 0.8 blocks horizontally / 1 block vertically instead of 0.18 / 0.24. The native 1.21.1 `ScalableParticleOptionsBase` constructor was checked: four is its maximum supported scale. Counts, sixteen-tick cadence, distance culling and packet budget remain unchanged. Larger particles may add client overdraw; no new load-performance approval is claimed.

The regression test failed against the old size and passed with the new range. Full tests and CI-equivalent checks pass (`build/seal-distance-ci.log`). Current artifact SHA256: `efb99694c88051c2c3023b8c4152c9fd5b245de83334a0332cf3e5f0df3d8616`. The running private preview `04b45dece5e56c16` has the preceding particle build; it needs a restart before the larger effects can be assessed visually.

Distance-revision graphical gate: `GRAPHICAL HOLOGRAM: PASS` (`build/seal-distance-graphics.log`, disposable fixture `3f148d60d056f7a4`). All 1,413 tests passed with zero failures, errors or skips. This checks client integration, not the revised seal appearance; distance visibility is still awaiting native review after the preview restarts.

Final native-tested artifact SHA256: `f97654cd09bb0c193ca5eebd6f100f3070b69852de7444cdbaae71a1860b8b82`. The observer coverage fix, full/minimal/off switching, stop cleanup and recreation were checked. Current automated count: 1,414 passing tests.
