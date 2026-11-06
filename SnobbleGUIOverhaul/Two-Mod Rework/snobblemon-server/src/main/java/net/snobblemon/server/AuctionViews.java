package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The Auction House. SnobbleAuctions owns the listings, prices, tax, slots and every purchase; this view keeps which tab / filter / page / listing the
 * player is on, asks the mod for that view, draws what comes back, runs the confirm windows and passes buy / sell / cancel / collect on to the mod,
 * which checks everything again. The cosmetic settings the web page had (theme, zoom, glow) are not part of the native screen.
 */
final class AuctionViews {
    private AuctionViews() {}

    static final String PAGE = "auction";
    private static final String CHANNEL = "snobbleauctions";
    private static final long UNDER = 50_000;
    private static final String[] STAT = {"HP", "ATK", "DEF", "SPA", "SPD", "SPE"};
    private static final String[][] MSORTS = {{"newest", "Newest"}, {"price_asc", "Price: low to high"}, {"price_desc", "Price: high to low"}, {"level", "Level"}, {"iv", "IV total"}};
    private static final String[][] ISORTS = {{"newest", "Newest"}, {"price_asc", "Price: low to high"}, {"price_desc", "Price: high to low"}, {"unit", "Price per item"}, {"name", "Name"}};
    private static final String[][] CATS = {{"", "All"}, {"tools", "Tools"}, {"blocks", "Blocks"}, {"food", "Food"}, {"cobblemon", "Cobblemon"}, {"misc", "Other"}};
    private static final String[][] FILTERS = {{"shiny", "Shiny only"}, {"legendary", "Legendary / Mythical"}, {"hiddenAbility", "Hidden ability"}, {"perfect5", "5+ perfect IVs"}, {"under", "Under 50,000"}};

    /** Last answer from the mod per player: header, browse, detail, mine, sell (pokemon), selli (items). */
    private static final Map<UUID, JsonObject> CACHE = new ConcurrentHashMap<>();

    static void register() {
        SnobbleUi.registerView(PAGE, AuctionViews::apply);
    }

    private static JsonArray arr(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }
    private static JsonObject obj(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null; }
    private static String money(long n) { return "$ " + String.format(Locale.US, "%,d", n); }
    private static String digits(String s) { return s == null ? "" : s.replaceAll("[^0-9]", ""); }
    private static long parse(String s) { String d = digits(s); if (d.isEmpty() || d.length() > 12) return d.isEmpty() ? 0 : Long.MAX_VALUE / 4; return Long.parseLong(d); }
    private static String pretty(String s) {
        if (s == null || s.isEmpty()) return "";
        int c = s.indexOf(':');
        if (c >= 0) s = s.substring(c + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : s.replace('_', ' ').split(" ")) if (!w.isEmpty()) sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return sb.toString();
    }
    private static String gender(String g) { return "MALE".equals(g) ? "Male" : "FEMALE".equals(g) ? "Female" : "Genderless"; }

    private static String left(long ms) {
        long t = Math.max(0, (ms - System.currentTimeMillis()) / 1000);
        if (t <= 0) return "expired";
        long d = t / 86400, h = t % 86400 / 3600, m = t % 3600 / 60;
        if (d > 0) return d + "d " + h + "h left";
        if (h > 0) return h + "h " + m + "m left";
        return Math.max(1, m) + "m left";
    }

    private static JsonObject fresh() {
        JsonObject st = new JsonObject();
        st.addProperty("view", "browse");
        st.addProperty("tab", "mon");
        st.addProperty("text", "");
        for (String[] f : FILTERS) st.addProperty(f[0], false);
        st.addProperty("sort", "newest");
        st.addProperty("page", 0);
        st.addProperty("cat", "");
        st.addProperty("mod", "");
        st.addProperty("isort", "newest");
        st.addProperty("sellSlot", -1);
        st.addProperty("price", "");
        st.addProperty("itemSlot", -1);
        st.addProperty("iq", 1);
        st.addProperty("imode", "all");
        st.addProperty("iprice", "");
        st.addProperty("localSeq", 0);
        return st;
    }

