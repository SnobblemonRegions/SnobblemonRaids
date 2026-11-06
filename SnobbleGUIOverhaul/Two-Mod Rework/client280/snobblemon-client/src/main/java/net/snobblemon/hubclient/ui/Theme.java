package net.snobblemon.hubclient.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared names and colours for every Snobble screen. Art is not in this jar: the names resolve through ThemeRegistry to the server's resource pack, with neutral placeholders as the fallback. */
public final class Theme {
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFD4DEE6;
    public static final int GOLD = 0xFFE3B455;
    public static final int AQUA = 0xFF5FD8EA;
    public static final int GREEN = 0xFF5FD38A;
    public static final int RED = 0xFFE0646E;
    public static final int[] RARITY = {0xFFB5C0CE, 0xFF5FD38A, 0xFF5AA7F0, 0xFFB98BFF, 0xFFF2A94E, 0xFFFF7FB0};

    /** Region of the screen being drawn; set by SnobbleScreen so buttons and the stage pick the matching art. */
    public static String accent = "dusk";

    private Theme() {}

    /** The owo draw context leaves blending off, so translucent art would come out solid; every sprite / texture draw goes through these. */
    public static void blit(GuiGraphics ctx, ResourceLocation sprite, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.blitSprite(sprite, x, y, w, h);
    }

    public static void tex(GuiGraphics ctx, ResourceLocation tex, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.blit(tex, x, y, w, h, u, v, uw, vh, tw, th);
    }

    public static String region() {
        return accent == null || accent.isEmpty() ? "dusk" : accent;
    }

    /** Screen colour of a window: bright (wardrobe) or dark (card), tinted by region. */
    public static int interior(String accent, boolean bright) {
        return switch (accent == null ? "" : accent) {
            case "root" -> bright ? 0xFF3DBB72 : 0xFF26382E;
            case "tide" -> bright ? 0xFF2DB8B4 : 0xFF243B3D;
            default -> bright ? 0xFF8466E0 : 0xFF332D47;
        };
    }

    /** The screen glow from the reference: a bright rim just inside the edge that fades inward, and a soft halo spilling onto the bezel. */
    public static void glow(GuiGraphics ctx, int x0, int y0, int x1, int y1, int rgb) {
        for (int i = 0; i < 16; i++) {                                    // inner fade
            float t = 1f - i / 16f;
            int a = (int) (170 * t * t);
            int c = (a << 24) | 0xFFFFFF & (rgb | 0x808080);
            ctx.fill(x0 + i, y0 + i, x1 - i, y0 + i + 1, c);
            ctx.fill(x0 + i, y1 - i - 1, x1 - i, y1 - i, c);
            ctx.fill(x0 + i, y0 + i + 1, x0 + i + 1, y1 - i - 1, c);
            ctx.fill(x1 - i - 1, y0 + i + 1, x1 - i, y1 - i - 1, c);
        }
        ctx.fill(x0, y0, x1, y0 + 1, 0xE6FFFFFF);                         // crisp white rim
        ctx.fill(x0, y1 - 1, x1, y1, 0xE6FFFFFF);
        ctx.fill(x0, y0, x0 + 1, y1, 0xE6FFFFFF);
        ctx.fill(x1 - 1, y0, x1, y1, 0xE6FFFFFF);
        for (int i = 1; i <= 6; i++) {                                    // outer halo
            int a = (int) (80 * (1f - i / 7f) * (1f - i / 7f));
            int c = (a << 24) | (rgb & 0xFFFFFF);
            ctx.fill(x0 - i, y0 - i, x1 + i, y0 - i + 1, c);
            ctx.fill(x0 - i, y1 + i - 1, x1 + i, y1 + i, c);
            ctx.fill(x0 - i, y0 - i + 1, x0 - i + 1, y1 + i - 1, c);
            ctx.fill(x1 + i - 1, y0 - i + 1, x1 + i, y1 + i - 1, c);
        }
    }

    /** Faint CRT scan lines over the screen: one dark line every second GUI pixel. Flat fills, about one per two pixels of screen height. */
    public static void scanlines(GuiGraphics ctx, int x0, int y0, int x1, int y1) {
        final int c = 0x24000000;
        for (int y = y0 + 1; y < y1; y += 2) ctx.fill(x0, y, x1, y + 1, c);
    }

    /** A texture file by path (e.g. "textures/gui/icons/gem.png"): the server's theme art when it has the file, the neutral placeholder otherwise. */
    public static ResourceLocation id(String path) {
        return net.snobblemon.client.theme.ThemeRegistry.file(path);
    }

    /** A GUI-atlas sprite by name: theme art when the server's pack has it, a neutral placeholder otherwise. */
    public static ResourceLocation sprite(String name) {
        return net.snobblemon.client.theme.ThemeRegistry.sprite(name);
    }

    public static ResourceLocation icon(String name) {
        return id("textures/gui/icons/" + name + ".png");
    }

    public static ResourceLocation frame(String accent) {
        return sprite("frame_" + (accent == null || accent.isEmpty() ? "dusk" : accent));
    }

    public static int regionColor(String accent) {
        return switch (accent == null ? "" : accent) {
            case "root" -> 0xFF7BE0A0;
            case "tide" -> 0xFF86F0DF;
            default -> 0xFFB9A2F5;
        };
    }
}
