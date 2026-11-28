package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Two nonblocking ward windows. Deadlines use remaining active milliseconds, so recovery pauses them. */
public final class WorldBossWards {
    public static final long WINDOW_MILLIS = 45_000;
    public enum Status { ACTIVE, SUCCESS, FAILED, CANCELLED }
    public record Pylon(String dimension, int x, int y, int z) {
        public Pylon { if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid ward dimension"); }
    }
    public record CombatTurn(UUID encounter, UUID player, int turn) {
        public CombatTurn { java.util.Objects.requireNonNull(encounter); java.util.Objects.requireNonNull(player); if (turn < 1) throw new IllegalArgumentException("Invalid ward turn"); }
    }
    public record Window(int serial, int quorum, long endRemainingMillis, Status status,
                         Set<CombatTurn> turns, Set<UUID> focuses, Set<UUID> relays) {
        public Window(int serial, int quorum, long endRemainingMillis, Status status, Set<CombatTurn> turns, Set<UUID> focuses) {
            this(serial, quorum, endRemainingMillis, status, turns, focuses, Set.of());
        }
        public Window {
            java.util.Objects.requireNonNull(status); turns = Set.copyOf(turns); focuses = Set.copyOf(focuses); relays = Set.copyOf(relays);
            if (serial < 1 || serial > 2 || quorum < 1 || quorum > 5 || endRemainingMillis < 0 || endRemainingMillis > WorldBossClock.ACTIVE_MILLIS - WINDOW_MILLIS) throw new IllegalArgumentException("Invalid ward window");
            Map<UUID, Integer> combatCounts = counts(turns, Set.of(), Set.of());
            if (combatCounts.values().stream().anyMatch(count -> count > 3)) throw new IllegalArgumentException("Excess ward combat receipts");
            Map<UUID, Integer> counts = counts(turns, focuses, relays);
            boolean goal = units(counts) >= 3 * quorum && counts.size() >= quorum;
            if (status == Status.SUCCESS && !goal || status == Status.FAILED && goal) throw new IllegalArgumentException("Ward result contradicts receipts");
        }
        public int units() { return units(counts(turns, focuses, relays)); }
        public int people() { return counts(turns, focuses, relays).size(); }
        public boolean goalMet() { return units() >= 3 * quorum && people() >= quorum; }
        private static Map<UUID, Integer> counts(Set<CombatTurn> turns, Set<UUID> focuses, Set<UUID> relays) {
            Map<UUID, Integer> counts = new HashMap<>(); turns.forEach(turn -> counts.merge(turn.player(), 1, Integer::sum));
            focuses.forEach(player -> counts.merge(player, 1, Integer::sum)); relays.forEach(player -> counts.merge(player, 3, Integer::sum)); return counts;
        }
        private static int units(Map<UUID, Integer> counts) { return counts.values().stream().mapToInt(value -> Math.min(3, value)).sum(); }
        int unitsOf(UUID player) { return Math.min(3, counts(turns, focuses, relays).getOrDefault(player, 0)); }
        Window combat(CombatTurn token) {
            if (unitsOf(token.player()) >= 3 || turns.contains(token)) return this;
            var updated = new LinkedHashSet<>(turns); updated.add(token);
            return new Window(serial, quorum, endRemainingMillis, status, updated, focuses, relays);
        }
        Window focus(UUID player) {
            var updated = new LinkedHashSet<>(focuses); updated.add(player);
            return new Window(serial, quorum, endRemainingMillis, status, turns, updated, relays);
        }
        Window relay(UUID player) {
            var updated = new LinkedHashSet<>(relays); updated.add(player);
            return new Window(serial, quorum, endRemainingMillis, status, turns, focuses, updated);
        }
        Window close(Status result) { return new Window(serial, quorum, endRemainingMillis, result, turns, focuses, relays); }
    }
    public record Snapshot(Pylon pylon, WorldBossRelay.Route route, int crossed, boolean stopped, List<Window> completed, Window active) {
        public Snapshot(Pylon pylon, int crossed, boolean stopped, List<Window> completed, Window active) { this(pylon, null, crossed, stopped, completed, active); }
        public Snapshot {
            completed = List.copyOf(completed);
            if (route != null) route.validate(pylon);
            if (route == null && (completed.stream().anyMatch(window -> !window.relays().isEmpty()) || active != null && !active.relays().isEmpty())) throw new IllegalArgumentException("Relay receipts have no route");
            if (crossed != 0 && crossed != 1 && crossed != 3 || completed.size() > 2 || stopped && active != null) throw new IllegalArgumentException("Invalid ward progression");
            for (int i = 0; i < completed.size(); i++) if (completed.get(i).serial() != i + 1 || completed.get(i).status() == Status.ACTIVE
                    || completed.get(i).status() == Status.CANCELLED && !stopped) throw new IllegalArgumentException("Invalid ward history");
            int opened = completed.size() + (active == null ? 0 : 1);
            if (opened > 2 || opened > Integer.bitCount(crossed) || active != null && (active.serial() != completed.size() + 1 || active.status() != Status.ACTIVE)) throw new IllegalArgumentException("Invalid active ward");
            if (completed.size() == 2 && completed.get(1).endRemainingMillis() >= completed.getFirst().endRemainingMillis()
                    || active != null && !completed.isEmpty() && active.endRemainingMillis() >= completed.getLast().endRemainingMillis()) throw new IllegalArgumentException("Overlapping saved ward windows");
        }
    }

