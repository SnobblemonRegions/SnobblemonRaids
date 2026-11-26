package com.cobbleraids.duel;

/**
 * The shared pool's health bar for one duel raid: over the boss in the world for an ordinary raid ({@link RaidHpBar}), at the top of
 * the screen for a renowned one, which is an event boss like the World Boss ({@link RaidHudBar}). Cosmetic; server thread only.
 */
interface DuelBossBar {
    /** The pool now, how many players are still fighting, and the seconds left in the raid's window. */
    void update(float current, float max, int fighters, long secondsLeft, long tick);

    /** Takes the bar down for good. */
    void remove();
}
