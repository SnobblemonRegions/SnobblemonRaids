# CORRECTION (added after the report was first written)

**Sections E-J were written without checking `G:\Claude\SnobbleHub`, which already is most of this plan.** SnobbleHub Version 11 (source in `SnobbleHub\Version 11\source`) is a server mod + an owo-ui client mod (`client/`, Mojang mappings, compiles against the same owo-lib 0.12.15.4+1.21 that is already on your server), with a shared protocol file (`shared/.../HubNet.java`: S2C `Data(page, open, json)`, C2S `Request(action, arg)`), native Trainer Card / Wiki / Warp / Cosmetics / Updates screens, an inventory tab button, chest-menu fallback for players without the client, and a forged-request bot test. Per the project notes it was real-client verified over Versions 3-10. So:
- Phases 2, 3 (basic), 4, 7 (card) of the roadmap already exist; the "first prototype = Trainer Card" recommendation is already built (`CardScreen.java`).
- The honest question is no longer "which framework" (owo-ui is confirmed, in production use) but "what is still missing" (see below).
- The server snapshot you sent (`d4d68938...zip`) has **no SnobbleHub jar** in `mods/`; it does have owo-lib 0.12.15.4+1.21 (pulled in by Accessories), `Gts-2.6.1` (MIT) AND `SnobbleAuctions-1.3.1` (separate systems), CobbleDollars 2.0.0+Beta-6.1 (All Rights Reserved: use only its public API at run time, never copy it), CobbleRaids 0.8.197 (MIT), SnobblemonCompanion 10.0.0 (MIT), SnobbleWiki 1.1.0, SnobbleGemCurrency 2.1.0, polymer-bundled 0.9.19, webgui-fabric 2.0.0+mc1.21.1.
- The second zip (`4313c161...`) is world/player data, not a modpack; I did not open databases, IP caches or ban lists.

# Snobblemon Client — Research & Architecture Report

Prepared 2026-10-31 for Dibble. **Research only: no project files were modified, no code written.**
Every claim is tagged **[V]** verified from a primary source (GitHub API/source, Modrinth API, official docs, your own source), **[R]** recalled from general knowledge (verify before relying on it) or **[U]** unverifiable / unknown.

---

## A. Executive summary

**Recommendation: build "Snobblemon Client" as a small Fabric 1.21.1 client mod on owo-lib's owo-ui (0.12.15.4+1.21), and make it an optional enhancement over the server-side GUIs you already have, not a hard replacement.**

Why:

1. **owo-ui is the only candidate that clears every bar.** It is MIT [V], has a 1.21.1 Fabric build [V: Modrinth lists 0.12.15.4+1.21 for game versions 1.21 and 1.21.1], is a declarative component tree with flow/grid/scroll/overlay containers, built-in animations, a `Hud` API, an `EntityComponent` (for the player model) and a `BaseOwoHandledScreen` for container screens [V: source on the `1.21` branch].
2. **Everything else fails a requirement.** MineGui supports only 1.21.4 and ships native ImGui [V]. Strata is 2 months old and Kotlin-based [V]. SpruceUI is a lower-level widget kit with no layout tree or animation [V]. LibGui (MIT, 11.1.0 for 1.21.1) is the only serious runner-up [V].
3. **Your server code is already shaped for this.** `SnobbleGemCurrency` has a clean authoritative buy path (`Shop.buy`) and a quote/confirm token protocol in `WebShop.java` that is the right model for the new packets [V: read your source]. Your cosmetics mod is **server-only** and renders through Polymer virtual entities [V], so it already works for vanilla clients.
4. **Key design choice: keep vanilla-client fallbacks.** Your server mods already ship sgui chest menus as the fallback when WebGUI is absent [V]. If Snobblemon Client replaces WebGUI the same way, a player without it still gets a working chest menu. That drops the risk of the migration a lot and lets you roll out screen by screen.

Two things I need to be upfront about:

- **Earlier in this conversation I extracted and inspected the Cobblemon Delta jar at your request.** That was before you set the "do not decompile proprietary distributions" rule. I have **deleted that extraction and used nothing from it**. Section B is based only on public pages.
- **I could not find any public documentation of "Atlas" or of Cobblemon.gg's client architecture** (section C). I'm not going to guess.

