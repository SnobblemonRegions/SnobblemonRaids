package com.cobbleraids.reward;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * A guaranteed item a claim carries on top of its rolled tables: a Daily Boss's Renown Coins, or an event World Boss's seasonal item. Fixed
 * when the boss falls (so it cannot reroll), saved with the claim, and handed over when the player claims it, so it shows on the reveal.
 *
 * @param item    the item id
 * @param count   how many
 * @param species for a Renown Coin, the species it is struck with (e.g. cobblemon:rayquaza); empty for any other item
 * @param tier    for a Renown Coin, the boss's tier ("legendary" or "mythical"); empty otherwise
 */
public record RewardExtra(ResourceLocation item, int count, String species, String tier) {
    public static final ResourceLocation RENOWN_COIN = ResourceLocation.fromNamespaceAndPath("cobbleraids", "renown_coin");

    public RewardExtra {
        Objects.requireNonNull(item, "item");
        if (count < 1 || count > 64_000) throw new IllegalArgumentException("extra count must be 1..64000");
        species = species == null ? "" : species;
        tier = tier == null ? "" : tier;
    }

    public static RewardExtra coins(ResourceLocation species, String tier, int count) {
        return new RewardExtra(RENOWN_COIN, count, species.toString(), tier);
    }

    public boolean isCoin() {
        return RENOWN_COIN.equals(item) && !species.isEmpty();
    }
}
