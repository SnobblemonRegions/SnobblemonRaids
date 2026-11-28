package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The Daily Boss arena: the veil's ring of panels, and the seal's stages and turning speed. */
class DailyArenaGeometryTest {

    // ---- the veil ----

    @Test
    void theVeilIsAClosedRingOfUprightPanelsFacingOutward() {
        List<DomeGeometry.Panel> panels = VeilGeometry.panels(128, 22, 4);
        int expected = (int) Math.ceil(2 * Math.PI * 128 / 4);
        assertEquals(expected, panels.size(), "about one panel per 4 blocks of rim");
        for (DomeGeometry.Panel p : panels) {
            assertEquals(128, Math.hypot(p.x(), p.z()), 1e-6, "every panel stands on the rim");
            assertEquals(11, p.y(), 1e-6, "panel centres sit at half the height above the ground ring");
            assertEquals(22, p.s2(), 1e-6);
            assertEquals(0, p.ny(), 1e-9, "upright");
            // the normal points out from the centre, along the panel's own position
            assertEquals(1.0, (p.nx() * p.x() + p.nz() * p.z()) / 128, 1e-6);
            // the left rotation is a turn about Y only, and a unit quaternion
            assertEquals(0, p.lx(), 1e-6);
            assertEquals(0, p.lz(), 1e-6);
            assertEquals(1.0, p.ly() * p.ly() + p.lw() * p.lw(), 1e-5);
        }
    }

    @Test
    void neighbouringVeilPanelsOverlapSoTheRingHasNoGaps() {
        List<DomeGeometry.Panel> panels = VeilGeometry.panels(128, 22, 4);
        DomeGeometry.Panel a = panels.get(0);
        DomeGeometry.Panel b = panels.get(1);
        double gap = Math.hypot(a.x() - b.x(), a.z() - b.z());
        assertTrue(a.s1() > gap, "each panel is wider than the step between centres (" + a.s1() + " vs " + gap + ")");
        assertTrue(a.s1() < gap * 1.1, "but only just, so overlaps do not double the glow");
    }

    @Test
    void theVeilRotationTurnsTheQuadToFaceItsOwnNormal() {
        for (DomeGeometry.Panel p : VeilGeometry.panels(40, 10, 5)) {
            // rotate the quad's +Z facing by the left quaternion (about Y): (2*w*y, 0, w*w - y*y) for a pure Y turn
            double fx = 2 * p.lw() * p.ly();
            double fz = p.lw() * p.lw() - p.ly() * p.ly();
            assertEquals(p.nx(), fx, 1e-5);
            assertEquals(p.nz(), fz, 1e-5);
        }
    }

    // ---- the seal's states ----

    @Test
    void theSealDrawsItselfInDuringTheGatherThenOpensThenBurnsLow() {
        DailySeal.Phase gathering = DailySeal.phase(30, 60, 1.0f);
        assertEquals(DailySeal.Stage.GATHER, gathering.stage());
        assertEquals(0.5, gathering.progress(), 1e-9, "half the countdown gone: half drawn");
        assertEquals(0.0, DailySeal.phase(60, 60, 1.0f).progress(), 1e-9);

        assertEquals(DailySeal.Stage.OPEN, DailySeal.phase(0, 60, 1.0f).stage());
        assertEquals(1.0, DailySeal.phase(0, 60, 0.6f).progress(), 1e-9);
        assertEquals(DailySeal.Stage.OPEN, DailySeal.phase(0, 60, 0.25f).stage(), "a quarter is still open");
        assertEquals(DailySeal.Stage.LOW, DailySeal.phase(0, 60, 0.249f).stage());
        assertEquals(DailySeal.Stage.OPEN, DailySeal.phase(0, 0, 1.0f).stage(), "no countdown configured: open at once");
    }

    @Test
    void theSealTurnsOnceAMinuteOpenAndThreeTimesAMinuteLow() {
        assertEquals(2 * Math.PI, DailySeal.angle(DailySeal.Stage.OPEN, 60 * 20L), 1e-9, "one full turn after a minute");
        assertEquals(Math.PI / 2, DailySeal.angle(DailySeal.Stage.OPEN, 15 * 20L), 1e-9);
        assertEquals(3 * Math.PI / 2, DailySeal.angle(DailySeal.Stage.LOW, 15 * 20L), 1e-9);
        assertEquals(0.0, DailySeal.angle(DailySeal.Stage.GATHER, 15 * 20L), 1e-9, "it does not turn until it is whole");
    }
}
