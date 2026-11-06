package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The native Wiki page, a copy of the original wiki (wiki/app.js): the same views (home, mod, article, search), quick-look pane, history,
 * search scoring and snippets. The text lives in the server (data/snobblemon_server/wiki/content.json, converted from the page's content.js);
 * the server answers each click with the one view the player is looking at. Read-only: nothing here changes any player data.
 * Inline markup becomes rich-text markers for the client: U+E001 bold, U+E002 code, U+E003 link.
 */
final class WikiViews {
    private WikiViews() {}

    private static final String PAGE = "wiki";
    private static final char BOLD = '', CODE = '', LINK = '';
    private static JsonObject content;
    private static final Map<String, JsonObject> MODS = new LinkedHashMap<>();
    private static final List<Entry> INDEX = new ArrayList<>();
    private static int totalPages;

    private record Entry(String route, JsonObject m, JsonObject p, String title, String trail, String text, String low, String tl, String ml) {}

    // the original's colours
    private static final String INK = "#d9f1ee", FAINT = "#5f9a98", PANE2 = "#0d343a", PANE3 = "#12414a", ACCENT = "#ff6b7a";
    private static final String[] TILE = {"", "#7d2a22", "#1f5c86", "#1a7a43", "#5e2c75", "#8a5a0a", "#0f6b5a", "#46515c", "#7a2749"};
    private static final Map<String, String> GROUP_ICON = Map.of("start", "cap", "challenges", "badge", "progress", "medal", "economy", "coin",
            "pokemon", "ball", "style", "sparkle", "world", "tree", "ref", "book");
    private static final java.util.Set<String> ICONS = java.util.Set.of("badge", "bag", "ball", "bolt", "book", "cap", "card", "cart", "chat", "clock", "coin",
            "crown", "egg", "flag", "gear", "gem", "hammer", "heart", "house", "key", "lock", "map", "medal", "mic", "pencil", "ribbon", "scroll", "shield",
            "skull", "sparkle", "star", "sword", "swords", "ticket", "tower", "tree", "trophy", "wave");

    static void register() {
        SnobbleUi.registerNative(PAGE);
        SnobbleUi.registerView(PAGE, WikiViews::apply);
    }

    // ------------------------------------------------------------------ data

    private static synchronized void load() {
        if (content != null) return;
        try (InputStream in = WikiViews.class.getResourceAsStream("/data/snobblemon_server/wiki/content.json")) {
            if (in == null) { content = new JsonObject(); return; }
            content = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            SnobblemonServer.LOGGER.warn("Could not read the wiki content: {}", e.toString());
            content = new JsonObject();
            return;
        }
        for (JsonElement e : arr(content, "mods")) MODS.put(s(e.getAsJsonObject(), "id"), e.getAsJsonObject());
        for (JsonObject m : MODS.values()) {
            totalPages += arr(m, "pages").size();
            String info = String.join(" ", strings(m, "info"));
            index(s(m, "id"), m, null, s(m, "name"), "Mod", plain(String.join(" ", s(m, "name"), s(m, "tagline"), s(m, "summary"), info)));
            for (JsonElement pe : arr(m, "pages")) {
                JsonObject p = pe.getAsJsonObject();
                index(s(m, "id") + "/" + s(p, "id"), m, p, s(p, "title"), s(m, "name"), flat(p));
            }
        }
    }

    private static List<String> strings(JsonObject m, String k) {
        List<String> out = new ArrayList<>();
        for (JsonElement r : arr(m, k)) if (r.isJsonArray()) for (JsonElement x : r.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
        return out;
    }

    private static void index(String route, JsonObject m, JsonObject p, String title, String trail, String text) {
        INDEX.add(new Entry(route, m, p, title, trail, text, text.toLowerCase(Locale.ROOT), title.toLowerCase(Locale.ROOT), s(m, "name").toLowerCase(Locale.ROOT)));
    }

    /** flat(p): the page's searchable text, the same parts the original indexed. */
    private static String flat(JsonObject p) {
        JsonArray bl = arr(p, "blocks");
        JsonObject f0 = bl.isEmpty() ? null : bl.get(0).getAsJsonObject();
        String desc = plain(s(p, "desc"));
        boolean dupDesc = f0 != null && s(f0, "t").equals("p") && plain(s(f0, "x")).startsWith(desc.substring(0, Math.min(40, desc.length())));
        StringBuilder sb = new StringBuilder(s(p, "title")).append(" \n ").append(dupDesc ? "" : s(p, "desc"));
        for (JsonElement be : bl) {
            JsonObject b = be.getAsJsonObject();
            if (b.has("x") && b.get("x").isJsonPrimitive()) sb.append(" \n ").append(b.get("x").getAsString());
            for (JsonElement i : arr(b, "items")) {
                if (i.isJsonPrimitive()) sb.append(" \n ").append(i.getAsString());
                else if (i.isJsonObject()) sb.append(" \n ").append(s(i.getAsJsonObject(), "title")).append(' ').append(s(i.getAsJsonObject(), "x"));
            }
            JsonArray cols = arr(b, "cols");
            if (!cols.isEmpty()) { sb.append(" \n "); for (JsonElement c : cols) sb.append(c.getAsString()).append(' '); }
            for (JsonElement r : arr(b, "rows")) if (r.isJsonArray()) { sb.append(" \n "); for (JsonElement c : r.getAsJsonArray()) sb.append(cellText(c)).append(' '); }
            for (JsonElement l : arr(b, "lines")) sb.append(" \n ").append(l.getAsString());
        }
        return plain(sb.toString()).replaceAll("\\s+", " ");
    }

    private static JsonArray arr(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }
    private static String s(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : ""; }

    private static String cellText(JsonElement c) {
        if (c.isJsonPrimitive()) return c.getAsString();
        if (c.isJsonObject()) return s(c.getAsJsonObject(), "x");
        return "";
    }

    private static JsonObject pageOf(JsonObject m, String id) {
        for (JsonElement pe : arr(m, "pages")) if (s(pe.getAsJsonObject(), "id").equals(id)) return pe.getAsJsonObject();
        return null;
    }

    // ------------------------------------------------------------------ text

    private static final Pattern LINK1 = Pattern.compile("\\[\\[([^\\]|]+)\\|([^\\]]+)\\]\\]");
    private static final Pattern LINK2 = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");
    private static final Pattern CODE1 = Pattern.compile("`([^`]+)`");
    private static final Pattern BOLD1 = Pattern.compile("\\*\\*([^*]+)\\*\\*");

    private static String label(String route) {
        String[] sp = route.split("/");
        JsonObject m = MODS.get(sp[0]);
        if (m == null) return route;
        if (sp.length > 1) { JsonObject p = pageOf(m, sp[1]); return p != null ? s(p, "title") : route; }
        return s(m, "name");
    }

    /** plain(): text without the markup. */
    static String plain(String t) {
        t = LINK1.matcher(t).replaceAll("$2");
        Matcher m = LINK2.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(label(m.group(1))));
        m.appendTail(sb);
        return sb.toString().replace("`", "").replace("*", "");
    }

    /** inline(): **bold**, `code`, [[route|label]] / [[route]] as rich-text markers. */
    static String inline(String t) {
        t = t.replace(String.valueOf(BOLD), "").replace(String.valueOf(CODE), "").replace(String.valueOf(LINK), "");
        t = LINK1.matcher(t).replaceAll(Matcher.quoteReplacement(String.valueOf(LINK)) + "$2" + Matcher.quoteReplacement(String.valueOf(LINK)));
        Matcher m = LINK2.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(LINK + label(m.group(1)) + LINK));
        m.appendTail(sb);
        t = CODE1.matcher(sb.toString()).replaceAll(CODE + "$1" + CODE);
        t = BOLD1.matcher(t).replaceAll(BOLD + "$1" + BOLD);
        return t;
    }

    /** The first link in a piece of text: a paragraph that holds a link opens it when clicked. */
    private static String firstLink(String t) {
        Matcher m = LINK1.matcher(t);
        if (m.find()) return m.group(1);
        m = LINK2.matcher(t);
        return m.find() ? m.group(1) : "";
    }

    private static String plural(int n, String w) { return n + " " + w + (n == 1 ? "" : "s"); }

    // ------------------------------------------------------------------ messages and navigation

    private static void apply(ServerPlayer p, String event, JsonObject msg, JsonObject data) {
        load();
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : fresh();
        if (event.equals("msg")) {
            switch (s(msg, "action")) {
                case "go" -> { String r = s(msg, "route"); if (!r.equals("-")) go(st, r, true); }
                case "home" -> go(st, "", true);
                case "back" -> back(st);
                case "search" -> search(st, s(msg, "q"));
                case "step" -> step(st, s(msg, "dir"));
                case "sel" -> { String k = s(msg, "k"); if (k.length() <= 80) st.addProperty("sel", k); }
                case "tfilter" -> { String q = s(msg, "q"); st.addProperty("tf", q.length() > 40 ? q.substring(0, 40) : q); }
                default -> { }
            }
        }
        data.add("st", st);
        data.add("vm", build(st, p.getGameProfile().getName()));
    }

    private static JsonObject fresh() {
        JsonObject st = new JsonObject();
        st.addProperty("view", "home");
        st.addProperty("mod", "");
        st.addProperty("page", "");
        st.addProperty("q", "");
        st.addProperty("sel", MODS.isEmpty() ? "" : MODS.keySet().iterator().next());
        st.addProperty("tf", "");
        st.add("hist", new JsonArray());
        return st;
    }

    private static void push(JsonObject st) {
        JsonArray h = st.getAsJsonArray("hist");
        JsonObject snap = new JsonObject();
        for (String k : new String[]{"view", "mod", "page", "q", "sel"}) snap.addProperty(k, s(st, k));
        h.add(snap);
        while (h.size() > 30) h.remove(0);
    }

    private static void go(JsonObject st, String route, boolean remember) {
        if (remember) push(st);
        String[] sp = route.split("/");
        JsonObject m = MODS.get(sp[0]);
        st.addProperty("q", "");
        st.addProperty("tf", "");
        if (m == null) { st.addProperty("view", "home"); st.addProperty("mod", ""); st.addProperty("page", ""); return; }
        st.addProperty("mod", sp[0]);
        if (sp.length > 1 && pageOf(m, sp[1]) != null) { st.addProperty("view", "page"); st.addProperty("page", sp[1]); }
        else {
            st.addProperty("view", "mod");
            st.addProperty("page", "");
            JsonArray pages = arr(m, "pages");
            st.addProperty("sel", pages.isEmpty() ? "" : s(pages.get(0).getAsJsonObject(), "id"));
        }
    }

    private static void back(JsonObject st) {
        JsonArray h = st.getAsJsonArray("hist");
        if (h.isEmpty()) { if (!s(st, "view").equals("home")) { st.addProperty("view", "home"); st.addProperty("mod", ""); st.addProperty("page", ""); st.addProperty("q", ""); } return; }
        JsonObject snap = h.remove(h.size() - 1).getAsJsonObject();
        for (String k : new String[]{"view", "mod", "page", "q", "sel"}) st.addProperty(k, s(snap, k));
        st.addProperty("tf", "");
    }

    private static void search(JsonObject st, String q) {
        q = q.trim();
        if (q.length() > 60) q = q.substring(0, 60);
        if (q.equals(s(st, "q"))) return;
        if (s(st, "q").isEmpty() && !q.isEmpty()) push(st);
        st.addProperty("q", q);
        if (!q.isEmpty()) st.addProperty("view", "search");
        else {
            JsonArray h = st.getAsJsonArray("hist");
            if (h.isEmpty()) st.addProperty("view", "home");
            else { JsonObject snap = h.remove(h.size() - 1).getAsJsonObject(); for (String k : new String[]{"view", "mod", "page", "sel"}) st.addProperty(k, s(snap, k)); }
        }
    }

    private static void step(JsonObject st, String dir) {
        JsonObject m = MODS.get(s(st, "mod"));
        if (m == null || !s(st, "view").equals("page")) return;
        JsonArray pages = arr(m, "pages");
        for (int i = 0; i < pages.size(); i++) {
            if (!s(pages.get(i).getAsJsonObject(), "id").equals(s(st, "page"))) continue;
            int j = dir.equals("prev") ? i - 1 : i + 1;
            if (j >= 0 && j < pages.size()) { push(st); st.addProperty("page", s(pages.get(j).getAsJsonObject(), "id")); st.addProperty("tf", ""); }
            return;
        }
    }

    // ------------------------------------------------------------------ view model

    private static String groupName(String id) {
        for (JsonElement g : arr(content, "groups")) if (s(g.getAsJsonObject(), "id").equals(id)) return s(g.getAsJsonObject(), "name");
        return "";
    }

    private static String icon(String name) { return ICONS.contains(name) ? name : "star"; }
    private static String tileColor(JsonObject o, JsonObject fallback) {
        int c = o != null && o.has("c") ? o.get("c").getAsInt() : fallback != null && fallback.has("c") ? fallback.get("c").getAsInt() : 7;
        return TILE[c >= 1 && c <= 8 ? c : 7];
    }

    private static JsonObject build(JsonObject st, String player) {
        JsonObject vm = new JsonObject();
        String view = s(st, "view");
        JsonObject m = MODS.get(s(st, "mod"));
        JsonObject page = view.equals("page") && m != null ? pageOf(m, s(st, "page")) : null;
        if (view.equals("page") && page == null) view = m != null ? "mod" : "home";
        if (view.equals("mod") && m == null) view = "home";
        vm.addProperty("isHome", view.equals("home"));
        vm.addProperty("isMod", view.equals("mod"));
        vm.addProperty("isPage", view.equals("page"));
        vm.addProperty("isSearch", view.equals("search"));
        vm.addProperty("whoName", player);
        vm.addProperty("whoLine", plural(MODS.size(), "mod") + " · " + plural(totalPages, "page"));
        String key = view + "|" + s(st, "mod") + "|" + s(st, "page") + "|" + (view.equals("search") ? s(st, "q") : "");
        vm.addProperty("scrollKey", key);
        vm.addProperty("sideKey", "side");
        vm.addProperty("detailKey", key + "|" + s(st, "sel"));
        vm.add("side", side(st, view));
        vm.addProperty("backOp", st.getAsJsonArray("hist").isEmpty() && view.equals("home") ? 0.45 : 1);
        // footer status line
        String crumb = "Wiki";
        if (view.equals("search")) crumb += " > Search";
        else if (m != null) { crumb += " > " + s(m, "name"); if (page != null) crumb += " > " + s(page, "title"); }
        String note = s(content, "note");
        vm.addProperty("stat", crumb + (note.isEmpty() ? "" : "  ·  " + note));
        switch (view) {
            case "home" -> home(vm, st);
            case "mod" -> mod(vm, st, m);
            case "page" -> article(vm, st, m, page);
            default -> results(vm, s(st, "q"));
        }
        return vm;
    }

    private static JsonArray side(JsonObject st, String view) {
        JsonArray out = new JsonArray();
        boolean first = true;
        for (JsonElement ge : arr(content, "groups")) {
            JsonObject g = ge.getAsJsonObject();
            boolean any = false;
            for (JsonObject m : MODS.values()) {
                if (!s(m, "group").equals(s(g, "id"))) continue;
                if (!any) {
                    JsonObject h = new JsonObject();
                    h.addProperty("hdr", true);
                    h.addProperty("first", first);
                    h.addProperty("name", s(g, "name"));
                    h.addProperty("gicon", GROUP_ICON.getOrDefault(s(g, "id"), ""));
                    out.add(h);
                    any = true;
                    first = false;
                }
                boolean on = s(st, "mod").equals(s(m, "id")) && !view.equals("home") && !view.equals("search");
                JsonObject o = new JsonObject();
                o.addProperty("nav", true);
                o.addProperty("route", s(m, "id"));
                o.addProperty("name", s(m, "name"));
                o.addProperty("n", String.valueOf(arr(m, "pages").size()));
                o.addProperty("icon", icon(s(m, "icon")));
                o.addProperty("c", tileColor(m, null));
                boolean lit = on && view.equals("mod");
                o.addProperty("bg", lit ? PANE3 : "");
                o.addProperty("hv", lit ? PANE3 : PANE2);
                o.addProperty("border", lit ? "#d96a74" : "");
                out.add(o);
                if (on && view.equals("page")) for (JsonElement pe : arr(m, "pages")) {
                    JsonObject p = pe.getAsJsonObject(), x = new JsonObject();
                    boolean cur = s(p, "id").equals(s(st, "page"));
                    x.addProperty("sub", true);
                    x.addProperty("route", s(m, "id") + "/" + s(p, "id"));
                    x.addProperty("name", s(p, "title"));
                    x.addProperty("bg", cur ? PANE3 : "");
                    x.addProperty("bar", cur ? ACCENT : "");
                    x.addProperty("color", cur ? "#ffffff" : "#8db5b3");
                    out.add(x);
                }
            }
        }
        return out;
    }

    private static final Map<String, String[]> PILL = Map.of("g", new String[]{"#12b45a", "#04210f"}, "b", new String[]{"#2ea8e8", "#04202e"},
            "y", new String[]{"#f2c230", "#2a2000"}, "r", new String[]{"#e0364a", "#ffffff"}, "p", new String[]{"#9b6bd6", "#12062a"});

    private static JsonObject card(String sel, boolean on, String icon, String c, String title, String text, String pill, String pillc) {
        JsonObject o = new JsonObject();
        o.addProperty("sel", sel);
        o.addProperty("on", on);
        o.addProperty("icon", icon);
        o.addProperty("c", c);
        o.addProperty("title", title);
        o.addProperty("text", plain(text));
        o.addProperty("pill", pill);
        String[] pc = PILL.getOrDefault(pillc, new String[]{"#2a4146", "#bbccdd"});
        o.addProperty("pillBg", pc[0]);
        o.addProperty("pillFg", pc[1]);
        return o;
    }

    private static void home(JsonObject vm, JsonObject st) {
        vm.addProperty("crumbs", "Wiki");
        vm.addProperty("title", s(content, "title").isEmpty() ? "Snobblemon Wiki" : s(content, "title"));
        vm.addProperty("desc", s(content, "intro").isEmpty() ? "Everything about this server in one place. Pick a mod, or search." : s(content, "intro"));
        String sel = MODS.containsKey(s(st, "sel")) ? s(st, "sel") : MODS.isEmpty() ? "" : MODS.keySet().iterator().next();
        JsonArray groups = new JsonArray();
        for (JsonElement ge : arr(content, "groups")) {
            JsonObject g = ge.getAsJsonObject();
            JsonArray cards = new JsonArray();
            for (JsonObject m : MODS.values()) if (s(m, "group").equals(s(g, "id")))
                cards.add(card(s(m, "id"), s(m, "id").equals(sel), icon(s(m, "icon")), tileColor(m, null), s(m, "name"), s(m, "tagline"),
                        plural(arr(m, "pages").size(), "page"), s(m, "pillc").isEmpty() ? "g" : s(m, "pillc")));
            if (cards.isEmpty()) continue;
            JsonObject go = new JsonObject();
            go.addProperty("name", s(g, "name"));
            go.addProperty("gicon", GROUP_ICON.getOrDefault(s(g, "id"), ""));
            go.add("cards", cards);
            groups.add(go);
        }
        vm.add("groups", groups);
        vm.add("cards", new JsonArray());
        vm.addProperty("noPages", false);
        JsonObject m = MODS.get(sel);
        if (m == null) return;
        JsonObject d = new JsonObject();
        d.addProperty("icon", icon(s(m, "icon")));
        d.addProperty("c", tileColor(m, null));
        d.addProperty("title", s(m, "name"));
        d.addProperty("red", groupName(s(m, "group")) + (s(m, "kind").isEmpty() ? "" : " · " + s(m, "kind")));
        d.addProperty("overview", inline(s(m, "summary").isEmpty() ? s(m, "tagline") : s(m, "summary")));
        JsonArray rows = new JsonArray();
        int n = 0;
        for (JsonElement r : arr(m, "info")) {
            if (!r.isJsonArray() || r.getAsJsonArray().size() < 2 || n++ >= 4) continue;
            rows.add(kv(r.getAsJsonArray().get(0).getAsString(), r.getAsJsonArray().get(1).getAsString()));
        }
        d.addProperty("rowsLabel", rows.isEmpty() ? "" : "Quick facts");
        d.addProperty("contents", false);
        d.add("rows", rows);
        JsonArray rel = related(m);
        d.add("related", rel);
        d.addProperty("hasRelated", !rel.isEmpty());
        d.addProperty("open", s(m, "id"));
        d.addProperty("openText", "Open");
        vm.add("d", d);
    }

    private static JsonObject kv(String k, String v) {
        JsonObject o = new JsonObject();
        o.addProperty("k", k);
        o.addProperty("v", v);
        return o;
    }

    private static JsonArray related(JsonObject m) {
        JsonArray out = new JsonArray();
        for (JsonElement r : arr(m, "related")) {
            String id = r.getAsString();
            if (!MODS.containsKey(id) || out.size() >= 8) continue;
            JsonObject o = new JsonObject();
            o.addProperty("route", id);
            o.addProperty("text", s(MODS.get(id), "name"));
            out.add(o);
        }
        return out;
    }

    private static List<String> headings(JsonObject p) {
        List<String> out = new ArrayList<>();
        for (JsonElement be : arr(p, "blocks")) if (s(be.getAsJsonObject(), "t").equals("h")) out.add(s(be.getAsJsonObject(), "x"));
        return out;
    }

    private static void mod(JsonObject vm, JsonObject st, JsonObject m) {
        vm.addProperty("crumbs", "Wiki  ›  " + groupName(s(m, "group")) + "  ›  " + s(m, "name"));
        vm.addProperty("title", s(m, "name"));
        vm.addProperty("desc", inline(s(m, "summary").isEmpty() ? s(m, "tagline") : s(m, "summary")));
        vm.add("groups", new JsonArray());
        JsonArray pages = arr(m, "pages");
        JsonObject sel = pageOf(m, s(st, "sel"));
        if (sel == null && !pages.isEmpty()) sel = pages.get(0).getAsJsonObject();
        JsonArray cards = new JsonArray();
        for (JsonElement pe : pages) {
            JsonObject p = pe.getAsJsonObject();
            cards.add(card(s(p, "id"), p == sel, icon(s(p, "icon").isEmpty() ? s(m, "icon") : s(p, "icon")), tileColor(p, m), s(p, "title"), s(p, "desc"),
                    s(p, "pill"), s(p, "pillc").isEmpty() ? "b" : s(p, "pillc")));
        }
        vm.add("cards", cards);
        vm.addProperty("noPages", pages.isEmpty());
        if (sel == null) return;
        JsonObject d = new JsonObject();
        d.addProperty("icon", icon(s(sel, "icon").isEmpty() ? s(m, "icon") : s(sel, "icon")));
        d.addProperty("c", tileColor(sel, m));
        d.addProperty("title", s(sel, "title"));
        d.addProperty("red", s(m, "name") + " · " + groupName(s(m, "group")));
        d.addProperty("overview", inline(s(sel, "desc")));
        JsonArray rows = new JsonArray();
        if (!s(sel, "pill").isEmpty()) rows.add(kv("Contents", s(sel, "pill")));
        List<String> hs = headings(sel);
        for (int i = 0; i < hs.size() && i < 8; i++) rows.add(kv(hs.get(i), ""));
        d.addProperty("contents", !s(sel, "pill").isEmpty() && hs.isEmpty());
        d.addProperty("rowsLabel", hs.isEmpty() ? "" : "In this page");
        d.add("rows", rows);
        JsonArray rel = related(m);
        d.add("related", rel);
        d.addProperty("hasRelated", !rel.isEmpty());
        d.addProperty("open", s(m, "id") + "/" + s(sel, "id"));
        d.addProperty("openText", "Open page");
        vm.add("d", d);
    }

    private static JsonObject crumb(String route, String text, boolean link) {
        JsonObject o = new JsonObject();
        o.addProperty("route", link ? route : "-");
        o.addProperty("text", text);
        o.addProperty("color", link ? "#99cccc" : FAINT);
        o.addProperty("hv", link ? "#ffffff" : FAINT);
        return o;
    }

    private static void article(JsonObject vm, JsonObject st, JsonObject m, JsonObject p) {
        JsonArray cl = new JsonArray();
        cl.add(crumb("", "Wiki", true));
        cl.add(crumb("-", "  ›  ", false));
        cl.add(crumb(s(m, "id"), s(m, "name"), true));
        cl.add(crumb("-", "  ›  " + s(p, "title"), false));
        vm.add("crumbList", cl);
        vm.addProperty("title", s(p, "title"));
        JsonArray bl = arr(p, "blocks");
        JsonObject first = bl.isEmpty() ? null : bl.get(0).getAsJsonObject();
        String desc = plain(s(p, "desc"));
        boolean dup = first != null && s(first, "t").equals("p") && plain(s(first, "x")).startsWith(desc.substring(0, Math.min(40, desc.length())));
        vm.addProperty("lede", s(p, "desc").isEmpty() || dup ? "" : inline(s(p, "desc")));
        JsonArray out = new JsonArray();
        String tf = s(st, "tf").toLowerCase(Locale.ROOT);
        for (JsonElement be : bl) { if (out.size() > 470) break; block(be.getAsJsonObject(), out, tf); }
        vm.add("blocks", out);
        vm.addProperty("modName", s(m, "name"));
        vm.addProperty("modIcon", icon(s(m, "icon")));
        vm.addProperty("modC", tileColor(m, null));
        JsonArray info = new JsonArray();
        for (JsonElement r : arr(m, "info")) if (r.isJsonArray() && r.getAsJsonArray().size() >= 2) info.add(kv(r.getAsJsonArray().get(0).getAsString(), r.getAsJsonArray().get(1).getAsString()));
        vm.add("info", info);
        JsonArray hs = new JsonArray();
        for (String h : headings(p)) hs.add(h);
        vm.add("headings", hs);
        JsonArray rel = new JsonArray();
        for (JsonElement r : arr(m, "related")) if (MODS.containsKey(r.getAsString())) { JsonObject o = new JsonObject(); o.addProperty("route", r.getAsString()); o.addProperty("text", s(MODS.get(r.getAsString()), "name")); rel.add(o); }
        vm.add("related", rel);
        JsonArray pages = arr(m, "pages");
        for (int i = 0; i < pages.size(); i++) if (s(pages.get(i).getAsJsonObject(), "id").equals(s(p, "id"))) {
            vm.addProperty("prevOp", i > 0 ? 1 : 0.45);
            vm.addProperty("nextOp", i < pages.size() - 1 ? 1 : 0.45);
        }
    }

    private static JsonObject b(String t, String x) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        o.addProperty("x", x);
        return o;
    }

    private static final String[][] CALL = {{"important", "#ff6b7a", "#2a1519", "#ff9aa5"}, {"tip", "#2fd36b", "#0f2a1d", "#6ee7a0"},
            {"note", "#2ea8e8", "#0b2433", "#7fc8f0"}, {"warn", "#f2c230", "#2a2208", "#ffd966"}};

    private static void block(JsonObject bl, JsonArray out, String tf) {
        String t = s(bl, "t");
        switch (t) {
            case "p" -> { JsonObject o = b("p", inline(s(bl, "x"))); o.addProperty("route", firstLink(s(bl, "x"))); out.add(o); }
            case "h" -> out.add(b("h", s(bl, "x")));
            case "h3" -> out.add(b("h3", s(bl, "x")));
            case "ul", "ol" -> {
                int n = 1;
                for (JsonElement i : arr(bl, "items")) {
                    String x = i.isJsonPrimitive() ? i.getAsString() : i.isJsonObject() ? s(i.getAsJsonObject(), "x") : "";
                    JsonObject o = b("li", inline(x));
                    o.addProperty("mark", t.equals("ul") ? "•" : (n++) + ".");
                    out.add(o);
                }
                out.add(b("listend", ""));
            }
            case "call" -> {
                String k = s(bl, "k").isEmpty() ? "important" : s(bl, "k");
                String[] c = CALL[0];
                for (String[] cc : CALL) if (cc[0].equals(k)) c = cc;
                JsonObject o = b("call", BOLD + k.toUpperCase(Locale.ROOT) + BOLD + " · " + inline(s(bl, "x")));
                o.addProperty("bar", c[1]);
                o.addProperty("bg", c[2]);
                o.addProperty("key", c[3]);
                out.add(o);
            }
            case "code" -> {
                List<String> lines = new ArrayList<>();
                for (JsonElement l : arr(bl, "lines")) lines.add(l.getAsString());
                if (lines.isEmpty()) lines.add(s(bl, "x"));
                out.add(b("code", String.join("\n", lines)));
            }
            case "cards" -> {
                JsonObject o = b("cards", "");
                JsonArray items = new JsonArray();
                for (JsonElement ce : arr(bl, "items")) {
                    JsonObject c = ce.getAsJsonObject(), x = new JsonObject();
                    x.addProperty("title", s(c, "title"));
                    x.addProperty("x", inline(s(c, "x")));
                    x.addProperty("icon", icon(s(c, "icon")));
                    x.addProperty("c", tileColor(c, null));
                    x.addProperty("route", s(c, "go").isEmpty() ? "-" : s(c, "go"));
                    items.add(x);
                }
                o.add("items", items);
                out.add(o);
            }
            case "table", "cmds", "kv" -> table(bl, t, out, tf);
            default -> { }
        }
    }

    private static void table(JsonObject bl, String t, JsonArray out, String tf) {
        List<String> cols = new ArrayList<>();
        List<JsonArray> rows = new ArrayList<>();
        boolean key = !(bl.has("key") && bl.get("key").isJsonPrimitive() && !bl.get("key").getAsBoolean());
        if (t.equals("table")) for (JsonElement c : arr(bl, "cols")) cols.add(c.getAsString());
        else if (t.equals("cmds")) { cols.add("Command"); cols.add("What it does"); if (bl.has("perm") && !bl.get("perm").isJsonNull() && !(bl.get("perm").isJsonPrimitive() && bl.get("perm").getAsJsonPrimitive().isBoolean() && !bl.get("perm").getAsBoolean())) cols.add("Who"); }
        else { cols.add(s(bl, "k").isEmpty() ? "Setting" : s(bl, "k")); cols.add(s(bl, "v").isEmpty() ? "Value" : s(bl, "v")); }
        for (JsonElement re : arr(bl, "rows")) if (re.isJsonArray()) rows.add(re.getAsJsonArray());
        boolean big = (bl.has("filter") && bl.get("filter").isJsonPrimitive() && bl.get("filter").getAsBoolean()) || rows.size() > 14;
        int nc = Math.max(1, Math.min(8, cols.size()));
        // column widths: by the longest text in each column (the browser's automatic table layout, roughly), in 5% steps
        double[] wgt = new double[nc];
        for (int i = 0; i < nc; i++) wgt[i] = Math.min(28, plain(cols.get(i)).length() * 0.8 + 3);
        for (JsonArray r : rows) for (int i = 0; i < nc && i < r.size(); i++) wgt[i] = Math.max(wgt[i], Math.min(i == nc - 1 ? 60 : 34, plain(cellText(r.get(i))).length() + 3));
        double sum = 0;
        for (double w : wgt) sum += w;
        int[] pct = new int[nc];
        int used = 0;
        for (int i = 0; i < nc - 1; i++) { pct[i] = Math.max(10, (int) Math.round(wgt[i] / sum * 20) * 5); used += pct[i]; }
        pct[nc - 1] = Math.max(5, 100 - used);
        if (used + pct[nc - 1] > 100) for (int i = 0; i < nc - 1 && used + pct[nc - 1] > 100; i++) if (pct[i] > 10) { pct[i] -= 5; used -= 5; }
        if (big) { JsonObject f = b("filter", ""); f.addProperty("hint", "Filter " + rows.size() + " rows..."); out.add(f); }
        JsonObject head = b("th", "");
        JsonArray hc = new JsonArray();
        for (int i = 0; i < nc; i++) hc.add(cell(cols.get(i), FAINT, pct[i]));
        head.add("cells", hc);
        out.add(head);
        int shown = 0, k = 0;
        for (JsonArray r : rows) {
            if (big && !tf.isEmpty()) {
                StringBuilder all = new StringBuilder();
                for (JsonElement c : r) all.append(plain(cellText(c))).append(' ');
                if (!all.toString().toLowerCase(Locale.ROOT).contains(tf)) continue;
            }
            if (++shown > 160 || out.size() > 485) break;
            JsonObject row = b("tr", "");
            row.addProperty("bg", (k++ % 2 == 1) ? "#0c3036" : "");
            JsonArray cells = new JsonArray();
            for (int i = 0; i < nc; i++) {
                JsonElement ce = i < r.size() ? r.get(i) : null;
                String txt = ce == null ? "" : cellText(ce);
                if (t.equals("cmds") && i == 0) txt = "`" + txt + "`";
                String color = i == 0 && key ? "#ffffff" : "#d2ecea";
                if (ce != null && ce.isJsonObject() && ce.getAsJsonObject().has("pill")) color = PILL.getOrDefault(s(ce.getAsJsonObject(), "pill"), new String[]{"#bbccdd"})[0];
                else if (ce != null && ce.isJsonObject() && ce.getAsJsonObject().has("go")) txt = "[[" + s(ce.getAsJsonObject(), "go") + "|" + txt + "]]";
                cells.add(cell(txt, color, pct[i]));
            }
            row.add("cells", cells);
            out.add(row);
        }
        out.add(b("tableend", ""));
    }

    private static JsonObject cell(String x, String color, int p) {
        JsonObject o = new JsonObject();
        o.addProperty("x", inline(x));
        o.addProperty("color", color);
        o.addProperty("p", p);
        return o;
    }

    // ------------------------------------------------------------------ search (the original's scoring and snippets)

    private static void results(JsonObject vm, String q) {
        String[] toks = q.toLowerCase(Locale.ROOT).trim().split("\\s+");
        record Hit(Entry e, int score) { }
        List<Hit> hits = new ArrayList<>();
        if (!q.isBlank()) for (Entry e : INDEX) {
            int score = 0;
            boolean all = true;
            for (String tk : toks) {
                if (tk.isEmpty()) continue;
                boolean a = e.tl().contains(tk), bb = e.ml().contains(tk), c = e.low().contains(tk);
                if (!a && !bb && !c) { all = false; break; }
                score += (a ? 6 : 0) + (bb ? 3 : 0) + (c ? 1 : 0);
            }
            if (!all) continue;
            if (e.p() == null) score += 2;
            hits.add(new Hit(e, score));
        }
        hits.sort((x, y) -> y.score() - x.score());
        vm.addProperty("crumbs", "Wiki  ›  Search");
        vm.addProperty("desc", hits.isEmpty() ? "Nothing found for \"" + q + "\". Try a shorter word." : plural(hits.size() > 40 ? 40 : hits.size(), "result") + " for \"" + q + "\"");
        JsonArray out = new JsonArray();
        for (int i = 0; i < hits.size() && i < 40; i++) {
            Entry e = hits.get(i).e();
            JsonObject o = new JsonObject();
            o.addProperty("route", e.route());
            o.addProperty("title", e.title());
            o.addProperty("trail", e.trail());
            JsonObject src = e.p() != null ? e.p() : e.m();
            o.addProperty("icon", icon(s(src, "icon").isEmpty() ? s(e.m(), "icon") : s(src, "icon")));
            o.addProperty("c", tileColor(src, e.m()));
            o.addProperty("snip", snippet(e, toks));
            out.add(o);
        }
        vm.add("results", out);
    }

    private static String snippet(Entry e, String[] toks) {
        String body = e.text().startsWith(e.title()) ? e.text().substring(e.title().length()).stripLeading() : e.text();
        String low = body.toLowerCase(Locale.ROOT);
        int i = -1;
        for (int k = 0; k < toks.length && i < 0; k++) if (!toks[k].isEmpty()) i = low.indexOf(toks[k]);
        int a = i < 0 ? 0 : Math.max(0, i - 45);
        if (a > 0) { int sp = body.indexOf(' ', a); if (sp > 0 && sp < i) a = sp + 1; }
        int end = Math.min(body.length(), a + 140);
        if (end < body.length()) { int sp2 = body.lastIndexOf(' ', end); if (sp2 > a + 60) end = sp2; }
        String out = body.substring(a, end).replace(String.valueOf(BOLD), "");
        for (String t : toks) if (!t.isEmpty()) out = Pattern.compile("(" + Pattern.quote(t) + ")", Pattern.CASE_INSENSITIVE).matcher(out).replaceAll(Matcher.quoteReplacement(String.valueOf(BOLD)) + "$1" + Matcher.quoteReplacement(String.valueOf(BOLD)));
        return (a > 0 ? "..." : "") + out + (end < body.length() ? "..." : "");
    }
}
