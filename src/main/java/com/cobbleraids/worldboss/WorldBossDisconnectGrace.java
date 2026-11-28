package com.cobbleraids.worldboss;

/** Runtime-only thirty real seconds. Never serialize nanoTime across server processes. */
public record WorldBossDisconnectGrace(long startedNanos) {
    public static final int SECONDS = 30;
    public boolean expired(long nowNanos) { long elapsed = nowNanos - startedNanos; return elapsed < 0 || elapsed >= SECONDS * 1_000_000_000L; }
}
