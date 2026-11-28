package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread-owned participant slots and FIFO ready queue. A reservation counts toward the
 * cap before an encounter is created. Only the exact event/player/generation token may finish it.
 * The Minecraft adapter supplies readiness; this class never polls players or starts battles.
 */
public final class WorldBossAdmission {
    public enum Phase { RESERVED, DUELLING, BREATHER }
    public record Token(UUID eventId, UUID playerId, long generation, UUID encounterId) {}
    public record Slot(Token token, Phase phase, long availableAtMillis) {}
    public record Snapshot(UUID event, int capacity, long breatherMillis, long generation, boolean closed, boolean cutoffEnabled, long checkpointMillis,
                           List<Slot> slots, List<UUID> waiting, Map<UUID, Long> cooldowns, Set<UUID> admitted) {
        public Snapshot {
            java.util.Objects.requireNonNull(event); slots = List.copyOf(slots); waiting = List.copyOf(waiting); cooldowns = Map.copyOf(cooldowns); admitted = Set.copyOf(admitted);
            if (capacity < 1 || capacity > 50 || breatherMillis < 0 || generation < 0 || generation > Long.MAX_VALUE - 50 || checkpointMillis <= 0 || slots.size() > capacity) throw new IllegalArgumentException("Invalid admission checkpoint");
            var players = new java.util.HashSet<UUID>(); var encounters = new java.util.HashSet<UUID>(); var generations = new java.util.HashSet<Long>();
            for (var slot : slots) {
                var token = slot.token();
                if (token == null || token.playerId() == null || token.encounterId() == null || slot.phase() == null || !event.equals(token.eventId()) || token.generation() < 1 || token.generation() > generation
                        || slot.availableAtMillis() < 0 || slot.phase() != Phase.BREATHER && slot.availableAtMillis() != 0 || !players.add(token.playerId()) || !encounters.add(token.encounterId()) || !generations.add(token.generation())) throw new IllegalArgumentException("Invalid saved participant slot");
            }
            for (var player : waiting) if (!players.add(player)) throw new IllegalArgumentException("Duplicate queued participant");
            if (cooldowns.values().stream().anyMatch(value -> value < 0) || closed && (!waiting.isEmpty() || slots.stream().anyMatch(slot -> slot.phase() != Phase.DUELLING))) throw new IllegalArgumentException("Invalid saved admission intent");
        }
        public Snapshot without(UUID player) {
            return new Snapshot(event, capacity, breatherMillis, generation, closed, cutoffEnabled, checkpointMillis, slots.stream().filter(slot -> !slot.token().playerId().equals(player)).toList(), waiting.stream().filter(id -> !id.equals(player)).toList(), cooldowns, admitted);
        }
        /** Recovery candidates retain order; new regroup arrivals append behind them. */
        public Snapshot mergeRegroup(Snapshot current, long now) {
            if (!event.equals(current.event()) || capacity != current.capacity() || breatherMillis != current.breatherMillis() || cutoffEnabled != current.cutoffEnabled()) throw new IllegalArgumentException("Recovery checkpoint owner changed");
            if (now < checkpointMillis) throw new IllegalArgumentException("Recovery checkpoint is in the future");
            var occupied = new LinkedHashSet<UUID>(); slots.forEach(slot -> occupied.add(slot.token().playerId()));
            var order = new LinkedHashSet<>(waiting); current.waiting().forEach(player -> { if (!occupied.contains(player)) order.add(player); });
            var delays = new LinkedHashMap<>(cooldowns); current.cooldowns().forEach((player, time) -> delays.merge(player, time, Math::max));
            var known = new LinkedHashSet<>(admitted); known.addAll(current.admitted());
            return new Snapshot(event, capacity, breatherMillis, Math.max(generation, current.generation()), closed, cutoffEnabled, now, slots, List.copyOf(order), delays, known);
        }
    }

    private final UUID eventId;
    private final int capacity;
    private final long breatherMillis;
    private final boolean cutoffEnabled;
    private final Map<UUID, Slot> slots = new LinkedHashMap<>();
    private final Set<UUID> queue = new LinkedHashSet<>();
    private final Set<UUID> paused = new LinkedHashSet<>();
    private final Map<UUID, Long> cooldowns = new LinkedHashMap<>();
    private final Set<UUID> admitted = new LinkedHashSet<>();
    private long generation;
    private long revision;
    private boolean closed;

    public WorldBossAdmission(UUID eventId, int capacity, long breatherMillis, Set<UUID> pausedPlayers) {
        this(eventId, capacity, breatherMillis, pausedPlayers, true);
    }
    public WorldBossAdmission(UUID eventId, int capacity, long breatherMillis, Set<UUID> pausedPlayers, boolean cutoffEnabled) {
        this.eventId = java.util.Objects.requireNonNull(eventId, "eventId");
        if (capacity < 1 || capacity > 50 || breatherMillis < 0) throw new IllegalArgumentException("Invalid admission limits");
        this.capacity = capacity;
        this.breatherMillis = breatherMillis;
        this.cutoffEnabled = cutoffEnabled;
        paused.addAll(pausedPlayers);
    }

