package com.cobbleraids.shop;

import java.util.Locale;

/**
 * What a shop entry is paid in. Raid Points by default; the Renown Vendor's entries cost Renown Coins, taken from the coins in the player's
 * inventory (coins pressed into the Coin Album are safe there and never spent).
 */
public enum ShopCurrency {
    RAID_POINTS("RP"),
    RENOWN_COINS("coins");

    private final String unit;

    ShopCurrency(String unit) {
        this.unit = unit;
    }

    /** How the price reads: "120 RP", "10 coins". */
    public String unit() {
        return unit;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Unknown values read as Raid Points, so a typo can never make an entry free of its usual currency. */
    public static ShopCurrency parse(String value) {
        if (value == null) return RAID_POINTS;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "renown_coins", "renown_coin", "coins" -> RENOWN_COINS;
            default -> RAID_POINTS;
        };
    }
}
