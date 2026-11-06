package net.snobblemon.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.snobblemon.proto.SnobbleProto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Small Pokemon pictures for the native pages. The files come from the cobblemon-cards mod that is installed on this server (the same ones the
 * old web pages showed); nothing is bundled here. The client asks for a key like "s:abra:0" (species, shiny 0/1), the server checks the key,
 * reads the PNG once, caches it and answers. Anything that is not a plain small PNG is never sent.
 */
public final class SpriteService {
    private SpriteService() { }

    private static final Pattern KEY = Pattern.compile("s:[a-z0-9_\\-]{1,40}:[01]|a:[a-z0-9_]{1,44}|p:[a-z0-9_+\\-]{1,46}|x:[a-z]{1,8}:[0-9a-f]{6}:[a-z]{1,8}|w:[a-z0-9_]{1,44}:[01]|g:[0-9a-f]{6}");
    private static final int MAX_BYTES = 65536;
    private static final String DIR = "assets/cobblemon-cards/textures/item/cards/pokemon/entity_icon/";
    private static final byte[] EMPTY = new byte[0];

    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private static volatile Map<String, String[]> manifest;
    private static final Map<UUID, long[]> RATE = new ConcurrentHashMap<>();      // {window start, count}

    public static void onReq(ServerPlayer p, SnobbleProto.SpriteReq r) {
        String key = r.key();
        if (!KEY.matcher(key).matches()) return;
        long now = System.currentTimeMillis();
        long[] w = RATE.computeIfAbsent(p.getUUID(), u -> new long[]{now, 0});
        if (now - w[0] > 10_000) { w[0] = now; w[1] = 0; }
        if (++w[1] > 120) return;
        byte[] png = CACHE.computeIfAbsent(key, SpriteService::load);
        if (ServerPlayNetworking.canSend(p, SnobbleProto.SpriteData.TYPE)) ServerPlayNetworking.send(p, new SnobbleProto.SpriteData(key, png));
    }

    public static void forget(UUID id) { RATE.remove(id); }

    // ---- the Auction House's own portrait atlas (data/snobblemon_server/portraits: atlas.json + atlas<n>.png, 128px cells)
    private static volatile JsonObject atlas;

    private static JsonObject atlas() {
        JsonObject a = atlas;
        if (a != null) return a;
        try (InputStream in = SpriteService.class.getResourceAsStream("/data/snobblemon_server/portraits/atlas.json")) {
            a = in == null ? new JsonObject() : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            a = new JsonObject();
        }
        atlas = a;
        return a;
    }

    private static final Pattern PKEY = Pattern.compile("[a-z0-9_+\\-]{1,46}");

    /** The picture key the Auction House page would show: its portrait when the atlas has one (sprite, sprite without +shiny, species), else the small sprite. */
    public static String portraitKey(String sprite, String speciesId, boolean shiny) {
        JsonObject s = atlas().has("sprites") ? atlas().getAsJsonObject("sprites") : null;
        if (s != null) for (String k : new String[]{sprite, sprite == null ? null : sprite.replace("+shiny", ""), speciesId}) {
            if (k != null && !k.isEmpty() && s.has(k) && PKEY.matcher(k).matches()) return "p:" + k;
        }
        String id = speciesId == null ? "" : speciesId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return id.isEmpty() ? "" : "s:" + id + ":" + (shiny ? 1 : 0);
    }

