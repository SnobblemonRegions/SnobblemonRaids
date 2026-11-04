package com.cobbleraids.raidio;

/**
 * Whether the Raid Radar is Raidio, which it is by default: an open radar tunes the player to a raid by sound and draws no map or hologram. {@code -Dcobbleraids.raidio=off}
 * brings back the hologram and flat map, which are still in the mod.
 */
public final class RaidioMode {
    private static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("cobbleraids.raidio", "on"));

    private RaidioMode() {}

    public static boolean enabled() {
        return ENABLED;
    }
}
