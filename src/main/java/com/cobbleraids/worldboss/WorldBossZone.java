package com.cobbleraids.worldboss;

/** The shared three-dimensional boundary used by admission, music and the visible bubble. */
public final class WorldBossZone {
    private WorldBossZone() {}
    /** Existing presence gets one block of hysteresis; newcomers must cross the real sphere. */
    public static boolean present(double cx, double cy, double cz, double radius, double x, double y, double z, boolean previous) {
        if (!Double.isFinite(radius) || radius <= 0) return false;
        return inside(cx, cy, cz, radius + (previous ? 1 : 0), x, y, z);
    }

    public static boolean inside(double cx, double cy, double cz, double radius, double x, double y, double z) {
        if (!Double.isFinite(radius) || radius <= 0) return false;
        double dx = (x - cx) / radius, dy = (y - cy) / radius, dz = (z - cz) / radius;
        return dx * dx + dy * dy + dz * dz <= 1.0;
    }

    /** Radius of the horizontal ring at this height; zero outside the sphere. */
    public static double horizontalRadius(double cy, double radius, double y) {
        if (!Double.isFinite(radius) || radius <= 0 || !Double.isFinite(cy) || !Double.isFinite(y)) return 0;
        double dy = (y - cy) / radius;
        return Math.abs(dy) >= 1 ? 0 : radius * Math.sqrt(1 - dy * dy);
    }
}
