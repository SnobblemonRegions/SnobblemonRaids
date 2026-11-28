package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorldBossCampaignStateTest {
    private WorldBossState preparing() {
        var campaign = WorldBossCampaignTest.planned();
        var event = campaign.require(WorldBossCampaignTest.KEY);
        campaign = campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.PREPARING, "", WorldBossCampaignTest.PREP);
        var state = new WorldBossState();
        state.campaign(campaign);
        state.begin(event.eventId(), event.definition(), "minecraft:overworld", 0, 64, 0, WorldBossCampaignTest.PREP, 0, new WorldBossPool(1), true);
        state.clock(WorldBossClock.begin(300_000, WorldBossCampaignTest.PREP));
        state.stageOpeningRules(WorldBossOpeningRules.capture(WorldBossConfig.defaults(), new WorldBossCalibration(event.definition(), "a".repeat(64), BigDecimal.valueOf(100), "fixture")));
        state.configureWardPylon(new WorldBossWards.Pylon("minecraft:overworld", 2, 64, 2));
        state.bindOccurrence(event.key());
        return state;
    }

    @Test void stagedCampaignSurvivesAnEmptyEventStoreAndCorruptionIsPreserved() {
        var state = new WorldBossState();
        state.campaign(WorldBossCampaign.empty().stage(new WorldBossCampaign.Season("next", UUID.randomUUID(), "Vaelrath", "cobbleraids:giratina", 0), WorldBossCampaignTest.PREP));
        var tag = state.save(new CompoundTag(), null);
        assertEquals(9, tag.getInt("schema"));
        var restored = WorldBossState.load(tag, null);
        assertFalse(restored.isQuarantined());
        assertEquals(state.campaign(), restored.campaign());
        assertFalse(restored.active());
        tag.getCompound("campaign").remove("wall_high_water");
        restored = WorldBossState.load(tag, null);
        assertTrue(restored.isQuarantined());
        assertEquals(tag, restored.save(new CompoundTag(), null));
    }

    @Test void eventAndOccurrenceCommitTogetherAndNaturalWinReplaysWithoutAdvancingTwice() {
        var state = preparing();
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertFalse(restored.isQuarantined());
        assertEquals(state.campaignEvent(), restored.campaignEvent());
        restored.freezeOpening(List.of(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3), new UUID(0, 4), new UUID(0, 5)));
        restored.clock(restored.clock().advance(300_000, WorldBossCampaignTest.COMBAT, true));
        var event = restored.campaignEvent();
        restored.campaign(restored.campaign().transition(event.key(), event.eventId(), WorldBossCampaign.Status.OPEN, "", WorldBossCampaignTest.COMBAT));
        var duel = UUID.randomUUID(); restored.merit().register(duel, new UUID(0, 1));
        restored.damageAction(duel, new UUID(0, 1), 1, 1, Float.MAX_VALUE, 5);
        restored.pool().claimDefeat();
        restored.campaign(restored.campaign().transition(event.key(), event.eventId(), WorldBossCampaign.Status.VICTORY, "", WorldBossCampaignTest.COMBAT + 1));
        var won = WorldBossState.load(restored.save(new CompoundTag(), null), null);
        assertFalse(won.isQuarantined());
        assertTrue(won.active(), "Unsettled outcome must continue reserving the server slot");
        assertFalse(won.rewardsPaid());
        assertEquals(2, won.campaign().active().awakening());
        var replay = won.campaign().transition(event.key(), event.eventId(), WorldBossCampaign.Status.VICTORY, "", WorldBossCampaignTest.COMBAT + 2);
        assertEquals(1, replay.active().victories());
        won.freezeRewardPlan(java.util.Map.of()); won.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 2); won.markRewardsPaid(); won.finish(WorldBossCampaignTest.COMBAT + 2);
        assertFalse(WorldBossState.load(won.save(new CompoundTag(), null), null).isQuarantined());
    }

    @Test void missingEventLinkOrWrongOwnerCannotLaunchOrSettleAnOccurrence() {
        var state = preparing();
        var tag = state.save(new CompoundTag(), null);
        tag.remove("event_occurrence");
        assertTrue(WorldBossState.load(tag, null).isQuarantined());
        tag = state.save(new CompoundTag(), null);
        tag.putUUID("boss", UUID.randomUUID());
        assertTrue(WorldBossState.load(tag, null).isQuarantined());
        var empty = new WorldBossState(); empty.campaign(WorldBossCampaignTest.open());
        assertTrue(WorldBossState.load(empty.save(new CompoundTag(), null), null).isQuarantined());
    }

    @Test void interruptedReservationBeforeBeginIsRepresentableButCannotPretendToBeOpen() {
        var campaign = WorldBossCampaignTest.planned(); var event = campaign.require(WorldBossCampaignTest.KEY);
        campaign = campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.PREPARING, "", WorldBossCampaignTest.PREP);
        var state = new WorldBossState(); state.campaign(campaign);
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertFalse(restored.isQuarantined());
        assertFalse(restored.active());
        assertEquals(WorldBossCampaign.Status.PREPARING, restored.campaign().require(event.key()).status());
    }
}
