package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * Sneelz' Cosmetics - Admin Hub (page "admin", channel snobblecosmeticsadmin). The cosmetics mod's AdminHub still checks every permission and does every
 * change; this view only holds the navigation, search, page, the draft of the open cosmetic and the confirm box, and turns the mod's
 * state / detail / result / pack / owned events into the data the layout reads.
 */
final class AdminViews {
    private AdminViews() {}

    static final String PAGE = "admin";
    private static final String CHANNEL = "snobblecosmeticsadmin";
    private static final int PER = 12;
    private static final String[][] NAV = {
            {"all", "All Cosmetics"}, {"hats", "Hats"}, {"pokemon_skins", "Pokemon Skins"}, {"armour", "Armour"}, {"back", "Back Cosmetics"}, {"balloons", "Balloons"}, {"effects", "Effects"},
            {"-", "STATUS"}, {"draft", "Draft Cosmetics"}, {"published", "Published Cosmetics"}, {"hidden", "Hidden Cosmetics"}, {"problems", "Errors & Disabled"},
            {"-", "SYSTEM"}, {"pack", "Resource Pack Status"}, {"owners", "Ownership"}, {"settings", "Settings"}};
    private static final Map<UUID, JsonObject> STATE = new ConcurrentHashMap<>(), DETAIL = new ConcurrentHashMap<>(), OWNED = new ConcurrentHashMap<>();
    private static final Pattern GRAD = Pattern.compile("^(<b>)?<gradient:(#[0-9a-fA-F]{6}):(#[0-9a-fA-F]{6})>([^<>]*)</gradient>(</b>)?$");
    private static final Pattern SOLID = Pattern.compile("^(<b>)?<(#[0-9a-fA-F]{6})>([^<>]*)(</#[0-9a-fA-F]{6}>)?(</b>)?$");
    private static final Pattern AMP = Pattern.compile("^&([0-9a-fA-F])(&l)?([^<>&]*)$");
    private static final String[] AMPC = {"#000000", "#0000aa", "#00aa00", "#00aaaa", "#aa0000", "#aa00aa", "#ffaa00", "#aaaaaa", "#555555", "#5555ff", "#55ff55", "#55ffff", "#ff5555", "#ff55ff", "#ffff55", "#ffffff"};
    private static long seq = 1;

    static void register() { SnobbleUi.registerView(PAGE, AdminViews::apply); }

