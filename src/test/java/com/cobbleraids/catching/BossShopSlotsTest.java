package com.cobbleraids.catching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/** The two limits on a player's saved bosses: how many, and for how long. */
class BossShopSlotsTest {

    private static final long DAY = 86_400_000L;
    private static final long SAVED = 1_800_000_000_000L;

    @Test
    @DisplayName("a boss is saved while there is a free slot")
    void roomWhileUnderTheCap() {
        assertEquals(BossShopSlots.Verdict.ROOM, BossShopSlots.verdict(List.of(), "garchomp", 5));
        assertEquals(BossShopSlots.Verdict.ROOM, BossShopSlots.verdict(List.of("a", "b", "c", "d"), "garchomp", 5));
    }

    @Test
    @DisplayName("at the cap a new species needs one of the saved ones given up")
    void fullAtTheCap() {
        assertEquals(BossShopSlots.Verdict.FULL, BossShopSlots.verdict(List.of("a", "b", "c", "d", "e"), "garchomp", 5));
        // A player who had more than the cap before it existed keeps them, and is simply full.
        assertEquals(BossShopSlots.Verdict.FULL, BossShopSlots.verdict(List.of("a", "b", "c", "d", "e", "f", "g"), "garchomp", 5));
    }

    @Test
    @DisplayName("the same species again takes the slot it already has, even when full")
    void sameSpeciesNeverNeedsASecondSlot() {
        assertEquals(BossShopSlots.Verdict.SAME_SPECIES, BossShopSlots.verdict(List.of("a", "b", "c", "d", "garchomp"), "garchomp", 5));
    }

    @Test
    @DisplayName("a saved boss leaves once its days are up, and not a moment before")
    void expiry() {
        assertFalse(BossShopSlots.expired(SAVED, SAVED + 7 * DAY - 1, 7));
        assertTrue(BossShopSlots.expired(SAVED, SAVED + 7 * DAY, 7));
        assertEquals(DAY, BossShopSlots.millisLeft(SAVED, SAVED + 6 * DAY, 7));
        assertEquals(0L, BossShopSlots.millisLeft(SAVED, SAVED + 9 * DAY, 7));
    }

    @Test
    @DisplayName("zero days means saved bosses never leave")
    void zeroDaysNeverExpires() {
        assertFalse(BossShopSlots.expired(SAVED, SAVED + 1000 * DAY, 0));
        assertEquals(-1L, BossShopSlots.millisLeft(SAVED, SAVED + 1000 * DAY, 0));
    }

    @Test
    @DisplayName("a clock that ran backwards does not expire anything")
    void clockSkew() {
        assertFalse(BossShopSlots.expired(SAVED, SAVED - DAY, 7));
        assertEquals(7 * DAY, BossShopSlots.millisLeft(SAVED, SAVED - DAY, 7));
    }
}
