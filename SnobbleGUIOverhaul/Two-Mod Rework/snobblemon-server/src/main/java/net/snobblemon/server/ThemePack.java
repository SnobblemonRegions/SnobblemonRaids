package net.snobblemon.server;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * The server's art, delivered through the Polymer resource pack that is already hosted: this mod adds one more namespace to it
 * (via Polymer's own addModAssets), merges an optional override folder so art can be swapped without rebuilding any jar, and writes
 * a marker file with a content hash so the client can tell which art version it is showing. Polymer still builds, hosts, hashes and
 * requires the pack; none of its other namespaces are touched.
 */
public final class ThemePack {
    public static final String NS = "snobblemon_regions";
    public static final String MARKER_PATH = "assets/" + NS + "/snobblemon_theme.json";

    private static volatile String artVersion = "";

    private ThemePack() {}

    public static String artVersion() { return artVersion; }

    public static Path overrideDir() { return FabricLoader.getInstance().getConfigDir().resolve("snobblemon").resolve("theme"); }

    public static void init() {
        boolean ok = PolymerResourcePackUtils.addModAssets(SnobblemonServer.MOD_ID);
        if (!ok) SnobblemonServer.LOGGER.warn("Polymer did not accept this mod's assets (addModAssets returned false)");
        artVersion = compute();
        SnobblemonServer.LOGGER.info("Theme art version {} (namespace {}, override folder {})", artVersion, NS, overrideDir());
        // runs every time Polymer builds the pack (server start, /polymer generate), AFTER mods' own assets were copied so the override folder wins
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder -> {
            Path dir = overrideDir();
            if (Files.isDirectory(dir)) {
                boolean merged = builder.copyResourcePackFromPath(dir);
                SnobblemonServer.LOGGER.info("Theme override folder merged into the pack: {}", merged);
            }
            artVersion = compute();
            builder.addData(MARKER_PATH, marker(artVersion));
        });
    }

    public static byte[] marker(String version) {
        return ("{\"format\":1,\"namespace\":\"" + NS + "\",\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    /** modVersion + first 10 hex of a SHA-256 over every theme file (jar and override folder, sorted by path). Same art = same version. */
    static String compute() {
        try {
            List<String[]> entries = new ArrayList<>();   // {path, source marker}
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            ModContainer me = FabricLoader.getInstance().getModContainer(SnobblemonServer.MOD_ID).orElseThrow();
            List<Path> roots = new ArrayList<>(me.getRootPaths());
            List<String> keys = new ArrayList<>();
            java.util.Map<String, Path> files = new java.util.TreeMap<>();
            for (Path r : roots) collect(r.resolve("assets").resolve(NS), "assets/" + NS, files);
            collect(overrideDir().resolve("assets").resolve(NS), "assets/" + NS, files);   // override wins on the same path
            for (var e : files.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                md.update(Files.readAllBytes(e.getValue()));
            }
            return me.getMetadata().getVersion().getFriendlyString() + "+" + HexFormat.of().formatHex(md.digest()).substring(0, 10);
        } catch (Exception e) {
            SnobblemonServer.LOGGER.warn("Could not compute the art version: {}", e.toString());
            return "unknown";
        }
    }

    private static void collect(Path dir, String prefix, java.util.Map<String, Path> out) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile).forEach(p -> out.put(prefix + "/" + dir.relativize(p).toString().replace('\\', '/'), p));
        }
    }
}
