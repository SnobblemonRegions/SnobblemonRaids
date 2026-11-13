package com.cobbleraids.reward;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * When to remind a player of a raid reward they have not claimed. Minecraft-free so it is testable.
 *
 * <p>The reward screen opens at victory and on each login, and the server never learns when a player closes it. So a player who closed it without
 * choosing heard nothing more until their next login: live data from 12 Nov 2026 had three new raiders (one or two raids each) with rewards and their
 * capture rolls waiting unclaimed. A reminder is due one interval after the reward is first seen, then every interval while it waits, never while the
 * player is in a raid, and the schedule forgets a player once nothing is pending.
 */
public final class RewardReminderSchedule {

    private final long intervalTicks;
    private final Map<UUID, Long> nextDue = new HashMap<>();

    public RewardReminderSchedule(long intervalTicks) {
        if (intervalTicks < 1) throw new IllegalArgumentException("The reminder interval must be at least one tick");
        this.intervalTicks = intervalTicks;
    }

    /** Whether to remind {@code player} now. Call once per check for every online player. */
    public boolean due(UUID player, long nowTick, boolean pending, boolean inRaid) {
        if (!pending) {
            nextDue.remove(player);
            return false;
        }
        Long due = nextDue.get(player);
        if (due == null) {
            nextDue.put(player, nowTick + intervalTicks);
            return false;
        }
        if (inRaid || nowTick < due) return false;
        nextDue.put(player, nowTick + intervalTicks);
        return true;
    }

    /** Drops a player who left, so the map does not grow over a long uptime. */
    public void forget(UUID player) {
        nextDue.remove(player);
    }
}
