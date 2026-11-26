# CobbleRaids

A Fabric mod adding cooperative multiplayer boss fights against scaled Pokémon to a Cobblemon
server, plus the reward economy, shop and renown systems built around them.

## Language

**Raid**:
One cooperative fight between a group of players and a single scaled boss Pokémon, run as one
Cobblemon/Showdown battle with one side per player plus one side for the boss.
_Avoid_: encounter (see below — a different, narrower concept), battle (a raid is a battle, but not
every battle is a raid)

**Raid Definition**:
The config-authored template for one kind of raid: species, spawn rules, recruitment window,
scaling, and rewards. Loaded from datapack JSON into `RaidDefinitionRegistry`.
_Avoid_: raid config, boss config

**Rarity Tier**:
The aggregate classification a natural spawn rolls before an individual Raid Definition is chosen:
Starter, Powerhouse, Legendary, Mythical.
_Avoid_: rarity, boss tier

**Lobby**:
The pre-battle window where players join a spawned boss before the fight starts, bounded by a
duration, a radius and a player cap. Owned by `RaidLobbyManager`.
_Avoid_: recruitment window (the config field on Raid Definition is spelled `Recruitment` for this
same concept — an existing naming inconsistency, not a second concept)

**Raid Session**:
The server-authoritative identity of one running raid: the Cobblemon battle, the boss entity, and
the definition it was spawned from. Exists only while a server is running.
_Avoid_: raid instance

**Raid Progress**:
The arithmetic and state-machine half of a running raid, split out of Raid Session so it can be
tested without a server: the shared boss health pool, who is still in, each participant's
contribution, and which terminal state (won/lost/abandoned) has been reached.
_Avoid_: raid state

**Contribution**:
One participant's share of the damage dealt to the boss, expressed as a percentage of the total.
Crosses a Raid Definition's configured thresholds to grant bonus reward rolls.
_Avoid_: participation, damage share

**Encounter**:
A request from code outside the normal natural-spawn/lobby flow — another mod, or an admin command
— to run one raid-shaped battle against a specific definition, for a specific set of players, right
now. The seam other mods (e.g. CobbleTowers) use to run a raid boss inside their own content instead
of a wild spawn.
_Avoid_: raid (an encounter becomes a raid once it starts; the word names the request to start one)

**Renown / Renowned Boss**:
A boss that rolled a name and epithet (e.g. "Kaelen, the Relentless") on spawn, on top of its normal
species and tier, and pays a reward multiplier. While the Daily Boss runs, it is the only renowned boss:
wild raids never roll renown (an operator can still force one).
_Avoid_: named boss, elite boss

**Boon**:
What a Renowned Boss's epithet grants: a larger shared health pool, or maximum IV plus extra EVs in
one stat. A closed set of kinds — the epithet may say what kind of boon and, for a stat boon, which
stat, but never how strong it is; strength is a server config number, not something a datapack or
word list can set.
_Avoid_: epithet effect, boss buff

**Affix**:
One extra battle power a Renowned Boss rolls from its Boon's group of five. Only the raid catch (Capture
Protocol, after a Daily Boss) passes it to a player's Pokémon; a Personal Boss Shop buy-back never carries one. It is
stored on the Pokémon by id and run by Showdown in any battle the config allows; how strong it is lives
in code and config, never in the stored value. On a raid boss, the HP affixes act on the shared pool.
_Avoid_: perk, trait, modifier

**World Boss**:
A server-wide boss with one shared health pool, the **World Pool**. Each player fights it in a **Duel**,
a solo owned encounter against a private copy of the boss, and every Duel's damage drains the World Pool.
One runs at a time. Not a raid. Its kind says how it starts and pays: the seasonal event boss, or the
Daily Boss (a weekly boss is planned).
_Avoid_: event boss (that is one kind of World Boss), raid boss

**Daily Boss**:
The World Boss kind that appears once a day at a fixed local time (19:00 America/New_York by default) as a
Renowned Boss with an Affix, for up to 25 players. Everyone who dealt damage gets its raid reward and a
capture attempt, the only way its Affix reaches a player.
_Avoid_: daily raid, renowned raid

**Raid Points**:
The currency a raid pays out and the raid shop spends. Owned entirely by this mod rather than
borrowed from a server economy mod — see [ADR 0001](docs/adr/0001-raid-points-owned-currency.md).
_Avoid_: raid currency, points (ambiguous with contribution or reward rolls)

**Pending Reward**:
An immutable, per-player claim token created the moment a raid is won, snapshotting the reward
config and a seed rather than concrete items. A claim regenerates its contents from that seed, so an
unclaimed reward survives a server restart and a `/reload` cannot rewrite something already earned.
_Avoid_: reward claim, loot ticket

**Raid Shop**:
The storefront that spends Raid Points, separate from any general-purpose server shop.
_Avoid_: reward shop

**Raidio**:
The Raid Radar item, tuned in or out from its menu. While it is tuned in it plays note-block sounds to its holder alone, in the style of a Game Boy: quiet bleeps and the
odd faint tick while they turn, and the tune of the raid they are facing, a short tune for each Rarity Tier that starts slow a long way off and quickens and
rises as they close in. It gives a direction and a sense of nearness and never a position, draws no map or hologram, and is silent while the holder stands
still facing away. The item id stays `raid_radar`; `-Dcobbleraids.raidio=off` brings back the older hologram and
flat-map display.
_Avoid_: radar display, tracker, minimap

**Raid Edge**:
The boundary a raid player must stay inside: the 48-block leash around the boss while a raid is fought (crossing it forfeits at
once), or the recruitment radius while its Lobby is open (outside it when the Lobby locks, a player is left out). Drawn as a ring
of dust in the boss's Rarity Tier colour, sent only to that raid's or Lobby's own players, with an action-bar warning near it.
Owned by `RaidEdgeService`; the thresholds are `RaidEdge`.
_Avoid_: border, wall (the World Boss zone's particle wall is a different thing), arena

**Land Claim**:
An area a claim mod (Flan) protects for a player or the server. Wild raids and surface World Bosses never spawn inside one or
within `land_claims.buffer_blocks` of it, because players the claim does not trust cannot fight a boss there. Checked by
`LandClaimGuard`.
_Avoid_: protected area, claim (alone -- it collides with claiming a Pending Reward)

**Player Changelog**:
The release notes shipped in the jar for players to read in game, one entry per version, newest first.
_Avoid_: patch notes, release notes (the PR description is the developer-facing record)
