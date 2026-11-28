/* Prepare native wire data; Java invokes the actual host-bound functions and owns output isolation. */
(() => {
  'use strict';
  const bridge = globalThis.__cobbleraidsStartupBridge;
  if (typeof startBattle !== 'function' || typeof sendBattleMessage !== 'function' ||
      typeof endBattle !== 'function' || bridge?.length !== 2 || bridge[0]?.length !== 2 ||
      typeof bridge[1]?.sendFromShowdown !== 'function') throw new Error('Unsupported simulator startup bridge');
  if (Math.min(1, 2) !== 1) throw new Error('Simulator startup numeric warmup failed');
  bridge[1].sendFromShowdown('00000000-0000-0000-0000-000000000000', '{"winner":""}');
  const packed = (species, uuid, hp, ability, moves, pp, nature, gender, ivs, level, tera) =>
    [species, '', uuid, hp, '', -1, '', ability, moves.join(','), pp.map(p => p + '/' + p).join(','),
      nature, '0,0,0,0,0,0', gender, ivs, '', level, '50,pokeball,,,0,' + tera + ','].join('|');
  const identities = [];
  for (let run = 0; run < 3; run++) {
    const id = 'cobbleraids:startup:' + run;
    if (battleMap.has(id)) throw new Error('Simulator startup identity already in use');
    const uuids = [1, 2].map(index => '00000000-0000-4000-8000-' + String(run * 2 + index).padStart(12, '0'));
    identities.push(uuids);
    // Representative optional v1 effects, never player storage or live difficulty settings.
    const ascensionFx = {v: 1, caps: {out: 100, inc: 50, heal: 50, res: 100}, mons: {
      [uuids[0]]: [{i: 'last_stand', p: 12}, {i: 'momentum', p: 7}, {i: 'type_ward', p: 6, t: 'Rock'}],
      [uuids[1]]: [{i: 'swift_strike', p: 10}, {i: 'physical_force', p: 9}, {i: 'last_stand', p: 28},
        {i: 'restorative', p: 24}, {i: 'opening_guard', p: 22}]
    }};
    const lines = [
      '>start ' + JSON.stringify({format: {mod: 'cobblemon', gameType: 'raid', gen: 9,
        ruleset: [], effectType: 'Format', playerCount: 2, ascensionFx}}),
      '>player p1 ' + JSON.stringify({name: uuids[0], team: packed('glaceon', uuids[0], 240, 'snowcloak',
        ['freezedry', 'blizzard', 'mirrorcoat', 'lastresort'], [20, 5, 20, 5], 'lax', 'M', '0,5,27,0,10,24', 100, 'Ice')}),
      '>player p2 ' + JSON.stringify({name: uuids[1], team: packed('giratina', uuids[1], 290, 'pressure',
        ['shadowball', 'dragonpulse', 'calmmind'], [15, 10, 20], 'hardy', 'N', '1,7,19,21,27,27', 70, 'Fire')})
    ];
    if (bridge[0][0][run]?.length !== 3) throw new Error('Invalid startup header budget');
    lines.forEach((line, index) => { bridge[0][0][run][index] = line; });
    for (let turn = 0; turn < 8; turn++) bridge[0][1][run * 8 + turn][0] = '>p1 move 1';
  }
  return function startupStatus(run) {
    const battle = battleMap.get('cobbleraids:startup:' + run)?.battle;
    if (!battle || battle.sides.some((side, index) => side.pokemon[0]?.uuid !== identities[run][index])) {
      throw new Error('Simulator startup packed-team identity mismatch');
    }
    return battle.ended ? -battle.turn : battle.turn;
  };
})()
