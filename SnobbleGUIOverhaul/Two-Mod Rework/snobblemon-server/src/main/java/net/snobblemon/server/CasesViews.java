package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * Regions Cases: help tickets, suggestions, reports, punishments and the audit log. SnobbleStaff owns every ticket, rule and permission; this view keeps
 * which tab / filter / page the player is on, asks the mod for it, draws the answer and passes each click back as the same request the web page made.
 * The mod checks permissions again on every request, so a player who is not staff gets nothing from the staff requests.
 */
final class CasesViews {
    private CasesViews() {}

    static final String PAGE = "cases";
    private static final String CHANNEL = "snobblestaff";
    private static final String[][] SUG_STATUS = {{"NEW", "NEW"}, {"CONSIDERING", "CONSIDERING"}, {"PLANNED", "PLANNED"}, {"DONE", "DONE"}, {"DECLINED", "DECLINED"}};
    private static final String[][] PTYPES = {{"warn", "Warn", "canWarn"}, {"note", "Staff note", "canWarn"}, {"mute", "Mute", "canMute"}, {"tempmute", "Temp mute", "canMute"},
            {"kick", "Kick", "canKick"}, {"tempban", "Temp ban", "canBan"}, {"ban", "Ban", "canBan"}};
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    /** The mod's last "state" per player. */
    private static final Map<UUID, JsonObject> STATE = new ConcurrentHashMap<>();

    static void register() {
        SnobbleUi.registerView(PAGE, CasesViews::apply);
        // TEST RIGS ONLY (needs -Dsnobble.rig): SnobbleStaff is not installed there, so write what the page asked it to do into the log
        if (System.getProperty("snobble.rig") != null)
            SnobbleUi.onMessage(CHANNEL, (p, json) -> SnobblemonServer.LOGGER.info("[rig] snobblestaff <- {}", json));
    }

    private static JsonArray arr(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }
    private static JsonObject obj(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null; }
    private static JsonObject o() { return new JsonObject(); }
    private static boolean has(JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull(); }
    private static String up(String s) { return s == null ? "" : s.toUpperCase(Locale.ROOT); }

