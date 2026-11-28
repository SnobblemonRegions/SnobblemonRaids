package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class WorldBossScheduleTest {
    private final WorldBossSchedule.Occurrence noon = WorldBossSchedule.on(LocalDate.of(2026, 11, 17)).getFirst();

    @Test void noonAndEveningUseNewYorkAcrossBothDaylightSavingTransitions() {
        assertEquals(Instant.parse("2026-03-07T17:00:00Z"), WorldBossSchedule.on(LocalDate.of(2026, 3, 7)).getFirst().combatAt());
        assertEquals(Instant.parse("2026-03-08T16:00:00Z"), WorldBossSchedule.on(LocalDate.of(2026, 3, 8)).getFirst().combatAt());
        assertEquals(Instant.parse("2026-11-01T17:00:00Z"), WorldBossSchedule.on(LocalDate.of(2026, 11, 1)).getFirst().combatAt());
        assertEquals(Instant.parse("2026-11-02T01:00:00Z"), WorldBossSchedule.on(LocalDate.of(2026, 11, 1)).getLast().combatAt());
        assertEquals(Duration.ofHours(23), Duration.between(WorldBossSchedule.on(LocalDate.of(2026, 3, 7)).getFirst().combatAt(),
                WorldBossSchedule.on(LocalDate.of(2026, 3, 8)).getFirst().combatAt()));
        assertEquals(Duration.ofHours(25), Duration.between(WorldBossSchedule.on(LocalDate.of(2026, 10, 31)).getFirst().combatAt(),
                WorldBossSchedule.on(LocalDate.of(2026, 11, 1)).getFirst().combatAt()));
    }

    @Test void machineTimezoneDoesNotMoveTheSchedule() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(Instant.parse("2026-11-17T17:00:00Z"), WorldBossSchedule.next(Instant.parse("2026-11-17T16:00:00Z")).combatAt());
        } finally { TimeZone.setDefault(original); }
    }

    @Test void exactBoundaryIsIncludedAndPastSlotsNeverCatchUp() {
        assertEquals(noon, WorldBossSchedule.next(noon.combatAt()));
        assertEquals(LocalTime.of(20, 0), WorldBossSchedule.next(noon.combatAt().plusNanos(1)).time());
        var evening = WorldBossSchedule.on(noon.date()).getLast();
        assertEquals(noon.date().plusDays(1), WorldBossSchedule.next(evening.combatAt().plusSeconds(1)).date());
        assertEquals("2026-11-17T12:00[America/New_York]", noon.key());
    }

    @Test void fullPreparationIsRequiredAndPersistedConsumptionSuppressesRepeats() {
        Instant prep = noon.preparationAt(), boot = prep.minusSeconds(60);
        assertEquals(noon, WorldBossSchedule.preparationDue(boot, prep.minusSeconds(1), prep, null));
        assertEquals(noon, WorldBossSchedule.preparationDue(prep, prep, prep, null));
        assertNull(WorldBossSchedule.preparationDue(prep.plusMillis(1), prep.plusMillis(1), prep.plusSeconds(1), null));
        assertNull(WorldBossSchedule.preparationDue(boot, prep.minusSeconds(1), prep, noon.combatAt()));
        assertNull(WorldBossSchedule.preparationDue(boot, prep, prep.plusSeconds(1), noon.combatAt().plusSeconds(1)));
        assertNull(WorldBossSchedule.preparationDue(boot, prep.minusSeconds(1), noon.combatAt(), null));
        assertNull(WorldBossSchedule.preparationDue(boot, prep.minusSeconds(1), noon.combatAt().plusSeconds(1), null));
    }

    @Test void openingAcceptsNormalTickDelayAndRejectsLateBootStallAndOverlap() {
        Instant combat = noon.combatAt(), previous = combat.minusMillis(1), prepared = noon.preparationAt();
        assertEquals(WorldBossSchedule.Opening.WAIT, WorldBossSchedule.opening(noon, prepared, previous, previous, false));
        assertEquals(WorldBossSchedule.Opening.START, WorldBossSchedule.opening(noon, prepared, previous, combat, false));
        assertEquals(WorldBossSchedule.Opening.START, WorldBossSchedule.opening(noon, prepared, previous, combat.plusSeconds(10), false));
        assertEquals(WorldBossSchedule.Opening.SKIP_LATE, WorldBossSchedule.opening(noon, prepared, previous, combat.plusSeconds(10).plusMillis(1), false));
        assertEquals(WorldBossSchedule.Opening.SKIP_INCOMPLETE_PREPARATION, WorldBossSchedule.opening(noon, prepared.plusMillis(1), previous, combat, false));
        assertEquals(WorldBossSchedule.Opening.SKIP_OCCUPIED, WorldBossSchedule.opening(noon, prepared, previous, combat, true));
    }

    @Test void backwardClockCannotTriggerPreparationOrOpening() {
        Instant prep = noon.preparationAt();
        assertNull(WorldBossSchedule.preparationDue(prep.minusSeconds(60), prep.plusSeconds(1), prep, null));
        assertEquals(WorldBossSchedule.Opening.SKIP_CLOCK_REWIND,
                WorldBossSchedule.opening(noon, prep, noon.combatAt().plusSeconds(1), noon.combatAt(), false));
    }

    @Test void announcementsAndEntranceUseThresholdEdgesWithoutLateBootReplay() {
        Instant entrance = noon.entranceAt(), boot = entrance.minusSeconds(600);
        assertTrue(WorldBossSchedule.crossed(boot, entrance.minusMillis(1), entrance, entrance));
        assertFalse(WorldBossSchedule.crossed(boot, entrance, entrance.plusMillis(1), entrance));
        assertFalse(WorldBossSchedule.crossed(entrance.plusMillis(1), entrance.plusMillis(1), entrance.plusSeconds(1), entrance));
        assertFalse(WorldBossSchedule.crossed(boot, entrance.plusSeconds(1), entrance, entrance));
    }

    @Test void occurrenceCannotRepresentAnArbitraryOrMismatchedInstant() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossSchedule.Occurrence(noon.date(), LocalTime.of(13, 0), noon.combatAt()));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossSchedule.Occurrence(noon.date(), noon.time(), noon.combatAt().plusSeconds(1)));
    }
}
