package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class WorldBossAdmissionCodec {
    private WorldBossAdmissionCodec() {}
    static CompoundTag write(WorldBossAdmission.Snapshot snapshot) {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putUUID("event", snapshot.event()); tag.putInt("capacity", snapshot.capacity()); tag.putLong("breather", snapshot.breatherMillis()); tag.putLong("generation", snapshot.generation()); tag.putBoolean("closed", snapshot.closed()); tag.putBoolean("cutoff", snapshot.cutoffEnabled()); tag.putLong("checkpoint", snapshot.checkpointMillis());
        var slots = new ListTag(); snapshot.slots().forEach(slot -> { var row = new CompoundTag(); row.putUUID("player", slot.token().playerId()); row.putUUID("encounter", slot.token().encounterId()); row.putLong("generation", slot.token().generation()); row.putString("phase", slot.phase().name()); row.putLong("available", slot.availableAtMillis()); slots.add(row); }); tag.put("slots", slots);
        var queue = new ListTag(); snapshot.waiting().forEach(player -> { var row = new CompoundTag(); row.putUUID("player", player); queue.add(row); }); tag.put("queue", queue);
        var delays = new ListTag(); snapshot.cooldowns().forEach((player, until) -> { var row = new CompoundTag(); row.putUUID("player", player); row.putLong("until", until); delays.add(row); }); tag.put("cooldowns", delays);
        var admitted = new ListTag(); snapshot.admitted().forEach(player -> { var row = new CompoundTag(); row.putUUID("player", player); admitted.add(row); }); tag.put("admitted", admitted); return tag;
    }
    static WorldBossAdmission.Snapshot read(CompoundTag tag) {
        if (integer(tag, "version") != 1) throw new IllegalArgumentException("Unknown admission snapshot version");
        var event = uuid(tag, "event"); var slots = new ArrayList<WorldBossAdmission.Slot>(); var rows = rows(tag, "slots");
        if (rows.size() > 50) throw new IllegalArgumentException("Too many saved slots");
        for (int i = 0; i < rows.size(); i++) { var row = rows.getCompound(i); slots.add(new WorldBossAdmission.Slot(new WorldBossAdmission.Token(event, uuid(row, "player"), number(row, "generation"), uuid(row, "encounter")), WorldBossAdmission.Phase.valueOf(text(row, "phase")), number(row, "available"))); }
        var queue = new ArrayList<UUID>(); rows = rows(tag, "queue"); for (int i = 0; i < rows.size(); i++) queue.add(uuid(rows.getCompound(i), "player"));
        var cooldowns = new LinkedHashMap<UUID, Long>(); rows = rows(tag, "cooldowns");
        for (int i = 0; i < rows.size(); i++) { var row = rows.getCompound(i); if (cooldowns.putIfAbsent(uuid(row, "player"), number(row, "until")) != null) throw new IllegalArgumentException("Duplicate saved cooldown"); }
        var admitted = new LinkedHashSet<UUID>(); rows = rows(tag, "admitted"); for (int i = 0; i < rows.size(); i++) if (!admitted.add(uuid(rows.getCompound(i), "player"))) throw new IllegalArgumentException("Duplicate admitted player");
        require(tag, "closed", Tag.TAG_BYTE);
        require(tag, "cutoff", Tag.TAG_BYTE);
        return new WorldBossAdmission.Snapshot(event, integer(tag, "capacity"), number(tag, "breather"), number(tag, "generation"), tag.getBoolean("closed"), tag.getBoolean("cutoff"), number(tag, "checkpoint"), slots, queue, cooldowns, admitted);
    }
    private static void require(CompoundTag tag, String key, int type) { if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing admission " + key); }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static String text(CompoundTag tag, String key) { require(tag, key, Tag.TAG_STRING); return tag.getString(key); }
    private static UUID uuid(CompoundTag tag, String key) { if (!tag.hasUUID(key)) throw new IllegalArgumentException("Invalid admission UUID"); return tag.getUUID(key); }
    private static ListTag rows(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LIST); var list = (ListTag) tag.get(key); if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid admission rows"); return list; }
}
