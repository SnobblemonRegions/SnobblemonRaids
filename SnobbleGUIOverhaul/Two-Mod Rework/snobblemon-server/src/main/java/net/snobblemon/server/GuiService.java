package net.snobblemon.server;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.snobblemon.proto.GuiSpec;
import net.snobblemon.proto.SnobbleProto;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Everything the server decides: which clients are compatible, which GUIs exist, which one a player has open, and what a pressed
 * button does. The client sends only (gui id, widget id); this class looks the widget up in ITS OWN copy of the definition, checks
 * the player's state, the widget's permission, enabled flag and a rate limit, and only then runs the named action.
 */
public final class GuiService {
    public enum OpenResult { OK, NO_CLIENT, REFUSED, NO_THEME, UNKNOWN_GUI, INVALID }

    /** What an action handler returns. */
    public record Result(String message, boolean ok, JsonObject newData, boolean close) {
        public static Result msg(String m, boolean ok) { return new Result(m, ok, null, false); }
    }

    @FunctionalInterface
    public interface Action { Result run(ServerPlayer player, GuiSpec.Widget widget, JsonObject data); }

    static final class PlayerState {
        boolean hello, accepted;
        String clientVersion = "";
        String loadedArt = "", themeNs = "";
        GuiSpec open;
        JsonObject data = new JsonObject();
        long openAt;
        long windowStart;
        int windowCount;
        int dropped;
    }

    private static final Map<UUID, PlayerState> STATE = new ConcurrentHashMap<>();
    private static final Map<String, String> DEFS = new HashMap<>();                 // id -> validated JSON text
    private static final Map<String, Function<ServerPlayer, JsonObject>> DATA = new ConcurrentHashMap<>();
    private static final Map<String, Action> ACTIONS = new ConcurrentHashMap<>();
    private static final int RATE_PER_SECOND = 8;
    private static final long MAX_OPEN_MS = 15 * 60_000L;

    private GuiService() {}

    // ------------------------------------------------------------------ setup

    public static void init() {
        reload();
        // built-in demo (a real feature registers its own actions and data the same way)
        DATA.put("demo", p -> {
            PlayerState s = STATE.get(p.getUUID());
            JsonObject o = new JsonObject();
            o.addProperty("player", p.getGameProfile().getName());
            o.addProperty("art", ThemePack.artVersion());
            o.addProperty("count", counters.getOrDefault(p.getUUID(), 0));
            return o;
        });
        ACTIONS.put("demo.count", (p, w, d) -> {
            int n = counters.merge(p.getUUID(), 1, Integer::sum);
            JsonObject o = d.deepCopy();
            o.addProperty("count", n);
            return new Result("Counted on the server: " + n, true, o, false);
        });
        ACTIONS.put("demo.admin", (p, w, d) -> Result.msg("The admin action ran.", true));
        ACTIONS.put("demo.close", (p, w, d) -> new Result("", true, null, true));
    }

    private static final Map<UUID, Integer> counters = new ConcurrentHashMap<>();

    public static void registerData(String guiId, Function<ServerPlayer, JsonObject> provider) { DATA.put(guiId, provider); }
    public static void registerAction(String name, Action a) { ACTIONS.put(name, a); }

    /** Loads definitions from this jar, then from config/snobblemon/gui (which override by id). Invalid ones are logged and skipped. */
    public static synchronized String reload() {
        Map<String, String> fresh = new HashMap<>();
        int bad = 0;
        try {
            var me = FabricLoader.getInstance().getModContainer(SnobblemonServer.MOD_ID).orElseThrow();
            for (Path root : me.getRootPaths()) bad += load(root.resolve("data").resolve(SnobblemonServer.MOD_ID).resolve("gui"), fresh);
        } catch (RuntimeException e) {
            SnobblemonServer.LOGGER.warn("Could not read built-in GUI definitions: {}", e.toString());
        }
        bad += load(FabricLoader.getInstance().getConfigDir().resolve("snobblemon").resolve("gui"), fresh);
        DEFS.clear();
        DEFS.putAll(fresh);
        String s = "Loaded " + fresh.size() + " GUI definition(s)" + (bad > 0 ? ", skipped " + bad + " invalid" : "") + ": " + fresh.keySet();
        SnobblemonServer.LOGGER.info(s);
        return s;
    }