    private static void ask(ServerPlayer p, JsonObject st) {
        JsonObject m = new JsonObject();
        boolean item = str(st, "tab").equals("item");
        switch (str(st, "view")) {
            case "browse" -> {
                m.addProperty("action", "browse");
                m.addProperty("text", str(st, "text"));
                m.addProperty("page", (int) num(st, "page"));
                if (item) {
                    m.addProperty("kind", "item");
                    m.addProperty("category", str(st, "cat"));
                    m.addProperty("mod", str(st, "mod"));
                    m.addProperty("maxPrice", 0);
                    m.addProperty("sort", str(st, "isort"));
                } else {
                    m.addProperty("shiny", bool(st, "shiny"));
                    m.addProperty("legendary", bool(st, "legendary"));
                    m.addProperty("hiddenAbility", bool(st, "hiddenAbility"));
                    m.addProperty("minPerfectIvs", bool(st, "perfect5") ? 5 : 0);
                    m.addProperty("maxPrice", bool(st, "under") ? UNDER : 0);
                    m.addProperty("sort", str(st, "sort"));
                }
            }
            case "sell" -> { m.addProperty("action", "sellview"); if (item) m.addProperty("kind", "item"); }
            default -> m.addProperty("action", "mine");
        }
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static void send(ServerPlayer p, String action, String k, Object v) {
        JsonObject m = new JsonObject();
        m.addProperty("action", action);
        if (k != null) {
            if (v instanceof Number n) m.addProperty(k, n); else m.addProperty(k, String.valueOf(v));
        }
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static void toast(JsonObject data, String text, boolean ok) {
        JsonObject t = new JsonObject();
        t.addProperty("text", text);
        t.addProperty("ok", ok);
        data.add("_toast", t);
    }

    private static void clearLocal(JsonObject st, String... names) {
        JsonObject lo = new JsonObject();
        JsonObject vals = new JsonObject();
        for (String n : names) vals.addProperty(n, "");
        st.addProperty("localSeq", num(st, "localSeq") + 1);
        lo.addProperty("seq", num(st, "localSeq"));
        lo.add("vals", vals);
        st.add("_lo", lo);
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : fresh();
        JsonObject c = CACHE.computeIfAbsent(p.getUUID(), u -> new JsonObject());
        switch (event) {
            case "state" -> {
                String v = str(s, "view");
                if (s.has("balance")) {
                    for (String k : new String[]{"balance", "slotsUsed", "slotsMax", "taxRate", "maxPrice", "canSell", "buyOwn", "cancelDelay", "itemMaxPrice", "itemMinEach", "itemTaxRate", "allowItems", "title"})
                        if (s.has(k)) c.add("h_" + k, s.get(k));
                }
                if (s.has("prefs") && s.get("prefs").isJsonObject()) {
                    boolean first = !c.has("h_prefs");
                    c.add("h_prefs", s.getAsJsonObject("prefs").deepCopy());
                    if (first) setZoom(st, (int) Math.max(60, Math.min(160, num(prefs(c), "zoom"))));
                }
                if (!v.isEmpty()) c.add("v_" + v + (v.equals("sell") && str(s, "kind").equals("item") ? "i" : ""), s);
                if (v.equals("detail") && obj(s, "listing") != null && str(st, "confirm").isEmpty()) st.addProperty("detailOpen", true);
            }
            case "result" -> {
                if (!str(s, "message").isEmpty()) toast(data, str(s, "message"), bool(s, "ok"));
                String a = str(s, "action");
                if (a.equals("buy") && bool(s, "ok")) { st.addProperty("sweepAt", System.currentTimeMillis()); }          // the page keeps the panel open ~0.7 s for the gold sweep + sparks, then closes it (see "tick")
                else if (a.equals("buy")) { st.addProperty("confirm", ""); st.addProperty("detailOpen", false); st.remove("detailId"); ask(p, st); }
                if (a.equals("sell") && bool(s, "ok")) { st.addProperty("sellSlot", -1); st.addProperty("itemSlot", -1); st.addProperty("price", ""); st.addProperty("iprice", ""); clearLocal(st, "price", "iprice"); ask(p, st); }
                if (a.equals("sell") || a.equals("cancel")) st.addProperty("confirm", "");
                if (a.equals("detail") && !bool(s, "ok")) { st.addProperty("detailOpen", false); st.remove("detailId"); }
                if ((a.equals("cancel") || a.equals("collect")) && str(st, "view").equals("mine")) ask(p, st);
                if (a.equals("sellitem") && bool(s, "ok")) { st.addProperty("itemSlot", -1); st.addProperty("iprice", ""); clearLocal(st, "iprice"); ask(p, st); }
            }
            case "msg" -> message(p, st, c, data, s);
            default -> { return; }
        }
        data.add("st", st);
        if (st.has("_lo")) { data.add("_local", st.remove("_lo")); }
        JsonObject vm = build(st, c);
        decorate(vm, st, c);
        data.add("vm", vm);
    }

    // ------------------------------------------------------------ clicks

    private static void message(ServerPlayer p, JsonObject st, JsonObject c, JsonObject data, JsonObject m) {
        String a = str(m, "action"), k = str(m, "k");
        boolean refresh = false;
        switch (a) {
            case "nav" -> { st.addProperty("view", k); st.addProperty("detailOpen", false); st.addProperty("confirm", ""); st.addProperty("page", 0); refresh = true; }
            case "tab" -> { if (!k.equals(str(st, "tab"))) { st.addProperty("tab", k); st.addProperty("page", 0); st.addProperty("itemSlot", -1); st.addProperty("detailOpen", false); refresh = true; } }
            case "search" -> { String q = str(m, "q"); if (!q.equals(str(st, "text"))) { st.addProperty("text", q.length() > 40 ? q.substring(0, 40) : q); st.addProperty("page", 0); refresh = true; } }
            case "flt" -> { for (String[] f : FILTERS) if (f[0].equals(k)) { st.addProperty(k, !bool(st, k)); st.addProperty("page", 0); refresh = true; } }
            case "sort" -> { st.addProperty(str(st, "tab").equals("item") ? "isort" : "sort", k); st.addProperty("page", 0); refresh = true; }
            case "cat" -> { st.addProperty("cat", k); st.addProperty("page", 0); refresh = true; }
            case "mod" -> {
                JsonObject b = obj(c, "v_browse");
                JsonArray mods = b == null ? new JsonArray() : arr(b, "mods");
                String cur = str(st, "mod"), next = "";
                if (cur.isEmpty()) { if (!mods.isEmpty()) next = str(mods.get(0).getAsJsonObject(), "mod"); }
                else for (int i = 0; i < mods.size(); i++) if (str(mods.get(i).getAsJsonObject(), "mod").equals(cur)) { next = i + 1 < mods.size() ? str(mods.get(i + 1).getAsJsonObject(), "mod") : ""; break; }
                st.addProperty("mod", next); st.addProperty("page", 0); refresh = true;
            }
            case "page" -> { st.addProperty("page", Math.max(0, (int) num(st, "page") + (int) num(m, "d"))); refresh = true; }
            case "detail" -> { st.addProperty("detailId", k); send(p, "detail", "id", k); }
            case "back" -> { st.addProperty("detailOpen", false); st.addProperty("confirm", ""); st.remove("detailId"); }
            case "buy" -> {
                JsonObject l = obj(obj(c, "v_detail") == null ? new JsonObject() : c.getAsJsonObject("v_detail"), "listing");
                if (l == null) return;
                long bal = num(c, "h_balance");
                boolean own = bool(l, "mine") && !bool(c, "h_buyOwn");
                if (own || bal < num(l, "price")) return;
                st.addProperty("confirm", "buy");
            }
            case "no" -> st.addProperty("confirm", st.has("detailOpen") && bool(st, "detailOpen") && str(st, "confirm").equals("buy") ? "" : "");
            case "yes" -> yes(p, st, c);
            case "sellsel" -> { st.addProperty("sellSlot", (int) num(m, "n")); st.addProperty("price", ""); clearLocal(st, "price"); }
            case "price" -> st.addProperty("price", digits(str(m, "v")));
            case "isel" -> { st.addProperty("itemSlot", (int) num(m, "n")); st.addProperty("iprice", ""); st.addProperty("iq", 0); clearLocal(st, "iprice"); }
            case "qty" -> {
                JsonObject it = invItem(c, st);
                if (it != null) {
                    long have = num(it, "count");
                    long q = k.equals("all") ? have : num(st, "iq") + (int) num(m, "d");
                    st.addProperty("iq", Math.max(1, Math.min(have, q)));
                }
            }
            case "imode" -> st.addProperty("imode", k.equals("each") ? "each" : "all");
            case "iprice" -> st.addProperty("iprice", digits(str(m, "v")));
            case "list" -> {
                if (listProblem(c, st) == null && str(st, "tab").equals("mon") && sellParty(c, st) != null) st.addProperty("confirm", "sell");
            }
            case "ilist" -> {
                if (itemProblem(c, st) == null && invItem(c, st) != null) st.addProperty("confirm", "sellitem");
            }
            case "cancelask" -> { st.addProperty("cancelId", k); st.addProperty("confirm", "cancel"); }
            case "collect" -> send(p, "collect", "id", k);
            case "mineact" -> { if (str(m, "a").equals("collect")) send(p, "collect", "id", k); else { st.addProperty("cancelId", k); st.addProperty("confirm", "cancel"); } }
            case "settings" -> st.addProperty("settings", k.equals("open"));
            case "pref" -> pref(p, st, c, k, str(m, "v"));
            case "tick" -> {
                if (st.has("sweepAt") && System.currentTimeMillis() - (long) AscViews.num(st, "sweepAt") >= 700) { st.addProperty("confirm", ""); st.addProperty("detailOpen", false); st.remove("detailId"); refresh = true; }
                else if (!c.has("v_browse") && !c.has("h_balance")) refresh = true;
            }
            default -> { return; }
        }
        if (refresh) ask(p, st);
    }

    private static void yes(ServerPlayer p, JsonObject st, JsonObject c) {
        switch (str(st, "confirm")) {
            case "buy" -> {
                JsonObject d = c.has("v_detail") ? c.getAsJsonObject("v_detail") : null;
                JsonObject l = d == null ? null : obj(d, "listing");
                if (l != null) send(p, "buy", "id", str(l, "id"));
            }
            case "sell" -> {
                JsonObject pm = sellParty(c, st);
                if (pm != null) {
                    JsonObject m = new JsonObject();
                    m.addProperty("action", "sell");
                    m.addProperty("slot", (int) num(st, "sellSlot"));
                    m.addProperty("price", parse(str(st, "price")));
                    SnobbleUi.forward(p, CHANNEL, m);
                }
                st.addProperty("confirm", "");
            }
            case "sellitem" -> {
                JsonObject m = new JsonObject();
                m.addProperty("action", "sellitem");
                m.addProperty("slot", (int) num(st, "itemSlot"));
                m.addProperty("count", (int) num(st, "iq"));
                m.addProperty("price", itemTotal(st));
                SnobbleUi.forward(p, CHANNEL, m);
                st.addProperty("confirm", "");
            }
            case "cancel" -> { send(p, "cancel", "id", str(st, "cancelId")); st.addProperty("confirm", ""); }
            default -> { }
        }
    }

    private static JsonObject sellParty(JsonObject c, JsonObject st) {
        JsonObject v = c.has("v_sell") ? c.getAsJsonObject("v_sell") : null;
        if (v == null) return null;
        int slot = (int) num(st, "sellSlot");
        JsonArray party = arr(v, "party");
        for (JsonElement e : party) { JsonObject pm = e.getAsJsonObject(); if ((int) num(pm, "slot") == slot && !bool(pm, "empty") && !pm.has("blocked")) return pm; }
        return null;
    }

    private static JsonObject invItem(JsonObject c, JsonObject st) {
        JsonObject v = c.has("v_selli") ? c.getAsJsonObject("v_selli") : null;
        int slot = (int) num(st, "itemSlot");
        JsonArray inv = v == null ? new JsonArray() : arr(v, "inventory");
        if (slot < 0 || slot >= inv.size() || !inv.get(slot).isJsonObject()) return null;
        JsonObject it = inv.get(slot).getAsJsonObject();
        return bool(it, "empty") || it.has("blocked") || !it.has("name") ? null : it;
    }

    private static long itemTotal(JsonObject st) {
        long v = parse(str(st, "iprice"));
        return str(st, "imode").equals("each") ? v * Math.max(1, num(st, "iq")) : v;
    }

    private static String listProblem(JsonObject c, JsonObject st) {
        JsonObject pm = sellParty(c, st);
        if (pm == null) return "Pick a Pokemon";
        long v = parse(str(st, "price")), min = num(pm, "minPrice"), max = num(c, "h_maxPrice");
        if (v <= 0) return "Enter a price";
        if (v < min) return "Minimum price is " + money(min);
        if (v > max) return "Maximum price is " + money(max);
        if (num(c, "h_slotsUsed") >= num(c, "h_slotsMax")) return "All your listing slots are in use";
        if (!bool(c, "h_canSell")) return "You cannot sell right now";
        return null;
    }

    private static String itemProblem(JsonObject c, JsonObject st) {
        JsonObject it = invItem(c, st);
        if (it == null) return "Pick an item";
        long total = itemTotal(st), q = Math.max(1, num(st, "iq"));
        long min = Math.max(1, (c.has("h_itemMinEach") ? num(c, "h_itemMinEach") : 1) * q), max = c.has("h_itemMaxPrice") ? num(c, "h_itemMaxPrice") : num(c, "h_maxPrice");
        if (total <= 0) return "Enter a price";
        if (total < min) return "Minimum price is " + money(min);
        if (total > max) return "Maximum price is " + money(max);
        if (num(c, "h_slotsUsed") >= num(c, "h_slotsMax")) return "All your listing slots are in use";
        if (!bool(c, "h_canSell")) return "You cannot sell right now";
        return null;
    }

    // ------------------------------------------------------------ view model

    private static String spriteKey(JsonObject it) {
        String id = str(it, "speciesId");
        if (id.isEmpty()) id = str(it, "species");
        id = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
        if (id.isEmpty()) return "";
        boolean shiny = bool(it, "shiny") || str(it, "sprite").contains("shiny");
        // the page's own portrait (its sprites/atlas, shipped in this mod) when there is one, like the web page drew
        return SpriteService.portraitKey(str(it, "sprite"), str(it, "speciesId").isEmpty() ? id : str(it, "speciesId"), shiny);
    }

    private static void art(JsonObject o, JsonObject it) {
        boolean item = str(it, "kind").equals("ITEM") || it.has("itemId");
        o.addProperty("isItem", item);
        o.addProperty("item", item ? str(it, "itemId") : "minecraft:air");
        o.addProperty("sprite", item ? "" : spriteKey(it));
    }

    private static String tagLine(JsonObject it) {
        StringBuilder sb = new StringBuilder();
        if (bool(it, "shiny")) sb.append("SHINY ");
        if (bool(it, "legendary")) sb.append("LEGENDARY ");
        if (bool(it, "hiddenAbility")) sb.append("HA ");
        JsonArray asp = arr(it, "aspects");
        for (JsonElement e : asp) if (e.getAsString().equals("alpha")) sb.append("ALPHA ");
        if (it.has("perfect") && num(it, "perfect") > 0) sb.append(num(it, "perfect")).append("/6 IV");
        return sb.toString().trim();
    }

    private static String rarityColor(JsonObject it) {
        if (str(it, "kind").equals("ITEM")) {
            String r = str(it, "rarity").toUpperCase(Locale.ROOT);
            return r.equals("UNCOMMON") ? "#7AE08A" : r.equals("RARE") ? "#6AB4FF" : r.equals("EPIC") ? "#C58BFF" : "#C1C5CF";
        }
        if (bool(it, "legendary")) return "#FF6AD5";
        for (JsonElement e : arr(it, "aspects")) if (e.getAsString().equals("alpha")) return "#FF6AD5";
        if (bool(it, "shiny")) return "#FFD437";
        return num(it, "perfect") >= 5 ? "#C58BFF" : num(it, "perfect") >= 3 ? "#6AB4FF" : "#8FA0B3";
    }

    private static JsonObject card(JsonObject it) {
        JsonObject o = new JsonObject();
        boolean item = str(it, "kind").equals("ITEM");
        o.addProperty("id", str(it, "id"));
        art(o, it);
        o.addProperty("name", str(it, "name"));
        o.addProperty("color", rarityColor(it));
        o.addProperty("cnt", item && num(it, "count") > 1 ? "x" + num(it, "count") : "");
        o.addProperty("sub", item ? str(it, "modName") + (str(it, "highlight").isEmpty() ? "" : "  -  " + str(it, "highlight")) : "Lv. " + num(it, "level") + "  " + gender(str(it, "gender")));
        o.addProperty("tags", item ? "" : tagLine(it));
        o.addProperty("time", item && num(it, "count") > 1 ? money(Math.round((double) num(it, "price") / num(it, "count"))) + " each" : left(num(it, "expiresAt")));
        o.addProperty("price", money(num(it, "price")));
        o.addProperty("border", bool(it, "mine") ? "#4AD8F6" : "#2A4658");
        JsonArray ivs = arr(it, "ivs");
        String[] t = new String[8];
        java.util.Arrays.fill(t, "");
        if (item) {
            t[0] = str(it, "modName") + "  -  " + pretty(str(it, "rarity"));
            JsonArray lines = arr(it, "lines");
            for (int i = 0; i < Math.min(3, lines.size()); i++) t[1 + i] = lines.get(i).getAsString();
        } else {
            t[0] = str(it, "species") + "  -  Lv. " + num(it, "level") + "  -  " + gender(str(it, "gender"));
            t[1] = "Nature " + pretty(str(it, "nature")) + "   Ability " + pretty(str(it, "ability")) + (bool(it, "hiddenAbility") ? " (HA)" : "");
            if (!ivs.isEmpty()) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < Math.min(6, ivs.size()); i++) sb.append(STAT[i]).append(' ').append(ivs.get(i).getAsInt()).append("  "); t[2] = sb.toString().trim(); }
            JsonArray mv = arr(it, "moves");
            StringBuilder mvs = new StringBuilder();
            for (JsonElement e : mv) mvs.append(mvs.length() == 0 ? "" : ", ").append(e.getAsString());
            t[3] = mv.isEmpty() ? "" : "Moves " + mvs;
        }
        t[4] = "Seller " + str(it, "seller");
        t[5] = "Price " + money(num(it, "price"));
        t[6] = "Ends in " + left(num(it, "expiresAt")).replace(" left", "");
        t[7] = bool(it, "mine") ? "YOUR LISTING" : "CLICK TO VIEW & BUY";
        for (int i = 0; i < 8; i++) o.addProperty("t" + i, t[i]);
        return o;
    }

