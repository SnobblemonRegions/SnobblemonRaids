package com.cobbleraids.validation.perf;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Rehearsal prototype seam; implementation must preserve order and fail closed. */
final class SerialSimulatorQueue implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Consumer<Throwable> fault;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicLong submitted = new AtomicLong(), completed = new AtomicLong(), discarded = new AtomicLong();
    private final AtomicInteger peak = new AtomicInteger();
    private final AtomicLong maxWait = new AtomicLong(), maxExecution = new AtomicLong();
    private volatile Thread worker;
    SerialSimulatorQueue(int capacity, Consumer<Throwable> fault) {
        this.fault = fault;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), runnable -> {
            Thread thread = new Thread(runnable, "WorldBoss simulator prototype"); thread.setDaemon(true); worker = thread; return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    void execute(Runnable work) {
        if (failure.get() != null) throw new RejectedExecutionException("Simulator prototype failed", failure.get());
        submitted.incrementAndGet();
        long accepted = System.nanoTime();
        try {
            executor.execute(() -> {
                long started = System.nanoTime(); maxWait.accumulateAndGet(started - accepted, Math::max);
                try { work.run(); }
                catch (Throwable problem) { fail(problem); if (problem instanceof Error fatal) throw fatal; }
                finally { maxExecution.accumulateAndGet(System.nanoTime() - started, Math::max); completed.incrementAndGet(); }
            });
            peak.accumulateAndGet(executor.getQueue().size(), Math::max);
        } catch (RejectedExecutionException rejected) {
            submitted.decrementAndGet();
            if (!executor.isShutdown()) fail(rejected);
            throw rejected;
        }
    }
    boolean onWorker() { return Thread.currentThread() == worker; }
    Throwable failure() { return failure.get(); }
    void fail(Throwable problem) {
        if (failure.compareAndSet(null, problem)) {
            discarded.addAndGet(executor.shutdownNow().size());
            fault.accept(problem);
        }
    }
    java.util.Map<String, Object> stats() {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("submitted", submitted.get()); result.put("completed", completed.get());
        result.put("queued", executor.getQueue().size()); result.put("peak_queued", peak.get());
        result.put("discarded", discarded.get()); result.put("worker", worker == null ? "not started" : worker.getName());
        result.put("shutdown", executor.isShutdown()); result.put("terminated", executor.isTerminated());
        result.put("max_queue_wait_ms", maxWait.get() / 1e6); result.put("max_execution_ms", maxExecution.get() / 1e6);
        result.put("failed", failure.get() != null); result.put("failure", failure.get() == null ? null : failure.get().toString()); return result;
    }
    @Override public void close() {
        executor.shutdown();
        if (onWorker()) return;
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) fail(new java.util.concurrent.TimeoutException("Simulator prototype did not stop within two seconds"));
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); fail(interrupted); }
    }
}
