package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorldBossMeritStateTest {
    private WorldBossState relayActive() {
        var state = new WorldBossState(); state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, 100, 0, new WorldBossPool(1), true);
        state.clock(WorldBossClock.begin(0, 100));
        state.stageOpeningRules(WorldBossOpeningRules.capture(WorldBossConfig.defaults(), new WorldBossCalibration("cobbleraids:giratina", "a".repeat(64), BigDecimal.valueOf(100), "fixture")));
        state.configureWardPylon(new WorldBossWards.Pylon("minecraft:overworld", 8, 64, 8));
        state.configureWardRelay(new WorldBossRelay.Route(new WorldBossWards.Pylon("minecraft:overworld", 0, 64, 0), new WorldBossWards.Pylon("minecraft:overworld", 8, 64, 0)));
        state.freezeOpening(List.of(A, B, new UUID(0, 3), new UUID(0, 4), new UUID(0, 5))); state.clock(state.clock().advance(0, 100, true)); return state;
    }
    @Test void twoWardRoutesQualifyFiftyNovicesAndSettlementIncludesTheirOfflineReceipts() {
        var state = relayActive(); var duel = UUID.randomUUID(); state.merit().register(duel, A);
        state.damageAction(duel, A, 1, 1, 1200, 50);
        for (int i = 100; i < 150; i++) { var id = new UUID(0, i); assertTrue(state.relayWard(id, 1)); assertTrue(state.focusWard(id, 1)); }
        state.clock(state.clock().advance(45_000, 45_100, true)); state.wards().advance(state.clock().remainingMillis(), 50);
        state.damageAction(duel, A, 2, 2, 1400, 50);
        for (int i = 100; i < 150; i++) { var id = new UUID(0, i); assertTrue(state.relayWard(id, 2)); assertTrue(state.focusWard(id, 2)); }
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertFalse(restored.isQuarantined()); assertEquals(50, restored.merit().qualifiedPlayers().size());
        assertFalse(restored.relayWard(new UUID(0, 100), 2));
        assertEquals(151, restored.wards().active().units(), "Fifty capped relays plus the threshold-crossing combat turn");
        restored.prepareClose("CANCELLED", "service cancelled"); assertEquals(50, restored.rewardPlan().size());
        assertTrue(restored.rewardPlan().values().stream().allMatch(amount -> amount == 200));
    }
    @Test void partialRelayReceiptOrMissingRouteQuarantinesInsteadOfChangingQualification() {
        var state = relayActive(); var duel = UUID.randomUUID(); state.merit().register(duel, A); state.damageAction(duel, A, 1, 1, 1200, 5); assertTrue(state.relayWard(B, 1));
        var tag = state.save(new CompoundTag(), null); tag.getCompound("merit").getList("relays", 10).clear();
        assertTrue(WorldBossState.load(tag, null).isQuarantined());
        tag = state.save(new CompoundTag(), null); tag.getCompound("wards").remove("route"); assertTrue(WorldBossState.load(tag, null).isQuarantined());
    }
    @Test void decidedOrExpiredEventsCannotAcceptFurtherRelayCredit() {
        var state = relayActive(); var duel = UUID.randomUUID(); state.merit().register(duel, A); state.damageAction(duel, A, 1, 1, 1200, 5);
        state.prepareClose("CANCELLED", "service cancelled"); assertFalse(state.relayWard(B, 1)); assertEquals(0, state.merit().score(B));
        state = relayActive(); state.merit().register(duel, A); state.damageAction(duel, A, 1, 1, 1200, 5);
        state.clock(state.clock().advance(900_000, 900_100, true)); assertFalse(state.relayWard(B, 1));
    }
    @Test void completedFocusAndMeritPersistAsOneReceiptPairAndRejectPartialCorruption() {
        var state = active(); var duel = UUID.randomUUID(); state.merit().register(duel, A);
        state.damageAction(duel, A, 1, 1, 1200, 5);
        assertEquals(1, state.wards().active().serial()); assertTrue(state.focusWard(B, 1));
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertFalse(restored.isQuarantined()); assertEquals(20, restored.merit().score(B)); assertFalse(restored.focusWard(B, 1));
        var tag = state.save(new CompoundTag(), null); tag.getCompound("merit").getList("focuses", 10).clear();
        assertTrue(WorldBossState.load(tag, null).isQuarantined());
    }
    @Test void failedFinalClosureCanRetainTheSlotWithTheSameSecuredPromise() {
        var state = active(); qualify(state, A); state.prepareClose("CANCELLED", "cancelled");
        assertThrows(IllegalStateException.class, () -> state.keepSettlementOpen(0));
        state.markRewardsPaid(); long oldEnd = state.lastEndedAtMillis(); state.finish(1000); state.keepSettlementOpen(oldEnd);
        assertTrue(state.active()); assertEquals(oldEnd, state.lastEndedAtMillis());
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertFalse(restored.isQuarantined()); assertTrue(restored.active()); assertTrue(restored.rewardsPaid()); assertEquals(java.util.Map.of(A, 200), restored.rewardPlan());
    }
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    private WorldBossState active() {
        var state = new WorldBossState(); state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, 100, 0, new WorldBossPool(1), true);
        state.clock(WorldBossClock.begin(0, 100));
        state.stageOpeningRules(WorldBossOpeningRules.capture(WorldBossConfig.defaults(), new WorldBossCalibration("cobbleraids:giratina", "a".repeat(64), BigDecimal.valueOf(100), "fixture")));
        state.configureWardPylon(new WorldBossWards.Pylon("minecraft:overworld", 2, 64, 2));
        state.freezeOpening(List.of(A, B, new UUID(0, 3), new UUID(0, 4), new UUID(0, 5))); state.clock(state.clock().advance(0, 100, true)); return state;
    }
    private void qualify(WorldBossState state, UUID player) {
        for (int battle = 0; battle < 2; battle++) { var duel = UUID.randomUUID(); state.merit().register(duel, player); for (int turn = 1; turn <= 5; turn++) state.damageAction(duel, player, turn, turn, 1, 5); }
    }
    @Test void finalHitQualifiesBeforeOutcomeAndOldSequencesCannotDamageTwiceAfterReload() {
        var state = active(); var first = UUID.randomUUID(); var last = UUID.randomUUID(); state.merit().register(first, A); state.merit().register(last, A);
        for (int turn = 1; turn <= 5; turn++) state.damageAction(first, A, turn, turn, 1, 5);
        for (int turn = 1; turn <= 4; turn++) state.damageAction(last, A, turn, turn, 1, 5);
        var before = state.pool().fixedSnapshot();
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null); assertFalse(restored.isQuarantined());
        assertEquals(WorldBossMerit.Evidence.DUPLICATE, restored.damageAction(last, A, 4, 4, 1000, 5).evidence()); assertEquals(before, restored.pool().fixedSnapshot());
        var result = restored.damageAction(last, A, 5, 5, Float.MAX_VALUE, 5);
        assertTrue(result.qualifiedNow()); assertTrue(restored.merit().qualified(A)); assertEquals(0, restored.pool().current());
        assertTrue(restored.wards().snapshot().stopped());
    }
    @Test void qualificationSurvivesPauseAndTheFrozenTimeoutPromiseCannotBecomeAVictory() {
        var state = active(); qualify(state, A); state.pause(A);
        state.clock(state.clock().advance(900_000, 900_100, true)); state.prepareClose("TIMEOUT", "escaped");
        assertEquals(java.util.Map.of(A, 50), state.rewardPlan());
        var loaded = WorldBossState.load(state.save(new CompoundTag(), null), null); assertFalse(loaded.isQuarantined());
        assertTrue(loaded.active()); assertTrue(loaded.merit().qualified(A)); assertFalse(loaded.rewardsPaid());
        loaded.prepareClose("CANCELLED", "changed"); assertEquals("TIMEOUT", loaded.closeReason()); assertEquals(java.util.Map.of(A, 50), loaded.rewardPlan());
        var duel = UUID.randomUUID(); loaded.merit().register(duel, A);
        assertEquals(WorldBossMerit.Evidence.INVALID, loaded.damageAction(duel, A, 1, 1, Float.MAX_VALUE, 5).evidence());
    }
    @Test void qualifiedCancellationPaysEqualCompensationWithoutDamageRankMultipliers() {
        var state = active(); qualify(state, A); qualify(state, B);
        state.prepareClose("CANCELLED", "cancelled"); assertEquals(java.util.Map.of(A, 200, B, 200), state.rewardPlan());
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null); assertFalse(restored.isQuarantined()); assertEquals(state.rewardPlan(), restored.rewardPlan());
        assertFalse(restored.rewardsPaid());
    }
    @Test void aDamageLeaderWithoutEnoughMeaningfulTurnsDoesNotDisqualifyOtherPlayers() {
        var state = active(); qualify(state, A);
        var leader = UUID.randomUUID(); state.merit().register(leader, B); state.damageAction(leader, B, 1, 1, 3500, 5);
        assertTrue(state.pool().contributionOf(B) > state.pool().contributionOf(A)); assertFalse(state.merit().qualified(B));
        state.prepareClose("CANCELLED", "cancelled"); assertEquals(java.util.Map.of(A, 200), state.rewardPlan());
    }
    @Test void forgedRewardsOrMissingMeritAreQuarantinedRatherThanFallingBackToDamageShare() {
        var state = active(); qualify(state, A); state.prepareClose("CANCELLED", "cancelled");
        var tag = state.save(new CompoundTag(), null); tag.remove("merit"); assertTrue(WorldBossState.load(tag, null).isQuarantined());
        tag = state.save(new CompoundTag(), null); tag.getList("reward_plan", 10).getCompound(0).putInt("amount", 999);
        var restored = WorldBossState.load(tag, null); assertTrue(restored.isQuarantined()); assertEquals(tag, restored.save(new CompoundTag(), null));
    }
    @Test void testsRetainQualificationButNeverFreezeLiveMoney() {
        var state = active(); state.markTestMode(); qualify(state, A); state.prepareClose("CANCELLED", "cancelled");
        assertTrue(state.merit().qualified(A)); assertTrue(state.rewardPlan().isEmpty());
    }
}
