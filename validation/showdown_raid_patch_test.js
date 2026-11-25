'use strict';

// Exercises raid-patch.js's exported topology predicates against the REAL Showdown sim modules it
// requires -- not a reimplementation of them. Requiring raid-patch.js also applies every monkeypatch
// in the file (Battle.prototype.add, Side.prototype.chooseSwitch, etc.), so a syntax or load-time
// error anywhere in it fails this test too, not just the two functions asserted below.
//
// This is deliberately narrow: only the exported, pure functions (isRaid, bossIndex, bossSide,
// isBossSide, isPlayerSide, isEliminatedPlayerSide) are tested here, with plain object literals
// standing in for real Battle/Side instances -- the functions are written to accept exactly that
// shape (isRaid does `value?.battle || value`), so a fake is not a guess about their contract, it is
// the contract. The deeper fixes in this file (the >p5 dispatcher, the forced-switch resolver) touch
// real Side/BattleStream internals closely enough that a hand-built fake would risk testing this
// file's own wrong assumptions about those internals rather than the real ones; those stay covered
// only by validation/smoke's live multi-player tests.
//
// Invoked with the extracted-Showdown-plus-raid-patch.js directory as argv[2]; see
// validate_showdown_raid_patch_behavior.py, which builds that directory from the same Cobblemon jar
// the mod ships against.

const path = require('path');

const fixtureDir = process.argv[2];
if (!fixtureDir) {
  console.error('usage: node showdown_raid_patch_test.js <extracted-showdown-dir>');
  process.exit(2);
}

const patch = require(path.join(fixtureDir, 'raid-patch.js'));

let failures = 0;
function check(name, actual, expected) {
  if (actual !== expected) {
    failures++;
    console.error(`FAIL ${name}: expected ${expected}, got ${actual}`);
  }
}

function pokemon({fainted = false, hp = 100} = {}) {
  return {fainted, hp};
}

function battle({gameType, sides}) {
  return {gameType, sides};
}

function side(battleObj, n, extra = {}) {
  return {battle: battleObj, n, pokemon: [], pokemonLeft: 1, raidWithdrawn: false, ...extra};
}

// --- isRaid ---
{
  const raidBattle = battle({gameType: 'raid', sides: []});
  const ordinaryBattle = battle({gameType: 'singles', sides: []});
  check('isRaid(raid battle)', patch.isRaid(raidBattle), true);
  check('isRaid(ordinary battle)', patch.isRaid(ordinaryBattle), false);
  check('isRaid(side of a raid battle)', patch.isRaid({battle: raidBattle}), true);
  check('isRaid(null)', patch.isRaid(null), false);
}

// --- bossIndex / bossSide: last side is always the boss, at any player count ---
for (const playerCount of [1, 2, 3, 4]) {
  const sides = new Array(playerCount + 1).fill(null);
  const raidBattle = battle({gameType: 'raid', sides});
  const bossIdx = patch.bossIndex(raidBattle);
  check(`bossIndex with ${playerCount} player(s)`, bossIdx, playerCount);

  const bossSideObj = side(raidBattle, bossIdx);
  raidBattle.sides[bossIdx] = bossSideObj;
  check(`bossSide resolves the last side (${playerCount} players)`, patch.bossSide(raidBattle), bossSideObj);
  check(`isBossSide true for the last side (${playerCount} players)`, patch.isBossSide(bossSideObj), true);

  for (let n = 0; n < bossIdx; n++) {
    const playerSideObj = side(raidBattle, n);
    raidBattle.sides[n] = playerSideObj;
    check(`isPlayerSide true for slot ${n} of ${playerCount}`, patch.isPlayerSide(playerSideObj), true);
    check(`isBossSide false for slot ${n} of ${playerCount}`, patch.isBossSide(playerSideObj), false);
  }
}

