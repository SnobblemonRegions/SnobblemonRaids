package com.cobbleraids.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.config.RaidRarityTier;
import org.junit.jupiter.api.Test;

class RaidDuelRulesTest {

    @Test
    void duelHealthRisesWithTheTier() {
        assertEquals(550, RaidDuelRules.duelHealth(RaidRarityTier.STARTER));
        assertEquals(1400, RaidDuelRules.duelHealth(RaidRarityTier.POWERHOUSE));
        assertEquals(3500, RaidDuelRules.duelHealth(RaidRarityTier.LEGENDARY));
        assertEquals(5000, RaidDuelRules.duelHealth(RaidRarityTier.MYTHICAL));
    }

    @Test
    void theSoloPoolIsThreeDuels() {
        assertEquals(1650, RaidDuelRules.poolFor(RaidRarityTier.STARTER, 1));
        assertEquals(1650, RaidDuelRules.poolFor(RaidRarityTier.STARTER, 0), "nobody counts as one");
    }

    @Test
    void thePoolGrowsSubLinearlyAndStopsAtEight() {
        long one = RaidDuelRules.poolFor(RaidRarityTier.LEGENDARY, 1);
        long eight = RaidDuelRules.poolFor(RaidRarityTier.LEGENDARY, 8);
        assertEquals(Math.pow(8, 0.85), (double) eight / one, 0.001);
        assertTrue(eight < one * 8, "each extra player is worth a little less");
        assertEquals(eight, RaidDuelRules.poolFor(RaidRarityTier.LEGENDARY, 20), "capped at eight");
        long previous = 0;
        for (int n = 1; n <= 8; n++) {
            long pool = RaidDuelRules.poolFor(RaidRarityTier.POWERHOUSE, n);
            assertTrue(pool > previous, n + " players");
            assertTrue(pool / n <= RaidDuelRules.perPlayerPool(RaidRarityTier.POWERHOUSE), "no player carries more than solo");
            previous = pool;
        }
    }

    @Test
    void aRaisedBossGetsTheSquareRootOfItsLevelRise() {
        // The tester's raid: a level 25 Starter Delphox raised to 88 by a level 100 team.
        assertEquals(1032, RaidDuelRules.duelHealth(RaidRarityTier.STARTER, 25, 88));
        assertEquals(3096, RaidDuelRules.poolFor(RaidRarityTier.STARTER, 1, 25, 88));
        // The two-player raid raised to 49: 40% more, not double.
        assertEquals(1.4, RaidDuelRules.levelFactor(25, 49), 1e-9);
        assertEquals(4164, RaidDuelRules.poolFor(RaidRarityTier.STARTER, 2, 25, 49));
    }

    @Test
    void levelScalingIsUpwardOnly() {
        assertEquals(1.0, RaidDuelRules.levelFactor(25, 25));
        assertEquals(1.0, RaidDuelRules.levelFactor(50, 30), "a boss below its level keeps its base HP");
        assertEquals(1.0, RaidDuelRules.levelFactor(0, 80), "a definition with no level is not scaled");
        assertEquals(550, RaidDuelRules.duelHealth(RaidRarityTier.STARTER, 25, 25));
        assertEquals(RaidDuelRules.poolFor(RaidRarityTier.MYTHICAL, 4), RaidDuelRules.poolFor(RaidRarityTier.MYTHICAL, 4, 70, 70));
    }

    @Test
    void eightPlayersFit() {
        assertTrue(RaidDuelRules.hasRoom(7));
        assertFalse(RaidDuelRules.hasRoom(8));
    }

    @Test
    void aWinPaysWhoFoughtAndStayed() {
        java.util.UUID fought = java.util.UUID.randomUUID(), wiped = java.util.UUID.randomUUID(),
                left = java.util.UUID.randomUUID(), neverStarted = java.util.UUID.randomUUID();
        var players = new java.util.LinkedHashSet<>(java.util.List.of(fought, wiped, left, neverStarted));
        // A wiped player fought and did not forfeit: their damage still counts, so they are paid.
        var victors = RaidDuelRules.victors(players, java.util.Set.of(fought, wiped, left), java.util.Set.of(left));
        assertEquals(java.util.List.of(fought, wiped), java.util.List.copyOf(victors), "in joining order");
    }

    @Test
    void nobodyIsPaidForARaidTheyOnlyJoined() {
        java.util.UUID bystander = java.util.UUID.randomUUID();
        assertTrue(RaidDuelRules.victors(java.util.Set.of(bystander), java.util.Set.of(), java.util.Set.of()).isEmpty());
    }
}
