package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WorldBossSkinTest {
    @Test
    void ownedSkinUsesIndependentSpritesAndValidAnimationFrames() throws Exception {
        var manifest = getClass().getResourceAsStream("/bossbarskins.json");
        assertNotNull(manifest, "World Boss must declare its own skin instead of sharing Halloween sprites");
        try (var reader = new InputStreamReader(manifest, StandardCharsets.UTF_8)) {
            var skins = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("skins");
            assertEquals(1, skins.size());
            var skin = skins.get(0).getAsJsonObject();
            assertEquals("BLUE/PROGRESS", skin.get("match").getAsString());
            for (String key : new String[]{"background", "progress"}) {
                String path = skin.get(key).getAsString();
                assertTrue(path.startsWith("assets/cobbleraids/"), "never replace a global vanilla colour");
                try (var input = getClass().getResourceAsStream("/" + path)) {
                    assertNotNull(input, path);
                    var image = ImageIO.read(input);
                    assertNotNull(image);
                    assertEquals(182, image.getWidth());
                    assertEquals(0, image.getHeight() % 5);
                    if (key.equals("background")) assertEquals(5, image.getHeight());
                    else assertTrue(image.getHeight() > 5, "authored animated progress");
                }
            }
            try (var input = getClass().getResourceAsStream("/" + skin.get("meta").getAsString());
                 var meta = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                var animation = JsonParser.parseReader(meta).getAsJsonObject().getAsJsonObject("animation");
                assertEquals(182, animation.get("width").getAsInt());
                assertEquals(5, animation.get("height").getAsInt());
            }
        }
    }
}
