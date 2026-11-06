# SnobbleGUIOverhaul

- `REPORT.md` - research + architecture (read the CORRECTION at the top: SnobbleHub already was an owo-ui client).
- `Gems 2.5.0 Shop Bridge\` - SnobbleGemCurrency 2.5.0 source + jar: adds `GemsApi.shopState/shopQuote/shopConfirm/shopCancel` (the existing WebShop quote/confirm logic without any transport).
- `Optional Client\` and `Required Client\` - SnobbleHub 2.7.0 + SnobbleHelper 3.9.0 (Gem Shop screen, tab, `/hub shop`) built from SnobbleHub Version 11; `source\`, `DEPLOY\`, `INSTALL.md`.
- `tests\` - rig copy (own server, port 25598, stopped by marker flag), `shop_test.js`, `gate_test.js`, `bypass_test.js`, `results\`.
Not done: real-client run (your rule), Main Menu screen, GTS / Auctions screens, container "skin" prototype, WebGUI removal.
