package com.cobbleraids.showdown;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/** Server-owned ticket boundary around a simulator that never sees native actors. */
final class WorldBossSimulation implements AutoCloseable {
    interface Engine extends AutoCloseable {
        void start(UUID id, String[] messages) throws Exception;
        void send(UUID id, String[] messages) throws Exception;
        void end(UUID id) throws Exception;
        @Override void close() throws Exception;
    }
    interface Factory { Engine open(BiConsumer<String, String> output) throws Exception; }
    enum Phase { PENDING, OPEN, CLOSING, CLOSED }
    enum Kind { STARTED, OUTPUT, CLOSED }
    record Event(UUID id, Kind kind, List<String> messages) {}
    private record Work(UUID id, Kind kind, String[] messages, long bytes) {}
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private final Thread owner = Thread.currentThread(), worker;
    private final ArrayBlockingQueue<Work> input = new ArrayBlockingQueue<>(512);
    private final ArrayBlockingQueue<Event> output = new ArrayBlockingQueue<>(512);
    private final Map<UUID, Phase> slots = new HashMap<>();
    private final Map<UUID, Long> startingAt = new HashMap<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final CompletableFuture<Void> initialized = new CompletableFuture<>();
    private final Object budget = new Object();
    private long inputBytes, outputBytes;
    private volatile boolean ready, stopping, terminated;
    private Work current;
    private List<String> batch;

