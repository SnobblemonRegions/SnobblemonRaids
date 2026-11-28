package com.cobbleraids.worldboss;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Unacknowledged entry requests are runtime-only; originals are committed immediately before travel. */
final class WorldBossArrivalQueue<T> {
    enum Mode { WARP, FIGHT, WATCH }
    static final int CAPACITY = 75, PER_TICK = 2;
    static final long TIMEOUT_NANOS = 30_000_000_000L;
    record Request<T>(UUID player, UUID event, Mode mode, T owner, WorldBossTravel.Position source, long createdNanos) {
        boolean expired(long now) { long elapsed = now - createdNanos; return elapsed < 0 || elapsed >= TIMEOUT_NANOS; }
    }
    private final LinkedHashMap<UUID, Request<T>> waiting = new LinkedHashMap<>();
    boolean submit(Request<T> request) {
        java.util.Objects.requireNonNull(request.player()); java.util.Objects.requireNonNull(request.event()); java.util.Objects.requireNonNull(request.mode()); java.util.Objects.requireNonNull(request.owner()); java.util.Objects.requireNonNull(request.source());
        var old = waiting.get(request.player());
        if (old != null && old.owner() == request.owner() && old.mode() == request.mode() && old.event().equals(request.event())) return true;
        if (old == null && waiting.size() >= CAPACITY) return false;
        waiting.remove(request.player()); waiting.put(request.player(), request); return true;
    }
    List<Request<T>> take() {
        var result = new java.util.ArrayList<Request<T>>(); var iterator = waiting.values().iterator();
        while (iterator.hasNext() && result.size() < PER_TICK) { result.add(iterator.next()); iterator.remove(); }
        return List.copyOf(result);
    }
    int position(UUID player) { int index = 0; for (UUID id : waiting.keySet()) { index++; if (id.equals(player)) return index; } return 0; }
    boolean pending(UUID player) { return waiting.containsKey(player); }
    java.util.Set<UUID> watchers() {
        var result = new java.util.HashSet<UUID>(); waiting.values().forEach(request -> { if (request.mode() == Mode.WATCH) result.add(request.player()); }); return java.util.Set.copyOf(result);
    }
    void cancel(UUID player) { waiting.remove(player); }
    List<Request<T>> all() { return List.copyOf(waiting.values()); }
    void clear() { waiting.clear(); }
}
