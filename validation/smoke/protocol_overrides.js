// Protocol overrides every mineflayer bot here passes as `customPackets`.
//
// declare_recipes is read as opaque bytes, because minecraft-data's 1.20.5-1.21.1 table for it is wrong.
// The table lists `crafting_special_banneraddpattern` at serializer id 11, but vanilla has not
// registered that since the loom replaced it in 1.14. So every id from 11 up resolves to the
// serializer after the real one: smelting (15) is read as repairitem (no body) and the parser drifts
// into the next recipe's bytes. Fixed upstream from 1.21.3 on.
//
// How it shows up: about 1.5 s after login, `array size is abnormally large ... 1718575989` inside
// Slot -> ingredient. 1718575989 is "focu" as ASCII: the parser has drifted into "cobblemon:focus_band".
// Whether the drift ends in a throw or in quiet garbage depends on the server's recipe order, so a
// mod that adds or removes recipes can turn it on or off. That is why bots that joined fine began
// failing on 13 Nov 2026 with no change to the bots. A throw stops the packet stream, the
// bot never spawns, and the server drops it on a keep-alive timeout 30 s later.
//
// mineflayer never reads declare_recipes; its crafting uses minecraft-data's own recipe list. So
// skipping the packet costs the bots nothing.
const SKIP_RECIPES = { '1.21': { play: { toClient: { types: { packet_declare_recipes: 'restBuffer' } } } } };

module.exports = { SKIP_RECIPES };
