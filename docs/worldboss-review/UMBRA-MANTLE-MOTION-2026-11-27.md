# Umbra Mantle wing motion — 2026-11-27

Implemented and checked in the private TEST client. No production deployment.

The old broad body envelope could fold into itself and flip its frame toward the camera. It has been replaced by thin, crossed shadow/violet/scarlet wisps along the animated wings. Sixteen authored attachment points follow the inspected Giratina's visible feather contour, rather than the transparent rectangular bounds of its wing planes. The underlying Pokemon animation asset was not modified.

The attachment captures the real posed model subtree during the owned showpiece's world render. Public Cobblemon accessors are cached through reflection, preserving the optional companion's dependency boundary. GUI rendering and unowned/duel entities are excluded. Unsupported bone hierarchies omit the wing wisps. A replacement seasonal model needs a reviewed profile; matching bone names alone cannot validate a changed texture atlas.

Geometry uses a parallel-transport frame independent of camera direction, C1 path resampling, curvature-limited widths and bounded width gradients. Shoulder roots remain exact; tip inertia increases to 100 ms. Elapsed-time smoothing is tested at 30 and 144 FPS. Model/owner changes, teleports and long frame gaps reset history. Disconnect/world changes clear attachment state.

The owned model's render bounds now include its authored 9-block upper extent before scale. The native scale-4 fixture reports upper Y=101 and collision width/height=1. Effects Off captures no wing anchors while retaining model visibility.

## Verification

- Complete Windows CI-equivalent gate passed: 1,413 core + 21 companion tests, zero failures/errors/skips. Artifact boundaries, guarded mixins, API and Showdown checks passed (`build/mantle-contour-ci.log`).
- Failing geometry regressions reproduced camera-axis flips, excessive width on tight bends, and same-timestamp root lag before their fixes. Those tests now pass.
- Graphical radar compatibility harness passed with the new attachment mixins and Iris/Complementary (`build/mantle-smooth-graphics.log`); this is initialization/radar coverage, not actual Veil shader acceptance.
- Final native scene: all 16 anchors captured across 12 frames; maximum displacement 4.2183 blocks, largest sampled step 1.7714 blocks in the stationary scale-4 fixture. Start/middle/end captures were visually inspected. This detects frozen poses and large discontinuities; it does not measure frame-time smoothness or replace viewing the animation.
- Native near/far/105-block observer regression passed with Full and Off. Off reports `captured=0`, Full reports `captured=16`; all report `render=true` (`build/mantle-contour-visibility.log`, `build/mantle-contour-off.log`).
- No attachment, Veil-renderer or decoding errors were found in the reviewed client log. Unrelated existing resource warnings are not treated as renderer failures.

Retained native check: `python validation/smoke/worldboss_wing_motion.py --rig <owned-private-rig> --output <development-probe-output>`. Requires the development-only capture probe, which is excluded from the player companion.

## Cost and remaining acceptance

Full emits at most 2,950 quads per scene frame; Minimal 942; Off zero. Wing capture evaluates the cached required joint subtree once per owned model draw, with bounded streams and samples. These counts do not establish CPU/GPU performance approval. Actual Veil shader combinations, target-client measurements, and final artistic approval remain open. The private runtime uses cached Loader 0.19.5; source pin remains 0.19.3.

Native evidence and exact artifact hashes: [motion samples](mantle-motion-2026-11-27/wing-motion-result.json), [artifacts](mantle-motion-2026-11-27/artifacts.json), [start](mantle-motion-2026-11-27/wing-cycle-start.png), [middle](mantle-motion-2026-11-27/wing-cycle-middle.png), [end](mantle-motion-2026-11-27/wing-cycle-end.png).
