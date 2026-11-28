package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorldBossArenaRegionTest {
    @Test void earlyCullRequiresTheWholeCellInsideTheSameProtectedColumn() {
        var region = new WorldBossArenaRegion("minecraft:overworld", 0, 0, 16);
        assertTrue(region.contains("minecraft:overworld", -1, -1, 0, 0));
        assertTrue(region.intersects("minecraft:overworld", 15, 0, 16, 1, 0));
        assertFalse(region.contains("minecraft:overworld", 15, 0, 16, 1));
        assertFalse(region.contains("minecraft:the_end", -1, -1, 0, 0));
        assertFalse(region.contains("minecraft:overworld", 1, 0, 0, 1));
        assertFalse(region.contains("minecraft:overworld", Double.NaN, 0, 1, 1));
    }
    @Test void containmentAgreesWithAllFourIndependentCornerDistances() {
        var region = new WorldBossArenaRegion("minecraft:overworld", -31.5, 15.25, 40);
        for (int x = -90; x <= 30; x += 3) for (int z = -45; z <= 75; z += 3) {
            boolean expected = true;
            for (double px : new double[] {x, x + 2.5}) for (double pz : new double[] {z, z + 1.75}) expected &= Math.hypot(px + 31.5, pz - 15.25) <= 40;
            assertEquals(expected, region.contains("minecraft:overworld", x, z, x + 2.5, z + 1.75));
        }
    }
    @Test void protectsIntersectingBlocksAndBodiesRatherThanOnlyTheirCenters() {
        var region = new WorldBossArenaRegion("minecraft:overworld", 0.5, -0.5, 16);
        assertTrue(region.intersects("minecraft:overworld", -1, -1, 1, 1, 0));
        assertTrue(region.intersects("minecraft:overworld", 16, -1, 17, 0, 0));
        assertFalse(region.intersects("minecraft:overworld", 17, -1, 18, 0, 0));
        assertFalse(region.intersects("minecraft:overworld", 15, 15, 16, 16, 0));
        assertFalse(region.intersects("minecraft:the_end", 0, 0, 1, 1, 0));
    }
    @Test void outsidePistonSourcesUseTheDocumentedBoundaryGuard() {
        var region = new WorldBossArenaRegion("minecraft:overworld", -16, -16, 32);
        assertFalse(region.intersects("minecraft:overworld", 29, -16, 30, -15, 0));
        assertTrue(region.intersects("minecraft:overworld", 29, -16, 30, -15, 14));
        assertFalse(region.intersects("minecraft:overworld", 31, -16, 32, -15, 14));
    }
    @Test void regionIntersectionMatchesAnIndependentClosestPointCalculation() {
        var region = new WorldBossArenaRegion("minecraft:overworld", -31.5, 15.25, 40);
        for (int x = -90; x <= 30; x += 3) for (int z = -45; z <= 75; z += 3) {
            double closestX = Math.min(x + 2.5, Math.max(x, -31.5)), closestZ = Math.min(z + 1.75, Math.max(z, 15.25));
            boolean expected = Math.hypot(closestX + 31.5, closestZ - 15.25) <= 40;
            assertEquals(expected, region.intersects("minecraft:overworld", x, z, x + 2.5, z + 1.75, 0));
        }
    }
    @Test void malformedRegionsAndQueriesNeverBecomeAnUnboundedProtectionArea() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossArenaRegion("bad", 0, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossArenaRegion("minecraft:overworld", Double.NaN, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossArenaRegion("minecraft:overworld", 0, 0, Double.POSITIVE_INFINITY));
        var region = new WorldBossArenaRegion("minecraft:overworld", 0, 0, 16);
        assertFalse(region.intersects("minecraft:overworld", 0, 0, 1, 1, Double.NaN));
        assertFalse(region.intersects("minecraft:overworld", 2, 0, 1, 1, 0));
        assertFalse(region.intersects("minecraft:overworld", Double.NaN, 0, 1, 1, 0));
    }
}
