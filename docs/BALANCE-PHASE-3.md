# Duel raids and the Threat balance (balance phase 3)

## Every raid is a duel raid

- The lobby recruits up to 8 players. When it locks, each player fights their own hidden copy of the boss (`RaidDuelService`)
  and every hit comes off one shared pool: `3 x duel HP x players^0.85` (`RaidDuelRules`). Duel HP: starter 550, powerhouse
  1,400, legendary 3,500, mythical 5,000.
- A copy stands on the raid boss itself and is invisible, so players see their Pokemon fight the boss they walked up to. A
  player within 4 blocks of the boss gets the World Boss's ground-checked spot beside them instead.
- Beating a copy chains into the next after 3 seconds. A wiped party is out; its damage still counts. 10 minutes to win.
- A win pays: Cobblemon experience to every Pokemon in each victor's party (below), the reward screen, wins, titles, the
  trophy room, the Hall of Legends for a renown boss, the capture offer, and AscensionLib's share pay. Stats and the phase-0
  log line (`Raid ... (tier, duel) ended ...`) are recorded for wins and losses.
- The World Boss takes at most 40 players.

## Pool experience

When a raid's or World Boss's pool falls, each victor's whole party gains
`base experience yield x boss level / 5 x tier x share` per Pokemon (Cobblemon's trainer formula). Tier: starter 1.5,
powerhouse 2, legendary 3, mythical 4, World Boss 5. Share: damage share x players, clamped to 0.5-1.5; under a quarter of an
even split pays nothing. A level-70 legendary (yield about 300) pays about 12,600 per Pokemon at an even split, close to one
level for a level-70 party. Tune in `PoolExperienceRules`.

## Raid shop: Scouters and Resonance Dust

`ascensionlib:scouter` and `ascensionlib:resonance_dust` are AscensionLib wallet materials, not items. An entry selling one is
shown only when AscensionLib (balance phase 3 or later) is installed, and a purchase credits the buyer's wallet. Existing
servers keep their own `config/cobbleraids/shop.json`, so add a section like this:

```json
{
  "id": "ascension",
  "title": "Ascension",
  "entries": [
    { "id": "scouter_5", "cost": 120, "limit": 2, "reset": "daily", "item": "ascensionlib:scouter", "count": 5 },
    { "id": "resonance_dust_50", "cost": 75, "limit": 4, "reset": "daily", "item": "ascensionlib:resonance_dust", "count": 50 }
  ]
}
```

Dust is priced so 4 packs a day (300 RP, 200 dust) is a little under one Threat III Tower completion: a top-up, not a
replacement for the Tower.