// --- The historically-buggy 4-player (5-side) shape specifically ---
{
  const raidBattle = battle({gameType: 'raid', sides: new Array(5).fill(null)});
  const boss = side(raidBattle, 4);
  raidBattle.sides[4] = boss;
  const players = [0, 1, 2, 3].map(n => {
    const s = side(raidBattle, n);
    raidBattle.sides[n] = s;
    return s;
  });
  check('4-player raid: bossIndex is 4', patch.bossIndex(raidBattle), 4);
  for (const p of players) {
    check(`4-player raid: player slot ${p.n} is a player side`, patch.isPlayerSide(p), true);
  }
  check('4-player raid: boss is not a player side', patch.isPlayerSide(boss), false);
}

// --- isPlayerSide / isBossSide are false outside a raid, regardless of index ---
{
  const ordinaryBattle = battle({gameType: 'singles', sides: new Array(2).fill(null)});
  const s0 = side(ordinaryBattle, 0);
  const s1 = side(ordinaryBattle, 1);
  ordinaryBattle.sides[0] = s0;
  ordinaryBattle.sides[1] = s1;
  check('isPlayerSide false outside a raid', patch.isPlayerSide(s0), false);
  check('isBossSide false outside a raid (last side)', patch.isBossSide(s1), false);
}

// --- isEliminatedPlayerSide ---
{
  const raidBattle = battle({gameType: 'raid', sides: new Array(3).fill(null)});
  raidBattle.sides[2] = side(raidBattle, 2);

  const zeroLeft = side(raidBattle, 0, {pokemonLeft: 0, pokemon: [pokemon()]});
  check('eliminated when pokemonLeft is 0', patch.isEliminatedPlayerSide(zeroLeft), true);

  const allFainted = side(raidBattle, 0, {pokemonLeft: 1, pokemon: [pokemon({fainted: true}), pokemon({hp: 0})]});
  check('eliminated when every listed Pokemon is fainted or at 0 HP', patch.isEliminatedPlayerSide(allFainted), true);

  const oneAlive = side(raidBattle, 0, {pokemonLeft: 1, pokemon: [pokemon({fainted: true}), pokemon({hp: 50})]});
  check('not eliminated with one live Pokemon left', patch.isEliminatedPlayerSide(oneAlive), false);

  const bossNeverEliminated = side(raidBattle, 2, {pokemonLeft: 0, pokemon: [pokemon({fainted: true})]});
  check('the boss side is never "eliminated" (it is not a player side)',
      patch.isEliminatedPlayerSide(bossNeverEliminated), false);
}

// --- bossLateMoveUnusable: a late AI choice for a move the boss cannot use must be dropped ---
// Pure predicate over a side's moveSlots and request, so plain object literals are the contract.
{
  const boss = (slots, requestMoves) => ({
    active: [{fainted: false, moveSlots: slots}],
    activeRequest: requestMoves ? {active: [{moves: requestMoves}]} : null,
  });
  const slots = [{id: 'splash', disabled: true}, {id: 'tackle', disabled: false}, {id: 'ember', disabled: 'hidden'}];
  check('unusable: visibly disabled slot', patch.bossLateMoveUnusable(boss(slots), 'move 1'), true);
  check('usable: enabled slot', patch.bossLateMoveUnusable(boss(slots), 'move 2'), false);
  check('unusable: hidden-disabled slot (Imprison)', patch.bossLateMoveUnusable(boss(slots), 'move 3'), true);
  check('unusable: no such slot', patch.bossLateMoveUnusable(boss(slots), 'move 9'), true);
  check('unusable: disabled in the request only',
      patch.bossLateMoveUnusable(boss(slots, [{}, {disabled: true}, {}]), 'move 2'), true);
  check('usable by name', patch.bossLateMoveUnusable(boss(slots), 'move tackle'), false);
  check('unusable by name', patch.bossLateMoveUnusable(boss(slots), 'move splash'), true);
  check('a target suffix does not change the verdict', patch.bossLateMoveUnusable(boss(slots), 'move 2 1'), false);
  check('a switch is never judged here', patch.bossLateMoveUnusable(boss(slots), 'switch 2'), false);
  check('"default" is never judged here', patch.bossLateMoveUnusable(boss(slots), 'default'), false);
  check('a fainted boss is never judged here',
      patch.bossLateMoveUnusable({active: [{fainted: true, moveSlots: slots}]}, 'move 1'), false);
}

