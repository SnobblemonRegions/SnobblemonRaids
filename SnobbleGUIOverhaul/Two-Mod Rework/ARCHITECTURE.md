# Snobblemon two-mod architecture (client framework + server companion)

Status at writing: design plus facts checked against the real jars. Sections marked **TESTED** are filled in at the bottom (TEST RESULTS) only after a real run.

## 1. Audit of the existing client project (SnobbleHelper 3.9.1)

Location: `Optional Client\source\client` (the Required variant is identical code).

| What | Where | Finding |
|---|---|---|
| Screen code | `net.snobblemon.hubclient.ui.*` (2,300 lines, owo-ui) | Trainer Card, Wiki, Warp, Cosmetics, Updates, Gem Shop. Generic helpers (`Ui`, `Theme`, `MarqueeText`, `SnobbleScreen` window/zoom/tabs) plus Snobble-specific screens. |
| Art | `assets/snobblehubclient/textures/gui/**` | 130 files, 175 KB: 39 sprites (+39 `.mcmeta` 9-slice files: panels, buttons in 4 region colours, category chips, inset), 33 icons (tabs, regions, gem, ticket...), platform and seam pictures for Rootvale / Duskvale / Tideforge. **All of this is server branding bundled in the client jar.** |
| Texture lookup | `Theme.sprite/icon/frame/id` build `ResourceLocation(NS="snobblehubclient", ...)` | One place to change; every screen goes through it. |
| Protocol | shared `HubNet` (`snobblehub:data` S2C, `snobblehub:request` C2S) | Compiled into both jars; JSON pages. Works; stays for the existing screens. |
| Fonts | none in the client; the server hub jar ships `assets/snobblehub/font/*` and models in the Polymer pack | The server side already delivers art through Polymer for chest menus. |
| Server hub (SnobbleHub 2.7.0) | `PolymerResourcePackUtils.addModAssets("snobblehub")`, `polymer:resource_pack_include` | **Proof that the Polymer pack route already works on your server for a mod's own namespace.** |

Not removed: the existing screens and the HubNet protocol. They keep working; only their art moves.

## 2. Resource delivery: decision

