package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbleraids.config.RaidDefinition;
import com.cobbleraids.config.RaidBossTraits;
import com.cobbleraids.config.RaidRarityTier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class WorldBossCalibrationTest {
    private static final String DEFINITION = "cobbleraids:giratina", HASH = "a".repeat(64);
    private WorldBossCalibration calibration(String dpm) { return new WorldBossCalibration(DEFINITION, HASH, new BigDecimal(dpm), "test-fixture-not-production-data"); }
    private RaidDefinition definition(String move) {
        return new RaidDefinition(ResourceLocation.parse(DEFINITION), ResourceLocation.parse("cobblemon:giratina"), RaidRarityTier.LEGENDARY,
                70, 3000, new RaidDefinition.Spawn(false, 1, List.of(), List.of(), List.of(), List.of(), 0, 60, 60, 1),
                new RaidDefinition.Recruitment(60, 16, 1), new RaidDefinition.Scaling(0), 240, true, List.of(move), RaidBossTraits.NONE,
                new RaidDefinition.Rewards(Map.of(), new RaidDefinition.ContributionBonus(false, List.of(), List.of()), List.of()));
    }

    @Test void healthUsesTheExactDecimalReferenceAndRoundHalfUp() {
        var calibration = calibration("100.012");
        assertEquals(4000, calibration.healthFor(5));
        assertEquals(24003, calibration.healthFor(30));
        assertEquals(40005, calibration.healthFor(50));
        assertEquals(calibration, WorldBossCalibration.fromJson(calibration.toJson()));
    }

    @Test void invalidMeasurementsAndNumericalEnvelopesCannotBecomeLiveSettings() {
        for (String bad : new String[]{"0", "-1", "0.001", "1.0001", "50000", "1e100000000"}) {
            assertThrows(RuntimeException.class, () -> calibration(bad));
        }
        assertThrows(IllegalArgumentException.class, () -> new WorldBossCalibration(DEFINITION, HASH, BigDecimal.ONE, ""));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossCalibration(DEFINITION, "unknown", BigDecimal.ONE, "report"));
        assertThrows(IllegalArgumentException.class, () -> calibration("1").healthFor(51));
    }

    @Test void absentCalibrationStaysAbsentAndConfigurationRoundTripsEvidence() {
        var config = WorldBossConfig.defaults();
        assertTrue(config.calibrations().isEmpty());
        var json = config.toJson();
        json.getAsJsonArray("calibrations").add(calibration("6000").toJson());
        var parsed = WorldBossConfig.fromJson(json);
        assertEquals(calibration("6000"), parsed.calibrations().getFirst());
        assertEquals(parsed, WorldBossConfig.fromJson(parsed.toJson()));
        json.getAsJsonArray("calibrations").add(calibration("7000").toJson());
        assertThrows(IllegalArgumentException.class, () -> WorldBossConfig.fromJson(json));
    }

    @Test void testOnlyReferencesAreNeverAcceptedAsLiveConfiguration() {
        var json = WorldBossConfig.defaults().toJson();
        json.getAsJsonArray("calibrations").add(new WorldBossCalibration(DEFINITION, HASH, BigDecimal.valueOf(6000), "TEST_ONLY_UNCALIBRATED").toJson());
        assertThrows(IllegalArgumentException.class, () -> WorldBossConfig.fromJson(json));
    }

    @Test void fingerprintBindsDefinitionCombatInputsAndRuntimeButNotVisualSettings() {
        var config = WorldBossConfig.defaults();
        var boss = definition("shadowball");
        String fingerprint = WorldBossOpeningRules.fingerprint(config, boss, "fixture-pack@1");
        assertEquals(64, fingerprint.length());
        assertNotEquals(fingerprint, WorldBossOpeningRules.fingerprint(config, definition("dragonclaw"), "fixture-pack@1"));
        assertNotEquals(fingerprint, WorldBossOpeningRules.fingerprint(config, boss, "fixture-pack@2"));
        var visual = config.toJson();
        visual.getAsJsonObject("visual").addProperty("boss_scale", 5);
        assertEquals(fingerprint, WorldBossOpeningRules.fingerprint(WorldBossConfig.fromJson(visual), boss, "fixture-pack@1"));
        var changed = config.toJson();
        changed.getAsJsonObject("duel").addProperty("boss_level", 90);
        var newConfig = WorldBossConfig.fromJson(changed);
        assertNotEquals(fingerprint, WorldBossOpeningRules.fingerprint(newConfig, boss, "fixture-pack@1"));
        var measured = new WorldBossCalibration(DEFINITION, fingerprint, BigDecimal.valueOf(6000), "fixture");
        var frozen = WorldBossOpeningRules.capture(config, measured);
        assertEquals(fingerprint, frozen.fingerprint(boss, "fixture-pack@1"), "Frozen duel inputs survive a subsequent config reload");
        assertNotEquals(fingerprint, frozen.legacyFingerprint(boss, "fixture-pack@1"), "Old live encounters retain their earlier calibrated policy");
        assertNotEquals(fingerprint, frozen.focusOnlyFingerprint(boss, "fixture-pack@1"), "Relay-enabled fights require their own calibration");
        assertNotEquals(fingerprint, frozen.relayOnlyFingerprint(boss, "fixture-pack@1"), "Admission/grace rules are part of the new calibration policy");
        assertNotEquals(frozen.legacyFingerprint(boss, "fixture-pack@1"), frozen.focusOnlyFingerprint(boss, "fixture-pack@1"));
        assertEquals(config.duelBossLevel(), frozen.duelLevel());
    }
}
