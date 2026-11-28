package com.cobbleraids.album;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which species each player has pressed into their Coin Album. A pressed coin lives here, not in an inventory, so it cannot be dropped,
 * stolen, traded or spent until its owner takes it out. Written to disk right after every change (see {@link #flush}), so a crash can never
 * keep a coin in both the album and the inventory, or in neither.
 */
public final class RenownAlbumStore extends SavedData {
    private static final String FILE_ID = "cobbleraids_renown_album";
    private final Map<UUID, Set<String>> pressed = new HashMap<>();

    public static RenownAlbumStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RenownAlbumStore::new, RenownAlbumStore::load, null), FILE_ID);
    }

    public Set<String> pressed(UUID player) {
        return Set.copyOf(pressed.getOrDefault(player, Set.of()));
    }

    /** Fills a slot. False when it was already filled (the caller then keeps the coin). */
    public boolean press(UUID player, String species) {
        boolean added = pressed.computeIfAbsent(player, id -> new LinkedHashSet<>()).add(species);
        if (added) setDirty();
        return added;
    }

    /** Empties a slot. False when it was empty (the caller then gives nothing back). */
    public boolean takeOut(UUID player, String species) {
        Set<String> mine = pressed.get(player);
        boolean removed = mine != null && mine.remove(species);
        if (removed) setDirty();
        return removed;
    }

    public void flush(MinecraftServer server) throws java.io.IOException {
        com.cobbleraids.config.SavedDataFlush.flush(server, this, FILE_ID);
    }

    static RenownAlbumStore load(CompoundTag tag, HolderLookup.Provider registries) {
        RenownAlbumStore store = new RenownAlbumStore();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException bad) {
                continue;
            }
            Set<String> species = new LinkedHashSet<>();
            for (Tag entry : players.getList(key, Tag.TAG_STRING)) species.add(entry.getAsString());
            if (!species.isEmpty()) store.pressed.put(id, species);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        pressed.forEach((id, species) -> {
            ListTag list = new ListTag();
            species.forEach(s -> list.add(StringTag.valueOf(s)));
            players.put(id.toString(), list);
        });
        tag.put("players", players);
        return tag;
    }
}
