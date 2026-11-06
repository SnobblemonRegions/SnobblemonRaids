package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * View models for the AscensionLib pages. They only reshape what AscensionLib sends into text and numbers the layouts print; every balance,
 * fee, craft and fusion is still decided by AscensionLib on the server.
 */
final class AscViews {
    private AscViews() {}

    static void register() {
        SnobbleUi.registerView("ascensionlib_wallet", AscViews::wallet);
        SnobbleUi.registerView("ascensionlib_guide", AscViews::guide);
        SnobbleUi.registerView("ascensionlib_party", AscViews::party);
        SnobbleUi.registerView("ascensionlib_inspect", AscViews::inspect);
    }

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

    static JsonObject vm(JsonObject data) {
        JsonObject v = data.has("vm") && data.get("vm").isJsonObject() ? data.getAsJsonObject("vm") : new JsonObject();
        data.add("vm", v);
        return v;
    }

    // ------------------------------------------------------------------ wallet

    private static final int[] AMOUNTS = {1, 8, 64, 256};

    private static void wallet(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject v = vm(data);
        if (event.equals("walletResult")) {
            v.addProperty("msg", str(s, "message"));
            v.addProperty("msgOk", bool(s, "ok"));
            v.addProperty("msgColor", bool(s, "ok") ? "#62D06A" : "#FF7A6B");
            v.addProperty("hasMsg", !str(s, "message").isEmpty());
            return;
        }
        if (!event.equals("walletView")) return;
        long fee = num(s, "feePercent");
        JsonArray amts = new JsonArray();
        for (int a : AMOUNTS) amts.add(a);
        v.add("amts", amts);
        JsonArray lines = new JsonArray();
        long frag = 0, cata = 0, carriedAny = 0;
        if (s.has("lines")) for (JsonElement e : s.getAsJsonArray("lines")) {
            JsonObject l = e.getAsJsonObject();
            String id = str(l, "id");
            long amount = num(l, "amount"), carried = num(l, "carried");
            boolean tradable = !l.has("tradable") || bool(l, "tradable");
            if (id.equals("unique_fragment")) frag = amount;
            if (id.equals("unique_catalyst")) cata = amount;
            carriedAny += carried;
            JsonObject o = new JsonObject();
            o.addProperty("icon", "a:asc_" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", ""));
            o.addProperty("id", id);
            o.addProperty("name", str(l, "name"));
            o.addProperty("color", str(l, "color").matches("#[0-9a-fA-F]{6}") ? str(l, "color") : "#E6F7FF");
            o.addProperty("amount", grouped(amount));
            o.addProperty("tradable", tradable);
            o.addProperty("takeText", tradable ? "Take" : "Bound");
            JsonArray feeText = new JsonArray(), takeOk = new JsonArray(), feeBad = new JsonArray();
            for (int n : AMOUNTS) {
                long f = (n * fee + 99) / 100;
                boolean shortBy = n + f > amount;
                feeText.add(tradable ? "fee +" + grouped(f) + " = " + grouped(n + f) + (shortBy ? " (you have " + grouped(amount) + ")" : "") : "");
                takeOk.add(tradable && !shortBy);
                feeBad.add(shortBy);
            }
            o.add("feeText", feeText);
            o.add("takeOk", takeOk);
            o.add("feeBad", feeBad);
            o.addProperty("hasCarried", carried > 0);
            o.addProperty("carried", "carrying " + grouped(carried));
            lines.add(o);
        }
        v.add("lines", lines);
        long needed = num(s, "catalystNeeded");
        v.addProperty("hasCatalyst", needed > 0 && frag >= 0);
        long have = Math.min(frag, Math.max(1, needed));
        v.addProperty("catPct", needed > 0 ? Math.round(have * 100.0 / needed) : 0);
        v.addProperty("catText", grouped(frag) + " of " + needed + " fragments" + (cata > 0 ? "  -  " + grouped(cata) + " ready" : ""));
        long free = num(s, "freeSlots");
        v.addProperty("free", free + " free inventory slot" + (free == 1 ? "" : "s") + " (64 units per slot)");
        v.addProperty("canDepositAll", carriedAny > 0);
        v.addProperty("fee", fee);
    }

    // ------------------------------------------------------------------ guide

    private static JsonObject guideData;

    private static synchronized JsonObject guideData() {
        if (guideData != null) return guideData;
        try (java.io.InputStream in = AscViews.class.getResourceAsStream("/data/snobblemon_server/ascension_guide.json")) {
            guideData = in == null ? new JsonObject() : com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException | RuntimeException e) {
            guideData = new JsonObject();
        }
        return guideData;
    }

    private static final java.util.regex.Pattern PLACE = java.util.regex.Pattern.compile("\\{([a-zA-Z]+\\.[a-zA-Z]+)\\}");

    private static String fill(String text, JsonObject facts) {
        java.util.regex.Matcher m = PLACE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(facts.has(m.group(1)) ? facts.get(m.group(1)).getAsString() : m.group(0)));
        m.appendTail(sb);
        return sb.toString();
    }

