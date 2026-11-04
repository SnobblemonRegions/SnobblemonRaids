package com.cobbleraids.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The server-side hologram's terrain plan: bounded, circular, terraced, and honest about what it does not know. */
class HologramPlanTest {

    private static final int GRID = RadarRenderer.GRID;
    private static final int UNKNOWN = RadarRenderer.UNKNOWN;

    private static int[] filled(int height) {
        int[] heights = new int[GRID * GRID];
        Arrays.fill(heights, height);
        return heights;
    }

    @Test
    void nothingKnownPlansNothing() {
        HologramPlan plan = HologramPlan.of(filled(UNKNOWN));
        assertTrue(plan.columns().isEmpty());
        assertEquals(0, plan.bands());
    }

    @Test
    void aWrongSizedGridIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> HologramPlan.of(new int[10]));
    }

    @Test
    void theColumnCountIsBoundedWhateverTheTerrain() {
        int[] heights = new int[GRID * GRID];
        for (int i = 0; i < heights.length; i++) heights[i] = (i * 37) % 300;   // jagged on purpose
        HologramPlan plan = HologramPlan.of(heights);
        assertTrue(plan.columns().size() <= HologramPlan.MAX_COLUMNS, "got " + plan.columns().size());
        assertTrue(plan.bands() <= HologramPlan.MAX_BANDS);
        assertFalse(plan.columns().isEmpty());
    }

    @Test
    void theHologramIsCroppedToACircle() {
        HologramPlan plan = HologramPlan.of(filled(64));
        double radius = HologramPlan.CELLS / 2.0;
        for (HologramPlan.Column column : plan.columns()) {
            double dx = column.cellX() + 0.5 - radius, dz = column.cellZ() + 0.5 - radius;
            assertTrue(Math.hypot(dx, dz) <= radius, "cell " + column.cellX() + "," + column.cellZ() + " is outside the circle");
        }
        // A full-known disc is most of the circle's area, and far fewer cells than the square.
        assertTrue(plan.columns().size() > HologramPlan.CELLS * HologramPlan.CELLS / 2);
        assertTrue(plan.columns().size() < HologramPlan.CELLS * HologramPlan.CELLS);
    }

    @Test
    void flatTerrainIsOneBandNotADivisionByZero() {
        HologramPlan plan = HologramPlan.of(filled(70));
        assertEquals(1, plan.bands());
        for (HologramPlan.Column column : plan.columns()) assertEquals(0, column.band());
    }

    @Test
    void higherGroundNeverGetsALowerBand() {
        int[] heights = new int[GRID * GRID];
        for (int z = 0; z < GRID; z++) for (int x = 0; x < GRID; x++) heights[z * GRID + x] = x * 8;   // a ramp rising to the east
        HologramPlan plan = HologramPlan.of(heights);
        assertTrue(plan.bands() > 1);
        for (HologramPlan.Column a : plan.columns()) for (HologramPlan.Column b : plan.columns()) {
            if (a.cellZ() == b.cellZ() && a.cellX() < b.cellX()) assertTrue(a.band() <= b.band(), "the ramp must not dip");
        }
    }

    @Test
    void unknownTerrainIsAHoleNotInventedGround() {
        int[] heights = filled(64);
        // Blank out the whole north-west corner of the sampled grid.
        for (int z = 0; z < 12; z++) for (int x = 0; x < 12; x++) heights[z * GRID + x] = UNKNOWN;
        HologramPlan full = HologramPlan.of(filled(64));
        HologramPlan holed = HologramPlan.of(heights);
        assertTrue(holed.columns().size() < full.columns().size());
        for (HologramPlan.Column column : holed.columns()) {
            assertFalse(column.cellX() < 3 && column.cellZ() < 3, "no column where nothing was sampled");
        }
    }

    @Test
    void aPartlyKnownCellUsesWhatWasSampled() {
        int[] heights = filled(UNKNOWN);
        int centre = GRID / 2;
        heights[centre * GRID + centre] = 90;   // one lone sample
        HologramPlan plan = HologramPlan.of(heights);
        assertEquals(1, plan.columns().size());
        assertEquals(1, plan.bands());
    }

    @Test
    void extremeHeightsDoNotOverflow() {
        int[] heights = filled(Short.MAX_VALUE);
        heights[0] = Short.MIN_VALUE + 1;
        heights[heights.length - 1] = Short.MAX_VALUE;
        HologramPlan plan = HologramPlan.of(heights);
        assertTrue(plan.bands() >= 1 && plan.bands() <= HologramPlan.MAX_BANDS);
        for (HologramPlan.Column column : plan.columns()) {
            assertTrue(column.band() >= 0 && column.band() < plan.bands());
        }
    }

    @Test
    void theSamePlanComesOutForTheSameTerrain() {
        int[] heights = new int[GRID * GRID];
        for (int i = 0; i < heights.length; i++) heights[i] = 40 + (i % 17) * 3;
        assertEquals(HologramPlan.of(heights), HologramPlan.of(heights.clone()), "a refresh of unchanged terrain must not look like a change");
    }
}
