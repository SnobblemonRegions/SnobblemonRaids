package com.cobbleraids.radar;

/**
 * What an open radar shows a player. No Minecraft types, so the rule is tested.
 *
 * <p>The flat map and the server-side hologram are never shown together: the held map rises up the screen as the player looks down at the hologram and
 * covers it. By default a player sees the hologram; they can choose the flat map in the radar menu, an operator can switch the hologram off for
 * everyone, and a player whose client has the optional companion keeps the map, because the companion draws its own 3D from it.
 */
public final class RadarDisplayRules {
    private RadarDisplayRules() {}

    public static boolean showsFlatMap(boolean prefersFlatMap, boolean serverHologramEnabled, boolean hasCompanion) {
        return prefersFlatMap || !serverHologramEnabled || hasCompanion;
    }

    public static boolean showsServerHologram(boolean prefersFlatMap, boolean serverHologramEnabled, boolean hasCompanion) {
        return !showsFlatMap(prefersFlatMap, serverHologramEnabled, hasCompanion);
    }
}
