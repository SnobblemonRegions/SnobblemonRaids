package com.cobbleraids.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The natural-spawn settings that keep wild raids reachable: height, ground, AFK and how many players a check may try. */
class SpawnSafetyConfigTest {

    @Test
    @DisplayName("defaults: 24 blocks of height, natural ground only, 5 minutes AFK, 8 players tried per check")
    void defaults() {
        CobbleRaidsConfig.NaturalSpawning n = CobbleRaidsConfig.defaults().naturalSpawning();
        assertEquals(24.0, n.maxHeightDifference(), 0.0);
        assertTrue(n.naturalGroundOnly());
        assertEquals(300, n.afkSeconds());
        assertEquals(8, n.playersTriedPerCheck());
    }

    @Test
    @DisplayName("a server.json from before these settings loads with the defaults")
    void missingKeys() {
        JsonObject json = CobbleRaidsConfig.defaults().toJson();
        JsonObject natural = json.getAsJsonObject("natural_spawning");
        for (String key : new String[] {"max_height_difference", "natural_ground_only", "afk_seconds", "players_tried_per_check"}) {
            assertTrue(natural.has(key), key + " is written");
            natural.remove(key);
        }
        assertEquals(CobbleRaidsConfig.defaults().naturalSpawning(), CobbleRaidsConfig.fromJson(json).naturalSpawning());
    }

    @Test
    @DisplayName("an operator's values survive the round trip")
    void roundTrip() {
        JsonObject json = CobbleRaidsConfig.defaults().toJson();
        JsonObject natural = json.getAsJsonObject("natural_spawning");
        natural.addProperty("max_height_difference", 0);
        natural.addProperty("natural_ground_only", false);
        natural.addProperty("afk_seconds", 0);
        natural.addProperty("players_tried_per_check", 3);
        CobbleRaidsConfig parsed = CobbleRaidsConfig.fromJson(json);
        assertEquals(0.0, parsed.naturalSpawning().maxHeightDifference(), 0.0);
        assertFalse(parsed.naturalSpawning().naturalGroundOnly());
        assertEquals(0, parsed.naturalSpawning().afkSeconds());
        assertEquals(3, parsed.naturalSpawning().playersTriedPerCheck());
        assertEquals(parsed, CobbleRaidsConfig.fromJson(parsed.toJson()));
    }

    @Test
    @DisplayName("out-of-range values are refused with the setting's name")
    void validation() {
        for (String[] bad : new String[][] {
                {"max_height_difference", "-1"}, {"max_height_difference", "385"},
                {"afk_seconds", "-1"}, {"afk_seconds", "86401"},
                {"players_tried_per_check", "0"}, {"players_tried_per_check", "65"}}) {
            JsonObject json = CobbleRaidsConfig.defaults().toJson();
            json.getAsJsonObject("natural_spawning").addProperty(bad[0], Double.parseDouble(bad[1]));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> CobbleRaidsConfig.fromJson(json), bad[0]);
            assertTrue(ex.getMessage().contains("natural_spawning." + bad[0]), ex.getMessage());
        }
    }
}
