# Sovereign Seal crest depth repair — 2026-11-27

Artifact: `0.8.211-worldboss-playtest`, c3f787d073b6364896cf61f42656105c4f814894583b82a410c4e5c17a54dcf4256 `c3f787d073b6364896cf61f42656105c4f814894583b82a410c4e5c17a54dcf4`.

## Cause and repair

The crest contained overlapping horn stems/caps with shared front, back and top planes; coplanar shoulder/body surfaces; and overlapping trim/chest details. The stricter diagnostic found 21 overlapping or insufficiently separated same-facing face pairs after applying the element rotations.

Horn stems now end where their caps start. Shoulder backs are inset from the main body. Raised trim and the forehead inlay have deliberate depth separation, and the hidden red chest overlap under the gold plate is trimmed away. The front outline and all materials are retained. No render entities, particle density, combat rules or dependencies were added.

## Regression and validation

`SovereignSealMaterialTest.crestFacesHaveClearDepthSeparation` reads the actual crest JSON, applies native element rotations, projects parallel faces and clips their polygons to detect overlapping areas. Same-facing overlapping surfaces must have at least a quarter model pixel of separation (0.21875 blocks at the crest's scale). Adjacent opposite-facing internal joints are excluded.

The original model failed this test. The repaired model passes. All 1,419 tests passed with zero failures, errors or skips. Complete Windows CI-equivalent checks passed (`build/seal-zfight-ci.log`), and the graphical compatibility harness passed (`build/seal-zfight-graphics.log`, fixture `8ba01bdbc935f2b7`).

The private native fixture `2eb1924c04064835` loaded the corrected pack after byte comparison against the built assets. Front, oblique and rear views were checked with decorative layers disabled, followed by a full-effects view. The cap joins and trim remained visually stable during those observations. This is scoped visual verification, not a guarantee for every shader or camera configuration.

## Evidence and private preview

* [Repaired front view](z-fighting-2026-11-27/front.png)
* [Repaired rear view](z-fighting-2026-11-27/rear.png)

The private preview remains running with full effects and a crest-facing spectator camera. It uses sixteen-chunk visibility and a 600-second gathering window; an unstaffed TEST cancels when that window expires. Production configuration was not changed. The independent javaw Minecraft client was left alone.

Fifty-player performance and production-runtime acceptance remain open. No commit, push or production deployment was performed.
