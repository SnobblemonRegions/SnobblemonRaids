/* Diagnostic-only offline replay. No Minecraft process, actors, network or rewards. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const {createRequire} = require('node:module');
const {createHash, randomUUID} = require('node:crypto');
const {performance} = require('node:perf_hooks');

async function main() {
  const [rigArg, recordingArg, speciesArg] = process.argv.slice(2);
  if (!rigArg || !recordingArg || !speciesArg) throw new Error('Usage: node showdown_runtime_replay.js <capture-rig> <recording> <species-export>');
  const rig = fs.realpathSync(rigArg), recording = fs.realpathSync(recordingArg), speciesFile = fs.realpathSync(speciesArg);
  const manifest = JSON.parse(fs.readFileSync(path.join(rig, 'rehearsal.json'), 'utf8'));
  const exportManifest = JSON.parse(fs.readFileSync(path.join(path.dirname(speciesFile), '..', 'rehearsal.json'), 'utf8'));
  assert.equal(manifest.throwaway, true); assert.equal(exportManifest.throwaway, true);
  assert.equal(path.dirname(recording), path.join(rig, 'logs'));
  assert.equal(manifest.jar_sha256, exportManifest.jar_sha256, 'gameplay candidate must match');
  const gameplayMods = m => m.mods.filter(mod => !mod.name.startsWith('worldboss-perf-probe')).map(mod => [mod.name, mod.sha256]).sort();
  assert.deepEqual(gameplayMods(manifest), gameplayMods(exportManifest), 'gameplay dependencies must match');
  const native = JSON.parse(fs.readFileSync(recording, 'utf8')).bootstrapTrace;
  assert.equal(native.status, 'complete');
  const headers = native.nativeMessages;
  assert.equal(headers.length, 3);
  assert.equal(JSON.parse(headers[0].slice(7)).format.gameType, 'raid');
  const species = JSON.parse(fs.readFileSync(speciesFile, 'utf8'));
  assert.ok(Array.isArray(species) && species.length > 2);
  const showdown = path.join(rig, 'showdown'), indexFile = path.join(showdown, 'index.js');
  const hash = data => createHash('sha256').update(data).digest('hex');
  const sourceHash = createHash('sha256');
  const visit = dir => {
    for (const entry of fs.readdirSync(dir, {withFileTypes: true}).sort((a, b) => a.name.localeCompare(b.name))) {
      const file = path.join(dir, entry.name);
      assert.equal(entry.isSymbolicLink(), false, 'replay source cannot escape through symlinks');
      if (entry.isDirectory()) visit(file);
      else if (entry.name.endsWith('.js')) sourceHash.update(path.relative(showdown, file).replaceAll('\\', '/') + '\0').update(fs.readFileSync(file));
    }
  };
  visit(showdown);
  // Keep mod console messages out of the machine-readable artifact output.
  console.log = (...args) => process.stderr.write(args.join(' ') + '\n');
  const module = {exports: {}};
  const api = vm.compileFunction(fs.readFileSync(indexFile, 'utf8') +
    '\nreturn {startBattle,endBattle,receiveData,afterSpeciesInit,hasBattle:id=>battleMap.has(id),battle:id=>battleMap.get(id)?.battle};',
    ['require', 'module', 'exports', '__filename', '__dirname'], {filename: indexFile})
    (createRequire(indexFile), module, module.exports, indexFile, showdown);
  api.receiveData(JSON.stringify(species), 'species');
  api.afterSpeciesInit();
  const requests = messages => {
    const result = {};
    for (const message of messages) {
      const request = message.split('\n').find(line => line.startsWith('|request|'));
      if (request) { const body = JSON.parse(request.slice(9)); result[body.side.id] = body; }
    }
    return result;
  };
  const expected = requests(native.nativeOutputMessages);
  assert.deepEqual(Object.keys(expected).sort(), ['p1', 'p2']);
  const rows = [];
  for (let iteration = 0; iteration < 5; iteration++) {
    const id = 'offline-replay:' + iteration, outputs = [], errors = [];
    const sink = {sendFromShowdown(battleId, message) { assert.equal(battleId, id); outputs.push(message); }, log(message) { errors.push(message); }};
    try {
      const cpu = process.cpuUsage(), started = performance.now();
      api.startBattle(sink, id, headers.slice());
      const syncMs = performance.now() - started;
      await new Promise(setImmediate);
      const completeMs = performance.now() - started, used = process.cpuUsage(cpu);
      assert.deepEqual(errors, [], 'simulator startup must not catch and hide errors');
      assert.ok(outputs.length > 0 && !outputs.some(message => message.includes('|error|')));
      assert.deepEqual(requests(outputs), expected, 'both initial requests must match the real native capture');
      assert.equal(api.battle(id).turn, 1);
      rows.push({iteration, sync_ms: syncMs, output_complete_ms: completeMs, process_cpu_ms: (used.user + used.system) / 1000,
        output_count: outputs.length, native_requests_match: true});
    } finally { api.endBattle(id); await new Promise(setImmediate); assert.equal(api.hasBattle(id), false); }
  }
  const result = {scope: 'OFFLINE SIMULATOR ONLY; no Java dispatch, no capacity or production approval',
    node: process.version, v8: process.versions.v8, executable: process.execPath,
    main_jar_sha256: manifest.jar_sha256, recording, species_file: speciesFile,
    species_count: species.length, species_sha256: hash(fs.readFileSync(speciesFile)), source_js_sha256: sourceHash.digest('hex'),
    replay_script_sha256: hash(fs.readFileSync(__filename)), native_boundary_wall_ms: native.nativeCallWallMs,
    native_outside_callbacks_wall_ms: native.outsideHostCallbacksWallMs, rows};
  const out = path.join(rig, 'logs', 'wbperf-node-replay-' + randomUUID() + '.json');
  fs.writeFileSync(out, JSON.stringify(result, null, 2) + '\n');
  process.stdout.write(JSON.stringify({artifact: out, rows}) + '\n');
}
main().catch(error => { process.stderr.write(error.stack + '\n'); process.exitCode = 1; });
