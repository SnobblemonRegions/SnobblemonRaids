package com.cobbleraids.worldboss;

/** Server-thread-only native-start budget. No catch-up burst after a slow tick or long hold. */
final class WorldBossStartPacer {
    static final long INTERVAL_NANOS = 250_000_000L;
    private boolean attempted;
    private long lastAttempt;

    int budget(long nowNanos) { return !attempted || nowNanos - lastAttempt >= INTERVAL_NANOS ? 1 : 0; }
    void attempted(long nowNanos) { attempted = true; lastAttempt = nowNanos; }
    void reset() { attempted = false; }
}
