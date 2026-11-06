package net.snobblemon.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.snobblemon.proto.SnobbleProto;
import net.snobblemon.proto.UiSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Server side of layout v2 pages. A backing mod (rules, goal, shop...) keeps its own logic and its own message format; it only changes
 * WHERE it sends data and WHERE it reads button presses:
 * <ul>
 * <li>{@link #open(ServerPlayer, String)}: send the page's layout and wake the backing mod with a synthetic {@code hello}.</li>
 * <li>{@link #emit(ServerPlayer, String, String, JsonObject)}: the backing mod's "event" (what it used to push to the web page).</li>
 * <li>{@link #onMessage(String, BiConsumer)}: the backing mod's handler for {@code {channel, kind, ...}} messages (what the web page posted).</li>
 * </ul>
 * The client sends only (page, node, raw args). The message the backing mod receives is built HERE from the server's own copy of the
 * node's template, each argument checked against its declared type and range.
 */
public final class SnobbleUi {
    /** Turns a backing-mod event into page data (mutates {@code data}). The default stores the payload under the event name. */
    @FunctionalInterface
    public interface View { void apply(ServerPlayer p, String event, JsonObject payload, JsonObject data); }

    static final class Open {
        final UiSpec spec;
        final String json;
        JsonObject data = new JsonObject();
        final long at = System.currentTimeMillis();
        long windowStart;
        int windowCount;
        Open(UiSpec s, String j) { spec = s; json = j; }
    }

    private static final Map<UUID, Open> OPEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Open> HUDS = new ConcurrentHashMap<>();

    /** The page of this id the player has open, as a full page or as the HUD. */
    private static Open find(ServerPlayer p, String pageId) {
        Open o = OPEN.get(p.getUUID());
        if (o != null && o.spec.id.equals(pageId)) return o;
        Open h = HUDS.get(p.getUUID());
        return h != null && h.spec.id.equals(pageId) ? h : null;
    }

    /** Shows a HUD page (over the world, no mouse) next to any full page. False means "not shown". */
    public static boolean openHud(ServerPlayer p, String pageId) {
        if (!available(p)) return false;
        String json = LAYOUTS.get(pageId);
        if (json == null) return false;
        UiSpec spec;
        try { spec = UiSpec.parse(json); } catch (UiSpec.Invalid e) { return false; }
        Open o = new Open(spec, json);
        withClock(o.data);
        HUDS.put(p.getUUID(), o);
        record(pageId, "_open", new JsonObject());
        ServerPlayNetworking.send(p, new SnobbleProto.UiHud(pageId, json, o.data.toString()));
        dispatch(p, spec, hello(spec));
        return true;
    }

    /** True when the layout is a HUD (drawn over the world); other layouts are full screens even if a mod asked for a HUD. */
    public static boolean isHudLayout(String pageId) {
        String json = LAYOUTS.get(pageId);
        if (json == null) return false;
        try { return !UiSpec.parse(json).hudAnchor.isEmpty(); } catch (UiSpec.Invalid e) { return false; }
    }

    /** The id of the HUD page this player has, or null. */
    public static String hudPage(ServerPlayer p) {
        Open h = HUDS.get(p.getUUID());
        return h == null ? null : h.spec.id;
    }
    /**
     * The layout adapted to the player's client: a client that is too old to know a feature would refuse the whole page, so the page is trimmed instead.
     * Client 2.7.0 / below 2.6.0: tooltips cut to 8 lines, no tilt. Below 2.9.0: keyframe motions ("kf") are dropped (the node just shows).
     */
    static String adapt(ServerPlayer p, String json) {
        boolean old = GuiService.legacyTips(p), noKf = !GuiService.clientAtLeast(p, 2, 9);
        if (!old && !noKf) return json;
        if (!json.contains("\"tooltip\"") && !json.contains("\"tilt\"") && !json.contains("\"kf\"")) return json;
        try { JsonElement e = com.google.gson.JsonParser.parseString(json); trim(e, old, noKf); return e.toString(); } catch (RuntimeException ex) { return json; }
    }

    private static void trim(JsonElement e, boolean old, boolean noKf) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (old) {
                if (o.has("tooltip") && o.get("tooltip").isJsonArray() && o.getAsJsonArray("tooltip").size() > 8) {
                    com.google.gson.JsonArray a = new com.google.gson.JsonArray();
                    for (int i = 0; i < 8; i++) a.add(o.getAsJsonArray("tooltip").get(i));
                    o.add("tooltip", a);
                }
                o.remove("tilt");          // the older client's tilt shares one pose between all cards and hides their contents: flat cards instead
                o.remove("holo");
            }
            if (noKf) {
                o.remove("kf");
                if (o.has("anim") && o.get("anim").isJsonPrimitive() && o.get("anim").getAsString().equals("kf")) o.remove("anim");
            }
            for (var en : new java.util.ArrayList<>(o.entrySet())) trim(en.getValue(), old, noKf);
        } else if (e.isJsonArray()) for (JsonElement c : e.getAsJsonArray()) trim(c, old, noKf);
    }

    private static final java.util.Queue<Runnable> DEFER = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** runs the work that must wait for the end of the current tick (called once at start-up) */
    public static void initTick() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(srv -> { Runnable r; int n = DEFER.size(); while (n-- > 0 && (r = DEFER.poll()) != null) { try { r.run(); } catch (RuntimeException e) { SnobblemonServer.LOGGER.warn("deferred UI work failed: {}", e.toString()); } } });
    }

    private static final Map<String, String> LAYOUTS = new HashMap<>();
    private static final Map<String, BiConsumer<ServerPlayer, String>> HANDLERS = new ConcurrentHashMap<>();
    private static final Map<String, View> VIEWS = new ConcurrentHashMap<>();
    private static final int RATE_PER_SECOND = 14;
    private static final long MAX_OPEN_MS = 6 * 3_600_000L;
    private static volatile long dropped;

    private SnobbleUi() {}

    // ------------------------------------------------------------------ registry

    public static void registerView(String pageId, View v) { VIEWS.put(pageId, v); }

    private static final java.util.Set<String> NATIVE = ConcurrentHashMap.newKeySet();

    /** A page whose messages are answered by its own view (no backing mod): the hello and every click go to the view as "hello" / "msg". */
    public static void registerNative(String pageId) { NATIVE.add(pageId); }
    /** The backing mod's handler for messages of one channel (the layout's "channel"). Replaces an earlier handler. */
    public static void onMessage(String channel, BiConsumer<ServerPlayer, String> h) { HANDLERS.put(channel, h); }

    public static synchronized String reload() {
        Map<String, String> fresh = new HashMap<>();
        int bad = 0;
        try {
            var me = FabricLoader.getInstance().getModContainer(SnobblemonServer.MOD_ID).orElseThrow();
            for (Path root : me.getRootPaths()) bad += load(root.resolve("data").resolve(SnobblemonServer.MOD_ID).resolve("ui"), fresh);
        } catch (RuntimeException e) {
            SnobblemonServer.LOGGER.warn("Could not read built-in UI pages: {}", e.toString());
        }
        bad += load(FabricLoader.getInstance().getConfigDir().resolve("snobblemon").resolve("ui"), fresh);
        LAYOUTS.clear();
        LAYOUTS.putAll(fresh);
        String s = "Loaded " + fresh.size() + " UI page(s)" + (bad > 0 ? ", skipped " + bad + " invalid" : "") + ": " + fresh.keySet();
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
                    UiSpec spec = UiSpec.parse(text);
                    out.put(spec.id, text);
                } catch (UiSpec.Invalid | IOException e) {
                    bad++;
                    SnobblemonServer.LOGGER.warn("Skipping UI page {}: {}", f.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            SnobblemonServer.LOGGER.warn("Could not list {}: {}", dir, e.toString());
        }
        return bad;
    }

    public static List<String> pageIds() { return new ArrayList<>(LAYOUTS.keySet()); }
    public static long dropped() { return dropped; }

    // ------------------------------------------------------------------ opening and events

    /** True when this player can be shown native pages (client installed and speaking our protocol). */
    public static boolean available(ServerPlayer p) {
        return GuiService.hasClient(p) && ServerPlayNetworking.canSend(p, SnobbleProto.UiOpen.TYPE);
    }

    /** Opens a page. False means "not shown" (no client, unknown page): the caller keeps its old (WebGUI) route. */
    public static boolean open(ServerPlayer p, String pageId) {
        if (!available(p)) return false;
        String json = LAYOUTS.get(pageId);
        if (json == null) return false;
        json = adapt(p, json);
        UiSpec spec;
        try { spec = UiSpec.parse(json); } catch (UiSpec.Invalid e) { return false; }
        Open o = new Open(spec, json);
        withClock(o.data);
        OPEN.put(p.getUUID(), o);
        record(pageId, "_open", new JsonObject());
        ServerPlayNetworking.send(p, new SnobbleProto.UiOpen(pageId, json, o.data.toString()));
        // wake the backing mod exactly like the web page's first "hello" did
        if (spec.channel.startsWith("ascension")) {
            // the web page's "ready" reached AscensionLib over the network, after the call that opened the page had finished (AscensionLib marks the card
            // active right AFTER opening it): answer one tick later, or AscensionLib sees an empty queue and closes the card at once
            final String pid = pageId;
            DEFER.add(() -> { Open cur = OPEN.get(p.getUUID()); if (cur == o && pid.equals(cur.spec.id)) dispatch(p, spec, hello(spec)); });
        } else dispatch(p, spec, hello(spec));
        return true;
    }

    /** TEST RIGS ONLY (needs -Dsnobble.rig): every event a page receives is appended to logs/ui_events.jsonl, so the original web page can be fed the very same data for side-by-side comparison. */
    private static void record(String pageId, String event, JsonObject payload) {
        if (System.getProperty("snobble.rig") == null || event.equals("msg") || event.equals("hello")) return;
        try {
            JsonObject r = new JsonObject();
            r.addProperty("t", System.currentTimeMillis());
            r.addProperty("page", pageId);
            r.addProperty("event", event);
            r.add("payload", payload == null ? new JsonObject() : payload);
            Files.writeString(Path.of("logs", "ui_events.jsonl"), r + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            SnobblemonServer.LOGGER.warn("ui event record failed: {}", e.toString());
        }
    }

    /** The id of the page this player has open, or null. */
    public static String openPage(ServerPlayer p) {
        Open o = OPEN.get(p.getUUID());
        return o == null ? null : o.spec.id;
    }

    /** True when a layout with this id is loaded. */
    public static boolean hasPage(String pageId) { return LAYOUTS.containsKey(pageId); }

    public static boolean isOpen(ServerPlayer p, String pageId) {
        Open o = OPEN.get(p.getUUID());
        return o != null && o.spec.id.equals(pageId);
    }

    /** The backing mod's event for this player. Ignored when the player does not have that page open. */
    public static void emit(ServerPlayer p, String pageId, String event, JsonObject payload) {
        record(pageId, event, payload);
        Open o = find(p, pageId);
        if (o == null) return;
        View v = VIEWS.get(pageId);
        JsonObject next = o.data.deepCopy();
        next.remove("_now");
        String toast = "";
        boolean ok = false;
        if (v != null) {
            try { v.apply(p, event, payload, next); } catch (RuntimeException ex) {
                SnobblemonServer.LOGGER.warn("Page {} view failed on event '{}': {}", pageId, event, ex.toString());
                return;
            }
        } else next.add(event, payload);
        if (next.has("_toast")) {
            JsonObject t = next.getAsJsonObject("_toast");
            toast = t.has("text") ? t.get("text").getAsString() : "";
            ok = t.has("ok") && t.get("ok").getAsBoolean();
            next.remove("_toast");
        }
        JsonObject before = o.data.deepCopy();
        before.remove("_now");
        if (next.equals(before) && toast.isEmpty()) return;          // nothing changed: do not resend the whole page
        withClock(next);
        o.data = next;
        ServerPlayNetworking.send(p, new SnobbleProto.UiUpdate(pageId, next.toString(), toast.length() > 200 ? toast.substring(0, 200) : toast, ok));
    }

    /** Same as emit(JsonObject) for callers that only have the JSON text (the backing mods call this by reflection). */
    public static void emitJson(ServerPlayer p, String pageId, String event, String json) {
        JsonObject o;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed.isJsonObject()) o = parsed.getAsJsonObject();
            else { o = new JsonObject(); o.add("list", parsed); }          // a list payload reaches the view as {"list": [...]}
        } catch (RuntimeException e) { return; }
        emit(p, pageId, event, o);
    }

    public static void close(ServerPlayer p, String pageId) {
        Open h = HUDS.get(p.getUUID());
        if (h != null && h.spec.id.equals(pageId)) {
            HUDS.remove(p.getUUID());
            ServerPlayNetworking.send(p, new SnobbleProto.UiClose(pageId));
            return;
        }
        Open o = OPEN.get(p.getUUID());
        if (o == null || !o.spec.id.equals(pageId)) return;
        OPEN.remove(p.getUUID());
        ServerPlayNetworking.send(p, new SnobbleProto.UiClose(pageId));
    }

    public static void forget(UUID id) { OPEN.remove(id); HUDS.remove(id); }

    private static void withClock(JsonObject d) { d.addProperty("_now", System.currentTimeMillis()); }

    private static JsonObject hello(UiSpec spec) {
        JsonObject m = new JsonObject();
        m.addProperty("channel", spec.channel);
        m.addProperty("kind", spec.hello);
        m.addProperty("action", spec.hello);      // mods whose page posted {action:...} read this instead of kind
        return m;
    }

    /** The reserved channel for clicks the page's own view answers (tabs, selections) while the rest of the page still talks to its backing mod. */
    static final String VIEW_CHANNEL = "snobble_view";

    /** A view passes a message on to the backing mod's handler for that channel, as if the page had sent it. */
    static void forward(ServerPlayer p, String channel, JsonObject msg) {
        BiConsumer<ServerPlayer, String> h = HANDLERS.get(channel);
        if (h == null) return;
        try {
            msg.addProperty("channel", channel);
            h.accept(p, msg.toString());
        } catch (Throwable t) {
            SnobblemonServer.LOGGER.warn("Forwarded message on '{}' failed: {}", channel, t.toString());
        }
    }

    private static void dispatch(ServerPlayer p, UiSpec spec, JsonObject msg) {
        if (msg.has("channel") && msg.get("channel").getAsString().equals(VIEW_CHANNEL)) {
            emit(p, spec.id, "msg", msg);
            return;
        }
        if (NATIVE.contains(spec.id)) {
            emit(p, spec.id, msg.has("kind") ? "hello" : "msg", msg);
            return;
        }
        BiConsumer<ServerPlayer, String> h = HANDLERS.get(msg.has("channel") ? msg.get("channel").getAsString() : spec.channel);
        if (h == null) return;
        try {
            h.accept(p, msg.toString());
        } catch (Throwable t) {
            SnobblemonServer.LOGGER.warn("Page {} handler failed on '{}': {}", spec.id, msg.has("kind") ? msg.get("kind").getAsString() : "?", t.toString());
        }
    }

    // ------------------------------------------------------------------ messages from the client

    static void onMsg(ServerPlayer p, SnobbleProto.UiMsg m) {
        Open o = find(p, m.pageId());
        if (o == null) return;                 // not open here: ignore silently
        long now = System.currentTimeMillis();
        if (now - o.windowStart >= 1000) { o.windowStart = now; o.windowCount = 0; }
        if (++o.windowCount > RATE_PER_SECOND) { dropped++; return; }
        if (now - o.at > MAX_OPEN_MS) { OPEN.remove(p.getUUID()); HUDS.remove(p.getUUID()); return; }

        if (m.nodeId().equals("__close")) {
            if (HUDS.get(p.getUUID()) == o) HUDS.remove(p.getUUID()); else OPEN.remove(p.getUUID());
            JsonObject bye = new JsonObject();
            bye.addProperty("channel", o.spec.channel);
            bye.addProperty("kind", "bye");
            if (!o.spec.byeAction.isEmpty()) bye.addProperty("action", o.spec.byeAction);
            if (o.spec.bye) dispatch(p, o.spec, bye);
            return;
        }
        UiSpec.Node n = o.spec.byId.get(m.nodeId());
        if (n == null || n.msg == null) { dropped++; return; }
        Map<String, String> raw = new HashMap<>();
        try {
            JsonElement e = JsonParser.parseString(m.argsJson().isEmpty() ? "{}" : m.argsJson());
            if (e.isJsonObject()) {
                Set<String> seen = new HashSet<>();
                for (var en : e.getAsJsonObject().entrySet()) {
                    if (!n.args.containsKey(en.getKey()) || !seen.add(en.getKey())) continue;       // undeclared names are dropped
                    if (en.getValue().isJsonPrimitive()) raw.put(en.getKey(), en.getValue().getAsString());
                }
            }
        } catch (RuntimeException ex) {
            dropped++;
            return;
        }
        JsonObject built = UiSpec.buildMessage(n, raw);
        if (built == null) { dropped++; return; }                                   // missing or out-of-range argument
        if (!built.has("channel")) built.addProperty("channel", o.spec.channel);
        dispatch(p, o.spec, built);
    }
}
