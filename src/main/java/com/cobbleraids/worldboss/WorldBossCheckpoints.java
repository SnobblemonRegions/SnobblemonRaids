package com.cobbleraids.worldboss;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;

/** Single outstanding periodic checkpoint; critical commits must drain it first. */
final class WorldBossCheckpoints implements AutoCloseable {
    private final WorldBossJournal journal;
    private final java.util.function.LongSupplier nanoTime;
    private final Thread owner = Thread.currentThread();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> Thread.ofPlatform().daemon(true).inheritInheritableThreadLocals(false)
                    .name("World Boss journal checkpoints").unstarted(runnable), new ThreadPoolExecutor.AbortPolicy());
    private CompletableFuture<Void> pending;
    private volatile IOException failure;
    private boolean closed;
    private long submittedAt;
    WorldBossCheckpoints(WorldBossJournal journal) { this(journal, System::nanoTime); }
    WorldBossCheckpoints(WorldBossJournal journal, java.util.function.LongSupplier nanoTime) {
        this.journal = java.util.Objects.requireNonNull(journal); this.nanoTime = java.util.Objects.requireNonNull(nanoTime);
    }
    boolean pending() { requireOwner(); return pending != null; }
    boolean overdue() {
        requireOwner(); return pending != null && !pending.isDone() && nanoTime.getAsLong() - submittedAt >= TimeUnit.SECONDS.toNanos(2);
    }
    boolean failed() { return failure != null; }
    void submit(CompoundTag root, List<WorldBossJournal.Promise> promises) throws IOException {
        requireOwner(); checkFailure();
        if (closed) throw new IOException("World Boss checkpoints are closed");
        if (pending != null) throw new IllegalStateException("A checkpoint is already outstanding");
        CompoundTag frozen = root.copy(); List<WorldBossJournal.Promise> captured = List.copyOf(promises);
        CompletableFuture<Void> completion = new CompletableFuture<>(); pending = completion; submittedAt = nanoTime.getAsLong();
        try {
            worker.execute(() -> {
                try { journal.commit(frozen, captured); completion.complete(null); }
                catch (Throwable problem) {
                    failure = problem instanceof IOException io ? io : new IOException("World Boss checkpoint failed", problem);
                    completion.completeExceptionally(failure);
                    if (problem instanceof Error fatal) throw fatal;
                }
            });
        } catch (RuntimeException rejected) {
            failure = new IOException("World Boss checkpoint was rejected", rejected);
            completion.completeExceptionally(failure); throw failure;
        }
    }
    boolean poll() throws IOException {
        requireOwner(); checkFailure();
        if (pending == null || !pending.isDone()) return false;
        drain(); return true;
    }
    void drain() throws IOException {
        requireOwner(); checkFailure();
        if (pending == null) return;
        try { pending.get(); pending = null; }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); failure = new IOException("World Boss checkpoint wait interrupted", interrupted); throw failure;
        } catch (ExecutionException failed) {
            throw failure == null ? new IOException("World Boss checkpoint failed", failed.getCause()) : failure;
        }
    }
    private void checkFailure() throws IOException { if (failure != null) throw failure; }
    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Checkpoint control requires its server thread");
    }
    @Override public void close() throws IOException {
        requireOwner(); closed = true;
        worker.shutdown();
        boolean interrupted = Thread.interrupted();
        try {
            // Even a failed/interrupted caller must not release a recovery set while accepted IO can still mutate it.
            while (pending != null) {
                try { pending.get(); pending = null; }
                catch (InterruptedException stop) {
                    interrupted = true;
                    if (failure == null) failure = new IOException("World Boss checkpoint close interrupted", stop);
                } catch (ExecutionException failed) {
                    if (failure == null) failure = new IOException("World Boss checkpoint failed", failed.getCause());
                    pending = null;
                }
            }
            checkFailure();
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}
