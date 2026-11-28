package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossCampaignTest {
    @SuppressWarnings("deprecation")
    @Test void legacyManualStartCannotMintAnotherLiveOccurrence() {
        assertEquals(2, com.cobbleraids.api.worldboss.CobbleRaidsWorldBoss.API_VERSION);
        assertNotNull(com.cobbleraids.api.worldboss.CobbleRaidsWorldBoss.start(null, null));
    }
    static final WorldBossSchedule.Occurrence SLOT = WorldBossSchedule.on(LocalDate.of(2026, 11, 17)).getFirst();
    static final long PREP = SLOT.preparationAt().toEpochMilli(), COMBAT = SLOT.combatAt().toEpochMilli();
    static final String KEY = WorldBossCampaign.key("season1", SLOT);
    static WorldBossCampaign active() {
        return WorldBossCampaign.empty().stage(new WorldBossCampaign.Season("season1", new UUID(0, 1), "Vaelrath", "cobbleraids:giratina", 0), PREP - 600_000)
                .activate(PREP - 599_999);
    }
    static WorldBossCampaign planned() { return active().plan(SLOT, PREP); }
    static WorldBossCampaign open() {
        var campaign = planned(); var event = campaign.require(KEY);
        return campaign.transition(KEY, event.eventId(), WorldBossCampaign.Status.PREPARING, "", PREP)
                .transition(KEY, event.eventId(), WorldBossCampaign.Status.OPEN, "", COMBAT);
    }

    @Test void seasonsRequireStagingAndStartAtAwakeningOne() {
        assertThrows(IllegalStateException.class, () -> WorldBossCampaign.empty().activate(PREP));
        var campaign = active();
        assertTrue(campaign.dailyEnabled());
        assertEquals(1, campaign.active().awakening());
        assertNull(campaign.staged());
        assertThrows(IllegalArgumentException.class, () -> campaign.stage(campaign.active(), PREP));
    }

    @Test void occurrenceIdentityIncludesSeasonZoneLocalSlotOffsetAndStableEventId() {
        var campaign = planned(); var event = campaign.require(KEY);
        assertTrue(event.key().contains("season1|r1|2026-11-17T12:00[America/New_York]"));
        assertEquals(-5 * 3600, event.offsetSeconds());
        assertEquals("Vaelrath I", event.displayName());
        assertSame(campaign, campaign.plan(SLOT, PREP + 1));
        assertEquals(event, WorldBossCampaignCodec.read(WorldBossCampaignCodec.write(campaign)).require(KEY));
    }

    @Test void naturalVictoryAdvancesOnceAndCannotReplayThroughAnotherEventId() {
        var campaign = open(); var event = campaign.require(KEY);
        campaign = campaign.transition(KEY, event.eventId(), WorldBossCampaign.Status.VICTORY, "", COMBAT + 1);
        assertEquals(1, campaign.active().victories());
        assertEquals(2, campaign.active().awakening());
        assertSame(campaign, campaign.transition(KEY, event.eventId(), WorldBossCampaign.Status.VICTORY, "", COMBAT + 2));
        var won = campaign;
        assertThrows(IllegalArgumentException.class, () -> won.transition(KEY, UUID.randomUUID(), WorldBossCampaign.Status.VICTORY, "", COMBAT + 3));
        assertThrows(IllegalStateException.class, () -> won.transition(KEY, event.eventId(), WorldBossCampaign.Status.CLOSED, "STAFF", COMBAT + 3));
        var evening = WorldBossSchedule.on(SLOT.date()).getLast();
        assertEquals("Vaelrath II", campaign.plan(evening, COMBAT + 3).require(WorldBossCampaign.key("season1", evening)).displayName());
    }

    @Test void skipsAndCancellationsCannotAdvanceAwakeningOrTurnIntoWins() {
        var campaign = planned(); var event = campaign.require(KEY);
        var skipped = campaign.transition(KEY, event.eventId(), WorldBossCampaign.Status.SKIPPED, "OCCUPIED", PREP + 1);
        assertEquals(1, skipped.active().awakening());
        assertThrows(IllegalStateException.class, () -> skipped.transition(KEY, event.eventId(), WorldBossCampaign.Status.VICTORY, "", COMBAT));
    }

    @Test void openEscapeDoesNotAdvanceAndReservationsBlockSeasonSwitch() {
        var campaign = open(); var event = campaign.require(KEY);
        var staged = campaign.stage(new WorldBossCampaign.Season("season2", new UUID(0, 2), "Asterion", "cobbleraids:rayquaza", 0), COMBAT + 1);
        assertThrows(IllegalStateException.class, () -> staged.activate(COMBAT + 2));
        var closed = staged.transition(KEY, event.eventId(), WorldBossCampaign.Status.CLOSED, "ESCAPE", COMBAT + 3);
        assertEquals(0, closed.active().victories());
        var next = closed.activate(COMBAT + 4);
        assertEquals(1, next.active().awakening());
        assertEquals("Vaelrath", next.seasons().get("season1").name());
        assertEquals("Asterion", next.active().name());
    }

    @Test void newSeasonPreservesVictoriesAndUsesANewCharacterIdentity() {
        var campaign = open(); var event = campaign.require(KEY);
        campaign = campaign.transition(KEY, event.eventId(), WorldBossCampaign.Status.VICTORY, "", COMBAT + 1);
        var oldCharacter = campaign.active().characterId();
        var next = campaign.stage(new WorldBossCampaign.Season("season2", new UUID(0, 2), "Asterion", "cobbleraids:rayquaza", 0), COMBAT + 2).activate(COMBAT + 3);
        assertNotEquals(oldCharacter, next.active().characterId());
        assertEquals(1, next.active().awakening());
        assertEquals(1, next.seasons().get("season1").victories());
        assertEquals(event.eventId(), next.require(KEY).eventId());
    }

    @Test void pausingFutureLaunchesCancelsPlannedCuesButDoesNotCloseCombat() {
        var planned = planned().daily(false, PREP + 1);
        assertEquals(WorldBossCampaign.Status.SKIPPED, planned.require(KEY).status());
        assertFalse(planned.dailyEnabled());
        var fighting = open().daily(false, COMBAT + 1);
        assertEquals(WorldBossCampaign.Status.OPEN, fighting.require(KEY).status());
        var late = planned.daily(true, PREP + 2);
        assertThrows(IllegalStateException.class, () -> late.plan(SLOT, PREP + 3));
    }

    @Test void cueReceiptsAreIdempotentAndSurviveReloadWithoutChangingAwakening() {
        var campaign = planned().cue(KEY, WorldBossCampaign.TEN_MINUTE, PREP);
        assertSame(campaign, campaign.cue(KEY, WorldBossCampaign.TEN_MINUTE, PREP + 1));
        var loaded = WorldBossCampaignCodec.read(WorldBossCampaignCodec.write(campaign));
        assertEquals(WorldBossCampaign.TEN_MINUTE, loaded.require(KEY).cues());
        assertEquals(1, loaded.active().awakening());
        assertThrows(IllegalArgumentException.class, () -> loaded.cue(KEY, 3, PREP + 2));
    }

    @Test void clockRewindBlocksActivationAndPlanningUntilStaffCorrectsIt() {
        var campaign = planned();
        assertThrows(IllegalStateException.class, () -> campaign.plan(SLOT, PREP - 1));
        var paused = campaign.daily(false, PREP - 1);
        assertEquals(PREP, paused.wallHighWaterMillis());
        assertThrows(IllegalStateException.class, () -> paused.daily(true, PREP - 1));
        assertTrue(paused.daily(true, PREP + 1).dailyEnabled());
    }

    @Test void malformedCodecAndUnreconciledVictoryCountersAreRejected() {
        var tag = WorldBossCampaignCodec.write(planned());
        tag.remove("daily_enabled");
        assertThrows(IllegalArgumentException.class, () -> WorldBossCampaignCodec.read(tag));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossCampaign(
                Map.of("season1", new WorldBossCampaign.Season("season1", new UUID(0, 1), "Vaelrath", "cobbleraids:giratina", 1)),
                null, "season1", false, PREP, PREP, Map.of()));
    }

    @Test void repeatedDailyOutcomesAndReloadsPreserveExactlyOneAdvancementPerNaturalWin() {
        var campaign = active();
        long wins = 0;
        for (int i = 0; i < 400; i++) {
            var slot = WorldBossSchedule.on(SLOT.date().plusDays(i / 2)).get(i % 2);
            long prep = slot.preparationAt().toEpochMilli(), combat = slot.combatAt().toEpochMilli();
            campaign = campaign.plan(slot, prep);
            String key = WorldBossCampaign.key("season1", slot); var event = campaign.require(key);
            campaign = campaign.transition(key, event.eventId(), WorldBossCampaign.Status.PREPARING, "", prep)
                    .transition(key, event.eventId(), WorldBossCampaign.Status.OPEN, "", combat);
            var result = i % 3 == 0 ? WorldBossCampaign.Status.CLOSED : WorldBossCampaign.Status.VICTORY;
            campaign = campaign.transition(key, event.eventId(), result, result == WorldBossCampaign.Status.CLOSED ? "ESCAPE" : "", combat + 1);
            if (result == WorldBossCampaign.Status.VICTORY) wins++;
            campaign = campaign.transition(key, event.eventId(), result, "", combat + 2);
            if (i % 20 == 0) campaign = WorldBossCampaignCodec.read(WorldBossCampaignCodec.write(campaign));
            assertEquals(wins, campaign.active().victories());
            assertEquals(wins + 1, campaign.active().awakening());
        }
        assertEquals(400, campaign.occurrences().size());
    }
}
