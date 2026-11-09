package com.cobbleraids.reward.points;

import java.util.Locale;
import java.util.Optional;

/**
 * The kind of content a Raid Points award came from, which decides the daily cap it counts against.
 *
 * <p>Only content a player can repeat all day has a cap. A World Boss reward is bounded by its own
 * per-day reward count, and one-off grants (an administrator, a progression track) are never capped,
 * so neither appears here: they credit through the uncapped path.
 */
public enum RaidPointsSource {
    /** A won raid's claim, including points banked by a Raid Capture Protocol session and credited later. */
    RAID,
    /** A tower run, paid by another mod through {@code CobbleRaidsPoints.award(..., "tower")}. */
    TOWER;

    /** The id stored in the player record and accepted from the public API. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The source an API caller named, or empty for one this mod does not cap. */
    public static Optional<RaidPointsSource> fromId(String id) {
        if (id == null) return Optional.empty();
        for (RaidPointsSource source : values()) {
            if (source.id().equals(id.toLowerCase(Locale.ROOT))) return Optional.of(source);
        }
        return Optional.empty();
    }
}
