package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorldBossParticipationCheckpointTest {
    private static final UUID EVENT = new UUID(0, 1), A = new UUID(0, 2), B = new UUID(0, 3), C = new UUID(0, 4), D = new UUID(0, 5);
    private WorldBossAdmission queue(int capacity) { return new WorldBossAdmission(EVENT, capacity, 15_000, Set.of()); }
    @Test void checkpointRestoresReadyFifoAndFreshBreatherTokensWithoutAStaleBattle() {
        var queue = queue(2); queue.reconcileReady(List.of(A, B, C));
        var old = queue.reserve(2, 1000, true, Set.of(A, B, C)); old.forEach(token -> { queue.started(token); queue.confirm(token); });
        queue.ended(old.get(1), 2000, true);
        var snapshot = WorldBossAdmissionCodec.read(WorldBossAdmissionCodec.write(queue.snapshot(3000)));
        var restored = WorldBossAdmission.recover(snapshot, List.of(A, B, C, D), Set.of(), 4000);
        assertEquals(List.of(C, D), restored.waiting()); assertEquals(2, restored.occupied());
        for (var slot : restored.slots().values()) { assertEquals(WorldBossAdmission.Phase.BREATHER, slot.phase()); assertTrue(slot.token().generation() > snapshot.generation()); assertFalse(old.contains(slot.token())); }
        assertFalse(restored.ended(old.getFirst(), 5000, false)); assertTrue(restored.admittedBefore(A));
        assertEquals(17_000, restored.slots().get(B).availableAtMillis());
    }
    @Test void absentUnavailableAndPausedPlayersDoNotGetOldPriorityOnRecovery() {
        var queue = queue(1); queue.reconcileReady(List.of(A, B, C));
        var snapshot = queue.snapshot(1000);
        var restored = WorldBossAdmission.recover(snapshot, List.of(C, A, D), Set.of(A), 2000);
        assertEquals(List.of(C, D), restored.waiting());
        restored.resume(A, true); assertEquals(List.of(C, D, A), restored.waiting());
    }
    @Test void repeatedRecoveryDuringRegroupRetainsCheckpointPriorityAndNewTailOrder() {
        var queue = queue(2); queue.reconcileReady(List.of(A, B)); var original = queue.snapshot(1000);
        var regroup = WorldBossAdmission.intent(original, Set.of()); regroup.reconcileReady(List.of(C, A));
        var merged = original.mergeRegroup(regroup.snapshot(2000), 2000);
        assertEquals(List.of(A, B, C), merged.waiting());
        merged = merged.without(A); var next = WorldBossAdmission.intent(merged, Set.of()); next.reconcileReady(List.of(D, A));
        merged = merged.mergeRegroup(next.snapshot(3000), 3000); assertEquals(List.of(B, C, D, A), merged.waiting());
        var restored = WorldBossAdmission.recover(merged, List.of(A, B, C, D), Set.of(), 4000); assertEquals(List.of(B, C, D, A), restored.waiting());
    }
    @Test void reservedOnlyNewcomerDoesNotBypassTheFinalMinuteButSuccessfulWardAndDuelsDo() {
        var queue = queue(2); queue.reconcileReady(List.of(A, B)); var reserved = queue.reserve(2, 1000, true, Set.of(A, B));
        assertTrue(queue.eligibleAt(A, 60_001)); assertFalse(queue.eligibleAt(A, 60_000));
        queue.started(reserved.getFirst()); assertFalse(queue.admittedBefore(A)); queue.confirm(reserved.getFirst()); assertTrue(queue.eligibleAt(A, 1));
        queue.defer(reserved.getLast(), 1000); assertTrue(queue.eligibleAt(B, 60_000));
        var restored = WorldBossAdmission.intent(queue.snapshot(1000), Set.of()); assertTrue(restored.eligibleAt(A, 1)); assertTrue(restored.eligibleAt(B, 1)); assertFalse(restored.eligibleAt(C, 60_000));
    }
    @Test void snapshotRevisionDoesNotDirtyEveryPresenceTickAndMalformedStateIsRejected() {
        var queue = queue(2); queue.reconcileReady(List.of(A, B)); long revision = queue.revision(); queue.reconcileReady(List.of(A, B)); assertEquals(revision, queue.revision());
        queue.depart(A); assertTrue(queue.revision() > revision);
        var tag = WorldBossAdmissionCodec.write(queue.snapshot(1000)); var rows = tag.getList("queue", 10); rows.add(rows.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> WorldBossAdmissionCodec.read(tag));
        assertThrows(IllegalArgumentException.class, () -> WorldBossAdmission.recover(queue.snapshot(1000), List.of(B), Set.of(), 999));
    }
    @Test void stateCheckpointAndTravelRoundTripAndResetOnlyTheEventOnNewBegin() {
        var state = new WorldBossState(); state.begin(EVENT, "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, 1000, 0, new WorldBossPool(100), true);
        var queue = queue(50); queue.reconcileReady(List.of(B, A)); state.admissionCheckpoint(queue.snapshot(1000));
        var position = new WorldBossTravel.Position("minecraft:the_nether", 12, 70, 9, 20, 30);
        state.travel(state.travel().outbound(A, position, new WorldBossTravel.Position("minecraft:overworld", 0, 64, 0, 0, 0), UUID.randomUUID()));
        var restored = WorldBossState.load(state.save(new CompoundTag(), null), null); assertFalse(restored.isQuarantined()); assertEquals(List.of(B, A), restored.admissionCheckpoint().waiting()); assertEquals(position, restored.travel().ticket(A).origin());
        restored.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, 2000, 0, new WorldBossPool(100), true);
        assertTrue(restored.admissionCheckpoint().waiting().isEmpty()); assertEquals(position, restored.travel().ticket(A).origin());
    }
    @Test void corruptCheckpointOwnerOrMissingReturnRecordsAreQuarantinedWithoutDroppingEvidence() {
        var state = new WorldBossState(); state.begin(EVENT, "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, 1000, 0, new WorldBossPool(100), true);
        var tag = state.save(new CompoundTag(), null); tag.getCompound("admission").putUUID("event", UUID.randomUUID());
        var restored = WorldBossState.load(tag, null); assertTrue(restored.isQuarantined()); assertEquals(tag, restored.save(new CompoundTag(), null));
        var missing = state.save(new CompoundTag(), null); missing.remove("travel"); assertTrue(WorldBossState.load(missing, null).isQuarantined());
    }
    @Test void earlierRootMigratesWithoutInventingQueueOrHistoricalReturnLocations() {
        var state = new WorldBossState(); state.begin(EVENT, "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, 1000, 0, new WorldBossPool(100), true);
        var tag = state.save(new CompoundTag(), null); tag.putInt("schema", 8); tag.remove("travel"); tag.remove("admission");
        var restored = WorldBossState.load(tag, null); assertFalse(restored.isQuarantined()); assertTrue(restored.travel().tickets().isEmpty()); assertTrue(restored.admissionCheckpoint().waiting().isEmpty());
        assertFalse(restored.admissionCheckpoint().cutoffEnabled(), "Do not retroactively close old fights whose historical admissions were never stored");
    }
    @Test void hysteresisRetainsOnlyPreviousMembersAndWorksOnVerticalSphereEdges() {
        assertFalse(WorldBossZone.present(0, 64, 0, 32, 32.1, 64, 0, false));
        assertTrue(WorldBossZone.present(0, 64, 0, 32, 33, 64, 0, true));
        assertFalse(WorldBossZone.present(0, 64, 0, 32, 33.001, 64, 0, true));
        assertTrue(WorldBossZone.present(0, 64, 0, 32, 0, 97, 0, true));
        assertFalse(WorldBossZone.present(0, 64, 0, 32, 0, 97, 0, false));
    }
    @Test void randomizedRecoveryCannotExceedCapacityOrReviveAnyOldToken() {
        var queue = queue(3); var players = List.of(A, B, C, D); var random = new java.util.Random(17);
        for (int step = 1; step <= 3000; step++) {
            long now = step * 100L; var player = players.get(random.nextInt(players.size()));
            switch (random.nextInt(5)) {
                case 0 -> queue.ready(player, random.nextBoolean());
                case 1 -> { for (var token : queue.reserve(2, now, true, Set.copyOf(players))) { if (random.nextBoolean()) { queue.started(token); queue.confirm(token); } else queue.failedStart(token, now, 5000); } }
                case 2 -> { for (var slot : queue.slots().values()) if (slot.phase() == WorldBossAdmission.Phase.DUELLING) queue.ended(slot.token(), now, random.nextBoolean()); }
                case 3 -> { if (random.nextBoolean()) queue.pause(player); else queue.resume(player, true); }
                case 4 -> {
                    var snapshot = WorldBossAdmissionCodec.read(WorldBossAdmissionCodec.write(queue.snapshot(now))); var before = queue.slots().values().stream().map(WorldBossAdmission.Slot::token).toList();
                    queue = WorldBossAdmission.recover(snapshot, players, queue.pausedPlayers(), now + 1);
                    for (var token : before) assertFalse(queue.owns(token));
                    assertTrue(queue.slots().values().stream().allMatch(slot -> slot.phase() == WorldBossAdmission.Phase.BREATHER));
                }
            }
            assertTrue(queue.occupied() <= 3); var unique = new java.util.HashSet<>(queue.waiting()); assertEquals(queue.waiting().size(), unique.size());
            for (var id : queue.slots().keySet()) assertTrue(unique.add(id));
        }
    }
}
