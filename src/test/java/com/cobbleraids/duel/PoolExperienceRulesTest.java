package com.cobbleraids.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobbleraids.config.RaidRarityTier;
import org.junit.jupiter.api.Test;

class PoolExperienceRulesTest {

    @Test
    void anEvenSplitPaysTheTrainerFormulaTimesTheTier() {
        // 300 x 70 / 5 = 4200, x3 for a legendary.
        assertEquals(12600, PoolExperienceRules.perPokemon(300, 70, PoolExperienceRules.tierMultiplier(RaidRarityTier.LEGENDARY), 0.25, 4));
    }

    @Test
    void theShareFactorIsClampedAndCutsOffTheIdle() {
        assertEquals(1.5, PoolExperienceRules.shareFactor(1.0, 4));
        assertEquals(0.5, PoolExperienceRules.shareFactor(0.1, 4), "0.4 of even is still paid at the floor");
        assertEquals(0.0, PoolExperienceRules.shareFactor(0.02, 8), "under a quarter of even pays nothing");
        assertEquals(1.0, PoolExperienceRules.shareFactor(1.0, 1), "solo is an even split");
    }

    @Test
    void theWorldBossPaysMost() {
        for (RaidRarityTier tier : RaidRarityTier.values())
            assert PoolExperienceRules.tierMultiplier(tier) < PoolExperienceRules.WORLD_BOSS_MULTIPLIER;
    }
}