    /** One 128x128 portrait cut from its sheet; only that region of the sheet is decoded. */
    private static byte[] portrait(String k) {
        try {
            JsonObject a = atlas();
            var pos = a.getAsJsonObject("sprites").getAsJsonArray(k);
            if (pos == null) return EMPTY;
            int cell = a.get("cell").getAsInt(), sheet = pos.size() > 2 ? pos.get(2).getAsInt() : 0;
            String file = a.getAsJsonArray("sheets").get(sheet).getAsJsonObject().get("file").getAsString();
            if (!file.startsWith("atlas") || !file.endsWith(".png") || file.length() > 14 || file.contains("/")) return EMPTY;
            try (InputStream in = SpriteService.class.getResourceAsStream("/data/snobblemon_server/portraits/" + file);
                 var iis = javax.imageio.ImageIO.createImageInputStream(in)) {
                var readers = javax.imageio.ImageIO.getImageReaders(iis);
                if (!readers.hasNext()) return EMPTY;
                var reader = readers.next();
                reader.setInput(iis, true, true);
                var param = reader.getDefaultReadParam();
                param.setSourceRegion(new java.awt.Rectangle(pos.get(0).getAsInt() * cell, pos.get(1).getAsInt() * cell, cell, cell));
                java.awt.image.BufferedImage img = reader.read(0, param);
                reader.dispose();
                var out = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "png", out);
                byte[] data = out.toByteArray();
                return data.length <= MAX_BYTES ? data : EMPTY;
            }
        } catch (IOException | RuntimeException e) {
            return EMPTY;
        }
    }

    private static byte[] load(String key) {
        if (key.startsWith("p:")) return portrait(key.substring(2));
        if (key.startsWith("x:")) return CrateArt.fromKey(key);
        if (key.startsWith("w:")) return WardrobeArt.fromKey(key);
        if (key.startsWith("g:")) return GlowArt.fromKey(key);
        if (key.startsWith("a:")) {                                    // a picture shipped inside this mod (data/snobblemon_server/art/<name>.png)
            try (InputStream in = SpriteService.class.getResourceAsStream("/data/snobblemon_server/art/" + key.substring(2) + ".png")) {
                if (in == null) return EMPTY;
                byte[] data = in.readNBytes(MAX_BYTES + 1);
                boolean png = data.length > 8 && data.length <= MAX_BYTES && (data[0] & 0xFF) == 0x89 && data[1] == 'P';
                return png ? data : EMPTY;
            } catch (IOException e) {
                return EMPTY;
            }
        }
        try {
            String[] parts = key.split(":");
            String[] entry = manifest().get(parts[1]);
            if (entry == null) return EMPTY;
            String rel = parts[2].equals("1") && entry[1] != null ? entry[1] : entry[0];
            if (rel == null || rel.contains("..") || rel.startsWith("/")) return EMPTY;
            var mod = FabricLoader.getInstance().getModContainer("cobblemon-cards");
            if (mod.isEmpty()) return EMPTY;
            var path = mod.get().findPath(DIR + rel + ".png");
            if (path.isEmpty()) return EMPTY;
            byte[] data = Files.readAllBytes(path.get());
            boolean png = data.length > 8 && data.length <= MAX_BYTES && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
            return png ? data : EMPTY;
        } catch (IOException | RuntimeException e) {
            return EMPTY;
        }
    }

    /** species -> {normal, shiny} picture names, read from the manifest CobbleRaids ships (the same table its own shop uses). */
    private static Map<String, String[]> manifest() {
        Map<String, String[]> m = manifest;
        if (m != null) return m;
        m = new HashMap<>();
        try {
            var mod = FabricLoader.getInstance().getModContainer("cobbleraids");
            if (mod.isPresent()) {
                var path = mod.get().findPath("assets/cobbleraids/shop_icons.json");
                if (path.isPresent()) {
                    try (InputStream in = Files.newInputStream(path.get())) {
                        JsonObject icons = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("icons");
                        for (var en : icons.entrySet()) {
                            JsonObject o = en.getValue().getAsJsonObject();
                            m.put(en.getKey(), new String[]{o.has("icon") ? o.get("icon").getAsString() : null, o.has("shiny") ? o.get("shiny").getAsString() : null});
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            SnobblemonServer.LOGGER.warn("Could not read the Pokemon picture table: {}", e.toString());
        }
        manifest = m;
        return m;
    }
}
