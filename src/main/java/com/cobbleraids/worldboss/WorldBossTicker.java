package com.cobbleraids.worldboss;

/** Runtime-only monotonic sampling; nanoTime origins are never persisted or compared across boots. */
final class WorldBossTicker {
    private long lastNanos;
    private long remainderNanos;
    private long unflushedMillis;

    WorldBossTicker(long nowNanos) { lastNanos = nowNanos; }

    long sample(long nowNanos) {
        long elapsed = nowNanos - lastNanos; // Signed subtraction also handles nanoTime rollover.
        lastNanos = nowNanos;
        if (elapsed < 0) return -1;
        long millis = elapsed / 1_000_000;
        remainderNanos += elapsed % 1_000_000;
        millis += remainderNanos / 1_000_000;
        remainderNanos %= 1_000_000;
        unflushedMillis = unflushedMillis > Long.MAX_VALUE - millis ? Long.MAX_VALUE : unflushedMillis + millis;
        return millis;
    }

    boolean checkpointDue() { return unflushedMillis >= 2_000; }
    void checkpointed() { unflushedMillis = 0; }
}
