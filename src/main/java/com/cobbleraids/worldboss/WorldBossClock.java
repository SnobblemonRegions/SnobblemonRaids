package com.cobbleraids.worldboss;

import java.util.Objects;

/** Immutable event timing policy. Elapsed intervals come from a monotonic clock, never game ticks. */
public record WorldBossClock(Phase phase, long remainingMillis, long phaseRemainingMillis,
                             long checkpointMillis, int recoveries, long offlineMillis,
                             boolean cleanStop, Reason reason) {
    public static final long ACTIVE_MILLIS = 15 * 60_000L;
    public static final long REGROUP_MILLIS = 2 * 60_000L;
    public static final long MAX_OFFLINE_MILLIS = 30 * 60_000L;
    public static final int MAX_RECOVERIES = 2;

    public enum Phase { PREPARING, ACTIVE, REGROUP, HELD, EXPIRED, CANCELLED }
    public enum Reason { NONE, STAFF, CLOCK_UNCERTAIN, MONOTONIC_REWIND, OUTAGE_TOO_LONG,
        RECOVERY_LIMIT, INSUFFICIENT_READY, PREPARATION_INTERRUPTED, STORAGE_FAILURE, RULES_CHANGED, CALIBRATION_INVALID, SCHEDULE_MISSED, PRESENTATION_FAILURE, ACTION_EVIDENCE_UNAVAILABLE, SIMULATOR_FAILURE }

    public WorldBossClock {
        Objects.requireNonNull(phase);
        Objects.requireNonNull(reason);
        if (remainingMillis < 0 || remainingMillis > ACTIVE_MILLIS || phaseRemainingMillis < 0
                || checkpointMillis <= 0 || recoveries < 0 || recoveries > MAX_RECOVERIES || offlineMillis < 0) {
            throw new IllegalArgumentException("Invalid World Boss clock");
        }
        if (phase != Phase.PREPARING && phase != Phase.REGROUP && phaseRemainingMillis != 0
                || phase == Phase.PREPARING && phaseRemainingMillis > 600_000
                || phase == Phase.REGROUP && phaseRemainingMillis > REGROUP_MILLIS
                || phase == Phase.EXPIRED && remainingMillis != 0
                || phase != Phase.EXPIRED && remainingMillis == 0
                || (phase == Phase.HELD || phase == Phase.CANCELLED) == (reason == Reason.NONE)) {
            throw new IllegalArgumentException("Contradictory World Boss clock state");
        }
        if (phase == Phase.CANCELLED && reason != Reason.INSUFFICIENT_READY && reason != Reason.PREPARATION_INTERRUPTED && reason != Reason.SCHEDULE_MISSED) {
            throw new IllegalArgumentException("Invalid cancellation reason");
        }
    }

    public static WorldBossClock begin(long preparationMillis, long nowMillis) {
        return new WorldBossClock(Phase.PREPARING,
                ACTIVE_MILLIS, preparationMillis, nowMillis, 0, 0, false, Reason.NONE);
    }

    /** Fixed daily wall boundary; a long stall/jump cannot quietly become a late combat launch. */
    public WorldBossClock scheduledPreparation(WorldBossSchedule.Occurrence slot, long elapsedMillis, long nowMillis, boolean enoughReady) {
        if (phase != Phase.PREPARING || elapsedMillis < 0) throw new IllegalStateException("Not scheduled preparation");
        if (nowMillis < checkpointMillis || Math.abs((nowMillis - checkpointMillis) - elapsedMillis) > WorldBossSchedule.START_TOLERANCE.toMillis()) return hold(Reason.CLOCK_UNCERTAIN);
        long combat = slot.combatAt().toEpochMilli();
        if (nowMillis < combat) return copy(Phase.PREPARING, remainingMillis, combat - nowMillis, nowMillis, recoveries, offlineMillis, false, Reason.NONE);
        if (nowMillis > combat + WorldBossSchedule.START_TOLERANCE.toMillis()) return copy(Phase.CANCELLED, remainingMillis, 0, nowMillis, recoveries, offlineMillis, false, Reason.SCHEDULE_MISSED);
        return advance(phaseRemainingMillis, nowMillis, enoughReady);
    }

    /** Preparation and regroup never consume the active budget, including their final tick. */
    public WorldBossClock advance(long elapsedMillis, long nowMillis, boolean enoughReady) {
        if (elapsedMillis < 0) throw new IllegalArgumentException("Negative elapsed interval");
        WorldBossClock next = this;
        if (phase == Phase.ACTIVE) {
            long remaining = Math.max(0, remainingMillis - elapsedMillis);
            next = copy(remaining == 0 ? Phase.EXPIRED : Phase.ACTIVE, remaining, 0, checkpointMillis, recoveries, offlineMillis, false, Reason.NONE);
        } else if (phase == Phase.PREPARING || phase == Phase.REGROUP) {
            long left = Math.max(0, phaseRemainingMillis - elapsedMillis);
            if (left > 0) next = copy(phase, remainingMillis, left, checkpointMillis, recoveries, offlineMillis, false, Reason.NONE);
            else if (enoughReady) next = copy(Phase.ACTIVE, remainingMillis, 0, checkpointMillis, recoveries, offlineMillis, false, Reason.NONE);
            else next = copy(phase == Phase.PREPARING ? Phase.CANCELLED : Phase.HELD, remainingMillis, 0,
                        checkpointMillis, recoveries, offlineMillis, false, Reason.INSUFFICIENT_READY);
        }
        if (nowMillis < checkpointMillis) return next.hold(Reason.CLOCK_UNCERTAIN);
        return next.stamp(nowMillis, false);
    }

    /** Conservative interruption begins at the last durable heartbeat, also for an abrupt kill. */
    public WorldBossClock recover(long nowMillis) {
        if (terminal() || phase == Phase.HELD) return this;
        if (nowMillis < checkpointMillis) return hold(Reason.CLOCK_UNCERTAIN);
        if (phase == Phase.PREPARING) return copy(Phase.CANCELLED, remainingMillis, 0, nowMillis,
                recoveries, offlineMillis, false, Reason.PREPARATION_INTERRUPTED);
        long outage = nowMillis - checkpointMillis;
        long total = offlineMillis > Long.MAX_VALUE - outage ? Long.MAX_VALUE : offlineMillis + outage;
        if (outage > MAX_OFFLINE_MILLIS || total > MAX_OFFLINE_MILLIS) {
            return copy(Phase.HELD, remainingMillis, 0, nowMillis, recoveries, total, false, Reason.OUTAGE_TOO_LONG);
        }
        if (recoveries == MAX_RECOVERIES) return copy(Phase.HELD, remainingMillis, 0, nowMillis,
                recoveries, total, false, Reason.RECOVERY_LIMIT);
        return copy(Phase.REGROUP, remainingMillis, REGROUP_MILLIS, nowMillis, recoveries + 1, total, false, Reason.NONE);
    }

    public WorldBossClock hold(Reason why) {
        if (why == Reason.NONE) throw new IllegalArgumentException("Hold requires a reason");
        return terminal() ? this : copy(Phase.HELD, remainingMillis, 0, checkpointMillis, recoveries, offlineMillis, false, why);
    }

    /** Staff override retains HP, remaining combat time and automatic-recovery history. */
    public WorldBossClock resumeByStaff(long nowMillis) {
        if (phase != Phase.HELD || nowMillis < checkpointMillis) throw new IllegalStateException("Cannot resume this clock");
        return copy(Phase.REGROUP, remainingMillis, REGROUP_MILLIS, nowMillis, recoveries, offlineMillis, false, Reason.NONE);
    }

    public WorldBossClock stopped(long nowMillis) {
        return nowMillis < checkpointMillis ? hold(Reason.CLOCK_UNCERTAIN) : stamp(nowMillis, true);
    }

    public boolean terminal() { return phase == Phase.EXPIRED || phase == Phase.CANCELLED; }
    public boolean combatOpen() { return phase == Phase.ACTIVE && remainingMillis > 0; }

    private WorldBossClock stamp(long millis, boolean stopped) {
        return copy(phase, remainingMillis, phaseRemainingMillis, millis, recoveries, offlineMillis, stopped, reason);
    }

    private static WorldBossClock copy(Phase phase, long remaining, long phaseRemaining, long checkpoint,
                                       int recoveries, long offline, boolean clean, Reason reason) {
        return new WorldBossClock(phase, remaining, phaseRemaining, checkpoint, recoveries, offline, clean, reason);
    }
}
