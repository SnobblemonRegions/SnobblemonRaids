package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The QuestDex quest journal. The catalogue of quests (names, descriptions, objectives, rewards) is a copy of what the web page had baked in
 * (questdex_catalog.json); the player's own state (what is accepted, progress, cooldowns, tracking, regions) comes live from the QuestDex Bridge as
 * "quest_state". This view decides what to show and passes accept / turn in / track / rules clicks back to the bridge, which runs the real
 * SimpleQuests commands and checks everything again.
 */
final class QuestDexViews {
    private QuestDexViews() {}

    static final String PAGE = "questdex";
    private static final String CHANNEL = "questdex";
    private static final Map<String, String> COLORS = Map.of("rootvale", "#4ADE80", "tideforge", "#3AB4FF", "duskvale", "#B98CFF", "journey", "#FFD23A", "tutorial", "#FF7A7A",
            "daily", "#FFA94D", "active", "#5EE6FF", "completed", "#3AFF8C");
    private static final List<String> REGION_TABS = List.of("rootvale", "tideforge", "duskvale");
    private static final Map<String, String[]> VIRTUAL = Map.of(
            "daily", new String[]{"Daily Quests", "Reset every day. Complete them for steady Journey Points."},
            "active", new String[]{"In Progress", "Everything you have accepted right now."},
            "completed", new String[]{"Completed", "Finished quests and ones cooling down before they repeat."});
    private static final String RULES_MSG = "You must complete the Rules quest first before beginning!";

    private static final List<JsonObject> Q = new ArrayList<>();
    private static final Map<String, JsonObject> BY_ID = new HashMap<>();
    private static final Map<String, JsonObject> CATS = new HashMap<>();
    private static JsonObject catalog;

    /** A player's last quest_state, already unpacked. */
    private static final class Snap {
        boolean live;
        String trainer = "Trainer", tracked = "";
        List<String> regions = new ArrayList<>();
        Map<String, Integer> counters = new HashMap<>();
        Set<String> done = new HashSet<>();
        Map<String, JsonObject> quests = new HashMap<>();
        Map<String, JsonObject> cdOf = new HashMap<>();
    }
    private static final Map<UUID, Snap> SNAPS = new ConcurrentHashMap<>();

    static void register() {
        load();
        SnobbleUi.registerView(PAGE, QuestDexViews::apply);
    }