    private static JsonObject build(JsonObject st, JsonObject c) {
        JsonObject v = new JsonObject();
        String view = str(st, "view"), tab = str(st, "tab");
        boolean item = tab.equals("item");
        v.addProperty("title", c.has("h_title") ? str(c, "h_title") : "AUCTION HOUSE");
        v.addProperty("slots", num(c, "h_slotsUsed") + " / " + num(c, "h_slotsMax"));
        v.addProperty("slotsFull", num(c, "h_slotsUsed") >= num(c, "h_slotsMax") && num(c, "h_slotsMax") > 0);
        v.addProperty("balance", money(num(c, "h_balance")));
        v.addProperty("allowItems", !c.has("h_allowItems") || bool(c, "h_allowItems"));
        for (String n : new String[]{"browse", "sell", "mine"}) v.addProperty("nav_" + n, view.equals(n) ? "#E7B65C" : "#2A4658");
        v.addProperty("tab_mon", !item ? "#E7B65C" : "#2A4658");
        v.addProperty("tab_item", item ? "#E7B65C" : "#2A4658");
        v.addProperty("isBrowse", view.equals("browse"));
        v.addProperty("isSellMon", view.equals("sell") && !item);
        v.addProperty("isSellItem", view.equals("sell") && item);
        v.addProperty("isMine", view.equals("mine"));
        v.addProperty("isItemTab", item);
        v.addProperty("isMonTab", !item);
        String confirm = str(st, "confirm");
        boolean detail = bool(st, "detailOpen") && c.has("v_detail") && obj(c.getAsJsonObject("v_detail"), "listing") != null;
        long swAt = st.has("sweepAt") ? AscViews.num(st, "sweepAt") : 0, nowFx = System.currentTimeMillis();
        v.addProperty("sweepKey", swAt > 0 && nowFx - swAt < 700 ? "s" + swAt : "");
        v.addProperty("sparkKey", swAt > 0 && nowFx - swAt < 1400 ? "k" + swAt : "");
        v.addProperty("overlay", detail || !confirm.isEmpty());
        v.addProperty("showDetail", detail && !confirm.equals("buy") && !confirm.equals("cancel") && !confirm.equals("sell") && !confirm.equals("sellitem"));
        v.addProperty("showConfirm", !confirm.isEmpty());
        v.addProperty("hint", view.equals("sell")
                ? (item ? "Pick items from your inventory, choose how many and set a price. They leave your inventory until sold or taken back." : "Pick a Pokemon from your party, set a price and list it. It leaves your party until it sells or you take it back.")
                : "Your active listings, expired listings waiting to be collected, and your recent sales.");
        // browse rail
        JsonArray filters = new JsonArray();
        for (String[] f : FILTERS) { JsonObject o = new JsonObject(); o.addProperty("k", f[0]); o.addProperty("label", f[1]); o.addProperty("border", bool(st, f[0]) ? "#E7B65C" : "#2A4658"); o.addProperty("mark", bool(st, f[0]) ? "[x] " : "[ ] "); filters.add(o); }
        v.add("filters", filters);
        JsonArray sorts = new JsonArray();
        for (String[] sd : item ? ISORTS : MSORTS) { JsonObject o = new JsonObject(); o.addProperty("k", sd[0]); o.addProperty("label", sd[1]); o.addProperty("border", (item ? str(st, "isort") : str(st, "sort")).equals(sd[0]) ? "#E7B65C" : "#2A4658"); sorts.add(o); }
        v.add("sorts", sorts);
        JsonArray cats = new JsonArray();
        for (String[] ct : CATS) { JsonObject o = new JsonObject(); o.addProperty("k", ct[0].isEmpty() ? "-" : ct[0]); o.addProperty("label", ct[1]); o.addProperty("border", str(st, "cat").equals(ct[0]) ? "#E7B65C" : "#2A4658"); cats.add(o); }
        v.add("cats", cats);
        JsonObject b = c.has("v_browse") ? c.getAsJsonObject("v_browse") : null;
        String modName = "Any mod";
        if (b != null && !str(st, "mod").isEmpty()) for (JsonElement e : arr(b, "mods")) if (str(e.getAsJsonObject(), "mod").equals(str(st, "mod"))) modName = str(e.getAsJsonObject(), "name");
        v.addProperty("modLabel", "Mod: " + modName);
        // browse grid
        JsonArray rows = new JsonArray();
        JsonArray cells = null;
        int count = 0;
        if (b != null && view.equals("browse")) {
            for (JsonElement e : arr(b, "items")) {
                if (count % 4 == 0) { cells = new JsonArray(); JsonObject r = new JsonObject(); r.add("cells", cells); rows.add(r); }
                if (count >= 24) break;
                cells.add(card(e.getAsJsonObject()));
                count++;
            }
            while (cells != null && cells.size() < 4) { JsonObject e = new JsonObject(); e.addProperty("id", ""); e.addProperty("empty", true); cells.add(e); }
        }
        v.add("rows", rows);
        v.addProperty("noneText", count == 0 ? "No listings match. Try clearing a filter." : "");
        int pages = b == null ? 1 : (int) Math.max(1, num(b, "pages")), page = b == null ? 0 : (int) num(b, "page");
        v.addProperty("pageLabel", "PAGE " + (page + 1) + " / " + pages);
        v.addProperty("canPrev", page > 0);
        v.addProperty("canNext", page < pages - 1);
        // detail
        if (detail) v.add("d", detail(c, st));
        if (!confirm.isEmpty()) v.add("cf", confirmView(c, st, confirm));
        // sell (pokemon)
        buildSellMon(v, c, st);
        buildSellItem(v, c, st);
        buildMine(v, c, st);
        return v;
    }

