package com.cobbleraids.worldboss;

/** Pure, sampled route proof. Tokens are runtime-only; only completed deliveries are persisted. */
final class WorldBossRelay {
    static final double REACH = 1.5, CORRIDOR = 2.5, MAX_STEP = 3;
    record Point(double x, double y, double z) {
        Point { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Invalid relay position"); }
        double distanceSquared(Point other) { double dx = x - other.x, dy = y - other.y, dz = z - other.z; return dx * dx + dy * dy + dz * dz; }
    }
    record Route(WorldBossWards.Pylon cache, WorldBossWards.Pylon checkpoint) {
        Route { java.util.Objects.requireNonNull(cache); java.util.Objects.requireNonNull(checkpoint); leg(cache, checkpoint); }
        void validate(WorldBossWards.Pylon pylon) {
            java.util.Objects.requireNonNull(pylon); leg(checkpoint, pylon);
            if (at(cache).distanceSquared(at(pylon)) < 64) throw new IllegalArgumentException("Relay cache must be separate from its pylon");
        }
        private static void leg(WorldBossWards.Pylon from, WorldBossWards.Pylon to) {
            double distance = at(from).distanceSquared(at(to));
            if (!from.dimension().equals(to.dimension()) || from.y() != to.y() || distance < 64 || distance > 576) throw new IllegalArgumentException("Relay legs must be level and 8-24 blocks long");
        }
    }
    enum Phase { TO_CHECKPOINT, TO_PYLON, READY, CHANNEL, COMPLETE, CANCELLED }
    private final Route route;
    private final WorldBossWards.Pylon pylon;
    private Point last, channelAt;
    private long lastRemaining, finishRemaining;
    private Phase phase = Phase.TO_CHECKPOINT;

    WorldBossRelay(Route route, WorldBossWards.Pylon pylon, Point start, long remaining) {
        route.validate(pylon);
        if (remaining < 0 || remaining > WorldBossClock.ACTIVE_MILLIS || start.distanceSquared(at(route.cache())) > REACH * REACH) throw new IllegalArgumentException("Relay must start at its cache");
        this.route = route; this.pylon = pylon; last = start; lastRemaining = remaining;
    }
    Phase phase() { return phase; }
    long finishRemaining() { return finishRemaining; }
    boolean channel(Point point, long remaining) {
        if (phase != Phase.READY || remaining < 6_000 || sample(point, remaining) != Phase.READY) return false;
        phase = Phase.CHANNEL; channelAt = point; finishRemaining = remaining - 6_000; return true;
    }
    Phase sample(Point point, long remaining) {
        if (phase == Phase.CANCELLED || phase == Phase.COMPLETE) return phase;
        if (remaining < 0 || remaining > lastRemaining || last.distanceSquared(point) > MAX_STEP * MAX_STEP) return cancel();
        if (phase == Phase.CHANNEL) {
            if (channelAt.distanceSquared(point) > 0.09) return cancel();
            if (remaining <= finishRemaining) phase = Phase.COMPLETE;
        } else if (phase == Phase.READY) {
            if (point.distanceSquared(at(pylon)) > REACH * REACH) return cancel();
        } else {
            Point from = at(phase == Phase.TO_CHECKPOINT ? route.cache() : route.checkpoint());
            Point target = at(phase == Phase.TO_CHECKPOINT ? route.checkpoint() : pylon);
            if (segmentDistanceSquared(point, from, target) > CORRIDOR * CORRIDOR) return cancel();
            if (point.distanceSquared(target) <= REACH * REACH) {
                if (phase == Phase.TO_CHECKPOINT) phase = Phase.TO_PYLON;
                else phase = Phase.READY;
            }
        }
        last = point; lastRemaining = remaining; return phase;
    }
    private Phase cancel() { phase = Phase.CANCELLED; return phase; }
    static Point at(WorldBossWards.Pylon pylon) { return new Point(pylon.x() + 0.5, pylon.y(), pylon.z() + 0.5); }
    private static double segmentDistanceSquared(Point point, Point a, Point b) {
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double t = Math.max(0, Math.min(1, ((point.x - a.x) * dx + (point.y - a.y) * dy + (point.z - a.z) * dz) / (dx * dx + dy * dy + dz * dz)));
        return point.distanceSquared(new Point(a.x + t * dx, a.y + t * dy, a.z + t * dz));
    }
}