    private static String when(long ms) { return WHEN.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())); }
    private static String ago(long ms) {
        long m = Math.max(0, (System.currentTimeMillis() - ms) / 60000);
        if (m < 1) return "just now";
        if (m < 60) return m + "m ago";
        if (m < 1440) return m / 60 + "h " + m % 60 + "m ago";
        return m / 1440 + "d " + m % 1440 / 60 + "h ago";
    }

    // ------------------------------------------------------------ requests to the mod

    private static JsonObject st0() {
        JsonObject st = o();
        JsonObject q = o();
        q.addProperty("open", true); q.addProperty("progress", true); q.addProperty("closed", false);
        q.addProperty("category", ""); q.addProperty("mineOnly", false); q.addProperty("unclaimed", false);
        q.addProperty("sort", "oldest"); q.addProperty("text", ""); q.addProperty("page", 0);
        st.add("q", q);
        JsonObject sq = o();
        sq.addProperty("status", ""); sq.addProperty("text", ""); sq.addProperty("page", 0);
        st.add("sq", sq);
        st.addProperty("newMode", false); st.addProperty("newCat", ""); st.addProperty("newSug", false);
        st.addProperty("reportsClosed", false); st.addProperty("logKind", ""); st.addProperty("ptype", "");
        return st;
    }

    private static void to(ServerPlayer p, String action, Object... kv) {
        JsonObject m = o();
        m.addProperty("action", action);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v instanceof Boolean b) m.addProperty(k, b);
            else if (v instanceof Number n) m.addProperty(k, n);
            else m.addProperty(k, String.valueOf(v));
        }
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static void queueReq(ServerPlayer p, JsonObject st) {
        JsonObject q = st.getAsJsonObject("q");
        to(p, "queue", "open", bool(q, "open"), "progress", bool(q, "progress"), "closed", bool(q, "closed"), "category", str(q, "category"), "mineOnly", bool(q, "mineOnly"),
                "unclaimed", bool(q, "unclaimed"), "sort", str(q, "sort"), "text", str(q, "text"), "page", num(q, "page"));
    }

    private static void sugsReq(ServerPlayer p, JsonObject st) {
        JsonObject q = st.getAsJsonObject("sq");
        to(p, "sugs", "status", str(q, "status"), "text", str(q, "text"), "page", num(q, "page"));
    }

    private static void refresh(ServerPlayer p, JsonObject st) {
        JsonObject d = STATE.get(p.getUUID());
        if (d == null) { to(p, "ready", "view", "mine"); return; }
        switch (str(d, "view")) {
            case "queue" -> queueReq(p, st);
            case "ticket" -> to(p, "ticket", "id", num(obj(d, "ticket"), "id"));
            case "reports" -> to(p, "reports", "closed", bool(st, "reportsClosed"));
            case "sugs" -> sugsReq(p, st);
            case "sug" -> to(p, "sug", "id", num(obj(d, "suggestion"), "id"));
            case "psug" -> to(p, "psug", "id", has(d, "selected") ? num(obj(d, "selected"), "id") : 0);
            case "prep" -> to(p, "prep");
            case "punish" -> to(p, "player", "name", str(d, "query"));
            case "logs" -> to(p, "logs", "kind", str(st, "logKind"));
            default -> to(p, "mine", "id", has(d, "selected") ? num(obj(d, "selected"), "id") : 0);
        }
    }

    // ------------------------------------------------------------ events

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : st0();
        switch (event) {
            case "state" -> STATE.put(p.getUUID(), s.deepCopy());
            case "result" -> result(st, data, s);
            case "tick" -> { }
            case "preview" -> st.add("detected", s.deepCopy());
            case "close" -> { SnobbleUi.close(p, PAGE); return; }
            case "msg" -> { if (!message(p, st, data, s)) return; }
            default -> { return; }
        }
        data.add("st", st);
        if (st.has("_lo")) data.add("_local", st.remove("_lo"));
        JsonObject d = STATE.get(p.getUUID());
        data.add("vm", d == null ? connecting() : build(d, st));
    }

    private static void toast(JsonObject data, String text, boolean ok) {
        JsonObject t = o();
        t.addProperty("text", text);
        t.addProperty("ok", ok);
        data.add("_toast", t);
    }

    private static void clearLocal(JsonObject st, String... names) {
        JsonObject lo = st.has("_lo") ? st.getAsJsonObject("_lo") : o();
        JsonObject vals = lo.has("vals") ? lo.getAsJsonObject("vals") : o();
        for (String n : names) vals.addProperty(n, "");
        st.addProperty("localSeq", num(st, "localSeq") + 1);
        lo.addProperty("seq", num(st, "localSeq"));
        lo.add("vals", vals);
        st.add("_lo", lo);
    }

    private static void result(JsonObject st, JsonObject data, JsonObject r) {
        boolean ok = bool(r, "ok");
        String a = str(r, "action");
        toast(data, str(r, "message").isEmpty() ? (ok ? "Done." : "That did not work.") : str(r, "message"), ok);
        if (!ok) return;
        if (a.equals("sclose") || a.equals("pclose")) { st.addProperty("stampUntil", System.currentTimeMillis() + 2300); st.addProperty("stampText", "CASE CLOSED"); st.addProperty("stampSub", "filed away"); }
        switch (a) {
            case "create" -> { st.addProperty("newMode", false); st.remove("detected"); st.addProperty("newCat", ""); clearLocal(st, "newtext"); }
            case "screate" -> { st.addProperty("newSug", false); clearLocal(st, "sugtitle", "sugidea"); }
            case "rcreate" -> clearLocal(st, "repname", "reptext");
            case "preply", "sreply", "sgpreply", "sgsreply" -> clearLocal(st, "reply");
            case "punish" -> clearLocal(st, "preason");
            default -> { }
        }
    }


    // ------------------------------------------------------------ clicks

    private static boolean message(ServerPlayer p, JsonObject st, JsonObject data, JsonObject m) {
        if (!str(m, "action").equals("act")) return false;
        String a = str(m, "a"), v = str(m, "v"), n = str(m, "n"), t = str(m, "t").trim(), dur = str(m, "d");
        long id = num(m, "id");
        JsonObject q = st.getAsJsonObject("q"), sq = st.getAsJsonObject("sq");
        JsonObject d = STATE.get(p.getUUID());
        switch (a) {
            case "refresh" -> refresh(p, st);
            case "tick" -> { }
            case "tab" -> {
                switch (v) {
                    case "queue" -> queueReq(p, st);
                    case "reports" -> { st.addProperty("reportsClosed", false); to(p, "reports", "closed", false); }
                    case "sugs" -> sugsReq(p, st);
                    case "psug" -> { st.addProperty("newSug", false); to(p, "psug", "id", 0); }
                    case "prep" -> to(p, "prep");
                    case "punish" -> to(p, "player", "name", "");
                    case "logs" -> to(p, "logs", "kind", str(st, "logKind"));
                    default -> { st.addProperty("newMode", false); to(p, "mine", "id", 0); }
                }
            }
            case "newticket" -> st.addProperty("newMode", true);
            case "cancelnew" -> st.addProperty("newMode", false);
            case "selmine" -> { st.addProperty("newMode", false); to(p, "mine", "id", id); }
            case "newcat" -> st.addProperty("newCat", v);
            case "preview" -> { if (t.length() >= 3) to(p, "preview", "text", t); else st.remove("detected"); }
            case "create" -> {
                if (t.length() < 5) { toast(data, "Please describe the problem in a little more detail.", false); break; }
                to(p, "create", "text", t, "category", str(st, "newCat"));
            }
            case "preply", "sreply", "sgpreply", "sgsreply" -> {
                if (t.isEmpty()) { toast(data, "Write a message first.", false); break; }
                if (a.startsWith("sg")) to(p, a, "id", id, "text", t, "status", str(sq, "status"));
                else to(p, a, "id", id, "text", t);
            }
            case "pclose" -> to(p, "pclose", "id", id);
            case "flt" -> {
                if (List.of("open", "progress", "closed", "mineOnly", "unclaimed").contains(v)) { q.addProperty(v, !bool(q, v)); q.addProperty("page", 0); queueReq(p, st); }
            }
            case "cat" -> { q.addProperty("category", v); q.addProperty("page", 0); queueReq(p, st); }
            case "sort" -> { q.addProperty("sort", v); q.addProperty("page", 0); queueReq(p, st); }
            case "qtext" -> { q.addProperty("text", t); q.addProperty("page", 0); queueReq(p, st); }
            case "pg" -> { q.addProperty("page", Math.max(0, num(q, "page") + (v.equals("-1") ? -1 : 1))); queueReq(p, st); }
            case "openticket" -> to(p, "ticket", "id", id);
            case "claim", "release", "sclose" -> to(p, a, "id", id);
            case "urgent" -> to(p, "urgent", "id", id, "on", v.equals("true"));
            case "tpto" -> {
                if (d != null && str(d, "view").equals("ticket")) to(p, "tpto", "name", v, "id", num(obj(d, "ticket"), "id"));
                else to(p, "tpto", "name", v);
            }
            case "invsee", "endersee" -> to(p, a, "name", v);
            case "setcat" -> to(p, "setcat", "id", id, "category", v);
            case "ptype" -> st.addProperty("ptype", v);
            case "history" -> to(p, "player", "name", v);
            case "lookup" -> to(p, "player", "name", t);
            case "rep" -> { st.addProperty("reportsClosed", v.equals("true")); to(p, "reports", "closed", v.equals("true")); }
            case "rclaim", "rclose" -> to(p, a, "id", id, "closed", bool(st, "reportsClosed"));
            case "lift" -> to(p, "lift", "type", v, "name", n);
            case "punish" -> to(p, "punish", "type", v, "name", n, "reason", t, "duration", dur.trim());
            case "logk" -> { st.addProperty("logKind", v); to(p, "logs", "kind", v); }
            case "newsug" -> st.addProperty("newSug", true);
            case "cancelsug" -> st.addProperty("newSug", false);
            case "selsug" -> { st.addProperty("newSug", false); to(p, "psug", "id", id); }
            case "sugcreate" -> {
                if (t.length() < 5) { toast(data, "Please describe your idea in a little more detail.", false); break; }
                to(p, "screate", "title", n.trim(), "text", t);
            }
            case "opensug" -> to(p, "sug", "id", id, "status", str(sq, "status"));
            case "sugfilter" -> { sq.addProperty("status", v); sq.addProperty("page", 0); sugsReq(p, st); }
            case "sugtext" -> { sq.addProperty("text", t); sq.addProperty("page", 0); sugsReq(p, st); }
            case "sugpg" -> { sq.addProperty("page", Math.max(0, num(sq, "page") + (v.equals("-1") ? -1 : 1))); sugsReq(p, st); }
            case "sugset" -> to(p, "sgstatus", "id", id, "set", v, "status", str(sq, "status"));
            case "pickname" -> {
                JsonObject lo = o(), vals = o();
                vals.addProperty("repname", v);
                st.addProperty("localSeq", num(st, "localSeq") + 1);
                lo.addProperty("seq", num(st, "localSeq"));
                lo.add("vals", vals);
                st.add("_lo", lo);
            }
            case "repcreate" -> {
                if (n.trim().isEmpty()) { toast(data, "Type the name of the player first.", false); break; }
                if (t.length() < 3) { toast(data, "Please say what happened.", false); break; }
                to(p, "rcreate", "name", n.trim(), "reason", t);
            }
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------ view model (field names match tools/gen_ui_cases.py; colours from cases/style.css)

    private static final String GREEN = "#2f7d3a", AMBER = "#b87500", RED = "#b3261e", GRAY = "#6b6b6b", BLUE = "#2b5fa8", PURPLE = "#6b3fa0";
    private static final String INK = "#2b2118", INK2 = "#5b4310", BTN = "#3a2a17", CREAM = "#f3e6c8", BROWN = "#7a5f2e", MANILA = "#e7c97a", EDGE = "#8a6a28";
    private static final double[] ROT = {-1.2, .8, -.6, .9, -.8, 1};

    /** statusInfo(): .s-open green, .s-prog amber, .s-urg red, .s-closed gray. */
    private static String[] statusInfo(JsonObject t) {
        if (str(t, "status").equals("CLOSED")) return new String[]{"CLOSED", GRAY};
        if (bool(t, "urgent")) return new String[]{"URGENT", RED};
        if (str(t, "status").equals("IN_PROGRESS")) return new String[]{"IN PROGRESS", AMBER};
        return new String[]{"OPEN", GREEN};
    }

    /** .stg.new blue, .con amber, .plan purple, .done green, .dec gray. */
    private static String sugColor(String s) {
        return switch (s) { case "CONSIDERING" -> AMBER; case "PLANNED" -> PURPLE; case "DONE" -> GREEN; case "DECLINED" -> GRAY; default -> BLUE; };
    }

    private static String sugKey(String s) {
        return switch (s) { case "CONSIDERING" -> "con"; case "PLANNED" -> "plan"; case "DONE" -> "done"; case "DECLINED" -> "dec"; default -> "new"; };
    }

    /** .cat-cheating #b3261e, griefing #b25a00, item_loss #1f5aa6, bug #6b3fa0, general/other #55606b. */
    private static String catColor(String id) {
        return switch (id) { case "cheating" -> "#b3261e"; case "griefing" -> "#b25a00"; case "item_loss" -> "#1f5aa6"; case "bug" -> "#6b3fa0"; default -> "#55606b"; };
    }

    /** .chip / .chip.on */
    private static JsonObject chip(String k, String label, boolean on) {
        JsonObject c = o();
        c.addProperty("k", k);
        c.addProperty("label", label);
        c.addProperty("bg", on ? BTN : "#00000000");
        c.addProperty("hover", on ? BTN : "#00000012");
        c.addProperty("border", on ? BTN : BROWN);
        c.addProperty("color", on ? CREAM : INK2);
        return c;
    }

    /** .tab (.on, .k-help / .k-sug / .k-rep give the active tab a 5px coloured top border). */
    private static JsonObject tab(String k, String label, boolean on, long n, String kind) {
        JsonObject c = o();
        c.addProperty("k", k);
        c.addProperty("label", label);
        c.addProperty("on", on);
        c.addProperty("n", String.valueOf(n));
        c.addProperty("hasN", n > 0);
        String col = switch (kind) { case "help" -> GREEN; case "sug" -> BLUE; case "rep" -> RED; default -> ""; };
        c.addProperty("btop", col.isEmpty() ? "2," + BROWN : "5," + col);
        return c;
    }

    private static JsonObject meter(String label, String val, boolean hot) {
        JsonObject m = o();
        m.addProperty("label", label);
        m.addProperty("val", val);
        m.addProperty("color", hot ? "#ffd24a" : CREAM);
        return m;
    }

    private static final String[] VIEWS = {"vMine", "vQueue", "vTicket", "vReports", "vSugs", "vSug", "vPsug", "vPrep", "vPunish", "vLogs"};

    private static JsonObject build(JsonObject d, JsonObject st) {
        JsonObject v = o();
        String view = str(d, "view").isEmpty() ? "mine" : str(d, "view");
        boolean staff = bool(d, "staff");
        JsonObject c = obj(d, "counts") == null ? o() : obj(d, "counts");
        String active = switch (view) { case "ticket" -> "queue"; case "sug" -> "sugs"; default -> view; };
        JsonArray tabs = new JsonArray(), meters = new JsonArray();
        if (staff) {
            tabs.add(tab("queue", "HELP TICKETS", active.equals("queue"), num(c, "open"), "help"));
            tabs.add(tab("sugs", "SUGGESTIONS", active.equals("sugs"), num(c, "sugs"), "sug"));
            tabs.add(tab("reports", "REPORTS", active.equals("reports"), num(c, "reports"), "rep"));
            tabs.add(tab("punish", "PUNISHMENTS", active.equals("punish"), 0, ""));
            if (bool(d, "canLogs")) tabs.add(tab("logs", "LOGS", active.equals("logs"), 0, ""));
            tabs.add(tab("mine", "MY TICKETS", active.equals("mine"), 0, ""));
            meters.add(meter("HELP OPEN", String.valueOf(num(c, "open")), true));
            meters.add(meter("NEW IDEAS", String.valueOf(num(c, "sugs")), false));
            meters.add(meter("REPORTS", String.valueOf(num(c, "reports")), true));
            meters.add(meter("STAFF ON DUTY", String.valueOf(num(c, "staffOnline")), false));
        } else {
            tabs.add(tab("mine", "HELP TICKETS", active.equals("mine"), num(c, "myHelpUnread"), "help"));
            tabs.add(tab("psug", "SUGGESTIONS", active.equals("psug"), num(c, "mySugUnread"), "sug"));
            if (!(d.has("canReport") && !bool(d, "canReport"))) tabs.add(tab("prep", "REPORTS", active.equals("prep"), num(c, "myReportsLive"), "rep"));
            meters.add(meter("YOUR OPEN HELP TICKETS", (has(d, "openCount") ? String.valueOf(num(d, "openCount")) : "-") + (num(d, "maxOpen") > 0 ? " / " + num(d, "maxOpen") : ""), false));
        }
        v.add("tabs", tabs);
        v.add("meters", meters);
        v.addProperty("v", view + (bool(st, "newMode") ? "+n" : "") + (bool(st, "newSug") ? "+s" : ""));
        v.addProperty("title", up(str(d, "title").isEmpty() ? "Regions Cases" : str(d, "title")));
        v.addProperty("connecting", false);
        for (String k : VIEWS) v.addProperty(k, false);
        v.addProperty("pager", false);
        v.addProperty("back", false);
        v.addProperty("backLabel", "");
        v.addProperty("backTab", "");
        boolean stamp = num(st, "stampUntil") > System.currentTimeMillis();
        v.addProperty("stamp", stamp);
        v.addProperty("stampText", stamp ? str(st, "stampText") : "");
        v.add("th", threadNone());
        switch (view) {
            case "queue" -> queue(v, d, st, c);
            case "ticket" -> ticket(v, d);
            case "reports" -> reports(v, d, c);
            case "sugs" -> sugs(v, d, st, c);
            case "sug" -> sug(v, d);
            case "psug" -> mySug(v, d, st);
            case "prep" -> myRep(v, d);
            case "punish" -> punish(v, d, st);
            case "logs" -> logs(v, d);
            default -> mine(v, d, st);
        }
        return v;
    }

    private static JsonObject connecting() {
        JsonObject v = o();
        v.addProperty("title", "REGIONS CASES");
        v.addProperty("sub", "");
        v.addProperty("v", "connecting");
        v.addProperty("connecting", true);
        for (String k : VIEWS) v.addProperty(k, false);
        v.addProperty("pager", false);
        v.addProperty("back", false);
        v.addProperty("stamp", false);
        v.add("tabs", new JsonArray());
        v.add("meters", new JsonArray());
        v.add("th", threadNone());
        return v;
    }

    private static JsonObject threadNone() {
        JsonObject t = o();
        t.addProperty("id", 0);
        t.addProperty("head", "");
        t.addProperty("sla", "");
        t.addProperty("hasSla", false);
        t.addProperty("canWrite", false);
        t.addProperty("closedText", "");
        t.addProperty("act", "preply");
        t.addProperty("tag", "");
        t.addProperty("tagColor", INK);
        t.add("msgs", new JsonArray());
        return t;
    }

    /** ticketPaper / sugPaper: the messages (newest 60) as .msg.sys / .msg.st / .msg.pl. */
    private static JsonObject thread(JsonObject t, long id, String head, String sla, boolean canWrite, String closedText, String act) {
        JsonObject th = threadNone();
        th.addProperty("id", id);
        th.addProperty("head", head);
        th.addProperty("sla", sla);
        th.addProperty("hasSla", !sla.isEmpty());
        th.addProperty("canWrite", canWrite);
        th.addProperty("closedText", closedText);
        th.addProperty("act", act);
        JsonArray all = arr(t, "messages"), out = new JsonArray();
        for (int i = Math.max(0, all.size() - 60); i < all.size(); i++) {
            JsonObject m = all.get(i).getAsJsonObject(), r = o();
            boolean sys = bool(m, "system"), stf = !sys && bool(m, "staff");
            r.addProperty("sys", sys);
            r.addProperty("staff", stf);
            r.addProperty("pl", !sys && !stf);
            r.addProperty("head", sys ? "" : (stf ? "Staff " : "") + str(m, "name") + " - " + ago(num(m, "at")));
            r.addProperty("text", sys ? str(m, "text") + " - " + when(num(m, "at")) : str(m, "text"));
            out.add(r);
        }
        th.add("msgs", out);
        return th;
    }

    /** slaBox(): only on the player's own ticket. **x** = <b>. */
    private static String sla(JsonObject d, JsonObject t) {
        if (str(t, "status").equals("CLOSED")) return "This ticket is **closed**" + (has(t, "closedBy") && !str(t, "closedBy").isEmpty() ? " (by " + str(t, "closedBy") + ")" : "") + ".";
        for (JsonElement e : arr(t, "messages")) { JsonObject m = e.getAsJsonObject(); if (bool(m, "staff") && !bool(m, "system")) return ""; }
        long created = num(t, "createdAt"), min = num(d, "replyMin"), max = num(d, "replyMax");
        return "Opened **" + when(created) + "**. Our staff will get to your ticket within **" + min + "-" + max + " hours** of that time (between **"
                + when(created + min * 3600000L) + "** and **" + when(created + max * 3600000L) + "**).";
    }

    private static void pager(JsonObject v, long page, long pages, String act) {
        v.addProperty("pager", true);
        v.addProperty("pgText", "PAGE " + (page + 1) + " OF " + pages);
        v.addProperty("pgPrev", page > 0);
        v.addProperty("pgNext", page + 1 < pages);
        v.addProperty("pgAct", act);
    }

    private static void back(JsonObject v, String label, String tab) {
        v.addProperty("back", true);
        v.addProperty("backLabel", label);
        v.addProperty("backTab", tab);
    }

    /** .mini card colours: manila, .on #fff0b8 + btn border + shadow; suggestion notes use their status colours. */
    private static void miniLook(JsonObject r, boolean on, String bg, String border) {
        r.addProperty("bg", on ? (bg.equals(MANILA) ? "#fff0b8" : bg) : bg);
        r.addProperty("hover", bg.equals(MANILA) ? (on ? "#fff0b8" : "#f0d589") : bg);
        r.addProperty("border", on ? BTN : border);
        r.addProperty("shadow", on ? "3,3,#00000040" : "0,0,#00000000");
    }

    // ---- mine (vMine)
    private static void mine(JsonObject v, JsonObject d, JsonObject st) {
        v.addProperty("vMine", true);
        JsonArray tickets = arr(d, "tickets");
        JsonObject sel = obj(d, "selected");
        boolean newMode = bool(st, "newMode") || sel == null;
        JsonArray list = new JsonArray();
        for (JsonElement e : tickets) {
            JsonObject t = e.getAsJsonObject(), r = o();
            String[] si = statusInfo(t);
            r.addProperty("id", num(t, "id"));
            r.addProperty("no", "#" + num(t, "id"));
            r.addProperty("tag", si[0]);
            r.addProperty("tagColor", si[1]);
            r.addProperty("sub", str(t, "categoryName") + " - " + ago(num(t, "createdAt")) + (bool(t, "unread") ? " - **NEW REPLY**" : ""));
            miniLook(r, !newMode && sel != null && num(sel, "id") == num(t, "id"), MANILA, EDGE);
            list.add(r);
        }
        v.add("list", list);
        v.addProperty("noList", tickets.isEmpty());
        v.addProperty("canNew", !(num(d, "maxOpen") > 0 && num(d, "openCount") >= num(d, "maxOpen")));
        v.addProperty("showNew", newMode);
        v.addProperty("sub", newMode ? "OPEN A NEW CASE" : "CASE #" + num(sel, "id") + " - " + str(sel, "categoryName"));
        JsonObject f = o();
        f.addProperty("sla", "Tell us what happened: what, where and when. Our staff will get to your ticket within **" + num(d, "replyMin") + "-" + num(d, "replyMax")
                + " hours** of the time you open it.");
        JsonArray cats = new JsonArray();
        String cur = str(st, "newCat");
        cats.add(chip("", "AUTO", cur.isEmpty()));
        for (JsonElement e : arr(d, "categories")) { JsonObject k = e.getAsJsonObject(); cats.add(chip(str(k, "id"), up(str(k, "name")), cur.equals(str(k, "id")))); }
        f.add("cats", cats);
        JsonObject det = obj(st, "detected");
        f.addProperty("detected", det == null ? "Type your message and I will pick a category." : "Category: **" + up(str(det, "name")) + "**");
        f.addProperty("canCancel", !tickets.isEmpty());
        v.add("nf", f);
        boolean closed = sel != null && str(sel, "status").equals("CLOSED");
        if (!newMode) v.add("th", thread(sel, num(sel, "id"), "CORRESPONDENCE - CASE #" + num(sel, "id"), sla(d, sel), !closed, "This ticket is closed.", "preply"));
        v.addProperty("canClose", !newMode && !closed);
        v.addProperty("selId", sel == null ? 0 : num(sel, "id"));
    }

    // ---- queue (vQueue): folders on a 3-column grid
    private static void queue(JsonObject v, JsonObject d, JsonObject st, JsonObject c) {
        v.addProperty("vQueue", true);
        JsonObject q = st.getAsJsonObject("q");
        JsonArray flt = new JsonArray(), flt2 = new JsonArray();
        flt.add(chip("open", "OPEN", bool(q, "open")));
        flt.add(chip("progress", "IN PROGRESS", bool(q, "progress")));
        flt.add(chip("closed", "CLOSED", bool(q, "closed")));
        flt2.add(chip("mineOnly", "MINE", bool(q, "mineOnly")));
        flt2.add(chip("unclaimed", "UNCLAIMED", bool(q, "unclaimed")));
        v.add("flt", flt);
        v.add("flt2", flt2);
        JsonArray cats = new JsonArray();
        cats.add(chip("", "ALL", str(q, "category").isEmpty()));
        for (JsonElement e : arr(d, "categories")) { JsonObject k = e.getAsJsonObject(); cats.add(chip(str(k, "id"), up(str(k, "name")), str(q, "category").equals(str(k, "id")))); }
        v.add("cats", cats);
        JsonArray sorts = new JsonArray();
        sorts.add(chip("oldest", "OLDEST", str(q, "sort").equals("oldest")));
        sorts.add(chip("newest", "NEWEST", str(q, "sort").equals("newest")));
        sorts.add(chip("urgent", "URGENT FIRST", str(q, "sort").equals("urgent")));
        v.add("sorts", sorts);
        v.addProperty("total", num(d, "total") + " case" + (num(d, "total") == 1 ? "" : "s"));
        JsonArray cards = new JsonArray();
        int i = 0;
        for (JsonElement e : arr(d, "items")) {
            if (i >= 18) break;
            JsonObject t = e.getAsJsonObject(), r = o();
            String[] si = statusInfo(t);
            String who = str(t, "status").equals("IN_PROGRESS") ? (bool(t, "mine") ? "yours" : str(t, "assignee")) : "";
            int col = i % 3, rw = i / 3;
            r.addProperty("x", col * 460 - 6);                // the art has the folder at (6, 24)
            r.addProperty("y", rw * 266 + 16 - 24);
            r.addProperty("rot", ROT[i % 6]);
            r.addProperty("delay", i * 55);
            r.addProperty("stampDelay", i * 55 + 240);
            r.addProperty("id", num(t, "id"));
            r.addProperty("no", "CASE #" + num(t, "id"));
            r.addProperty("stamp", si[0]);
            r.addProperty("stampColor", si[1]);
            r.addProperty("who", str(t, "owner"));
            r.addProperty("cat", up(str(t, "categoryName")));
            r.addProperty("catColor", catColor(str(t, "category")));
            r.addProperty("preview", str(t, "preview"));
            r.addProperty("foot", ago(num(t, "createdAt")) + (bool(t, "waiting") && !str(t, "status").equals("CLOSED") ? " **- needs reply**" : "") + (who.isEmpty() ? "" : " - " + who));
            cards.add(r);
            i++;
        }
        v.add("cards", cards);
        v.addProperty("empty", i == 0);
        pager(v, num(d, "page"), Math.max(1, num(d, "pages")), "pg");
        v.addProperty("sub", num(c, "open") + " open - " + num(c, "progress") + " in progress - you hold " + num(c, "mine") + " of " + num(d, "maxActive"));
    }

    // ---- one ticket (vTicket)
    private static void ticket(JsonObject v, JsonObject d) {
        v.addProperty("vTicket", true);
        JsonObject t = obj(d, "ticket"), p = obj(d, "player") == null ? o() : obj(d, "player");
        boolean closed = str(t, "status").equals("CLOSED");
        String[] si = statusInfo(t);
        v.add("th", thread(t, num(t, "id"), "CORRESPONDENCE - CASE #" + num(t, "id"), "", !closed, "This ticket is closed.", "sreply"));
        JsonObject cl = o();
        boolean online = bool(p, "online");
        cl.addProperty("name", str(p, "name"));
        cl.addProperty("online", online ? "ONLINE" : "OFFLINE");
        cl.addProperty("onlineColor", online ? GREEN : "#8a7a52");
        cl.addProperty("kv", "**Opened at** " + str(p, "openedAt") + "\n" + (online ? "**Now at** " + str(p, "world") + " " + num(p, "x") + ", " + num(p, "y") + ", " + num(p, "z") + "\n" : "")
                + "**Past cases** " + num(p, "pastTickets") + "  **Warnings** " + num(p, "warnings") + "  **Mutes** " + num(p, "mutes"));
        cl.addProperty("stamp", si[0]);
        cl.addProperty("stampColor", si[1]);
        boolean held = has(t, "assignee") && !str(t, "assignee").isEmpty();
        cl.addProperty("holder", "CATEGORY (CLICK TO CHANGE) - HELD BY: " + up(held ? (bool(t, "mine") ? "You" : str(t, "assignee")) : "Nobody yet"));
        cl.addProperty("id", num(t, "id"));
        cl.addProperty("closed", closed);
        cl.addProperty("mine", bool(t, "mine"));
        cl.addProperty("claimLabel", held ? "TAKE OVER" : "CLAIM");
        cl.addProperty("urgent", bool(t, "urgent"));
        cl.addProperty("urgentLabel", bool(t, "urgent") ? "NOT URGENT" : "SET URGENT");
        cl.addProperty("urgentVal", bool(t, "urgent") ? "false" : "true");
        cl.addProperty("canTp", bool(d, "canTp"));
        cl.addProperty("tpLabel", online ? "TELEPORT TO" : "GO TO SPOT");
        cl.addProperty("canInv", bool(d, "canInvsee") && online);
        cl.addProperty("canEnder", bool(d, "canEnder") && online);
        v.add("cl", cl);
        JsonArray cats = new JsonArray();
        for (JsonElement e : arr(d, "categories")) { JsonObject k = e.getAsJsonObject(); cats.add(chip(str(k, "id"), up(str(k, "name")), str(k, "id").equals(str(t, "category")))); }
        v.add("tcats", cats);
        v.addProperty("sub", "CASE #" + num(t, "id") + " - " + str(t, "owner") + " - " + str(t, "categoryName"));
        back(v, "< BACK TO PILE", "queue");
    }

    // ---- reports (vReports)
    private static void reports(JsonObject v, JsonObject d, JsonObject c) {
        v.addProperty("vReports", true);
        JsonArray rows = new JsonArray();
        for (JsonElement e : arr(d, "reports")) {
            if (rows.size() >= 40) break;
            JsonObject r = e.getAsJsonObject(), o = o();
            String s = str(r, "status");
            o.addProperty("id", num(r, "id"));
            o.addProperty("tag", s.equals("CLOSED") ? "CLOSED" : s.equals("CLAIMED") ? up(str(r, "handler")) : "OPEN");
            o.addProperty("tagColor", s.equals("CLOSED") ? GRAY : s.equals("CLAIMED") ? AMBER : GREEN);
            o.addProperty("target", str(r, "target"));
            o.addProperty("reporter", str(r, "reporter"));
            o.addProperty("reason", str(r, "reason"));
            o.addProperty("note", has(r, "note") && !str(r, "note").isEmpty() ? "Note: " + str(r, "note") : "");
            o.addProperty("ago", ago(num(r, "createdAt")));
            o.addProperty("where", str(r, "where"));
            o.addProperty("canClaim", s.equals("OPEN"));
            o.addProperty("canClose", !s.equals("CLOSED"));
            o.addProperty("canTp", bool(d, "canTp") && bool(r, "targetOnline"));
            rows.add(o);
        }
        v.add("rows", rows);
        v.addProperty("empty", rows.isEmpty());
        v.addProperty("emptyText", "No reports " + (bool(d, "showClosed") ? "" : "waiting."));
        JsonArray f = new JsonArray();
        f.add(chip("false", "OPEN", !bool(d, "showClosed")));
        f.add(chip("true", "CLOSED", bool(d, "showClosed")));
        v.add("flt", f);
        v.addProperty("sub", num(c, "reports") + " open report" + (num(c, "reports") == 1 ? "" : "s"));
    }

    // ---- suggestions board (vSugs): pinned notes
    private static void sugs(JsonObject v, JsonObject d, JsonObject st, JsonObject c) {
        v.addProperty("vSugs", true);
        JsonObject q = st.getAsJsonObject("sq");
        JsonArray chips = new JsonArray();
        chips.add(chip("", "ALL", str(q, "status").isEmpty()));
        for (String[] s : SUG_STATUS) chips.add(chip(s[0], s[1], str(q, "status").equals(s[0])));
        v.add("flt", chips);
        v.addProperty("total", num(d, "total") + " suggestion" + (num(d, "total") == 1 ? "" : "s"));
        JsonArray cards = new JsonArray();
        int i = 0;
        for (JsonElement e : arr(d, "items")) {
            if (i >= 18) break;
            JsonObject s = e.getAsJsonObject(), r = o();
            int col = i % 3, rw = i / 3;
            r.addProperty("x", col * 460 - 6);
            r.addProperty("y", rw * 280 + 16 - 6);
            r.addProperty("rot", ROT[i % 6]);
            r.addProperty("delay", i * 55);
            r.addProperty("id", num(s, "id"));
            r.addProperty("no", "IDEA #" + num(s, "id"));
            r.addProperty("title", str(s, "title"));
            r.addProperty("preview", str(s, "preview"));
            r.addProperty("foot", str(s, "owner") + " - " + ago(num(s, "createdAt")));
            r.addProperty("tag", up(str(s, "statusName")));
            r.addProperty("tagColor", sugColor(str(s, "status")));
            r.addProperty("tex", "tex:cases/note_" + sugKey(str(s, "status")) + ".png");
            cards.add(r);
            i++;
        }
        v.add("cards", cards);
        v.addProperty("empty", i == 0);
        pager(v, num(d, "page"), Math.max(1, num(d, "pages")), "sugpg");
        v.addProperty("sub", num(c, "sugs") + " new - " + num(c, "reports") + " open reports - " + num(c, "open") + " help tickets open");
    }

    // ---- one suggestion (vSug)
    private static void sug(JsonObject v, JsonObject d) {
        v.addProperty("vSug", true);
        JsonObject s = obj(d, "suggestion"), p = obj(d, "player") == null ? o() : obj(d, "player");
        JsonObject th = thread(s, num(s, "id"), up(str(s, "title")) + " - IDEA #" + num(s, "id"), "", true, "", "sgsreply");
        th.addProperty("tag", up(str(s, "statusName")));
        th.addProperty("tagColor", sugColor(str(s, "status")));
        v.add("th", th);
        JsonObject cl = o();
        cl.addProperty("name", str(p, "name"));
        cl.addProperty("online", bool(p, "online") ? "ONLINE" : "OFFLINE");
        cl.addProperty("onlineColor", bool(p, "online") ? GREEN : "#8a7a52");
        cl.addProperty("kv", "**Sent** " + when(num(s, "createdAt")) + "\n**Other ideas by them** " + num(p, "otherSuggestions")
                + (has(s, "handledBy") && !str(s, "handledBy").isEmpty() ? "\n**Last decision by** " + str(s, "handledBy") : ""));
        cl.addProperty("id", num(s, "id"));
        v.add("cl", cl);
        JsonArray chips = new JsonArray();
        for (String[] x : SUG_STATUS) chips.add(chip(x[0], x[1], str(s, "status").equals(x[0])));
        v.add("scats", chips);
        v.addProperty("sub", "IDEA #" + num(s, "id") + " - " + str(s, "owner") + " - " + str(s, "statusName"));
        back(v, "< BACK TO BOARD", "sugs");
    }

    // ---- my suggestions (vMySug)
    private static void mySug(JsonObject v, JsonObject d, JsonObject st) {
        v.addProperty("vPsug", true);
        JsonArray list = arr(d, "suggestions");
        JsonObject sel = obj(d, "selected");
        boolean showNew = bool(st, "newSug") || sel == null;
        JsonArray out = new JsonArray();
        for (JsonElement e : list) {
            JsonObject s = e.getAsJsonObject(), r = o();
            String k = sugKey(str(s, "status"));
            String bg = switch (k) { case "con" -> "#cfe4f7"; case "plan" -> "#e3d4f5"; case "done" -> "#d4efd2"; case "dec" -> "#e4e4e4"; default -> "#fff1a0"; };
            String bd = switch (k) { case "con" -> "#8fb3d6"; case "plan" -> "#a58cc8"; case "done" -> "#86b684"; case "dec" -> "#aaaaaa"; default -> "#d3bd6a"; };
            r.addProperty("id", num(s, "id"));
            r.addProperty("no", str(s, "title"));
            r.addProperty("tag", up(str(s, "statusName")));
            r.addProperty("tagColor", sugColor(str(s, "status")));
            r.addProperty("sub", "#" + num(s, "id") + " - " + ago(num(s, "createdAt")) + (bool(s, "unread") ? " - **NEW REPLY**" : ""));
            miniLook(r, !showNew && sel != null && num(sel, "id") == num(s, "id"), bg, bd);
            out.add(r);
        }
        v.add("list", out);
        v.addProperty("noList", list.isEmpty());
        v.addProperty("showNew", showNew);
        v.addProperty("canCancel", !list.isEmpty());
        v.addProperty("sub", showNew ? "SHARE AN IDEA" : "IDEA #" + num(sel, "id") + " - " + str(sel, "statusName"));
        if (!showNew) {
            JsonObject th = thread(sel, num(sel, "id"), up(str(sel, "title")) + " - IDEA #" + num(sel, "id"),
                    "Staff will read your idea and set a status: **Considering**, **Planned**, **Done** or **Declined**. You get a message in chat when it changes.",
                    bool(sel, "open"), "Staff marked this **" + up(str(sel, "statusName")) + "**, so it is closed for new messages.", "sgpreply");
            th.addProperty("tag", up(str(sel, "statusName")));
            th.addProperty("tagColor", sugColor(str(sel, "status")));
            v.add("th", th);
        }
    }

    // ---- my reports (vMyRep)
    private static void myRep(JsonObject v, JsonObject d) {
        v.addProperty("vPrep", true);
        JsonArray rows = new JsonArray();
        for (JsonElement e : arr(d, "myReports")) {
            if (rows.size() >= 30) break;
            JsonObject r = e.getAsJsonObject(), o = o();
            String s = str(r, "status");
            o.addProperty("no", "REPORT #" + num(r, "id") + " - " + up(ago(num(r, "createdAt"))));
            o.addProperty("target", str(r, "target"));
            o.addProperty("reason", str(r, "reason"));
            o.addProperty("tag", s.equals("CLOSED") ? "CLOSED" : s.equals("CLAIMED") ? "STAFF LOOKING" : "OPEN");
            o.addProperty("tagColor", s.equals("CLOSED") ? GRAY : s.equals("CLAIMED") ? AMBER : GREEN);
            rows.add(o);
        }
        v.add("rows", rows);
        v.addProperty("empty", rows.isEmpty());
        JsonArray online = new JsonArray();
        for (JsonElement e : arr(d, "online")) { if (online.size() >= 10) break; JsonObject o = o(); o.addProperty("k", e.getAsString()); online.add(o); }
        v.add("online", online);
        v.addProperty("hasOnline", !online.isEmpty());
        v.addProperty("sub", "REPORT A PLAYER TO THE STAFF");
    }

    // ---- punishments (vPunish)
    private static void punish(JsonObject v, JsonObject d, JsonObject st) {
        v.addProperty("vPunish", true);
        JsonObject p = obj(d, "player");
        v.addProperty("hasPlayer", p != null);
        v.addProperty("sub", "PLAYER RECORDS");
        JsonArray matches = new JsonArray();
        if (p == null) for (JsonElement e : arr(d, "matches")) { JsonObject o = o(); o.addProperty("k", e.getAsString()); matches.add(o); }
        v.add("matches", matches);
        v.addProperty("hasMatches", p == null && !matches.isEmpty());
        v.addProperty("showEmpty", p == null && matches.isEmpty());
        v.addProperty("emptyText", has(d, "query") && !str(d, "query").isEmpty() ? "No player called \"" + str(d, "query") + "\" has joined this server."
                : "Type a player name to see their record, then warn, mute, kick or ban them.");
        if (p == null) return;
        JsonObject pl = o();
        pl.addProperty("name", str(p, "name"));
        pl.addProperty("initial", str(p, "name").isEmpty() ? "?" : up(str(p, "name").substring(0, 1)));
        pl.addProperty("line", (bool(p, "online") ? "**ONLINE**" : "last seen " + ago(num(p, "lastSeen"))) + (num(p, "firstJoin") > 0 ? " - first joined " + when(num(p, "firstJoin")) : ""));
        pl.addProperty("ban", bool(p, "banned") ? "BANNED - " + str(p, "banInfo") : "");
        pl.addProperty("mute", bool(p, "muted") ? "MUTED - " + str(p, "muteInfo") : "");
        pl.addProperty("frozen", bool(p, "frozen"));
        for (String k : new String[]{"warnings", "mutes", "bans", "kicks"}) pl.addProperty(k, String.valueOf(num(p, k)));
        v.add("pl", pl);
        JsonArray hist = new JsonArray();
        for (JsonElement e : arr(p, "history")) {
            if (hist.size() >= 60) break;
            JsonObject h = e.getAsJsonObject(), r = o();
            String type = str(h, "type");
            String len = num(h, "until") > 0 ? " until " + when(num(h, "until")) : (type.equals("BAN") || type.equals("MUTE") ? " (permanent)" : "");
            r.addProperty("type", type);
            r.addProperty("color", type.equals("BAN") ? RED : type.equals("MUTE") || type.equals("WARN") ? AMBER : GRAY);
            r.addProperty("reason", str(h, "reason"));
            r.addProperty("meta", str(h, "by") + " - " + when(num(h, "at")) + len + (bool(h, "lifted") ? " - lifted by " + str(h, "liftedBy") : ""));
            r.addProperty("canLift", bool(h, "inForce"));
            r.addProperty("liftK", type.equals("BAN") ? "ban" : "mute");
            hist.add(r);
        }
        v.add("hist", hist);
        v.addProperty("clean", hist.isEmpty());
        String ptype = str(st, "ptype"), first = "";
        boolean found = false;
        for (String[] t : PTYPES) if (bool(d, t[2])) { if (first.isEmpty()) first = t[0]; if (t[0].equals(ptype)) found = true; }
        if (!found) ptype = first;
        JsonArray types = new JsonArray();
        for (String[] t : PTYPES) if (bool(d, t[2])) types.add(chip(t[0], up(t[1]), t[0].equals(ptype)));
        v.add("ptypes", types);
        v.addProperty("ptype", ptype);
        v.addProperty("hasType", !ptype.isEmpty());
        v.addProperty("temp", ptype.startsWith("temp"));
        v.addProperty("applyLabel", "APPLY " + up(ptype));
    }

    // ---- logs (vLogs)
    private static void logs(JsonObject v, JsonObject d) {
        v.addProperty("vLogs", true);
        String[][] kinds = {{"", "ALL"}, {"STAFF", "STAFF"}, {"TICKET", "TICKETS"}, {"CHAT", "CHAT"}, {"CMD", "COMMANDS"}};
        JsonArray chips = new JsonArray();
        for (String[] k : kinds) chips.add(chip(k[0], k[1], str(d, "kind").equals(k[0])));
        v.add("kinds", chips);
        JsonArray rows = new JsonArray();
        for (JsonElement e : arr(d, "entries")) {
            if (rows.size() >= 100) break;
            JsonObject en = e.getAsJsonObject(), r = o();
            String k = str(en, "kind");
            r.addProperty("time", CLOCK.format(Instant.ofEpochMilli(num(en, "at")).atZone(ZoneId.systemDefault())));
            r.addProperty("kind", k);
            r.addProperty("color", k.equals("STAFF") ? RED : k.equals("TICKET") ? GREEN : k.equals("CHAT") ? GRAY : AMBER);
            r.addProperty("actor", str(en, "actor"));
            r.addProperty("text", str(en, "text"));
            rows.add(r);
        }
        v.add("rows", rows);
        v.addProperty("empty", rows.isEmpty());
        v.addProperty("sub", "NEWEST ENTRIES FIRST - FULL LOGS ARE FILES IN CONFIG/SNOBBLE_STAFF/AUDIT");
    }
}
