# World Boss typography — 2026-11-27

Current presentation: [Cobblemon-style pixel HUD](WORLD-BOSS-COBBLEMON-HUD-2026-11-27.md). This document retains the earlier design review.

Implemented and reviewed in the private native client. No production deployment, version bump, commit or push.

The boss title now uses Cinzel Decorative Bold: tall, engraved capitals and ornamental serifs. Health numbers, percentage and event status use Barlow SemiBold for clarity. Both are native TrueType providers with 4x glyph oversampling, rather than enlarged Minecraft bitmap characters. A fine half-pixel ink shadow replaces the heavier default shadow. The title baseline was adjusted after the first native capture exposed crowding with the WORLD BOSS label.

Only this HUD opts into these two namespaced font resources. Minecraft's default font remains the fallback for unsupported glyphs. Measurement, clipping, centering and bidirectional ordering use the styled text's actual font metrics. Other interfaces and mods retain their fonts. Minecraft owns font caching and resource reloads; no custom rasterizer or runtime download was introduced. The dedicated-server artifact is unchanged.

## Verification

- Complete Windows CI-equivalent gate: PASS; 1,415 core + 25 companion tests, no failures/errors/skips (`build/worldboss-font-final-ci.log`).
- Artifact checks verify native TTF files, 4x providers, default-glyph fallback, absence of global vanilla font overrides, and exact pinned font/license checksums.
- Iris/Complementary radar compatibility harness: PASS (`build/worldboss-font-final-graphics.log`). This verifies companion initialization and radar rendering, not actual World Boss shader performance.
- Eight final native presentation captures passed their ownership/fallback checks. Gathering, full health, damage echo, critical health, coexistence, native fallback and Effects Off were visually reviewed (`build/worldboss-font-final-native.log`). Combat snapshots remain explicitly development-only client presentation fixtures, not server damage evidence.
- No font-loading, HUD-renderer or new packet decoding errors were found in the reviewed native client log. Full-font shader coverage and target-client CPU/GPU approval remain open.

## Font provenance

Unmodified fonts and their complete copyright/OFL notices are packaged with the optional companion. Both were retrieved from primary source at Google Fonts revision `5026800714d07231937d92eb4cceabf06e05d74e`:

- [Cinzel Decorative Bold, Natanael Gama](https://github.com/google/fonts/tree/5026800714d07231937d92eb4cceabf06e05d74e/ofl/cinzeldecorative).
- [Barlow SemiBold, The Barlow Project Authors](https://github.com/google/fonts/tree/5026800714d07231937d92eb4cceabf06e05d74e/ofl/barlow).

Exact files, URLs and checksums: [font sources](typography-2026-11-27/font-sources.json). [Artifact hashes](typography-2026-11-27/artifacts.json), [gathering](typography-2026-11-27/hud-gathering.png), [damage echo](typography-2026-11-27/hud-damage-echo.png), [critical](typography-2026-11-27/hud-critical.png).
