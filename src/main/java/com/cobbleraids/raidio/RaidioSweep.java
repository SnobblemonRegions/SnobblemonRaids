package com.cobbleraids.raidio;

/**
 * Whether the player is turning, read once per step from their yaw. The bleeps and ticks only play while they sweep, so Raidio is silent when they stand still
 * facing away from the raid. A sweep
 * is held for a moment after the turn ends so it does not stutter. No Minecraft types.
 */
public final class RaidioSweep {
    /** A turn at least this big in one step is a sweep. */
    public static final double MIN_DEGREES_PER_STEP = 2.0;
    /** How many steps a sweep lasts after the turn stops (five steps is half a second). */
    public static final int HOLD_STEPS = 5;

    private double last = Double.NaN;
    private int quietSteps = HOLD_STEPS;

    /** Reads this step's yaw and says whether the player is sweeping. */
    public boolean update(double yaw) {
        if (!Double.isFinite(yaw)) {
            quietSteps = Math.min(quietSteps + 1, HOLD_STEPS);
            return quietSteps < HOLD_STEPS;
        }
        if (Double.isNaN(last)) {
            last = yaw;
            return false;
        }
        double delta = Math.abs(((yaw - last + 180) % 360 + 360) % 360 - 180);
        last = yaw;
        quietSteps = delta >= MIN_DEGREES_PER_STEP ? 0 : Math.min(quietSteps + 1, HOLD_STEPS);
        return quietSteps < HOLD_STEPS;
    }
}
