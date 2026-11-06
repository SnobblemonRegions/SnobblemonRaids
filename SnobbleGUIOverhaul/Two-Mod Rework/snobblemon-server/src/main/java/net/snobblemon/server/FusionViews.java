package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The AscensionLib Fusion page. This view holds the chosen host and donor, the picker and the review step, and forwards the preview, pick list
 * and the confirmed fusion to AscensionLib, which still checks the pair, the price and the revisions. The client only says what was clicked.
 */
final class FusionViews {
    private FusionViews() {}

    static final String PAGE = "ascensionlib_fusion";
    private static final int TILES = 24;

    static void register() {
        SnobbleUi.registerView(PAGE, FusionViews::apply);
    }

    private static JsonObject obj(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null; }

    private static JsonArray arr(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    private static JsonObject fresh() {
        JsonObject st = new JsonObject();
        st.addProperty("phase", "browse");
        st.addProperty("banner", "");
        st.addProperty("bannerOk", false);
        st.addProperty("hostBlock", "");
        st.addProperty("opId", UUID.randomUUID().toString());
        JsonObject pick = new JsonObject();
        pick.addProperty("open", false);
        pick.addProperty("role", "donor");
        pick.addProperty("pc", false);
        pick.addProperty("sort", "rarity");
        pick.addProperty("search", "");
        st.add("pick", pick);
        return st;
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : fresh();
        JsonObject pick = st.getAsJsonObject("pick");
        switch (event) {
            case "fusionCandidates" -> {
                JsonObject host = obj(s, "host");
                if (host == null) return;
                st.add("host", host);
                st.addProperty("hostBlock", str(s, "hostBlock"));
                if (str(st, "phase").equals("browse")) {
                    JsonObject d = obj(st, "donor");
                    if (d != null && str(d, "id").equals(str(host, "id"))) st.remove("donor");
                    preview(p, st);
                }
            }
            case "fusionPickList" -> {
                if (!(bool(s, "hostRole") ? "host" : "donor").equals(str(pick, "role"))) return;
                st.add("entries", s.has("entries") && s.get("entries").isJsonArray() ? s.getAsJsonArray("entries") : new JsonArray());
            }
            case "fusionDetail" -> {
                JsonObject host = obj(st, "host"), donor = obj(st, "donor");
                if (host == null || donor == null || !str(s, "hostId").equals(str(host, "id")) || !str(s, "donorId").equals(str(donor, "id"))) return;
                st.add("detail", s);
            }
            case "fusionResult" -> {
                if (!str(s, "operationId").equals(str(st, "opId"))) return;
                st.addProperty("phase", "browse");
                boolean ok = bool(s, "ok");
                st.addProperty("bannerOk", ok);
                JsonObject host = obj(st, "host");
                if (ok && host != null) {
                    st.addProperty("banner", str(host, "name") + " became " + str(s, "name") + ".");
                    st.remove("donor");
                    st.remove("detail");
                    JsonObject r = new JsonObject();
                    r.addProperty("hostId", str(host, "id"));
                    SnobbleUi.forward(p, "ascension_fusion_refresh", r);
                } else st.addProperty("banner", str(s, "message").isEmpty() ? "Not allowed." : str(s, "message"));
                st.addProperty("opId", UUID.randomUUID().toString());
            }
            case "msg" -> message(p, st, pick, s);
            default -> { return; }
        }
        data.add("st", st);
        data.add("vm", build(st, pick));
    }

    private static void preview(ServerPlayer p, JsonObject st) {
        st.remove("detail");
        st.addProperty("phase", "browse");
        JsonObject host = obj(st, "host"), donor = obj(st, "donor");
        if (donor != null && host != null && str(st, "hostBlock").isEmpty()) {
            JsonObject m = new JsonObject();
            m.addProperty("hostId", str(host, "id"));
            m.addProperty("donorId", str(donor, "id"));
            SnobbleUi.forward(p, "ascension_fusion_preview", m);
        }
    }

    private static void openPicker(ServerPlayer p, JsonObject st, JsonObject pick, String role) {
        JsonObject host = obj(st, "host"), donor = obj(st, "donor");
        if (!str(st, "phase").equals("browse") || host == null) return;
        pick.addProperty("open", true);
        pick.addProperty("role", role);
        pick.addProperty("pc", false);
        pick.addProperty("search", "");
        st.remove("entries");
        JsonObject m = new JsonObject();
        m.addProperty("hostRole", role.equals("host"));
        m.addProperty("hostId", str(host, "id"));
        m.addProperty("donorId", donor != null ? str(donor, "id") : "");
        SnobbleUi.forward(p, "ascension_fusion_pick", m);
    }

    private static void message(ServerPlayer p, JsonObject st, JsonObject pick, JsonObject m) {
        String action = str(m, "action"), k = str(m, "k");
        boolean browse = str(st, "phase").equals("browse");
        JsonObject host = obj(st, "host"), donor = obj(st, "donor"), detail = obj(st, "detail");
        switch (action) {
            case "pickHost" -> openPicker(p, st, pick, "host");
            case "pickDonor" -> openPicker(p, st, pick, "donor");
            case "pickClose" -> pick.addProperty("open", false);
            case "tab" -> pick.addProperty("pc", k.equals("pc"));
            case "sort" -> pick.addProperty("sort", str(pick, "sort").equals("rarity") ? "name" : "rarity");
            case "search" -> pick.addProperty("search", str(m, "q").length() > 40 ? str(m, "q").substring(0, 40) : str(m, "q"));
            case "choose" -> {
                if (!bool(pick, "open")) return;
                for (JsonElement e : arr(st, "entries")) {
                    JsonObject c = e.getAsJsonObject();
                    if (!str(c, "id").equals(k) || !str(c, "block").isEmpty()) continue;
                    if (str(pick, "role").equals("host")) { st.add("host", c); st.addProperty("hostBlock", ""); }
                    else st.add("donor", c);
                    st.addProperty("banner", "");
                    pick.addProperty("open", false);
                    preview(p, st);
                    break;
                }
            }
            case "swap" -> {
                if (browse && host != null && donor != null && bool(donor, "canHost") && str(st, "hostBlock").isEmpty()) {
                    st.add("host", donor);
                    st.add("donor", host);
                    st.addProperty("hostBlock", "");
                    st.addProperty("banner", "");
                    preview(p, st);
                }
            }
            case "fuse" -> {
                if (browse) { if (detail != null && str(detail, "block").isEmpty() && !str(detail, "name").isEmpty()) st.addProperty("phase", "review"); return; }
                if (!str(st, "phase").equals("review") || detail == null || !str(detail, "block").isEmpty() || donor == null || host == null) return;
                JsonObject c = new JsonObject();
                c.addProperty("operationId", str(st, "opId"));
                c.addProperty("hostId", str(host, "id"));
                c.addProperty("donorId", str(donor, "id"));
                c.add("hostRevision", detail.get("hostRevision"));
                c.add("donorRevision", detail.get("donorRevision"));
                c.add("walletRevision", detail.get("walletRevision"));
                st.addProperty("phase", "browse");      // the answer shows in the banner; the same operation id keeps a repeated click from being charged twice
                SnobbleUi.forward(p, "ascension_fusion_confirm", c);
            }
            case "cancel" -> st.addProperty("phase", "browse");
            default -> { }
        }
    }

    // ------------------------------------------------------------------ view model

    private static String rarityColor(String id) {
        String[] r = AscViews.RARITY.get(id);
        return r != null ? r[1] : "#5DA6BD";
    }

    private static JsonObject line(String t, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        o.addProperty("text", text);
        return o;
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static JsonObject chamber(JsonObject e) {
        JsonObject o = new JsonObject();
        o.addProperty("has", e != null);
        String sid = e == null ? "" : str(e, "speciesId").replaceAll("^.*:", "");
        o.addProperty("sprite", sid.isEmpty() ? "" : RaidViews.spriteKey(sid, false));
        o.addProperty("name", e == null ? "empty" : str(e, "name"));
        o.addProperty("rarity", e == null ? "" : str(e, "rarityId"));
        o.addProperty("color", e == null ? "#5DA6BD" : rarityColor(str(e, "rarityId")));
        o.addProperty("unique", e == null ? "" : str(e, "uniqueName"));
        return o;
    }

    private static JsonObject build(JsonObject st, JsonObject pick) {
        JsonObject v = new JsonObject();
        JsonObject host = obj(st, "host"), donor = obj(st, "donor"), detail = obj(st, "detail");
        String phase = str(st, "phase"), hostBlock = str(st, "hostBlock");
        boolean idle = phase.equals("browse");
        v.add("host", chamber(host));
        v.add("donor", chamber(donor));
        v.addProperty("ready", host != null);
        v.addProperty("canChangeHost", idle && host != null);
        v.addProperty("canChangeDonor", idle && host != null);
        v.addProperty("donorText", donor != null ? "Change donor" : "Choose donor");
        v.addProperty("canSwap", idle && donor != null && bool(donor, "canHost") && hostBlock.isEmpty());
        v.addProperty("fuseText", phase.equals("review") ? "Fuse for good" : "Review fusion");
        v.addProperty("canFuse", phase.equals("review") || (idle && detail != null && str(detail, "block").isEmpty() && !str(detail, "name").isEmpty()));
        v.addProperty("cancelText", idle ? "Close" : "Cancel");
        v.addProperty("cancelCloses", idle);
        String banner = str(st, "banner");
        v.addProperty("msg", !banner.isEmpty() ? banner : donor != null ? "The donor is lost for good." : "");
        v.addProperty("msgColor", !banner.isEmpty() ? (bool(st, "bannerOk") ? "#62D06A" : "#FF7A6B") : "#5DA6BD");
        // result text
        JsonArray dl = new JsonArray();
        if (host == null) dl.add(line("muted", "Reading your Pokemon..."));
        else if (!hostBlock.isEmpty()) dl.add(line("bad", hostBlock + " Choose a different host."));
        else if (donor == null) dl.add(line("muted", "Choose a donor: Epic or better, with a Unique, and not a Transcendent."));
        else if (!idle) {
            dl.add(line("text", "Fuse " + str(donor, "name") + " into " + str(host, "name") + "? " + str(donor, "name") + " is consumed and cannot be recovered. " + str(host, "name")
                    + " keeps its own modifiers and becomes " + (detail != null && !str(detail, "name").isEmpty() ? str(detail, "name") : "a Transcendent") + ", once, for good."));
        } else if (detail == null) dl.add(line("muted", "Reading the pairing..."));
        else if (str(detail, "name").isEmpty()) dl.add(line("bad", str(detail, "block")));
        else {
            dl.add(line("big", str(detail, "name")));
            dl.add(line("text", "Fused from: " + str(detail, "fromUniques")));
            dl.add(line("muted", cap(str(detail, "harmony")) + " harmony: " + num(detail, "benefit") + "% of the benefits, " + num(detail, "drawback") + "% of the drawbacks"));
            if (!str(detail, "blurb").isEmpty()) dl.add(line("muted", str(detail, "blurb")));
            dl.add(line("text", "Core: " + str(detail, "core")));
            dl.add(line("text", "Clutch: " + str(detail, "clutch")));
            dl.add(line("bad", "Drawback: " + str(detail, "drawbackText")));
            dl.add(line("text", "Twist, " + str(detail, "twistName") + ": " + str(detail, "twistText")));
            dl.add(line("text", "Rider: " + str(detail, "riderText")));
            List<String> types = new ArrayList<>();
            if (!str(detail, "hostType").isEmpty()) types.add("+10% " + cap(str(detail, "hostType")) + " moves");
            if (!str(detail, "donorType").isEmpty()) types.add("-10% damage from " + cap(str(detail, "donorType")) + " moves");
            if (!types.isEmpty()) dl.add(line("text", "Types: " + String.join("; ", types)));
            JsonArray pr = arr(detail, "price"), bal = arr(detail, "balances");
            if (pr.size() >= 5) dl.add(line("gold", "Price: " + pr.get(0).getAsLong() + " Resonance Dust, " + pr.get(1).getAsLong() + " Facets, " + pr.get(2).getAsLong() + " Cores, " + pr.get(3).getAsLong() + " Catalyst, " + pr.get(4).getAsLong() + " Tower Marks"));
            List<String> b = new ArrayList<>();
            for (JsonElement e : bal) b.add(e.getAsString());
            dl.add(line("muted", "You have: " + String.join(" / ", b)));
            if (!str(detail, "block").isEmpty()) dl.add(line("bad", str(detail, "block")));
        }
        v.add("dl", dl);
        // picker
        boolean open = bool(pick, "open");
        v.addProperty("pickOpen", open);
        v.addProperty("pickTitle", str(pick, "role").equals("host") ? "Choose the host (kept): Mythic with a Unique" : "Choose the donor (consumed)");
        v.addProperty("tabParty", !bool(pick, "pc") ? "#4AD8F6" : "#1F6B86");
        v.addProperty("tabPc", bool(pick, "pc") ? "#4AD8F6" : "#1F6B86");
        v.addProperty("sortText", "Sort: " + (str(pick, "sort").equals("rarity") ? "Rarity" : "Name"));
        JsonArray rows = new JsonArray();
        String pickNote = "";
        if (open) {
            if (!st.has("entries")) pickNote = "Reading your Pokemon...";
            else {
                String needle = str(pick, "search").toLowerCase(Locale.ROOT).trim();
                List<JsonObject> list = new ArrayList<>();
                for (JsonElement e : arr(st, "entries")) {
                    JsonObject c = e.getAsJsonObject();
                    if (bool(c, "inParty") == bool(pick, "pc")) continue;
                    if (!needle.isEmpty() && !str(c, "name").toLowerCase(Locale.ROOT).contains(needle) && !str(c, "speciesId").toLowerCase(Locale.ROOT).contains(needle)
                            && !str(c, "uniqueName").toLowerCase(Locale.ROOT).contains(needle)) continue;
                    list.add(c);
                }
                List<String> order = List.of("common", "uncommon", "rare", "epic", "legendary", "mythical");
                boolean byRarity = str(pick, "sort").equals("rarity");
                list.sort((a, b) -> {
                    int ua = str(a, "block").isEmpty() ? 0 : 1, ub = str(b, "block").isEmpty() ? 0 : 1;
                    if (ua != ub) return ua - ub;
                    if (byRarity) { int d = order.indexOf(str(b, "rarityId")) - order.indexOf(str(a, "rarityId")); if (d != 0) return d; }
                    return str(a, "name").compareToIgnoreCase(str(b, "name"));
                });
                if (list.isEmpty()) pickNote = str(pick, "role").equals("host") ? "No Pokemon here can be the host: it must be a Mythic with a Unique and not a Transcendent."
                        : bool(pick, "pc") ? "Nothing in the first page of your PC matches." : "Nothing in your party matches.";
                JsonArray row = null;
                int n = 0;
                for (JsonObject c : list) {
                    if (n >= TILES) break;
                    if (n % 4 == 0) { row = new JsonArray(); JsonObject ro = new JsonObject(); ro.add("cells", row); rows.add(ro); }
                    JsonObject t = new JsonObject();
                    t.addProperty("id", str(c, "id"));
                    String sid = str(c, "speciesId").replaceAll("^.*:", "");
                    t.addProperty("sprite", sid.isEmpty() ? "" : RaidViews.spriteKey(sid, false));
                    t.addProperty("name", str(c, "name"));
                    String blk = str(c, "block");
                    t.addProperty("sub", !blk.isEmpty() ? blk : str(c, "rarityId").isEmpty() ? "no profile" : str(c, "rarityId") + (str(c, "uniqueName").isEmpty() ? "" : "  -  " + str(c, "uniqueName")));
                    t.addProperty("color", !blk.isEmpty() ? "#FF7A6B" : rarityColor(str(c, "rarityId")));
                    t.addProperty("ok", blk.isEmpty()); t.addProperty("op", blk.isEmpty() ? 1 : 0.6);
                    t.addProperty("border", !blk.isEmpty() ? "#1F6B86" : rarityColor(str(c, "rarityId")));
                    row.add(t);
                    n++;
                }
                if (list.size() > TILES) pickNote = "Showing " + TILES + " of " + list.size() + ". Type in the search box to narrow it down.";
            }
        }
        v.add("tiles", rows);
        JsonArray flat = new JsonArray();
        for (JsonElement rw : rows) for (JsonElement c0 : rw.getAsJsonObject().getAsJsonArray("cells")) flat.add(c0);
        v.add("tileList", flat);
        v.addProperty("tabPartyBg", !bool(pick, "pc") ? "#8e1426" : "#0f3347");
        v.addProperty("tabPcBg", bool(pick, "pc") ? "#8e1426" : "#0f3347");
        v.addProperty("tabPartyEdge", !bool(pick, "pc") ? "#eef2f7" : "#1f6b86");
        v.addProperty("tabPcEdge", bool(pick, "pc") ? "#eef2f7" : "#1f6b86");
        v.addProperty("pickNote", pickNote);
        v.addProperty("hasPickNote", !pickNote.isEmpty());
        return v;
    }
}
