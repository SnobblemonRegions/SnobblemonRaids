package com.cobbleraids.worldboss;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * How many World Boss defeat rewards each player was paid today (UTC), for the optional {@code reward.max_per_player_per_day} cap. Only the current
 * day is kept. The cap is per account, so it limits a player's farming, not a player's alts.
 */
public final class WorldBossPayoutStore extends SavedData {
    private static final String FILE_ID = "cobbleraids_worldboss_payouts";

    private record Day(String key, int paid) {}

    private final Map<UUID, Day> players = new LinkedHashMap<>();

    public static WorldBossPayoutStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldBossPayoutStore::new, WorldBossPayoutStore::load, null), FILE_ID);
    }

    /** The key of today, in UTC, so the day does not depend on the server's time zone. */
    public static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    public int paidOn(UUID player, String dayKey) {
        Day day = players.get(player);
        return day != null && day.key().equals(dayKey) ? day.paid() : 0;
    }

    /** Records one more reward paid on that day; returns the day's new total. */
    public int record(UUID player, String dayKey) {
        int paid = paidOn(player, dayKey) + 1;
        players.put(player, new Day(dayKey, paid));
        setDirty();
        return paid;
    }

    /** Writes to disk now, like the boss state, so a payment is never forgotten by a crash right after it. */
    public void flush(MinecraftServer server) throws java.io.IOException {
        com.cobbleraids.config.SavedDataFlush.flush(server, this, FILE_ID);
    }

    static WorldBossPayoutStore load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldBossPayoutStore store = new WorldBossPayoutStore();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            if (!item.hasUUID("player")) continue;
            store.players.put(item.getUUID("player"), new Day(item.getString("day"), item.getInt("paid")));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Day> entry : players.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", entry.getKey());
            item.putString("day", entry.getValue().key());
            item.putInt("paid", entry.getValue().paid());
            list.add(item);
        }
        tag.put("players", list);
        return tag;
    }
}