// --- the real dispatcher + the real sim: Taunt must not leave the boss with no choice ---
// Reproduced on 2026-09-30 from a live raid freeze ("[Unavailable choice] Can't move: Blaziken's
// Flare Blitz is disabled"): Showdown's choose() clears the boss's valid pre-fill BEFORE it validates
// the late AI choice, so a choice for a taunted/disabled move left the boss with none and the turn
// waited forever. Asserts presence of the surviving choice, not just the absence of an error.
{
  const {Battle} = require(path.join(fixtureDir, 'sim/index.js'));
  const {BattleStream} = require(path.join(fixtureDir, 'sim/battle-stream'));
  const set = (species, moves) => ({
    name: species, species, level: 100, gender: 'M', moves,
    movesInfo: moves.map(() => ({pp: 40, maxPp: 40})), ability: 'Pressure', item: '', nature: 'Hardy',
    evs: {hp: 85, atk: 85, def: 85, spa: 85, spd: 85, spe: 85},
    ivs: {hp: 31, atk: 31, def: 31, spa: 31, spd: 31, spe: 31},
  });
  const sim = new Battle({formatid: 'gen9customgame'});
  sim.gameType = 'raid';
  sim.setPlayer('p1', {team: [set('Regieleki', ['taunt', 'tackle'])]});
  sim.setPlayer('p2', {team: [set('Arceus', ['splash', 'tackle'])]});
  if (sim.requestState === 'teampreview') {
    sim.choose('p1', 'team 1');
    sim.choose('p2', 'team 1');
  }
  const stream = new BattleStream();
  stream.battle = sim;
  const errors = [];
  const send = sim.send.bind(sim);
  sim.send = (type, data) => {
    if (/\|error\|/.test(String(data))) errors.push(String(data));
    return send(type, data);
  };
  const chosen = () => sim.p2.choice.actions.map(action => action.moveid).join(',');

  sim.choose('p1', 'move 1'); // Taunt: splash is now disabled for the boss
  check('precondition: the boss pre-filled a usable move', chosen(), 'tackle');
  stream._writeLine('p2', 'move 1'); // the AI names the taunted move
  check('a late choice for a disabled move leaves the pre-filled choice in place', chosen(), 'tackle');
  check('the boss still has a complete choice', sim.p2.isChoiceDone(), true);
  check('no |error| is emitted for the dropped choice', errors.length, 0);

  stream._writeLine('p2', 'move 2'); // a valid late choice must still be applied as before
  check('a valid late choice is still applied', chosen(), 'tackle');
  const before = sim.turn;
  sim.choose('p1', 'move 2');
  check('the turn advances rather than waiting on the boss', sim.turn, before + 1);
}

