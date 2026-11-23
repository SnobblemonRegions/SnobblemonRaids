package com.cobbleraids.spawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The natural-ground block tag decides where every wild raid may stand. Empty or missing, no raid would ever spawn; holding a
 * building block, raids would land on roofs and floors again.
 */
class RaidSpawnGroundTagTest {

    private static List<String> entries() throws Exception {
        var stream = RaidSpawnGroundTagTest.class.getResourceAsStream("/data/cobbleraids/tags/block/raid_spawn_ground.json");
        assertTrue(stream != null, "data/cobbleraids/tags/block/raid_spawn_ground.json ships in the jar");
        JsonObject tag = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean(), "adds to, never replaces, a datapack's own entries");
        List<String> out = new ArrayList<>();
        for (JsonElement value : tag.getAsJsonArray("values")) {
            out.add(value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString());
        }
        return out;
    }

    @Test
    @DisplayName("covers the ordinary overworld surface: grass and dirt, sand, stone, badlands, snow and ice")
    void coversTheSurface() throws Exception {
        List<String> tag = entries();
        for (String required : new String[] {"#minecraft:dirt", "#minecraft:sand", "#minecraft:base_stone_overworld",
                "#minecraft:terracotta", "#minecraft:snow", "minecraft:gravel", "minecraft:packed_ice"}) {
            assertTrue(tag.contains(required), required);
        }
    }

    @Test
    @DisplayName("holds no block players build with")
    void noBuildingBlocks() throws Exception {
        for (String entry : entries()) {
            for (String built : new String[] {"planks", "cobblestone", "brick", "glass", "wool", "concrete", "slab", "stairs",
                    "farmland", "dirt_path", "log", "wood"}) {
                assertFalse(entry.contains(built), entry + " is a building block");
            }
        }
    }
}
