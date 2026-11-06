package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The AscensionLib "Modifiers" (craft) page. The page keeps no logic of its own: this view holds the tab, the chosen affix and the review
 * step, builds every text from AscensionLib's craftView, and forwards a confirmed operation to AscensionLib, which still checks and charges it.
 * The client only ever says "confirm"; the kind, slot, pokemon and revisions come from here.
 */
final class CraftViews {
    private CraftViews() {}

    static final String PAGE = "ascensionlib_craft";
    private static final String[] MODES = {"upgrade", "refine", "reforge", "perfect", "overreach", "promote", "unique", "respec"};
    private static final java.util.List<String> SLOT_MODES = java.util.List.of("upgrade", "refine", "reforge", "perfect", "overreach");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    static void register() {
        SnobbleUi.registerView(PAGE, CraftViews::apply);
    }

    private static String roman(long r) { return ROMAN[(int) Math.max(1, Math.min(5, r))]; }

    private static JsonObject obj(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    private static JsonArray arr(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    private static String shortPrice(JsonObject p) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (num(p, "dust") > 0) parts.add(num(p, "dust") + " Dust");
        if (num(p, "facets") > 0) parts.add(num(p, "facets") + (num(p, "facets") == 1 ? " Facet" : " Facets"));
        if (num(p, "cores") > 0) parts.add(num(p, "cores") + (num(p, "cores") == 1 ? " Core" : " Cores"));
        return parts.isEmpty() ? "Free" : String.join(" + ", parts);
    }

    private static JsonObject slotById(JsonObject cv, String id) {
        for (JsonElement e : arr(cv, "slots")) if (str(e.getAsJsonObject(), "slotId").equals(id)) return e.getAsJsonObject();
        return null;
    }

    // ------------------------------------------------------------------ events

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : fresh();
        switch (event) {
            case "craftView" -> {
                boolean first = !data.has("cv");
                data.add("cv", s);
                if (first) {
                    st.addProperty("mode", num(s, "pending") > 0 ? "upgrade" : "refine");
                    JsonArray sl = arr(s, "slots");
                    st.addProperty("sel", sl.isEmpty() ? "" : str(sl.get(0).getAsJsonObject(), "slotId"));
                }
                // keep the choice valid
                JsonObject u = obj(s, "unique");
                st.addProperty("usel", validUnique(u, str(st, "usel")));
                boolean found = slotById(s, str(st, "sel")) != null || (str(st, "sel").equals("haunt") && bool(obj(s, "haunt"), "show"));
                if (!found) { JsonArray sl = arr(s, "slots"); st.addProperty("sel", sl.isEmpty() ? "" : str(sl.get(0).getAsJsonObject(), "slotId")); }
                if (!str(s, "message").isEmpty() && str(st, "banner").isEmpty()) { st.addProperty("banner", str(s, "message")); st.addProperty("bannerOk", false); }
                st.addProperty("opId", UUID.randomUUID().toString());
            }
            case "craftDone" -> {
                if (!data.has("cv")) return;
                done(data.getAsJsonObject("cv"), st, s);
                st.addProperty("phase", "browse");
                st.addProperty("act", "");
                st.addProperty("opId", UUID.randomUUID().toString());
            }
            case "msg" -> {
                if (!data.has("cv")) return;
                message(p, data.getAsJsonObject("cv"), st, s);
            }
            default -> { return; }
        }
        data.add("st", st);
        if (data.has("cv")) data.add("vm", build(data.getAsJsonObject("cv"), st));
    }

    private static JsonObject fresh() {
        JsonObject st = new JsonObject();
        st.addProperty("mode", "upgrade");
        st.addProperty("sel", "");
        st.addProperty("usel", "");
        st.addProperty("phase", "browse");
        st.addProperty("act", "");
        st.addProperty("banner", "");
        st.addProperty("bannerOk", false);
        st.addProperty("opId", UUID.randomUUID().toString());
        return st;
    }

    private static String validUnique(JsonObject u, String wanted) {
        JsonArray o = arr(u, "options");
        for (JsonElement e : o) if (str(e.getAsJsonObject(), "id").equals(wanted)) return wanted;
        for (JsonElement e : o) if (bool(e.getAsJsonObject(), "current")) return str(e.getAsJsonObject(), "id");
        return o.isEmpty() ? "" : str(o.get(0).getAsJsonObject(), "id");
    }

    private static JsonObject uniqueOpt(JsonObject cv, JsonObject st) {
        for (JsonElement e : arr(obj(cv, "unique"), "options")) if (str(e.getAsJsonObject(), "id").equals(str(st, "usel"))) return e.getAsJsonObject();
        return null;
    }

