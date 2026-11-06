package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The AscensionLib "capture reveal" card. AscensionLib keeps the queue and sends one captureReveal at a time (then captureClosed when none are left),
 * so this view only draws the current card, counts down its 15 seconds, and passes Explore / Continue / Disable back to AscensionLib.
 */
final class CaptureRevealViews {
    private CaptureRevealViews() {}

    static final String PAGE = "ascensionlib_capture";
    private static final int SHOW_SECONDS = 12;                // capture.js SHOW_MS = 12000: the clock starts when the 1.8 s entrance is over and stops while the mouse is on the card
    private static final String[] SECONDARY = {"#bdf1ff", "#9affd5", "#a9eaff", "#65ecff", "#fff3b3", "#bd8bff"};
    private static final String[] RARITY_IDS = {"common", "uncommon", "rare", "epic", "legendary", "mythical"};

    static void register() {
        SnobbleUi.registerView(PAGE, CaptureRevealViews::apply);
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        switch (event) {
            case "captureReveal" -> {
                JsonObject d = s.has("detail") && s.get("detail").isJsonObject() ? s.getAsJsonObject("detail") : null;
                if (str(s, "pokemonId").isEmpty() || d == null || !java.util.Arrays.asList(RARITY_IDS).contains(str(d, "rarityId")) || !d.has("slots") || !d.get("slots").isJsonArray()) return;
                data.add("reveal", s);
                data.addProperty("left", SHOW_SECONDS);
                data.addProperty("flip", 2);
                data.add("vm", build(s, SHOW_SECONDS));
            }
            case "captureClosed" -> { if (System.getProperty("snobble.rig") != null) return; SnobbleUi.close(p, PAGE); }     // test rig only: the real AscensionLib answers captureClosed at once (its queue is empty); a sample card must stay
            case "msg" -> {
                JsonObject r = data.has("reveal") && data.get("reveal").isJsonObject() ? data.getAsJsonObject("reveal") : null;
                if (r == null) return;
                String id = str(r, "revealId").isEmpty() ? str(r, "pokemonId") : str(r, "revealId");
                switch (str(s, "action")) {
                    case "tick" -> {
                        if ((int) num(data, "flip") > 0) { data.addProperty("flip", (int) num(data, "flip") - 1); return; }     // the entrance is still turning the card
                        if ((int) num(s, "hov") == 1) return;                                                                      // the mouse is on the card: the page pauses its timer
                        int left = (int) num(data, "left") - 1;
                        if (left <= 0) { data.remove("reveal"); data.add("vm", new JsonObject()); dismiss(p, id); return; }
                        data.addProperty("left", left);
                        data.add("vm", build(r, left));
                    }
                    case "explore" -> {
                        data.remove("reveal");
                        data.add("vm", new JsonObject());
                        JsonObject m = new JsonObject();
                        m.addProperty("id", str(r, "pokemonId"));
                        m.addProperty("revealId", id);
                        SnobbleUi.forward(p, "ascension_capture_open", m);
                    }
                    case "continue" -> { data.remove("reveal"); data.add("vm", new JsonObject()); dismiss(p, id); }
                    case "disable" -> {
                        data.remove("reveal");
                        SnobbleUi.forward(p, "ascension_capture_disable", new JsonObject());
                        SnobbleUi.close(p, PAGE);
                    }
                    default -> { }
                }
            }
            default -> { }
        }
    }

    private static void dismiss(ServerPlayer p, String id) {
        JsonObject m = new JsonObject();
        m.addProperty("revealId", id);
        SnobbleUi.forward(p, "ascension_capture_dismiss", m);
    }

    private static JsonObject build(JsonObject r, int left) {
        JsonObject d = r.getAsJsonObject("detail");
        String rid = str(d, "rarityId");
        String[] rar = AscViews.RARITY.get(rid);
        int index = java.util.Arrays.asList(RARITY_IDS).indexOf(rid);
        JsonObject v = new JsonObject();
        v.addProperty("name", str(r, "name"));
        String sid = str(r, "speciesId").replaceAll("^.*:", "");
        v.addProperty("sprite", sid.isEmpty() ? "" : RaidViews.spriteKey(sid, false));
        v.addProperty("rarity", (rid.equals("mythical") ? "MYTHIC" : rid.toUpperCase(Locale.ROOT)));
        v.addProperty("color", rar != null ? rar[1] : "#C1C5CF");
        v.addProperty("bg", rar != null ? rar[2] : "#293547");
        JsonArray traits = new JsonArray();
        Set<String> seen = new HashSet<>();
        String uniq = str(d, "uniqueName");
        if (!uniq.isEmpty()) traits.add(trait(uniq, "", "Unique power", true));
        for (JsonElement e : d.getAsJsonArray("slots")) {
            JsonObject sl = e.getAsJsonObject();
            if (!seen.add(str(sl, "category") + ":" + str(sl, "name"))) continue;
            long rank = num(sl, "rank");
            traits.add(trait(str(sl, "name"), "+" + num(sl, "value") + "%", "Rank " + (rank >= 1 && rank <= 5 ? new String[]{"", "I", "II", "III", "IV", "V"}[(int) rank] : String.valueOf(rank)), false));
        }
        v.add("traits", traits);
        v.addProperty("traitsLabel", traits.size() == 1 ? "ASCENSION TRAIT DISCOVERED" : traits.size() > 2 ? "MODIFIERS (" + traits.size() + ")" : "ASCENSION TRAITS DISCOVERED");
        v.addProperty("sub", index == 5 ? "A Mythic partner. Make it your own." : "Every Pokemon can reach Mythic.");
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < 6; i++) path.append(i <= index ? "#" : "-").append(i < 5 ? " " : "");
        v.addProperty("path", path.toString());
        JsonArray pips = new JsonArray();
        for (int i = 0; i < 6; i++) { JsonObject pi = new JsonObject(); pi.addProperty("v", i <= index ? 1 : 0); pi.addProperty("c", i <= index ? (rar != null ? rar[1] : "#4AD8F6") : "#16303F"); pips.add(pi); }
        v.add("pips", pips);
        v.addProperty("left", "Closes in " + left + "s");
        v.addProperty("anim", true);
        v.addProperty("bezel", AscViews.bezel(rid).replace("/bezel_", "/capbezel_"));
        v.addProperty("portrait", "tex:ascensionlib/cap_portrait_" + Math.max(0, index) + ".png");
        int nt = traits.size();
        v.addProperty("nTraits", nt);
        int hv = nt <= 1 ? 1 : nt == 2 ? 2 : 3;
        v.addProperty("hv", hv);
        int cardH = hv == 1 ? 577 : hv == 2 ? 628 : 647;
        v.addProperty("fxText", "220," + (320) + ",440," + Math.max(0, index) + ",440," + cardH);
        v.addProperty("fxCols", (rar != null ? rar[1] : "#C1C5CF") + "|" + SECONDARY[Math.max(0, index)]);
        v.addProperty("revealKey", str(r, "revealId").isEmpty() ? str(r, "pokemonId") : str(r, "revealId"));
        return v;
    }

    private static JsonObject trait(String name, String value, String sub, boolean unique) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("value", value);
        o.addProperty("sub", sub);
        o.addProperty("icon", AscViews.affixIcon(name, unique));
        return o;
    }
}
