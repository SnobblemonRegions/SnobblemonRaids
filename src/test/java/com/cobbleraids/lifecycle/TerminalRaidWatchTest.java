package com.cobbleraids.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The deadline that ends a raid whose own ending never arrived.
 *
 * <p>A won raid is finalized by Showdown answering {@code >raidwin}. When the killing blow and the last
 * player Pokemon's faint land in the same turn, Showdown has already ended the battle, never answers, and
 * the raid stayed registered until the server restarted. The property under test is that a raid in a
 * terminal state is always handed back for finalization after a bounded wait.
 */
class TerminalRaidWatchTest {

    private final TerminalRaidWatch watch = new TerminalRaidWatch();
    private final UUID raid = UUID.randomUUID();

    private List<UUID> tick(int times, Set<UUID> terminal) {
        List<UUID> last = List.of();
        for (int i = 0; i < times; i++) last = watch.tick(terminal);
        return last;
    }

    @Test
    @DisplayName("a terminal raid nobody finalized is overdue after the long wait, not before")
    void overdueAfterTerminalGrace() {
        assertTrue(tick(TerminalRaidWatch.TERMINAL_GRACE_TICKS, Set.of(raid)).isEmpty());
        assertEquals(List.of(raid), watch.tick(Set.of(raid)));
    }

    @Test
    @DisplayName("once victory was requested, the wait is the short one")
    void overdueSoonAfterVictoryRequest() {
        watch.tick(Set.of(raid));
        watch.victoryRequested(raid);
        assertTrue(tick(TerminalRaidWatch.REQUESTED_GRACE_TICKS - 1, Set.of(raid)).isEmpty());
        assertEquals(List.of(raid), watch.tick(Set.of(raid)));
    }

    @Test
    @DisplayName("a victory request never lengthens a wait that is already shorter")
    void requestDoesNotExtend() {
        tick(TerminalRaidWatch.TERMINAL_GRACE_TICKS - 5, Set.of(raid));
        watch.victoryRequested(raid);
        assertTrue(tick(5, Set.of(raid)).isEmpty());
        assertEquals(List.of(raid), watch.tick(Set.of(raid)));
    }

    @Test
    @DisplayName("a raid that was finalized in time is forgotten")
    void finalizedRaidIsForgotten() {
        watch.tick(Set.of(raid));
        watch.victoryRequested(raid);
        watch.tick(Set.of());
        assertEquals(0, watch.size());
        // The same id seen again starts a fresh, full wait.
        assertTrue(tick(TerminalRaidWatch.TERMINAL_GRACE_TICKS, Set.of(raid)).isEmpty());
    }

    @Test
    @DisplayName("a raid that is still there after being handed back is retried later, not every tick")
    void retryIsSpacedOut() {
        tick(TerminalRaidWatch.TERMINAL_GRACE_TICKS + 1, Set.of(raid));
        assertTrue(tick(TerminalRaidWatch.TERMINAL_GRACE_TICKS - 1, Set.of(raid)).isEmpty());
        assertEquals(List.of(raid), watch.tick(Set.of(raid)));
    }

    @Test
    @DisplayName("clear drops everything")
    void clear() {
        watch.tick(Set.of(raid));
        watch.clear();
        assertEquals(0, watch.size());
    }
}
