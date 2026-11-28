package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorldBossTickerTest {
    @Test void fractionalMillisecondsAccumulateInsteadOfGrantingTimeOnEveryTick() {
        var ticker = new WorldBossTicker(0);
        long total = 0;
        for (long nanos = 1; nanos <= 3_000_000; nanos++) total += ticker.sample(nanos);
        assertEquals(3, total);
    }

    @Test void heartbeatUsesRealTimeAndAcknowledgedCheckpointResetsTheInterval() {
        var ticker = new WorldBossTicker(100);
        assertEquals(1999, ticker.sample(1_999_000_100L));
        assertFalse(ticker.checkpointDue());
        assertEquals(1, ticker.sample(2_000_000_100L));
        assertTrue(ticker.checkpointDue());
        ticker.checkpointed();
        assertFalse(ticker.checkpointDue());
        assertEquals(90_000, ticker.sample(92_000_000_100L));
        assertTrue(ticker.checkpointDue(), "A stalled tick cannot postpone the real-time heartbeat test");
    }

    @Test void nanoTimeRolloverIsValidButAnActualBackwardSampleIsReported() {
        var ticker = new WorldBossTicker(Long.MAX_VALUE - 999_999);
        assertEquals(2, ticker.sample(Long.MIN_VALUE + 1_000_000));
        assertEquals(-1, ticker.sample(Long.MIN_VALUE + 999_999));
    }
}
