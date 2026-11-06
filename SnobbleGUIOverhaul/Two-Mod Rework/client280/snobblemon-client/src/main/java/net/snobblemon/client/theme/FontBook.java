package net.snobblemon.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The page fonts the SERVER ships in its resource pack (the client bundles none). The pack holds, per font, a list of Minecraft TrueType font
 * definitions at several pixel sizes plus one metrics file:
 * <pre>assets/&lt;theme ns&gt;/snobblemon_fonts.json = {"fonts": {"nunito": {"asc": 1.011, "desc": 0.353, "sizes": [8,10,12,...]}}}</pre>
 * and {@code assets/<ns>/font/<name>_<size>.json}. A page asks for a font by name and CSS pixel size; we hand back the definition whose
 * pixel size is closest to what will actually be on screen, so glyphs are rasterised near their real size (crisp, like the browser).
 */
public final class FontBook {
    public static final String FILE = "snobblemon_fonts.json";

    public record Face(String name, double asc, double desc, double scale, int[] sizes) {}

    /** A chosen font for one piece of text: the Minecraft font id (null = the vanilla font), scale from font units to page pixels, ascent/descent per em. */
    public record Pick(ResourceLocation id, double k, double asc, double desc, double px) {}

    private static final Map<String, Face> FACES = new ConcurrentHashMap<>();
    /** Emoji the server pack's fonts can draw (a bitmap atlas inside every page font); other emoji are still removed from text. */
    public static volatile java.util.Set<Integer> EMOJI = java.util.Set.of();
    private static volatile String ns = "";

    private FontBook() {}

    public static void reload(ResourceManager rm) {
        FACES.clear();
        EMOJI = java.util.Set.of();
        ns = ThemeRegistry.namespace();
        if (ns.isEmpty()) return;
        var res = rm.getResource(ResourceLocation.fromNamespaceAndPath(ns, FILE));
        if (res.isEmpty()) return;
        try (InputStream in = res.get().open(); var r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject top = JsonParser.parseReader(r).getAsJsonObject();
            java.util.Set<Integer> em = new java.util.HashSet<>();
            if (top.has("emoji") && top.get("emoji").isJsonArray()) for (JsonElement e : top.getAsJsonArray("emoji")) em.add(e.getAsInt());
            EMOJI = java.util.Set.copyOf(em);
            JsonObject o = top.getAsJsonObject("fonts");
            for (var en : o.entrySet()) {
                JsonObject f = en.getValue().getAsJsonObject();
                var arr = f.getAsJsonArray("sizes");
                int[] sizes = new int[arr.size()];
                int i = 0;
                for (JsonElement e : arr) sizes[i++] = e.getAsInt();
                java.util.Arrays.sort(sizes);
                FACES.put(en.getKey(), new Face(en.getKey(), f.get("asc").getAsDouble(), f.get("desc").getAsDouble(), f.has("scale") ? f.get("scale").getAsDouble() : 1.0, sizes));
            }
            net.snobblemon.client.SnobblemonClient.LOGGER.info("Page fonts from the server pack: {}", FACES.keySet());
        } catch (Exception e) {
            net.snobblemon.client.SnobblemonClient.LOGGER.warn("Ignoring bad {}: {}", FILE, e.toString());
        }
    }

    public static boolean has(String name) { return FACES.containsKey(name); }

    /**
     * @param name   font name from the layout ("" = vanilla)
     * @param px     size in page (CSS) pixels
     * @param screen real screen pixels per page pixel (so the closest raster size can be chosen)
     */
    public static Pick pick(String name, double px, double screen) {
        Face f = name == null || name.isEmpty() ? null : FACES.get(name);
        if (f == null || f.sizes.length == 0) {
            // vanilla: an 8-unit em with the baseline at 7
            return new Pick(null, px / 8.0, 0.875, 0.25, px);
        }
        // a substitute font is drawn scaled so its letters are as wide as the original's; line metrics stay the original's
        double glyph = px * f.scale, want = glyph * screen;
        int best = f.sizes[0];
        for (int s : f.sizes) if (Math.abs(s - want) < Math.abs(best - want)) best = s;
        return new Pick(ResourceLocation.fromNamespaceAndPath(ns, name + "_" + best), glyph / best, f.asc, f.desc, px);
    }
}