    /** Reconcile eligibility without granting priority to an unavailable, paused or absent player. */
    public void ready(UUID player, boolean ready) {
        java.util.Objects.requireNonNull(player, "player");
        if (!ready || paused.contains(player) || closed) {
            boolean changed = queue.remove(player);
            Slot slot = slots.get(player);
            if (slot != null && slot.phase() == Phase.BREATHER) changed |= slots.remove(player, slot);
            if (changed) revision++;
        } else if (!slots.containsKey(player)) {
            if (queue.add(player)) revision++;
        }
    }

    public void pause(UUID player) {
        if (paused.add(java.util.Objects.requireNonNull(player, "player"))) revision++;
        queue.remove(player);
        Slot slot = slots.get(player);
        if (slot != null && slot.phase() != Phase.DUELLING) slots.remove(player, slot);
    }

    public void resume(UUID player, boolean ready) {
        if (paused.remove(player)) revision++;
        ready(player, ready);
    }

    /** Cancels queued/reserved work; an already-started duel finishes before its slot is released. */
    public void depart(UUID player) {
        boolean changed = queue.remove(player);
        Slot slot = slots.get(player);
        if (slot != null && slot.phase() != Phase.DUELLING) changed |= slots.remove(player, slot);
        if (changed) revision++;
    }

    /** All new and chained starts share this budget and the same reservation operation. */
    public List<Token> reserve(int budget, long nowMillis, boolean combatOpen, Set<UUID> readyPlayers) {
        if (budget < 0) throw new IllegalArgumentException("Negative admission budget");
        if (closed || !combatOpen || budget == 0) return List.of();
        List<Token> result = new ArrayList<>();
        for (var entry : new ArrayList<>(slots.entrySet())) {
            if (result.size() == budget) break;
            Slot slot = entry.getValue();
            UUID player = entry.getKey();
            if (slot.phase() != Phase.BREATHER) continue;
            if (!readyPlayers.contains(player) || paused.contains(player)) {
                if (slots.remove(player, slot)) revision++;
            } else if (nowMillis >= slot.availableAtMillis()) {
                result.add(reserveSlot(player));
            }
        }
        if (queue.removeIf(player -> !readyPlayers.contains(player) || paused.contains(player))) revision++;
        for (UUID player : new ArrayList<>(queue)) {
            if (result.size() == budget || slots.size() == capacity) break;
            if (nowMillis < cooldowns.getOrDefault(player, 0L)) continue;
            queue.remove(player);
            result.add(reserveSlot(player));
        }
        return List.copyOf(result);
    }

    private Token reserveSlot(UUID player) {
        generation = Math.incrementExact(generation);
        Token token = new Token(eventId, player, generation, UUID.randomUUID());
        slots.put(player, new Slot(token, Phase.RESERVED, 0));
        revision++;
        return token;
    }

    public boolean started(Token token) {
        Slot slot = matching(token);
        if (closed || slot == null || slot.phase() != Phase.RESERVED || paused.contains(token.playerId())) return false;
        slots.put(token.playerId(), new Slot(token, Phase.DUELLING, 0));
        revision++;
        return true;
    }
    public boolean confirm(Token token) {
        var slot = matching(token); if (slot == null || slot.phase() != Phase.DUELLING) return false;
        if (admitted.add(token.playerId())) revision++; return true;
    }
    public boolean admittedBefore(UUID player) { return admitted.contains(player); }
    public boolean eligibleAt(UUID player, long remainingMillis) { return !cutoffEnabled || remainingMillis > 60_000 || admittedBefore(player); }
    public long revision() { return revision; }
    public Snapshot snapshot(long nowMillis) { return new Snapshot(eventId, capacity, breatherMillis, generation, closed, cutoffEnabled, nowMillis, List.copyOf(slots.values()), waiting(), cooldowns, admitted); }
    public static WorldBossAdmission intent(Snapshot snapshot, Set<UUID> paused) {
        var result = new WorldBossAdmission(snapshot.event(), snapshot.capacity(), snapshot.breatherMillis(), paused, snapshot.cutoffEnabled());
        result.generation = snapshot.generation(); result.cooldowns.putAll(snapshot.cooldowns()); result.admitted.addAll(snapshot.admitted()); result.closed = snapshot.closed(); return result;
    }
    public static WorldBossAdmission recover(Snapshot snapshot, List<UUID> readyOrder, Set<UUID> paused, long nowMillis) {
        if (nowMillis < snapshot.checkpointMillis()) throw new IllegalArgumentException("Admission checkpoint is in the future");
        var result = intent(snapshot, paused);
        if (result.closed) return result;
        var ready = new LinkedHashSet<>(readyOrder); ready.removeAll(paused);
        for (var old : snapshot.slots()) if (ready.contains(old.token().playerId())) {
            UUID player = old.token().playerId(); var fresh = result.reserveSlot(player);
            result.slots.put(player, new Slot(fresh, Phase.BREATHER, Math.max(old.availableAtMillis(), result.cooldowns.getOrDefault(player, 0L))));
        }
        for (UUID player : snapshot.waiting()) if (ready.contains(player)) result.ready(player, true);
        for (UUID player : readyOrder) if (ready.contains(player)) result.ready(player, true);
        return result;
    }

