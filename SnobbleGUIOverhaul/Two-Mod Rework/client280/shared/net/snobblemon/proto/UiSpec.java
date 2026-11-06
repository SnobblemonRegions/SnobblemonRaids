package net.snobblemon.proto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Layout v2: a tree of containers, text, buttons, inputs and bars with data bindings, repeats and conditions (see UiExpr). Declarative
 * data only; validated here by both mods. Buttons never carry code: a button names a MESSAGE TEMPLATE (fixed keys and values, plus
 * declared arguments with a type and range). The server rebuilds the message from its own copy of the template, so a forged press can
 * only ever send what the layout already allowed, with each argument re-validated.
 */
public final class UiSpec {
    public static final int MAX_NODES = 1400, MAX_DEPTH = 14, MAX_EXPR = 160, MAX_TEXT = 400;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9_.]{1,48}");
    private static final Pattern KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,23}");
    private static final Pattern VAR = Pattern.compile("[a-z][a-z0-9_]{0,23}");
    private static final Pattern ART = Pattern.compile("[a-z0-9_./-]{1,96}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6,8}");
    private static final Set<String> TYPES = Set.of("column", "row", "scroll", "label", "button", "input", "bar", "image", "item", "spacer", "stack", "sweep", "grid", "pixtext", "curve", "burst", "sparks", "goldsweep", "capfx");

    public static final class Invalid extends RuntimeException {
        public Invalid(String m) { super(m); }
    }

    /**
     * Keyframes: {"loop":false,"ease":[x1,y1,x2,y2],"keys":[{"t":0,"dx":0,"dy":0,"sc":1,"rot":0,"a":1,"sx":1,"sy":1}, ...]} over the node's anim_ms. A key may set any of
     * dx, dy (px), sc (scale), rot (deg), a (opacity 0..1), sx, sy (stretch). Each property runs through the keys that set it (before its first key it starts from
     * its rest value, after its last it holds). The ease is applied between keys, like CSS.
     */
    public static final class Kf {
        public static final String[] PROPS = {"dx", "dy", "sc", "rot", "a", "sx", "sy"};
        public static final double[] REST = {0, 0, 1, 0, 1, 1, 1};
        public boolean loop;
        public double[] ease = {.25, .1, .25, 1};
        public double[][] ts = new double[7][], vs = new double[7][];

        static Kf parse(JsonObject o) {
            Kf k = new Kf();
            k.loop = o.has("loop") && o.get("loop").getAsBoolean();
            if (o.has("ease")) {
                if (!o.get("ease").isJsonArray() || o.getAsJsonArray("ease").size() != 4) throw new Invalid("kf ease");
                for (int i = 0; i < 4; i++) k.ease[i] = Math.max(-2, Math.min(3, o.getAsJsonArray("ease").get(i).getAsDouble()));
            }
            if (!o.has("keys") || !o.get("keys").isJsonArray() || o.getAsJsonArray("keys").size() < 1 || o.getAsJsonArray("keys").size() > 24) throw new Invalid("kf keys");
            java.util.List<double[]>[] tr = new java.util.List[7];
            for (int i = 0; i < 7; i++) tr[i] = new java.util.ArrayList<>();
            double last = -1;
            for (JsonElement e : o.getAsJsonArray("keys")) {
                if (!e.isJsonObject()) throw new Invalid("kf key");
                JsonObject kk = e.getAsJsonObject();
                double t = kk.has("t") ? kk.get("t").getAsDouble() : 0;
                if (t < 0 || t > 1 || t < last) throw new Invalid("kf key time");
                last = t;
                for (int i = 0; i < 7; i++) if (kk.has(PROPS[i])) {
                    double v = kk.get(PROPS[i]).getAsDouble();
                    double lim = switch (i) { case 0, 1 -> 4000; case 3 -> 3600; case 4 -> 1; default -> 20; };
                    double lo = (i == 0 || i == 1 || i == 3) ? -lim : 0;
                    tr[i].add(new double[]{t, Math.max(lo, Math.min(lim, v))});
                }
            }
            for (int i = 0; i < 7; i++) {
                k.ts[i] = new double[tr[i].size()]; k.vs[i] = new double[tr[i].size()];
                for (int j = 0; j < tr[i].size(); j++) { k.ts[i][j] = tr[i].get(j)[0]; k.vs[i][j] = tr[i].get(j)[1]; }
            }
            return k;
        }
    }

    public static final class Node {
        public String type, id = "", cond = "", enabled = "", repeatFor = "", repeatAs = "", repeatIf = "";
        public int repeatLimit = 200;
        public String w = "content", h = "content";
        public int[] pad = {0, 0, 0, 0};
        public int gap;
        public String align = "start", cross = "start", bg = "", border = "", grow = "";
        public String text = "", color = "#FFFFFF", font = "", textAlign = "left";
        public double scale = 1.0;
        public boolean wrap, stick, shadow = true;
        // CSS-like drawing (layout in the original page's own pixels)
        public String x = "", y = "", right = "", bottom = "", rot = "";                            // absolute position inside the parent's box; rotation in degrees (number or expression)
        public double size, ls, lh, opacity = 1;                           // font size in px, letter spacing, line height (0 = the font's normal line), opacity
        public int bw = 1, hoverDy, minW, minH, maxW;                                         // border width; hover lift (negative = up)
        public String bshadow = "", tshadow = "";                          // "dx,dy,#RRGGBBAA" box / text shadow
        public boolean upper, clip, dash, bold;
        public int[] radii;
        public boolean rich, italic, ellipsis;                               // **bold** markup in text, italic, cut with "..." at the box edge
        public String ccolor = "", lcolor = "", cfont = "";                // rich text: `code` colour + font, [[link]] colour (markers U+E002 / U+E003; U+E001 = bold)
        public String bcolor = "";                                          // colour of **bold** runs
        public String bsides = "";
        public int lines, minCol;                                           // label: clamp to this many lines ("..."); grid: CSS repeat(auto-fill, minmax(minCol, 1fr))
        public int ringPulseMs = 0, ringLoops = 0;                          // ring_pulse: the ring fades in and out over this many ms (the original dring/dpulse box-shadow pulses); ring_loops: how many times (0 = forever)
        public String ring = "", opacityExpr = "";                          // ring: "width,#colour" outline drawn outside the box                                          // which border sides to draw ("trbl" subset; empty = all)
        public String btop = "";
        public String[] bcolors;                                            // per-side border colours {top, right, bottom, left} (bevels); may use {expressions}                                            // a different top border: "width,#colour"                                                 // per-corner radius {top-left, top-right, bottom-right, bottom-left}
        public String animDelay = "", animKey = "", animHold = "";
        public int animDy, z, cut, cutTl, animSteps;
        public String holo = "";                                              // rare+ cards: strength (0..1) of the hover holographic sheen (rc-holo --hv)
        public boolean tipWait;                                                 // tooltip waits until every one-shot motion on this node and its parents has finished (the card reveal)
        public Kf kf;                                                           // anim "kf": a motion described by keyframes (any effect the server wants, no client update)
        public boolean tilt;                                                    // the web cards' mouse-following 3D tilt (rc-tilt: perspective 1100 px, +-9 degrees)
        public boolean animStable;                                              // the motion keeps running when only the node's y changes (a lifted card), keyed by x alone                               // cut: chamfered bottom-right corner (CSS clip-path panels); animSteps: steps() timing
        public boolean tipBox, tipAbove, aa, cursor, shrink, natural; public double tile; public double[] tipZone;                     // natural: an image drawn at its own pixel size (times "size"), centred                              // shrink: width = natural width of the content (CSS shrink-to-fit)                                      // cursor: drawn at the mouse (a carried item)
        public int easeMs;
        public String xp = "", wExpr = "";                                  // wExpr: width from the data (overrides w)
        public boolean richPlain;                                           // **runs** only change colour (no bold)                                              // x as a share (0..100) of the room left in the parent: (parent w - own w) * xp / 100                                                  // CSS transition: position (x) and bar values glide to their new value                                              // drawn only while its parent is hovered, at the mouse (a styled hover card)                                                // z: draw this overlay above everything before it (modals, banners)                                                  // distance of rise / deal (px)                         // animKey: the motion restarts whenever this value changes                                       // ms before the motion starts (number or expression)
        public String thumb = "", track = "";                               // scroll bar colours                            // uppercase text, clip children to this box, dashed border, synthetic bold
        public String hoverColor = "", hoverBorder = "", hintColor = "", screenBg = "";
        public String sprite = "", hover = "", disabled = "", texture = "", item = "", hint = "", bind = "", fill = "", fillBg = "", value = "", max = "100";
        public int count = 1, maxLen = 32, debounceMs = 250, radius, everyMs;
        public String minMs = "";
        public String travel = "", good = "", perfect = "", anchor = "";   // sweep: a marker that moves across the width and back, with a good and a perfect zone in the middle (all milliseconds)
        public String key = "";
        public String pokemon = "";                                         // image: a Pokemon picture key such as "s:abra:0" (fetched from the server)
        public String anim = "";                                            // motion: pop, fade, slide, flash (once, when the node appears) or shake, pulse, spin (looping)
        public int animMs = 0;                                               // length of one run, 0 = the default for that motion                                              // button: also pressed by this key ("space")
        public String watch = "";
        public String until = "";
        public final List<String> tooltip = new ArrayList<>();
        public JsonObject msg;                                   // template: literal strings and "{arg}" placeholders
        public final Map<String, String> args = new LinkedHashMap<>();   // name -> type spec
        public final Map<String, String> set = new LinkedHashMap<>();    // local state assignments done on click
        public final Map<String, String> hoverSet = new LinkedHashMap<>();   // ... done when the mouse moves onto the node (CSS mouseenter)
        public final Map<String, String> argv = new LinkedHashMap<>();   // arg name -> client-side expression giving its raw value
        public boolean close;                                            // button: close the screen after the click
        public final List<Node> children = new ArrayList<>();
    }

    public String id, title = "", bg = "panel", channel = "", hello = "hello";
    public boolean bye = true;          // tell the backing mod when the page closes (some mods log unknown messages)
    public int width, height;
    public int fitW, fitH;
    public JsonObject toast;
    public double[] zoomFit;
    public boolean noDim;                    // the original page was see-through: the world stays at full brightness around the window
    /** A page's own pixel font (char -> rows of '0'/'1'), drawn cell by cell by "pixtext" nodes. Sent with the layout; the client bundles none. */
    public final Map<Integer, String[]> pixFont = new java.util.HashMap<>();                 // {base, factor, min, max}: zoom = clamp(screenH / base * factor); the page is the window divided by the zoom                 // optional toast look: {bg, color, ok, bad, y, font, size}                   // the original page's fit rule: scale = min(screenW / fitW, screenH / fitH) in real pixels (0 = old behaviour)
    public String byeAction = "";           // optional: also sent as "action" in the close message (mods whose page posted {action:"close"})
    public String hudAnchor = "";            // HUD pages only: tl, tr, tc, bl, br
    public int hudX = 12, hudY = 12;         // distance from that edge
    public final Map<String, JsonElement> state = new LinkedHashMap<>();       // initial local state: literal or {"from": path, "default": x}
    public Node root;
    public String src = "";                  // the layout text this spec came from (kept so it can be parsed again at another window size)
    public boolean hasKs;                    // some nodes carry "ks": a unit scale chosen from the real window (see prescale)
    public double[] ksCur;                   // the {chrome, table, pixel} factors the current tree was built with
    public final Map<String, Node> byId = new LinkedHashMap<>();
    private int count;

    public static boolean looksLikeV2(String json) {
        return json != null && json.length() > 12 && json.substring(0, Math.min(80, json.length())).replace(" ", "").contains("\"v\":2");
    }

    public static UiSpec parse(String json) { return parse(json, null); }

    public static UiSpec parse(String json, double[] ksf) {
        JsonObject o;
        try {
            if (json == null || json.length() > SnobbleProto.MAX_JSON) throw new Invalid("layout too large");
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new Invalid("layout must be an object");
            o = e.getAsJsonObject();
        } catch (Invalid i) {
            throw i;
        } catch (RuntimeException e) {
            throw new Invalid("not valid JSON");
        }
        try {
            UiSpec s = new UiSpec();
            s.src = json;
            s.hasKs = json.contains("\"ks\"");
            if (ksf != null && s.hasKs && o.has("root") && o.get("root").isJsonObject()) { prescale(o.getAsJsonObject("root"), 1.0, ksf); s.ksCur = ksf; }
            if (!o.has("v") || o.get("v").getAsInt() != 2) throw new Invalid("not a v2 layout");
            s.id = id(o, "id");
            s.title = str(o, "title", MAX_TEXT);
            s.channel = str(o, "channel", 48);
            if (o.has("bye")) s.bye = bool(o, "bye");
            if (o.has("hello")) { s.hello = str(o, "hello", 24); if (!KEY.matcher(s.hello).matches()) throw new Invalid("bad hello kind"); }
            if (!s.channel.isEmpty() && !CHANNEL.matcher(s.channel).matches()) throw new Invalid("bad channel");
            s.width = num(o, "width", 64, 3000);
            s.height = num(o, "height", 48, 2000);
            if (o.has("toast")) s.toast = o.getAsJsonObject("toast");
            if (o.has("nodim")) s.noDim = bool(o, "nodim");
            if (o.has("pixfont")) {
                JsonObject pf = o.getAsJsonObject("pixfont");
                if (pf.size() > 128) throw new Invalid("pixfont too big");
                for (var en : pf.entrySet()) {
                    if (en.getKey().codePointCount(0, en.getKey().length()) != 1) throw new Invalid("pixfont keys are single characters");
                    JsonArray rows = en.getValue().getAsJsonArray();
                    if (rows.size() < 1 || rows.size() > 16) throw new Invalid("pixfont glyph rows");
                    String[] g = new String[rows.size()];
                    for (int i = 0; i < g.length; i++) { g[i] = rows.get(i).getAsString(); if (!g[i].matches("[01]{1,16}")) throw new Invalid("pixfont glyph cells"); }
                    s.pixFont.put(en.getKey().codePointAt(0), g);
                }
            }
            if (o.has("zoomfit")) {
                JsonArray z = o.getAsJsonArray("zoomfit");
                if (z.size() != 4) throw new Invalid("zoomfit needs [base, factor, min, max]");
                s.zoomFit = new double[]{z.get(0).getAsDouble(), z.get(1).getAsDouble(), z.get(2).getAsDouble(), z.get(3).getAsDouble()};
                if (s.zoomFit[0] < 100 || s.zoomFit[2] <= 0 || s.zoomFit[3] < s.zoomFit[2]) throw new Invalid("bad zoomfit");
            }
            if (o.has("fit")) {
                JsonArray f = o.getAsJsonArray("fit");
                if (f.size() != 2) throw new Invalid("fit needs [w, h]");
                s.fitW = f.get(0).getAsInt(); s.fitH = f.get(1).getAsInt();
                if (s.fitW < 64 || s.fitW > 4000 || s.fitH < 48 || s.fitH > 3000) throw new Invalid("fit out of range");
            }
            if (o.has("bye_action")) { s.byeAction = str(o, "bye_action", 24); if (!KEY.matcher(s.byeAction).matches()) throw new Invalid("bad bye_action"); }
            if (o.has("hud_anchor")) s.hudAnchor = o.get("hud_anchor").getAsString();
            if (o.has("hud_x")) s.hudX = num(o, "hud_x", 0, 2000);
            if (o.has("hud_y")) s.hudY = num(o, "hud_y", 0, 2000);
            if (o.has("bg")) s.bg = art(o, "bg");
            if (o.has("state")) {
                JsonObject st = o.getAsJsonObject("state");
                for (var en : st.entrySet()) {
                    if (!VAR.matcher(en.getKey()).matches()) throw new Invalid("bad state name");
                    s.state.put(en.getKey(), en.getValue());
                }
            }
            if (!o.has("root") || !o.get("root").isJsonObject()) throw new Invalid("missing root");
            s.root = s.node(o.getAsJsonObject("root"), 0);
            return s;
        } catch (Invalid i) {
            throw i;
        } catch (RuntimeException e) {
            throw new Invalid("malformed field (" + e.getClass().getSimpleName() + ")");
        }
    }


    // ------------------------------------------------------------------ unit scales chosen from the real window
    /** keys whose numbers are lengths in layout units; the second set is kept as whole numbers */
    private static final java.util.Set<String> KS_KEYS = java.util.Set.of("x", "y", "w", "h", "right", "bottom", "size", "lh", "ls", "gap", "radius", "bw", "min_h", "min_w", "max_w", "hover_dy", "anim_dy", "cut", "cut_tl");
    private static final java.util.Set<String> KS_FRACTION = java.util.Set.of("x", "y", "right", "bottom", "size", "lh", "ls");

    /**
     * A node with "ks": "c" (page chrome), "t" (the table content) or "p" (real pixels) has its lengths, and those of everything below it, multiplied by the
     * matching factor (a child's own "ks" replaces it, "1" switches it off). Lets one layout follow a web page whose parts scale by different rules.
     */
    static void prescale(JsonObject n, double f, double[] k) {
        if (n.has("ks") && n.get("ks").isJsonPrimitive()) {
            String m = n.get("ks").getAsString();
            f = switch (m) { case "c" -> k[0]; case "t" -> k[1]; case "p" -> k[2]; default -> 1.0; };
        }
        if (f != 1.0) {
            for (String key : KS_KEYS) {
                JsonElement e = n.get(key);
                if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                    double v = e.getAsDouble() * f;
                    n.addProperty(key, KS_FRACTION.contains(key) ? Math.round(v * 100) / 100.0 : (double) Math.round(v));
                }
            }
            JsonElement kfe = n.get("kf");                                    // a keyframed motion's distances scale with the box
            if (kfe != null && kfe.isJsonObject() && kfe.getAsJsonObject().has("keys")) for (JsonElement ke : kfe.getAsJsonObject().getAsJsonArray("keys")) if (ke.isJsonObject()) for (String kp : new String[]{"dx", "dy"}) {
                JsonElement kv = ke.getAsJsonObject().get(kp);
                if (kv != null && kv.isJsonPrimitive() && kv.getAsJsonPrimitive().isNumber()) ke.getAsJsonObject().addProperty(kp, Math.round(kv.getAsDouble() * f * 100) / 100.0);
            }
            JsonElement p = n.get("pad");
            if (p != null && p.isJsonArray() && p.getAsJsonArray().size() == 4) {
                JsonArray np = new JsonArray();
                for (JsonElement q : p.getAsJsonArray()) np.add(Math.round(q.getAsDouble() * f));
                n.add("pad", np);
            }
            for (String key : new String[]{"tshadow", "bshadow"}) {
                JsonElement e = n.get(key);
                if (e != null && e.isJsonPrimitive() && e.getAsString().matches("-?[0-9.]+,-?[0-9.]+,.*")) {
                    String[] a = e.getAsString().split(",", 3);
                    n.addProperty(key, Math.round(Double.parseDouble(a[0]) * f) + "," + Math.round(Double.parseDouble(a[1]) * f) + "," + a[2]);
                }
            }
        }
        JsonElement tl = n.get("tile");                                      // a repeated picture's square is always in real pixels
        if (tl != null && tl.isJsonPrimitive() && tl.getAsJsonPrimitive().isNumber()) n.addProperty("tile", Math.round(tl.getAsDouble() * k[2] * 100) / 100.0);
        JsonElement tz = n.get("tipzone");                                   // the zone is in the hovered box's own units: the table's
        if (tz != null && tz.isJsonArray()) {
            JsonArray nz = new JsonArray();
            for (JsonElement q : tz.getAsJsonArray()) nz.add(Math.round(q.getAsDouble() * k[1] * 100) / 100.0);
            n.add("tipzone", nz);
        }
        JsonElement ch = n.get("children");
        if (ch != null && ch.isJsonArray()) for (JsonElement c : ch.getAsJsonArray()) if (c.isJsonObject()) prescale(c.getAsJsonObject(), f, k);
    }

    /** parse the same layout again with other unit factors (the window changed); the page-level fields stay as they are */
    public void rescale(double[] ksf) {
        UiSpec n = parse(src, ksf);
        this.root = n.root;
        this.byId.clear();
        this.byId.putAll(n.byId);
        this.ksCur = ksf;
    }

    private Node node(JsonObject o, int depth) {
        if (depth > MAX_DEPTH) throw new Invalid("nested too deep");
        if (++count > MAX_NODES) throw new Invalid("too many nodes");
        Node n = new Node();
        n.type = str(o, "type", 16);
        if (!TYPES.contains(n.type)) throw new Invalid("unknown node type");
        if (o.has("id")) { n.id = id(o, "id"); if (byId.containsKey(n.id)) throw new Invalid("duplicate id " + n.id); byId.put(n.id, n); }
        n.cond = expr(o, "if");
        n.enabled = expr(o, "enabled");
        if (o.has("repeat")) {
            JsonObject r = o.getAsJsonObject("repeat");
            n.repeatFor = expr(r, "for");
            n.repeatAs = str(r, "as", 24);
            if (!VAR.matcher(n.repeatAs).matches()) throw new Invalid("bad repeat name");
            n.repeatIf = expr(r, "if");
            if (r.has("limit")) n.repeatLimit = num(r, "limit", 1, 500);
        }
        if (o.has("w")) n.w = size(o, "w");
        if (o.has("h")) n.h = size(o, "h");
        if (o.has("pad")) {
            JsonElement p = o.get("pad");
            if (p.isJsonArray() && p.getAsJsonArray().size() == 4) { for (int i = 0; i < 4; i++) n.pad[i] = bounded(p.getAsJsonArray().get(i).getAsInt()); }
            else n.pad = new int[]{bounded(p.getAsInt()), bounded(p.getAsInt()), bounded(p.getAsInt()), bounded(p.getAsInt())};
        }
        if (o.has("gap")) n.gap = num(o, "gap", -50, 200);
        if (o.has("every_ms")) n.everyMs = num(o, "every_ms", 500, 60000);
        if (o.has("radius")) n.radius = num(o, "radius", 0, 400);
        n.watch = expr(o, "watch");
        if (o.has("align")) n.align = oneOf(o, "align", "start", "center", "end");
        if (o.has("cross")) n.cross = oneOf(o, "cross", "start", "center", "end");
        if (o.has("bg")) n.bg = fillSpec(o, "bg");
        if (o.has("border")) { n.border = str(o, "border", 40); if (!COLOR.matcher(n.border).matches() && !n.border.contains("{")) throw new Invalid("bad border colour"); }
        n.grow = expr(o, "grow");
        n.text = str(o, "text", MAX_TEXT);
        if (o.has("color")) { n.color = str(o, "color", 60); if (!COLOR.matcher(n.color).matches() && !n.color.contains("{") && !n.color.matches("grad:#[0-9a-fA-F]{6}(,#[0-9a-fA-F]{6}){1,7}")) throw new Invalid("bad colour"); }
        if (o.has("font")) { n.font = str(o, "font", 64); if (!n.font.isEmpty() && !ART.matcher(n.font).matches()) throw new Invalid("bad font"); }
        if (o.has("scale")) { double d = o.get("scale").getAsDouble(); if (d < 0.5 || d > 4) throw new Invalid("bad scale"); n.scale = d; }
        if (o.has("wrap")) n.wrap = bool(o, "wrap");
        if (o.has("shadow")) n.shadow = bool(o, "shadow");
        if (o.has("stick")) n.stick = bool(o, "stick");
        if (o.has("text_align")) n.textAlign = oneOf(o, "text_align", "left", "center", "right");
        if (o.has("sprite")) n.sprite = artOrExpr(o, "sprite");
        if (o.has("hover")) n.hover = fillSpec(o, "hover");
        n.hoverColor = expr(o, "hover_color");
        n.hoverBorder = expr(o, "hover_border");
        if (o.has("disabled")) n.disabled = fillSpec(o, "disabled");
        if (o.has("texture")) n.texture = artOrExpr(o, "texture");
        if (o.has("item")) n.item = str(o, "item", 100);
        if (o.has("count")) n.count = num(o, "count", 1, 99);
        if (o.has("hint")) n.hint = str(o, "hint", 60);
        if (o.has("bind")) { n.bind = str(o, "bind", 24); if (!VAR.matcher(n.bind).matches()) throw new Invalid("bad bind"); }
        if (o.has("max_len")) n.maxLen = num(o, "max_len", 1, 500);
        if (o.has("debounce_ms")) n.debounceMs = num(o, "debounce_ms", 0, 2000);
        if (o.has("fill")) n.fill = fillSpec(o, "fill");
        if (o.has("fill_bg")) n.fillBg = fillSpec(o, "fill_bg");
        if (o.has("value")) n.value = expr(o, "value");
        if (o.has("max")) n.max = expr(o, "max");
        if (o.has("min_ms")) n.minMs = o.get("min_ms").getAsJsonPrimitive().isNumber() ? Integer.toString(num(o, "min_ms", 0, 600000)) : expr(o, "min_ms");
        n.until = expr(o, "until");
        n.travel = expr(o, "travel");
        n.pokemon = expr(o, "pokemon");
        n.good = expr(o, "good");
        n.perfect = expr(o, "perfect");
        n.anchor = expr(o, "anchor");
        if (o.has("key")) n.key = oneOf(o, "key", "space", "esc", "left", "right");
        if (o.has("anim")) n.anim = oneOf(o, "anim", "pop", "fade", "slide", "flash", "shake", "pulse", "spin", "deal", "thunk", "rise", "drop", "tabin", "popin", "grow", "slidein", "floaty", "sweep", "glow", "slidel", "slider", "slideu", "wipe", "reveal", "drift", "ring", "fadeout", "fadeaway", "tear", "flipa", "flipb", "slam", "burstin", "stamp", "ringout", "beam", "dpop", "drise", "dslide", "dbounce", "dburst", "draysin", "dtwinkle", "capa", "capb", "kf");
        if (o.has("anim_delay")) n.animDelay = numOrExpr(o, "anim_delay");
        if (o.has("anim_hold")) n.animHold = numOrExpr(o, "anim_hold");
        if (o.has("holo")) n.holo = numOrExpr(o, "holo");
        if (o.has("tip_wait")) n.tipWait = o.get("tip_wait").getAsBoolean();
        if (o.has("kf")) { if (!o.get("kf").isJsonObject()) throw new Invalid("kf"); n.kf = Kf.parse(o.getAsJsonObject("kf")); }
        if (o.has("tilt")) n.tilt = o.get("tilt").getAsBoolean();
        if (o.has("anim_stable")) n.animStable = o.get("anim_stable").getAsBoolean();
        n.animKey = expr(o, "anim_key");
        if (o.has("anim_dy")) n.animDy = num(o, "anim_dy", -2000, 2000);
        if (o.has("z")) n.z = num(o, "z", 0, 2000);
        if (o.has("cut")) n.cut = num(o, "cut", 0, 100);
        if (o.has("cut_tl")) n.cutTl = num(o, "cut_tl", 0, 100);
        if (o.has("anim_steps")) n.animSteps = num(o, "anim_steps", 0, 100);
        if (o.has("tipbox")) n.tipBox = bool(o, "tipbox");
        if (o.has("tip_above")) n.tipAbove = bool(o, "tip_above");
        if (o.has("aa")) n.aa = bool(o, "aa");
        if (o.has("tile")) n.tile = dnum(o, "tile", 0, 4000);
        if (o.has("tipzone")) { JsonArray za = o.getAsJsonArray("tipzone"); if (za.size() == 4) { n.tipZone = new double[4]; for (int i = 0; i < 4; i++) n.tipZone[i] = za.get(i).getAsDouble(); } }
        if (o.has("cursor")) n.cursor = bool(o, "cursor");
        if (o.has("shrink")) n.shrink = bool(o, "shrink");
        if (o.has("natural")) n.natural = bool(o, "natural");
        if (o.has("ease_ms")) n.easeMs = num(o, "ease_ms", 0, 5000);
        if (o.has("xp")) n.xp = numOrExpr(o, "xp");
        if (o.has("w_expr")) n.wExpr = numOrExpr(o, "w_expr");
        if (o.has("rich_plain")) n.richPlain = bool(o, "rich_plain");
        if (o.has("radii")) {
            JsonArray a = o.getAsJsonArray("radii");
            if (a.size() != 4) throw new Invalid("radii needs 4 numbers");
            n.radii = new int[4];
            for (int i = 0; i < 4; i++) { n.radii[i] = a.get(i).getAsInt(); if (n.radii[i] < 0 || n.radii[i] > 64) throw new Invalid("radius out of range"); }
        }
        if (o.has("thumb")) n.thumb = fillSpec(o, "thumb");
        if (o.has("rich")) n.rich = bool(o, "rich");
        if (o.has("italic")) n.italic = bool(o, "italic");
        if (o.has("ellipsis")) n.ellipsis = bool(o, "ellipsis");
        if (o.has("ccolor")) { n.ccolor = str(o, "ccolor", 9); if (!COLOR.matcher(n.ccolor).matches()) throw new Invalid("bad ccolor"); }
        if (o.has("lcolor")) { n.lcolor = str(o, "lcolor", 9); if (!COLOR.matcher(n.lcolor).matches()) throw new Invalid("bad lcolor"); }
        if (o.has("cfont")) { n.cfont = str(o, "cfont", 24); if (!n.cfont.matches("[a-z0-9_]{1,24}")) throw new Invalid("bad cfont"); }
        if (o.has("bcolor")) { n.bcolor = str(o, "bcolor", 60); if (!COLOR.matcher(n.bcolor).matches() && !n.bcolor.contains("{")) throw new Invalid("bad bcolor"); }
        if (o.has("bsides")) { n.bsides = str(o, "bsides", 4); if (!n.bsides.matches("[trbl]{1,4}")) throw new Invalid("bad bsides"); }
        if (o.has("bcolors")) {
            JsonArray a = o.getAsJsonArray("bcolors");
            if (a.size() != 4) throw new Invalid("bcolors needs 4 colours");
            n.bcolors = new String[4];
            for (int i = 0; i < 4; i++) { n.bcolors[i] = a.get(i).getAsString(); if (!COLOR.matcher(n.bcolors[i]).matches() && !n.bcolors[i].contains("{")) throw new Invalid("bad bcolors"); }
        }
        if (o.has("btop")) { n.btop = str(o, "btop", 60); if (!n.btop.contains("{") && !n.btop.matches("[0-9]{1,2},#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new Invalid("bad btop"); }
        if (o.has("track")) n.track = fillSpec(o, "track");
        if (o.has("anim_ms")) n.animMs = num(o, "anim_ms", 50, 10000);
        if (o.has("tooltip")) {
            JsonArray a = o.getAsJsonArray("tooltip");
            if (a.size() > 24) throw new Invalid("tooltip too long");
            for (JsonElement l : a) { String s = l.getAsString(); if (s.length() > MAX_TEXT) throw new Invalid("tooltip line too long"); n.tooltip.add(s); }
        }
        if (o.has("args")) {
            for (var en : o.getAsJsonObject("args").entrySet()) {
                if (!KEY.matcher(en.getKey()).matches()) throw new Invalid("bad arg name");
                String spec = en.getValue().getAsString();
                checkArgSpec(spec);
                n.args.put(en.getKey(), spec);
            }
        }
        if (o.has("msg")) {
            JsonObject m = o.getAsJsonObject("msg");
            for (var en : m.entrySet()) {
                if (!KEY.matcher(en.getKey()).matches()) throw new Invalid("bad msg key");
                if (!en.getValue().isJsonPrimitive()) throw new Invalid("msg values must be plain");
                String v = en.getValue().getAsString();
                if (v.length() > 64) throw new Invalid("msg value too long");
                if (en.getKey().equals("channel") && !CHANNEL.matcher(v).matches()) throw new Invalid("bad msg channel");     // a fixed channel written in the layout (never from the client)
                if (v.startsWith("{") && v.endsWith("}") && !n.args.containsKey(v.substring(1, v.length() - 1))) throw new Invalid("msg uses an undeclared argument");
            }
            n.msg = m;
        }
        if (o.has("msg") && n.id.isEmpty()) throw new Invalid("a node with msg needs an id");
        if (o.has("set")) {
            for (var en : o.getAsJsonObject("set").entrySet()) {
                if (!VAR.matcher(en.getKey()).matches()) throw new Invalid("bad set name");
                String v = en.getValue().getAsString();
                if (v.length() > MAX_EXPR) throw new Invalid("set value too long");
                n.set.put(en.getKey(), v);
            }
        }
        if (o.has("hover_set")) {
            for (var en : o.getAsJsonObject("hover_set").entrySet()) {
                if (!VAR.matcher(en.getKey()).matches()) throw new Invalid("bad hover_set name");
                String v = en.getValue().getAsString();
                if (v.length() > MAX_EXPR) throw new Invalid("hover_set value too long");
                n.hoverSet.put(en.getKey(), v);
            }
        }
        if (o.has("argv")) {
            for (var en : o.getAsJsonObject("argv").entrySet()) {
                if (!n.args.containsKey(en.getKey())) throw new Invalid("argv names an undeclared argument");
                String v = en.getValue().getAsString();
                if (v.length() > MAX_EXPR) throw new Invalid("argv too long");
                n.argv.put(en.getKey(), v);
            }
        }
        if (o.has("close")) n.close = bool(o, "close");
        if (o.has("x")) n.x = numOrExpr(o, "x");
        if (o.has("y")) n.y = numOrExpr(o, "y");
        if (o.has("rot")) n.rot = numOrExpr(o, "rot");
        if (o.has("right")) n.right = numOrExpr(o, "right");
        if (o.has("bottom")) n.bottom = numOrExpr(o, "bottom");
        if (o.has("size")) n.size = dnum(o, "size", 1, 400);
        if (o.has("ls")) n.ls = dnum(o, "ls", -10, 40);
        if (o.has("lh")) n.lh = dnum(o, "lh", 0, 400);
        if (o.has("opacity")) {
            if (o.get("opacity").getAsJsonPrimitive().isNumber()) n.opacity = dnum(o, "opacity", 0, 1);
            else n.opacityExpr = expr(o, "opacity");
        }
        if (o.has("lines")) n.lines = num(o, "lines", 1, 50);
        if (o.has("min_col")) n.minCol = num(o, "min_col", 8, 2000);
        if (o.has("ring_pulse")) n.ringPulseMs = num(o, "ring_pulse", 400, 10000);
        if (o.has("ring_loops")) n.ringLoops = num(o, "ring_loops", 0, 20);
        if (o.has("ring")) { n.ring = str(o, "ring", 60); if (!n.ring.contains("{") && !n.ring.matches("[0-9]{1,2},#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new Invalid("bad ring"); }
        if (o.has("bw")) n.bw = num(o, "bw", 0, 16);
        if (o.has("hover_dy")) n.hoverDy = num(o, "hover_dy", -40, 40);
        if (o.has("min_w")) n.minW = num(o, "min_w", 0, 3000);
        if (o.has("min_h")) n.minH = num(o, "min_h", 0, 2000);
        if (o.has("screen_bg")) { n.screenBg = str(o, "screen_bg", 9); if (!COLOR.matcher(n.screenBg).matches()) throw new Invalid("bad screen_bg"); }
        if (o.has("max_w")) n.maxW = num(o, "max_w", 1, 3000);
        if (o.has("hint_color")) { n.hintColor = str(o, "hint_color", 9); if (!COLOR.matcher(n.hintColor).matches()) throw new Invalid("bad hint_color"); }
        if (o.has("bshadow")) n.bshadow = shadowSpec(o, "bshadow");
        if (o.has("tshadow")) n.tshadow = shadowSpec(o, "tshadow");
        if (o.has("upper")) n.upper = bool(o, "upper");
        if (o.has("clip")) n.clip = bool(o, "clip");
        if (o.has("dash")) n.dash = bool(o, "dash");
        if (o.has("bold")) n.bold = bool(o, "bold");
        if (o.has("children")) {
            JsonArray ch = o.getAsJsonArray("children");
            for (JsonElement c : ch) n.children.add(node(c.getAsJsonObject(), depth + 1));
        }
        return n;
    }

    // ------------------------------------------------------------------ message templates (used by the server)

    private static void checkArgSpec(String spec) {
        String[] p = spec.split(":");
        switch (p[0]) {
            case "int" -> { if (p.length != 3) throw new Invalid("bad int arg spec"); Long.parseLong(p[1]); Long.parseLong(p[2]); }
            case "enum" -> { if (p.length != 2 || p[1].isEmpty() || p[1].length() > 120) throw new Invalid("bad enum arg spec"); }
            case "str" -> { if (p.length != 2) throw new Invalid("bad str arg spec"); int m = Integer.parseInt(p[1]); if (m < 0 || m > 500) throw new Invalid("str arg too long"); }
            case "bool" -> { }
            default -> throw new Invalid("unknown arg type");
        }
    }

    /** Validates a client-supplied argument against its declared spec. Returns the typed value, or null when it does not fit. */
    public static JsonElement checkArg(String spec, String raw) {
        if (raw == null) return null;
        String[] p = spec.split(":");
        try {
            switch (p[0]) {
                case "int": { long v = Long.parseLong(raw.trim()); return v >= Long.parseLong(p[1]) && v <= Long.parseLong(p[2]) ? new JsonPrimitive(v) : null; }
                case "enum": for (String e : p[1].split("\\|")) if (e.equals(raw)) return new JsonPrimitive(raw); return null;
                case "str": {
                    int max = Integer.parseInt(p[1]);
                    if (raw.length() > max) return null;
                    for (int i = 0; i < raw.length(); i++) if (raw.charAt(i) < 32) return null;
                    return new JsonPrimitive(raw);
                }
                case "bool": return raw.equals("true") ? new JsonPrimitive(true) : raw.equals("false") ? new JsonPrimitive(false) : null;
                default: return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Builds the final message from the node's own template and the client's raw arguments. Null if any argument is missing or invalid. */
    public static JsonObject buildMessage(Node n, Map<String, String> raw) {
        if (n.msg == null) return null;
        JsonObject out = new JsonObject();
        for (var en : n.msg.entrySet()) {
            String v = en.getValue().getAsString();
            if (v.startsWith("{") && v.endsWith("}")) {
                String name = v.substring(1, v.length() - 1);
                JsonElement typed = checkArg(n.args.get(name), raw.get(name));
                if (typed == null) return null;
                out.add(en.getKey(), typed);
            } else out.addProperty(en.getKey(), v);
        }
        return out;
    }

    // ------------------------------------------------------------------ checked readers

    private static String str(JsonObject o, String k, int max) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return "";
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new Invalid(k + " must be text");
        String s = e.getAsString();
        if (s.length() > max) throw new Invalid(k + " too long");
        return s;
    }
    private static String expr(JsonObject o, String k) { return str(o, k, MAX_EXPR); }
    private static String id(JsonObject o, String k) {
        String s = str(o, k, 48);
        if (!ID.matcher(s).matches()) throw new Invalid("bad " + k);
        return s;
    }
    private static String art(JsonObject o, String k) {
        String s = str(o, k, 96);
        if (!s.isEmpty() && (!ART.matcher(s).matches() || s.contains(".."))) throw new Invalid("bad art name in " + k);
        return s;
    }
    private static String artOrExpr(JsonObject o, String k) {
        String s = str(o, k, 120);
        if (s.contains("{")) return s;
        if (!s.isEmpty() && (!ART.matcher(s).matches() || s.contains(".."))) throw new Invalid("bad art name in " + k);
        return s;
    }
    private static String fillSpec(JsonObject o, String k) {
        String s = str(o, k, 100);
        if (s.isEmpty() || s.contains("{")) return s;
        if (s.startsWith("gradv:")) { if (!s.matches("gradv:#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?,#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new Invalid("bad vertical gradient in " + k); return s; }
        if (s.startsWith("grad:")) { if (!s.matches("grad:#[0-9a-fA-F]{6}(,#[0-9a-fA-F]{6}){1,12}")) throw new Invalid("bad gradient in " + k); return s; }
        if (s.startsWith("tex:")) { String t = s.substring(4); if (!t.matches("[a-z0-9_/.-]{1,120}[.]png") || t.contains("..")) throw new Invalid("bad texture in " + k); return s; }
        if (s.startsWith("sprite:")) { if (!ART.matcher(s.substring(7)).matches()) throw new Invalid("bad sprite in " + k); return s; }
        if (!COLOR.matcher(s).matches()) throw new Invalid("bad colour in " + k);
        return s;
    }
    private static String oneOf(JsonObject o, String k, String... opts) {
        String s = str(o, k, 12);
        for (String x : opts) if (x.equals(s)) return s;
        throw new Invalid("bad " + k);
    }
    private static String size(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e.getAsJsonPrimitive().isNumber()) { int v = e.getAsInt(); if (v < 0 || v > 3000) throw new Invalid(k + " out of range"); return Integer.toString(v); }
        String s = e.getAsString();
        if (s.equals("fill") || s.equals("content") || s.matches("[0-9]{1,3}%")) return s;
        throw new Invalid("bad " + k);
    }
    private static String numOrExpr(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            double d = e.getAsDouble();
            if (d < -4000 || d > 4000) throw new Invalid(k + " out of range");
            return Double.toString(d);
        }
        return expr(o, k);
    }
    private static double dnum(JsonObject o, String k, double min, double max) {
        JsonElement e = o.get(k);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new Invalid(k + " must be a number");
        double d = e.getAsDouble();
        if (d < min || d > max) throw new Invalid(k + " out of range");
        return d;
    }
    private static String shadowSpec(JsonObject o, String k) {
        String s = str(o, k, 120);
        if (s.contains("{")) return s;
        if (!s.matches("-?[0-9]{1,3},-?[0-9]{1,3},#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new Invalid("bad " + k + " (dx,dy,#colour)");
        return s;
    }
    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new Invalid(k + " must be true or false");
        return e.getAsBoolean();
    }
    private static int num(JsonObject o, String k, int min, int max) {
        JsonElement e = o.get(k);
        if (e == null) return min;
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new Invalid(k + " must be a number");
        double d = e.getAsDouble();
        if (d < min || d > max) throw new Invalid(k + " out of range");
        return (int) d;
    }
    private static int bounded(int v) {
        if (v < 0 || v > 200) throw new Invalid("padding out of range");
        return v;
    }
}
