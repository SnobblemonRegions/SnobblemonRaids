package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Strict same-generation journal; cached progress/ownership is rebuilt, never trusted from disk. */
final class WorldheartCollectionCodec {
    private WorldheartCollectionCodec() {}
    static CompoundTag write(WorldheartCollection collection) {
        var tag = new CompoundTag(); tag.putInt("version", 2); var snapshot = collection.snapshot(); tag.putString("roll_secret", snapshot.secret());
        var selections = new ListTag(); snapshot.legacySelections().forEach((player, character) -> { var row = new CompoundTag(); row.putUUID("player", player); row.putUUID("character", character); selections.add(row); }); tag.put("selections", selections);
        var victories = new ListTag();
        for (var victory : snapshot.victories()) {
            var row = new CompoundTag(); row.putUUID("event", victory.event()); row.putUUID("character", victory.character()); row.putLong("awakening", victory.awakening()); row.putLong("at", victory.atMillis());
            row.putBoolean("blessed", victory.blessing() != null);
            if (victory.blessing() != null) { row.putLong("blessing_start", victory.blessing().start()); row.putLong("blessing_until", victory.blessing().until()); }
            var awards = new ListTag(); victory.awards().forEach((player, award) -> {
                var at = new CompoundTag(); at.putUUID("player", player); at.putInt("step", award.step()); at.putInt("roll", award.roll());
                at.putBoolean("legacy", award.legacyCharacter() != null);
                if (award.legacyCharacter() != null) { at.putUUID("legacy_character", award.legacyCharacter()); at.putInt("legacy_step", award.legacyStep()); }
                awards.add(at);
            }); row.put("awards", awards); victories.add(row);
        }
        tag.put("victories", victories); return tag;
    }
    static WorldheartCollection read(CompoundTag tag) {
        int version = integer(tag, "version"); if (version != 1 && version != 2) throw new IllegalArgumentException("Unknown Worldheart collection version");
        Map<UUID, UUID> selections = new LinkedHashMap<>(); var rows = rows(tag, "selections");
        for (int i = 0; i < rows.size(); i++) { var row = rows.getCompound(i); if (selections.putIfAbsent(uuid(row, "player"), uuid(row, "character")) != null) throw new IllegalArgumentException("Duplicate legacy selection"); }
        var victories = new ArrayList<WorldheartCollection.Victory>(); rows = rows(tag, "victories");
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i); var awards = new LinkedHashMap<UUID, WorldheartCollection.Award>(); var entries = rows(row, "awards");
            for (int j = 0; j < entries.size(); j++) {
                var at = entries.getCompound(j); require(at, "legacy", Tag.TAG_BYTE); boolean legacy = at.getBoolean("legacy");
                if (!legacy && (at.contains("legacy_character") || at.contains("legacy_step"))) throw new IllegalArgumentException("Disabled legacy contains a grant");
                var award = new WorldheartCollection.Award(integer(at, "step"), integer(at, "roll"), legacy ? uuid(at, "legacy_character") : null, legacy ? integer(at, "legacy_step") : 0);
                if (awards.putIfAbsent(uuid(at, "player"), award) != null) throw new IllegalArgumentException("Duplicate Worldheart recipient");
            }
            WorldBossBlessing.Window blessing = null;
            if (version == 2) {
                require(row, "blessed", Tag.TAG_BYTE);
                if (row.getBoolean("blessed")) blessing = new WorldBossBlessing.Window(number(row, "blessing_start"), number(row, "blessing_until"));
                else if (row.contains("blessing_start") || row.contains("blessing_until")) throw new IllegalArgumentException("Unblessed victory contains bounds");
            } else if (row.contains("blessed") || row.contains("blessing_start") || row.contains("blessing_until")) throw new IllegalArgumentException("Legacy collection contains blessing data");
            victories.add(new WorldheartCollection.Victory(uuid(row, "event"), uuid(row, "character"), number(row, "awakening"), number(row, "at"), awards, blessing));
        }
        require(tag, "roll_secret", Tag.TAG_STRING);
        return new WorldheartCollection(new WorldheartCollection.Snapshot(tag.getString("roll_secret"), victories, selections));
    }
    private static void require(CompoundTag tag, String key, int type) { if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing Worldheart " + key); }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static UUID uuid(CompoundTag tag, String key) { if (!tag.hasUUID(key)) throw new IllegalArgumentException("Invalid Worldheart UUID"); return tag.getUUID(key); }
    private static ListTag rows(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LIST); var list = (ListTag) tag.get(key); if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid Worldheart rows"); return list; }
}
