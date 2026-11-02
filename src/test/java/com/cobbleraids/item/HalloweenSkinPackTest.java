package com.cobbleraids.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class HalloweenSkinPackTest {
    @Test
    void everyListedFileShipsInTheJar() {
        for (String path : HalloweenSkinPack.FILES) {
            assertNotNull(getClass().getClassLoader().getResource(HalloweenSkinPack.JAR_ROOT + path), "missing from resources: " + path);
        }
    }

    @Test
    void noFileIsListedTwice() {
        assertEquals(HalloweenSkinPack.FILES.size(), new HashSet<>(HalloweenSkinPack.FILES).size());
    }

    @Test
    void everySevenSkinsHaveAResolver() {
        long resolvers = HalloweenSkinPack.FILES.stream().filter(p -> p.contains("/resolvers/")).count();
        assertEquals(7, resolvers);
    }

    /**
     * Cobblemon's model loader reads a cube's "uv" only as the box form [u, v]. A per-face object makes it throw while the client loads the pack,
     * which drops every server resource pack and kicks the player ("Server requires a custom resource pack").
     */
    @Test
    void everyModelCubeUsesBoxUv() throws Exception {
        for (String path : HalloweenSkinPack.FILES) {
            if (!path.endsWith(".geo.json")) continue;
            try (var in = getClass().getClassLoader().getResourceAsStream(HalloweenSkinPack.JAR_ROOT + path)) {
                var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var geometry : root.getAsJsonArray("minecraft:geometry")) {
                    for (var bone : geometry.getAsJsonObject().getAsJsonArray("bones")) {
                        var cubes = bone.getAsJsonObject().getAsJsonArray("cubes");
                        if (cubes == null) continue;
                        for (var cube : cubes) {
                            var uv = cube.getAsJsonObject().get("uv");
                            org.junit.jupiter.api.Assertions.assertTrue(uv != null && uv.isJsonArray(),
                                    path + ": bone " + bone.getAsJsonObject().get("name") + " has a per-face uv");
                        }
                    }
                }
            }
        }
    }
}
