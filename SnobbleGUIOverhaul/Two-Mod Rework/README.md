# Snobblemon Client + Snobblemon Server (two-mod rework)

Read `ARCHITECTURE.md` first (audit, decisions, packets, licences, risks). This file is how to build, install, change art, and what was actually tested.

## What you get

| File | Path |
|---|---|
| Server mod (all branded art + GUI definitions) | `DEPLOY\server\mods\SnobblemonServer-1.0.0.jar` |
| Client mod (generic renderer, placeholder art only) | `DEPLOY\client\mods\SnobblemonClient-1.0.0.jar` |

The client jar contains one asset namespace, `snobblemon_client`, with neutral grey placeholders (checked: 90 PNGs, no server names or pictures). All branded art (129 files) lives in the server jar under `assets/snobblemon_regions`.

## Build

JDK 21 and Gradle 9.x (the same setup as the other projects):

```
cd snobblemon-server && gradle build      -> build\libs\SnobblemonServer-1.0.0.jar
cd snobblemon-client && gradle build      -> build\libs\SnobblemonClient-1.0.0.jar
```

`shared\` holds the protocol (`SnobbleProto`) and the GUI definition validator (`GuiSpec`); both builds compile it in.

## Install

- **Server**: put `SnobblemonServer-1.0.0.jar` in `mods`. Polymer must already be installed (it is: polymer-bundled 0.9.19). Restart; Polymer rebuilds the pack with the extra namespace and hosts it as it does today.
- **Client modpack**: put `SnobblemonClient-1.0.0.jar` in the pack. It needs Fabric API and owo-lib (already in the pack). It declares `provides: snobblehubclient`, so **remove SnobbleHelper** or Fabric will refuse to start with both.
- Existing server mods (SnobbleHub 2.7.0, Cosmetics, Gems) are untouched and keep working. SnobbleHub's older screens (Trainer Card, Warp, Wiki, Cosmetics, Gem Shop) are still code in the client jar; their pictures now come from the server pack.
- Try it in game: `/snobblemon demo` (anyone), `/snobblemon status` and `/snobblemon reload` (operators).

## Change the server's graphics without rebuilding anything

1. Put files in `config/snobblemon/theme/assets/snobblemon_regions/...`, mirroring the jar's layout (`textures/gui/sprites/NAME.png` for atlas sprites, `textures/gui/icons/NAME.png`, `font/...`). A file there replaces the same file in the jar.
2. Restart the server (or run `/polymer generate`). Polymer builds a new pack with a new hash; players download it on next join. The art version string (`1.0.0+<hash>`) changes automatically.
3. No client jar change. Verified: same client jar (sha1 `4e6a9ed1...`) showed the old buttons, then green buttons and a red gem after step 1-2.

## Change or add menus without rebuilding anything

Put a `.json` file in `config/snobblemon/gui/` (same `id` as a built-in replaces it) and run `/snobblemon reload`. Format: see `snobblemon-server/src/main/resources/data/snobblemon_server/gui/demo.json`. Widgets: `panel`, `image`, `label`, `button`, `item`. Invalid files are skipped with a reason in the log.

What a client update is needed for: new widget types, new input behaviour, scrolling lists, 3D previews, new animation kinds. What it is not needed for: art, fonts, colours, text, layout, tooltips, which menus exist, what buttons do.

## Wiring a real feature (how Phase 6 will work)

In your mod's init: `GuiService.registerData("shop", player -> json)` and `GuiService.registerAction("shop.buy", (player, widget, data) -> ...)`, then `GuiService.open(player, "shop", false)`. `open` returns `NO_CLIENT` or `NO_THEME` when the player has no client or stale art; use that to fall back to the existing chest menu. Not done yet: the shop and main menu are not moved to this system (the existing Gem Shop screen from SnobbleHelper still works through `snobblehub:*`).

## Tests (all run on my own rig, port 25597, Minecraft 1.21.1, Fabric Loader 0.19.3, polymer-bundled 0.9.19)

| Test | Result | Evidence |
|---|---|---|
| Protocol/security bots (handshake, version gate, art gate, forged/closed/flooded actions, permission, oversized packet, non-mod player) | 22 of 22 pass | `tests\results\proto_test_final.txt` |
| GUI definition loader: 8 invalid files (bad JSON, `..` path, other namespace, 300 widgets, `script` widget, bad action, huge coordinate, wrong types) | all 8 skipped with reasons; a valid override applied after `/snobblemon reload` | server log, `tests\reload_check.js` |
| Real client joins with only Fabric API + owo + Cobblemon + Snobblemon Client (no Polymer on the client) | theme loaded from the server pack, Welcome accepted | `tests\results\client_log_*.txt` |
| Real client: demo menu with server art, click counted by server, admin button refused with message | works | `previews\run_1_demo.png`, `run_3_admin_denied.png`, `run_5_click_after_reload.png` |
| Forced resource reload while the menu is open | theme re-found, menu still works, click afterwards still counted | log "after reload" lines |
| Pack cache | second launch: "Returning cached file since actual hash matches requested" (no re-download) | `client_log_final.txt` |
| Same client jar, changed server art | new art + new version | `previews\before_art_swap_1_demo.png` vs `after_art_swap_1_demo.png` |
| Polymer coexistence | hosted pack still has `cobblemon` 22, `minecraft` 4, `polymer` 2, `snobblehub` 306, `snobblemon_cosmetics` 196 files (same as before); `snobblemon_regions` added; no exceptions in either log from these mods | pack zip listing, logs |
| Legacy Gem Shop screen with its art coming from the server pack | draws fully | `previews\run_6_legacy_hub_shop.png` |
| Placeholder mode (theme namespace cleared) | generic grey UI, no crash | `previews\run_7_placeholder_only.png` |
| Client rejects an invalid definition | fallback "This menu could not be displayed" | `previews\run_8_invalid_definition_fallback.png` |

One bug the tests caught and I fixed: the override folder was merged before Polymer copied the jar's own assets, so the jar's PNG won while the version string claimed new art. Moved to Polymer's after-creation event; re-tested.

## Not done / not verified (so you do not assume otherwise)

- Phase 6 (main menu and shop moved onto this system) and Phase 7 production items: reconnect loops, disconnect during a pack download, many players at once, other client setups.
- Not tested: Sodium / Iris / other client mods, GUI scales other than the test window's, very small windows for `DefinedScreen` (it scales down to fit but I did not screenshot that).
- Not tested: a client with no pack accepted at all (the server's pack is `required`, as today).
- Fonts and sounds are supported by the pack route but no GUI definition uses a custom font in my tests.
- The legacy screens (SnobbleHelper code) are still Snobble-specific code inside the client jar; Phase 6 would turn them into definitions. Sprite names such as `region_dusk` still exist as names (placeholder pictures) because those screens ask for them.
- The client tests used an in-process click on the screen, not a physical mouse (by design: no OS input on your PC).
- `/snobblemon demo` and the demo actions are test scaffolding; remove or restrict them before production if you do not want them.
- The server mod has no config file yet for rate limits (hard-coded 8 actions/second, GUI valid 15 minutes).
