package com.cobbleraids.reward;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RewardReminderScheduleTest {
    private static final long FIVE_MINUTES = 6000;
    private final UUID player = UUID.randomUUID();

    @Test
    void remindsOneIntervalAfterTheRewardIsFirstSeenThenEveryInterval() {
        var schedule = new RewardReminderSchedule(FIVE_MINUTES);
        assertFalse(schedule.due(player, 0, true, false), "the reward screen has just opened: no reminder yet");
        assertFalse(schedule.due(player, FIVE_MINUTES - 1, true, false));
        assertTrue(schedule.due(player, FIVE_MINUTES, true, false));
        assertFalse(schedule.due(player, FIVE_MINUTES + 20, true, false), "not again right away");
        assertTrue(schedule.due(player, 2 * FIVE_MINUTES, true, false));
    }

    @Test
    void neverRemindsInARaidAndRemindsRightAfterIt() {
        var schedule = new RewardReminderSchedule(FIVE_MINUTES);
        schedule.due(player, 0, true, false);
        assertFalse(schedule.due(player, FIVE_MINUTES, true, true), "a raid is no time for a chat reminder");
        assertTrue(schedule.due(player, FIVE_MINUTES + 20, true, false), "the overdue reminder comes once the raid is over");
    }

    @Test
    void aClaimedRewardEndsTheRemindersAndANewOneStartsFresh() {
        var schedule = new RewardReminderSchedule(FIVE_MINUTES);
        schedule.due(player, 0, true, false);
        assertFalse(schedule.due(player, FIVE_MINUTES, false, false), "claimed: nothing to remind");
        assertFalse(schedule.due(player, FIVE_MINUTES + 20, true, false), "a new reward waits a full interval again");
        assertTrue(schedule.due(player, 2 * FIVE_MINUTES + 20, true, false));
    }

    @Test
    void forgettingAPlayerRestartsTheirInterval() {
        var schedule = new RewardReminderSchedule(FIVE_MINUTES);
        schedule.due(player, 0, true, false);
        schedule.forget(player);
        assertFalse(schedule.due(player, FIVE_MINUTES, true, false));
        assertThrows(IllegalArgumentException.class, () -> new RewardReminderSchedule(0));
    }
}
