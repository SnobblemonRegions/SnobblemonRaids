package com.cobbleraids.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a player stands relative to a raid's edge: the 48-block leash during a raid, the recruitment radius in a Lobby. Decides
 * whether they are shown the ring and warned, so a player is told before the step that forfeits their rewards, never after.
 */
class RaidEdgeTest {

    private static final double LEASH = 48.0;

    @Test
    @DisplayName("near the boss, nothing is shown")
    void clearNearTheBoss() {
        assertEquals(RaidEdge.Zone.CLEAR, RaidEdge.zone(10.0, LEASH, 12.0, 8.0));
        assertEquals(RaidEdge.Zone.CLEAR, RaidEdge.zone(0.0, LEASH, 12.0, 8.0));
    }

    @Test
    @DisplayName("within show_within of the edge the ring appears, within warn_within the warning too")
    void nearThenWarn() {
        assertEquals(RaidEdge.Zone.NEAR, RaidEdge.zone(36.0, LEASH, 12.0, 8.0), "exactly 12 from the edge");
        assertEquals(RaidEdge.Zone.NEAR, RaidEdge.zone(39.9, LEASH, 12.0, 8.0));
        assertEquals(RaidEdge.Zone.WARN, RaidEdge.zone(40.0, LEASH, 12.0, 8.0), "exactly 8 from the edge");
        assertEquals(RaidEdge.Zone.WARN, RaidEdge.zone(48.0, LEASH, 12.0, 8.0), "on the edge is still inside, as the leash counts it");
    }

    @Test
    @DisplayName("past the edge is outside, matching the leash's strictly-greater test")
    void outside() {
        assertEquals(RaidEdge.Zone.OUTSIDE, RaidEdge.zone(48.01, LEASH, 12.0, 8.0));
        assertEquals(RaidEdge.Zone.OUTSIDE, RaidEdge.zone(200.0, LEASH, 12.0, 8.0));
    }

    @Test
    @DisplayName("a warn distance of zero never warns inside the edge")
    void warnOff() {
        assertEquals(RaidEdge.Zone.NEAR, RaidEdge.zone(47.9, LEASH, 12.0, 0.0));
        assertEquals(RaidEdge.Zone.WARN, RaidEdge.zone(48.0, LEASH, 12.0, 0.0), "only the edge itself");
    }

    @Test
    @DisplayName("blocks left counts down to zero and never goes negative")
    void blocksLeft() {
        assertEquals(8, RaidEdge.blocksLeft(40.0, LEASH));
        assertEquals(7, RaidEdge.blocksLeft(40.5, LEASH), "rounded down: never promise a block that isn't there");
        assertEquals(0, RaidEdge.blocksLeft(48.0, LEASH));
        assertEquals(0, RaidEdge.blocksLeft(60.0, LEASH));
    }

    @Test
    @DisplayName("how far round the ring is drawn: the whole of a small Lobby ring, only the near stretch of the leash")
    void viewDistance() {
        assertEquals(20.0 + 12.0, RaidEdge.viewDistance(10.0, 12.0), 1.0e-9, "a 10-block Lobby ring is drawn whole");
        assertEquals(12.0, RaidEdge.viewDistance(48.0, 12.0), 1.0e-9, "a wide ring keeps to show_within");
    }
}
