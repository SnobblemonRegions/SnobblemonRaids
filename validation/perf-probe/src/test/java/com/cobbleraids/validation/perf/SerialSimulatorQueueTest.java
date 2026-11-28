package com.cobbleraids.validation.perf;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class SerialSimulatorQueueTest {
    @Test void rejectingAfterNormalShutdownDoesNotReportAnEngineFault() {
        var fault = new AtomicReference<Throwable>();
        var queue = new SerialSimulatorQueue(1, fault::set);
        queue.close();
        assertThrows(RejectedExecutionException.class, () -> queue.execute(() -> fail("closed")));
        assertNull(fault.get()); assertNull(queue.failure());
        assertEquals(0L, queue.stats().get("submitted"));
    }

    @Test void gameplayCallsRunOffCallerInFifoOrderAndCloseDrainsThem() throws Exception {
        Thread caller = Thread.currentThread(); var threads = new ArrayList<Thread>(); var values = new ArrayList<Integer>();
        try (var queue = new SerialSimulatorQueue(8, ignored -> {})) {
            for (int n = 0; n < 8; n++) { int value = n; queue.execute(() -> { threads.add(Thread.currentThread()); values.add(value); }); }
            queue.close();
            assertEquals(List.of(0,1,2,3,4,5,6,7), values);
            assertTrue(threads.stream().allMatch(thread -> thread != caller));
            assertEquals(1, threads.stream().distinct().count());
            assertThrows(RejectedExecutionException.class, () -> queue.execute(() -> fail("closed")));
        }
    }

    @Test void missedShutdownDeadlineIsReportedRatherThanClaimedAsCleanDrain() throws Exception {
        var entered = new CountDownLatch(1); var stopped = new CountDownLatch(1);
        var fault = new AtomicReference<Throwable>();
        var queue = new SerialSimulatorQueue(1, fault::set);
        try {
            queue.execute(() -> {
                entered.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException shutdown) { Thread.currentThread().interrupt(); }
                finally { stopped.countDown(); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            queue.close();
            assertInstanceOf(TimeoutException.class, fault.get());
            assertSame(fault.get(), queue.failure());
            assertTrue(stopped.await(5, TimeUnit.SECONDS));
            assertEquals(true, queue.stats().get("shutdown"));
        } finally { queue.close(); }
    }
    @Test void saturationNeverRunsGameplayOnTheCallingThreadOrSilentlyDropsIt() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var pendingRan = new AtomicBoolean();
        var fault = new AtomicReference<Throwable>();
        try (var queue = new SerialSimulatorQueue(1, fault::set)) {
            queue.execute(() -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); } });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            queue.execute(() -> pendingRan.set(true));
            assertThrows(RejectedExecutionException.class, () -> queue.execute(() -> fail("must not fall back to caller")));
            assertNotNull(fault.get()); assertNotNull(queue.failure());
            assertFalse(pendingRan.get());
            assertThrows(RejectedExecutionException.class, () -> queue.execute(() -> {}));
        } finally { release.countDown(); }
    }
    @Test void aFailedEngineTaskPoisonsTheQueueAndPreventsLaterGameplay() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var reported = new CountDownLatch(1);
        var pendingRan = new AtomicBoolean(); var cause = new IllegalStateException("engine failure");
        try (var queue = new SerialSimulatorQueue(4, failure -> reported.countDown())) {
            queue.execute(() -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException stop) { throw new RuntimeException(stop); } throw cause; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            queue.execute(() -> pendingRan.set(true)); release.countDown();
            assertTrue(reported.await(5, TimeUnit.SECONDS));
            assertSame(cause, queue.failure()); assertFalse(pendingRan.get());
            assertThrows(RejectedExecutionException.class, () -> queue.execute(() -> {}));
        } finally { release.countDown(); }
    }
}
