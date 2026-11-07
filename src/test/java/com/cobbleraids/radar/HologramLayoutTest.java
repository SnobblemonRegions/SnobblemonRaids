package com.cobbleraids.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where the server-side hologram puts things: the maths that decides what a player sees, kept free of Minecraft so it is tested. */
class HologramLayoutTest {

    private static final double EPS = 1e-9;

    @Test
    void theModelIsCentredOnItsOrigin() {
        HologramLayout.Box a = HologramLayout.column(new HologramPlan.Column(0, 0, 0), 4);
        HologramLayout.Box b = HologramLayout.column(new HologramPlan.Column(HologramPlan.CELLS - 1, HologramPlan.CELLS - 1, 0), 4);
        assertEquals(-b.centerX(), a.centerX(), EPS);
        assertEquals(-b.centerZ(), a.centerZ(), EPS);
    }

    @Test
    void everyColumnSitsInsideTheModelCircle() {
        double radius = HologramLayout.MODEL_WIDTH / 2 + 1e-9;
        for (int z = 0; z < HologramPlan.CELLS; z++) for (int x = 0; x < HologramPlan.CELLS; x++) {
            HologramLayout.Box box = HologramLayout.column(new HologramPlan.Column(x, z, 0), 3);
            double corner = Math.hypot(Math.abs(box.centerX()) + box.sizeX() / 2, Math.abs(box.centerZ()) + box.sizeZ() / 2);
            // A cell the planner keeps is inside the circle by its centre; its far corner may poke out by less than half a cell.
            if (Math.hypot(x + 0.5 - HologramPlan.CELLS / 2.0, z + 0.5 - HologramPlan.CELLS / 2.0) <= HologramPlan.CELLS / 2.0) {
                assertTrue(corner <= radius + HologramLayout.CELL_SIZE, "cell " + x + "," + z);
            }
        }
    }

    @Test
    void higherTerracesAreTallerAndNeverExceedTheRelief() {
        double previous = 0;
        for (int band = 0; band < 6; band++) {
            double height = HologramLayout.column(new HologramPlan.Column(8, 8, band), 6).sizeY();
            assertTrue(height > previous, "band " + band + " must be taller than the one below");
            assertTrue(height <= HologramLayout.MAX_RELIEF + EPS);
            previous = height;
        }
    }

    @Test
    void aSingleTerraceStillHasAVisibleThickness() {
        double flat = HologramLayout.column(new HologramPlan.Column(8, 8, 0), 1).sizeY();
        assertTrue(flat >= HologramLayout.MIN_THICKNESS - EPS);
    }

    @Test
    void columnsLeaveAThinGapSoTheTerracesReadAsAGrid() {
        HologramLayout.Box box = HologramLayout.column(new HologramPlan.Column(8, 8, 1), 3);
        assertTrue(box.sizeX() < HologramLayout.CELL_SIZE);
        assertTrue(box.sizeX() > HologramLayout.CELL_SIZE * 0.5);
    }

    @Test
    void aMarkerInsideTheRadarKeepsItsBearingAndScale() {
        HologramLayout.Marker marker = HologramLayout.marker(100, -50);
        assertTrue(!marker.clamped());
        assertEquals(100 * HologramLayout.BLOCKS_TO_MODEL, marker.x(), EPS);
        assertEquals(-50 * HologramLayout.BLOCKS_TO_MODEL, marker.z(), EPS);
    }

    @Test
    void aFarMarkerIsPinnedToTheEdgeOnTheSameBearing() {
        HologramLayout.Marker marker = HologramLayout.marker(3000, 0);
        assertTrue(marker.clamped());
        assertEquals(HologramLayout.MODEL_WIDTH / 2 * HologramLayout.EDGE_FRACTION, marker.x(), EPS);
        assertEquals(0, marker.z(), EPS);
        HologramLayout.Marker diagonal = HologramLayout.marker(-4000, -4000);
        assertTrue(diagonal.x() < 0 && diagonal.z() < 0, "north-west stays north-west");
        assertEquals(diagonal.x(), diagonal.z(), EPS);
        assertEquals(HologramLayout.MODEL_WIDTH / 2 * HologramLayout.EDGE_FRACTION, Math.hypot(diagonal.x(), diagonal.z()), 1e-9);
    }

