package com.cobbleraids.reward;

import com.cobbleraids.config.RaidDefinition;
import com.cobbleraids.config.RaidRarityTier;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable per-player claim token. The full reward config is snapshotted so /reload cannot rewrite
 * an earned reward.
 *
 * <p>{@code rewardSeed} is fixed when the raid is won, not when the claim is made, and every roll a
 * claim performs derives from it. That is what makes an unclaimed reward survive a restart as the
 * same reward: the contents are regenerated rather than stored, so nothing has to serialise an
 * ItemStack, and logging off does not reroll what you already earned. It is only as stable as the
 * loot tables behind it -- edit a table between the win and the claim and the same seed yields
 * something else, which is the trade this makes against freezing the stacks outright.
 *
 * <p>{@code renownTitle} is "Kaelen, the Relentless" for a renowned boss and empty otherwise. It
 * is both what the reward screen shows and what makes the claim pay renown's multipliers.
 * {@code renownBoon} is that boss's {@link com.cobbleraids.renown.RenownBoon#encode()}, along for
 * the same reason -- it is what lets the reveal screen pick a stat icon for the title banner.
 *
 * <p>{@code points} is the claim's Raid Points, fixed and counted against the daily cap on the day of the win, so
 * holding claims for later days cannot get around the cap, and a Raid Core handed to the party just before claiming
 * adds nothing. {@link #POINTS_AT_CLAIM} marks a claim saved before this existed: it is priced at claim time, as it
 * always was.
 */
public record PendingRaidReward(
        UUID raidId,
        ResourceLocation definitionId,
        RaidRarityTier rarityTier,
        RaidDefinition.Rewards rewards,
        double contributionPercentage,
        int contributionBonusRolls,
        int elapsedCombatTicks,
        int participantCount,
        long rewardSeed,
        String renownTitle,
        String renownBoon,
        int points
) {
    /** A claim saved before its points were fixed at victory. */
    public static final int POINTS_AT_CLAIM = -1;

    public PendingRaidReward {
        points = Math.max(POINTS_AT_CLAIM, points);
        renownTitle = renownTitle == null ? "" : renownTitle;
        renownBoon = renownBoon == null ? "" : renownBoon;
    }

    public boolean renowned() {
        return !renownTitle.isEmpty();
    }
}
