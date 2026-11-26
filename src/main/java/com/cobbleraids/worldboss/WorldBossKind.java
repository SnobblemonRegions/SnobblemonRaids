package com.cobbleraids.worldboss;

import java.util.Locale;

/**
 * Which World Boss is running. One runs at a time; the kind decides how it starts, who may join and how it pays.
 *
 * <ul>
 *   <li>{@link #EVENT}: the seasonal event boss (Halloween), started by its window, a command or another mod. No player cap, flat Raid Points.</li>
 *   <li>{@link #DAILY}: the daily renowned boss. Starts once a day at the configured time, admits up to {@code daily.max_players}, pays a raid
 *       reward and a capture attempt to everyone who dealt damage.</li>
 * </ul>
 *
 * A weekly boss with phases and lore is planned as a third kind.
 */
public enum WorldBossKind {
    EVENT, DAILY;

    public String serializedName() { return name().toLowerCase(Locale.ROOT); }

    /** Unknown or missing (a state saved before kinds existed) reads as {@link #EVENT}, which is what every boss was then. */
    public static WorldBossKind parse(String value) {
        if (value == null) return EVENT;
        for (WorldBossKind kind : values()) {
            if (kind.serializedName().equals(value)) return kind;
        }
        return EVENT;
    }
}
