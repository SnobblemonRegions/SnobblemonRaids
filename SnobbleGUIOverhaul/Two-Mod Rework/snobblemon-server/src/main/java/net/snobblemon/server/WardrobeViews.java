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
 * The SnobbleCosmetics wardrobe. The cosmetics mod still decides ownership and applies everything; this view only holds the tab, filters, sort,
 * page and the open item, builds the cards from the mod's "state" event, and passes the player's equip / try-on / skin clicks back to it.
 */
final class WardrobeViews {
    private WardrobeViews() {}

    static final String PAGE = "wardrobe";
    private static final String CHANNEL = "snobblecosmetics";
    private static final int PER = 8;
    private static final String[][] CATS = {{"hats", "Hats"}, {"back", "Back"}, {"armor", "Armor"}, {"tools", "Tool Skins"}, {"pokemon", "Pokemon Skins"}, {"balloons", "Balloons"}, {"effects", "Effects"}};
    private static final String[] RARITY = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
    private static final String[] RCOLOR = {"#C1C5CF", "#1BD7E8", "#38CCEF", "#AF69FF", "#FFD437", "#FF3448"};
    private static final List<String> SLOT_ORDER = List.of("Helmet", "Chestplate", "Leggings", "Boots", "Back");
    private static final Map<UUID, JsonObject> STATE = new ConcurrentHashMap<>();        // per player: the mod's last state event
    private static final Map<UUID, JsonObject> PARTY = new ConcurrentHashMap<>();

    static void register() {
        SnobbleUi.registerView(PAGE, WardrobeViews::apply);
    }

    private static JsonArray arr(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    private static String pretty(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.replace('_', ' ').split(" ")) if (!w.isEmpty()) sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return sb.toString();
    }

    private static boolean worn(JsonObject i) { return bool(i, "equipped") || num(i, "wornBy") > 0; }

    private static JsonObject fresh() {
        JsonObject st = new JsonObject();
        st.addProperty("cat", "hats");
        st.addProperty("owned", false);
        st.addProperty("wornOnly", false);
        st.addProperty("sort", "rarity");
        st.addProperty("page", 0);
        st.addProperty("sel", "");
        st.addProperty("partyMode", false);
        return st;
    }

    private static JsonObject find(JsonObject state, String id) {
        for (JsonElement e : arr(state, "items")) if (str(e.getAsJsonObject(), "id").equals(id)) return e.getAsJsonObject();
        return null;
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : fresh();
        switch (event) {
            case "state" -> {
                if (!s.has("items")) return;
                STATE.put(p.getUUID(), s);
                if (!str(st, "sel").isEmpty() && find(s, str(st, "sel")) == null) { st.addProperty("sel", ""); st.addProperty("partyMode", false); }
            }
            case "party" -> {
                PARTY.put(p.getUUID(), s);
            }
            case "result" -> {
                if (!str(s, "message").isEmpty()) {
                    JsonObject t = new JsonObject();
                    t.addProperty("text", str(s, "message"));
                    t.addProperty("ok", bool(s, "ok"));
                    data.add("_toast", t);
                }
                if (bool(s, "ok") && bool(s, "close")) SnobbleUi.close(p, PAGE);
            }
            case "msg" -> {
                JsonObject state = STATE.get(p.getUUID());
                if (state == null) return;
                message(p, state, st, s);
            }
            default -> { return; }
        }
        JsonObject state = STATE.get(p.getUUID());
        data.add("st", st);
        if (state != null) data.add("vm", build(state, st, PARTY.get(p.getUUID())));
    }

