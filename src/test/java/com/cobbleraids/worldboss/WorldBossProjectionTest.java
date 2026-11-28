package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldBossProjectionTest {
    @TempDir Path directory;

    private static CompoundTag root(int value) {
        var root = new CompoundTag(); var data = new CompoundTag();
        data.putInt("value", value); root.put("data", data); return root;
    }

    @Test void ordinaryCommitDoesNotRewriteRebuildableCacheAndLatestStateRecoversWithoutIt() throws Exception {
        Path file = directory.resolve("state.journal"), head = directory.resolve("journal.head"), cache = directory.resolve("state.dat");
        var journal = WorldBossJournal.open(file, head, null);
        journal.commit(root(1), List.of());
        var state = new WorldBossState(journal);
        state.commitRoot(cache, root(2));
        assertFalse(Files.exists(cache), "An acknowledged journal commit must not synchronously rewrite the redundant NBT cache");
        var restored = WorldBossJournal.open(file, head, null);
        assertEquals(root(2), restored.snapshot());
        assertEquals(2, restored.sequence());
    }

    @Test void staleProjectionRecoversLatestAndExplicitShutdownCheckpointRefreshesIt() throws Exception {
        Path file = directory.resolve("state.journal"), head = directory.resolve("journal.head"), cache = directory.resolve("state.dat");
        var journal = WorldBossJournal.open(file, head, null); journal.commit(root(1), List.of());
        var state = new WorldBossState(journal); state.checkpointProjection(cache);
        byte[] before = Files.readAllBytes(cache);
        state.commitRoot(cache, root(2)); state.commitRoot(cache, root(3));
        assertArrayEquals(before, Files.readAllBytes(cache));
        var stale = NbtIo.readCompressed(cache, NbtAccounter.unlimitedHeap());
        assertEquals(root(3), WorldBossJournal.open(file, head, stale).snapshot());
        state.checkpointProjection(cache);
        assertEquals(journal.projection(), NbtIo.readCompressed(cache, NbtAccounter.unlimitedHeap()));
    }

    @Test void failedCacheRefreshDoesNotUndoOrBlockAcknowledgedJournalCommits() throws Exception {
        Path file = directory.resolve("state.journal"), head = directory.resolve("journal.head"), cache = directory.resolve("occupied");
        Files.createDirectory(cache); Files.writeString(cache.resolve("keep"), "keep");
        var journal = WorldBossJournal.open(file, head, null); journal.commit(root(1), List.of());
        var state = new WorldBossState(journal); state.commitRoot(cache, root(2));
        assertThrows(java.io.IOException.class, () -> state.checkpointProjection(cache));
        assertFalse(journal.blocked()); state.commitRoot(cache, root(3));
        assertEquals(root(3), WorldBossJournal.open(file, head, null).snapshot());
        assertEquals("keep", Files.readString(cache.resolve("keep")));
    }

    @Test void recipientsComeFromSecuredPromiseAndRemainImmutableAcrossSubsequentEvents() throws Exception {
        UUID boss = new UUID(1, 1), support = new UUID(2, 2);
        var journal = WorldBossJournal.open(directory.resolve("state.journal"), directory.resolve("head"), null);
        journal.commit(root(1), List.of(new WorldBossJournal.Promise(boss, Map.of(support, 200), false, true)));
        var state = new WorldBossState(journal);
        assertThrows(IllegalStateException.class, () -> state.qualifiedPlayers(boss));
        journal.commit(root(2), List.of(new WorldBossJournal.Promise(boss, Map.of(support, 200), true, true)));
        var recipients = state.qualifiedPlayers(boss);
        assertEquals(Set.of(support), recipients);
        assertThrows(UnsupportedOperationException.class, recipients::clear);
        state.commitRoot(directory.resolve("cache"), root(3));
        assertEquals(recipients, state.qualifiedPlayers(boss));
        assertThrows(IllegalStateException.class, () -> state.qualifiedPlayers(new UUID(9, 9)));
    }
}
