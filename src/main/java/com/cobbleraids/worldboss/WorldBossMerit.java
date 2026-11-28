package com.cobbleraids.worldboss;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Event-scoped, monotonic qualification reconstructed from accepted action receipts. */
public final class WorldBossMerit {
    public static final int REQUIRED = 100, PER_TURN = 10, MAX_DUEL_TURNS = 5, FOCUS_MERIT = 20, RELAY_MERIT = 30;
    public enum Evidence { ACCEPT, DUPLICATE, INVALID }
    public record Duel(UUID player, long lastSequence, int lastTurn, int scoredTurns) {
        public Duel {
            java.util.Objects.requireNonNull(player);
            if (lastSequence < 0 || lastSequence > 9_007_199_254_740_991L || lastTurn < 0 || scoredTurns < 0 || scoredTurns > MAX_DUEL_TURNS
                    || scoredTurns > lastTurn || scoredTurns > lastSequence || lastTurn > 0 && scoredTurns == 0 || lastSequence == 0 && lastTurn != 0) throw new IllegalArgumentException("Invalid Merit duel receipt");
        }
    }
    public record Focus(UUID player, int window) {
        public Focus { java.util.Objects.requireNonNull(player); if (window < 1 || window > 2) throw new IllegalArgumentException("Invalid focus receipt"); }
    }
    public record Relay(UUID player, int window) {
        public Relay { java.util.Objects.requireNonNull(player); if (window < 1 || window > 2) throw new IllegalArgumentException("Invalid relay receipt"); }
    }
    public record Snapshot(Map<UUID, Duel> duels, Set<Focus> focuses, Set<Relay> relays, int victoryReward, int timeoutReward, int cancellationReward) {
        public Snapshot(Map<UUID, Duel> duels, Set<Focus> focuses, int victoryReward, int timeoutReward, int cancellationReward) {
            this(duels, focuses, Set.of(), victoryReward, timeoutReward, cancellationReward);
        }
        public Snapshot {
            duels = Map.copyOf(duels); focuses = Set.copyOf(focuses); relays = Set.copyOf(relays);
            if (victoryReward < 0 || timeoutReward < 0 || cancellationReward < 0) throw new IllegalArgumentException("Invalid frozen Merit rewards");
        }
    }

    private final Map<UUID, Duel> duels = new LinkedHashMap<>();
    private final Set<Focus> focuses = new LinkedHashSet<>();
    private final Set<Relay> relays = new LinkedHashSet<>();
    private final Map<UUID, Integer> totals = new LinkedHashMap<>();
    private final int victoryReward, timeoutReward, cancellationReward;

    public WorldBossMerit() { victoryReward = 200; timeoutReward = 50; cancellationReward = 200; }
    public WorldBossMerit(Snapshot snapshot) {
        victoryReward = snapshot.victoryReward(); timeoutReward = snapshot.timeoutReward(); cancellationReward = snapshot.cancellationReward();
        duels.putAll(snapshot.duels()); focuses.addAll(snapshot.focuses()); relays.addAll(snapshot.relays());
        duels.values().forEach(duel -> add(duel.player(), duel.scoredTurns() * PER_TURN));
        focuses.forEach(focus -> add(focus.player(), FOCUS_MERIT));
        relays.forEach(relay -> add(relay.player(), RELAY_MERIT));
    }

    public void register(UUID encounter, UUID player) {
        java.util.Objects.requireNonNull(encounter);
        var existing = duels.putIfAbsent(encounter, new Duel(player, 0, 0, 0));
        if (existing != null && !existing.player().equals(player)) throw new IllegalStateException("Merit encounter owner changed");
    }

    public Evidence inspect(UUID encounter, UUID player, int turn, long sequence) {
        Duel receipt = duels.get(encounter);
        if (receipt == null || !receipt.player().equals(player) || turn < 0 || sequence < 1 || sequence > 9_007_199_254_740_991L) return Evidence.INVALID;
        if (sequence <= receipt.lastSequence()) return Evidence.DUPLICATE;
        return turn < receipt.lastTurn() ? Evidence.INVALID : Evidence.ACCEPT;
    }

    /** Call after the corresponding accepted shared damage; before deciding a killing outcome. */
    public void damaged(UUID encounter, UUID player, int turn, long sequence) {
        if (inspect(encounter, player, turn, sequence) != Evidence.ACCEPT) throw new IllegalStateException("Unaccepted damage receipt");
        Duel old = duels.get(encounter);
        int scored = old.scoredTurns() + (turn > old.lastTurn() && old.scoredTurns() < MAX_DUEL_TURNS ? 1 : 0);
        duels.put(encounter, new Duel(player, sequence, turn, scored));
        add(player, (scored - old.scoredTurns()) * PER_TURN);
    }

    public boolean focused(UUID player, int window) {
        if (!focuses.add(new Focus(player, window))) return false;
        add(player, FOCUS_MERIT); return true;
    }
    public boolean relayed(UUID player, int window) {
        if (!relays.add(new Relay(player, window))) return false;
        add(player, RELAY_MERIT); return true;
    }
    private void add(UUID player, int points) { if (points > 0) totals.compute(player, (key, old) -> Math.min(REQUIRED, (old == null ? 0 : old) + points)); }
    public int score(UUID player) { return totals.getOrDefault(player, 0); }
    public boolean qualified(UUID player) { return score(player) >= REQUIRED; }
    public Set<UUID> qualifiedPlayers() {
        Set<UUID> players = new LinkedHashSet<>();
        players.addAll(totals.keySet());
        players.removeIf(player -> !qualified(player)); return Set.copyOf(players);
    }
    public int victoryReward() { return victoryReward; }
    public int timeoutReward() { return timeoutReward; }
    public int cancellationReward() { return cancellationReward; }
    public Snapshot snapshot() { return new Snapshot(duels, focuses, relays, victoryReward, timeoutReward, cancellationReward); }
}
