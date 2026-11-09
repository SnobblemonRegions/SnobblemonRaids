package com.cobbleraids.reward.points;

/**
 * How much of a Raid Points award a daily cap lets through.
 *
 * <p>The award that crosses the cap is paid up to it rather than refused whole, so a player at 290
 * of 300 who wins a 75-point raid gets 10, not nothing: the cap is a ceiling on the day, not a
 * penalty on the raid that reached it. Free of Minecraft types so it is tested without a server.
 */
public final class RaidPointsDailyCap {

    private RaidPointsDailyCap() {}

    /**
     * Points that may be credited now.
     *
     * @param earnedToday what this source has already paid the player today
     * @param cap         the day's ceiling for this source; 0 means no cap
     * @param amount      what the award would pay without a cap
     */
    public static int allowance(int earnedToday, int cap, int amount) {
        if (amount <= 0) return 0;
        if (cap <= 0) return amount;
        return Math.max(0, Math.min(amount, cap - Math.max(0, earnedToday)));
    }
}
