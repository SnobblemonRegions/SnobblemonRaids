package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorldBossZoneTest {
    @Test void boundaryIncludesAllThreeAxesAndExcludesPlayersAboveAndBelow() {
        assertTrue(WorldBossZone.inside(10, 100, -20, 50, 10, 150, -20));
        assertTrue(WorldBossZone.inside(10, 100, -20, 50, 40, 140, -20));
        assertFalse(WorldBossZone.inside(10, 100, -20, 50, 10, 151, -20));
        assertFalse(WorldBossZone.inside(10, 100, -20, 50, 10, 49, -20));
        assertFalse(WorldBossZone.inside(10, 100, -20, 50, 50, 140, -20));
    }

    @Test void particleRingLiesOnTheSameBoundary() {
        for (int y = -99; y <= 99; y++) {
            double ring = WorldBossZone.horizontalRadius(0, 100, y);
            assertEquals(10_000, ring * ring + y * y, 1e-8);
            assertTrue(WorldBossZone.inside(0, 0, 0, 100, ring * 0.999, y, 0));
            assertFalse(WorldBossZone.inside(0, 0, 0, 100, ring * 1.001, y, 0));
        }
        assertEquals(0, WorldBossZone.horizontalRadius(0, 100, 101));
    }

    @Test void invalidCoordinatesAndRadiiNeverEnroll() {
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(WorldBossZone.inside(0, 0, 0, 50, 0, bad, 0));
            assertFalse(WorldBossZone.inside(0, 0, 0, bad, 0, 0, 0));
            assertEquals(0, WorldBossZone.horizontalRadius(0, 50, bad));
        }
        assertFalse(WorldBossZone.inside(0, 0, 0, 0, 0, 0, 0));
    }
}
