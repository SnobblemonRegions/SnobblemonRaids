package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional build-time recorded performances. Missing/unverified audio always falls back to text. */
public final class WorldBossVoicePack {
    record Recording(String script, String scriptHash, String file, String sha256, int durationMillis, byte[] data) {
        Recording { data = data.clone(); }
        String soundId() { return "cobbleraids_worldboss:voice/" + script; }
    }
    private static Map<String, Recording> recordings = Map.of();
    private static boolean registered;
    private WorldBossVoicePack() {}
    static Recording recording(WorldBossSceneScript script) { var recording = recordings.get(script.id()); return recording != null && recording.scriptHash().equals(script.fingerprint()) ? recording : null; }
    static boolean compatible(Recording recording, WorldBossSceneScript script, String pinnedHash) {
        return recording != null && recording.scriptHash().equals(script.fingerprint()) && (pinnedHash.isEmpty() || pinnedHash.equals(recording.sha256()));
    }
    static int available() { return recordings.size(); }
    public static void register() {
        if (registered) return; registered = true;
        try (var in = WorldBossVoicePack.class.getResourceAsStream("/worldboss_voice/manifest.json")) {
            if (in == null) throw new IOException("Missing voice manifest");
            var manifest = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (manifest.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unknown voice manifest version");
            var loaded = new LinkedHashMap<String, Recording>();
            for (var element : manifest.getAsJsonArray("recordings")) {
                var row = element.getAsJsonObject(); var script = WorldBossSceneScript.catalogue().stream().filter(value -> value.id().equals(row.get("script").getAsString())).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown voice script"));
                String file = row.get("file").getAsString(); if (!file.matches("worldboss/v1/[a-z0-9_]+\\.ogg")) throw new IllegalArgumentException("Invalid voice path");
                try (var audio = WorldBossVoicePack.class.getResourceAsStream("/worldboss_voice/" + file)) {
                    if (audio == null) throw new IOException("Missing recording for " + script.id());
                    byte[] data = audio.readNBytes(4 * 1024 * 1024 + 1); var recording = verify(row, script, data);
                    if (loaded.putIfAbsent(script.id(), recording) != null) throw new IllegalArgumentException("Duplicate voice script");
                }
            }
            recordings = Map.copyOf(loaded);
        } catch (IOException | RuntimeException failure) { recordings = Map.of(); RaidLog.error("World Boss recorded voice assets unavailable; scene text remains enabled", failure); }
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> RaidFaultBarrier.guard("resource-pack:worldboss-voice", () -> {
            if (recordings.isEmpty()) return;
            var sounds = new JsonObject();
            recordings.values().forEach(recording -> {
                builder.addData("assets/cobbleraids_worldboss/sounds/" + recording.file(), recording.data());
                var definition = new JsonObject(); var list = new JsonArray(); var sound = new JsonObject();
                sound.addProperty("name", "cobbleraids_worldboss:" + recording.file().substring(0, recording.file().length() - 4)); sound.addProperty("stream", true); list.add(sound); definition.add("sounds", list);
                sounds.add("voice/" + recording.script(), definition);
            });
            builder.addData("assets/cobbleraids_worldboss/sounds.json", sounds.toString().getBytes(StandardCharsets.UTF_8));
        }));
    }
    static Recording verify(JsonObject row, WorldBossSceneScript script, byte[] data) {
        if (!row.get("redistribution_approved").getAsBoolean() || row.get("performer").getAsString().isBlank() || row.get("license").getAsString().isBlank()) throw new IllegalArgumentException("Voice performance needs recorded rights evidence");
        int duration = row.get("duration_ms").getAsInt();
        String file = row.get("file").getAsString(); String expected = row.get("sha256").getAsString();
        if (!row.get("script").getAsString().equals(script.id()) || !row.get("script_sha256").getAsString().equals(script.fingerprint()) || duration < 1 || duration > script.durationMillis() - 1000
                || !file.matches("worldboss/v1/[a-z0-9_]+\\.ogg") || !expected.matches("[0-9a-f]{64}") || data.length < 32 || data.length > 4 * 1024 * 1024
                || data[0] != 'O' || data[1] != 'g' || data[2] != 'g' || data[3] != 'S') throw new IllegalArgumentException("Voice metadata or container is invalid");
        try { if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).equals(expected)) throw new IllegalArgumentException("Voice checksum mismatch"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return new Recording(script.id(), script.fingerprint(), file, expected, duration, data);
    }
}
