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
import java.util.stream.Collectors;
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
    void everyListedFileShips() throws IOException {
        List<String> paths = index();
        assertTrue(paths.size() > 50, "a resolver and texture for most raid species");
        for (String path : paths) assertNotNull(resource(path), path);
    }

    @Test
    void everyResolverSwapsOnlyTheTextureForTheRenownedAspect() throws IOException {
        List<String> paths = index();
        Set<String> textures = paths.stream().filter(p -> p.endsWith(".png")).collect(Collectors.toSet());
        int resolvers = 0;
        for (String path : paths) {
            if (!path.endsWith(".json")) continue;
            resolvers++;
            JsonObject resolver = JsonParser.parseString(new String(resource(path), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(resolver.get("order").getAsInt() > 0, path + " runs after Cobblemon's own variations");
            for (JsonElement element : resolver.getAsJsonArray("variations")) {
                JsonObject variation = element.getAsJsonObject();
                assertEquals(Set.of("aspects", "texture"), variation.keySet(), path + " leaves model, poser and layers alone");
                boolean renowned = false;
                for (JsonElement aspect : variation.getAsJsonArray("aspects")) renowned |= RenownLook.ASPECT.equals(aspect.getAsString());
                assertTrue(renowned, path + " only applies to a renowned boss");
                String texture = variation.get("texture").getAsString();
                assertTrue(texture.startsWith("cobbleraids:"), texture);
                assertTrue(textures.contains("assets/cobbleraids/" + texture.substring("cobbleraids:".length())), path + " -> " + texture);
            }
        }
        assertTrue(resolvers > 50);
    }
}
