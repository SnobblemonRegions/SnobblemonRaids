package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossWardsTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), DUEL = new UUID(0, 3);
    @Test void simultaneousThresholdCrossingsQueueTwoNonOverlappingWindows() {
        var wards = new WorldBossWards(); wards.damaged(100_000, 34_000, 800_000, 30);
        assertEquals(1, wards.active().serial()); assertEquals(3, wards.active().quorum());
        assertEquals(755_000, wards.active().endRemainingMillis());
        wards.advance(755_001, 50); assertEquals(1, wards.active().serial());
        wards.advance(755_000, 50); assertEquals(2, wards.active().serial());
        assertEquals(710_000, wards.active().endRemainingMillis()); assertEquals(5, wards.active().quorum());
        assertEquals(WorldBossWards.Status.FAILED, wards.snapshot().completed().getFirst().status());
    }
    @Test void chargeRequiresTheFrozenQuorumAndCapsEachPersonsUnits() {
        var wards = new WorldBossWards(); wards.damaged(100_000, 70_000, 800_000, 30);
        for (int person = 0; person < 3; person++) for (int turn = 1; turn <= 5; turn++) wards.combat(new UUID(1, person), new UUID(0, person), turn);
        assertEquals(9, wards.active().units()); assertEquals(3, wards.active().people()); assertTrue(wards.active().goalMet());
        assertEquals(WorldBossWards.Status.ACTIVE, wards.active().status(), "Keep the window available so others can earn their legitimate focus");
        wards.advance(755_000, 0); assertNull(wards.active()); assertEquals(WorldBossWards.Status.SUCCESS, wards.snapshot().completed().getFirst().status());
    }
    @Test void duplicateTurnsAndFocusCannotIncreaseChargeRepeatedly() {
        var wards = new WorldBossWards(); wards.damaged(100_000, 70_000, 800_000, 5);
        wards.combat(DUEL, A, 1); wards.combat(DUEL, A, 1); assertEquals(1, wards.active().units());
        wards.combat(DUEL, A, 2); wards.combat(DUEL, A, 3);
        assertTrue(wards.focus(A, 1, 790_000)); assertFalse(wards.focus(A, 1, 790_000));
        assertEquals(3, wards.active().units()); assertEquals(java.util.Set.of(A), wards.active().focuses());
        assertFalse(wards.focus(B, 2, 790_000)); assertFalse(wards.focus(B, 1, 755_000));
    }
    @Test void recoveryAndReloadDoNotConsumeWindowTimeOrResizeItsGoal() {
        var wards = new WorldBossWards(); wards.damaged(100_000, 70_000, 800_000, 10);
        wards.combat(DUEL, A, 1);
        var restored = WorldBossContributionCodec.wards(WorldBossContributionCodec.wards(wards));
        assertEquals(wards.snapshot(), restored.snapshot());
        restored.advance(800_000, 50); assertEquals(1, restored.active().quorum());
        assertEquals(755_000, restored.active().endRemainingMillis());
    }
    @Test void victoryCancelsOptionalWindowsAndNeverCreatesQueuedWorkAfterward() {
        var wards = new WorldBossWards(); wards.damaged(100_000, 30_000, 800_000, 50);
        wards.stop(); wards.advance(600_000, 50);
        assertNull(wards.active()); assertTrue(wards.snapshot().stopped());
        assertEquals(1, wards.snapshot().completed().size()); assertEquals(WorldBossWards.Status.CANCELLED, wards.snapshot().completed().getFirst().status());
    }
    @Test void invalidWardReceiptsAndSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossWards.Window(1, 1, 10, WorldBossWards.Status.SUCCESS, java.util.Set.of(), java.util.Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossWards.Snapshot(null, 2, false, java.util.List.of(), null));
        var wards = new WorldBossWards(); assertThrows(IllegalArgumentException.class, () -> wards.advance(900_000, 51));
        var tag = WorldBossContributionCodec.wards(wards); tag.putInt("version", 99);
        assertThrows(IllegalArgumentException.class, () -> WorldBossContributionCodec.wards(tag));
    }
}
