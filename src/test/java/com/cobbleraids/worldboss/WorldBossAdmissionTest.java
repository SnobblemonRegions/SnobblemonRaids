package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossAdmissionTest {
    @Test void recoveryCanRestoreWardSlotsWithoutStartingDuelsOrInvalidatingAnExistingFocusBinding() {
        var queue = admission(2); queue.reconcileReady(List.of(A, B, C));
        queue.admitWardWaiters(2, 0, Set.of(A, B, C));
        assertEquals(2, queue.occupied()); assertEquals(List.of(C), queue.waiting());
        var token = queue.slots().get(A).token();
        assertEquals(WorldBossAdmission.Phase.BREATHER, queue.slots().get(A).phase());
        queue.admitWardWaiters(2, 1, Set.of(A, B, C)); assertTrue(queue.owns(token));
        queue.pause(A); queue.admitWardWaiters(2, 2, Set.of(B, C));
        assertEquals(2, queue.occupied()); assertTrue(queue.slots().containsKey(C)); assertFalse(queue.owns(token));
    }
    @Test void aWardOpeningWithinAStartBatchDefersRemainingReservationsWithoutLosingTheirSlots() {
        var queue = admission(2); queue.reconcileReady(List.of(A, B, C));
        var reserved = queue.reserve(2, 0, true, Set.of(A, B, C)); queue.started(reserved.getFirst());
        assertTrue(queue.defer(reserved.get(1), 0)); assertFalse(queue.started(reserved.get(1)));
        assertTrue(queue.reserve(2, 45_000, false, Set.of(A, B, C)).isEmpty());
        var next = queue.reserve(2, 45_000, true, Set.of(A, B, C));
        assertEquals(1, next.size()); assertEquals(B, next.getFirst().playerId()); assertEquals(2, queue.occupied());
        assertEquals(List.of(C), queue.waiting()); assertFalse(queue.owns(reserved.get(1)));
    }
    @Test void presenceEnrollsBeforeOpeningAndReleasesAbsentBreathersWithoutTouchingDuels() {
        var queue = admission(2);
        queue.reconcileReady(List.of(A, B, C));
        assertEquals(List.of(A, B, C), queue.waiting());
        assertTrue(queue.reserve(2, 0, false, Set.of(A, B, C)).isEmpty());
        var tokens = queue.reserve(2, 0, true, Set.of(A, B, C));
        tokens.forEach(queue::started);
        queue.ended(tokens.getFirst(), 0, true);
        queue.reconcileReady(List.of(C));
        assertEquals(1, queue.occupied(), "Absent breather releases capacity; in-flight duel retains its slot");
        assertTrue(queue.owns(tokens.get(1)));
        assertEquals(C, queue.reserve(2, 1, true, Set.of(C)).getFirst().playerId());
    }

    @Test void presenceRespectsPauseAndReturningReadyPlayersJoinTheTail() {
        var queue = admission(1);
        queue.reconcileReady(List.of(A, B));
        queue.reconcileReady(List.of(B));
        queue.reconcileReady(List.of(A, B, C));
        assertEquals(List.of(B, A, C), queue.waiting());
        queue.pause(A);
        queue.reconcileReady(List.of(A, B, C));
        assertEquals(List.of(B, C), queue.waiting());
        queue.resume(A, true);
        assertEquals(List.of(B, C, A), queue.waiting());
    }

    @Test void failedStartsBackOffAndStaleFailuresCannotDelayTheNewReservation() {
        var queue = admission(1);
        queue.ready(A, true);
        var first = queue.reserve(1, 1000, true, Set.of(A)).getFirst();
        queue.started(first);
        assertTrue(queue.failedStart(first, 1000, 5000));
        queue.reconcileReady(List.of(A, B));
        assertEquals(B, queue.reserve(1, 1001, true, Set.of(A, B)).getFirst().playerId());
        queue.depart(B);
        assertTrue(queue.reserve(1, 5999, true, Set.of(A)).isEmpty());
        var second = queue.reserve(1, 6000, true, Set.of(A)).getFirst();
        assertFalse(queue.failedStart(first, 6000, 5000));
        assertTrue(queue.owns(second));
    }
    private static final UUID EVENT = new UUID(0, 1), A = new UUID(0, 2), B = new UUID(0, 3), C = new UUID(0, 4);
    private WorldBossAdmission admission(int capacity) { return new WorldBossAdmission(EVENT, capacity, 15000, Set.of()); }

    @Test void reservationsCountBeforeStartAndResumeDoesNotBypassEarlierWaiter() {
        var queue = admission(1);
        queue.ready(A, true); queue.ready(B, true);
        var a = queue.reserve(10, 0, true, Set.of(A, B)).getFirst();
        assertEquals(1, queue.occupied());
        assertTrue(queue.started(a));
        queue.pause(A);
        assertEquals(1, queue.occupied(), "Pausing must let the current duel finish");
        assertTrue(queue.ended(a, 1000, true));
        assertEquals(0, queue.occupied());
        queue.resume(A, true); queue.resume(A, true);
        assertEquals(List.of(B, A), queue.waiting());
        assertEquals(B, queue.reserve(10, 1000, true, Set.of(A, B)).getFirst().playerId());
    }

    @Test void staleOrDuplicateCallbacksCannotReleaseANewerEncounter() {
        var queue = admission(1); queue.ready(A, true);
        var old = queue.reserve(1, 0, true, Set.of(A)).getFirst(); queue.started(old);
        assertTrue(queue.ended(old, 0, true));
        assertFalse(queue.ended(old, 0, true));
        var next = queue.reserve(1, 15000, true, Set.of(A)).getFirst(); queue.started(next);
        assertFalse(queue.ended(old, 16000, true));
        assertTrue(queue.owns(next));
        assertEquals(1, queue.occupied());
        var wrongEvent = new WorldBossAdmission.Token(UUID.randomUUID(), A, next.generation(), next.encounterId());
        assertFalse(queue.ended(wrongEvent, 17000, false));
        assertTrue(queue.owns(next));
    }

    @Test void pauseDuringReservationAndBreatherStopsStartsWithoutSkippingCooldown() {
        var queue = admission(1); queue.ready(A, true);
        var cancelled = queue.reserve(1, 0, true, Set.of(A)).getFirst();
        queue.pause(A);
        assertFalse(queue.started(cancelled));
        assertFalse(queue.refused(cancelled));
        queue.resume(A, true);
        var active = queue.reserve(1, 0, true, Set.of(A)).getFirst(); queue.started(active);
        queue.ended(active, 1000, true); queue.pause(A); queue.resume(A, true);
        assertTrue(queue.reserve(1, 15999, true, Set.of(A)).isEmpty());
        assertEquals(1, queue.reserve(1, 16000, true, Set.of(A)).size());
    }

    @Test void ritualAndClosedEventNeverStartNewDuelsAndRetainedPauseSurvivesReentry() {
        var queue = new WorldBossAdmission(EVENT, 1, 15000, Set.of(A));
        queue.ready(A, true); queue.ready(B, true);
        assertEquals(List.of(B), queue.waiting());
        assertTrue(queue.reserve(1, 0, false, Set.of(A, B)).isEmpty());
        var b = queue.reserve(1, 0, true, Set.of(A, B)).getFirst(); queue.started(b);
        queue.close();
        assertTrue(queue.ended(b, 1000, true));
        assertEquals(0, queue.occupied());
        queue.resume(A, true);
        assertTrue(queue.reserve(1, 100000, true, Set.of(A, B)).isEmpty());
    }

    @Test void unavailablePlayersLoseReadyPriorityAndRefusedReservationsReleaseOnlyThemselves() {
        var queue = admission(2);
        queue.ready(A, true); queue.ready(B, true); queue.ready(C, true);
        queue.ready(A, false); queue.ready(A, true);
        assertEquals(List.of(B, C, A), queue.waiting());
        var reserved = queue.reserve(2, 0, true, Set.of(A, B, C));
        assertTrue(queue.refused(reserved.getFirst()));
        assertFalse(queue.refused(reserved.getFirst()));
        assertTrue(queue.owns(reserved.get(1)));
        assertEquals(A, queue.reserve(1, 0, true, Set.of(A, B, C)).getFirst().playerId());
    }

    @Test void reentrantStartRefusalDoesNotLeakASlotOrReleaseTheNextReservation() {
        var queue = admission(1); queue.ready(A, true); queue.ready(B, true);
        var failed = queue.reserve(1, 0, true, Set.of(A, B)).getFirst();
        queue.started(failed);
        assertTrue(queue.failedStart(failed));
        var next = queue.reserve(1, 0, true, Set.of(A, B)).getFirst();
        assertFalse(queue.failedStart(failed));
        assertTrue(queue.owns(next));
        assertEquals(1, queue.occupied());
    }

    @Test void randomizedPauseResumeFinishAndAdmissionSequencesNeverExceedFiftyOrDuplicateMembership() {
        var queue = admission(50);
        List<UUID> players = java.util.stream.LongStream.range(100, 175).mapToObj(id -> new UUID(0, id)).toList();
        Set<UUID> ready = new LinkedHashSet<>(players);
        Random random = new Random(0xB055);
        for (int step = 0; step < 20000; step++) {
            UUID player = players.get(random.nextInt(players.size()));
            var slot = queue.slots().get(player);
            switch (random.nextInt(6)) {
                case 0 -> queue.ready(player, ready.contains(player));
                case 1 -> queue.pause(player);
                case 2 -> queue.resume(player, ready.contains(player));
                case 3 -> {
                    if (slot != null && slot.phase() == WorldBossAdmission.Phase.DUELLING) queue.ended(slot.token(), step * 1000L, true);
                }
                case 4 -> { queue.depart(player); ready.remove(player); }
                case 5 -> ready.add(player);
            }
            for (var token : queue.reserve(2, step * 1000L, step % 7 != 0, ready)) queue.started(token);
            assertTrue(queue.occupied() <= 50, "seed B055 step " + step);
            assertEquals(queue.waiting().size(), new HashSet<>(queue.waiting()).size());
            for (UUID waiting : queue.waiting()) {
                assertFalse(queue.slots().containsKey(waiting));
                assertFalse(queue.isPaused(waiting));
            }
        }
    }
}
