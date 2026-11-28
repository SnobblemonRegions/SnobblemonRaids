package com.cobbleraids.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProjectorContoursTest {
    private static final int SIDE = ProjectorContours.SIDE;

    @Test void followsAnInterpolatedElevationBetweenSampleCenters() {
        int[] bands = new int[SIDE * SIDE];
        for (int i = 0; i < bands.length; i++) bands[i] = 2 + (i % SIDE) / 2;
        var lines = ProjectorContours.build(bands);
        assertFalse(lines.isEmpty());
        var firstLevel = lines.stream().filter(line -> line.elevation() == 2.5).toList();
        assertEquals(SIDE - 1, firstLevel.size());
        for (var segment : firstLevel) {
            assertEquals(-6 * ProjectorContours.STEP, segment.x(), 1e-9);
            assertEquals(90, Math.abs(segment.rotation()), 1e-9);
        }
    }

    @Test void leavesUnknownGroundUnconnected() {
        int[] bands = new int[SIDE * SIDE];
        Arrays.fill(bands, 2);
        for (int i = 0; i < bands.length; i++) {
            int column = i % SIDE;
            if (column == 8) bands[i] = 0;
            else if (column > 8) bands[i] = 11;
        }
        assertTrue(ProjectorContours.build(bands).isEmpty());
    }

    @Test void splitsASaddleIntoTwoPaths() {
        int[] bands = new int[SIDE * SIDE];
        bands[0] = bands[SIDE + 1] = 4;
        bands[1] = bands[SIDE] = 2;
        var lines = ProjectorContours.build(bands);
        assertEquals(2, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.length() > 0 && line.elevation() == 2.5));
    }

    @Test void denseTerrainStaysDeterministicWithinTheModelPool() {
        int[] bands = new int[SIDE * SIDE];
        for (int i = 0; i < bands.length; i++) bands[i] = (i / SIDE + i % SIDE) % 2 == 0 ? 1 : 12;
        var lines = ProjectorContours.build(bands);
        assertFalse(lines.isEmpty());
        assertTrue(lines.size() <= ProjectorContours.MAX_SEGMENTS);
        assertEquals(lines, ProjectorContours.build(bands));
    }
}
