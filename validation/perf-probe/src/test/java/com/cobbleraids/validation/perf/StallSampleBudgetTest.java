package com.cobbleraids.validation.perf;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StallSampleBudgetTest {
    @Test void boundsStackWalksAndSpacesSamples() {
        var budget = new StallSampleBudget();
        assertTrue(budget.allow(1, 100_000_000));
        assertFalse(budget.allow(1, 125_000_000));
        assertTrue(budget.allow(1, 300_000_000));
        assertTrue(budget.allow(1, 500_000_000));
        assertFalse(budget.allow(1, 9_000_000_000L));
    }
    @Test void newTickGetsImmediateSample() {
        var budget = new StallSampleBudget();
        assertTrue(budget.allow(1, 100_000_000));
        assertTrue(budget.allow(2, 125_000_000));
    }
}
