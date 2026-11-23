package com.cobbleraids.battle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RaidBannedGimmicksTest {

    @Test
    @DisplayName("a Z-Move is refused")
    void zMoveIsBanned() {
        assertTrue(RaidBannedGimmicks.isBanned("zmove"));
    }

    @Test
    @DisplayName("a plain move and the other gimmicks are not refused by this list")
    void othersAreAllowed() {
        assertFalse(RaidBannedGimmicks.isBanned(null));
        assertFalse(RaidBannedGimmicks.isBanned("mega"));
        assertFalse(RaidBannedGimmicks.isBanned("ultra"));
        assertFalse(RaidBannedGimmicks.isBanned("max"));
        assertFalse(RaidBannedGimmicks.isBanned("terastal"));
    }
}
