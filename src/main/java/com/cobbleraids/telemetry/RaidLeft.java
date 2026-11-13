package com.cobbleraids.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player who left a raid before it ended, as the {@code raid_left} telemetry event: when in the fight players give up and how
 * it was going for them. Minecraft-free. {@code reason} is the encounter API's {@code LeaveReason} in lower case
 * ({@code withdrew}, {@code fled}, {@code disconnected}).
 */
public record RaidLeft(String encounterId, String mode, String definitionId, String tier, String reason, int combatSeconds,
                       double bossHpLeft, int stillIn, UUID player) {

    public static final String TYPE = "raid_left";

    public Map<String, Object> fields() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", encounterId);
        out.put("mode", mode);
        out.put("raid", definitionId);
        out.put("tier", tier);
        out.put("reason", reason);
        out.put("seconds", Math.max(0, combatSeconds));
        if (bossHpLeft >= 0) out.put("bossHpLeft", RaidEnd.round3(bossHpLeft));
        out.put("stillIn", stillIn);
        out.put("player", player);
        return out;
    }
}
