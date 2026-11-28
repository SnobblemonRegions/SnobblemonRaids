package com.cobbleraids.radar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extracts a bounded set of interpolated isolines from the projector's existing height field. */
final class ProjectorContours {
    static final int SIDE = 16;
    static final double STEP = 0.74;
    static final int MAX_SEGMENTS = 176;
    private static final double[] LEVELS = {2.5, 4.5, 6.5, 8.5, 10.5};

    private ProjectorContours() {}

    record Segment(double x, double z, double elevation, double rotation, double length) {}

    private record Point(double x, double z) {
        Point between(Point other, double weight) {
            return new Point(x + (other.x - x) * weight, z + (other.z - z) * weight);
        }

        double distance(Point other) {
            return Math.hypot(x - other.x, z - other.z);
        }
    }

    static List<Segment> build(int[] bands) {
        var paths = new ArrayList<List<Segment>>();
        for (double level : LEVELS) {
            var edges = new LinkedHashMap<Point, List<Point>>();
            for (int z = 0; z < SIDE - 1; z++) {
                for (int x = 0; x < SIDE - 1; x++) {
                    int index = z * SIDE + x;
                    traceCell(edges, x, z, level, bands[index], bands[index + 1], bands[index + SIDE + 1], bands[index + SIDE]);
                }
            }
            var endpoints = edges.entrySet().stream().filter(e -> e.getValue().size() == 1)
                    .map(Map.Entry::getKey).toList();
            for (Point start : endpoints) {
                if (edges.containsKey(start)) paths.add(segments(walk(edges, start), level));
            }
            while (!edges.isEmpty()) {
                paths.add(segments(walk(edges, edges.keySet().iterator().next()), level));
            }
        }
        // Keep complete, longer paths when rugged terrain exhausts the segment pool.
        paths.sort(Comparator.<List<Segment>>comparingDouble(path ->
                path.stream().mapToDouble(Segment::length).sum()).reversed());
        var result = new ArrayList<Segment>(MAX_SEGMENTS);
        for (var path : paths) {
            if (result.size() + path.size() <= MAX_SEGMENTS) result.addAll(path);
        }
        return result;
    }

    static boolean inside(int column, int row) {
        double x = column - 7.5;
        double z = row - 7.5;
        return x * x + z * z <= 7.75 * 7.75;
    }

    private static void traceCell(Map<Point, List<Point>> edges, int x, int z, double level,
                                  int nw, int ne, int se, int sw) {
        if (nw == 0 || ne == 0 || se == 0 || sw == 0) return;
        var crossings = new ArrayList<Point>(4);
        cross(crossings, x, z, nw, ne, level, true);
        cross(crossings, x + 1, z, ne, se, level, false);
        cross(crossings, x, z + 1, sw, se, level, true);
        cross(crossings, x, z, nw, sw, level, false);
        if (crossings.size() == 2) {
            connect(edges, crossings.get(0), crossings.get(1));
        } else if (crossings.size() == 4) {
            // For a saddle, connect around the two corners on the opposite side of the center.
            boolean centerHigh = nw + ne + se + sw >= level * 4;
            if (centerHigh == (nw > level)) {
                connect(edges, crossings.get(0), crossings.get(1));
                connect(edges, crossings.get(2), crossings.get(3));
            } else {
                connect(edges, crossings.get(0), crossings.get(3));
                connect(edges, crossings.get(1), crossings.get(2));
            }
        }
    }

    private static void cross(List<Point> crossings, int x, int z, int from, int to, double level, boolean horizontal) {
        if ((from > level) == (to > level)) return;
        double fraction = (level - from) / (to - from);
        crossings.add(new Point((x - 7.5 + (horizontal ? fraction : 0)) * STEP,
                (z - 7.5 + (horizontal ? 0 : fraction)) * STEP));
    }

    private static void connect(Map<Point, List<Point>> edges, Point a, Point b) {
        edges.computeIfAbsent(a, ignored -> new ArrayList<>(2)).add(b);
        edges.computeIfAbsent(b, ignored -> new ArrayList<>(2)).add(a);
    }

    private static List<Point> walk(Map<Point, List<Point>> edges, Point start) {
        var path = new ArrayList<Point>();
        Point point = start;
        path.add(point);
        while (edges.containsKey(point)) {
            Point next = edges.get(point).removeFirst();
            if (edges.get(point).isEmpty()) edges.remove(point);
            List<Point> reverse = edges.get(next);
            reverse.remove(point);
            if (reverse.isEmpty()) edges.remove(next);
            path.add(next);
            point = next;
        }
        return path;
    }

    private static List<Segment> segments(List<Point> path, double level) {
        boolean loop = path.getFirst().equals(path.getLast());
        int count = loop ? path.size() - 1 : path.size();
        var smoothed = new ArrayList<Point>(path.size());
        for (int i = 0; i < count; i++) {
            Point point = path.get(i);
            if (loop || (i > 0 && i + 1 < count)) {
                Point previous = path.get((i + count - 1) % count);
                Point next = path.get((i + 1) % count);
                point = point.between(previous.between(next, 0.5), 0.34);
            }
            smoothed.add(point);
        }
        if (loop) smoothed.add(smoothed.getFirst());
        var result = new ArrayList<Segment>();
        Point start = smoothed.getFirst();
        for (int i = 1; i < smoothed.size(); i++) {
            Point end = smoothed.get(i);
            if (i + 1 < smoothed.size() && end.distance(start) < 0.24) continue;
            Point middle = start.between(end, 0.5);
            double length = start.distance(end);
            double rotation = Math.toDegrees(Math.atan2(start.z() - end.z(), end.x() - start.x()));
            result.add(new Segment(middle.x(), middle.z(), level, rotation, length));
            start = end;
        }
        return result;
    }
}