    private static boolean hauntShown(JsonObject cv, JsonObject st) {
        return bool(obj(cv, "haunt"), "show") && SLOT_MODES.contains(str(st, "mode"));
    }

    private static boolean isHaunt(JsonObject cv, JsonObject st) { return str(st, "sel").equals("haunt") && hauntShown(cv, st); }

    private static String blockFor(JsonObject cv, JsonObject st, JsonObject s) {
        switch (str(st, "mode")) {
            case "promote": return str(obj(cv, "promotion"), "block");
            case "unique": return str(obj(cv, "unique"), "block");
            case "respec": return str(obj(cv, "respec"), "hourglassBlock");
            default: break;
        }
        if (s == null) return "-";
        return switch (str(st, "mode")) {
            case "upgrade" -> str(s, "upgradeBlock");
            case "refine" -> str(s, "refineBlock");
            case "perfect" -> str(s, "perfectBlock");
            case "overreach" -> str(s, "overreachBlock");
            default -> str(s, "reforgeBlock");
        };
    }

    private static boolean canReview(JsonObject cv, JsonObject st) {
        String mode = str(st, "mode");
        JsonObject s = slotById(cv, str(st, "sel"));
        if (mode.equals("promote")) return str(obj(cv, "promotion"), "block").isEmpty() && !str(obj(cv, "promotion"), "toRarity").isEmpty();
        if (mode.equals("unique")) {
            JsonObject u = obj(cv, "unique");
            return !str(st, "usel").isEmpty() && str(u, "block").isEmpty() && !str(st, "usel").equals(str(u, "currentId"));
        }
        if (mode.equals("respec")) return str(obj(cv, "respec"), "hourglassBlock").isEmpty();
        if (isHaunt(cv, st)) return str(obj(cv, "haunt"), "block").isEmpty();
        return s != null && blockFor(cv, st, s).isEmpty();
    }

    // ------------------------------------------------------------------ clicks

    private static void message(ServerPlayer p, JsonObject cv, JsonObject st, JsonObject m) {
        String action = str(m, "action"), k = str(m, "k");
        boolean browse = str(st, "phase").equals("browse");
        switch (action) {
            case "mode" -> {
                if (browse && java.util.Arrays.asList(MODES).contains(k) && !k.equals(str(st, "mode"))) { st.addProperty("mode", k); st.addProperty("banner", ""); }
            }
            case "sel" -> {
                if (!browse) return;
                if (str(st, "mode").equals("unique")) { if (validUnique(obj(cv, "unique"), k).equals(k)) st.addProperty("usel", k); }
                else if (slotById(cv, k) != null || (k.equals("haunt") && hauntShown(cv, st))) st.addProperty("sel", k);
            }
            case "review" -> { if (browse && canReview(cv, st)) { st.addProperty("act", ""); st.addProperty("phase", "review"); } }
            case "prism" -> {
                JsonObject rs = obj(cv, "respec");
                if (browse && prismOk(cv, st)) { st.addProperty("act", "prism"); st.addProperty("phase", "review"); }
            }
            case "buy" -> {
                JsonObject rs = obj(cv, "respec");
                if (browse && num(rs, "marks") >= num(rs, "price")) { st.addProperty("act", "buy"); st.addProperty("phase", "review"); }
            }
            case "cancel" -> { if (str(st, "phase").equals("review")) { st.addProperty("phase", "browse"); st.addProperty("act", ""); } }
            case "confirm" -> confirm(p, cv, st);
            case "assemble" -> {
                JsonObject u = obj(cv, "unique");
                if (browse && str(st, "mode").equals("unique") && num(u, "fragments") >= num(u, "fragmentsNeeded")) submit(p, cv, st, "assemble", "");
            }
            case "wallet" -> { if (browse) SnobbleUi.forward(p, "ascension_wallet_open", new JsonObject()); }
            case "fuse" -> {
                if (browse && str(cv, "rarityId").equals("mythical")) {
                    JsonObject f = new JsonObject();
                    f.addProperty("hostId", str(cv, "pokemonId"));
                    SnobbleUi.forward(p, "ascension_fusion_open", f);
                }
            }
            default -> { }
        }
    }

    private static boolean prismOk(JsonObject cv, JsonObject st) {
        JsonObject rs = obj(cv, "respec"), u = obj(cv, "unique");
        JsonObject o = uniqueOpt(cv, st);
        return str(st, "mode").equals("unique") && o != null && !str(u, "currentId").isEmpty() && str(rs, "prismBlock").isEmpty() && !bool(o, "current");
    }

