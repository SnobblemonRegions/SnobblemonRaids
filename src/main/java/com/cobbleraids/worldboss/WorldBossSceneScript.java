package com.cobbleraids.worldboss;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Versioned authored dialogue. Captured in scene receipts so later edits cannot rewrite history. */
record WorldBossSceneScript(String id, Kind kind, int durationMillis, List<Beat> beats) {
    enum Kind { ENTRANCE, VICTORY, ESCAPE }
    record Beat(int atMillis, String text) {
        Beat { if (atMillis < 0 || text == null || text.isBlank() || text.length() > 256 || text.chars().anyMatch(Character::isISOControl) || text.indexOf('\u00a7') >= 0) throw new IllegalArgumentException("Invalid scene subtitle"); }
    }
    WorldBossSceneScript {
        beats = List.copyOf(beats);
        if (id == null || !id.matches("[a-z0-9_]{1,64}") || kind == null || durationMillis < 1000 || durationMillis > (kind == Kind.ENTRANCE ? 15_000 : 12_000) || beats.isEmpty() || beats.size() > 8 || beats.getFirst().atMillis() != 0) throw new IllegalArgumentException("Invalid scene script");
        int previous = -1; for (var beat : beats) { if (beat.atMillis() <= previous || beat.atMillis() >= durationMillis) throw new IllegalArgumentException("Invalid scene subtitle order"); previous = beat.atMillis(); }
    }
    String fingerprint() {
        var value = new com.google.gson.JsonArray(); value.add("concord-scene-v1"); value.add(id); value.add(kind.name()); value.add(durationMillis);
        for (var beat : beats) { var line = new com.google.gson.JsonArray(); line.add(beat.atMillis()); line.add(beat.text()); value.add(line); }
        String text = value.toString();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static WorldBossSceneScript select(String definition, long awakening, Kind kind) {
        if (awakening < 1) throw new IllegalArgumentException("Invalid scene awakening");
        boolean distortion = definition.equals("cobbleraids:giratina");
        if (kind == Kind.ENTRANCE) {
            if (distortion && awakening == 1) return script("distortion_entrance_first_v1", kind, 14_000,
                    new Beat(0, "You built your sanctuary upon my prison."), new Beat(5000, "Did you believe I would never wake?"), new Beat(10_000, "Step forward. Let your bonds face the dark."));
            if (distortion && awakening == 3) return script("distortion_entrance_third_v1", kind, 14_000,
                    new Beat(0, "Twice you have sealed this gate."), new Beat(5000, "Twice you have mistaken survival for victory."), new Beat(10_000, "Show me what your sanctuary is worth."));
            return script(distortion ? "distortion_entrance_return_v1" : "entrance_generic_v1", kind, 14_000,
                    new Beat(0, "Your sanctuary endures. So do I."), new Beat(5000, "Gather your courage. Stand beside one another."), new Beat(10_000, "Let this place remember our battle."));
        }
        if (kind == Kind.VICTORY) {
            if (distortion && awakening == 1) return script("distortion_victory_first_v1", kind, 11_000,
                    new Beat(0, "Remember this victory, little trainers."), new Beat(4000, "I will remember every wound."), new Beat(8000, "I will return stronger."));
            return script(distortion ? "distortion_victory_respect_v1" : "victory_generic_v1", kind, 10_000,
                    new Beat(0, "That bond... I had forgotten its strength."), new Beat(4500, "Guard this sanctuary. I will return stronger."));
        }
        return script(distortion ? "distortion_escape_v1" : "escape_generic_v1", kind, 8000,
                new Beat(0, "Your wards are failing."), new Beat(4000, "Next time, bring more than courage."));
    }
    static List<WorldBossSceneScript> catalogue() {
        return List.of(select("cobbleraids:giratina", 1, Kind.ENTRANCE), select("cobbleraids:giratina", 2, Kind.ENTRANCE), select("cobbleraids:giratina", 3, Kind.ENTRANCE),
                select("cobbleraids:giratina", 1, Kind.VICTORY), select("cobbleraids:giratina", 2, Kind.VICTORY), select("cobbleraids:giratina", 1, Kind.ESCAPE),
                select("generic:boss", 1, Kind.ENTRANCE), select("generic:boss", 1, Kind.VICTORY), select("generic:boss", 1, Kind.ESCAPE));
    }
    private static WorldBossSceneScript script(String id, Kind kind, int duration, Beat... beats) { return new WorldBossSceneScript(id, kind, duration, List.of(beats)); }
}
