package com.cobbleraids.duel;

import java.util.Locale;

/**
 * The numbers behind the HP bar that floats over a duel raid's boss: sizes, colours and the two text lines. Pure, so it can be
 * tested without a server; {@link RaidHpBar} turns it into display entities.
 *
 * <p>The bar is drawn with text displays whose text is empty. Vanilla's text display renderer then draws exactly one background
 * pixel, a {@link #PIXEL}-block square whose bottom-left corner sits on the display's origin (no lines means a width and height
 * of zero, and the background spans -1..0 px before the renderer's own 1px shift). Scaling that square gives a solid rectangle
 * of any size and colour whose left edge never moves, so the fill drains towards the left by scaling X alone, and the client
 * interpolates the scale smoothly.
 */
public final class RaidHpBarLayout {

    /** One text display background pixel, in blocks (the renderer's 0.025 scale). */
    public static final float PIXEL = 0.025f;
    /** The bar's inner width and height, in blocks. */
    public static final float WIDTH = 1.5f;
    public static final float HEIGHT = 0.1f;
    /** The dark border around the bar. */
    public static final float BORDER = PIXEL;
    /** How far above the boss's head the bar sits, leaving room for its nameplate. */
    public static final float ABOVE_HEAD = 0.6f;
    /**
     * Extra room as a share of the boss's height: Cobblemon models overhang their hitbox (Chandelure's flames, horns, crests),
     * and a raid boss is scaled up, so a fixed gap let the model poke through the bar.
     */
    public static final float HEAD_ROOM = 0.25f;
    /** The gap between the top of the bar and the text above it. */
    public static final float TEXT_GAP = 0.06f;
    /** The text's scale: the HP line at full size is wider than the bar; at this size it matches it. */
    public static final float TEXT_SCALE = 0.75f;
    /** A vanilla nameplate's translucent backing, so the text reads against bright sky and pale walls alike. */
    public static final int TEXT_BACKGROUND = 0x40000000;

    public static final int TRACK_COLOR = 0xE0000000;
    public static final int EMPTY_COLOR = 0xFF3A3A3A;
    public static final int TRAIL_COLOR = 0xFFFFFFFF;
    public static final int HIGH_COLOR = 0xFF55FF55;
    public static final int MID_COLOR = 0xFFFFFF55;
    public static final int LOW_COLOR = 0xFFFF5555;

    private RaidHpBarLayout() {}

    /** How full the bar is, 0..1. A pool with no size reads as empty. */
    public static float fraction(float current, float max) {
        if (!(max > 0f) || !(current > 0f)) return 0f;
        return Math.min(1f, current / max);
    }

    /** The HP number shown: rounded up, so a boss that is still standing never reads 0. */
    public static long shownHp(float hp) {
        if (!(hp > 0f)) return 0L;
        return (long) Math.ceil(hp);
    }

    /** Green above half, yellow above a fifth, red below. */
    public static int fillColor(float fraction) {
        if (fraction > 0.5f) return HIGH_COLOR;
        if (fraction > 0.2f) return MID_COLOR;
        return LOW_COLOR;
    }

    /** X scale that stretches the one-pixel square to {@code fraction} of the bar's width. */
    public static float fillScaleX(float fraction) {
        return WIDTH / PIXEL * Math.max(0f, Math.min(1f, fraction));
    }

    public static float barScaleY() {
        return HEIGHT / PIXEL;
    }

    /** The bar's left edge, relative to the boss's centre (the square's origin is its left edge). */
    public static float barLeft() {
        return -WIDTH / 2f;
    }

    /** The bar's bottom edge above the boss's feet. */
    public static float barBottom(float bossHeight) {
        return Math.max(0f, bossHeight) * (1f + HEAD_ROOM) + ABOVE_HEAD;
    }

    /** Where the text's bottom line sits: just above the bar's top border. */
    public static float textBottom(float bossHeight) {
        return barBottom(bossHeight) + HEIGHT + BORDER + TEXT_GAP;
    }

    /** "2,605 / 4,201". */
    public static String hpText(long current, long max) {
        return String.format(Locale.ROOT, "%,d / %,d", current, max);
    }

    /** "7:42"; never negative. */
    public static String clock(long secondsLeft) {
        long s = Math.max(0L, secondsLeft);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** "⚔ 3 dueling · 7:42". */
    public static String status(int fighters, long secondsLeft) {
        return "⚔ " + Math.max(0, fighters) + " dueling · " + clock(secondsLeft);
    }

    /**
     * What follows the species in the chat line a player gets when their copy falls but the raid boss does not:
     * "Copy down! [species] has 2,605 / 4,201 HP left. Next duel in 3s." The species goes in as a translatable component (the
     * server cannot translate it), so the line is built in two halves.
     */
    public static String copyDownTail(long current, long max, int nextInSeconds) {
        return " has " + hpText(current, max) + " HP left. Next duel in " + nextInSeconds + "s.";
    }
}