    private static void confirm(ServerPlayer p, JsonObject cv, JsonObject st) {
        if (!str(st, "phase").equals("review")) return;
        String act = str(st, "act"), mode = str(st, "mode");
        JsonObject rs = obj(cv, "respec");
        boolean ok = act.equals("buy") ? num(rs, "marks") >= num(rs, "price") : act.equals("prism") ? prismOk(cv, st) : canReview(cv, st);
        if (!ok) return;
        if (isHaunt(cv, st)) { submit(p, cv, st, "haunt", ""); return; }
        if (act.equals("buy")) { submit(p, cv, st, "buy_hourglass", ""); return; }
        if (act.equals("prism")) { submit(p, cv, st, "respec_unique", str(st, "usel")); return; }
        if (mode.equals("respec")) { submit(p, cv, st, "respec_ranks", ""); return; }
        submit(p, cv, st, mode, mode.equals("promote") ? "" : mode.equals("unique") ? str(st, "usel") : str(st, "sel"));
    }

    private static void submit(ServerPlayer p, JsonObject cv, JsonObject st, String kind, String slot) {
        JsonObject m = new JsonObject();
        m.addProperty("operationId", str(st, "opId"));
        m.addProperty("pokemonId", str(cv, "pokemonId"));
        m.addProperty("kind", kind);
        m.addProperty("slotId", slot);
        m.add("profileRevision", cv.get("profileRevision"));
        m.add("walletRevision", cv.get("walletRevision"));
        st.addProperty("doneAct", str(st, "act"));
        st.addProperty("phase", "browse");      // the answer (craftDone) shows in the banner; the same operation id keeps a repeated click from being charged twice
        st.addProperty("act", "");
        SnobbleUi.forward(p, "ascension_craft_confirm", m);
    }

    // ------------------------------------------------------------------ the result banner

