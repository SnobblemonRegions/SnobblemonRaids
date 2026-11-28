package com.cobbleraids.validation.perf;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TickSamplesTest {
    @Test void recordsWholeRecordingPercentilesAndActualPopulationWithoutAveragingWindows() {
        var samples = new TickSamples(200);
        for (int n = 1; n <= 100; n++) samples.add(new TickSamples.Tick(n, n, "combat", 51, n * 1_000_000L, 50_000_000));
        var result = samples.summary(null);
        assertEquals(50, result.p50Ms()); assertEquals(95, result.p95Ms()); assertEquals(99, result.p99Ms()); assertEquals(100, result.maxMs());
        assertEquals(51, result.minPlayers()); assertEquals(51, result.maxPlayers()); assertEquals(50, result.maxIntervalMs());
    }
    @Test void separatesPhasesAndDoesNotEraseStallsOrOverflow() {
        var samples = new TickSamples(2);
        samples.add(new TickSamples.Tick(1, 1, "setup", 0, 900_000_000, 1));
        samples.add(new TickSamples.Tick(2, 2, "combat", 31, 10_000_000, 1));
        samples.add(new TickSamples.Tick(3, 3, "combat", 31, 1, 1));
        assertEquals(900, samples.summary(null).maxMs()); assertEquals(1, samples.summary(null).over250ms());
        assertEquals(10, samples.summary("combat").maxMs()); assertEquals(1, samples.summary("combat").dropped());
        assertThrows(UnsupportedOperationException.class, () -> samples.ticks().clear());
    }
    @Test void emptyRecordingsHaveNoFabricatedSamplesAndInvalidDurationsFail() {
        var samples = new TickSamples(1);
        assertEquals(0, samples.summary(null).count());
        assertThrows(IllegalArgumentException.class, () -> samples.add(new TickSamples.Tick(1, 1, "combat", 1, -1, 1)));
    }
}
