const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/assets/cobbleraids/showdown/battle-warmup.js'), 'utf8');

function execute(failTurn = false, finishAfterFirst = false, suppressOutput = false, failHost = false) {
  let created = 0, destroyed = 0, choices = 0, hostCalls = 0, minCalls = 0, privateOutputs = 0;
  const formats = [], teams = [];
  class NativeStreamContract {
    constructor(options) {
      assert.equal(options.noCatch, true);
      assert.equal(options.keepAlive, true);
      this.battle = null; this.sides = 0;
    }
    _write(input) {
      if (input.includes('\n')) { input.split('\n').forEach(line => this._write(line)); return; }
      if (input.startsWith('>start ')) {
        const options = JSON.parse(input.slice(7));
        formats.push(options.format);
        assert.equal(options.format.gameType, 'raid');
        assert.equal(options.format.playerCount, 2);
        assert.equal(options.send, undefined, 'no native output callback may be supplied');
        this.battle = {turn: 1, ended: false, sides: []}; created++;
      } else if (input.startsWith('>player ')) {
        const side = JSON.parse(input.slice(input.indexOf(' ', 8) + 1));
        teams.push(side.team);
        assert.equal(typeof side.team, 'string', 'exercise the native packed-team decoder');
        const fields = side.team.split('|');
        assert.equal(fields.length, 17); assert.match(fields[2], /^[0-9a-f-]{36}$/);
        const pokemon = {uuid: fields[2], moves: fields[8].split(','), movesInfo: fields[9].split(',').map(pair => {
          const [pp, maxPp] = pair.split('/').map(Number); return {pp, maxPp};
        })};
        this.battle.sides.push({pokemon: [pokemon]});
        for (const pokemon of this.battle.sides.at(-1).pokemon) {
          // Cobblemon 1.8.2's actual Pokemon constructor indexes set.movesInfo[i].pp/maxPp.
          assert.equal(pokemon.movesInfo?.length, pokemon.moves.length, 'native move PP metadata is required');
          pokemon.movesInfo.forEach(info => assert.ok(info.pp > 0 && info.maxPp >= info.pp));
        }
        this.sides++;
      } else {
        assert.ok(input === '>p1 move 1' || input === '>p2 move 1');
        assert.equal(this.battle.ended, false, 'raid boss choice is automatic; no input may follow a terminal player choice');
        if (input.startsWith('>p2')) return;
        assert.equal(this.sides, 2); choices++;
        if (failTurn) throw new Error('turn rejected');
        this.battle.turn++;
        if (finishAfterFirst) this.battle.ended = true;
      }
    }
    _writeEnd() { if (this.battle) destroyed++; }
  }
  const battleMap = new Map();
  const host = {sendFromShowdown(id, message) {
    if (id.startsWith('cobbleraids:startup:')) { privateOutputs++; return; }
    assert.equal(id, '00000000-0000-0000-0000-000000000000');
    assert.equal(message, '{"winner":""}', 'only the native ignored-summary branch may be exercised');
    hostCalls++;
    if (failHost) throw new Error('native callback unavailable');
  }};
  const packets = [Array.from({length: 3}, () => new Array(3)), Array.from({length: 24}, () => new Array(1))];
  const sandbox = {
    battleMap,
    Math: Object.assign(Object.create(Math), {min(...args) { minCalls++; return Math.min(...args); }}),
    __cobbleraidsStartupBridge: [packets, host],
    startBattle(sink, id, lines) {
      assert.ok(id.startsWith('cobbleraids:startup:'));
      const stream = new NativeStreamContract({noCatch: true, keepAlive: true});
      stream.sink = sink; battleMap.set(id, stream);
      lines.forEach(line => stream._write(line));
      if (!suppressOutput) sink.sendFromShowdown(id, 'update\nlocal only');
    },
    sendBattleMessage(id, lines) { lines.forEach(line => battleMap.get(id)._write(line)); },
    endBattle(id) { const stream = battleMap.get(id); if (stream) stream._writeEnd(); battleMap.delete(id); }
  };
  const context = vm.createContext(sandbox);
  const run = () => {
    const status = vm.runInContext(script, context, {timeout: 1000});
    for (let run = 0; run < 3; run++) {
      const id = 'cobbleraids:startup:' + run;
      try {
        sandbox.startBattle(host, id, packets[0][run]);
        for (let turn = 0; turn < 8 && status(run) > 0; turn++) sandbox.sendBattleMessage(id, packets[1][run * 8 + turn]);
        assert.ok(Math.abs(status(run)) >= 2);
      } finally { sandbox.endBattle(id); }
    }
    if (privateOutputs < 3) throw new Error('Simulator startup output failed');
    assert.equal(battleMap.size, 0, 'all temporary simulator identities are removed');
    assert.equal(sandbox.__cobbleraidsStartupWarmupVerify, undefined, 'startup verifier is removed');
    return choices;
  };
  return {run, counts: () => ({created, destroyed, choices}), hostCalls: () => hostCalls, minCalls: () => minCalls, formats, teams};
}

test('native PP contract, bounded work and cleanup without a native output callback', () => {
  const fixture = execute();
  assert.equal(fixture.run(), 24);
  assert.deepEqual(fixture.counts(), {created: 3, destroyed: 3, choices: 24});
});

test('a failed turn always destroys the completed simulator object', () => {
  const fixture = execute(true);
  assert.throws(fixture.run, /turn rejected/);
  assert.deepEqual(fixture.counts(), {created: 1, destroyed: 1, choices: 1});
});

test('automatic boss choice can end a raid on the first player input without a stale second input', () => {
  const fixture = execute(false, true);
  assert.equal(fixture.run(), 3);
  assert.deepEqual(fixture.counts(), {created: 3, destroyed: 3, choices: 3});
});

test('missing output delivery cannot publish startup success', () => {
  const fixture = execute(false, false, true);
  assert.throws(fixture.run, /Simulator startup output failed/);
  assert.deepEqual(fixture.counts(), {created: 3, destroyed: 3, choices: 24});
});

test('the real host callback is warmed once using only an ignored summary', () => {
  const fixture = execute();
  fixture.run();
  assert.equal(fixture.hostCalls(), 1);
});

test('a failed host callback cannot publish startup success or create simulator entries', () => {
  const fixture = execute(false, false, false, true);
  assert.throws(fixture.run, /native callback unavailable/);
  assert.deepEqual(fixture.counts(), {created: 0, destroyed: 0, choices: 0});
});

test('the observed lazy math builtin is initialized before native admission', () => {
  const fixture = execute();
  fixture.run();
  assert.equal(fixture.minCalls(), 1);
});

test('startup exercises native packed teams and UUID-bound optional effects instead of skipping their parsers', () => {
  const fixture = execute(); fixture.run();
  assert.equal(fixture.teams.length, 6);
  assert.ok(fixture.teams.every(team => typeof team === 'string'), 'arrays bypass native Teams.unpack');
  fixture.formats.forEach((format, index) => {
    assert.equal(format.gen, 9); assert.equal(format.effectType, 'Format');
    assert.equal(format.ascensionFx.v, 1);
    const ids = fixture.teams.slice(index * 2, index * 2 + 2).map(team => team.split('|')[2]);
    assert.deepEqual(Object.keys(format.ascensionFx.mons).sort(), ids.sort());
    assert.ok(Object.values(format.ascensionFx.mons).every(effects => effects.length > 0));
  });
  assert.equal(new Set(fixture.teams.map(team => team.split('|')[2])).size, 6);
});
