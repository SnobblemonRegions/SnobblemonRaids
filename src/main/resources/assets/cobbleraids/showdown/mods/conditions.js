"use strict";
// CobbleRaids' own battle conditions. Installed as data/mods/cobblemon/cobbleraids-conditions.js and
// merged into Cobblemon's conditions.js by a one-line hook (ShowdownModMerge), never copied over it:
// Cobblemon keeps its own conditions in that file (alphaboost, which the Wild Alpha rule adds), and
// replacing it took them out of every battle on the server.
const Conditions = {};
Conditions.raidboss = {
  name: 'raidboss',
  onDamage(damage, target, source, effect) {
    if (target?.side?.battle?.gameType !== 'raid') return damage;
    // Showdown follows every Damage event with an ordinary -damage line carrying simulator
    // health, which would overwrite the authoritative shared raid percentage on every client.
    // raid-patch.js consumes exactly that one boss-health instruction.
    target.__cobbleRaidsSuppressVanillaDamageLog = true;
    // raid-patch.js reports the full hit to the pool as -raiddamage and answers how much the
    // simulator may take off its mirrored HP, so drain, recoil, Counter and Bide see the hit.
    if (typeof this.raidBossDamage === 'function') return this.raidBossDamage(target, damage, source);
    if (source) this.add('-raiddamage', target, damage, source);
    else this.add('-raiddamage', target, damage);
    return 0;
  }
  // No onTryHeal here. It used to report -raidheal and return 0, which made
  // Battle#heal answer "healed nothing" to its callers: moves that test the result
  // (Synthesis, Morning Sun, Moonlight, Shore Up) then logged `-fail <boss> heal`
  // even though the pool had just been topped up, and moves carrying a `heal:`
  // property never reached the event at all because BattleActions#runMoveEffects
  // calls Pokemon#heal directly. Boss healing is now owned by raid-patch.js, which
  // overrides Battle#heal and Pokemon#heal, so the TryHeal event stays vanilla and
  // effects that legitimately cancel healing (Heal Block, Liquid Ooze) keep working.
};
module.exports = {Conditions};
