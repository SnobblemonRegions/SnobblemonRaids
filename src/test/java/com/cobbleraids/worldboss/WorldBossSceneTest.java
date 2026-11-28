package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorldBossSceneTest {
    private WorldBossCampaign preparing() { var campaign = WorldBossCampaignTest.planned(); var event = campaign.require(WorldBossCampaignTest.KEY); return campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.PREPARING, "", WorldBossCampaignTest.PREP); }
    private WorldBossSceneLedger entrance(WorldBossCampaign campaign) {
        return new WorldBossSceneLedger().record(campaign.require(WorldBossCampaignTest.KEY), WorldBossSceneScript.Kind.ENTRANCE, WorldBossCampaignTest.SLOT.entranceAt().toEpochMilli(), "minecraft:overworld", 0, 64, 0, 32);
    }
    @Test void catalogueHasBoundedVersionedTimelinesAndCorrectAwakeningVariants() {
        var ids = new java.util.HashSet<String>();
        for (var script : WorldBossSceneScript.catalogue()) { assertTrue(ids.add(script.id())); assertEquals(64, script.fingerprint().length()); assertTrue(script.durationMillis() <= (script.kind() == WorldBossSceneScript.Kind.ENTRANCE ? 15_000 : 12_000)); }
        assertTrue(WorldBossSceneScript.select("cobbleraids:giratina", 3, WorldBossSceneScript.Kind.ENTRANCE).beats().getFirst().text().contains("Twice"));
        assertFalse(WorldBossSceneScript.select("cobbleraids:giratina", 4, WorldBossSceneScript.Kind.ENTRANCE).beats().getFirst().text().contains("Twice"));
        assertTrue(WorldBossSceneScript.select("cobbleraids:giratina", 1, WorldBossSceneScript.Kind.VICTORY).beats().getLast().text().contains("return stronger"));
    }
    @Test void slowTicksShowOneLatestSubtitleWithoutReplayingADialogueBacklog() {
        var script = WorldBossSceneScript.select("cobbleraids:giratina", 1, WorldBossSceneScript.Kind.ENTRANCE); var timeline = new WorldBossSceneTimeline(script);
        assertEquals(script.beats().getFirst(), timeline.advance(0)); assertNull(timeline.advance(0));
        assertEquals(script.beats().getLast(), timeline.advance(10_500)); assertNull(timeline.advance(10_500));
        assertNull(timeline.advance(14_000)); assertTrue(timeline.done(14_000));
        assertThrows(IllegalArgumentException.class, () -> timeline.advance(13_999));
    }
    @Test void entranceAndTranscriptCommitWithTheirCampaignCueAndNeverOverwriteOnRetry() {
        var campaign = preparing().cue(WorldBossCampaignTest.KEY, WorldBossCampaign.ENTRANCE, WorldBossCampaignTest.COMBAT - 15_000);
        var ledger = entrance(campaign); ledger.validate(campaign);
        var receipt = ledger.receipts().values().iterator().next();
        assertSame(ledger, ledger.record(campaign.require(WorldBossCampaignTest.KEY), WorldBossSceneScript.Kind.ENTRANCE, 99, "minecraft:the_end", 100, 64, 0, 12));
        var state = new WorldBossState(); state.campaign(campaign); state.scenes(ledger);
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertFalse(restored.isQuarantined()); assertEquals(receipt, restored.scenesView().receipts().get(receipt.key()));
        assertTrue(receipt.inside("minecraft:overworld", 0, 64, 0)); assertFalse(receipt.inside("minecraft:overworld", 0, 100, 0)); assertFalse(receipt.inside("minecraft:the_end", 0, 64, 0));
    }
    @Test void victoryAndEscapeReceiptsRequireTheirActualCommittedOutcome() {
        var campaign = WorldBossCampaignTest.open(); var event = campaign.require(WorldBossCampaignTest.KEY);
        var victory = new WorldBossSceneLedger().record(event, WorldBossSceneScript.Kind.VICTORY, WorldBossCampaignTest.COMBAT + 1, "minecraft:overworld", 0, 64, 0, 32);
        var open = campaign; assertThrows(IllegalArgumentException.class, () -> victory.validate(open));
        var won = campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.VICTORY, "", WorldBossCampaignTest.COMBAT + 1); victory.validate(won);
        var escape = new WorldBossSceneLedger().record(event, WorldBossSceneScript.Kind.ESCAPE, WorldBossCampaignTest.COMBAT + 1, "minecraft:overworld", 0, 64, 0, 32);
        var cancelled = campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.CLOSED, "CANCELLED", WorldBossCampaignTest.COMBAT + 1);
        assertThrows(IllegalArgumentException.class, () -> escape.validate(cancelled));
        escape.validate(campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.CLOSED, "TIMEOUT", WorldBossCampaignTest.COMBAT + 1));
    }
    @Test void corruptDuplicateOrUncommittedSceneHistoryIsRejectedAndPreserved() {
        var uncommitted = preparing(); assertThrows(IllegalArgumentException.class, () -> entrance(uncommitted).validate(uncommitted));
        var campaign = uncommitted.cue(WorldBossCampaignTest.KEY, WorldBossCampaign.ENTRANCE, WorldBossCampaignTest.COMBAT - 15_000);
        var ledger = entrance(campaign); var tag = ledger.save(); var rows = tag.getList("receipts", 10); rows.add(rows.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> WorldBossSceneLedger.load(tag));
        var state = new WorldBossState(); state.campaign(campaign); state.scenes(ledger); var broken = state.save(new CompoundTag(), null); broken.getCompound("scenes").getList("receipts", 10).getCompound(0).remove("beats");
        var quarantined = WorldBossState.load(broken, null); assertTrue(quarantined.isQuarantined()); assertEquals(broken, quarantined.save(new CompoundTag(), null));
    }
    @Test void scenePreferencesSurviveOtherBenefitsAndLegacyDefaultsDoNotMuteByAccident() {
        var player = UUID.randomUUID(); var options = new WorldBossBenefits.SceneOptions(0, false, WorldBossBenefits.Effects.MINIMAL);
        var benefits = new WorldBossBenefits().sceneOptions(player, options).effects(player, WorldBossBenefits.Effects.OFF).promise(UUID.randomUUID(), 1000, 10, java.util.Set.of(player), false);
        assertEquals(options, WorldBossBenefits.load(benefits.save()).sceneOptions(player));
        assertSame(benefits, benefits.sceneOptions(player, options));
        var legacy = new WorldBossBenefits().save(); legacy.putInt("version", 1); legacy.remove("scenes");
        assertEquals(WorldBossBenefits.SceneOptions.DEFAULT, WorldBossBenefits.load(legacy).sceneOptions(player));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossBenefits.SceneOptions(101, true, WorldBossBenefits.Effects.FULL));
    }
    @Test void invalidOrUnboundedScriptsCannotInjectControlsOrLongRunningScenes() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossSceneScript.Beat(0, "bad\ntext"));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossSceneScript("bad", WorldBossSceneScript.Kind.ENTRANCE, 16_000, List.of(new WorldBossSceneScript.Beat(0, "text"))));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossSceneScript("bad", WorldBossSceneScript.Kind.VICTORY, 1000, List.of(new WorldBossSceneScript.Beat(0, "first"), new WorldBossSceneScript.Beat(0, "duplicate"))));
    }
    @Test void readOnlyStatusCannotResetScenesAndBootConsumesExistingIntentWithoutReplayingIt() throws Exception {
        var campaign = preparing().cue(WorldBossCampaignTest.KEY, WorldBossCampaign.ENTRANCE, WorldBossCampaignTest.COMBAT - 15_000); var state = new WorldBossState(); state.campaign(campaign); state.scenes(entrance(campaign));
        WorldBossScenes.started(state);
        var field = WorldBossScenes.class.getDeclaredField("consumed"); field.setAccessible(true);
        try { var before = java.util.Set.copyOf((java.util.Set<?>) field.get(null)); WorldBossService.isActive(); assertEquals(before, field.get(null)); assertEquals(1, before.size()); }
        finally { WorldBossScenes.reset(); }
    }
    @Test void rootSevenMigrationDoesNotInventPastSceneTranscripts() {
        var tag = new WorldBossState().save(new CompoundTag(), null); tag.putInt("schema", 7); tag.remove("scenes"); tag.remove("travel");
        var migrated = WorldBossState.load(tag, null); assertFalse(migrated.isQuarantined()); assertTrue(migrated.scenes().receipts().isEmpty());
        var partial = new WorldBossState().save(new CompoundTag(), null); partial.remove("scenes"); assertTrue(WorldBossState.load(partial, null).isQuarantined());
    }
    @Test void laterAwakeningsDoNotHideEarlierRecordedChapters() {
        var campaign = WorldBossCampaignTest.open(); var event = campaign.require(WorldBossCampaignTest.KEY);
        campaign = campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.VICTORY, "", WorldBossCampaignTest.COMBAT + 1);
        var ledger = new WorldBossSceneLedger().record(event, WorldBossSceneScript.Kind.VICTORY, WorldBossCampaignTest.COMBAT + 1, "minecraft:overworld", 0, 64, 0, 32);
        var nextSlot = WorldBossSchedule.on(java.time.LocalDate.of(2026, 11, 17)).get(1); var key = WorldBossCampaign.key("season1", nextSlot);
        campaign = campaign.plan(nextSlot, nextSlot.preparationAt().toEpochMilli()); var next = campaign.require(key);
        campaign = campaign.transition(key, next.eventId(), WorldBossCampaign.Status.PREPARING, "", nextSlot.preparationAt().toEpochMilli())
                .transition(key, next.eventId(), WorldBossCampaign.Status.OPEN, "", nextSlot.combatAt().toEpochMilli())
                .transition(key, next.eventId(), WorldBossCampaign.Status.VICTORY, "", nextSlot.combatAt().toEpochMilli() + 1);
        ledger = ledger.record(next, WorldBossSceneScript.Kind.VICTORY, nextSlot.combatAt().toEpochMilli() + 1, "minecraft:overworld", 0, 64, 0, 32); ledger.validate(campaign);
        assertEquals(2, ledger.find(event.characterId(), WorldBossSceneScript.Kind.VICTORY, null).awakening());
        assertEquals("distortion_victory_first_v1", ledger.find(event.characterId(), WorldBossSceneScript.Kind.VICTORY, 1L).script().id());
        assertNull(ledger.find(event.characterId(), WorldBossSceneScript.Kind.VICTORY, 99L));
    }
}
