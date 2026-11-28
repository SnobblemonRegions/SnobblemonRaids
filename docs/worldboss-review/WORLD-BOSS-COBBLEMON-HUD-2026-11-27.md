# World Boss HUD — Cobblemon visual integration

Current livery: [pixel armour and flair](WORLD-BOSS-PIXEL-FLAIR-2026-11-27.md). This document retains the initial compact-card review.

2026-11-27. Implemented and reviewed in the private native client; no production deployment, version bump, commit or push. This replaces the earlier ornate frame and typography direction.

## Visual direction

The installed Cobblemon 1.8.2 battle-info and party-panel textures were inspected as local primary references. The new art is originally authored on a 300 x 44 pixel grid; no Cobblemon UI texture was copied into the companion. A stepped grey nameplate, charcoal lower panel, one-pixel bevels and a portrait-sized World Boss badge align it with the surrounding game UI. The badge is an original crowned raid-ball symbol, not a species portrait. Giratina-gold marks and a muted opponent-red tab provide small accents.

The entire HUD uses the native Minecraft font with ordinary pixel shadows. Its textures use nearest filtering and native-resolution drawing. At Minecraft's 320px GUI width the card remains 1:1; it also retains a narrow-window fallback. The old ornate frame, TrueType faces and associated unused assets are no longer packaged. The previous source snapshot is preserved separately.

The HP strip has simple pixel shading: green above 50 percent, yellow from 50 through above 20 percent, red at or below 20 percent. These are cosmetic health bands, not combat mechanics. The Full setting retains a muted damage echo; Minimal/Off show immediate static health without that added animation. The moving energy sheen and decorative vitality footer were removed.

Name and World Boss tag sit in the upper row. The lower row shows compact status text and health numbers/percentage with independently measured space. Gathering retains its opening countdown and ready count without displaying the temporary calibration pool. Staff hold, regroup and server-owned status semantics are unchanged. The 44px card reserves less vertical space above other native boss bars.

## Verification

- Complete Windows CI-equivalent gate: PASS; 1,415 core + 26 companion tests, zero failures/errors/skips (`build/cobblemon-hud-ci.log`). The native-size viewport regression and compact countdown/ready/combat-label regression were observed failing before implementation and pass now.
- Artifact checks verify the four new pixel textures, nearest filtering, no custom font payload, no vanilla font replacement, and existing server/client boundaries.
- Iris/Complementary radar compatibility harness: PASS (`build/cobblemon-hud-graphics.log`). This covers companion initialization and radar rendering, not actual World Boss shader performance.
- Eight native presentation captures passed ownership/fallback checks: gathering, full health, damage echo, critical health, coexistence, stale-metadata native fallback, Effects Off and restoration. Their PNGs were visually inspected (`build/cobblemon-hud-native.log`). Combat-health captures are development-only CLIENT PRESENTATION FIXTURES, not server damage/reward or calibration evidence.
- No new HUD renderer, metadata, mixin-application or decoding failures were found in the reviewed native log. The dedicated-server artifact is unchanged.

Reproduce the source art with `python tools/worldboss_card_art.py`. Run the retained native fixture with `python validation/smoke/worldboss_hud_visuals.py --rig <owned-private-rig> --output <probe-output>` and an active, reward-free TEST boss. The capture probe remains excluded from player/server artifacts.

## Acceptance limits

Final artistic approval, actual World Boss shader combinations and target-client CPU/GPU measurements remain open. No global performance approval or all-bugs guarantee is claimed. The private runtime uses cached Loader 0.19.5; source pin remains 0.19.3.

[Gathering](cobblemon-hud-2026-11-27/hud-gathering.png), [damage echo](cobblemon-hud-2026-11-27/hud-damage-echo.png), [critical](cobblemon-hud-2026-11-27/hud-critical.png), [coexistence](cobblemon-hud-2026-11-27/hud-coexistence.png), [artifact hashes](cobblemon-hud-2026-11-27/artifacts.json).
