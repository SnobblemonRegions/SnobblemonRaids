package com.cobbleraids.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Who a wild raid is spawned around, where it may stand relative to them, and which failures pass the spawn on to another player.
 * A wrong answer here is silent in game: a raid nobody can reach, or a server that quietly gets fewer raids than it is tuned for.
 */
class RaidSpawnRulesTest {

    private static RaidSpawnRules.PlayerState player() {
        return new RaidSpawnRules.PlayerState(true, false, false, false, false, 0L, true);
    }

    @Test
    @DisplayName("an ordinary, active player in a raid dimension can have a raid spawned around them")
    void ordinaryPlayer() {
        assertEquals(RaidSpawnRules.Skip.NONE, RaidSpawnRules.skipReason(player(), 300));
    }

    @Test
    @DisplayName("dead, spectating, creative, battling, already-raiding and raid-less-dimension players are skipped")
    void skipped() {
        var p = player();
        assertEquals(RaidSpawnRules.Skip.DEAD, RaidSpawnRules.skipReason(
                new RaidSpawnRules.PlayerState(false, p.spectator(), p.creative(), p.inBattle(), p.inRaid(), 0, true), 300));
        assertEquals(RaidSpawnRules.Skip.SPECTATOR, RaidSpawnRules.skipReason(
                new RaidSpawnRules.PlayerState(true, true, false, false, false, 0, true), 300));
        assertEquals(RaidSpawnRules.Skip.CREATIVE, RaidSpawnRules.skipReason(
                new RaidSpawnRules.PlayerState(true, false, true, false, false, 0, true), 300));
        assertEquals(RaidSpawnRules.Skip.IN_BATTLE, RaidSpawnRules.skipReason(
                new RaidSpawnRules.PlayerState(true, false, false, true, false, 0, true), 300));
        assertEquals(RaidSpawnRules.Skip.IN_RAID, RaidSpawnRules.skipReason(
                new RaidSpawnRules.PlayerState(true, false, false, false, true, 0, true), 300));
        assertEquals(RaidSpawnRules.Skip.NO_RAIDS_HERE, RaidSpawnRules.skipReason(
                new RaidSpawnRules.PlayerState(true, false, false, false, false, 0, false), 300));
    }

    @Test
    @DisplayName("AFK: idle at least afk_seconds is skipped, just under is not, and 0 turns the rule off")
    void afk() {
        assertEquals(RaidSpawnRules.Skip.AFK, RaidSpawnRules.skipReason(
                new RaidSpawnRules.PlayerState(true, false, false, false, false, 300_000L, true), 300));
        assertEquals(RaidSpawnRules.Skip.NONE, RaidSpawnRules.skipReason(
                new RaidSpawnRules.PlayerState(true, false, false, false, false, 299_999L, true), 300));
        assertEquals(RaidSpawnRules.Skip.NONE, RaidSpawnRules.skipReason(
                new RaidSpawnRules.PlayerState(true, false, false, false, false, 86_400_000L, true), 0));
    }

    @Test
    @DisplayName("height: the spot must be within max_height_difference of the player, either way; 0 turns the rule off")
    void height() {
        assertTrue(RaidSpawnRules.heightOk(88, 64.0, 24.0), "24 above");
        assertTrue(RaidSpawnRules.heightOk(40, 64.0, 24.0), "24 below");
        assertFalse(RaidSpawnRules.heightOk(89, 64.0, 24.0), "25 above");
        assertFalse(RaidSpawnRules.heightOk(70, -40.0, 24.0), "a miner 110 blocks under the surface");
        assertTrue(RaidSpawnRules.heightOk(70, -40.0, 0.0), "off");
    }

    @Test
    @DisplayName("a failure about where the boss could stand passes the spawn on; a deliberate roll or a server-wide block does not")
    void retry() {
        assertTrue(RaidSpawnRules.tryAnotherPlayer(RaidSpawnHistory.Outcome.NO_VALID_TERRAIN));
        assertTrue(RaidSpawnRules.tryAnotherPlayer(RaidSpawnHistory.Outcome.TOO_CLOSE_TO_EXISTING));
        assertTrue(RaidSpawnRules.tryAnotherPlayer(RaidSpawnHistory.Outcome.NO_ROOM_FOR_BOSS));
        // Terminal on purpose: re-rolling a skipped tier would redistribute it (see RaidTierSpawnChance), and the others would
        // fail the same way for the next player too, or, for biome matches, shift raids toward some biomes.
        assertFalse(RaidSpawnRules.tryAnotherPlayer(RaidSpawnHistory.Outcome.TIER_CHANCE_SKIPPED));
        assertFalse(RaidSpawnRules.tryAnotherPlayer(RaidSpawnHistory.Outcome.NO_ELIGIBLE_DEFINITIONS));
        assertFalse(RaidSpawnRules.tryAnotherPlayer(RaidSpawnHistory.Outcome.PER_DIMENSION_CAP));
        assertFalse(RaidSpawnRules.tryAnotherPlayer(RaidSpawnHistory.Outcome.SHOWDOWN_INTEGRATION_FAILED));
        assertFalse(RaidSpawnRules.tryAnotherPlayer(RaidSpawnHistory.Outcome.SUCCESS));
    }

    @Test
    @DisplayName("the rejection summary lists only what happened, most frequent first, so an operator sees why spawns fail")
    void summary() {
        var counts = new EnumMap<RaidSpawnRules.Rejection, Integer>(RaidSpawnRules.Rejection.class);
        assertEquals("", RaidSpawnRules.summarize(counts));
        counts.merge(RaidSpawnRules.Rejection.LAND_CLAIM, 4, Integer::sum);
        counts.merge(RaidSpawnRules.Rejection.NOT_NATURAL_GROUND, 9, Integer::sum);
        counts.merge(RaidSpawnRules.Rejection.HEIGHT, 3, Integer::sum);
        assertEquals("9 not natural ground, 4 in a land claim, 3 too far above or below the player", RaidSpawnRules.summarize(counts));
    }
}
