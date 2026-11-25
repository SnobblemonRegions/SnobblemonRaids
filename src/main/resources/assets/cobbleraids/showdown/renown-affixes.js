'use strict';

/**
 * Renown affixes: the one extra power a renowned raid boss fights with, and keeps when it is caught.
 *
 * Which Pokemon have one arrives from Java as `cobbleAffixes` on the battle's format object -- Pokemon
 * UUID to affix id (RenownAffixBattle.java). Nothing else can turn one on: the map is built on the server
 * from the Pokemon's own saved data, and an id this file does not know is ignored.
 *
 * Each affix is an effect object attached to its Pokemon by wrapping Battle#findPokemonEventHandlers, the
 * one place Showdown gathers a Pokemon's handlers (status, volatiles, ability, item, species). That makes
 * an affix behave like an innate ability that is not an ability: Gastro Acid, Neutralizing Gas, Skill Swap
 * and Mold Breaker never touch it, and it survives switching out because its state lives on the Pokemon.
 *
 * Raid bosses: Showdown keeps a raid boss pinned at full HP (raid-patch.js), so the affixes that read HP
 * act differently on one. Last Stand and Second Wind run on the shared pool in Java instead
 * (RaidPoolAffix.java) and do nothing here; Unbroken asks Java whether the pool has fallen below half,
 * which Java answers with `>cobbleaffixpool unbroken_ended`; Regrowth heals the pool through the patched
 * Battle#heal only on a turn nobody damaged the boss.
 *
 * Every id below must match RenownAffix.java; RenownAffixTest checks the two lists are the same.
 * Loaded by raid-patch.js inside its own try/catch: a fault here costs battles their affixes, never more.
 */

const {Battle} = require('./sim/battle');
const {BattleStream} = require('./sim/battle-stream');

const isRaidBoss = pokemon => {
  const battle = pokemon && pokemon.battle;
  return !!battle && battle.gameType === 'raid' && pokemon.side && pokemon.side.n === battle.sides.length - 1;
};
const label = pokemon => (pokemon && (pokemon.name || (pokemon.species && pokemon.species.name))) || 'It';
const announce = (battle, pokemon, text) => battle.add('-message', `${label(pokemon)}'s ${text}`);
const isPhysical = move => !!move && move.category === 'Physical';
const isSpecial = move => !!move && move.category === 'Special';
const makesContact = (battle, move, attacker, defender) =>
  !!move && !!move.flags && !!move.flags['contact'] && battle.checkMoveMakesContact(move, attacker, defender);
const foeDidLower = (target, source) => !!source && source !== target;

/** Removes every lowered stat in `boost` that `keep` does not allow; true when it removed any. */
function blockDrops(boost, keep) {
  let blocked = false;
  for (const stat in boost) {
    if (boost[stat] < 0 && !keep(stat)) {
      delete boost[stat];
      blocked = true;
    }
  }
  return blocked;
}

