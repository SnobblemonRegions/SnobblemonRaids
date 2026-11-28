package com.cobbleraids.worldboss;

import com.cobbleraids.config.RaidDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Frozen combat inputs for a new encounter; a visual/config reload cannot silently alter a duel. */
public record WorldBossOpeningRules(WorldBossCalibration calibration, int duelLevel, long duelHealth,
                                     int duelTimeoutSeconds, List<String> bossBannedMoves) {
    public WorldBossOpeningRules {
        Objects.requireNonNull(calibration);
        bossBannedMoves = List.copyOf(bossBannedMoves);
        if (duelLevel < 1 || duelLevel > 100 || duelHealth < 1 || duelHealth > Long.MAX_VALUE / 100 || duelTimeoutSeconds < 30) {
            throw new IllegalArgumentException("Invalid opening battle rules");
        }
        if (bossBannedMoves.stream().anyMatch(move -> !move.matches("[a-z0-9]{1,64}"))) throw new IllegalArgumentException("Invalid boss move ban");
    }

    public static WorldBossOpeningRules capture(WorldBossConfig config, WorldBossCalibration calibration) {
        return new WorldBossOpeningRules(calibration, config.duelBossLevel(), config.duelBossHealth(),
                config.duelTimeLimitSeconds(), config.bossBannedMoves());
    }

    public static String fingerprint(WorldBossConfig config, RaidDefinition definition, String runtimeSignature) {
        return fingerprint(config.duelBossLevel(), config.duelBossHealth(), config.duelTimeLimitSeconds(), config.bossBannedMoves(), definition, runtimeSignature, 25);
    }

    public String fingerprint(RaidDefinition definition, String runtimeSignature) {
        return fingerprint(duelLevel, duelHealth, duelTimeoutSeconds, bossBannedMoves, definition, runtimeSignature, 25);
    }
    String relayOnlyFingerprint(RaidDefinition definition, String runtimeSignature) { return fingerprint(duelLevel, duelHealth, duelTimeoutSeconds, bossBannedMoves, definition, runtimeSignature, 3); }

    String focusOnlyFingerprint(RaidDefinition definition, String runtimeSignature) {
        return fingerprint(duelLevel, duelHealth, duelTimeoutSeconds, bossBannedMoves, definition, runtimeSignature, 2);
    }

    public String legacyFingerprint(RaidDefinition definition, String runtimeSignature) {
        return fingerprint(duelLevel, duelHealth, duelTimeoutSeconds, bossBannedMoves, definition, runtimeSignature, 1);
    }

    private static String fingerprint(int level, long health, int timeout, List<String> moveBans, RaidDefinition definition, String runtimeSignature, int policy) {
        JsonObject rules = new JsonObject();
        rules.addProperty("policy", switch (policy) {
            case 1 -> "concord-fixed-v1:milli-hp:heal:15s-breather:no-bag-items:carryover:ascension";
            case 2 -> "concord-fixed-v2:milli-hp:heal:15s-breather:no-bag-items:carryover:ascension:merit100:wards45:combat+focus:offense0";
            case 3 -> "concord-fixed-v3:milli-hp:heal:15s-breather:no-bag-items:carryover:ascension:merit100:wards45:combat+focus+relay:level-legs8-24:corridor2.5:step3:reach1.5:channel6:offense0";
            default -> "concord-fixed-v27:milli-hp:heal:15s-breather:no-bag-items:carryover:ascension:merit100:wards45:combat+focus+relay:level-legs8-24:corridor2.5:step3:reach1.5:channel6:offense0:admission60:grace30:checkpoint-fifo:hysteresis1:protected-arena:reserved-lane6x8:boss-body4x8:no-air-fallback:observers25:arrivals2:queue75:landing96:frozen-audience:duel-start1per250ms:startup-turn-warmup:ignored-summary-host-warmup:preload-listbuilder-native-dto:math-min:protected-natural-pokemon-spawns:packed-bootstrap3:optional-ascensionfx-v1:no-unrequested-team-preview:native-request-codec:native-host-bootstrap:message-switch-renown-preparation:early-player-spawn-cells:async-periodic-journal:private-worldboss-simulator-v1:arrival-batch8-two-per-tick-ack-v1:whole-protected-spawn-area-v2:early-herd-cells-v1:async-start-recover-ack-v1:async-opening-ack-v1:stationary-dome-view-v1:arena-player-autosave-v1:async-staff-hold-ack-v1:scoped-card-stat-debug-v1:native-journal-autosave-ack-v1:scoped-card-info-async128-v1:detached-worker-thread-contexts-v1:scoped-native-world-nbt128-v1:paced-native-advancements1per-tick-v1:worldboss-duel-debug-v1:cached-travel-dimension-pattern-v1";
        });
        rules.addProperty("runtime", runtimeSignature);
        rules.addProperty("definition", definition.id().toString());
        rules.addProperty("species", definition.species().toString());
        rules.addProperty("rarity", definition.rarityTier().name());
        rules.addProperty("definition_time_limit", definition.timeLimitSeconds());
        rules.addProperty("allow_flee", definition.allowFlee());
        JsonArray moves = new JsonArray();
        definition.moves().forEach(moves::add);
        rules.add("moves", moves);
        rules.add("traits", new Gson().toJsonTree(definition.traits()));
        rules.addProperty("duel_level", level);
        rules.addProperty("duel_health", health);
        rules.addProperty("duel_timeout_seconds", timeout);
        JsonArray banned = new JsonArray();
        new TreeSet<>(moveBans).forEach(banned::add);
        rules.add("boss_banned_moves", banned);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(rules).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) sorted.add(key, canonical(value.getAsJsonObject().get(key)));
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray array = new JsonArray();
            value.getAsJsonArray().forEach(item -> array.add(canonical(item)));
            return array;
        }
        return value.deepCopy();
    }

    static String canonicalText(JsonElement value) { return canonical(value).toString(); }
}
