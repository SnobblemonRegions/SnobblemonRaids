package com.cobbleraids.worldboss;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Daily local-time occurrences, independent of Minecraft ticks and the machine's default zone.
 * This plans eligibility only: the adapter must durably consume an occurrence before announcing
 * preparation, and must commit the ready roster/health before admitting its first duel.
 */
public final class WorldBossSchedule {
    public static final ZoneId ZONE = ZoneId.of("America/New_York");
    public static final Duration PREPARATION = Duration.ofMinutes(5);
    public static final Duration START_TOLERANCE = Duration.ofSeconds(10);
    private static final List<LocalTime> TIMES = List.of(LocalTime.NOON, LocalTime.of(20, 0));

    private WorldBossSchedule() {}

    public record Occurrence(LocalDate date, LocalTime time, Instant combatAt) {
        public Occurrence {
            Objects.requireNonNull(date);
            Objects.requireNonNull(time);
            Objects.requireNonNull(combatAt);
            if (!TIMES.contains(time) || !date.atTime(time).atZone(ZONE).toInstant().equals(combatAt)) {
                throw new IllegalArgumentException("Not a daily World Boss occurrence");
            }
        }
        public String key() { return date + "T" + time + "[America/New_York]"; }
        public Instant preparationAt() { return combatAt.minus(PREPARATION); }
        public Instant entranceAt() { return combatAt.minusSeconds(15); }
    }

    public enum Opening { WAIT, START, SKIP_INCOMPLETE_PREPARATION, SKIP_LATE, SKIP_CLOCK_REWIND, SKIP_OCCUPIED }

    public static List<Occurrence> on(LocalDate date) {
        Objects.requireNonNull(date);
        return TIMES.stream().map(time -> new Occurrence(date, time, date.atTime(time).atZone(ZONE).toInstant())).toList();
    }

    /** Combat at or after the supplied instant, including an exact noon/evening boundary. */
    public static Occurrence next(Instant now) {
        Objects.requireNonNull(now);
        LocalDate today = now.atZone(ZONE).toLocalDate();
        return on(today).stream().filter(slot -> !slot.combatAt().isBefore(now)).findFirst()
                .orElseGet(() -> on(today.plusDays(1)).getFirst());
    }

    /**
     * Returns only an occurrence whose preparation boundary was crossed while this process was
     * running. A boot at 11:58 cannot manufacture the missing three minutes of preparation.
     * A stall that crosses both preparation and combat skips the occurrence rather than catching up.
     * Persisted last-consumed time suppresses duplicate/repeated slots after clock changes/restart.
     */
    public static Occurrence preparationDue(Instant processStartedAt, Instant previousTick, Instant now,
                                             Instant lastConsumedCombatAt) {
        Objects.requireNonNull(processStartedAt);
        Objects.requireNonNull(previousTick);
        Objects.requireNonNull(now);
        if (now.isBefore(previousTick) || previousTick.isBefore(processStartedAt)) return null;
        if (Duration.between(previousTick, now).compareTo(START_TOLERANCE) > 0) return null;
        Occurrence slot = next(now);
        if (!slot.combatAt().isAfter(now)) return null;
        Instant preparation = slot.preparationAt();
        if (processStartedAt.isAfter(preparation) || previousTick.isAfter(preparation) || now.isBefore(preparation)) return null;
        if (now.isAfter(preparation.plus(START_TOLERANCE))) return null;
        if (lastConsumedCombatAt != null && !slot.combatAt().isAfter(lastConsumedCombatAt)) return null;
        return slot;
    }

    /** Decide once at/after combat; a WAIT result never consumes an occurrence. */
    public static Opening opening(Occurrence slot, Instant preparedAt, Instant previousTick, Instant now, boolean occupied) {
        Objects.requireNonNull(slot);
        Objects.requireNonNull(previousTick);
        Objects.requireNonNull(now);
        if (now.isBefore(previousTick)) return Opening.SKIP_CLOCK_REWIND;
        if (now.isBefore(slot.combatAt())) return Opening.WAIT;
        if (occupied) return Opening.SKIP_OCCUPIED;
        if (preparedAt == null || preparedAt.isAfter(slot.preparationAt())) return Opening.SKIP_INCOMPLETE_PREPARATION;
        if (now.isAfter(slot.combatAt().plus(START_TOLERANCE))) return Opening.SKIP_LATE;
        return Opening.START;
    }

    /** A single threshold edge, not a catch-up notification after a late boot or a clock rewind. */
    public static boolean crossed(Instant processStartedAt, Instant previousTick, Instant now, Instant threshold) {
        Objects.requireNonNull(processStartedAt);
        Objects.requireNonNull(previousTick);
        Objects.requireNonNull(now);
        Objects.requireNonNull(threshold);
        return !processStartedAt.isAfter(threshold) && !previousTick.isBefore(processStartedAt)
                && previousTick.isBefore(threshold) && !now.isBefore(threshold);
    }
}
