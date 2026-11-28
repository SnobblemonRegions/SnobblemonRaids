package com.cobbleraids.worldboss.arena;

import com.cobbleraids.config.SavedDataFlush;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The arena's marker blocks, remembered on disk: where World Boss spawn markers and entrance markers have been placed, per dimension.
 * A marker registers itself when placed ({@link ArenaMarkerBlock}) and unregisters when broken; an operator can also rebuild the list from
 * the world with {@code /cobbleraids worldboss arena scan}, for markers that arrived in a pasted schematic.
 */
public final class ArenaMarkers extends SavedData {
    private static final String FILE_ID = "cobbleraids_world_boss_arena";

    /** A marker's place: dimension id plus block position. */
    public record Spot(String dimension, BlockPos pos) {}

    public enum Kind { SPAWN, ENTRANCE, WARD_PYLON, RELAY_CACHE, RELAY_CHECKPOINT, OBSERVER }

    private final Set<Spot> spawns = new LinkedHashSet<>();
    private final Set<Spot> entrances = new LinkedHashSet<>();
    private final Set<Spot> wardPylons = new LinkedHashSet<>();
    private final Set<Spot> relayCaches = new LinkedHashSet<>(), relayCheckpoints = new LinkedHashSet<>();
    private final Set<Spot> observers = new LinkedHashSet<>();

    public static SavedData.Factory<ArenaMarkers> factory() {
        return new SavedData.Factory<>(ArenaMarkers::new, ArenaMarkers::load, DataFixTypes.LEVEL);
    }

    public static ArenaMarkers get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public void flush(MinecraftServer server) {
        try {
            SavedDataFlush.flush(server, this, FILE_ID);
        } catch (IOException e) {
            com.cobbleraids.RaidLog.warn("Could not save the arena markers: {}", e.toString());
        }
        com.cobbleraids.fault.RaidFaultBarrier.guard("worldboss:marker-protection", () -> com.cobbleraids.worldboss.WorldBossService.refreshArenaProtection(server));
    }

    private Set<Spot> set(Kind kind) {
        return switch (kind) { case SPAWN -> spawns; case ENTRANCE -> entrances; case WARD_PYLON -> wardPylons; case RELAY_CACHE -> relayCaches; case RELAY_CHECKPOINT -> relayCheckpoints; case OBSERVER -> observers; };
    }

    public boolean add(Kind kind, Spot spot) {
        boolean changed = set(kind).add(spot);
        if (changed) setDirty();
        return changed;
    }

    public boolean remove(Kind kind, Spot spot) {
        boolean changed = set(kind).remove(spot);
        if (changed) setDirty();
        return changed;
    }

    public void clear(Kind kind) {
        if (!set(kind).isEmpty()) setDirty();
        set(kind).clear();
    }

    public List<Spot> list(Kind kind) {
        return List.copyOf(set(kind));
    }

    // ---- disk ----

    private static ArenaMarkers load(CompoundTag tag, HolderLookup.Provider registries) {
        ArenaMarkers markers = new ArenaMarkers();
        read(tag.getList("spawns", Tag.TAG_COMPOUND), markers.spawns);
        read(tag.getList("entrances", Tag.TAG_COMPOUND), markers.entrances);
        read(tag.getList("ward_pylons", Tag.TAG_COMPOUND), markers.wardPylons);
        read(tag.getList("relay_caches", Tag.TAG_COMPOUND), markers.relayCaches);
        read(tag.getList("relay_checkpoints", Tag.TAG_COMPOUND), markers.relayCheckpoints);
        read(tag.getList("observers", Tag.TAG_COMPOUND), markers.observers);
        return markers;
    }

    private static void read(ListTag list, Set<Spot> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            into.add(new Spot(entry.getString("dimension"), new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"))));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("spawns", write(spawns));
        tag.put("entrances", write(entrances));
        tag.put("ward_pylons", write(wardPylons));
        tag.put("relay_caches", write(relayCaches));
        tag.put("relay_checkpoints", write(relayCheckpoints));
        tag.put("observers", write(observers));
        return tag;
    }

    private static ListTag write(Set<Spot> spots) {
        ListTag list = new ListTag();
        for (Spot s : new ArrayList<>(spots)) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", s.dimension());
            entry.putInt("x", s.pos().getX());
            entry.putInt("y", s.pos().getY());
            entry.putInt("z", s.pos().getZ());
            list.add(entry);
        }
        return list;
    }
}
