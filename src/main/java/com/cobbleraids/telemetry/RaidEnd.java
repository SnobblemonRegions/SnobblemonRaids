package com.cobbleraids.telemetry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One finished raid, as the {@code raid_end} telemetry event: every ending, not only victories, so the loss rate and the
 * difficulty of each tier can be measured. Minecraft-free, so it is tested directly; {@link RaidTelemetry} fills it from a raid.
 *
 * @param encounterId      {@code raid:<boss Pokemon UUID>}, the id AscensionLib's {@code encounter} and {@code settlement} events carry
 * @param mode             {@code party} (an ordinary raid), {@code duel} (a duel raid) or {@code owned} (another mod's encounter, such as a tower boss floor)
 * @param owner            the encounter owner's id for an owned raid, otherwise null
 * @param partySize        everyone who started the raid, including those who left
 * @param activeAtEnd      how many were still in it when it ended
 * @param timeLimitSeconds 0 for an untimed raid
 * @param bossHpLeft       the boss's health pool left, 0 to 1, or a negative number when unknown
 * @param dealers          how many players dealt any damage
 * @param players          every participant (AscensionLib writes them as player keys, never as UUIDs)
 */
public record RaidEnd(String encounterId, String mode, String owner, String definitionId, String tier, int threat, String species,
                      int level, RaidEndReason reason, int partySize, int activeAtEnd, int combatSeconds, int timeLimitSeconds,
                      double bossHpLeft, int dealers, Collection<UUID> players) {

    public static final String TYPE = "raid_end";

    public Map<String, Object> fields() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", encounterId);
        out.put("mode", mode);
        if (owner != null) out.put("owner", owner);
        out.put("raid", definitionId);
        out.put("tier", tier);
        if (threat > 0) out.put("threat", threat);
        if (species != null) out.put("species", species);
        if (level > 0) out.put("level", level);
        out.put("outcome", reason.outcome());
        out.put("reason", reason.id());
        out.put("partySize", partySize);
        out.put("activeAtEnd", activeAtEnd);
        out.put("seconds", Math.max(0, combatSeconds));
        if (timeLimitSeconds > 0) out.put("timeLimit", timeLimitSeconds);
        if (bossHpLeft >= 0) out.put("bossHpLeft", round3(bossHpLeft));
        out.put("dealers", dealers);
        out.put("players", players == null ? List.of() : List.copyOf(players));
        return out;
    }

    /** Health left as a fraction, or -1 when the pool is unknown (zero or negative maximum). */
    public static double fraction(float current, float max) {
        return max > 0f ? Math.max(0.0, current) / max : -1.0;
    }

    static double round3(double fraction) {
        return Math.round(Math.min(1.0, fraction) * 1000.0) / 1000.0;
    }
}