    /** A ward can open while an earlier reservation in the same start batch is being created. */
    public boolean defer(Token token, long nowMillis) {
        Slot slot = matching(token);
        if (slot == null || slot.phase() != Phase.RESERVED) return false;
        slots.put(token.playerId(), new Slot(token, Phase.BREATHER, Math.max(nowMillis, cooldowns.getOrDefault(token.playerId(), 0L))));
        admitted.add(token.playerId());
        revision++;
        return true;
    }

    /** Ward work needs real slots after recovery too, without creating private battles. */
    public void admitWardWaiters(int budget, long nowMillis, Set<UUID> readyPlayers) {
        if (budget < 0) throw new IllegalArgumentException("Negative ward admission budget");
        if (closed) return;
        if (queue.removeIf(player -> !readyPlayers.contains(player) || paused.contains(player))) revision++;
        int added = 0;
        for (UUID player : new ArrayList<>(queue)) {
            if (added == budget || slots.size() == capacity) break;
            queue.remove(player);
            Token token = reserveSlot(player);
            slots.put(player, new Slot(token, Phase.BREATHER, Math.max(nowMillis, cooldowns.getOrDefault(player, 0L))));
            admitted.add(player);
            added++;
        }
    }

    public boolean refused(Token token) {
        Slot slot = matching(token);
        boolean removed = slot != null && slot.phase() == Phase.RESERVED && slots.remove(token.playerId(), slot); if (removed) revision++; return removed;
    }

    /** Adapter-only rollback when encounter creation returned refusal, including reentrant starts. */
    public boolean failedStart(Token token) {
        Slot slot = matching(token);
        boolean removed = slot != null && slot.phase() != Phase.BREATHER && slots.remove(token.playerId(), slot); if (removed) revision++; return removed;
    }

    /** A failed adapter attempt releases capacity and cannot be retried on every server tick. */
    public boolean failedStart(Token token, long nowMillis, long retryMillis) {
        if (retryMillis < 0) throw new IllegalArgumentException("Negative retry delay");
        long available = Math.addExact(nowMillis, retryMillis);
        if (!failedStart(token)) return false;
        cooldowns.merge(token.playerId(), available, Math::max);
        return true;
    }

    /** Reconcile one presence sample. Unavailable breathers release capacity; active duels finish. */
    public void reconcileReady(List<UUID> readyPlayers) {
        Set<UUID> readySet = new LinkedHashSet<>(readyPlayers);
        for (UUID player : new ArrayList<>(queue)) if (!readySet.contains(player)) ready(player, false);
        for (UUID player : new ArrayList<>(slots.keySet())) if (!readySet.contains(player)) ready(player, false);
        for (UUID player : readySet) ready(player, true);
    }

    public boolean ended(Token token, long nowMillis, boolean retainSlot) {
        Slot slot = matching(token);
        if (slot == null || slot.phase() != Phase.DUELLING) return false;
        long available = Math.addExact(nowMillis, breatherMillis);
        cooldowns.put(token.playerId(), available);
        if (closed || paused.contains(token.playerId()) || !retainSlot) slots.remove(token.playerId(), slot);
        else slots.put(token.playerId(), new Slot(token, Phase.BREATHER, available));
        revision++;
        return true;
    }

    public boolean owns(Token token) { return matching(token) != null; }
    public boolean isPaused(UUID player) { return paused.contains(player); }
    public int occupied() { return slots.size(); }
    public Map<UUID, Slot> slots() { return Map.copyOf(slots); }
    public Set<UUID> pausedPlayers() { return Set.copyOf(paused); }
    public List<UUID> waiting() { return List.copyOf(queue); }
    public int queuePosition(UUID player) {
        int position = 0;
        for (UUID waiting : queue) { position++; if (waiting.equals(player)) return position; }
        return 0;
    }

    public void close() {
        if (closed) return;
        closed = true;
        queue.clear();
        slots.entrySet().removeIf(entry -> entry.getValue().phase() != Phase.DUELLING);
        revision++;
    }

    private Slot matching(Token token) {
        if (token == null || !eventId.equals(token.eventId())) return null;
        Slot slot = slots.get(token.playerId());
        return slot != null && slot.token().equals(token) ? slot : null;
    }
}
