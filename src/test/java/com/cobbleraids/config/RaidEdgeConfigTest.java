package com.cobbleraids.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The particle ring that shows raid players the edge they must not cross, and Lobby players where to stand. */
class RaidEdgeConfigTest {

    @Test
    @DisplayName("on by default: ring within 12 blocks of the edge, warning within 8")
    void defaults() {
        CobbleRaidsConfig.RaidEdge edge = CobbleRaidsConfig.defaults().raidEdge();
        assertTrue(edge.enabled());
        assertEquals(12.0, edge.showWithinBlocks(), 0.0);
        assertEquals(8.0, edge.warnWithinBlocks(), 0.0);
        assertEquals(1.5, edge.particleSpacing(), 0.0);
    }

    @Test
    @DisplayName("a server.json from before this setting loads with the defaults")
    void missingSection() {
        JsonObject old = CobbleRaidsConfig.defaults().toJson();
        old.remove("raid_edge");
        assertEquals(CobbleRaidsConfig.defaults().raidEdge(), CobbleRaidsConfig.fromJson(old).raidEdge());
    }

    @Test
    @DisplayName("an operator's values survive the round trip")
    void roundTrip() {
        JsonObject json = CobbleRaidsConfig.defaults().toJson();
        json.add("raid_edge", JsonParser.parseString(
                "{\"enabled\":false,\"show_within_blocks\":20,\"warn_within_blocks\":5,\"particle_spacing\":2.5}"));
        CobbleRaidsConfig parsed = CobbleRaidsConfig.fromJson(json);
        assertFalse(parsed.raidEdge().enabled());
        assertEquals(20.0, parsed.raidEdge().showWithinBlocks(), 0.0);
        assertEquals(5.0, parsed.raidEdge().warnWithinBlocks(), 0.0);
        assertEquals(2.5, parsed.raidEdge().particleSpacing(), 0.0);
        assertEquals(parsed, CobbleRaidsConfig.fromJson(parsed.toJson()));
    }

    @Test
    @DisplayName("out-of-range values are refused with the setting's name")
    void validation() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new CobbleRaidsConfig.RaidEdge(true, 0.5, 0, 1.5))
                .getMessage().contains("raid_edge.show_within_blocks"));
        assertThrows(IllegalArgumentException.class, () -> new CobbleRaidsConfig.RaidEdge(true, 49, 8, 1.5));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new CobbleRaidsConfig.RaidEdge(true, 12, 13, 1.5))
                .getMessage().contains("raid_edge.warn_within_blocks"));
        assertThrows(IllegalArgumentException.class, () -> new CobbleRaidsConfig.RaidEdge(true, 12, -1, 1.5));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new CobbleRaidsConfig.RaidEdge(true, 12, 8, 0.25))
                .getMessage().contains("raid_edge.particle_spacing"));
        assertThrows(IllegalArgumentException.class, () -> new CobbleRaidsConfig.RaidEdge(true, 12, 8, 9));
        new CobbleRaidsConfig.RaidEdge(true, 1, 0, 0.5);
        new CobbleRaidsConfig.RaidEdge(true, 48, 48, 8);
    }
}
