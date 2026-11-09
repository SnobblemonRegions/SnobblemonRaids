package com.cobbleraids.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cobbleraids.config.CobbleRaidsConfig;
import com.cobbleraids.config.CobbleRaidsConfig.AnnouncementPrecision;
import org.junit.jupiter.api.Test;

/** A wild-raid announcement names the biome and, by default, no coordinates: the Raidio is how a raid is found. */
class RaidSpawnAnnouncementHintTest {

    @Test
    void theDefaultIsBiomeOnly() {
        assertEquals(AnnouncementPrecision.BIOME_ONLY, CobbleRaidsConfig.defaults().naturalSpawning().announcementPrecision());
    }

    @Test
    void biomeOnlyGivesNoCoordinates() {
        String hint = RaidSpawnAnnouncementService.locationHint(AnnouncementPrecision.BIOME_ONLY, 1234, -5678);
        assertFalse(hint.contains("1234") || hint.contains("1200") || hint.contains("5678") || hint.contains("5700"), hint);
        assertFalse(hint.contains("X ") || hint.contains("Z "), hint);
    }

    @Test
    void theOtherPrecisionsStillWorkWhenAServerAsksForThem() {
        assertEquals("Coordinate hint: near X 1200, Z -5700",
                RaidSpawnAnnouncementService.locationHint(AnnouncementPrecision.NEAREST_HUNDRED, 1234, -5678));
        assertEquals("Location: X 1234, Z -5678",
                RaidSpawnAnnouncementService.locationHint(AnnouncementPrecision.EXACT, 1234, -5678));
    }
}
