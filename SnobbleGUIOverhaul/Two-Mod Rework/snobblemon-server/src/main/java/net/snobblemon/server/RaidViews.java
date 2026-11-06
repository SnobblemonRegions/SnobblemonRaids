package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * View models for the CobbleRaids pages (hub, leaderboards, Hall of Legends, shop, trophy room). They only reshape the numbers and
 * names CobbleRaids sends into text the layouts print; every ranking, price and purchase is still decided by CobbleRaids on the server.
 */
final class RaidViews {
    private RaidViews() {}

    static void register() {
        SnobbleUi.registerView("cobbleraids_hub", RaidViews::hub);
        SnobbleUi.registerView("cobbleraids_leaders", RaidViews::leaders);
        SnobbleUi.registerView("cobbleraids_legends", RaidViews::legends);
        SnobbleUi.registerView("cobbleraids_shop", RaidViews::shop);
        SnobbleUi.registerView("cobbleraids_trophy", RaidViews::trophy);
        SnobbleUi.registerView("cobbleraids_reveal", RaidViews::reveal);
        SnobbleUi.registerView("cobbleraids_capture", RaidViews::capture);
    }

    // ------------------------------------------------------------------ helpers

    static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    static long num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        try { return e != null && e.isJsonPrimitive() ? (long) e.getAsDouble() : 0; } catch (RuntimeException ex) { return 0; }
    }

    static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    static String grouped(long n) { return String.format(Locale.US, "%,d", n); }

    /** Picture key for the Pokemon pictures ("s:abra:0"), or "" when the name cannot be one. */
    static String spriteKey(String species, boolean shiny) {
        String k = species == null ? "" : species.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
        return k.isEmpty() || k.length() > 40 ? "" : "s:" + k + ":" + (shiny ? "1" : "0");
    }

    static String title(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = s.replace('_', ' ');
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char ch : t.toCharArray()) { b.append(up ? Character.toUpperCase(ch) : ch); up = ch == ' '; }
        return b.toString();
    }

    static String plural(long n, String one, String many) { return grouped(n) + " " + (n == 1 ? one : many); }

    static String duration(long total) {
        total = Math.max(0, total);
        long d = total / 86400, h = total % 86400 / 3600, m = total % 3600 / 60, s = total % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    private static JsonObject vm(JsonObject data) {
        JsonObject v = data.has("vm") && data.get("vm").isJsonObject() ? data.getAsJsonObject("vm") : new JsonObject();
        data.add("vm", v);
        return v;
    }

    // ------------------------------------------------------------------ hub

    private static void hub(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        if (!event.equals("hub_state")) return;
        data.add("hub", s);
        JsonObject v = vm(data);
        v.addProperty("who", "/ " + str(s, "name"));
        v.addProperty("rp", grouped(num(s, "balance")) + " RP");
        v.addProperty("trophies", plural(num(s, "trophies"), "trophy", "trophies"));
        v.addProperty("legends", plural(num(s, "legends"), "legend", "legends"));
        v.addProperty("raids", plural(num(s, "raidsWon"), "raid won", "raids won"));
    }

    // ------------------------------------------------------------------ leaderboards

    private static void leaders(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        if (!event.equals("leaders_page")) return;
        data.add("lp", s);
        JsonObject v = vm(data);
        boolean seconds = bool(s, "seconds");
        JsonArray boards = s.has("boards") ? s.getAsJsonArray("boards") : new JsonArray();
        int idx = 0;
        for (int i = 0; i < boards.size(); i++) if (str(boards.get(i).getAsJsonObject(), "id").equals(str(s, "stat"))) idx = i;
        int n = Math.max(1, boards.size());
        String prev = boards.isEmpty() ? "" : str(boards.get((idx - 1 + n) % n).getAsJsonObject(), "id");
        String next = boards.isEmpty() ? "" : str(boards.get((idx + 1) % n).getAsJsonObject(), "id");
        String scope = str(s, "scope").equals("weekly") ? "weekly" : "alltime";
        v.addProperty("prevStat", prev);
        v.addProperty("nextStat", next);
        v.addProperty("scope", scope);
        v.addProperty("otherScope", scope.equals("weekly") ? "alltime" : "weekly");
        v.addProperty("scopeLabel", scope.equals("weekly") ? "This week" : "All time");
        v.addProperty("valueHead", seconds ? "TIME" : "TOTAL");
        v.addProperty("crumb", "/ " + str(s, "title"));
        JsonArray rows = new JsonArray();
        if (s.has("rows")) for (JsonElement e : s.getAsJsonArray("rows")) {
            JsonObject r = e.getAsJsonObject();
            long rank = num(r, "rank");
            JsonObject o = new JsonObject();
            o.addProperty("rank", "#" + rank);
            o.addProperty("name", str(r, "name"));
            o.addProperty("val", seconds ? duration(num(r, "value")) : grouped(num(r, "value")));
            boolean you = bool(r, "you");
            o.addProperty("bg", you ? "#0E4668" : "#08233A");
            o.addProperty("color", rank == 1 ? "#FFD36B" : rank == 2 ? "#D5E3EE" : rank == 3 ? "#E0A070" : "#E6F7FF");
            o.addProperty("you", you);
            o.addProperty("youBorder", you ? "#46e3ff" : "");
            o.addProperty("edge", rank == 1 ? "#ffb02e" : rank == 2 ? "#c8d6df" : rank == 3 ? "#d98b52" : "#1d6f8f");
            o.addProperty("rankColor", rank == 1 ? "#ffb02e" : rank == 2 ? "#c8d6df" : rank == 3 ? "#d98b52" : "#7aa7bd");
            o.addProperty("rowBg", you ? "#46e3ff29" : "#072a4599");
            rows.add(o);
        }
        v.add("rows", rows);
        v.addProperty("empty", rows.isEmpty());
        StringBuilder line = new StringBuilder(num(s, "ranked") + " ranked");
        if (s.has("viewer") && s.get("viewer").isJsonObject()) {
            JsonObject vw = s.getAsJsonObject("viewer");
            line.append("   |   You: #").append(num(vw, "rank")).append("  ").append(seconds ? duration(num(vw, "value")) : grouped(num(vw, "value")));
        } else line.append("   |   You are not ranked");
        if (scope.equals("weekly") && s.has("resetsInSeconds") && !s.get("resetsInSeconds").isJsonNull()) line.append("   |   Resets in ").append(duration(num(s, "resetsInSeconds")));
        v.addProperty("counts", line.toString());
    }

    // ------------------------------------------------------------------ Hall of Legends

    private static void legends(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        if (!event.equals("legends_page")) return;
        data.add("lg", s);
        JsonObject v = vm(data);
        long idx = num(s, "pageIndex"), count = Math.max(1, num(s, "pageCount"));
        v.addProperty("prev", Math.max(0, idx - 1));
        v.addProperty("next", Math.min(count - 1, idx + 1));
        v.addProperty("canPrev", idx > 0);
        v.addProperty("canNext", idx + 1 < count);
        v.addProperty("where", "PAGE " + (idx + 1) + " OF " + count);
        v.addProperty("prevOp", idx > 0 ? 1 : 0.45);
        v.addProperty("nextOp", idx + 1 < count ? 1 : 0.45);
        v.addProperty("total", plural(num(s, "total"), "legend", "legends"));
        v.addProperty("mineTotal", grouped(num(s, "mineCount")) + " of yours");
        boolean none = !s.has("entries") || s.getAsJsonArray("entries").isEmpty();
        v.addProperty("none", none);
        v.addProperty("emptyText", num(s, "total") == 0 ? "No legends recorded yet." : "No legends match this filter.");
        JsonArray rows = new JsonArray();
        if (s.has("entries")) for (JsonElement e : s.getAsJsonArray("entries")) {
            JsonObject r = e.getAsJsonObject();
            JsonObject o = new JsonObject();
            o.addProperty("line1", str(r, "title") + "  " + title(str(r, "species")));
            o.addProperty("line2", str(r, "tierName").toUpperCase(Locale.ROOT) + "  -  " + str(r, "boon"));
            o.addProperty("when", str(r, "defeatedAt"));
            o.addProperty("color", str(r, "tierColor").isEmpty() ? "#39F2FF" : str(r, "tierColor"));
            JsonArray vic = r.has("victors") ? r.getAsJsonArray("victors") : new JsonArray();
            StringBuilder b = new StringBuilder();
            for (JsonElement x : vic) { if (b.length() > 0) b.append(", "); b.append(x.getAsString()); }
            o.addProperty("victors", b.toString());
            o.addProperty("title", str(r, "title") + "  ");
            o.addProperty("species", title(str(r, "species")));
            o.addProperty("tierName", str(r, "tierName").toUpperCase(Locale.ROOT));
            o.addProperty("l2", "   \u00b7   " + str(r, "boon") + "   \u00b7   " + isoDay(num(r, "defeatedAt")));
            rows.add(o);
        }
        v.add("rows", rows);
        v.add("tierCycle", tierCycle(s));
        v.addProperty("firstTier", firstTier(s));
        v.addProperty("crumbAll", "/ " + num(s, "total") + " legends");
        v.addProperty("crumbMine", "/ " + num(s, "mineCount") + " of yours");
        v.addProperty("counts", "PAGE " + (idx + 1) + " OF " + count);
    }

    // ------------------------------------------------------------------ shop

    private static void shop(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        if (!event.equals("shop_page")) return;       // icon images are not used by the native page
        data.add("sh", s);
        JsonObject v = vm(data);
        long idx = num(s, "pageIndex"), count = Math.max(1, num(s, "pageCount"));
        long balance = num(s, "balance");
        JsonArray entries = s.has("entries") ? s.getAsJsonArray("entries") : new JsonArray();
        boolean pokemon = !entries.isEmpty();
        for (JsonElement e : entries) if (!bool(e.getAsJsonObject(), "pokemon")) pokemon = false;
        int per = pokemon ? 4 : 8;
        JsonArray rows = new JsonArray();
        JsonArray cur = null;
        int i = 0;
        for (JsonElement e : entries) {
            JsonObject r = e.getAsJsonObject();
            long cost = num(r, "cost");
            boolean sold = bool(r, "soldOut");
            boolean affordable = balance >= cost && !sold;
            JsonObject c = new JsonObject();
            c.addProperty("id", str(r, "id"));
            String icon = str(r, "icon");
            c.addProperty("itemId", icon.startsWith("i:") ? icon.substring(2) : "");
            c.addProperty("sprite", icon.startsWith("s:") ? icon : bool(r, "pokemon") ? spriteKey(str(r, "species"), bool(r, "shiny")) : "");
            c.addProperty("count", (int) Math.max(1, Math.min(99, num(r, "count"))));
            c.addProperty("price", sold ? "SOLD OUT" : grouped(cost) + " RP");
            c.addProperty("priceColor", sold ? "#FF6B6B" : affordable ? "#46E3FF" : "#4A8BA3");
            c.addProperty("nameColor", !affordable ? "#4F7A90" : bool(r, "shiny") ? "#FFE066" : "#E6F7FF");
            c.addProperty("bg", affordable ? "#08233A" : "#061A2B");
            c.addProperty("border", affordable ? "#1D6F8F" : "#17475C");
            if (bool(r, "pokemon")) {
                c.addProperty("name", (bool(r, "shiny") ? "* " : "") + title(str(r, "species")));
                c.addProperty("meta", "Level " + num(r, "level"));
                c.addProperty("meta2", bool(r, "personal") ? "IV " + num(r, "ivPercent") + "%  EV " + num(r, "evPercent") + "%" : "");
                c.addProperty("tipName", (bool(r, "shiny") ? "Shiny " : "") + title(str(r, "species")));
            } else {
                c.addProperty("name", str(r, "name"));
                c.addProperty("meta", num(r, "count") > 1 ? "x" + num(r, "count") : "");
                c.addProperty("meta2", "");
                c.addProperty("tipName", str(r, "name") + (num(r, "count") > 1 ? "  x" + num(r, "count") : ""));
            }
            long limit = num(r, "limit");
            c.addProperty("left", limit > 0 && !sold ? Math.max(0, num(r, "remaining")) + " left" : "");
            c.addProperty("countText", num(r, "count") > 1 ? "\u00d7" + num(r, "count") : "");
            c.addProperty("op", affordable ? 1 : 0.45);
            c.addProperty("pname", (bool(r, "shiny") ? "\u2605 " : "") + title(str(r, "species")));
            c.addProperty("pcolor", bool(r, "shiny") ? "#ffe066" : "#e6f7ff");
            c.addProperty("ivev", bool(r, "personal") ? "IV " + num(r, "ivPercent") + "%  EV " + num(r, "evPercent") + "%" : "");
            c.addProperty("priceCol", sold ? "#ff6b6b" : "#46e3ff");
            c.addProperty("x", pokemon ? (i % 4) * (361.6 + 19.2) : (i % 8) * (175.1 + 14.4));
            c.addProperty("y", pokemon ? (i / 4) * (142.4 + 19.2) : (i / 8) * (134.4 + 14.4));
            String tipPrice;
            if (sold) tipPrice = limit == 1 ? "Already purchased" : "All " + limit + " bought";
            else if (cost > balance) tipPrice = grouped(cost) + " RP  (" + grouped(cost - balance) + " RP short)";
            else tipPrice = grouped(cost) + " RP";
            c.addProperty("tipPrice", tipPrice);
            c.addProperty("tipLimit", limit > 0 && !sold ? num(r, "remaining") + " of " + limit + " left" : (sold ? (num(s, "rotatesInMillis") > 0 ? "Back with the next restock" : "Resets daily") : ""));
            boolean canReroll = bool(r, "personal") && bool(r, "canReroll") && str(r, "id").startsWith("personal:");
            c.addProperty("canReroll", canReroll);
            c.addProperty("rerollId", canReroll ? "reroll:" + str(r, "id").substring("personal:".length()) : "");
            c.addProperty("rerollText", canReroll ? "REROLL " + num(r, "rerollCost") : "");
            if (i % per == 0) { cur = new JsonArray(); JsonObject row = new JsonObject(); row.add("cells", cur); rows.add(row); }
            cur.add(c);
            i++;
        }
        if (cur != null) while (cur.size() < per) { JsonObject blank = new JsonObject(); blank.addProperty("blank", true); cur.add(blank); }
        v.add("rows", rows);
        JsonArray flat = new JsonArray();
        for (JsonElement rw : rows) for (JsonElement c0 : rw.getAsJsonObject().getAsJsonArray("cells")) if (!bool(c0.getAsJsonObject(), "blank")) flat.add(c0);
        v.add("cells", flat);
        v.addProperty("pokemon", pokemon);
        v.addProperty("none", entries.isEmpty());
        v.addProperty("balanceText", grouped(balance) + " RP");
        v.addProperty("where", "PAGE " + (idx + 1) + " OF " + count);
        v.addProperty("prev", (idx - 1 + count) % count);
        v.addProperty("next", (idx + 1) % count);
        v.addProperty("single", count <= 1);
        long rot = num(s, "rotatesInMillis");
        v.addProperty("rotates", rot > 0);
        v.addProperty("deadline", rot > 0 ? System.currentTimeMillis() + rot : 0);
        v.addProperty("crumb", "/ " + str(s, "heading"));
    }

    // ------------------------------------------------------------------ trophy room

    private static void trophy(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        if (!event.equals("trophy_page")) return;     // sprite images are not used by the native page
        data.add("tp", s);
        JsonObject v = vm(data);
        long idx = num(s, "pageIndex"), count = Math.max(1, num(s, "pageCount"));
        v.addProperty("prev", Math.max(0, idx - 1));
        v.addProperty("next", Math.min(count - 1, idx + 1));
        v.addProperty("canPrev", idx > 0);
        v.addProperty("canNext", idx + 1 < count);
        v.addProperty("where", "PAGE " + (idx + 1) + " OF " + count);
        v.addProperty("crumb", "/ " + grouped(num(s, "total")) + " trophies  -  " + grouped(num(s, "shinyTotal")) + " shiny");
        v.addProperty("prevOp", idx > 0 ? 1 : 0.45);
        v.addProperty("nextOp", idx + 1 < count ? 1 : 0.45);
        JsonArray list = new JsonArray();
        JsonArray entries = s.has("entries") ? s.getAsJsonArray("entries") : new JsonArray();
        for (int i = 0; i < 3; i++) {
            JsonObject o = new JsonObject();
            if (i < entries.size()) {
                JsonObject r = entries.get(i).getAsJsonObject();
                o.addProperty("filled", true);
                o.addProperty("name", title(str(r, "species")));
                o.addProperty("sprite", spriteKey(str(r, "species"), bool(r, "shiny")));
                o.addProperty("level", "LEVEL " + num(r, "level"));
                o.addProperty("tier", str(r, "tierName").toUpperCase(Locale.ROOT));
                o.addProperty("tierColor", str(r, "tierColor").isEmpty() ? "#39F2FF" : str(r, "tierColor"));
                o.addProperty("shiny", bool(r, "shiny") ? "* SHINY" : "NORMAL");
                o.addProperty("shinyColor", bool(r, "shiny") ? "#FFE066" : "#7AA7BD");
                o.addProperty("iv", Math.max(0, Math.min(100, num(r, "iv"))));
                o.addProperty("ev", Math.max(0, Math.min(100, num(r, "ev"))));
                o.addProperty("ivText", num(r, "iv") + "%");
                o.addProperty("evText", num(r, "ev") + "%");
                o.addProperty("first", str(r, "firstDefeatedAt"));
                o.addProperty("times", "Beaten x" + num(r, "times"));
                o.addProperty("firstText", "First " + isoDay(num(r, "firstDefeatedAt")));
                o.addProperty("timesText", "Beaten \u00d7" + num(r, "times"));
                o.addProperty("ivW", Math.max(0, Math.min(100, num(r, "iv"))) * 3.584);
                o.addProperty("evW", Math.max(0, Math.min(100, num(r, "ev"))) * 3.584);
                o.addProperty("shinyText", bool(r, "shiny") ? "\u2605 SHINY" : "NORMAL");
                String tc = str(r, "tierColor").toLowerCase(Locale.ROOT);
                o.addProperty("glow", tc.matches("#[0-9a-f]{6}") ? "g:" + tc.substring(1) : "g:46e3ff");
            } else {
                o.addProperty("filled", false);
                o.addProperty("blank", i == 0 && num(s, "total") == 0 ? "Win a raid" : (num(s, "total") > 0 && entries.isEmpty() ? "No matches" : ""));
            }
            list.add(o);
        }
        v.add("cases", list);
        v.addProperty("crumb2", "/ " + grouped(num(s, "total")) + " trophies  \u00b7  " + grouped(num(s, "shinyTotal")) + " shiny");
        v.add("tierCycle", tierCycle(s));
        v.addProperty("firstTier", firstTier(s));
    }

    /** The tiers in order, each with the one after it ("" after the last = all tiers again): the page's cycling tier button. */
    static JsonArray tierCycle(JsonObject s) {
        JsonArray out = new JsonArray();
        JsonArray t = s.has("tiers") && s.get("tiers").isJsonArray() ? s.getAsJsonArray("tiers") : new JsonArray();
        for (int i = 0; i < t.size(); i++) {
            JsonObject o = new JsonObject(), r = t.get(i).getAsJsonObject();
            o.addProperty("id", str(r, "id"));
            o.addProperty("name", str(r, "name"));
            o.addProperty("next", i + 1 < t.size() ? str(t.get(i + 1).getAsJsonObject(), "id") : "");
            out.add(o);
        }
        return out;
    }

    static String firstTier(JsonObject s) {
        JsonArray t = s.has("tiers") && s.get("tiers").isJsonArray() ? s.getAsJsonArray("tiers") : new JsonArray();
        return t.isEmpty() ? "" : str(t.get(0).getAsJsonObject(), "id");
    }

    static String isoDay(long ms) {
        return ms <= 0 ? "?" : java.time.Instant.ofEpochMilli(ms).toString().substring(0, 10);
    }

    // ------------------------------------------------------------------ reward reveal

    private static void reveal(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject v = vm(data);
        switch (event) {
            case "reveal_offer" -> {
                data.add("of", s);
                data.remove("rs");
                v.addProperty("stage", "CHOOSING");
                v.addProperty("beamOp", 1);
                long secs = num(s, "elapsedTicks") / 20;
                v.addProperty("time", String.format(Locale.ROOT, "%02d:%02d", secs / 60, secs % 60));
                v.addProperty("contribution", String.format(Locale.ROOT, "%.1f%%", s.has("contribution") ? s.get("contribution").getAsDouble() : 0));
                v.addProperty("crumb", "/ " + str(s, "species") + " neutralised");
                JsonArray choices = s.has("choices") ? s.getAsJsonArray("choices") : new JsonArray();
                JsonArray list = new JsonArray();
                for (JsonElement e : choices) {
                    JsonObject c = new JsonObject();
                    String id = e.getAsString();
                    c.addProperty("id", id);
                    c.addProperty("label", choices.size() == 1 ? "Claim Rewards" : title(id.replace('-', ' ').replace('.', ' ')));
                    list.add(c);
                }
                v.add("choices", list);
                v.addProperty("choiceW", Math.min(480, (1072 - 25.6 * Math.max(0, list.size() - 1)) / Math.max(1, list.size())));
                v.addProperty("hint", choices.size() > 1 ? "Choose 1 of " + choices.size() : "");
                v.addProperty("promptBig", choices.size() > 1 ? "CHOOSE YOUR REWARD" : "REWARD READY");
                v.addProperty("promptSmall", choices.size() > 1 ? "Pick one to open the chamber" : "Claim it to open the chamber");
                boolean banner = s.has("banner") && s.get("banner").isJsonObject();
                v.addProperty("hasBanner", banner);
                if (banner) {
                    JsonObject b = s.getAsJsonObject("banner");
                    String ep = str(b, "epithet");
                    v.addProperty("bannerText", str(b, "name") + (ep.isEmpty() ? "" : ", " + ep));
                }
                String boon = str(s, "boonIcon");
                v.addProperty("boon", boon.isEmpty() ? "" : "Boon: " + title(boon));
                v.addProperty("boonTex", boon.matches("attack|defence|hp_pool|special_attack|special_defence|speed") ? "tex:cobbleraids/boon_" + boon + ".png" : "");
                v.addProperty("species", str(s, "species"));
                v.addProperty("tierName", str(s, "tierName"));
                String tc = str(s, "tierColor");
                v.addProperty("tierColor", tc.matches("#[0-9a-fA-F]{6}") ? tc : "#46e3ff");
                v.addProperty("raiders", String.valueOf(num(s, "participants")));
                if (banner) {
                    JsonObject b = s.getAsJsonObject("banner");
                    v.addProperty("bannerName", str(b, "name"));
                    v.addProperty("bannerEp", str(b, "epithet").isEmpty() ? "" : ", " + str(b, "epithet"));
                }
                v.add("slots", new JsonArray());
            }
            case "reveal_result" -> {
                if (!"CHOOSING".equals(str(v, "stage"))) return;
                data.add("rs", s);
                v.addProperty("stage", "RESULT");
                v.addProperty("beamOp", 0.35);
                v.addProperty("ok", bool(s, "success"));
                v.addProperty("hint", bool(s, "hasMore") ? "More rewards waiting" : "Claimed");
                JsonArray granted = new JsonArray();
                if (s.has("granted")) for (JsonElement e : s.getAsJsonArray("granted")) {
                    JsonObject g = e.getAsJsonObject();
                    JsonObject o = new JsonObject();
                    String icon = str(g, "icon");
                    o.addProperty("itemId", icon.startsWith("i:") ? icon.substring(2) : "");
                    o.addProperty("sprite", icon.startsWith("s:") ? icon : "");
                    o.addProperty("name", str(g, "name"));
                    long amt = num(g, "amount");
                    o.addProperty("amount", amt > 1 ? "x" + amt : "");
                    granted.add(o);
                }
                if (num(s, "points") > 0) granted.add(extra("Raid Points", "+" + grouped(num(s, "points"))));
                if (num(s, "currency") > 0) granted.add(extra("CobbleDollars", "+" + grouped(num(s, "currency"))));
                JsonArray rows = new JsonArray();
                JsonArray cur = null;
                for (int i = 0; i < granted.size(); i++) {
                    if (i % 6 == 0) { cur = new JsonArray(); JsonObject r = new JsonObject(); r.add("slots", cur); rows.add(r); }
                    cur.add(granted.get(i));
                }
                v.add("grows", rows);
                // the chamber's row of slots (reveal.css #row): 10.6cqw squares, 1.1cqw apart, centred, wrapping; the block sits at 50% - 38% of its height
                JsonArray slots = new JsonArray();
                int n = granted.size(), per = 5;
                double sz = 169.6, gap = 17.6, cw = 1072, ch = 567.2;
                int rowsN = Math.max(1, (n + per - 1) / per);
                double blockH = rowsN * sz + (rowsN - 1) * gap, top = ch * 0.5 - blockH * 0.38;
                for (int i = 0; i < n; i++) {
                    JsonObject g = granted.get(i).getAsJsonObject().deepCopy();
                    int r = i / per, inRow = Math.min(per, n - r * per), c = i % per;
                    double rowW = inRow * sz + (inRow - 1) * gap;
                    g.addProperty("x", Math.round((cw - rowW) / 2 + c * (sz + gap)));
                    g.addProperty("y", Math.round(top + r * (sz + gap)));
                    g.addProperty("delay", i * 90);
                    String nm = str(g, "name");
                    boolean rp = nm.equals("Raid Points") && !g.has("sprite") && str(g, "itemId").isEmpty(), cd = nm.equals("CobbleDollars") && str(g, "itemId").isEmpty() && !g.has("sprite");
                    g.addProperty("chip", rp || cd);
                    g.addProperty("border", rp ? "#1fb6c8" : cd ? "#d9a73a" : "#1d6f8f");
                    g.addProperty("valColor", rp ? "#8fe8f2" : "#ffd666");
                    g.addProperty("cnt", str(g, "amount").startsWith("x") ? "\u00d7" + str(g, "amount").substring(1) : "");
                    slots.add(g);
                }
                v.add("slots", slots);
            }
            default -> { }
        }
    }

    private static JsonObject extra(String name, String amount) {
        JsonObject o = new JsonObject();
        o.addProperty("itemId", "");
        o.addProperty("name", name);
        o.addProperty("amount", amount);
        return o;
    }

    // ------------------------------------------------------------------ raid capture (choice, three pulses, a throw, the result)

    private static void capture(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject v = vm(data);
        switch (event) {
            case "capture_offer" -> {
                data.add("co", s);
                v.addProperty("stage", "OFFER");
                v.addProperty("pidx", 0);
                v.addProperty("step", 0);
                JsonArray sc = new JsonArray();
                for (int i = 0; i < 3; i++) sc.add(0.0);
                v.add("scores", sc);
                v.addProperty("deadline", System.currentTimeMillis() + Math.max(0, num(s, "choiceSeconds")) * 1000L);
                v.addProperty("name", (bool(s, "shiny") ? "Shiny " : "") + str(s, "speciesName"));
                v.addProperty("tier", str(s, "tier").toUpperCase(Locale.ROOT));
                v.addProperty("rp", num(s, "rp"));
                v.addProperty("ok", false);
                v.remove("final");
                v.remove("delivery");
            }
            case "capture_pulse" -> {
                String stage = str(v, "stage");
                if (!(stage.equals("OFFER") || stage.equals("PULSE"))) return;
                long idx = num(s, "index");
                if (idx != num(v, "pidx")) return;
                JsonArray sc = v.getAsJsonArray("scores");
                double score = s.has("score") ? Math.max(0, Math.min(1, s.get("score").getAsDouble())) : 0;
                sc.set((int) Math.max(0, Math.min(2, idx)), new com.google.gson.JsonPrimitive(score));
                if (idx >= 2) v.addProperty("stage", "THROW");
                else { v.addProperty("stage", "PULSE"); v.addProperty("pidx", idx + 1); }
                v.addProperty("step", num(v, "step") + 1);
            }
            case "capture_result" -> {
                if ("RESULT".equals(str(v, "stage"))) return;
                v.addProperty("stage", "RESULT");
                v.addProperty("ok", bool(s, "success"));
                v.addProperty("final", String.format(Locale.ROOT, "%.1f%%", s.has("finalChance") ? s.get("finalChance").getAsDouble() : 0));
                v.addProperty("delivery", str(s, "delivery").toUpperCase(Locale.ROOT));
                v.addProperty("step", num(v, "step") + 1);
            }
            default -> { return; }
        }
        finishCapture(data, v);
    }

    private static void finishCapture(JsonObject data, JsonObject v) {
        JsonObject co = data.has("co") ? data.getAsJsonObject("co") : new JsonObject();
        String stage = str(v, "stage");
        JsonObject d = co.has("details") && co.get("details").isJsonObject() ? co.getAsJsonObject("details") : null;
        String capIcon = d != null ? str(d, "icon") : "";
        v.addProperty("sprite", capIcon.startsWith("s:") ? capIcon : spriteKey(str(co, "species"), bool(v, "shiny")));
        JsonArray sc = v.getAsJsonArray("scores");
        double avg = (sc.get(0).getAsDouble() + sc.get(1).getAsDouble() + sc.get(2).getAsDouble()) / 3;
        long pidx = num(v, "pidx");
        v.addProperty("pnum", pidx + 1);
        long done = stage.equals("OFFER") ? 0 : stage.equals("PULSE") ? pidx : 3;
        for (int i = 0; i < 3; i++) v.addProperty("lamp" + i, i < done ? "#46E3FF" : "#17475C");
        for (int i = 0; i < 3; i++) v.addProperty("lampBg" + i, i < done ? "#70f8ff" : "#001a30");
        JsonArray meter = new JsonArray();
        int filled = (int) Math.round(Math.max(0, Math.min(1, avg)) * 12);
        for (int i = 11; i >= 0; i--) { JsonObject mc = new JsonObject(); mc.addProperty("bg", i < filled ? "#46e3ff" : "#075385"); meter.add(mc); }   // column-reverse: index 0 at the bottom
        v.add("meter", meter);
        JsonObject t = stage.equals("THROW") && co.has("throwT") ? co.getAsJsonObject("throwT") : co.has("pulse") ? co.getAsJsonObject("pulse") : new JsonObject();
        v.addProperty("travel", Math.max(200, num(t, "travel")));
        v.addProperty("good", num(t, "good"));
        v.addProperty("perfect", num(t, "perfect"));
        v.addProperty("avgPct", Math.round(avg * 100));
        v.addProperty("state", stage.equals("RESULT") ? "COMPLETE" : stage.equals("THROW") ? "THROW READY" : stage.equals("PULSE") ? "STABILIZING" : "ECHO READY");
        v.addProperty("rpText", num(v, "rp") + (stage.equals("OFFER") ? " RP earned" : " RP reserved"));
        String odds;
        if (stage.equals("OFFER")) odds = d != null ? pct(d.get("base").getAsDouble()) + " - " + pct(Math.min(1, d.get("base").getAsDouble() + d.get("stabCap").getAsDouble() + d.get("throwCap").getAsDouble())) : "Server configured";
        else if (stage.equals("RESULT")) odds = "Final chance: " + str(v, "final");
        else odds = d != null ? pct(Math.min(1, d.get("base").getAsDouble() + avg * d.get("stabCap").getAsDouble())) + " + throw bonus" : "Server scored";
        v.addProperty("odds", odds);
        JsonArray rows = new JsonArray();
        for (int i = 0; i < 3; i++) {
            boolean isDone = i < done;
            double sv = sc.get(i).getAsDouble();
            String label = !isDone ? "--" : sv >= 1 ? "PERFECT" : sv >= 0.5 ? "GOOD" : "MISSED";
            JsonObject r = new JsonObject();
            r.addProperty("a", "Pulse " + (i + 1));
            r.addProperty("b", label);
            r.addProperty("color", !isDone ? "#7aa7bd" : sv >= 0.5 ? "#46e3ff" : "#ffc46b");
            rows.add(r);
        }
        v.add("pulseRows", rows);
        long rp = num(v, "rp");
        v.addProperty("keepText", "Keep " + rp + " RP");
        v.addProperty("outro1", "Caught: the Pokemon replaces this raid's RP. Escaped: keep all " + rp + " RP.");
        boolean ok = bool(v, "ok");
        v.addProperty("resultTitle", ok ? "CAPTURED!" : "THE ECHO ESCAPED");
        v.addProperty("result1", ok ? ("DELIVERED".equals(str(v, "delivery")) ? "Added to your party / PC." : "Captured! Delivery waits for party / PC space.") : "You keep all " + rp + " RP from this raid.");
        v.addProperty("result2", ok ? "Capture replaces this raid's " + rp + " RP. Other loot is yours." : "Final chance " + str(v, "final") + ". Other raid loot is yours.");
    }

    private static String pct(double f) { return String.format(Locale.ROOT, "%.1f%%", f * 100); }
}