    @Test
    void northIsUpSoANorthernTargetHasANegativeZ() {
        assertTrue(HologramLayout.marker(0, -120).z() < 0);
        assertTrue(HologramLayout.marker(0, 120).z() > 0);
    }

    @Test
    void theCentreAndNonsenseAreSafe() {
        HologramLayout.Marker centre = HologramLayout.marker(0, 0);
        assertEquals(0, centre.x(), EPS);
        assertEquals(0, centre.z(), EPS);
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            HologramLayout.Marker marker = HologramLayout.marker(bad, 10);
            assertTrue(Double.isFinite(marker.x()) && Double.isFinite(marker.z()), "a bad input must not become a bad entity position");
        }
    }

    @Test
    void theAnchorSitsOnTheLineOfSightAndJustAboveItWhateverTheYawAndPitch() {
        double[] yaws = {0, 90, 180, 270, -45, 135};
        double[] pitches = {-60, -20, 0, 20, 35, 60, 89};
        for (double yaw : yaws) for (double pitch : pitches) {
            HologramLayout.Point anchor = HologramLayout.anchor(10, 70, -5, yaw, pitch);
            double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
            double dx = anchor.x() - 10, dy = anchor.y() - 70, dz = anchor.z() + 5;
            double along = dx * (-Math.sin(y) * Math.cos(p)) + dy * -Math.sin(p) + dz * (Math.cos(y) * Math.cos(p));
            double above = dx * (-Math.sin(y) * Math.sin(p)) + dy * Math.cos(p) + dz * (Math.cos(y) * Math.sin(p));
            assertEquals(HologramLayout.AHEAD, along, 1e-9, "yaw " + yaw + " pitch " + pitch);
            assertEquals(HologramLayout.RAISE, above, 1e-9, "yaw " + yaw + " pitch " + pitch);
            assertEquals(Math.hypot(HologramLayout.AHEAD, HologramLayout.RAISE), Math.sqrt(dx * dx + dy * dy + dz * dz), 1e-9);
        }
    }

    @Test
    void aLevelLookPutsItAheadAndAboveTheEyesInTheWorldDirectionTheyFace() {
        HologramLayout.Point south = HologramLayout.anchor(0, 70, 0, 0, 0);   // yaw 0 faces south, +z
        assertEquals(0, south.x(), 1e-9);
        assertEquals(HologramLayout.AHEAD, south.z(), 1e-9);
        assertEquals(70 + HologramLayout.RAISE, south.y(), 1e-9);
        HologramLayout.Point west = HologramLayout.anchor(0, 70, 0, 90, 0);   // yaw 90 faces west, -x
        assertEquals(-HologramLayout.AHEAD, west.x(), 1e-9);
        assertEquals(0, west.z(), 1e-9);
    }

    @Test
    void lookingStraightUpOrDownOrAtNonsenseNeverProducesABadPosition() {
        for (double pitch : new double[] {90, -90, 1000, Double.NaN, Double.POSITIVE_INFINITY}) {
            HologramLayout.Point anchor = HologramLayout.anchor(1, 2, 3, 45, pitch);
            assertTrue(Double.isFinite(anchor.x()) && Double.isFinite(anchor.y()) && Double.isFinite(anchor.z()), "pitch " + pitch);
        }
        HologramLayout.Point nan = HologramLayout.anchor(1, 2, 3, Double.NaN, 10);
        assertTrue(Double.isFinite(nan.x()) && Double.isFinite(nan.y()) && Double.isFinite(nan.z()));
    }

    @Test
    void theColourCyclesThroughTheRampAndNeverLeavesIt() {
        for (int bands = 1; bands <= HologramPlan.MAX_BANDS; bands++) for (int band = 0; band < bands; band++) {
            int shade = HologramLayout.shade(band, bands);
            assertTrue(shade >= 0 && shade < HologramLayout.SHADES, "bands=" + bands + " band=" + band);
        }
        assertEquals(0, HologramLayout.shade(0, 12));
        assertEquals(HologramLayout.SHADES - 1, HologramLayout.shade(11, 12));
    }
}
