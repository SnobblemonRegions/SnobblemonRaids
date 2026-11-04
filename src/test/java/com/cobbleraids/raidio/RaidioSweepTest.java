package com.cobbleraids.raidio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Whether the player is turning, which decides when static is allowed to play. */
class RaidioSweepTest {

    @Test
    void standingStillIsNotSweeping() {
        RaidioSweep sweep = new RaidioSweep();
        assertFalse(sweep.update(10), "the first reading has nothing to compare with");
        for (int i = 0; i < 20; i++) assertFalse(sweep.update(10));
    }

    @Test
    void aSmallJitterIsNotSweeping() {
        RaidioSweep sweep = new RaidioSweep();
        sweep.update(10);
        assertFalse(sweep.update(10.4));
        assertFalse(sweep.update(9.8));
    }

    @Test
    void turningIsSweeping() {
        RaidioSweep sweep = new RaidioSweep();
        sweep.update(0);
        assertTrue(sweep.update(RaidioSweep.MIN_DEGREES_PER_STEP + 0.5));
    }

    @Test
    void sweepingHoldsForAMomentAfterTheTurnStops() {
        RaidioSweep sweep = new RaidioSweep();
        sweep.update(0);
        assertTrue(sweep.update(20));
        for (int i = 0; i < RaidioSweep.HOLD_STEPS - 1; i++) assertTrue(sweep.update(20), "still sweeping " + i + " steps after the turn");
        assertFalse(sweep.update(20), "silent once the hold has run out");
    }

    @Test
    void aTurnAcrossTheWrapCountsAsTheSmallTurnItIs() {
        RaidioSweep sweep = new RaidioSweep();
        sweep.update(179);
        assertTrue(sweep.update(-179), "179 to -179 is a 2 degree turn, not 358");
        RaidioSweep still = new RaidioSweep();
        still.update(179.9);
        assertFalse(still.update(-179.9), "0.2 degrees across the wrap is no turn at all");
    }

    @Test
    void notANumberDoesNotCountAsTurning() {
        RaidioSweep sweep = new RaidioSweep();
        sweep.update(0);
        assertFalse(sweep.update(Double.NaN));
        assertFalse(sweep.update(0), "and does not poison what comes next");
    }
}
