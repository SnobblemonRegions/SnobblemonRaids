Later geometry correction: [crest depth repair](SOVEREIGN-SEAL-Z-FIGHTING-2026-11-27.md).

# Sovereign Seal theatre and material pass — 2026-11-27

Material-review artifact: `0.8.211-worldboss-playtest`, 177ea1b54b1541e4db71798bbf1700f820b9e5fe4b1632ea1d4628bc96e2e440256 `177ea1b54b1541e4db71798bbf1700f820b9e5fe4b1632ea1d4628bc96e2e440`.

## Appearance

Full presentation now has five packet-only display models: the broken orbital band, jewel-detailed crest, rotating aureole, counter-moving scarlet/ivory energy arcs, and a slowly turning ritual floor circle. Aureole and floor circle unfold with a smooth six-game-second opening scale. The aureole breathes over twelve seconds. No display is added to the server world, saved, given AI or collision.

Gold, obsidian and crimson enamel now use generated 128px textures with broad bevel highlights, inset borders, engraved chevrons, wear and facet variation. Solid faces have separate side/shadow textures. Solid model elements enable shading and model ambient occlusion; band and crest use native world lighting. Decorative displays keep full brightness. These are diffuse textures and baked surface shading; no PBR reflections or dynamically cast shadows are implemented.

The prior offline geometry sheet averaged texture colours. It remains a geometry illustration. The native captures below establish actual in-game material appearance.

## Preferences and lifecycle

Full mode shows five layers and full particle accents. Minimal shows the original band/crest plus aureole with reduced particles. Off preserves the original two models and disables particles. Each layer's dirty metadata is consumed once and shared across eligible viewers. Preference changes add/remove only the affected layers.

Activation remains behind both the Sovereign prototype JVM flag and a reward-suppressed TEST encounter. Model paths and Polymer allocations remain independent of the Halloween dome and boss-bar themes.

## Validation

All 1,418 tests passed, with zero failures, errors or skips. Coverage includes bounded/unit transforms at supported radii and long uptimes, motion looping, effect-level layer counts, opening-scale clamping, model rotation/coordinate limits and resource dependencies.

Complete Windows CI-equivalent checks passed (`build/seal-material-ci.log`). Graphical companion integration passed with Iris/Complementary (`build/seal-material-graphics.log`, disposable fixture `7471fefd18d8d58f`). That harness checks the companion renderer, rather than the seal's material style.

Private native fixture `886712dc2843d509` loaded a pack checked byte-for-byte against all five models and their declared textures, with five distinct Polymer model IDs. Native review observed textured crest/band surfaces, aureole, energy arcs and floor sigil. Off removed the decorative layers; minimal restored the aureole while leaving the floor circle absent. Explicit stop left the arena clear, and subsequent TEST starts recreated the scene. Opening and fully expanded states were observed; intermediate activation frames were not exhaustively reviewed.

## Native evidence

* [Textured crest close-up](materials-2026-11-27/crest-close.png)
* [Full ritual circle](materials-2026-11-27/full-topdown.png)
* [Effects-off comparison](materials-2026-11-27/off-topdown.png)

These are unedited Minecraft screenshots. Objects move between captures. Brief window-input interference interrupted one minimal-mode observation; the mode was checked again after restarting the TEST.

## Private preview and remaining limits

The private server/client remain on loopback 25741/25742, with sixteen-chunk visibility and a 600-second gathering window. WorldBossTester is a spectator, full effects are restored and the camera faces the crest. The TEST cancels if its timer expires without a ready fighter. Production configuration was not changed. The independent javaw Minecraft client was left alone.

Aesthetic approval, night/covered-arena lighting, reconnect/dimension transitions, native seal shader comparisons, fifty-player load and production-runtime acceptance remain open. The private runtime uses cached Fabric Loader 0.19.5; the repository pin stays 0.19.3. No production deployment, commit or push was performed.