    private static void done(JsonObject cv, JsonObject st, JsonObject d) {
        boolean ok = bool(d, "ok");
        String mode = str(st, "mode"), act = str(st, "doneAct");
        String banner;
        if (!ok) banner = str(d, "message");
        else if (isHaunt(cv, st)) banner = (num(d, "newRank") == 1 ? "Rare! " : "Haunted: ") + str(d, "newName") + (bool(d, "replayed") ? " (already done)" : "");
        else {
            long oldV = num(d, "oldValue"), newV = num(d, "newValue");
            String now = roman(num(d, "newRank"));
            JsonObject rs = obj(cv, "respec");
            banner = switch (mode) {
                case "upgrade" -> str(d, "newName") + " -> " + now + " +" + newV + "%";
                case "refine" -> "Refined: +" + oldV + "% -> +" + newV + "%";
                case "perfect" -> "Floor raised: +" + oldV + "% -> +" + newV + "%";
                case "overreach" -> newV > oldV ? "Overreached! +" + oldV + "% -> +" + newV + "%" : "The gamble failed: +" + oldV + "% -> +" + newV + "%, and the slot is sealed";
                case "reforge" -> "Now " + str(d, "newName") + " +" + newV + "%";
                case "promote" -> "Promoted to " + cap(str(d, "newName")) + ": a new modifier slot opened";
                case "respec" -> "Reset to rank I: " + oldV + (oldV == 1 ? " upgrade credit" : " upgrade credits") + " back to spend";
                default -> str(d, "newName").equals("Unique Catalyst") ? "Assembled a Unique Catalyst" : "Unique set: " + str(d, "newName");
            };
            if (act.equals("buy")) banner = "Bought an Hourglass of Undoing for " + num(rs, "price") + " Tower Marks";
            else if (act.equals("prism")) banner = "Unique swapped: " + str(d, "newName");
            if (bool(d, "replayed")) banner += " (already done)";
        }
        st.addProperty("banner", banner);
        boolean good = ok;
        if (ok && mode.equals("overreach") && !isHaunt(cv, st)) good = num(d, "newValue") > num(d, "oldValue");
        st.addProperty("bannerOk", good);
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' '); }

    // ------------------------------------------------------------------ the view model

    private static JsonObject line(String t, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        o.addProperty("text", text);
        return o;
    }

    private static JsonObject pairLine(String l1, String l2, String l3, String r1, String r2, String r3) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "pair");
        o.addProperty("text", "");
        o.addProperty("l1", l1); o.addProperty("l2", l2); o.addProperty("l3", l3);
        o.addProperty("r1", r1); o.addProperty("r2", r2); o.addProperty("r3", r3);
        return o;
    }

    private static JsonObject rankLine(long rank) {
        JsonObject o = line("rank", "");
        o.addProperty("v", rank);
        return o;
    }

    private static JsonObject row(String k, String icon, String name, String sub, String value, boolean on, boolean off, String nameColor) {
        JsonObject o = new JsonObject();
        o.addProperty("k", k);
        o.addProperty("icon", icon);
        o.addProperty("name", name);
        o.addProperty("sub", sub);
        o.addProperty("value", value);
        o.addProperty("on", on);
        o.addProperty("bg", on ? "#12354A" : "#0C2231");
        o.addProperty("border", on ? "#4AD8F6" : "#1F6B86");
        o.addProperty("nameColor", nameColor);
        o.addProperty("clickable", !k.isEmpty());
        return o;
    }

    private static JsonObject extra(String id, String note, String text, boolean enabled, String why) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("note", note);
        o.addProperty("text", text);
        o.addProperty("enabled", enabled);
        o.addProperty("why", why);
        return o;
    }

    private static String costText(JsonObject cv, JsonObject st) {
        JsonObject h = obj(cv, "haunt"), rs = obj(cv, "respec"), u = obj(cv, "unique");
        if (isHaunt(cv, st)) return num(h, "cost") + " Spooky Essence (you have " + num(h, "essence") + ")";
        JsonObject s = slotById(cv, str(st, "sel"));
        switch (str(st, "mode")) {
            case "upgrade": return "1 pending upgrade (you have " + num(cv, "pending") + ")";
            case "refine": return shortPrice(obj(cv, "refine"));
            case "reforge": return shortPrice(obj(cv, "reforge"));
            case "perfect": return s != null ? num(s, "perfectCost") + " Resonance Dust (you have " + num(cv, "dust") + ")" : "-";
            case "overreach": return s != null ? num(s, "overreachDust") + " Resonance Dust + " + num(s, "overreachFacets") + " Facets + 1 Tower Mark (you have " + num(cv, "dust") + " / " + num(cv, "facets") + " / " + num(cv, "marks") + ")" : "-";
            case "promote": return shortPrice(obj(obj(cv, "promotion"), "price"));
            case "respec": return "1 Hourglass of Undoing (you have " + num(rs, "hourglasses") + ")";
            default: return "1 Unique Catalyst + 1 Tower Mark (you have " + num(u, "catalysts") + " / " + num(cv, "marks") + ")";
        }
    }

    private static String shortCost(JsonObject cv, JsonObject st) {
        JsonObject h = obj(cv, "haunt");
        if (isHaunt(cv, st)) return num(h, "cost") + " Spooky Essence";
        JsonObject s = slotById(cv, str(st, "sel"));
        return switch (str(st, "mode")) {
            case "upgrade" -> "1 upgrade";
            case "respec" -> "1 Hourglass";
            case "unique" -> "1 Catalyst + 1 Mark";
            case "perfect" -> s != null ? num(s, "perfectCost") + " Dust" : "-";
            case "overreach" -> s != null ? num(s, "overreachDust") + " Dust + " + num(s, "overreachFacets") + " Facets + 1 Mark" : "-";
            default -> costText(cv, st);
        };
    }

    private static JsonObject build(JsonObject cv, JsonObject st) {
        JsonObject v = new JsonObject();
        String mode = str(st, "mode"), phase = str(st, "phase"), act = str(st, "act");
        JsonObject rs = obj(cv, "respec"), u = obj(cv, "unique"), h = obj(cv, "haunt"), pr = obj(cv, "promotion");
        String rid = str(cv, "rarityId");
        String[] rar = AscViews.RARITY.get(rid);
        v.addProperty("bezel", AscViews.bezel(rid));
        v.addProperty("name", str(cv, "name"));
        String sid = str(cv, "speciesId").replaceAll("^.*:", "");
        v.addProperty("sprite", sid.isEmpty() ? "" : RaidViews.spriteKey(sid, false));
        v.addProperty("lvl", "Lv " + num(cv, "level"));
        v.addProperty("grade", (rid.equals("mythical") ? "MYTHIC" : rid.toUpperCase(Locale.ROOT)) + (str(cv, "uniqueName").isEmpty() ? "" : "  -  UNIQUE"));
        v.addProperty("gradeColor", rar != null ? rar[1] : "#C1C5CF");
        v.addProperty("gradeBg", rar != null ? rar[2] : "#293547");
        v.addProperty("pend", String.valueOf(num(cv, "pending")));
        v.addProperty("pendLabel", num(cv, "pending") == 1 ? "upgrade available" : "upgrades available");
        v.addProperty("next", num(cv, "nextMilestone") > 0 ? "Next upgrade at Lv " + num(cv, "nextMilestone") : "All level milestones reached");
        v.addProperty("showFuse", rid.equals("mythical"));
        v.addProperty("browse", phase.equals("browse"));
        v.addProperty("review", phase.equals("review"));
        // tabs
        JsonArray tabs = new JsonArray();
        for (String m : MODES) {
            JsonObject t = new JsonObject();
            t.addProperty("m", m);
            t.addProperty("label", cap(m));
            boolean on = m.equals(mode);
            t.addProperty("on", on);
            t.addProperty("bg", on ? "#0D2230" : "#0A1B27");
            t.addProperty("border", on ? "#4AD8F6" : "#1F6B86");
            t.addProperty("color", on ? "#FFFFFF" : "#5DA6BD");
            tabs.add(t);
        }
        v.add("tabs", tabs);
        v.addProperty("banner", str(st, "banner"));
        v.addProperty("hasBanner", !str(st, "banner").isEmpty());
        v.addProperty("bannerColor", bool(st, "bannerOk") ? "#62D06A" : "#FF7A6B");
        // rows + detail
        JsonArray rows = new JsonArray();
        JsonArray dl = new JsonArray();
        JsonArray extras = new JsonArray();
        JsonObject s = slotById(cv, str(st, "sel"));
        String reason = "";
        String detailTitle = "";
        if (mode.equals("respec")) {
            rows.add(row("", "", "A Rank Respec resets all of these.", "", "", false, false, "#5DA6BD"));
            for (JsonElement e : arr(rs, "resets")) {
                JsonObject r = e.getAsJsonObject();
                rows.add(row("", AscViews.affixIcon(str(r, "name"), false), str(r, "name"), "rank " + roman(num(r, "rank")) + " -> I" + (num(r, "floor") > 0 ? "  -  floor +" + num(r, "floor") + "% cleared" : ""), "", false, false, "#BDF1FF"));
            }
            detailTitle = "Hourglass of Undoing";
            long spent = num(rs, "spent");
            dl.add(line("text", "Every modifier goes back to rank I, its perfected floor is cleared, and all " + spent + (spent == 1 ? " upgrade credit comes" : " upgrade credits come") + " back to spend again."));
            dl.add(pairLine("Now", spent + " spent", arr(rs, "resets").size() + " modifiers", "After", "0 spent", spent + " to spend again"));
            dl.add(line("muted", "Kept: the affixes themselves, the Unique, the rarity and attunement. Not returned: any dust or facets already spent. A Pokemon that has Overreached is sealed for good and can never be respecced."));
            long hg = num(rs, "hourglasses");
            boolean canBuy = num(rs, "marks") >= num(rs, "price");
            extras.add(extra("buy", "You hold " + hg + (hg == 1 ? " Hourglass" : " Hourglasses") + "  -  " + num(cv, "marks") + " Tower Marks", "Buy for " + num(rs, "price") + " Marks", canBuy && phase.equals("browse"),
                    canBuy ? "" : "Needs " + num(rs, "price") + " Tower Marks"));
            reason = str(rs, "hourglassBlock");
        } else if (mode.equals("unique")) {
            for (JsonElement e : arr(u, "options")) {
                JsonObject o = e.getAsJsonObject();
                rows.add(row(str(o, "id"), AscViews.affixIcon(str(o, "name"), true), str(o, "name"), "", bool(o, "current") ? "held" : "", str(o, "id").equals(str(st, "usel")), false, "#BDF1FF"));
            }
            JsonObject o = uniqueOpt(cv, st);
            if (o == null) dl.add(line("muted", "No Unique powers are on offer."));
            else {
                detailTitle = bool(o, "current") ? str(o, "name") + " (held)" : str(o, "name");
                dl.add(line("muted", "Benefit"));
                dl.add(line("text", str(o, "benefit")));
                dl.add(line("muted", "Drawback"));
                dl.add(line("bad", str(o, "drawback")));
                long fr = num(u, "fragments"), need = num(u, "fragmentsNeeded");
                extras.add(extra("assemble", "Catalysts: " + num(u, "catalysts") + "  -  Fragments: " + fr, "Assemble " + Math.min(fr, need) + "/" + need, fr >= need && phase.equals("browse"), ""));
                if (!str(u, "currentId").isEmpty()) {
                    String why = !str(rs, "prismBlock").isEmpty() ? str(rs, "prismBlock") : bool(o, "current") ? "Already held" : "";
                    extras.add(extra("prism", "Or swap it free with an Unbinding Prism (you hold " + num(rs, "prisms") + ")", "Use Unbinding Prism", why.isEmpty() && phase.equals("browse"), why));
                }
                reason = !str(u, "block").isEmpty() ? str(u, "block") : bool(o, "current") ? "Already held" : "";
            }
        } else {
            if (mode.equals("promote")) rows.add(row("", "", "Promotion applies to the whole Pokemon.", "", "", false, false, "#BDF1FF"));
            if (arr(cv, "slots").isEmpty() && !mode.equals("promote")) rows.add(row("", "", "No affixes to change.", "", "", false, false, "#5DA6BD"));
            for (JsonElement e : arr(cv, "slots")) {
                JsonObject r = e.getAsJsonObject();
                boolean prefix = str(r, "category").equals("prefix");
                boolean off = !mode.equals("promote") && !blockFor(cv, st, r).isEmpty();
                rows.add(row(str(r, "slotId"), AscViews.affixIcon(str(r, "name"), false), str(r, "name"),
                        (prefix ? "Offense" : "Defense") + "  -  " + roman(num(r, "rank")) + (bool(r, "sealed") ? "  -  sealed" : ""), "+" + num(r, "value") + "%",
                        str(r, "slotId").equals(str(st, "sel")), off, off ? "#5DA6BD" : "#BDF1FF"));
            }
            if (hauntShown(cv, st)) {
                boolean has = !str(h, "id").isEmpty();
                rows.add(row("haunt", has ? "a:asc_haunting_" + str(h, "id") : "a:asc_spooky_essence", str(h, "name").isEmpty() ? "Haunting" : str(h, "name"),
                        has ? (bool(h, "rare") ? "Rare Haunting" : "Haunting  -  event") : "None yet  -  " + num(h, "cost") + " Spooky Essence", "", str(st, "sel").equals("haunt"), false, "#FF9A3C"));
            }
            if (mode.equals("promote")) {
                detailTitle = "Promote";
                if (str(pr, "toRarity").isEmpty()) dl.add(line("text", "Already the highest rarity."));
                else {
                    String from = rarName(rid), to = rarName(str(pr, "toRarity"));
                    dl.add(line("text", from + " -> " + to));
                    dl.add(pairLine("Now", from, "keeps all modifiers", "Promote to", to, "+1 modifier slot"));
                    long a = num(pr, "attunement"), need = num(pr, "attunementNeeded");
                    dl.add(line("text", "Attunement " + a + " / " + need));
                    JsonObject bar = line("bar", "");
                    bar.addProperty("v", need <= 0 ? 100 : Math.min(100, 100.0 * a / need));
                    dl.add(bar);
                    dl.add(line("muted", "Attunement grows when this Pokemon is in your party for a Tower boss clear."));
                }
                reason = str(pr, "block");
            } else if (isHaunt(cv, st)) {
                boolean has = !str(h, "id").isEmpty();
                detailTitle = "Haunting  -  Halloween 2026";
                dl.add(line("big", (has ? str(h, "name") + (bool(h, "rare") ? "   [RARE]" : "   [COMMON]") : "Not haunted")));
                dl.add(line("muted", has ? str(h, "effect") : "An event power in a slot of its own, apart from the affixes."));
                JsonArray all = arr(h, "all");
                long rolls = num(h, "rolls");
                dl.add(line("muted", (has ? "Re-roll: one of " : "Roll: one of ") + all.size() + " Hauntings  -  " + rolls + (rolls == 1 ? " roll so far" : " rolls so far")));
                for (JsonElement e : all) {
                    JsonObject o = e.getAsJsonObject();
                    dl.add(line(bool(o, "rare") ? "gold" : "text", (str(o, "id").equals(str(h, "id")) ? "> " : "  ") + str(o, "name") + "   " + (Math.round(((o.has("chance") ? o.get("chance").getAsDouble() : 0) * 10)) / 10.0) + "%   " + str(o, "effect")));
                }
                dl.add(line("muted", "Stays with the Pokemon when traded, after the event too. A re-roll replaces it: 1, 3, 5, then 8 Spooky Essence."));
                reason = str(h, "block");
            } else if (s == null) {
                dl.add(line("muted", "Choose an affix on the left."));
            } else {
                boolean prefix = str(s, "category").equals("prefix");
                detailTitle = cap(mode) + "  -  " + (prefix ? "Offense" : "Defense");
                dl.add(line("big", str(s, "name")));
                dl.add(line("muted", str(s, "condition").isEmpty() ? (prefix ? "Boosts your damage." : "Protects or heals you.") : str(s, "condition")));
                long rank = num(s, "rank");
                JsonObject pair;
                String help;
                switch (mode) {
                    case "upgrade" -> {
                        boolean top = !s.has("nextMin") || num(s, "nextMin") < 0;
                        pair = pairLine("Current  -  " + roman(rank), "+" + num(s, "value") + "%", "Range " + num(s, "floor") + "-" + num(s, "bandMax") + "%",
                                top ? "Top rank" : "Next  -  " + roman(rank + 1), top ? "-" : num(s, "nextMin") + "-" + num(s, "nextMax") + "%", top ? "Cannot rise further" : "New roll range");
                        help = "Raises this affix one rank and rerolls its value. Never fails.";
                    }
                    case "refine" -> {
                        pair = pairLine("Current  -  " + roman(rank), "+" + num(s, "value") + "%", "Range " + num(s, "floor") + "-" + num(s, "bandMax") + "%",
                                "Reroll", num(s, "floor") + "-" + num(s, "bandMax") + "%", num(s, "floor") > num(s, "bandMin") ? "Never below your floor" : "Same rank, new value");
                        help = "Rerolls the value inside this rank's range. It can go lower.";
                    }
                    case "overreach" -> {
                        pair = pairLine("Current", "+" + num(s, "value") + "%", bool(s, "sealed") ? "Sealed" : "Band top " + num(s, "bandMax") + "%",
                                num(s, "overreachChance") + "% to win", "+" + num(s, "overreachMin") + "-" + num(s, "overreachMax") + "%", "lose: it falls and seals");
                        help = "A gamble for a rank V affix at its band top, one attempt per affix. Win: a permanent value above the band top. Lose: the value drops into the lower half of the band, ignoring your floor. Win or lose the slot is sealed: only a Reforge can change it.";
                    }
                    case "perfect" -> {
                        boolean capped = rank < 5 || num(s, "floor") >= num(s, "bandMax");
                        pair = pairLine("Floor", "+" + num(s, "floor") + "%", "value +" + num(s, "value") + "%",
                                capped ? "Not available" : "New floor", capped ? "-" : "+" + (num(s, "floor") + 1) + "%", capped ? "Band top " + num(s, "bandMax") + "%" : "value -> +" + Math.max(num(s, "value"), num(s, "floor") + 1) + "%");
                        help = "Raises the lowest value this rank V affix can roll by one point, up to the band top. A Refine can never roll below it. Each step costs more dust; a Reforge clears it.";
                    }
                    default -> {
                        pair = pairLine("Current  -  " + roman(rank), str(s, "name"), "+" + num(s, "value") + "%  -  rank " + roman(rank), "New affix", "?", num(s, "reforgePool") + " eligible");
                        help = "Swaps this affix for a different one. Rank stays, value rerolls.";
                    }
                }
                dl.add(pair);
                dl.add(rankLine(rank));
                dl.add(line("muted", help));
                reason = blockFor(cv, st, s);
            }
        }
        v.add("rows", rows);
        v.addProperty("noteOnly", false);
        v.addProperty("detailTitle", detailTitle);
        v.add("dl", dl);
        v.add("extras", extras);
        v.addProperty("hasReason", !reason.isEmpty() && !reason.equals("-"));
        v.addProperty("cost", reason.isEmpty() || reason.equals("-") ? shortCost(cv, st) : reason);
        v.addProperty("costColor", reason.isEmpty() || reason.equals("-") ? "#BDF1FF" : "#FF7A6B");
        v.addProperty("canReview", canReview(cv, st) && phase.equals("browse"));
        // wallet
        JsonArray wal = new JsonArray();
        Object[][] coins = {{"Dust", "resonance_dust", num(cv, "dust")}, {"Facets", "facet", num(cv, "facets")}, {"Cores", "ascension_core", num(cv, "cores")},
                {"Scouters", "scouter", num(cv, "scouters")}, {"Fragments", "unique_fragment", num(u, "fragments")}, {"Catalysts", "unique_catalyst", num(u, "catalysts")},
                {"Marks", "tower_mark", num(cv, "marks")}, {"Hourglass", "rank_respec", num(rs, "hourglasses")}, {"Prism", "unique_respec", num(rs, "prisms")}};
        for (Object[] c : coins) wal.add(coin((String) c[0], (String) c[1], (Long) c[2]));
        if (bool(h, "show") || num(h, "essence") > 0) wal.add(coin("Essence", "spooky_essence", num(h, "essence")));
        v.add("wallet", wal);
        // review step
        if (phase.equals("review")) v.add("dlg", dialog(cv, st, s));
        v.addProperty("dlgOn", phase.equals("review"));
        return v;
    }

    private static JsonObject coin(String name, String icon, long n) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("icon", "a:asc_" + icon);
        o.addProperty("n", AscViews.grouped(n));
        o.addProperty("color", n > 0 ? "#BDF1FF" : "#5DA6BD");
        return o;
    }

    private static String rarName(String id) {
        String[] r = AscViews.RARITY.get(id);
        return r != null ? r[0] : cap(id);
    }

    private static JsonObject dialog(JsonObject cv, JsonObject st, JsonObject s) {
        String mode = str(st, "mode"), act = str(st, "act");
        JsonObject rs = obj(cv, "respec"), h = obj(cv, "haunt");
        boolean haunt = isHaunt(cv, st);
        String title = haunt ? (!str(h, "id").isEmpty() ? "Confirm re-roll" : "Confirm haunting") : act.equals("prism") ? "Confirm unbinding" : act.equals("buy") ? "Confirm purchase" : mode.equals("respec") ? "Confirm respec" : "Confirm " + mode;
        String change = "";
        if (haunt) change = !str(h, "id").isEmpty() ? "Re-roll " + str(h, "name") + ": it is replaced by a new random Haunting (it can land on the same one)." : "Give " + str(cv, "name") + " a random Haunting.";
        else if (act.equals("buy")) change = "Trade " + num(rs, "price") + " Tower Marks for one Hourglass of Undoing.";
        else if (act.equals("prism")) { JsonObject po = uniqueOpt(cv, st); change = po != null ? "Swap " + str(cv, "uniqueName") + " for " + str(po, "name") + ". Drawback: " + str(po, "drawback") : ""; }
        else if (mode.equals("upgrade") && s != null) change = str(s, "name") + " " + roman(num(s, "rank")) + " +" + num(s, "value") + "%  ->  " + roman(Math.min(5, num(s, "rank") + 1)) + ", rolls " + num(s, "nextMin") + "-" + num(s, "nextMax") + "%";
        else if (mode.equals("refine") && s != null) change = str(s, "name") + " +" + num(s, "value") + "%  ->  a new value in " + num(s, "bandMin") + "-" + num(s, "bandMax") + "%";
        else if (mode.equals("overreach") && s != null) change = str(s, "name") + " +" + num(s, "value") + "%  ->  " + num(s, "overreachChance") + "% chance of +" + num(s, "overreachMin") + "-" + num(s, "overreachMax") + "%; otherwise it falls to the lower half of the band. Either way the slot is sealed.";
        else if (mode.equals("perfect") && s != null) change = str(s, "name") + " floor +" + num(s, "floor") + "%  ->  +" + (num(s, "floor") + 1) + "% (band top " + num(s, "bandMax") + "%), value +" + num(s, "value") + "% -> +" + Math.max(num(s, "value"), num(s, "floor") + 1) + "%";
        else if (mode.equals("promote")) change = rarName(str(cv, "rarityId")) + "  ->  " + rarName(str(obj(cv, "promotion"), "toRarity")) + ", and one new rank I modifier";
        else if (mode.equals("respec")) change = "Reset " + arr(rs, "resets").size() + " modifiers to rank I and return " + num(rs, "spent") + (num(rs, "spent") == 1 ? " upgrade credit" : " upgrade credits") + ". Perfected floors are cleared.";
        else if (mode.equals("unique")) { JsonObject o = uniqueOpt(cv, st); change = o != null ? (!str(obj(cv, "unique"), "currentId").isEmpty() ? "Replace with " : "Install ") + str(o, "name") + ". Drawback: " + str(o, "drawback") : ""; }
        else if (s != null) change = str(s, "name") + " +" + num(s, "value") + "%  ->  another affix (" + num(s, "reforgePool") + " eligible), rank " + roman(num(s, "rank"));
        String cost = "Cost: " + (haunt ? costText(cv, st) : act.equals("prism") ? "1 Unbinding Prism (no Catalyst, no Tower Mark)" : act.equals("buy") ? num(rs, "price") + " Tower Marks (you have " + num(rs, "marks") + ")" : costText(cv, st));
        String warn = haunt ? "The roll is random and cannot be undone. A re-roll loses the Haunting it replaces for good."
                : act.equals("prism") ? "Uses one Unbinding Prism. The Unique you hold now is lost. It cannot be undone."
                : act.equals("buy") ? "The Marks are spent and the Hourglass joins your wallet."
                : switch (mode) {
                    case "respec" -> "Uses one Hourglass of Undoing. Nothing is refunded and it cannot be undone. A Pokemon that has Overreached can never be respecced.";
                    case "upgrade" -> "No failure chance. This spends one pending upgrade.";
                    case "refine" -> "The result can be lower than now. It cannot be undone.";
                    case "perfect" -> "Always works. Each step costs 10 more dust than the last on this slot. A Refine keeps the floor; a Reforge clears it.";
                    case "overreach" -> "RISKY. The materials are spent win or lose. A lost attempt lowers the roll and seals the slot for good (a Reforge is the only way out). This cannot be undone.";
                    case "promote" -> "No failure chance. Existing modifiers are kept. It cannot be undone.";
                    case "unique" -> "Uses one Catalyst. A Unique already set is lost. It cannot be undone.";
                    default -> "The result is random and cannot be undone.";
                };
        JsonObject o = new JsonObject();
        o.addProperty("title", title);
        o.addProperty("change", change);
        o.addProperty("cost", cost);
        o.addProperty("warn", warn);
        o.addProperty("risky", mode.equals("overreach") && act.isEmpty());
        return o;
    }
}
