package com.cobbleraids.presentation;

/**
 * Where a player stands relative to a raid's edge: the leash during a raid ({@code RaidDuelRules.LEASH_BLOCKS}), or the recruitment
 * radius in a Lobby. Decides whether they are shown the particle ring and warned, so a player hears about the edge before the step
 * that forfeits their rewards. Minecraft-free so the thresholds are tested; {@link RaidEdgeService} draws the result.
 */
public final class RaidEdge {

    /** CLEAR: nothing shown. NEAR: the ring. WARN: the ring and a warning. OUTSIDE: past the edge. */
    public enum Zone { CLEAR, NEAR, WARN, OUTSIDE }

    private RaidEdge() {}

    /**
     * @param distance    horizontal-or-3D distance from the boss, as the edge's own check measures it
     * @param radius      the edge; standing exactly on it is inside, matching the leash's strictly-greater test
     * @param showWithin  blocks inside the edge from which the ring is shown
     * @param warnWithin  blocks inside the edge from which the warning is shown; 0 warns only on the edge itself
     */
    public static Zone zone(double distance, double radius, double showWithin, double warnWithin) {
        if (distance > radius) return Zone.OUTSIDE;
        double left = radius - distance;
        if (left <= warnWithin) return Zone.WARN;
        if (left <= showWithin) return Zone.NEAR;
        return Zone.CLEAR;
    }

    /** Whole blocks a player can still walk before the edge, rounded down so the count never promises a block that is not there. */
    public static int blocksLeft(double distance, double radius) {
        return (int) Math.max(0.0, Math.floor(radius - distance));
    }

    /**
     * How far along the ring, from the player, it is drawn. A ring no wider than {@code showWithin} (a Lobby's) is drawn whole so a
     * player can see where to stand; a wide one (the leash) only near the player, so its cost stays with the players at its edge.
     */
    public static double viewDistance(double radius, double showWithin) {
        return radius <= showWithin ? 2.0 * radius + showWithin : showWithin;
    }
}