const AFFIXES = {
  // ---------------------------------------------------------------- Attack
  bloodrush: {
    name: 'Bloodrush',
    onSourceAfterFaint(length, target, source, effect) {
      if (this.effectState.used || !effect || effect.effectType !== 'Move') return;
      this.effectState.used = true;
      announce(this, source, 'Bloodrush!');
      this.boost({atk: 1, spe: 1}, source, source);
    },
  },
  opening_strike: {
    name: 'Opening Strike',
    onModifyPriority(priority, source, target, move) {
      if (!this.effectState.used && isPhysical(move)) return priority + 1;
    },
    onBeforeMove(pokemon, target, move) {
      if (isPhysical(move)) this.effectState.used = true;
    },
  },
  sundering_blows: {
    name: 'Sundering Blows',
    onSourceDamagingHit(damage, target, source, move) {
      if (!target || target.fainted || !makesContact(this, move, source, target)) return;
      if (this.randomChance(2, 10)) this.boost({def: -1}, target, source);
    },
  },
  vendetta: {
    name: 'Vendetta',
    onDamagingHit(damage, target, source, move) {
      if (source && source !== target) this.effectState.lastAttacker = source;
    },
    onModifyCritRatio(critRatio, source, target) {
      if (target && target === this.effectState.lastAttacker) return critRatio + 1;
    },
  },
  unstoppable_force: {
    name: 'Unstoppable Force',
    onModifyMove(move) {
      if (!isPhysical(move)) return;
      move.ignoreDefensive = true;
      move.infiltrates = true;
    },
  },

  // ---------------------------------------------------------------- Defence
  plated_hide: {
    name: 'Plated Hide',
    onCriticalHit(target, source, move) {
      if (move && move.flags && move.flags['contact']) return false;
    },
  },
  barbed_shell: {
    name: 'Barbed Shell',
    onDamagingHit(damage, target, source, move) {
      if (source && !source.fainted && makesContact(this, move, source, target)) {
        this.damage(source.baseMaxhp / 16, source, target);
      }
    },
  },
  immovable: {
    name: 'Immovable',
    onDragOutPriority: 1,
    onDragOut(pokemon) {
      announce(this, pokemon, 'Immovable holds it in place!');
      return null;
    },
    onTryBoost(boost, target, source) {
      if (!foeDidLower(target, source)) return;
      if (blockDrops(boost, stat => stat !== 'def')) announce(this, target, 'Immovable stops its Defence dropping!');
    },
  },
  iron_riposte: {
    name: 'Iron Riposte',
    onDamagingHit(damage, target, source, move) {
      if (!source || source.fainted || !isPhysical(move)) return;
      if (this.randomChance(2, 10)) this.boost({atk: -1}, source, target, null, true);
    },
  },
  bracing: {
    name: 'Bracing',
    onAfterMove(pokemon) {
      if ((this.effectState.boosts || 0) >= 2) return;
      const foes = pokemon.foes();
      if (!foes.length || foes.some(foe => this.queue.willMove(foe))) return;
      this.effectState.boosts = (this.effectState.boosts || 0) + 1;
      this.boost({def: 1}, pokemon, pokemon);
    },
  },

  // ---------------------------------------------------------------- Sp. Atk
  dread_aura: {
    name: 'Dread Aura',
    onSwitchIn(pokemon) {
      const foes = pokemon.adjacentFoes();
      if (!foes.length) return;
      announce(this, pokemon, 'Dread Aura weighs on its foes!');
      for (const foe of foes) {
        if (foe.volatiles['substitute']) continue;
        this.boost({spd: -1}, foe, pokemon, null, true);
      }
    },
  },
  afterburn: {
    name: 'Afterburn',
    onModifyMovePriority: -2,
    onModifyMove(move) {
      if (!isSpecial(move) || !move.secondaries) return;
      for (const secondary of move.secondaries) {
        if (secondary.chance) secondary.chance *= 2;
      }
    },
  },
  spellpierce: {
    name: 'Spellpierce',
    onModifyMove(move) {
      if (isSpecial(move)) move.infiltrates = true;
    },
  },
  echo_cast: {
    name: 'Echo Cast',
    onAfterMove(pokemon, target, move) {
      this.effectState.last = isSpecial(move) ? {id: move.id, turn: this.turn} : null;
    },
    onModifyCritRatio(critRatio, source, target, move) {
      const last = this.effectState.last;
      if (isSpecial(move) && last && last.id === move.id && last.turn === this.turn - 1) return critRatio + 1;
    },
  },
  steady_channel: {
    name: 'Steady Channel',
    onModifyMove(move) {
      if (isSpecial(move) && typeof move.accuracy === 'number' && move.accuracy < 100) {
        move.accuracy = Math.min(100, move.accuracy + 10);
      }
    },
  },

  // ---------------------------------------------------------------- Sp. Def
  spellward: {
    name: 'Spellward',
    onModifySecondaries(secondaries, target, source, move) {
      if (!isSpecial(move)) return;
      return secondaries.filter(effect => !!effect.self || !!effect.dustproof);
    },
  },
  hollow_mind: {
    name: 'Hollow Mind',
    onTryAddVolatile(status, pokemon) {
      if (!status || !['confusion', 'taunt', 'encore', 'torment'].includes(status.id)) return;
      announce(this, pokemon, 'Hollow Mind shrugs it off!');
      return null;
    },
  },
  resonant_ward: {
    name: 'Resonant Ward',
    onDamagingHit(damage, target, source, move) {
      if (!isSpecial(move) || (this.effectState.boosts || 0) >= 2) return;
      if (!this.randomChance(3, 10)) return;
      this.effectState.boosts = (this.effectState.boosts || 0) + 1;
      this.boost({spd: 1}, target, target);
    },
  },
  cleansing_veil: {
    name: 'Cleansing Veil',
    onResidualOrder: 5,
    onResidualSubOrder: 3,
    onResidual(pokemon) {
      if (pokemon.hp && pokemon.status && this.randomChance(1, 3)) {
        announce(this, pokemon, 'Cleansing Veil washes its status away!');
        pokemon.cureStatus();
      }
    },
  },
  mirrorglass: {
    name: 'Mirrorglass',
    onTryHitPriority: 1,
    onTryHit(target, source, move) {
      if (this.effectState.used || target === source || move.hasBounced || !move.flags['reflectable']) return;
      this.effectState.used = true;
      announce(this, target, 'Mirrorglass turns the move back!');
      const bounced = this.dex.getActiveMove(move.id);
      bounced.hasBounced = true;
      bounced.pranksterBoosted = false;
      this.actions.useMove(bounced, target, source);
      return null;
    },
  },

  // ---------------------------------------------------------------- Speed
  quickdraw: {
    name: 'Quickdraw',
    onSwitchIn() {
      this.effectState.armed = true;
    },
    onFractionalPriorityPriority: -1,
    onFractionalPriority(priority) {
      if (this.effectState.armed && priority <= 0) return 0.1;
    },
    onBeforeMove() {
      this.effectState.armed = false;
    },
  },
  slipstream: {
    name: 'Slipstream',
    onTryBoost(boost, target, source) {
      if (foeDidLower(target, source)) blockDrops(boost, stat => stat !== 'spe');
    },
    onModifySpe(spe, pokemon) {
      // Paralysis halves Speed in its own ModifySpe handler; this cancels exactly that, in either order.
      if (pokemon.status === 'par') return this.chainModify(2);
    },
  },
  outpace: {
    name: 'Outpace',
    onModifyCritRatio(critRatio, source, target) {
      if (target && target !== source && this.queue.willMove(target)) return critRatio + 1;
    },
  },
  feral_instinct: {
    name: 'Feral Instinct',
    onFoeAfterBoost(boost, target) {
      if (this.effectState.used || !Object.values(boost).some(value => value > 0)) return;
      const self = this.effectState.target;
      if (!self || self.fainted) return;
      this.effectState.used = true;
      announce(this, self, 'Feral Instinct!');
      this.boost({spe: 1}, self, self);
    },
  },
  head_start: {
    name: 'Head Start',
    onSwitchIn(pokemon) {
      if (this.effectState.used) return;
      this.effectState.used = true;
      announce(this, pokemon, 'Head Start!');
      this.boost({spe: 1}, pokemon, pokemon);
    },
  },

  // ---------------------------------------------------------------- HP pool
  last_stand: {
    name: 'Last Stand',
    onDamagePriority: -30,
    onDamage(damage, target, source, effect) {
      if (isRaidBoss(target) || this.effectState.used) return; // the boss's runs on the raid pool, in Java
      if (!effect || effect.effectType !== 'Move' || target.hp <= 1 || damage < target.hp) return;
      this.effectState.used = true;
      announce(this, target, 'Last Stand! It hung on!');
      return target.hp - 1;
    },
  },
  second_wind: {
    name: 'Second Wind',
    onUpdate(pokemon) {
      if (isRaidBoss(pokemon) || this.effectState.used) return; // the boss's runs on the raid pool, in Java
      if (!pokemon.hp || pokemon.hp > pokemon.maxhp / 3) return;
      this.effectState.used = true;
      announce(this, pokemon, 'Second Wind!');
      this.heal(pokemon.baseMaxhp / 4, pokemon, pokemon);
    },
  },
  grit: {
    name: 'Grit',
    onDamagingHit(damage, target, source, move) {
      if (this.effectState.used || !move || target.getMoveHitData(move).typeMod <= 0) return;
      this.effectState.used = true;
      announce(this, target, 'Grit!');
      this.boost({def: 1, spd: 1}, target, target);
    },
  },
  unbroken: {
    name: 'Unbroken',
    onTryBoost(boost, target, source) {
      if (!foeDidLower(target, source)) return;
      const standing = isRaidBoss(target)
        ? !this.cobbleAffixUnbrokenEnded
        : target.hp > target.maxhp / 2;
      if (standing && blockDrops(boost, () => false)) announce(this, target, 'Unbroken! Its stats were not lowered!');
    },
  },
  regrowth: {
    name: 'Regrowth',
    onDamagingHit() {
      this.effectState.hitThisTurn = true;
    },
    onResidualOrder: 5,
    onResidualSubOrder: 4,
    onResidual(pokemon) {
      const hit = this.effectState.hitThisTurn;
      this.effectState.hitThisTurn = false;
      if (!pokemon.hp || (isRaidBoss(pokemon) && hit)) return;
      this.heal(pokemon.baseMaxhp / 16, pokemon, pokemon);
    },
  },
};

