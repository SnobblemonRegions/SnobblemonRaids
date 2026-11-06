# WebGUI replacement: inventory and plan

Source: `G:\a0062141-3c36-466a-8b26-242473d02e67.zip` (extracted to `webgui_pages_src\extracted`, kept apart from the project). 379 files: **28 HTML pages in 13 page folders**, plus their JS, CSS, fonts and sprite sheets.

**Security note:** `webgui\server.json` in that zip contains the WebGUI token secret (`tokenSecretBase64`). I did not use it. If that zip was shared anywhere, rotate the secret (it goes away anyway when WebGUI is removed).

## How a WebGUI page works (what has to be replaced)

Each page is a small web app. The server mod opens the page and pushes named events with JSON (`webgui:state`, `shop_quote`, `crate_view`...). The page draws them and sends player actions back (`webgui.postToGame`, `webgui.closeGui`). So every replacement needs three parts: (1) a native screen that draws the same content, (2) a transport so the backing server mod sends the same data through Snobblemon Server instead of WebGUI, (3) the action path back, validated by the server. Part 2 means changing each backing mod, not just the client.

## Inventory

Status key: **native** = a native screen exists already (but has not been compared side by side with its WebGUI page), **none** = nothing yet. Effort is my estimate for a native version at the same look, with its backing-mod change and a real-client check: S = under a day, M = 1-2 days, L = several days, XL = a week or more.

| # | Page(s) | Backing mod (source folder) | Events in (data) | Needs beyond what exists | Status | Effort |
|---|---|---|---|---|---|---|
| 1 | `wardrobe/index.html` | Snobblemon Cosmetics (`CustomArmour\Animated\mod-src`) | state, result, party | grid, 3D player preview, thumbs | native (Hub Cosmetics screen) | S to compare/adjust |
| 2 | `wiki/index.html` (+203 KB content.js) | SnobbleWiki (`SnobbleWiki (Sneelz)`) | state | scrolling rich text, search, icons | native (Hub Wiki screen) | S to compare/adjust |
| 3 | `snobblegems/shop.html` | SnobbleGemCurrency (`SnobbleGemCurrency\Version 4`) | shop_state, shop_quote, shop_result, shop_icon | grid, categories, quote flow | native (Gem Shop screen, tested) | S to compare/adjust |
| 4 | `snobblerules/rules.html`, `verify.html` | SnobbleRules (`snobblerules`) | rules_state, rules_progress, rules_result, verify_state | scroll text, checkboxes, progress | none | M |
| 5 | `snobblemon/goal.html` | SnobblemonCompanion (`SnobblemonCompanion`) | goal_state, player_lookup | progress bars, text input | none | S |
| 6 | `snobblecrates/crate.html` | SnobbleCrates (`SnobbleCrates`) | crate_view | grid of rewards, themed looks, chunking | none | M |
| 7 | `shellpack/index.html` | SnobbleShellPack (`SnobbleShellPack (Sneelz)`) | state, result, closed | grid, sprite icons, see-through HUD mode | none | M |
| 8 | `questdex/index.html` (135 KB single file) | QuestDex bridge (`snobble quests`) | quest_state, rules_notice, client | tabs, lists, regions, progress, text | none | L |
| 9 | `auction/index.html` (+43 KB JS, 12 MB sprites) | SnobbleAuctions (`snobbleauctions`) | state, result | lists, search, sort, item/Pokemon sprites from sprite sheets, bid flow | none | L |
| 10 | `cases/index.html` (+50 KB JS) | case/crate roulette mod (source not identified) | state, preview, result, close | roulette animation, item grid | none | L |
| 11 | `cobbleraids/*` 7 pages: hub, shop, leaders, legends, trophy, reveal, capture | CobbleRaids (`SnobblemonRaids`) | hub_state, shop_page, leaders_page, legends_page, trophy_page, reveal_*, capture_* | lists, reveal and capture animations, icons | none | L (7 screens) |
| 12 | `ascensionlib/*` 9 pages: capture, completion, craft, fusion, guide, hud, inspect, party, wallet | Ascension library (source not identified) | captureReveal, craftView, fusionCandidates, hudTeam, inspectOwned, partyView, walletView, completionView ... | HUD overlay, crafting UI, animations, 150 icons | none | XL |
| 13 | `cobbletowers/index.html` (252 KB JS app) | CobbleTowers (`cobbletowers-ui`) | tower (one big state) | an entire multi-screen app (lobby, run view, shop, inventory...) | none | XL |

Not found in the zip, so not covered: the WebGUI main menu and HUD pages that `server.example.json` mentions, and any page not in this zip.

## What the engine needs before most of this is possible

The current JSON menu system (`DefinedScreen`) only has panel, image, label, button and item. To reproduce these pages it must gain, in the client, once: scroll containers, repeated rows and grids filled from data, text input, checkboxes and toggles, progress bars, tabs, wrapped rich text, 3D player and Pokemon previews, timeline animation (reveals, roulette, spinners), sprite-sheet icons, sounds and custom fonts (Nunito, Oswald, Monocraft, Silkscreen, IBM Plex Mono, Jersey 15 are all in the zip with OFL licences, so they can go in the server pack). Every one of those is a client update, which is why they should be built together and early, then pages become server-side data.

## How "looks the same" will be checked

For every page: open the original HTML in a browser with the page's own event payloads (captured from the backing mod or taken from its sample state), screenshot it; open the native screen on my rig with the same payload, framebuffer screenshot at the same size; put the two side by side in a comparison sheet in `previews\parity\`. Differences are listed per page, not hidden. A page counts as replaced only when its action paths also pass the same forged-request tests as the Gem Shop.

## Proposed order

1. Compare/adjust the three that already exist (1-3), because they unlock removing WebGUI from the Gem Shop, wardrobe and wiki.
2. Build the engine widgets (above) with page 4 (rules/verify) and 5 (goal) as the first customers, since they are small.
3. Pages 6, 7, 9, 8 (crates, shell pack, auctions, QuestDex).
4. Pages 10 and 11 (cases, raids).
5. Pages 12 and 13 (Ascension, CobbleTowers) last; they are each a project of their own.
6. Only after all pages in the list are verified, and with a rollback plan, remove WebGUI from the modpack and server.
