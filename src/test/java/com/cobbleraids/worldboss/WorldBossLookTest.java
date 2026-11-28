package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class WorldBossLookTest {

    @Test
    void theCopyIsHiddenByDefaultAndTheZoneHasNoGlow() {
        var look = WorldBossConfig.defaults().look();
        assertTrue(look.duelBossInvisible(), "players see the shared showpiece instead of private copies");
        assertTrue(look.noGlowInZone());
    }

    @Test
    void staffCanExplicitlyKeepDuelCopiesVisible() {
        var config = WorldBossConfig.fromJson(JsonParser.parseString(
                "{\"visual\":{\"duel_boss_invisible\":false}}").getAsJsonObject());
        assertFalse(config.look().duelBossInvisible());
        assertEquals(config.look(), WorldBossConfig.fromJson(config.toJson()).look());
    }

    @Test
    void lookSurvivesAJsonRoundTripAndReadsFromTheVisualBlock() {
        var config = WorldBossConfig.fromJson(JsonParser.parseString(
                "{\"visual\":{\"duel_boss_invisible\":true,\"no_glow_in_zone\":false}}").getAsJsonObject());
        assertTrue(config.look().duelBossInvisible());
        assertFalse(config.look().noGlowInZone());
        assertEquals(config.look(), WorldBossConfig.fromJson(config.toJson()).look());
        assertEquals(config.wallColor(), WorldBossConfig.fromJson(config.toJson()).wallColor(), "the rest of visual is untouched");
    }
}