// One effect object per affix, shared by every Pokemon that has it; per-Pokemon state lives on the Pokemon.
const EFFECTS = {};
for (const id of Object.keys(AFFIXES)) {
  EFFECTS[id] = Object.assign({}, AFFIXES[id], {
    id: 'cobbleaffix' + id.replace(/_/g, ''),
    fullname: 'cobbleaffix: ' + AFFIXES[id].name,
    effectType: 'Condition',
    exists: true,
  });
}

/** This Pokemon's affix effect, or null. Cached on the Pokemon: the map never changes after >start. */
function affixOf(battle, pokemon) {
  if (pokemon.cobbleAffix !== undefined) return pokemon.cobbleAffix;
  const map = battle.format && battle.format.cobbleAffixes;
  const id = map && typeof map === 'object' && pokemon.uuid ? map[pokemon.uuid] : undefined;
  const effect = typeof id === 'string' && Object.prototype.hasOwnProperty.call(EFFECTS, id) ? EFFECTS[id] : null;
  pokemon.cobbleAffix = effect;
  if (effect) pokemon.cobbleAffixState = {id: effect.id, target: pokemon};
  return effect;
}

const oldFindPokemonEventHandlers = Battle.prototype.findPokemonEventHandlers;
Battle.prototype.findPokemonEventHandlers = function(pokemon, callbackName, getKey) {
  const handlers = oldFindPokemonEventHandlers.call(this, pokemon, callbackName, getKey);
  const effect = affixOf(this, pokemon);
  if (!effect) return handlers;
  const callback = effect[callbackName];
  if (callback === undefined) return handlers;
  handlers.push(this.resolvePriority({
    effect,
    callback,
    state: pokemon.cobbleAffixState,
    end() {},
    effectHolder: pokemon,
  }, callbackName));
  return handlers;
};

// >cobbleaffixpool unbroken_ended -- sent by Java (RaidDamageInstruction) the first time an Unbroken boss's
// raid pool falls below half. Any other argument is ignored, and the command never reaches stock Showdown,
// which would throw on a command it does not know.
const oldWriteLine = BattleStream.prototype._writeLine;
BattleStream.prototype._writeLine = function(type, message) {
  if (type !== 'cobbleaffixpool') return oldWriteLine.call(this, type, message);
  if (this.battle && String(message).trim() === 'unbroken_ended') {
    this.battle.cobbleAffixUnbrokenEnded = true;
    this.battle.inputLog.push('>cobbleaffixpool unbroken_ended');
  }
  return true;
};

module.exports = {AFFIXES: Object.keys(AFFIXES), isRaidBoss};
