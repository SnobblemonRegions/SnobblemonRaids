package com.cobbleraids.renown;

/**
 * The HP-pool affixes as the raid boss has them: on the shared raid health pool, not the boss's own HP.
 *
 * <p>Showdown keeps a raid boss pinned at full HP (raid-patch.js), so "a hit that would knock it out" or
 * "below a third of its HP" mean nothing inside the simulator. The pool in RaidProgress is the boss's real
 * health, so Last Stand and Second Wind act on it here, and Unbroken learns from here when the pool has
 * fallen below half. Grit and Regrowth need no pool rule; renown-affixes.js runs them as they are.
 *
 * <p>One per raid, created with the session and used only from the server thread that applies
 * {@code -raiddamage}. Pure, so every threshold is unit-tested.
 */
public final class RaidPoolAffix {

    /** Last Stand holds the pool at the next of these marks below its current health. */
    static final float[] LAST_STAND_MARKS = {0.75f, 0.50f, 0.25f};
    static final float SECOND_WIND_BELOW = 1.0f / 3.0f;
    static final float SECOND_WIND_HEAL = 0.05f;
    static final float UNBROKEN_BELOW = 0.5f;

    private final RenownAffix affix;
    private boolean lastStandUsed;
    private boolean secondWindUsed;
    private boolean unbrokenEnded;

    public RaidPoolAffix(RenownAffix affix) {
        this.affix = affix;
    }

    public static RaidPoolAffix none() { return new RaidPoolAffix(null); }

    public RenownAffix affix() { return affix; }

    /**
     * The damage the pool actually takes. Last Stand, once per raid, stops one hit at the next 25% mark
     * below where the pool stands. It never protects the last quarter: a pool at or under 25% has no mark
     * left to hold, so the boss can always be finished.
     *
     * @return {@code amount} unchanged, or less when Last Stand fires (see {@link #lastStandFired})
     */
    public float adjustDamage(float current, float max, float amount) {
        if (affix != RenownAffix.LAST_STAND || lastStandUsed || !(max > 0f) || !(amount > 0f)) return amount;
        float mark = nextMarkBelow(current, max);
        if (mark <= 0f || current - amount >= mark) return amount;
        lastStandUsed = true;
        return Math.max(0f, current - mark);
    }

    /** True once Last Stand has been spent; the caller uses the change to announce it. */
    public boolean lastStandFired() { return lastStandUsed; }

    /**
     * How much the pool regains after a hit: Second Wind's 5% the first time the pool drops below a third,
     * and nothing otherwise. Never on a pool that is empty, so it cannot undo the winning blow.
     */
    public float healAfterDamage(float current, float max) {
        if (affix != RenownAffix.SECOND_WIND || secondWindUsed || !(max > 0f) || current <= 0f) return 0f;
        if (current >= max * SECOND_WIND_BELOW) return 0f;
        secondWindUsed = true;
        return max * SECOND_WIND_HEAL;
    }

    /** True exactly once: the first hit that leaves an Unbroken boss's pool below half. */
    public boolean unbrokenEndsNow(float current, float max) {
        if (affix != RenownAffix.UNBROKEN || unbrokenEnded || !(max > 0f)) return false;
        if (current >= max * UNBROKEN_BELOW) return false;
        unbrokenEnded = true;
        return true;
    }

    private static float nextMarkBelow(float current, float max) {
        for (float fraction : LAST_STAND_MARKS) {
            float mark = max * fraction;
            if (mark < current) return mark;
        }
        return 0f;
    }
}
