package com.cobbleraids.showdown;

/**
 * The {@code >raidpool <current> <max>} line that tells raid-patch.js where the shared health pool is,
 * so the boss's simulated HP can mirror its fraction (see raidBossMirrorHp in raid-patch.js). Sent
 * after every pool change Java applies; between sends the patch predicts from its own lines.
 */
final class RaidPoolCommand {

    private RaidPoolCommand() {}

    /** The line, or null when the pool cannot be mirrored (no usable maximum). */
    static String of(float current, float max) {
        if (!Float.isFinite(current) || !Float.isFinite(max) || max <= 0f) return null;
        float clamped = Math.max(0f, Math.min(max, current));
        // Float.toString is locale-independent; String.format would write "0,5" on a German JVM.
        return ">raidpool " + Float.toString(clamped) + " " + Float.toString(max);
    }
}
