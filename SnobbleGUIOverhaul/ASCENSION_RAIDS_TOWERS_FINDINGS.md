# Findings: how AscensionLib, CobbleRaids, CobbleTowers, SnobbleWiki talk to WebGUI (2026-10-31)

Source: jars from the user's R:\Downloads zip (user explicitly allowed decompiling), extracted to `mods_src\extracted\mods`.

## All four use the SAME small WebGUI server API (reflection / method handles)
`land.webgui.api.WebviewApi` static methods:
- `openGui(ServerPlayer, String url)`      e.g. `webgui://cobbletowers/index.html`, `webgui://wiki/index.html`, `capture/capture.html`, `hub/hub.html`
- `openHud(ServerPlayer, String url)`      (CobbleTowers, Ascension HUD)
- `emitToPage(ServerPlayer, String event, String json)`   (towers event = `tower`; json length capped by WebGUI MAX_EMIT)
- `onPageEvent(String channel, BiConsumer<ServerPlayer,String>)`   (channel e.g. `cobbletowers`)
Extra, CobbleRaids + SnobbleWiki only: `land.webgui.server.WebviewAssets` + `WebviewServerConfig` (install bundled html into config/webgui/web), `WebviewApi.clientHasWebGui` / `WebviewNetworking.clientKind`, `reloadPages`.
CobbleTowers also calls `com.ascensionlib.AscensionScreens.revealShowing(UUID)` (holds events while an Ascension reveal shows).

Mods and their page folders: ascensionlib (9 pages), cobbleraids (hub, shop, leaders, legends, trophy, capture, reveal), cobbletowers (one SPA, `webgui://cobbletowers/index.html`), wiki (`webgui://wiki/index.html`).

## Consequence (design decision)
No per-mod jar edits needed: build a **WebGUI-compat provider** that implements exactly the API above and maps
`url -> layout-v2 view`, `emitToPage -> SnobbleUi.emit`, `onPageEvent -> SnobbleUi.onMessage`.
It must be a separate drop-in jar ("snobblemon-webgui-compat") that replaces the WebGUI jar at cutover (class names collide with WebGUI while both are installed, so they are never installed together). Rollback = swap the WebGUI jar back.
Each page still needs its own layout + view model (PageViews) built from the page HTML/JS in `webgui_pages_src\extracted\webgui\web`.

## Page counts / size (work remaining, nothing built yet for these)
- Ascension: capture, completion, craft(35KB), fusion, guide, hud, inspect, party, wallet (+ reveal animation, icons, fonts)
- Raids: hub, shop, leaders, legends, trophy, reveal, capture
- Towers: 252KB JS SPA (many screens, tickets inside)
- Wiki: 203KB content.js (native Hub Wiki screen already exists)

## Open question
"Tickets" is assumed = CobbleTowers tickets (+ gem shop ticket items). Unconfirmed with the user.
