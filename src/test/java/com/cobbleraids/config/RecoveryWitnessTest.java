package com.cobbleraids.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoveryWitnessTest {
    @TempDir Path directory;
    private static final UUID ID = new UUID(1, 1), PLAYER = new UUID(2, 2);

    @Test void atomicChecksummedHeadRoundTripsAndRejectsOtherGenerations() throws Exception {
        Path file = directory.resolve("config/recovery.head"); assertNull(RecoveryWitness.read(file));
        var head = new RecoveryWitness.Head(ID, 1, "a".repeat(64)); RecoveryWitness.write(file, head);
        assertEquals(head, RecoveryWitness.read(file)); assertEquals(92, Files.size(file));
        RecoveryWitness.verify(file, head);
        assertThrows(IOException.class, () -> RecoveryWitness.verify(file, new RecoveryWitness.Head(ID, 2, head.digest())));
        RecoveryWitness.write(file, new RecoveryWitness.Head(ID, 2, "b".repeat(64)));
        assertEquals(2, RecoveryWitness.read(file).sequence());
    }

    @Test void truncatedTamperedAndMissingWitnessCannotBecomeAnEmptyRecoverySet() throws Exception {
        Path file = directory.resolve("head"); var head = new RecoveryWitness.Head(ID, 0, "c".repeat(64));
        assertThrows(IOException.class, () -> RecoveryWitness.verify(file, head)); RecoveryWitness.write(file, head);
        byte[] bytes = Files.readAllBytes(file);
        for (int offset : new int[]{0, 4, 20, 28, 59, 60, 91}) {
            byte[] altered = bytes.clone(); altered[offset] ^= 1; Files.write(file, altered);
            assertThrows(IOException.class, () -> RecoveryWitness.read(file));
        }
        Files.write(file, java.util.Arrays.copyOf(bytes, 91)); assertThrows(IOException.class, () -> RecoveryWitness.read(file));
    }

    @Test void recipientAndMarkerHashesAreOrderIndependentAndBindExactPromises() {
        UUID other = new UUID(3, 3); var first = new LinkedHashMap<UUID, Integer>(); first.put(PLAYER, 200); first.put(other, 50);
        var reverse = new LinkedHashMap<UUID, Integer>(); reverse.put(other, 50); reverse.put(PLAYER, 200);
        assertEquals(RecoveryWitness.promise(ID, first), RecoveryWitness.promise(ID, reverse));
        assertNotEquals(RecoveryWitness.promise(ID, first), RecoveryWitness.promise(other, first));
        assertNotEquals(RecoveryWitness.promise(ID, first), RecoveryWitness.promise(ID, Map.of(PLAYER, 200)));
        assertThrows(IllegalArgumentException.class, () -> RecoveryWitness.promise(ID, Map.of(PLAYER, -1)));
        var markers = Map.of(ID, RecoveryWitness.promise(ID, first));
        assertNotEquals(RecoveryWitness.points(ID, markers), RecoveryWitness.points(other, markers));
        assertNotEquals(RecoveryWitness.points(ID, markers), RecoveryWitness.points(ID, Map.of()));
    }

    @Test void failedAtomicReplacementPreservesExistingTargetAndLeavesNoTemporaryDebris() throws Exception {
        Path occupied = directory.resolve("occupied"); Files.createDirectory(occupied); Files.writeString(occupied.resolve("keep"), "evidence");
        assertThrows(IOException.class, () -> RecoveryWitness.write(occupied, new RecoveryWitness.Head(ID, 1, "a".repeat(64))));
        assertEquals("evidence", Files.readString(occupied.resolve("keep")));
        try (var children = Files.list(directory)) { assertEquals(1, children.count()); }
    }
}
