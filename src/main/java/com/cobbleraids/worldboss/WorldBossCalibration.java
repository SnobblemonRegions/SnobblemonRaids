package com.cobbleraids.worldboss;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Staff-supplied measured reference, never inferred from the legacy tuning defaults. */
public record WorldBossCalibration(String definition, String rulesFingerprint, BigDecimal referenceDpm, String evidence) {
    public WorldBossCalibration {
        Objects.requireNonNull(referenceDpm);
        referenceDpm = referenceDpm.stripTrailingZeros();
        if (definition == null || !definition.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || rulesFingerprint == null || !rulesFingerprint.matches("[0-9a-f]{64}")
                || referenceDpm.signum() <= 0 || referenceDpm.scale() > 3 || referenceDpm.scale() < -8 || referenceDpm.precision() > 12
                || evidence == null || evidence.isBlank() || evidence.length() > 256 || evidence.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid World Boss calibration");
        }
        long minimum = roundedHealth(referenceDpm, 5), maximum = roundedHealth(referenceDpm, 50);
        if (minimum < 1 || maximum > WorldBossFixedHealth.MAX_WHOLE_HP) {
            throw new IllegalArgumentException("Calibration exceeds the supported 5-50 player numerical envelope");
        }
    }

    private static long roundedHealth(BigDecimal dpm, int players) {
        return dpm.multiply(BigDecimal.valueOf(players * 8L)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public long healthFor(int players) {
        if (players < 1 || players > 50) throw new IllegalArgumentException("Opening roster must have 1-50 players");
        long health = roundedHealth(referenceDpm, players);
        if (health < 1) throw new IllegalArgumentException("Calibration rounds this test roster to zero HP");
        return health;
    }

    public static WorldBossCalibration fromJson(JsonObject json) {
        if (!json.has("reference_dpm") || !json.get("reference_dpm").isJsonPrimitive()
                || !json.getAsJsonPrimitive("reference_dpm").isNumber()) throw new IllegalArgumentException("reference_dpm must be a measured decimal number");
        return new WorldBossCalibration(text(json, "definition"), text(json, "rules_fingerprint"),
                json.get("reference_dpm").getAsBigDecimal(), text(json, "evidence"));
    }

    private static String text(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException("calibrations." + key + " must be text");
        }
        return json.get(key).getAsString();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("definition", definition);
        json.addProperty("rules_fingerprint", rulesFingerprint);
        json.addProperty("reference_dpm", referenceDpm);
        json.addProperty("evidence", evidence);
        return json;
    }
}
