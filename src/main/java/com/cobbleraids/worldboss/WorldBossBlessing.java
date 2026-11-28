package com.cobbleraids.worldboss;

/** Real-world blessing bounds. Bonus uses the ordinary tier's unmultiplied base points. */
final class WorldBossBlessing {
    static final long HOUR = 3_600_000L;
    record Window(long start, long until) {
        Window { if (start <= 0 || until <= start || until - start < HOUR || until - start > 2 * HOUR) throw new IllegalArgumentException("Invalid blessing window"); }
        boolean contains(long instant) { return instant >= start && instant < until; }
    }
    static Window extend(long previousUntil, long committedAt) {
        return new Window(committedAt, Math.min(Math.addExact(Math.max(previousUntil, committedAt), HOUR), Math.addExact(committedAt, 2 * HOUR)));
    }
    static int bonus(int base) { if (base < 0) throw new IllegalArgumentException("Negative base points"); return base / 10; }
    private WorldBossBlessing() {}
}
