package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class WorldBossAudienceStateTest {
    private static final UUID PLAYER = new UUID(2, 1);
    private static WorldBossState event() {
        var state = new WorldBossState(); state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0.5, 64, 0.5, 100, 0, new WorldBossPool(100), true); return state;
    }
    @Test void watchIntentAndFrozenRadiusSurviveRestartAndExplicitResumeClearsOnlyRoleControl() {
        var state = event(); state.configureAudience(96); state.watch(PLAYER);
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null); assertFalse(restored.isQuarantined());
        assertTrue(restored.observing(PLAYER)); assertTrue(restored.isPaused(PLAYER)); assertEquals(96.0, restored.audienceRadius());
        var json = WorldBossConfig.defaults().toJson(); json.getAsJsonObject("arena").addProperty("radius", 128);
        assertEquals(96, WorldBossRules.zoneRadius(restored, WorldBossConfig.fromJson(json)));
        assertTrue(restored.resume(PLAYER)); assertFalse(restored.observing(PLAYER)); assertFalse(restored.isPaused(PLAYER));
    }
    @Test void newOccurrenceClearsWatchChoiceWhileLegacyRecordsDoNotInventAudienceGeometry() {
        var state = event(); state.pause(PLAYER);
        var legacy = WorldBossState.load(state.save(new CompoundTag(), null), null); assertNull(legacy.audienceRadius()); assertFalse(legacy.observing(PLAYER)); assertTrue(legacy.isPaused(PLAYER));
        state.configureAudience(96); state.watch(PLAYER); state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0.5, 64, 0.5, 200, 0, new WorldBossPool(1), true);
        assertNull(state.audienceRadius()); assertFalse(state.observing(PLAYER)); assertFalse(state.isPaused(PLAYER));
    }
    @Test void malformedOrUnpausedObserverDataQuarantinesRawEvidence() {
        var state = event(); state.configureAudience(96); state.watch(PLAYER); var original = state.save(new CompoundTag(), null);
        for (int mutation = 0; mutation < 5; mutation++) {
            var tag = original.copy(); var audience = tag.getCompound("audience");
            if (mutation == 0) audience.remove("radius");
            if (mutation == 1) audience.putInt("version", 2);
            if (mutation == 2) audience.getList("watching", 10).add(audience.getList("watching", 10).getCompound(0).copy());
            if (mutation == 3) tag.put("paused", new ListTag());
            if (mutation == 4) audience.putDouble("radius", Double.NaN);
            var invalid = WorldBossState.load(tag, null); assertTrue(invalid.isQuarantined()); assertEquals(tag, invalid.save(new CompoundTag(), null));
        }
        var orphan = new WorldBossState().save(new CompoundTag(), null); orphan.put("audience", original.getCompound("audience")); assertTrue(WorldBossState.load(orphan, null).isQuarantined());
    }
    @Test void watchingKeepsExistingContributionAndFrozenRewardPromises() {
        var state = event(); state.markTestMode(); state.pool().strike(PLAYER, 25); state.freezeRewardPlan(Map.of(PLAYER, 200)); state.configureAudience(96); state.watch(PLAYER);
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null); assertFalse(restored.isQuarantined());
        assertEquals(25, restored.pool().contributions().get(PLAYER)); assertEquals(Map.of(PLAYER, 200), restored.rewardPlan()); assertFalse(restored.rewardsPaid()); assertTrue(restored.observing(PLAYER));
    }
    @Test void finaleRadiusKeepsTerraceViewersInsideItsFrozenReceiptAfterTheEventCloses() {
        var state = event(); state.configureAudience(96); state.watch(PLAYER);
        assertEquals(112, state.sceneRadius());
        var campaign = WorldBossCampaignTest.open(); var occurrence = campaign.require(WorldBossCampaignTest.KEY);
        campaign = campaign.transition(occurrence.key(), occurrence.eventId(), WorldBossCampaign.Status.VICTORY, "", WorldBossCampaignTest.COMBAT + 1);
        var ledger = new WorldBossSceneLedger().record(campaign.require(occurrence.key()), WorldBossSceneScript.Kind.VICTORY,
                WorldBossCampaignTest.COMBAT + 1, "minecraft:overworld", 0.5, 64, 0.5, state.sceneRadius());
        var receipt = ledger.receipts().get(new WorldBossSceneLedger.Key(occurrence.eventId(), WorldBossSceneScript.Kind.VICTORY));
        state.finish(200); assertEquals(112, state.sceneRadius());
        assertTrue(receipt.inside("minecraft:overworld", 104.5, 64, 0.5));
        assertFalse(receipt.inside("minecraft:overworld", 113, 64, 0.5));
    }
}