    private static JsonObject detail(JsonObject c, JsonObject st) {
        JsonObject l = obj(c.getAsJsonObject("v_detail"), "listing");
        JsonObject d = new JsonObject();
        boolean item = str(l, "kind").equals("ITEM");
        art(d, l);
        d.addProperty("isMon", !item);
        d.addProperty("name", str(l, "name") + (!item && !str(l, "name").equals(str(l, "species")) ? " (" + str(l, "species") + ")" : ""));
        if (item) {
            d.addProperty("meta", str(l, "modName") + "  -  x" + num(l, "count") + (num(l, "count") > 1 ? "  -  " + money(Math.round((double) num(l, "price") / num(l, "count"))) + " each" : ""));
        } else {
            String f = str(l, "form");
            String form = !f.isEmpty() && !f.equalsIgnoreCase("normal") && !f.equalsIgnoreCase("standard") ? " - " + f : "";
            d.addProperty("meta", "Lv. " + num(l, "level") + " - " + gender(str(l, "gender")) + " - " + pretty(str(l, "nature")) + " - " + pretty(str(l, "ability")) + form);
        }
        d.addProperty("tags", item ? str(l, "highlight") : tagLine(l));
        JsonArray ivs = new JsonArray();
        JsonArray src = arr(l, "ivs");
        for (int i = 0; i < Math.min(6, src.size()); i++) {
            JsonObject o = new JsonObject();
            int val = src.get(i).getAsInt();
            o.addProperty("stat", STAT[i]);
            o.addProperty("v", val);
            o.addProperty("color", val >= 31 ? "#FFD437" : "#4AD8F6");
            ivs.add(o);
        }
        d.add("ivs", ivs);
        JsonArray lines = new JsonArray();
        if (item) {
            JsonObject o0 = new JsonObject(); o0.addProperty("text", str(l, "name")); lines.add(o0);
            for (JsonElement e : arr(l, "lines")) { JsonObject o = new JsonObject(); o.addProperty("text", e.getAsString()); lines.add(o); }
        } else for (JsonElement e : arr(l, "moves")) { JsonObject o = new JsonObject(); o.addProperty("text", e.getAsString()); lines.add(o); }
        d.add("lines", lines);
        d.addProperty("extra", item ? "Item id: " + str(l, "itemId") : "Ball: " + pretty(str(l, "ball")) + (str(l, "ot").isEmpty() ? "" : "   OT: " + str(l, "ot")));
        d.addProperty("price", money(num(l, "price")));
        d.addProperty("time", "Listed by " + str(l, "seller") + " - " + left(num(l, "expiresAt")));
        boolean own = bool(l, "mine") && !bool(c, "h_buyOwn");
        boolean poor = num(c, "h_balance") < num(l, "price");
        d.addProperty("buyOk", !own && !poor);
        d.addProperty("buyText", own ? "YOUR LISTING" : poor ? "NOT ENOUGH MONEY" : "BUY NOW");
        return d;
    }