    private static void act(ServerPlayer p, String action, String id) {
        JsonObject m = new JsonObject();
        m.addProperty("action", action);
        m.addProperty("id", id);
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static void message(ServerPlayer p, JsonObject state, JsonObject st, JsonObject m) {
        String a = str(m, "action"), k = str(m, "k");
        switch (a) {
            case "cat" -> { for (String[] c : CATS) if (c[0].equals(k) && !k.equals(str(st, "cat"))) { st.addProperty("cat", k); st.addProperty("page", 0); play(st); } }
            case "filter" -> {
                if (k.equals("owned")) st.addProperty("owned", !bool(st, "owned")); else if (k.equals("worn")) st.addProperty("wornOnly", !bool(st, "wornOnly"));
                st.addProperty("page", 0);
                play(st);
            }
            case "sort" -> { if (k.equals("rarity") || k.equals("name") || k.equals("owned")) { st.addProperty("sort", k); st.addProperty("page", 0); play(st); } }
            case "page" -> {
                int before = (int) num(st, "page"), want = Math.max(0, before + (int) num(m, "d"));
                int pages = Math.max(1, (visible(state, st).size() + PER - 1) / PER);
                want = Math.min(want, pages - 1);
                if (want != before) { st.addProperty("page", want); play(st); }
            }
            case "open" -> {
                if (find(state, k) != null) {
                    if (str(st, "sel").isEmpty()) st.addProperty("openN", num(st, "openN") + 1);
                    st.addProperty("sel", k);
                    st.addProperty("partyMode", false);
                    PARTY.remove(p.getUUID());
                }
            }
            case "close" -> { st.addProperty("sel", ""); st.addProperty("partyMode", false); }
            case "party" -> {
                JsonObject i = find(state, str(st, "sel"));
                if (i != null && bool(i, "owned")) { st.addProperty("partyMode", true); PARTY.remove(p.getUUID()); act(p, "party", str(i, "id")); }
            }
            case "back" -> { if (bool(st, "partyMode")) st.addProperty("partyMode", false); else st.addProperty("sel", ""); }
            case "quick" -> {
                // a card's own button: equip / take off / try on act at once; a Pokemon skin opens the item and asks for the party
                JsonObject i = find(state, k);
                if (i == null) return;
                if (!bool(i, "owned")) { if (bool(i, "wearable")) act(p, "tryon", k); return; }
                if (bool(i, "wearable")) { act(p, worn(i) ? "unequip" : "equip", k); return; }
                if (str(i, "cat").equals("tools")) { act(p, "tool", k); return; }
                if (str(st, "sel").isEmpty()) st.addProperty("openN", num(st, "openN") + 1);
                st.addProperty("sel", k);
                st.addProperty("partyMode", true);
                PARTY.remove(p.getUUID());
                act(p, "party", k);
            }
            case "main" -> {
                JsonObject i = find(state, str(st, "sel"));
                if (i == null || !bool(i, "owned")) return;
                if (bool(i, "wearable")) act(p, worn(i) ? "unequip" : "equip", str(i, "id"));
                else if (str(i, "cat").equals("tools")) act(p, "tool", str(i, "id"));
                else { st.addProperty("partyMode", true); PARTY.remove(p.getUUID()); act(p, "party", str(i, "id")); }
            }
            case "try" -> {
                JsonObject i = find(state, str(st, "sel"));
                if (i != null && bool(i, "wearable")) act(p, "tryon", str(i, "id"));
            }
            case "skin" -> {
                JsonObject i = find(state, str(st, "sel"));
                JsonObject party = PARTY.get(p.getUUID());
                if (i == null || party == null || !str(party, "id").equals(str(i, "id"))) return;
                for (JsonElement e : arr(party, "members")) {
                    JsonObject mem = e.getAsJsonObject();
                    if (!bool(mem, "empty") && bool(mem, "fits") && num(mem, "slot") == num(m, "slot")) {
                        JsonObject out = new JsonObject();
                        out.addProperty("action", "skin");
                        out.addProperty("id", str(i, "id"));
                        out.addProperty("slot", num(mem, "slot"));
                        SnobbleUi.forward(p, CHANNEL, out);
                        break;
                    }
                }
            }
            case "offall" -> act(p, "unequipall", "");
            default -> { }
        }
    }

    private static void play(JsonObject st) { st.addProperty("play", num(st, "play") + 1); }

    // ------------------------------------------------------------------ view model

    private static List<JsonObject> visible(JsonObject state, JsonObject st) {
        List<JsonObject> list = new ArrayList<>();
        for (JsonElement e : arr(state, "items")) {
            JsonObject i = e.getAsJsonObject();
            if (!str(i, "cat").equals(str(st, "cat"))) continue;
            if (bool(st, "owned") && !bool(i, "owned")) continue;
            if (bool(st, "wornOnly") && !worn(i)) continue;
            list.add(i);
        }
        String sort = str(st, "sort");
        list.sort((a, b) -> {
            if (sort.equals("name")) return str(a, "name").compareToIgnoreCase(str(b, "name"));
            if (sort.equals("owned") && bool(a, "owned") != bool(b, "owned")) return bool(a, "owned") ? -1 : 1;
            int d = (int) (num(b, "rank") - num(a, "rank"));
            return d != 0 ? d : str(a, "name").compareToIgnoreCase(str(b, "name"));
        });
        return list;
    }

    private static String sub(JsonObject i) {
        if (!str(i, "set").isEmpty()) return pretty(str(i, "set")) + " set";
        if (str(i, "cat").equals("tools") && !str(i, "target").isEmpty()) return "Fits " + str(i, "target");
        if (str(i, "cat").equals("pokemon") && !str(i, "species").isEmpty()) return pretty(str(i, "species")) + " skin";
        JsonArray d = arr(i, "desc");
        return d.isEmpty() ? str(i, "slot") : d.get(0).getAsString();
    }

    private static String goLabel(JsonObject i) {
        if (!bool(i, "owned")) return bool(i, "wearable") ? "TRY ON" : "LOCKED";
        if (bool(i, "wearable")) return worn(i) ? "TAKE OFF" : "EQUIP";
        return str(i, "cat").equals("tools") ? "APPLY" : "CHOOSE";
    }

    // ---- the original page's look (wardrobe/style.css)
    private static final String EDGE = "#8465a8", ACC = "#e6cffa", TXT = "#efe7f7", DIM = "#a898bd", GOLD = "#ffd24a", GREEN = "#4dffb0", RED = "#ff6b7a", BTNFG = "#1a1026";
    private static final String[] RC = {"#e6cffa", "#4dffb0", "#5fa8ff", "#b36bff", "#ffd24a", "#ff4fe0"};
    private static final Map<String, Double> TAB_W = Map.of("hats", 70.5, "back", 72.0, "armor", 85.0, "tools", 130.5, "pokemon", 169.5, "balloons", 116.5, "effects", 99.0);
    private static final String[][] SORTS = {{"rarity", "Rarity", "68.2"}, {"name", "Name", "52.8"}, {"owned", "Owned", "60.5"}};
    private static final Map<String, String> SLOT_ABBR = Map.of("Helmet", "HE", "Chestplate", "CH", "Leggings", "LE", "Boots", "BO", "Back", "BA");
    private static final String T = "tex:wardrobe/", S = "sprite:wardrobe/";

    /** Width of text in the page's Pixel font (Silkscreen Bold advance widths, 1000 units per em) plus letter spacing. */
    static double pixW(String t, double px, double ls) {
        double u = 0;
        for (char ch : t.toCharArray()) {
            u += switch (Character.toUpperCase(ch)) {
                case 'E', 'F', 'L', 'T', 'Z', '1', '<', '>', '/', '-' -> 750;
                case 'I', ' ' -> 500;
                case 'M', 'N', 'V', 'W', 'X', 'Y' -> 1000;
                default -> 875;
            };
        }
        return u * px / 1000 + ls * t.length();
    }

    private static int rank(JsonObject i) { return (int) Math.max(0, Math.min(5, num(i, "rank"))); }

    private static String nameColor(JsonObject i) {
        String c1 = str(i, "c1"), c2 = str(i, "c2");
        if (!c1.matches("#[0-9a-fA-F]{6}")) return TXT;
        String a = lift(c1), b = c2.matches("#[0-9a-fA-F]{6}") ? lift(c2) : a;
        return a.equalsIgnoreCase(b) ? a : "grad:" + a + "," + b;
    }

    /** Dark name colours would vanish on the dark panel: lighten them to a readable level (the page's lift()). */
    private static String lift(String hex) {
        int n = Integer.parseInt(hex.substring(1), 16);
        int r = n >> 16 & 255, g = n >> 8 & 255, b = n & 255;
        double lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
        if (lum >= 0.5) return hex.toLowerCase(Locale.ROOT);
        double k = (0.5 - lum) / (1 - lum) + 0.05;
        return String.format(Locale.ROOT, "#%02x%02x%02x", Math.min(255, (int) Math.round(r + (255 - r) * k)), Math.min(255, (int) Math.round(g + (255 - g) * k)), Math.min(255, (int) Math.round(b + (255 - b) * k)));
    }

    private static String thumb(JsonObject i) {
        String id = "thumb_" + str(i, "id").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (id.length() > 44) id = id.substring(0, 44);
        return "w:" + id + ":" + (bool(i, "owned") ? 0 : 1);
    }

    private static int nameSize(String n) {
        int l = n.length();
        return l <= 18 ? 21 : l <= 20 ? 19 : l <= 23 ? 17 : l <= 26 ? 15 : 14;
    }

    private static String emptyText(String cat, String catName) {
        return switch (cat) {
            case "back" -> "No back items yet.";
            case "armor" -> "No armor sets yet.";
            case "tools" -> "No tool skins yet.";
            case "pokemon" -> "No Pokemon skins yet.";
            default -> "No " + catName.toLowerCase(Locale.ROOT) + " yet.";
        };
    }

    private static JsonObject build(JsonObject state, JsonObject st, JsonObject partyData) {
        JsonObject v = new JsonObject();
        String cat = str(st, "cat");
        v.addProperty("owned", num(state, "owned") + " / " + num(state, "total"));
        v.addProperty("worn", String.valueOf(num(state, "worn")));
        v.addProperty("play", num(st, "play"));
        v.addProperty("openN", num(st, "openN"));
        JsonArray tabs = new JsonArray();
        for (String[] c : CATS) {
            JsonObject t = new JsonObject();
            boolean on = c[0].equals(cat);
            t.addProperty("k", c[0]);
            t.addProperty("label", c[1].toUpperCase(Locale.ROOT));
            t.addProperty("w", TAB_W.getOrDefault(c[0], pixW(c[1], 12, 1) + 26));
            t.addProperty("on", on);
            t.addProperty("bg", S + (on ? "tab_on" : "tab"));
            t.addProperty("hv", S + (on ? "tab_on" : "tab_hover"));
            t.addProperty("color", on ? BTNFG : DIM);
            tabs.add(t);
        }
        v.add("tabs", tabs);
        List<JsonObject> mine = new ArrayList<>();
        int own = 0;
        for (JsonElement e : arr(state, "items")) if (str(e.getAsJsonObject(), "cat").equals(cat)) { mine.add(e.getAsJsonObject()); if (bool(e.getAsJsonObject(), "owned")) own++; }
        String catName = "";
        for (String[] c : CATS) if (c[0].equals(cat)) catName = c[1];
        v.addProperty("collName", catName);
        v.addProperty("collText", own + " / " + mine.size() + " owned");
        v.addProperty("collPct", mine.isEmpty() ? 0 : Math.round(100.0 * own / mine.size()));
        v.addProperty("collW", mine.isEmpty() ? 0 : Math.round(100.0 * own / mine.size()) * 2.48);
        v.addProperty("fOwned", bool(st, "owned"));
        v.addProperty("fWorn", bool(st, "wornOnly"));
        JsonArray sorts = new JsonArray();
        for (String[] so : SORTS) {
            JsonObject o = new JsonObject();
            boolean on = str(st, "sort").equals(so[0]);
            o.addProperty("k", so[0]);
            o.addProperty("label", so[1]);
            o.addProperty("w", Double.parseDouble(so[2]));
            o.addProperty("bg", on ? ACC : "#36254f99");
            o.addProperty("hv", on ? ACC : "#36254f");
            o.addProperty("border", on ? "" : EDGE);
            o.addProperty("color", on ? BTNFG : TXT);
            sorts.add(o);
        }
        v.add("sorts", sorts);
        List<JsonObject> list = visible(state, st);
        int pages = Math.max(1, (list.size() + PER - 1) / PER);
        int page = Math.min((int) num(st, "page"), pages - 1);
        st.addProperty("page", page);
        v.addProperty("pageLabel", "PAGE " + (page + 1) + " / " + pages);
        boolean canPrev = page > 0, canNext = page < pages - 1;
        v.addProperty("prevOp", canPrev ? 1 : 0.35);
        v.addProperty("nextOp", canNext ? 1 : 0.35);
        v.addProperty("prevHv", S + (canPrev ? "ghost_hover" : "ghost"));
        v.addProperty("nextHv", S + (canNext ? "ghost_hover" : "ghost"));
        JsonArray cards = new JsonArray();
        for (int n = 0; n < PER && page * PER + n < list.size(); n++) {
            JsonObject i = list.get(page * PER + n), c = new JsonObject();
            int r = rank(i);
            boolean owned = bool(i, "owned"), wornNow = worn(i), wearable = bool(i, "wearable");
            String base = T + "card_r" + r + (wornNow ? "_worn" : "");
            c.addProperty("id", str(i, "id"));
            c.addProperty("x", (n % 4) * 290);
            c.addProperty("y", (n / 4) * 340);
            c.addProperty("delay", n * 45 + 60);
            c.addProperty("f", (n % 5) * -700);
            c.addProperty("shineDelay", n * 500);
            c.addProperty("bg", base + ".png");
            c.addProperty("hv", base + "_hv.png");
            c.addProperty("epic", r >= 4);
            c.addProperty("pulse", T + "pulse_r" + Math.max(4, r) + ".png");
            c.addProperty("shine", r >= 3);
            c.addProperty("thumb", thumb(i));
            c.addProperty("slot", str(i, "slot").toUpperCase(Locale.ROOT));
            c.addProperty("state", wornNow ? "WORN" : owned ? "OWNED" : "LOCKED");
            c.addProperty("stateColor", wornNow ? GREEN : owned ? DIM : RED);
            c.addProperty("stateBorder", wornNow ? GREEN : owned ? EDGE : RED);
            c.addProperty("name", str(i, "name"));
            c.addProperty("nameColor", nameColor(i));
            c.addProperty("nsize", nameSize(str(i, "name")));
            c.addProperty("nameOp", owned ? 1 : 0.7);
            c.addProperty("sub", sub(i));
            c.addProperty("rarity", str(i, "rarity").isEmpty() ? RARITY[r] : str(i, "rarity"));
            c.addProperty("rc", RC[r]);
            String go = goLabel(i);
            boolean take = wornNow && wearable, off = !owned && !wearable;
            c.addProperty("go", go);
            c.addProperty("goW", pixW(go, 12, 1) + 24);
            c.addProperty("goBg", S + (take ? "go_take" : "go"));
            c.addProperty("goHv", S + (off ? "go" : take ? "go_take_hover" : "go_hover"));
            c.addProperty("goColor", take ? GREEN : TXT);
            c.addProperty("goHvColor", off ? TXT : take ? "#07251a" : BTNFG);
            c.addProperty("goOp", off ? 0.4 : 1);
            cards.add(c);
        }
        v.add("cards", cards);
        boolean empty = list.isEmpty(), none = mine.isEmpty();
        v.addProperty("empty", empty);
        v.addProperty("emptyTitle", empty && !none);
        v.addProperty("emptyText", none ? emptyText(cat, catName) : "Try another search or turn a filter off.");
        v.addProperty("emptyColor", none ? TXT : DIM);
        String sel = str(st, "sel");
        JsonObject i = sel.isEmpty() ? null : find(state, sel);
        v.addProperty("detail", i != null);
        v.addProperty("partyMode", i != null && bool(st, "partyMode"));
        if (i != null) v.add("d", detail(state, i, bool(st, "partyMode"), partyData));
        return v;
    }

    private static JsonObject tagOf(String text, String color, String border) {
        JsonObject t = new JsonObject();
        t.addProperty("text", text);
        t.addProperty("color", color);
        t.addProperty("border", border);
        return t;
    }

    private static JsonObject detail(JsonObject state, JsonObject i, boolean partyMode, JsonObject partyData) {
        JsonObject d = new JsonObject();
        boolean owned = bool(i, "owned"), wearable = bool(i, "wearable"), wornNow = worn(i);
        int r = rank(i);
        int preview = num(state, "previewSeconds") == 0 ? 15 : (int) num(state, "previewSeconds");
        d.addProperty("id", str(i, "id"));
        d.addProperty("glow", T + "glow_r" + r + ".png");
        d.addProperty("ring", T + "ring_r" + r + ".png");
        d.addProperty("thumb", thumb(i));
        d.addProperty("slot", str(i, "slot") + (str(i, "set").isEmpty() ? "" : "  /  " + pretty(str(i, "set")) + " set"));
        d.addProperty("name", str(i, "name"));
        d.addProperty("nameColor", nameColor(i));
        JsonArray tags = new JsonArray();
        tags.add(tagOf(str(i, "rarity").isEmpty() ? RARITY[r] : str(i, "rarity"), RC[r], RC[r]));
        tags.add(wornNow ? tagOf("WORN", GREEN, GREEN) : owned ? tagOf("OWNED", DIM, EDGE) : tagOf("LOCKED", RED, RED));
        if (bool(i, "glint")) tags.add(tagOf("RADIANT", RC[5], RC[5]));
        d.add("tags", tags);
        JsonArray desc = new JsonArray();
        for (JsonElement e : arr(i, "desc")) desc.add(e.getAsString());
        d.add("desc", desc);
        JsonArray extra = new JsonArray();
        if (str(i, "cat").equals("tools") && !str(i, "target").isEmpty()) extra.add("Fits: **" + str(i, "target").replace("*", "") + "**");
        if (str(i, "cat").equals("pokemon")) {
            extra.add("For: **" + pretty(str(i, "species")).replace("*", "") + "**");
            if (num(i, "wornBy") > 0) extra.add("Worn by: **" + (int) num(i, "wornBy") + " Pokemon**");
        }
        if (bool(i, "free")) extra.add("Unlocked: **for everyone**");
        d.add("extra", extra);
        d.addProperty("hasExtra", !extra.isEmpty());
        // the set
        JsonArray set = new JsonArray();
        String setName = str(i, "set");
        if (!setName.isEmpty()) {
            List<JsonObject> mates = new ArrayList<>();
            for (JsonElement e : arr(state, "items")) if (setName.equals(str(e.getAsJsonObject(), "set"))) mates.add(e.getAsJsonObject());
            mates.sort((a, b) -> SLOT_ORDER.indexOf(str(a, "slot")) - SLOT_ORDER.indexOf(str(b, "slot")));
            if (mates.size() > 1) {
                long have = mates.stream().filter(x -> bool(x, "owned")).count();
                d.addProperty("setLabel", pretty(setName).toUpperCase(Locale.ROOT) + " SET  " + have + "/" + mates.size());
                for (JsonObject x : mates) {
                    JsonObject c = new JsonObject();
                    String slot = str(x, "slot");
                    boolean cur = str(x, "id").equals(str(i, "id")), have1 = bool(x, "owned");
                    c.addProperty("id", str(x, "id"));
                    c.addProperty("abbr", SLOT_ABBR.getOrDefault(slot, slot.length() >= 2 ? slot.substring(0, 2).toUpperCase(Locale.ROOT) : slot));
                    c.addProperty("name", str(x, "name"));
                    c.addProperty("bg", have1 ? "#e6cffa24" : "#e6cffa0d");
                    c.addProperty("border", cur ? GOLD : have1 ? ACC : EDGE);
                    c.addProperty("ring", cur ? "1," + GOLD : "");
                    c.addProperty("color", cur ? GOLD : have1 ? ACC : DIM);
                    c.addProperty("worn", worn(x));
                    set.add(c);
                }
            }
        }
        d.add("set", set);
        d.addProperty("hasSet", !set.isEmpty());
        // status and buttons
        d.addProperty("status", wornNow ? "Wearing this" : owned ? "In your collection" : "Locked");
        d.addProperty("statusColor", wornNow ? GREEN : owned ? ACC : RED);
        d.addProperty("note", owned ? (wearable ? "Click to " + (wornNow ? "take it off." : "put it on.") : str(i, "cat").equals("tools") ? "Hold a matching item in your main hand." : "Choose which Pokemon gets it.")
                : wearable ? "You can still try it on for " + preview + " seconds." : "Ask a staff member how to unlock it.");
        String tryText = "TRY ON " + preview + "S";
        String mainText = !owned ? "LOCKED" : wearable ? (wornNow ? "TAKE OFF" : "EQUIP") : str(i, "cat").equals("tools") ? "APPLY TO HELD ITEM" : "CHOOSE POKEMON";
        String backText = partyMode ? "BACK" : "CLOSE";
        boolean green = wornNow && wearable;
        double tryW = pixW(tryText, 13, 1) + 44, mainW = Math.max(250, pixW(mainText, 18, 1) + 44), backW = pixW(backText, 13, 1) + 44;
        d.addProperty("canTry", wearable);
        d.addProperty("tryText", tryText);
        d.addProperty("tryW", tryW);
        d.addProperty("mainText", mainText);
        d.addProperty("mainW", mainW);
        d.addProperty("mainBg", S + (green ? "green" : "solid"));
        d.addProperty("mainHv", S + (green ? "green" : owned ? "solid_hover" : "solid"));
        d.addProperty("mainSprite", green ? "green" : "solid");
        d.addProperty("mainColor", green ? "#07251a" : BTNFG);
        d.addProperty("mainOp", owned ? 1 : 0.35);
        d.addProperty("backText", backText);
        d.addProperty("backW", backW);
        double btns = backW + (wearable ? tryW + 14 : 0) + (partyMode ? 0 : mainW + 14);
        d.addProperty("statusW", Math.max(200, Math.min(420, 882 - btns - 24)));
        // party choices
        JsonArray members = new JsonArray();
        boolean loaded = partyData != null && str(partyData, "id").equals(str(i, "id"));
        if (partyMode && loaded) for (JsonElement e : arr(partyData, "members")) {
            JsonObject m = e.getAsJsonObject(), o = new JsonObject();
            boolean emptySlot = bool(m, "empty"), fits = bool(m, "fits"), wearing = bool(m, "wearing");
            o.addProperty("slot", num(m, "slot"));
            o.addProperty("empty", emptySlot);
            o.addProperty("name", emptySlot ? "Slot " + ((int) num(m, "slot") + 1) + " - empty" : str(m, "name"));
            o.addProperty("level", emptySlot ? "" : "Lv. " + (int) num(m, "level"));
            o.addProperty("status", emptySlot ? "" : !fits ? "WRONG SPECIES" : wearing ? "WEARING - REMOVE" : bool(m, "skin") ? "REPLACE SKIN" : "APPLY");
            o.addProperty("scolor", wearing ? GREEN : ACC);
            o.addProperty("border", wearing ? GREEN : "#8465a899");
            o.addProperty("hv", emptySlot || !fits ? "" : "#e6cffa14");
            o.addProperty("op", !emptySlot && !fits ? 0.45 : 1);
            members.add(o);
        }
        d.add("members", members);
        d.addProperty("partyLoading", partyMode && !loaded);
        return d;
    }
}
