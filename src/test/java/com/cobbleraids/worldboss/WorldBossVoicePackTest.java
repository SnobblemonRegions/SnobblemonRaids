package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WorldBossVoicePackTest {
    private byte[] containerHeaderFixture() { byte[] bytes = new byte[32]; bytes[0] = 'O'; bytes[1] = 'g'; bytes[2] = 'g'; bytes[3] = 'S'; return bytes; }
    private JsonObject metadata(WorldBossSceneScript script, byte[] bytes) throws Exception {
        var row = new JsonObject(); row.addProperty("script", script.id()); row.addProperty("script_sha256", script.fingerprint()); row.addProperty("file", "worldboss/v1/fixture.ogg");
        row.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); row.addProperty("duration_ms", 1000);
        row.addProperty("performer", "Unit test fixture"); row.addProperty("license", "Fixture only, not playable audio"); row.addProperty("redistribution_approved", true); return row;
    }
    @Test void voiceMetadataBindsTheExactScriptFileHashAndDeclaredDuration() throws Exception {
        var script = WorldBossSceneScript.catalogue().getFirst(); byte[] bytes = containerHeaderFixture(); var row = metadata(script, bytes);
        var result = WorldBossVoicePack.verify(row, script, bytes); assertEquals(script.fingerprint(), result.scriptHash()); assertEquals("cobbleraids_worldboss:voice/" + script.id(), result.soundId());
        row.addProperty("sha256", "a".repeat(64)); assertThrows(IllegalArgumentException.class, () -> WorldBossVoicePack.verify(row, script, bytes));
    }
    @Test void missingRightsDifferentSubtitlesUnsafePathsAndOverlongTakesAreRejected() throws Exception {
        var script = WorldBossSceneScript.catalogue().getFirst(); byte[] bytes = containerHeaderFixture();
        var rights = metadata(script, bytes); rights.addProperty("redistribution_approved", false); assertThrows(IllegalArgumentException.class, () -> WorldBossVoicePack.verify(rights, script, bytes));
        var text = metadata(script, bytes); text.addProperty("script_sha256", "0".repeat(64)); assertThrows(IllegalArgumentException.class, () -> WorldBossVoicePack.verify(text, script, bytes));
        var path = metadata(script, bytes); path.addProperty("file", "../bad.ogg"); assertThrows(IllegalArgumentException.class, () -> WorldBossVoicePack.verify(path, script, bytes));
        var duration = metadata(script, bytes); duration.addProperty("duration_ms", script.durationMillis()); assertThrows(IllegalArgumentException.class, () -> WorldBossVoicePack.verify(duration, script, bytes));
    }
    @Test void unloadedOptionalRegistryFallsBackAndTheManifestIsPackaged() throws Exception {
        assertNull(WorldBossVoicePack.recording(WorldBossSceneScript.catalogue().getFirst()));
        try (var stream = WorldBossVoicePackTest.class.getResourceAsStream("/worldboss_voice/manifest.json")) { assertNotNull(stream); var manifest = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); assertEquals(1, manifest.get("version").getAsInt()); assertNotNull(manifest.getAsJsonArray("recordings")); }
    }
    @Test void everyPackagedPerformanceMatchesItsApprovedMetadataAndFrozenDialogue() throws Exception {
        try (var stream = WorldBossVoicePackTest.class.getResourceAsStream("/worldboss_voice/manifest.json")) {
            assertNotNull(stream); var manifest = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); var seen = new java.util.HashSet<String>();
            for (var element : manifest.getAsJsonArray("recordings")) {
                var row = element.getAsJsonObject(); var script = WorldBossSceneScript.catalogue().stream().filter(value -> value.id().equals(row.get("script").getAsString())).findFirst().orElseThrow();
                assertTrue(seen.add(script.id()));
                try (var data = WorldBossVoicePackTest.class.getResourceAsStream("/worldboss_voice/" + row.get("file").getAsString())) { assertNotNull(data); assertNotNull(WorldBossVoicePack.verify(row, script, data.readAllBytes())); }
            }
        }
    }
    @Test void recordedHistoryNeverSilentlySubstitutesADifferentPerformance() throws Exception {
        var script = WorldBossSceneScript.catalogue().getFirst(); var bytes = containerHeaderFixture(); var recording = WorldBossVoicePack.verify(metadata(script, bytes), script, bytes);
        assertTrue(WorldBossVoicePack.compatible(recording, script, recording.sha256()));
        assertFalse(WorldBossVoicePack.compatible(recording, script, "a".repeat(64)));
        assertTrue(WorldBossVoicePack.compatible(recording, script, ""), "An originally text-only chapter may use a first approved matching performance");
    }
}
