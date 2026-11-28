package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SovereignSealGeometryTest {
    @Test
    void ringAndCrestKeepFiniteUnitRotationsAcrossLongServerUptimes() {
        for (double radius : new double[]{8, 96, 512, 1024}) {
            for (long tick : new long[]{0, 8, 3592, 3600, Long.MAX_VALUE}) {
                for (var pose : new SovereignSealGeometry.Pose[]{SovereignSealGeometry.ring(radius, tick), SovereignSealGeometry.crest(radius, tick)}) {
                    assertTrue(Double.isFinite(pose.x()) && Double.isFinite(pose.y()) && Double.isFinite(pose.z()));
                    assertTrue(pose.scale() > 0 && Double.isFinite(pose.scale()));
                    assertEquals(1, dot(pose, pose), 1e-9);
                }
            }
        }
    }

    @Test
    void ringMotionIsSlowAndDoesNotJumpAtTheLoopBoundary() {
        var a = SovereignSealGeometry.ring(96, 0);
        var b = SovereignSealGeometry.ring(96, 8);
        var c = SovereignSealGeometry.ring(96, 3592);
        var d = SovereignSealGeometry.ring(96, 3600);
        double step = 2 * Math.acos(Math.min(1, Math.abs(dot(a, b))));
        assertTrue(step > 0 && step < Math.toRadians(1));
        assertEquals(step, 2 * Math.acos(Math.min(1, Math.abs(dot(c, d)))), 1e-9);
        assertEquals(1, Math.abs(dot(a, d)), 1e-9);
        assertEquals(a.y(), d.y());
    }

    @Test
    void crestFloatsAtThePerimeterAndDoesNotCoverTheBossCentre() {
        var first = SovereignSealGeometry.crest(96, 0);
        var later = SovereignSealGeometry.crest(96, 45);
        double distance = Math.hypot(first.x(), first.z());
        assertTrue(distance > 72 && distance < 96);
        assertTrue(first.y() > 20);
        assertTrue(Math.abs(first.y() - later.y()) > 0.5 && Math.abs(first.y() - later.y()) < 1);
    }

    @Test
    void crestFacesTheArenaAfterMinecraftItemDisplaysNativeHalfTurn() {
        var pose = SovereignSealGeometry.crest(96, 0);
        // Native ItemDisplayRenderer rotates the item by PI around Y: model north becomes positive Z.
        var front = new org.joml.Vector3d(0, 0, 1).rotate(new org.joml.Quaterniond(pose.qx(), pose.qy(), pose.qz(), pose.qw()));
        var towardsArena = new org.joml.Vector3d(-pose.x(), 0, -pose.z()).normalize();
        assertTrue(front.dot(towardsArena) > 0.99, "players inside must see the gold face, not the crest's back");
    }

    @Test
    void rejectsInvalidArenaSizes() {
        for (double radius : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 1025}) {
            assertThrows(IllegalArgumentException.class, () -> SovereignSealGeometry.ring(radius, 0));
            assertThrows(IllegalArgumentException.class, () -> SovereignSealGeometry.crest(radius, 0));
        }
    }

    private static double dot(SovereignSealGeometry.Pose a, SovereignSealGeometry.Pose b) {
        return a.qx() * b.qx() + a.qy() * b.qy() + a.qz() * b.qz() + a.qw() * b.qw();
    }
}
