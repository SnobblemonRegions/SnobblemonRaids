package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The AscensionLib Tower completion screen. AscensionLib has already paid out; this page only plays the reveal. A beat every 500 ms advances
 * the step (banner, each pile line, the crown rows rolling then revealed, the report), so each part appears in turn like the web page's timeline.
 */
final class CompletionViews {
    private CompletionViews() {}

    static final String PAGE = "ascensionlib_completion";

    static void register() {
        SnobbleUi.registerView(PAGE, CompletionViews::apply);
    }

    private static JsonArray arr(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        switch (event) {
            case "completionView" -> {
                data.add("cv", s);
                data.addProperty("step", 0);
            }
            case "msg" -> {
                if (!data.has("cv")) return;
                switch (str(s, "action")) {
                    case "tick" -> {
                        int step = (int) num(data, "step") + 1;
                        if (step > 400) return;       // the reveal is long over; stop resending
                        data.addProperty("step", step);
                        if (step % 3 == 0) SnobbleUi.forward(p, "ascension_screen_ping", new JsonObject());
                    }
                    case "replay" -> data.addProperty("step", 0);
                    case "share" -> {
                        JsonObject cv = data.getAsJsonObject("cv");
                        if (!bool(cv, "preview") && !bool(data, "shared")) {
                            data.addProperty("shared", true);
                            SnobbleUi.forward(p, "ascension_completion_share", new JsonObject());
                        }
                    }
                    case "finish" -> {
                        SnobbleUi.forward(p, "ascension_screen_release", new JsonObject());
                        SnobbleUi.close(p, PAGE);
                        return;
                    }
                    default -> { return; }
                }
            }
            default -> { return; }
        }
        data.add("vm", build(data.getAsJsonObject("cv"), (int) num(data, "step"), bool(data, "shared")));
    }

    private static JsonObject build(JsonObject cv, int step, boolean shared) {
        JsonObject v = new JsonObject();
        String tier = str(cv, "tier");
        String[] t = switch (tier) {
            case "bronze" -> new String[]{"#cd8a4b", "#2b1a0d"};
            case "gold" -> new String[]{"#ffd437", "#2b2308"};
            case "mythic" -> new String[]{"#ff3448", "#2e0710"};
            default -> new String[]{"#cfd6e4", "#1c2330"};
        };
        v.addProperty("tierColor", t[0]);
        v.addProperty("tierBg", t[1]);
        v.addProperty("banner", str(cv, "tierName") + " COMPLETION");
        v.addProperty("bannerOn", step >= 0);
        long cards = num(cv, "cards");
        v.addProperty("sub", "Score " + num(cv, "score") + "  -  " + cards + " challenge card" + (cards == 1 ? "" : "s") + "  -  cycle " + num(cv, "cycle") + "  -  " + (bool(cv, "flawless") ? "flawless" : "not flawless"));
        v.addProperty("msg", bool(cv, "preview") ? "Preview only: nothing was paid." : shared ? "Shared in chat." : "");
        v.addProperty("canShare", !bool(cv, "preview") && !shared);
        JsonArray tiers = new JsonArray();
        for (JsonElement e : arr(cv, "tiers")) {
            JsonObject o = e.getAsJsonObject();
            JsonObject m = new JsonObject();
            boolean on = num(cv, "score") >= num(o, "from");
            m.addProperty("text", str(o, "name") + " " + num(o, "from") + "+");
            m.addProperty("v", on ? 1 : 0);
            m.addProperty("color", on ? t[0] : "#16303F");
            tiers.add(m);
        }
        v.add("tiers", tiers);
        // the pile: one line per tick from step 2
        JsonArray pile = new JsonArray();
        JsonArray src = arr(cv, "pile");
        for (int i = 0; i < src.size(); i++) {
            if (step < 2 + i) break;
            JsonObject l = src.get(i).getAsJsonObject();
            JsonObject o = new JsonObject();
            o.addProperty("icon", "a:asc_" + str(l, "id"));
            o.addProperty("name", str(l, "name"));
            o.addProperty("color", str(l, "color").isEmpty() ? "#BDF1FF" : str(l, "color"));
            o.addProperty("amount", "+" + AscViews.grouped(num(l, "amount")));
            pile.add(o);
        }
        v.add("pile", pile);
        int crownAt = 2 + src.size() + 2;
        v.addProperty("crownLabel", step >= crownAt);
        JsonArray crowns = new JsonArray();
        JsonArray cs = arr(cv, "crowns");
        for (int i = 0; i < cs.size(); i++) {
            int appear = crownAt + 2 + i * 4;
            if (step < appear) break;
            JsonObject c = cs.get(i).getAsJsonObject();
            boolean revealed = step >= appear + 2;
            JsonObject o = new JsonObject();
            o.addProperty("revealed", revealed);
            o.addProperty("rolling", !revealed);
            o.addProperty("icon", revealed && !str(c, "id").isEmpty() ? "a:asc_" + str(c, "id") : "");
            o.addProperty("star", revealed && str(c, "id").isEmpty());
            o.addProperty("name", revealed ? str(c, "label") + (num(c, "amount") > 1 ? " x" + num(c, "amount") : "") : "Rolling...");
            o.addProperty("color", revealed ? (str(c, "color").isEmpty() ? "#BDF1FF" : str(c, "color")) : "#5DA6BD");
            o.addProperty("hint", !revealed ? "" : !str(c, "id").isEmpty() ? (bool(c, "big") ? "Headline item" : "Added to your wallet") : "Handed over by the Tower");
            o.addProperty("border", revealed && bool(c, "big") ? "#F2CF3A" : "#1F6B86");
            crowns.add(o);
        }
        v.add("crowns", crowns);
        int reportAt = crownAt + 2 + cs.size() * 4 + 1;
        v.addProperty("report", step >= reportAt ? "+" + num(cv, "attunement") + " attunement to your party  -  everything is in your wallet (AscensionDex, Wallet tab)" : "");
        return v;
    }
}
