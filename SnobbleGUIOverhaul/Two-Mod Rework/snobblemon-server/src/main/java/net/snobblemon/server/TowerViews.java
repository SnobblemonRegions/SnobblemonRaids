package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The CobbleTowers page. CobbleTowers (patched with its WebGUI bridge) still sends every screen's data as one "tower" event
 * {@code {kind, data}} and reads its actions from the channel "cobbletowers"; this view only reshapes that data for the native layout, mirroring the
 * original page's own logic (app.js: card facts, payout, hint text, button states, reward tiers, vendor targets). Clicks that only change what is
 * shown (card selection, reward paging, vendor selection) are answered here; every action that changes the game goes to CobbleTowers unchanged and
 * is checked there (the page never decides anything).
 */
final class TowerViews {
    private TowerViews() {}

    static final String PAGE = "cobbletowers";
    private static final String TEX = "tex:cobbletowers/";

    static void register() {
        SnobbleUi.registerView(PAGE, TowerViews::apply);
    }

    private static JsonObject obj(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null; }

    private static JsonArray arr(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    // ------------------------------------------------------------------ icons (item -> pack texture)

    private static Set<String> ITEMS, ICONS, SPRITES;

    private static synchronized void loadIcons() {
        if (ITEMS != null) return;
        ITEMS = new HashSet<>();
        ICONS = new HashSet<>();
        SPRITES = new HashSet<>();
        try (InputStream in = TowerViews.class.getResourceAsStream("/data/snobblemon_server/tower_icons.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("items")) ITEMS.add(e.getAsString());
            for (JsonElement e : o.getAsJsonArray("icons")) ICONS.add(e.getAsString());
            for (JsonElement e : o.getAsJsonArray("sprites")) SPRITES.add(e.getAsString());
        } catch (Exception e) {
            SnobblemonServer.LOGGER.warn("tower_icons.json unreadable: {}", e.toString());
        }
    }

    private static String path(String id) { String s = String.valueOf(id); int i = s.lastIndexOf(':'); return i >= 0 ? s.substring(i + 1) : s; }

    /** app.js ze(): the page's own category icon for an item that has no picture of its own. */
    static String category(String id) {
        String t = path(id);
        Matcher m;
        if ((m = Pattern.compile("^exp_candy_(xs|s|m|l|xl)$").matcher(t)).find()) return "candy_" + m.group(1);
        if (t.equals("rare_candy")) return "candy_rare";
        if (t.endsWith("_ball")) {
            return "ball_" + switch (t) { case "poke_ball" -> "poke"; case "great_ball" -> "great"; case "ultra_ball" -> "ultra"; case "master_ball" -> "master"; default -> "other"; };
        }
        String bottle = switch (t) {
            case "potion" -> "blue"; case "super_potion" -> "purple"; case "hyper_potion" -> "pink"; case "max_potion" -> "gold"; case "full_restore", "full_heal" -> "white";
            case "revive", "max_revive" -> "gold"; case "antidote" -> "purple"; case "burn_heal" -> "red"; case "paralyze_heal" -> "yellow"; case "awakening" -> "teal"; case "ice_heal" -> "blue";
            default -> null;
        };
        if (bottle != null) return "bottle_" + bottle;
        if (t.matches("^(protein|iron|calcium|zinc|carbos|hp_up|pp_up|pp_max)$")) return "bottle_green";
        if ((m = Pattern.compile("^(.+)_berry$").matcher(t)).find()) {
            return "berry_" + switch (m.group(1)) { case "sitrus" -> "yellow"; case "oran" -> "blue"; case "pecha" -> "pink"; case "lum" -> "green"; default -> "red"; };
        }
        if ((m = Pattern.compile("^(red|green|blue|yellow|black|white|pink)_apricorn$").matcher(t)).find()) return "nut_" + m.group(1);
        if ((m = Pattern.compile("^(.+)_stone$").matcher(t)).find()) {
            return "gem_" + switch (m.group(1)) { case "water" -> "blue"; case "leaf" -> "green"; case "dusk" -> "purple"; case "fire", "sun" -> "red"; case "thunder", "shiny" -> "yellow";
                case "moon" -> "white"; case "dawn", "ice" -> "cyan"; default -> "white"; };
        }
        if (id.startsWith("ascensionlib:")) {
            String a = switch (t) { case "resonance_dust" -> "gem_yellow"; case "facet" -> "gem_cyan"; case "tower_mark" -> "gem_white"; case "scouter" -> "gem_green"; case "ascension_core" -> "gem_red"; default -> null; };
            if (a != null) return a;
        }
        if (t.equals("diamond") || t.startsWith("deep_sea_")) return "gem_cyan";
        if (t.equals("emerald")) return "gem_green";
        if (t.equals("netherite_scrap")) return "gem_brown";
        if (t.endsWith("apple")) return t.matches(".*(golden|enchanted|syrupy).*") ? "apple_gold" : t.matches(".*(tart|sweet).*") ? "apple_green" : "apple_red";
        if (t.startsWith("booster_pack")) return "pack";
        if (t.endsWith("ticket")) return "ticket";
        if (t.endsWith("banner_pattern") || t.contains("trim_template")) return "scroll";
        if (t.endsWith("banner")) return "banner";
        return null;
    }

    /** at(): what kind of grant this is ("coin", "raid" or "item"). */
    private static String grantKind(JsonObject g) {
        String t = str(g, "label") + " " + str(g, "item");
        if (Pattern.compile("dollar", Pattern.CASE_INSENSITIVE).matcher(t).find()) return "coin";
        if (Pattern.compile("raid", Pattern.CASE_INSENSITIVE).matcher(t).find()) return "raid";
        return "item";
    }

    private static String itemIcon(String id) {
        loadIcons();
        String key = id.replace(':', '_');
        if (ITEMS.contains(key)) return TEX + "item_" + key + ".png";
        String c = category(id);
        return TEX + (c != null && ICONS.contains(c) ? "ico_" + c : "ico_gift") + ".png";
    }

    private static String grantIcon(JsonObject g) {
        return switch (grantKind(g)) {
            case "coin" -> TEX + "ico_coin.png";
            case "raid" -> TEX + "raid_points.png";
            default -> itemIcon(str(g, "item"));
        };
    }

    private static String teamSprite(String species) {
        loadIcons();
        String k = species == null ? "" : species.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SPRITES.contains(species) ? TEX + "sp_" + species + ".png" : SPRITES.contains(k) ? TEX + "sp_" + k + ".png" : TEX + "ico_gift.png";
    }

    // ------------------------------------------------------------------ the page's own helpers

    private static final Pattern PAYOUT = Pattern.compile("([+-]\\d+%)\\s+to your final payout");
    private static final Pattern RISK_CARRY = Pattern.compile("^The risk you carry is paid out when the tower ends\\.?$");
    private static final String HELD = "Held for this run; not permanent equipment.";
    private static final String[] RISK = {"Minor", "Moderate", "Severe"};

    /** ge(): "[+] text * more" -> sign / text / tone. */
    private static String[] line(String raw) {
        Matcher m = Pattern.compile("^\\[([+-])\\]\\s*(.*)$").matcher(raw);
        String body = m.matches() ? m.group(2) : raw;
        body = body.replaceAll("\\s+\\*\\s+", " · ");
        if (m.matches()) return new String[]{m.group(1).equals("+") ? "+" : "−", body, m.group(1).equals("+") ? "benefit" : "cost"};
        return new String[]{"", body, "neutral"};
    }

    private static JsonObject factJson(String[] f) {
        JsonObject o = new JsonObject();
        o.addProperty("sign", f[0].isEmpty() ? "·" : f[0]);
        o.addProperty("text", f[1]);
        o.addProperty("bg", f[2].equals("benefit") ? "#15803d" : f[2].equals("cost") ? "#d6264e" : "#4d5e90");
        return o;
    }

    /** One offered card as the page's ic()/st() read it. */
    private static JsonObject card(JsonObject c, int i, int sel, boolean chosen, int votes) {
        int risk = (int) num(c, "risk");
        String name = str(c, "displayName");
        JsonArray lines = arr(c, "lines");
        String kind, title, sub;
        java.util.List<String> ls = new java.util.ArrayList<>();
        for (JsonElement e : lines) ls.add(e.getAsString());
        if (risk < 0) {
            int k = name.indexOf(": ");
            title = k > 0 ? name.substring(0, k) : name;
            String n = k > 0 ? name.substring(k + 2) : "";
            sub = n.isEmpty() ? "" : Character.toUpperCase(n.charAt(0)) + n.substring(1);
            kind = "event";
            ls = new java.util.ArrayList<>(sub.isEmpty() ? java.util.List.of(name) : java.util.List.of(sub));
        } else if (ls.contains(HELD)) {
            String tags = "";
            for (String l : ls) if (l.startsWith("Tags: ")) { tags = l.substring(6); break; }
            title = name;
            sub = tags.isEmpty() ? "Relic" : "Relic: " + tags;
            kind = "relic";
            ls.remove(HELD);
        } else {
            title = name;
            sub = (risk >= 0 && risk < 3 ? RISK[risk] : "Event") + " risk";
            kind = "modifier";
        }
        String payout = null;
        for (JsonElement e : lines) { Matcher m = PAYOUT.matcher(e.getAsString()); if (m.find()) { payout = m.group(1); break; } }
        JsonArray facts = new JsonArray(), all = new JsonArray();
        for (String l : ls) {
            String[] f = line(l);
            all.add(factJson(f));
            if (!PAYOUT.matcher(f[1]).find() && !RISK_CARRY.matcher(f[1]).find() && !f[1].startsWith("Risk: ")) facts.add(factJson(f));
        }
        JsonArray first3 = new JsonArray();
        for (int k = 0; k < Math.min(3, facts.size()); k++) first3.add(facts.get(k));
        String tone = risk == 0 ? "ok" : risk == 1 ? "warn" : risk == 2 ? "bad" : "event";
        JsonObject o = new JsonObject();
        o.addProperty("i", i);
        o.addProperty("name", kind.equals("event") && !sub.isEmpty() ? sub : title);
        o.addProperty("title", title);
        o.addProperty("risk", risk);
        for (int g = 0; g < 3; g++) o.addProperty("g" + g, TEX + "gem_" + (risk >= 0 && g <= risk ? String.valueOf(Math.min(2, risk)) : "off") + ".png");
        o.addProperty("pill", kind.equals("event") ? title : sub);
        o.addProperty("pillBg", switch (tone) { case "ok" -> "#3fa34d"; case "warn" -> "#d98a06"; case "bad" -> "#c8203f"; default -> "#5b6a8a"; });
        o.addProperty("pillFg", tone.equals("ok") || tone.equals("warn") ? "#101828" : "#ffffff");
        o.addProperty("stack", o.get("pill").getAsString().length() > 10);
        o.add("facts", kind.equals("event") ? new JsonArray() : first3);
        o.add("lines", all);
        if (payout != null) o.addProperty("payout", payout);
        boolean isSel = i == sel;
        o.addProperty("status", (chosen ? "Chosen" : isSel ? "Selected" : "") + (votes > 0 ? (chosen || isSel ? " · " : "") + votes + (votes == 1 ? " vote" : " votes") : ""));
        o.addProperty("edge", chosen || isSel ? "#3f6fe6" : "#2b3b68");
        String key = themeKey(str(c, "theme"), kind);
        o.addProperty("art", TEX + "art_" + key + ".png");
        o.addProperty("wide", TEX + "wide_" + key + ".png");
        return o;
    }

    /** we(): which captured picture a theme string gets (the same decisions the page's painter makes). */
    static String themeKey(String theme, String kind) {
        if (kind.equals("event")) return "unknown";
        String r = theme, a = "";
        int k = theme.indexOf(':');
        if (k >= 0) { r = theme.substring(0, k); a = theme.substring(k + 1); }
        switch (r) {
            case "weather": return a.matches(".*(sunny|desolate).*") ? "weather_sunny" : a.equals("sandstorm") ? "weather_sandstorm" : a.matches(".*(hail|snow).*") ? "weather_hail" : "weather_rain";
            case "terrain": return switch (a) { case "electricterrain" -> "terrain_electricterrain"; case "psychicterrain" -> "terrain_psychicterrain"; case "mistyterrain" -> "terrain_mistyterrain"; default -> "terrain_grassyterrain"; };
            case "enemy": return switch (a) { case "fragile" -> "enemy_fragile"; case "champion" -> "enemy_champion"; case "veteran" -> "enemy_veteran"; case "novice" -> "enemy_novice"; case "tough" -> "enemy_tough"; default -> "enemy_other"; };
            case "encounter": return a.equals("crowd") ? "encounter_crowd" : "encounter_other";
            case "constraint": return switch (a) { case "no_heal" -> "constraint_no_heal"; case "no_setup" -> "constraint_no_setup"; case "no_switch" -> "constraint_no_switch"; case "no_items" -> "constraint_no_items"; default -> "constraint_other"; };
            case "scouting": return "scouting";
            case "reward_up": return "reward_up";
            case "reward_down": return "reward_down";
            case "custom": return switch (a) { case "glass_cannon", "fortunes_wheel", "field_hospital", "black_market", "swift_start", "iron_hide", "war_banner" -> "custom_" + a; default -> "unknown"; };
            default: return "unknown";
        }
    }

    // ------------------------------------------------------------------ state

    private static JsonObject fresh() {
        JsonObject st = new JsonObject();
        st.addProperty("sel", -1);
        st.addProperty("offer", "");
        st.addProperty("rwIdx", 0);
        st.addProperty("venIdx", 0);
        st.addProperty("venTarget", 0);
        return st;
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = obj(data, "st") != null ? data.getAsJsonObject("st") : fresh();
        switch (event) {
            case "tower" -> {
                String kind = str(s, "kind");
                JsonObject d = obj(s, "data");
                if (d == null) return;
                switch (kind) {
                    case "IntermissionState" -> {
                        st.add("inter", d);
                        st.remove("votePendAt");                                  // the server has answered: "Sending your vote..." is over
                        st.addProperty("view", "inter");
                        JsonObject draft = obj(d, "draft");
                        StringBuilder key = new StringBuilder();
                        for (JsonElement c : arr(draft, "cards")) key.append(str(c.getAsJsonObject(), "id")).append('|');
                        key.append('@').append((int) num(d, "floor"));
                        if (!key.toString().equals(str(st, "offer"))) {            // a new offer: selection starts from the server's own record
                            st.addProperty("offer", key.toString());
                            st.addProperty("sel", draft == null ? -1 : (int) num(draft, num(draft, "state") == 2 ? "chosen" : "myVote"));
                        }
                    }
                    case "TowerHallState" -> {
                        st.add("hall", d);
                        if (obj(d, "play") != null) st.add("play", d.getAsJsonObject("play"));
                        if (!st.has("view") || str(st, "view").isEmpty() || "hall".equals(str(st, "view"))) st.addProperty("view", "hall");
                    }
                    case "PlayState" -> {
                        st.add("play", d);
                        if (bool(d, "open") || "lobby".equals(str(st, "view"))) st.addProperty("view", "lobby");
                        String msg = str(d, "message");
                        if (!msg.isEmpty() && !msg.equals(str(st, "lastMsg"))) toast(data, msg);
                        st.addProperty("lastMsg", msg);
                    }
                    case "TrackState" -> {
                        JsonObject old = obj(st, "trk");
                        if (old != null && !str(old, "selectedTower").equals(str(d, "selectedTower"))) { st.addProperty("lane", "mastery"); st.remove("tsel"); }
                        st.add("trk", d);
                        if (!str(d, "message").isEmpty()) toast(data, str(d, "message"));
                    }
                    case "RanksState" -> {
                        st.add("rk", d);
                        if (!str(d, "message").isEmpty()) toast(data, str(d, "message"));
                    }
                    case "RentalDraft" -> {
                        JsonObject old = obj(st, "rd");
                        st.add("rd", d);
                        st.addProperty("view", "draft");
                        int cur = (int) num(d, "current");
                        if (old == null || (int) num(old, "current") != cur || !st.has("rdStage")) {
                            st.addProperty("rdStage", st.has("rdAck") ? "sealed" : "warn");
                            st.add("rdSel", new JsonArray());
                        } else if (str(st, "rdStage").equals("choose") && !rdCards(old).toString().equals(rdCards(d).toString())) {
                            st.add("rdSel", new JsonArray());
                        }
                        if (bool(d, "complete")) st.addProperty("rdStage", "done");
                        if (!str(d, "message").isEmpty()) toast(data, str(d, "message"));
                    }
                    case "TowerFeatureState" -> {
                        int rid = (int) num(d, "requestId");
                        if (rid != 0 && (rid != (int) num(st, "freq") || !str(d, "section").equals(str(st, "fsec")))) return;     // an answer to an older request
                        boolean same = obj(st, "fe") != null && str(obj(st, "fe"), "section").equals(str(d, "section"));
                        st.add("fe", d);
                        st.addProperty("fsec", str(d, "section"));
                        st.addProperty("fpend", false);
                        st.addProperty("sub", "feature");
                        st.addProperty("tab", "codex");
                        if (!same) { st.remove("fsel"); st.remove("frev"); }
                        JsonArray ents = arr(d, "entries");
                        if (!str(d, "message").isEmpty() && ents.size() > 0) st.addProperty("fsel", str(ents.get(0).getAsJsonObject(), "id"));
                        else if (st.has("fsel") && featEntry(d, str(st, "fsel")) == null) st.remove("fsel");
                        st.remove("frev");
                        if (!str(d, "message").isEmpty()) toast(data, str(d, "message"));
                    }
                    case "RegistrationState" -> {
                        st.add("reg", d);
                        if (bool(d, "open")) st.addProperty("view", "party");
                        if (!str(d, "message").isEmpty()) toast(data, str(d, "message"));
                    }
                    case "ScoutingReveal" -> { st.add("scout", d); st.addProperty("view", "scout"); st.addProperty("scSel", 0); }
                    case "SpectatorPanel" -> { st.add("spec", d); st.addProperty("specAt", System.currentTimeMillis()); }
                    case "RewardReveal" -> { st.add("rw", d); st.addProperty("rwIdx", 0); }
                    case "VendorCatalog" -> {
                        JsonObject old = obj(st, "ven");
                        st.add("ven", d);
                        if (old != null && (int) num(d, "cobbleDollars") < (int) num(old, "cobbleDollars")) st.addProperty("venBurstAt", System.currentTimeMillis());   // a purchase went through: celebrate
                        if (old == null || arr(old, "services").size() != arr(d, "services").size()) st.addProperty("venIdx", 0);
                    }
                    default -> { return; }          // the other screens are not ported yet
                }
            }
            case "msg" -> {
                if (!message(p, st, s)) return;
                if (st.has("pendingToast")) { toast(data, str(st, "pendingToast")); st.remove("pendingToast"); }
            }
            default -> { return; }
        }
        data.add("st", st);
        data.add("vm", build(p, st));
    }

    /** False when the message changes nothing (or closed the page). */
    private static boolean message(ServerPlayer p, JsonObject st, JsonObject m) {
        String action = str(m, "action");
        int k = (int) num(m, "k");
        JsonObject inter = obj(st, "inter"), rw = obj(st, "rw"), ven = obj(st, "ven");
        switch (action) {
            case "pick" -> {
                int i = (int) num(m, "i");
                JsonObject draft = obj(inter, "draft");
                if (draft == null || num(draft, "state") != 1 || num(inter, "countdown") >= 0 || i < 0 || i >= arr(draft, "cards").size()) return false;
                st.addProperty("sel", i);
            }
            case "currun" -> {
                if (!"inter".equals(str(st, "view"))) return false;
                st.addProperty("view", "hall");
                st.addProperty("tab", "codex");
                st.addProperty("sub", "feature");
                st.addProperty("fsec", "run");
                st.remove("fe");
                featSend(p, st, "run", "open", "");
            }
            case "confirmVote" -> {
                int i = st.has("sel") ? (int) num(st, "sel") : -1;
                JsonObject draft = obj(inter, "draft");
                boolean pending = st.has("votePendAt") && System.currentTimeMillis() - (long) num(st, "votePendAt") < 8000;
                if (draft == null || pending || num(draft, "state") != 1 || num(inter, "countdown") >= 0 || i < 0 || i >= arr(draft, "cards").size() || i == (int) num(draft, "myVote")) return false;
                st.addProperty("votePendAt", System.currentTimeMillis());
                forwardKind(p, "IntermissionAction", "action", "PICK_CARD", "argument", i);
            }
            case "rewardPick" -> { if (rw == null || k < 0 || k >= arr(rw, "grants").size()) return false; st.addProperty("rwIdx", k); }
            case "rewardStep" -> {
                if (rw == null) return false;
                int n = arr(rw, "grants").size(), d = (int) num(m, "d");
                if (n == 0) return false;
                st.addProperty("rwIdx", ((int) num(st, "rwIdx") + d + n) % n);
            }
            case "rewardClose" -> {
                st.remove("rw");
                if (inter == null && ven == null) { SnobbleUi.close(p, PAGE); return false; }
            }
            case "vendorPick" -> { if (ven == null || k < 0 || k >= arr(ven, "services").size()) return false; st.addProperty("venIdx", k); }
            case "vendorTarget" -> {
                if (ven == null || arr(ven, "team").size() == 0) return false;
                st.addProperty("venTarget", ((int) num(st, "venTarget") + 1) % arr(ven, "team").size());
            }
            case "vendorClose" -> st.remove("ven");
            case "fopen" -> {
                JsonObject fe = obj(st, "fe");
                if (fe == null || bool(st, "fpend")) return false;
                JsonArray items = featItems(fe);
                int i = (int) num(m, "i");
                if (i < 0 || i >= items.size()) return false;
                JsonObject it = items.get(i).getAsJsonObject();
                if (str(it, "kind").equals("entry")) { st.addProperty("fsel", str(it, "id")); st.remove("frev"); }
                else featAct(p, st, str(fe, "section"), it.getAsJsonObject("action"));
            }
            case "fprim" -> {
                JsonObject fe = obj(st, "fe");
                JsonObject en = fe == null || !st.has("fsel") ? null : featEntry(fe, str(st, "fsel"));
                if (en == null || arr(en, "actions").size() == 0 || bool(st, "fpend")) return false;
                featAct(p, st, str(fe, "section"), arr(en, "actions").get(0).getAsJsonObject());
            }
            case "fconf" -> {
                JsonObject fe = obj(st, "fe"), rv = obj(st, "frev");
                if (fe == null || rv == null || bool(st, "fpend")) return false;
                boolean needs = !str(rv, "inputHint").isEmpty();
                featSend(p, st, str(fe, "section"), str(rv, "id"), needs ? str(m, "t").trim() : str(rv, "argument"));
            }
            case "fcancel" -> { if (!st.has("frev")) return false; st.remove("frev"); }
            case "partyDone" -> {
                if (!"party".equals(str(st, "view"))) return false;
                st.addProperty("view", "lobby");
                forwardKind(p, "PlayAction", "action", "REFRESH", "argument", "");
            }
            case "scoutPick" -> { JsonObject sc = obj(st, "scout"); int ck = (int) num(m, "k"); if (sc == null || ck < 0 || ck >= arr(sc, "categories").size()) return false; st.addProperty("scSel", ck); }
            case "fover" -> { st.remove("fsel"); st.remove("frev"); }
            case "fref" -> { if (!st.has("fsec") || bool(st, "fpend")) return false; featSend(p, st, str(st, "fsec"), "open", ""); }
            case "rdWarn" -> {
                if (!"warn".equals(str(st, "rdStage"))) return false;
                if ("calm".equals(str(m, "k"))) st.addProperty("noAnim", true);
                st.addProperty("rdAck", true);
                st.addProperty("rdStage", "sealed");
            }
            case "rdOpen" -> {
                if (!"sealed".equals(str(st, "rdStage"))) return false;
                st.addProperty("rdOpenN", (int) num(st, "rdOpenN") + 1);
                st.addProperty("rdStage", "choose");
                st.addProperty("rdOpenAt", System.currentTimeMillis());
                st.addProperty("rdSkip", false);
                st.add("rdSel", new JsonArray());
            }
            case "rdSkip" -> { if (!"choose".equals(str(st, "rdStage")) || bool(st, "rdSkip")) return false; st.addProperty("rdSkip", true); }
            case "rdPick" -> {
                JsonObject rd = obj(st, "rd");
                if (rd == null || !"choose".equals(str(st, "rdStage"))) return false;
                if (rdRevealing(st, rd)) { st.addProperty("rdSkip", true); return true; }       // a click during the reveal skips it (the page: "Click to skip the reveal")
                int i = (int) num(m, "i");
                JsonArray cards = rdCards(rd);
                if (i < 0 || i >= cards.size()) return false;
                JsonArray sel = arr(st, "rdSel"), nsel = new JsonArray();
                boolean had = false;
                for (JsonElement e : sel) { if (e.getAsInt() == i) had = true; else nsel.add(e.getAsInt()); }
                if (!had) {
                    if (sel.size() >= 2) { st.addProperty("pendingToast", "Keep exactly two cards."); return true; }
                    int big = bigCount(rd, cards, nsel) + (isBig(cards.get(i).getAsJsonObject()) ? 1 : 0);
                    if (big > 2) { st.addProperty("pendingToast", "A team may keep at most 2 legendary or mythic Pok\u00e9mon. Choose different cards."); return true; }
                    nsel.add(i);
                }
                st.add("rdSel", nsel);
            }
            case "back" -> { SnobbleUi.close(p, PAGE); return false; }
            case "tab" -> {
                String t = str(m, "t");
                if (!java.util.List.of("towers", "trials", "progress", "social", "ranks", "codex").contains(t)) return false;
                st.addProperty("tab", t);
                st.remove("sub"); st.remove("subId");
                if (t.equals("progress") && !st.has("trk")) forwardKind(p, "TrackAction", "action", "refresh", "lane", "", "tower", "", "number", 0);
                if (t.equals("ranks") && !st.has("rk")) forwardKind(p, "RanksRequest", "left", "", "right", "");
                if (t.equals("codex")) {
                    st.addProperty("sub", "feature");
                    if (!st.has("fsec") || obj(st, "fe") == null) featSend(p, st, "modifiers", "open", "");
                }
            }
            case "lane" -> {
                String v = str(m, "v");
                if (!v.equals("season") && !v.equals("mastery")) return false;
                st.addProperty("lane", v);
            }
            case "rowpick" -> {
                JsonObject trk = obj(st, "trk");
                if (trk == null) return false;
                if ("season".equals(str(m, "k"))) st.addProperty("lane", "season");
                else {
                    st.addProperty("lane", "mastery");
                    String id = str(m, "id");
                    if (!id.equals(str(trk, "selectedTower"))) forwardKind(p, "TrackAction", "action", "refresh", "lane", "", "tower", id, "number", 0);
                }
            }
            case "tsel" -> {
                JsonObject trk = obj(st, "trk");
                if (trk == null) return false;
                int i = (int) num(m, "i");
                st.addProperty("tsel_" + (str(st, "lane").equals("season") ? "season" : "mastery"), i);
            }
            case "hsel" -> st.addProperty("hsel", str(m, "id"));
            case "go" -> {
                String v = str(m, "v");
                if (v.equals("tower")) { st.addProperty("sub", "tower"); st.addProperty("subId", str(m, "id")); }
                else if (v.equals("trial")) { st.addProperty("sub", "trial"); st.addProperty("subId", str(m, "k")); }
                else if (v.equals("guide") || v.equals("settings")) { st.addProperty("sub", v); st.remove("subId"); }
                else return false;
            }
            case "anim" -> st.addProperty("noAnim", !bool(st, "noAnim"));
            case "up" -> { if (!st.has("sub")) return false; st.remove("sub"); st.remove("subId"); }
            default -> { return false; }
        }
        return true;
    }



    // ------------------------------------------------------------------ hall (TowerHallState)

    private static String bl(String id) {
        String t = path(id).toLowerCase();
        return t.equals("test") ? "neutral" : java.util.List.of("neutral", "tideforge", "rootvale", "duskvale", "halloween").contains(t) ? t : "other";
    }

    private static final java.util.Map<String, String[]> RESIDENTS = java.util.Map.of(
        "duskvale", new String[]{"darkrai", "umbreon", "obstagoon"}, "halloween", new String[]{"mimikyu", "phantump", "trevenant"},
        "neutral", new String[]{"machoke", "haunter", "lairon"}, "rootvale", new String[]{"celebi", "hydrapple", "leafeon"},
        "test", new String[]{"machoke", "haunter", "lairon"}, "tideforge", new String[]{"kyogre", "vaporeon", "blastoise"});

    private static String[] medalColors(String rank, boolean ranked) {
        if (!ranked) return new String[]{"#232f52", "#9aa8cf"};
        return switch (rank.toLowerCase()) {
            case "bronze" -> new String[]{"#3a2819", "#e0a674"};
            case "silver" -> new String[]{"#2b3446", "#c4cee2"};
            case "gold" -> new String[]{"#3a2f10", "#f2c75c"};
            case "platinum" -> new String[]{"#1a2f48", "#7fb6ec"};
            case "diamond" -> new String[]{"#12343a", "#5fd0dd"};
            case "master" -> new String[]{"#2b2147", "#c3a6ff"};
            case "champion" -> new String[]{"#3a1620", "#ff7a94"};
            default -> new String[]{"#232f52", "#9aa8cf"};
        };
    }

    private static String runWords(String s) {
        String[] parts = s.split(" / ");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append(" \u00b7 ");
            b.append(i == 0 ? parts[i] : parts[i].isEmpty() ? "" : parts[i].charAt(0) + parts[i].substring(1).toLowerCase());
        }
        return b.toString();
    }

    private static JsonObject hall(ServerPlayer p, JsonObject st, JsonObject h) {
        JsonObject o = new JsonObject();
        JsonObject ex = obj(h, "extras");
        JsonArray src = arr(h, "towers");
        java.util.List<JsonObject> list = new java.util.ArrayList<>();
        for (JsonElement e : src) list.add(e.getAsJsonObject());
        list.sort((a, b) -> Boolean.compare(num(b, "endsAt") > 0, num(a, "endsAt") > 0));
        String sel = st.has("hsel") ? str(st, "hsel") : "";
        String runTower = ex == null ? "" : str(ex, "runTower");
        JsonObject selT = null;
        for (JsonObject t : list) if (t.has("id") && str(t, "id").equals(sel)) selT = t;
        if (selT == null) { for (JsonObject t : list) if (str(t, "id").equals(runTower)) selT = t; }
        if (selT == null && !list.isEmpty()) selT = list.get(0);
        JsonArray towers = new JsonArray();
        for (JsonObject t : list) {
            String id = str(t, "id");
            boolean on = selT != null && str(selT, "id").equals(id);
            JsonObject r = new JsonObject();
            r.addProperty("id", id);
            r.addProperty("name", towerName(str(t, "name")));
            r.addProperty("sub", (int) num(t, "floors") + " floors \u00b7 " + (int) num(t, "milestones") + " milestones" + (bool(t, "ascends") ? " \u00b7 Ascends" : ""));
            r.addProperty("pano", TEX + "panofull_" + regionKey(id) + ".png");
            r.addProperty("medal", TEX + "tw_" + bl(id) + ".png");
            r.addProperty("dim", on ? "#00000000" : "#080c1c66");
            r.addProperty("bg", on ? "grad:#1d2a50,#16213f" : "#16213f");
            r.addProperty("bd", on ? "#ddab3c" : "#2b3b68");
            r.addProperty("hbd", on ? "#ddab3c" : "#3f5aa0");
            String[] res = RESIDENTS.getOrDefault(path(id), new String[0]);
            for (int i = 0; i < 3; i++) r.addProperty("r" + i, i < res.length ? teamSprite(res[i]) : "");
            JsonObject stand = null;
            if (ex != null) for (JsonElement se : arr(ex, "standings")) if (str(se.getAsJsonObject(), "tower").equals(id)) stand = se.getAsJsonObject();
            boolean ranked = stand != null && num(stand, "level") > 0;
            String mt = !ranked ? "Unranked" : num(stand, "toNext") < 0 ? str(stand, "rank") + " (top rank)" : str(stand, "rank") + " \u00b7 " + (int) num(stand, "toNext") + (num(stand, "toNext") == 1 ? " level" : " levels") + " to next";
            JsonArray medals = new JsonArray();
            String[] mc = medalColors(ranked ? str(stand, "rank") : "", ranked);
            addMedal(medals, mt, mc[0], mc[1]);
            if (stand != null && num(stand, "ascension") > 0) addMedal(medals, "Ascension " + (int) num(stand, "ascension"), "#2b2147", "#c3a6ff");
            if (num(t, "endsAt") > 0) addMedal(medals, "Limited time", "#3a2f10", "#f2c75c");
            if (runTower.equals(id)) addMedal(medals, "Run in progress", "#1a2f48", "#7fb6ec");
            r.add("medals", medals);
            towers.add(r);
        }
        o.add("towers", towers);
        // party
        JsonArray party = new JsonArray();
        JsonArray raw = ex == null ? new JsonArray() : arr(ex, "party");
        int lo = 999, hi = 0, fainted = 0;
        for (JsonElement e : raw) {
            JsonObject m = e.getAsJsonObject();
            JsonObject pj = new JsonObject();
            pj.addProperty("icon", teamSprite(str(m, "species")));
            pj.addProperty("name", str(m, "name"));
            int lv = (int) num(m, "level");
            lo = Math.min(lo, lv); hi = Math.max(hi, lv);
            boolean f = bool(m, "fainted");
            if (f) fainted++;
            pj.addProperty("sub", "Lv " + lv + (f ? " Fainted" : ""));
            pj.addProperty("op", f ? 0.55 : 1.0);
            party.add(pj);
        }
        if (party.size() > 0) o.add("party", party);
        o.addProperty("teamLabel", raw.size() == 0 ? "No Pok\u00e9mon in your party" : (raw.size() == 1 ? "1 Pok\u00e9mon" : raw.size() + " of 6") + " \u00b7 " + (lo == hi ? "Level " + lo : "Levels " + lo + " to " + hi));
        // run / lobby card
        String rs = str(h, "runStatus");
        JsonObject pl = obj(h, "play"), lb = obj(pl, "lobby");
        int role = lb == null ? 0 : (int) num(lb, "role");
        if (!rs.isEmpty()) {
            String nm = runTower.isEmpty() ? "Your tower" : "";
            for (JsonObject t : list) if (str(t, "id").equals(runTower)) nm = towerName(str(t, "name")) + " Tower";
            o.addProperty("runSmall", "Run in progress");
            o.addProperty("runTitle", nm + " \u00b7 " + runWords(rs));
            o.addProperty("runIcon", TEX + "tw_" + bl(runTower) + ".png");
            o.addProperty("runBtn", bool(h, "canResume") ? "Return to run" : "Back at the next intermission");
            o.addProperty("runOn", bool(h, "canResume"));
            o.addProperty("runAct", "resume");
        } else if (role != 0) {
            o.addProperty("runSmall", "Expedition lobby");
            o.addProperty("runTitle", "Your party is gathering");
            o.addProperty("runIcon", TEX + "tw_other.png");
            o.addProperty("runBtn", "Open lobby");
            o.addProperty("runOn", true);
            o.addProperty("runAct", "lobby");
        }
        JsonObject daily = null, weekly = null;
        for (JsonElement e : arr(h, "trials")) {
            JsonObject t = e.getAsJsonObject();
            if (str(t, "kind").equals("daily")) daily = t;
            if (str(t, "kind").equals("weekly")) weekly = t;
        }
        o.addProperty("hasDaily", daily != null);
        o.addProperty("hasWeekly", weekly != null);
        o.addProperty("dailyText", daily == null ? "Daily" : str(daily, "title").replaceFirst("^Daily Trial: ", "Daily \u00b7 "));
        o.addProperty("weeklyText", weekly == null ? "Weekly" : str(weekly, "title").replaceFirst("^Weekly Trial: ", "Weekly \u00b7 "));
        o.add("trials", trialCards(h));
        o.addProperty("selId", selT == null ? "" : str(selT, "id"));
        o.addProperty("canAct", rs.isEmpty() && (lb == null || num(lb, "countdown") < 0) && role <= 1);
        o.addProperty("canClimb", selT != null);
        o.addProperty("climbText", selT == null ? "Choose a tower" : "Climb " + towerName(str(selT, "name")) + " \u2192");
        o.addProperty("status", !rs.isEmpty() ? runWords(rs) : role == 0 ? "Choose a tower to begin." : "Your expedition lobby is available.");
        return o;
    }



    private static String fmtTime(long ms) {
        return java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", java.util.Locale.ENGLISH).format(java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault()));
    }

    private static JsonObject towerDetail(JsonObject h, JsonObject hv, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        JsonObject t = null;
        for (JsonElement e : arr(h, "towers")) if (str(e.getAsJsonObject(), "id").equals(id)) t = e.getAsJsonObject();
        JsonObject row = null;
        for (JsonElement e : arr(hv, "towers")) if (str(e.getAsJsonObject(), "id").equals(id)) row = e.getAsJsonObject();
        if (t == null || row == null) { o.addProperty("name", "Tower unavailable"); o.addProperty("medal", "Refresh the Hall"); o.addProperty("medalBg", "#232f52"); o.addProperty("medalFg", "#9aa8cf"); o.addProperty("canAct", false); o.add("facts", new JsonArray()); return o; }
        o.addProperty("name", towerName(str(t, "name")));
        o.addProperty("banner", TEX + "panofull_" + regionKey(id) + ".png");
        JsonObject m0 = arr(row, "medals").get(0).getAsJsonObject();
        o.addProperty("medal", str(m0, "text"));
        o.addProperty("medalBg", str(m0, "bg"));
        o.addProperty("medalFg", str(m0, "fg"));
        JsonArray facts = new JsonArray();
        addFact(facts, "Floors", (int) num(t, "floors") + " per cycle");
        addFact(facts, "Milestones", String.valueOf((int) num(t, "milestones")));
        addFact(facts, "Ascension", bool(t, "ascends") ? "Continues into harder cycles" : "Ends after the final floor");
        if (num(t, "endsAt") > 0) addFact(facts, "Limited time", "Closes " + fmtTime((long) num(t, "endsAt")));
        addFact(facts, "Your mastery", str(m0, "text"));
        o.add("facts", facts);
        for (int i = 0; i < 3; i++) o.addProperty("r" + i, str(row, "r" + i));
        o.addProperty("canAct", bool(hv, "canAct"));
        return o;
    }

    private static void addFact(JsonArray a, String k, String v) {
        JsonObject f = new JsonObject();
        f.addProperty("k", k);
        f.addProperty("v", v);
        a.add(f);
    }

    private static JsonObject trialBrief(JsonObject h, JsonObject hv, String kind) {
        JsonObject o = new JsonObject();
        o.addProperty("k", kind);
        JsonArray cards = arr(hv, "trials");
        JsonObject c = null;
        JsonObject raw = null;
        for (JsonElement e : arr(h, "trials")) if (str(e.getAsJsonObject(), "kind").equals(kind)) raw = e.getAsJsonObject();
        for (JsonElement e : cards) if (str(e.getAsJsonObject(), "kind").equals(kind)) c = e.getAsJsonObject();
        o.addProperty("canAct", raw != null && bool(hv, "canAct"));
        if (c == null || raw == null) {
            o.addProperty("title", "No " + kind + " trial");
            o.addProperty("kind", kind);
            o.addProperty("banner", TEX + "panofull_neutral.png");
            o.addProperty("note", "No " + kind + " trial is currently available.");
            o.addProperty("hasMods", false);
            o.add("mods", new JsonArray());
            o.addProperty("floors", ""); o.addProperty("floorsSub", ""); o.addProperty("mode", ""); o.addProperty("enemy", "");
            o.addProperty("rev", "");
            return o;
        }
        o.addProperty("title", str(raw, "title"));
        o.addProperty("kind", kind);
        o.addProperty("banner", str(c, "banner").replace("tb_", "panofull_"));
        for (String k : new String[]{"floors", "floorsSub", "mode", "enemy", "hasMods", "mods", "note"}) o.add(k, c.get(k));
        o.addProperty("rev", str(raw, "revision"));
        return o;
    }

    private static String modName(String id) {
        String t = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder b = new StringBuilder();
        for (String w : t.split("[_ ]+")) if (!w.isEmpty()) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    private static JsonArray trialCards(JsonObject h) {
        JsonArray out = new JsonArray();
        for (JsonElement e : arr(h, "trials")) {
            JsonObject t = e.getAsJsonObject();
            JsonArray rules = arr(t, "rules");
            java.util.function.IntFunction<String> rule = i -> i < rules.size() ? rules.get(i).getAsString() : "";
            String kind = str(t, "kind"), title = str(t, "title");
            JsonObject k = new JsonObject();
            k.addProperty("kind", kind);
            k.addProperty("name", cap(kind) + " Tower");
            k.addProperty("label", title.contains(": ") ? title.substring(title.indexOf(": ") + 2) : title);
            String r = bl(str(t, "tower"));
            k.addProperty("banner", TEX + "tb_" + (r.equals("other") ? "neutral" : r) + ".png");
            String[] fl = rule.apply(0).split(" / ", 2);
            String[] f1 = fl[0].split(" ", 2);
            String when = fl.length > 1 ? fl[1].replaceFirst("^\\d{4}-w(\\d+)$", "Week $1") : "";
            k.addProperty("floors", f1[0]);
            k.addProperty("floorsSub", (f1.length > 1 ? f1[1] : "floors") + (when.isEmpty() ? "" : " \u00b7 " + when));
            k.addProperty("mode", rule.apply(1).replaceFirst("^Mode: ", ""));
            k.addProperty("enemy", "Lv " + rule.apply(2).replaceFirst("^Enemy level: ", ""));
            String sm = rule.apply(3).replaceFirst("^Starting modifiers: ", "");
            JsonArray mods = new JsonArray();
            if (!sm.isEmpty() && !sm.equalsIgnoreCase("None")) for (String m : sm.split(",\\s*")) mods.add(modName(m.trim()));
            k.add("mods", mods);
            k.addProperty("hasMods", mods.size() > 0);
            k.addProperty("note", rule.apply(4));
            out.add(k);
        }
        return out;
    }

    private static void addMedal(JsonArray a, String text, String bg, String fg) {
        JsonObject m = new JsonObject();
        m.addProperty("text", text);
        m.addProperty("bg", bg);
        m.addProperty("fg", fg);
        a.add(m);
    }


    // ------------------------------------------------------------------ progress (TrackState) and ranks (RanksState)


    // ------------------------------------------------------------------ rental draft (RentalDraft)

    private static JsonObject RENTAL;
    private static final String[][] TYPE_COL = {{"normal", "a8a77a"}, {"fire", "ee8130"}, {"water", "6390f0"}, {"electric", "f7d02c"}, {"grass", "7ac74c"}, {"ice", "96d9d6"},
            {"fighting", "c22e28"}, {"poison", "a33ea1"}, {"ground", "e2bf65"}, {"flying", "a98ff3"}, {"psychic", "f95587"}, {"bug", "a6b91a"}, {"rock", "b6a136"}, {"ghost", "735797"},
            {"dragon", "6f35fc"}, {"dark", "705746"}, {"steel", "b7b7ce"}, {"fairy", "d685ad"}};

    private static synchronized JsonObject rentalTypes() {
        if (RENTAL != null) return RENTAL;
        RENTAL = new JsonObject();
        try (InputStream in = TowerViews.class.getResourceAsStream("/data/snobblemon_server/rental_types.json")) {
            if (in != null) RENTAL = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            SnobblemonServer.LOGGER.warn("rental_types.json unreadable: {}", e.toString());
        }
        return RENTAL;
    }

    /** the cards the player is choosing from (or, when complete, the kept ones in pack order) */
    private static JsonArray rdCards(JsonObject rd) {
        JsonArray out = new JsonArray();
        JsonArray packs = arr(rd, "packs");
        if (bool(rd, "complete")) {
            for (JsonElement pe : packs) {
                JsonObject pk = pe.getAsJsonObject();
                for (JsonElement ki : arr(pk, "kept")) { int k = ki.getAsInt(); if (k >= 0 && k < arr(pk, "cards").size()) out.add(arr(pk, "cards").get(k)); }
            }
            return out;
        }
        int cur = (int) num(rd, "current");
        if (cur < 0 || cur >= packs.size()) return out;
        return arr(packs.get(cur).getAsJsonObject(), "cards");
    }

    private static JsonObject NAMES;

    private static synchronized JsonObject rentalNames() {
        if (NAMES != null) return NAMES;
        NAMES = new JsonObject();
        try (InputStream in = TowerViews.class.getResourceAsStream("/data/snobblemon_server/rental_names.json")) {
            if (in != null) NAMES = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            SnobblemonServer.LOGGER.warn("rental_names.json unreadable: {}", e.toString());
        }
        return NAMES;
    }

    static final int TIP_LINES = 24;
    /** set while a page is built for a client that only takes 8 tooltip lines and plain colour codes (Snobblemon Client 2.7.0 and older than 2.6.0) */
    private static final ThreadLocal<Boolean> LEGACY_TIP = ThreadLocal.withInitial(() -> false);

    /** the same card in at most 8 lines of legacy-coloured text: name, types, ability, nature, held, IV total, EV total, moves on one line */
    private static JsonArray legacyTip(JsonArray full) {
        JsonArray out = new JsonArray();
        java.util.List<String> moves = new java.util.ArrayList<>();
        for (int i = 0; i < full.size(); i++) {
            String l = full.get(i).getAsString().replaceAll("§#[0-9a-fA-F]{6}", "").replace("§l", "").replace("§r", "").trim();
            if (l.isEmpty() || l.equals("Moves:") || l.matches("^(HP|ATK|DEF|SP[.]ATK|SP[.]DEF|SPE)( .*|$)")) continue;
            if (l.startsWith("- ")) { moves.add(l.substring(2)); continue; }
            if (out.size() < 7) out.add(l);
        }
        if (!moves.isEmpty() && out.size() < 8) out.add("Moves: " + String.join(", ", moves));
        return out;
    }
    private static final String[] PS_LABEL = {"HP", "ATK", "DEF", "SP.ATK", "SP.DEF", "SPE"};
    private static final String[] PS_COL = {"ff4d4d", "ff9a3d", "ffd42e", "4da0ff", "4dd64d", "e8a0e0"};
    private static String psType(String t) {
        return switch (t.toLowerCase(java.util.Locale.ROOT)) { case "normal" -> "c8c8a0"; case "fire" -> "ff8c42"; case "water" -> "6f9bff"; case "grass" -> "78cf55"; case "electric" -> "ffd940";
            case "ice" -> "9be0e8"; case "fighting" -> "e0453f"; case "poison" -> "c070c8"; case "ground" -> "e8c870"; case "flying" -> "b0a0ff"; case "psychic" -> "ff5e8c"; case "bug" -> "b8d030";
            case "rock" -> "c8b050"; case "ghost" -> "8a70b0"; case "dark" -> "8a7a70"; case "dragon" -> "7a50ff"; case "steel" -> "b8b8d0"; case "fairy" -> "ffa0c0"; default -> "ffffff"; };
    }
    private static String psPct(double p) { return p >= 100 ? "ffc83d" : p >= 90 ? "55ff55" : p >= 70 ? "c8ff60" : p >= 50 ? "ffee66" : "aaaaaa"; }
    private static String psTotal(String label, int[] v, int max, boolean iv) {
        int sum = 0;
        for (int x : v) sum += x;
        double pct = Math.floor(sum * 100.0 / max);
        StringBuilder sb = new StringBuilder("§#aaaaaa").append(label).append(" §#ffffff").append(sum).append('/').append(max).append(" §#666666(§#").append(psPct(pct)).append("§l").append((int) pct).append("%§r§#666666)");
        sb.append((char) 10).append(psRow(v, iv));
        return sb.toString();
    }
    private static String psRow(int[] v, boolean iv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i == 3) sb.append((char) 10); else if (i > 0) sb.append("§#666666  ");
            boolean zero = v[i] == 0, max = iv && v[i] >= 31;
            sb.append("§#").append(PS_COL[i]).append("§l").append(PS_LABEL[i]).append("§r ").append("§#").append(zero ? "666666" : PS_COL[i]).append(max ? "§l" : "").append(v[i]).append("§r");
        }
        return sb.toString();
    }
    /** the hover card of /pokeshout (CobblemonExtras ShoutStyle.card): name + level, types, ability, nature with its +/- stats, held item, IV and EV totals with a coloured stat row each, then the moves */
    private static JsonArray psCard(JsonObject d, String name, int lv, String t1, String t2) {
        java.util.List<String> L = new java.util.ArrayList<>();
        L.add("§#ffffff§l" + name + "§r §#aaaaaaLv." + lv);
        StringBuilder ty = new StringBuilder("§#").append(psType(t1)).append("§l").append(pretty(t1)).append("§r");
        if (!t1.equals(t2)) ty.append(" §#666666/ §#").append(psType(t2)).append("§l").append(pretty(t2)).append("§r");
        L.add(ty.toString());
        L.add(" ");
        if (!str(d, "ability").isEmpty()) L.add("§#aaaaaaAbility: §#ffffff" + named("abilities", str(d, "ability")));
        String nat = str(d, "nature");
        if (!nat.isEmpty()) {
            String eff = str(obj(rentalNames(), "natures"), nat), line = "§#aaaaaaNature: §#ffffff" + pretty(nat);
            if (eff.contains("/")) { String[] pe = eff.split(" / "); line += "  §#666666(§#55ff55" + pe[0].replace("+", "").trim() + " §#666666/ §#ff5555" + pe[1].replace("-", "").trim() + "§#666666)"; }
            L.add(line);
        }
        if (!str(d, "item").isEmpty()) L.add("§#aaaaaaHeld: §#ffffff" + pretty(str(d, "item")));
        JsonArray iv = arr(d, "ivs"), ev = arr(d, "evs");
        if (iv.size() == 6 || ev.size() == 6) L.add(" ");
        for (int w = 0; w < 2; w++) {
            JsonArray ar = w == 0 ? iv : ev;
            if (ar.size() != 6) continue;
            int[] v = new int[6];
            for (int i = 0; i < 6; i++) v[i] = ar.get(i).getAsInt();
            L.addAll(java.util.Arrays.asList(psTotal(w == 0 ? "IVs" : "EVs", v, w == 0 ? 186 : 510, w == 0).split("\n")));
        }
        JsonArray mvs = arr(d, "moves");
        if (mvs.size() > 0) {
            L.add(" ");
            L.add("§#aaaaaaMoves:");
            for (int q = 0; q < mvs.size() && q < 4; q++) {
                JsonObject m = mvs.get(q).getAsJsonObject();
                L.add("§#666666- §#" + psType(str(m, "type").isEmpty() ? "x" : str(m, "type")) + named("moves", str(m, "id")));
            }
        }
        JsonArray out = new JsonArray();
        for (int i = 0; i < L.size() && i < TIP_LINES; i++) out.add(L.get(i));
        return out;
    }

