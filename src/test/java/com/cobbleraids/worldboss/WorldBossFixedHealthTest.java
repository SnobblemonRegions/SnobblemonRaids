package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossFixedHealthTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

    @Test void tinyHitsStillReduceLargePoolsWhereFloatSubtractionWouldLoseThem() {
        var health = new WorldBossFixedHealth(1_000_000);
        assertEquals(1, health.strike(A, 0.001f));
        assertEquals(999_999_999, health.currentUnits());
        assertEquals(1, health.contribution(A));
    }

    @Test void everyOpeningWholeHpValueNearTheTechnicalLimitProjectsExactly() {
        for (long hp = WorldBossFixedHealth.MAX_WHOLE_HP - 1000; hp <= WorldBossFixedHealth.MAX_WHOLE_HP; hp++) {
            var pool = WorldBossPool.fixed(hp);
            assertEquals(hp, (long) pool.max());
            assertEquals(pool.max(), pool.current());
        }
    }

    @Test void subUnitDamageCarriesAcrossHitsAndSnapshotReload() {
        var health = new WorldBossFixedHealth(10);
        for (int i = 0; i < 9; i++) assertEquals(0, health.strike(A, 0.0001f));
        health = new WorldBossFixedHealth(health.snapshot());
        assertEquals(1, health.strike(A, 0.0001f));
        for (int i = 0; i < 9990; i++) health.strike(A, 0.0001f);
        assertEquals(1000, health.contribution(A));
        assertEquals(9000, health.currentUnits());
        assertTrue(health.snapshot().fractionalUnits().isEmpty());
    }

    @Test void carriesBelongToTheirPlayerAndOverkillCannotInflateCredit() {
        var health = new WorldBossFixedHealth(1);
        health.strike(A, 0.0005f);
        health.strike(B, 0.0005f);
        assertEquals(1000, health.currentUnits());
        health.strike(A, 0.0005f);
        assertEquals(1, health.contribution(A));
        assertEquals(999, health.strike(B, Float.MAX_VALUE));
        assertEquals(999, health.contribution(B));
        assertEquals(0, health.strike(A, 100));
    }

    @Test void fixedPoolsCannotGrowAndDefeatIsClaimedExactlyOnce() {
        var pool = WorldBossPool.fixed(10);
        pool.growTo(1_000_000);
        assertEquals(10, pool.max());
        assertEquals(10, pool.strike(A, 50));
        assertEquals(1, pool.shareOf(A));
        assertEquals(10, pool.totalDamage());
        assertTrue(pool.claimDefeat());
        assertFalse(pool.claimDefeat());
        assertEquals(0, pool.strike(B, 10));
        var restored = new WorldBossPool(pool.fixedSnapshot(), true);
        assertTrue(restored.isDefeated());
        assertEquals(0, restored.current());
    }

    @Test void nonfiniteAndInvalidHitsNeverChangeTheLedger() {
        var health = new WorldBossFixedHealth(10);
        var before = health.snapshot();
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0, -1}) health.strike(A, bad);
        health.strike(null, 1);
        assertEquals(before, health.snapshot());
    }

    @Test void invalidOrUnreconciledSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossFixedHealth(0));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossFixedHealth(WorldBossFixedHealth.MAX_WHOLE_HP + 1));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossFixedHealth.Snapshot(1000, 999, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossFixedHealth.Snapshot(1000, 1000, Map.of(), Map.of(A, BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossFixedHealth.Snapshot(1000, 1000, Map.of(), Map.of(A, new BigDecimal("1e-100"))));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossPool(new WorldBossFixedHealth(1).snapshot(), true));
    }

    @Test void randomizedMultiPlayerHitsAlwaysReconcileExactlyIncludingReloads() {
        var random = new Random(0xB055);
        var health = new WorldBossFixedHealth(20_000);
        for (int i = 0; i < 20_000; i++) {
            health.strike(new UUID(0, random.nextInt(50)), (1 + random.nextInt(10_000)) / 10_000f);
            if (i % 100 == 0) health = new WorldBossFixedHealth(health.snapshot());
            var snapshot = health.snapshot();
            assertEquals(snapshot.maxUnits(), snapshot.currentUnits() + snapshot.damageUnits().values().stream().mapToLong(Long::longValue).sum());
        }
    }
}
