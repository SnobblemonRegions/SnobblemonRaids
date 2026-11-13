package com.cobbleraids.duel;

import com.cobbleraids.config.RaidRarityTier;

/**
 * Balance phase 3: the numbers of a duel raid, where up to eight players each fight their own copy of the boss and every hit
 * comes off one shared pool (the World Boss method, {@code WorldBossPool}). Pure, so the curve can be tested and tuned
 * without a server.
 *
 * <p>The pool grows sub-linearly with the crowd ({@code players^0.85}): eight players face 5.85 times one player's pool, not
 * eight, so a full group clears faster than a duo but each extra player is worth a little less. Each duel's own boss has a
 * fixed HP by tier; knocking it out ends that duel and the player chains into the next one until the pool is empty or the
 * window closes.
 */
public final class RaidDuelRules {

    /** The most players one duel raid takes. */
    public static final int MAX_PLAYERS = 8;
    /** How long the group has to empty the pool: the default of {@code duel_raid.raid_time_limit_seconds} in the server config. */
    public static final int WINDOW_SECONDS = 600;
    /** The exponent the pool grows with. */
    public static final double CROWD_EXPONENT = 0.85;
    /** First guess: one player's share of the pool is this many of their duel bosses. Tune from the phase-0 logs. */
    public static final int DUELS_PER_PLAYER = 3;
    /** One duel's own time limit, the default of {@code duel_raid.duel_time_limit_seconds}: a duel still running after this long is ended and its player chains on. */
    public static final int DUEL_LIMIT_SECONDS = 240;
    /** A player this far from the raid boss, or in another dimension, has walked out of the raid. */
    public static final double LEASH_BLOCKS = 48.0;

    private RaidDuelRules() {}

    /** The HP of the boss in one player's duel. */
    public static long duelHealth(RaidRarityTier tier) {
        return switch (tier) {
            case STARTER -> 550;
            case POWERHOUSE -> 1400;
            case LEGENDARY -> 3500;
            case MYTHICAL -> 5000;
        };
    }

    /** One player's share of the pool before the crowd curve. */
    public static long perPlayerPool(RaidRarityTier tier) {
        return duelHealth(tier) * DUELS_PER_PLAYER;
    }

    /** The shared pool for {@code players} (clamped to 1-{@link #MAX_PLAYERS}). */
    public static long poolFor(RaidRarityTier tier, int players) {
        int n = Math.max(1, Math.min(MAX_PLAYERS, players));
        return Math.round(perPlayerPool(tier) * Math.pow(n, CROWD_EXPONENT));
    }

    /**
     * Who a won raid pays: everybody who actually fought a duel and did not give the raid up. A player whose party was wiped is
     * still paid (their damage counts); one who withdrew, walked away or never came back from a disconnect forfeited, exactly as
     * leaving a party raid did; and one whose first duel never started took no part at all.
     */
    public static java.util.Set<java.util.UUID> victors(java.util.Collection<java.util.UUID> players,
            java.util.Set<java.util.UUID> fought, java.util.Set<java.util.UUID> forfeited) {
        java.util.Set<java.util.UUID> victors = new java.util.LinkedHashSet<>();
        for (java.util.UUID player : players) if (fought.contains(player) && !forfeited.contains(player)) victors.add(player);
        return victors;
    }

    /**
     * Whether a duel raid ending this way is called off rather than lost: a server stop or an operator abort is nobody's
     * defeat, so it records no loss and gives back the charges it took.
     */
    public static boolean isCalledOff(com.cobbleraids.telemetry.RaidEndReason reason) {
        return reason == com.cobbleraids.telemetry.RaidEndReason.SERVER_STOP
                || reason == com.cobbleraids.telemetry.RaidEndReason.ABORTED;
    }

    /**
     * Who gets their charge back when a raid is called off: everybody whose duel began and who had not walked away. A
     * player whose duel never started was refunded at the start.
     */
    public static java.util.Set<java.util.UUID> refundedWhenCalledOff(java.util.Collection<java.util.UUID> players,
            java.util.Set<java.util.UUID> fought, java.util.Set<java.util.UUID> forfeited) {
        return victors(players, fought, forfeited);
    }

    /** Who a lost duel raid records a loss for: only the players whose duel began. */
    public static java.util.Set<java.util.UUID> losers(java.util.Collection<java.util.UUID> players, java.util.Set<java.util.UUID> fought) {
        java.util.Set<java.util.UUID> losers = new java.util.LinkedHashSet<>();
        for (java.util.UUID player : players) if (fought.contains(player)) losers.add(player);
        return losers;
    }

    /** Whether one more player may join a duel raid that has {@code current} already. */
    public static boolean hasRoom(int current) {
        return current < MAX_PLAYERS;
    }
}
