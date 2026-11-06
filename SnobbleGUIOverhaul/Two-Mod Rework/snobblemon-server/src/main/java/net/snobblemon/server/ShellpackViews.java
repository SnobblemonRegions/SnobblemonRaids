package net.snobblemon.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The Shellpack screen. The Shellpack mod owns every rule (stack limits, nesting, capacity, who owns what); this view only draws the mod's
 * "state" event as two grids, remembers which stack the player picked up (click a stack, then click where it goes; right-click takes half and
 * places one; shift-click moves a whole stack across) and passes the resulting move on to the mod, which checks it again.
 */
final class ShellpackViews {
    private ShellpackViews() {}

    static final String PAGE = "shellpack";
    private static final String CHANNEL = "shellpack";
    private static final String[][] MODES = {{"name", "N", "Name"}, {"count", "C", "Amount"}, {"type", "T", "Item type"}};
    private static final String[] TIER = {"basic", "small", "medium", "large", "gigantic", "colossal"};
    private static final Map<UUID, JsonObject> STATE = new ConcurrentHashMap<>();
    private static final AtomicLong RID = new AtomicLong();

    static void register() {
        SnobbleUi.registerView(PAGE, ShellpackViews::apply);
    }

    private static JsonArray arr(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }
    private static JsonObject obj(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null; }

