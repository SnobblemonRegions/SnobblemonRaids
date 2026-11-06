package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Page "view models": small pieces of server code that turn a backing mod's events into the data its layout reads. They only reshape
 * data (add computed fields); the layouts do all the drawing. A feature without one just gets its event payload stored under the event name.
 */
final class PageViews {
    private PageViews() {}

    static void register() {
        SnobbleUi.registerView("rules", PageViews::rules);
        SnobbleUi.registerView("verify", PageViews::verify);
        SnobbleUi.registerView("goal", PageViews::goal);
        RaidViews.register();
        WikiViews.register();
        AscViews.register();
        CraftViews.register();
        FusionViews.register();
        CaptureRevealViews.register();
        CompletionViews.register();
        AscHudViews.register();
        CrateViews.register();
        WardrobeViews.register();
        ShellpackViews.register();
        GemsShopViews.register();
        AuctionViews.register();
        QuestDexViews.register();
        CasesViews.register();
        TowerViews.register();
        AdminViews.register();
        HubViews.register();
    }

    private static void toast(JsonObject data, String text, boolean ok) {
        JsonObject t = new JsonObject();
        t.addProperty("text", text);
        t.addProperty("ok", ok);
        data.add("_toast", t);
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    // ------------------------------------------------------------------ rules book

    private static void rules(ServerPlayer p, String event, JsonObject payload, JsonObject data) {
        switch (event) {
            case "rules_state" -> {
                data.add("st", payload);
                normalizeBlocks(payload);
                finishRules(data);
            }
            case "rules_progress" -> {
                if (!data.has("st")) return;
                data.getAsJsonObject("st").add("read", payload.has("read") ? payload.get("read") : new JsonArray());
                finishRules(data);
            }
            case "rules_result" -> {
                if (payload.has("ok") && payload.get("ok").getAsBoolean()) data.addProperty("done", true);
                else toast(data, str(payload, "message").isEmpty() ? "Something went wrong." : str(payload, "message"), false);
            }
            case "rules_notice" -> toast(data, str(payload, "text"), false);
            default -> { }
        }
    }

    /** The page accepted a list item as a plain string or as {icon,text}; the layout reads {text}. */
    private static void normalizeBlocks(JsonObject st) {
        JsonObject book = st.has("book") && st.get("book").isJsonObject() ? st.getAsJsonObject("book") : null;
        if (book == null || !book.has("pages")) return;
        for (JsonElement pg : book.getAsJsonArray("pages")) {
            if (!pg.isJsonObject() || !pg.getAsJsonObject().has("blocks")) continue;
            for (JsonElement bl : pg.getAsJsonObject().getAsJsonArray("blocks")) {
                if (!bl.isJsonObject() || !"list".equals(str(bl.getAsJsonObject(), "t"))) continue;
                JsonArray items = bl.getAsJsonObject().getAsJsonArray("items");
                if (items == null) continue;
                JsonArray fixed = new JsonArray();
                for (JsonElement it : items) {
                    if (it.isJsonPrimitive()) { JsonObject o = new JsonObject(); o.addProperty("text", it.getAsString()); fixed.add(o); }
                    else fixed.add(it);
                }
                bl.getAsJsonObject().add("items", fixed);
            }
        }
    }

    private static void finishRules(JsonObject data) {
        JsonObject st = data.getAsJsonObject("st");
        JsonArray pages = st.has("book") && st.getAsJsonObject("book").has("pages") ? st.getAsJsonObject("book").getAsJsonArray("pages") : new JsonArray();
        boolean accepted = st.has("accepted") && st.get("accepted").getAsBoolean();
        Set<Integer> read = new HashSet<>();
        if (st.has("read")) for (JsonElement e : st.getAsJsonArray("read")) read.add(e.getAsInt());
        JsonObject old = data.has("vm") ? data.getAsJsonObject("vm") : new JsonObject();
        JsonObject vm = new JsonObject();
        JsonArray list = new JsonArray();
        int count = 0, first = -1;
        for (int i = 0; i < pages.size(); i++) {
            JsonObject pg = pages.get(i).getAsJsonObject();
            boolean done = accepted || read.contains(i);
            if (done) count++;
            else if (first < 0) first = i;
            JsonObject row = new JsonObject();
            row.addProperty("title", str(pg, "title"));
            row.addProperty("done", done);
            row.addProperty("no", i + 1);
            row.addProperty("mark", done ? "✔" : Integer.toString(i + 1));
            row.addProperty("markbg", done ? "#1f9d55" : "#243250");
            row.addProperty("markBorder", done ? "#4ade80" : "#2b3a58");
            row.addProperty("markColor", done ? "#ffffff" : "#9fb0cb");
            row.addProperty("icon", str(pg, "icon"));
            list.add(row);
        }
        int total = pages.size();
        vm.add("pages", list);
        vm.addProperty("readCount", count);
        vm.addProperty("total", total);
        vm.addProperty("last", Math.max(0, total - 1));
        vm.addProperty("allRead", total > 0 && count >= total);
        vm.addProperty("confirmBorder", total > 0 && count >= total ? "#4ade80" : "#4a4f5c");
        vm.addProperty("confirmColor", total > 0 && count >= total ? "#ffffff" : "#8a909e");
        long minSec = st.has("minSeconds") ? st.get("minSeconds").getAsLong() : 3;
        vm.addProperty("minMs", Math.max(0, minSec) * 1000L);
        // the page the book opens on: the first unread one, only decided once
        if (old.has("startPage")) vm.add("startPage", old.get("startPage"));
        else vm.addProperty("startPage", accepted || first < 0 ? 0 : first);
        data.add("vm", vm);
    }

    // ------------------------------------------------------------------ rules roll

    private static void verify(ServerPlayer p, String event, JsonObject payload, JsonObject data) {
        if (!event.equals("verify_state")) return;
        if (payload.has("same")) { data.addProperty("live", true); return; }
        if (payload.has("denied")) {
            data.addProperty("denied", true);
            data.remove("vs");
            data.addProperty("live", true);
            return;
        }
        data.remove("denied");
        payload.addProperty("shown", payload.has("rows") ? payload.getAsJsonArray("rows").size() : 0);
        data.add("vs", payload);
        data.addProperty("live", true);
    }

    // ------------------------------------------------------------------ community goal

    private static void goal(ServerPlayer p, String event, JsonObject payload, JsonObject data) {
        if (event.equals("player_lookup")) {
            data.add("lk", payload);
            JsonObject vm = data.has("vm") ? data.getAsJsonObject("vm") : new JsonObject();
            vm.addProperty("lkShown", payload.has("results") ? payload.getAsJsonArray("results").size() : 0);
            data.add("vm", vm);
            return;
        }
        if (!event.equals("goal_state")) return;
        data.add("st", payload);
        JsonObject vm = data.has("vm") ? data.getAsJsonObject("vm") : new JsonObject();
        double pct = payload.has("percent") ? payload.get("percent").getAsDouble() : 0;
        vm.addProperty("togo", Math.max(0, 100 - (int) Math.floor(pct)));
        JsonObject me = payload.has("me") ? payload.getAsJsonObject("me") : new JsonObject();
        vm.addProperty("donations", payload.has("showAmounts") && payload.get("showAmounts").getAsBoolean() && me.has("donated")
                ? str(payload, "symbol") + str(me, "donated") : str(me, "donations"));
        vm.add("msRows", milestoneRows(payload, "milestones", "Reached!", "Coming up"));
        vm.add("dmRows", milestoneRows(payload, "donorMilestones", "Unlocked!", "Not yet"));
        vm.add("msList", milestoneList(payload, "milestones", "Reached!", "Coming up"));
        vm.add("dmList", milestoneList(payload, "donorMilestones", "Unlocked!", "Not yet"));
        vm.addProperty("barW", Math.max(0, Math.min(100, pct)) / 100.0 * (1000 - 56 - 6));     // the bar's inner width: panel 1000 - main padding 56 - border 6
        String nm = str(me, "name");
        String[] av = {"#ffb3d1", "#ffd9a0", "#c4f5b4", "#a8dcff", "#d2b8ff", "#fff0a0"};
        vm.addProperty("meAv", av[nm.length() % av.length]);
        data.add("vm", vm);
    }

    /** Milestone chips (one list; the layout's grid makes the rows), with their text and colours worked out. */
    private static JsonArray milestoneList(JsonObject st, String key, String hitText, String missText) {
        JsonArray out = new JsonArray();
        for (JsonElement row : milestoneRows(st, key, hitText, missText)) for (JsonElement c : row.getAsJsonObject().getAsJsonArray("items")) out.add(c);
        return out;
    }

    /** Milestone chips in rows of four (the page was a 4-column grid), with their text and colours worked out. */
    private static JsonArray milestoneRows(JsonObject st, String key, String hitText, String missText) {
        JsonArray rows = new JsonArray();
        if (!st.has(key)) return rows;
        String sym = str(st, "symbol");
        JsonArray cur = null;
        int i = 0;
        for (JsonElement e : st.getAsJsonArray(key)) {
            JsonObject m = e.getAsJsonObject();
            boolean hit = m.has("hit") && m.get("hit").getAsBoolean();
            JsonObject c = new JsonObject();
            String name = m.has("amount") ? sym + str(m, "amount") : str(m, "label");
            c.addProperty("title", (hit ? "✔ " : "") + name);
            c.addProperty("sub", hit ? hitText : (m.has("togo") ? sym + str(m, "togo") + " to go" : missText));
            c.addProperty("bg", hit ? "#17503a" : "#13263f");
            c.addProperty("border", hit ? "#8cf5c4" : "#2c5080");
            if (i % 4 == 0) { cur = new JsonArray(); JsonObject r = new JsonObject(); r.add("items", cur); rows.add(r); }
            cur.add(c);
            i++;
        }
        return rows;
    }
}
