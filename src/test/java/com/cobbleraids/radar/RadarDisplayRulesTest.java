package com.cobbleraids.radar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What an open radar shows a player: the floating hologram, or the flat map in their hand. Never both, because the held map covers the hologram. */
class RadarDisplayRulesTest {

    @Test
    void aPlayerWithNothingSpecialSeesTheServerHologramNotTheMap() {
        assertFalse(RadarDisplayRules.showsFlatMap(false, true, false));
        assertTrue(RadarDisplayRules.showsServerHologram(false, true, false));
    }

    @Test
    void aPlayerWhoChoseTheFlatMapGetsItAndNoHologram() {
        assertTrue(RadarDisplayRules.showsFlatMap(true, true, false));
        assertFalse(RadarDisplayRules.showsServerHologram(true, true, false));
    }

    @Test
    void anOperatorWhoSwitchedTheServerHologramOffLeavesEveryoneWithTheMap() {
        assertTrue(RadarDisplayRules.showsFlatMap(false, false, false));
        assertFalse(RadarDisplayRules.showsServerHologram(false, false, false));
    }

    @Test
    void aClientWithTheCompanionKeepsTheMapAndGetsNoServerHologram() {
        // The companion draws its own 3D from the map's data and the held map; two holograms would be one too many.
        assertTrue(RadarDisplayRules.showsFlatMap(false, true, true));
        assertFalse(RadarDisplayRules.showsServerHologram(false, true, true));
    }

    @Test
    void exactlyOneOfTheTwoIsShownInEveryCase() {
        for (boolean prefersMap : new boolean[] {false, true}) for (boolean enabled : new boolean[] {false, true}) for (boolean companion : new boolean[] {false, true}) {
            boolean map = RadarDisplayRules.showsFlatMap(prefersMap, enabled, companion);
            boolean hologram = RadarDisplayRules.showsServerHologram(prefersMap, enabled, companion);
            if (companion) {
                assertTrue(map && !hologram, "companion: " + prefersMap + enabled);
            } else {
                assertTrue(map ^ hologram, "exactly one for " + prefersMap + "," + enabled + "," + companion);
            }
        }
    }
}