---

## B. Cobblemon Delta (public information only)

**Verified public facts** [V: CurseForge page, fetched today]:
- "Delta Client" is a **client-side Fabric mod, Minecraft 1.21.1**, **All Rights Reserved**, authors Chriscendo and landonjw_. Current file shown: `DeltaClient-5.7.1.jar`.
- Advertised features: custom Battle UI (stat boosts, moves, better text box); PC tweaks (**mass select, improved search**); real-time battle support; new blocks for a custom dimension; minimap improvements (3D Pokémon icons); **custom GTS menu**; inventory buttons for quick access; custom UI and visuals for Raids and Dungeons.
- The wiki front page [V] gave no client-feature detail.

**Not public / unknown [U]:** its internal architecture, GUI framework, networking and asset pipeline. I won't claim any.

**What we can reasonably infer (generic, not about Delta's code):** every advertised feature is a *screen replacement or HUD* that reads server-sent data. That means the same categories are feasible for us:

| Feature class | Needs client mod? | Notes |
|---|---|---|
| New menus (shop, trainer card, warp, GTS) | Yes for native look; **no** for sgui chest menu fallback | owo-ui screens + packets |
| HUD overlays (raid, battle info) | Yes | owo `Hud` API |
| Inventory shortcut buttons | Yes | Screen init hook (Fabric `ScreenEvents.AFTER_INIT`) |
| PC search/sort/mass select | Yes + Cobblemon-internal access | highest risk (section G) |
| Battle UI changes | Yes + mixins | highest risk |
| Custom dimension blocks | Resource pack/Polymer can cover most | not in scope |

---

## C. Cobblemon.gg

**Verified:** the wiki states the server runs Minecraft 1.21.1 with "a custom modpack available through Modrinth", has "custom addons", a Server Store (cosmetics, ranks, perks) and an economy with PokeCoins and PokeGems [V: wiki page fetched]. The page does **not** mention client mods, menus, resource packs or "Atlas".

**Not verified:** a web search for "Atlas" found nothing attributable to cobblemon.gg [V: search returned only unrelated mods (CobbleCentral, CobbleAsia GUI, COBBLEGOR, Extended Battle UI)]. A GitHub repo named `rewqde/viruscheck-atlas-cobblemon.gg` exists but has no description or license; **I did not open or run it** and draw no conclusion from it.

**Conclusion:** the question "Custom client mod vs vanilla ScreenHandler vs Polymer vs resource-pack" for cobblemon.gg is **unanswerable from public evidence**. The *generic* options are the same four we evaluate in section F, so nothing is lost.

---

## D. WebGUI vs native Fabric GUI

**WebGUI facts** [V: github.com/mc-webgui/webgui README, MIT, © KoSHeroff, v2.0.0 on 2026-10-13]:
- Chromium via **Rinku** (the MCEF fork). On Fabric the browser library is bundled in the jar; Chromium (~150 MB) downloads on first launch.
- 1.21–1.21.1 Fabric is listed as **"Supported"**, while 1.21.5–1.21.11 is "Active". We're on the less-actively-targeted line.
- Modes: full-screen Screen, transparent HUD (interactive toggled with backtick), auto-HUD on join. A JS bridge pushes `window.webgui.client` at 20 TPS; each URL carries an HMAC token.

| | WebGUI (today) | Native owo-ui |
|---|---|---|
| Look | Whatever HTML/CSS you build; risk of "website in a game" | Minecraft draw calls; vanilla textures/fonts available |
| Memory/GPU | Embedded Chromium process + GPU compositing [R: expected heavier; **not measured**] | Same pipeline as vanilla GUI [R: expected lighter; **not measured**] |
| Install | Chromium download on first run | Small jar + owo-lib |
| Fast iteration | Excellent (HTML) | Good: owo XML hot reload in dev [V: docs]; Java otherwise |
| Players without it | Falls back to sgui chest menu (your code) | Same fallback possible |
| Dev skills | Web | Java/Minecraft rendering |
| White/gray flash | Unknown cause (your own investigation: hypothesis only, not reproduced) | Not automatically fixed |

**On flashing (Rule 12):** your `webgui white flash` report says the cause is a hypothesis and not reproduced. A native GUI removes the Chromium-compositing *class* of cause, but a flash from resource-pack reload, Cobblemon screen transitions or shader state would remain. Plan: reproduce on WebGUI first, then confirm the native screen on the same machine.

---

## E. MIT-licensed library research

### E.1 Comparison table

| | **owo-lib / owo-ui** | **SpruceUI** | **LibGui** | **MineGui** | **Strata** | **SGUI** (comparison only) |
|---|---|---|---|---|---|---|
| License [V LICENSE/API] | MIT | MIT | MIT | MIT (+ imgui-java, its own license [U]) | MIT | **LGPL-3.0** |
| MC 1.21.1 Fabric | **Yes**: `0.12.15.4+1.21` (Apr 2025), games 1.21 & 1.21.1 | **Yes**: `v6.2.5+1.21.1` | **Yes**: `11.1.0` "for Minecraft 1.21(.1)" | **No**: README: 1.21.4 only | **Yes**: runtime `-fabric-1.21.1` exists | Yes (you already use `1.6.1+1.21.1`) |
| Java 21 | Yes [R] | `java_version=21` [V] | [R] | n/a | Yes (21 for 1.21.x) | Yes |
| Maintenance | Active; 1.21 branch frozen since Apr 2025, main work on 26.x and a `braid-ui` rewrite | Active on newer versions; 1.21.1 line frozen | Active on newer; 1.21.1 line frozen | Last push Feb 2026, 6 stars | Created Aug 2026, v0.3.3, 4 stars | Active (2.2.1+26.3) |
| Layout system | **Flow, Grid, Stack, Scroll, Collapsible, Draggable, Overlay**, sizing modes, alignment, Insets | Widgets + simple containers | Fixed grid panels (`WGridPanel`, `WBox`) | Dear ImGui immediate mode | Row/column/grid/overlay + modifiers | n/a (chest slots) |
| Declarative / XML | Yes, Java builder **and XML** (hot reload in dev) | No | No (Java) | No | Yes (Kotlin DSL) | n/a |
| Animations | Built in (`Animation`, `Easing`, animatable size/position/color) | None | None | ImGui-level | Not verified | none |
| Custom drawing | `Surface` (tiled/flat/outline/panel/blur), `OwoUIDrawContext`, `EntityComponent`, `ItemComponent`, `TextureComponent`, `SpriteComponent` | Vanilla-like renderers | Painters | Draw lists | Primitive SPI | n/a |
| HUD | **`Hud` API** | No | No | Overlays | Screen or HUD | n/a |
| Container screens | **`BaseOwoHandledScreen`** (`disableSlot/enableSlot`) | No | **`SyncedGuiDescription`** (server-synced) | No | Not verified | Server-side only, works on vanilla clients |
| Networking | `OwoNetChannel` (record-based, endec) **or** plain Fabric payloads | none | none | none | Server-driven UI | n/a |
| Dependency weight | Fabric API; JiJ's endec, jankson (both MIT [V]) | Fabric API | Fabric API | **GitHub Packages with auth token**; bundles imgui natives | **Fabric Language Kotlin** + Fabric API | Fabric API |
| Docs | Docs site + in-game inspector + "owo-ui Academy" | Wiki | Good docs | Docs in repo | Docs in repo, new | Good |
| Fit for Snobblemon | **Best** | Usable for vanilla-style forms | Good for simple server-synced inventories | **Reject** | **Reject for now** | **Keep for server-side fallbacks** |

### E.2 Verdicts

- **owo-ui: adopt.** Verified methods from `BaseOwoScreen` on the 1.21 branch: `createAdapter()`, `build(R root)`, `component(Class, id)`; `BaseOwoHandledScreen` has the same plus `disableSlot`/`enableSlot`. Gradle (from the README and repo, **verify exact 1.21 coordinates during Phase 2**): repository `https://maven.wispforest.io`, dependency `modImplementation "io.wispforest:owo-lib:0.12.15.4+1.21"`. fabric.mod.json declares `fabric >=0.100`, `minecraft >=1.21`, `fabricloader >=0.15.0` [V]. The `0.13.0-alpha.15+1.21` build also lists 1.21.1 but is an **alpha on the `braid-ui` rewrite**: do not use.
  - *Caveat:* owo-lib is a **separate required client jar** unless you JiJ it. Putting it in your modpack is normal and fine.
  - *Caveat:* the 1.21 line is frozen. That is acceptable for a pinned server on 1.21.1, but a future MC upgrade means a port.
- **SpruceUI: not needed.** Fine for vanilla-style option screens but no layout tree, animation or HUD. Using it with owo-ui would be two frameworks for no concrete gain (your rule).
- **LibGui: fallback plan.** If owo-ui hits a blocker, LibGui is the lowest-risk replacement for server-synced inventory screens. It is much weaker for anything custom or animated.
- **MineGui: reject.** (1) README states 1.21.4 only; (2) GitHub Packages needs credentials to even resolve it; (3) embeds native ImGui, which brings native-library distribution, licensing review of imgui-java and non-Minecraft styling; (4) 6 stars. Dear ImGui is a tools UI, not a polished public-player interface.
- **Strata: reject for now.** Interesting design (headless tests, Paper support), but **repo created 2026-08-19, v0.3.3, 4 stars**, needs Fabric Language Kotlin as a runtime dependency on every player's client, and its container-screen story is unverified. Revisit if it matures.
- **SGUI: licensing answer.** LGPL-3.0, not MIT. Using it unmodified as a dependency / JiJ'd jar (what your mods do now, `include 'eu.pb4:sgui:1.6.1+1.21.1'`) is the usual accepted LGPL pattern: keep the license text, state that it's included, allow replacement of the library, and release your changes to SGUI itself if you ever modify it. Polymer is also LGPL-3.0 [V]. *Not legal advice, so have a quick review before a public modpack.*

### E.3 Other candidates found

- **LibGui (MIT, CottonMC)**: covered above. Its `CREDITS.txt` notes dark-mode button textures adapted from REI (MIT): an example of the asset-license check you asked for.
- **Cloth Config / YACL**: config screen libraries, LGPL-3 [R]. Not general GUI frameworks and not MIT: skip.
- I did not find any additional MIT Fabric 1.21.1 GUI framework that beats owo-ui. Considering ModernUI/LDLib-style frameworks: I **did not verify** their licenses (LDLib appears as a CobbleCentral dependency per search results, but I didn't check it) and they are heavier.

### E.4 What you can do with owo-ui (MIT)

| | |
|---|---|
| Use as dependency | Yes |
| Bundle (JiJ) in our jar | Yes, but a separate mod in the pack is cleaner |
| Modify the source | Yes |
| Redistribute modifications | Yes, keep copyright + MIT text |
| Include in public modpack | Yes |
| Notice required | Keep MIT license text + copyright for owo-lib, endec, Jankson (all MIT [V]) in a `THIRD_PARTY_NOTICES` file |

Assets in owo-lib that are not code (textures) should be checked file-by-file in Phase 1b; I did not audit images.

---

## F. Recommended architecture

```
                          Players WITHOUT client mod
                                   │ (sgui chest menu fallback, unchanged)
                                   ▼
 ┌─────────────────────────────  SERVER (Fabric 1.21.1)  ──────────────────────────────┐
 │  existing: SnobbleGems (Shop.buy, Ledger)  Cosmetics (server-only, Polymer)          │
 │            Companion, Raids, Warp, LuckPerms, GTS, Cobblemon                          │
 │                                                                                       │
 │  NEW  snobbleclient-server  (thin adapter, NO game rules)                             │
 │    ├─ RequestRouter (rate limit, schema validation, perms)                            │
 │    ├─ providers: TrainerCard, Shop(→GemsApi/ShopProvider), Warp, Cosmetics, ...       │
 │    └─ sends authoritative "view models" (small records) to the client                 │
 └───────────────▲──────────────────────────────────────────────────┬────────────────────┘
        C2S payloads (intent only)                       S2C payloads (state/results)
 ┌───────────────┴──────────────────────────────────────────────────▼────────────────────┐
 │  NEW  snobbleclient (client)                                                           │
 │    ├─ screens/   owo-ui screens: MainMenu, TrainerCard, Shop, Warp, Wiki               │
 │    ├─ state/     per-screen view-models, updated only by S2C packets                   │
 │    ├─ widgets/   reusable SnobbleButton, Panel surfaces, Currency chip, Region theme   │
 │    ├─ net/       payload handlers (client thread hand-off)                             │
 │    └─ theme/     colors, Surfaces, fonts, atlas ids                                    │
 └────────────────────────────────────────────────────────────────────────────────────────┘
 shared: snobbleclient-common (payload records, codecs, protocol version, constants)
```

**Module split.** A three-module Gradle layout, `common` / `client` / `server-adapter`, is appropriate but **overbuilt for day one**. Start with *one* Gradle project and **three packages** (`common`, `client`, `server`) in one jar with `"environment": "*"` and strict rule that `common` never imports client classes. Split into modules only when the server adapter must ship separately. Reason: fewer moving parts, and the same jar on both sides means the protocol version can't drift.

**Separation of concerns:** `screens` render only; `state` holds the data; `net` is the only package touching payload classes; `server/providers` call existing mods' APIs. No screen ever knows a price rule.

### Container interfaces (section 8.5): three options, in order of preference

1. **Resource-pack-only (no client mod).** The classic approach is a custom bitmap font used in the container's title to draw a background texture behind a vanilla `generic_9xN` screen. Works for vanilla clients and sgui menus [R: widely used technique; **verify with a test pack**]. Limitation: static art only, no animation, no custom widgets.
2. **Client "skin" over vanilla handlers (recommended for Snobblemon Client).** Keep the server using sgui/vanilla `GenericContainerScreenHandler` so slot sync, shift-click and drag stay exactly vanilla. On the client, detect a marker title (e.g. a private-use character) and replace/wrap the screen with a custom one. Needs Fabric `ScreenEvents`; **whether to subclass `GenericContainerScreen` or use a mixin must be prototyped [U]**.
3. **Custom `ScreenHandlerType` + `BaseOwoHandledScreen`.** Most flexible, but the type must be registered on both sides, so vanilla clients cannot open it, and you take on slot-sync correctness yourself. Use only when option 2 can't do the layout.

### Wiki / Codex (8.6)
**Hybrid:** server-provided JSON for server-specific content (regions, rules, tutorials, custom spawns) plus Cobblemon's own species data for generic Pokémon facts, with a link-out to the web wiki for long guides. You already have `SnobbleWiki (Sneelz)` server source [V: exists in G:\Claude]; its data format should be read before choosing. *Not yet read.*

### Cosmetics (8.4): important correction to the brief
Your cosmetics mod is `"environment": "server"` and renders through Polymer virtual entities [V: fabric.mod.json and build.gradle]. So cosmetics **already display to every client with no client mod**. Moving rendering to the client would (a) make cosmetics invisible to players without the mod and (b) force a rewrite of working code. **Recommendation:** keep rendering server-side; use the client mod only for the *browse/equip UI* and optional richer previews. Ownership/permissions already live on the server; they stay there.

---

## G. Integration plan

| System | How the client gets data | Authoritative action | Status of my knowledge |
|---|---|---|---|
| **Gem Shop** | S2C: product list view-model built from `Shop` + `ShopProvider` | C2S "quote(productId)" → server returns token+price+ttl; C2S "confirm(token)" → `Shop.buy` | **Read your source** [V]: `WebShop.java` already implements this quote/confirm flow with a per-player `Quote(token, product, price, until)`; reuse its logic, swap the transport |
| **Gem balance** | `GemsApi.get().getBalance(uuid)` [V] | n/a | |
| **Cosmetics** | `CosmeticManager`/`PlayerData` (server) | equip request validated against ownership | Read file list only; internals **not yet read** |
| **LuckPerms rank** | server reads groups/permissions (you use `fabric-permissions-api` already) [V] | admin preview/grant gated by a permission node | Node names TBD |
| **CobbleDollars, Raid Points, Companion Points** | each from its own mod's API/DB | read-only for trainer card | **Source of each not yet read [U]**; Companion Points are in SnobblemonCompanion v10 per your notes |
| **Cobblemon PC** | client already has the data via Cobblemon's own client packets | storage ops stay Cobblemon's | Cobblemon is Kotlin; its PC GUI classes are internal, so extension means mixins [R]. **Defer to last** |
| **GTS** | server-side backend must be identified first | existing backend | **I don't know which GTS mod you run [U]**; `snobbleauctions` exists in G:\Claude, so inspect it |
| **Raids / battle HUD** | `Hud` API for overlays | server | Need Cobblemon event hooks; avoid mixins until proven needed |
| **Polymer / resource pack** | Polymer autohost pack delivers textures/fonts | | Put server-wide art in the Polymer pack, put screen-specific atlases inside the client jar under our own namespace `snobbleclient:` to avoid Cobblemon clashes |

**Assets:** client jar holds the GUI atlas/fonts/icons (guaranteed present, no pack race). The server pack holds cosmetic models and anything the server must change without a client update.

---

## H. Compatibility and licensing

| Dependency | Version (1.21.1) | License | Notes |
|---|---|---|---|
| Minecraft | 1.21.1 | | |
| Fabric Loader | ≥0.15 required by owo; you run 0.19.3 | Apache-2.0 [R] | your Gems mod compiled vs 0.17.2: fine |
| Fabric API | ≥0.100 required by owo; your Gems build uses `0.116.6+1.21.1` | Apache-2.0 [R] | |
| Java | 21 | | |
| owo-lib | **0.12.15.4+1.21** | MIT [V] | `maven.wispforest.io` |
| endec / jankson (inside owo) | pinned by owo | MIT [V] | |
| SGUI (server fallbacks) | 1.6.1+1.21.1 | LGPL-3.0 [V] | already JiJ'd by your mods |
| Polymer | your current | LGPL-3.0 [V] | |
| Cobblemon | 1.8.2 | MPL-2.0 [R: not verified, repo is on GitLab not GitHub] | confirm before redistributing anything |
| WebGUI | 2.0.0 line | MIT [V] | keep until Phase 10 |

**Required notices:** MIT text + copyright for owo-lib/endec/Jankson/SpruceUI-or-LibGui if used; LGPL notice for SGUI/Polymer. Potential conflicts: Sodium and similar mods don't change the GUI draw path as far as I know [R], but **I won't promise compatibility; test with your actual modpack**.

---

## I. Roadmap (complexity: S/M/L)

| Phase | Work | Size | Exit test |
|---|---|---|---|
| 1 | Finish verification (this report) + read Cosmetics/Companion/Raids/GTS source, check owo coordinates | S | open questions below answered |
| 2 | Fabric 1.21.1 project, owo-lib dep, "hello" screen, GUI scale 1–4 + resize | S | boots on real client |
| 3 | Payloads + router, rate limit, version handshake | M | bot sends malformed/flooded packets, server survives |
| 4 | Main menu, theme, buttons, animations | M | |
| 5 | Shop (quote/confirm over Fabric payloads) | M | double-confirm, stale token, negative qty, disconnect mid-buy all safe |
| 6 | Container "skin" prototype (option 2) | M–L | shift-click/drag identical to vanilla |
| 7 | Trainer Card (can move earlier, see J) + Wiki | M | |
| 8 | PC / GTS / raid HUD / battle HUD | **L, risky** | one at a time |
| 9 | Cosmetics browse/equip UI | M | |
| 10 | WebGUI removal after parity checklist | S | clean boot with WebGUI jar absent |

**Rollout:** because every server screen keeps its sgui fallback, phases ship independently. Never test on production; reuse your existing rig (`G:\Claude\towertest`: minimal server + protocol bots + real-client launcher, per your notes).

**Networking/security checklist** (Fabric docs [V: docs.fabricmc.net/develop/networking, currently written for 26.2 with Mojang names; for 1.21.1 the Yarn equivalents are `CustomPayload`, `PayloadTypeRegistry.playC2S()/playS2C()`, `PacketCodec`, `ServerPlayNetworking.registerGlobalReceiver` — **confirm exact signatures on first compile** [R]]): validate every field on the server; the docs themselves warn "validate the content of the packet on the server side". Specific defenses: server-owned quote tokens with TTL (already in `WebShop`), idempotent confirm (token consumed once), clamp quantities ≥1 and ≤limit, look up product by id from server data (never trust a client price), per-player rate limit and an in-flight flag to serialize one transaction per player, run the actual transaction on the server thread/ledger you already serialize, refund-on-throw is already in `ShopProvider` delivery. **I could not verify from the docs which thread payload handlers run on in 1.21.1**; test it rather than assume.

**Performance:** no numbers exist yet. Measure: frame time with the screen open vs a vanilla screen vs the WebGUI shop (same machine), with Spark/JFR, memory before/after open/close ×50, packet bytes per open. Keep view-models small, cache textures, no per-frame allocations, send deltas by revision number as `ShopProvider.revision()` already does.

---

## J. First prototype recommendation: **Trainer Card (read-only)**

Why this first: it exercises every layer (dependency, screen, theme, packets, server-authoritative data, player model, LuckPerms default "Trainer") and has **no transactions**, so nothing can be duplicated or lost.

- **Dependencies:** Fabric API, owo-lib `0.12.15.4+1.21`, `fabric-permissions-api` (already used) for the rank lookup.
- **Classes:** `SnobbleClient` (client init, keybind to request card); `TrainerCardScreen extends BaseOwoScreen<FlowLayout>` (uses `EntityComponent` for the local player model, `LabelComponent` rows, `Surface.PANEL`); `TrainerCardData` record; `OpenCardC2S` (empty) and `TrainerCardS2C` payloads with codecs; `ProtocolVersion`; server `TrainerCardProvider` (reads `GemsApi.getBalance`, rank, other currencies as they are verified); `RequestRouter` with a 1-request/second per-player limit.
- **Config files:** `fabric.mod.json` (`environment: *`, depends owo + fabric), `snobbleclient.json` (server-side: which currency providers are enabled).
- **Rendering:** build the tree in `build()`, `Sizing.content()` for panels so it adapts to GUI scale; test at scale 1–4 and auto.
- **Networking:** client sends `OpenCardC2S`; server builds the record entirely from its own data and replies; the client never sends numbers.
- **Build:** Gradle + Loom for 1.21.1, JDK 21 (your user-level `gradle.properties` already sets that per notes).
- **Test:** unit-test the router with the bot rig; then real client at all GUI scales, resize, open/close ×100 for leaks, join with/without the mod, malformed packet, flood.

---

## Open questions (need your input or a read of source before Phase 2)

1. Which GTS mod/backend do you actually run? (`snobbleauctions` is in G:\Claude; is that it?)
2. Where do CobbleDollars, Raid Points and Companion Points each get read from (APIs not yet read)?
3. Is `SnobbleHub`'s F-key hub (in G:\Claude, 11 versions) the thing the new Main Menu replaces?
4. Public modpack: is there already one on Modrinth where owo-lib can be added?
5. Is it acceptable that players without the mod keep using the sgui chest menus (my recommendation), or do you want to make the client mod mandatory?

## Sources
- owo-lib: https://github.com/wisp-forest/owo-lib (branch `1.21`), https://modrinth.com/mod/owo-lib, https://docs.wispforest.io/owo/ui/
- SpruceUI: https://github.com/LambdAurora/SpruceUI (tag `v6.2.5+1.21.1`)
- LibGui: https://github.com/CottonMC/LibGui (release 11.1.0)
- MineGui: https://github.com/trethore/MineGui
- Strata: https://github.com/sya-ri/strata (docs/reference/compatibility.md)
- SGUI: https://github.com/Patbox/SGUI · Polymer: https://github.com/Patbox/polymer
- WebGUI: https://github.com/mc-webgui/webgui
- Cobblemon Delta Client: https://www.curseforge.com/minecraft/mc-mods/delta-client
- Cobblemon.gg wiki: https://cobblemon.gg/wiki
- Fabric networking docs: https://docs.fabricmc.net/develop/networking
- Your source read: `G:\Claude\SnobbleGemCurrency\Version 4\Source` (GemsApi, ShopProvider, WebShop), `G:\Claude\CustomArmour\Animated\mod-src` (cosmetics)
