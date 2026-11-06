package net.snobblemon.proto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The tiny, safe expression language of layout v2: paths into the data (a.b[0].c, $local, loopvar.field), literals, ! == != < > <= >= && ||,
 * and {interpolation|filter} inside text. It can only READ values; it cannot call anything, loop or allocate. Pure Gson, shared by both mods.
 */
public final class UiExpr {
    private UiExpr() {}

    /** Where names are looked up: the page data, the local UI state ($name) and the loop variables of enclosing repeats. */
    public static final class Ctx {
        public JsonObject data = new JsonObject();
        public final Map<String, JsonElement> local = new HashMap<>();
        public final Map<String, JsonElement> loop = new HashMap<>();
        public long clockSkew;

        public Ctx child() {
            Ctx c = new Ctx();
            c.data = data;
            c.local.putAll(local);
            c.loop.putAll(loop);
            c.clockSkew = clockSkew;
            return c;
        }
    }

    // ------------------------------------------------------------------ paths

    public static JsonElement path(String p, Ctx c) {
        p = p.trim();
        if (p.isEmpty()) return null;
        int i = 0, n = p.length();
        JsonElement cur;
        // first segment
        int end = i;
        while (end < n && p.charAt(end) != '.' && p.charAt(end) != '[') end++;
        String first = p.substring(i, end);
        if (first.startsWith("$")) cur = c.local.get(first.substring(1));
        else if (c.loop.containsKey(first)) cur = c.loop.get(first);
        else cur = c.data.get(first);
        i = end;
        while (i < n && cur != null && !cur.isJsonNull()) {
            char ch = p.charAt(i);
            if (ch == '.') {
                int e = i + 1;
                while (e < n && p.charAt(e) != '.' && p.charAt(e) != '[') e++;
                String key = p.substring(i + 1, e);
                cur = cur.isJsonObject() ? cur.getAsJsonObject().get(key) : null;
                i = e;
            } else if (ch == '[') {
                int close = p.indexOf(']', i);
                if (close < 0) return null;
                JsonElement idx = value(p.substring(i + 1, close), c);
                if (!cur.isJsonArray() || idx == null || !idx.isJsonPrimitive() || !idx.getAsJsonPrimitive().isNumber()) return null;
                JsonArray a = cur.getAsJsonArray();
                int k = idx.getAsInt();
                cur = k >= 0 && k < a.size() ? a.get(k) : null;
                i = close + 1;
            } else return null;
        }
        return cur == null || cur.isJsonNull() ? null : cur;
    }