    WorldBossSimulation(Factory factory) {
        worker = Thread.ofPlatform().daemon(true).inheritInheritableThreadLocals(false)
                .name("World Boss private simulator").unstarted(() -> run(factory));
        worker.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        worker.start();
    }
    void awaitReady() throws Exception { initialized.get(5, TimeUnit.SECONDS); }
    boolean ready() { return ready && !stopping && failure.get() == null; }
    Throwable failure() { return failure.get(); }
    boolean terminated() { return terminated; }
    String summary() {
        requireOwner(); var counts = new EnumMap<Phase, Integer>(Phase.class);
        for (Phase phase : slots.values()) counts.merge(phase, 1, Integer::sum);
        return "pending=" + counts.getOrDefault(Phase.PENDING, 0) + ", open=" + counts.getOrDefault(Phase.OPEN, 0)
                + ", closing=" + counts.getOrDefault(Phase.CLOSING, 0) + ", closed=" + counts.getOrDefault(Phase.CLOSED, 0)
                + ", input=" + input.size() + ", output=" + output.size() + ", terminated=" + terminated;
    }
    Phase phase(UUID id) { requireOwner(); return slots.get(id); }
    boolean overdue() {
        requireOwner(); long now = System.nanoTime();
        return startingAt.values().stream().anyMatch(at -> now - at >= TimeUnit.SECONDS.toNanos(10));
    }
    void start(UUID id, String[] messages) {
        requireOwner(); requireReady();
        if (slots.containsKey(id)) throw new IllegalStateException("Duplicate simulation identity");
        if (slots.size() >= 65_536) throw reject("Private simulator identity budget exhausted; restart required");
        slots.put(id, Phase.PENDING); startingAt.put(id, System.nanoTime()); enqueue(id, Kind.STARTED, messages);
    }
    void send(UUID id, String[] messages) {
        requireOwner(); Phase state = slots.get(id);
        if (state == null) throw new IllegalArgumentException("Unowned simulator input");
        if (state == Phase.CLOSING || state == Phase.CLOSED || stopping || failure.get() != null) return;
        enqueue(id, Kind.OUTPUT, messages);
    }
    void end(UUID id) {
        requireOwner(); Phase state = slots.get(id);
        if (state == null) throw new IllegalArgumentException("Unowned simulator close");
        if (state == Phase.CLOSING || state == Phase.CLOSED) return;
        slots.put(id, Phase.CLOSING); startingAt.remove(id);
        if (stopping || failure.get() != null) { if (terminated) slots.put(id, Phase.CLOSED); return; }
        enqueue(id, Kind.CLOSED, new String[0]);
    }
    Event poll() {
        requireOwner(); if (!ready()) return null;
        Event event;
        while ((event = output.poll()) != null) {
            synchronized (budget) { outputBytes = Math.max(0, outputBytes - bytes(event.messages())); }
            Phase state = slots.get(event.id());
            if (event.kind() == Kind.CLOSED) { slots.put(event.id(), Phase.CLOSED); startingAt.remove(event.id()); return event; }
            if (state == Phase.CLOSING || state == Phase.CLOSED) continue;
            if (event.kind() == Kind.STARTED) { slots.put(event.id(), Phase.OPEN); startingAt.remove(event.id()); }
            return event;
        }
        return null;
    }
    private void enqueue(UUID id, Kind kind, String[] messages) {
        requireReady(); String[] copy = Objects.requireNonNull(messages).clone();
        if (copy.length > 256 || Arrays.stream(copy).anyMatch(Objects::isNull)) throw reject("Invalid simulator input batch");
        long size = bytes(Arrays.asList(copy));
        synchronized (budget) {
            if (size > 1024 * 1024 || inputBytes + size > MAX_BYTES) throw reject("Private simulator input budget exceeded");
            inputBytes += size;
            if (!input.offer(new Work(id, kind, copy, size))) { inputBytes -= size; throw reject("Private simulator input queue full"); }
        }
    }
    private void run(Factory factory) {
        Engine engine = null;
        try {
            if (stopping) return;
            engine = factory.open(this::capture); ready = !stopping && failure.get() == null;
            if (ready) initialized.complete(null);
            while (ready()) {
                Work work = input.poll(100, TimeUnit.MILLISECONDS); if (work == null) continue;
                synchronized (budget) { inputBytes = Math.max(0, inputBytes - work.bytes()); }
                current = work; batch = new ArrayList<>();
                try {
                    switch (work.kind()) {
                        case STARTED -> engine.start(work.id(), work.messages());
                        case OUTPUT -> engine.send(work.id(), work.messages());
                        case CLOSED -> engine.end(work.id());
                    }
                    if (work.kind() == Kind.STARTED && batch.isEmpty()) throw new IllegalStateException("Private simulator start returned no output");
                    if (work.kind() == Kind.STARTED && batch.stream().anyMatch(message -> message.contains("|error|"))) throw new IllegalStateException("Private simulator start returned an error");
                    if (ready() && (work.kind() != Kind.OUTPUT || !batch.isEmpty())) {
                        Event event = new Event(work.id(), work.kind(), List.copyOf(batch));
                        synchronized (budget) {
                            long size = bytes(event.messages());
                            if (outputBytes + size > MAX_BYTES || !output.offer(event)) throw new IllegalStateException("Private simulator output queue full");
                            outputBytes += size;
                        }
                    }
                } finally { current = null; batch = null; }
            }
        } catch (Throwable problem) { fail(problem); }
        finally {
            ready = false;
            if (engine != null) try { engine.close(); } catch (Throwable problem) { fail(problem); }
            clearQueues(); terminated = true;
            initialized.completeExceptionally(failure.get() == null ? new CancellationException("Private simulator stopped") : failure.get());
        }
    }
    private void capture(String id, String message) {
        if (stopping || failure.get() != null) return;
        if (Thread.currentThread() != worker) throw new IllegalStateException("Private simulator output escaped its worker");
        if (current == null) {
            if (!ready) return; // Native bootstrap only; no live ticket exists yet.
            throw new IllegalStateException("Private simulator emitted output outside its operation");
        }
        if (!current.id().toString().equals(id) || message == null) throw new IllegalStateException("Private simulator output identity mismatch");
        if (batch.size() >= 256 || bytes(batch) + (long) message.length() * 2 > 2L * 1024 * 1024) throw new IllegalStateException("Private simulator output batch too large");
        batch.add(message);
    }
    void fail(Throwable problem) { if (failure.compareAndSet(null, Objects.requireNonNull(problem))) { ready = false; clearQueues(); } }
    private RejectedExecutionException reject(String message) { var problem = new RejectedExecutionException(message); fail(problem); return problem; }
    private void clearQueues() { synchronized (budget) { input.clear(); output.clear(); inputBytes = 0; outputBytes = 0; } }
    private static long bytes(List<String> messages) { long size = 0; for (String message : messages) size += (long) message.length() * 2; return size; }
    private void requireOwner() { if (Thread.currentThread() != owner) throw new IllegalStateException("Simulator tickets require the server thread"); }
    private void requireReady() { if (!ready()) throw new RejectedExecutionException("Private world-boss simulator unavailable", failure.get()); }
    @Override public void close() throws Exception {
        requireOwner(); stopping = true; ready = false; clearQueues(); startingAt.clear();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); boolean interrupted = Thread.interrupted();
        try {
            while (worker.isAlive() && System.nanoTime() < deadline) {
                try { worker.join(100); } catch (InterruptedException stop) { interrupted = true; }
            }
            if (worker.isAlive()) { var timeout = new TimeoutException("Private simulator did not stop; restart required"); fail(timeout); throw timeout; }
            slots.clear(); clearQueues();
            if (failure.get() != null) throw new IllegalStateException("Private simulator failed", failure.get());
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}
