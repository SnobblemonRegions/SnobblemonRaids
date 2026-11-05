package com.cobbleraids.radar;

import java.util.ArrayList;
import java.util.List;

/**
 * The terrain of the server-side radar hologram, as a bounded set of terraced columns. No Minecraft, Polymer or client dependency, so the limits are
 * unit tested: every viewer's hologram is built from this plan and can never exceed it.
 *
 * <p>The input is the radar's own height grid ({@link RadarRenderer#GRID} squared absolute heights, {@link RadarRenderer#UNKNOWN} where nothing was
 * sampled), so the hologram adds no terrain queries. Two by two samples make one cell, the cells are cropped to a circle, and each known cell takes the
 * mean of the samples it has. Heights are then stepped into at most {@link #MAX_BANDS} terraces, normalized to the sampled terrain (it is a model of the
 * shape of the ground, not an altitude scale). A cell with no sample is a hole: nothing is invented there.
 */
public record HologramPlan(List<Column> columns, int bands) {
    /** Cells across the hologram: the 33 samples make 16 cells of 2x2 (the last row and column are left out). */
    public static final int CELLS = 16;
    public static final int MAX_BANDS = 12;
    public static final int MAX_COLUMNS = CELLS * CELLS;

    /** One column of the model: its cell and its terrace, 0 (lowest known ground) up to {@code bands - 1}. */
    public record Column(int cellX, int cellZ, int band) {}

    public HologramPlan {
        columns = List.copyOf(columns);
    }

    public static HologramPlan of(int[] heights) {
        int grid = RadarRenderer.GRID;
        if (heights.length != grid * grid) throw new IllegalArgumentException("Expected " + grid + "x" + grid + " terrain");
        double radius = CELLS / 2.0;
        int[] cellX = new int[MAX_COLUMNS], cellZ = new int[MAX_COLUMNS];
        long[] cellHeight = new long[MAX_COLUMNS];
        int known = 0;
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (int cz = 0; cz < CELLS; cz++) {
            for (int cx = 0; cx < CELLS; cx++) {
                if (Math.hypot(cx + 0.5 - radius, cz + 0.5 - radius) > radius) continue;
                long sum = 0;
                int count = 0;
                for (int dz = 0; dz < 2; dz++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int height = heights[(cz * 2 + dz) * grid + (cx * 2 + dx)];
                        if (height == RadarRenderer.UNKNOWN) continue;
                        sum += height;
                        count++;
                    }
                }
                if (count == 0) continue;
                long mean = Math.floorDiv(sum, count);
                cellX[known] = cx;
                cellZ[known] = cz;
                cellHeight[known] = mean;
                known++;
                min = Math.min(min, mean);
                max = Math.max(max, mean);
            }
        }
        if (known == 0) return new HologramPlan(List.of(), 0);
        long range = max - min;
        // A few blocks of relief is one terrace, not twelve paper-thin ones.
        int bands = (int) Math.min(MAX_BANDS, range / 4 + 1);
        List<Column> columns = new ArrayList<>(known);
        for (int i = 0; i < known; i++) {
            int band = (int) ((cellHeight[i] - min) * bands / (range + 1));
            columns.add(new Column(cellX[i], cellZ[i], band));
        }
        return new HologramPlan(columns, bands);
    }
}
