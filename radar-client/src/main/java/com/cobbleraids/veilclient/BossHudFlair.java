package com.cobbleraids.veilclient;

/** Selects bounded, time-based pixel-art frames; presentation preferences always win. */
final class BossHudFlair {
    private BossHudFlair() {}
    static int row(long millis, int detail, boolean critical) {
        if (detail != 2) return -1;
        return (int) Math.floorMod(millis / 150,8) + (critical ? 8 : 0);
    }
}
