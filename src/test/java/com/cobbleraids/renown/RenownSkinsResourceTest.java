package com.cobbleraids.renown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The generated renowned skins (tools/renown_skins.py) hang together: every listed file ships, every resolver points at a shipped texture. */
class RenownSkinsResourceTest {
    private static final String INDEX = "assets/cobbleraids/renowned_skins.txt";

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = RenownSkinsResourceTest.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static List<String> index() throws IOException {
        byte[] bytes = resource(INDEX);
        assertNotNull(bytes, "the skin list ships in the jar");
        return Arrays.stream(new String(bytes, StandardCharsets.UTF_8).split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Test
    void everyListedFileShipsAndNoArtIsCopied() throws IOException {
        List<String> paths = index();
        assertTrue(paths.size() >= 132, "a resolver for every raid species");
        for (String path : paths) {
            assertNotNull(resource(path), path);
            // The look is a tint over the player's own textures: shipping a texture would copy someone's art (CCC forbids it).
            assertTrue(path.endsWith(".json"), path);
        }
    }

    @Test
    void everyResolverOnlyAddsTheTintedLayerForTheRenownedAspect() throws IOException {
        for (String path : index()) {
            JsonObject resolver = JsonParser.parseString(new String(resource(path), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(resolver.get("order").getAsInt() < 0, path + " goes first, so the species' own layers draw over ours");
            for (JsonElement element : resolver.getAsJsonArray("variations")) {
                JsonObject variation = element.getAsJsonObject();
                assertEquals(Set.of("aspects", "layers"), variation.keySet(), path + " leaves model, poser and texture alone");
                boolean renowned = false;
                for (JsonElement aspect : variation.getAsJsonArray("aspects")) renowned |= RenownLook.ASPECT.equals(aspect.getAsString());
                assertTrue(renowned, path + " only applies to a renowned boss");
                assertEquals(1, variation.getAsJsonArray("layers").size(), path);
                JsonObject layer = variation.getAsJsonArray("layers").get(0).getAsJsonObject();
                assertEquals("cobbleraids_renowned", layer.get("name").getAsString(), path);
                assertEquals(4, layer.getAsJsonArray("tint").size(), path + " tint is [r, g, b, a]");
                assertTrue(layer.has("texture"), path);
            }
        }
    }
}