    private static JsonObject confirmView(JsonObject c, JsonObject st, String kind) {
        JsonObject o = new JsonObject();
        JsonArray math = new JsonArray();
        String title, text, yes = "CONFIRM", no = "CANCEL";
        switch (kind) {
            case "buy" -> {
                JsonObject l = obj(c.getAsJsonObject("v_detail"), "listing");
                long after = num(c, "h_balance") - num(l, "price");
                boolean item = str(l, "kind").equals("ITEM");
                title = "CONFIRM PURCHASE";
                text = "Pay " + money(num(l, "price")) + " for " + str(l, "name") + (item && num(l, "count") > 1 ? " x" + num(l, "count") : "") + "?";
                line(math, item ? "x" + num(l, "count") + " " + str(l, "name") + " from " + str(l, "seller") : "Lv. " + num(l, "level") + " " + str(l, "species") + " from " + str(l, "seller"));
                line(math, "Balance after purchase: " + money(after));
            }
            case "sell" -> {
                JsonObject pm = sellParty(c, st);
                long v = parse(str(st, "price")), tax = (long) Math.floor(v * (c.has("h_taxRate") ? c.get("h_taxRate").getAsDouble() : 0));
                title = "CONFIRM LISTING";
                text = pm == null ? "" : "List " + str(pm, "name") + " for " + money(v) + "?";
                yes = "LIST IT"; no = "BACK";
                if (pm != null) line(math, "Lv. " + num(pm, "level") + " " + str(pm, "species") + " - " + num(pm, "perfect") + "/6 IV");
                line(math, "Tax " + Math.round((c.has("h_taxRate") ? c.get("h_taxRate").getAsDouble() : 0) * 100) + "%: you receive " + money(v - tax) + " when it sells");
                line(math, "It leaves your party now and is held safely until it sells.");
                cancelNote(math, c);
            }
            case "sellitem" -> {
                JsonObject it = invItem(c, st);
                long total = itemTotal(st), q = Math.max(1, num(st, "iq"));
                double rate = c.has("h_itemTaxRate") ? c.get("h_itemTaxRate").getAsDouble() : c.has("h_taxRate") ? c.get("h_taxRate").getAsDouble() : 0;
                title = "CONFIRM LISTING";
                text = it == null ? "" : "List " + str(it, "name") + " x" + q + " for " + money(total) + "?";
                yes = "LIST IT"; no = "BACK";
                if (q > 1) line(math, money(Math.round((double) total / q)) + " each");
                line(math, "Tax " + Math.round(rate * 100) + "%: you receive " + money(total - (long) Math.floor(total * rate)) + " when it sells");
                line(math, "The items leave your inventory now and are held safely until they sell.");
                cancelNote(math, c);
            }
            default -> {
                JsonObject mine = c.has("v_mine") ? c.getAsJsonObject("v_mine") : null;
                JsonObject it = null;
                if (mine != null) for (JsonElement e : arr(mine, "items")) if (str(e.getAsJsonObject(), "id").equals(str(st, "cancelId"))) it = e.getAsJsonObject();
                boolean isItem = it != null && str(it, "kind").equals("ITEM");
                title = "CANCEL LISTING";
                text = it == null ? "Take this off the Auction House?" : "Take " + str(it, "name") + (isItem ? " x" + num(it, "count") : "") + " off the Auction House?";
                yes = "YES, CANCEL"; no = "KEEP IT";
                line(math, "It comes back to your " + (isItem ? "inventory." : "party, or your PC if the party is full."));
            }
        }
        o.addProperty("title", title);
        o.addProperty("text", text);
        o.add("math", math);
        o.addProperty("yes", yes);
        o.addProperty("no", no);
        return o;
    }

    private static void cancelNote(JsonArray math, JsonObject c) {
        long d = num(c, "h_cancelDelay");
        if (d > 0) line(math, "You can cancel it " + (d >= 60 ? Math.round(d / 60.0) + " min" : d + "s") + " after listing.");
    }

    private static void line(JsonArray a, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        a.add(o);
    }

    private static void buildSellMon(JsonObject v, JsonObject c, JsonObject st) {
        JsonObject sv = c.has("v_sell") ? c.getAsJsonObject("v_sell") : null;
        JsonArray party = new JsonArray();
        if (sv != null) for (JsonElement e : arr(sv, "party")) {
            JsonObject pm = e.getAsJsonObject();
            JsonObject o = new JsonObject();
            boolean empty = bool(pm, "empty");
            o.addProperty("n", (int) num(pm, "slot"));
            o.addProperty("ok", !empty && !pm.has("blocked"));
            o.addProperty("isEmpty", empty);
            o.addProperty("name", empty ? "(empty)" : str(pm, "name"));
            o.addProperty("meta", empty ? "" : "Lv. " + num(pm, "level") + " - " + num(pm, "perfect") + "/6 IV" + (pm.has("blocked") ? " - " + str(pm, "blocked") : ""));
            o.addProperty("sprite", empty ? "" : spriteKey(pm));
            o.addProperty("border", (int) num(st, "sellSlot") == (int) num(pm, "slot") && !empty ? "#E7B65C" : "#2A4658");
            o.addProperty("color", pm.has("blocked") ? "#FF7A6B" : "#E4ECF1");
            party.add(o);
        }
        v.add("party", party);
        JsonObject sel = sellParty(c, st);
        v.addProperty("sellPick", sel != null);
        if (sel != null) {
            JsonObject x = new JsonObject();
            x.addProperty("sprite", spriteKey(sel));
            x.addProperty("name", str(sel, "name"));
            x.addProperty("meta", "Lv. " + num(sel, "level") + " - " + gender(str(sel, "gender")) + " - " + pretty(str(sel, "nature")) + " - " + pretty(str(sel, "ability")));
            x.addProperty("tags", tagLine(sel));
            double rate = c.has("h_taxRate") ? c.get("h_taxRate").getAsDouble() : 0;
            long val = parse(str(st, "price"));
            String bad = listProblem(c, st);
            x.addProperty("tax", "Listing tax " + Math.round(rate * 100) + "%");
            x.addProperty("receive", "You receive " + money(val - (long) Math.floor(val * rate)));
            x.addProperty("help", bad != null && val > 0 ? bad : "Minimum " + money(num(sel, "minPrice")) + "   Maximum " + money(num(c, "h_maxPrice")));
            x.addProperty("helpColor", bad != null && val > 0 ? "#FF7A6B" : "#8FA0B3");
            x.addProperty("ok", bad == null);
            v.add("sm", x);
        }
    }