    private static final String[] TIP_COL = {"#ff3b3b", "#ff9a2e", "#ffc83a", "#3d8bff", "#3ddc4f", "#e04fe0"};

    /** the page's #rc-tip card as plain fields (the native hover card lays them out): name, types, ability / item / nature, IV and EV chips, moves */
    private static void tipFields(JsonObject k, JsonObject d, String name, int lv, String t1, String t2, String rarCol) {
        k.addProperty("tn", name);
        k.addProperty("tlv", "Lv." + lv);
        k.addProperty("ty1", pretty(t1));
        k.addProperty("ty1c", "#" + psType(t1));
        k.addProperty("ty2", pretty(t2));
        k.addProperty("ty2c", "#" + psType(t2));
        String ab = str(d, "ability"), it = str(d, "item"), nat = str(d, "nature");
        k.addProperty("hasAb", !ab.isEmpty());
        k.addProperty("ab", ab.isEmpty() ? "" : named("abilities", ab));
        k.addProperty("hasIt", !it.isEmpty());
        k.addProperty("item", it.isEmpty() ? "" : pretty(it));
        String eff = nat.isEmpty() ? "" : str(obj(rentalNames(), "natures"), nat);
        k.addProperty("hasNat", !nat.isEmpty());
        k.addProperty("nat", nat.isEmpty() ? "" : pretty(nat));
        String up = "", dn = "";
        if (eff.contains("/")) { String[] pe = eff.split(" / "); up = pe[0].trim(); dn = pe[1].trim(); }
        k.addProperty("up", up);
        k.addProperty("dn", dn);
        for (int w = 0; w < 2; w++) {
            JsonArray ar = arr(d, w == 0 ? "ivs" : "evs");
            String key = w == 0 ? "iv" : "ev";
            boolean has = ar.size() == 6;
            k.addProperty("has" + (w == 0 ? "Iv" : "Ev"), has);
            JsonArray items = new JsonArray();
            int sum = 0;
            if (has) for (int i = 0; i < 6; i++) {
                int v = ar.get(i).getAsInt();
                sum += v;
                JsonObject c = new JsonObject(); c.addProperty("chip", true); c.addProperty("t", PS_LABEL[i]); c.addProperty("c", TIP_COL[i]); items.add(c);
                JsonObject n = new JsonObject(); n.addProperty("chip", false); n.addProperty("t", String.valueOf(v)); n.addProperty("c", TIP_COL[i]); items.add(n);
            }
            int max = w == 0 ? 186 : 510;
            double pct = sum * 100.0 / max;
            k.addProperty(key + "T", sum + "/" + max);
            k.addProperty(key + "P", (pct == 100 ? "100" : String.format(java.util.Locale.ROOT, w == 0 ? "%.0f" : "%.2f", pct)) + "%");
            k.add(key + "s", items);
        }
        JsonArray mv = new JsonArray();
        JsonArray mvs = arr(d, "moves");
        for (int q = 0; q < mvs.size() && q < 4; q++) {
            JsonObject m = mvs.get(q).getAsJsonObject();
            if (q > 0) { JsonObject sep = new JsonObject(); sep.addProperty("t", "-"); sep.addProperty("c", "#7a7a7a"); mv.add(sep); }
            JsonObject e = new JsonObject();
            e.addProperty("t", named("moves", str(m, "id")));
            e.addProperty("c", "#" + psType(str(m, "type").isEmpty() ? "x" : str(m, "type")));
            mv.add(e);
        }
        k.add("mv", mv);
        k.addProperty("hasMv", mv.size() > 0);
        int rr = Integer.parseInt(rarCol.substring(1, 3), 16), rg = Integer.parseInt(rarCol.substring(3, 5), 16), rb = Integer.parseInt(rarCol.substring(5, 7), 16);
        k.addProperty("tring", String.format("#%02x%02x%02x", Math.round(rr * .55f + 0x28 * .45f), Math.round(rg * .55f + 0x00 * .45f), Math.round(rb * .55f + 0x7f * .45f)));
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("[_ -]+")) if (!w.isEmpty()) { if (sb.length() > 0) sb.append(' '); sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)); }
        return sb.toString();
    }

    private static String named(String group, String id) {
        JsonObject g = obj(rentalNames(), group);
        return g != null && g.has(id) ? g.get(id).getAsString() : pretty(id);
    }

    /** the reveal lasts until the last card has flipped (900 ms + 300 ms per card + the 850 ms flip) unless skipped */
    /** charge time before the flip (the page'"'"'s rarity table: ct * .8) */
    private static int rdCharge(String r) {
        return (int) Math.round((switch (r) { case "uncommon" -> 700; case "rare" -> 950; case "epic" -> 1200; case "legendary" -> 1500; case "mythic", "mythical" -> 1800; default -> 450; }) * .8);
    }

    private static String rdRarCol(String r) {
        return switch (r) { case "uncommon" -> "#62d56f"; case "rare" -> "#4ba8ff"; case "epic" -> "#b866ff"; case "legendary" -> "#ffb52e"; case "mythic", "mythical" -> "#ff4f86"; default -> "#aab2c5"; };
    }

    /** the page: choosing starts 900 ms (tear) + max(i * 300 + 300 + ct * .8 + 560 + 1500) after the click */
    private static long rdRevealMs(JsonObject rd) {
        JsonArray cs = rdCards(rd);
        long h = 0;
        for (int i = 0; i < cs.size(); i++) {
            JsonObject c = cs.get(i).getAsJsonObject();
            String r = str(c, "rarity");
            if (r.isEmpty()) { JsonObject info = obj(rentalTypes(), str(c, "species")); if (info != null) r = str(info, "r"); }
            h = Math.max(h, i * 300L + 300 + rdCharge(r) + 560 + 1500);
        }
        return 900L + h;
    }

    private static boolean rdRevealing(JsonObject st, JsonObject rd) {
        if (bool(st, "rdSkip") || !st.has("rdOpenAt")) return false;
        return System.currentTimeMillis() - (long) num(st, "rdOpenAt") < rdRevealMs(rd);
    }

    private static boolean isBig(JsonObject c) { String r = str(c, "rarity"); return r.equals("legendary") || r.equals("mythic"); }

    /** legendary/mythic cards already kept in earlier packs plus the selected ones */
    private static int bigCount(JsonObject rd, JsonArray cards, JsonArray sel) {
        int n = 0;
        int cur = (int) num(rd, "current");
        JsonArray packs = arr(rd, "packs");
        for (int pi = 0; pi < packs.size() && pi < cur; pi++) {
            JsonObject pk = packs.get(pi).getAsJsonObject();
            for (JsonElement ki : arr(pk, "kept")) { int k = ki.getAsInt(); if (k >= 0 && k < arr(pk, "cards").size() && isBig(arr(pk, "cards").get(k).getAsJsonObject())) n++; }
        }
        for (JsonElement e : sel) { int i = e.getAsInt(); if (i >= 0 && i < cards.size() && isBig(cards.get(i).getAsJsonObject())) n++; }
        return n;
    }

    private static String accOf(String type) {
        for (String[] t : TYPE_COL) if (t[0].equals(type)) return t[1];
        return "a8a77a";
    }

    private static String dark(String hex) {         // color-mix(in srgb, acc 42%, #000)
        int r = Integer.parseInt(hex.substring(0, 2), 16), g = Integer.parseInt(hex.substring(2, 4), 16), b = Integer.parseInt(hex.substring(4, 6), 16);
        return String.format("#%02x%02x%02x", Math.round(r * .42f), Math.round(g * .42f), Math.round(b * .42f));
    }

    private static JsonObject rental(JsonObject st, JsonObject rd) {
        JsonObject o = new JsonObject();
        String stage = st.has("rdStage") ? str(st, "rdStage") : "warn";
        o.addProperty("stage", stage);
        o.addProperty("calm", bool(st, "noAnim"));
        o.addProperty("warnTitle", "Warning: flashing lights and animation");
        o.addProperty("warnText", "Opening the rental packs uses bursts of light, sparks, glints and screen shake. If you are sensitive to flashing or motion, for example photosensitive epilepsy, migraine or motion sickness, turn on calm effects before you continue. It removes these effects here and on the Ascension pages, and you can change it at any time in Settings.");
        int cur = (int) num(rd, "current");
        boolean done = bool(rd, "complete");
        o.addProperty("packText", "Pack " + Math.min(3, cur + 1) + " of 3");
        JsonArray sel = arr(st, "rdSel");
        o.addProperty("chooseText", "Choose two cards to keep (" + sel.size() + " of 2)");
        o.addProperty("canKeep", sel.size() == 2);
        o.addProperty("akey", "p" + cur + ":" + st.get("rdOpenN"));
        boolean skip = bool(st, "rdSkip");
        o.addProperty("skip", skip);
        o.addProperty("dend", skip ? 0 : (int) rdRevealMs(rd));
        o.addProperty("selA", sel.size() > 0 ? sel.get(0).getAsInt() : 0);
        o.addProperty("selB", sel.size() > 1 ? sel.get(1).getAsInt() : 0);
        JsonArray dots = new JsonArray();
        for (int i = 0; i < 3; i++) {
            boolean on = done || i < cur, here = !done && i == cur;
            JsonObject d = new JsonObject();
            d.addProperty("bg", on ? "#16a34a" : here ? "#3f6fe6" : "#4d5e90");
            d.addProperty("bd", here ? "#f8e7c2" : on ? "#16a34a" : "#4d5e90");
            dots.add(d);
        }
        o.add("dots", dots);
        JsonObject types = rentalTypes();
        JsonArray cards = new JsonArray();
        JsonArray src = rdCards(rd);
        for (int i = 0; i < src.size(); i++) {
            JsonObject c = src.get(i).getAsJsonObject();
            String sp = str(c, "species");
            JsonObject info = obj(types, sp);
            String t1 = "normal", t2 = "normal", rar = str(c, "rarity");
            int[] bb = {8, 4, 24, 24};
            if (info != null) {
                JsonArray ta = arr(info, "t");
                if (ta.size() > 0) { t1 = ta.get(0).getAsString(); t2 = ta.size() > 1 ? ta.get(1).getAsString() : t1; }
                JsonArray ba = arr(info, "b");
                if (ba.size() == 4) for (int k = 0; k < 4; k++) bb[k] = ba.get(k).getAsInt();
                if (rar.isEmpty()) rar = str(info, "r");
            }
            if (rar.isEmpty()) rar = "common";
            JsonObject d = obj(c, "details");
            int lv = d != null && d.has("level") ? (int) num(d, "level") : info != null ? (int) num(info, "l") : 50;
            int s = Math.max(4, Math.min(11, (int) Math.floor(Math.min(228.0 / bb[2], 232.0 / bb[3]))));
            double dx = 140 - (bb[0] + bb[2] / 2.0) * s, dy = 36 + 118 - (bb[1] + bb[3] / 2.0) * s;
            JsonObject k = new JsonObject();
            k.addProperty("i", i);
            k.addProperty("num", String.valueOf(i + 1));
            String nm = str(c, "name");
            k.addProperty("name", nm.isEmpty() ? cap(sp) : nm);
            k.addProperty("lv", String.valueOf(lv));
            k.addProperty("big", rar.equals("legendary") || rar.equals("mythic"));
            int sk = skip ? 1 : 0;       // skipping: every reveal motion is already past
            final int past = -4000;      // a negative delay starts the motion part-way: far enough that it is over
            int chg = rdCharge(rar), t0 = 900 + i * 300 + chg;     // the card charges (shakes, glows) for ct * .8, then flips
            k.addProperty("dcharge", sk == 1 ? past : 900 + i * 300);
            k.addProperty("hv", switch (rar) { case "rare" -> 0.3; case "epic" -> 0.5; case "legendary" -> 0.7; case "mythic" -> 0.95; default -> 0.0; });
            k.addProperty("rcol", rdRarCol(rar));
            k.addProperty("chg", chg);
            k.addProperty("dflip", sk == 1 ? past : t0);
            k.addProperty("dhold", sk == 1 ? past : i * 300 + chg);
            k.addProperty("dstamp", sk == 1 ? past : t0 + 380);
            k.addProperty("dslam", sk == 1 ? past : t0 + 300);
            k.addProperty("dflash", sk == 1 ? past : t0 + 550);
            k.addProperty("dprism", sk == 1 ? past : t0 + 800);
            for (int q = 0; q < 4; q++) k.addProperty("dr" + q, sk == 1 ? past : t0 + 600 + q * 140);
            if (d != null) {                                                         // the page's hover card: name, types, ability, item, nature, moves
                tipFields(k, d, nm.isEmpty() ? cap(sp) : nm, lv, t1, t2, rdRarCol(rar));
                JsonArray tl = psCard(d, nm.isEmpty() ? cap(sp) : nm, lv, t1, t2);
                if (LEGACY_TIP.get()) tl = legacyTip(tl);
                for (int q = 0; q < TIP_LINES; q++) k.addProperty("tip" + q, q < tl.size() ? tl.get(q).getAsString() : "");
            }
            k.addProperty("nr", switch (rar) { case "rare" -> 2; case "epic" -> 3; case "legendary", "mythic" -> 4; default -> 1; });
            k.addProperty("rc", switch (rar) { case "uncommon" -> "#62d56f"; case "rare" -> "#4ba8ff"; case "epic" -> "#b866ff"; case "legendary" -> "#ffb52e"; case "mythic" -> "#ff4f86"; default -> "#aab2c5"; });
            k.addProperty("stamp", TEX + "rd_stamp_" + (rar.equals("mythic") ? "mythic" : "legendary") + ".png");
            k.addProperty("base", TEX + "rd_" + t1 + ".png");
            k.addProperty("rar", TEX + "rd_r_" + rar + ".png");
            k.addProperty("acc2", dark(accOf(t1)));
            k.addProperty("two", !t1.equals(t2));
            k.addProperty("gem1", TEX + "rd_gem_" + t1 + ".png");
            k.addProperty("gem2", TEX + "rd_gem_" + t2 + ".png");
            k.addProperty("med1", TEX + "rd_med_" + t1 + ".png");
            k.addProperty("med2", TEX + "rd_med_" + t2 + ".png");
            k.addProperty("sp1", TEX + "rsc_" + sp + "_1.png");
            k.addProperty("sp2", TEX + "rsc_" + sp + "_2.png");
            boolean kept = false;
            for (JsonElement e : sel) if (e.getAsInt() == i) kept = true;
            k.addProperty("kept", kept && !done);
            k.addProperty("lift1", kept && !done ? 0 : 14);
            k.addProperty("lift2", 0);
            k.addProperty("sty", Math.round(((kept && !done ? 0 : 14) + 90 * 218.4 / 280) * 100) / 100.0);
            cards.add(k);
        }
        o.add("cards", cards);
        return o;
    }


    // ------------------------------------------------------------------ feature sections (TowerFeatureState): the Codex and its list/entry/review screens

    private static void featSend(ServerPlayer p, JsonObject st, String section, String action, String argument) {
        int rid = (int) num(st, "freq") + 1;
        st.addProperty("freq", rid);
        st.addProperty("fsec", section);
        st.addProperty("fpend", true);
        st.addProperty("fpendAt", System.currentTimeMillis());
        forwardKind(p, "TowerFeatureRequest", "requestId", rid, "section", section, "action", action, "argument", argument);
    }

    /** app.js rt(): a feature action either opens the lobby, another section, a confirmation, or is sent as is */
    private static void featAct(ServerPlayer p, JsonObject st, String section, JsonObject a) {
        String id = str(a, "id");
        if (id.equals("lobby")) { forwardKind(p, "TowerHallAction", "action", "lobby", "argument", "", "revision", ""); return; }
        if (id.equals("page")) { featSend(p, st, str(a, "argument"), "open", ""); return; }
        if (!str(a, "confirmation").isEmpty() || !str(a, "inputHint").isEmpty()) { st.add("frev", a); return; }
        featSend(p, st, section, id, str(a, "argument"));
    }

    private static JsonObject featEntry(JsonObject fe, String id) {
        for (JsonElement e : arr(fe, "entries")) if (str(e.getAsJsonObject(), "id").equals(id)) return e.getAsJsonObject();
        return null;
    }

    private static JsonArray featItems(JsonObject fe) {
        JsonArray out = new JsonArray();
        for (JsonElement e : arr(fe, "entries")) {
            JsonObject o = e.getAsJsonObject(), it = new JsonObject();
            it.addProperty("kind", "entry");
            it.addProperty("id", str(o, "id"));
            it.addProperty("label", str(o, "title"));
            out.add(it);
        }
        for (JsonElement e : arr(fe, "actions")) {
            JsonObject o = e.getAsJsonObject(), it = new JsonObject();
            it.addProperty("kind", "action");
            it.addProperty("id", str(o, "id"));
            it.addProperty("label", str(o, "label"));
            it.add("action", o);
            out.add(it);
        }
        return out;
    }

    /** app.js Xi(): a one-line summary of an entry */
    private static String featSub(JsonObject en) {
        JsonArray ls = arr(en, "lines");
        if (ls.size() == 0) return "";
        String pick = ls.get(0).getAsString();
        for (JsonElement l : ls) { String t = l.getAsString(); if (!t.matches("^(Tags: |The risk you carry|Held for this run).*")) { pick = t; break; } }
        pick = pick.replaceFirst("^\\[[+-]\\]\\s*", "").replaceAll("\\s+\\*\\s+", " \u00b7 ");
        return pick.length() > 90 ? pick.substring(0, 89).stripTrailing() + "\u2026" : pick;
    }

    private static JsonObject featureVm(JsonObject st) {
        JsonObject o = new JsonObject();
        JsonObject fe = obj(st, "fe");
        boolean pend = bool(st, "fpend");
        boolean stale = pend && System.currentTimeMillis() - (long) num(st, "fpendAt") > 8000;
        o.addProperty("status", pend ? (stale ? "Response delayed. Refresh before another action." : "Waiting for the server\u2026") : fe != null ? str(fe, "message") : "");
        o.addProperty("canAct", !pend || stale);
        if (fe == null || !str(fe, "section").equals(str(st, "fsec"))) {
            o.addProperty("loading", true);
            o.addProperty("mode", "list");
            o.addProperty("canAct", false);
            return o;
        }
        o.addProperty("loading", false);
        JsonObject rv = obj(st, "frev");
        JsonObject en = st.has("fsel") ? featEntry(fe, str(st, "fsel")) : null;
        String mode = rv != null ? "review" : en != null ? "entry" : "list";
        o.addProperty("mode", mode);
        o.addProperty("review", rv != null);
        o.addProperty("title", str(fe, "title"));
        o.addProperty("heading", rv != null ? str(rv, "label") : en != null ? str(en, "title") : str(fe, "title"));
        JsonArray items = featItems(fe);
        o.addProperty("count", items.size() + (items.size() == 1 ? " item" : " items"));
        o.addProperty("hasItems", items.size() > 0);
        JsonArray vi = new JsonArray();
        for (int i = 0; i < items.size(); i++) {
            JsonObject it = items.get(i).getAsJsonObject(), v = new JsonObject();
            boolean entry = str(it, "kind").equals("entry");
            boolean sel = entry && en != null && str(it, "id").equals(str(en, "id"));
            v.addProperty("i", i);
            v.addProperty("label", str(it, "label"));
            v.addProperty("sub", entry && mode.equals("list") ? featSub(featEntry(fe, str(it, "id"))) : "");
            v.addProperty("bg", !entry ? "#1d2a50" : "#16213f");
            v.addProperty("bd", sel ? "#3f6fe6" : "#2b3b68");
            vi.add(v);
        }
        o.add("items", vi);
        JsonArray lines = new JsonArray();
        JsonArray src = rv != null ? new JsonArray() : en != null ? arr(en, "lines") : new JsonArray();
        if (rv != null) src.add(!str(rv, "confirmation").isEmpty() ? str(rv, "confirmation") : str(rv, "label"));
        for (JsonElement e : src) {
            String t = e.getAsString();
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("^\\[([+-])\\]\\s*(.*)$").matcher(t);
            JsonObject l = new JsonObject();
            String sign = "\u00b7", dot = "#4d5e90";
            if (mm.find()) { sign = mm.group(1).equals("+") ? "+" : "\u2212"; dot = mm.group(1).equals("+") ? "#15803d" : "#d6264e"; t = mm.group(2); }
            l.addProperty("sign", sign);
            l.addProperty("dot", dot);
            l.addProperty("text", t.replaceAll("\\s+\\*\\s+", " \u00b7 "));
            lines.add(l);
        }
        o.add("lines", lines);
        boolean prim = mode.equals("entry") && arr(en, "actions").size() > 0;
        o.addProperty("hasPrimary", prim);
        o.addProperty("primary", prim ? str(arr(en, "actions").get(0).getAsJsonObject(), "label") : "");
        boolean needs = rv != null && !str(rv, "inputHint").isEmpty();
        o.addProperty("needsInput", needs);
        o.addProperty("hint", needs ? str(rv, "inputHint") : "");
        return o;
    }


    // ------------------------------------------------------------------ party registration, scouting reveal, spectator box

    private static String speciesOf(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** app.js Qi()/ea(): the player's Pokemon with their order in the chosen party */
    private static JsonObject partyVm(JsonObject reg) {
        JsonObject o = new JsonObject();
        JsonArray chosen = arr(reg, "chosen"), tiles = new JsonArray();
        int max = (int) num(reg, "max");
        o.addProperty("sub", chosen.size() == 0 ? "Nothing chosen: your current party will be used" : chosen.size() + " of " + max + " registered");
        for (JsonElement pe : arr(reg, "pokemon")) {
            JsonObject pk = pe.getAsJsonObject(), t = new JsonObject();
            int order = 0;
            for (int i = 0; i < chosen.size(); i++) if (chosen.get(i).getAsString().equals(str(pk, "id"))) order = i + 1;
            boolean out = bool(pk, "fainted");
            t.addProperty("id", str(pk, "id"));
            t.addProperty("name", str(pk, "name"));
            t.addProperty("sub", "Lv " + (int) num(pk, "level") + (out ? " \u00b7 fainted" : ""));
            t.addProperty("where", str(pk, "where"));
            t.addProperty("order", order == 0 ? "" : String.valueOf(order));
            t.addProperty("on", order > 0);
            t.addProperty("op", out ? 0.6 : 1.0);
            t.addProperty("pic", SpriteService.portraitKey(null, speciesOf(str(pk, "name")), false));
            t.addProperty("bg", order > 0 ? "#1d2a50" : "#16213f");
            t.addProperty("bd", order > 0 ? "#3f6fe6" : "#2b3b68");
            t.addProperty("ring", order > 0 ? "3,#355190" : "0,#00000000");
            tiles.add(t);
        }
        o.add("mons", tiles);
        o.addProperty("none", tiles.size() == 0);
        return o;
    }

    /** app.js xn(): which glyph and chips a scouted category shows */
    private static JsonObject scoutCat(JsonObject c, int i, boolean sel) {
        String name = str(c, "name").toLowerCase(java.util.Locale.ROOT), val = str(c, "value");
        String glyph = "scroll", kind = "other";
        if (name.contains("type")) { glyph = "swords"; kind = "types"; }
        else if (name.contains("lead")) { glyph = "crown"; kind = "lead"; }
        else if (name.contains("held") || name.contains("item")) { glyph = "bag"; kind = "items"; }
        else if (name.contains("move")) { glyph = "swords"; kind = "moves"; }
        else if (name.contains("boss")) { glyph = "skull"; kind = "boss"; }
        else if (name.contains("weather")) {
            String v = val.toLowerCase(java.util.Locale.ROOT);
            glyph = v.contains("rain") ? "rain" : v.contains("sun") ? "sun" : v.contains("sand") ? "sand" : v.contains("snow") || v.contains("hail") ? "snow" : "waves";
            kind = "weather";
        }
        JsonObject o = new JsonObject();
        o.addProperty("i", i);
        o.addProperty("name", str(c, "name"));
        o.addProperty("value", val);
        o.addProperty("glyph", TEX + "glyph_" + glyph + ".png");
        o.addProperty("kind", kind);
        o.addProperty("bg", sel ? "#1d2a50" : "#16213f");
        o.addProperty("bd", sel ? "#3f6fe6" : "#2b3b68");
        o.addProperty("ring", sel ? "3,#355190" : "0,#00000000");
        JsonArray chips = new JsonArray();
        if (kind.equals("types") || kind.equals("items") || kind.equals("moves")) {
            for (String part : val.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) continue;
                JsonObject ch = new JsonObject();
                String col = kind.equals("types") ? accOf(t.toLowerCase(java.util.Locale.ROOT)) : "";
                boolean typed = kind.equals("types");
                ch.addProperty("text", kind.equals("types") ? t.toUpperCase(java.util.Locale.ROOT) : t);
                ch.addProperty("bg", typed ? "#" + col : "#1d2a50");
                ch.addProperty("bd", typed ? "#" + col : "#2b3b68");
                int lum = typed ? Integer.parseInt(col.substring(0, 2), 16) * 299 + Integer.parseInt(col.substring(2, 4), 16) * 587 + Integer.parseInt(col.substring(4, 6), 16) * 114 : 0;
                ch.addProperty("fg", typed && lum > 140000 ? "#101018" : "#ffffff");
                chips.add(ch);
            }
        }
        o.add("chips", chips);
        o.addProperty("hasChips", chips.size() > 0);
        o.addProperty("lead", kind.equals("lead") ? SpriteService.portraitKey(null, speciesOf(val.split("[ ,(]")[0]), false) : "");
        o.addProperty("hasLead", kind.equals("lead"));
        o.addProperty("isBoss", kind.equals("boss"));
        return o;
    }

    private static JsonObject scoutVm(JsonObject st, JsonObject sc) {
        JsonObject o = new JsonObject();
        JsonArray cats = arr(sc, "categories"), list = new JsonArray();
        int sel = Math.max(0, Math.min((int) num(st, "scSel"), cats.size() - 1));
        for (int i = 0; i < cats.size(); i++) list.add(scoutCat(cats.get(i).getAsJsonObject(), i, i == sel));
        o.addProperty("floor", (int) num(sc, "floorIndex"));
        o.addProperty("count", cats.size() + (cats.size() == 1 ? " detail" : " details"));
        o.add("cats", list);
        o.addProperty("none", cats.size() == 0);
        o.addProperty("hasSel", cats.size() > 0);
        if (cats.size() > 0) o.add("sel", list.get(sel));
        return o;
    }

    private static JsonObject specVm(JsonObject sp) {
        JsonObject o = new JsonObject();
        o.addProperty("title", "Following " + str(sp, "teammateName"));
        o.addProperty("line", (int) num(sp, "remainingCount") + "/" + (int) num(sp, "totalCount") + " Pok\u00e9mon remaining");
        o.addProperty("sub", "Floor " + (int) num(sp, "floorIndex") + " \u00b7 " + str(sp, "runStateLabel"));
        return o;
    }


    // ------------------------------------------------------------------ route map of the intermission (extras.route.now / ahead: app.js ri() + li())

    private static String roomName(String k) {
        return switch (k) { case "POOL" -> "Mystic Pool"; case "TRICK_OR_TREAT" -> "Trick or Treat"; case "RELIC" -> "Relic"; default -> pretty(k.toLowerCase(java.util.Locale.ROOT)); };
    }

    private static String roomGlyph(String k) {
        return TEX + "glyph_" + switch (k) { case "BATTLE" -> "swords"; case "ELITE", "BOSS" -> "skull"; case "REST" -> "plus"; case "SHRINE" -> "orb"; case "FORGE" -> "bolt"; case "LIBRARY" -> "book";
            case "VAULT" -> "coffer"; case "POOL" -> "waves"; case "GAMBLER" -> "dice"; case "MERCHANT" -> "bag"; case "RELIC" -> "gem"; case "TRICK_OR_TREAT" -> "mask"; default -> "scroll"; } + ".png";
    }

    private static String roomColor(String k) {
        return switch (k) { case "BATTLE" -> "#3b82d6"; case "FORGE" -> "#e8702a"; case "REST" -> "#3fa34d"; case "SHRINE" -> "#8c5cc9"; case "GAMBLER" -> "#d9468f"; case "VAULT" -> "#0e9aa7";
            case "LIBRARY" -> "#c98a06"; case "POOL" -> "#2f7fd0"; case "RELIC", "BOSS" -> "#d6264e"; case "ELITE" -> "#e5533d"; case "MERCHANT" -> "#e0b422"; case "TRICK_OR_TREAT" -> "#ff8a2b";
            default -> "#93a2c9"; };
    }

    private static boolean laneNear(int a, int b) { return a < 0 || Math.abs(a - b) <= 1; }

    private static JsonObject routeVm(JsonObject route, JsonArray cards, int sel, boolean canPick, int chosen) {
        JsonArray nowA = arr(route, "now"), ahead = arr(route, "ahead");
        int cols = 1 + ahead.size();
        double[] centers = new double[cols];
        double total = 3 + (cols - 1), acc = 0;
        for (int i = 0; i < cols; i++) { double w = i == 0 ? 3 : 1; centers[i] = (acc + w / 2) / total * 100; acc += w; }
        int[][] state = new int[cols][3];                         // 0 blocked, 1 now, 2 ahead, 3 milestone
        JsonObject o = new JsonObject();
        JsonArray colsJ = new JsonArray();
        for (int ci = 0; ci < cols; ci++) {
            JsonObject col = new JsonObject();
            col.addProperty("pw", Math.round((ci == 0 ? 3 : 1) / total * 512 * 100) / 100.0);
            JsonObject floor = ci == 0 ? null : ahead.get(ci - 1).getAsJsonObject();
            boolean mile = floor != null && bool(floor, "milestone");
            col.addProperty("head", ci == 0 ? "Choose" : mile ? "F" + (int) num(floor, "floor") + " relic" : "After F" + (int) num(floor, "floor"));
            col.addProperty("now", ci == 0);
            JsonArray slots = new JsonArray();
            for (int lane = 0; lane < 3; lane++) {
                JsonObject nd = new JsonObject();
                String kind = "";
                int card = -1, st = 0;
                String arena = "";
                if (ci == 0) {
                    for (JsonElement e : nowA) { JsonObject n = e.getAsJsonObject(); if ((int) num(n, "lane") == lane) { kind = str(n, "kind"); card = (int) num(n, "card"); arena = str(n, "arena"); st = 1; } }
                } else if (mile) {
                    if (lane == 1) { kind = "RELIC"; st = 3; }
                } else {
                    JsonArray ks = arr(floor, "kinds");
                    if (lane < ks.size() && !ks.get(lane).getAsString().isEmpty()) { kind = ks.get(lane).getAsString(); st = 2; }
                }
                state[ci][lane] = st;
                String k = kind.isEmpty() ? "" : kind;
                JsonObject card0 = card >= 0 && card < cards.size() ? cards.get(card).getAsJsonObject() : null;
                String title = card0 != null ? str(card0, "name") : "";
                String sub = roomName(kind) + (card0 != null && !str(card0, "payout").isEmpty() ? " \u00b7 " + str(card0, "payout") : "");
                boolean isSel = st == 1 && card >= 0 && card == sel, isChosen = st == 1 && card >= 0 && card == chosen;
                String kc = st == 0 ? "#4d5e90" : roomColor(kind);
                nd.addProperty("st", st);
                nd.addProperty("isNow", st == 1);
                nd.addProperty("isAhead", st == 2 || st == 3);
                nd.addProperty("isBlocked", st == 0);
                nd.addProperty("card", card);
                nd.addProperty("name", title.isEmpty() ? roomName(kind) : title);
                nd.addProperty("sub", sub);
                nd.addProperty("label", st == 3 ? "Relic" : roomName(kind));
                nd.addProperty("glyph", kind.isEmpty() ? TEX + "glyph_scroll.png" : roomGlyph(kind));
                nd.addProperty("k", kc);
                nd.addProperty("bg", isSel || isChosen ? kc : "#16213f");
                nd.addProperty("fg", isSel || isChosen ? "#ffffff" : "#e9eefc");
                nd.addProperty("fg2", isSel || isChosen ? "#ffffffbf" : "#93a2c9");
                nd.addProperty("ring", isSel || isChosen ? "3,#ffffff" : "0,#00000000");
                nd.addProperty("on", canPick && st == 1);
                slots.add(nd);
            }
            col.add("slots", slots);
            colsJ.add(col);
        }
        o.add("cols", colsJ);
        double[] ys = {16.67, 50, 83.33};
        JsonArray edges = new JsonArray();
        for (int r = 0; r + 1 < cols; r++) for (int a = 0; a < 3; a++) {
            if (state[r][a] == 0) continue;
            for (int b = 0; b < 3; b++) {
                if (state[r + 1][b] == 0) continue;
                boolean mile = state[r][a] == 3 || state[r + 1][b] == 3;
                if (!(mile || laneNear(a, b))) continue;
                JsonObject e = new JsonObject();
                e.addProperty("t", String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f,%.2f,%d", centers[r], ys[a], centers[r + 1], ys[b], r == 0 ? 0 : 1));
                e.addProperty("col", r == 0 ? "#7ea2ff" : "#4d5e90");
                e.addProperty("lw", r == 0 ? 3 : 2);
                edges.add(e);
            }
        }
        o.add("edges", edges);
        int lane = (int) num(route, "lane");
        o.addProperty("youLane", lane >= 0 ? lane : 1);
        return o;
    }


    // ------------------------------------------------------------------ atlas route graph (extras.route.graph: app.js di() + yi())

    private static JsonObject atlasVm(JsonObject route, JsonArray cards, int sel, boolean canPick, int chosen) {
        JsonObject g = obj(route, "graph");
        int rows = (int) num(g, "rows");
        JsonArray trailA = arr(g, "trail"), stops = arr(g, "stops"), links = arr(g, "links"), nowA = arr(route, "now");
        int[] trail = trailA.size() == 0 ? new int[]{1} : new int[trailA.size()];
        for (int i = 0; i < trailA.size(); i++) trail[i] = trailA.get(i).getAsInt();
        int n = trail.length - 1, cur = trail[n], reveal = (int) num(g, "reveal");
        java.util.Set<Integer> reach = new java.util.HashSet<>();
        for (JsonElement e : links) { JsonObject l = e.getAsJsonObject(); if ((int) num(l, "row") == n && (int) num(l, "from") == cur) reach.add((int) num(l, "to")); }
        double fullH = 72 + rows * 54, availH = 470, sc = Math.min(1.0, availH / fullH);
        double[] lx = {90, 180, 270};
        JsonObject o = new JsonObject();
        o.addProperty("w", Math.round(360 * sc * 10) / 10.0);
        o.addProperty("h", Math.round(fullH * sc * 10) / 10.0);
        JsonArray nodes = new JsonArray(), opts = new JsonArray(), edges = new JsonArray();
        for (JsonElement e : stops) {
            JsonObject sp = e.getAsJsonObject();
            int row = (int) num(sp, "row"), lane = (int) num(sp, "lane");
            String kind = str(sp, "kind"), state;
            if (row <= n) state = row == n && lane == cur ? "current" : trail[Math.min(row, trail.length - 1)] == lane ? "done" : "passed";
            else if (row == n + 1) state = reach.contains(lane) ? "reach" : "seen";
            else state = row <= reveal || kind.equals("BOSS") ? "seen" : "fog";
            int card = -1;
            if (state.equals("reach")) for (JsonElement ne : nowA) { JsonObject nn = ne.getAsJsonObject(); if ((int) num(nn, "lane") == lane) card = (int) num(nn, "card"); }
            double cx = lx[Math.max(0, Math.min(2, lane))], cy = 36 + (rows - row) * 54;
            double half = kind.equals("BOSS") ? 21 : 17;
            String kc = state.equals("fog") ? "#6f6a80" : roomColor(kind);
            boolean isSel = card >= 0 && card == sel, isCh = card >= 0 && card == chosen;
            JsonObject nd = new JsonObject();
            nd.addProperty("x", Math.round((cx - half) * sc * 10) / 10.0);
            nd.addProperty("y", Math.round((cy - 19) * sc * 10) / 10.0);
            nd.addProperty("w", Math.round(half * 2 * sc * 10) / 10.0);
            nd.addProperty("h", Math.round(36 * sc * 10) / 10.0);
            nd.addProperty("r", Math.round(half * sc * 10) / 10.0);
            nd.addProperty("gx", Math.round((half - 10) * sc * 10) / 10.0);
            nd.addProperty("gs", Math.round(20 * sc * 10) / 10.0);
            nd.addProperty("bx", Math.round((cx + half - 12) * sc * 10) / 10.0);
            nd.addProperty("by", Math.round((cy - 26) * sc * 10) / 10.0);
            nd.addProperty("lx", Math.round((cx - 40) * sc * 10) / 10.0);
            nd.addProperty("ly", Math.round((cy + 21) * sc * 10) / 10.0);
            nd.addProperty("glyph", state.equals("fog") ? TEX + "glyph_scroll.png" : roomGlyph(kind));
            nd.addProperty("k", kc);
            nd.addProperty("bg", isSel || isCh ? kc : "#16141c");
            nd.addProperty("op", state.equals("passed") ? 0.38 : state.equals("fog") ? 0.5 : state.equals("seen") ? 0.85 : 1.0);
            nd.addProperty("ring", state.equals("current") ? "2,#ffffff" : state.equals("reach") ? "3," + kc + "55" : "0,#00000000");
            nd.addProperty("bw", state.equals("current") ? 2.5 : 2.4);
            nd.addProperty("bd", state.equals("current") ? "#ffffff" : kc);
            nd.addProperty("on", canPick && state.equals("reach") && card >= 0); nd.addProperty("kg", kc + "66"); nd.addProperty("reach", state.equals("reach"));
            nd.addProperty("card", card);
            nd.addProperty("done", state.equals("done"));
            nd.addProperty("cur", state.equals("current"));
            nd.addProperty("name", roomName(kind));
            nodes.add(nd);
        }
        for (JsonElement e : links) {
            JsonObject l = e.getAsJsonObject();
            int row = (int) num(l, "row"), from = (int) num(l, "from"), to = (int) num(l, "to");
            String st = "future";
            if (row < n) st = trail[Math.min(row, trail.length - 1)] == from && trail[Math.min(row + 1, trail.length - 1)] == to ? "taken" : "dead";
            else if (row == n) st = from == cur ? "open" : "dead";
            double x1 = lx[Math.max(0, Math.min(2, from))] / 360 * 100, y1 = (36 + (rows - row) * 54) / fullH * 100, x2 = lx[Math.max(0, Math.min(2, to))] / 360 * 100, y2 = (36 + (rows - row - 1) * 54) / fullH * 100;
            JsonObject ed = new JsonObject();
            ed.addProperty("t", String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f,%.2f,0,1", x1, y1, x2, y2));
            ed.addProperty("st", st);
            edges.add(ed);
        }
        o.add("nodes", nodes);
        o.add("edges", edges);
        // the choosable rooms on the left, like the page's .aopt buttons
        java.util.List<JsonObject> reachable = new java.util.ArrayList<>();
        for (JsonElement ne : nowA) reachable.add(ne.getAsJsonObject());
        reachable.sort((a, b) -> Integer.compare((int) num(a, "lane"), (int) num(b, "lane")));
        for (JsonObject nn : reachable) {
            int card = (int) num(nn, "card");
            String kind = str(nn, "kind"), kc = roomColor(kind);
            JsonObject card0 = card >= 0 && card < cards.size() ? cards.get(card).getAsJsonObject() : null;
            boolean isSel = card == sel, isCh = card == chosen;
            JsonObject nd = new JsonObject();
            nd.addProperty("card", card);
            nd.addProperty("name", card0 != null ? str(card0, "name") : roomName(kind));
            nd.addProperty("sub", roomName(kind) + (card0 != null && !str(card0, "payout").isEmpty() ? " \u00b7 " + str(card0, "payout") : ""));
            nd.addProperty("glyph", roomGlyph(kind));
            nd.addProperty("k", kc);
            nd.addProperty("bg", isSel || isCh ? kc : "#16213f");
            nd.addProperty("fg", isSel || isCh ? "#ffffff" : "#e9eefc");
            nd.addProperty("fg2", isSel || isCh ? "#ffffffbf" : "#93a2c9");
            nd.addProperty("ring", isSel || isCh ? "3,#ffffff" : "0,#00000000");
            nd.addProperty("on", canPick);
            opts.add(nd);
        }
        o.add("opts", opts);
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (JsonElement e : stops) { String k = str(e.getAsJsonObject(), "kind"); if (!k.isEmpty()) seen.add(k); }
        JsonArray leg = new JsonArray();
        for (String k : seen) { JsonObject li = new JsonObject(); li.addProperty("glyph", roomGlyph(k)); li.addProperty("name", roomName(k)); li.addProperty("k", roomColor(k)); leg.add(li); }
        o.add("legend", leg);
                return o;
    }

    private static void forwardKind(ServerPlayer p, String kind, Object... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] instanceof Number n) o.addProperty((String) kv[i], n);
            else o.addProperty((String) kv[i], String.valueOf(kv[i + 1]));
        }
        SnobbleUi.forward(p, "cobbletowers", o);
    }

    private static final java.util.Set<String> ITEM_ICONS = new java.util.HashSet<>(java.util.List.of("ascensionlib_ascension_core", "ascensionlib_ascension_core_voucher", "ascensionlib_ascension_dex", "ascensionlib_ascension_sigil", "ascensionlib_ascension_sigil_voucher", "ascensionlib_facet", "ascensionlib_facet_voucher", "ascensionlib_rank_respec", "ascensionlib_rank_respec_voucher", "ascensionlib_resonance_dust", "ascensionlib_resonance_dust_voucher", "ascensionlib_scouter", "ascensionlib_scouter_voucher", "ascensionlib_tower_mark", "ascensionlib_unique_catalyst", "ascensionlib_unique_catalyst_voucher", "ascensionlib_unique_fragment", "ascensionlib_unique_fragment_voucher", "ascensionlib_unique_respec", "ascensionlib_unique_respec_voucher", "cobbletowers_challenger_boots", "cobbletowers_challenger_chestplate", "cobbletowers_challenger_helmet", "cobbletowers_challenger_leggings", "cobbletowers_duskvale_boots", "cobbletowers_duskvale_chestplate", "cobbletowers_duskvale_helmet", "cobbletowers_duskvale_leggings", "cobbletowers_duskwanderer_boots", "cobbletowers_duskwanderer_chestplate", "cobbletowers_duskwanderer_helmet", "cobbletowers_duskwanderer_leggings", "cobbletowers_heartwood_boots", "cobbletowers_heartwood_chestplate", "cobbletowers_heartwood_helmet", "cobbletowers_heartwood_leggings", "cobbletowers_leviathan_boots", "cobbletowers_leviathan_chestplate", "cobbletowers_leviathan_helmet", "cobbletowers_leviathan_leggings", "cobbletowers_nightfall_boots", "cobbletowers_nightfall_chestplate", "cobbletowers_nightfall_helmet", "cobbletowers_nightfall_leggings", "cobbletowers_paragon_boots", "cobbletowers_paragon_chestplate", "cobbletowers_paragon_helmet", "cobbletowers_paragon_leggings", "cobbletowers_recruit_boots", "cobbletowers_recruit_chestplate", "cobbletowers_recruit_helmet", "cobbletowers_recruit_leggings", "cobbletowers_rental_key", "cobbletowers_rootvale_boots", "cobbletowers_rootvale_chestplate", "cobbletowers_rootvale_helmet", "cobbletowers_rootvale_leggings", "cobbletowers_season_trim_template", "cobbletowers_sprout_boots", "cobbletowers_sprout_chestplate", "cobbletowers_sprout_helmet", "cobbletowers_sprout_leggings", "cobbletowers_tideforge_boots", "cobbletowers_tideforge_chestplate", "cobbletowers_tideforge_helmet", "cobbletowers_tideforge_leggings", "cobbletowers_tidewalker_boots", "cobbletowers_tidewalker_chestplate", "cobbletowers_tidewalker_helmet", "cobbletowers_tidewalker_leggings", "cobbletowers_tower_key"));

    private static String timerWords(long ms) {
        if (ms <= 0) return "ending now";
        long days = ms / 86400000L, hours = ms / 3600000L;
        return days >= 2 ? days + " days" : hours >= 1 ? hours + (hours == 1 ? " hour" : " hours") : Math.max(1, ms / 60000L) + " min";
    }

    private static int readyCount(JsonObject lane) {
        int n = 0;
        if (lane != null && bool(lane, "present")) for (JsonElement e : arr(lane, "nodes")) if ((int) num(e.getAsJsonObject(), "state") == 1) n++;
        return n;
    }

    private static JsonObject prog(JsonObject st, JsonObject t, JsonObject hall) {
        JsonObject o = new JsonObject();
        JsonObject mastery = obj(t, "mastery"), season = obj(t, "season");
        boolean hasSeason = season != null && bool(season, "present");
        boolean seasonLane = hasSeason && "season".equals(str(st, "lane"));
        JsonObject u = seasonLane ? season : mastery;
        if (u == null) return o;
        String lane = seasonLane ? "season" : "mastery", sel = str(t, "selectedTower");
        int rm = readyCount(mastery), rs = readyCount(season), total = rm + rs;
        o.addProperty("lane", lane);
        o.addProperty("tower", sel);
        o.addProperty("ready", total == 0 ? "No rewards ready" : total == 1 ? "1 reward ready" : total + " rewards ready");
        o.addProperty("contBg", total > 0 ? "grad:#8a5a00,#d98a06" : "grad:#1f2c47,#2e4272");
        o.addProperty("contBd", total > 0 ? "#f6dc7a" : "#00000000");
        o.addProperty("contFg", total > 0 ? "#ffe9b0" : "#b9c6e6");
        String laneBtn = rm > 0 && !lane.equals("mastery") ? "Mastery" : rs > 0 && !lane.equals("season") ? "Season" : "";
        o.addProperty("laneBtn", laneBtn);
        o.addProperty("laneTo", laneBtn.equals("Mastery") ? "mastery" : "season");
        // left rows
        JsonObject ex = obj(hall, "extras");
        JsonArray rows = new JsonArray();
        JsonArray towers = arr(t, "towers");
        for (JsonElement e : towers) {
            JsonObject r = e.getAsJsonObject();
            String id = str(r, "id");
            boolean on = !seasonLane && id.equals(sel);
            JsonObject row = new JsonObject();
            row.addProperty("kind", "tower");
            row.addProperty("id", id);
            row.addProperty("mine", id.equals(sel));
            row.addProperty("name", towerName(str(r, "name")));
            row.addProperty("crown", false);
            row.addProperty("icon", TEX + "tw_" + bl(id) + ".png");
            String rank = "";
            JsonObject stand = null;
            if (ex != null) for (JsonElement se : arr(ex, "standings")) if (str(se.getAsJsonObject(), "tower").equals(id)) stand = se.getAsJsonObject();
            if (stand != null && num(stand, "level") > 0) rank = str(stand, "rank");
            row.addProperty("sub", stand != null && num(stand, "level") > 0 ? rank + " \u00b7 Level " + (int) num(stand, "level") : "Level " + (int) num(r, "level"));
            row.addProperty("hasRank", !rank.isEmpty());
            row.addProperty("rank", rank.isEmpty() ? TEX + "rk_bronze.png" : TEX + "rk_" + rank.toLowerCase() + ".png");
            row.addProperty("bg", on ? "gradh:#1d2a50,#16213f" : "#16213f");
            row.addProperty("bd", on ? "#ddab3c" : "#2b3b68");
            row.addProperty("hbd", on ? "#ddab3c" : "#3f5aa0");
            rows.add(row);
        }
        if (hasSeason) {
            JsonObject row = new JsonObject();
            row.addProperty("kind", "season");
            row.addProperty("id", "season");
            row.addProperty("mine", false);
            row.addProperty("name", str(season, "title"));
            row.addProperty("sub", str(season, "subtitle"));
            row.addProperty("crown", true);
            row.addProperty("icon", "");
            row.addProperty("hasRank", false);
            row.addProperty("rank", TEX + "rk_bronze.png");
            row.addProperty("bg", seasonLane ? "gradh:#1d2a50,#16213f" : "#16213f");
            row.addProperty("bd", seasonLane ? "#ddab3c" : "#2b3b68");
            row.addProperty("hbd", seasonLane ? "#ddab3c" : "#3f5aa0");
            rows.add(row);
        }
        o.add("rows", rows);
        // header
        o.addProperty("crown", seasonLane);
        o.addProperty("icon", TEX + "tw_" + bl(sel) + ".png");
        o.addProperty("title", str(u, "title"));
        String sub = str(u, "subtitle");
        if (!seasonLane && towers.size() > 1) for (JsonElement e : towers) if (str(e.getAsJsonObject(), "id").equals(sel)) sub = towerName(str(e.getAsJsonObject(), "name")) + " Tower: " + sub;
        o.addProperty("sub", sub);
        long ends = (long) num(u, "endsInMillis");
        String tl = str(u, "timerLabel");
        o.addProperty("timer", ends > 0 ? (tl.isEmpty() ? "Ends in" : Character.toUpperCase(tl.charAt(0)) + tl.substring(1).toLowerCase()) + " " + timerWords(ends) : "");
        JsonArray nodes = arr(u, "nodes");
        boolean any = false;
        for (JsonElement e : nodes) if ((int) num(e.getAsJsonObject(), "state") == 1) any = true;
        o.addProperty("claimAll", any);
        double need = num(u, "need"), into = num(u, "into");
        o.addProperty("hasProg", need > 0);
        o.addProperty("pct", need > 0 ? Math.max(0, Math.min(100, Math.round(100 * Math.min(into, need) / need))) : 0);
        o.addProperty("progText", (int) into + " / " + (int) need);
        // tiles
        int cur = Math.max(0, Math.min(nodes.size() - 1, (int) num(u, "current") - 1));
        int selI = st.has("tsel_" + lane) ? (int) num(st, "tsel_" + lane) : -1;
        if (selI >= nodes.size()) selI = -1;
        JsonArray tiles = new JsonArray();
        for (int i = 0; i < nodes.size() && i < 120; i++) {
            JsonObject n = nodes.get(i).getAsJsonObject();
            int state = (int) num(n, "state");
            boolean open = state == 1, done = state == 2, lock = state == 0;
            String reward = str(n, "reward"), note = str(n, "note"), icon = str(n, "icon");
            java.util.regex.Matcher rm2 = java.util.regex.Pattern.compile("rank:\\s*([A-Za-z]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(note);
            String rank = rm2.find() ? rm2.group(1).toLowerCase() : "";
            JsonObject t2 = new JsonObject();
            t2.addProperty("i", i);
            t2.addProperty("n", (int) num(n, "number"));
            boolean isSel = i == selI;
            t2.addProperty("bg", open ? "gradv:#433410,#31260b" : lock ? "#0f1830" : "#1d2a50");
            t2.addProperty("bd", open ? "#ddab3c" : isSel || i == cur ? "#3f6fe6" : lock ? "#1e2644" : "#2b3b68");
            t2.addProperty("hbd", open ? "#ddab3c" : "#3f6fe6");
            t2.addProperty("nbg", open ? "#ddab3c" : "#0f1830");
            t2.addProperty("nfg", open ? "#2a1a00" : "#b3c0e0");
            String lab = reward.isEmpty() ? note : reward;
            if (lab.length() > 22) lab = lab.substring(0, 21).stripTrailing() + "\u2026";
            t2.addProperty("label", lab.isEmpty() ? (rank.isEmpty() ? "No reward" : "") : lab);
            t2.addProperty("lfg", lock ? "#93a2c9" : "#b3c0e0");
            String tex = ""; boolean med = false, item = false;
            if (!rank.isEmpty() && java.util.List.of("bronze", "silver", "gold", "platinum", "diamond", "master", "champion").contains(rank)) { tex = TEX + "rk_" + rank + ".png"; med = true; }
            else if (!icon.isEmpty()) {
                String pth = path(icon);
                if (icon.endsWith("cobble_dollar")) { tex = TEX + "ico_coin.png"; item = true; }
                else if (icon.endsWith("raid_points")) { tex = TEX + "raid_points.png"; item = true; }
                else {
                    String key = icon.replace(':', '_');
                    if (ITEM_ICONS.contains(key)) { tex = TEX + "item_" + key + ".png"; item = true; }
                }
            } else if (!note.isEmpty()) { tex = TEX + (note.toLowerCase().contains("rank") ? "ico_star.png" : "ico_gear.png"); item = true; }
            t2.addProperty("icon", tex);
            t2.addProperty("med", med);
            t2.addProperty("item", item);
            t2.addProperty("gap", tex.isEmpty());
            t2.addProperty("ready", open);
            t2.addProperty("done", done);
            tiles.add(t2);
        }
        o.add("tiles", tiles);
        // detail
        String unit = seasonLane ? "Step " : "Level ";
        if (selI < 0) {
            o.addProperty("dTitle", "");
            o.addProperty("dBody", "Select a tile to see its reward. Scroll with the wheel, drag, or use the Left and Right keys.");
            o.addProperty("dNote", "");
            o.addProperty("dFg", "#93a2c9");
            o.addProperty("claimOn", false);
            o.addProperty("dNum", 0);
        } else {
            JsonObject n = nodes.get(selI).getAsJsonObject();
            int state = (int) num(n, "state");
            String stw = switch (state) { case 1 -> "ready to claim"; case 2 -> "claimed"; case 0 -> "locked"; case 4 -> "unavailable here"; default -> ""; };
            o.addProperty("dTitle", unit + (int) num(n, "number") + (stw.isEmpty() ? "" : "  -  " + stw));
            String body = str(n, "reward");
            o.addProperty("dBody", !body.isEmpty() ? body : !str(n, "note").isEmpty() ? "" : seasonLane ? "No reward on this step." : "No reward at this level. Keep unlocking achievements to climb.");
            o.addProperty("dNote", str(n, "note"));
            o.addProperty("dFg", INKC);
            o.addProperty("claimOn", state == 1);
            o.addProperty("dNum", (int) num(n, "number"));
        }
        return o;
    }

    private static final String INKC = "#e9eefc";

    private static String regionBase(String id) { String t = path(id).toLowerCase(); return t; }

    private static String[] regionColors(String id) {
        String t = regionBase(id);
        return switch (t) {
            case "tideforge" -> new String[]{"#3fc7c0", "#0f3b40"};
            case "rootvale" -> new String[]{"#6fcf6a", "#16351b"};
            case "duskvale" -> new String[]{"#b794ff", "#2e2150"};
            default -> new String[]{"#c9d3f0", "#1d2a50"};
        };
    }

    private static String regionName(JsonObject rk, String id) {
        for (JsonElement e : arr(rk, "regions")) if (str(e.getAsJsonObject(), "id").equals(id)) return str(e.getAsJsonObject(), "name");
        return id;
    }

    private static JsonArray picks(JsonObject rk, boolean left) {
        JsonArray out = new JsonArray();
        String l = str(rk, "left"), r = str(rk, "right"), mine = str(rk, "yourRegion");
        String chosen = left ? l : r, other = left ? r : l;
        for (JsonElement e : arr(rk, "regions")) {
            JsonObject g = e.getAsJsonObject();
            String id = str(g, "id");
            String[] c = regionColors(id);
            JsonObject pj = new JsonObject();
            int mem = (int) num(g, "members");
            pj.addProperty("name", str(g, "name"));
            pj.addProperty("sub", (id.equals(mine) ? "Your region \u00b7 " : "") + (mem <= 0 ? "no players yet" : mem == 1 ? "1 player" : mem + " players"));
            pj.addProperty("r", c[0]);
            pj.addProperty("rd", c[1]);
            boolean on = id.equals(chosen);
            pj.addProperty("on", on);
            pj.addProperty("op", on ? 1.0 : 0.5);
            String nl, nr;
            if (left) { nl = id; nr = id.equals(r) ? l : r; } else { nl = id.equals(l) ? r : l; nr = id; }
            pj.addProperty("nl", nl);
            pj.addProperty("nr", nr);
            out.add(pj);
        }
        return out;
    }

    private static JsonObject ranksVm(JsonObject rk) {
        JsonObject o = new JsonObject();
        String l = str(rk, "left"), r = str(rk, "right");
        String[] lc = regionColors(l), rc = regionColors(r);
        o.add("lpicks", picks(rk, true));
        o.add("rpicks", picks(rk, false));
        JsonArray rows = new JsonArray();
        int lw = 0, rw = 0;
        for (JsonElement e : arr(rk, "rows")) {
            JsonObject w = e.getAsJsonObject();
            int lead = (int) num(w, "lead");
            if (lead < 0) lw++; else if (lead > 0) rw++;
            JsonObject x = new JsonObject();
            x.addProperty("label", str(w, "label"));
            x.addProperty("lt", str(w, "leftText"));
            x.addProperty("rt", str(w, "rightText"));
            x.addProperty("lbar", 100 - Math.max(0, Math.min(100, (int) num(w, "leftBar"))));
            x.addProperty("rbar", Math.max(0, Math.min(100, (int) num(w, "rightBar"))));
            x.addProperty("lfg", lead < 0 ? "#f2c75c" : INKC);
            x.addProperty("rfg", lead > 0 ? "#f2c75c" : INKC);
            x.addProperty("lr", lc[0]);
            x.addProperty("rr", rc[0]);
            x.addProperty("bd", "#2b3b68");
            rows.add(x);
        }
        o.add("rows", rows);
        String ln = regionName(rk, l), rn = regionName(rk, r);
        o.addProperty("verdict", lw == rw ? "Level at " + lw + " each" : lw > rw ? ln + " leads " + lw + " to " + rw : rn + " leads " + rw + " to " + lw);
        o.addProperty("score", lw + " : " + rw);
        o.addProperty("lName", ln);
        o.addProperty("rName", rn);
        o.addProperty("lR", lc[0]);
        o.addProperty("rR", rc[0]);
        o.addProperty("lNone", arr(rk, "leftChampions").size() == 0);
        o.addProperty("rNone", arr(rk, "rightChampions").size() == 0);
        o.add("lChamps", arr(rk, "leftChampions"));
        o.add("rChamps", arr(rk, "rightChampions"));
        String msg = str(rk, "message");
        o.addProperty("foot", !msg.isEmpty() ? msg : str(rk, "scope") + " \u00b7 totals are judged per player. A clear in a rival region's tower is an away conquest.");
        return o;
    }

    // ------------------------------------------------------------------ lobby (PlayState)

    private static void toast(JsonObject data, String text) {
        JsonObject t = new JsonObject();
        t.addProperty("text", text);
        t.addProperty("ok", true);
        data.add("_toast", t);
    }

    private static String regionKey(String id) {
        return id.contains("tideforge") ? "tideforge" : id.contains("rootvale") ? "rootvale" : id.contains("duskvale") ? "duskvale" : id.contains("halloween") ? "halloween" : "neutral";
    }

    private static String towerName(String n) { return n.endsWith(" Tower") ? n.substring(0, n.length() - 6) : n; }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static JsonObject lobby(ServerPlayer p, JsonObject play) {
        JsonObject lb = obj(play, "lobby");
        JsonObject o = new JsonObject();
        if (lb == null) return o;
        JsonObject opt = obj(lb, "options"), depth = obj(opt, "depth"), modes = obj(opt, "modes"), ready = obj(modes, "readiness");
        int role = (int) num(lb, "role");
        boolean idle = num(lb, "countdown") < 0, host = role == 1;
        boolean confirmed = bool(ready, "confirmed"), rental = bool(modes, "rental");
        boolean canChoose = (role == 0 || host) && idle;
        String sel = str(lb, "selected"), hostName = str(lb, "hostName");
        o.addProperty("canChoose", canChoose);
        o.addProperty("chooseLabel", canChoose ? "Choose one" : "Set by the host");
        o.addProperty("host", hostName.isEmpty() ? "" : " \u00b7 Host " + hostName);
        o.addProperty("host0", hostName);
        // towers
        JsonArray towers = new JsonArray();
        for (JsonElement e : arr(play, "towers")) {
            JsonObject t = e.getAsJsonObject();
            String id = str(t, "id");
            boolean on = id.equals(sel);
            JsonObject r = new JsonObject();
            r.addProperty("id", id);
            r.addProperty("name", towerName(str(t, "displayName")));
            r.addProperty("sub", on ? "Selected" : "Open expedition");
            r.addProperty("pano", TEX + "pano_" + regionKey(id) + ".png");
            r.addProperty("dim", on ? "#00000000" : "#080c1c66");
            r.addProperty("bg", on ? "grad:#1d2a50,#16213f" : "#16213f");
            r.addProperty("bd", on ? "#ddab3c" : "#2b3b68");
            r.addProperty("hbd", on ? "#ddab3c" : "#2b3b68");
            r.addProperty("op", canChoose ? 1.0 : 0.75);
            towers.add(r);
        }
        o.add("towers", towers);
        // team / seats (wo().team + gi())
        record Seat(String name, boolean host, boolean good, String status) {}
        java.util.List<Seat> team = new java.util.ArrayList<>();
        if (role != 0) {
            boolean hostReady = rental && bool(ready, "host");
            team.add(new Seat(hostName, true, hostReady, hostReady ? "ready" : "host"));
            for (JsonElement e : arr(lb, "members")) {
                JsonObject m = e.getAsJsonObject();
                boolean flag = rental ? bool(m, "ready") : bool(m, "accepted");
                String status = rental ? (bool(m, "accepted") ? (bool(m, "ready") ? "ready" : "drafting") : "invited") : (bool(m, "accepted") ? "ready" : "invited");
                team.add(new Seat(str(m, "name"), false, flag, status));
            }
        }
        String me = p.getGameProfile().getName();
        JsonArray seats = new JsonArray();
        boolean inviteGiven = false;
        JsonArray rosters = arr(opt, "rosters");
        for (int i = 0; i < 4; i++) {
            JsonObject sj = new JsonObject();
            if (i < team.size()) {
                Seat st = team.get(i);
                boolean mine = st.name().equals(me);
                String shown = st.status().equals("host") ? (st.host() ? "host" : "") : st.status();
                sj.addProperty("kind", 1);
                sj.addProperty("name", st.name() + (mine ? " (you)" : ""));
                sj.addProperty("av", st.name().isEmpty() ? "?" : st.name().substring(0, 1).toUpperCase());
                sj.addProperty("sub", (st.host() ? "Host" : "Guest") + (shown.isEmpty() || shown.equals("host") ? "" : " \u00b7 " + cap(shown)));
                String pill = shown.isEmpty() ? "" : cap(shown);
                sj.addProperty("pill", pill);
                String pc = switch (st.status()) { case "ready", "accepted" -> "g"; case "drafting" -> "y"; case "host" -> "b"; default -> "n"; };
                sj.addProperty("pillBg", switch (pc) { case "g" -> "#12301f"; case "y" -> "#3a2d0f"; case "b" -> "#1c2d57"; default -> "#232f52"; });
                sj.addProperty("pillFg", switch (pc) { case "g" -> "#5fd68e"; case "y" -> "#f2c75c"; case "b" -> "#8fb2ff"; default -> "#9aa8cf"; });
                sj.addProperty("bg", "gradv:#1d2a50,#16213f");
                sj.addProperty("bd", mine ? "#355190" : "#2b3b68");
                JsonArray party = new JsonArray();
                for (JsonElement re : rosters) {
                    JsonObject ro = re.getAsJsonObject();
                    if (!str(ro, "name").equals(st.name())) continue;
                    for (JsonElement me2 : arr(ro, "team")) {
                        JsonObject mon = me2.getAsJsonObject();
                        JsonObject pj = new JsonObject();
                        pj.addProperty("icon", teamSprite(str(mon, "species")));
                        pj.addProperty("op", bool(mon, "fainted") ? 0.4 : 1.0);
                        party.add(pj);
                    }
                }
                if (party.size() > 0) sj.add("party", party);
                if (mine && party.size() == 0) {
                    StringBuilder sb = new StringBuilder();
                    JsonArray lv = arr(play, "partyLevels");
                    for (JsonElement l : lv) sb.append(sb.length() > 0 ? ", " : "").append("Lv ").append(l.getAsInt());
                    sj.addProperty("note", "Your party: " + (lv.size() == 0 ? "no Pokemon" : sb));
                }
            } else {
                sj.addProperty("kind", 0);
                sj.addProperty("bg", "#00000000");
                sj.addProperty("bd", "#2b3b68");
                boolean inv = host && idle && !inviteGiven;
                if (inv) inviteGiven = true;
                sj.addProperty("invite", inv);
            }
            seats.add(sj);
        }
        o.add("seats", seats);
        o.addProperty("partyLabel", team.size() + " of 4");
        o.addProperty("wait", role == 3 && !confirmed ? "Waiting for the host to confirm the mode." : "");
        // settings
        String modeName = "Standard";
        if (modes != null) {
            String chosen = str(modes, "chosen");
            JsonArray ids = arr(modes, "ids"), names = arr(modes, "names");
            for (int i = 0; i < ids.size(); i++) if (ids.get(i).getAsString().equals(chosen)) modeName = names.get(i).getAsString();
            if (host && ids.size() > 0) {
                JsonArray mb = new JsonArray();
                String cur = chosen.isEmpty() ? "standard" : chosen;
                for (int i = -1; i < ids.size(); i++) {
                    String id = i < 0 ? "standard" : ids.get(i).getAsString();
                    boolean on = id.equals(cur);
                    JsonObject b = new JsonObject();
                    b.addProperty("id", id);
                    b.addProperty("name", i < 0 ? "Standard" : names.get(i).getAsString());
                    b.addProperty("bg", on ? "#3f6fe6" : "#1d2a50");
                    b.addProperty("fg", on ? "#ffffff" : "#93a2c9");
                    mb.add(b);
                }
                o.add("modes", mb);
                o.addProperty("modeOn", idle);
            }
        }
        o.addProperty("modeName", modeName);
        if (host && depth != null && bool(depth, "offered")) {
            JsonObject d = new JsonObject();
            int chosen = (int) num(depth, "chosen"), max = (int) num(depth, "max");
            d.addProperty("label", chosen == 0 ? "Floor 1" : "Ascension " + chosen);
            d.addProperty("next", chosen >= max ? 0 : chosen + 1);
            d.addProperty("on", max > 0 && idle);
            o.add("depth", d);
        }
        o.addProperty("confirm", host && !sel.isEmpty() && !confirmed && idle);
        boolean draft = rental && (host || role == 3) && confirmed && idle;
        o.addProperty("draft", draft);
        if (draft) {
            boolean mine = bool(ready, "mine");
            o.addProperty("readyText", mine ? "Ready (click to undo)" : "Ready up");
            o.addProperty("readyAct", mine ? "UNREADY" : "READY");
            o.addProperty("readyOn", bool(ready, "drafted"));
            o.addProperty("readyBg", mine ? "#1d2a50" : "#15803d");
            o.addProperty("readyFg", mine ? "#e9eefc" : "#ffffff");
        }
        o.addProperty("answer", role == 2);
        o.addProperty("chooser", host || role == 3);
        if (host) {
            JsonObject sv = new JsonObject();
            sv.addProperty("label", idle ? "Start" : "Starting in " + (int) num(lb, "countdown") + "...");
            boolean ok = !sel.isEmpty() && idle && confirmed;
            sv.addProperty("on", ok);
            sv.addProperty("text", ok ? "Everything is ready." : !idle ? "The countdown is running." : sel.isEmpty() ? "Choose a tower first." : confirmed ? "Waiting." : "Confirm the mode to continue.");
            o.add("start", sv);
        }
        if (role != 0) o.addProperty("leave", host ? "End team" : "Leave");
        return o;
    }

    // ------------------------------------------------------------------ the layout's data

    private static JsonObject build(ServerPlayer p, JsonObject st) {
        LEGACY_TIP.set(GuiService.legacyTips(p));
        JsonObject vm = new JsonObject();
        vm.addProperty("view", st.has("view") ? str(st, "view") : "");
        JsonObject inter = obj(st, "inter");
        if (inter != null) intermission(p, st, inter, vm);
        JsonObject play = obj(st, "play");
        if (play != null) vm.add("lob", lobby(p, play));
        String tab = st.has("tab") ? str(st, "tab") : "towers";
        vm.addProperty("tab", tab);
        vm.addProperty("sub", st.has("sub") ? str(st, "sub") : "");
        JsonObject setv = new JsonObject();
        setv.addProperty("anim", bool(st, "noAnim") ? "Off" : "On");
        setv.addProperty("animBg", bool(st, "noAnim") ? "#1d2a50" : "#15803d");
        vm.add("set", setv);
        vm.addProperty("anim", !bool(st, "noAnim"));
        JsonArray tabs = new JsonArray();
        String[][] names = {{"towers", "Play"}, {"trials", "Trials"}, {"progress", "Progress"}, {"social", "Social"}, {"ranks", "Ranks"}, {"codex", "Codex"}};
        for (String[] n : names) {
            JsonObject k = new JsonObject();
            boolean on = n[0].equals(tab);
            k.addProperty("id", n[0]);
            k.addProperty("name", n[1]);
            k.addProperty("bg", on ? "#2a3c70" : "#00000000");
            k.addProperty("hov", on ? "#2a3c70" : "#16213f");
            k.addProperty("fg", on ? "#e9eefc" : "#93a2c9");
            tabs.add(k);
        }
        vm.add("tabs", tabs);
        JsonObject hallSt = obj(st, "hall");
        if (hallSt != null) {
            JsonObject hv = hall(p, st, hallSt);
            vm.add("hall", hv);
            if ("tower".equals(str(st, "sub"))) vm.add("td", towerDetail(hallSt, hv, str(st, "subId")));
            if ("trial".equals(str(st, "sub"))) vm.add("tb", trialBrief(hallSt, hv, str(st, "subId")));
        }
        if (obj(st, "trk") != null) vm.add("prog", prog(st, obj(st, "trk"), hallSt));
        if (obj(st, "rd") != null) vm.add("rd", rental(st, obj(st, "rd")));
        if ("feature".equals(str(st, "sub"))) vm.add("fe", featureVm(st));
        if (obj(st, "reg") != null && "party".equals(str(st, "view"))) vm.add("pt", partyVm(obj(st, "reg")));
        if (obj(st, "scout") != null && "scout".equals(str(st, "view"))) vm.add("sc", scoutVm(st, obj(st, "scout")));
        JsonObject spec = obj(st, "spec");
        if (spec != null && System.currentTimeMillis() - (long) num(st, "specAt") < 120_000L) vm.add("spec", specVm(spec));
        if (obj(st, "rk") != null) vm.add("rk", ranksVm(obj(st, "rk")));
        JsonObject rw = obj(st, "rw");
        if (rw != null) vm.add("rw", reward(st, rw));
        JsonObject ven = obj(st, "ven");
        if (ven != null) vm.add("ven", vendor(st, ven));
        return vm;
    }

    private static String clockWords(JsonObject inter) { return ""; }

    private static void intermission(ServerPlayer p, JsonObject st, JsonObject inter, JsonObject vm) {
        JsonObject draft = obj(inter, "draft");
        if (draft == null) { draft = new JsonObject(); draft.addProperty("state", 0); draft.addProperty("chosen", -1); draft.addProperty("myVote", -1); draft.add("cards", new JsonArray()); }
        JsonObject ex = obj(inter, "extras") != null ? obj(inter, "extras") : new JsonObject();
        int state = (int) num(draft, "state"), chosen = (int) num(draft, "chosen"), myVote = (int) num(draft, "myVote");
        double countdown = inter.has("countdown") ? num(inter, "countdown") : -1;
        JsonArray cards = arr(draft, "cards");
        int sel = (int) num(st, "sel");
        if (sel >= cards.size()) sel = -1;
        JsonArray outCards = new JsonArray();
        boolean allEvent = true, allRelic = true;
        for (int i = 0; i < cards.size(); i++) {
            JsonObject c = cards.get(i).getAsJsonObject();
            JsonObject o = card(c, i, state == 1 ? sel : -1, state == 2 && i == chosen, (int) num(c, "votes"));
            outCards.add(o);
            String pill = str(o, "pill");
            boolean relic = pill.startsWith("Relic"), event = num(c, "risk") < 0;
            allEvent &= event;
            allRelic &= relic;
        }
        if (outCards.size() > 0) vm.add("cards", outCards);
        JsonObject routeSt = obj(ex, "route");
        if (routeSt != null && obj(routeSt, "graph") != null && (int) num(obj(routeSt, "graph"), "rows") > 0) vm.add("atlas", atlasVm(routeSt, outCards, sel, state == 1 && countdown < 0 && !(st.has("votePendAt") && System.currentTimeMillis() - (long) num(st, "votePendAt") < 8000), state == 2 ? chosen : -1));
        else if (routeSt != null && arr(routeSt, "now").size() > 0 && obj(routeSt, "graph") == null) vm.add("route", routeVm(routeSt, outCards, sel, state == 1 && countdown < 0 && !(st.has("votePendAt") && System.currentTimeMillis() - (long) num(st, "votePendAt") < 8000), state == 2 ? chosen : -1));
        vm.addProperty("cur", sel >= 0 ? sel : 0);
        boolean votePend = st.has("votePendAt") && System.currentTimeMillis() - (long) num(st, "votePendAt") < 8000;
        boolean canPick = state == 1 && countdown < 0 && !votePend;
        vm.addProperty("canPick", canPick);
        vm.addProperty("pickText", state == 2 ? "Settled" : state != 1 ? "Confirm" : sel < 0 ? "Choose a card" : sel == myVote ? "Voted" : "Confirm");
        vm.addProperty("canConfirm", canPick && sel >= 0 && sel != myVote);
        String me = p.getGameProfile().getName();
        boolean ready = false, cash = false;
        JsonArray members = new JsonArray();
        for (JsonElement e : arr(inter, "members")) {
            JsonObject mb = e.getAsJsonObject();
            boolean r = bool(mb, "ready"), c = bool(mb, "cashOut");
            if (str(mb, "name").equals(me)) { ready = r; cash = c; }
            JsonObject o = new JsonObject();
            o.addProperty("name", str(mb, "name"));
            o.addProperty("dot", r ? "#16a34a" : "#4d5e90");
            o.addProperty("state", c ? "cashing out" : r ? "ready" : "waiting");
            members.add(o);
        }
        vm.add("members", members);
        vm.addProperty("readyText", ready ? "Not ready" : "Ready");
        vm.addProperty("readyAct", ready ? "UNREADY" : "READY");
        vm.addProperty("canReady", ready || state != 1);
        vm.addProperty("cashText", cash ? "Keep going" : "Cash out");
        vm.addProperty("cashAct", cash ? "STAY" : "CASH_OUT");
        String hint;
        if (countdown >= 0) hint = "Next floor in ";
        else if (!str(inter, "message").isEmpty()) hint = str(inter, "message");
        else if (state == 1 && votePend) hint = "Sending your vote...";
        else if (state == 1) hint = outCards.size() == 0 ? "No modifier this floor" : allRelic ? "Choose a relic to carry" : allEvent ? "A room on the way: choose" : "Choose a modifier for the next floor";
        else hint = state == 2 ? "Modifier chosen" : "No modifier this floor";
        vm.addProperty("hint", hint);
        if (countdown >= 0) vm.addProperty("until", System.currentTimeMillis() + (long) (countdown * 1000));
        int cyc = (int) num(ex, "cycleLength"), cf = (int) num(ex, "cycleFloor"), floor = (int) num(inter, "floor");
        vm.addProperty("crumbB", cyc > 0 && cf > 0 ? "Floor " + cf + " of " + cyc + " cleared" : "Floor " + floor + " cleared");
        vm.addProperty("crumbT", str(ex, "tower").isEmpty() ? "" : " · " + str(ex, "tower"));
        JsonArray party = new JsonArray();
        for (JsonElement e : arr(ex, "party")) {
            JsonObject m = e.getAsJsonObject();
            boolean out = bool(m, "fainted");
            double hp = num(m, "hp");
            JsonObject o = new JsonObject();
            o.addProperty("icon", teamSprite(str(m, "species")));
            o.addProperty("name", str(m, "name"));
            o.addProperty("level", (int) num(m, "level"));
            o.addProperty("hp", out ? 0 : hp);
            o.addProperty("dl", party.size() * 90);
            o.addProperty("hpc", hp <= 0 ? "#00000000" : hp < 35 ? "#d6264e" : hp < 70 ? "#d9a406" : "#16a34a");
            o.addProperty("op", out ? 0.55 : 1.0);
            party.add(o);
        }
        if (party.size() > 0) vm.add("party", party);
    }

    // ------------------------------------------------------------------ reward reveal

    private static String tier(JsonObject g) {
        if (!grantKind(g).equals("item")) return num(g, "amount") >= 1000 ? "fine" : "common";
        String t = str(g, "item"), o = path(t);
        if (t.startsWith("cobbletowers:")) {
            if (o.matches(".*(tower_key|rental_key|season_trim_template).*")) return "rare";
            String s = o.replaceAll("_(helmet|chestplate|leggings|boots)$", "");
            return switch (s) { case "recruit", "sprout", "tidewalker", "duskwanderer" -> "fine"; case "challenger", "rootvale", "tideforge", "duskvale" -> "rare";
                case "paragon", "heartwood", "leviathan", "nightfall" -> "epic"; default -> "fine"; };
        }
        if (t.startsWith("ascensionlib:")) return o.equals("ascension_core") ? "epic" : o.equals("tower_mark") ? "rare" : o.equals("resonance_dust") ? "common" : "fine";
        String n = category(t);
        if ("ball_master".equals(n)) return "epic";
        if ("candy_rare".equals(n) || "candy_xl".equals(n) || "pack".equals(n) || "ticket".equals(n) || "scroll".equals(n)) return "rare";
        if ("ball_ultra".equals(n) || "candy_l".equals(n) || (n != null && n.startsWith("gem_")) || "apple_gold".equals(n) || "bottle_gold".equals(n)) return "fine";
        return "common";
    }

    private static int rank(String t) { return switch (t) { case "common" -> 0; case "fine" -> 1; case "rare" -> 2; default -> 3; }; }

    private static String titleCase(String snake) {
        StringBuilder b = new StringBuilder();
        for (String w : snake.split("[_\\s]+")) if (!w.isEmpty()) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    private static JsonObject reward(JsonObject st, JsonObject rw) {
        JsonArray grants = arr(rw, "grants");
        String best = "common";
        for (JsonElement e : grants) { String t = tier(e.getAsJsonObject()); if (rank(t) > rank(best)) best = t; }
        JsonObject o = new JsonObject();
        o.addProperty("floor", (int) num(rw, "floorIndex"));
        o.addProperty("edge", switch (best) { case "rare" -> "#b78cff"; case "epic" -> "#f6dc7a"; default -> "#ddab3c"; });
        int idx = Math.min((int) num(st, "rwIdx"), Math.max(0, grants.size() - 1));
        JsonArray chips = new JsonArray();
        for (int i = 0; i < grants.size(); i++) {
            JsonObject g = grants.get(i).getAsJsonObject();
            JsonObject c = new JsonObject();
            c.addProperty("i", i);
            c.addProperty("icon", grantIcon(g));
            String t = tier(g);
            c.addProperty("edge", i == idx ? "#ddab3c" : t.equals("rare") ? "#7f5fc0" : t.equals("epic") ? "#a8741e" : "#2b3b68");
            chips.add(c);
        }
        o.add("grants", chips);
        o.addProperty("many", grants.size() > 1);
        o.addProperty("pos", (idx + 1) + " / " + grants.size());
        if (grants.size() > 0) {
            JsonObject g = grants.get(idx).getAsJsonObject();
            String t = tier(g);
            o.addProperty("name", str(g, "label").isEmpty() ? titleCase(path(str(g, "item"))) : str(g, "label"));
            o.addProperty("amount", (int) num(g, "amount"));
            o.addProperty("icon", grantIcon(g));
            o.addProperty("plinth", TEX + "plinth_" + t + ".png");
            String label = switch (t) { case "fine" -> "Fine find"; case "rare" -> "Rare find"; case "epic" -> "Treasure"; default -> ""; };
            if (!label.isEmpty()) o.addProperty("tier", label);
            o.addProperty("tierBg", switch (t) { case "fine" -> "#12301f"; case "rare" -> "#2b2147"; default -> "#3a2d0f"; });
            o.addProperty("tierFg", switch (t) { case "fine" -> "#5fd68e"; case "rare" -> "#c3a6ff"; default -> "#f2c75c"; });
        }
        return o;
    }

    // ------------------------------------------------------------------ vendor

    private static String serviceGlyph(String id) {
        String t = path(id);
        if (t.matches(".*(revive).*") || t.matches(".*(heal|potion).*")) return "heart";
        if (t.matches(".*(cure|status|antidote).*")) return "flask";
        if (t.matches(".*(key|ticket|pass).*")) return "scroll";
        return "bag";
    }

    private static JsonObject vendor(JsonObject st, JsonObject ven) {
        JsonArray services = arr(ven, "services"), team = arr(ven, "team");
        int idx = Math.min((int) num(st, "venIdx"), Math.max(0, services.size() - 1));
        int ti = Math.min((int) num(st, "venTarget"), Math.max(0, team.size() - 1));
        JsonObject target = team.size() > 0 ? team.get(ti).getAsJsonObject() : null;
        JsonObject o = new JsonObject();
        int balance = (int) num(ven, "cobbleDollars");
        o.addProperty("balance", balance);
        if (target != null) o.addProperty("target", str(target, "name") + (bool(target, "online") ? "" : " (offline)"));
        JsonArray svc = new JsonArray();
        for (int i = 0; i < services.size(); i++) {
            JsonObject s = services.get(i).getAsJsonObject();
            JsonObject c = new JsonObject();
            c.addProperty("i", i);
            c.addProperty("name", str(s, "displayName"));
            c.addProperty("price", (int) num(s, "priceCobbleDollars"));
            int left = (int) num(s, "remainingPurchases");
            c.addProperty("left", left < 0 ? "Unlimited" : left + " left");
            c.addProperty("glyph", TEX + "glyph_" + serviceGlyph(str(s, "id")) + ".png");
            c.addProperty("edge", i == idx ? "#3f6fe6" : "#2b3b68");
            svc.add(c);
        }
        if (svc.size() > 0) o.add("services", svc);
        if (services.size() > 0) {
            JsonObject s = services.get(idx).getAsJsonObject();
            int price = (int) num(s, "priceCobbleDollars"), left = (int) num(s, "remainingPurchases");
            o.addProperty("pickName", str(s, "displayName"));
            o.addProperty("pickSub", price + " CobbleDollars · " + (left < 0 ? "Stock: unlimited" : "Stock: " + left));
            o.addProperty("pickId", str(s, "id"));
            o.addProperty("canBuy", target != null && bool(target, "online") && balance >= price && left != 0);
        }
        o.addProperty("targetId", target == null ? "" : str(target, "id"));
        o.addProperty("message", str(ven, "message"));
        o.addProperty("burstKey", st.has("venBurstAt") && System.currentTimeMillis() - (long) num(st, "venBurstAt") < 3200 ? "v" + (long) num(st, "venBurstAt") : "");
        return o;
    }
}
