package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossChunkLeaseTest {
    private record Ticket(WorldBossChunkLease.Chunk chunk, UUID event) {}
    private static final class Backend implements WorldBossChunkLease.Backend {
        final Set<Ticket> tickets = new HashSet<>();
        final Set<WorldBossChunkLease.Chunk> missing = new HashSet<>();
        final Map<WorldBossChunkLease.Chunk, Integer> removals = new HashMap<>();
        int additions;
        int failAddAt = -1;
        WorldBossChunkLease.Chunk failRelease;
        public boolean loaded(WorldBossChunkLease.Chunk chunk) { return !missing.contains(chunk); }
        public void retain(WorldBossChunkLease.Chunk chunk, UUID event) {
            tickets.add(new Ticket(chunk, event));
            if (++additions == failAddAt) throw new IllegalStateException("injected add failure after side effect");
        }
        public void release(WorldBossChunkLease.Chunk chunk, UUID event) {
            removals.merge(chunk, 1, Integer::sum);
            if (chunk.equals(failRelease)) throw new IllegalStateException("injected release failure");
            tickets.remove(new Ticket(chunk, event));
        }
    }

    @Test void footprintCoversPlacementAndNegativeChunkBoundariesWithDependencyRing() {
        for (double x : new double[]{-17, -16, -0.5, 0, 15.999, 16, 29_999_000}) {
            var plan = WorldBossChunkLease.plan(x, -x, 96);
            var retained = new HashSet<>(plan.retained());
            var required = new HashSet<>(plan.required());
            assertEquals(plan.retained().size(), retained.size());
            assertEquals(plan.required().size(), required.size());
            assertTrue(required.size() <= WorldBossChunkLease.MAX_CHUNKS);
            for (int dx = -112; dx <= 112; dx++) for (int dz = -112; dz <= 112; dz++) {
                assertTrue(retained.contains(new WorldBossChunkLease.Chunk((int) Math.floor((x + dx) / 16), (int) Math.floor((-x + dz) / 16))));
            }
            for (var chunk : retained) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                assertTrue(required.contains(new WorldBossChunkLease.Chunk(chunk.x() + dx, chunk.z() + dz)));
            }
        }
    }

    @Test void invalidOrOversizedFootprintsFailBeforeAllocation() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 15, 513, 512}) {
            assertThrows(IllegalArgumentException.class, () -> WorldBossChunkLease.plan(0, 0, value));
        }
        assertThrows(IllegalArgumentException.class, () -> WorldBossChunkLease.plan(Double.NaN, 0, 96));
        assertThrows(IllegalArgumentException.class, () -> WorldBossChunkLease.plan(30_000_001, 0, 96));
        var plan = WorldBossChunkLease.plan(0.5, 0.5, 96);
        assertThrows(UnsupportedOperationException.class, () -> plan.retained().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.required().clear());
    }

    @Test void missingDependencyNeverAcquiresAnyTicket() {
        Backend backend = new Backend();
        var plan = WorldBossChunkLease.plan(0, 0, 16);
        backend.missing.add(plan.required().getFirst());
        var lease = new WorldBossChunkLease(UUID.randomUUID(), plan, backend);
        assertThrows(IllegalStateException.class, lease::renew);
        assertEquals(0, backend.additions);
        assertEquals(0, lease.ownedCount());
        assertThrows(IllegalStateException.class, lease::renew);
    }

    @Test void renewalAndIdempotentCloseKeepOtherEventsTickets() {
        Backend backend = new Backend();
        var plan = WorldBossChunkLease.plan(0, 0, 16);
        UUID event = UUID.randomUUID(), other = UUID.randomUUID();
        Ticket unrelated = new Ticket(plan.retained().getFirst(), other);
        backend.tickets.add(unrelated);
        var lease = new WorldBossChunkLease(event, plan, backend);
        lease.renew(); lease.renew();
        assertEquals(plan.retained().size(), lease.ownedCount());
        assertEquals(plan.retained().size() + 1, backend.tickets.size());
        assertTrue(lease.matches(event, plan));
        assertFalse(lease.matches(other, plan));
        assertFalse(lease.matches(event, WorldBossChunkLease.plan(100, 0, 16)));
        lease.close(); lease.close();
        assertEquals(Set.of(unrelated), backend.tickets);
        assertTrue(backend.removals.values().stream().allMatch(count -> count == 1));
        assertFalse(lease.matches(event, plan));
        assertThrows(IllegalStateException.class, lease::renew);
    }

    @Test void partialSideEffectingAcquisitionRollsBackEveryAttemptedTicket() {
        Backend backend = new Backend(); backend.failAddAt = 3;
        var lease = new WorldBossChunkLease(UUID.randomUUID(), WorldBossChunkLease.plan(0, 0, 16), backend);
        assertThrows(IllegalStateException.class, lease::renew);
        assertTrue(backend.tickets.isEmpty());
        assertEquals(0, lease.ownedCount());
        assertEquals(3, backend.removals.size());
    }

    @Test void failedRemovalDoesNotBlockOtherRemovalsAndOnlyFailuresRetry() {
        Backend backend = new Backend();
        var plan = WorldBossChunkLease.plan(0, 0, 16);
        var lease = new WorldBossChunkLease(UUID.randomUUID(), plan, backend);
        lease.renew(); backend.failRelease = plan.retained().getFirst();
        assertThrows(IllegalStateException.class, lease::close);
        assertEquals(1, lease.ownedCount());
        assertEquals(plan.retained().size(), backend.removals.size());
        var failed = backend.failRelease; backend.failRelease = null;
        lease.close();
        assertEquals(0, lease.ownedCount());
        assertTrue(backend.tickets.isEmpty());
        assertEquals(2, backend.removals.get(failed));
        assertEquals(plan.retained().size() + 1, backend.removals.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test void failedRenewalReleasesTheOldLeaseInsteadOfKeepingAnUnsafeFootprint() {
        Backend backend = new Backend();
        var plan = WorldBossChunkLease.plan(-0.5, -0.5, 16);
        var lease = new WorldBossChunkLease(UUID.randomUUID(), plan, backend);
        lease.renew(); backend.missing.add(plan.required().getLast());
        assertThrows(IllegalStateException.class, lease::renew);
        assertTrue(backend.tickets.isEmpty());
        assertEquals(0, lease.ownedCount());
    }

    @Test void acquisitionFailurePreservesCleanupFailureAndCanRetryItsRemainingOwnership() {
        Backend backend = new Backend();
        var plan = WorldBossChunkLease.plan(0, 0, 16);
        backend.failAddAt = 3; backend.failRelease = plan.retained().getFirst();
        var lease = new WorldBossChunkLease(UUID.randomUUID(), plan, backend);
        var failure = assertThrows(IllegalStateException.class, lease::renew);
        assertEquals("injected add failure after side effect", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(1, lease.ownedCount());
        backend.failRelease = null; lease.close();
        assertTrue(backend.tickets.isEmpty());
    }
}
