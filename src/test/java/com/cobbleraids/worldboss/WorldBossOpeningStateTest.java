package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorldBossOpeningStateTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    private WorldBossState preparing() {
        var state = new WorldBossState();
        state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, 100, 0, new WorldBossPool(1), true);
        state.clock(WorldBossClock.begin(300_000, 100));
        state.markTestMode();
        state.stageOpeningRules(WorldBossOpeningRules.capture(WorldBossConfig.defaults(), new WorldBossCalibration(
                "cobbleraids:giratina", "a".repeat(64), BigDecimal.valueOf(100), "fixture")));
        return state;
    }

    @Test void openingLocksOrderedRosterAndHealthOnceAndRecoveryNeverRecomputesThem() {
        var state = preparing();
        state.freezeOpening(List.of(B, A));
        state.clock(state.clock().advance(300_000, 300_100, true));
        assertEquals(List.of(B, A), state.openingRoster());
        assertEquals(1600, state.pool().max());
        state.pool().strike(A, 0.0009f);
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertFalse(restored.isQuarantined());
        assertEquals(state.openingRules(), restored.openingRules());
        assertEquals(state.openingRoster(), restored.openingRoster());
        assertEquals(state.pool().fixedSnapshot(), restored.pool().fixedSnapshot());
        restored.pool().strike(A, 0.0001f);
        assertEquals(1_599_999, restored.pool().fixedSnapshot().currentUnits());
        restored.clock(restored.clock().recover(300_200));
        assertThrows(IllegalStateException.class, () -> restored.freezeOpening(List.of(A)));
        restored.pool().growTo(1_000_000);
        assertEquals(1600, restored.pool().max());
    }

    @Test void stagedPreparationCanReloadBeforeTheRosterExists() {
        var state = preparing();
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null);
        assertFalse(restored.isQuarantined());
        assertNull(restored.openingRoster());
        assertEquals(state.openingRules(), restored.openingRules());
        assertFalse(restored.pool().isFixed());
    }

    @Test void unfrozenActiveStatesAndCorruptFixedLedgersAreQuarantined() {
        var state = preparing();
        state.clock(state.clock().advance(300_000, 300_100, true));
        assertTrue(WorldBossState.load(state.save(new CompoundTag(), null), null).isQuarantined());
        state = preparing();
        state.freezeOpening(List.of(A, B));
        var broken = state.save(new CompoundTag(), null);
        broken.getCompound("fixed_health").putLong("current_units", 0);
        var restored = WorldBossState.load(broken, null);
        assertTrue(restored.isQuarantined());
        assertEquals(broken, restored.save(new CompoundTag(), null));
        broken = state.save(new CompoundTag(), null);
        broken.remove("opening_roster");
        assertTrue(WorldBossState.load(broken, null).isQuarantined());
    }

    @Test void invalidRosterIsRejectedAndANewEventClearsOpeningData() {
        var state = preparing();
        assertThrows(IllegalArgumentException.class, () -> state.freezeOpening(List.of(A, A)));
        assertThrows(IllegalArgumentException.class, () -> state.freezeOpening(List.of()));
        state.freezeOpening(List.of(A));
        state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, 1000, 0, new WorldBossPool(1), true);
        assertNull(state.openingRules());
        assertNull(state.openingRoster());
        assertFalse(state.pool().isFixed());
    }

    @Test void liveOpeningsRequireFiveReadyPlayersAndCannotBeInventedAfterCombatBegan() {
        var state = preparing();
        var tag = state.save(new CompoundTag(), null);
        tag.putBoolean("test_mode", false);
        var live = WorldBossState.load(tag, null);
        assertFalse(live.isQuarantined());
        assertThrows(IllegalArgumentException.class, () -> live.freezeOpening(List.of(A, B)));
        live.freezeOpening(List.of(A, B, new UUID(0, 3), new UUID(0, 4), new UUID(0, 5)));
        assertEquals(4000, live.pool().max());
        var unfrozen = preparing();
        unfrozen.clock(unfrozen.clock().advance(300_000, 300_100, true));
        assertThrows(IllegalStateException.class, () -> unfrozen.freezeOpening(List.of(A)));
    }

    @Test void missingTestFlagCannotSilentlyConvertAnEncounterToLive() {
        var tag = preparing().save(new CompoundTag(), null);
        tag.remove("test_mode");
        assertTrue(WorldBossState.load(tag, null).isQuarantined());
    }
}