    private static int load(Path dir, Map<String, String> out) {
        if (!Files.isDirectory(dir)) return 0;
        int bad = 0;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path f : (Iterable<Path>) s.filter(p -> p.toString().endsWith(".json"))::iterator) {
                try {
                    String text = Files.readString(f, StandardCharsets.UTF_8);
                    GuiSpec spec = GuiSpec.parse(text);
                    out.put(spec.id, text);
                } catch (GuiSpec.Invalid | IOException e) {
                    bad++;
                    SnobblemonServer.LOGGER.warn("Skipping GUI definition {}: {}", f.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            SnobblemonServer.LOGGER.warn("Could not list {}: {}", dir, e.toString());
        }
        return bad;
    }

    public static String status() {
        StringBuilder b = new StringBuilder("Snobblemon Server: art " + ThemePack.artVersion() + ", protocol " + SnobbleProto.PROTOCOL + ", GUIs " + DEFS.keySet());
        STATE.forEach((id, s) -> b.append("\n ").append(id.toString(), 0, 8).append(" hello=").append(s.hello).append(" accepted=").append(s.accepted)
                .append(" client=").append(s.clientVersion).append(" art=").append(s.loadedArt).append(" open=").append(s.open == null ? "-" : s.open.id).append(" dropped=").append(s.dropped));
        return b.toString();
    }

    public static void forget(UUID id) { STATE.remove(id); counters.remove(id); }

    // ------------------------------------------------------------------ handshake

    /** Snobblemon Client 2.7.0 (and anything older than 2.6.0) takes at most 8 tooltip lines and no hex colour codes */
    static boolean legacyTips(ServerPlayer p) {
        PlayerState s = STATE.get(p.getUUID());
        String v = s == null ? "" : s.clientVersion;
        if (v.isEmpty()) return false;
        if (v.equals("2.7.0")) return true;
        String[] a = v.split("[^0-9]+");
        try { int ma = Integer.parseInt(a[0]), mi = a.length > 1 ? Integer.parseInt(a[1]) : 0; return ma < 2 || (ma == 2 && mi < 6); } catch (RuntimeException e) { return false; }
    }

    /** the client's reported version is at least major.minor */
    static boolean clientAtLeast(ServerPlayer p, int major, int minor) {
        PlayerState s = STATE.get(p.getUUID());
        String v = s == null ? "" : s.clientVersion;
        String[] a = v.split("[^0-9]+");
        try { int ma = Integer.parseInt(a[0]), mi = a.length > 1 ? Integer.parseInt(a[1]) : 0; return ma > major || (ma == major && mi >= minor); } catch (RuntimeException e) { return false; }
    }

    static void onHello(ServerPlayer p, SnobbleProto.Hello h) {
        PlayerState s = STATE.computeIfAbsent(p.getUUID(), k -> new PlayerState());
        s.hello = true;
        s.clientVersion = h.clientVersion();
        s.accepted = h.protocol() == SnobbleProto.PROTOCOL;
        String reason = s.accepted ? "" : "Protocol " + h.protocol() + " is not supported (server speaks " + SnobbleProto.PROTOCOL + "). Update the Snobblemon Client mod.";
        ServerPlayNetworking.send(p, new SnobbleProto.Welcome(SnobbleProto.PROTOCOL, s.accepted, reason, ThemePack.NS, ThemePack.artVersion(),
                FabricLoader.getInstance().getModContainer(SnobblemonServer.MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?")));
        SnobblemonServer.LOGGER.info("{} has Snobblemon Client {} (accepted={})", p.getGameProfile().getName(), h.clientVersion(), s.accepted);
    }

    static void onPackState(ServerPlayer p, SnobbleProto.PackState ps) {
        PlayerState s = STATE.get(p.getUUID());
        if (s == null || !s.hello) return;                          // never answered a Hello: ignore
        s.loadedArt = ps.loadedArtVersion();
        s.themeNs = ps.themeNamespace();
        boolean current = ThemePack.artVersion().equals(s.loadedArt);
        SnobblemonServer.LOGGER.info("{} reports art '{}' (server '{}'): {}", p.getGameProfile().getName(), s.loadedArt, ThemePack.artVersion(), current ? "current" : "NOT current");
    }

    // ------------------------------------------------------------------ opening

    /** True when this player has the client and it speaks our protocol. */
    public static boolean hasClient(ServerPlayer p) {
        PlayerState s = STATE.get(p.getUUID());
        return s != null && s.accepted && ServerPlayNetworking.canSend(p, SnobbleProto.GuiOpen.TYPE);
    }

    public static boolean themeCurrent(ServerPlayer p) {
        PlayerState s = STATE.get(p.getUUID());
        return s != null && ThemePack.artVersion().equals(s.loadedArt);
    }

    /** Opens a GUI for a player. Callers use the result to fall back to the legacy menu (NO_CLIENT / NO_THEME). */
    public static OpenResult open(ServerPlayer p, String guiId, boolean ignoreTheme) {
        PlayerState s = STATE.get(p.getUUID());
        if (s == null || !s.hello) return OpenResult.NO_CLIENT;
        if (!s.accepted) return OpenResult.REFUSED;
        if (!ignoreTheme && !themeCurrent(p)) return OpenResult.NO_THEME;
        String json = DEFS.get(guiId);
        if (json == null) return OpenResult.UNKNOWN_GUI;
        GuiSpec spec;
        try { spec = GuiSpec.parse(json); } catch (GuiSpec.Invalid e) { return OpenResult.INVALID; }
        Function<ServerPlayer, JsonObject> prov = DATA.get(guiId);
        JsonObject data = prov == null ? new JsonObject() : prov.apply(p);
        s.open = spec;
        s.data = data;
        s.openAt = System.currentTimeMillis();
        ServerPlayNetworking.send(p, new SnobbleProto.GuiOpen(guiId, json, data.toString()));
        return OpenResult.OK;
    }

    // ------------------------------------------------------------------ actions

    static void onAction(ServerPlayer p, SnobbleProto.GuiAction a) {
        PlayerState s = STATE.get(p.getUUID());
        if (s == null || !s.accepted) return;                       // no handshake: ignore silently
        long now = System.currentTimeMillis();
        if (now - s.windowStart >= 1000) { s.windowStart = now; s.windowCount = 0; }
        if (++s.windowCount > RATE_PER_SECOND) { s.dropped++; return; }

        if (a.widgetId().equals("__close")) {
            if (s.open != null && s.open.id.equals(a.guiId())) s.open = null;
            return;
        }
        if (a.widgetId().equals("__invalid")) return;
        GuiSpec spec = s.open;
        if (spec == null || !spec.id.equals(a.guiId()) || now - s.openAt > MAX_OPEN_MS) {
            deny(p, a.guiId(), "That menu is not open.");
            return;
        }
        GuiSpec.Widget w = spec.byId.get(a.widgetId());
        if (w == null || !w.type.equals("button") || w.action.isEmpty()) { deny(p, a.guiId(), "That button does not exist."); return; }
        if (!w.enabledIf.isEmpty()) {
            var v = s.data.get(w.enabledIf);
            boolean on = v != null && v.isJsonPrimitive() && (v.getAsJsonPrimitive().isBoolean() ? v.getAsBoolean() : !v.getAsString().isEmpty() && !v.getAsString().equals("0"));
            if (!on) { deny(p, a.guiId(), "That button is not available."); return; }
        }
        if (!w.permission.isEmpty() && !permitted(p, w.permission)) { deny(p, a.guiId(), "You do not have permission."); return; }
        Action act = ACTIONS.get(w.action);
        if (act == null) { deny(p, a.guiId(), "That does nothing yet."); return; }
        Result r;
        try {
            r = act.run(p, w, s.data);
        } catch (Throwable t) {
            SnobblemonServer.LOGGER.warn("Action {} failed: {}", w.action, t.toString());
            deny(p, a.guiId(), "Something went wrong.");
            return;
        }
        if (r.close()) {
            s.open = null;
            ServerPlayNetworking.send(p, new SnobbleProto.GuiClose(spec.id));
            return;
        }
        if (r.newData() != null) s.data = r.newData();
        ServerPlayNetworking.send(p, new SnobbleProto.GuiUpdate(spec.id, r.newData() == null ? "" : r.newData().toString(), r.message(), r.ok()));
    }

    private static void deny(ServerPlayer p, String guiId, String msg) {
        ServerPlayNetworking.send(p, new SnobbleProto.GuiUpdate(guiId, "", msg, false));
    }

    /** LuckPerms-compatible check through fabric-permissions-api when it is installed (looked up reflectively, no dependency), else operator level 3. Fails closed. */
    static boolean permitted(ServerPlayer p, String node) {
        try {
            Class<?> c = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method m = c.getMethod("check", net.minecraft.world.entity.Entity.class, String.class, int.class);
            return (boolean) m.invoke(null, p, node, 3);
        } catch (ReflectiveOperationException | LinkageError e) {
            return p.hasPermissions(3);
        }
    }

    public static List<String> guiIds() { return new ArrayList<>(DEFS.keySet()); }
}
