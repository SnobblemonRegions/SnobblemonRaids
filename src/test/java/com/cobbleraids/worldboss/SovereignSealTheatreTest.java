package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SovereignSealTheatreTest {
    @Test
    void activationUnfoldsOnceAndStopsGrowingAtFullSize() {
        assertEquals(0.08, SovereignSealGeometry.openingScale(-1), 1e-9);
        assertEquals(0.08, SovereignSealGeometry.openingScale(0), 1e-9);
        assertEquals(0.54, SovereignSealGeometry.openingScale(60), 1e-9);
        assertEquals(1, SovereignSealGeometry.openingScale(120), 1e-9);
        assertEquals(1, SovereignSealGeometry.openingScale(Long.MAX_VALUE), 1e-9);
    }

    @Test
    void newLayersStayFiniteAndKeepTheGroundCircleBelowTheBattle() {
        for (double radius : new double[]{8, 96, 1024}) {
            for (long tick : new long[]{0, 8, 240, 1200, Long.MAX_VALUE}) {
                var ground = SovereignSealGeometry.ground(radius, tick);
                assertTrue(ground.y() >= 0.15 && ground.y() <= 1.1);
                for (var p : new SovereignSealGeometry.Pose[]{ground,
                        SovereignSealGeometry.halo(radius, tick), SovereignSealGeometry.energy(radius, tick)}) {
                    assertTrue(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()));
                    assertTrue(Double.isFinite(p.scale()) && p.scale() > 0);
                    assertEquals(1, p.qx()*p.qx()+p.qy()*p.qy()+p.qz()*p.qz()+p.qw()*p.qw(), 1e-9);
                }
            }
        }
    }

    @Test
    void effectPreferencesKeepTheBaseSealAndReduceDecorativeLayers() {
        for (var effects : WorldBossBenefits.Effects.values()) {
            assertTrue(SovereignSealPrototype.visible(0, effects));
            assertTrue(SovereignSealPrototype.visible(1, effects));
        }
        assertFalse(SovereignSealPrototype.visible(2, WorldBossBenefits.Effects.OFF));
        assertTrue(SovereignSealPrototype.visible(2, WorldBossBenefits.Effects.MINIMAL));
        assertFalse(SovereignSealPrototype.visible(3, WorldBossBenefits.Effects.MINIMAL));
        assertFalse(SovereignSealPrototype.visible(4, WorldBossBenefits.Effects.MINIMAL));
        for (int layer = 0; layer < 5; layer++) assertTrue(SovereignSealPrototype.visible(layer, WorldBossBenefits.Effects.FULL));
        int minimal = 0, off = 0;
        for (int layer = 0; layer < 5; layer++) {
            if (SovereignSealPrototype.visible(layer, WorldBossBenefits.Effects.MINIMAL)) minimal++;
            if (SovereignSealPrototype.visible(layer, WorldBossBenefits.Effects.OFF)) off++;
        }
        assertEquals(3, minimal);
        assertEquals(2, off);
    }

    @Test
    void allDecorativeMotionClosesTheLoopWithoutAVisibleJump() {
        for (var pair : new SovereignSealGeometry.Pose[][]{
                {SovereignSealGeometry.halo(96,0),SovereignSealGeometry.halo(96,7200)},
                {SovereignSealGeometry.energy(96,0),SovereignSealGeometry.energy(96,7200)},
                {SovereignSealGeometry.ground(96,0),SovereignSealGeometry.ground(96,7200)}}) {
            assertEquals(pair[0], pair[1]);
        }
    }
}
