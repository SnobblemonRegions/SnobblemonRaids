package com.cobbleraids.validation.perf;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RootTimingsTest {
    @Test void parentSelfExcludesAllChildrenAndRecursionUsesDistinctFrames() {
        var timing = new RootTimings(8);
        timing.enter(0); timing.enter(20); timing.enter(25);
        assertEquals(new RootTimings.Span(10, 10, 2), timing.exit(35));
        assertEquals(new RootTimings.Span(30, 20, 1), timing.exit(50));
        assertEquals(new RootTimings.Span(100, 70, 0), timing.exit(100));
        assertEquals(3, timing.calls()); assertTrue(timing.balanced());
    }
    @Test void overflowStaysBoundedAndDoesNotCorruptOuterStack() {
        var timing = new RootTimings(1);
        timing.enter(0); timing.enter(10); timing.enter(20);
        assertNull(timing.exit(30)); assertNull(timing.exit(40));
        assertEquals(new RootTimings.Span(50, 20, 0), timing.exit(50));
        assertEquals(2, timing.overflow()); assertTrue(timing.balanced());
    }
    @Test void unmatchedReturnsCannotBecomeValidTimings() {
        assertThrows(IllegalStateException.class, () -> new RootTimings(1).exit(0));
    }
}
