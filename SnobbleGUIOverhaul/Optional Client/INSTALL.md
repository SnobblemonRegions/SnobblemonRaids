# Optional Client variant (Hub 2.7.0 + Client 3.9.0 + Gems 2.5.0)

Players WITHOUT the client mod still play normally and get the existing chest menus. Players WITH it get the native screens, now including the Gem Shop.

## SERVER  (`DEPLOY\server\mods\` -> server `mods\`, restart once)
- SnobbleHub-2.7.0.jar            replaces SnobbleHub-2.x.jar
- SnobbleGemCurrency-2.5.0.jar    replaces SnobbleGemCurrency-2.x.jar (adds the shop methods the Hub calls; WebShop and the chest menu are untouched)
- Snobble-Cosmetics-2.1.1.jar     unchanged from Version 11 (only needed if not installed already)
Delete the old jars first. Never put a `*-sources.jar` in mods/.

## PLAYERS  (`DEPLOY\client\mods\SnobbleHelper-3.9.0.jar` -> their `mods\`, replacing SnobbleHelper-3.8.1)
Needs Fabric API + owo-lib 0.12.15.4+1.21 (your server pack already has owo-lib via Accessories).

## What is new
- **Gem Shop tab** (shopping-bag icon, last tab) and `/hub shop`. Categories, search, product grid, details, Buy -> quote -> Confirm. Prices, balances, limits and the purchase are all decided by the Gems mod; the screen only sends a product id, then a single-use token.
- `/hub shop` for a player without the client opens the normal `/gemshop` chest menu.
- Config `config/snobblehub/config.json` gains `require_client` (false here), `require_client_message`, `require_client_grace_ticks`.

## Status (honest)
Protocol-bot tests: `..\tests\results\optional_client_results.txt` (22 shop checks incl. replay, forged token, injection, 300-request flood, no-funds, plain player; 2 gate checks). **The Shop screen was NOT looked at in a real client** (compiles and its data path is tested). Check look, tab icon, search typing, scrolling and Buy/Confirm at GUI scales 1-4 before releasing.
The shop shows item icons for item products, the Gem/ticket picture for the shop's own icons, and a paper icon for cosmetics thumbnails (buy cosmetics in the Cosmetics tab).
