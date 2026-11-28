package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded horizontal arena footprint and ownership of one event's native tickets. */
final class WorldBossChunkLease {
    static final int MAX_CHUNKS = 1024;
    // Ten-block ring, one-block presence retention and the diagonal corners of a six-block-wide lane.
    static final double PLACEMENT_MARGIN = 16;
    static final int DEPENDENCY_RING = 2;
    record Chunk(int x, int z) {}
    record Plan(List<Chunk> retained, List<Chunk> required) {
        Plan { retained = List.copyOf(retained); required = List.copyOf(required); }
    }

    static Plan plan(double x, double z, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000
                || !Double.isFinite(radius) || radius < 16 || radius > 512) {
            throw new IllegalArgumentException("Invalid arena chunk footprint");
        }
        double extent = radius + PLACEMENT_MARGIN;
        int minX = (int) Math.floor((x - extent) / 16), maxX = (int) Math.floor((x + extent) / 16);
        int minZ = (int) Math.floor((z - extent) / 16), maxZ = (int) Math.floor((z + extent) / 16);
        long total = (long) (maxX - minX + 1 + 2 * DEPENDENCY_RING) * (maxZ - minZ + 1 + 2 * DEPENDENCY_RING);
        if (total > MAX_CHUNKS) throw new IllegalArgumentException("Arena and duel margin exceed the " + MAX_CHUNKS + "-chunk retention budget");
        List<Chunk> retained = new ArrayList<>(), required = new ArrayList<>();
        for (int cx = minX - DEPENDENCY_RING; cx <= maxX + DEPENDENCY_RING; cx++) {
            for (int cz = minZ - DEPENDENCY_RING; cz <= maxZ + DEPENDENCY_RING; cz++) {
                Chunk chunk = new Chunk(cx, cz);
                required.add(chunk);
                if (cx >= minX && cx <= maxX && cz >= minZ && cz <= maxZ) retained.add(chunk);
            }
        }
        return new Plan(retained, required);
    }

    interface Backend {
        boolean loaded(Chunk chunk);
        void retain(Chunk chunk, UUID event);
        void release(Chunk chunk, UUID event);
    }

    private final UUID event;
    private final Plan plan;
    private final Backend backend;
    private final LinkedHashSet<Chunk> owned = new LinkedHashSet<>();
    private boolean closed;

    WorldBossChunkLease(UUID event, Plan plan, Backend backend) {
        this.event = Objects.requireNonNull(event);
        this.plan = Objects.requireNonNull(plan);
        this.backend = Objects.requireNonNull(backend);
    }

    boolean matches(UUID id, Plan requested) { return !closed && event.equals(id) && plan.equals(requested); }
    int ownedCount() { return owned.size(); }

    /** A partial add is rolled back, including the possibly side-effecting failing operation. */
    void renew() {
        if (closed) throw new IllegalStateException("Arena chunk lease is closed");
        try {
            for (Chunk chunk : plan.required()) {
                if (!backend.loaded(chunk)) throw new IllegalStateException("Arena chunk " + chunk.x() + ", " + chunk.z() + " must already be fully loaded");
            }
            for (Chunk chunk : plan.retained()) {
                owned.add(chunk);
                backend.retain(chunk, event);
            }
        } catch (RuntimeException failure) {
            try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Failed releases remain owned for a later retry; successful releases are never repeated. */
    void close() {
        closed = true;
        RuntimeException failure = null;
        for (var iterator = owned.iterator(); iterator.hasNext();) {
            Chunk chunk = iterator.next();
            try { backend.release(chunk, event); iterator.remove(); }
            catch (RuntimeException problem) {
                if (failure == null) failure = problem;
                else failure.addSuppressed(problem);
            }
        }
        if (failure != null) throw failure;
    }
}
