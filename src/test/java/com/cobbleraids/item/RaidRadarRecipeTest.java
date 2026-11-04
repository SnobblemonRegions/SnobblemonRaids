package com.cobbleraids.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Raidio's crafting recipe: a shaped 3x3 whose every pattern letter has an ingredient, with no compass or netherite (the old compass design's cost). */
class RaidRadarRecipeTest {
    private static JsonObject read(String path) throws IOException {
        try (InputStream in = RaidRadarRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing from the jar");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void theRecipeIsAWellFormedShapedGridThatMakesTheRaidio() throws IOException {
        JsonObject recipe = read("/data/cobbleraids/recipe/raid_radar.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("cobbleraids:raid_radar", recipe.getAsJsonObject("result").get("id").getAsString());
        var pattern = recipe.getAsJsonArray("pattern");
        assertEquals(3, pattern.size());
        Set<Character> used = new HashSet<>();
        for (var row : pattern) {
            assertEquals(3, row.getAsString().length());
            for (char c : row.getAsString().toCharArray()) if (c != ' ') used.add(c);
        }
        Set<Character> keys = new HashSet<>();
        recipe.getAsJsonObject("key").keySet().forEach(k -> keys.add(k.charAt(0)));
        assertEquals(keys, used, "every pattern letter needs an ingredient and every ingredient must be used");
    }

    @Test
    void theRecipeIsARadioNotTheOldCompass() throws IOException {
        String text = read("/data/cobbleraids/recipe/raid_radar.json").getAsJsonObject("key").toString();
        assertTrue(text.contains("minecraft:note_block"), "the speaker is a note block");
        assertTrue(!text.contains("compass") && !text.contains("netherite"), "the compass design's ingredients should be gone: " + text);
    }

    @Test
    void theRecipeIsUnlockedByTheFirstNoteBlock() throws IOException {
        JsonObject advancement = read("/data/cobbleraids/advancement/recipes/tools/raid_radar.json");
        assertTrue(advancement.toString().contains("minecraft:note_block"));
        assertEquals("cobbleraids:raid_radar", advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
    }
}
