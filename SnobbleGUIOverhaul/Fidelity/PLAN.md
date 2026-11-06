# GUI visual-fidelity rework - plan (2026-11-01)

Source of truth: the original pages in `webgui_pages_src.zip` (identical to `webgui_pages_src\extracted`, checked file by file; `server.json` holds the WebGUI token secret and is never read or copied).
Current state: `DEPLOY4` (Snobblemon Server/Client 2.2.0). Its screenshots are the "before" picture, not the design.

## Inventory (28 original pages + Towers HUD)
| Original page | Native id | Native exists | Notes |
|---|---|---|---|
| snobblerules/rules.html | rules | yes | |
| snobblerules/verify.html | verify | yes | |
| snobblemon/goal.html | goal | yes | |
| cobbleraids hub / shop / trophy / leaders / legends / capture / reveal | cobbleraids_* | yes (7) | |
| ascensionlib wallet / guide / party / inspect / craft / fusion / capture / completion / hud | ascensionlib_* | yes (9) | |
| auction/index.html | auction | yes | settings (theme/zoom/glow) were dropped - must come back |
| cases/index.html | cases | yes | layout is absolute-positioned paper/folders; first pilot |
| snobblecrates/crate.html | snobblecrates_crate | yes | |
| snobblegems/shop.html | snobblegems_shop | yes | |
| questdex/index.html | questdex | yes | |
| shellpack/index.html | shellpack | yes | |
| wardrobe/index.html | wardrobe | yes | |
| wiki/index.html | wiki | yes | |
| cobbletowers/index.html (13 screens + HUD) | cobbletowers | **no** | minified Preact bundle; needs a full port |

## Method
1. **Objective comparison (built):** `Fidelity\tools\fid.py`. A rig-only recorder in Snobblemon Server writes every event a page receives (`logs/ui_events.jsonl`); the real-client harness writes a timeline of its clicks and screenshots. The tool replays the very same events and clicks into the ORIGINAL page in headless Chrome at 1280x720 over the same world backdrop, then writes side-by-side + difference images and a pixel score (`Fidelity\compare\<run>\`). Pixel score is a guide only; every screen is also inspected by eye.
2. **Renderer foundation (client engine), so pages can be expressed in the originals' own terms:**
   - page units = CSS pixels of the original window, with the original's own "fit to window" rule (e.g. Cases: min(w/1700, h/960));
   - absolute positioning (x/y) alongside the existing rows/columns;
   - the original web fonts (Nunito, Oswald, IBM Plex Mono, Silkscreen, Monocraft, Jersey 15, Outfit, the pixel font) as TrueType fonts in the SERVER's Polymer pack, with CSS-like size, letter spacing and line height;
   - rotation, border width, box shadow, text shadow colour, opacity, vertical gradients, hover lift;
   - textured / patterned backgrounds and nine-slice frames as server pack sprites. Where a CSS effect cannot be drawn natively (layered gradients, patterns), the art is rendered FROM the original CSS in headless Chrome and shipped as a server texture, so it is pixel-identical by construction.
3. **Page by page:** rebuild each layout against its CSS (sizes, positions, colours, fonts, states, animations), run the real client with sample data, compare, fix, repeat. Pilot: Cases. Then QuestDex, Gem shop, Auction, Shell pack, Wardrobe, Crates, Wiki, Rules/Verify/Goal, Raids, Ascension.
4. **CobbleTowers:** port all screens and the HUD with the same method.
5. **Assets:** move any Snobblemon Regions art out of the client jar (133 textures under `snobblemon_client`) into the server pack; client keeps only neutral fallbacks.
6. **Validation:** every page/state with sample data, interactions, GUI scales 1-4, resource reload (F3+T), Polymer pack together with other Polymer content, functional bot tests.

## Performance impact
- Rendering: TrueType text costs about the same as bitmap text once glyphs are cached (one atlas per font/size); rotation and shadows add a pose push or one extra quad per node. Nothing re-lays-out per frame; layout stays cached until data changes. Expected: no measurable FPS change for one open screen.
- Server pack: fonts add about 1-2 MB (TTF), captured backgrounds a few hundred KB, downloaded once with the existing Polymer pack (hash-cached).
- Server: none at runtime. The event recorder only runs with `-Dsnobble.rig`.

## Honesty rules for this work
Built != tested != visually matched. Every page in the report gets: compared (yes/no), remaining differences, and why.
