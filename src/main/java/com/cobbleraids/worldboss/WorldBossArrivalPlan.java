package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Bounded candidate generation, without terrain searches or guessed heightmaps. */
final class WorldBossArrivalPlan {
    static final int MAX_CANDIDATES = 96;
    record Venue(String dimension, double x, double y, double z, double radius) {
        Venue {
            if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Double.isFinite(radius) || radius < 16 || radius > 512) throw new IllegalArgumentException("Invalid audience venue");
        }
        boolean contains(WorldBossTravel.Position point) { return point != null && dimension.equals(point.dimension()) && WorldBossZone.inside(x, y, z, radius + 16, point.x(), point.y(), point.z()); }
        boolean accepts(WorldBossTravel.Position point, boolean watch) {
            return point != null && dimension.equals(point.dimension()) && (watch
                    ? !WorldBossZone.inside(x, y, z, radius + 2, point.x(), point.y(), point.z()) && WorldBossZone.inside(x, y, z, radius + 12, point.x(), point.y(), point.z())
                    : WorldBossZone.inside(x, y, z, radius, point.x(), point.y(), point.z()));
        }
    }
    static List<WorldBossTravel.Position> candidates(Venue venue, List<WorldBossTravel.Position> anchors, boolean watch) {
        if (anchors.size() > (watch ? 25 : 50)) throw new IllegalArgumentException("Too many arrival markers");
        var result = new LinkedHashSet<WorldBossTravel.Position>();
        for (var anchor : anchors) if (venue.accepts(anchor, watch)) result.add(new WorldBossTravel.Position(anchor.dimension(), anchor.x(), anchor.y(), anchor.z(), 0, 0));
        var offsets = new ArrayList<int[]>(); int extent = watch ? 3 : 4;
        for (int x = -extent; x <= extent; x++) for (int z = -extent; z <= extent; z++) offsets.add(new int[]{x, z});
        offsets.sort(Comparator.comparingInt(value -> value[0] * value[0] + value[1] * value[1]));
        outer: for (var anchor : anchors) {
            if (!venue.accepts(anchor, watch)) continue;
            for (var offset : offsets) {
                var point = new WorldBossTravel.Position(venue.dimension(), (watch ? anchor.x() : venue.x()) + offset[0] * (watch ? 3 : 18), anchor.y(),
                        (watch ? anchor.z() : venue.z()) + offset[1] * (watch ? 3 : 18), 0, 0);
                if (venue.accepts(point, watch)) result.add(point);
                if (result.size() >= MAX_CANDIDATES) break outer;
            }
        }
        return List.copyOf(result);
    }
    static List<WorldBossTravel.Position> spread(List<WorldBossTravel.Position> candidates, List<WorldBossTravel.Position> occupied) {
        var ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble((WorldBossTravel.Position point) -> clearance(point, occupied)).reversed());
        return List.copyOf(ordered);
    }
    private static double clearance(WorldBossTravel.Position point, List<WorldBossTravel.Position> occupied) {
        double distance = Double.POSITIVE_INFINITY;
        for (var other : occupied) if (point.dimension().equals(other.dimension())) {
            double dx = point.x() - other.x(), dy = point.y() - other.y(), dz = point.z() - other.z();
            distance = Math.min(distance, dx * dx + dy * dy + dz * dz);
        }
        return distance;
    }
}
