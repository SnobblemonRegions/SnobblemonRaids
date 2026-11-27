package com.cobbleraids.lifecycle;

import com.cobbleraids.reward.RewardExtra;
import java.util.List;
import com.cobbleraids.raid.RaidSession;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable terminal snapshot used by the reward layer; independent from actor-level event ordering.
 *
 * <p>{@code tableTier}, when set, names the loot tier the claim rolls instead of the boss's own ("daily" for the Daily Boss, "world_boss"
 * for an event World Boss); {@code extras} are guaranteed items per player (Renown Coins, seasonal items); {@code skipPoints} leaves Raid
 * Points out of the claim, for a boss that pays them its own way.
 *
 * <p>{@code renownTitle} and {@code renownBoon} are empty for an ordinary boss. They ride along
 * because nothing downstream can ask the boss any more: the entity is gone by the time a reward is
 * claimed.
 */
public record RaidRewardEligibility(
        UUID raidId,
        ResourceLocation definitionId,
        RaidOutcome outcome,
        Map<UUID, Float> contribution,
        Set<UUID> participants,
        int elapsedCombatTicks,
        String renownTitle,
        String renownBoon,
        String tableTier,
        Map<UUID, List<RewardExtra>> extras,
        boolean skipPoints) {
    public RaidRewardEligibility {
        renownTitle = renownTitle == null ? "" : renownTitle;
        renownBoon = renownBoon == null ? "" : renownBoon;
        tableTier = tableTier == null ? "" : tableTier;
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    /** An ordinary raid's claim: its tier's tables, no extras, Raid Points as usual. */
    public RaidRewardEligibility(UUID raidId, ResourceLocation definitionId, RaidOutcome outcome, Map<UUID, Float> contribution,
                                 Set<UUID> participants, int elapsedCombatTicks, String renownTitle, String renownBoon) {
        this(raidId, definitionId, outcome, contribution, participants, elapsedCombatTicks, renownTitle, renownBoon, "", Map.of(), false);
    }

    /** The extras this player's claim carries (none for most). */
    public List<RewardExtra> extrasFor(UUID player) {
        return extras.getOrDefault(player, List.of());
    }

    public static RaidRewardEligibility victory(RaidSession raid) {
        return new RaidRewardEligibility(
                raid.getId(), raid.getDefinitionId(), RaidOutcome.VICTORY,
                raid.getContributionSnapshot(), raid.getActiveParticipants(), raid.getElapsedCombatTicks(),
                raid.getRenownTitle(), raid.getRenownBoon());
    }
}
