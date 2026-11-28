package com.cobbleraids.validation.perf;

/** Bounded, owner-thread nesting clock. Self time excludes every child, including unreported children. */
final class RootTimings {
    record Span(long elapsedNanos, long selfNanos, int depth) {}
    private final long[] starts, children;
    private int depth, skipped, maxDepth;
    private long calls, overflow, skippedStart;
    RootTimings(int capacity) { starts = new long[capacity]; children = new long[capacity]; }
    void enter(long now) {
        calls++;
        if (skipped > 0 || depth == starts.length) { if (skipped == 0) skippedStart = now; skipped++; overflow++; return; }
        starts[depth] = now; children[depth] = 0; depth++;
        maxDepth = Math.max(maxDepth, depth);
    }
    Span exit(long now) {
        if (skipped > 0) { if (--skipped == 0 && depth > 0) children[depth - 1] += now - skippedStart; return null; }
        if (depth == 0) throw new IllegalStateException("Unbalanced root return");
        int index = --depth;
        long elapsed = now - starts[index];
        if (depth > 0) children[depth - 1] += elapsed;
        return new Span(elapsed, Math.max(0, elapsed - children[index]), depth);
    }
    long calls() { return calls; }
    long overflow() { return overflow; }
    int maxDepth() { return maxDepth; }
    boolean balanced() { return depth == 0 && skipped == 0; }
}
