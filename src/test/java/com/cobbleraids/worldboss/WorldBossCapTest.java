package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Balance phase 3: a World Boss takes up to 40 players; one already fighting can always rejoin. */
class WorldBossCapTest {

    @Test
    void fortyAndNoMore() {
        Set<UUID> fighters = new HashSet<>();
        for (int i = 0; i < 39; i++) fighters.add(UUID.randomUUID());
        assertTrue(WorldBossRules.hasRoom(fighters, UUID.randomUUID()), "the 40th joins");
        fighters.add(UUID.randomUUID());
        assertFalse(WorldBossRules.hasRoom(fighters, UUID.randomUUID()), "the 41st does not");
        assertTrue(WorldBossRules.hasRoom(fighters, fighters.iterator().next()), "a fighter can chain back in");
    }

    /** The daily boss's cap, counted over its participants: everyone who took a place, waiting for the countdown or not. */
    @Test
    void dailyCapCountsEveryPlaceTaken() {
        Set<UUID> participants = new HashSet<>();
        for (int i = 0; i < 24; i++) participants.add(UUID.randomUUID());
        assertTrue(WorldBossRules.hasRoom(participants, UUID.randomUUID(), 25), "the 25th takes the last place");
        participants.add(UUID.randomUUID());
        assertFalse(WorldBossRules.hasRoom(participants, UUID.randomUUID(), 25),
                "a 26th is refused even while the 25 are still waiting for the countdown and none has fought");
        assertTrue(WorldBossRules.hasRoom(participants, participants.iterator().next(), 25), "a player with a place keeps it");
        assertTrue(WorldBossRules.hasRoom(participants, UUID.randomUUID(), 0), "0 means no cap (event bosses)");
    }
}
