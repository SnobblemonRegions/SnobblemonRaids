'use strict';

// Behavioural coverage for renown-affixes.js on the REAL Showdown sim Cobblemon ships, with this build's
// raid-patch.js (which loads renown-affixes.js) applied. Each check is a real battle: the affix map arrives on
// the format object exactly as the Java side sends it (RenownAffixBattle), and the assertions read the
// simulator's own state and log. Seeded, so a failure replays exactly.
//
// Invoked with the extracted-Showdown directory (raid-patch.js and renown-affixes.js copied in) as argv[2];
// see validate_showdown_renown_affixes.py.

const path = require('path');

const fixtureDir = process.argv[2];
if (!fixtureDir) {
  console.error('usage: node showdown_renown_affixes_test.js <extracted-showdown-dir>');
  process.exit(2);
}

require(path.join(fixtureDir, 'raid-patch.js'));
const affixModule = require(path.join(fixtureDir, 'renown-affixes.js'));
const {Battle} = require(path.join(fixtureDir, 'sim/battle'));
const {BattleStream} = require(path.join(fixtureDir, 'sim/battle-stream'));

let failures = 0;
let checks = 0;
function check(name, condition, detail = '') {
  checks++;
  if (!condition) {
    failures++;
    console.error(`FAIL ${name}${detail ? `: ${detail}` : ''}`);
  }
}

let uuidCounter = 0;
function mon(species, moves, extra = {}) {
  uuidCounter++;
  return {
    name: species, species, moves, movesInfo: moves.map(() => ({pp: 16, maxPp: 16})),
    gender: 'M', ability: extra.ability || 'noability', item: extra.item || '',
    level: extra.level || 50, evs: {hp: 0, atk: 0, def: 0, spa: 0, spd: 0, spe: 0},
    ivs: {hp: 31, atk: 31, def: 31, spa: 31, spd: 31, spe: 31}, nature: 'serious',
    uuid: `00000000-0000-0000-0000-${String(uuidCounter).padStart(12, '0')}`,
    ...extra.set,
  };
}

/** A singles battle; `affixes` maps a set (by object) to an affix id. */
function battle(p1Team, p2Team, affixes = new Map(), seed = 1) {
  const sim = new Battle({formatid: 'gen9customgame', seed: [seed, 2, 3, 4]});
  const map = {};
  for (const [set, id] of affixes) map[set.uuid] = id;
  sim.format = Object.assign(Object.create(sim.format), {cobbleAffixes: map});
  sim.setPlayer('p1', {name: 'P1', team: p1Team});
  sim.setPlayer('p2', {name: 'P2', team: p2Team});
  // Custom Game opens on team preview; lead with the first set, as listed.
  if (sim.requestState === 'teampreview') turn(sim, 'default', 'default');
  return sim;
}
const turn = (sim, p1, p2) => { sim.choose('p1', p1); sim.choose('p2', p2); };
const log = sim => sim.log.join('\n');
const active = (sim, side) => sim.sides[side].active[0];

// --- the module declares exactly the thirty affixes, and an unknown id is ignored ---
check('thirty affixes', affixModule.AFFIXES.length === 30, String(affixModule.AFFIXES.length));
{
  const a = mon('Pikachu', ['tackle']);
  const sim = battle([a], [mon('Pikachu', ['tackle'])], new Map([[a, 'not_an_affix']]));
  check('unknown id gives no affix', active(sim, 0).cobbleAffix === null);
}

// --- Head Start: +1 Speed on first entry ---
{
  const a = mon('Pikachu', ['tackle']);
  const sim = battle([a], [mon('Pikachu', ['tackle'])], new Map([[a, 'head_start']]));
  check('head start boosts speed on entry', active(sim, 0).boosts.spe === 1, JSON.stringify(active(sim, 0).boosts));
  check('no affix, no boost', active(sim, 1).boosts.spe === 0);
}

// --- Dread Aura: foe Sp. Def -1 on entry ---
{
  const a = mon('Gengar', ['shadowball']);
  const sim = battle([a], [mon('Snorlax', ['tackle'])], new Map([[a, 'dread_aura']]));
  check('dread aura lowers foe sp. def', active(sim, 1).boosts.spd === -1, JSON.stringify(active(sim, 1).boosts));
}

