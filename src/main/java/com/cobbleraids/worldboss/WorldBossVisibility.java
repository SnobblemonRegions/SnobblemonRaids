package com.cobbleraids.worldboss;

/** One owned showpiece must cover its arena plus a small observer margin. */
final class WorldBossVisibility {
    private WorldBossVisibility() {}
    static int trackingRange(int nativeRange, double radius) {
        if (nativeRange <= 0 || !Double.isFinite(radius) || radius <= 0) return nativeRange;
        return Math.max(nativeRange, (int) Math.ceil(Math.min(1024, radius) + 32));
    }
}
