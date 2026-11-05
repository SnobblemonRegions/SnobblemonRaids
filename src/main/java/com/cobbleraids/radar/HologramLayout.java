package com.cobbleraids.radar;

/**
 * Where the server-side hologram puts things, in model space: metres (blocks) around the model's own origin, north-up (a target to the north has a
 * negative z, as in the world). No Minecraft types, so the decisions that decide what a player sees are unit tested.
 */
public final class HologramLayout {
    /** The model's diameter, in blocks: about arm's length across, small enough to sit in front of the player without filling the view. */
    public static final double MODEL_WIDTH = 0.9;
    public static final double CELL_SIZE = MODEL_WIDTH / HologramPlan.CELLS;
    /** A column fills this much of its cell, so the terraces read as a grid of towers rather than one slab. */
    public static final double GAP_FRACTION = 0.88;
    /** The tallest a column gets, and the least a column of a flat plain has. */
    public static final double MAX_RELIEF = 0.20;
    public static final double MIN_THICKNESS = 0.02;
    /** The grid reaches 16 samples either side of the player's view centre, {@link RadarRenderer#STEP} blocks apart. */
    public static final double BLOCKS_TO_MODEL = (MODEL_WIDTH / 2) / (RadarRenderer.STEP * 16.0);
    /** A target past the edge is pinned this fraction of the way out, so it stays inside the circle. */
    public static final double EDGE_FRACTION = 0.95;
    /** How many colours the terrace ramp has. */
    public static final int SHADES = 5;
    /**
     * Where the model floats, measured from the player's eyes along their line of sight and then up from it: a little over a metre ahead, slightly above
     * the crosshair. It follows where the player looks, not only which way their body faces, so it sits in the same part of the screen (above the held
     * map, which is fixed to the screen) whether they look level or down at it; looking down at it is also what shows the terrain from above.
     */
    public static final double AHEAD = 1.05, RAISE = 0.10;

    private HologramLayout() {}

    /** A position in the world, or an offset from one. */
    public record Point(double x, double y, double z) {}

    /**
     * The model's origin for a player whose eyes are at ({@code eyeX}, {@code eyeY}, {@code eyeZ}), facing {@code yawDegrees} and looking {@code pitchDegrees}
     * (Minecraft's convention: yaw 0 faces south and 90 west, positive pitch is down). Anything that is not a number is taken as zero, and pitch is
     * limited to straight up or down, so a bad input can never become a bad entity position.
     */
    public static Point anchor(double eyeX, double eyeY, double eyeZ, double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(sane(yawDegrees, 0));
        double pitch = Math.toRadians(Math.clamp(sane(pitchDegrees, 0), -90, 90));
        double lookX = -Math.sin(yaw) * Math.cos(pitch), lookY = -Math.sin(pitch), lookZ = Math.cos(yaw) * Math.cos(pitch);
        double upX = -Math.sin(yaw) * Math.sin(pitch), upY = Math.cos(pitch), upZ = Math.cos(yaw) * Math.sin(pitch);
        return new Point(eyeX + lookX * AHEAD + upX * RAISE, eyeY + lookY * AHEAD + upY * RAISE, eyeZ + lookZ * AHEAD + upZ * RAISE);
    }

    private static double sane(double value, double fallback) {
        return Double.isNaN(value) || Double.isInfinite(value) ? fallback : value;
    }

    /** An axis-aligned box in model space: its centre on the ground plane, and its size. The column stands on y = 0. */
    public record Box(double centerX, double centerZ, double sizeX, double sizeY, double sizeZ) {}

    /** A marker's place on the model's ground plane. {@code clamped} says it was pinned to the edge because the target is farther than the radar reaches. */
    public record Marker(double x, double z, boolean clamped) {}

    public static Box column(HologramPlan.Column column, int bands) {
        double x = (column.cellX() + 0.5 - HologramPlan.CELLS / 2.0) * CELL_SIZE;
        double z = (column.cellZ() + 0.5 - HologramPlan.CELLS / 2.0) * CELL_SIZE;
        // Flat ground is a low slab; otherwise each terrace is a step taller than the one below.
        double fraction = bands <= 1 ? 0.35 : (column.band() + 1) / (double) bands;
        double height = MIN_THICKNESS + (MAX_RELIEF - MIN_THICKNESS) * fraction;
        double footprint = CELL_SIZE * GAP_FRACTION;
        return new Box(x, z, footprint, height, footprint);
    }

    /** Where a world offset (blocks east and south of the view centre) lands on the model, pinned to the edge on its own bearing when it is too far. */
    public static Marker marker(double dxBlocks, double dzBlocks) {
        double dx = sane(dxBlocks), dz = sane(dzBlocks);
        double x = dx * BLOCKS_TO_MODEL, z = dz * BLOCKS_TO_MODEL;
        double limit = MODEL_WIDTH / 2 * EDGE_FRACTION;
        double distance = Math.hypot(x, z);
        if (distance <= limit) return new Marker(x, z, false);
        double scale = limit / distance;
        return new Marker(x * scale, z * scale, true);
    }

    /** The colour step of a terrace, 0 (lowest) to {@link #SHADES} - 1, spread evenly over however many terraces there are. */
    public static int shade(int band, int bands) {
        if (bands <= 1) return 1;
        return (int) Math.round(band * (SHADES - 1) / (double) (bands - 1));
    }

    /** Keeps NaN and infinity out of anything that becomes an entity position. */
    private static double sane(double value) {
        if (Double.isNaN(value)) return 0;
        if (Double.isInfinite(value)) return Math.copySign(1.0e9, value);
        return value;
    }
}
