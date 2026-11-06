package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The SnobbleGems shop. The Gems mod holds the balance, prices, limits and the purchase; this view assembles its (chunked) shop_state, keeps the
 * chosen category, page and item here, shows the confirm window for a quote, and answers "quote", "confirm" and "cancel" back to the mod, which
 * checks price and funds again before anything is taken.
 */
final class GemsShopViews {
    private GemsShopViews() {}

    static final String PAGE = "snobblegems_shop";
    private static final String CHANNEL = "snobblegems";
    private static final int PER = 10;
    private static final Map<UUID, JsonObject> SHOP = new ConcurrentHashMap<>();          // assembled state: header + products
    private static final Map<UUID, JsonObject> PARTS = new ConcurrentHashMap<>();         // chunks being collected

    static void register() {
        SnobbleUi.registerView(PAGE, GemsShopViews::apply);
    }

    private static JsonArray arr(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    private static String n3(long n) { return String.format(Locale.US, "%,d", n); }

    private static void kind(ServerPlayer p, String kind, JsonObject extra) {
        JsonObject m = extra == null ? new JsonObject() : extra;
        m.addProperty("kind", kind);
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : new JsonObject();
        long now = System.currentTimeMillis();
        switch (event) {
            case "shop_state" -> {
                if (!assemble(p, s)) return;
            }
            case "shop_quote" -> {
                if (bool(s, "ok")) {
                    JsonObject q = new JsonObject();
                    for (String k : new String[]{"product", "token", "name", "color"}) q.addProperty(k, str(s, k));
                    for (String k : new String[]{"price", "balance", "after"}) q.addProperty(k, AscViews.num(s, k));
                    q.addProperty("until", now + (s.has("seconds") ? AscViews.num(s, "seconds") : 30) * 1000);
                    st.add("quote", q);
                } else {
                    st.remove("quote");
                    toast(data, str(s, "message").isEmpty() ? "Cannot buy that." : str(s, "message"), false);
                }
            }
            case "shop_result" -> {
                st.remove("quote");
                JsonObject shop = SHOP.get(p.getUUID());
                if (shop != null && s.has("balance")) shop.addProperty("balance", AscViews.num(s, "balance"));
                toast(data, str(s, "message").isEmpty() ? (bool(s, "ok") ? "Purchased." : "Purchase failed.") : str(s, "message"), bool(s, "ok"));
                if (bool(s, "ok")) {
                    st.addProperty("banner", now + 2200);
                    st.addProperty("burstAt", now);
                    JsonObject prod = shop == null ? null : product(shop, str(s, "product"));
                    st.addProperty("bannerName", prod == null ? "" : str(prod, "name"));
                }
            }
            case "msg" -> {
                JsonObject shop = SHOP.get(p.getUUID());
                message(p, shop, st, data, s, now);
            }
            default -> { return; }
        }
        JsonObject shop = SHOP.get(p.getUUID());
        data.add("st", st);
        if (shop != null) data.add("vm", build(shop, st, now));
        else data.add("vm", connecting());
    }

    private static boolean assemble(ServerPlayer p, JsonObject m) {
        int chunks = (int) AscViews.num(m, "chunks");
        if (chunks <= 1) {
            JsonObject shop = m.deepCopy();
            PARTS.remove(p.getUUID());
            SHOP.put(p.getUUID(), shop);
            return true;
        }
        JsonObject parts = PARTS.get(p.getUUID());
        if (parts == null || (int) AscViews.num(parts, "seq") != (int) AscViews.num(m, "seq")) {
            parts = new JsonObject();
            parts.addProperty("seq", AscViews.num(m, "seq"));
            parts.addProperty("n", chunks);
            parts.add("got", new JsonObject());
            PARTS.put(p.getUUID(), parts);
        }
        parts.getAsJsonObject("got").add(String.valueOf((int) AscViews.num(m, "chunk")), arr(m, "products"));
        if ((int) AscViews.num(m, "chunk") == 0) parts.add("head", m.deepCopy());
        if (parts.getAsJsonObject("got").size() < chunks || !parts.has("head")) return false;
        JsonObject head = parts.getAsJsonObject("head");
        JsonArray all = new JsonArray();
        for (int i = 0; i < chunks; i++) for (JsonElement e : arr(parts.getAsJsonObject("got"), String.valueOf(i))) all.add(e);
        head.add("products", all);
        PARTS.remove(p.getUUID());
        SHOP.put(p.getUUID(), head);
        return true;
    }

    private static JsonObject product(JsonObject shop, String id) {
        for (JsonElement e : arr(shop, "products")) if (str(e.getAsJsonObject(), "id").equals(id)) return e.getAsJsonObject();
        return null;
    }

    private static void toast(JsonObject data, String text, boolean ok) {
        JsonObject t = new JsonObject();
        t.addProperty("text", text);
        t.addProperty("ok", ok);
        data.add("_toast", t);
    }

    private static List<JsonObject> inCat(JsonObject shop, String cat) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : arr(shop, "products")) {
            JsonObject pr = e.getAsJsonObject();
            for (JsonElement c : arr(pr, "cats")) if (c.getAsString().equals(cat)) { out.add(pr); break; }
        }
        return out;
    }

    private static String curCat(JsonObject shop, JsonObject st) {
        JsonArray cats = arr(shop, "cats");
        String want = str(st, "cat");
        for (JsonElement e : cats) if (str(e.getAsJsonObject(), "id").equals(want)) return want;
        return cats.isEmpty() ? "" : str(cats.get(0).getAsJsonObject(), "id");
    }

    private static void message(ServerPlayer p, JsonObject shop, JsonObject st, JsonObject data, JsonObject m, long now) {
        String a = str(m, "action");
        switch (a) {
            case "cat" -> { st.addProperty("cat", str(m, "k")); st.addProperty("page", 0); st.remove("sel"); }
            case "page" -> st.addProperty("page", Math.max(0, (int) AscViews.num(st, "page") + (int) AscViews.num(m, "d")));
            case "sel" -> st.addProperty("sel", str(m, "k"));
            case "quote" -> {
                if (shop == null) return;
                JsonObject pr = product(shop, str(st, "sel"));
                if (pr == null || !bool(pr, "ok")) return;
                JsonObject o = new JsonObject();
                o.addProperty("product", str(pr, "id"));
                kind(p, "quote", o);
            }
            case "confirm" -> {
                JsonObject q = st.has("quote") ? st.getAsJsonObject("quote") : null;
                if (q == null) return;
                JsonObject o = new JsonObject();
                o.addProperty("token", str(q, "token"));
                st.remove("quote");
                kind(p, "confirm", o);
            }
            case "cancel" -> { st.remove("quote"); kind(p, "cancel", null); }
            case "tick" -> {
                JsonObject q = st.has("quote") ? st.getAsJsonObject("quote") : null;
                if (q != null && now > AscViews.num(q, "until")) { st.remove("quote"); kind(p, "cancel", null); }
                if (st.has("banner") && now > AscViews.num(st, "banner")) st.remove("banner");
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------ view model

    private static JsonObject connecting() {
        JsonObject v = new JsonObject();
        v.addProperty("live", false);
        v.addProperty("title", "Gem Shop");
        v.addProperty("empty", true);
        v.addProperty("emptyText", "Connecting to the shop...");
        v.add("rows", new JsonArray());
        v.add("cats", new JsonArray());
        v.add("cards", new JsonArray());
        v.addProperty("liveColor", "#3a5566");
        v.addProperty("balance", "0");
        v.addProperty("currency", "Gems");
        v.addProperty("yourCur", "Your Gems");
        return v;
    }

    private static String fmt(long ms) {
        long t = Math.max(0, ms / 1000), d = t / 86400, h = t % 86400 / 3600, mi = t % 3600 / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + mi + "m";
        if (mi > 0) return mi + "m " + t % 60 + "s";
        return t + "s";
    }

    private static String itemOf(JsonObject pr) {
        String ic = str(pr, "icon");
        return ic.startsWith("snobblegems:") || ic.isEmpty() ? "minecraft:air" : ic;
    }

    private static String artOf(JsonObject pr) {
        String ic = str(pr, "icon");
        if (!ic.startsWith("snobblegems:")) return "";
        return ic.contains("ticket") ? "a:gems_ticket" : ic.contains("skin") ? "a:gems_skin" : "a:gems_gem";
    }

    private static String lift(String c) {
        if (c == null || !c.matches("#[0-9a-fA-F]{6}")) return "#FBF6FF";
        int r = Integer.parseInt(c.substring(1, 3), 16), g = Integer.parseInt(c.substring(3, 5), 16), b = Integer.parseInt(c.substring(5, 7), 16);
        double l = .3 * r + .59 * g + .11 * b;
        if (l >= 150) return c;
        double t = (150 - l) / (255 - l);
        return String.format(Locale.ROOT, "#%02X%02X%02X", Math.round(r + (255 - r) * t), Math.round(g + (255 - g) * t), Math.round(b + (255 - b) * t));
    }

    private static final String PRISM = "grad:#ff9af0,#d65aea,#8a44f5,#4c7cff,#3ddcff,#b4f7ff";

    private static JsonObject card(JsonObject pr, boolean sel, long now) {
        JsonObject c = new JsonObject();
        boolean ok = bool(pr, "ok");
        c.addProperty("has", true);
        c.addProperty("id", str(pr, "id"));
        c.addProperty("name", str(pr, "name"));
        c.addProperty("color", lift(str(pr, "color")));
        c.addProperty("item", itemOf(pr));
        c.addProperty("art", artOf(pr));
        c.addProperty("isArt", !artOf(pr).isEmpty());
        c.addProperty("qty", num(pr, "count") > 1 ? "x" + num(pr, "count") : "");
        c.addProperty("price", n3(AscViews.num(pr, "price")));
        c.addProperty("priceColor", ok ? PRISM : "#ff9aa6");
        c.addProperty("op", ok ? 1 : 0.5);
        c.addProperty("tex", sel ? "tex:snobblegems/card_sel.png" : "tex:snobblegems/card.png");
        c.addProperty("htex", sel ? "tex:snobblegems/card_sel.png" : "tex:snobblegems/card_hover.png");
        c.addProperty("star", bool(pr, "featured"));
        c.addProperty("timer", pr.has("ends") ? (AscViews.num(pr, "ends") - now <= 0 ? "ENDED" : fmt(AscViews.num(pr, "ends") - now)) : "");
        long left = pr.has("ends") ? AscViews.num(pr, "ends") - now : Long.MAX_VALUE;
        c.addProperty("timerBg", left <= 0 ? "#3b2a66" : left < 600000 ? "grad:#ffb36b,#ff5a6a" : "grad:#ff9af0,#b4f7ff");
        c.addProperty("border", sel ? "#E7C8FF" : ok ? "#4A3A86" : "#6A2A3A");
        c.addProperty("bg", sel ? "#2E1B5E" : "#1A0F38");
        return c;
    }

    private static JsonObject build(JsonObject shop, JsonObject st, long now) {
        JsonObject v = new JsonObject();
        v.addProperty("live", true);
        String cur = str(shop, "currency").isEmpty() ? "Gems" : str(shop, "currency");
        v.addProperty("title", str(shop, "title").isEmpty() ? "Gem Shop" : str(shop, "title"));
        v.addProperty("balance", n3(AscViews.num(shop, "balance")));
        v.addProperty("currency", cur);
        v.addProperty("yourCur", "Your " + cur);
        v.addProperty("earned", n3(AscViews.num(shop, "earnedToday")));
        v.addProperty("forSale", String.valueOf(arr(shop, "products").size()));
        v.addProperty("tip", "Earn " + cur + " by helping the community: welcome new players, celebrate big catches, and support the server.");
        String cat = curCat(shop, st);
        JsonArray cats = new JsonArray();
        String catDesc = "", catName = "";
        for (JsonElement e : arr(shop, "cats")) {
            JsonObject k = e.getAsJsonObject();
            JsonObject o = new JsonObject();
            boolean on = str(k, "id").equals(cat);
            o.addProperty("id", str(k, "id"));
            o.addProperty("name", str(k, "name"));
            o.addProperty("count", String.valueOf(num(k, "count")));
            // .cat / .cat.on / .cat:hover
            o.addProperty("y", 372 + cats.size() * 52);
            o.addProperty("bg", on ? "#e08cf51a" : "#00000000");
            o.addProperty("hover", on ? "#e08cf51a" : "#e08cf50d");
            o.addProperty("border", on ? "#8a44f5" : "#00000000");
            o.addProperty("dot", on ? "#e08cf5" : "#4a2a99");
            o.addProperty("color", on ? "#f6d2ff" : "#d4c8ee");
            o.addProperty("ncolor", on ? "#e08cf5" : "#8f82b8");
            cats.add(o);
            if (on) {
                catName = str(k, "name");
                StringBuilder sb = new StringBuilder();
                for (JsonElement d : arr(k, "desc")) sb.append(sb.length() == 0 ? "" : "  ").append(d.getAsString());
                catDesc = sb.toString();
            }
        }
        v.add("cats", cats);
        v.addProperty("catName", catName);
        v.addProperty("catDesc", catDesc);
        List<JsonObject> list = inCat(shop, cat);
        int pages = Math.max(1, (list.size() + PER - 1) / PER);
        int page = Math.min((int) AscViews.num(st, "page"), pages - 1);
        st.addProperty("page", page);
        v.addProperty("pageText", pages > 1 ? (page + 1) + " / " + pages : "");
        v.addProperty("canPrev", page > 0);
        v.addProperty("canNext", page < pages - 1);
        v.addProperty("paged", pages > 1);
        String sel = str(st, "sel");
        JsonObject selP = null;
        for (JsonObject pr : list) if (str(pr, "id").equals(sel)) selP = pr;
        if (selP == null) st.remove("sel");
        JsonArray rows = new JsonArray();
        JsonArray cells = null;
        for (int i = 0; i < PER; i++) {
            if (i % 5 == 0) { cells = new JsonArray(); JsonObject r = new JsonObject(); r.add("cells", cells); rows.add(r); }
            int gi = page * PER + i;
            if (gi < list.size()) cells.add(card(list.get(gi), list.get(gi) == selP, now));
            else { JsonObject e = new JsonObject(); e.addProperty("has", false); cells.add(e); }
        }
        v.add("rows", rows);
        // the original's card placement: rows of five, each row centred in the 1078px grid
        JsonArray cards = new JsonArray();
        int shown = Math.max(0, Math.min(PER, list.size() - page * PER));
        for (int i = 0; i < shown; i++) {
            JsonObject pr = list.get(page * PER + i);
            JsonObject c = card(pr, pr == selP, now);
            int col = i % 5, rw = i / 5, inRow = Math.min(5, shown - rw * 5), off = Math.round((1078 - (inRow * 198 + (inRow - 1) * 22)) / 2f);
            c.addProperty("x", off + col * 220);
            c.addProperty("y", rw * 220);
            cards.add(c);
        }
        v.add("cards", cards);
        v.addProperty("popKey", cat + "|" + page);
        v.addProperty("liveColor", "#3ee06b");
        v.addProperty("empty", list.isEmpty());
        v.addProperty("emptyText", "Nothing for sale here right now");
        v.addProperty("hasSel", selP != null);
        if (selP != null) {
            boolean ok = bool(selP, "ok");
            StringBuilder d = new StringBuilder();
            for (JsonElement e : arr(selP, "desc")) d.append(d.length() == 0 ? "" : " ").append(e.getAsString());
            JsonObject x = new JsonObject();
            x.addProperty("name", str(selP, "name"));
            x.addProperty("color", lift(str(selP, "color")));
            x.addProperty("desc", d.toString());
            x.addProperty("price", n3(AscViews.num(selP, "price")));
            x.addProperty("priceColor", ok ? PRISM : "#ff9aa6");
            x.addProperty("buyTex", ok ? "tex:snobblegems/buy.png" : "tex:snobblegems/buy_off.png");
            x.addProperty("buyColor", ok ? "#1a0a40" : "#6a5a9a");
            x.addProperty("endsColor", selP.has("ends") && AscViews.num(selP, "ends") - now < 600000 ? "#ff9a8a" : "#f6d2ff");
            String lim = str(selP, "limit");
            x.addProperty("limit", ok ? lim : str(selP, "reason") + (lim.isEmpty() ? "" : "  -  " + lim));
            x.addProperty("limitColor", ok ? "#8F82B8" : "#FF7A88");
            x.addProperty("ends", selP.has("ends") ? (AscViews.num(selP, "ends") - now <= 0 ? "Offer ended" : "Ends in " + fmt(AscViews.num(selP, "ends") - now)) : "");
            x.addProperty("ok", ok);
            x.addProperty("item", itemOf(selP));
            x.addProperty("art", artOf(selP));
            x.addProperty("isArt", !artOf(selP).isEmpty());
            v.add("sel", x);
        }
        // the confirm window
        JsonObject q = st.has("quote") ? st.getAsJsonObject("quote") : null;
        v.addProperty("modal", q != null);
        if (q != null) {
            JsonObject pr = product(shop, str(q, "product"));
            JsonObject m = new JsonObject();
            m.addProperty("name", str(q, "name"));
            m.addProperty("color", lift(str(q, "color")));
            StringBuilder d = new StringBuilder();
            if (pr != null) for (JsonElement e : arr(pr, "desc")) d.append(d.length() == 0 ? "" : " ").append(e.getAsString());
            m.addProperty("desc", d.toString());
            m.addProperty("item", pr == null ? "minecraft:air" : itemOf(pr));
            m.addProperty("art", pr == null ? "" : artOf(pr));
            m.addProperty("isArt", pr != null && !artOf(pr).isEmpty());
            m.addProperty("price", n3(AscViews.num(q, "price")));
            m.addProperty("balance", n3(AscViews.num(q, "balance")));
            m.addProperty("after", n3(AscViews.num(q, "after")));
            m.addProperty("held", "This offer is held for " + Math.max(0, (AscViews.num(q, "until") - now + 500) / 1000) + " seconds.");
            v.add("q", m);
        }
        boolean banner = st.has("banner") && AscViews.num(st, "banner") > now;
        v.addProperty("banner", banner);
        v.addProperty("burstKey", st.has("burstAt") && now - (long) AscViews.num(st, "burstAt") < 3200 ? "b" + (long) AscViews.num(st, "burstAt") : "");
        v.addProperty("bannerName", str(st, "bannerName"));
        return v;
    }
}
