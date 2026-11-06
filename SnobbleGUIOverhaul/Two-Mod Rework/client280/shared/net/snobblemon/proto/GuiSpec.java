package net.snobblemon.proto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A declarative GUI definition: plain data, no code. Parsed and validated here, by BOTH mods (the server before it sends a
 * definition, the client again before it draws one). Pure Gson, no Minecraft classes. Art is referenced by NAME inside the
 * theme namespace; no other namespace or path can be named.
 */
public final class GuiSpec {
    public static final int MAX_WIDGETS = 256, MAX_TEXT = 256, MAX_COORD = 2000, MAX_TOOLTIP = 8;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Pattern ART = Pattern.compile("[a-z0-9_./-]{1,96}");
    private static final Pattern ITEM = Pattern.compile("[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,64}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern ACTION = Pattern.compile("[a-z0-9_.]{1,48}");
    private static final Set<String> TYPES = Set.of("panel", "image", "label", "button", "item");

    public static final class Invalid extends RuntimeException {
        public Invalid(String m) { super(m); }
    }

    public static final class Widget {
        public String type, id, text = "", sprite = "", hoverSprite = "", disabledSprite = "", texture = "", item = "", color = "#FFFFFF", font = "", align = "left", action = "", permission = "", enabledIf = "";
        public int x, y, w, h, count = 1;
        public boolean shadow = true;
        public List<String> tooltip = new ArrayList<>();
    }

    public String id, title = "", background = "panel";
    public int width, height, fadeMs;
    public final List<Widget> widgets = new ArrayList<>();
    public final Map<String, Widget> byId = new LinkedHashMap<>();

    public static GuiSpec parse(String json) {
        JsonObject o;
        try {
            if (json == null || json.length() > SnobbleProto.MAX_JSON) throw new Invalid("definition too large");
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new Invalid("definition must be an object");
            o = e.getAsJsonObject();
        } catch (Invalid i) {
            throw i;
        } catch (RuntimeException e) {
            throw new Invalid("not valid JSON");
        }
        try {
            GuiSpec s = new GuiSpec();
            s.id = id(o, "id");
            s.title = text(o, "title");
            s.width = num(o, "width", 64, 1000, true, 0);
            s.height = num(o, "height", 48, 700, true, 0);
            s.fadeMs = num(o, "fade_ms", 0, 2000, false, 0);
            if (o.has("background")) s.background = art(o, "background");
            JsonElement ws = o.get("widgets");
            if (ws == null || !ws.isJsonArray()) throw new Invalid("widgets must be an array");
            JsonArray arr = ws.getAsJsonArray();
            if (arr.size() > MAX_WIDGETS) throw new Invalid("too many widgets");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) throw new Invalid("widget must be an object");
                Widget w = widget(el.getAsJsonObject());
                if (s.byId.containsKey(w.id)) throw new Invalid("duplicate widget id " + w.id);
                s.byId.put(w.id, w);
                s.widgets.add(w);
            }
            return s;
        } catch (Invalid i) {
            throw i;
        } catch (RuntimeException e) {
            throw new Invalid("malformed field (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static Widget widget(JsonObject o) {
        Widget w = new Widget();
        w.type = str(o, "type", true, 16);
        if (!TYPES.contains(w.type)) throw new Invalid("unknown widget type");
        w.id = id(o, "id");
        w.x = num(o, "x", -MAX_COORD, MAX_COORD, false, 0);
        w.y = num(o, "y", -MAX_COORD, MAX_COORD, false, 0);
        w.w = num(o, "w", 0, MAX_COORD, false, 0);
        w.h = num(o, "h", 0, MAX_COORD, false, 0);
        w.text = text(o, "text");
        if (o.has("sprite")) w.sprite = art(o, "sprite");
        if (o.has("hover_sprite")) w.hoverSprite = art(o, "hover_sprite");
        if (o.has("disabled_sprite")) w.disabledSprite = art(o, "disabled_sprite");
        if (o.has("texture")) w.texture = art(o, "texture");
        if (o.has("font")) {
            w.font = str(o, "font", false, 64);
            if (!w.font.isEmpty() && !ART.matcher(w.font).matches()) throw new Invalid("bad font name");
        }
        if (o.has("item")) {
            w.item = str(o, "item", false, 100);
            if (!ITEM.matcher(w.item).matches()) throw new Invalid("bad item id");
        }
        w.count = num(o, "count", 1, 99, false, 1);
        if (o.has("color")) {
            w.color = str(o, "color", false, 7);
            if (!COLOR.matcher(w.color).matches()) throw new Invalid("bad colour");
        }
        if (o.has("shadow")) {
            JsonElement sh = o.get("shadow");
            if (!sh.isJsonPrimitive() || !sh.getAsJsonPrimitive().isBoolean()) throw new Invalid("shadow must be true or false");
            w.shadow = sh.getAsBoolean();
        }
        if (o.has("align")) {
            w.align = str(o, "align", false, 8);
            if (!Set.of("left", "center", "right").contains(w.align)) throw new Invalid("bad align");
        }
        if (o.has("action")) {
            w.action = str(o, "action", false, 48);
            if (!w.action.isEmpty() && !ACTION.matcher(w.action).matches()) throw new Invalid("bad action name");
        }
        if (o.has("permission")) w.permission = str(o, "permission", false, 96);
        if (o.has("enabled_if")) {
            w.enabledIf = str(o, "enabled_if", false, 48);
            if (!w.enabledIf.isEmpty() && !ID.matcher(w.enabledIf).matches()) throw new Invalid("bad enabled_if");
        }
        if (o.has("tooltip")) {
            JsonElement t = o.get("tooltip");
            if (!t.isJsonArray() || t.getAsJsonArray().size() > MAX_TOOLTIP) throw new Invalid("bad tooltip");
            for (JsonElement l : t.getAsJsonArray()) {
                if (!l.isJsonPrimitive() || !l.getAsJsonPrimitive().isString()) throw new Invalid("tooltip lines must be text");
                String line = l.getAsString();
                if (line.length() > MAX_TEXT) throw new Invalid("tooltip line too long");
                w.tooltip.add(line);
            }
        }
        if (w.type.equals("button") && (w.w <= 0 || w.h <= 0)) throw new Invalid("button needs a size");
        if (w.type.equals("item") && w.item.isEmpty()) throw new Invalid("item widget needs an item");
        return w;
    }

    // ---- small checked readers: any wrong type becomes an Invalid, never a runtime exception
    private static String str(JsonObject o, String k, boolean required, int max) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) {
            if (required) throw new Invalid("missing " + k);
            return "";
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new Invalid(k + " must be text");
        String s = e.getAsString();
        if (s.length() > max) throw new Invalid(k + " too long");
        return s;
    }

    private static String text(JsonObject o, String k) { return str(o, k, false, MAX_TEXT); }

    private static String id(JsonObject o, String k) {
        String s = str(o, k, true, 48);
        if (!ID.matcher(s).matches()) throw new Invalid("bad " + k);
        return s;
    }

    private static String art(JsonObject o, String k) {
        String s = str(o, k, false, 96);
        if (!s.isEmpty() && (!ART.matcher(s).matches() || s.contains(".."))) throw new Invalid("bad art name in " + k);
        return s;
    }

    private static int num(JsonObject o, String k, int min, int max, boolean required, int def) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) {
            if (required) throw new Invalid("missing " + k);
            return def;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new Invalid(k + " must be a number");
        double d = e.getAsDouble();
        if (d < min || d > max) throw new Invalid(k + " out of range");
        return (int) d;
    }
}
