package com.cobbleraids.worldboss;

/** Prototype poses relative to the arena floor. Resource models are centred on the native item origin. */
final class SovereignSealGeometry {
    static final double MODEL_RADIUS = 14.0 / 16.0;
    static final int PERIOD_TICKS = 3600;
    private static final double TILT = Math.toRadians(22.5);

    record Pose(double x, double y, double z, double qx, double qy, double qz, double qw, double scale) {}

    private SovereignSealGeometry() {}

    static Pose ring(double radius, long tick) {
        validate(radius);
        double half = phase(tick, PERIOD_TICKS) / 2;
        double sx = Math.sin(TILT / 2), cx = Math.cos(TILT / 2);
        double sy = Math.sin(half), cy = Math.cos(half);
        // The band's thickness and polygon corners grow with radius too, so clearance must scale with them.
        return new Pose(0, radius * (Math.sin(TILT) + 0.04) + 6, 0,
                sx * cy, cx * sy, sx * sy, cx * cy, radius / MODEL_RADIUS);
    }

    static Pose crest(double radius, long tick) {
        validate(radius);
        double bob = Math.sin(phase(tick, 180));
        // Native ItemDisplayRenderer adds a Y half-turn before rendering the model's north-facing gold trim.
        double yaw = (Math.atan2(-0.72, 0.52) + Math.PI) / 2;
        double roll = Math.toRadians(3) * bob / 2;
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sz = Math.sin(roll), cz = Math.cos(roll);
        return new Pose(-radius * 0.72, radius * 0.25 + 6 + bob * 0.75, radius * 0.52,
                sy * sz, sy * cz, cy * sz, cy * cz, 14);
    }

    static Pose halo(double radius, long tick) {
        var p = crest(radius, tick);
        double half = -phase(tick, 2400) / 2;
        double s = Math.sin(half), c = Math.cos(half);
        return new Pose(p.x(), p.y() + 0.5, p.z(), p.qx()*c + p.qy()*s,
                p.qy()*c - p.qx()*s, p.qz()*c + p.qw()*s, p.qw()*c - p.qz()*s,
                18 * (1 + 0.035 * Math.sin(phase(tick, 240))));
    }

    static Pose energy(double radius, long tick) {
        long backwards = (1200 - Math.floorMod(tick, 1200L)) % 1200;
        var p = ring(radius, backwards * 3);
        return new Pose(p.x(), p.y() + 1, p.z(), p.qx(), p.qy(), p.qz(), p.qw(), p.scale() * 0.97);
    }

    static Pose ground(double radius, long tick) {
        validate(radius);
        double half = phase(tick, 7200) / 2;
        return new Pose(0, Math.max(0.15, radius * 0.001), 0, 0, Math.sin(half), 0, Math.cos(half),
                radius * 0.58 / MODEL_RADIUS * (1 + 0.005 * Math.sin(phase(tick, 240))));
    }

    static double openingScale(long ageTicks) {
        double progress = Math.clamp(ageTicks / 120.0, 0, 1);
        return 0.08 + 0.92 * progress * progress * (3 - 2 * progress);
    }

    static Pose reveal(Pose pose, long ageTicks) {
        return new Pose(pose.x(), pose.y(), pose.z(), pose.qx(), pose.qy(), pose.qz(), pose.qw(), pose.scale() * openingScale(ageTicks));
    }

    private static double phase(long tick, int period) {
        return Math.floorMod(tick, (long) period) / (double) period * Math.TAU;
    }

    private static void validate(double radius) {
        if (!Double.isFinite(radius) || radius < 8 || radius > 1024) throw new IllegalArgumentException("Unsupported seal radius");
    }
}
