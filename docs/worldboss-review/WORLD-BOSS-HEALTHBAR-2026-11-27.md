# Distortion healthbar — 2026-11-27

Current presentation: [Cobblemon-style pixel HUD](WORLD-BOSS-COBBLEMON-HUD-2026-11-27.md). This document retains the earlier design review.

Implemented in the optional companion and loaded in the private WorldBossTester preview. No production deployment, version bump, commit or push.


Typography was subsequently improved; see [the current typography review](WORLD-BOSS-TYPOGRAPHY-2026-11-27.md). The captures below retain the original healthbar review.

## Experience

A 384 x 64 GUI composition replaces the five-pixel native strip for the owned World Boss: shaded obsidian, aged gold wing ornaments, faceted scarlet/violet gems and an engraved frame. The violet energy reservoir is cropped to health rather than squeezed. A pale damage echo holds for 250 ms and decays over time, while the main edge follows the native authoritative progress. Below 30 percent the reservoir turns crimson. UNBROKEN / FALTERING / CRITICAL are descriptive health labels, not additional combat mechanics.

The title, health numbers, percentage and event status have separate space. Gathering shows AWAITING CHALLENGERS instead of the temporary 1/1 calibration pool. Unfrozen opening rosters remain in that state during a staff hold or regroup. Actual event status comes from the server. Frame width adapts below 400 GUI pixels, and title/status text has bounded width.

Full enables the slow energy glint and damage echo. Minimal and Off retain the readable static HUD with those additions disabled. The HUD works independently of the TEST-only Distortion Veil flag; future seasonal art still needs its own authored visual treatment.

## Ownership and fallback

A separate, capability-gated S2C metadata channel identifies the native boss event by exact UUID. No matching by species name, title or colour. The companion keeps other boss events in the native map, preserving their updates, fog/music flags and BossBarSkins rendering; it filters only the owned event's drawing and reserves room above the remaining bars. Missing/expired metadata (six seconds), missing event, disconnect/world change or renderer failure restores normal native handling. Without the companion, the existing optional BossBarSkins owner-scoped skin remains the fallback.

The server sends bounded presentation metadata at most once per 20 game ticks per visible viewer, except identity or presentation-state changes. It receives no new client combat, health or reward instructions. The protocol is Minecraft-common; all HUD/render classes and textures stay in radar-client. No dependency or global vanilla sprite override was added.

## Verification

- Complete Windows CI-equivalent gate: PASS, 1,415 core + 25 companion tests; zero failures, errors or skips (`build/boss-hud-acceptance-ci.log`). Tests cover payload round-trip and invalid snapshots, immediate health vs delayed echo, owner/reset/healing, reduced motion, frame-rate-independent decay and narrow-window width.
- Graphical radar compatibility harness with Iris/Complementary: PASS (`build/boss-hud-reviewed-graphics.log`). Its companion artifact is unchanged by the final server-only unopened-roster guard. This is initialization/radar coverage, not actual World Boss shader or performance approval.
- Final private native captures cover gathering, full health, damage echo, critical health, coexistence with a separate pink boss event, stale-metadata native fallback, Effects Off and restoration. PNGs were visually reviewed. A final staff-hold capture verifies the unopened pool remains hidden.
- The combat-health captures are explicitly a development-only CLIENT PRESENTATION FIXTURE with an 8k display pool. They verify appearance and native event integration, not server damage, calibration, reward correctness or live battle performance. The fixture and screenshot probe are excluded from player/core artifacts.
- No new HUD metadata, renderer, mixin-application or packet decoding errors were found in the reviewed logs. Existing unrelated resource warnings remain outside this change.

Retained native loop: `python validation/smoke/worldboss_hud_visuals.py --rig <owned-private-rig> --output <probe-output>` with an active reward-free TEST scene and the development capture probe. Source artwork is reproducible with `python tools/worldboss_hud_art.py` (Pillow is an authoring dependency only).

## Acceptance limits

Actual World Boss shader combinations, target GPU/CPU timing and final artistic approval remain open. No global performance approval or all-bugs guarantee is claimed. Private runtime Loader is 0.19.5; the source pin remains 0.19.3. The GUI dimensions and healthbar colours are presentation choices, not production balance numbers.

[Gathering](healthbar-2026-11-27/hud-gathering.png), [critical health](healthbar-2026-11-27/hud-critical.png), [coexistence](healthbar-2026-11-27/hud-coexistence.png), [native fallback](healthbar-2026-11-27/hud-native-fallback.png), [exact artifacts](healthbar-2026-11-27/artifacts.json).
