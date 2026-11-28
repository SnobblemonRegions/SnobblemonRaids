package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorldheartStateTest {
    private static final UUID PLAYER = new UUID(0, 1);
    @Test void newNaturalVictoryFreezesBlessingAndBonusOutboxInTheSameGeneration() {
        var state = won(); state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1);
        assertEquals(2, state.worldheartPolicy()); assertNotNull(state.collection().victory(state.bossId()).blessing());
        var raid = UUID.randomUUID(); state.benefits(state.benefits().promise(raid, WorldBossCampaignTest.COMBAT + 2, 199, java.util.Set.of(PLAYER), true));
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null); assertFalse(restored.isQuarantined());
        assertEquals(Map.of(PLAYER, 19), restored.benefits().bonuses().get(raid).amounts()); assertFalse(restored.benefits().bonuses().get(raid).paid());
        restored.benefits(restored.benefits().secured(raid));
        assertTrue(WorldBossState.load(restored.save(new CompoundTag(), null), null).benefits().bonuses().get(raid).paid());
    }
    @Test void existingRootSixFightsRetainTheirWorldheartOnlyPolicy() {
        var state = active(); var tag = state.save(new CompoundTag(), null); tag.putInt("schema", 6); tag.remove("benefits"); tag.remove("scenes"); tag.remove("travel"); tag.remove("admission"); tag.putInt("worldheart_policy", 1);
        var restored = WorldBossState.load(tag, null); assertFalse(restored.isQuarantined()); assertEquals(1, restored.worldheartPolicy());
        var duel = UUID.randomUUID(); restored.merit().register(duel, PLAYER); restored.damageAction(duel, PLAYER, 1, 1, Float.MAX_VALUE, 5); restored.pool().claimDefeat();
        restored.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1); assertNull(restored.collection().victory(restored.bossId()).blessing());
        assertFalse(WorldBossState.load(restored.save(new CompoundTag(), null), null).isQuarantined());
    }
    @Test void missingBenefitsAndOrphanedBonusPromisesQuarantineInsteadOfMintingMoney() {
        var state = won(); state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1);
        var tag = state.save(new CompoundTag(), null); tag.remove("benefits"); assertTrue(WorldBossState.load(tag, null).isQuarantined());
        assertThrows(IllegalArgumentException.class, () -> state.benefits(state.benefits().promise(UUID.randomUUID(), WorldBossCampaignTest.COMBAT - 1, 200, java.util.Set.of(PLAYER), true)));
    }
    @Test void outcomePreparationAtomicallyFreezesCampaignPointsAndCollectionWithoutAnIntermediatePromise() {
        var state = active(); var duel = UUID.randomUUID(); state.merit().register(duel, PLAYER); state.damageAction(duel, PLAYER, 1, 1, Float.MAX_VALUE, 5); state.pool().claimDefeat();
        var beforeCampaign = state.campaign(); var beforeCollection = state.collection().snapshot();
        assertThrows(IllegalArgumentException.class, () -> state.selectLegacy(PLAYER, null));
        assertThrows(IllegalStateException.class, () -> state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT - 1));
        assertEquals(beforeCampaign, state.campaign()); assertEquals(beforeCollection, state.collection().snapshot()); assertNull(state.rewardPlan());
        assertTrue(state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1));
        assertEquals(WorldBossCampaign.Status.VICTORY, state.campaignEvent().status()); assertEquals(Map.of(PLAYER, 200), state.rewardPlan());
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null); assertFalse(restored.isQuarantined());
        assertEquals(1, restored.collection().progress(PLAYER, restored.campaignEvent().characterId()).steps());
    }
    private WorldBossState active() {
        var campaign = WorldBossCampaignTest.planned(); var event = campaign.require(WorldBossCampaignTest.KEY);
        campaign = campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.PREPARING, "", WorldBossCampaignTest.PREP);
        var state = new WorldBossState(); state.campaign(campaign);
        state.begin(event.eventId(), event.definition(), "minecraft:overworld", 0, 64, 0, WorldBossCampaignTest.PREP, 0, new WorldBossPool(1), true);
        state.clock(WorldBossClock.begin(0, WorldBossCampaignTest.PREP));
        state.stageOpeningRules(WorldBossOpeningRules.capture(WorldBossConfig.defaults(), new WorldBossCalibration(event.definition(), "a".repeat(64), BigDecimal.valueOf(100), "fixture")));
        state.configureWardPylon(new WorldBossWards.Pylon("minecraft:overworld", 2, 64, 2)); state.bindOccurrence(event.key());
        state.freezeOpening(List.of(PLAYER, new UUID(0, 2), new UUID(0, 3), new UUID(0, 4), new UUID(0, 5)));
        state.clock(state.clock().advance(0, WorldBossCampaignTest.COMBAT, true));
        state.campaign(state.campaign().transition(event.key(), event.eventId(), WorldBossCampaign.Status.OPEN, "", WorldBossCampaignTest.COMBAT));
        for (int duelNumber = 0; duelNumber < 2; duelNumber++) {
            var duel = UUID.randomUUID(); state.merit().register(duel, PLAYER);
            for (int turn = 1; turn <= 5; turn++) state.damageAction(duel, PLAYER, turn, turn, 1, 5);
        }
        return state;
    }
    private WorldBossState won() {
        var state = active(); var duel = UUID.randomUUID(); state.merit().register(duel, PLAYER);
        state.damageAction(duel, PLAYER, 1, 1, Float.MAX_VALUE, 5); state.pool().claimDefeat();
        var event = state.campaignEvent(); state.campaign(state.campaign().transition(event.key(), event.eventId(), WorldBossCampaign.Status.VICTORY, "", WorldBossCampaignTest.COMBAT + 1));
        state.freezeRewardPlan(Map.of(PLAYER, 200)); return state;
    }
    @Test void grantAndItsFrozenRecipientsPersistBeforePointsCompleteAndRetryCannotReroll() {
        var state = won(); assertTrue(state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1));
        var before = state.collection().snapshot(); var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertFalse(restored.isQuarantined()); assertTrue(restored.active()); assertFalse(restored.rewardsPaid());
        assertFalse(restored.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 100)); assertEquals(before, restored.collection().snapshot());
        assertEquals(1, restored.collection().progress(PLAYER, restored.campaignEvent().characterId()).steps());
        restored.markRewardsPaid(); restored.finish(WorldBossCampaignTest.COMBAT + 2);
        assertFalse(WorldBossState.load(restored.save(new CompoundTag(), null), null).isQuarantined());
    }
    @Test void uncommittedProgressIsNotPublishedToTheCollectionMenu() {
        var state = won(); state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1); var character = state.campaignEvent().characterId();
        assertEquals(1, state.collection().progress(PLAYER, character).steps()); assertEquals(0, state.collectionView().progress(PLAYER, character).steps());
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null); assertEquals(1, restored.collectionView().progress(PLAYER, character).steps());
    }
    @Test void missingMandatoryGrantSecretOrRecipientDataQuarantinesTheCompletedOutcome() {
        var state = won(); state.markRewardsPaid();
        assertTrue(WorldBossState.load(state.save(new CompoundTag(), null), null).isQuarantined());
        state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1);
        var tag = state.save(new CompoundTag(), null); tag.getCompound("collection").remove("roll_secret");
        var restored = WorldBossState.load(tag, null); assertTrue(restored.isQuarantined()); assertEquals(tag, restored.save(new CompoundTag(), null));
        tag = state.save(new CompoundTag(), null); tag.getCompound("collection").getList("victories", 10).getCompound(0).getList("awards", 10).clear();
        assertTrue(WorldBossState.load(tag, null).isQuarantined());
    }
    @Test void oldFightsReadWithoutRetroactiveCollectionRewardsAndRemainReadableAfterMigration() {
        var state = active(); var tag = state.save(new CompoundTag(), null); tag.putInt("schema", 5); tag.remove("collection"); tag.remove("worldheart_policy"); tag.remove("benefits"); tag.remove("scenes"); tag.remove("travel"); tag.remove("admission");
        var restored = WorldBossState.load(tag, null); assertFalse(restored.isQuarantined()); assertEquals(0, restored.worldheartPolicy());
        assertFalse(restored.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1));
        var migrated = WorldBossState.load(restored.save(new CompoundTag(), null), null); assertFalse(migrated.isQuarantined()); assertEquals(0, migrated.worldheartPolicy());
        assertTrue(migrated.collection().snapshot().victories().isEmpty());
    }
    @Test void cancellationAndTimeoutNeverGrantVictoryCollectionProgress() {
        var state = active(); state.prepareClose("CANCELLED", "service cancelled");
        assertThrows(IllegalStateException.class, () -> state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1)); assertTrue(state.collection().snapshot().victories().isEmpty());
        var timeout = active(); timeout.clock(timeout.clock().advance(900_000, WorldBossCampaignTest.COMBAT + 900_000, true)); timeout.prepareClose("TIMEOUT", "escaped");
        assertThrows(IllegalStateException.class, () -> timeout.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 900_000));
    }
    @Test void archiveSelectionAndOwnershipSurviveStartingANewEvent() {
        var state = won(); state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1); state.markRewardsPaid(); state.finish(WorldBossCampaignTest.COMBAT + 2);
        var original = state.campaignEvent().characterId(); var next = new UUID(5, 1);
        state.campaign(state.campaign().stage(new WorldBossCampaign.Season("next", next, "Asterion", "cobbleraids:giratina", 0), WorldBossCampaignTest.COMBAT + 3).activate(WorldBossCampaignTest.COMBAT + 3));
        assertThrows(IllegalArgumentException.class, () -> state.selectLegacy(PLAYER, next)); assertThrows(IllegalArgumentException.class, () -> state.selectLegacy(PLAYER, UUID.randomUUID()));
        state.selectLegacy(PLAYER, original); var before = state.collection().snapshot();
        state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, WorldBossCampaignTest.COMBAT + 4, 0, new WorldBossPool(1), true);
        assertEquals(before, state.collection().snapshot()); assertEquals(0, state.worldheartPolicy());
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null); assertFalse(restored.isQuarantined()); assertEquals(before, restored.collection().snapshot());
    }
    @Test void unknownVersionsPartialDowngradeAndOrphanedHistoryArePreservedForRepair() {
        var state = won(); state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1);
        var tag = state.save(new CompoundTag(), null); tag.getCompound("collection").putInt("version", 99); assertTrue(WorldBossState.load(tag, null).isQuarantined());
        tag = state.save(new CompoundTag(), null); tag.putInt("schema", 5); assertTrue(WorldBossState.load(tag, null).isQuarantined());
        tag = state.save(new CompoundTag(), null); tag.getCompound("collection").getList("victories", 10).getCompound(0).putUUID("event", UUID.randomUUID());
        assertTrue(WorldBossState.load(tag, null).isQuarantined());
    }
}
