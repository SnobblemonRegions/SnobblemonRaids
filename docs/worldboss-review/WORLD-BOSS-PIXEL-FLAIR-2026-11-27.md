# World Boss pixel armour and flair

Superseded by [Rift Crown, approved concept A](WORLD-BOSS-RIFT-CROWN-2026-11-27.md). This document preserves the prior card review.

2026-11-27. Implemented and reviewed in the private native client. No production deployment, version bump, commit or push.

## Experience

The compact Cobblemon-style battle card now has Giratina-inspired pixel armour: shaded slate metal, stepped gold corner brackets, small crimson-tipped fins, violet seams and fasteners. The portrait-sized badge contains an original winged raid-ball crest and crown. A gold native-pixel boss name and muted crimson World Boss tag make the hierarchy stronger. Font size, the 300 x 44 footprint and the unobstructed text/HP layout remain intact.

Full effects adds one sparse overlay draw: a slow violet current along the upper channel, orbiting badge glints and small highlights on the armour. At critical health those overlay colours become crimson. All 16 frames are authored pixel art with nearest filtering; there is no global shader, blurred glow or font replacement. The existing damage trail now has a pale metal highlight. The green/yellow/red health fill and actual server-owned health rules are unchanged.

Minimal and Off keep the detailed static armour and skip the animated overlay. They also retain the existing reduced-motion behaviour for the damage echo. The static livery is visible even with effects disabled. The overlay uses an elapsed-time frame index and remains within the authored atlas for every timestamp; it does not add server particles or packets.

## Verification

- Full Windows CI-equivalent gate: PASS; 1,415 core + 27 companion tests, zero failures/errors/skips (`build/cobblemon-flair-final-ci.log`). The animation preference/wrap regression was observed failing before implementation.
- Artifact validation checks nearest filtering, no font override, exact sprite dimensions (300 x 44 card, 250 x 6 health strips, 300 x 704 overlay atlas) and client/server boundaries. The final validator was rerun successfully.
- Iris/Complementary radar graphical compatibility harness: PASS (`build/cobblemon-flair-final-graphics.log`). This is initialization/radar coverage, not actual World Boss shader or performance approval.
- Eight final native presentation cases passed ownership/fallback checks and their PNGs were visually reviewed (`build/cobblemon-flair-final-native.log`). Health snapshots remain development-only client presentation fixtures, not server combat, reward or calibration evidence.
- Native pixel sampling confirms the opaque shimmer channel changes in Full and remains static in Off (`build/cobblemon-flair-motion.log`, retained frame hashes below). The test uses the private fixture's GUI scale 2; world motion and status text are outside the sampled region. This is animation/preference evidence, not an FPS benchmark.
- The development fixture now applies simulated combat metadata at world-render start, before the HUD draw. This fixes a capture-only timing race with real gathering metadata. No such override or capture code is packaged in the player/server artifacts.
- No new HUD renderer, metadata, mixin-application or decoding errors were found in the reviewed client log. The dedicated-server artifact is unchanged.

Reproduce art: `python tools/worldboss_card_art.py`. Native presentation loop: `python validation/smoke/worldboss_hud_visuals.py --rig <owned-private-rig> --output <probe-output>`. Native motion/preferences: `python validation/smoke/worldboss_hud_flair.py --rig <owned-private-rig> --output <probe-output> --gui-scale 2`. Requires the excluded development capture probe and a reward-free TEST boss.

## Acceptance limits

Final artistic approval, actual World Boss shader combinations and target-client CPU/GPU timing remain open. No global performance or all-bugs guarantee is claimed. The private runtime uses cached Loader 0.19.5; the source pin remains 0.19.3. Future seasonal themes can replace this authored livery without changing combat rules.

[Gathering](cobblemon-flair-2026-11-27/hud-gathering.png), [damage](cobblemon-flair-2026-11-27/hud-damage-echo.png), [critical](cobblemon-flair-2026-11-27/hud-critical.png), [coexistence](cobblemon-flair-2026-11-27/hud-coexistence.png), [native motion samples](cobblemon-flair-2026-11-27/flair-native-result.json), [artifacts](cobblemon-flair-2026-11-27/artifacts.json).