    private static synchronized void load() {
        if (catalog != null) return;
        try (InputStream in = QuestDexViews.class.getResourceAsStream("/data/snobblemon_server/questdex_catalog.json")) {
            if (in == null) { catalog = new JsonObject(); return; }
            catalog = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : catalog.getAsJsonArray("quests")) { JsonObject q = e.getAsJsonObject(); Q.add(q); BY_ID.put(str(q, "id"), q); }
            for (JsonElement e : catalog.getAsJsonArray("categories")) CATS.put(str(e.getAsJsonObject(), "id"), e.getAsJsonObject());
        } catch (Exception e) {
            SnobblemonServer.LOGGER.warn("QuestDex catalogue could not be read: {}", e.toString());
            catalog = new JsonObject();
        }
    }

    private static JsonArray arr(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }
    private static JsonObject obj(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null; }
    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // ------------------------------------------------------------ events and clicks

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : new JsonObject();
        if (!st.has("tab")) { st.addProperty("tab", "tutorial"); st.addProperty("sel", ""); st.addProperty("query", ""); }
        Snap snap = SNAPS.computeIfAbsent(p.getUUID(), u -> new Snap());
        switch (event) {
            case "quest_state" -> unpack(snap, s, data);
            case "rules_notice" -> { if (!str(s, "text").isEmpty()) toast(data, str(s, "text")); }
            case "msg" -> message(p, snap, st, data, s);
            default -> { return; }
        }
        data.add("st", st);
        data.add("vm", build(snap, st));
    }

    private static void toast(JsonObject data, String text) {
        JsonObject t = new JsonObject();
        t.addProperty("text", text);
        t.addProperty("ok", true);
        data.add("_toast", t);
    }

    private static void unpack(Snap n, JsonObject p, JsonObject data) {
        Map<String, JsonObject> before = n.quests;
        boolean wasLive = n.live;
        String wasTracked = n.tracked;
        n.live = true;
        n.trainer = str(p, "trainer").isEmpty() ? n.trainer : str(p, "trainer");
        n.regions = new ArrayList<>();
        for (JsonElement e : arr(p, "regions")) n.regions.add(e.getAsString());
        n.tracked = str(p, "tracked");
        n.counters = new HashMap<>();
        JsonObject cs = obj(p, "counters");
        if (cs != null) for (var en : cs.entrySet()) if (en.getValue().isJsonPrimitive() && en.getValue().getAsJsonPrimitive().isNumber()) n.counters.put(en.getKey(), en.getValue().getAsInt());
        n.done = new HashSet<>();
        for (JsonElement e : arr(p, "done")) n.done.add(e.getAsString());
        n.quests = new HashMap<>();
        JsonObject qs = obj(p, "quests");
        if (qs != null) for (var en : qs.entrySet()) if (en.getValue().isJsonObject()) n.quests.put(en.getKey(), en.getValue().getAsJsonObject());
        String doneText = str(catalog, "doneText").isEmpty() ? "Quest Complete: %quest_name%" : str(catalog, "doneText");
        if (wasLive) for (var en : n.quests.entrySet()) {
            JsonObject was = before.get(en.getKey()), q = BY_ID.get(en.getKey());
            if (q != null && top(q)) {
                if (was != null && str(was, "status").equals("in_progress") && str(en.getValue(), "status").equals("completed")) toast(data, doneText.replace("%quest_name%", str(q, "name")));
            }
        }
        if (wasLive && !n.tracked.isEmpty() && !n.tracked.equals(wasTracked) && BY_ID.containsKey(n.tracked))
            toast(data, (str(catalog, "trackedText").isEmpty() ? "Tracking quest: %quest_name%" : str(catalog, "trackedText")).replace("%quest_name%", str(BY_ID.get(n.tracked), "name")));
    }

    private static void send(ServerPlayer p, String kind, String id, String choice) {
        JsonObject m = new JsonObject();
        m.addProperty("kind", kind);
        if (id != null) m.addProperty("id", id);
        if (choice != null) m.addProperty("choice", choice);
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static void message(ServerPlayer p, Snap n, JsonObject st, JsonObject data, JsonObject m) {
        String a = str(m, "action"), k = str(m, "k");
        switch (a) {
            case "tab" -> { if (tabVisible(n, k)) { st.addProperty("tab", k); st.addProperty("query", ""); st.addProperty("sel", ""); } }
            case "sel" -> { if (BY_ID.containsKey(k)) st.addProperty("sel", k); }
            case "q" -> st.addProperty("query", str(m, "q").trim().toLowerCase(Locale.ROOT));
            case "goto" -> {
                JsonObject q = BY_ID.get(k);
                if (q != null) { st.addProperty("tab", str(q, "cat")); st.addProperty("query", ""); st.addProperty("sel", k); }
            }
            case "act" -> act(p, n, st, data, k, str(m, "id"));
            case "tick" -> { }
            default -> { }
        }
        if (!tabVisible(n, str(st, "tab"))) { st.addProperty("tab", "tutorial"); st.addProperty("sel", ""); }
    }

    private static void act(ServerPlayer p, Snap n, JsonObject st, JsonObject data, String kind, String id) {
        JsonObject q = BY_ID.get(id.isEmpty() ? str(st, "sel") : id);
        if (kind.equals("rules")) { send(p, "rules", null, null); return; }
        if (q == null) return;
        String rulesId = str(catalog, "rulesQuest");
        switch (kind) {
            case "accept" -> {
                if (!str(q, "id").equals(rulesId) && rulesLocked(n)) { toast(data, RULES_MSG); return; }
                JsonObject parent = q.has("parent") && !q.get("parent").isJsonNull() ? BY_ID.get(str(q, "parent")) : null;
                if (parent != null && str(parent, "kind").equals("composite")) send(p, "accept", str(parent, "id"), str(q, "id"));
                else send(p, "accept", str(q, "id"), null);
            }
            case "submit" -> send(p, "submit", null, null);
            case "track" -> send(p, "track", str(q, "id"), null);
            default -> { }
        }
    }

    // ------------------------------------------------------------ the page logic (a port of the web page's rules)

    private static boolean regionOk(Snap n, JsonObject q) { return str(q, "region").equals("general") || n.regions.contains(str(q, "region")); }
    private static boolean tabVisible(Snap n, String t) { return !REGION_TABS.contains(t) || n.regions.contains(t); }
    private static boolean visible(Snap n, JsonObject q) { return regionOk(n, q) && tabVisible(n, str(q, "cat")); }
    private static boolean top(JsonObject q) { return !q.has("parent") || q.get("parent").isJsonNull(); }
    private static JsonObject st(Snap n, String id) { return n.quests.get(id); }
    private static String sstat(Snap n, String id) { JsonObject s = n.quests.get(id); return s == null ? "" : str(s, "status"); }
    private static boolean rulesLocked(Snap n) {
        String id = str(catalog, "rulesQuest");
        return n.live && BY_ID.containsKey(id) && !sstat(n, id).equals("completed");
    }

    private static String status(Snap n, JsonObject q) {
        JsonObject s = st(n, str(q, "id"));
        String kind = str(obj(q, "repeat"), "kind");
        if (s != null && str(s, "status").equals("in_progress")) return "in_progress";
        if (s != null && str(s, "status").equals("completed")) {
            if (kind.equals("once")) return "completed";
            if (num(s, "cooldownEnds") > System.currentTimeMillis()) return "cooldown";
        }
        if (!regionOk(n, q)) return "locked";
        for (JsonElement e : arr(q, "prereq")) if (!sstat(n, e.getAsString()).equals("completed")) return "locked";
        return "available";
    }

    private static String lockReason(Snap n, JsonObject q) {
        if (!regionOk(n, q)) return str(catalog, "denied").replace("%region%", cap(str(q, "region")));
        for (JsonElement e : arr(q, "prereq")) if (!sstat(n, e.getAsString()).equals("completed")) return "Finish the required quest first.";
        return "";
    }

    private static long have(Snap n, JsonObject o, int i, JsonObject s) {
        if (s == null) return 0;
        long amount = num(o, "amount");
        if (str(s, "status").equals("completed")) return amount;
        JsonArray pr = arr(s, "progress");
        long h = i < pr.size() ? pr.get(i).getAsLong() : 0;
        if (str(o, "type").equals("advancement")) {
            for (JsonElement a : arr(o, "advs")) if (n.done.contains(a.getAsString())) return amount;
            Integer c = n.counters.get(str(o, "counter"));
            if (!str(o, "counter").isEmpty() && c != null) h = Math.max(h, c);
        } else {
            JsonObject tasks = obj(s, "tasks");
            JsonObject t = tasks == null ? null : obj(tasks, str(o, "key"));
            if (t != null) h = Math.max(h, num(t, "have"));
        }
        return Math.min(amount, h);
    }

    private static boolean unknown(Snap n, JsonObject o, JsonObject s) {
        if (!n.live || s == null || !str(s, "status").equals("in_progress")) return false;
        if (str(o, "type").equals("advancement")) return false;
        JsonObject tasks = obj(s, "tasks");
        JsonObject t = tasks == null ? null : obj(tasks, str(o, "key"));
        return t == null || str(t, "text").isEmpty();
    }

    private static JsonObject currentSub(Snap n, JsonObject q) {
        String kind = str(q, "kind");
        if (kind.equals("sequential")) {
            if (!sstat(n, str(q, "id")).equals("in_progress")) return null;
            for (JsonElement e : arr(q, "subs")) if (!sstat(n, e.getAsString()).equals("completed")) return BY_ID.get(e.getAsString());
        }
        if (kind.equals("composite")) for (JsonElement e : arr(q, "subs")) if (sstat(n, e.getAsString()).equals("in_progress")) return BY_ID.get(e.getAsString());
        return null;
    }

    /** {done, total} of a quest. */
    private static long[] progress(Snap n, JsonObject q) {
        String kind = str(q, "kind");
        if (kind.equals("sequential")) {
            long d = 0;
            for (JsonElement e : arr(q, "subs")) if (sstat(n, e.getAsString()).equals("completed")) d++;
            return new long[]{d, Math.max(1, arr(q, "subs").size())};
        }
        if (kind.equals("composite")) {
            JsonObject act = currentSub(n, q);
            return act != null ? progress(n, act) : new long[]{0, 1};
        }
        JsonObject s = st(n, str(q, "id"));
        long done = 0, total = 0;
        JsonArray objs = arr(q, "objs");
        for (int i = 0; i < objs.size(); i++) { JsonObject o = objs.get(i).getAsJsonObject(); total += num(o, "amount"); done += have(n, o, i, s); }
        if (s != null && str(s, "status").equals("completed")) done = total;
        return new long[]{done, total == 0 ? 1 : total};
    }

    private static boolean ready(Snap n, JsonObject q) {
        String kind = str(q, "kind");
        if (kind.equals("sequential") || kind.equals("composite")) { JsonObject cur = currentSub(n, q); return cur != null && ready(n, cur); }
        JsonObject s = st(n, str(q, "id"));
        if (s == null || !str(s, "status").equals("in_progress")) return false;
        JsonArray objs = arr(q, "objs");
        for (int i = 0; i < objs.size(); i++) {
            JsonObject o = objs.get(i).getAsJsonObject();
            if (!(unknown(n, o, s) || have(n, o, i, s) >= num(o, "amount"))) return false;
        }
        return true;
    }

    private static int activeCount(Snap n, String cat) {
        int c = 0;
        for (JsonObject q : Q) if (top(q) && visible(n, q) && str(q, "cat").equals(cat) && status(n, q).equals("in_progress")) c++;
        return c;
    }

    private static List<JsonObject> tabQuests(Snap n, String tab) {
        List<JsonObject> r = new ArrayList<>();
        for (JsonObject q : Q) {
            if (!top(q) || !visible(n, q)) continue;
            String s = status(n, q);
            boolean ok = switch (tab) {
                case "daily" -> str(obj(q, "repeat"), "kind").equals("daily");
                case "active" -> s.equals("in_progress");
                case "completed" -> s.equals("completed") || s.equals("cooldown");
                default -> str(q, "cat").equals(tab);
            };
            if (ok) r.add(q);
        }
        r.sort((a, b) -> Long.compare(num(a, "sort"), num(b, "sort")));
        return r;
    }

    private static boolean matches(String query, JsonObject q) {
        if (query.isEmpty()) return true;
        StringBuilder h = new StringBuilder(str(q, "name")).append(' ');
        for (JsonElement e : arr(q, "desc")) h.append(e.getAsString()).append(' ');
        for (JsonElement e : arr(q, "rewards")) h.append(e.getAsString()).append(' ');
        for (JsonElement e : arr(q, "objs")) h.append(str(e.getAsJsonObject(), "text")).append(' ');
        for (JsonElement e : arr(q, "subs")) {
            JsonObject sq = BY_ID.get(e.getAsString());
            if (sq == null) continue;
            h.append(str(sq, "name")).append(' ');
            for (JsonElement o : arr(sq, "objs")) h.append(str(o.getAsJsonObject(), "text")).append(' ');
        }
        return h.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    // ------------------------------------------------------------ view model (field names match tools/gen_ui_questdex.py; rules from questdex/index.html)

    private static final String SCREEN_L = "#164244", DIMC = "#5a9a86", INKC = "#bff5d8";
    private static final Map<String, String> SHORT = Map.of("journey", "Journey", "tutorial", "Tutorial");
    private static final String[][] BGS = {{"apricorn", "#3b5d2e"}, {"berry", "#5d2e3b"}, {"candy", "#5d2e5d"}, {"stone", "#4a4430"}, {"fossil", "#4a4430"}};
    private static final Map<String, String> BALLC = Map.of("poke", "#7a2a2a", "great", "#243f7a", "ultra", "#6b5a14", "dusk", "#3d1a5a", "moon", "#2a3278",
            "dive", "#14606b", "nest", "#3f5a22", "net", "#1a5a50", "quick", "#1a5878");

    /** glyph(): the tile colour behind a quest's item icon. */
    private static String glyphBg(String item) {
        int c = item.indexOf(':');
        String id = c >= 0 ? item.substring(c + 1) : item;
        if (id.contains("ball")) return BALLC.getOrDefault(id.split("_")[0], "#444444");
        for (String[] b : BGS) if (id.contains(b[0])) return b[1];
        return "#2a4a4a";
    }

    private static String fmtDur(long ms) {
        long m = Math.max(1, Math.round(ms / 60000.0));
        if (m < 60) return m + "M";
        long h = Math.round(m / 60.0);
        return h < 48 ? h + "H" : Math.round(h / 24.0) + "D";
    }

    private static String chipText(Snap n, JsonObject q, String s) {
        return switch (s) {
            case "available" -> "AVAILABLE";
            case "in_progress" -> "IN PROGRESS";
            case "completed" -> "COMPLETED";
            case "locked" -> "LOCKED";
            default -> "IN " + fmtDur(num(st(n, str(q, "id")), "cooldownEnds") - System.currentTimeMillis());
        };
    }

    /** .st-available blue, .st-in_progress yellow, .st-completed green, .st-locked grey (light text), .st-cooldown purple. */
    private static String chipBg(String s) {
        return switch (s) { case "available" -> "#3ab4ff"; case "in_progress" -> "#ffd23a"; case "completed" -> "#3aff8c"; case "locked" -> "#6b7b7b"; default -> "#b98cff"; };
    }

    private static String chipFg(String s) { return s.equals("locked") ? "#e5eded" : "#03201a"; }

    private static String desc(JsonObject q) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : arr(q, "desc")) sb.append(sb.length() == 0 ? "" : " ").append(e.getAsString());
        return sb.toString();
    }

    private static JsonObject build(Snap n, JsonObject st) {
        JsonObject v = new JsonObject();
        v.addProperty("live", n.live);
        v.addProperty("ledOp", n.live ? 1 : 0.35);
        String tab = str(st, "tab"), sel = str(st, "sel"), query = str(st, "query");
        JsonObject menu = obj(catalog, "menu") == null ? new JsonObject() : catalog.getAsJsonObject("menu");
        // nav (renderNav)
        JsonArray nav = new JsonArray();
        String[][] groups = {{"START HERE", "tutorial"}, {"REGIONS", "rootvale", "tideforge", "duskvale"}, {"TRAINER", "journey"}, {"TRACKING", "daily", "active", "completed"}};
        Set<String> order = new HashSet<>();
        for (JsonElement e : arr(catalog, "menuOrder")) order.add(e.getAsString());
        for (String[] g : groups) {
            List<String> tabs = new ArrayList<>();
            for (int i = 1; i < g.length; i++) if (order.contains(g[i]) && tabVisible(n, g[i])) tabs.add(g[i]);
            if (tabs.isEmpty()) continue;
            JsonObject head = new JsonObject();
            head.addProperty("isHead", true);
            head.addProperty("label", g[0]);
            nav.add(head);
            for (String t : tabs) {
                JsonObject c = CATS.get(t), mt = obj(menu, t);
                String name = mt != null && !str(mt, "label").isEmpty() ? str(mt, "label") : SHORT.containsKey(t) ? SHORT.get(t) : c != null ? str(c, "name") : VIRTUAL.get(t)[0];
                List<JsonObject> list = tabQuests(n, t);
                long avail = list.stream().filter(q -> status(n, q).equals("available")).count();
                JsonObject o = new JsonObject();
                o.addProperty("isHead", false);
                o.addProperty("k", t);
                o.addProperty("label", name);
                o.addProperty("count", t.equals("active") || t.equals("completed") ? "**" + list.size() + "**" : "**" + avail + "**/" + list.size());
                o.addProperty("icon", "a:qd_" + t);
                boolean on = t.equals(tab);
                o.addProperty("on", on);
                o.addProperty("bg", on ? SCREEN_L : "#00000000");
                o.addProperty("border", on ? COLORS.get(t) : "#00000000");
                o.addProperty("c", COLORS.get(t));
                nav.add(o);
            }
        }
        v.add("nav", nav);
        // list head (renderList)
        JsonObject c = CATS.get(tab), mt = obj(menu, tab);
        v.addProperty("listName", mt != null && !str(mt, "title").isEmpty() ? str(mt, "title") : c != null ? str(c, "name") : VIRTUAL.containsKey(tab) ? VIRTUAL.get(tab)[0] : tab);
        v.addProperty("listColor", COLORS.getOrDefault(tab, "#ffffff"));
        v.addProperty("listDesc", c != null ? desc(c) : VIRTUAL.containsKey(tab) ? VIRTUAL.get(tab)[1] : "");
        v.addProperty("slots", c != null ? "Active slots: **" + activeCount(n, tab) + " / " + num(c, "max") + "**" : "");
        boolean locked = rulesLocked(n);
        v.addProperty("banner", locked ? RULES_MSG + (tab.equals("tutorial") ? "" : " Open **Tutorial** and press **Open the rules**.") : "");
        List<JsonObject> list;
        if (!query.isEmpty()) {
            list = new ArrayList<>();
            for (JsonObject q : Q) if (top(q) && visible(n, q)) list.add(q);
            list.sort((a, b) -> Long.compare(num(a, "sort"), num(b, "sort")));
        } else list = tabQuests(n, tab);
        list.removeIf(q -> !matches(query, q));
        JsonArray cards = new JsonArray();
        for (JsonObject q : list) {
            if (cards.size() >= 120) break;
            String s = status(n, q);
            long[] p = progress(n, q);
            JsonObject o = new JsonObject();
            o.addProperty("id", str(q, "id"));
            o.addProperty("item", str(q, "icon"));
            o.addProperty("g", glyphBg(str(q, "icon")));
            o.addProperty("name", str(q, "name"));
            JsonArray objs = arr(q, "objs");
            String sum = !desc(q).isEmpty() ? desc(q) : objs.size() > 0 ? str(objs.get(0).getAsJsonObject(), "text")
                    : str(q, "kind").equals("sequential") ? arr(q, "subs").size() + "-step trial" : "Choose one of " + arr(q, "subs").size() + " deals";
            o.addProperty("sum", sum);
            o.addProperty("chip", chipText(n, q, s));
            o.addProperty("chipBg", chipBg(s));
            o.addProperty("chipColor", chipFg(s));
            o.addProperty("showBar", s.equals("in_progress") || s.equals("completed"));
            o.addProperty("pct", Math.round(100.0 * p[0] / p[1]));
            o.addProperty("isNew", bool(q, "isNew"));
            o.addProperty("pin", n.tracked.equals(str(q, "id")));
            boolean isSel = sel.equals(str(q, "id"));
            o.addProperty("border", isSel ? "#ffffff" : SCREEN_L);
            o.addProperty("ring", isSel ? "2,#FFFFFF40" : "0,#00000000");
            o.addProperty("op", s.equals("locked") ? 0.55 : s.equals("completed") ? 0.8 : 1);
            o.addProperty("cat", query.isEmpty() ? "" : CATS.containsKey(str(q, "cat")) ? str(CATS.get(str(q, "cat")), "name") : "");
            cards.add(o);
        }
        v.add("cards", cards);
        v.addProperty("empty", list.isEmpty());
        v.addProperty("emptyText", "No quests here" + (query.isEmpty() ? " yet." : " match your search."));
        // detail
        JsonObject sq = BY_ID.get(sel);
        v.addProperty("hasDetail", sq != null && visible(n, sq));
        if (sq != null && visible(n, sq)) v.add("d", detail(n, sq, locked));
        // tracker + trainer
        JsonObject tq = n.tracked.isEmpty() ? null : BY_ID.get(n.tracked);
        if (tq != null && status(n, tq).equals("in_progress")) {
            JsonObject sub = currentSub(n, tq) == null ? tq : currentSub(n, tq);
            long[] p = progress(n, sub);
            v.addProperty("tracking", true);
            v.addProperty("trackName", str(tq, "name"));
            v.addProperty("trackPct", Math.round(100.0 * p[0] / p[1]));
            v.addProperty("trackText", p[0] + "/" + p[1]);
        } else {
            v.addProperty("tracking", false);
            v.addProperty("trackName", "");
            v.addProperty("trackPct", 0);
            v.addProperty("trackText", "");
        }
        long done = 0, tops = 0;
        for (JsonObject q : Q) if (top(q) && visible(n, q)) { tops++; if (sstat(n, str(q, "id")).equals("completed")) done++; }
        StringBuilder regions = new StringBuilder();
        for (String r : n.regions) regions.append(regions.length() == 0 ? "" : ", ").append(cap(r));
        v.addProperty("trainer", "**" + n.trainer + "**\n" + done + " / " + tops + " quests · " + (n.regions.isEmpty() ? "No region" : regions));
        return v;
    }

    private static JsonArray objectives(Snap n, JsonObject q) {
        JsonArray out = new JsonArray();
        JsonObject s = st(n, str(q, "id"));
        JsonArray objs = arr(q, "objs");
        for (int i = 0; i < objs.size(); i++) {
            JsonObject o = objs.get(i).getAsJsonObject();
            long have = have(n, o, i, s), amount = Math.max(1, num(o, "amount"));
            boolean u = unknown(n, o, s);
            JsonObject r = new JsonObject();
            r.addProperty("text", str(o, "text"));
            r.addProperty("amt", u ? "?" : amount > 1 ? have + " / " + amount : have >= amount ? "✔" : "—");
            r.addProperty("amtColor", have >= amount ? "#3aff8c" : "#ffd23a");
            r.addProperty("pct", Math.round(100.0 * have / amount));
            out.add(r);
        }
        return out;
    }

    private static JsonObject detail(Snap n, JsonObject q, boolean rulesLocked) {
        JsonObject d = new JsonObject();
        String s = status(n, q), id = str(q, "id"), kind = str(q, "kind"), rulesId = str(catalog, "rulesQuest");
        JsonObject c = CATS.get(str(q, "cat"));
        d.addProperty("item", str(q, "icon"));
        d.addProperty("g", glyphBg(str(q, "icon")));
        d.addProperty("name", str(q, "name"));
        d.addProperty("meta", (c == null ? "" : str(c, "name")) + (str(q, "region").equals("general") ? "" : " · " + cap(str(q, "region")) + " only"));
        d.addProperty("metaColor", COLORS.getOrDefault(str(q, "cat"), "#ffffff"));
        d.addProperty("chip", chipText(n, q, s));
        d.addProperty("chipBg", chipBg(s));
        d.addProperty("chipColor", chipFg(s));
        d.addProperty("repeat", str(obj(q, "repeat"), "label") + (kind.equals("sequential") ? " · " + arr(q, "subs").size() + " steps, in order" : kind.equals("composite") ? " · pick one" : ""));
        d.addProperty("brief", desc(q));
        d.addProperty("hasBrief", !desc(q).isEmpty());
        boolean single = kind.equals("single");
        d.addProperty("single", single);
        d.add("objs", single ? objectives(n, q) : new JsonArray());
        JsonObject cur = currentSub(n, q);
        d.addProperty("stepsHead", kind.equals("sequential") ? "STEPS" : "DEALS — CHOOSE ONE");
        JsonArray steps = new JsonArray();
        if (!single) {
            JsonArray subs = arr(q, "subs");
            for (int i = 0; i < subs.size(); i++) {
                JsonObject sub = BY_ID.get(subs.get(i).getAsString());
                if (sub == null) continue;
                boolean done = sstat(n, str(sub, "id")).equals("completed"), isCur = cur != null && str(cur, "id").equals(str(sub, "id"));
                JsonObject o = new JsonObject();
                o.addProperty("id", str(sub, "id"));
                o.addProperty("mk", done ? "✔" : kind.equals("sequential") ? String.valueOf(i + 1) : "○");
                o.addProperty("mkColor", done ? "#3aff8c" : INKC);
                String nm = str(sub, "name");
                int colon = nm.indexOf(':');
                o.addProperty("name", colon >= 0 ? nm.substring(colon + 1).trim() : nm);
                StringBuilder line = new StringBuilder();
                for (JsonElement e : arr(sub, "objs")) line.append(line.length() == 0 ? "" : " · ").append(str(e.getAsJsonObject(), "text"));
                o.addProperty("meta", line.toString());
                o.addProperty("border", isCur ? "#ffd23a" : SCREEN_L);
                o.addProperty("take", kind.equals("composite") && s.equals("available") && !isCur);
                o.addProperty("cur", isCur);
                o.add("objs", isCur ? objectives(n, sub) : new JsonArray());
                steps.add(o);
            }
        }
        d.add("steps", steps);
        JsonArray reqs = new JsonArray();
        for (JsonElement e : arr(q, "prereq")) {
            JsonObject p = BY_ID.get(e.getAsString());
            if (p == null || !visible(n, p)) continue;
            boolean ok = sstat(n, str(p, "id")).equals("completed");
            JsonObject o = new JsonObject();
            o.addProperty("id", str(p, "id"));
            o.addProperty("mk", ok ? "✔" : "✖");
            o.addProperty("mkColor", ok ? "#3aff8c" : "#ff5a5a");
            o.addProperty("name", str(p, "name"));
            o.addProperty("cat", CATS.containsKey(str(p, "cat")) ? str(CATS.get(str(p, "cat")), "name") : "");
            reqs.add(o);
        }
        d.add("reqs", reqs);
        d.addProperty("hasReqs", !reqs.isEmpty());
        JsonArray rewards = new JsonArray();
        for (JsonElement e : arr(q, "rewards")) { JsonObject o = new JsonObject(); o.addProperty("text", e.getAsString()); rewards.add(o); }
        if (rewards.isEmpty()) { JsonObject o = new JsonObject(); o.addProperty("text", "—"); rewards.add(o); }
        d.add("rewards", rewards);
        boolean full = c != null && activeCount(n, str(q, "cat")) >= num(c, "max");
        boolean isRules = id.equals(rulesId);
        d.addProperty("showRules", isRules && (s.equals("available") || s.equals("in_progress")));
        d.addProperty("showAcceptPlain", s.equals("available") && isRules);
        d.addProperty("showAcceptGo", s.equals("available") && !isRules && !kind.equals("composite"));
        d.addProperty("acceptOk", isRules ? !full : !(full || rulesLocked));
        d.addProperty("showSubmit", s.equals("in_progress"));
        d.addProperty("submitOk", ready(n, q));
        d.addProperty("showTrack", s.equals("in_progress"));
        d.addProperty("trackText", n.tracked.equals(id) ? "◉ Tracking" : "○ Track");
        String note = "", noteColor = "#ffb4b4";
        if (s.equals("available")) {
            if (kind.equals("composite")) { note = "Choose one of the deals above to start."; noteColor = DIMC; }
            if (rulesLocked && !isRules) { note = RULES_MSG; noteColor = "#ffb4b4"; }
            else if (full) { note = (c == null ? "" : str(c, "name")) + " already has " + (c == null ? 0 : num(c, "max")) + " active quests. Finish or turn one in first."; noteColor = "#ffb4b4"; }
        }
        if (s.equals("in_progress")) {
            if (isRules) { note = "Read every page of the rules, then press “I have read the rules”. This quest finishes by itself."; noteColor = DIMC; }
            else if (!ready(n, q)) { note = "Finish every objective, then turn it in."; noteColor = DIMC; }
        }
        if (s.equals("locked")) { note = lockReason(n, q); noteColor = "#ffb4b4"; }
        if (s.equals("cooldown")) { note = "Available again in " + fmtDur(num(st(n, id), "cooldownEnds") - System.currentTimeMillis()).toLowerCase(Locale.ROOT) + "."; noteColor = DIMC; }
        d.addProperty("note", note);
        d.addProperty("noteColor", noteColor);
        return d;
    }
}
