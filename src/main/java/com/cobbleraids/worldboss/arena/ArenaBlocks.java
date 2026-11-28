package com.cobbleraids.worldboss.arena;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registers arena spawn, entrance, ward pylon and relay route markers and their items.
 * Give one with {@code /give @s cobbleraids:world_boss_spawn} and place it where the boss should stand.
 */
public final class ArenaBlocks {
    private ArenaBlocks() {}

    public static Block spawn;
    public static Block entrance;
    public static Block wardPylon;
    public static Block relayCache, relayCheckpoint;
    public static Block observer;

    /** Idempotent: a second call would throw on the duplicate id. */
    public static void register() {
        if (spawn != null) return;
        spawn = block("world_boss_spawn", ArenaMarkers.Kind.SPAWN, "World Boss Spawn Marker");
        entrance = block("world_boss_entrance", ArenaMarkers.Kind.ENTRANCE, "World Boss Arena Entrance Marker");
        wardPylon = block("world_boss_ward_pylon", ArenaMarkers.Kind.WARD_PYLON, "World Boss Ward Pylon Marker");
        relayCache = block("world_boss_relay_cache", ArenaMarkers.Kind.RELAY_CACHE, "World Boss Relay Cache Marker");
        relayCheckpoint = block("world_boss_relay_checkpoint", ArenaMarkers.Kind.RELAY_CHECKPOINT, "World Boss Relay Checkpoint Marker");
        observer = block("world_boss_observer", ArenaMarkers.Kind.OBSERVER, "World Boss Spectator Terrace Marker");
    }

    private static Block block(String path, ArenaMarkers.Kind kind, String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbleraids", path);
        Block block = Registry.register(BuiltInRegistries.BLOCK, id, new ArenaMarkerBlock(
                BlockBehaviour.Properties.of().strength(-1.0f, 3_600_000.0f).noCollission().noLootTable().noOcclusion(), kind));
        Registry.register(BuiltInRegistries.ITEM, id, new PolymerBlockItem(block,
                new Item.Properties().component(DataComponents.ITEM_NAME, Component.literal(name)), Items.STRUCTURE_VOID));
        return block;
    }
}
