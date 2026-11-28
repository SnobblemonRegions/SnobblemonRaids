package com.cobbleraids.worldboss;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Strict, versioned campaign representation; unknown/corrupt data is quarantined by the owner. */
final class WorldBossCampaignCodec {
    private WorldBossCampaignCodec() {}

    static CompoundTag write(WorldBossCampaign campaign) {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putString("active", campaign.activeSeason() == null ? "" : campaign.activeSeason());
        tag.putBoolean("daily_enabled", campaign.dailyEnabled());
        tag.putLong("enabled_at", campaign.enabledAtMillis());
        tag.putLong("wall_high_water", campaign.wallHighWaterMillis());
        if (campaign.staged() != null) tag.put("staged", season(campaign.staged()));
        var seasons = new ListTag(); campaign.seasons().values().forEach(value -> seasons.add(season(value))); tag.put("seasons", seasons);
        var events = new ListTag();
        for (var event : campaign.occurrences().values()) {
            var row = new CompoundTag();
            row.putString("key", event.key()); row.putString("season", event.season()); row.putUUID("character", event.characterId());
            row.putString("name", event.name()); row.putString("definition", event.definition()); row.putLong("awakening", event.awakening());
            row.putLong("combat_at", event.combatAtMillis()); row.putInt("offset", event.offsetSeconds()); row.putUUID("event", event.eventId());
            row.putString("status", event.status().name()); row.putInt("cues", event.cues()); row.putString("reason", event.reason()); events.add(row);
        }
        tag.put("occurrences", events);
        return tag;
    }

    static WorldBossCampaign read(CompoundTag tag) {
        if (integer(tag, "version") != 1) throw new IllegalArgumentException("Unknown campaign version");
        WorldBossCampaign.Season staged = null;
        if (tag.contains("staged")) { require(tag, "staged", Tag.TAG_COMPOUND); staged = season(tag.getCompound("staged")); }
        Map<String, WorldBossCampaign.Season> seasons = new LinkedHashMap<>();
        ListTag owners = rows(tag, "seasons");
        for (int i = 0; i < owners.size(); i++) {
            var owner = season(owners.getCompound(i));
            if (seasons.putIfAbsent(owner.id(), owner) != null) throw new IllegalArgumentException("Duplicate season");
        }
        Map<String, WorldBossCampaign.Occurrence> occurrences = new LinkedHashMap<>();
        ListTag events = rows(tag, "occurrences");
        for (int i = 0; i < events.size(); i++) {
            CompoundTag row = events.getCompound(i);
            var event = new WorldBossCampaign.Occurrence(text(row, "key"), text(row, "season"), uuid(row, "character"), text(row, "name"),
                    text(row, "definition"), number(row, "awakening"), number(row, "combat_at"), integer(row, "offset"), uuid(row, "event"),
                    WorldBossCampaign.Status.valueOf(text(row, "status")), integer(row, "cues"), text(row, "reason"));
            if (occurrences.putIfAbsent(event.key(), event) != null) throw new IllegalArgumentException("Duplicate occurrence");
        }
        String active = text(tag, "active");
        require(tag, "daily_enabled", Tag.TAG_BYTE);
        byte enabled = tag.getByte("daily_enabled");
        if (enabled != 0 && enabled != 1) throw new IllegalArgumentException("Invalid daily switch");
        return new WorldBossCampaign(seasons, staged, active.isEmpty() ? null : active, enabled == 1,
                number(tag, "enabled_at"), number(tag, "wall_high_water"), occurrences);
    }

    private static CompoundTag season(WorldBossCampaign.Season season) {
        var tag = new CompoundTag(); tag.putString("id", season.id()); tag.putUUID("character", season.characterId());
        tag.putString("name", season.name()); tag.putString("definition", season.definition()); tag.putLong("victories", season.victories()); return tag;
    }
    private static WorldBossCampaign.Season season(CompoundTag tag) {
        return new WorldBossCampaign.Season(text(tag, "id"), uuid(tag, "character"), text(tag, "name"), text(tag, "definition"), number(tag, "victories"));
    }
    private static void require(CompoundTag tag, String key, int type) { if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing/invalid campaign " + key); }
    private static String text(CompoundTag tag, String key) { require(tag, key, Tag.TAG_STRING); return tag.getString(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static UUID uuid(CompoundTag tag, String key) { if (!tag.hasUUID(key)) throw new IllegalArgumentException("Invalid campaign UUID"); return tag.getUUID(key); }
    private static ListTag rows(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_LIST); var rows = (ListTag) tag.get(key);
        if (!rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid campaign rows"); return rows;
    }
}