// --- Last Stand: survives one knockout at 1 HP, then faints ---
{
  const a = mon('Magikarp', ['splash'], {level: 5});
  const sim = battle([a], [mon('Garchomp', ['earthquake'], {level: 100})], new Map([[a, 'last_stand']]));
  turn(sim, 'move 1', 'move 1');
  check('last stand leaves 1 HP', active(sim, 0).hp === 1 && !active(sim, 0).fainted, `hp=${active(sim, 0).hp}`);
  turn(sim, 'move 1', 'move 1');
  check('last stand is once per battle', active(sim, 0).fainted || active(sim, 0).hp === 0);
}

// --- Unbroken: foe Growl does nothing at full HP ---
{
  const a = mon('Snorlax', ['splash']);
  const sim = battle([a], [mon('Pikachu', ['growl'])], new Map([[a, 'unbroken']]));
  turn(sim, 'move 1', 'move 1');
  check('unbroken blocks drops above half', active(sim, 0).boosts.atk === 0, JSON.stringify(active(sim, 0).boosts));
}

// --- Hollow Mind: Taunt fails ---
{
  const a = mon('Snorlax', ['splash']);
  const sim = battle([a], [mon('Pikachu', ['taunt'])], new Map([[a, 'hollow_mind']]));
  turn(sim, 'move 1', 'move 1');
  check('hollow mind blocks taunt', !active(sim, 0).volatiles['taunt']);
}

// --- Mirrorglass: first Toxic bounces, the second lands ---
{
  const a = mon('Snorlax', ['splash']);
  const sim = battle([a], [mon('Pikachu', ['toxic'])], new Map([[a, 'mirrorglass']]));
  turn(sim, 'move 1', 'move 1');
  check('mirrorglass bounces the first status move', active(sim, 1).status === 'tox' && !active(sim, 0).status,
    `p1=${active(sim, 0).status} p2=${active(sim, 1).status}`);
}

// --- Immovable: Roar fails ---
{
  const a = mon('Snorlax', ['splash']);
  const sim = battle([a, mon('Pikachu', ['tackle'])], [mon('Pikachu', ['roar'])], new Map([[a, 'immovable']]));
  turn(sim, 'move 1', 'move 1');
  check('immovable stays in', active(sim, 0).species.name === 'Snorlax');
}

// --- Barbed Shell: a contact attacker loses 1/16 ---
{
  const a = mon('Snorlax', ['splash']);
  const attacker = mon('Pikachu', ['tackle']);
  const sim = battle([a], [attacker], new Map([[a, 'barbed_shell']]));
  turn(sim, 'move 1', 'move 1');
  const foe = active(sim, 1);
  check('barbed shell hurts a contact attacker', foe.hp === foe.maxhp - Math.floor(foe.baseMaxhp / 16), `hp=${foe.hp}/${foe.maxhp}`);
}

// --- Plated Hide: Storm Throw (always crits, contact) does not crit ---
{
  const a = mon('Snorlax', ['splash']);
  const sim = battle([a], [mon('Throh', ['stormthrow'])], new Map([[a, 'plated_hide']]));
  turn(sim, 'move 1', 'move 1');
  check('plated hide stops contact crits', !/\|-crit\|/.test(log(sim)));
}

// --- Opening Strike: a slower Pokemon's first physical move goes first, once ---
{
  const a = mon('Snorlax', ['tackle']);
  const sim = battle([a], [mon('Jolteon', ['tackle'])], new Map([[a, 'opening_strike']]));
  turn(sim, 'move 1', 'move 1');
  const moves = sim.log.filter(line => line.startsWith('|move|'));
  check('opening strike moves first', moves[0] && moves[0].startsWith('|move|p1a'), moves.join(' / '));
  const before = sim.log.length;
  turn(sim, 'move 1', 'move 1');
  const second = sim.log.slice(before).filter(line => line.startsWith('|move|'));
  check('opening strike only once', second[0] && second[0].startsWith('|move|p2a'), second.join(' / '));
}

