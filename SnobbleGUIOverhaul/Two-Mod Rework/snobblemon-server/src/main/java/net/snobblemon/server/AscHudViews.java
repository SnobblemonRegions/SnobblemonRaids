package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The AscensionLib battle HUD: bleed and Momentum badges on the left, brief Wardstone / Ensnaring flashes, and the scouting + team panel on the right.
 * It is display only (the HUD never takes the mouse): scouting and inspecting happen from /ascend scout and the AscensionDex. When nothing is left
 * to show for 2.5 seconds the view asks AscensionLib to close it, as the web page did.
 */
final class AscHudViews {
    private AscHudViews() {}

    static final String PAGE = "ascensionlib_hud";
    private static final long FLASH_MS = 3500;
    private static final long IDLE_MS = 2500;

    static void register() {
        SnobbleUi.registerView(PAGE, AscHudViews::apply);
    }

    private static JsonArray list(JsonObject s, String key) {
        JsonElement e = s.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject hs(JsonObject data) {
        if (!data.has("hs") || !data.get("hs").isJsonObject()) {
            JsonObject h = new JsonObject();
            h.add("entries", new JsonArray());
            h.addProperty("scouters", 0);
            h.add("team", new JsonArray());
            h.add("bleeds", new JsonArray());
            h.add("momentum", new JsonArray());
            h.add("flashes", new JsonArray());
            h.addProperty("idleSince", System.currentTimeMillis());
            data.add("hs", h);
        }
        return data.getAsJsonObject("hs");
    }

    private static boolean busy(JsonObject h) {
        return !list(h, "entries").isEmpty() || !list(h, "bleeds").isEmpty() || !list(h, "momentum").isEmpty() || !list(h, "flashes").isEmpty();
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject h = hs(data);
        long now = System.currentTimeMillis();
        switch (event) {
            case "hudScout" -> {
                h.add("entries", list(s, "entries"));
                h.addProperty("scouters", num(s, "scouters"));
            }
            case "hudTeam" -> h.add("team", list(s, "list"));
            case "hudBleed" -> h.add("bleeds", list(s, "list"));
            case "hudEffects" -> {
                h.add("momentum", list(s, "momentum"));
                JsonArray flashes = list(h, "flashes");
                for (JsonElement e : list(s, "procs")) {
                    JsonObject pr = e.getAsJsonObject();
                    String kind = str(pr, "kind");
                    if (!kind.equals("wardstone") && !kind.equals("ensnaring")) continue;
                    JsonObject f = new JsonObject();
                    f.addProperty("kind", kind);
                    f.addProperty("side", str(pr, "side"));
                    f.addProperty("name", str(pr, "name"));
                    f.addProperty("until", now + FLASH_MS);
                    flashes.add(f);
                }
                h.add("flashes", flashes);
            }
            case "hudClose" -> {
                if (busy(h)) return;
                SnobbleUi.forward(p, "ascension_hud_closed", new JsonObject());
                SnobbleUi.close(p, PAGE);
                return;
            }
            case "msg" -> {
                if (!str(s, "action").equals("tick")) return;
                JsonArray keep = new JsonArray();
                for (JsonElement e : list(h, "flashes")) if (num(e.getAsJsonObject(), "until") > now) keep.add(e);
                h.add("flashes", keep);
                if (busy(h)) h.addProperty("idleSince", now);
                else if (now - num(h, "idleSince") > IDLE_MS) {
                    h.addProperty("idleSince", now);        // ask again in 2.5 s if AscensionLib keeps it open
                    SnobbleUi.forward(p, "ascension_hud_idle", new JsonObject());
                }
            }
            default -> { return; }
        }
        if (busy(h)) h.addProperty("idleSince", now);
        data.add("vm", build(h));
    }

    private static JsonObject build(JsonObject h) {
        JsonObject v = new JsonObject();
        JsonArray badges = new JsonArray();
        for (JsonElement e : list(h, "bleeds")) {
            JsonObject b = e.getAsJsonObject();
            boolean foe = str(b, "side").equals("foe");
            long turns = num(b, "turnsLeft");
            badges.add(badge(str(b, "name") + "  BLEED x" + num(b, "stacks") + "  -  " + turns + (turns == 1 ? " turn" : " turns"), foe ? "#2A1A0B" : "#2A0B12", foe ? "#E08A2C" : "#C4283C", foe ? "#FFE6C8" : "#FFD6DA"));
        }
        for (JsonElement e : list(h, "momentum")) {
            JsonObject m = e.getAsJsonObject();
            boolean foe = str(m, "side").equals("foe");
            badges.add(badge(str(m, "name") + "  MOMENTUM x" + num(m, "stacks"), foe ? "#2A1A0B" : "#0B1C2A", foe ? "#E08A2C" : "#3D8FD1", foe ? "#FFE6C8" : "#D6EBFF"));
        }
        for (JsonElement e : list(h, "flashes")) {
            JsonObject f = e.getAsJsonObject();
            boolean foe = str(f, "side").equals("foe");
            String text = str(f, "name") + "  " + (str(f, "kind").equals("wardstone") ? "WARDSTONE: status resisted" : "ENSNARED: Speed lowered");
            badges.add(badge(text, foe ? "#2A1A0B" : "#0B1C2A", foe ? "#E08A2C" : "#3D8FD1", foe ? "#FFE6C8" : "#D6EBFF"));
        }
        v.add("badges", badges);
        JsonArray opp = new JsonArray();
        for (JsonElement e : list(h, "entries")) {
            JsonObject en = e.getAsJsonObject();
            JsonObject d = en.has("detail") && en.get("detail").isJsonObject() ? en.getAsJsonObject("detail") : null;
            opp.add(tag(str(en, "name"), d != null ? str(d, "rarityId") : ""));
        }
        v.add("opp", opp);
        JsonArray team = new JsonArray();
        for (JsonElement e : list(h, "team")) {
            JsonObject m = e.getAsJsonObject();
            team.add(tag(str(m, "name") + "  Lv " + num(m, "level"), str(m, "rarityId")));
        }
        v.add("team", team);
        v.addProperty("panel", !opp.isEmpty());
        v.addProperty("scouters", "Scouters: " + num(h, "scouters"));
        return v;
    }

    private static JsonObject badge(String text, String bg, String border, String color) {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        o.addProperty("bg", bg);
        o.addProperty("border", border);
        o.addProperty("color", color);
        return o;
    }

    private static JsonObject tag(String name, String rarityId) {
        String[] r = AscViews.RARITY.get(rarityId);
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("rarity", r != null ? r[0] : "?");
        o.addProperty("color", r != null ? r[1] : "#FFFFFF");
        o.addProperty("bg", r != null ? r[2] : "#46526A");
        return o;
    }
}