Compared (Polymer is the existing infrastructure, so "native" means Polymer's pack):

| | A. Native resource pack (through Polymer) | B. Custom packets | C. External HTTP endpoint | D. Hybrid |
|---|---|---|---|---|
| Registers sprites in the GUI atlas, fonts, models, sounds | Yes, Minecraft does it | Only single textures via `DynamicTexture`; sprites/fonts/sounds need hacks | Same problems as B plus a file written to disk | Yes |
| Download, caching, SHA-1 check, retry, progress | Built in (Polymer hosts it, the vanilla client verifies the hash) | Must be written, 1 MiB packet limit, chunking | Must be written | Built in |
| Blocks the client | No (vanilla downloads off-thread) | Easy to get wrong | Easy to get wrong | No |
| Security | Player accepts the server pack as today; no code | Server could push arbitrary bytes into the renderer | Arbitrary files on disk; needs hosting and TLS | Same as A |
| New dependency | None (Polymer already installed) | None | A web host | None |
| Version negotiation / "is my art current?" | Not provided | Yes | Yes | **Yes, via small packets** |

**Chosen: D.** Graphics, fonts, models, sounds travel in the **same Polymer resource pack you already host**. A tiny set of packets does what the pack cannot: detect the client mod, compare art versions, and deliver GUI layouts and data. B/C are rejected for the reasons above; this also matches your stated preference for native resource packs.

How the client sees the art: the server pack is applied as a normal pack, so `assets/snobblemon_regions/textures/gui/sprites/*` goes into the GUI atlas and `.../textures/gui/icons/*` is readable through `ResourceManager`. A resource reload listener in the client mod re-reads a marker file `assets/snobblemon_regions/pack.json` (`{"format":1,"version":"..."}`) after every reload, so the client knows exactly which art version it is showing.

Responsibilities:
- **Polymer**: builds, hosts, hashes, requires and sends the pack (unchanged). Its other namespaces (`snobblehub`, `snobblemon_cosmetics`, `cobblemon`, `minecraft`, `polymer`) are untouched.
- **Snobblemon Server**: contributes one more namespace, `snobblemon_regions`, through `PolymerResourcePackUtils.addModAssets` plus an optional **override folder** `config/snobblemon/theme/` merged by `RESOURCE_PACK_CREATION_EVENT`, so a designer can swap a PNG with no rebuild of any mod.
- **Snobblemon Client**: generic renderer; ships neutral placeholder art in its own namespace `snobblemon_client` and uses it only when the theme art is missing.

Update flow without a client release: change a PNG in `config/snobblemon/theme/` (or the server jar) -> restart/`/polymer generate` -> Polymer's pack hash changes -> clients download the new pack on next join -> the client mod reports the new art version. **No client jar rebuild.** A client update is needed only for new kinds of widgets or behaviour (section 6).

## 3. Project structure

```
Two-Mod Rework/
  shared/net/snobblemon/proto/     one protocol file compiled into both jars
  snobblemon-client/               mod id snobblemon_client (replaces SnobbleHelper; "provides" snobblehubclient)
    src/main/java/net/snobblemon/client/   net, theme, gui (definition parser, validator, renderer)
    src/main/java/net/snobblemon/hubclient/ existing screens, unchanged apart from Theme
    src/main/resources/assets/snobblemon_client/   NEUTRAL placeholder art only
  snobblemon-server/               mod id snobblemon_server
    src/main/java/net/snobblemon/server/    handshake, pack setup, gui registry, actions, command
    src/main/resources/assets/snobblemon_regions/   ALL branded art + pack.json marker
    src/main/resources/data/snobblemon_server/gui/  default GUI definitions
  tests/                           protocol bots, real-client harness, results
  DEPLOY/                          server/mods and client/mods
```

## 4. Networking design (namespace `snobblemon`; none of these ids are used by other mods)

| Packet | Dir | Purpose |
|---|---|---|
| `snobblemon:hello` | C2S | Client announces protocol version, client mod version, GUI scale-independent capabilities. Sent on join. |
| `snobblemon:welcome` | S2C | Server answers: protocol version, accepted?, theme namespace, expected art version, list of available GUI ids. Mismatched protocol -> `accepted=false` + reason. |
| `snobblemon:pack_state` | C2S | Client reports the art version it has loaded (after join and after every resource reload) and whether the theme loaded. |
| `snobblemon:gui_open` | S2C | A full GUI definition (JSON) plus data object. |
| `snobblemon:gui_update` | S2C | New data object for the open GUI (no layout). |
| `snobblemon:gui_close` | S2C | Close the GUI. |
| `snobblemon:gui_action` | C2S | `guiId`, `widgetId`, `action`, small string args. The client only **asks**; the server re-checks that the GUI is open for this player, the widget exists, the action is allowed, permissions, rate limits and then acts. |

Rules: every string has a length cap in the codec; JSON capped at 256 KiB; per-player rate limit and "currently open GUI" check on the server; unknown packets from a player without a completed handshake are ignored; the client ignores `gui_open` for unknown protocol.

Existing `snobblehub:*` packets are not touched.

## 5. GUI definitions

Declarative JSON, parsed and validated on both sides (the server validates before sending, the client validates again before drawing): widget types `panel`, `image`, `label`, `button`, `item`; fields are coordinates, sizes, text, colour, sprite or texture **names inside the theme namespace** (no other namespace can be named), font name inside the theme or `minecraft`, tooltip lines, an `action` name for buttons, simple `{placeholders}` filled from the data object, optional `fade_ms`. No scripts, no expressions, no URLs. Limits: 256 widgets, coordinates within +-2000, strings <= 256 chars, ids `[a-z0-9_]+`. Anything invalid -> the client shows the generic fallback screen with the error code, never a crash.

Typed as "needs a client update": a new widget type, a new input behaviour, a new animation kind, 3D previews. "No client update": any change of art, colour, text, layout, order, tooltips, which GUIs exist, what buttons do (server-side).

## 6. Dependencies and licences

| Dependency | Where | Licence | Note |
|---|---|---|---|
| Fabric Loader 0.19.3, Fabric API 0.116.17+1.21.1 | both | Apache-2.0 | already in the pack |
| Minecraft 1.21.1 (Mojang mappings for dev only) | both | Mojang EULA | |
| Polymer resource-pack API 0.9.19 | server, **compile-only; not bundled** | **LGPL-3.0** | already installed on the server; we only call its public API and do not modify or bundle it, so no new LGPL obligation lands in our jars |
| fabric-permissions-api 0.3.1 | server | MIT | optional soft use, LuckPerms-compatible |
| owo-lib 0.12.15.4 | client (existing screens only) | MIT | the new GUI renderer uses plain Minecraft screens, no owo |
| Gson | both | Apache-2.0 | shipped with Minecraft |

Nothing from Cobblemon Delta or Cobblemon.gg is used. Our own jars are "All Rights Reserved" as before; the client jar contains only neutral placeholder art made for this project.

## 7. Compatibility risks

1. **Required pack and first join**: the player must accept the server pack (already the case today). Mitigation: client mod shows placeholders until the theme loads; server marks the player "no theme" and keeps legacy menus.
2. **Namespace collision**: `snobblemon_regions` is not present in the current pack (existing: cobblemon, minecraft, polymer, snobblehub, snobblemon_cosmetics). Checked.
3. **Pack regeneration time**: Polymer rebuilds the pack at server start; adding ~175 KB is negligible.
4. **GUI atlas stitching**: sprites in a server pack are stitched on reload; verified by a real client run (see results), not assumed.
5. **Duplicate client mods**: new client declares `provides: snobblehubclient` so SnobbleHelper and the new client cannot both load; players must remove the old jar.
6. **Old servers**: new client on a server without the companion mod just shows placeholder art on legacy screens and nothing else changes.
7. **Shaders/Sodium/Iris**: GUI atlas is vanilla, expected unaffected; **untested** here.
8. **Reload while a GUI is open**: screen is rebuilt after the reload listener finishes; tested by forcing a reload.

## 8. Roadmap (matches your phases)

1 restructure + strip branding from the client; 2 handshake; 3 pack delivery + proof of live art swap; 4 JSON GUI + validated action; 5 move all branded art to the server, set it up in the override folder; 6 hook main menu and shop to real server features (the existing Gem Shop bridge is reused); 7 production tests (reconnects, invalid packets, resource reloads, other client setups) and only then WebGUI removal. This session implements phases 1-5 and the compatibility tests; phase 6 and the 7 items that need real players are listed at the end as not done.
