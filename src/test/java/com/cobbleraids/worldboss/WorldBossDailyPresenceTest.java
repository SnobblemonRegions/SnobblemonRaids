package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The daily boss's pool follows the players still taking part: a grace to come back, a shrink that keeps the percentage, a regrow that costs. */
class WorldBossDailyPresenceTest {

    private static final long GRACE = 180_000L;

    @Test
    void aPlayerCountsThroughTheGraceAndNotAfter() {
        UUID stays = UUID.randomUUID();
        UUID left = UUID.randomUUID();
        UUID neverSeen = UUID.randomUUID();
        Set<UUID> joined = new LinkedHashSet<>(Set.of(stays, left, neverSeen));
        Map<UUID, Long> seen = new HashMap<>();
        seen.put(stays, 1_000_000L);
        seen.put(left, 1_000_000L - GRACE);
        assertEquals(2, WorldBossRules.present(joined, seen, 1_000_000L, GRACE), "exactly at the grace still counts");
        assertEquals(1, WorldBossRules.present(joined, seen, 1_000_001L, GRACE), "a millisecond later it does not");
    }

    @Test
    void aShrinkKeepsThePercentageAndTheDamageCredited() {
        WorldBossPool pool = new WorldBossPool(100_000f);
        UUID hitter = UUID.randomUUID();
        pool.strike(hitter, 60_000f);
        pool.resizeTo(50_000f);
        assertEquals(50_000f, pool.max(), 1e-3f);
        assertEquals(20_000f, pool.current(), 1e-3f, "40% left stays 40% left");
        assertEquals(60_000f, pool.contributionOf(hitter), 1e-3f, "what was dealt is still credited for the reward");
        assertFalse(pool.isDefeated());
    }

    @Test
    void aShrinkNeverKillsTheBoss() {
        WorldBossPool pool = new WorldBossPool(1_000_000f);
        pool.strike(UUID.randomUUID(), 999_999.9f);
        pool.resizeTo(10f);
        assertTrue(pool.current() > 0f);
        assertFalse(pool.claimDefeat());
    }

    @Test
    void leavingAndComingBackMakesTheBossHarderNotEasier() {
        WorldBossPool pool = new WorldBossPool(100_000f);
        pool.strike(UUID.randomUUID(), 60_000f);   // 40,000 of 100,000 left
        pool.resizeTo(50_000f);                     // someone leaves: 20,000 of 50,000
        pool.resizeTo(100_000f);                    // they return: their full 50,000 share is added back
        assertEquals(70_000f, pool.current(), 1e-3f, "more left than the 40,000 before the leave");
    }

    @Test
    void aDefeatedOrBadResizeChangesNothing() {
        WorldBossPool pool = new WorldBossPool(1_000f);
        pool.resizeTo(Float.NaN);
        pool.resizeTo(-5f);
        pool.resizeTo(Float.POSITIVE_INFINITY);
        assertEquals(1_000f, pool.max(), 1e-3f);
        pool.strike(UUID.randomUUID(), 1_000f);
        assertTrue(pool.claimDefeat());
        pool.resizeTo(500f);
        assertEquals(1_000f, pool.max(), 1e-3f);
        assertEquals(0f, pool.current(), 1e-3f);
    }

    @Test
    void thePoolNeverDropsBelowTheMinimumPlayers() {
        assertEquals(WorldBossScaling.dailyPool(5_000L, 6f, 3, 3), WorldBossScaling.dailyPool(5_000L, 6f, 1, 3), 1e-3f);
        assertEquals(WorldBossScaling.dailyPool(5_000L, 6f, 0, 3), WorldBossScaling.dailyPool(5_000L, 6f, 3, 3), 1e-3f);
    }
}
