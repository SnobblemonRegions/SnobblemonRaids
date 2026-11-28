package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Durable scene intent and immutable transcript. Never grants an outcome or any reward. */
final class WorldBossSceneLedger {
    record Key(UUID event, WorldBossSceneScript.Kind kind) { Key { java.util.Objects.requireNonNull(event); java.util.Objects.requireNonNull(kind); } }
    record Receipt(Key key, UUID character, String name, long awakening, long atMillis, String dimension, double x, double y, double z, double radius, WorldBossSceneScript script, String voiceHash) {
        Receipt(Key key, UUID character, String name, long awakening, long atMillis, String dimension, double x, double y, double z, double radius, WorldBossSceneScript script) {
            this(key, character, name, awakening, atMillis, dimension, x, y, z, radius, script, "");
        }
        Receipt {
            java.util.Objects.requireNonNull(key); java.util.Objects.requireNonNull(character); java.util.Objects.requireNonNull(script);
            if (name == null || name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl) || name.indexOf('\u00a7') >= 0 || awakening < 1 || atMillis <= 0
                    || dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Double.isFinite(radius) || radius <= 0 || radius > 10_000 || script.kind() != key.kind() || voiceHash == null || !voiceHash.isEmpty() && !voiceHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid scene receipt");
        }
        boolean inside(String dim, double px, double py, double pz) { return dimension.equals(dim) && WorldBossZone.inside(x, y, z, radius, px, py, pz); }
    }
    private final Map<Key, Receipt> receipts;
    WorldBossSceneLedger() { this(Map.of()); }
    WorldBossSceneLedger(Map<Key, Receipt> receipts) { this.receipts = Map.copyOf(receipts); receipts.forEach((key, receipt) -> { if (!key.equals(receipt.key())) throw new IllegalArgumentException("Scene key mismatch"); }); }
    Map<Key, Receipt> receipts() { return receipts; }
    Receipt find(UUID character, WorldBossSceneScript.Kind kind, Long awakening) {
        return receipts.values().stream().filter(row -> row.character().equals(character) && row.key().kind() == kind && (awakening == null || row.awakening() == awakening))
                .max(java.util.Comparator.comparingLong(Receipt::awakening).thenComparingLong(Receipt::atMillis)).orElse(null);
    }
    WorldBossSceneLedger record(WorldBossCampaign.Occurrence event, WorldBossSceneScript.Kind kind, long atMillis, String dimension, double x, double y, double z, double radius) {
        var key = new Key(event.eventId(), kind); if (receipts.containsKey(key)) return this;
        var script = WorldBossSceneScript.select(event.definition(), event.awakening(), kind); var recording = WorldBossVoicePack.recording(script);
        var receipt = new Receipt(key, event.characterId(), event.name(), event.awakening(), atMillis, dimension, x, y, z, radius, script, recording == null ? "" : recording.sha256());
        var updated = new LinkedHashMap<>(receipts); updated.put(key, receipt); return new WorldBossSceneLedger(updated);
    }
    void validate(WorldBossCampaign campaign) {
        var events = new java.util.HashMap<UUID, WorldBossCampaign.Occurrence>(); campaign.occurrences().values().forEach(event -> events.put(event.eventId(), event));
        for (var receipt : receipts.values()) {
            var event = events.get(receipt.key().event());
            if (event == null || !event.characterId().equals(receipt.character()) || !event.name().equals(receipt.name()) || event.awakening() != receipt.awakening()) throw new IllegalArgumentException("Scene identity disagrees with campaign");
            switch (receipt.key().kind()) {
                case ENTRANCE -> { if (event.status() == WorldBossCampaign.Status.PLANNED || (event.cues() & WorldBossCampaign.ENTRANCE) == 0 || receipt.atMillis() != event.slot().entranceAt().toEpochMilli()) throw new IllegalArgumentException("Uncommitted entrance scene"); }
                case VICTORY -> { if (event.status() != WorldBossCampaign.Status.VICTORY || receipt.atMillis() < event.combatAtMillis()) throw new IllegalArgumentException("Uncommitted victory scene"); }
                case ESCAPE -> { if (event.status() != WorldBossCampaign.Status.CLOSED || !event.reason().equals("TIMEOUT") || receipt.atMillis() < event.combatAtMillis()) throw new IllegalArgumentException("Escape scene is not a natural timeout"); }
            }
        }
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("version", 1); var rows = new ListTag();
        receipts.values().forEach(receipt -> {
            var row = new CompoundTag(); row.putUUID("event", receipt.key().event()); row.putString("kind", receipt.key().kind().name()); row.putUUID("character", receipt.character()); row.putString("name", receipt.name()); row.putLong("awakening", receipt.awakening()); row.putLong("at", receipt.atMillis());
            row.putString("dimension", receipt.dimension()); row.putDouble("x", receipt.x()); row.putDouble("y", receipt.y()); row.putDouble("z", receipt.z()); row.putDouble("radius", receipt.radius());
            row.putString("script", receipt.script().id()); row.putString("voice_sha256", receipt.voiceHash()); row.putInt("duration", receipt.script().durationMillis()); var beats = new ListTag();
            receipt.script().beats().forEach(beat -> { var at = new CompoundTag(); at.putInt("at", beat.atMillis()); at.putString("text", beat.text()); beats.add(at); }); row.put("beats", beats); rows.add(row);
        }); tag.put("receipts", rows); return tag;
    }
    static WorldBossSceneLedger load(CompoundTag tag) {
        if (integer(tag, "version") != 1) throw new IllegalArgumentException("Unknown scene ledger version");
        var receipts = new LinkedHashMap<Key, Receipt>(); var rows = rows(tag, "receipts");
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i); var key = new Key(uuid(row, "event"), WorldBossSceneScript.Kind.valueOf(text(row, "kind"))); var beats = new ArrayList<WorldBossSceneScript.Beat>(); var lines = rows(row, "beats");
            if (lines.isEmpty() || lines.size() > 8) throw new IllegalArgumentException("Scene transcript exceeds its bounds");
            for (int j = 0; j < lines.size(); j++) { var at = lines.getCompound(j); beats.add(new WorldBossSceneScript.Beat(integer(at, "at"), text(at, "text"))); }
            var script = new WorldBossSceneScript(text(row, "script"), key.kind(), integer(row, "duration"), beats);
            var receipt = new Receipt(key, uuid(row, "character"), text(row, "name"), number(row, "awakening"), number(row, "at"), text(row, "dimension"), decimal(row, "x"), decimal(row, "y"), decimal(row, "z"), decimal(row, "radius"), script, text(row, "voice_sha256"));
            if (receipts.putIfAbsent(key, receipt) != null) throw new IllegalArgumentException("Duplicate scene receipt");
        }
        return new WorldBossSceneLedger(receipts);
    }
    private static void require(CompoundTag tag, String key, int type) { if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing scene " + key); }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static double decimal(CompoundTag tag, String key) { require(tag, key, Tag.TAG_DOUBLE); return tag.getDouble(key); }
    private static String text(CompoundTag tag, String key) { require(tag, key, Tag.TAG_STRING); return tag.getString(key); }
    private static UUID uuid(CompoundTag tag, String key) { if (!tag.hasUUID(key)) throw new IllegalArgumentException("Invalid scene UUID"); return tag.getUUID(key); }
    private static ListTag rows(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LIST); var rows = (ListTag) tag.get(key); if (!rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid scene rows"); return rows; }
}
