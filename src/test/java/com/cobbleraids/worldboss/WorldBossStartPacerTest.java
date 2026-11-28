package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorldBossStartPacerTest {
    @Test void firstAttemptIsImmediateThenAtMostOnePerQuarterSecond() {
        var pacer = new WorldBossStartPacer();
        assertEquals(1, pacer.budget(10)); pacer.attempted(10);
        assertEquals(0, pacer.budget(10)); assertEquals(0, pacer.budget(250_000_009));
        assertEquals(1, pacer.budget(250_000_010)); pacer.attempted(250_000_010);
        assertEquals(0, pacer.budget(250_000_011));
    }
    @Test void emptyQueueDoesNotConsumeBudgetAndSlowTicksCannotAccumulateStarts() {
        var pacer = new WorldBossStartPacer();
        assertEquals(1, pacer.budget(0)); assertEquals(1, pacer.budget(1));
        pacer.attempted(1);
        assertEquals(1, pacer.budget(10_000_000_000L)); pacer.attempted(10_000_000_000L);
        assertEquals(0, pacer.budget(10_000_000_001L));
    }
    @Test void nanoTimeRolloverAndFreshServerResetPreserveTheBound() {
        var pacer = new WorldBossStartPacer(); pacer.attempted(Long.MAX_VALUE - 100);
        assertEquals(0, pacer.budget(Long.MIN_VALUE + 100));
        assertEquals(1, pacer.budget(Long.MIN_VALUE + WorldBossStartPacer.INTERVAL_NANOS));
        pacer.reset(); assertEquals(1, pacer.budget(0));
    }
}
