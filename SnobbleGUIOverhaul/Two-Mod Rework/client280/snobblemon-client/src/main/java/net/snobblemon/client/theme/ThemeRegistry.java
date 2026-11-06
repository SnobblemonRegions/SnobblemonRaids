package net.snobblemon.client.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the server-delivered art. The server's resource pack is applied by Minecraft like any pack; one namespace in it carries a
 * marker file {@code snobblemon_theme.json} ({"format":1,"namespace":"...","version":"..."}). This class finds that namespace after every
 * resource reload and answers "which ResourceLocation do I draw for sprite X?": the theme's file when the pack has it, otherwise the
 * neutral placeholder shipped in this jar ({@value #DEFAULT_NS}). Nothing here knows any server's name or brand.
 */
public final class ThemeRegistry {
    public static final String DEFAULT_NS = "snobblemon_client";
    public static final String MARKER = "snobblemon_theme.json";

    private static volatile String ns = "";
    private static volatile String version = "";
    private static final Map<String, ResourceLocation> FILES = new ConcurrentHashMap<>();
    private static final Map<String, int[]> SIZES = new ConcurrentHashMap<>();
    /** name -> "theme" or "placeholder", for diagnostics and tests. */
    private static final Map<String, String> SOURCE = new ConcurrentHashMap<>();
    private static volatile int generation;

    private ThemeRegistry() {}

    /** Called by the reload listener on the render thread after every resource reload. */
    public static void reload(ResourceManager rm) {
        String foundNs = "", foundVer = "";
        for (String n : new TreeSet<>(rm.getNamespaces())) {
            var res = rm.getResource(ResourceLocation.fromNamespaceAndPath(n, MARKER));
            if (res.isEmpty()) continue;
            try (InputStream in = res.get().open(); var r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                if (o.has("format") && o.get("format").getAsInt() == 1 && n.equals(o.get("namespace").getAsString())) {
                    foundNs = n;
                    foundVer = o.get("version").getAsString();
                    break;
                }
            } catch (Exception e) {
                net.snobblemon.client.SnobblemonClient.LOGGER.warn("Ignoring bad theme marker in namespace {}: {}", n, e.toString());
            }
        }
        ns = foundNs;
        version = foundVer;
        FILES.clear();
        SIZES.clear();
        SOURCE.clear();
        generation++;
        net.snobblemon.client.SnobblemonClient.LOGGER.info("Theme: namespace='{}' version='{}'", foundNs, foundVer);
    }

    public static String namespace() { return ns; }
    public static String version() { return version; }
    public static boolean loaded() { return !ns.isEmpty(); }
    public static int generation() { return generation; }
    public static Map<String, String> sources() { return SOURCE; }

    /** A texture file ("textures/gui/icons/gem.png"): the theme's when present. */
    public static ResourceLocation file(String path) {
        ResourceLocation cached = FILES.get(path);
        if (cached != null) return cached;
        ResourceLocation pick = ResourceLocation.fromNamespaceAndPath(DEFAULT_NS, path);
        boolean theme = false;
        String n = ns;
        if (!n.isEmpty()) {
            ResourceLocation t = ResourceLocation.fromNamespaceAndPath(n, path);
            if (Minecraft.getInstance().getResourceManager().getResource(t).isPresent()) { pick = t; theme = true; }
        }
        SOURCE.put(path, theme ? "theme" : "placeholder");
        FILES.put(path, pick);
        return pick;
    }

    /** A GUI-atlas sprite name: lives at textures/gui/sprites/NAME.png, drawn with blitSprite(id). */
    public static ResourceLocation sprite(String name) {
        String key = "sprite:" + name;
        ResourceLocation cached = FILES.get(key);
        if (cached != null) return cached;
        ResourceLocation f = file("textures/gui/sprites/" + name + ".png");
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(f.getNamespace(), name);
        FILES.put(key, id);
        SOURCE.put("sprite:" + name, f.getNamespace().equals(DEFAULT_NS) ? "placeholder" : "theme");
        return id;
    }

    /** Pixel size of a texture file (needed to draw a whole image scaled); {16,16} if unreadable. */
    public static int[] size(ResourceLocation tex) {
        return SIZES.computeIfAbsent(tex.toString(), k -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(tex).open(); NativeImage img = NativeImage.read(in)) {
                return new int[]{img.getWidth(), img.getHeight()};
            } catch (Exception e) {
                return new int[]{16, 16};
            }
        });
    }
}
