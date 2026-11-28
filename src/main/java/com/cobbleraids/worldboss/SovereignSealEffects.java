package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.List;

/** Bounded particle positions attached to the seal's native item-display poses. */
final class SovereignSealEffects {
    static final int INTERVAL_TICKS = 16;
    static final int PACKET_BUDGET = 800;
    static final double VIEW_DISTANCE = 256;
    record Point(double x, double y, double z, boolean scarlet, float size) {}

    private SovereignSealEffects() {}

    static boolean due(long tick, long previous) {
        return previous == Long.MIN_VALUE || tick < previous || tick - previous >= INTERVAL_TICKS;
    }

    static List<Point> frame(double radius, long tick, boolean minimal) {
        var ring = SovereignSealGeometry.ring(radius, tick);
        var crest = SovereignSealGeometry.crest(radius, tick);
        double phase = Math.floorMod(tick, 120L) / 120.0 * Math.TAU;
        // Arena-distance silhouettes need a high floor; vanilla caps dust at scale four.
        float size = (float) (3.5 + 0.5 * Math.sin(phase));
        var points = new ArrayList<Point>(minimal ? 4 : 16);
        for (int part = 0; part < 16; part++) {
            if (part == 2 || part == 7 || part == 12 || (minimal && part != 0 && part != 5 && part != 10)) continue;
            double angle = part * Math.TAU / 16;
            // Crawl along each intact gold section, leaving the authored gaps dark.
            double along = 0.08 * Math.sin(phase + part);
            points.add(place(ring, SovereignSealGeometry.MODEL_RADIUS * Math.cos(angle) - along * Math.sin(angle),
                    0.03, SovereignSealGeometry.MODEL_RADIUS * Math.sin(angle) + along * Math.cos(angle), false, size));
        }
        int wisps = minimal ? 1 : 3;
        for (int i = 0; i < wisps; i++) {
            double angle = phase + i * Math.TAU / 3;
            points.add(place(crest, 0.23 * Math.cos(angle), -0.12 + 0.22 * Math.sin(angle), -0.19, true, size));
        }
        return List.copyOf(points);
    }

    private static Point place(SovereignSealGeometry.Pose pose, double x, double y, double z, boolean scarlet, float size) {
        // Native ItemDisplayRenderer adds a Y half-turn before applying the display quaternion.
        x *= -pose.scale(); y *= pose.scale(); z *= -pose.scale();
        double tx = 2 * (pose.qy() * z - pose.qz() * y);
        double ty = 2 * (pose.qz() * x - pose.qx() * z);
        double tz = 2 * (pose.qx() * y - pose.qy() * x);
        return new Point(pose.x() + x + pose.qw() * tx + pose.qy() * tz - pose.qz() * ty,
                pose.y() + y + pose.qw() * ty + pose.qz() * tx - pose.qx() * tz,
                pose.z() + z + pose.qw() * tz + pose.qx() * ty - pose.qy() * tx, scarlet, size);
    }
}