    private static void buildSellItem(JsonObject v, JsonObject c, JsonObject st) {
        JsonObject sv = c.has("v_selli") ? c.getAsJsonObject("v_selli") : null;
        JsonArray inv = sv == null ? new JsonArray() : arr(sv, "inventory");
        JsonArray rows = new JsonArray();
        int[] order = new int[36];
        int n = 0;
        for (int i = 9; i < 36; i++) order[n++] = i;
        for (int h = 0; h < 9; h++) order[n++] = h;
        int slotSel = (int) num(st, "itemSlot");
        JsonArray cells = null;
        for (int i = 0; i < 36; i++) {
            if (i % 9 == 0) { cells = new JsonArray(); JsonObject r = new JsonObject(); r.add("cells", cells); r.addProperty("gap", i == 27 ? 6 : 0); rows.add(r); }
            int slot = order[i];
            JsonObject it = slot < inv.size() && inv.get(slot).isJsonObject() ? inv.get(slot).getAsJsonObject() : null;
            boolean has = it != null && !bool(it, "empty") && it.has("name");
            JsonObject o = new JsonObject();
            o.addProperty("n", slot);
            o.addProperty("has", has);
            o.addProperty("item", has ? str(it, "itemId") : "minecraft:air");
            o.addProperty("cnt", has && num(it, "count") > 1 ? String.valueOf(num(it, "count")) : "");
            o.addProperty("name", has ? str(it, "name") + (it.has("blocked") ? " (" + str(it, "blocked") + ")" : "") : "");
            o.addProperty("border", slot == slotSel && has ? "#E7B65C" : has && it.has("blocked") ? "#5A2A2A" : "#2A4658");
            cells.add(o);
        }
        v.add("invRows", rows);
        JsonObject sel = invItem(c, st);
        v.addProperty("itemPick", sel != null);
        if (sel != null) {
            long have = num(sel, "count");
            long q = Math.max(1, Math.min(have, num(st, "iq") <= 0 ? have : num(st, "iq")));
            if (q != num(st, "iq")) st.addProperty("iq", q);
            double rate = c.has("h_itemTaxRate") ? c.get("h_itemTaxRate").getAsDouble() : c.has("h_taxRate") ? c.get("h_taxRate").getAsDouble() : 0;
            long total = itemTotal(st);
            String bad = itemProblem(c, st);
            JsonObject x = new JsonObject();
            x.addProperty("item", str(sel, "itemId"));
            x.addProperty("name", str(sel, "name"));
            x.addProperty("mod", str(sel, "modName"));
            x.addProperty("have", "You have " + have);
            x.addProperty("qty", String.valueOf(q));
            x.addProperty("modeAll", str(st, "imode").equals("all") ? "#E7B65C" : "#2A4658");
            x.addProperty("modeEach", str(st, "imode").equals("each") ? "#E7B65C" : "#2A4658");
            x.addProperty("total", "Total price " + money(total));
            x.addProperty("tax", "Listing tax " + Math.round(rate * 100) + "%");
            x.addProperty("receive", "You receive " + money(total - (long) Math.floor(total * rate)));
            x.addProperty("help", bad != null && total > 0 ? bad : str(st, "imode").equals("all") && q > 1 && total > 0 ? "= " + money(Math.round((double) total / q)) + " each" : "");
            x.addProperty("helpColor", bad != null && total > 0 ? "#FF7A6B" : "#8FA0B3");
            x.addProperty("ok", bad == null);
            v.add("si", x);
        }
    }

    private static void buildMine(JsonObject v, JsonObject c, JsonObject st) {
        JsonObject mv = c.has("v_mine") ? c.getAsJsonObject("v_mine") : null;
        JsonArray items = new JsonArray(), sales = new JsonArray();
        long now = System.currentTimeMillis();
        long delay = num(c, "h_cancelDelay") * 1000;
        if (mv != null) {
            for (JsonElement e : arr(mv, "items")) {
                JsonObject it = e.getAsJsonObject();
                boolean item = str(it, "kind").equals("ITEM");
                String state = str(it, "state");
                JsonObject o = new JsonObject();
                art(o, it);
                o.addProperty("id", str(it, "id"));
                o.addProperty("name", item ? str(it, "name") + "  x" + num(it, "count") : str(it, "name") + "  Lv. " + num(it, "level"));
                o.addProperty("price", money(num(it, "price")));
                String thing = item ? "items" : "Pokemon";
                boolean active = state.equals("ACTIVE");
                o.addProperty("status", active ? "On sale - " + left(num(it, "expiresAt")) : state.equals("EXPIRED") ? "Expired - collect your " + thing : "Cancelled - collect your " + thing);
                o.addProperty("statusColor", active ? "#62D06A" : "#FFB36A");
                long wait = active ? (num(it, "createdAt") + delay - now + 999) / 1000 : 0;
                o.addProperty("active", active);
                o.addProperty("canAct", !active || wait <= 0);
                o.addProperty("actText", active ? (wait > 0 ? "CANCEL (" + wait + "s)" : "CANCEL") : "COLLECT");
                items.add(o);
            }
            for (JsonElement e : arr(mv, "sales")) {
                JsonObject sl = e.getAsJsonObject();
                JsonObject o = new JsonObject();
                o.addProperty("text", str(sl, "name") + (str(sl, "kind").equals("ITEM") && num(sl, "count") > 1 ? " x" + num(sl, "count") : "") + " to " + str(sl, "buyer"));
                o.addProperty("gain", "+" + money(num(sl, "proceeds")) + " after tax");
                sales.add(o);
            }
        }
        v.add("mine", items);
        v.add("sales", sales);
        v.addProperty("noMine", items.isEmpty());
        v.addProperty("noSales", sales.isEmpty());
    }

    // ------------------------------------------------------------ the original page's look (theme palette, states, rarity, hover card)

    /** :root[data-theme] palettes from auction/style.css: bg1 bg2 panel edge acc input btnfg btnbg on hov spot txt dim grid(8-digit). */
    private static final Map<String, String[]> THEMES = new java.util.LinkedHashMap<>();
    static {
        THEMES.put("gray", new String[]{"Gray", "#0d1013", "#242c33", "#171d23", "#5d7280", "#cfe3f0", "#0b0e11", "#11181d", "#26323b", "#2b3842", "#1d252c", "#2c3943", "#e4ecf1", "#97a6b1", "#cfe3f00b"});
        THEMES.put("violet", new String[]{"Violet", "#0c0820", "#1d1442", "#150f33", "#6a4fd0", "#cdb4ff", "#090618", "#120a2c", "#271b5c", "#33237a", "#1d1445", "#3a2a86", "#efe9ff", "#a597d6", "#bea0ff0d"});
        THEMES.put("emerald", new String[]{"Emerald", "#071410", "#12362d", "#0d241e", "#2f9a7c", "#7ff0c6", "#061210", "#05231a", "#164537", "#1d5a48", "#123228", "#1f6a55", "#eafff6", "#86b9a8", "#7ff0c60d"});
        THEMES.put("crimson", new String[]{"Crimson", "#140709", "#3a1119", "#240c11", "#b0384c", "#ffb3bd", "#10050a", "#2b0509", "#4a1620", "#661d2b", "#331018", "#6a1f2e", "#fff0f2", "#c79199", "#ff96a50d"});
        THEMES.put("ocean", new String[]{"Ocean", "#060f1c", "#10305a", "#0b1e38", "#2e7fc4", "#9fd4ff", "#050c18", "#06182e", "#143a68", "#1b4d88", "#0f2a4c", "#1d5a9c", "#eaf5ff", "#8fb2d3", "#96cdff0d"});
        THEMES.put("amber", new String[]{"Amber", "#130d05", "#3a2a10", "#22180a", "#b98a32", "#ffd98a", "#100a03", "#2a1c02", "#4a3512", "#664a18", "#33260f", "#6a4d1a", "#fff6e4", "#c4a877", "#ffd78c0d"});
    }
    private static final String[] PKEYS = {"bg1", "bg2", "panel", "edge", "acc", "input", "btnfg", "btnbg", "on", "hov", "spot", "txt", "dim", "grid"};
    private static final String A_GREEN = "#4dffb0", A_GOLD = "#ffd24a", A_MAG = "#ff4fe0", A_BLUE = "#7cc0ff", A_PURPLE = "#b9a0ff";

    private static JsonObject prefs(JsonObject c) {
        JsonObject p = obj(c, "h_prefs");
        if (p == null) { p = new JsonObject(); c.add("h_prefs", p); }
        if (!p.has("theme") || !THEMES.containsKey(str(p, "theme"))) p.addProperty("theme", "gray");
        if (!p.has("zoom")) p.addProperty("zoom", 100);
        if (!p.has("glow")) p.addProperty("glow", false);
        if (!p.has("tips")) p.addProperty("tips", true);
        return p;
    }

