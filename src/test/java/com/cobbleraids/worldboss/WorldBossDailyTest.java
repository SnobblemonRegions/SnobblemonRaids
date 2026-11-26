package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossDailyTest {

    private static final WorldBossConfig.Daily DAILY = WorldBossConfig.Daily.defaults();

    @Test
    void defaultsAreSevenPmEasternWithTwentyFivePlayers() {
        assertTrue(DAILY.enabled());
        assertEquals("19:00", DAILY.time());
        assertEquals("America/New_York", DAILY.zone());
        assertEquals(25, DAILY.maxPlayers());
        assertEquals(List.of(15, 5), DAILY.warningMinutes());
        assertEquals(List.of("legendary"), DAILY.tiers());
    }

    @Test
    void slotFollowsEasternDaylightAndStandardTime() {
        // 19:00 EDT is 23:00 UTC in summer; 19:00 EST is 00:00 UTC the next day in winter.
        assertEquals(Instant.parse("2026-07-01T23:00:00Z"), WorldBossDaily.slotOn(java.time.LocalDate.parse("2026-07-01"), DAILY));
        assertEquals(Instant.parse("2026-12-02T00:00:00Z"), WorldBossDaily.slotOn(java.time.LocalDate.parse("2026-12-01"), DAILY));
    }

    @Test
    void dueFromSevenPmUntilTheLateStartRunsOut() {
        assertEquals(Optional.empty(), WorldBossDaily.dueDay(Instant.parse("2026-07-01T22:59:59Z"), DAILY, ""));
        assertEquals(Optional.of("2026-07-01"), WorldBossDaily.dueDay(Instant.parse("2026-07-01T23:00:00Z"), DAILY, ""));
        assertEquals(Optional.of("2026-07-01"), WorldBossDaily.dueDay(Instant.parse("2026-07-01T23:59:59Z"), DAILY, ""));
        assertEquals(Optional.empty(), WorldBossDaily.dueDay(Instant.parse("2026-07-02T00:00:00Z"), DAILY, ""),
                "an hour late is too late: that day is skipped");
    }

    @Test
    void neverTwiceOnTheSameDay() {
        assertEquals(Optional.empty(), WorldBossDaily.dueDay(Instant.parse("2026-07-01T23:10:00Z"), DAILY, "2026-07-01"));
        assertEquals(Optional.of("2026-07-02"), WorldBossDaily.dueDay(Instant.parse("2026-07-02T23:10:00Z"), DAILY, "2026-07-01"));
    }

    @Test
    void lateStartCrossingMidnightStillBelongsToTheSlotsDay() {
        WorldBossConfig.Daily late = new WorldBossConfig.Daily(true, "23:30", "America/New_York", 60, List.of(5), 25, List.of(), List.of("legendary"));
        // 23:30 EDT on 1 July is 03:30 UTC on 2 July; 00:10 EDT on 2 July is 04:10 UTC.
        assertEquals(Optional.of("2026-07-01"), WorldBossDaily.dueDay(Instant.parse("2026-07-02T04:10:00Z"), late, ""));
        assertEquals(Optional.empty(), WorldBossDaily.dueDay(Instant.parse("2026-07-02T04:10:00Z"), late, "2026-07-01"));
    }

    @Test
    void warnsFifteenThenFiveMinutesAheadOnceEach() {
        Set<String> warned = new HashSet<>();
        assertEquals(Optional.empty(), WorldBossDaily.warningDue(Instant.parse("2026-07-01T22:44:59Z"), DAILY, "", warned));
        var fifteen = WorldBossDaily.warningDue(Instant.parse("2026-07-01T22:45:00Z"), DAILY, "", warned).orElseThrow();
        assertEquals(15, fifteen.minutesLeft());
        warned.add(fifteen.key());
        assertEquals(Optional.empty(), WorldBossDaily.warningDue(Instant.parse("2026-07-01T22:50:00Z"), DAILY, "", warned));
        var five = WorldBossDaily.warningDue(Instant.parse("2026-07-01T22:55:00Z"), DAILY, "", warned).orElseThrow();
        assertEquals(5, five.minutesLeft());
        warned.add(five.key());
        assertEquals(Optional.empty(), WorldBossDaily.warningDue(Instant.parse("2026-07-01T22:58:00Z"), DAILY, "", warned));
    }

    @Test
    void aServerStartingLateWarnsWithTheRealTimeLeft() {
        var warning = WorldBossDaily.warningDue(Instant.parse("2026-07-01T22:57:30Z"), DAILY, "", new HashSet<>()).orElseThrow();
        assertEquals(3, warning.minutesLeft());
    }

    @Test
    void noWarningForADayThatAlreadyRan() {
        assertEquals(Optional.empty(), WorldBossDaily.warningDue(Instant.parse("2026-07-01T22:55:00Z"), DAILY, "2026-07-01", new HashSet<>()));
    }

    @Test
    void theCapCountsEachPlayerOnceAndLetsReturningPlayersBackIn() {
        Set<UUID> in = new HashSet<>();
        for (int i = 0; i < 25; i++) in.add(UUID.randomUUID());
        UUID already = in.iterator().next();
        assertFalse(WorldBossRules.hasRoom(in, UUID.randomUUID(), 25), "the 26th player is turned away");
        assertTrue(WorldBossRules.hasRoom(in, already, 25), "someone already in may come back");
        assertTrue(WorldBossRules.hasRoom(in, UUID.randomUUID(), 0), "0 means no cap");
    }

    @Test
    void renownedPoolGrowsByTheRenownHealthBonus() {
        assertEquals(1.30f, WorldBossScaling.renownMultiplier(0.30, 0.15, false), 1e-6f);
        assertEquals(1.45f, WorldBossScaling.renownMultiplier(0.30, 0.15, true), 1e-6f);
        assertEquals(1.0f, WorldBossScaling.renownMultiplier(0.0, 0.0, false), 1e-6f);
        // The multiplier applies on top of the ceiling, so a renowned boss at the cap is still tougher than an ordinary one.
        assertEquals(195_000f, WorldBossScaling.renowned(150_000f, 1.30f), 1e-3f);
    }

    @Test
    void parsesAndWritesTheDailySection() {
        JsonObject root = WorldBossConfig.defaults().toJson();
        JsonObject daily = root.getAsJsonObject("daily");
        daily.addProperty("time", "20:30");
        daily.addProperty("max_players", 10);
        JsonArray tiers = new JsonArray();
        tiers.add("mythical");
        daily.add("tiers", tiers);
        WorldBossConfig parsed = WorldBossConfig.fromJson(root);
        assertEquals("20:30", parsed.daily().time());
        assertEquals(10, parsed.daily().maxPlayers());
        assertEquals(List.of("mythical"), parsed.daily().tiers());
        assertEquals(parsed.daily(), WorldBossConfig.fromJson(parsed.toJson()).daily());
    }

    @Test
    void theDailyPoolAsksTheSameWinsOfEachPlayerAtAnySize() {
        // 5,000 HP duels, 6 wins each, at least 3 players: the old event formula hit its cap at 5 players and left 25 with 2-3 wins each.
        assertEquals(90_000f, WorldBossScaling.dailyPool(5_000L, 6f, 0, 3), 1e-3f, "the start, before anyone joins");
        assertEquals(90_000f, WorldBossScaling.dailyPool(5_000L, 6f, 1, 3), 1e-3f, "one player still has the three-player fight");
        assertEquals(150_000f, WorldBossScaling.dailyPool(5_000L, 6f, 5, 3), 1e-3f);
        assertEquals(750_000f, WorldBossScaling.dailyPool(5_000L, 6f, 25, 3), 1e-3f);
        for (int players = 3; players <= 25; players++) {
            float wins = WorldBossScaling.dailyPool(5_000L, 6f, players, 3) / 5_000f / players;
            assertEquals(6f, wins, 1e-4f, players + " players");
        }
    }

    @Test
    void dailyDuelsFightAtTheBossesOwnLevelUnlessSet() {
        assertEquals(100, WorldBossScaling.dailyDuelLevel(0, 100));
        assertEquals(85, WorldBossScaling.dailyDuelLevel(85, 100));
        assertEquals(0, DAILY.duelLevel());
        assertEquals(5_000L, DAILY.duelHealth());
        assertEquals(6f, DAILY.winsPerPlayer(), 1e-6f);
        assertEquals(3, DAILY.minPlayers());
    }

    @Test
    void theDailyStrengthRoundTripsThroughJson() {
        JsonObject root = WorldBossConfig.defaults().toJson();
        JsonObject daily = root.getAsJsonObject("daily");
        daily.addProperty("duel_level", 90);
        daily.addProperty("duel_health", 4_000);
        daily.addProperty("wins_per_player", 8);
        daily.addProperty("min_players", 4);
        daily.addProperty("grace_seconds", 60);
        WorldBossConfig parsed = WorldBossConfig.fromJson(root);
        assertEquals(90, parsed.daily().duelLevel());
        assertEquals(4_000L, parsed.daily().duelHealth());
        assertEquals(8f, parsed.daily().winsPerPlayer(), 1e-6f);
        assertEquals(4, parsed.daily().minPlayers());
        assertEquals(60, parsed.daily().graceSeconds());
        assertEquals(parsed.daily(), WorldBossConfig.fromJson(parsed.toJson()).daily());
    }

    @Test
    void badDailyStrengthFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossConfig.Daily(true, "19:00", "America/New_York", 60, List.of(5), 25,
                List.of(), List.of("legendary"), 101, 5_000L, 6f, 3, 180));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossConfig.Daily(true, "19:00", "America/New_York", 60, List.of(5), 25,
                List.of(), List.of("legendary"), 0, 0L, 6f, 3, 180));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossConfig.Daily(true, "19:00", "America/New_York", 60, List.of(5), 25,
                List.of(), List.of("legendary"), 0, 5_000L, Float.NaN, 3, 180));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossConfig.Daily(true, "19:00", "America/New_York", 60, List.of(5), 25,
                List.of(), List.of("legendary"), 0, 5_000L, 6f, 0, 180));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossConfig.Daily(true, "19:00", "America/New_York", 60, List.of(5), 25,
                List.of(), List.of("legendary"), 0, 5_000L, 6f, 3, -1));
    }

    @Test
    void aConfigWithoutADailySectionGetsTheDefaults() {
        JsonObject root = WorldBossConfig.defaults().toJson();
        root.remove("daily");
        assertEquals(DAILY, WorldBossConfig.fromJson(root).daily());
    }

    @Test
    void badDailyValuesFailLoudly() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldBossConfig.Daily(true, "7pm", "America/New_York", 60, List.of(5), 25, List.of(), List.of("legendary")));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldBossConfig.Daily(true, "19:00", "Eastern", 60, List.of(5), 25, List.of(), List.of("legendary")));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldBossConfig.Daily(true, "19:00", "America/New_York", 60, List.of(5), 25, List.of(), List.of("epic")));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldBossConfig.Daily(true, "19:00", "America/New_York", 60, List.of(5), -1, List.of(), List.of("legendary")));
    }
}
