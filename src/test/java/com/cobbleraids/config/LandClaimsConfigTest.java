package com.cobbleraids.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Raids and World Bosses keep out of land claims (Flan) by default, with a buffer an operator can tune or switch off. */
class LandClaimsConfigTest {

    @Test
    @DisplayName("claims are avoided by default, with an 8-block buffer")
    void defaults() {
        CobbleRaidsConfig.LandClaims claims = CobbleRaidsConfig.defaults().landClaims();
        assertTrue(claims.avoid());
        assertEquals(8, claims.bufferBlocks());
    }

    @Test
    @DisplayName("a server.json written before this setting existed loads with the defaults")
    void missingSectionUsesDefaults() {
        JsonObject old = CobbleRaidsConfig.defaults().toJson();
        old.remove("land_claims");
        assertEquals(CobbleRaidsConfig.defaults().landClaims(), CobbleRaidsConfig.fromJson(old).landClaims());
    }

    @Test
    @DisplayName("an operator's values survive the round trip")
    void customValuesSurvive() {
        JsonObject json = CobbleRaidsConfig.defaults().toJson();
        json.add("land_claims", JsonParser.parseString("{\"avoid\":false,\"buffer_blocks\":24}"));
        CobbleRaidsConfig parsed = CobbleRaidsConfig.fromJson(json);
        assertFalse(parsed.landClaims().avoid());
        assertEquals(24, parsed.landClaims().bufferBlocks());
        assertEquals(parsed, CobbleRaidsConfig.fromJson(parsed.toJson()));
    }

    @Test
    @DisplayName("a buffer outside 0..64 is refused with the setting's name")
    void bufferOutOfRange() {
        IllegalArgumentException low = assertThrows(IllegalArgumentException.class, () -> new CobbleRaidsConfig.LandClaims(true, -1));
        assertTrue(low.getMessage().contains("land_claims.buffer_blocks"));
        assertThrows(IllegalArgumentException.class, () -> new CobbleRaidsConfig.LandClaims(true, 65));
        new CobbleRaidsConfig.LandClaims(true, 0);
        new CobbleRaidsConfig.LandClaims(true, 64);
    }
}
