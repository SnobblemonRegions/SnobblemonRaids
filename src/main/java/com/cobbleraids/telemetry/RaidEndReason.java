package com.cobbleraids.telemetry;

import java.util.Locale;

/**
 * Why a raid ended, for the {@code raid_end} telemetry event. {@link #outcome()} groups them: a win, a defeat the players
 * suffered or gave up, or an end nobody played to (an operator or the server stopping it).
 */
public enum RaidEndReason {
    /** The boss's health pool reached zero, or its real Pokemon fainted. */
    VICTORY,
    /** Every player side was exhausted: the boss won, or both sides fainted together. In a duel raid, nobody was left fighting. */
    WIPE,
    /** The combat timer (or a duel raid's window) ran out. */
    TIMEOUT,
    /** The last player still in it used the raid's leave command. */
    WITHDREW,
    /** The last player still in it fled the battle. */
    FLED,
    /** The last player still in it disconnected for good. */
    DISCONNECT,
    /** An operator despawned the boss, or the boss was removed mid-raid. */
    ABORTED,
    /** The server stopped with the raid in progress. */
    SERVER_STOP,
    /** The raid reached an end but was never finalized, and the terminal watch finalized it (always also an error in the log). */
    STRANDED;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    /**
     * A duel raid's reason when nobody is left fighting. {@code departures} lists, in the order they went out, how each player
     * left: {@link #WIPE} for one knocked out (or whose next duel could not start), {@link #WITHDREW}, {@link #FLED} (also walking
     * away from the boss) or {@link #DISCONNECT} for one who gave the raid up. It can be empty (no duel ever started).
     * The last one out decides, as in a party raid; with nobody ever in, nobody's result: {@link #ABORTED}.
     */
    public static RaidEndReason whenNobodyIsLeft(java.util.List<RaidEndReason> departures) {
        return departures.isEmpty() ? ABORTED : departures.get(departures.size() - 1);
    }

    /** {@code victory}, {@code defeat} (the players lost or gave up) or {@code aborted} (nobody's result). */
    public String outcome() {
        return switch (this) {
            case VICTORY -> "victory";
            case ABORTED, SERVER_STOP -> "aborted";
            default -> "defeat";
        };
    }
}