    /** setPref(): applied here at once, and saved by SnobbleAuctions (setprefs) like the web page did. */
    private static void pref(ServerPlayer p, JsonObject st, JsonObject c, String k, String v) {
        JsonObject pr = prefs(c);
        JsonObject m = new JsonObject();
        m.addProperty("action", "setprefs");
        switch (k) {
            case "theme" -> { if (!THEMES.containsKey(v)) return; pr.addProperty("theme", v); m.addProperty("theme", v); }
            case "zoom" -> {
                int z = (int) Math.max(60, Math.min(160, num(pr, "zoom") + Integer.parseInt(v.matches("-?[0-9]{1,3}") ? v : "0")));
                pr.addProperty("zoom", z);
                m.addProperty("zoom", z);
                setZoom(st, z);
            }
            case "glow", "tips" -> { boolean b = !bool(pr, k); pr.addProperty(k, b); m.addProperty(k, b); }
            default -> { return; }
        }
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static void setZoom(JsonObject st, int z) {
        JsonObject lo = st.has("_lo") ? st.getAsJsonObject("_lo") : new JsonObject();
        JsonObject vals = lo.has("vals") ? lo.getAsJsonObject("vals") : new JsonObject();
        vals.addProperty("zoompct", z);
        st.addProperty("localSeq", num(st, "localSeq") + 1);
        lo.addProperty("seq", num(st, "localSeq"));
        lo.add("vals", vals);
        st.add("_lo", lo);
    }

    private static JsonObject ui(boolean on, String[] t) {
        // #topTabs button / .on: bg btnbg|on, inset 2px edge|acc, colour dim|acc; hover bg on
        JsonObject o = new JsonObject();
        o.addProperty("bg", on ? t[9] : t[8]);
        o.addProperty("hover", t[9]);
        o.addProperty("border", on ? t[5] : t[4]);
        o.addProperty("color", on ? t[5] : t[13]);
        return o;
    }

    /** rarityOf(): {colour class, label}. */
    private static String[] rarity(JsonObject it, String edge) {
        if (str(it, "kind").equals("ITEM")) {
            String r = str(it, "rarity").toUpperCase(Locale.ROOT);
            String lab = switch (str(it, "category")) { case "tools" -> "Tool"; case "blocks" -> "Block"; case "food" -> "Food"; case "cobblemon" -> "Cobblemon"; default -> "Item"; };
            return new String[]{r.equals("UNCOMMON") ? A_GREEN : r.equals("RARE") ? A_BLUE : r.equals("EPIC") ? A_PURPLE : edge, lab};
        }
        for (JsonElement e : arr(it, "aspects")) if (e.getAsString().equals("alpha")) return new String[]{A_MAG, "Alpha"};
        if (bool(it, "legendary")) return new String[]{A_MAG, "Legendary"};
        if (bool(it, "shiny")) return new String[]{A_GOLD, "Shiny"};
        long pf = num(it, "perfect");
        return new String[]{pf >= 5 ? A_PURPLE : pf >= 3 ? A_BLUE : edge, "Pokemon"};
    }

    /** tags(): IV count, ALPHA, BOSS, SHINY, LEGENDARY, HA. */
    private static JsonArray tagList(JsonObject it, String edge, String dim) {
        JsonArray a = new JsonArray();
        java.util.function.BiConsumer<String, String> add = (t, col) -> { JsonObject o = new JsonObject(); o.addProperty("text", t); o.addProperty("color", col); a.add(o); };
        if (it.has("perfect")) add.accept(num(it, "perfect") + "/6 IV", A_GREEN);
        for (JsonElement e : arr(it, "aspects")) if (e.getAsString().equals("alpha")) add.accept("ALPHA", A_MAG);
        for (JsonElement e : arr(it, "aspects")) if (e.getAsString().contains("boss")) { add.accept("BOSS", A_MAG); break; }
        if (bool(it, "shiny")) add.accept("SHINY", A_GOLD);
        if (bool(it, "legendary")) add.accept("LEGENDARY", A_MAG);
        if (bool(it, "hiddenAbility")) add.accept("HA", dim);
        return a;
    }

    private static JsonObject tl(String kind, String text, String color) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("text", text);
        o.addProperty("color", color);
        return o;
    }

    /** showTip(): the hover card's lines. */
    private static JsonArray tipLines(JsonObject it, String[] rc, String[] t) {
        JsonArray a = new JsonArray();
        boolean isItem = str(it, "kind").equals("ITEM");
        a.add(tl("t", str(it, "name"), rc[0].equals(t[4]) ? t[5] : rc[0]));
        if (isItem) {
            String rar = str(it, "rarity").isEmpty() ? "COMMON" : str(it, "rarity");
            a.add(tl("mod", str(it, "modName") + "  -  " + rar.charAt(0) + rar.substring(1).toLowerCase(Locale.ROOT) + "  -  " + rc[1], ""));
            JsonArray lines = arr(it, "lines");
            if (!lines.isEmpty()) a.add(tl("hr", "", ""));
            for (int i = 0; i < Math.min(9, lines.size()); i++) {
                String x = lines.get(i).getAsString();
                a.add(tl("line", x, x.matches("^[A-Z].* [IVX]+$") ? A_PURPLE : "#aeb8c0"));
            }
            if (lines.size() > 9) a.add(tl("kv", "+ " + (lines.size() - 9) + " more lines", t[13]));
        } else {
            String f = str(it, "form");
            String form = !f.isEmpty() && !f.equalsIgnoreCase("normal") && !f.equalsIgnoreCase("standard") ? " - " + f : "";
            a.add(tl("mod", str(it, "species") + form + "  -  Lv. " + num(it, "level") + "  -  " + gender(str(it, "gender")), ""));
            a.add(tl("hr", "", ""));
            if (!str(it, "nature").isEmpty()) a.add(tl("kv", "Nature  **" + pretty(str(it, "nature")) + "**", t[12]));
            if (!str(it, "ability").isEmpty()) a.add(tl("kv", "Ability  **" + pretty(str(it, "ability")) + (bool(it, "hiddenAbility") ? " (HA)" : "") + "**", t[12]));
            JsonArray ivs = arr(it, "ivs");
            for (int r = 0; r + 2 < Math.min(6, ivs.size()) + 1 && r < 6; r += 3) {
                JsonObject o = tl("iv", "", "");
                String[] keys = {"a", "b", "c"};
                for (int j = 0; j < 3 && r + j < ivs.size(); j++) {
                    int v = ivs.get(r + j).getAsInt();
                    o.addProperty(keys[j], STAT[r + j] + " **" + v + "**");
                    o.addProperty(keys[j] + "c", v >= 31 ? A_GREEN : t[12]);
                }
                a.add(o);
            }
            StringBuilder mv = new StringBuilder();
            for (JsonElement e : arr(it, "moves")) mv.append(mv.length() == 0 ? "" : ", ").append(e.getAsString());
            if (mv.length() > 0) a.add(tl("kv", "Moves  **" + mv + "**", t[12]));
        }
        a.add(tl("hr", "", ""));
        a.add(tl("kv", "Seller  **" + str(it, "seller") + "**", t[12]));
        if (isItem && num(it, "count") > 1) a.add(tl("kv", "Price each  **" + money(Math.round((double) num(it, "price") / num(it, "count"))) + "**", A_GOLD));
        a.add(tl("kv", (isItem && num(it, "count") > 1 ? "Total" : "Price") + "  **" + money(num(it, "price")) + "**", A_GOLD));
        a.add(tl("kv", "Ends in  **" + left(num(it, "expiresAt")).replace(" left", "") + "**", t[12]));
        a.add(tl("hint", bool(it, "mine") ? "YOUR LISTING" : "CLICK TO VIEW & BUY", A_GREEN));
        return a;
    }

