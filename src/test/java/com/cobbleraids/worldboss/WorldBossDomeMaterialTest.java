package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WorldBossDomeMaterialTest {
    @Test
    void allPanelsAndCrossingFlashHaveIndependentAnimatedAssets() throws Exception {
        for (int variant = 0; variant < 4; variant++) {
            String name = "worldboss_distortion_dome_" + variant;
            try (var model = getClass().getResourceAsStream("/assets/cobbleraids/models/item/" + name + ".json")) {
                assertNotNull(model, "World Boss must own its dome instead of reusing Halloween panels");
                var json = JsonParser.parseReader(new InputStreamReader(model, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("cobbleraids:item/" + name, json.getAsJsonObject("textures").get("panel").getAsString());
            }
            try (var input = getClass().getResourceAsStream("/assets/cobbleraids/textures/item/" + name + ".png")) {
                assertNotNull(input);
                var image = ImageIO.read(input);
                assertEquals(64, image.getWidth());
                assertEquals(64 * 24, image.getHeight());
                assertEquals(0, image.getRGB(0, 0) >>> 24, "outside the triangular panel stays transparent");
                if (variant < 3) {
                    long alphaSum = 0;
                    int visiblePixels = 0, strongPixels = 0;
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            int alpha = image.getRGB(x, y) >>> 24;
                            if (alpha == 0) continue;
                            alphaSum += alpha;
                            visiblePixels++;
                            if (alpha > 160) strongPixels++;
                        }
                    }
                    assertTrue(visiblePixels > 0);
                    assertTrue(alphaSum / (double) visiblePixels < 95, "the membrane stays transparent on average");
                    assertTrue(strongPixels / (double) visiblePixels < 0.15, "strong rift/seal highlights stay sparse");
                }
            }
            try (var input = getClass().getResourceAsStream("/assets/cobbleraids/textures/item/" + name + ".png.mcmeta")) {
                assertNotNull(input, "all variants, including crossing flash, must animate");
            }
        }
    }
}
