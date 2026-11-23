package com.cobbleraids.spawn;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.stream.Collectors;

/**
 * The decisions behind where a wild raid spawns, kept free of Minecraft so they are tested: which players a raid may be spawned
 * around, whether a spot is at a reachable height, which failures pass the spawn on to another player, and how the reasons a
 * search rejected spots are reported. {@link RaidSpawnScheduler} and {@link RaidSpawnPositionFinder} gather the inputs.
 */
public final class RaidSpawnRules {

    /** What the scheduler knows about a player when choosing who a raid is spawned around. */
    public record PlayerState(boolean alive, boolean spectator, boolean creative, boolean inBattle, boolean inRaid,
                              long idleMillis, boolean dimensionHasRaids) {}

    /** Why a player is passed over; NONE means a raid may be spawned around them. */
    public enum Skip { NONE, DEAD, SPECTATOR, CREATIVE, IN_BATTLE, IN_RAID, AFK, NO_RAIDS_HERE }

    /** Why the position search turned a candidate spot down, in the order the checks run. */
    public enum Rejection {
        UNLOADED("in an unloaded chunk"),
        WORLD_BORDER("outside the world border"),
        HEIGHT("too far above or below the player"),
        NOT_SIMPLE_SURFACE("not clear, dry, solid ground"),
        NOT_NATURAL_GROUND("not natural ground"),
        LAND_CLAIM("in a land claim");

        private final String label;

        Rejection(String label) { this.label = label; }

        public String label() { return label; }
    }

    private RaidSpawnRules() {}

    /**
     * @param afkSeconds idle time, in seconds, from which a player counts as AFK; 0 never counts anyone as AFK
     */
    public static Skip skipReason(PlayerState player, int afkSeconds) {
        if (!player.alive()) return Skip.DEAD;
        if (player.spectator()) return Skip.SPECTATOR;
        // Staff in creative (or vanished into it) would draw raids nobody came for, announced to the whole server.
        if (player.creative()) return Skip.CREATIVE;
        if (player.inBattle()) return Skip.IN_BATTLE;
        // A player in a raid or its Lobby cannot join a second one, so a raid spawned around them is wasted.
        if (player.inRaid()) return Skip.IN_RAID;
        if (afkSeconds > 0 && player.idleMillis() >= afkSeconds * 1000L) return Skip.AFK;
        // Tower and dungeon worlds have no wild raids; trying a player there only spends the check.
        if (!player.dimensionHasRaids()) return Skip.NO_RAIDS_HERE;
        return Skip.NONE;
    }

    /** Whether a surface spot at {@code candidateY} is within {@code maxDifference} blocks of the player's height; 0 allows any. */
    public static boolean heightOk(int candidateY, double playerY, double maxDifference) {
        return maxDifference <= 0.0 || Math.abs(candidateY - playerY) <= maxDifference;
    }

    /**
     * Whether a failed attempt should pass the spawn on to the next player in the same check. Only failures about where the boss
     * could stand do: the next player stands somewhere else. A skipped tier roll is final by design, and the other failures would
     * repeat for every player or, for biome matches, tilt raids toward some biomes.
     */
    public static boolean tryAnotherPlayer(RaidSpawnHistory.Outcome outcome) {
        return switch (outcome) {
            case NO_VALID_TERRAIN, TOO_CLOSE_TO_EXISTING, NO_ROOM_FOR_BOSS -> true;
            default -> false;
        };
    }

    /** "9 not natural ground, 4 in a land claim", most frequent first; empty when nothing was rejected. */
    public static String summarize(EnumMap<Rejection, Integer> counts) {
        return counts.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparing((java.util.Map.Entry<Rejection, Integer> e) -> e.getValue()).reversed()
                        .thenComparing(java.util.Map.Entry::getKey))
                .map(e -> e.getValue() + " " + e.getKey().label())
                .collect(Collectors.joining(", "));
    }
}