    private static void decorate(JsonObject v, JsonObject st, JsonObject c) {
        JsonObject pr = prefs(c);
        String th = str(pr, "theme");
        String[] t = THEMES.get(th);
        JsonObject p = new JsonObject();
        for (int i = 0; i < PKEYS.length; i++) p.addProperty(PKEYS[i], t[i + 1]);
        v.add("p", p);
        v.addProperty("artTex", "tex:auction/art_" + th + ".png");
        v.addProperty("tips", bool(pr, "tips"));
        String view = str(st, "view"), tab = str(st, "tab");
        boolean item = tab.equals("item");
        String tn = item ? " - ITEMS" : "";
        v.addProperty("crumb", view.equals("browse") ? "/ LIVE LISTINGS" + tn : view.equals("sell") ? "/ SELL" + tn : "/ MY LISTINGS");
        v.addProperty("slotsColor", bool(v, "slotsFull") ? A_MAG : t[12]);
        v.addProperty("searchHint", item ? "item or mod name..." : "species, move, ability...");
        v.add("tBrowse", ui(view.equals("browse"), t));
        v.add("tSell", ui(view.equals("sell"), t));
        v.add("tMine", ui(view.equals("mine"), t));
        v.add("tMon", ui(!item, t));
        v.add("tItem", ui(item, t));
        v.addProperty("viewKey", view + "|" + tab);
        // grid lines of the window background (40px)
        JsonArray gx = new JsonArray(), gy = new JsonArray();
        for (int x = 0; x <= 1200; x += 40) { JsonObject o = new JsonObject(); o.addProperty("x", x); gx.add(o); }
        for (int y = 0; y <= 720; y += 40) { JsonObject o = new JsonObject(); o.addProperty("y", y); gy.add(o); }
        v.add("gx", gx);
        v.add("gy", gy);
        // rail
        for (JsonElement e : arr(v, "filters")) { JsonObject o = e.getAsJsonObject(); o.addProperty("on", bool(st, str(o, "k"))); }
        String cur = item ? str(st, "isort") : str(st, "sort");
        for (JsonElement e : arr(v, "sorts")) {
            JsonObject o = e.getAsJsonObject();
            boolean on = str(o, "k").equals(cur);
            o.addProperty("bg", on ? t[9] : "#00000000");
            o.addProperty("hover", on ? t[9] : t[10]);
            o.addProperty("color", on ? t[5] : t[12]);
        }
        String[] icons = {"minecraft:chest", "minecraft:iron_pickaxe", "minecraft:grass_block", "minecraft:bread", "cobblemon:poke_ball", "minecraft:ender_pearl"};
        int ci = 0;
        for (JsonElement e : arr(v, "cats")) {
            JsonObject o = e.getAsJsonObject();
            boolean on = (str(o, "k").equals("-") ? "" : str(o, "k")).equals(str(st, "cat"));
            o.addProperty("icon", icons[Math.min(ci++, icons.length - 1)]);
            o.addProperty("bg", on ? t[9] : t[8]);
            o.addProperty("border", on ? t[5] : t[4]);
            o.addProperty("color", on ? t[5] : t[13]);
        }
        // browse cards (flat; the grid places them 4 per row)
        JsonArray cards = new JsonArray();
        JsonObject b = c.has("v_browse") ? c.getAsJsonObject("v_browse") : null;
        StringBuilder sig = new StringBuilder(view + "|" + tab + "|");
        if (b != null && view.equals("browse")) {
            int i = 0;
            for (JsonElement e : arr(b, "items")) {
                if (i >= 8) break;
                JsonObject it = e.getAsJsonObject();
                JsonObject o = card(it);
                String[] rc = rarity(it, t[4]);
                o.addProperty("rc", rc[0]);
                o.addProperty("label", rc[1]);
                o.addProperty("glow", bool(pr, "glow") && !rc[0].equals(t[4]) ? "2," + rc[0] + "8c" : "0,#00000000");
                o.addProperty("delay", i * 28);
                o.add("tags", tagList(it, t[4], t[13]));
                o.add("tip", tipLines(it, rc, t));
                sig.append(str(it, "id")).append(',');
                cards.add(o);
                i++;
            }
            sig.append(num(b, "page"));
        }
        v.add("cards", cards);
        v.addProperty("sig", sig.toString());
        v.addProperty("noneText", b != null && view.equals("browse") && cards.isEmpty());
        // sell
        for (JsonElement e : arr(v, "party")) {
            JsonObject o = e.getAsJsonObject();
            boolean sel = (int) num(st, "sellSlot") == (int) num(o, "n") && !bool(o, "isEmpty");
            o.addProperty("bg", sel ? t[9] : "#00000000");
            o.addProperty("hover", sel ? t[9] : t[10]);
            o.addProperty("border", sel ? t[5] : "#00000000");
            o.addProperty("op", bool(o, "ok") || bool(o, "isEmpty") ? 1 : 0.45);
            if (bool(o, "isEmpty")) o.addProperty("color", t[13]);
        }
        JsonObject sm = obj(v, "sm");
        if (sm != null) { JsonObject pm = sellParty(c, st); sm.add("tags", pm == null ? new JsonArray() : tagList(pm, t[4], t[13])); }
        int slotSel = (int) num(st, "itemSlot");
        for (JsonElement r : arr(v, "invRows")) for (JsonElement e : arr(r.getAsJsonObject(), "cells")) {
            JsonObject o = e.getAsJsonObject();
            boolean sel = (int) num(o, "n") == slotSel && bool(o, "has");
            boolean blk = str(o, "name").endsWith(")") && str(o, "border").equalsIgnoreCase("#5A2A2A");
            o.addProperty("bg", sel ? t[9] : t[6]);
            o.addProperty("border", sel ? t[5] : t[4] + "99");
            o.addProperty("op", blk ? 0.35 : 1);
        }
        JsonObject si = obj(v, "si");
        if (si != null) {
            boolean all = str(st, "imode").equals("all");
            si.addProperty("allBg", all ? t[9] : "#00000000");
            si.addProperty("allColor", all ? t[5] : t[12]);
            si.addProperty("eachBg", !all ? t[9] : "#00000000");
            si.addProperty("eachColor", !all ? t[5] : t[12]);
            si.addProperty("totalVal", str(si, "total").replace("Total price ", ""));
        }
        for (JsonElement e : arr(v, "mine")) {
            JsonObject o = e.getAsJsonObject();
            o.addProperty("act", bool(o, "active") ? "cancelask" : "collect");
            o.addProperty("statusColor", bool(o, "active") ? A_GREEN : A_GOLD);
        }
        for (JsonElement e : arr(v, "sales")) {
            JsonObject o = e.getAsJsonObject();
            o.addProperty("text", str(o, "text") + " **" + str(o, "gain") + "**");
        }
        JsonObject d = obj(v, "d");
        if (d != null) {
            JsonObject l = obj(c.getAsJsonObject("v_detail"), "listing");
            d.add("tags", tagList(l, t[4], t[13]));
            d.addProperty("isItem", str(l, "kind").equals("ITEM"));
            for (JsonElement e : arr(d, "ivs")) { JsonObject o = e.getAsJsonObject(); o.addProperty("color", num(o, "v") >= 31 ? A_GREEN : t[5]); }
            JsonArray ln = arr(d, "lines");
            for (int i = 0; i < ln.size(); i++) {
                JsonObject o = ln.get(i).getAsJsonObject();
                String x = str(o, "text");
                o.addProperty("color", str(l, "kind").equals("ITEM") ? (i == 0 ? t[5] : x.matches("^[A-Z].* [IVX]+$") ? A_PURPLE : t[12]) : t[12]);
            }
        }
        // settings
        v.addProperty("settings", bool(st, "settings"));
        JsonArray themes = new JsonArray();
        for (var en : THEMES.entrySet()) {
            JsonObject o = new JsonObject();
            String[] x = en.getValue();
            o.addProperty("k", en.getKey());
            o.addProperty("name", x[0]);
            o.addProperty("c0", x[2]); o.addProperty("c1", x[4]); o.addProperty("c2", x[5]);
            o.addProperty("border", en.getKey().equals(th) ? t[5] : t[4]);
            themes.add(o);
        }
        v.add("themes", themes);
        for (String k : new String[]{"glow", "tips"}) {
            boolean on = bool(pr, k);
            v.addProperty(k + "Border", on ? t[5] : t[4]);
            v.addProperty(k + "X", on ? 32 : 4);
            v.addProperty(k + "Knob", on ? t[5] : t[13]);
        }
    }

}
