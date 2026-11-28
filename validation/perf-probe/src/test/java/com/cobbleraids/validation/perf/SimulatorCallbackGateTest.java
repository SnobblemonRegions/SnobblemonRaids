package com.cobbleraids.validation.perf;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SimulatorCallbackGateTest {
    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<String> applied = new ArrayList<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicBoolean owner = new AtomicBoolean(true);
    private final SimulatorCallbackGate<String> gate = new SimulatorCallbackGate<>(scheduled::add, owner::get,
            () -> failure.get() == null, value -> {
                if (value.equals("broken")) throw new IllegalStateException("callback failed");
                applied.add(value);
            }, failure::set);

    @Test void successfulOperationPostsOneOrderedBatchAndClearsCaptureScope() {
        gate.run(() -> { assertTrue(gate.capture("first")); assertTrue(gate.capture("second")); });
        assertFalse(gate.capture("outside")); assertTrue(applied.isEmpty()); assertEquals(1, scheduled.size());
        scheduled.getFirst().run(); assertEquals(List.of("first", "second"), applied);
        assertEquals(2L, gate.stats().get("applied_callbacks"));
    }

    @Test void failedEngineOperationNeverPublishesItsPartialOutput() {
        var problem = new IllegalStateException("engine failed after output");
        assertSame(problem, assertThrows(IllegalStateException.class, () -> gate.run(() -> {
            gate.capture("partial"); throw problem;
        })));
        assertFalse(gate.capture("outside")); assertTrue(scheduled.isEmpty());
        assertEquals(1L, gate.stats().get("discarded_callbacks"));
    }

    @Test void shutdownInvalidatesAlreadyScheduledOutputs() {
        gate.run(() -> gate.capture("stale")); gate.close(); scheduled.getFirst().run();
        assertTrue(applied.isEmpty()); assertEquals(1L, gate.stats().get("discarded_callbacks"));
    }

    @Test void shutdownDuringEngineExecutionDiscardsOutputsBeforeScheduling() {
        gate.run(() -> { gate.capture("stale"); gate.close(); gate.capture("also stale"); });
        assertTrue(scheduled.isEmpty()); assertEquals(2L, gate.stats().get("discarded_callbacks"));
    }

    @Test void callbackFailureStopsItsBatchAndOtherPendingBatches() {
        gate.run(() -> { gate.capture("first"); gate.capture("broken"); gate.capture("later"); });
        gate.run(() -> gate.capture("another batch"));
        scheduled.forEach(Runnable::run);
        assertNotNull(failure.get()); assertEquals(List.of("first"), applied);
        assertEquals(3L, gate.stats().get("discarded_callbacks"));
    }

    @Test void outputOnWrongThreadFailsClosedAndIsAccounted() {
        gate.run(() -> gate.capture("wrong thread")); owner.set(false); scheduled.getFirst().run();
        assertNotNull(failure.get()); assertTrue(applied.isEmpty());
        assertEquals(1L, gate.stats().get("discarded_callbacks"));
    }

    @Test void shutdownInsideCallbackStopsRemainingOutput() {
        var local = new AtomicReference<SimulatorCallbackGate<String>>();
        var delivery = new SimulatorCallbackGate<String>(scheduled::add, owner::get, () -> true,
                value -> { applied.add(value); local.get().close(); }, failure::set);
        local.set(delivery);
        delivery.run(() -> { delivery.capture("first"); delivery.capture("after close"); });
        scheduled.getFirst().run(); assertEquals(List.of("first"), applied);
        assertEquals(1L, delivery.stats().get("discarded_callbacks"));
    }

    @Test void failedSchedulingAccountsForEveryUndeliveredOutputAndClearsScope() {
        var rejected = new java.util.concurrent.RejectedExecutionException("server stopped");
        var delivery = new SimulatorCallbackGate<String>(task -> { throw rejected; }, owner::get,
                () -> true, applied::add, failure::set);
        assertSame(rejected, assertThrows(java.util.concurrent.RejectedExecutionException.class,
                () -> delivery.run(() -> delivery.capture("undelivered"))));
        assertFalse(delivery.capture("outside")); assertTrue(applied.isEmpty());
        assertEquals(1L, delivery.stats().get("discarded_callbacks"));
    }

    @Test void workerFailureInvalidatesCallbacksFromEarlierSuccessfulOperations() throws Exception {
        var tasks = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        var reported = new java.util.concurrent.CountDownLatch(1);
        try (var queue = new SerialSimulatorQueue(4, problem -> reported.countDown())) {
            var delivery = new SimulatorCallbackGate<String>(tasks::add, owner::get,
                    () -> queue.failure() == null, applied::add, queue::fail);
            queue.execute(() -> delivery.run(() -> delivery.capture("earlier")));
            queue.execute(() -> delivery.run(() -> { delivery.capture("partial"); throw new IllegalStateException("engine"); }));
            assertTrue(reported.await(5, java.util.concurrent.TimeUnit.SECONDS));
            queue.close(); tasks.forEach(Runnable::run);
            assertTrue(applied.isEmpty()); assertEquals(1, tasks.size());
            assertEquals(2L, delivery.stats().get("discarded_callbacks"));
        }
    }
}
