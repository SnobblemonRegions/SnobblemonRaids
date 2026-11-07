package com.cobbleraids.raidio;

import com.cobbleraids.config.RaidRarityTier;

/**
 * Each raid tier's tune: one bar of sixteen sixteenth-note positions that repeats. A position is a note, in semitones above the note the raid's distance gives
 * (never above an octave, so the tune always fits on top of that note inside a note block's range), or a rest. The tiers differ in the notes and rhythm, and
 * not just in the instrument, the way a route theme differs from a fanfare. No Minecraft types.
 */
public final class RaidioMotifs {
    public static final int POSITIONS = 16;
    /** A position with no note. */
    public static final int REST = Integer.MIN_VALUE;

    private static final int R = REST;
    /** Starter: a playful major arpeggio, up and back down. */
    private static final int[] STARTER = {0, R, 4, R, 7, R, 4, R, 12, R, 7, R, 4, R, 0, R};
    /** Powerhouse: a driving, syncopated minor riff. */
    private static final int[] POWERHOUSE = {0, 0, R, 3, R, 7, R, 3, 5, R, 5, R, 7, R, 3, R};
    /** Mythical: a dreamy pentatonic ladder, with sparkles over it. */
    private static final int[] MYTHICAL = {0, R, 7, R, 9, R, 12, R, 9, R, 7, R, 4, R, 2, R};
    /** Legendary: a fanfare, short-short-long, with a harmony voice. */
    private static final int[] LEGENDARY = {0, 0, 0, R, 7, R, R, R, 5, 5, 5, R, 12, R, R, R};

    private RaidioMotifs() {}

    private static int[] of(RaidRarityTier tier) {
        if (tier == null) return STARTER;
        return switch (tier) {
            case STARTER -> STARTER;
            case POWERHOUSE -> POWERHOUSE;
            case MYTHICAL -> MYTHICAL;
            case LEGENDARY -> LEGENDARY;
        };
    }

    private static int wrap(int position) {
        return Math.floorMod(position, POSITIONS);
    }

    /** The lead's note at {@code position} (any position: the bar repeats), or {@link #REST}. */
    public static int lead(RaidRarityTier tier, int position) {
        return of(tier)[wrap(position)];
    }

    /** Whether the bass plays at this position: on the beats, and not at all for the floating Mythical tune. */
    public static boolean bass(RaidRarityTier tier, int position) {
        int p = wrap(position);
        if (tier == null) return p == 0 || p == 8;
        return switch (tier) {
            case STARTER, LEGENDARY -> p == 0 || p == 8;
            case POWERHOUSE -> p % 4 == 0;
            case MYTHICAL -> false;
        };
    }

    /** Whether a second voice plays a fifth below the lead: only the Legendary fanfare is harmonised. */
    public static boolean harmony(RaidRarityTier tier) {
        return tier == RaidRarityTier.LEGENDARY;
    }

    /** Whether a high sparkle plays at this position: the Mythical tune has two a bar. */
    public static boolean sparkle(RaidRarityTier tier, int position) {
        int p = wrap(position);
        return tier == RaidRarityTier.MYTHICAL && (p == 7 || p == 15);
    }
}
