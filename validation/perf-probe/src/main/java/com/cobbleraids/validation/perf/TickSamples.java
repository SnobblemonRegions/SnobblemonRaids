package com.cobbleraids.validation.perf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded raw samples; percentiles are nearest-rank over one declared recording, never averages. */
final class TickSamples {
    record Tick(long id, long epochMillis, String phase, int players, long nanos, long intervalNanos) {}
    record Summary(int count, long dropped, double p50Ms, double p95Ms, double p99Ms, double maxMs,
                   long over250ms, int minPlayers, int maxPlayers, double maxIntervalMs) {}
    private final int capacity;
    private final List<Tick> ticks = new ArrayList<>();
    private long dropped;
    TickSamples(int capacity) { if (capacity <= 0) throw new IllegalArgumentException("capacity"); this.capacity = capacity; }
    void add(Tick tick) {
        if (tick.nanos() < 0 || tick.intervalNanos() < 0) throw new IllegalArgumentException("negative duration");
        if (ticks.size() == capacity) { dropped++; return; }
        ticks.add(tick);
    }
    List<Tick> ticks() { return List.copyOf(ticks); }
    Summary summary(String phase) {
        var selected = ticks.stream().filter(t -> phase == null || t.phase().equals(phase)).toList();
        long[] times = selected.stream().mapToLong(Tick::nanos).sorted().toArray();
        return new Summary(times.length, dropped, percentile(times, .50), percentile(times, .95), percentile(times, .99),
                times.length == 0 ? 0 : times[times.length - 1] / 1e6,
                Arrays.stream(times).filter(n -> n > 250_000_000).count(),
                selected.stream().mapToInt(Tick::players).min().orElse(0), selected.stream().mapToInt(Tick::players).max().orElse(0),
                selected.stream().mapToLong(Tick::intervalNanos).max().orElse(0) / 1e6);
    }
    private static double percentile(long[] ordered, double fraction) {
        return ordered.length == 0 ? 0 : ordered[(int) Math.ceil(ordered.length * fraction) - 1] / 1e6;
    }
}
