package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorldBossClockTest {
    private static final long NOW = 1_000_000;
    private WorldBossClock active() { return WorldBossClock.begin(0, NOW).advance(0, NOW, true); }

    @Test void preparationIsExcludedAndInitialReadinessIsRequiredEvenWithZeroCountdown() {
        var clock = WorldBossClock.begin(300_000, NOW);
        clock = clock.advance(299_999, NOW + 299_999, false);
        assertEquals(WorldBossClock.Phase.PREPARING, clock.phase());
        assertEquals(900_000, clock.remainingMillis());
        clock = clock.advance(1, NOW + 300_000, true);
        assertTrue(clock.combatOpen());
        assertEquals(900_000, clock.remainingMillis());
        var absent = WorldBossClock.begin(0, NOW).advance(0, NOW, false);
        assertEquals(WorldBossClock.Phase.CANCELLED, absent.phase());
        assertEquals(WorldBossClock.Reason.INSUFFICIENT_READY, absent.reason());
        assertFalse(absent.combatOpen());
    }

    @Test void deadlineRejectsTheExactFinalMillisecondAndSlowTicksConsumeTheirFullElapsedTime() {
        var clock = active().advance(899_999, NOW + 899_999, true);
        assertTrue(clock.combatOpen());
        assertEquals(1, clock.remainingMillis());
        clock = clock.advance(1, NOW + 900_000, true);
        assertEquals(WorldBossClock.Phase.EXPIRED, clock.phase());
        assertFalse(clock.combatOpen());
        assertEquals(0, active().advance(Long.MAX_VALUE, Long.MAX_VALUE, true).remainingMillis());
        assertEquals(840_000, active().advance(60_000, NOW + 60_000, true).remainingMillis());
    }

    @Test void recoveryPreservesCombatBudgetAndRequiresAFullFreshRegroup() {
        var interrupted = active().advance(123_456, NOW + 123_456, true).stopped(NOW + 123_456);
        assertTrue(interrupted.cleanStop());
        var recovered = interrupted.recover(NOW + 183_456);
        assertEquals(WorldBossClock.Phase.REGROUP, recovered.phase());
        assertEquals(776_544, recovered.remainingMillis());
        assertEquals(60_000, recovered.offlineMillis());
        assertEquals(1, recovered.recoveries());
        assertFalse(recovered.cleanStop());
        assertFalse(recovered.combatOpen());
        recovered = recovered.advance(119_999, NOW + 303_455, true);
        assertEquals(1, recovered.phaseRemainingMillis());
        recovered = recovered.advance(1, NOW + 303_456, true);
        assertTrue(recovered.combatOpen());
        assertEquals(776_544, recovered.remainingMillis());
    }

    @Test void tooFewReadyPlayersAfterRegroupHoldWithoutConsumingTime() {
        var clock = active().recover(NOW + 1).advance(120_000, NOW + 120_001, false);
        assertEquals(WorldBossClock.Phase.HELD, clock.phase());
        assertEquals(WorldBossClock.Reason.INSUFFICIENT_READY, clock.reason());
        assertEquals(900_000, clock.remainingMillis());
        assertEquals(clock.remainingMillis(), clock.advance(600_000, NOW + 720_001, true).remainingMillis());
    }

    @Test void exactlyThirtyMinutesIsAllowedButOneMillisecondMoreRequiresStaff() {
        assertEquals(WorldBossClock.Phase.REGROUP, active().recover(NOW + 1_800_000).phase());
        var held = active().recover(NOW + 1_800_001);
        assertEquals(WorldBossClock.Phase.HELD, held.phase());
        assertEquals(WorldBossClock.Reason.OUTAGE_TOO_LONG, held.reason());
        assertEquals(1_800_001, held.offlineMillis());
        assertEquals(0, held.recoveries());
    }

    @Test void outageLimitIsCumulativeAndInterruptedRegroupCountsAsAnotherRecovery() {
        var first = active().recover(NOW + 900_000);
        var second = first.recover(NOW + 1_800_000);
        assertEquals(2, second.recoveries());
        assertEquals(1_800_000, second.offlineMillis());
        assertEquals(120_000, second.phaseRemainingMillis());
        var excessive = first.recover(NOW + 1_800_001);
        assertEquals(WorldBossClock.Reason.OUTAGE_TOO_LONG, excessive.reason());
        var third = active().recover(NOW + 1).recover(NOW + 2).recover(NOW + 3);
        assertEquals(WorldBossClock.Reason.RECOVERY_LIMIT, third.reason());
        assertEquals(2, third.recoveries());
    }

    @Test void backwardWallClockHoldsAndStaffCannotResumeBeforeSavedCheckpoint() {
        var held = active().recover(NOW - 1);
        assertEquals(WorldBossClock.Reason.CLOCK_UNCERTAIN, held.reason());
        assertThrows(IllegalStateException.class, () -> held.resumeByStaff(NOW - 1));
        var runningRewind = active().advance(1000, NOW - 1, true);
        assertEquals(WorldBossClock.Reason.CLOCK_UNCERTAIN, runningRewind.reason());
        assertEquals(899_000, runningRewind.remainingMillis(), "Known monotonic elapsed time must not be refunded");
        assertEquals(NOW, runningRewind.checkpointMillis());
    }

    @Test void interruptedPreparationIsCancelledAndCommittedTerminalStatesDoNotRecover() {
        var preparing = WorldBossClock.begin(300_000, NOW).recover(NOW + 1);
        assertEquals(WorldBossClock.Phase.CANCELLED, preparing.phase());
        assertEquals(WorldBossClock.Reason.PREPARATION_INTERRUPTED, preparing.reason());
        assertSame(preparing, preparing.recover(Long.MAX_VALUE));
        var expired = active().advance(900_000, NOW + 900_000, true);
        assertSame(expired, expired.recover(Long.MAX_VALUE));
    }

    @Test void staffResumeDoesNotResetBudgetOrAutomaticRecoveryHistoryAndHoldIsStickyAcrossBoots() {
        var clock = active().advance(100_000, NOW + 100_000, true).recover(NOW + 110_000)
                .hold(WorldBossClock.Reason.STAFF);
        assertSame(clock, clock.recover(NOW + 999_999));
        var resumed = clock.resumeByStaff(NOW + 200_000);
        assertEquals(800_000, resumed.remainingMillis());
        assertEquals(1, resumed.recoveries());
        assertEquals(10_000, resumed.offlineMillis());
        assertEquals(120_000, resumed.phaseRemainingMillis());
        assertThrows(IllegalStateException.class, () -> resumed.resumeByStaff(NOW + 200_000));
    }

    @Test void invalidSnapshotsAndIntervalsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> WorldBossClock.begin(-1, NOW));
        assertThrows(IllegalArgumentException.class, () -> WorldBossClock.begin(0, 0));
        assertThrows(IllegalArgumentException.class, () -> active().advance(-1, NOW, true));
        assertThrows(IllegalArgumentException.class, () -> active().hold(WorldBossClock.Reason.NONE));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossClock(WorldBossClock.Phase.ACTIVE,
                900_000, 1, NOW, 0, 0, false, WorldBossClock.Reason.NONE));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossClock(WorldBossClock.Phase.HELD,
                900_000, 0, NOW, 0, 0, false, WorldBossClock.Reason.NONE));
    }
}
