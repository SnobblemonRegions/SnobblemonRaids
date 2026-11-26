package com.cobbleraids.duel;

/**
 * The white strip that trails the HP bar's fill: after a hit it holds where the fill was for a moment, then slides down to meet
 * it, so a big hit stays visible. HP going up (a pool that grows) moves it at once. Pure; ticks are server game ticks.
 */
public final class RaidHpBarTrail {

    /** How long the strip holds after a hit before it starts to slide. */
    public static final int HOLD_TICKS = 10;
    /** How long the slide takes (the display's interpolation). */
    public static final int SLIDE_TICKS = 12;

    private float shown;
    private long slideAt = -1L;

    public RaidHpBarTrail(float start) {
        this.shown = start;
    }

    /** Where the strip is (or is sliding to). */
    public float shown() {
        return shown;
    }

    /**
     * Feeds the fill's current fraction. Returns true when the strip moved this tick (the display must be updated); after a hit it
     * waits {@link #HOLD_TICKS}, and further hits during the hold do not restart the wait.
     */
    public boolean update(float fill, long tick) {
        if (fill >= shown) {
            slideAt = -1L;
            if (fill == shown) return false;
            shown = fill;
            return true;
        }
        if (slideAt < 0L) {
            slideAt = tick + HOLD_TICKS;
            return false;
        }
        if (tick < slideAt) return false;
        slideAt = -1L;
        shown = fill;
        return true;
    }
}
