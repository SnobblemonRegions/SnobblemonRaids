# Rift Crown — approved concept A

2026-11-27. Implemented and reviewed in the private native Minecraft client. No production deployment, version bump, commit or push.

## Presentation

Giratina's shaded gold crown and face sit above its name. Black wings, crimson tips, gold ribs and violet distortion seams form the health bar's silhouette. The live green/yellow/red health rail, damage echo, native Minecraft text, countdown and participation information are drawn separately. The imported transparent artwork has no baked text or health fill. It is preserved unchanged; runtime UV bounds omit its unused transparent margin. [Asset prompt and provenance](../worldboss-concepts/rift-crown-asset-2026-11-27.md).

Full effects adds sparse violet seam currents, crown highlights and eye glints; critical health uses crimson accents. Minimal and Off preserve the detailed static artwork and omit the animation. No global font, shader or vanilla texture replacement is installed. Frame filtering is nearest. The generated static frame is 2172 × 724; its UV crop is 2156 × 584. The full-size HUD is 360 × 96 GUI pixels, with a 189 × 6 live health rail and a 16-frame flair atlas. `tools/rift_crown_effects.py` reproduces the separate code-authored health/effect textures without changing the imported frame.

Narrow layouts protect 64 GUI pixels on each side for Cobblemon's party panel. The native 320-pixel GUI check exposed a real wing overlap; [the retained failing image](rift-crown-2026-11-27/before-small-gutter.png) documents it. The corrected layout scales the art to 192 pixels, keeps native text at 1×, and stacks health/status or opening-time/readiness captions. Full-size art remains unchanged. Other boss bars are placed below the actual reserved height.

The server-owned event UUID, native progress, six-second metadata expiry and native fallback are retained. Other mods' boss bars and their events remain available. This is an optional client presentation change; the dedicated-server artifact is byte-identical to the preceding review.

## Verification

- Full Windows CI-equivalent gate passed: 1,415 core and 30 companion tests, zero failures/errors/skips. Narrow-layout and caption regressions were observed failing before their fixes. Artifact checks cover dimensions, nearest filtering, no global font override, excluded development probe and client/server boundaries.
- Final Iris/Complementary radar graphical compatibility harness passed. This covers initialization/radar paths, not actual World Boss shader acceptance.
- Nine final native presentation captures passed ownership/fallback checks: gathering, full, damaged, critical, empty, coexistence, stale metadata fallback, effects Off and restored. PNGs were visually reviewed.
- Five additional viewport captures cover 320-pixel GUI width, Giratina III with a crowded status, gathering/readiness, coexistence and restoration to the original 640-pixel GUI width. The corrected wing clears the native party panel.
- Native pixel sampling found 6 distinct Full shimmer frames and one static Off frame. This verifies animation and the preference; it is not a timing benchmark.

Native capture commands: `validation/smoke/worldboss_hud_visuals.py`, `worldboss_rift_viewport.py`, and `worldboss_hud_flair.py --gui-scale 2`, each with `--rig <owned-private-rig> --output <probe-output>`. They require the excluded development probe and reward-free TEST scene. All simulated health, legacy names and fifty-fighter labels are client presentation fixtures, not server capacity, reward or calibration evidence. Logs: `build/rift-crown-final-{ci,graphics,native,viewport,motion}.log` in the workspace.

## Remaining acceptance

Final artistic approval, actual World Boss shader combinations and target-client CPU/GPU timing remain open. The private runtime uses cached Loader 0.19.5; the source pin remains 0.19.3. This livery is authored for Giratina; another seasonal boss needs its own artwork. No claim of production readiness, universal compatibility or absence of all bugs is made.

[Gathering](rift-crown-2026-11-27/hud-gathering.png), [critical](rift-crown-2026-11-27/hud-critical.png), [empty](rift-crown-2026-11-27/hud-empty.png), [small crowded layout](rift-crown-2026-11-27/rift-small-crowded.png), [small gathering](rift-crown-2026-11-27/rift-small-gathering.png), [motion evidence](rift-crown-2026-11-27/flair-native-result.json), [exact artifacts](rift-crown-2026-11-27/artifacts.json).
