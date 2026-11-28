package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossMeritTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), DUEL = new UUID(0, 3);
    @Test void multiHitDamageEarnsOnlyOneTenPointAwardPerTurnAndFiftyPerDuel() {
        var merit = new WorldBossMerit(); merit.register(DUEL, A);
        merit.damaged(DUEL, A, 1, 1); merit.damaged(DUEL, A, 1, 2);
        assertEquals(10, merit.score(A));
        for (int turn = 2; turn <= 20; turn++) merit.damaged(DUEL, A, turn, turn + 1);
        assertEquals(50, merit.score(A)); assertFalse(merit.qualified(A));
    }
    @Test void losingOrIncompleteDuelsKeepMeritAndQualificationNeedsNoEndCallback() {
        var merit = new WorldBossMerit();
        for (int duel = 0; duel < 2; duel++) {
            var id = new UUID(1, duel); merit.register(id, A);
            for (int turn = 1; turn <= 5; turn++) merit.damaged(id, A, turn, turn);
        }
        assertTrue(merit.qualified(A)); assertEquals(java.util.Set.of(A), merit.qualifiedPlayers());
        assertFalse(merit.qualified(B));
    }
    @Test void duplicateOldWrongOwnerAndRegressedTurnEvidenceCannotScore() {
        var merit = new WorldBossMerit(); merit.register(DUEL, A); merit.damaged(DUEL, A, 3, 5);
        assertEquals(WorldBossMerit.Evidence.DUPLICATE, merit.inspect(DUEL, A, 3, 5));
        assertEquals(WorldBossMerit.Evidence.DUPLICATE, merit.inspect(DUEL, A, 1, 1));
        assertEquals(WorldBossMerit.Evidence.INVALID, merit.inspect(DUEL, A, 2, 6));
        assertEquals(WorldBossMerit.Evidence.INVALID, merit.inspect(DUEL, B, 4, 6));
        assertEquals(WorldBossMerit.Evidence.INVALID, merit.inspect(UUID.randomUUID(), A, 4, 6));
        assertThrows(IllegalStateException.class, () -> merit.register(DUEL, B));
    }
    @Test void entryDamageIsTrackedForReplayButDoesNotInventAMeaningfulTurn() {
        var merit = new WorldBossMerit(); merit.register(DUEL, A); merit.damaged(DUEL, A, 0, 1);
        assertEquals(0, merit.score(A)); assertEquals(WorldBossMerit.Evidence.DUPLICATE, merit.inspect(DUEL, A, 0, 1));
        merit.damaged(DUEL, A, 1, 2); assertEquals(10, merit.score(A));
    }
    @Test void focusAwardsOncePerWindowAndCombinesWithCombatAfterReload() {
        var merit = new WorldBossMerit(); merit.register(DUEL, A);
        for (int i = 1; i <= 5; i++) merit.damaged(DUEL, A, i, i);
        assertTrue(merit.focused(A, 1)); assertFalse(merit.focused(A, 1)); assertTrue(merit.focused(A, 2));
        var restored = WorldBossContributionCodec.merit(WorldBossContributionCodec.merit(merit));
        assertEquals(90, restored.score(A));
        var another = UUID.randomUUID(); restored.register(another, A); restored.damaged(another, A, 1, 1);
        assertTrue(restored.qualified(A)); assertEquals(200, restored.victoryReward()); assertEquals(50, restored.timeoutReward()); assertEquals(200, restored.cancellationReward());
    }
    @Test void invalidEvidenceAndForgedDuelCountersAreRejected() {
        var merit = new WorldBossMerit(); merit.register(DUEL, A);
        assertEquals(WorldBossMerit.Evidence.INVALID, merit.inspect(DUEL, A, 1, 0));
        assertEquals(WorldBossMerit.Evidence.INVALID, merit.inspect(DUEL, A, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossMerit.Duel(A, 1, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossMerit.Focus(A, 3));
        var tag = WorldBossContributionCodec.merit(merit); tag.remove("victory");
        assertThrows(IllegalArgumentException.class, () -> WorldBossContributionCodec.merit(tag));
    }
}
