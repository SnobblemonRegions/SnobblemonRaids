package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldBossArrivalQueueTest {
    private static final UUID EVENT = new UUID(1, 1);
    private static WorldBossArrivalQueue.Request<Object> request(int id, WorldBossArrivalQueue.Mode mode, Object owner, long now) {
        return new WorldBossArrivalQueue.Request<>(new UUID(0, id), EVENT, mode, owner, new WorldBossTravel.Position("minecraft:overworld", 0.5, 64, 0.5, 0, 0), now);
    }
    @Test void queueTakesAtMostTwoInFifoOrder() {
        var queue = new WorldBossArrivalQueue<Object>(); for (int i = 1; i <= 5; i++) queue.submit(request(i, WorldBossArrivalQueue.Mode.FIGHT, new Object(), i));
        assertEquals(5, queue.position(new UUID(0, 5))); var first = queue.take(); assertEquals(2, first.size());
        assertEquals(new UUID(0, 1), first.getFirst().player()); assertEquals(new UUID(0, 2), first.getLast().player());
        assertEquals(3, queue.position(new UUID(0, 5))); assertEquals(2, queue.take().size()); assertEquals(1, queue.take().size()); assertTrue(queue.take().isEmpty());
    }
    @Test void duplicateRequestDoesNotExtendDeadlineOrChangeCapturedSource() {
        var queue = new WorldBossArrivalQueue<Object>(); Object owner = new Object();
        queue.submit(request(1, WorldBossArrivalQueue.Mode.WATCH, owner, 100)); queue.submit(request(1, WorldBossArrivalQueue.Mode.WATCH, owner, 1000));
        assertEquals(1, queue.watchers().size()); assertEquals(100, queue.take().getFirst().createdNanos());
    }
    @Test void modeOrViewerInstanceReplacementMovesToTailAndDropsOldWatchReservationIntent() {
        var queue = new WorldBossArrivalQueue<Object>(); Object owner = new Object();
        queue.submit(request(1, WorldBossArrivalQueue.Mode.WATCH, owner, 1)); queue.submit(request(2, WorldBossArrivalQueue.Mode.FIGHT, new Object(), 2));
        Object reconnect = new Object(); queue.submit(request(1, WorldBossArrivalQueue.Mode.FIGHT, reconnect, 3));
        assertTrue(queue.watchers().isEmpty()); var batch = queue.take(); assertEquals(new UUID(0, 2), batch.getFirst().player()); assertSame(reconnect, batch.getLast().owner());
    }
    @Test void fullQueueAllowsReplacementButCannotGrowBeyondSeventyFive() {
        var queue = new WorldBossArrivalQueue<Object>(); for (int i = 1; i <= 75; i++) assertTrue(queue.submit(request(i, WorldBossArrivalQueue.Mode.WATCH, new Object(), 1)));
        assertFalse(queue.submit(request(76, WorldBossArrivalQueue.Mode.FIGHT, new Object(), 1)));
        assertTrue(queue.submit(request(1, WorldBossArrivalQueue.Mode.FIGHT, new Object(), 2))); assertEquals(75, queue.position(new UUID(0, 1)));
        queue.cancel(new UUID(0, 1)); assertFalse(queue.pending(new UUID(0, 1))); assertTrue(queue.submit(request(76, WorldBossArrivalQueue.Mode.FIGHT, new Object(), 3)));
        queue.clear(); assertTrue(queue.take().isEmpty()); assertTrue(queue.watchers().isEmpty());
    }
    @Test void thirtySecondRealDeadlineHandlesExactBoundaryRewindAndNanoTimeRollover() {
        long start = Long.MAX_VALUE - 1_000_000_000L; var request = request(1, WorldBossArrivalQueue.Mode.FIGHT, new Object(), start);
        assertFalse(request.expired(start + WorldBossArrivalQueue.TIMEOUT_NANOS - 1)); assertTrue(request.expired(start + WorldBossArrivalQueue.TIMEOUT_NANOS)); assertTrue(request.expired(start - 1));
    }
}
