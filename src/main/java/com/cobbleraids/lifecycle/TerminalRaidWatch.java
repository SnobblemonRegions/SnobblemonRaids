package com.cobbleraids.lifecycle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gives every raid that reached a terminal state a deadline by which it must have left the registry.
 *
 * <p>A won raid is not finalized where it is won. The pool empties, the session turns COMPLETED, and the
 * coordinator asks Showdown for a win with {@code >raidwin}; finalization then waits for Cobblemon's
 * BATTLE_VICTORY. That round trip can be lost. Showdown does not know about the raid's pool, so on the
 * turn of the killing blow it keeps resolving the turn, and if the last player Pokemon faints in it (the
 * boss moves second, recoil, Explosion) Showdown ends the battle itself. Its own win line is queued behind
 * the raid's presentation dispatch and discarded when {@code RaidCompletion} clears the queue; the
 * {@code >raidwin} that follows is refused by a battle that has already ended, and Cobblemon's stream
 * closed with it. Nothing ever answers, and since every other exit (timeout, withdrawal, disconnect, abort)
 * requires an ACTIVE raid, the session stayed registered with its boss and its players' battle until the
 * server restarted.
 *
 * <p>So the wait is bounded here instead. Two waits: a short one once victory has been requested, since
 * the answer to that takes a tick or two, and a long one for a terminal raid that never got as far as
 * asking, which has to outlast the presentation gate's own wait for a move animation. A raid still present
 * after being handed back is offered again a long wait later, never every tick.
 *
 * <p>Counts its own ticks, so it needs nothing from Minecraft. Server thread only.
 */
final class TerminalRaidWatch {
    /** After {@code >raidwin} was written. Showdown answers within a tick or two when it answers at all. */
    static final int REQUESTED_GRACE_TICKS = 60;
    /** With no request seen. Longer than RaidPresentationGate's 20 second wait for a move animation. */
    static final int TERMINAL_GRACE_TICKS = 600;

    private final Map<UUID, Long> deadlines = new HashMap<>();
    private long now;

    /** The raid's victory has been asked of Showdown; expect it soon. Never lengthens a wait already running. */
    void victoryRequested(UUID raidId) {
        deadlines.merge(raidId, now + REQUESTED_GRACE_TICKS, Math::min);
    }

    /**
     * Advances one tick.
     *
     * @param terminal every registered raid currently in a terminal state
     * @return the raids whose wait has run out, each of which is then given a fresh long wait
     */
    List<UUID> tick(Set<UUID> terminal) {
        now++;
        deadlines.keySet().retainAll(terminal);
        List<UUID> overdue = List.of();
        for (UUID raidId : terminal) {
            Long deadline = deadlines.putIfAbsent(raidId, now + TERMINAL_GRACE_TICKS);
            if (deadline == null || deadline > now) continue;
            if (overdue.isEmpty()) overdue = new ArrayList<>();
            overdue.add(raidId);
            deadlines.put(raidId, now + TERMINAL_GRACE_TICKS);
        }
        return overdue;
    }

    void clear() {
        if (!deadlines.isEmpty()) deadlines.clear();
    }

    int size() { return deadlines.size(); }
}
