package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The SnobbleCrates "what is inside this crate" box (view only). The crate mod sends the whole reward list (up to about a thousand rewards);
 * the list stays here and the page gets one page of eighteen at a time, rarest first, with the chosen reward's details. Nothing here gives or
 * takes anything: the box is a picture of the crate's table.
 */
final class CrateViews {
    private CrateViews() {}

    static final String PAGE = "snobblecrates_crate";
    private static final int PER = 18;
    private static final Map<UUID, JsonObject> TABLES = new ConcurrentHashMap<>();       // per player: the sorted rewards and the rarities

    static void register() {
        SnobbleUi.registerView(PAGE, CrateViews::apply);
    }

    private static boolean looksLikeItem(String id) { return id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"); }

    private static String pct(double c) {
        if (c < 0) return "?";
        double p = c * 100;
        String s = String.format(Locale.US, p >= 10 ? "%.1f" : p >= 1 ? "%.2f" : "%.3f", p);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") + "%" : s + "%";
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : new JsonObject();
        switch (event) {
            case "crate_view" -> {
                TABLES.put(p.getUUID(), prepare(s));
                st.addProperty("page", 0);
                st.addProperty("sel", -1);
            }
            case "msg" -> {
                JsonObject t = TABLES.get(p.getUUID());
                if (t == null) return;
                int pages = pages(t);
                int pg = (int) num(st, "page");
                switch (str(s, "action")) {
                    case "go" -> pg = Math.max(0, Math.min(pages - 1, pg + (int) num(s, "d")));
                    case "sel" -> st.addProperty("sel", (int) num(s, "k"));
                    default -> { return; }
                }
                if (pg != num(st, "page")) { st.addProperty("page", pg); st.addProperty("sel", -1); }
            }
            default -> { return; }
        }
        JsonObject t = TABLES.get(p.getUUID());
        if (t == null) return;
        data.add("st", st);
        data.add("vm", build(t, (int) num(st, "page"), (int) num(st, "sel")));
    }

    private static int pages(JsonObject t) { return Math.max(1, (t.getAsJsonArray("rewards").size() + PER - 1) / PER); }

    /** Sorts like the web page (rarest first) and works out the per-rarity odds. */
    private static JsonObject prepare(JsonObject d) {
        JsonObject t = new JsonObject();
        t.addProperty("empty", bool(d, "empty"));
        t.addProperty("name", str(d, "name"));
        t.addProperty("tagline", str(d, "tagline"));
        t.addProperty("showChances", bool(d, "showChances"));
        t.addProperty("needKey", bool(d, "needKey"));
        t.addProperty("keyName", str(d, "keyName"));
        Map<String, JsonObject> rar = new HashMap<>();
        Map<String, Integer> idx = new HashMap<>();
        JsonArray rarities = d.has("rarities") && d.get("rarities").isJsonArray() ? d.getAsJsonArray("rarities") : new JsonArray();
        int ri = 0;
        for (JsonElement e : rarities) { JsonObject r = e.getAsJsonObject(); rar.put(str(r, "id"), r); idx.put(str(r, "id"), ri++); }
        List<JsonObject> rewards = new ArrayList<>();
        if (d.has("rewards") && d.get("rewards").isJsonArray()) for (JsonElement e : d.getAsJsonArray("rewards")) if (e.isJsonObject()) rewards.add(e.getAsJsonObject());
        boolean chances = bool(d, "showChances");
        rewards.sort((a, b) -> {
            double ca = a.has("chance") ? a.get("chance").getAsDouble() : 0, cb = b.has("chance") ? b.get("chance").getAsDouble() : 0;
            int ra = idx.getOrDefault(str(a, "rarity"), 0), rb = idx.getOrDefault(str(b, "rarity"), 0);
            if (!chances) return rb - ra;
            int c = Double.compare(ca, cb);
            return c != 0 ? c : rb - ra;
        });
        // odds per rarity
        Map<String, Double> tot = new HashMap<>();
        Map<String, Integer> cnt = new HashMap<>();
        double sum = 0;
        for (JsonObject rw : rewards) {
            double c = Math.max(0, rw.has("chance") ? rw.get("chance").getAsDouble() : 0);
            tot.merge(str(rw, "rarity"), c, Double::sum);
            cnt.merge(str(rw, "rarity"), 1, Integer::sum);
            sum += c;
        }
        JsonArray odds = new JsonArray();
        for (JsonElement e : rarities) {
            JsonObject r = e.getAsJsonObject();
            String id = str(r, "id");
            if (!cnt.containsKey(id)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("text", str(r, "name") + " " + (chances ? pct(tot.get(id) / (sum == 0 ? 1 : sum)) : String.valueOf(cnt.get(id))));
            o.addProperty("color", str(r, "color").isEmpty() ? "#C8C8C8" : str(r, "color"));
            o.addProperty("g", chances && sum > 0 ? Math.max(tot.get(id), 0.002) : 1);
            odds.add(o);
        }
        t.add("odds", odds);
        t.addProperty("hasBar", chances && sum > 0 && !odds.isEmpty());
        JsonArray out = new JsonArray();
        int top = Math.max(0, rarities.size() - 3);
        for (JsonObject rw : rewards) {
            JsonObject r = rar.get(str(rw, "rarity"));
            JsonObject o = new JsonObject();
            o.addProperty("name", str(rw, "name"));
            String item = str(rw, "item");
            boolean perk = str(rw, "kind").equals("perk");
            o.addProperty("item", looksLikeItem(item) && !perk ? item : perk ? "minecraft:nether_star" : "minecraft:chest");
            o.addProperty("color", r == null || str(r, "color").isEmpty() ? "#C8C8C8" : str(r, "color"));
            o.addProperty("rarityName", r == null ? str(rw, "rarity") : str(r, "name"));
            long min = num(rw, "min"), max = num(rw, "max");
            o.addProperty("count", min == max ? (max > 1 ? "x" + max : "") : min + "-" + max);
            o.addProperty("perk", perk);
            o.addProperty("hi", r != null && idx.getOrDefault(str(rw, "rarity"), 0) >= top && rarities.size() > 3);
            double c = rw.has("chance") ? rw.get("chance").getAsDouble() : -1;
            List<String> tail = new ArrayList<>();
            if (c >= 0) tail.add(pct(c) + (c > 0 ? " (1 in " + String.format(Locale.US, "%,d", Math.max(1, Math.round(1 / c))) + ")" : ""));
            if (min != max) tail.add("Amount " + min + " to " + max); else if (max > 1) tail.add("Amount " + max);
            if (perk) tail.add("PERK"); else if (str(rw, "kind").equals("both")) tail.add("ITEM + PERK");
            o.addProperty("detail", String.join("  -  ", tail));
            String desc = str(rw, "desc");
            if (desc.isEmpty() && rw.has("lore") && rw.get("lore").isJsonArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonElement l : rw.getAsJsonArray("lore")) sb.append(sb.length() == 0 ? "" : " ").append(l.getAsString());
                desc = sb.toString();
            }
            o.addProperty("desc", desc);
            String col = o.get("color").getAsString();
            int rgb;
            try { rgb = Integer.parseInt(col.replace("#", "").substring(0, 6), 16); } catch (RuntimeException e) { rgb = 0xC8C8C8; }
            String art = CrateArt.pick(str(rw, "icon"), item, str(rw, "name"), str(rw, "kind"));
            o.addProperty("art", CrateArt.key(art, rgb, CrateArt.ballTint(item)));
            out.add(o);
        }
        t.add("rewards", out);
        return t;
    }

    private static JsonObject build(JsonObject t, int page, int sel) {
        JsonObject v = new JsonObject();
        boolean empty = bool(t, "empty");
        v.addProperty("empty", empty);
        v.addProperty("name", empty ? "Nothing to show" : str(t, "name"));
        JsonArray rewards = t.getAsJsonArray("rewards");
        v.addProperty("tag", empty ? "" : str(t, "tagline").isEmpty() ? rewards.size() + " possible rewards" : str(t, "tagline"));
        v.addProperty("key", empty ? "" : bool(t, "needKey") ? "Opens with: **" + str(t, "keyName") + "**" : "No key needed");
        v.add("odds", t.getAsJsonArray("odds"));
        v.addProperty("hasBar", bool(t, "hasBar"));
        int pages = pages(t);
        v.addProperty("pageText", empty ? "" : (page + 1) + " / " + pages);
        v.addProperty("canPrev", page > 0);
        v.addProperty("canNext", page < pages - 1);
        JsonArray slots = new JsonArray();
        for (int i = 0; i < PER; i++) {
            int gi = page * PER + i;
            JsonObject sl = new JsonObject();
            sl.addProperty("i", gi);
            boolean has = !empty && gi < rewards.size();
            sl.addProperty("has", has);
            if (has) {
                JsonObject rw = rewards.get(gi).getAsJsonObject();
                sl.addProperty("item", str(rw, "item"));
                sl.addProperty("name", str(rw, "name"));
                sl.addProperty("color", str(rw, "color"));
                sl.addProperty("count", str(rw, "count"));
                sl.addProperty("perk", bool(rw, "perk"));
                sl.addProperty("border", gi == sel ? str(rw, "color") : "#0E0B18");
                sl.addProperty("art", str(rw, "art"));
                sl.addProperty("hi", bool(rw, "hi"));
                // what the info well shows while this slot is pointed at (showInfo)
                JsonObject in = new JsonObject();
                in.addProperty("has", true);
                in.addProperty("art", str(rw, "art"));
                in.addProperty("title", str(rw, "name"));
                in.addProperty("rarity", str(rw, "rarityName").toUpperCase(Locale.ROOT));
                in.addProperty("color", str(rw, "color"));
                in.addProperty("detail", str(rw, "detail").isEmpty() ? "" : "  ·  " + str(rw, "detail").replace("  -  ", "  ·  "));
                in.addProperty("desc", str(rw, "desc"));
                sl.add("info", in);
            }
            slots.add(sl);
        }
        // three rows of six
        JsonArray rows = new JsonArray();
        for (int r = 0; r < 3; r++) {
            JsonArray cells = new JsonArray();
            for (int c = 0; c < 6; c++) cells.add(slots.get(r * 6 + c));
            JsonObject ro = new JsonObject();
            ro.add("cells", cells);
            rows.add(ro);
        }
        v.add("rows", rows);
        // the original's 6 x 3 grid inside the body (padding 10 + border 4, gap 6): slot positions and hover look
        double cw = (628 - 5 * 6) / 6.0, ch = (258 - 2 * 6) / 3.0;
        JsonArray flat = new JsonArray();
        for (int i = 0; i < PER; i++) {
            JsonObject sl = slots.get(i).getAsJsonObject();
            sl.addProperty("x", (int) Math.round(14 + (i % 6) * (cw + 6)));
            sl.addProperty("y", (int) Math.round(14 + (i / 6) * (ch + 6)));
            sl.addProperty("hover", bool(sl, "has") ? "#251f3b" : "tex:snobblecrates/slot.png");
            if (!sl.has("info")) { JsonObject e = new JsonObject(); e.addProperty("has", false); sl.add("info", e); }
            flat.add(sl);
        }
        v.add("slots", flat);
        // odds bar: segments share the bar's 500px by their chance (flex), 2px apart; the legend splits name / value
        JsonArray odds = v.getAsJsonArray("odds");
        double tg = 0;
        for (JsonElement e : odds) tg += e.getAsJsonObject().get("g").getAsDouble();
        int room = 500 - 2 * Math.max(0, odds.size() - 1);
        for (JsonElement e : odds) {
            JsonObject o = e.getAsJsonObject();
            o.addProperty("w", Math.max(3, (int) Math.round(room * o.get("g").getAsDouble() / (tg <= 0 ? 1 : tg))));
            String txt = str(o, "text");
            int sp = txt.lastIndexOf(' ');
            o.addProperty("label", sp > 0 ? txt.substring(0, sp) : txt);
            o.addProperty("val", sp > 0 ? txt.substring(sp + 1) : "");
        }
        JsonObject info = new JsonObject();
        boolean showSel = sel >= 0 && sel < rewards.size();
        if (showSel) {
            JsonObject rw = rewards.get(sel).getAsJsonObject();
            info.addProperty("item", str(rw, "item"));
            info.addProperty("title", str(rw, "name"));
            info.addProperty("rarity", str(rw, "rarityName").toUpperCase(Locale.ROOT));
            info.addProperty("color", str(rw, "color"));
            info.addProperty("detail", str(rw, "detail"));
            info.addProperty("desc", str(rw, "desc"));
        } else {
            info.addProperty("item", "");
            info.addProperty("title", "Click a reward to inspect it.");
            info.addProperty("rarity", bool(t, "showChances") ? "Rarest first. Chances are per opening." : "Rarest first.");
            info.addProperty("color", "#A9A6C9");
            info.addProperty("detail", "");
            info.addProperty("desc", "");
        }
        v.add("info", info);
        v.addProperty("hasInfoItem", showSel);
        v.addProperty("emptyText", "Left-click a crate in the world to see what it holds.");
        v.addProperty("hint", bool(t, "showChances") ? "Rarest first. Chances are per opening." : "Rarest first.");
        v.addProperty("pageKey", page);
        JsonArray gems = new JsonArray();
        for (int c : new int[]{0xf2559a, 0x52b2ee, 0xa448e8}) { JsonObject g = new JsonObject(); g.addProperty("art", CrateArt.key("gem", c, "poke")); gems.add(g); }
        v.add("gems", gems);
        return v;
    }
}
