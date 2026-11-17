package com.cobbleraids.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleraids.config.CobbleRaidsConfig;
import com.cobbleraids.config.CobbleRaidsConfig.AnnouncementPrecision;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A wild-raid announcement names the biome and never a coordinate: the Raidio is how a raid is found. It used to be a setting, so a
 * server that once saved {@code exact} kept printing X and Z long after the default changed.
 */
class RaidSpawnAnnouncementHintTest {

    private static JsonObject configWithPrecision(String value) {
        JsonObject root = CobbleRaidsConfig.defaults().toJson();
        root.getAsJsonObject("natural_spawning").addProperty("announcement_precision", value);
        return root;
    }

    @Test
    void theDefaultIsBiomeOnly() {
        assertEquals(AnnouncementPrecision.BIOME_ONLY, CobbleRaidsConfig.defaults().naturalSpawning().announcementPrecision());
    }

    @Test
    @DisplayName("the sentence after the biome gives no coordinates")
    void theHintGivesNoCoordinates() {
        String hint = RaidSpawnAnnouncementService.locationHint();
        assertFalse(hint.matches(".*\\d.*"), hint);
        assertFalse(hint.contains("X ") || hint.contains("Z ") || hint.toLowerCase().contains("coordinate"), hint);
    }

    @Test
    @DisplayName("a config saved with exact or nearest_hundred loads as biome only")
    void savedCoordinateSettingsBecomeBiomeOnly() {
        for (String legacy : new String[] {"exact", "nearest_hundred", "EXACT", " Nearest_Hundred "}) {
            assertEquals(AnnouncementPrecision.BIOME_ONLY,
                    CobbleRaidsConfig.fromJson(configWithPrecision(legacy)).naturalSpawning().announcementPrecision(), legacy);
        }
    }

    @Test
    @DisplayName("the rewritten config no longer carries the coordinate setting")
    void rewrittenConfigIsBiomeOnly() {
        JsonObject rewritten = CobbleRaidsConfig.fromJson(configWithPrecision("exact")).toJson();
        assertEquals("biome_only", rewritten.getAsJsonObject("natural_spawning").get("announcement_precision").getAsString());
    }

    @Test
    @DisplayName("an operator can still switch the announcement off")
    void disabledStillWorks() {
        assertEquals(AnnouncementPrecision.DISABLED,
                CobbleRaidsConfig.fromJson(configWithPrecision("disabled")).naturalSpawning().announcementPrecision());
        assertTrue(AnnouncementPrecision.values().length == 2, "only biome_only and disabled remain");
    }
}
