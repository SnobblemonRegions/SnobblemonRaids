package com.cobbleraids.worldboss;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Seats include pending arrivals; existing viewers retain priority during reconciliation. */
final class WorldBossAudience {
    static final int CAPACITY = 25;
    private final LinkedHashSet<UUID> seats = new LinkedHashSet<>();
    boolean admit(UUID player) {
        java.util.Objects.requireNonNull(player);
        if (seats.contains(player)) return true;
        if (seats.size() >= CAPACITY) return false;
        seats.add(player); return true;
    }
    boolean contains(UUID player) { return seats.contains(player); }
    int size() { return seats.size(); }
    void release(UUID player) { seats.remove(player); }
    Set<UUID> reconcile(Collection<UUID> present, Set<UUID> pending) {
        var visible = new LinkedHashSet<>(present);
        seats.removeIf(id -> !visible.contains(id) && !pending.contains(id));
        // Pending native/queued arrivals are admitted at request time, not invented during this pass.
        var overflow = new LinkedHashSet<UUID>();
        for (UUID id : visible) if (!admit(id)) overflow.add(id);
        return Set.copyOf(overflow);
    }
    void clear() { seats.clear(); }
}
