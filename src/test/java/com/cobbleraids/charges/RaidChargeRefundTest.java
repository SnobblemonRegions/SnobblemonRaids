package com.cobbleraids.charges;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RaidChargeRefundTest {
    private static final long NOW = 1_800_000_000_000L, HOUR = 3_600_000;

    @Test
    void aRefundGivesOneChargeBackAndKeepsTheRefillClock() {
        var two = RaidChargeAccount.FULL.spend(NOW).spend(NOW);
        var back = two.refund(NOW + HOUR / 2);
        assertEquals(2, back.available());
        assertEquals(NOW + HOUR, back.nextRefillAt(), "the hour already running still counts");
    }

    @Test
    void aRefundToOneShortOfFullIsFull() {
        assertEquals(RaidChargeAccount.FULL, RaidChargeAccount.FULL.spend(NOW).refund(NOW));
    }

    @Test
    void aRefundNeverGoesAboveCapacity() {
        assertEquals(RaidChargeAccount.FULL, RaidChargeAccount.FULL.refund(NOW));
        // The spent charge refilled on its own before the refund arrived.
        assertEquals(RaidChargeAccount.FULL, RaidChargeAccount.FULL.spend(NOW).refund(NOW + 2 * HOUR));
    }
}