// --- Regrowth: heals 1/16 at the end of the turn ---
{
  const a = mon('Snorlax', ['splash']);
  const sim = battle([a], [mon('Pikachu', ['thunderbolt'])], new Map([[a, 'regrowth']]));
  turn(sim, 'move 1', 'move 1');
  check('regrowth heals at residual', /\|-heal\|p1a/.test(log(sim)));
}

// --- Slipstream: foe Scary Face does nothing ---
{
  const a = mon('Pikachu', ['splash']);
  const sim = battle([a], [mon('Snorlax', ['scaryface'])], new Map([[a, 'slipstream']]));
  turn(sim, 'move 1', 'move 1');
  check('slipstream blocks speed drops', active(sim, 0).boosts.spe === 0);
}

// --- Grit: the first super-effective hit raises both defences ---
{
  const a = mon('Snorlax', ['splash']);
  const sim = battle([a], [mon('Machamp', ['karatechop'])], new Map([[a, 'grit']]));
  turn(sim, 'move 1', 'move 1');
  check('grit raises defences', active(sim, 0).boosts.def === 1 && active(sim, 0).boosts.spd === 1,
    JSON.stringify(active(sim, 0).boosts));
}

// --- Unbroken on a raid boss follows the pool, not simulator HP ---
{
  const boss = mon('Snorlax', ['splash']);
  const sim = new Battle({formatid: 'gen9customgame', seed: [5, 2, 3, 4]});
  sim.sides.length = 2;
  sim.sides.fill(null);
  sim.gameType = 'raid';
  sim.format = Object.assign(Object.create(sim.format), {cobbleAffixes: {[boss.uuid]: 'unbroken'}});
  sim.setPlayer('p1', {name: 'P1', team: [mon('Pikachu', ['growl'])]});
  sim.setPlayer('p2', {name: 'Boss', team: [boss]});
  const stream = new BattleStream();
  stream.battle = sim;
  for (const side of sim.sides) if (sim.requestState === 'teampreview') sim.choose(side.id, 'team 1');
  const bossMon = sim.sides[1].active[0];
  check('raid boss is recognised', affixModule.isRaidBoss(bossMon));
  sim.choose('p1', 'move 1');
  check('raid boss unbroken before the pool breaks', bossMon.boosts.atk === 0, JSON.stringify(bossMon.boosts));
  stream._writeLine('cobbleaffixpool', 'unbroken_ended');
  sim.choose('p1', 'move 1');
  check('raid boss unbroken ends on the Java signal', bossMon.boosts.atk === -1, JSON.stringify(bossMon.boosts));
  check('unknown pool argument is ignored', stream._writeLine('cobbleaffixpool', 'anything') === true);
}

// --- Every affix survives a few real turns on both sides without throwing ---
const MOVES = ['tackle', 'thunderbolt', 'growl', 'toxic', 'swordsdance', 'roar', 'earthquake', 'scald'];
for (const id of affixModule.AFFIXES) {
  for (const seed of [11, 12]) {
    const a = mon('Lapras', MOVES.slice(0, 4));
    const b = mon('Dragonite', MOVES.slice(4, 8));
    let thrown = null;
    try {
      const sim = battle([a, mon('Pikachu', ['tackle'])], [b, mon('Eevee', ['tackle'])],
        new Map([[a, id], [b, id]]), seed);
      for (let t = 0; t < 6 && !sim.ended; t++) {
        for (const side of sim.sides) {
          const req = side.activeRequest;
          if (!req || req.wait) continue;
          if (req.forceSwitch) {
            // Name the slot: this fork's chooseSwitch throws on a bare 'default' for a forced switch.
            const slot = side.pokemon.findIndex((p, i) => i > 0 && !p.fainted && !p.isActive);
            sim.choose(side.id, slot > 0 ? `switch ${slot + 1}` : 'pass');
          }
          else sim.choose(side.id, `move ${1 + ((t + seed) % 4)}`);
        }
      }
    } catch (err) {
      thrown = err;
    }
    check(`${id} runs without throwing (seed ${seed})`, !thrown, thrown && String(thrown.stack).split('\n').slice(0, 3).join(' | '));
  }
}

if (failures) {
  console.error(`renown affixes: ${failures} of ${checks} checks failed`);
  process.exit(1);
}
console.log(`Showdown renown affixes: ${checks} checks passed`);