    private static void guide(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        guideInto(data, event.equals("guideFacts") ? s : null);
    }

    /** Builds the guide topics into data.vm. The numbers a server can tune arrive in a guideFacts event and replace the {placeholders}. */
    static void guideInto(JsonObject data, JsonObject live) {
        JsonObject g = guideData();
        JsonObject facts = g.has("facts") ? g.getAsJsonObject("facts").deepCopy() : new JsonObject();
        JsonObject tables = new JsonObject();
        if (live != null) {
            if (live.has("facts") && live.get("facts").isJsonObject()) for (var en : live.getAsJsonObject("facts").entrySet()) if (en.getValue().isJsonPrimitive()) facts.add(en.getKey(), en.getValue());
            if (live.has("tables") && live.get("tables").isJsonObject()) tables = live.getAsJsonObject("tables");
            data.add("gfacts", facts);
            data.add("gtables", tables);
        } else if (data.has("gfacts")) {
            facts = data.getAsJsonObject("gfacts");
            tables = data.has("gtables") ? data.getAsJsonObject("gtables") : tables;
        }
        JsonObject v = vm(data);
        JsonArray topics = new JsonArray();
        JsonArray list = g.has("topics") ? g.getAsJsonArray("topics") : new JsonArray();
        int idx = 0;
        for (JsonElement te : list) {
            JsonObject t = te.getAsJsonObject();
            JsonObject o = new JsonObject();
            o.addProperty("i", idx);
            o.addProperty("title", str(t, "title"));
            o.addProperty("prev", idx > 0 ? "< " + str(list.get(idx - 1).getAsJsonObject(), "title") : "");
            o.addProperty("next", idx < list.size() - 1 ? str(list.get(idx + 1).getAsJsonObject(), "title") + " >" : "");
            o.addProperty("pi", idx - 1);
            o.addProperty("ni", idx + 1);
            o.addProperty("hasPrev", idx > 0);
            o.addProperty("hasNext", idx < list.size() - 1);
            JsonArray blocks = new JsonArray();
            if (t.has("blocks")) for (JsonElement be : t.getAsJsonArray("blocks")) {
                JsonObject b = be.getAsJsonObject();
                String type = str(b, "type");
                switch (type) {
                    case "p" -> blocks.add(gb("p", fill(str(b, "text"), facts)));
                    case "tip" -> blocks.add(gb("tip", "TIP  " + fill(str(b, "text"), facts)));
                    case "warn" -> blocks.add(gb("warn", "NOTE  " + fill(str(b, "text"), facts)));
                    case "ul", "steps" -> {
                        int n = 1;
                        if (b.has("items")) for (JsonElement i : b.getAsJsonArray("items")) blocks.add(gb("li", (type.equals("ul") ? "- " : (n++) + ".  ") + fill(i.getAsString(), facts)));
                    }
                    case "table" -> {
                        JsonObject src = b.has("live") && tables.has(str(b, "live")) && tables.get(str(b, "live")).isJsonObject() ? tables.getAsJsonObject(str(b, "live")) : b;
                        JsonObject head = new JsonObject();
                        head.addProperty("k", "th");
                        JsonArray hc = new JsonArray();
                        if (src.has("head")) for (JsonElement h : src.getAsJsonArray("head")) hc.add(cell(fill(h.getAsString(), facts), "#5DA6BD"));
                        head.add("cells", hc);
                        blocks.add(head);
                        if (src.has("rows")) for (JsonElement re : src.getAsJsonArray("rows")) {
                            JsonObject row = new JsonObject();
                            row.addProperty("k", "tr");
                            JsonArray cells = new JsonArray();
                            int ci = 0;
                            for (JsonElement ce : re.getAsJsonArray()) cells.add(cell(fill(ce.getAsString(), facts), ci++ == 0 ? "#BDF1FF" : "#9FCBDB"));
                            row.add("cells", cells);
                            blocks.add(row);
                        }
                    }
                    default -> { }
                }
            }
            o.add("blocks", blocks);
            topics.add(o);
            idx++;
        }
        v.add("topics", topics);
        v.addProperty("hasTopics", !topics.isEmpty());
    }

