package com.cobbleraids.showdown;

import com.cobbleraids.RaidLog;
import com.cobbleraids.raid.RaidSession;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;

/**
 * Tells Showdown where the shared health pool is, so the boss's simulated HP mirrors it (Eruption,
 * Brine, Reversal, Sitrus, Multiscale and every boss health line then read the pool, not "full").
 */
public final class RaidPoolSync {

    private RaidPoolSync() {}

    public static void send(PokemonBattle battle, RaidSession raid) {
        if (battle == null || raid == null) return;
        String line = RaidPoolCommand.of(raid.getCurrentHealth(), raid.getMaxHealth());
        if (line == null) return;
        try {
            battle.writeShowdownAction(line);
        } catch (RuntimeException ex) {
            // The mirror falls back to the patch's own prediction; the pool itself is unaffected.
            RaidLog.warn("Could not send the raid pool to Showdown for battle {}", battle.getBattleId(), ex);
        }
    }
}
