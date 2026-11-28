package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldheartCollectionTest {
    private static final UUID PLAYER = new UUID(0, 1), BOSS = new UUID(0, 2), OLD = new UUID(0, 3), OTHER = new UUID(0, 4);
    private WorldheartCollection empty() { return new WorldheartCollection(new WorldheartCollection.Snapshot("ab".repeat(32), List.of(), Map.of())); }
    private UUID event(int n) { return new UUID(1, n); }
    @Test void aFiftyRecipientGrantRoundTripsOnceWithoutDependingOnInventoriesOrOnlinePlayers() {
        var players = new java.util.LinkedHashSet<UUID>(); for (int i = 0; i < 50; i++) players.add(new UUID(7, i));
        var collection = empty().grant(event(1), BOSS, 1, 1, players);
        var restored = WorldheartCollectionCodec.read(WorldheartCollectionCodec.write(collection));
        assertSame(restored, restored.grant(event(1), BOSS, 1, 999, players));
        for (var player : players) assertEquals(1, restored.progress(player, BOSS).steps());
        assertEquals(50, restored.victory(event(1)).awards().size()); assertEquals(1, restored.snapshot().victories().size());
    }
    @Test void baseIsGuaranteedAtTwelveAndStylesContinueAtTwentyFourAndForty() {
        var collection = empty();
        for (int win = 1; win <= 40; win++) {
            collection = collection.grant(event(win), BOSS, win, win, Set.of(PLAYER));
            var progress = collection.progress(PLAYER, BOSS); assertEquals(win, progress.steps());
            if (win < 12) assertFalse(progress.owned(), "This fixed-seed fixture has no early unlock: exercise the guaranteed boundary");
            if (win == 12) { assertTrue(progress.owned()); assertEquals(0, progress.style()); }
            if (win == 24) assertEquals(1, progress.style()); if (win == 40) { assertEquals(2, progress.style()); assertEquals(0, progress.nextMilestone()); }
            if (win >= 12) assertEquals(-1, collection.victory(event(win)).awards().get(PLAYER).roll());
        }
    }
    @Test void earlyUnlockUsesOneStablePrivateRollAndStopsRollingOnceOwned() {
        var collection = empty(); UUID early = null;
        for (int attempt = 1; attempt <= 10_000; attempt++) {
            var candidate = collection.grant(event(attempt), BOSS, 1, 1, Set.of(PLAYER));
            if (candidate.progress(PLAYER, BOSS).owned()) { collection = candidate; early = event(attempt); break; }
        }
        assertNotNull(early); var roll = collection.victory(early).awards().get(PLAYER).roll(); assertTrue(roll >= 0 && roll < 200);
        var restored = WorldheartCollectionCodec.read(WorldheartCollectionCodec.write(collection));
        assertSame(restored, restored.grant(early, BOSS, 1, 1000, Set.of(PLAYER))); assertEquals(roll, restored.victory(early).awards().get(PLAYER).roll());
        var after = restored.grant(new UUID(9, 1), BOSS, 2, 2, Set.of(PLAYER)); assertEquals(-1, after.victory(new UUID(9, 1)).awards().get(PLAYER).roll());
        assertEquals(restored.progress(PLAYER, BOSS).unlock(), after.progress(PLAYER, BOSS).unlock());
    }
    @Test void archivedProjectHasNoEarlyRollAndNeverRedirectsAnAlreadyFrozenWin() {
        var collection = empty().select(PLAYER, OLD).grant(event(1), BOSS, 1, 1, Set.of(PLAYER));
        assertEquals(1, collection.progress(PLAYER, OLD).steps()); assertFalse(collection.progress(PLAYER, OLD).owned());
        collection = collection.select(PLAYER, OTHER); var before = collection;
        assertSame(before, collection.grant(event(1), BOSS, 1, 1000, Set.of(PLAYER)));
        assertEquals(0, collection.progress(PLAYER, OTHER).steps()); assertEquals(OLD, collection.victory(event(1)).awards().get(PLAYER).legacyCharacter());
        collection = collection.grant(event(2), BOSS, 2, 2, Set.of(PLAYER)); assertEquals(1, collection.progress(PLAYER, OTHER).steps());
        collection = collection.select(PLAYER, OLD);
        for (int win = 3; win <= 41; win++) {
            collection = collection.grant(event(win), BOSS, win, win, Set.of(PLAYER));
            if (win < 13) assertFalse(collection.progress(PLAYER, OLD).owned());
        }
        assertEquals(40, collection.progress(PLAYER, OLD).steps()); assertEquals(2, collection.progress(PLAYER, OLD).style());
        assertTrue(collection.progress(PLAYER, OLD).unlock().legacy()); assertFalse(collection.progress(PLAYER, OLD).unlock().early());
    }
    @Test void recipientsAreOfflineUuidAccountsAndAnotherTrainerCannotReceiveTheirProgress() {
        var collection = empty().grant(event(1), BOSS, 1, 1, Set.of(PLAYER));
        assertEquals(1, collection.progress(PLAYER, BOSS).steps()); assertEquals(0, collection.progress(OTHER, BOSS).steps());
        assertThrows(IllegalStateException.class, () -> collection.grant(event(1), BOSS, 1, 2, Set.of(OTHER)));
        assertThrows(IllegalStateException.class, () -> collection.grant(event(1), OLD, 1, 2, Set.of(PLAYER)));
        assertThrows(IllegalStateException.class, () -> collection.grant(event(1), BOSS, 2, 2, Set.of(PLAYER)));
    }
    @Test void clearingAndReloadingPreservesAllEarnedEntitlementsAndSelections() {
        var collection = empty().select(PLAYER, OLD).grant(event(1), BOSS, 1, 1, Set.of(PLAYER));
        var snapshot = collection.snapshot(); var restored = WorldheartCollectionCodec.read(WorldheartCollectionCodec.write(collection));
        assertEquals(snapshot, restored.snapshot()); assertEquals(OLD, restored.selectedLegacy(PLAYER));
        restored = restored.select(PLAYER, null); assertNull(restored.selectedLegacy(PLAYER));
        assertEquals(1, restored.progress(PLAYER, OLD).steps()); assertEquals(snapshot.victories(), restored.snapshot().victories());
    }
    @Test void missingDataDuplicateGrantsRecipientsAndForgedRollsAreRejected() {
        var collection = empty().grant(event(1), BOSS, 1, 1, Set.of(PLAYER));
        var tag = WorldheartCollectionCodec.write(collection); tag.remove("roll_secret"); var missing = tag; assertThrows(IllegalArgumentException.class, () -> WorldheartCollectionCodec.read(missing));
        tag = WorldheartCollectionCodec.write(collection); tag.getList("victories", 10).add(tag.getList("victories", 10).getCompound(0).copy()); var duplicate = tag;
        assertThrows(IllegalArgumentException.class, () -> WorldheartCollectionCodec.read(duplicate));
        tag = WorldheartCollectionCodec.write(collection); var awards = tag.getList("victories", 10).getCompound(0).getList("awards", 10); awards.add(awards.getCompound(0).copy()); var duplicatePlayer = tag;
        assertThrows(IllegalArgumentException.class, () -> WorldheartCollectionCodec.read(duplicatePlayer));
        tag = WorldheartCollectionCodec.write(collection); var award = tag.getList("victories", 10).getCompound(0).getList("awards", 10).getCompound(0); award.putInt("roll", (award.getInt("roll") + 1) % 10_000); var forged = tag;
        assertThrows(IllegalArgumentException.class, () -> WorldheartCollectionCodec.read(forged));
    }
}