    private static JsonArray arr(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    private static JsonObject fresh() {
        JsonObject st = new JsonObject();
        st.addProperty("view", "all");
        st.addProperty("sel", "");
        st.addProperty("page", 0);
        st.addProperty("q", "");
        st.addProperty("dirty", false);
        st.add("dr", new JsonObject());
        st.add("dlg", new JsonObject());
        return st;
    }

    private static String plain(String s) { return s == null ? "" : s.replaceAll("<[^<>]+>", "").replaceAll("&[0-9a-fk-orA-FK-OR]", ""); }

    /** the name's first colour (a gradient starts with its first stop) */
    private static String firstColor(String s) {
        Matcher g = GRAD.matcher(s == null ? "" : s);
        if (g.matches()) return g.group(2).toLowerCase(Locale.ROOT);
        Matcher m = SOLID.matcher(s == null ? "" : s);
        if (m.matches()) return m.group(2).toLowerCase(Locale.ROOT);
        Matcher a = AMP.matcher(s == null ? "" : s);
        if (a.matches()) return AMPC["0123456789abcdef".indexOf(Character.toLowerCase(a.group(1).charAt(0)))];
        Matcher any = Pattern.compile("<(#[0-9a-fA-F]{6})>|gradient:(#[0-9a-fA-F]{6})").matcher(s == null ? "" : s);
        if (any.find()) return (any.group(1) != null ? any.group(1) : any.group(2)).toLowerCase(Locale.ROOT);
        return "#e6edf3";
    }

    /** the page's readName(): {text, bold, mode keep|none|solid|gradient, c1, c2} */
    private static String[] readName(String s) {
        String raw = s == null ? "" : s;
        Matcher g = GRAD.matcher(raw);
        if (g.matches()) return new String[]{g.group(4), g.group(1) != null ? "1" : "0", "gradient", g.group(2).toLowerCase(Locale.ROOT), g.group(3).toLowerCase(Locale.ROOT)};
        Matcher a = AMP.matcher(raw);
        if (a.matches()) return new String[]{a.group(3), a.group(2) != null ? "1" : "0", "solid", AMPC["0123456789abcdef".indexOf(Character.toLowerCase(a.group(1).charAt(0)))], "#ff9ff3"};
        Matcher m = SOLID.matcher(raw);
        if (m.matches()) return new String[]{m.group(3), m.group(1) != null ? "1" : "0", "solid", m.group(2).toLowerCase(Locale.ROOT), "#ff9ff3"};
        return new String[]{plain(raw), "0", raw.matches(".*[<&].*") ? "keep" : "none", "#9ec8ff", "#ff9ff3"};
    }

    private static String buildName(String text, boolean bold, String mode, String c1, String c2, String raw) {
        if (mode.equals("keep")) return raw;
        String t = text.replaceAll("[<>]", "");
        String s = switch (mode) {
            case "gradient" -> "<gradient:" + c1 + ":" + c2 + ">" + t + "</gradient>";
            case "solid" -> "<" + c1 + ">" + t + "</" + c1 + ">";
            default -> t;
        };
        return bold ? "<b>" + s + "</b>" : s;
    }

    private static boolean hex(String s) { return s != null && s.matches("#[0-9a-fA-F]{6}"); }

    private static JsonObject cur(JsonObject state, String id) {
        for (JsonElement e : arr(state, "items")) if (str(e.getAsJsonObject(), "id").equals(id)) return e.getAsJsonObject();
        return null;
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : fresh();
        UUID id = p.getUUID();
        switch (event) {
            case "state" -> { if (!s.has("items")) return; STATE.put(id, s); }
            case "detail" -> {
                DETAIL.put(id, s);
                if (str(s, "id").equals(str(st, "sel"))) seed(st, data, s);
            }
            case "pack" -> { JsonObject state = STATE.get(id); if (state != null) state.add("pack", s); }
            case "owned" -> OWNED.put(id, s);
            case "result" -> {
                if (!str(s, "message").isEmpty()) {
                    JsonObject t = new JsonObject();
                    t.addProperty("text", str(s, "message"));
                    t.addProperty("ok", bool(s, "ok"));
                    data.add("_toast", t);
                }
                if (bool(s, "ok") && bool(s, "close")) SnobbleUi.close(p, PAGE);
            }
            case "msg" -> { JsonObject state = STATE.get(id); if (state != null) message(p, state, st, data, s); }
            default -> { return; }
        }
        data.add("st", st);
        JsonObject state = STATE.get(id);
        if (state != null) data.add("vm", build(state, st, DETAIL.get(id), OWNED.get(id)));
    }

    /** a different cosmetic was opened (or its details came in): the typed boxes start from its saved values */
    private static void seed(JsonObject st, JsonObject data, JsonObject d) {
        JsonObject dr = new JsonObject();
        String[] nm = readName(str(d, "name"));
        dr.addProperty("text", nm[0]);
        dr.addProperty("bold", nm[1].equals("1"));
        dr.addProperty("mode", nm[2]);
        dr.addProperty("c1", nm[3]);
        dr.addProperty("c2", nm[4]);
        dr.addProperty("raw", str(d, "name"));
        dr.addProperty("section", str(d, "section"));
        dr.addProperty("price", (long) num(d, "price"));
        dr.addProperty("desc", str(d, "description").replace("\n", " | "));
        dr.addProperty("purchasable", bool(d, "purchasable"));
        dr.addProperty("suppress", bool(d, "suppressRender"));
        st.add("dr", dr);
        st.addProperty("dirty", false);
        JsonObject vals = new JsonObject();
        vals.addProperty("nm", nm[0]);
        vals.addProperty("c1", nm[3]);
        vals.addProperty("c2", nm[4]);
        vals.addProperty("pr", String.valueOf((long) num(d, "price")));
        vals.addProperty("ds", plain(str(d, "description")).replace("\n", " | "));
        JsonObject lo = new JsonObject();
        lo.addProperty("seq", seq++);
        lo.add("vals", vals);
        data.add("_local", lo);
    }

    private static void act(ServerPlayer p, String action, String id) {
        JsonObject m = new JsonObject();
        m.addProperty("action", action);
        if (id != null && !id.isEmpty()) m.addProperty("id", id);
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static void message(ServerPlayer p, JsonObject state, JsonObject st, JsonObject data, JsonObject m) {
        String a = str(m, "action"), k = str(m, "k");
        JsonObject dr = st.has("dr") && st.get("dr").isJsonObject() ? st.getAsJsonObject("dr") : new JsonObject();
        st.add("dr", dr);
        String sel = str(st, "sel");
        switch (a) {
            case "nav" -> {
                for (String[] n : NAV) if (n[0].equals(k) && !k.equals("-")) {
                    st.addProperty("view", k); st.addProperty("page", 0); st.addProperty("sel", ""); st.addProperty("dirty", false);
                    DETAIL.remove(p.getUUID());
                }
            }
            case "search" -> { String q = str(m, "v"); st.addProperty("q", q.length() > 40 ? q.substring(0, 40) : q); st.addProperty("page", 0); }
            case "page" -> {
                int want = Math.max(0, (int) num(st, "page") + (int) num(m, "d"));
                int pages = Math.max(1, (filtered(state, st).size() + PER - 1) / PER);
                st.addProperty("page", Math.min(want, pages - 1));
            }
            case "sel" -> {
                if (cur(state, k) == null) return;
                st.addProperty("sel", k);
                st.addProperty("dirty", false);
                DETAIL.remove(p.getUUID());
                seed(st, data, cur(state, k));
                act(p, "detail", k);
            }
            case "in" -> {
                String v = str(m, "v");
                switch (k) {
                    case "nm" -> { dr.addProperty("text", v.length() > 40 ? v.substring(0, 40) : v); if (str(dr, "mode").equals("keep")) dr.addProperty("mode", "none"); nameDirty(dr); st.addProperty("dirty", true); }
                    case "c1", "c2" -> { if (hex(v)) { dr.addProperty(k, v.toLowerCase(Locale.ROOT)); nameDirty(dr); st.addProperty("dirty", true); } }
                    case "pr" -> { try { dr.addProperty("price", Math.max(0, Math.min(10000000, Long.parseLong(v.trim().isEmpty() ? "0" : v.trim())))); st.addProperty("dirty", true); } catch (NumberFormatException ignored) { } }
                    case "ds" -> { dr.addProperty("desc", v); dr.addProperty("descSet", true); st.addProperty("dirty", true); }
                    default -> { }
                }
            }
            case "mode" -> { if (k.matches("keep|none|solid|gradient")) { dr.addProperty("mode", k); nameDirty(dr); st.addProperty("dirty", true); } }
            case "bold" -> { dr.addProperty("bold", !bool(dr, "bold")); nameDirty(dr); st.addProperty("dirty", true); }
            case "section" -> { dr.addProperty("section", k); st.addProperty("dirty", true); }
            case "tog" -> { if (k.equals("purchasable") || k.equals("suppress")) { dr.addProperty(k, !bool(dr, k)); st.addProperty("dirty", true); } }
            case "save" -> save(p, st, dr, sel);
            case "preview" -> act(p, "preview", sel);
            case "validate" -> act(p, "validate", sel);
            case "enable" -> act(p, "enable", sel);
            case "ask" -> {                              // opens the confirm box
                JsonObject d = DETAIL.get(p.getUUID());
                String nm = plain(d != null ? str(d, "name") : sel);
                JsonObject dl = new JsonObject();
                dl.addProperty("kind", k);
                switch (k) {
                    case "publish" -> { dl.addProperty("title", "Publish " + nm + "?"); dl.addProperty("label", "Publish"); dl.addProperty("cls", "go");
                        dl.addProperty("text", "It will appear in the shop and players can buy it immediately."); }
                    case "hide" -> { dl.addProperty("title", "Hide " + nm + "?"); dl.addProperty("label", "Hide"); dl.addProperty("cls", "warn");
                        dl.addProperty("text", "It disappears from the shop and cannot be bought. Players who own it keep it and can still wear it."); }
                    case "disable" -> { dl.addProperty("title", "Disable " + nm + "?"); dl.addProperty("label", "Disable"); dl.addProperty("cls", "no");
                        dl.addProperty("text", "Nobody can equip it. Ownership is kept. Use this if something is broken."); }
                    case "rebuild" -> { dl.addProperty("title", "Rebuild the resource pack?"); dl.addProperty("label", "Rebuild"); dl.addProperty("cls", "go");
                        dl.addProperty("text", "Takes a few seconds. Players get the new pack when they next join or reconnect. No restart needed."); }
                    case "grant", "revoke" -> {
                        boolean g = k.equals("grant");
                        String who = str(st, "player"), cid = str(st, "gid");
                        dl.addProperty("title", (g ? "Grant " : "Revoke ") + cid + "?");
                        dl.addProperty("label", g ? "Grant" : "Revoke"); dl.addProperty("cls", g ? "go" : "no");
                        dl.addProperty("text", g ? "Give it to " + (who.isEmpty() ? "..." : who) + " for free." : "Remove it from " + (who.isEmpty() ? "..." : who) + ". They lose it, with no refund.");
                    }
                    default -> { return; }
                }
                st.add("dlg", dl);
            }
            case "no" -> st.add("dlg", new JsonObject());
            case "yes" -> {
                JsonObject dl = st.has("dlg") && st.get("dlg").isJsonObject() ? st.getAsJsonObject("dlg") : new JsonObject();
                String kind = str(dl, "kind");
                st.add("dlg", new JsonObject());
                switch (kind) {
                    case "publish", "hide", "disable" -> act(p, kind, sel);
                    case "rebuild" -> act(p, "rebuild", null);
                    case "grant", "revoke" -> {
                        JsonObject o = new JsonObject();
                        o.addProperty("action", kind);
                        o.addProperty("player", str(st, "player"));
                        o.addProperty("id", str(st, "gid"));
                        SnobbleUi.forward(p, CHANNEL, o);
                    }
                    default -> { }
                }
            }
            case "pl" -> { String v = str(m, "v"); st.addProperty("player", v.length() > 32 ? v.substring(0, 32) : v); }
            case "gid" -> { String v = str(m, "v"); st.addProperty("gid", v.length() > 48 ? v.substring(0, 48) : v); }
            case "owned" -> { JsonObject o = new JsonObject(); o.addProperty("action", "owned"); o.addProperty("player", str(st, "player")); SnobbleUi.forward(p, CHANNEL, o); }
            case "reload" -> act(p, "reload", null);
            case "refresh" -> act(p, "refresh", null);
            case "close" -> SnobbleUi.close(p, PAGE);
            default -> { }
        }
    }

    private static void nameDirty(JsonObject dr) { dr.addProperty("nameSet", true); }

    private static void save(ServerPlayer p, JsonObject st, JsonObject dr, String sel) {
        if (sel.isEmpty()) return;
        JsonObject o = new JsonObject();
        o.addProperty("action", "edit");
        o.addProperty("id", sel);
        if (bool(dr, "nameSet")) o.addProperty("name", buildName(str(dr, "text"), bool(dr, "bold"), str(dr, "mode"), str(dr, "c1"), str(dr, "c2"), str(dr, "raw")));
        if (bool(dr, "descSet")) {
            StringBuilder sb = new StringBuilder();
            for (String l : str(dr, "desc").replaceAll("[<>]", "").split("\\|", -1)) { String t = l.trim(); sb.append(sb.length() == 0 ? "" : "\n").append(t.isEmpty() ? "" : "&7" + t); }
            o.addProperty("description", sb.toString());
        }
        o.addProperty("price", (long) num(dr, "price"));
        o.addProperty("section", str(dr, "section"));
        o.addProperty("purchasable", bool(dr, "purchasable"));
        o.addProperty("suppress_render", bool(dr, "suppress"));
        SnobbleUi.forward(p, CHANNEL, o);
        dr.addProperty("nameSet", false);
        dr.addProperty("descSet", false);
        st.addProperty("dirty", false);
    }

    private static java.util.List<JsonObject> filtered(JsonObject state, JsonObject st) {
        String v = str(st, "view"), q = str(st, "q").trim().toLowerCase(Locale.ROOT);
        java.util.List<JsonObject> out = new java.util.ArrayList<>();
        for (JsonElement e : arr(state, "items")) {
            JsonObject i = e.getAsJsonObject();
            if (!q.isEmpty() && !(plain(str(i, "name")) + " " + str(i, "id")).toLowerCase(Locale.ROOT).contains(q)) continue;
            String stt = str(i, "state");
            boolean ok = switch (v) {
                case "all" -> true;
                case "draft" -> stt.equals("DRAFT") || stt.equals("READY");
                case "published" -> stt.equals("PUBLISHED");
                case "hidden" -> stt.equals("HIDDEN");
                case "problems" -> stt.equals("ERROR") || stt.equals("DISABLED");
                default -> str(i, "section").equals(v);
            };
            if (ok) out.add(i);
        }
        return out;
    }

    private static String stateColor(String s) {
        return switch (s) { case "PUBLISHED" -> "#6ee08a"; case "READY" -> "#6aa8ff"; case "HIDDEN" -> "#ffd166"; case "DISABLED" -> "#ff9a5a"; case "ERROR" -> "#ff7b7b"; default -> "#9aa7b4"; };
    }

    private static String thumb(String id) {
        String t = "thumb_" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return "w:" + (t.length() > 44 ? t.substring(0, 44) : t) + ":0";
    }

    private static String secTitle(JsonObject state, String id) {
        for (JsonElement e : arr(state, "sections")) if (str(e.getAsJsonObject(), "id").equals(id)) return str(e.getAsJsonObject(), "title");
        return id;
    }

    private static JsonObject kv(String k, String v) { JsonObject o = new JsonObject(); o.addProperty("k", k); o.addProperty("v", v); return o; }

    private static JsonObject build(JsonObject state, JsonObject st, JsonObject det, JsonObject owned) {
        JsonObject vm = new JsonObject();
        JsonObject perms = state.has("perms") && state.get("perms").isJsonObject() ? state.getAsJsonObject("perms") : new JsonObject();
        String view = str(st, "view");
        boolean sys = view.equals("pack") || view.equals("owners") || view.equals("settings");
        JsonObject s = state.has("stats") && state.get("stats").isJsonObject() ? state.getAsJsonObject("stats") : new JsonObject();
        JsonArray stats = new JsonArray();
        Object[][] sd = {{"Total", (long) num(s, "total"), "#e6edf3"}, {"Published", (long) num(s, "published"), "#6ee08a"}, {"Draft", (long) (num(s, "draft") + num(s, "ready")), "#e6edf3"},
                {"Hidden", (long) num(s, "hidden"), "#e6edf3"}, {"Disabled", (long) num(s, "disabled"), "#ffd166"}, {"Errors", (long) num(s, "error"), num(s, "error") > 0 ? "#ff7b7b" : "#e6edf3"},
                {"Await pack", (long) num(s, "awaiting"), num(s, "awaiting") > 0 ? "#ffd166" : "#e6edf3"}};
        for (Object[] r : sd) { JsonObject o = new JsonObject(); o.addProperty("label", (String) r[0]); o.addProperty("n", String.valueOf(r[1])); o.addProperty("color", (String) r[2]); stats.add(o); }
        vm.add("stats", stats);
        vm.addProperty("sub", "Manage, price, validate and publish cosmetics");

        JsonArray nav = new JsonArray();
        for (String[] n : NAV) {
            JsonObject o = new JsonObject();
            boolean hdr = n[0].equals("-");
            o.addProperty("hdr", hdr);
            o.addProperty("label", n[1]);
            o.addProperty("k", n[0]);
            boolean on = n[0].equals(view);
            o.addProperty("on", on);
            boolean counted = !hdr && !n[0].equals("pack") && !n[0].equals("owners") && !n[0].equals("settings");
            JsonObject tmp = new JsonObject(); tmp.addProperty("view", n[0]); tmp.addProperty("q", str(st, "q"));
            o.addProperty("count", counted ? String.valueOf(filtered(state, tmp).size()) : "");
            o.addProperty("bg", on ? "#243a2b" : "#171c22");
            o.addProperty("bd", on ? "#6ee08a" : "#171c22");
            nav.add(o);
        }
        vm.add("nav", nav);
        vm.addProperty("isList", !sys);
        vm.addProperty("isPack", view.equals("pack"));
        vm.addProperty("isOwners", view.equals("owners"));
        vm.addProperty("isSettings", view.equals("settings"));
        String currency = str(state, "currency").isEmpty() ? "Gems" : str(state, "currency");

        // ---- list
        java.util.List<JsonObject> list = filtered(state, st);
        int pages = Math.max(1, (list.size() + PER - 1) / PER), pg = Math.max(0, Math.min((int) num(st, "page"), pages - 1));
        String sel = str(st, "sel");
        JsonArray rows = new JsonArray();
        JsonArray cells = null;
        int shown = 0;
        java.util.List<JsonObject> cellObjs = new java.util.ArrayList<>();
        for (int i = pg * PER; i < Math.min(list.size(), pg * PER + PER); i++) {
            JsonObject it = list.get(i), c = new JsonObject();
            String nm = plain(str(it, "name"));
            c.addProperty("id", str(it, "id"));
            c.addProperty("name", nm.isEmpty() ? str(it, "id") : nm);
            c.addProperty("nameColor", firstColor(str(it, "name")));
            c.addProperty("sub", str(it, "id") + "  -  " + secTitle(state, str(it, "section")));
            c.addProperty("state", str(it, "state"));
            c.addProperty("sc", stateColor(str(it, "state")));
            c.addProperty("price", bool(it, "free") ? "free" : num(it, "price") > 0 ? (long) num(it, "price") + " " + currency : "no price");
            c.addProperty("thumb", thumb(str(it, "id")));
            boolean on = str(it, "id").equals(sel);
            c.addProperty("bg", on ? "#223329" : "#1e252d");
            c.addProperty("bd", on ? "#6ee08a" : "#2d3844");
            cellObjs.add(c);
        }
        if (view.equals("problems") && pg == pages - 1) for (JsonElement e : arr(state, "loadErrors")) {
            if (cellObjs.size() >= PER) break;
            JsonObject le = e.getAsJsonObject(), c = new JsonObject();
            c.addProperty("id", "");
            c.addProperty("name", str(le, "id"));
            c.addProperty("nameColor", "#e6edf3");
            c.addProperty("sub", str(le, "file") + " - " + str(le, "message"));
            c.addProperty("state", "ERROR");
            c.addProperty("sc", stateColor("ERROR"));
            c.addProperty("price", "");
            c.addProperty("thumb", "");
            c.addProperty("bg", "#1e252d");
            c.addProperty("bd", "#2d3844");
            cellObjs.add(c);
        }
        for (int i = 0; i < cellObjs.size(); i += 2) {
            JsonObject r = new JsonObject();
            JsonArray cs = new JsonArray();
            cs.add(cellObjs.get(i));
            if (i + 1 < cellObjs.size()) cs.add(cellObjs.get(i + 1));
            r.add("cells", cs);
            rows.add(r);
        }
        vm.add("rows", rows);
        vm.addProperty("empty", !sys && cellObjs.isEmpty());
        vm.addProperty("pageLabel", (pg + 1) + " / " + pages);
        vm.addProperty("prevOp", pg > 0 ? 1.0 : 0.38);
        vm.addProperty("nextOp", pg < pages - 1 ? 1.0 : 0.38);
        vm.addProperty("pager", !sys && pages > 1);

        // ---- pack / settings / owners
        JsonObject pk = state.has("pack") && state.get("pack").isJsonObject() ? state.getAsJsonObject("pack") : new JsonObject();
        JsonArray pkv = new JsonArray();
        pkv.add(kv("Pack built at server start", bool(pk, "built") ? "yes" : "not yet"));
        pkv.add(kv("Cosmetics registered in it", String.valueOf((long) num(pk, "startup"))));
        pkv.add(kv("Waiting for a pack rebuild", String.valueOf((long) num(pk, "awaiting"))));
        pkv.add(kv("Build errors", str(pk, "error").isEmpty() ? "none" : str(pk, "error")));
        vm.add("packKv", pkv);
        vm.addProperty("packNote", str(pk, "note").isEmpty() ? "A new model or texture only reaches players after a server restart builds the pack and they download it." : str(pk, "note"));
        StringBuilder ids = new StringBuilder();
        for (JsonElement e : arr(pk, "awaitingIds")) ids.append(ids.length() == 0 ? "" : ", ").append(e.getAsString());
        vm.addProperty("awaitIds", ids.toString());
        vm.addProperty("hasAwait", num(pk, "awaiting") > 0);
        JsonObject cfg = state.has("settings") && state.get("settings").isJsonObject() ? state.getAsJsonObject("settings") : new JsonObject();
        JsonArray set = new JsonArray();
        set.add(kv("Preview seconds", String.valueOf((long) num(cfg, "preview_seconds"))));
        set.add(kv("Auto-detect bare models as drafts", String.valueOf(bool(cfg, "auto_detect_models"))));
        set.add(kv("Auto-detected cosmetics are free (only when published)", String.valueOf(bool(cfg, "auto_detected_are_free"))));
        set.add(kv("Require resource pack", String.valueOf(bool(cfg, "require_resource_pack"))));
        set.add(kv("Gems shop link", bool(state, "gems") ? "SnobbleGems found" : "SnobbleGems not found: nothing can be bought"));
        vm.add("setKv", set);
        JsonArray pm = new JsonArray();
        for (String k : new String[]{"edit", "publish", "grant", "reload", "pack"}) pm.add(kv("snobblemon.cosmetics.admin." + k, bool(perms, k) ? "yes" : "no"));
        vm.add("permKv", pm);
        vm.addProperty("canReload", bool(perms, "reload"));
        vm.addProperty("canPack", bool(perms, "pack"));
        vm.addProperty("canGrant", bool(perms, "grant"));
        vm.addProperty("noGrant", !bool(perms, "grant"));
        vm.addProperty("reloadOp", bool(perms, "reload") ? 1.0 : 0.38);
        vm.addProperty("packOp", bool(perms, "pack") ? 1.0 : 0.38);
        vm.addProperty("grantOp", bool(perms, "grant") ? 1.0 : 0.38);
        if (owned != null) {
            StringBuilder o = new StringBuilder(str(owned, "player") + " owns " + arr(owned, "owned").size() + ": ");
            StringBuilder l = new StringBuilder();
            for (JsonElement e : arr(owned, "owned")) l.append(l.length() == 0 ? "" : ", ").append(e.getAsString());
            o.append(l.length() == 0 ? "nothing" : l);
            if (arr(owned, "equipped").size() > 0) { StringBuilder w = new StringBuilder(); for (JsonElement e : arr(owned, "equipped")) w.append(w.length() == 0 ? "" : ", ").append(e.getAsString()); o.append("  |  wearing: ").append(w); }
            vm.addProperty("ownedText", o.toString());
        } else vm.addProperty("ownedText", "");

        // ---- editor
        JsonObject it = sel.isEmpty() ? null : cur(state, sel);
        boolean ed = it != null && !sys;
        vm.addProperty("ed", ed);
        int listW = ed ? 636 : 1308;
        vm.addProperty("listW", listW);
        vm.addProperty("toolW", listW - 20);
        vm.addProperty("cellW", (listW - 34) / 2);
        vm.addProperty("edX", 268 + listW + 12);
        if (ed) {
            JsonObject d = det != null && str(det, "id").equals(sel) ? det : it;
            JsonObject dr = st.has("dr") && st.get("dr").isJsonObject() ? st.getAsJsonObject("dr") : new JsonObject();
            boolean edit = bool(perms, "edit"), pub = bool(perms, "publish"), dirty = bool(st, "dirty");
            JsonObject e = new JsonObject();
            String builtName = dr.has("text") ? buildName(str(dr, "text"), bool(dr, "bold"), str(dr, "mode"), str(dr, "c1"), str(dr, "c2"), str(dr, "raw")) : str(d, "name");
            String shown2 = plain(builtName);
            e.addProperty("name", shown2.isEmpty() ? str(d, "id") : shown2);
            e.addProperty("nameColor", firstColor(builtName));
            e.addProperty("bold", bool(dr, "bold"));
            e.addProperty("state", str(d, "state"));
            e.addProperty("sc", stateColor(str(d, "state")));
            e.addProperty("meta", "  " + str(d, "id") + "   " + str(d, "type") + (str(d, "set").isEmpty() ? "" : "   set: " + str(d, "set")));
            e.addProperty("thumb", thumb(str(d, "id")));
            String mode = str(dr, "mode");
            boolean keep = mode.equals("keep");
            e.addProperty("mKeep", keep);
            for (String[] mo : new String[][]{{"keep", "Keep"}, {"none", "Plain"}, {"solid", "Solid"}, {"gradient", "Gradient"}}) {
                e.addProperty("on_" + mo[0], mode.equals(mo[0]));
                e.addProperty("bg_" + mo[0], mode.equals(mo[0]) ? "#243a2b" : "#141a20");
                e.addProperty("bd_" + mo[0], mode.equals(mo[0]) ? "#6ee08a" : "#2d3844");
            }
            e.addProperty("showC1", mode.equals("solid") || mode.equals("gradient"));
            e.addProperty("showC2", mode.equals("gradient"));
            e.addProperty("boldBg", bool(dr, "bold") ? "#243a2b" : "#141a20");
            e.addProperty("boldBd", bool(dr, "bold") ? "#6ee08a" : "#2d3844");
            e.addProperty("c1", str(dr, "c1"));
            e.addProperty("c2", str(dr, "c2"));
            JsonArray secs = new JsonArray();
            for (JsonElement se : arr(state, "sections")) {
                JsonObject so = se.getAsJsonObject(), o = new JsonObject();
                boolean on = str(so, "id").equals(str(dr, "section"));
                o.addProperty("k", str(so, "id")); o.addProperty("label", str(so, "title"));
                o.addProperty("bg", on ? "#243a2b" : "#141a20"); o.addProperty("bd", on ? "#6ee08a" : "#2d3844");
                secs.add(o);
            }
            e.add("secs", secs);
            e.addProperty("currency", currency.toUpperCase(Locale.ROOT));
            e.addProperty("priceLocked", bool(d, "free"));
            boolean purch = bool(dr, "purchasable"), supp = bool(dr, "suppress");
            e.addProperty("purchOn", purch);
            e.addProperty("suppOn", supp);
            e.addProperty("purchLock", bool(d, "free") || !edit);
            e.addProperty("equipOn", bool(d, "equipEnabled"));
            JsonArray ck = new JsonArray();
            if (d.has("checks")) for (JsonElement ce : arr(d, "checks")) {
                JsonObject c = ce.getAsJsonObject(), o = new JsonObject();
                String lv = str(c, "level");
                o.addProperty("lv", lv); o.addProperty("color", lv.equals("OK") ? "#6ee08a" : lv.equals("WARN") ? "#ffd166" : "#ff7b7b");
                o.addProperty("text", str(c, "label") + ": " + str(c, "message"));
                ck.add(o);
            }
            e.add("checks", ck);
            e.addProperty("loading", !d.has("checks"));
            e.addProperty("meta2", !str(d, "by").isEmpty() ? "Last change by " + str(d, "by") + (str(d, "at").length() >= 19 ? " at " + str(d, "at").replace('T', ' ').substring(0, 19) + " UTC" : "") : "No admin changes yet");
            boolean canPub = pub && !str(d, "lifecycle").equals("PUBLISHED") && bool(d, "canPublish") && !dirty;
            e.addProperty("saveOp", edit && dirty ? 1.0 : 0.38);
            e.addProperty("pubOp", canPub ? 1.0 : 0.38);
            e.addProperty("pubText", str(d, "lifecycle").equals("PUBLISHED") ? "Published" : "Publish Cosmetic");
            e.addProperty("hideOp", pub && !str(d, "lifecycle").equals("HIDDEN") ? 1.0 : 0.38);
            e.addProperty("pubRightsOp", pub ? 1.0 : 0.38);
            e.addProperty("noEdit", !edit);
            vm.add("d", e);
        }
        // ---- confirm box
        JsonObject dl = st.has("dlg") && st.get("dlg").isJsonObject() ? st.getAsJsonObject("dlg") : new JsonObject();
        boolean dlg = !str(dl, "kind").isEmpty();
        vm.addProperty("dlg", dlg);
        if (dlg) {
            vm.addProperty("dlgTitle", str(dl, "title"));
            vm.addProperty("dlgText", str(dl, "text"));
            vm.addProperty("dlgLabel", str(dl, "label"));
            String cls = str(dl, "cls");
            vm.addProperty("dlgBg", cls.equals("no") ? "#6b2328" : cls.equals("warn") ? "#6b5520" : "#1f6b3a");
            vm.addProperty("dlgBd", cls.equals("no") ? "#ff7b7b" : cls.equals("warn") ? "#ffd166" : "#6ee08a");
        }
        return vm;
    }
}
