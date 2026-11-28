package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorldBossSettlementStateTest {
    @Test void clockRoundTripRetainsRecoveryBudgetAndLegacyRecordsKeepTheirOldDeadline() {
        WorldBossState state = new WorldBossState();
        state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0,
                100, 10000, new WorldBossPool(100), true);
        assertNull(WorldBossState.load(state.save(new CompoundTag(), null), null).clock());
        var clock = WorldBossClock.begin(0, 100).advance(0, 100, true).advance(42_000, 42_100, true).recover(50_000);
        state.clock(clock);
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertEquals(clock, restored.clock());
        assertEquals(100, restored.pool().current());
        restored.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0,
                100, 10000, new WorldBossPool(100), true);
        assertNull(restored.clock());
    }

    @Test void malformedClockIsQuarantinedRatherThanFallingBackToUnlimitedCombat() {
        WorldBossState state = new WorldBossState();
        state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0,
                100, 0, new WorldBossPool(100), true);
        state.clock(WorldBossClock.begin(0, 100));
        var tag = state.save(new CompoundTag(), null);
        tag.getCompound("event_clock").remove("remaining_ms");
        var restored = WorldBossState.load(tag, null);
        assertTrue(restored.isQuarantined());
        assertEquals(tag, restored.save(new CompoundTag(), null));
    }
    @Test void malformedActiveStateIsQuarantinedAndPreservedInsteadOfStartingFresh() {
        CompoundTag broken = new CompoundTag();
        broken.putBoolean("active", true);
        broken.putString("operator_note", "preserve this evidence");
        WorldBossState state = WorldBossState.load(broken, null);
        assertTrue(state.isQuarantined());
        assertEquals(broken, state.save(new CompoundTag(), null));
        assertThrows(IllegalStateException.class, () -> state.begin(UUID.randomUUID(), "cobbleraids:giratina",
                "minecraft:overworld", 0, 64, 0, 0, 1000, new WorldBossPool(100), true));
    }

    @Test void frozenPromiseAndPauseSurviveReloadWithoutMarkingPayoutCompleted() {
        UUID event = UUID.randomUUID(), player = UUID.randomUUID();
        WorldBossState state = new WorldBossState();
        state.begin(event, "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0,
                100, 10000, new WorldBossPool(100), true);
        state.pause(player);
        state.markTestMode();
        state.freezeRewardPlan(Map.of(player, 200));
        WorldBossState restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertEquals(Map.of(player, 200), restored.rewardPlan());
        assertTrue(restored.isPaused(player));
        assertTrue(restored.testMode());
        assertFalse(restored.rewardsPaid());
        assertThrows(IllegalStateException.class, () -> restored.freezeRewardPlan(Map.of(player, 999)));
        restored.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0,
                200, 10000, new WorldBossPool(100), true);
        assertFalse(restored.isPaused(player));
        assertFalse(restored.testMode());
        assertNull(restored.rewardPlan());
    }
}
