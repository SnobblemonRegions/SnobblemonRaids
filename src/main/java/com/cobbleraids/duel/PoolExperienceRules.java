package com.cobbleraids.duel;

import com.cobbleraids.config.RaidRarityTier;

/**
 * Balance phase 3: the Cobblemon experience a raid or World Boss pays when its shared pool falls. Duel copies pay nothing on
 * their own (they are owned encounters), so this is the raid's experience, paid once, to every Pokemon in each victor's party.
 *
 * <p>Built on Cobblemon's own trainer-battle formula, {@code baseYield x level / 5}, then a multiplier for how hard the boss is
 * and the player's damage share. For a typical level-70 legendary (yield about 300) that is about 12,600 per Pokemon, close to
 * one level for a level-70 party; a starter raid pays a few hundred. Pure, so the numbers can be tested and tuned.
 */
public final class PoolExperienceRules {

    /** The World Boss's multiplier; raid tiers are below it. */
    public static final double WORLD_BOSS_MULTIPLIER = 5.0;

    private PoolExperienceRules() {}

    public static double tierMultiplier(RaidRarityTier tier) {
        return switch (tier) {
            case STARTER -> 1.5;
            case POWERHOUSE -> 2.0;
            case LEGENDARY -> 3.0;
            case MYTHICAL -> 4.0;
        };
    }

    /**
     * The share factor: a player who dealt an even split gets x1, clamped to 0.5-1.5. Under a quarter of an even split pays
     * nothing, the same rule AscensionLib uses for raid pay, so standing at the back earns no experience.
     */
    public static double shareFactor(double share, int players) {
        int n = Math.max(1, players);
        double ofEven = share * n;
        if (ofEven < 0.25) return 0.0;
        return Math.max(0.5, Math.min(1.5, ofEven));
    }

    /** Experience for each Pokemon of one victor's party. {@code share} is 0-1. */
    public static int perPokemon(int baseYield, int bossLevel, double multiplier, double share, int players) {
        double amount = Math.max(1, baseYield) * Math.max(1, bossLevel) / 5.0 * multiplier * shareFactor(share, players);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(amount));
    }
}