    /** Like value(), plus left-to-right "a + b" / "a - b" (spaces required) on numbers. Used by button "set" and argv. */
    public static JsonElement calc(String s, Ctx c) {
        if (s == null) return null;
        List<String> terms = new ArrayList<>();
        List<Character> ops = new ArrayList<>();
        boolean q = false;
        int last = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') q = !q;
            if (!q && (ch == '+' || ch == '-') && i > 0 && i + 1 < s.length() && s.charAt(i - 1) == ' ' && s.charAt(i + 1) == ' ') {
                terms.add(s.substring(last, i)); ops.add(ch); last = i + 1;
            }
        }
        terms.add(s.substring(last));
        if (ops.isEmpty()) return value(s, c);
        JsonElement f = value(terms.get(0), c);
        double acc = f != null && f.isJsonPrimitive() && f.getAsJsonPrimitive().isNumber() ? f.getAsDouble() : 0;
        for (int k = 0; k < ops.size(); k++) {
            JsonElement t = value(terms.get(k + 1), c);
            double v = t != null && t.isJsonPrimitive() && t.getAsJsonPrimitive().isNumber() ? t.getAsDouble() : 0;
            acc = ops.get(k) == '+' ? acc + v : acc - v;
        }
        return new JsonPrimitive(acc);
    }

    /** A literal ('text', 12, 1.5, true, false, null) or a path. */
    public static JsonElement value(String s, Ctx c) {
        s = s.trim();
        if (s.isEmpty()) return null;
        if (s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') return new JsonPrimitive(s.substring(1, s.length() - 1));
        if (s.equals("true")) return new JsonPrimitive(true);
        if (s.equals("false")) return new JsonPrimitive(false);
        if (s.equals("null")) return null;
        char f = s.charAt(0);
        if ((f >= '0' && f <= '9') || f == '-') {
            try { return new JsonPrimitive(Double.parseDouble(s)); } catch (NumberFormatException e) { return null; }
        }
        return path(s, c);
    }

    // ------------------------------------------------------------------ conditions

    public static boolean truthy(JsonElement e) {
        if (e == null || e.isJsonNull()) return false;
        if (e.isJsonArray()) return e.getAsJsonArray().size() > 0;
        if (e.isJsonObject()) return e.getAsJsonObject().size() > 0;
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return p.getAsDouble() != 0;
        String s = p.getAsString();
        return !s.isEmpty() && !s.equals("false") && !s.equals("0");
    }

    public static boolean test(String expr, Ctx c) {
        if (expr == null || expr.isBlank()) return true;
        for (String orPart : split(expr, "||")) {
            boolean all = true;
            for (String andPart : split(orPart, "&&")) {
                if (!atom(andPart.trim(), c)) { all = false; break; }
            }
            if (all) return true;
        }
        return false;
    }

    private static List<String> split(String s, String sep) {
        List<String> out = new ArrayList<>();
        boolean q = false;
        int last = 0, depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') q = !q;
            if (!q && ch == '(') depth++;
            if (!q && ch == ')') depth--;
            if (!q && depth == 0 && s.startsWith(sep, i)) { out.add(s.substring(last, i)); last = i + sep.length(); i += sep.length() - 1; }
        }
        out.add(s.substring(last));
        return out;
    }

    /** True when the text is one parenthesised group "( ... )" from its first to its last character. */
    private static boolean wrapped(String a) {
        if (a.length() < 2 || a.charAt(0) != '(' || a.charAt(a.length() - 1) != ')') return false;
        int depth = 0;
        boolean q = false;
        for (int i = 0; i < a.length(); i++) {
            char ch = a.charAt(i);
            if (ch == '\'') q = !q;
            if (q) continue;
            if (ch == '(') depth++;
            if (ch == ')' && --depth == 0 && i < a.length() - 1) return false;
        }
        return depth == 0;
    }

    private static boolean atom(String a, Ctx c) {
        boolean neg = false;
        while (a.startsWith("!") && !a.startsWith("!=")) { neg = !neg; a = a.substring(1).trim(); }
        if (wrapped(a)) return neg != test(a.substring(1, a.length() - 1), c);
        boolean r;
        String[] ops = {"==", "!=", "<=", ">=", "<", ">"};
        String op = null;
        int at = -1;
        boolean q = false;
        for (int i = 0; i < a.length() && op == null; i++) {
            char ch = a.charAt(i);
            if (ch == '\'') q = !q;
            if (q) continue;
            for (String o : ops) if (a.startsWith(o, i)) { op = o; at = i; break; }
        }
        if (op == null) {
            r = truthy(value(a, c));
        } else {
            JsonElement l = value(a.substring(0, at), c), rr = value(a.substring(at + op.length()), c);
            r = compare(l, rr, op);
        }
        return neg != r;
    }

    private static boolean compare(JsonElement l, JsonElement r, String op) {
        boolean ln = l != null && l.isJsonPrimitive() && l.getAsJsonPrimitive().isNumber();
        boolean rn = r != null && r.isJsonPrimitive() && r.getAsJsonPrimitive().isNumber();
        if (ln && rn) {
            double x = l.getAsDouble(), y = r.getAsDouble();
            return switch (op) { case "==" -> x == y; case "!=" -> x != y; case "<" -> x < y; case ">" -> x > y; case "<=" -> x <= y; default -> x >= y; };
        }
        String x = l == null ? "" : asText(l), y = r == null ? "" : asText(r);
        if (l == null && r == null) return op.equals("==") || op.equals("<=") || op.equals(">=");
        return switch (op) { case "==" -> x.equals(y); case "!=" -> !x.equals(y); default -> false; };
    }

    // ------------------------------------------------------------------ text

    public static String asText(JsonElement e) {
        if (e == null || e.isJsonNull()) return "";
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) {
                double d = p.getAsDouble();
                return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
            }
            return p.getAsString();
        }
        return "";
    }

    private static final Map<String, String> MC_COLORS = Map.ofEntries(
            Map.entry("green", "#7ee8a2"), Map.entry("dark_green", "#4fbf7e"), Map.entry("aqua", "#8ff0ee"), Map.entry("dark_aqua", "#4fc4c8"),
            Map.entry("red", "#ff8f8f"), Map.entry("dark_red", "#d95a5a"), Map.entry("gold", "#ffd36b"), Map.entry("yellow", "#fff08a"),
            Map.entry("blue", "#8aa8ff"), Map.entry("dark_blue", "#6c7fe0"), Map.entry("light_purple", "#e3a8ff"), Map.entry("dark_purple", "#b58bff"),
            Map.entry("white", "#ffffff"), Map.entry("gray", "#cfd6e0"), Map.entry("dark_gray", "#9aa6b8"));
    private static final String[] AVATAR = {"#ffb3d1", "#ffd9a0", "#c4f5b4", "#a8dcff", "#d2b8ff", "#fff0a0"};
    private static final String[] NAME_COLORS = {"#6fcf6a", "#5bb8ff", "#b48cff", "#ffd166", "#ff9ab0", "#7ee0d0"};

    static String filter(String name, String arg, JsonElement v, Ctx c) {
        String s = asText(v);
        switch (name) {
            case "floor": try { return Long.toString((long) Math.floor(Double.parseDouble(s))); } catch (NumberFormatException e) { return s; }
            case "num": try { return NumberFormat.getIntegerInstance(Locale.US).format((long) Double.parseDouble(s)); } catch (NumberFormatException e) { return s; }
            case "upper": return s.toUpperCase(Locale.ROOT);
            case "first": return s.isEmpty() ? "?" : s.substring(0, 1).toUpperCase(Locale.ROOT);
            case "len": return v != null && v.isJsonArray() ? Integer.toString(v.getAsJsonArray().size()) : Integer.toString(s.length());
            case "color": return MC_COLORS.getOrDefault(s, "#cfe0ff");
            case "avatar": return AVATAR[hash(s) % AVATAR.length];
            case "namecolor": return NAME_COLORS[hash(s) % NAME_COLORS.length];
            case "ago": {
                try {
                    long t = (long) Double.parseDouble(s);
                    if (t <= 0) return "never";
                    long sec = Math.max(0, Math.round((System.currentTimeMillis() + c.clockSkew - t) / 1000.0));
                    if (sec < 90) return "just now";
                    if (sec < 5400) return Math.round(sec / 60.0) + " min ago";
                    if (sec < 129600) return Math.round(sec / 3600.0) + " h ago";
                    return Math.round(sec / 86400.0) + " d ago";
                } catch (NumberFormatException e) { return s; }
            }
            case "countdown": {          // a deadline (epoch ms, server clock) -> h:mm:ss left, 0:00:00 once passed
                try {
                    long left = Math.max(0, ((long) Double.parseDouble(s)) - (System.currentTimeMillis() + c.clockSkew));
                    long sec = (left + 999) / 1000;
                    return String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60);
                } catch (NumberFormatException e) { return s; }
            }
            case "countdownclock": {     // a deadline (epoch ms) -> m:ss
                try {
                    long left = Math.max(0, ((long) Double.parseDouble(s)) - (System.currentTimeMillis() + c.clockSkew)) / 1000;
                    return (left / 60) + ":" + String.format("%02d", left % 60);
                } catch (NumberFormatException e) { return s; }
            }
            case "countdownsec": {       // a deadline (epoch ms, server clock) -> whole seconds left
                try {
                    long left = Math.max(0, ((long) Double.parseDouble(s)) - (System.currentTimeMillis() + c.clockSkew));
                    return Long.toString(left / 1000);
                } catch (NumberFormatException e) { return s; }
            }
            case "dur": {                // seconds -> "1d 2h" / "3h 4m" / "5m 6s" / "7s"
                try {
                    long t = Math.max(0, Math.round(Double.parseDouble(s)));
                    long d = t / 86400, h = t % 86400 / 3600, m = t % 3600 / 60, sec = t % 60;
                    if (d > 0) return d + "d " + h + "h";
                    if (h > 0) return h + "h " + m + "m";
                    return m > 0 ? m + "m " + sec + "s" : sec + "s";
                } catch (NumberFormatException e) { return s; }
            }
            case "date": {
                try {
                    long t = (long) Double.parseDouble(s);
                    return DateTimeFormatter.ofPattern("M/d/yyyy, h:mm:ss a", Locale.US).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(t));
                } catch (NumberFormatException e) { return s; }
            }
            case "day": {
                try {
                    long t = (long) Double.parseDouble(s);
                    return DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(t));
                } catch (NumberFormatException e) { return s; }
            }
            case "daysago": {
                try {
                    long t = (long) Double.parseDouble(s);
                    if (t <= 0) return "?";
                    long d = (System.currentTimeMillis() - t) / 86400000L;
                    return d < 1 ? "today" : d + " day" + (d > 1 ? "s" : "") + " ago";
                } catch (NumberFormatException e) { return s; }
            }
            default: return s;
        }
    }

    private static int hash(String s) {
        long h = 0;
        for (int i = 0; i < s.length(); i++) h = (h * 31 + s.charAt(i)) & 0xFFFFFFFFL;
        return (int) (h % 1_000_000);
    }

    /** Replaces every {expr} or {expr|filter} in text. Unknown values become empty text. */
    public static String text(String t, Ctx c) {
        if (t == null || t.indexOf('{') < 0) return t == null ? "" : t;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < t.length()) {
            int a = t.indexOf('{', i);
            int b = a < 0 ? -1 : t.indexOf('}', a);
            if (a < 0 || b < 0) { out.append(t, i, t.length()); break; }
            out.append(t, i, a);
            String inner = t.substring(a + 1, b);
            String fname = null;
            int bar = inner.indexOf('|');
            if (bar >= 0) { fname = inner.substring(bar + 1).trim(); inner = inner.substring(0, bar); }
            JsonElement v = value(inner, c);
            out.append(fname == null ? asText(v) : filter(fname, "", v, c));
            i = b + 1;
        }
        return out.toString();
    }
}