// --- the real sim, installed as on a server: the boss's simulated HP mirrors the raid pool ---
// Audit of 2026-11-25. The boss used to be pinned at full simulated HP with every hit swallowed, and
// everything that READ boss HP or "damage dealt" was wrong: Struggle recoil drew a ~15% boss as 75%,
// drain/Leech Seed/Shell Bell healed nothing, player recoil was free, boss Counter hit for 1, Bide
// failed, Brine never doubled, Sitrus never ate, Multiscale never held. And Cobblemon's alphaboost
// was gone from every battle, because our conditions.js had been copied over Cobblemon's.
// These battles use Cobblemon's own format mod, so the merged raidboss condition is really in play.
{
  const {Battle} = require(path.join(fixtureDir, 'sim/battle'));
  const {BattleStream} = require(path.join(fixtureDir, 'sim/battle-stream'));
  const {Format} = require(path.join(fixtureDir, 'sim/dex-formats'));
  const mon = (species, moves, o = {}) => ({
    name: species, species, level: o.level || 100, gender: '', moves,
    movesInfo: moves.map(() => ({pp: 40, maxPp: 40})), ability: o.ability || 'No Ability', item: o.item || '',
    nature: 'Hardy', evs: {hp: 85, atk: 85, def: 85, spa: 85, spd: 85, spe: 85},
    ivs: {hp: 31, atk: 31, def: 31, spa: 31, spd: 31, spe: o.spe ?? 31},
  });
  /** A one-player raid, with a Java-side pool that answers every -raid* line with >raidpool. */
  function raid(player, boss, {poolMax = 1000, seed = [1, 2, 3, 4]} = {}) {
    const format = new Format({name: '[Gen 9] Cobblemon Raid', mod: 'cobblemon', gameType: 'raid',
      effectType: 'Format', ruleset: [], playerCount: 2});
    const sim = new Battle({format, seed});
    sim.setPlayer('p1', {team: [player]});
    sim.setPlayer('p2', {team: [boss]});
    const stream = new BattleStream();
    stream.battle = sim;
    const pool = {current: poolMax, max: poolMax};
    const sync = () => stream._writeLine('raidpool', `${pool.current} ${pool.max}`);
    sync(); // RaidFactory sends the pool as soon as the session exists
    return {sim, pool, stream, sync,
      setPool(current) { pool.current = current; sync(); },
      turn(pMove, bMove, setup) {
        if (setup) setup(sim);
        const from = sim.log.length;
        if (bMove) sim.choose('p2', bMove);
        sim.choose('p1', pMove);
        const lines = sim.log.slice(from);
        for (const line of lines) { // RaidPoolInstruction: apply, then RaidPoolSync
          const parts = line.split('|');
          if (parts[1] === '-raiddamage') pool.current = Math.max(0, pool.current - Number(parts[3]));
          if (parts[1] === '-raidheal') pool.current = Math.min(pool.max, pool.current + Number(parts[3]));
        }
        sync();
        return lines;
      },
      boss: () => sim.p2.active[0], player: () => sim.p1.active[0]};
  }
  const amounts = (lines, kind) => lines.filter(l => l.startsWith(`|${kind}|p2a:`)).map(l => Number(l.split('|')[3]));
  const sum = list => list.reduce((a, b) => a + b, 0);
  /** Every vanilla line that would put SIMULATOR health on the raid bar. None may ever be about the boss. */
  const barLeaks = lines => lines.filter(l => /^\|(-damage|-heal|-sethp)\|p2a:/.test(l));
  const allLines = [];
  const keep = lines => { allLines.push(...lines); return lines; };

  // Install: both Cobblemon's and our condition are in the cobblemon format mod.
  {
    const {sim} = raid(mon('Blissey', ['splash']), mon('Snorlax', ['splash']));
    check('install: raidboss exists in the cobblemon format mod', sim.dex.conditions.get('raidboss').exists, true);
    check('install: Cobblemon\'s alphaboost survives beside it', sim.dex.conditions.get('alphaboost').exists, true);
    check('install: Guardian Scale is in the item dex', sim.dex.items.get('cobbleraidsguardianscale').exists, true);
    check('install: the boss carries raidboss', !!sim.p2.active[0].volatiles.raidboss, true);
  }

  // The reported bug: Struggle recoil.
  {
    const r = raid(mon('Blissey', ['splash']), mon('Tapu Bulu', ['splash']));
    const lines = keep(r.turn('move 1', 'move 1', s => { s.p2.active[0].moveSlots.forEach(m => m.pp = 0); s.makeRequest('move'); }));
    check('struggle: the boss struggled', lines.some(l => /\|move\|p2a: [^|]*\|Struggle\|/.test(l)), true);
    const recoil = lines.filter(l => l.startsWith('|-raiddamage|p2a:'));
    check('struggle: recoil reported to the pool once', recoil.length, 1);
    check('struggle: a quarter of the boss\'s simulated max HP', Number((recoil[0] || '').split('|')[3]),
        Math.round(r.boss().baseMaxhp / 4));
    check('struggle: no source, so the boss is never credited for hurting itself', (recoil[0] || '').split('|').length, 4);
  }

  // Healing from damage dealt reaches the player.
  for (const [name, player, boss] of [
    ['giga drain', mon('Venusaur', ['gigadrain']), mon('Swampert', ['splash'])],
    ['drain punch', mon('Conkeldurr', ['drainpunch']), mon('Blissey', ['splash'])],
    ['shell bell', mon('Garchomp', ['dragonclaw'], {item: 'Shell Bell'}), mon('Blissey', ['splash'])],
  ]) {
    const r = raid(player, boss);
    keep(r.turn('move 1', 'move 1', s => { s.p1.active[0].hp = 10; }));
    check(`${name}: heals the player`, r.player().hp > 10, true);
  }
  {
    const r = raid(mon('Ferrothorn', ['leechseed', 'splash']), mon('Garchomp', ['splash']));
    keep(r.turn('move 1', 'move 1'));
    keep(r.turn('move 2', 'move 1', s => { s.p1.active[0].hp = 10; }));
    check('leech seed: drains the boss into the player', r.player().hp > 10, true);
  }

  // Recoil from damage dealt hurts the player.
  {
    const r = raid(mon('Staraptor', ['bravebird']), mon('Blissey', ['splash']));
    keep(r.turn('move 1', 'move 1'));
    check('brave bird: the player takes recoil', r.player().hp < r.player().maxhp, true);
  }

  // Counters answer with the damage taken, not 1.
  for (const [name, attacker, move] of [
    ['counter', mon('Garchomp', ['dragonclaw']), 'counter'],
    ['mirror coat', mon('Kyogre', ['surf']), 'mirrorcoat'],
    ['metal burst', mon('Garchomp', ['dragonclaw']), 'metalburst'],
  ]) {
    const r = raid(attacker, mon('Wobbuffet', [move], {spe: 0}));
    keep(r.turn('move 1', 'move 1'));
    check(`${name}: the boss hits back for more than 1`, r.player().maxhp - r.player().hp > 1, true);
  }
  {
    const r = raid(mon('Garchomp', ['dragonclaw']), mon('Snorlax', ['bide'], {spe: 0}));
    const lines = [...keep(r.turn('move 1', 'move 1')), ...keep(r.turn('move 1', 'move 1')), ...keep(r.turn('move 1', 'move 1'))];
    check('bide: does not fail', lines.some(l => l.startsWith('|-fail|p2a:')), false);
    check('bide: unleashes damage on the player', r.player().hp < r.player().maxhp, true);
  }

  // The mirror: simulated HP is the pool's fraction, and stays out of a simulator faint.
  {
    const r = raid(mon('Blissey', ['splash']), mon('Snorlax', ['splash']), {poolMax: 1000});
    check('mirror: a full pool is full HP', r.boss().hp, r.boss().maxhp);
    r.setPool(250);
    check('mirror: a quarter pool is a quarter of HP', r.boss().hp, Math.floor(r.boss().maxhp * 0.25));
    r.setPool(999);
    check('mirror: anything below a full pool is below full HP', r.boss().hp, r.boss().maxhp - 1);
    r.setPool(0);
    check('mirror: an empty pool never faints the simulator (Java ends the raid)', r.boss().hp, 1);
  }
  {
    const r = raid(mon('Kyogre', ['waterspout']), mon('Shuckle', ['splash'], {level: 5}), {poolMax: 100000});
    r.setPool(10);
    const lines = keep(r.turn('move 1', 'move 1'));
    check('mirror: a hit bigger than the mirror is reported in full', sum(amounts(lines, '-raiddamage')) > r.boss().maxhp, true);
    check('mirror: and the simulator boss is still standing', !r.boss().fainted && r.boss().hp >= 1, true);
  }

  // Effects that read boss HP read the pool.
  {
    const hit = poolNow => {
      const r = raid(mon('Kingdra', ['brine']), mon('Blissey', ['splash']), {poolMax: 10000});
      r.setPool(poolNow);
      return sum(amounts(keep(r.turn('move 1', 'move 1')), '-raiddamage'));
    };
    check('brine: doubles once the pool is at half or less', hit(4000) > 1.6 * hit(10000), true);
  }
  {
    const hit = poolNow => {
      const r = raid(mon('Blissey', ['splash']), mon('Typhlosion', ['eruption']), {poolMax: 10000});
      r.setPool(poolNow);
      const p = r.player(); const before = p.hp; keep(r.turn('move 1', 'move 1')); return before - p.hp;
    };
    check('eruption: weaker on a low pool', hit(2000) < 0.5 * hit(10000), true);
  }
  {
    const hit = poolNow => {
      const r = raid(mon('Garchomp', ['dragonclaw']), mon('Dragonite', ['splash'], {ability: 'Multiscale'}), {poolMax: 10000});
      r.setPool(poolNow);
      return sum(amounts(keep(r.turn('move 1', 'move 1')), '-raiddamage'));
    };
    check('multiscale: halves the hit only at a full pool', hit(10000) < 0.6 * hit(9000), true);
  }
  {
    const r = raid(mon('Blissey', ['splash']), mon('Snorlax', ['splash'], {item: 'Sitrus Berry'}), {poolMax: 10000});
    r.setPool(4000);
    const lines = keep(r.turn('move 1', 'move 1'));
    check('sitrus: eaten once the pool is at half or less', amounts(lines, '-raidheal').length, 1);
  }
  {
    const r = raid(mon('Blissey', ['splash']), mon('Snorlax', ['recover']), {poolMax: 10000});
    const full = keep(r.turn('move 1', 'move 1'));
    check('recover: fails at a really full pool', full.some(l => l.startsWith('|-fail|p2a:')), true);
    r.setPool(5000);
    const half = keep(r.turn('move 1', 'move 1'));
    check('recover: heals the pool below full', amounts(half, '-raidheal').length, 1);
  }

  // An ability that swallows the hit before raidboss runs: the pool is untouched, and Showdown's
  // `-damage <boss> 272/272` must not draw the boss back at 100% (fuzz seed 41).
  {
    const r = raid(mon('Garchomp', ['earthquake']), mon('Mimikyu', ['splash'], {ability: 'Disguise'}), {poolMax: 10000});
    r.setPool(5000);
    const lines = keep(r.turn('move 1', 'move 1'));
    check('disguise: the first hit is blocked', lines.some(l => /\|-activate\|p2a: [^|]*\|ability: Disguise/.test(l)), true);
  }

  check('no line in any of these battles put simulator health on the raid bar', barLeaks(allLines).join(' / '), '');

  // A player's own direct damage is untouched.
  {
    const r = raid(mon('Blissey', ['splash']), mon('Snorlax', ['splash']));
    const player = r.player();
    const before = player.hp;
    r.sim.directDamage(10, player, player, {id: 'strugglerecoil'});
    check('a player\'s direct damage still lowers its HP', player.hp, before - 10);
    check('a player\'s direct damage still logs vanilla -damage', r.sim.log[r.sim.log.length - 1].startsWith('|-damage|p1a:'), true);
  }
}

if (failures > 0) {
  console.error(`${failures} assertion(s) failed`);
  process.exit(1);
}
console.log('raid-patch.js topology predicates, late boss choices and the pool-mirrored boss: PASS');
