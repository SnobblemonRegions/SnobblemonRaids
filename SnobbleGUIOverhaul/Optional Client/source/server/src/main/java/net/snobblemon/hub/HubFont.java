package net.snobblemon.hub;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the menu artwork. A container menu's title is drawn at x=8, y=6, and the title may use any font, so the whole
 * window is built from full-size bitmap glyphs (one per layer) plus text in a pixel font, placed with "space" glyphs
 * that move the cursor. Layer names and glyphs come from snobblehub_layers.json, written by art/gen_art.py.
 */
public final class HubFont {
    public static final ResourceLocation GUI = ResourceLocation.fromNamespaceAndPath(SnobbleHub.MOD_ID, "gui");
    private static final int GLYPH_GAP = 1;   // a bitmap glyph advances by its width + 1
    private static Map<String, String> layers = new HashMap<>();
    private static Map<String, Integer> widths = new HashMap<>();

    private HubFont() {}

    public static void load() {
        layers = read("/snobblehub_layers.json", new TypeToken<Map<String, String>>() {});
        widths = read("/snobblehub_font_widths.json", new TypeToken<Map<String, Integer>>() {});
    }

    private static <T> Map<String, T> read(String path, TypeToken<Map<String, T>> type) {
        try (Reader r = new InputStreamReader(HubFont.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, type.getType());
        } catch (Exception e) {
            SnobbleHub.LOGGER.error("Could not read {}: {}", path, e.toString());
            return new HashMap<>();
        }
    }

    /** Advance of one character in the pixel font: width + 1, a space is 3. */
    public static int advance(char c) {
        if (c == ' ') return 3;
        Integer w = widths.get(String.valueOf(printable(c)));
        return (w == null ? 5 : w) + 1;
    }

    public static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += advance(s.charAt(i));
        return Math.max(0, w - 1);
    }

    /** Keeps only characters the pixel font has (printable ASCII), so unknown symbols never show as boxes. */
    public static String clean(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 32 && c < 127) sb.append(c);
        }
        return sb.toString();
    }

    private static char printable(char c) {
        return c >= 32 && c < 127 ? c : '?';
    }

    /** Cuts a string to a pixel width. */
    public static String fit(String s, int maxWidth) {
        s = clean(s);
        while (s.length() > 1 && width(s) > maxWidth) s = s.substring(0, s.length() - 1);
        return s;
    }

    static String shift(int px) {
        StringBuilder sb = new StringBuilder();
        int abs = Math.abs(px);
        char base = px < 0 ? '' : '豈';
        for (int bit = 128; bit >= 1; bit >>= 1) {
            while (abs >= bit) {
                sb.append((char) (base + bit));
                abs -= bit;
            }
        }
        return sb.toString();
    }

    public static Title title() {
        return new Title();
    }

    /** Collects layers and text; {@link #build()} returns the menu title. */
    public static final class Title {
        private final MutableComponent root = Component.empty();
        private final StringBuilder pending = new StringBuilder();
        private int cursor = 8;

        private void flush() {
            if (pending.length() > 0) {
                root.append(Component.literal(pending.toString()).withStyle(Style.EMPTY.withFont(GUI).withColor(0xFFFFFF)));
                pending.setLength(0);
            }
        }

        /** A full-window layer by name (see art/gen_art.py). Unknown names are ignored. */
        public Title layer(String name) {
            String g = layers.get(name);
            if (g == null) return this;
            pending.append(shift(-cursor)).append(g);
            cursor = 176 + GLYPH_GAP;
            return this;
        }

        /** Text with its top-left corner at (x, y), in Minecraft pixels, tinted with an RGB colour. */
        public Title text(int x, int y, String s, int rgb) {
            s = clean(s);
            if (s.isEmpty()) return this;
            y = Math.max(6, Math.min(127, y));
            pending.append(shift(x - cursor));
            flush();
            ResourceLocation font = ResourceLocation.fromNamespaceAndPath(SnobbleHub.MOD_ID, "t" + y);
            root.append(Component.literal(s).withStyle(Style.EMPTY.withFont(font).withColor(rgb)));
            cursor = x + width(s) + 1;
            return this;
        }

        public Title textRight(int rightX, int y, String s, int rgb) {
            return text(rightX - width(clean(s)), y, s, rgb);
        }

        public Title textCenter(int centerX, int y, String s, int rgb) {
            return text(centerX - width(clean(s)) / 2, y, s, rgb);
        }

        public Component build() {
            pending.append(shift(8 - cursor));
            flush();
            cursor = 8;
            return root;
        }
    }
}