    private final Pylon pylon;
    private final WorldBossRelay.Route route;
    private int crossed;
    private boolean stopped;
    private final List<Window> completed = new ArrayList<>();
    private Window active;

    public WorldBossWards() { this(null, null); }
    public WorldBossWards(Pylon pylon) { this(pylon, null); }
    public WorldBossWards(Pylon pylon, WorldBossRelay.Route route) { if (route != null) route.validate(pylon); this.pylon = pylon; this.route = route; }
    public WorldBossWards(Snapshot snapshot) { pylon = snapshot.pylon(); route = snapshot.route(); crossed = snapshot.crossed(); stopped = snapshot.stopped(); completed.addAll(snapshot.completed()); active = snapshot.active(); }
    public Pylon pylon() { return pylon; }
    WorldBossRelay.Route route() { return route; }
    public Window active() { return active; }
    public Snapshot snapshot() { return new Snapshot(pylon, route, crossed, stopped, completed, active); }

    public void damaged(long maxUnits, long currentUnits, long remainingMillis, int ready) {
        if (maxUnits < 1 || maxUnits > WorldBossFixedHealth.MAX_WHOLE_HP * 1000 || currentUnits < 0 || currentUnits > maxUnits) throw new IllegalArgumentException("Invalid ward health");
        if (stopped || currentUnits <= 0) return;
        if (currentUnits * 100 <= maxUnits * 70) crossed |= 1;
        if (currentUnits * 100 <= maxUnits * 35) crossed |= 3;
        advance(remainingMillis, ready);
    }
    public void advance(long remainingMillis, int ready) {
        if (remainingMillis < 0 || remainingMillis > WorldBossClock.ACTIVE_MILLIS || ready < 0 || ready > 50) throw new IllegalArgumentException("Invalid ward clock/readiness");
        if (stopped) return;
        if (active != null && remainingMillis <= active.endRemainingMillis()) { completed.add(active.close(active.goalMet() ? Status.SUCCESS : Status.FAILED)); active = null; }
        if (active == null && completed.size() < Integer.bitCount(crossed) && remainingMillis > 0) {
            int quorum = Math.max(1, Math.min(5, (ready + 9) / 10));
            active = new Window(completed.size() + 1, quorum, Math.max(0, remainingMillis - WINDOW_MILLIS), Status.ACTIVE, Set.of(), Set.of());
        }
    }
    public void combat(UUID encounter, UUID player, int turn) {
        if (active != null && turn > 0) active = active.combat(new CombatTurn(encounter, player, turn));
    }
    public boolean focus(UUID player, int serial, long remainingMillis) {
        if (active == null || active.serial() != serial || remainingMillis <= active.endRemainingMillis() || active.focuses().contains(player)) return false;
        active = active.focus(player); return true;
    }
    public boolean relay(UUID player, int serial, long remainingMillis) {
        if (route == null || active == null || active.serial() != serial || remainingMillis <= active.endRemainingMillis() || active.relays().contains(player)) return false;
        active = active.relay(player); return true;
    }
    public void stop() {
        stopped = true;
        if (active != null) { completed.add(active.close(Status.CANCELLED)); active = null; }
    }
}
