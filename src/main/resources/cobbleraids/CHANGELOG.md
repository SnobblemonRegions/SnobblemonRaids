# CobbleRaids changelog

## 0.8.209 (2026-11-27)

### Changed
- Raid screens (the raid shop, rewards and catch screens) now open as Snobblemon screens and fill your whole window. You no longer need the WebGUI mod; update to Snobblemon Client 2.25.0.
- When you catch a renowned boss, the catch screen names the affix your Pokémon keeps.

### Fixed
- The Daily Boss's 25-player limit now counts everyone who joined during the gather, not just players already fighting.
- Damage from Ascension traits, like Thorns, now counts toward a boss's shared health and can no longer faint your copy of the boss on its own.

## 0.8.208-daily-boss (2026-11-26)

### New
- Daily Boss: every day at 7 pm Eastern a renowned World Boss appears for up to 25 players. Everyone who deals it damage earns the raid reward and a chance to catch it.
- Renowned bosses now come only from the Daily Boss, and each one carries one of 30 renown affixes. Catch it and your Pokémon keeps the affix.
- Renowned bosses have a dark bronze look, so you can tell them apart at a glance.
- Duel raids show a health bar floating over the boss, with a countdown of the time left.
- A renowned duel raid shows the shared health at the top of your screen instead, like a World Boss.

### Changed
- The World Boss alert ends with its own [Teleport to the World Boss] line, so the link is easy to click.
- Bosses above their usual level have more health in duel raids.
- Endeavor and Pain Split can no longer be used against raid bosses.

### Fixed
- Draining moves, Leech Seed, Shell Bell, recoil, Counter, Mirror Coat, Metal Burst and Bide now work properly against raid bosses.
- Moves that grow stronger as the boss weakens now see the boss's real health.

## 0.8.207-spawn-safety (2026-11-23)

### Changed
- Wild raids only spawn on natural ground like grass, sand, stone and snow, never on roofs, farms or paths.
- A wild raid spawns at about your height, so it no longer appears on the surface far above you while you mine.
- No wild raids are spawned around players in creative mode, players already in a raid or its lobby, or players who have been idle for a few minutes.

### Fixed
- Big raid bosses no longer spawn stuck inside trees, overhangs or low ceilings.
- When a raid cannot find a spot near one player, it spawns near another player instead, so raids keep turning up while many players are at home or underground.

## 0.8.206-flan-claims (2026-11-23)

### New
- Raid edge ring: during a raid, a ring of particles in the boss's tier colour shows where the raid ends once you get close to it, and the action bar counts down the blocks you have left. Walking past it still forfeits the raid.
- Lobby ring: after you join a raid, its lobby circle is drawn around the boss. Stand inside it when the countdown ends to be in the raid.
- Unclaimed reward reminder: if you close the reward screen without choosing, chat reminds you every 5 minutes with a clickable [Open reward] link.
- The Raidio: the Raid Radar is now a pocket radio you tune by ear. Each tier plays its own tune that speeds up and rises as you get closer. It points you the right way but never gives a position.
- New Raidio recipe: iron, redstone, a note block, amethyst and a Cobblemon upgrade. It unlocks in the recipe book when you pick up a note block.
- Personal boss shop: after a win you choose whether to keep the boss, with up to 5 saved at once. A saved boss stays for 7 days, and the shop shows when each one leaves.
- Raid items have their own creative tab, so item viewers such as JEI can find the Raidio.

### Changed
- Wild raids and World Bosses no longer spawn inside land claims, or right at a claim's edge, so every raid can be reached and fought.
- Every raid is now a duel raid: up to 8 players each fight their own copy of the boss, all wearing down one shared health pool.
- A raid lasts 10 minutes and each duel up to 4. You are told the time at the start and warned with one minute left.
- Joining a raid spends one raid charge. You get it back if the raid never starts, or if your first duel cannot begin.
- Only players who fought a duel and stayed to the end are paid for a win.
- Raid Points have daily caps: 300 a day from raids and 150 from towers, resetting at 00:00 UTC. Your Raidio menu shows today's total.
- A World Boss kill now pays 75 Raid Points.
- Shop purchases and rerolls take a second click to confirm.
- Wild raid announcements name the biome, never coordinates. Use your Raidio to find the boss.
- A renowned boss's stat focus now applies to every copy you duel.
- The Halloween World Boss event has ended and its skins are gone.

### Fixed
- Z-Moves can no longer be used in raid battles.
- Moves with a missing target no longer break a raid turn.
- A raid stopped by a server restart gives back the charges of players whose duel had begun.
- The Raid Points of a won raid are fixed on the day of the win, even if you claim the reward later.
- A raid whose victory got lost is now finished and paid instead of being left stuck.
- A caught boss that could not be delivered gives back its withheld Raid Points.

## 0.8.204-mew-model-fix (2026-11-01)

### Fixed
- The Halloween Mew skin loads correctly.

## 0.8.203 (2026-11-01)

### Changed
- World Boss health is capped at 150,000, and the duel boss no longer uses self-healing moves.

## 0.8.202 (2026-11-01)

### Changed
- When a World Boss falls, the damage left over is shared across everyone taking part.

## 0.8.201 (2026-11-01)

### Changed
- Halloween skins are delivered through the server resource pack.

## 0.8.200 (2026-11-01)

### Fixed
- The World Boss bar shows the Pokémon's name.

## 0.8.199 (2026-11-01)

### New
- The World Boss has a join countdown.

## 0.8.198 (2026-11-01)

### New
- Halloween 2026 World Boss skins.

## 0.8.197 (2026-10-30)

### New
- World Boss duels get AscensionLib's battle effects.

## 0.8.196 (2026-10-30)

### Fixed
- Teleporting out of an active raid is blocked in every way, not just by command.

## 0.8.195 (2026-10-30)

### New
- You cannot teleport while you are in an active raid.