    private static JsonObject itemAt(JsonObject state, String pane, int i) {
        JsonArray a = arr(state, pane.equals("s") ? "shell" : "inv");
        if (i < 0 || i >= a.size() || !a.get(i).isJsonObject()) return null;
        return a.get(i).getAsJsonObject();
    }

    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : new JsonObject();
        if (!st.has("tab")) { st.addProperty("tab", ""); st.addProperty("mode", 0); }
        switch (event) {
            case "state" -> {
                if (!s.has("shell")) return;
                STATE.put(p.getUUID(), s);
                if (st.has("sel")) {                                                     // the picked-up stack must still be where it was
                    JsonObject sel = st.getAsJsonObject("sel");
                    JsonObject now = itemAt(s, str(sel, "c"), (int) num(sel, "i"));
                    if (now == null || !str(now, "k").equals(str(sel, "k"))) st.remove("sel");
                }
            }
            case "result" -> {
                if (!str(s, "message").isEmpty()) toast(data, str(s, "message"), bool(s, "ok"));
            }
            case "closed" -> {
                SnobbleUi.close(p, PAGE);
                return;
            }
            case "msg" -> {
                JsonObject state = STATE.get(p.getUUID());
                if (state == null) return;
                message(p, state, st, data, s);
            }
            default -> { return; }
        }
        JsonObject state = STATE.get(p.getUUID());
        data.add("st", st);
        if (state != null) data.add("vm", build(state, st));
    }

    private static void toast(JsonObject data, String text, boolean ok) {
        JsonObject t = new JsonObject();
        t.addProperty("text", text);
        t.addProperty("ok", ok);
        data.add("_toast", t);
    }

    private static void send(ServerPlayer p, JsonObject m) {
        m.addProperty("rid", "v" + RID.incrementAndGet());
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static void act(ServerPlayer p, String action) {
        JsonObject m = new JsonObject();
        m.addProperty("action", action);
        send(p, m);
    }

    private static int half(int n) { return Math.max(1, (n + 1) / 2); }

    private static JsonObject ref(String c, int i, String k) {
        JsonObject o = new JsonObject();
        o.addProperty("c", c);
        o.addProperty("i", i);
        o.addProperty("k", k);
        return o;
    }

    private static void message(ServerPlayer p, JsonObject state, JsonObject st, JsonObject data, JsonObject m) {
        switch (str(m, "action")) {
            case "tab" -> st.addProperty("tab", str(st, "tab").equals(str(m, "k")) ? "" : str(m, "k"));
            case "mode" -> st.addProperty("mode", Math.max(0, Math.min(2, (int) num(m, "k"))));
            case "cyclemode" -> st.addProperty("mode", ((int) num(st, "mode") + 1) % 3);
            case "sort" -> { st.remove("sel"); JsonObject o = new JsonObject(); o.addProperty("action", "sort"); o.addProperty("mode", MODES[(int) num(st, "mode")][0]); send(p, o); }
            case "stow" -> act(p, "stow");
            case "withdraw" -> act(p, "withdraw");
            case "upgrade" -> act(p, "upgrade");
            case "drop" -> st.remove("sel");
            case "slot" -> slot(p, state, st, data, str(m, "pane"), (int) num(m, "idx"), (int) num(m, "btn") == 1, (int) num(m, "shift") == 1);
            default -> { }
        }
    }

    private static void slot(ServerPlayer p, JsonObject state, JsonObject st, JsonObject data, String pane, int idx, boolean right, boolean shift) {
        if (!pane.equals("s") && !pane.equals("p")) return;
        JsonObject it = itemAt(state, pane, idx);
        JsonObject sel = st.has("sel") ? st.getAsJsonObject("sel") : null;
        if (shift && !right) {
            st.remove("sel");
            if (it == null) return;
            if (pane.equals("p") && it.has("bad")) { toast(data, str(it, "bad"), false); return; }
            JsonObject o = new JsonObject();
            o.addProperty("action", "quick");
            o.add("from", ref(pane, idx, str(it, "k")));
            send(p, o);
            return;
        }
        if (sel != null) {
            if (str(sel, "c").equals(pane) && (int) num(sel, "i") == idx) { st.remove("sel"); return; }
            JsonObject src = itemAt(state, str(sel, "c"), (int) num(sel, "i"));
            st.remove("sel");
            if (src == null) return;
            int count = right ? 1 : (int) num(sel, "count");
            if (pane.equals("s") && it == null && src.has("bad")) { toast(data, str(src, "bad"), false); return; }
            if (pane.equals("s") && src.has("bad")) { toast(data, str(src, "bad"), false); return; }
            if (str(sel, "c").equals("s") && it != null && it.has("bad") && count == (int) num(src, "n")) { toast(data, str(it, "bad"), false); return; }
            JsonObject o = new JsonObject();
            o.addProperty("action", "move");
            o.add("from", ref(str(sel, "c"), (int) num(sel, "i"), str(src, "k")));
            o.add("to", ref(pane, idx, it == null ? "" : str(it, "k")));
            o.addProperty("count", count);
            send(p, o);
            return;
        }
        if (it != null) {
            JsonObject n = ref(pane, idx, str(it, "k"));
            n.addProperty("count", right ? half((int) num(it, "n")) : (int) num(it, "n"));
            n.addProperty("name", str(it, "name"));
            st.add("sel", n);
        }
    }

    // ------------------------------------------------------------ view model

    private static String itemKey(JsonObject it) {
        String ic = str(it, "ic");
        return ic.isEmpty() ? str(it, "id") : ic;
    }

    private static JsonObject cell(JsonObject it, String pane, int i, boolean over, JsonObject sel) {
        JsonObject c = new JsonObject();
        c.addProperty("pane", pane);
        c.addProperty("idx", i);
        boolean picked = sel != null && str(sel, "c").equals(pane) && (int) num(sel, "i") == i;
        boolean has = it != null;
        c.addProperty("has", has);
        c.addProperty("border", picked ? "#4AD8F6" : over ? "#C4283C" : has && it.has("bad") && pane.equals("p") ? "#7A3A2A" : "#1F6B86");
        c.addProperty("bg", picked ? "#12425A" : over ? "#2A0F16" : "#0A1B27");
        if (!has) return c;
        boolean shell = it.has("shell");
        c.addProperty("shell", shell);
        c.addProperty("art", shell ? "a:shell_" + TIER[(int) Math.max(0, Math.min(5, num(it, "shell")))] : "");
        c.addProperty("item", shell ? "minecraft:air" : itemKey(it));
        int n = (int) num(it, "n");
        c.addProperty("n", n > 1 ? String.valueOf(n) : "");
        boolean dmg = it.has("dmg");
        c.addProperty("dmg", dmg);
        double left = dmg ? 1 - num(it, "dmg") : 1;
        c.addProperty("dpct", Math.max(4, Math.round(left * 100)));
        c.addProperty("dcol", String.format("#%02X%02X%02X", (int) (255 * Math.min(1, 2 * (1 - left))), (int) (255 * Math.min(1, 2 * left)), 40));
        JsonArray t = arr(it, "t");
        for (int k = 0; k < 6; k++) c.addProperty("t" + k, k < t.size() ? t.get(k).getAsString() : "");
        c.addProperty("name", str(it, "name") + (n > 1 ? "  x" + n : ""));
        c.addProperty("warn", it.has("bad") ? str(it, "bad") : over ? "Above this shellpack's capacity: take it out." : "");
        return c;
    }

    private static JsonObject rows(JsonArray rowsOut, JsonObject row) { rowsOut.add(row); return row; }

    private static JsonObject build(JsonObject state, JsonObject st) {
        JsonObject v = new JsonObject();
        JsonObject sel = st.has("sel") ? st.getAsJsonObject("sel") : null;
        int tier = (int) Math.max(0, Math.min(5, num(state, "tier")));
        v.addProperty("title", str(state, "tierName") + " Shellpack");
        v.addProperty("icon", "a:shell_" + TIER[tier]);
        int cols = (int) Math.max(1, Math.min(12, num(state, "cols") == 0 ? 9 : num(state, "cols")));
        int slots = arr(state, "shell").size(), cap = (int) num(state, "capacity");
        JsonArray shellRows = new JsonArray();
        JsonArray cells = null;
        for (int i = 0; i < slots; i++) {
            if (i % cols == 0) { cells = new JsonArray(); JsonObject r = new JsonObject(); r.add("cells", cells); shellRows.add(r); }
            cells.add(cell(itemAt(state, "s", i), "s", i, i >= cap, sel));
        }
        v.add("shellRows", shellRows);
        JsonArray invRows = new JsonArray();
        for (int r = 0; r < 4; r++) {
            JsonArray row = new JsonArray();
            for (int cI = 0; cI < 9; cI++) {
                int idx = r < 3 ? 9 + r * 9 + cI : cI;                     // three storage rows, then the hotbar
                row.add(cell(itemAt(state, "p", idx), "p", idx, false, sel));
            }
            JsonObject ro = new JsonObject();
            ro.add("cells", row);
            ro.addProperty("gap", r == 3 ? 6 : 0);
            invRows.add(ro);
        }
        v.add("invRows", invRows);
        int used = (int) num(state, "used");
        v.addProperty("used", used);
        v.addProperty("slots", slots);
        v.addProperty("pct", slots == 0 ? 0 : Math.round(100.0 * used / slots));
        v.addProperty("meter", used + " / " + slots + " slots  -  " + (slots == 0 ? 0 : Math.round(100.0 * used / slots)) + "% full");
        v.addProperty("held", sel == null ? "" : "Holding " + (num(sel, "count") > 1 ? num(sel, "count") + " x " : "") + str(sel, "name") + "  -  click a slot to put it there, right-click puts one");
        v.addProperty("hasHeld", sel != null);
        int mode = (int) num(st, "mode");
        v.addProperty("modeLetter", MODES[mode][1]);
        // left tabs
        String tab = str(st, "tab");
        for (String k : new String[]{"info", "opt", "up"}) v.addProperty("tab_" + k, tab.equals(k) ? "#4AD8F6" : "#1F6B86");
        v.addProperty("panel", !tab.isEmpty());
        JsonArray lines = new JsonArray();
        JsonArray rowsT = new JsonArray();
        JsonArray cost = new JsonArray();
        v.addProperty("pTitle", "");
        v.addProperty("pOpt", false);
        v.addProperty("pUp", false);
        v.addProperty("canUp", false);
        v.addProperty("upText", "");
        if (tab.equals("info")) {
            v.addProperty("pTitle", str(state, "tierName") + " Shellpack");
            line(lines, "Tier " + (tier + 1) + " of 6  -  " + cap + " slots");
            line(lines, used + " slots in use.");
            line(lines, str(state, "owner").isEmpty() ? "Not bound to anyone yet." : "Bound to " + str(state, "owner") + ".");
            line(lines, "Shellpacks cannot be stored inside shellpacks. Slots marked red are above this shellpack's capacity: you can take their items out, but not put anything in.");
        } else if (tab.equals("opt")) {
            v.addProperty("pTitle", "Options");
            v.addProperty("pOpt", true);
            line(lines, "The sort button sorts by:");
            for (int i = 0; i < 3; i++) {
                JsonObject o = new JsonObject();
                o.addProperty("k", i);
                o.addProperty("label", MODES[i][2]);
                o.addProperty("border", i == mode ? "#4AD8F6" : "#1F6B86");
                rowsT.add(o);
            }
            line(lines, "The up arrow moves items you carry into the shellpack when it already holds that item type" + (bool(state, "hotbarStow") ? "" : " (your hotbar is left alone)") + ". The down arrow does the reverse. Shift-click moves one stack.");
        } else if (tab.equals("up")) {
            v.addProperty("pTitle", "Upgrade path");
            v.addProperty("pUp", true);
            JsonArray tiers = arr(state, "tiers");
            for (int i = 0; i < tiers.size(); i++) {
                JsonObject t = tiers.get(i).getAsJsonObject();
                JsonObject o = new JsonObject();
                o.addProperty("name", (i < tier ? "[x] " : i == tier ? "> " : "    ") + str(t, "name"));
                o.addProperty("slots", num(t, "slots") + " slots");
                o.addProperty("color", i == tier ? "#4AD8F6" : i < tier ? "#62D06A" : "#BDF1FF");
                rowsT.add(o);
            }
            JsonObject up = obj(state, "upgrade");
            JsonObject next = up == null ? null : obj(up, "next");
            if (next != null) {
                line(lines, "To reach " + str(next, "name") + " (" + num(next, "slots") + " slots):");
                JsonArray cs = arr(up, "cost");
                if (cs.isEmpty()) line(lines, "Free.");
                for (JsonElement e : cs) {
                    JsonObject c = e.getAsJsonObject();
                    JsonObject o = new JsonObject();
                    o.addProperty("item", str(c, "id"));
                    boolean ok = num(c, "have") >= num(c, "need");
                    o.addProperty("text", num(c, "need") + " x " + str(c, "name") + "  (" + num(c, "have") + ")");
                    o.addProperty("color", ok ? "#62D06A" : "#FF7A6B");
                    cost.add(o);
                }
                v.addProperty("canUp", bool(up, "can"));
                v.addProperty("upText", bool(up, "enabled") ? "UPGRADE" : "UPGRADING IS OFF");
                line(lines, "Your items stay exactly where they are.");
            } else {
                line(lines, "Fully grown. There is nothing above Colossal.");
            }
        }
        v.add("lines", lines);
        v.add("rowsT", rowsT);
        v.add("cost", cost);
        decorate(v, state, st, tier);
        return v;
    }

    // ------------------------------------------------------------ the original page's look (shellpack/app.js PAL + style.css)
    private static final String[][] PAL = {{"#dcc7a2", "#8a6a3a", "#a96a2b"}, {"#cfd9b0", "#6f8240", "#5f7a33"}, {"#ead9a6", "#a88a3a", "#b98f3a"},
            {"#e6c3b8", "#a05a4c", "#b24a3c"}, {"#bfdfdf", "#4a8a92", "#34828a"}, {"#d6c6ea", "#7a5aa6", "#7e50b4"}};

    private static int[] rgb(String c) { return new int[]{Integer.parseInt(c.substring(1, 3), 16), Integer.parseInt(c.substring(3, 5), 16), Integer.parseInt(c.substring(5, 7), 16)}; }
    private static String hex(double r, double g, double b) {
        return String.format("#%02x%02x%02x", (int) Math.max(0, Math.min(255, Math.round(r))), (int) Math.max(0, Math.min(255, Math.round(g))), (int) Math.max(0, Math.min(255, Math.round(b))));
    }
    /** app.js mix(a, b, t) */
    private static String mix(String a, String b, double t) { int[] x = rgb(a), y = rgb(b); return hex(x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t); }
    /** CSS filter: brightness(k) */
    private static String bright(String a, double k) { int[] x = rgb(a); return hex(x[0] * k, x[1] * k, x[2] * k); }

    private static void decorate(JsonObject v, JsonObject state, JsonObject st, int tier) {
        String[] p = PAL[tier];
        JsonObject pal = new JsonObject();
        pal.addProperty("pb", p[0]);
        pal.addProperty("pl", mix(p[0], "#ffffff", .7));
        pal.addProperty("pd", p[1]);
        pal.addProperty("sl", mix(p[1], "#000000", .1));
        pal.addProperty("acc", p[2]);
        pal.addProperty("pbH", bright(p[0], 1.12));
        pal.addProperty("accH", bright(p[2], 1.12));
        pal.addProperty("trail0", mix(p[1], "#ffffff", .27));
        v.add("pal", pal);
        String sl = mix(p[1], "#000000", .1);
        JsonObject sel = st.has("sel") ? st.getAsJsonObject("sel") : null;
        for (String rowsKey : new String[]{"shellRows", "invRows"}) for (JsonElement r : arr(v, rowsKey)) for (JsonElement e : arr(r.getAsJsonObject(), "cells")) {
            JsonObject c = e.getAsJsonObject();
            boolean over = str(c, "border").equalsIgnoreCase("#C4283C");
            String bg = over ? "#7d4a44" : sl;
            c.addProperty("bg", bg);
            c.addProperty("hover", bright(bg, 1.3));
            c.addProperty("sel", sel != null && str(sel, "c").equals(str(c, "pane")) && (int) num(sel, "i") == (int) num(c, "idx"));
            c.addProperty("delay", Math.min((int) num(c, "idx") * 5, 450));
            JsonObject it = itemAt(state, str(c, "pane"), (int) num(c, "idx"));
            c.addProperty("iop", it != null && it.has("bad") && str(c, "pane").equals("p") ? 0.35 : 1);
            c.addProperty("foil", it != null && bool(it, "foil"));
            JsonArray tl = new JsonArray();
            for (int k = 0; k < 6; k++) if (!str(c, "t" + k).isEmpty()) { JsonObject o = new JsonObject(); o.addProperty("text", str(c, "t" + k)); tl.add(o); }
            c.add("tl", tl);
        }
        v.addProperty("snail", "textures/gui/pages/shellpack/snail_" + TIER[tier] + "_0.png");
        v.addProperty("popKey", "open");
        // left tabs: the open one sticks out 8px further (margin-left -8px)
        String tab = str(st, "tab");
        JsonObject tx = new JsonObject(), ty = new JsonObject();
        String[] ks = {"info", "opt", "up"};
        for (int i = 0; i < 3; i++) { tx.addProperty(ks[i], tab.equals(ks[i]) ? -72 : -64); ty.addProperty(ks[i], 52 + i * 62); }
        v.add("tabX", tx);
        v.add("tabY", ty);
        v.addProperty("tab", tab);
        int mode = (int) num(st, "mode");
        if (tab.equals("opt")) for (JsonElement e : arr(v, "rowsT")) {
            JsonObject o = e.getAsJsonObject();
            boolean on = (int) num(o, "k") == mode;
            String lab = str(o, "label");
            o.addProperty("label", lab.isEmpty() ? lab : Character.toUpperCase(lab.charAt(0)) + lab.substring(1));
            o.addProperty("bg", on ? p[2] : p[0]);
            o.addProperty("border", on ? "#ffffff88" : p[1]);
            o.addProperty("color", on ? "#ffffff" : "#3a2a18");
        }
        if (tab.equals("up")) {
            int i = 0;
            for (JsonElement e : arr(v, "rowsT")) {
                JsonObject o = e.getAsJsonObject();
                o.addProperty("name", str(o, "name").replaceFirst("^([\\[]x[\\]] |> |    )", ""));
                o.addProperty("color", i == tier ? p[2] : "#3a2a18");
                i++;
            }
            for (JsonElement e : arr(v, "cost")) { JsonObject o = e.getAsJsonObject(); o.addProperty("color", str(o, "color").equals("#62D06A") ? "#2a7a3a" : "#aa3333"); }
        }
        // the carried stack follows the mouse
        JsonObject held = sel == null ? null : itemAt(state, str(sel, "c"), (int) num(sel, "i"));
        v.addProperty("hasHeld", held != null);
        if (held != null) {
            boolean shell = held.has("shell");
            v.addProperty("heldShell", shell);
            v.addProperty("heldArt", shell ? "a:shell_" + TIER[(int) Math.max(0, Math.min(5, num(held, "shell")))] : "");
            v.addProperty("heldItem", shell ? "minecraft:air" : itemKey(held));
            v.addProperty("heldN", num(sel, "count") > 1 ? String.valueOf(num(sel, "count")) : "");
        }
    }

    private static void line(JsonArray a, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        a.add(o);
    }
}
