package com.cobbleraids.worldboss;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * When the daily renowned World Boss appears, and when players are warned it is coming.
 *
 * <p>Pure: every answer comes from the instant, the {@link WorldBossConfig.Daily} settings and the day that already ran, so daylight saving and a
 * late start past midnight are tested without a server. A day is the date of the slot in the configured zone ({@code "2026-07-01"}), which is
 * also what {@link WorldBossState} saves, so a restart cannot run the same day twice.
 */
public final class WorldBossDaily {

    private WorldBossDaily() {}

    /** One warning to broadcast: {@code key} marks it sent (date and step), {@code minutesLeft} is the real time left, rounded up. */
    public record Warning(String key, int minutesLeft) {}

    /** The slot's instant on a given date in the configured zone. A time that falls in a spring-forward gap moves later, as {@link ZonedDateTime#of} does. */
    public static Instant slotOn(LocalDate date, WorldBossConfig.Daily daily) {
        return ZonedDateTime.of(date, daily.localTime(), daily.zoneId()).toInstant();
    }

    /**
     * The day whose boss should start now, if any: from the slot until {@code late_start_minutes} after it, and only when that day has not already
     * run. Yesterday's slot is checked too, so a late start that runs past midnight still belongs to the day it was meant for.
     */
    public static Optional<String> dueDay(Instant now, WorldBossConfig.Daily daily, String lastDay) {
        LocalDate today = now.atZone(daily.zoneId()).toLocalDate();
        for (LocalDate date : List.of(today.minusDays(1), today)) {
            Instant slot = slotOn(date, daily);
            Instant lateEnd = slot.plus(Duration.ofMinutes(daily.lateStartMinutes()));
            String day = date.toString();
            if (!now.isBefore(slot) && now.isBefore(lateEnd) && !day.equals(lastDay)) return Optional.of(day);
        }
        return Optional.empty();
    }

    /**
     * The warning to send now, if any: the nearest configured step that has been reached and not yet sent, before the next slot of a day that
     * has not run. A server that comes up three minutes before the slot sends one warning saying three minutes, not a stale fifteen.
     */
    public static Optional<Warning> warningDue(Instant now, WorldBossConfig.Daily daily, String lastDay, Set<String> sent) {
        LocalDate today = now.atZone(daily.zoneId()).toLocalDate();
        for (LocalDate date : List.of(today, today.plusDays(1))) {
            Instant slot = slotOn(date, daily);
            if (!now.isBefore(slot)) continue;
            if (date.toString().equals(lastDay)) return Optional.empty();
            Integer step = null;
            for (int minutes : daily.warningMinutes()) {
                if (!now.isBefore(slot.minus(Duration.ofMinutes(minutes))) && (step == null || minutes < step)) step = minutes;
            }
            if (step == null) return Optional.empty();
            String key = date + "@" + step;
            if (sent.contains(key)) return Optional.empty();
            long secondsLeft = Duration.between(now, slot).getSeconds();
            return Optional.of(new Warning(key, (int) Math.max(1L, (secondsLeft + 59L) / 60L)));
        }
        return Optional.empty();
    }
}