    private static JsonObject gb(String k, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("k", k);
        o.addProperty("text", text);
        return o;
    }

    private static JsonObject cell(String x, String color) {
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("color", color);
        return o;
    }

    // ------------------------------------------------------------------ party (AscensionDex)

    static final java.util.Map<String, String[]> RARITY = java.util.Map.of(
            "common", new String[]{"Common", "#C1C5CF", "#293547"}, "uncommon", new String[]{"Uncommon", "#1BD7E8", "#073442"},
            "rare", new String[]{"Rare", "#38CCEF", "#103E79"}, "epic", new String[]{"Epic", "#AF69FF", "#2A1263"},
            "legendary", new String[]{"Legendary", "#FFD437", "#25305B"}, "mythical", new String[]{"Mythic", "#FF3448", "#620512"});

    private static void party(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject v = vm(data);
        if (!v.has("hasTopics")) guideInto(data, null);
        switch (event) {
            case "walletView", "walletResult" -> wallet(p, event, s, data);
            case "guideFacts" -> guideInto(data, s);
            case "partyView" -> {
                JsonArray slots = new JsonArray();
                int first = 0;
                boolean found = false;
                int i = 0;
                if (s.has("slots")) for (JsonElement e : s.getAsJsonArray("slots")) {
                    JsonObject sl = e.getAsJsonObject();
                    JsonObject o = new JsonObject();
                    String id = str(sl, "id");
                    boolean has = !id.isEmpty();
                    o.addProperty("i", i);
                    o.addProperty("num", String.valueOf(i + 1));
                    o.addProperty("id", id);
                    o.addProperty("empty", !has);
                    if (has && !found) { first = i; found = true; }
                    String rid = str(sl, "rarityId");
                    String[] r = RARITY.get(rid);
                    o.addProperty("name", (bool(sl, "shiny") ? "* " : "") + str(sl, "name"));
                    o.addProperty("sprite", has ? RaidViews.spriteKey(str(sl, "speciesId").replaceAll("^.*:", ""), bool(sl, "shiny")) : "");
                    o.addProperty("level", "Lv " + num(sl, "level"));
                    o.addProperty("rarity", r != null ? r[0] : "No profile");
                    o.addProperty("rarityColor", r != null ? r[1] : "#FFFFFF");
                    o.addProperty("rarityBg", r != null ? r[2] : "#46526A");
                    long hp = Math.max(0, Math.min(100, num(sl, "hp")));
                    o.addProperty("hp", hp);
                    o.addProperty("hpColor", hp < 25 ? "#FF7A6B" : hp < 50 ? "#F2CF3A" : "#62D06A");
                    long pending = num(sl, "pending");
                    o.addProperty("badge", pending > 0 ? pending + (pending == 1 ? " upgrade" : " upgrades") : "");
                    o.addProperty("nameColor", bool(sl, "fainted") ? "#FF7A6B" : "#BDF1FF");
                    o.addProperty("canUpgrade", has && !rid.isEmpty() && !bool(sl, "locked"));
                    o.addProperty("canInspect", has);
                    o.addProperty("info", has ? str(sl, "name") + (rid.isEmpty() ? " has no ascension profile" : "") : "Pick a Pokemon");
                    slots.add(o);
                    i++;
                }
                v.add("slots", slots);
                v.addProperty("first", first);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ inspect (owned Pokemon or scouted enemies)

    private static final java.util.Map<String, String> TYPE_COLOR = java.util.Map.ofEntries(
            java.util.Map.entry("normal", "#a8a77a"), java.util.Map.entry("fire", "#ee8130"), java.util.Map.entry("water", "#6390f0"), java.util.Map.entry("electric", "#f7d02c"),
            java.util.Map.entry("grass", "#7ac74c"), java.util.Map.entry("ice", "#96d9d6"), java.util.Map.entry("fighting", "#c22e28"), java.util.Map.entry("poison", "#a33ea1"),
            java.util.Map.entry("ground", "#e2bf65"), java.util.Map.entry("flying", "#a98ff3"), java.util.Map.entry("psychic", "#f95587"), java.util.Map.entry("bug", "#a6b91a"),
            java.util.Map.entry("rock", "#b6a136"), java.util.Map.entry("ghost", "#735797"), java.util.Map.entry("dragon", "#6f35fc"), java.util.Map.entry("dark", "#705746"),
            java.util.Map.entry("steel", "#b7b7ce"), java.util.Map.entry("fairy", "#d685ad"));
    private static final String[] STAT_NAMES = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
    private static final java.util.regex.Pattern AFFIX_RANK = java.util.regex.Pattern.compile("^(.*?)\\s*\\(([a-z]+)\\)\\s*$");

    private static boolean hasArt(String name) {
        return AscViews.class.getResource("/data/snobblemon_server/art/" + name + ".png") != null;
    }

    /** The icon key for an affix name, the same rule the web page used: "Name (kind)" tries name_kind first, then name. */
    static String affixIcon(String name, boolean unique) {
        if (unique) return hasArt("affix_unique") ? "a:affix_unique" : "";
        java.util.regex.Matcher m = AFFIX_RANK.matcher(name);
        String base = (m.matches() ? m.group(1) : name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
        if (m.matches() && hasArt("affix_" + base + "_" + m.group(2))) return "a:affix_" + base + "_" + m.group(2);
        return hasArt("affix_" + base) ? "a:affix_" + base : "";
    }

    private static String originText(String k) {
        if (k.isEmpty()) return "Origin unknown";
        return switch (k) {
            case "wild_capture" -> "Wild capture";
            case "hatch" -> "Hatched";
            case "admin" -> "Operator-registered";
            default -> { String t = k.replace('_', ' '); yield Character.toUpperCase(t.charAt(0)) + t.substring(1); }
        };
    }

    /** The tablet bezel for a rarity (ui.js A.setBezel; an unknown rarity gets the default shell). */
    static String bezel(String rarityId) {
        return "sprite:ascensionlib/bezel_" + (rarityId != null && rarityId.matches("common|uncommon|rare|epic|legendary|mythical") ? rarityId : "none");
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** One page entry (an owned Pokemon, or one scouted enemy) from a name, species and the detail object (null when not revealed). */
    private static JsonObject inspectEntry(int i, int n, String name, String speciesId, JsonObject d, JsonObject owned, boolean ownedMode, JsonObject enemy, long scouters) {
        JsonObject o = new JsonObject();
        o.addProperty("i", i);
        o.addProperty("pi", (i - 1 + n) % Math.max(1, n));
        o.addProperty("ni", (i + 1) % Math.max(1, n));
        o.addProperty("name", name);
        String sid = speciesId.replaceAll("^.*:", "");
        o.addProperty("sprite", sid.isEmpty() ? "" : RaidViews.spriteKey(sid, false));
        o.addProperty("dex", sid.isEmpty() ? "" : cap(sid.replace('_', ' ')));
        JsonArray types = d != null && d.has("types") && d.get("types").isJsonArray() ? d.getAsJsonArray("types") : new JsonArray();
        String t0 = types.isEmpty() ? "" : types.get(0).getAsString();
        o.addProperty("hasType", !t0.isEmpty());
        o.addProperty("type", t0.toUpperCase(Locale.ROOT));
        o.addProperty("typeColor", TYPE_COLOR.getOrDefault(t0, "#7f869b"));
        StringBuilder tl = new StringBuilder();
        for (JsonElement t : types) tl.append(tl.length() == 0 ? "" : " / ").append(t.getAsString());
        o.addProperty("sub", d != null ? "Lv " + num(d, "level") + "  -  " + tl : ownedMode ? (name.isEmpty() ? "Loading server data..." : "No profile available") : "Details not revealed");
        JsonArray stats = new JsonArray();
        JsonArray base = d != null && d.has("baseStats") && d.get("baseStats").isJsonArray() ? d.getAsJsonArray("baseStats") : new JsonArray();
        for (int k = 0; k < 6; k++) {
            JsonObject so = new JsonObject();
            so.addProperty("k", STAT_NAMES[k]);
            boolean has = k < base.size();
            long v = has ? base.get(k).getAsLong() : 0;
            so.addProperty("v", has ? String.valueOf(v) : "?");
            so.addProperty("bar", Math.max(has ? 1 : 0, Math.min(160, Math.round(v / 255.0 * 160))));
            stats.add(so);
        }
        o.add("stats", stats);
        String rid = d != null ? str(d, "rarityId") : "";
        String[] r = RARITY.get(rid);
        o.addProperty("bezel", bezel(rid));
        String uniq = d != null ? str(d, "uniqueName") : "";
        o.addProperty("grade", d == null ? "RARITY ?" : (rid.equals("mythical") ? "MYTHIC" : rid.toUpperCase(Locale.ROOT)) + (uniq.isEmpty() ? "" : "  -  UNIQUE"));
        o.addProperty("gradeColor", r != null ? r[1] : "#5DA6BD");
        o.addProperty("gradeBg", r != null ? r[2] : "#16303f");
        // affix rows
        JsonArray rows = new JsonArray();
        int pre = 0, tot = 0, ri = 0;
        if (d != null && !uniq.isEmpty()) {
            JsonObject ro = new JsonObject();
            ro.addProperty("i", ri++);
            ro.addProperty("name", "UNIQUE  " + uniq);
            ro.addProperty("icon", affixIcon(uniq, true));
            ro.addProperty("unique", true);
            ro.addProperty("value", "");
            ro.addProperty("tag", "");
            ro.addProperty("tagColor", "#F2CF3A");
            ro.addProperty("rank", "");
            ro.addProperty("cond", "A Unique power: fixed tuning, no ordinary rank.");
            rows.add(ro);
        }
        if (d != null && d.has("slots") && d.get("slots").isJsonArray()) for (JsonElement se : d.getAsJsonArray("slots")) {
            JsonObject s = se.getAsJsonObject();
            tot++;
            boolean prefix = str(s, "category").equals("prefix");
            if (prefix) pre++;
            long rank = num(s, "rank"), min = s.has("min") ? num(s, "min") : -1, max = s.has("max") ? num(s, "max") : -1, val = num(s, "value");
            boolean sealed = bool(s, "sealed");
            boolean perfected = rank == 5 && max >= 0 && val >= max && !sealed;
            boolean over = sealed && max >= 0 && val > max;
            JsonObject ro = new JsonObject();
            ro.addProperty("i", ri++);
            ro.addProperty("name", str(s, "name"));
            ro.addProperty("icon", affixIcon(str(s, "name"), false));
            ro.addProperty("unique", false);
            ro.addProperty("value", "+" + val + "%");
            String theme = str(s, "theme");
            String tag = (theme.isEmpty() ? "" : theme.toUpperCase(Locale.ROOT)) + (perfected ? "  MAX" : "") + (over ? "  OVERREACH" : sealed ? "  SEALED" : "");
            ro.addProperty("tag", tag.trim());
            ro.addProperty("tagColor", over || perfected ? "#F2CF3A" : sealed ? "#FF7A6B" : "#5DA6BD");
            StringBuilder pips = new StringBuilder();
            for (int k = 0; k < 5; k++) pips.append(k < rank ? "#" : "-");
            ro.addProperty("rank", pips + (min >= 0 ? "    rolls " + min + "-" + max + "%" : ""));
            String cond = str(s, "condition");
            ro.addProperty("cond", cond.isEmpty() ? (prefix ? "An offensive modifier." : "A defensive or recovery modifier.") : cond);
            rows.add(ro);
        }
        o.add("rows", rows);
        o.addProperty("hasRows", !rows.isEmpty());
        o.addProperty("noRows", d != null && rows.isEmpty());
        o.addProperty("noProfile", d == null);
        o.addProperty("emptyText", d != null ? "No modifiers rolled." : ownedMode ? (name.isEmpty() ? "Loading server data..." : "This Pokemon has no ascension profile.") : "Scout to reveal rarity and modifiers.");
        JsonObject ex = owned != null && owned.has("extra") && owned.get("extra").isJsonObject() ? owned.getAsJsonObject("extra") : new JsonObject();
        o.addProperty("count", d == null ? "? / ?" : ownedMode && owned != null
                ? "Prefix " + pre + "/" + num(ex, "prefixCap") + "  -  Suffix " + (tot - pre) + "/" + num(ex, "suffixCap") : rows.size() + " / " + rows.size());
        // resonance
        StringBuilder reso = new StringBuilder();
        if (d != null && d.has("resonance") && d.get("resonance").isJsonArray()) for (JsonElement te : d.getAsJsonArray("resonance")) {
            JsonObject t = te.getAsJsonObject();
            long b = num(t, "bonusPercent");
            reso.append(reso.length() == 0 ? "RESONANCE   " : "    ").append(str(t, "theme").toUpperCase(Locale.ROOT)).append(' ').append(num(t, "pieces")).append(b > 0 ? "  +" + b + "%" : "/2");
        }
        o.addProperty("reso", reso.toString());
        o.addProperty("hasReso", reso.length() > 0);
        // bottom line
        String bottom = "";
        if (ownedMode && owned != null && d != null) {
            long pend = num(owned, "pending");
            java.util.List<String> bits = new java.util.ArrayList<>();
            bits.add(pend + " upgrade" + (pend == 1 ? "" : "s") + " pending");
            bits.add("attunement " + num(owned, "attunement"));
            bits.add(originText(str(ex, "origin")));
            String init = str(ex, "initialRarity");
            if (!init.isEmpty() && !init.equals(rid)) bits.add("promoted from " + cap(init));
            long ms = num(ex, "milestones");
            bits.add(ms + " milestone" + (ms == 1 ? "" : "s"));
            if (num(ex, "highestLevel") > 0) bits.add("best Lv " + num(ex, "highestLevel"));
            bottom = String.join("  -  ", bits);
        } else if (!ownedMode) bottom = "Scouters: " + scouters;
        o.addProperty("bottom", bottom);
        // buttons
        boolean can = ownedMode && owned != null && d != null;
        boolean locked = can && bool(ex, "craftLocked");
        long pend = owned != null ? num(owned, "pending") : 0;
        o.addProperty("canUp", can);
        o.addProperty("upOk", can && !locked);
        o.addProperty("upText", locked ? "Locked" : pend > 0 ? "Upgrade (" + pend + ")" : "Upgrade");
        boolean bare = ownedMode && owned != null && !name.isEmpty() && d == null;
        long sig = owned != null ? num(owned, "sigils") : 0;
        o.addProperty("bare", bare);
        o.addProperty("sigOk", sig >= 1);
        o.addProperty("sigText", "Use Ascension Sigil (" + sig + ")");
        o.addProperty("pokemonId", owned != null ? str(owned, "pokemonId") : "");
        o.addProperty("subjectId", enemy != null ? str(enemy, "subjectId") : "");
        o.addProperty("scoutOk", enemy != null && d == null && scouters >= 1);
        o.addProperty("scoutText", d != null ? "Scouted" : "Scout");
        return o;
    }

    private static void inspect(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject v = vm(data);
        if (event.equals("hello")) { v.entrySet().clear(); return; }
        if (event.equals("inspectOwned")) {
            JsonObject d = s.has("detail") && s.get("detail").isJsonObject() ? s.getAsJsonObject("detail") : null;
            JsonArray es = new JsonArray();
            es.add(inspectEntry(0, 1, str(s, "name"), s.has("extra") && s.get("extra").isJsonObject() ? str(s.getAsJsonObject("extra"), "speciesId") : "", d, s, true, null, 0));
            v.add("entries", es);
            v.addProperty("title", "ASCENSION DEX");
            v.addProperty("scout", false);
            v.addProperty("multi", false);
            v.addProperty("encounterId", "");
            v.addProperty("n", 1);
        } else if (event.equals("inspectScout") && !(v.has("title") && str(v, "title").equals("ASCENSION DEX") && v.has("fresh") && !bool(v, "fresh"))) {
            JsonArray src = s.has("entries") && s.get("entries").isJsonArray() ? s.getAsJsonArray("entries") : new JsonArray();
            JsonArray es = new JsonArray();
            int n = src.size();
            for (int i = 0; i < n; i++) {
                JsonObject e = src.get(i).getAsJsonObject();
                JsonObject d = e.has("detail") && e.get("detail").isJsonObject() ? e.getAsJsonObject("detail") : null;
                es.add(inspectEntry(i, n, str(e, "name"), str(e, "speciesId"), d, null, false, e, num(s, "scouters")));
            }
            v.add("entries", es);
            v.addProperty("title", "SCOUTING");
            v.addProperty("scout", true);
            v.addProperty("multi", n > 1);
            v.addProperty("encounterId", str(s, "encounterId"));
            v.addProperty("n", n);
            v.addProperty("info", n == 0 ? "No encounter to scout." : "");
        }
        v.addProperty("loaded", v.has("entries"));
    }
}
