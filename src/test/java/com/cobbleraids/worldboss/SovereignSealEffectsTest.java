package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SovereignSealEffectsTest {
    @Test
    void observerPlatformCanSeeEffectsAcrossTheWholeNinetySixBlockArena() {
        for (long tick = 0; tick < 3600; tick += 16) {
            for (var point : SovereignSealEffects.frame(96, tick, false)) {
                double distance = Math.sqrt(Math.pow(point.x() - 105, 2)
                        + Math.pow(point.y() - 4, 2) + point.z() * point.z());
                assertTrue(distance <= SovereignSealEffects.VIEW_DISTANCE,
                        "The configured observer platform must see the far-side seal effects");
            }
        }
    }

    @Test
    void emissionsWorkWhenBorderUpdatesAreNotAlignedWithWorldTime() {
        assertTrue(SovereignSealEffects.due(5, Long.MIN_VALUE));
        assertFalse(SovereignSealEffects.due(13, 5));
        assertTrue(SovereignSealEffects.due(21, 5));
        assertFalse(SovereignSealEffects.due(Long.MAX_VALUE, Long.MAX_VALUE - 8));
        assertTrue(SovereignSealEffects.due(0, 100));
    }

    @Test
    void goldFollowsTheBandNativeHalfTurnAndLeavesThreeGaps() {
        var full = SovereignSealEffects.frame(96, 0, false);
        assertEquals(-96, full.getFirst().x(), 1e-9);
        assertEquals(13, full.stream().filter(point -> !point.scarlet()).count());
        assertEquals(3, full.stream().filter(SovereignSealEffects.Point::scarlet).count());
        var minimal = SovereignSealEffects.frame(96, 0, true);
        assertEquals(3, minimal.stream().filter(point -> !point.scarlet()).count());
        assertEquals(1, minimal.stream().filter(SovereignSealEffects.Point::scarlet).count());
        assertTrue(full.containsAll(minimal));
    }

    @Test
    void animationLoopsWithoutMovingEffectsAwayFromTheModels() {
        var initial = SovereignSealEffects.frame(96, 0, false);
        var loop = SovereignSealEffects.frame(96, 3600, false);
        assertEquals(initial, loop);
        assertNotEquals(initial, SovereignSealEffects.frame(96, 16, false));
    }

    @Test
    void particlesStayBoundedAtSupportedRadiiAndLongUptimes() {
        for (double radius : new double[]{8, 96, 1024}) {
            for (long tick : new long[]{0, 16, 3592, Long.MAX_VALUE}) {
                var full = SovereignSealEffects.frame(radius, tick, false);
                var minimal = SovereignSealEffects.frame(radius, tick, true);
                assertEquals(16, full.size());
                assertEquals(4, minimal.size());
                for (var point : full) {
                    assertTrue(Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z()));
                    assertTrue(point.y() > 3, "Effects must clear the arena floor");
                    assertTrue(point.size() >= 3.0f && point.size() <= 4.0f,
                            "Arena-scale dust must remain visible throughout the pulse and within vanilla's size limit");
                    assertTrue(Math.hypot(point.x(), point.z()) < radius * 1.15 + 2);
                }
            }
        }
    }
}
