package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossBlessingTest {
    private WorldheartCollection empty() { return new WorldheartCollection(new WorldheartCollection.Snapshot("ab".repeat(32), List.of(), Map.of())); }
    @Test void firstWinGivesAnHourAndReplayedWinsCannotRefreshIt() {
        var event = UUID.randomUUID(); var boss = UUID.randomUUID(); var collection = empty().grant(event, boss, 1, 1000, Set.of(), true);
        assertEquals(3_601_000, collection.blessingUntil()); assertFalse(collection.blessedAt(999)); assertTrue(collection.blessedAt(1000));
        assertTrue(collection.blessedAt(3_600_999)); assertFalse(collection.blessedAt(3_601_000));
        assertSame(collection, collection.grant(event, boss, 1, 9_999_999, Set.of(), true));
        assertEquals(collection.snapshot(), WorldheartCollectionCodec.read(WorldheartCollectionCodec.write(collection)).snapshot());
    }
    @Test void successiveWinsExtendButNeverExceedTheTwoHourHorizon() {
        var boss = UUID.randomUUID(); var collection = empty();
        for (int i = 0; i < 5; i++) collection = collection.grant(new UUID(1, i), boss, i + 1, 1000, Set.of(), true);
        assertEquals(7_201_000, collection.blessingUntil());
        collection = collection.grant(new UUID(2, 1), boss, 6, 10_000_000, Set.of(), true);
        assertEquals(13_600_000, collection.blessingUntil()); assertFalse(collection.blessedAt(8_000_000));
    }
    @Test void arithmeticFloorsBaseWithoutOverflowOrRecursion() {
        assertEquals(0, WorldBossBlessing.bonus(9)); assertEquals(1, WorldBossBlessing.bonus(19));
        assertEquals(214_748_364, WorldBossBlessing.bonus(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> WorldBossBlessing.bonus(-1));
        assertThrows(ArithmeticException.class, () -> WorldBossBlessing.extend(0, Long.MAX_VALUE));
    }
    @Test void forgedBoundsAndBackdatedNewWindowsCannotRewriteHistory() {
        var boss = UUID.randomUUID(); var collection = empty().grant(new UUID(1, 1), boss, 1, 1000, Set.of(), true);
        assertThrows(IllegalArgumentException.class, () -> collection.grant(new UUID(1, 2), boss, 2, 999, Set.of(), true));
        var tag = WorldheartCollectionCodec.write(collection); tag.getList("victories", 10).getCompound(0).putLong("blessing_until", 3_602_000);
        assertThrows(IllegalArgumentException.class, () -> WorldheartCollectionCodec.read(tag));
    }
    @Test void legacyCollectionFormatDoesNotInventBlessings() {
        var tag = WorldheartCollectionCodec.write(empty().grant(UUID.randomUUID(), UUID.randomUUID(), 1, 1000, Set.of()));
        tag.putInt("version", 1); tag.getList("victories", 10).getCompound(0).remove("blessed");
        var legacy = WorldheartCollectionCodec.read(tag); assertFalse(legacy.hasBlessings()); assertFalse(legacy.blessedAt(1000));
    }
}
