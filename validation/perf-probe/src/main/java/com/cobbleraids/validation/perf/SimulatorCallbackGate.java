package com.cobbleraids.validation.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Rehearsal-only boundary between a simulator operation and server-thread output. */
final class SimulatorCallbackGate<T> implements AutoCloseable {
    private final Consumer<Runnable> schedule;
    private final BooleanSupplier ownerThread;
    private final BooleanSupplier healthy;
    private final Consumer<T> apply;
    private final Consumer<Throwable> fault;
    private final ThreadLocal<List<T>> output = new ThreadLocal<>();
    private final AtomicLong posted = new AtomicLong(), applied = new AtomicLong(), discarded = new AtomicLong();
    private final AtomicLong maxWait = new AtomicLong(), maxBatch = new AtomicLong();
    private volatile boolean closed;

    SimulatorCallbackGate(Consumer<Runnable> schedule, BooleanSupplier ownerThread, BooleanSupplier healthy,
                          Consumer<T> apply, Consumer<Throwable> fault) {
        this.schedule = schedule; this.ownerThread = ownerThread; this.healthy = healthy;
        this.apply = apply; this.fault = fault;
    }

    void run(Runnable engine) {
        var outputs = new ArrayList<T>(); output.set(outputs);
        try { engine.run(); }
        catch (RuntimeException | Error failure) {
            discarded.addAndGet(outputs.size()); throw failure;
        } finally { output.remove(); }
        if (outputs.isEmpty()) return;
        if (!available()) { discarded.addAndGet(outputs.size()); return; }
        List<T> batch = List.copyOf(outputs); posted.addAndGet(batch.size());
        long queuedAt = System.nanoTime();
        try { schedule.accept(() -> deliver(batch, queuedAt)); }
        catch (RuntimeException | Error failure) {
            discarded.addAndGet(batch.size()); throw failure;
        }
    }

    boolean capture(T value) {
        List<T> batch = output.get();
        if (batch == null) return false;
        batch.add(value); return true;
    }

    private void deliver(List<T> batch, long queuedAt) {
        long started = System.nanoTime(); maxWait.accumulateAndGet(started - queuedAt, Math::max);
        try {
            if (!available()) { discarded.addAndGet(batch.size()); return; }
            if (!ownerThread.getAsBoolean()) {
                discarded.addAndGet(batch.size());
                close(); fault.accept(new IllegalStateException("Prototype callback escaped server thread")); return;
            }
            for (int index = 0; index < batch.size(); index++) {
                if (!available()) { discarded.addAndGet(batch.size() - index); return; }
                try { apply.accept(batch.get(index)); applied.incrementAndGet(); }
                catch (RuntimeException | Error failure) {
                    discarded.addAndGet(batch.size() - index);
                    close(); fault.accept(failure);
                    if (failure instanceof Error fatal) throw fatal;
                    return;
                }
            }
        } finally { maxBatch.accumulateAndGet(System.nanoTime() - started, Math::max); }
    }

    private boolean available() {
        return !closed && healthy.getAsBoolean();
    }

    java.util.Map<String, Object> stats() {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("posted_callbacks", posted.get()); result.put("applied_callbacks", applied.get());
        result.put("discarded_callbacks", discarded.get()); result.put("callbacks_closed", closed);
        result.put("max_callback_delivery_wait_ms", maxWait.get() / 1e6);
        result.put("max_callback_batch_ms", maxBatch.get() / 1e6); return result;
    }

    @Override public void close() { closed = true; }
}
