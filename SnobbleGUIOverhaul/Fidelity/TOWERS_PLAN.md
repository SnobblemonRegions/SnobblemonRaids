# CobbleTowers native port: plan (2026-11-02, not started in code)

## Architecture (same as the other pages)
- Backing mod (CobbleTowers, patched with `G:\Claude\cobbletowers-ui\webgui-bridge.patch`) calls `emitToPage(player, "tower", {kind, data})`; the compat jar routes it to `SnobbleUi.emit`. Page actions come back on channel `cobbletowers` (`PageActions`).
- New: `TowerViews.java` (server) maps each state kind to a vm; `tools/gen_ui_towers.py` emits the UiSpec; client engine unchanged unless a gap shows up.
- Server stays authoritative: the client only sends the same action records the web page sent.

## Screens / states (from app.js + HANDOFF.md)
Views: hall, lobby, feature, briefing, draft (choose/done/sealed), run, intermission (modifier draft + vendor), mastery (season/achievements/contracts/armory), rewards modal, scout, plus the HUD (`RunEffects`, hud mode).
Payload fixtures available: `G:\Claude\cobbletowers-ui\fixtures\{IntermissionState,PlayState,RentalDraft,RewardReveal,RunEffects,TowerHallState,VendorCatalog}.json`.
Live (real payloads) in the original: intermission, rewards, vendor, draft. Mock in the original: hall, briefing, run, mastery (invented data; native port must show the same placeholders or mark them).

## Assets
Pixel kit `assets/gui/pixel/*` (9-slice frames, buttons, tiles), icons.png, items.png, partners.webp (CC0), Jersey 15 + Outfit (OFL). All go in the SERVER pack (`textures/gui/pages/cobbletowers/`), not the client jar. Fonts via build_fonts.py.

## Order of work
1. Capture reference cases (fid.py cases for the 6 mockup deep links + fixtures).
2. Shared kit in gen_ui_towers.py: nine-slice sprites (frame_parchment/dark, buttons incl. hover/pressed/disabled), card frame 112x160.
3. Intermission + Rewards + Vendor + Draft (live payloads) first, then Hall/Briefing/Run/Mastery, then HUD.
4. Score each vs original at 1280x720 (target >=95%), bot-test actions with the towertest rig.

## Performance impact
Same as other pages: server builds a small vm per state change (no polling beyond the original 1.5 s heartbeat); client draws nine-slice sprites from the existing atlas path. No per-frame allocation expected; measure draw time on the run screen.

## Status
Plan only. Nothing built or tested for CobbleTowers yet.

## Findings (2026-11-02, step 1 done: reference captures)
- The shipped page is NOT the pixel-kit mockup. It is a dark-blue "Outfit" UI (header bar 64px with logo + crumb + actions + round X; cards #16213f / border #2b3b68 / radius 18; accent #7ea2ff, gold #f2c75c, green #158037, text #e9eefc / dim #93a2c9). Pixel art appears only in card art, sprites, the rental draft cards and item icons.
- Payload kinds the page handles: TowerHallState (hall: tabs Play/Trials/Progress/Social/Ranks/Codex + Guide/Settings), PlayState (lobby), IntermissionState, RentalDraft (pixel cards, gold frame, sunburst art), RewardReveal (modal), VendorCatalog (modal), RunEffects (HUD), MasteryScreen, RanksState, RegistrationState, TowerFeatureState, TrackState.
- Actions it posts on channel cobbletowers: IntermissionAction{action,argument}, PlayAction{action,argument}, TowerHallAction{action,argument,revision}, RentalDraftAction{action,a,b}, TrackAction{action,lane,tower,number}, FeatureRequest{requestId,section,action,argument}, RanksRequest{left,right}.
- Fixtures exist for 7 kinds; Mastery/Ranks/Registration/Feature/Track fixtures must be written from app.js field use.
- Modifier card art is procedural (canvas painter: look colours + overlay rain/sand/snow/zigzag/motes/bands/blades/sun + SVG glyph + sprite): needs a Java port (TowerArt) or per-theme capture.
- Tools added: Fidelity/tools/towers_ref.py (renders every fixture in the original page -> ref/<dir>/<Kind>.png), Fidelity/tools/_dump_tower.py (DOM boxes/colours dump; write output as utf8).
- Reference shots: Fidelity/ref/towers_a/*.png.
