# Required Client variant (Hub 2.7.0-required + Client 3.9.0 + Gems 2.5.0)

Same code and same screens as the Optional variant. The only difference: `require_client` defaults to **true**, so a player whose game does not have SnobbleHelper is disconnected about 2 seconds after joining with the message in `require_client_message`.

## Install
Same as `..\Optional Client\INSTALL.md`, but use `SnobbleHub-2.7.0-required.jar` from this folder's `DEPLOY\server\mods\`.

## Rules
- **Staff are never locked out:** operators (level 3+) or anyone with LuckPerms node `snobblehub.bypass_client` stay in without the mod.
- Turn it off any time: `"require_client": false` in `config/snobblehub/config.json` (then `/hub reload`; the join check reads it live).
- An existing config.json without the new keys gets this jar's defaults (true) - set it explicitly if you want it off.
- Ship the client mod in the modpack BEFORE switching this on; vanilla/Modrinth-pack-less players will be refused.
- The check works on the channel the game announces while logging in; a client that has the jar but fails to load it counts as "no client".

## Status (honest)
`..\tests\results\required_client_results.txt`: refused without the client, kept with it, operator bypass. Shop checks were run on the Optional rig (identical code). Real client NOT run; Gem Shop screen look unverified.
