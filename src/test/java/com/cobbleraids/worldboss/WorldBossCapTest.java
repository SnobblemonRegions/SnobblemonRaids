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
}
