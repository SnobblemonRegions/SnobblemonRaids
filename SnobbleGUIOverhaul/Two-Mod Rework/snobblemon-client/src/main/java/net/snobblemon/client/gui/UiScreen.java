package net.snobblemon.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.snobblemon.client.SnobblemonClient;
import net.snobblemon.client.theme.ThemeRegistry;
import net.snobblemon.proto.SnobbleProto;
import net.snobblemon.proto.UiExpr;
import net.snobblemon.proto.UiSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws a layout-v2 page (UiSpec). Generic: it knows rows, columns, scroll areas, text, buttons, inputs and bars, and nothing about any
 * feature. Every frame it works from the validated spec plus the data the server last sent; a press sends (page, node, raw args) and the
 * server decides. The page is drawn in design units and scaled down to fit small windows.
 */
public final class UiScreen extends Screen {
    private static final int LINE = 9;

    /** One expanded, evaluated node of the current frame's tree. */
    static final class Box {
        UiSpec.Node n;
        UiExpr.Ctx ctx;
        int[] gstops;                 // label colour "grad:#a,#b,..": a horizontal gradient across the label box (CSS background-clip: text)
        final List<Box> kids = new ArrayList<>();
        int x, y, w, h;               // relative to the parent's content origin, after place()
        int ax, ay;                   // absolute (design space) origin of the last draw
        int[] clip;                   // visible rectangle (design space) of the last draw, for hit testing
        int pw, ph, pkey = -1;        // memoised preferred size for width pkey
        int nat;                      // natural (content) width, before a "fill" width stretches it
        String text = "";
        List<FormattedCharSequence> lines = List.of();
        net.snobblemon.client.theme.FontBook.Pick pick;     // null = the old vanilla text path
        boolean abs;                                        // placed by x / y / right / bottom instead of the flow
        int color = 0xFFFFFFFF;
        boolean enabled = true;
        // scroll
        int contentH;
        double offset;
        boolean gutter;
        String key = "";
    }

    private final UiSpec spec;
    private final String pageId;
    private JsonObject data = new JsonObject();
    private final UiExpr.Ctx root = new UiExpr.Ctx();
    private final Set<String> inited = new HashSet<>();
    private Box tree;
    private boolean dirty = true;
    private long lastBuild;
    private float scale = 1f;
    private int ox, oy;
    private boolean serverClosed;

    private final Map<String, Double> scrollOffsets = new HashMap<>();
    private final Map<String, Double> scrollMax = new HashMap<>();
    private final Map<String, String> scrollKeys = new HashMap<>();
    private final Map<String, Long> scrollShown = new HashMap<>();
    private final Map<String, Long> lastSent = new HashMap<>();
    private final Map<String, String> watched = new HashMap<>();
    private final Map<String, String> inputSent = new HashMap<>();
    private final Map<String, Long> inputDirtyAt = new HashMap<>();
    private String focus = "";
    private String dragScroll = "";
    private double dragFromY, dragFromOffset;
    private String toast = "";
    private boolean toastOk;
    private long toastUntil;
    private long lastClickAt;
    private long localSeq = -1;
    private int clickBtn, clickShift;                        // mouse button and shift state of the click being sent (for buttons that declare "btn" / "shift")

    public UiScreen(UiSpec spec, String dataJson) {
        super(Component.literal(spec.title));
        this.spec = spec;
        this.pageId = spec.id;
        applyData(dataJson);
    }

    public String pageId() { return pageId; }
    public JsonObject data() { return data; }
    public Map<String, JsonElement> localState() { return root.local; }
    public Box debugTree() { return tree; }

    // ------------------------------------------------------------------ data

    private static JsonObject parse(String json) {
        try {
            JsonElement e = JsonParser.parseString(json == null || json.isEmpty() ? "{}" : json);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private void applyData(String json) {
        data = parse(json);
        root.data = data;
        if (data.has("_now") && data.get("_now").isJsonPrimitive()) root.clockSkew = data.get("_now").getAsLong() - System.currentTimeMillis();
        initState();
        // the server can set local fields (typed boxes) by sending {"_local": {"seq": n, "vals": {...}}}; a new seq applies the values once
        if (data.has("_local") && data.get("_local").isJsonObject()) {
            JsonObject lo = data.getAsJsonObject("_local");
            long seq = lo.has("seq") ? lo.get("seq").getAsLong() : 0;
            if (seq != localSeq && lo.has("vals") && lo.get("vals").isJsonObject()) {
                localSeq = seq;
                for (var en : lo.getAsJsonObject("vals").entrySet()) root.local.put(en.getKey(), en.getValue());
            }
        }
        dirty = true;
    }

    /** Local state variables take their start value from the data the first time that data exists ({"from": path, "default": x}). */
    private void initState() {
        for (var en : spec.state.entrySet()) {
            String name = en.getKey();
            JsonElement v = en.getValue();
            if (inited.contains(name)) continue;
            if (v.isJsonObject() && v.getAsJsonObject().has("from")) {
                JsonElement got = UiExpr.path(v.getAsJsonObject().get("from").getAsString(), root);
                if (got != null) { root.local.put(name, got); inited.add(name); }
                else if (!root.local.containsKey(name) && v.getAsJsonObject().has("default")) root.local.put(name, v.getAsJsonObject().get("default"));
            } else {
                root.local.put(name, v);
                inited.add(name);
            }
        }
    }

    public void update(String dataJson, String message, boolean ok) {
        if (dataJson != null && !dataJson.isEmpty()) applyData(dataJson);
        if (message != null && !message.isEmpty()) { toast = message; toastOk = ok; toastUntil = Util.getMillis() + 3200; }
    }

    public void closedByServer() { serverClosed = true; }

    @Override public boolean isPauseScreen() { return false; }

    /** Pages that were see-through in the browser must not blur or dim the world behind them either. */
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float d) { if (!spec.noDim) super.renderBackground(g, mx, my, d); }

    private boolean hudMode;

    /** Draw this page over the world as a HUD (no background dim, placed by its hud_anchor, never takes the mouse). */
    public void asHud() { hudMode = true; }

    /** One HUD frame: sizes the page to the window when that changed, then draws it with the mouse far away. */
    public void renderHud(GuiGraphics g, Minecraft mc) {
        if (width != mc.getWindow().getGuiScaledWidth() || height != mc.getWindow().getGuiScaledHeight() || minecraft == null) init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        render(g, -100000, -100000, 0f);
    }

    @Override
    protected void init() {
        scale = Math.min(1f, Math.min((width - 8f) / spec.width, (height - 8f) / spec.height));
        if (scale < 0.25f) scale = 0.25f;
        if (spec.fitW > 0 && minecraft != null) fitScale();
        if (spec.zoomFit != null && minecraft != null) zoomFitScale();
        ox = Math.round((width / scale - spec.width) / 2f);
        oy = Math.round((height / scale - spec.height) / 2f);
        if (hudMode) {
            scale = Math.min(1f, Math.max(0.25f, (width - 8f) / spec.width));
            switch (spec.hudAnchor) {
                case "tl" -> { ox = spec.hudX; oy = spec.hudY; }
                case "tc" -> { ox = Math.round((width / scale - spec.width) / 2f); oy = spec.hudY; }
                case "bl" -> { ox = spec.hudX; oy = Math.round(height / scale - spec.height - spec.hudY); }
                case "br" -> { ox = Math.round(width / scale - spec.width - spec.hudX); oy = Math.round(height / scale - spec.height - spec.hudY); }
                default -> { ox = Math.round(width / scale - spec.width - spec.hudX); oy = spec.hudY; }
            }
        }
        dirty = true;
    }

    private double zoomSeen = -1;

    /** The original's "fill the window" rule: zoom = clamp(h / base * factor), and the page is laid out at window / zoom page pixels. */
    private void zoomFitScale() {
        var win = minecraft.getWindow();
        double[] z = spec.zoomFit;
        JsonElement zp = root.local.get("zoompct");                      // the page's own zoom buttons (60..160%) multiply in, like the original's S.zoom
        double pct = zp != null && zp.isJsonPrimitive() && zp.getAsJsonPrimitive().isNumber() ? Math.max(60, Math.min(160, zp.getAsDouble())) / 100.0 : 1.0;
        zoomSeen = pct;
        double zoom = Math.max(z[2], Math.min(z[3], win.getHeight() / z[0] * z[1] * pct));
        spec.width = (int) Math.round(win.getWidth() / zoom);
        spec.height = (int) Math.round(win.getHeight() / zoom);
        scale = (float) (zoom / win.getGuiScale());
        ox = 0; oy = 0;
        dirty = true;
    }

    /** The original page's own rule: page pixels -> real screen pixels (times the page's zoom), then into GUI units, centred. */
    private void fitScale() {
        var win = minecraft.getWindow();
        JsonElement z = root.local.get("zoompct");
        double zoom = z != null && z.isJsonPrimitive() && z.getAsJsonPrimitive().isNumber() ? Math.max(60, Math.min(160, z.getAsDouble())) / 100.0 : 1.0;
        zoomSeen = zoom;
        double phys = Math.min(win.getWidth() / (double) spec.fitW, win.getHeight() / (double) spec.fitH) * zoom;
        scale = (float) (phys / win.getGuiScale());
        ox = Math.round((width / scale - spec.width) / 2f);
        oy = Math.round((height / scale - spec.height) / 2f);
    }

    @Override
    public void removed() {
        if (!serverClosed && Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(SnobbleProto.UiMsg.TYPE))
            ClientPlayNetworking.send(new SnobbleProto.UiMsg(pageId, "__close", "{}"));
    }

    // ------------------------------------------------------------------ build (expand the spec against the data)

    private void rebuild() {
        Box r = new Box();
        r.n = spec.root;
        r.ctx = root;
        expandInto(spec.root, root, r.kids, true);
        Box top = r.kids.isEmpty() ? new Box() : r.kids.get(0);
        tree = top;
        if (top.n == null) { top.n = spec.root; top.ctx = root; }
        top.pkey = -1;
        place(top, 0, 0, spec.width, spec.height);
        postLayout(top);
        dirty = false;
        lastBuild = Util.getMillis();
    }

    private void expandInto(UiSpec.Node n, UiExpr.Ctx c, List<Box> out, boolean top) {
        if (!n.repeatFor.isEmpty()) {
            JsonElement arr = UiExpr.path(n.repeatFor, c);
            if (arr == null || !arr.isJsonArray()) return;
            int i = 0;
            for (JsonElement item : arr.getAsJsonArray()) {
                if (i >= n.repeatLimit) break;
                UiExpr.Ctx cc = c.child();
                cc.loop.put(n.repeatAs, item);
                cc.loop.put(n.repeatAs + "_i", new com.google.gson.JsonPrimitive(i));
                i++;
                if (!n.repeatIf.isEmpty() && !UiExpr.test(n.repeatIf, cc)) continue;
                one(n, cc, out);
            }
        } else one(n, c, out);
    }

    private void one(UiSpec.Node n, UiExpr.Ctx c, List<Box> out) {
        if (!n.cond.isEmpty() && !UiExpr.test(n.cond, c)) return;
        Box b = new Box();
        b.n = n;
        b.ctx = c;
        b.enabled = n.enabled.isEmpty() || UiExpr.test(n.enabled, c);
        String colS = UiExpr.text(n.color, c);
        if (colS.startsWith("grad:")) {
            String[] gp = colS.substring(5).split(",");
            if (gp.length >= 2 && gp.length <= 8) { b.gstops = new int[gp.length]; for (int i = 0; i < gp.length; i++) b.gstops[i] = color(gp[i].trim(), 0xFFFFFFFF); }
            b.color = b.gstops != null ? b.gstops[0] : color(gp[0], 0xFFFFFFFF);
        } else b.color = color(colS, 0xFFFFFFFF);
        if (n.type.equals("label") || n.type.equals("button") || n.type.equals("input") || n.type.equals("pixtext")) b.text = clean(UiExpr.text(n.text, c));
        if (n.upper) b.text = b.text.toUpperCase(java.util.Locale.ROOT);
        b.abs = !n.x.isEmpty() || !n.y.isEmpty() || !n.right.isEmpty() || !n.bottom.isEmpty() || !n.xp.isEmpty();
        if (n.size > 0 || !n.font.isEmpty()) b.pick = net.snobblemon.client.theme.FontBook.pick(n.font, n.size > 0 ? n.size : 8 * n.scale, screenPerUnit());
        for (UiSpec.Node ch : n.children) expandInto(ch, c, b.kids, false);
        out.add(b);
    }

    /** The Minecraft font cannot draw emoji (surrogate pairs, variation selectors, joiners): drop them rather than show boxes - except the
     *  ones the server pack's page fonts carry as pictures (FontBook.EMOJI), which draw inline like the web page's emoji font. */
    static String clean(String s) {
        boolean dirty = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isSurrogate(ch) || ch == 0xFE0F || ch == 0x200D || ch == 0x20E3) { dirty = true; break; }
        }
        if (!dirty) return s;
        var keep = net.snobblemon.client.theme.FontBook.EMOJI;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            boolean drop = cp == 0xFE0F || cp == 0x200D || cp == 0x20E3 || (cp > 0xFFFF && !keep.contains(cp)) || (n == 1 && Character.isSurrogate((char) cp));
            if (!drop) b.appendCodePoint(cp);
            i += n;
        }
        return b.toString().replaceAll("^ +", "").replaceAll(" {2,}", " ");
    }

    static int color(String hex, int fallback) {
        try {
            if (hex == null || hex.length() < 7 || hex.charAt(0) != '#') return fallback;
            int rgb = Integer.parseInt(hex.substring(1, 7), 16);
            int a = hex.length() >= 9 ? Integer.parseInt(hex.substring(7, 9), 16) : 255;
            return (a << 24) | rgb;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ CSS-like text (fonts from the server pack)

    /** Real screen pixels per page pixel. */
    private double screenPerUnit() { return minecraft == null ? scale * 2.0 : scale * minecraft.getWindow().getGuiScale(); }

    private net.minecraft.network.chat.MutableComponent comp(String s, net.snobblemon.client.theme.FontBook.Pick p, boolean bold) {
        net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY;
        if (p != null && p.id() != null) st = st.withFont(p.id());
        if (bold) st = st.withBold(true);
        return Component.literal(s).withStyle(st);
    }

    /** The node's text as a component: plain, or with **bold** runs (rich) drawn in the bold font file and the bold colour; italic when asked. */
    private net.minecraft.network.chat.MutableComponent compN(String s, UiSpec.Node n, net.snobblemon.client.theme.FontBook.Pick p, UiExpr.Ctx c) {
        net.minecraft.network.chat.Style base = net.minecraft.network.chat.Style.EMPTY;
        if (p != null && p.id() != null) base = base.withFont(p.id());
        if (n.bold) base = base.withBold(true);
        if (n.italic) base = base.withItalic(true);
        boolean marks = n.rich && (s.indexOf('\uE001') >= 0 || s.indexOf('\uE002') >= 0 || s.indexOf('\uE003') >= 0);
        if (!n.rich || (!s.contains("**") && !marks)) return Component.literal(s).withStyle(base);
        net.minecraft.network.chat.Style bst = base;
        if (n.richPlain) { }
        else if (p != null && p.id() != null && net.snobblemon.client.theme.FontBook.has(n.font + "_b")) {
            var bp = net.snobblemon.client.theme.FontBook.pick(n.font + "_b", p.px(), screenPerUnit());
            bst = bst.withFont(bp.id());
        } else bst = bst.withBold(true);
        if (!n.bcolor.isEmpty()) bst = bst.withColor(color(c == null ? n.bcolor : UiExpr.text(n.bcolor, c), 0xFFFFFFFF) & 0xFFFFFF);
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        if (marks) {
            // U+E001 bold, U+E002 code (own colour + font), U+E003 link colour: each one toggles its run
            net.minecraft.network.chat.Style cst = base, lst = base;
            if (!n.ccolor.isEmpty()) cst = cst.withColor(color(n.ccolor, 0xFFFFFFFF) & 0xFFFFFF);
            if (!n.cfont.isEmpty() && p != null && net.snobblemon.client.theme.FontBook.has(n.cfont)) cst = cst.withFont(net.snobblemon.client.theme.FontBook.pick(n.cfont, p.px(), screenPerUnit()).id());
            if (!n.lcolor.isEmpty()) lst = lst.withColor(color(n.lcolor, 0xFFFFFFFF) & 0xFFFFFF);
            boolean bo = false, co = false, li = false;
            StringBuilder run = new StringBuilder();
            for (int i = 0; i <= s.length(); i++) {
                char ch = i < s.length() ? s.charAt(i) : 0;
                if (i == s.length() || ch == '\uE001' || ch == '\uE002' || ch == '\uE003') {
                    if (run.length() > 0) out.append(Component.literal(run.toString()).withStyle(co ? cst : li ? lst : bo ? bst : base));
                    run.setLength(0);
                    if (ch == '\uE001') bo = !bo; else if (ch == '\uE002') co = !co; else if (ch == '\uE003') li = !li;
                } else run.append(ch);
            }
            return out;
        }
        String[] parts = s.split("\\*\\*", -1);
        for (int i = 0; i < parts.length; i++) if (!parts[i].isEmpty()) out.append(Component.literal(parts[i]).withStyle(i % 2 == 1 ? bst : base));
        return out;
    }

    /** Width in page pixels of one line of this node's text. */
    private double textWidth(String s, UiSpec.Node n, net.snobblemon.client.theme.FontBook.Pick p) {
        if (p == null) return font.width(s) * n.scale;
        return font.width(compN(s, n, p, null)) * p.k() + n.ls * s.replace("**", "").replaceAll("[\uE001-\uE003]", "").length();
    }

    private double textWidth(FormattedCharSequence l, UiSpec.Node n, net.snobblemon.client.theme.FontBook.Pick p) {
        return p == null ? font.width(l) * n.scale : font.width(l) * p.k();
    }

    /** Height of one line box: the layout's line height, else the font's normal line (ascent + descent), like CSS "normal". */
    private double lineH(UiSpec.Node n, net.snobblemon.client.theme.FontBook.Pick p) {
        if (p == null) return LINE * n.scale;
        return n.lh > 0 ? n.lh : (p.asc() + p.desc()) * p.px();
    }

    private List<FormattedCharSequence> splitText(String text, double maxW, UiSpec.Node n, net.snobblemon.client.theme.FontBook.Pick p) {
        if (p == null) return font.split(Component.literal(text), Math.max(8, (int) (maxW / n.scale)));
        return font.split(compN(text, n, p, null), Math.max(8, (int) (maxW / p.k())));
    }

    /** Draws one line whose line box starts at top: the baseline goes where CSS puts it (half-leading + ascent); Minecraft draws a glyph's baseline 7 font units below y. */
    private void drawLine(GuiGraphics g, FormattedCharSequence l, String plain, double x, double top, int color, UiSpec.Node n, net.snobblemon.client.theme.FontBook.Pick p, UiExpr.Ctx c) {
        var pose = g.pose();
        double k = p == null ? n.scale : p.k();
        double y;
        if (p == null) y = top;
        else {
            double L = lineH(n, p), content = (p.asc() + p.desc()) * p.px();
            double baseline = top + (L - content) / 2 + p.asc() * p.px();
            y = baseline - 7 * k;
        }
        if (p != null) { double px = 1.0 / screenPerUnit(); x = Math.round(x / px) * px; y = Math.round(y / px) * px; }
        String ts = n.tshadow.isEmpty() ? "" : UiExpr.text(n.tshadow, c);
        int[] tsh = shadowOf(ts);
        boolean vanillaShadow = n.shadow && p == null && tsh == null;
        if ((n.ls != 0 || lineStops != null) && plain != null && p != null) {
            // letter spacing: one glyph at a time
            double cx = x;
            for (int i = 0; i < plain.length(); ) {
                int cp = plain.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                var cc = comp(ch, p, n.bold);
                if (tsh != null) drawAt(g, cc.getVisualOrderText(), cx + tsh[0], y + tsh[1], k, tsh[2]);
                double gw = font.width(cc) * k;
                drawAt(g, cc.getVisualOrderText(), cx, y, k, lineStops == null ? color : gradAt(lineStops, (cx + gw / 2 - lineX0) / Math.max(1, lineW)));
                cx += gw + n.ls;
                i += Character.charCount(cp);
            }
            return;
        }
        if (tsh != null) drawAt(g, l, x + tsh[0], y + tsh[1], k, tsh[2]);
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale((float) k, (float) k, 1f);
        g.drawString(font, l, 0, 0, color, vanillaShadow);
        pose.popPose();
    }

    /** GuiGraphics.blit does not switch blending on, and the batched fills before it leave it off: the textured shader then drops
     *  alpha-0 pixels and draws every other pixel opaque (soft shadows, glows and sweeps turn solid). Every picture goes through here. */
    private static GuiGraphics blend(GuiGraphics g) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        return g;
    }

    private int[] lineStops;                 // set while a gradient label is drawn
    private double lineX0, lineW;

    private static int gradAt(int[] st, double t) {
        t = Math.max(0, Math.min(1, t)) * (st.length - 1);
        int i = Math.min(st.length - 2, (int) Math.floor(t));
        double f = t - i;
        int a = st[i], b = st[i + 1], out = 0;
        for (int sh = 0; sh <= 24; sh += 8) out |= (int) Math.round(((a >>> sh) & 255) + (((b >>> sh) & 255) - ((a >>> sh) & 255)) * f) << sh;
        return out;
    }

    private void drawAt(GuiGraphics g, FormattedCharSequence l, double x, double y, double k, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale((float) k, (float) k, 1f);
        g.drawString(font, l, 0, 0, color, false);
        pose.popPose();
    }

    /** "dx,dy,#colour" -> {dx, dy, argb}; null when empty or malformed. */
    private static int[] shadowOf(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] p = s.split(",");
        if (p.length != 3) return null;
        try { return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), color(p[2].trim(), 0)}; } catch (NumberFormatException e) { return null; }
    }

    private FormattedCharSequence seq(String s, UiSpec.Node n, net.snobblemon.client.theme.FontBook.Pick p) {
        return p == null ? Component.literal(s).getVisualOrderText() : compN(s, n, p, null).getVisualOrderText();
    }

    // ------------------------------------------------------------------ the page's own pixel font ("pixtext")

    private int pixRows() {
        int r = 7;
        for (String[] g : spec.pixFont.values()) { r = g.length; break; }
        return r;
    }

    /** Width in cells; when out != null the lit cells are added as {x, y}. Same rules as the pages' pxt(): space = 3 + gap, unknown = 3. */
    private int pixCells(String text, UiSpec.Node n, List<int[]> out) {
        int gap = n.ls != 0 ? (int) Math.round(n.ls) : 1, x = 0;
        String t = n.upper ? text.toUpperCase(java.util.Locale.ROOT) : text;
        for (int i = 0; i < t.length(); ) {
            int cp = t.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') { x += 3 + gap; continue; }
            String[] g = spec.pixFont.get(cp);
            if (g == null) { x += 3; continue; }
            if (out != null) for (int r = 0; r < g.length; r++) for (int c = 0; c < g[r].length(); c++) if (g[r].charAt(c) == '1') out.add(new int[]{x + c, r});
            x += g[0].length() + gap;
        }
        return Math.max(1, x - gap);
    }

    private void drawPix(GuiGraphics g, Box b, int ax, int ay, UiExpr.Ctx c) {
        UiSpec.Node n = b.n;
        List<int[]> cells = new ArrayList<>();
        int w = pixCells(b.text, n, cells);
        double px = n.size > 0 ? n.size : 3, m = n.bold ? 0.21 : 0;
        String col = UiExpr.text(n.color, c);
        int[] stops = null;
        if (col.startsWith("grad:")) { String[] p = col.substring(5).split(","); if (p.length >= 2) { stops = new int[p.length]; for (int i = 0; i < p.length; i++) stops[i] = color(p[i], 0xFFFFFFFF); } else col = p.length == 1 ? p[0] : "#ffffff"; }
        int solid = stops == null ? color(col, 0xFFFFFFFF) : 0;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(ax + m * px, ay + m * px, 0);
        pose.scale((float) (px / 100.0), (float) (px / 100.0), 1f);          // cells in hundredths, so the bold outline (.21 cell) stays exact
        int e = (int) Math.round(m * 100);
        for (int[] cell : cells) {
            int cc = solid;
            if (stops != null) {
                double t = Math.max(0, Math.min(1, cell[0] / (double) w)) * (stops.length - 1);
                int i = Math.min((int) t, stops.length - 2);
                cc = mix(stops[i], stops[i + 1], t - i);
            }
            g.fill(cell[0] * 100 - e, cell[1] * 100 - e, cell[0] * 100 + 100 + e, cell[1] * 100 + 100 + e, cc);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ layout

    private int textW(String s, double scl) { return (int) Math.ceil(font.width(s) * scl); }

    private int sizeOf(String spec, int parent, int natural) {
        if (spec.equals("content")) return natural;
        if (spec.equals("fill")) return parent;
        if (spec.endsWith("%")) return parent * Integer.parseInt(spec.substring(0, spec.length() - 1)) / 100;
        return Integer.parseInt(spec);
    }

    private int padW(UiSpec.Node n) { return n.pad[1] + n.pad[3]; }
    private int padH(UiSpec.Node n) { return n.pad[0] + n.pad[2]; }

    /** Preferred size of a box given the width it may use. Memoised per box and width. */
    private void pref(Box b, int maxW) {
        if (b.pkey == maxW) return;
        UiSpec.Node n = b.n;
        if (n.w.matches("[0-9]+")) maxW = Integer.parseInt(n.w);          // an explicit width is what the children may use
        int innerMax = Math.max(0, maxW - padW(n));
        int nw = 0, nh = 0;
        switch (n.type) {
            case "label" -> {
                double sc = n.scale;
                int avail = n.w.equals("content") ? innerMax : sizeOf(n.w, innerMax, innerMax);
                if (b.pick != null) {
                    double lh = lineH(n, b.pick);
                    if (n.wrap && !b.text.isEmpty()) {
                        b.lines = splitText(b.text, Math.min(avail, innerMax), n, b.pick);
                        if (n.lines > 0 && b.lines.size() > n.lines) {
                            // -webkit-line-clamp: keep n lines, the last one ends in "..."
                            List<FormattedCharSequence> keep = new ArrayList<>(b.lines.subList(0, n.lines - 1));
                            StringBuilder sb = new StringBuilder();
                            b.lines.get(n.lines - 1).accept((idx, st, cp) -> { sb.appendCodePoint(cp); return true; });
                            String last = sb.toString().stripTrailing();
                            while (last.length() > 1 && textWidth(last + "...", n, b.pick) > Math.min(avail, innerMax)) last = last.substring(0, last.length() - 1).stripTrailing();
                            keep.add(seq(last + "...", n, b.pick));
                            b.lines = keep;
                        }
                        double widest = 0;
                        for (FormattedCharSequence l : b.lines) widest = Math.max(widest, textWidth(l, n, b.pick));
                        nw = (int) Math.ceil(widest);
                        nh = (int) Math.ceil(b.lines.size() * lh);
                    } else {
                        nw = (int) Math.ceil(textWidth(b.text, n, b.pick));
                        nh = (int) Math.ceil(lh);
                    }
                } else if (n.wrap && !b.text.isEmpty()) {
                    int wrapAt = Math.max(8, (int) (Math.min(avail, innerMax) / sc));
                    b.lines = font.split(Component.literal(b.text), wrapAt);
                    int widest = 0;
                    for (FormattedCharSequence l : b.lines) widest = Math.max(widest, font.width(l));
                    nw = (int) Math.ceil(widest * sc);
                    nh = (int) Math.ceil(b.lines.size() * LINE * sc);
                } else {
                    nw = textW(b.text, sc);
                    nh = (int) Math.ceil(LINE * sc);
                }
            }
            case "grid" -> {
                // CSS grid: repeat(auto-fill, minmax(minCol, 1fr)), rows as tall as their tallest item
                int gap = n.gap, cols = Math.max(1, (innerMax + gap) / (Math.max(8, n.minCol) + gap));
                int colW = Math.max(1, (innerMax - gap * (cols - 1)) / cols), total = 0, rowH = 0, i = 0;
                for (Box k : b.kids) {
                    if (k.abs) continue;
                    pref(k, colW);
                    rowH = Math.max(rowH, k.ph);
                    if (++i % cols == 0) { total += rowH + gap; rowH = 0; }
                }
                if (i % cols != 0) total += rowH; else if (total > 0) total -= gap;
                nw = innerMax; nh = total;
            }
            case "button", "column", "row", "stack" -> {
                boolean horiz = n.type.equals("row") || (n.type.equals("button") && !b.kids.isEmpty());
                boolean stack = n.type.equals("stack");
                if (n.type.equals("row") && n.wrap) {
                    int lineW = 0, lineH = 0, tot = 0, widest = 0;
                    for (Box k : b.kids) {
                        if (k.abs) continue;
                        pref(k, innerMax);
                        if (lineW > 0 && lineW + n.gap + k.pw > innerMax) { tot += lineH + n.gap; widest = Math.max(widest, lineW); lineW = 0; lineH = 0; }
                        lineW += (lineW > 0 ? n.gap : 0) + k.pw;
                        lineH = Math.max(lineH, k.ph);
                    }
                    nw = Math.max(widest, lineW); nh = tot + lineH;
                    break;
                }
                int main = 0, cross = 0, cnt = 0;
                for (Box k : b.kids) {
                    if (k.abs) continue;
                    // a filling child cannot size from its content: give it the full width, its height still comes from content
                    int childMax = horiz ? Math.max(0, innerMax - main) : innerMax;
                    pref(k, childMax);
                    // a content-sized box is as wide as its children's natural widths (CSS shrink-to-fit); fill children stretch to that afterwards
                    int kw = childW(k, innerMax, k.pw), kh = childH(k, k.ph);
                    if (stack) { main = Math.max(main, kw); cross = Math.max(cross, kh); }
                    else if (horiz) { main += kw; cross = Math.max(cross, kh); cnt++; }
                    else { main += kh; cross = Math.max(cross, kw); cnt++; }
                }
                int gaps = cnt > 1 ? (cnt - 1) * n.gap : 0;
                if (stack) { nw = main; nh = cross; }
                else if (horiz) { nw = main + gaps; nh = cross; }
                else { nw = cross; nh = main + gaps; }
                if (n.type.equals("button") && b.kids.isEmpty()) {
                    if (b.pick != null) { nw = (int) Math.ceil(textWidth(b.text, n, b.pick)); nh = (int) Math.ceil(lineH(n, b.pick)); }
                    else { nw = textW(b.text, n.scale) + 20; nh = 20; }
                }
            }
            case "scroll" -> {
                nw = innerMax;
                nh = 60;
            }
            case "pixtext" -> {
                double m = n.bold ? 0.21 : 0, px = n.size > 0 ? n.size : 3;
                nw = (int) Math.round((pixCells(b.text, n, null) + 2 * m) * px);
                nh = (int) Math.round((pixRows() + 2 * m) * px);
            }
            case "bar" -> { nw = innerMax; nh = 8; }
            case "sweep" -> { nw = innerMax; nh = 16; }
            case "input" -> { nw = innerMax; nh = 18; }
            case "image", "item" -> { nw = 16; nh = 16; }
            default -> { }
        }
        if (!n.wExpr.isEmpty()) { b.pw = Math.max(0, (int) Math.round(num(n.wExpr, b.ctx, 0))); b.ph = n.h.matches("[0-9]+") ? Integer.parseInt(n.h) : nh + padH(n); b.pkey = maxW; return; }
        boolean px = n.w.matches("[0-9]+");
        int w = px ? Integer.parseInt(n.w) : n.w.equals("content") ? nw + padW(n) : sizeOf(n.w, maxW, nw + padW(n));
        if (!px && w > maxW) w = maxW;                               // never wider than offered (except an explicit pixel width)
        int h = n.h.matches("[0-9]+") ? Integer.parseInt(n.h) : nh + padH(n);   // "fill" heights start natural; place() stretches them
        if (n.shrink && shrinking != b) { Box was = shrinking; shrinking = b; w = Math.min(maxW, natural(b, maxW)); shrinking = was; }   // CSS shrink-to-fit (natural() may measure this same box: no re-entry)
        if (n.minW > 0 && w < n.minW) w = n.minW;
        if (n.minH > 0 && h < n.minH) h = n.minH;
        if (n.maxW > 0 && w > n.maxW) w = n.maxW;
        b.pw = Math.max(0, w);
        b.ph = Math.max(0, h);
        b.pkey = maxW;
    }

    private Box shrinking;

    /** Natural width of a subtree: like pref(), but a "fill" width counts as its content (what CSS shrink-to-fit measures). */
    private int natural(Box b, int maxW) {
        UiSpec.Node n = b.n;
        if (n.w.matches("[0-9]+")) return Integer.parseInt(n.w);
        int inner = 0;
        switch (n.type) {
            case "button", "column", "row", "stack", "grid" -> {
                boolean horiz = n.type.equals("row") || (n.type.equals("button") && !b.kids.isEmpty());
                int cnt = 0;
                for (Box k : b.kids) {
                    if (k.abs || k.n.tipBox || k.n.cursor) continue;
                    int kw = natural(k, maxW);
                    if (horiz && !n.wrap) inner += kw; else inner = Math.max(inner, kw);
                    cnt++;
                }
                if (horiz && !n.wrap && cnt > 1) inner += (cnt - 1) * n.gap;
                if (n.type.equals("button") && b.kids.isEmpty()) { pref(b, maxW); return b.pw; }
            }
            default -> { pref(b, maxW); return n.w.equals("fill") ? Math.min(b.pw, b.pw) : b.pw; }
        }
        return Math.max(n.minW, inner + padW(n));
    }

    private static boolean grows(Box k, boolean horiz) {
        UiSpec.Node n = k.n;
        if (!n.grow.isEmpty() && UiExpr.test(n.grow, k.ctx)) return true;
        return horiz ? n.w.equals("fill") : n.h.equals("fill");
    }

    private int childW(Box k, int innerW, int pw) { return k.n.w.equals("fill") ? innerW : pw; }
    private int childH(Box k, int ph) { return ph; }

    /** Gives box b its final rectangle and lays out its children inside it. */
    private void place(Box b, int x, int y, int w, int h) {
        b.x = x; b.y = y; b.w = w; b.h = h;
        UiSpec.Node n = b.n;
        int ix = n.pad[3], iy = n.pad[0], iw = Math.max(0, w - padW(n)), ih = Math.max(0, h - padH(n));
        // absolutely placed children: like CSS position:absolute, measured from this box's outer edge
        for (Box k : b.kids) {
            if (!k.abs) continue;
            UiSpec.Node kn = k.n;
            boolean hasL = !kn.x.isEmpty() || !kn.xp.isEmpty(), hasT = !kn.y.isEmpty(), hasR = !kn.right.isEmpty(), hasB = !kn.bottom.isEmpty();
            int l = (int) Math.round(num(kn.x, k.ctx, 0)), t = (int) Math.round(num(kn.y, k.ctx, 0));
            int r = (int) Math.round(num(kn.right, k.ctx, 0)), bt = (int) Math.round(num(kn.bottom, k.ctx, 0));
            int availW = hasL && hasR ? Math.max(0, w - l - r) : Math.max(0, w - (hasL ? l : 0));
            pref(k, kn.w.matches("[0-9]+") ? Integer.parseInt(kn.w) : availW);
            int kw = kn.w.equals("fill") || (hasL && hasR && !kn.w.matches("[0-9]+")) ? availW : k.pw;
            int availH = hasT && hasB ? Math.max(0, h - t - bt) : Math.max(0, h - (hasT ? t : 0));
            int kh = kn.h.equals("fill") || (hasT && hasB && !kn.h.matches("[0-9]+")) ? availH : k.ph;
            int kx = hasL ? l : hasR ? w - r - kw : 0;
            if (!kn.xp.isEmpty()) kx = (int) Math.round((w - kw) * Math.max(0, Math.min(100, num(kn.xp, k.ctx, 0))) / 100.0);
            int ky = hasT ? t : hasB ? h - bt - kh : 0;
            place(k, kx, ky, kw, kh);
        }
        switch (n.type) {
            case "stack" -> {
                for (Box k : b.kids) {
                    if (k.abs) continue;
                    pref(k, iw);
                    int kw = k.n.w.equals("fill") ? iw : k.pw, kh = k.n.h.equals("fill") ? ih : k.ph;
                    int kx = ix + alignOff(n.align, iw, kw), ky = iy + alignOff(n.cross, ih, kh);
                    place(k, kx, ky, kw, kh);
                }
            }
            case "grid" -> {
                int gap = n.gap, cols = Math.max(1, (iw + gap) / (Math.max(8, n.minCol) + gap));
                int colW = Math.max(1, (iw - gap * (cols - 1)) / cols);
                List<Box> flow = new ArrayList<>();
                for (Box k : b.kids) if (!k.abs) flow.add(k);
                int yy = iy;
                for (int r0 = 0; r0 < flow.size(); r0 += cols) {
                    int rh = 0;
                    for (int j = r0; j < Math.min(flow.size(), r0 + cols); j++) { pref(flow.get(j), colW); rh = Math.max(rh, flow.get(j).ph); }
                    for (int j = r0; j < Math.min(flow.size(), r0 + cols); j++) place(flow.get(j), ix + (j - r0) * (colW + gap), yy, colW, rh);
                    yy += rh + gap;
                }
            }
            case "scroll" -> {
                String key = scrollKey(b);
                b.key = key;
                if (b.kids.isEmpty()) { b.contentH = 0; break; }
                Box c = b.kids.get(0);
                int cw = iw;
                c.pkey = -1; pref(c, cw);
                if (c.ph > ih) { cw = Math.max(0, iw - 10); c.pkey = -1; pref(c, cw); b.gutter = true; } else b.gutter = false;
                b.contentH = c.ph;
                double max = Math.max(0, b.contentH - ih);
                String resetKey = n.value.isEmpty() ? "" : UiExpr.text("{" + n.value + "}", b.ctx);
                String old = scrollKeys.get(key);
                if (old == null || !old.equals(resetKey)) {
                    scrollKeys.put(key, resetKey);
                    scrollOffsets.put(key, n.stick ? max : 0.0);
                    scrollShown.put(key, Util.getMillis());
                    lastSent.remove(key);
                }
                if (n.stick && scrollOffsets.getOrDefault(key, 0.0) >= scrollMax.getOrDefault(key, 0.0) - 10) scrollOffsets.put(key, max);   // a thread stays at its newest line
                scrollMax.put(key, max);
                b.offset = Math.max(0, Math.min(max, scrollOffsets.getOrDefault(key, 0.0)));
                scrollOffsets.put(key, b.offset);
                place(c, ix, iy, cw, Math.max(c.ph, 0));
            }
            case "button", "column", "row" -> {
                boolean horiz = n.type.equals("row") || (n.type.equals("button") && !b.kids.isEmpty());
                if (n.type.equals("row") && n.wrap) {
                    int lx = 0, ly = 0, lh = 0;
                    List<Box> line = new ArrayList<>();
                    for (Box k : b.kids) {
                        if (k.abs) continue;
                        pref(k, iw);
                        if (lx > 0 && lx + n.gap + k.pw > iw) { ly += lh + n.gap; lx = 0; lh = 0; }
                        int kx = lx > 0 ? lx + n.gap : 0;
                        place(k, ix + kx, iy + ly, Math.min(k.pw, iw), k.ph);
                        lx = kx + k.pw;
                        lh = Math.max(lh, k.ph);
                    }
                    break;
                }
                layoutLinear(b, horiz, ix, iy, iw, ih);
            }
            default -> { }
        }
    }

    private void layoutLinear(Box b, boolean horiz, int ix, int iy, int iw, int ih) {
        UiSpec.Node n = b.n;
        List<Box> flow = new ArrayList<>();
        for (Box k : b.kids) if (!k.abs) flow.add(k);
        layoutFlow(b, flow, horiz, ix, iy, iw, ih);
    }

    private void layoutFlow(Box b, List<Box> kids, boolean horiz, int ix, int iy, int iw, int ih) {
        UiSpec.Node n = b.n;
        int count = kids.size();
        if (count == 0) return;
        int mainAvail = horiz ? iw : ih;
        int[] mainSize = new int[count];
        int fixed = 0, growers = 0;
        for (int i = 0; i < count; i++) {
            Box k = kids.get(i);
            pref(k, horiz ? Math.max(0, iw - fixed) : iw);
            boolean g = grows(k, horiz);
            mainSize[i] = horiz ? k.pw : k.ph;
            if (g) growers++; else fixed += mainSize[i];
        }
        int gaps = (count - 1) * n.gap;
        int remaining = mainAvail - fixed - gaps;
        if (growers > 0) {
            int share = Math.max(0, remaining) / growers;
            int left = Math.max(0, remaining) - share * growers;
            for (int i = 0; i < count; i++) {
                if (!grows(kids.get(i), horiz)) continue;
                mainSize[i] = share + (left-- > 0 ? 1 : 0);
            }
        }
        int used = gaps;
        for (int s : mainSize) used += s;
        int pos = growers > 0 ? 0 : alignOff(n.align, mainAvail, used);
        for (int i = 0; i < count; i++) {
            Box k = kids.get(i);
            int ms = mainSize[i];
            if (horiz) {
                pref(k, ms);                                   // text may wrap differently at its final width
                int kh = k.n.h.equals("fill") ? ih : Math.min(k.ph, Math.max(ih, k.ph));
                int ky = iy + alignOff(n.cross, ih, kh);
                place(k, ix + pos, ky, ms, kh);
            } else {
                pref(k, iw);
                int kw = k.n.w.equals("fill") ? iw : Math.min(k.pw, iw == 0 ? k.pw : Math.max(iw, 0));
                if (k.n.w.matches("[0-9]+")) kw = k.pw;
                if (k.n.maxW > 0 && kw > k.n.maxW) kw = k.n.maxW;      // CSS max-width: a stretched child stops growing (centred by the parent's cross align)
                int kx = ix + alignOff(n.cross, iw, kw);
                place(k, kx, iy + pos, kw, ms);
            }
            pos += ms + n.gap;
        }
    }

    private static int alignOff(String align, int avail, int size) {
        return switch (align) { case "center" -> Math.max(0, (avail - size) / 2); case "end" -> Math.max(0, avail - size); default -> 0; };
    }

    private final java.util.Map<Integer, Long> sweepStart = new java.util.HashMap<>();

    private static double num(String expr, UiExpr.Ctx c, double dflt) {
        if (expr == null || expr.isEmpty()) return dflt;
        JsonElement v = UiExpr.calc(expr, c);
        try { return v == null ? dflt : Double.parseDouble(UiExpr.asText(v)); } catch (NumberFormatException e) { return dflt; }
    }

    private String scrollKey(Box b) { return b.n.id.isEmpty() ? "scroll@" + System.identityHashCode(b.n) : b.n.id; }

    // ------------------------------------------------------------------ after layout: locals, reading progress, watches, debounced inputs

    private void postLayout(Box b) {
        long now = Util.getMillis();
        walk(b, k -> {
            UiSpec.Node n = k.n;
            if (n.type.equals("scroll")) {
                double max = Math.max(0, k.contentH - (k.h - padH(n)));
                boolean atEnd = k.offset >= max - 10;
                long shown = scrollShown.getOrDefault(k.key, now);
                if (!n.minMs.isEmpty() || n.msg != null) {
                    long left = (long) Math.ceil((shown + minMsOf(k) + 300 - now) / 1000.0);
                    setLocal("_atend", new com.google.gson.JsonPrimitive(atEnd));
                    setLocal("_left", new com.google.gson.JsonPrimitive(Math.max(0, left)));
                }
            }
        });
    }

    private void setLocal(String name, JsonElement v) {
        JsonElement old = root.local.get(name);
        if (old == null || !old.equals(v)) { root.local.put(name, v); dirty = true; }
    }

    private void walk(Box b, java.util.function.Consumer<Box> f) {
        f.accept(b);
        for (Box k : b.kids) walk(k, f);
    }

    private void tick(long now) {
        if (tree == null) return;
        walk(tree, k -> {
            UiSpec.Node n = k.n;
            // watch: send the node's message whenever the watched value changes (e.g. "$cur" changed because a page was opened)
            if (!n.watch.isEmpty() && n.msg != null && !n.id.isEmpty()) {
                JsonElement v = UiExpr.calc(n.watch, k.ctx);
                String t = v == null ? "" : UiExpr.asText(v);
                if (!t.isEmpty() && !t.equals(watched.get(n.id))) { watched.put(n.id, t); send(k); }
            }
            // periodic message (a live list asking for a refresh)
            if (n.everyMs > 0 && n.msg != null && !n.id.isEmpty() && now - lastSent.computeIfAbsent("every:" + n.id, key -> now) >= n.everyMs) {
                lastSent.put("every:" + n.id, now);
                send(k);
            }
            // reading progress: a scroll area with a message sends it once it is at its end, has been shown long enough, and "until" is not yet true
            if (n.type.equals("scroll") && n.msg != null && !k.key.isEmpty()) {
                double max = Math.max(0, k.contentH - (k.h - padH(n)));
                boolean atEnd = k.offset >= max - 10;
                long shown = scrollShown.getOrDefault(k.key, now);
                boolean done = !n.until.isEmpty() && UiExpr.test(n.until, k.ctx);
                if (!done && atEnd && now - shown >= minMsOf(k) + 300 && now - lastSent.getOrDefault(k.key, 0L) > 2000) {
                    lastSent.put(k.key, now);
                    send(k);
                }
            }
            // text input: send the message a short while after the player stops typing
            if (n.type.equals("input") && n.msg != null) {
                Long at = inputDirtyAt.get(n.id);
                if (at != null && now - at >= n.debounceMs) {
                    inputDirtyAt.remove(n.id);
                    String cur = localText(n.bind);
                    if (!cur.equals(inputSent.getOrDefault(n.id, ""))) { inputSent.put(n.id, cur); send(k); }
                }
            }
        });
    }

    private long minMsOf(Box k) {
        if (k.n.minMs.isEmpty()) return 0;
        JsonElement v = UiExpr.calc(k.n.minMs, k.ctx);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? (long) v.getAsDouble() : 0;
    }

    private String localText(String name) { return UiExpr.asText(root.local.get(name)); }

    private void send(Box k) {
        if (!ClientPlayNetworking.canSend(SnobbleProto.UiMsg.TYPE)) return;
        JsonObject args = new JsonObject();
        for (var en : k.n.argv.entrySet()) {
            JsonElement v = UiExpr.calc(en.getValue(), k.ctx);
            args.addProperty(en.getKey(), v == null ? "" : UiExpr.asText(v));
        }
        if (k.n.args.containsKey("btn")) args.addProperty("btn", String.valueOf(clickBtn));
        if (k.n.args.containsKey("shift")) args.addProperty("shift", String.valueOf(clickShift));
        String json = args.toString();
        if (json.length() > 590) return;
        ClientPlayNetworking.send(new SnobbleProto.UiMsg(pageId, k.n.id, json));
        root.local.put("_sent", new com.google.gson.JsonPrimitive(Util.getMillis()));      // when the last message left: sweeps restart from here
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!hudMode && !spec.noDim) renderTransparentBackground(g);
        frameNo++;
        tiltSeq = 0;
        animBusy = 0;
        long now = Util.getMillis();
        if (spec.fitW > 0 && minecraft != null) {
            JsonElement z = root.local.get("zoompct");
            double zz = z != null && z.isJsonPrimitive() && z.getAsJsonPrimitive().isNumber() ? Math.max(60, Math.min(160, z.getAsDouble())) / 100.0 : 1.0;
            if (zz != zoomSeen) { fitScale(); dirty = true; }
        }
        if (spec.zoomFit != null && minecraft != null) {
            JsonElement z = root.local.get("zoompct");
            double zz = z != null && z.isJsonPrimitive() && z.getAsJsonPrimitive().isNumber() ? Math.max(60, Math.min(160, z.getAsDouble())) / 100.0 : 1.0;
            if (zz != zoomSeen) { zoomFitScale(); dirty = true; }
        }
        if (dirty || now - lastBuild > 250 || tree == null) rebuild();
        tick(now);
        double vx = mouseX / (double) scale - ox, vy = mouseY / (double) scale - oy;
        var pose = g.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1f);
        pose.translate(ox, oy, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        List<String> tip = new ArrayList<>();
        int[] full = {0, 0, spec.width, spec.height};
        hoverAnc = null;
        Box hotBox = hit(tree, 0, 0, full, vx, vy);
        // hover_set belongs to the nearest box under the mouse that has one (a hovered label inside a card counts as the card)
        Box owner = hoverAnc;
        if (owner != null && owner != lastHoverOwner) {
            for (var en : owner.n.hoverSet.entrySet()) {
                JsonElement v = UiExpr.calc(en.getValue(), owner.ctx);
                if (v != null) root.local.put(en.getKey(), v.deepCopy()); else root.local.remove(en.getKey());
            }
            dirty = true;
        }
        lastHoverOwner = owner;
        lastHover = hotBox;
        draw(g, tree, 0, 0, full, hotBox, tip, vx, vy);
        walk(tree, k -> {
            if (!k.n.cursor) return;
            // a carried item follows the mouse (its x / y are the offset from the pointer)
            int cx0 = (int) num(k.n.x, k.ctx, 0), cy0 = (int) num(k.n.y, k.ctx, 0);
            int sx = k.x, sy = k.y;
            k.x = (int) vx + cx0; k.y = (int) vy + cy0;
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 950);
            drawZ(g, k, 0, 0, full, null, tip, vx, vy);
            g.pose().popPose();
            k.x = sx; k.y = sy;
        });
        if (hotBox != null) for (Box k : hotBox.kids) if (k.n.tipBox) {
            // the page's own hover card: follows the mouse, flips left near the right edge, never leaves the window
            int tx = (int) vx + 22, ty = (int) vy + 18;
            if (tx + k.w > spec.width - 10) tx = (int) vx - k.w - 22;
            if (ty + k.h > spec.height - 10) ty = spec.height - 10 - k.h;
            k.x = Math.max(8, tx); k.y = Math.max(8, ty);
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 900);
            drawZ(g, k, 0, 0, full, null, tip, vx, vy);
            g.pose().popPose();
        }
        if (!toast.isEmpty() && now < toastUntil && spec.toast != null) {
            // the page's own toast: {bg, color, ok, bad (accent bar colours), y, font, size, anim}
            JsonObject ts = spec.toast;
            String fn = ts.has("font") ? ts.get("font").getAsString() : "";
            double sz = ts.has("size") ? ts.get("size").getAsDouble() : 16;
            var pk = net.snobblemon.client.theme.FontBook.pick(fn, sz, screenPerUnit());
            UiSpec.Node tn = new UiSpec.Node();
            tn.size = sz; tn.font = fn;
            double tw = textWidth(toast, tn, pk) + 52, th = lineH(tn, pk) + 24;
            int tx = (int) ((spec.width - tw) / 2), ty = ts.has("y") ? ts.get("y").getAsInt() : 132;
            double t = Math.min(1, (now - (toastUntil - 3200)) / 250.0), e = 1 - (1 - t) * (1 - t);
            int dy = (int) Math.round(-14 * (1 - e));
            g.flush(); RenderSystem.setShaderColor(1f, 1f, 1f, (float) Math.max(0.05, e));
            if (ts.has("tex_ok")) {
                // a fixed box drawn with the page's own art (rendered from its CSS); the text is centred in it
                int bx = ts.get("x").getAsInt(), by = ts.get("y").getAsInt(), bw2 = ts.get("w").getAsInt(), bh = ts.get("h").getAsInt();
                double tq = Math.min(1, (now - (toastUntil - 3200)) / 250.0);
                int ddy = (int) Math.round(-16 * (1 - tq));
                g.flush(); RenderSystem.setShaderColor(1f, 1f, 1f, (float) Math.max(0.05, tq));
                ResourceLocation tex = ThemeRegistry.file("textures/gui/pages/" + ts.get(toastOk ? "tex_ok" : "tex_bad").getAsString());
                int[] tsz = ThemeRegistry.size(tex);
                blend(g).blit(tex, bx, by + ddy, bw2, bh, 0f, 0f, tsz[0], tsz[1], tsz[0], tsz[1]);
                tn.ls = ts.has("ls") ? ts.get("ls").getAsDouble() : 0;
                double tw2 = textWidth(toast, tn, pk), lh2 = lineH(tn, pk);
                drawLine(g, seq(toast, tn, pk), toast, bx + (bw2 - tw2) / 2, by + ddy + (bh - lh2) / 2,
                        color(ts.get(toastOk ? "color_ok" : "color_bad").getAsString(), 0xFFFFFFFF), tn, pk, root);
                g.flush(); RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else if (ts.has("border")) {
                // the page's toast is a bordered box that slides down from above
                int padX = ts.has("pad_x") ? ts.get("pad_x").getAsInt() : 18, padY = ts.has("pad_y") ? ts.get("pad_y").getAsInt() : 8, rad = ts.has("radius") ? ts.get("radius").getAsInt() : 10;
                double from = ts.has("from") ? ts.get("from").getAsDouble() : -80, tq = bezier(.25, .1, .25, 1, Math.min(1, (now - (toastUntil - 3200)) / 350.0));
                int bw2 = (int) textWidth(toast, tn, pk) + 2 * padX + 4, bh = (int) lineH(tn, pk) + 2 * padY + 4;
                int bx = (spec.width - bw2) / 2, by = (int) Math.round(ty + from * (1 - tq));
                g.flush(); RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                fillRound(g, bx, by, bw2, bh, rad, color(ts.has("bg") ? ts.get("bg").getAsString() : "#0d2a2b", 0xFF0D2A2B));
                String bcol = !toastOk && ts.has("border_bad") ? ts.get("border_bad").getAsString() : ts.get("border").getAsString();
                String tcol = !toastOk && ts.has("color_bad") ? ts.get("color_bad").getAsString() : ts.has("color") ? ts.get("color").getAsString() : "#ffffff";
                int bwid = ts.has("bw") ? ts.get("bw").getAsInt() : 2;
                for (int i = 0; i < bwid; i++) outline(g, bx + i, by + i, bw2 - 2 * i, bh - 2 * i, Math.max(0, rad - i), color(bcol, 0xFF3AFF8C));
                drawLine(g, seq(toast, tn, pk), toast, bx + 2 + padX, by + 2 + padY, color(tcol, 0xFFFFFFFF), tn, pk, root);
            } else {
            g.fill(tx + 4, ty + 4 + dy, tx + (int) tw + 4, ty + (int) th + 4 + dy, 0x66000000);
            g.fill(tx, ty + dy, tx + (int) tw, ty + (int) th + dy, color(ts.has("bg") ? ts.get("bg").getAsString() : "#3a2a17", 0xFF3A2A17));
            g.fill(tx, ty + dy, tx + 8, ty + (int) th + dy, color(toastOk ? (ts.has("ok") ? ts.get("ok").getAsString() : "#2f7d3a") : (ts.has("bad") ? ts.get("bad").getAsString() : "#b3261e"), 0xFF2F7D3A));
            drawLine(g, seq(toast, tn, pk), toast, tx + 34, ty + 12 + dy, color(ts.has("color") ? ts.get("color").getAsString() : "#f3e6c8", 0xFFF3E6C8), tn, pk, root);
            }
            g.flush(); RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else if (!toast.isEmpty() && now < toastUntil) {
            int tw = font.width(toast) + 24, tx = (spec.width - tw) / 2;
            fillRound(g, tx, 8, tw, 22, 6, toastOk ? 0xFF12301F : 0xFF3A1D28);
            outline(g, tx, 8, tw, 22, 6, toastOk ? 0xFF2A6A45 : 0xFF7A2E45);
            g.drawString(font, toast, tx + 12, 15, toastOk ? 0xFFB9F3CF : 0xFFFFD0D8, false);
        }
        pose.popPose();
        if (!tip.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : tip) lines.add(styledTip(l));
            int tx0 = mouseX, ty0 = mouseY;
            if (tip.size() > 8 && tipAnchor != null) {
                // a tall card (the pokeshout-style Pokemon card) goes beside the hovered card, never over it, so the tilt stays visible
                int tw = 0;
                for (Component c : lines) tw = Math.max(tw, font.width(c));
                tw += 8;
                int th = lines.size() * 10 + 8, sx = Math.round((tipAnchor[0] + tipAnchor[2] + ox) * scale) + 6, sl = Math.round((tipAnchor[0] + ox) * scale) - 6 - tw;
                int sy = Math.round((tipAnchor[1] + oy) * scale) + 4;
                tx0 = sx + tw <= width ? sx : Math.max(2, sl);
                ty0 = Math.max(4, Math.min(sy, height - th - 4));
                tx0 -= 12; ty0 += 12;
            }
            g.renderComponentTooltip(font, lines, tx0, ty0);
        }
    }

    /** tooltip text with section markup: "§#rrggbb" sets the colour, "§l" bold, "§r" resets (the pokeshout hover card uses exact colours, which the 16 legacy codes cannot give) */
    private static Component styledTip(String l) {
        if (l.indexOf('§') < 0) return Component.literal(l);
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        net.minecraft.network.chat.Style st = net.minecraft.network.chat.Style.EMPTY;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < l.length(); i++) {
            char ch = l.charAt(i);
            if (ch == '§' && i + 1 < l.length()) {
                if (cur.length() > 0) { out.append(Component.literal(cur.toString()).withStyle(st)); cur.setLength(0); }
                char k = l.charAt(i + 1);
                if (k == '#' && i + 7 < l.length() + 0 && i + 8 <= l.length()) {
                    try { st = st.withColor(Integer.parseInt(l.substring(i + 2, i + 8), 16)); i += 7; continue; } catch (NumberFormatException ignored) { }
                }
                if (k == 'l') st = st.withBold(true);
                else if (k == 'r') st = net.minecraft.network.chat.Style.EMPTY;
                else { net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(k); if (f != null) st = st.applyFormat(f); }
                i++;
                continue;
            }
            cur.append(ch);
        }
        if (cur.length() > 0) out.append(Component.literal(cur.toString()).withStyle(st));
        return out;
    }

    private void scissor(GuiGraphics g, int[] c) {
        int x1 = Math.round((c[0] + ox) * scale), y1 = Math.round((c[1] + oy) * scale), x2 = Math.round((c[2] + ox) * scale), y2 = Math.round((c[3] + oy) * scale);
        g.enableScissor(x1, y1, Math.max(x1, x2), Math.max(y1, y2));
    }

    private final java.util.Map<String, long[]> animState = new java.util.HashMap<>();      // node key -> {start, last frame seen}
    private long frameNo = 0;
    private int tiltSeq;
    /** > 0 while the node being drawn sits inside a one-shot motion that has not finished (tooltips with tip_wait stay hidden) */
    private int animBusy;          // draw-order index of tilting nodes this frame: a repeated card template shares one Node, so the state is told apart by order

    /** Motion for a node: runs once when it appears (pop, fade, slide, flash) or loops (shake, pulse, spin). Purely visual. */
    /** test hook: when >= 0 the motions run on this clock (so a recording can step exactly 50 ms per frame) */
    public static volatile long fakeNow = -1;
    private float alphaMul = 1f;
    private Box lastHover;
    private int[] tipAnchor;      // screen rect (design units) of the node whose tooltip is showing

    private void draw(GuiGraphics g, Box b, int px, int py, int[] clip, Box hot, List<String> tip, double vx, double vy) {
        if (b.n.z > 0) {
            // an overlay: flush what is below, then lift it (items and batched text sit at higher depths than plain fills)
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, b.n.z);
            drawZ(g, b, px, py, clip, hot, tip, vx, vy);
            g.pose().popPose();
            return;
        }
        drawZ(g, b, px, py, clip, hot, tip, vx, vy);
    }

    private final Map<String, double[]> eases = new HashMap<>();         // node key -> {from, to, start ms}

    /** CSS transition: a value moving to a new target glides there over ms (ease-in-out). */
    private double ease(String key, double target, int ms) {
        long now = Util.getMillis();
        double[] e = eases.get(key);
        if (e == null) { eases.put(key, new double[]{target, target, now}); return target; }
        if (e[1] != target) {
            double t0 = Math.min(1, (now - e[2]) / (double) ms), cur = e[0] + (e[1] - e[0]) * bezier(.42, 0, .58, 1, t0);
            e[0] = cur; e[1] = target; e[2] = now;
        }
        double t = Math.min(1, (now - e[2]) / (double) ms);
        return e[0] + (e[1] - e[0]) * bezier(.42, 0, .58, 1, t);
    }

    private void drawZ(GuiGraphics g, Box b, int px, int py, int[] clip, Box hot, List<String> tip, double vx, double vy) {
        UiSpec.Node n0 = b.n;
        if (n0.easeMs > 0 && !n0.type.equals("bar")) {
            int real = b.x;
            b.x = (int) Math.round(ease("x@" + System.identityHashCode(n0), real, n0.easeMs));
            drawZ2(g, b, px, py, clip, hot, tip, vx, vy);
            b.x = real;
            return;
        }
        drawZ2(g, b, px, py, clip, hot, tip, vx, vy);
    }

    private void drawZ2(GuiGraphics g, Box b, int px, int py, int[] clip, Box hot, List<String> tip, double vx, double vy) {
        UiSpec.Node n0 = b.n;
        double rot0 = n0.rot.isEmpty() ? 0 : num(n0.rot, b.ctx, 0);
        int lift = n0.hoverDy != 0 && b == hot && b.enabled ? n0.hoverDy : 0;
        double op = n0.opacityExpr.isEmpty() ? n0.opacity : Math.max(0, Math.min(1, num(n0.opacityExpr, b.ctx, 1)));
        if (rot0 == 0 && lift == 0 && op >= 0.999) { drawAnim(g, b, px, py, clip, hot, tip, vx, vy); return; }
        var pose = g.pose();
        float before = alphaMul;
        if (op < 0.999) { g.flush(); alphaMul = before * (float) op; RenderSystem.setShaderColor(1f, 1f, 1f, alphaMul); }
        float cx = px + b.x + b.w / 2f, cy = py + b.y + b.h / 2f;
        pose.pushPose();
        pose.translate(cx, cy + lift, 0);
        if (rot0 != 0) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) rot0));
        pose.translate(-cx, -cy, 0);
        drawAnim(g, b, px, py, clip, hot, tip, vx, vy);
        pose.popPose();
        if (op < 0.999) { g.flush(); alphaMul = before; RenderSystem.setShaderColor(1f, 1f, 1f, alphaMul); }
    }

    /** a rectangle given in the 1000x563 burst canvas, drawn at this box's scale */
    private static void fillC(GuiGraphics g, int ax, int ay, double sx, double sy, double x, double y, double w, double h, int col) {
        g.fill((int) Math.round(ax + x * sx), (int) Math.round(ay + y * sy), Math.max((int) Math.round(ax + x * sx) + 1, (int) Math.round(ax + (x + w) * sx)), Math.max((int) Math.round(ay + y * sy) + 1, (int) Math.round(ay + (y + h) * sy)), col);
    }

    /**
     * The web cards' mouse tilt (cobbletowers app.js g()/C() + app.css .rc-tilt): while the pointer is over the card,
     * rotateY((px - .5) * 18deg) rotateX(-(py - .5) * 18deg) inside perspective 1100px (the card is 280 px wide), reached with a .06 s linear
     * transition; on leave the card eases back to rest over .35 s (ease-out). State per node: {rx, ry (double bits), leave start rx, ry, leave ms, inside, last ms}.
     */
    private void drawTilt(GuiGraphics g, Box b, int px, int py, int[] clip, Box hot, List<String> tip, double vx, double vy) {
        long now = fakeNow >= 0 ? fakeNow : Util.getMillis();
        String key = "tilt@" + System.identityHashCode(b.n) + "#" + (tiltSeq++);
        long[] s = animState.get(key);       // {curX, frame, curY, leaveX, leave t0, inside, last ms, leaveY}
        if (s == null || frameNo - s[1] > 3) { s = new long[]{0, frameNo, 0, 0, now, 0, now, 0}; animState.put(key, s); }
        s[1] = frameNo;
        double ax0 = px + b.x, ay0 = py + b.y;
        boolean calm = "false".equals(UiExpr.text("{vm.anim}", b.ctx));
        boolean inside = !calm && b.enabled && vx >= ax0 && vx < ax0 + b.w && vy >= ay0 && vy < ay0 + b.h;
        double dt = Math.max(0, now - s[6]);
        s[6] = now;
        double curX = Double.longBitsToDouble(s[0]), curY = Double.longBitsToDouble(s[2]);
        if (inside) {
            double tx = -((vy - ay0) / b.h - .5) * 18, ty = ((vx - ax0) / b.w - .5) * 18;
            double k = Math.min(1, dt / 60.0);                           // .06 s linear transition towards each new pointer position
            curX += (tx - curX) * k; curY += (ty - curY) * k;
            s[5] = 1;
        } else {
            if (s[5] == 1) { s[5] = 0; s[4] = now; s[3] = Double.doubleToLongBits(curX); s[7] = Double.doubleToLongBits(curY); }
            double q = bezier(0, 0, .58, 1, Math.min(1, (now - s[4]) / 350.0));
            curX = Double.longBitsToDouble(s[3]) * (1 - q);
            curY = Double.longBitsToDouble(s[7]) * (1 - q);
        }
        s[0] = Double.doubleToLongBits(curX); s[2] = Double.doubleToLongBits(curY);
        if (Math.abs(curX) < 0.01 && Math.abs(curY) < 0.01) { drawAnim0(g, b, px, py, clip, hot, tip, vx, vy); drawHolo(g, b, ax0, ay0, curX, curY, inside, vx, vy, now, s); return; }
        float cx = (float) (ax0 + b.w / 2.0), cy = (float) (ay0 + b.h / 2.0);
        double d = 1100.0 * b.w / 280.0;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        // CSS perspective(d) rotateY rotateX on the x/y plane only: the rotation's own depth feeds the perspective divide (w = 1 - z/d) but is then dropped, so the tilted card stays flat in
        // depth and cannot poke through layers above it, while the draw depths of the card's children (text, sprites, glows) keep their order and are not scaled or skewed.
        org.joml.Matrix4f rm = new org.joml.Matrix4f().rotateY((float) Math.toRadians(curY)).rotateX((float) Math.toRadians(curX));
        org.joml.Matrix4f m = new org.joml.Matrix4f(rm);
        m.m02(0f).m12(0f).m20(0f).m21(0f).m22(1f).m23(0f).m03((float) (-rm.m02() / d)).m13((float) (-rm.m12() / d));
        pose.last().pose().mul(m);
        pose.translate(-cx, -cy, 0);
        drawAnim0(g, b, px, py, clip, hot, tip, vx, vy);
        drawHolo(g, b, ax0, ay0, curX, curY, inside, vx, vy, now, s);
        pose.popPose();
    }

    /** rc-holo: while the pointer is on a rare+ card, a white glow under the pointer plus a hue-shifting rainbow wash fade in over .25 s (strength --hv by rarity). */
    private void drawHolo(GuiGraphics g, Box b, double ax0, double ay0, double curX, double curY, boolean inside, double vx, double vy, long now, long[] s) {
        if (b.n.holo.isEmpty()) return;
        double hv = num(b.n.holo, b.ctx, 0);
        if (hv <= 0) return;
        // fade state kept in the tilt slot's spare far end: 0 -> 1 over .25 s while inside
        String key = "holo@" + System.identityHashCode(b.n) + "#" + tiltSeq;
        long[] h = animState.get(key);
        if (h == null || frameNo - h[1] > 3) { h = new long[]{0, frameNo, now}; animState.put(key, h); }
        double f = Double.longBitsToDouble(h[0]);
        double dt = Math.max(0, now - h[2]);
        f = inside ? Math.min(1, f + dt / 250.0) : Math.max(0, f - dt / 250.0);
        h[0] = Double.doubleToLongBits(f); h[1] = frameNo; h[2] = now;
        double a = f * hv;
        if (a < 0.01) return;
        double mx = Math.max(0, Math.min(1, (vx - ax0) / b.w)), my = Math.max(0, Math.min(1, (vy - ay0) / b.h));
        int x0 = (int) ax0, y0 = (int) ay0, w = b.w, hgt = b.h;
        // rainbow wash: 6 hue bands sliding with the pointer (conic gradient rotated by mx * 3.6 deg is approximated by a horizontal band shift)
        int[] cols = {0xff6ec7, 0x7873f5, 0x4adede, 0x7cf29c, 0xffe66d, 0xff6ec7};
        int bands = 12;
        for (int i = 0; i < bands; i++) {
            double t = ((i + 0.5) / bands + mx * 0.1) % 1.0 * 5.0;
            int c0 = cols[(int) t], c1 = cols[Math.min(5, (int) t + 1)];
            double u = t - (int) t;
            int r = (int) (((c0 >> 16 & 255) * (1 - u)) + ((c1 >> 16 & 255) * u)), gg = (int) (((c0 >> 8 & 255) * (1 - u)) + ((c1 >> 8 & 255) * u)), bl = (int) (((c0 & 255) * (1 - u)) + ((c1 & 255) * u));
            int al = (int) (a * 0.45 * 255);
            int bx0 = x0 + w * i / bands, bx1 = x0 + w * (i + 1) / bands;
            g.fill(bx0, y0, bx1, y0 + hgt, (al << 24) | (r << 16) | (gg << 8) | bl);
        }
        // white glow under the pointer (concentric squares fading out to 42% of the card)
        int cx = (int) (x0 + mx * w), cy = (int) (y0 + my * hgt), rad = (int) (Math.max(w, hgt) * 0.42);
        for (int k = 0; k < 8; k++) {
            int rr = rad * (8 - k) / 8;
            int al = (int) (a * 0.12 * 255);
            g.fill(Math.max(x0, cx - rr), Math.max(y0, cy - rr), Math.min(x0 + w, cx + rr), Math.min(y0 + hgt, cy + rr), (al << 24) | 0xffffff);
        }
    }

    private void drawAnim(GuiGraphics g, Box b, int px, int py, int[] clip, Box hot, List<String> tip, double vx, double vy) {
        if (b.n.tilt) drawTilt(g, b, px, py, clip, hot, tip, vx, vy); else drawAnim0(g, b, px, py, clip, hot, tip, vx, vy);
    }

    private String animKeyOf(Box b) {
        return (b.key.isEmpty() ? "anim@" + System.identityHashCode(b.n) + ":" + b.x + (b.n.animStable ? "" : ":" + b.y) : b.key)
                + (b.n.animKey.isEmpty() ? "" : "|" + UiExpr.text("{" + b.n.animKey + "}", b.ctx));
    }

    private int animMsOf(Box b) {
        return b.n.animMs > 0 ? b.n.animMs : switch (b.n.anim) { case "pop" -> 380; case "flash" -> 600; case "shake" -> 267; case "pulse" -> 900; case "spin" -> 1200;
            case "deal" -> 380; case "rise" -> 320; case "popin" -> 260; case "grow" -> 360; case "slidein" -> 250;
            case "floaty" -> 3800; case "sweep" -> 5500; case "glow" -> 3200; case "slidel", "slider", "slideu" -> 500; case "drift" -> 50000; case "ring" -> 2800;
            case "wipe" -> 550; case "reveal" -> 700; case "drop" -> 250; case "tabin" -> 300; case "thunk" -> 300;
            case "fadeout" -> 700; case "fadeaway" -> 250; case "tear" -> 600; case "flipa", "flipb" -> 850; case "capa", "capb" -> 1800; case "slam" -> 600; case "burstin" -> 600; case "stamp" -> 1800; case "ringout" -> 700; case "beam" -> 800; case "dpop" -> 200; case "drise" -> 550; case "dslide" -> 280; case "dbounce" -> 500; case "dburst" -> 700; case "draysin" -> 900; case "dtwinkle" -> 1100; default -> 300; };
    }

    private static boolean loopKind(Box b) {
        return switch (b.n.anim) { case "shake", "pulse", "spin", "floaty", "sweep", "glow", "drift", "ring" -> true; case "kf" -> b.n.kf != null && b.n.kf.loop; default -> false; };
    }

    /** true while this node or anything inside it is still in a one-shot motion (before its delay, during it, or in its hold): the real animation state, no fixed timer */
    private boolean subtreeBusy(Box b, long now) {
        if (!b.n.anim.isEmpty() && !loopKind(b)) {
            long[] st = animState.get(animKeyOf(b));
            if (st != null) {
                long delay = b.n.animDelay.isEmpty() ? 0 : (long) num(b.n.animDelay, b.ctx, 0);
                long hold = (b.n.anim.equals("flipa") || b.n.anim.equals("flipb")) && !b.n.animHold.isEmpty() ? (long) num(b.n.animHold, b.ctx, 0) : 0;
                if (now - st[0] - delay < animMsOf(b) + hold) return true;
            }
        }
        for (Box k : b.kids) if (subtreeBusy(k, now)) return true;
        return false;
    }

    private void drawAnim0(GuiGraphics g, Box b, int px, int py, int[] clip, Box hot, List<String> tip, double vx, double vy) {
        int before = animBusy;
        try { drawAnim1(g, b, px, py, clip, hot, tip, vx, vy); } finally { animBusy = before; }
    }

    private void drawAnim1(GuiGraphics g, Box b, int px, int py, int[] clip, Box hot, List<String> tip, double vx, double vy) {
        if (b.n.anim.isEmpty()) { drawNode(g, b, px, py, clip, hot, tip, vx, vy); return; }
        if ("false".equals(UiExpr.text("{vm.anim}", b.ctx))) {      // the page's calm-effects setting: no motion; one-shot effects and the card back are not drawn at all
            switch (b.n.anim) { case "flipa", "capa", "tear", "fadeout", "fadeaway", "stamp", "ringout", "beam", "dburst", "draysin", "dtwinkle", "flash" -> { } default -> drawNode(g, b, px, py, clip, hot, tip, vx, vy); }
            return;
        }
        long now = fakeNow >= 0 ? fakeNow : Util.getMillis();
        String akey = animKeyOf(b);
        long[] st = animState.get(akey);
        if (st == null || frameNo - st[1] > 2) { st = new long[]{now, frameNo}; animState.put(akey, st); }
        st[1] = frameNo;
        String kind = b.n.anim;
        long delay = b.n.animDelay.isEmpty() ? 0 : (long) num(b.n.animDelay, b.ctx, 0);
        if (delay > 0 && now < st[0] + delay && !kind.equals("fadeaway")) return;        // CSS fill-mode both/backwards: nothing shows before the delay ends
        now -= delay;                                         // a negative delay starts a loop part-way through (CSS animation-delay: -1.4s)
        int ms = animMsOf(b);
        double t = Math.min(1.0, (now - st[0]) / (double) ms);
        {   // a one-shot motion that is still running: its card is not ready yet (the loops never end and do not count)
            boolean loops = loopKind(b);
            long hold = (kind.equals("flipa") || kind.equals("flipb")) && !b.n.animHold.isEmpty() ? (long) num(b.n.animHold, b.ctx, 0) : 0;
            if (!loops && now - st[0] < ms + hold) animBusy++;
        }
        double e = t - 1, back = 1 + 2.70158 * e * e * e + 1.70158 * e * e;               // ease out with a little overshoot
        float alpha = 1f, dx = 0, dy = 0, sc = 1f, rot = 0f, sx = 1f, sy = 1f;
        switch (kind) {
            case "pop" -> { alpha = (float) Math.min(1, t * 2); dy = (float) (9 * (1 - back)); sc = (float) (0.8 + 0.2 * back); }
            case "fade" -> alpha = (float) t;
            case "slide" -> { alpha = (float) t; dy = (float) (12 * (1 - t) * (1 - t)); }
            case "flash" -> alpha = (float) (1 - t);
            case "shake" -> { double ph = ((now - st[0]) % (2L * ms)) / (double) ms; double w = ph <= 1 ? ph : 2 - ph; dx = (float) ((w - 0.5) * 6); rot = (float) ((w - 0.5) * 12); }
            case "pulse" -> { double w = 0.5 + 0.5 * Math.sin((now - st[0]) * 2 * Math.PI / ms); alpha = (float) (0.55 + 0.45 * w); sc = (float) (1 + 0.04 * w); }
            case "spin" -> rot = (float) (((now - st[0]) % ms) * 360.0 / ms);
            // the web pages' keyframes, with CSS ease-out = cubic-bezier(0, 0, .58, 1)
            case "deal" -> { double q = bezier(0, 0, .58, 1, t); alpha = (float) q; dy = (float) ((b.n.animDy != 0 ? b.n.animDy : 16) * (1 - q)); }
            case "floaty" -> { double ph = ((now - st[0]) % ms) / (double) ms; dy = (float) (-(b.n.animDy != 0 ? b.n.animDy : 6) * (0.5 - 0.5 * Math.cos(2 * Math.PI * ph))); }
            case "sweep" -> { double ph = ((now - st[0]) % ms) / (double) ms; double q = Math.min(1, ph / 0.4); dx = (float) (-60 + ((b.n.animDy != 0 ? b.n.animDy : 300) + 60) * bezier(.42, 0, .58, 1, q)); }
            case "glow" -> { double ph = ((now - st[0]) % ms) / (double) ms; alpha = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * ph)); }
            case "slidel" -> { double q = bezier(.25, .1, .25, 1, t); alpha = (float) q; dx = (float) (-34 * (1 - q)); }
            case "slider" -> { double q = bezier(.25, .1, .25, 1, t); alpha = (float) q; dx = (float) (34 * (1 - q)); }
            case "slideu" -> { double q = bezier(.25, .1, .25, 1, t); alpha = (float) q; dy = (float) ((b.n.animDy != 0 ? b.n.animDy : 18) * (1 - q)); }
            case "drift" -> { double ph = ((now - st[0]) % ms) / (double) ms; dx = (float) ((b.n.animDy != 0 ? b.n.animDy : 40) * ph); dy = dx; }
            case "ring" -> { double ph = ((now - st[0]) % ms) / (double) ms, q = bezier(0, 0, .58, 1, ph); sc = (float) (0.7 + 0.8 * q); alpha = (float) (0.8 * (1 - q)); }
            case "wipe", "reveal" -> { }
            case "grow" -> { double q = bezier(.3, 1.5, .5, 1, t); alpha = (float) Math.min(1, t * 3); sc = (float) (0.2 + 0.8 * q); }
            case "slidein" -> dx = (float) (60 * (1 - bezier(0, 0, .58, 1, t)));
            case "popin" -> { alpha = (float) Math.min(1, t / 0.7); sc = (float) (t < 0.7 ? 0.86 + (1.03 - 0.86) * bezier(.25, .1, .25, 1, t / 0.7) : 1.03 - 0.03 * bezier(.25, .1, .25, 1, (t - 0.7) / 0.3)); }
            case "rise" -> { double q = b.n.animDy != 0 ? bezier(.25, .1, .25, 1, t) : bezier(0, 0, .58, 1, t); alpha = (float) q; dy = (float) ((b.n.animDy != 0 ? b.n.animDy : 8) * (1 - q)); }
            case "tabin" -> { double q = bezier(0, 0, .58, 1, t); alpha = (float) q; dy = (float) (6 * (1 - q)); }
            case "drop" -> { double q = bezier(0, 0, .58, 1, t); alpha = (float) q; dy = (float) (-14 * (1 - q)); }
            case "thunk" -> { double q = bezier(.2, 1.5, .4, 1, t); alpha = (float) Math.min(1, t / 0.6); sc = (float) (1.8 - 0.8 * q); }
            // rental draft motions (the page's keyframes: tear, fade forwards, 3D card flip as a squash, slam, burst, stamp, ring, beam)
            case "fadeaway" -> alpha = (float) (1 - Math.max(0, t));        // the delay is how long it stays before fading
            case "fadeout" -> { double q = bezier(.25, .1, .25, 1, t); alpha = (float) (1 - q); dy = (float) (30 * q); }
            case "tear" -> { double q = bezier(.42, 0, 1, 1, t); dx = (float) (40 * q); dy = (float) (-90 * q); rot = (float) (18 * q); alpha = (float) (1 - q); }
            case "flipa", "flipb" -> {
                long hold = b.n.animHold.isEmpty() ? 0 : (long) num(b.n.animHold, b.ctx, 0);
                double tt = Math.max(0, Math.min(1.0, (now - st[0] - hold) / (double) ms)), ang = bezier(.2, .8, .2, 1, tt);
                if (kind.equals("flipa")) { if (ang >= 0.5) return; sx = (float) Math.max(0.02, Math.cos(ang * Math.PI)); }
                else { if (ang < 0.5) return; sx = (float) Math.max(0.02, -Math.cos(ang * Math.PI)); }
            }
            // AscensionLib capture reveal (capture-flip.js "Prism Rupture", 1.8 s): the card turns from its back (A NEW BOND AWAITS) to its front with a little lift and tilt; capa is the back face, capb the front
            case "capa", "capb" -> {
                if (t >= 1) { if (kind.equals("capa")) return; }
                else {
                    double q = bezier(.24, .7, .25, 1, t);
                    double[] off = {0, .3, .52, .7, 1}, ty = {14, -14, -10, 2, 0}, ss = {.94, 1.02, 1.04, 1.04, 1}, ry = {180, 180, 94, -8, 0}, rz = {0, 0, 0, 0, 0};
                    int k = 0;
                    while (k < 3 && q > off[k + 1]) k++;
                    double f = (q - off[k]) / (off[k + 1] - off[k]);
                    dy = (float) (ty[k] + (ty[k + 1] - ty[k]) * f); sc = (float) (ss[k] + (ss[k + 1] - ss[k]) * f); rot = (float) (rz[k] + (rz[k + 1] - rz[k]) * f);
                    double cosv = Math.cos(Math.toRadians(ry[k] + (ry[k + 1] - ry[k]) * f));
                    if (kind.equals("capa")) { if (cosv >= 0) return; sx = (float) Math.max(0.02, -cosv); }
                    else { if (cosv < 0) return; sx = (float) Math.max(0.02, cosv); }
                }
            }
            case "slam" -> {
                if (t < 0.7) { double q = bezier(.25, .1, .25, 1, t / 0.7); dy = (float) (-130 + 138 * q); sc = (float) (1.5 - 0.54 * q); alpha = (float) Math.min(1, t / 0.3); }
                else { double q = bezier(.25, .1, .25, 1, (t - 0.7) / 0.3); dy = (float) (8 * (1 - q)); sc = (float) (0.96 + 0.04 * q); }
            }
            case "burstin" -> { double q = bezier(.25, .1, .25, 1, t); sc = (float) (0.15 + 0.85 * q); rot = (float) (-70 * (1 - q)); alpha = (float) q; }
            case "stamp" -> {
                rot = -8f;
                if (t < 0.1) { alpha = (float) Math.min(1, t / 0.1); sc = (float) (3.2 - 2.26 * (t / 0.1)); }
                else if (t < 0.15) sc = (float) (0.94 + 0.09 * ((t - 0.1) / 0.05));
                else if (t < 0.2) sc = (float) (1.03 - 0.03 * ((t - 0.15) / 0.05));
                else if (t > 0.78) { double q = (t - 0.78) / 0.22; alpha = (float) (1 - q); sc = (float) (1 + 0.08 * q); }
            }
            case "ringout" -> { double q = bezier(.25, .1, .25, 1, t); sc = (float) (0.1 + 3.5 * q); alpha = (float) (1 - q); }
            // the flat pages' keyframes (u = 12.8 px): dpop .2s ease-out, drise .55s, dslide .28s, dbounce .5s
            case "dpop" -> { double q = bezier(0, 0, .58, 1, t); alpha = (float) q; sc = (float) (0.9 + 0.1 * q); dy = (float) (12.8 * (1 - q)); }
            case "drise" -> { double q = bezier(.2, .8, .3, 1, t); alpha = (float) q; sc = (float) (0.94 + 0.06 * q); dy = (float) (38.4 * (1 - q)); }
            case "dburst" -> { double q = bezier(0, 0, .58, 1, t); alpha = (float) (0.85 * (1 - q)); sc = (float) (0.3 + 1.3 * q); }
            case "draysin" -> { double q = bezier(0, 0, .58, 1, t); alpha = (float) q; rot = (float) (-14 * (1 - q)); sc = (float) (0.7 + 0.3 * q); }
            case "dtwinkle" -> {                                      // 1.1 s ease-in-out, twice: scale 0 -> 1.3 -> 0 with the opacity
                double run = (now - st[0]) / (double) ms;
                if (run >= 2) return;
                double ph = run - Math.floor(run), w = ph < 0.5 ? bezier(.42, 0, .58, 1, ph / 0.5) : 1 - bezier(.42, 0, .58, 1, (ph - 0.5) / 0.5);
                sc = (float) Math.max(0.01, 1.3 * w); alpha = (float) w;
            }
            case "dslide" -> { double q = bezier(0, 0, .58, 1, t); alpha = (float) q; dx = (float) (-25.6 * (1 - q)); }
            case "dbounce" -> { alpha = (float) Math.min(1, t / 0.6); sc = (float) (t < 0.6 ? 0.3 + 0.84 * bezier(.2, .9, .3, 1.2, t / 0.6) : 1.14 - 0.14 * bezier(.2, .9, .3, 1.2, (t - 0.6) / 0.4)); }
            case "beam" -> { sy = (float) Math.min(1, t / 0.35); alpha = (float) (t < 0.35 ? 0.95 - 0.2 * (t / 0.35) : 0.75 * (1 - (t - 0.35) / 0.65)); }
            case "kf" -> {
                UiSpec.Kf k = b.n.kf;
                if (k != null) {
                    double u = k.loop ? ((now - st[0]) % ms) / (double) ms : t;
                    double[] out = new double[7];
                    for (int i = 0; i < 7; i++) {
                        double[] ts = k.ts[i], vs = k.vs[i];
                        if (ts.length == 0) { out[i] = UiSpec.Kf.REST[i]; continue; }
                        if (u <= ts[0]) { out[i] = ts[0] <= 0 ? vs[0] : UiSpec.Kf.REST[i] + (vs[0] - UiSpec.Kf.REST[i]) * bezier(k.ease[0], k.ease[1], k.ease[2], k.ease[3], u / ts[0]); continue; }
                        out[i] = vs[vs.length - 1];
                        for (int j = 0; j + 1 < ts.length; j++) if (u >= ts[j] && u <= ts[j + 1]) {
                            double f = ts[j + 1] > ts[j] ? (u - ts[j]) / (ts[j + 1] - ts[j]) : 1;
                            out[i] = vs[j] + (vs[j + 1] - vs[j]) * bezier(k.ease[0], k.ease[1], k.ease[2], k.ease[3], f);
                            break;
                        }
                    }
                    dx = (float) out[0]; dy = (float) out[1]; sc = (float) out[2]; rot = (float) out[3]; alpha = (float) out[4]; sx = (float) out[5]; sy = (float) out[6];
                }
            }
            default -> { }
        }
        if (kind.equals("wipe") || kind.equals("reveal")) {
            double q = b.n.animSteps > 0 ? Math.floor(t * b.n.animSteps) / b.n.animSteps : bezier(0, 0, .58, 1, t);
            if (q >= 1) { drawNode(g, b, px, py, clip, hot, tip, vx, vy); return; }
            int ax0 = px + b.x, ay0 = py + b.y;
            int[] part = kind.equals("wipe") ? new int[]{ax0 - 8, ay0 - 8, ax0 + (int) Math.round((b.w + 8) * q), ay0 + b.h + 8}
                    : new int[]{ax0 - 4, ay0 - 4, ax0 + (int) Math.round((b.w + 8) * q), ay0 + (int) Math.round((b.h + 8) * q)};
            int[] cl = intersect(clip, part);
            if (cl[2] <= cl[0] || cl[3] <= cl[1]) return;
            float a0 = kind.equals("reveal") ? (float) (0.3 + 0.7 * q) : 1f;
            g.flush();
            if (a0 < 1) RenderSystem.setShaderColor(1f, 1f, 1f, a0 * alphaMul);
            scissor(g, cl);
            drawNode(g, b, px, py, cl, hot, tip, vx, vy);
            g.disableScissor();
            g.flush();
            if (a0 < 1) RenderSystem.setShaderColor(1f, 1f, 1f, alphaMul);
            return;
        }
        float cx = px + b.x + b.w / 2f, cy = py + b.y + b.h / 2f;
        var pose = g.pose();
        boolean fade = alpha < 0.999f;
        float alphaBefore = alphaMul;
        if (fade) { g.flush(); alphaMul = alphaBefore * Math.max(0f, alpha); com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alphaMul); }
        pose.pushPose();
        pose.translate(cx + dx, cy + dy, 0);
        if (rot != 0f) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot));
        if (sc != 1f || sx != 1f || sy != 1f) pose.scale(sc * sx, sc * sy, 1f);
        pose.translate(-cx, -cy, 0);
        drawNode(g, b, px, py, clip, hot, tip, vx, vy);
        if (kind.equals("flipa") && !b.n.hint.isEmpty()) {
            // the charge before a card flips (rc-charging: rarity-coloured inner and outer glow on the back, from the card's charge start to its flip)
            String[] hp = UiExpr.text(b.n.hint, b.ctx).split("\\|");
            long hold = b.n.animHold.isEmpty() ? 0 : (long) num(b.n.animHold, b.ctx, 0);
            try {
                long chg = Long.parseLong(hp[1].trim()), cs = st[0] + delay + hold - chg;
                double a = Math.max(0, Math.min(1, (now + delay - cs) / 200.0));
                int rc = color(hp[0], 0xFFFFFFFF) & 0xFFFFFF;
                if (a > 0) {
                    int x0 = px + b.x, y0 = py + b.y;
                    for (int i = 0; i < 14; i++) {                                   // soft outer glow, strongest at the edge
                        int al = (int) Math.round(255 * a * .55 * Math.pow(1 - i / 14.0, 2));
                        if (al > 0) outline(g, x0 - 1 - i, y0 - 1 - i, b.w + 2 + 2 * i, b.h + 2 + 2 * i, b.n.radius + 1 + i, (al << 24) | rc);
                    }
                    for (int i = 0; i < 12; i++) {                                   // inset glow
                        int al = (int) Math.round(255 * a * .5 * Math.pow(1 - i / 12.0, 2));
                        if (al > 0) outline(g, x0 + i, y0 + i, b.w - 2 * i, b.h - 2 * i, Math.max(0, b.n.radius - i), (al << 24) | rc);
                    }
                    for (int i = 0; i < 2; i++) outline(g, x0 + i, y0 + i, b.w - 2 * i, b.h - 2 * i, Math.max(0, b.n.radius - i), ((int) Math.round(255 * a) << 24) | rc);
                }
            } catch (RuntimeException ignored) { }
        }
        pose.popPose();
        if (fade) { g.flush(); alphaMul = alphaBefore; com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alphaMul); }
    }

    /** CSS cubic-bezier timing: progress at time t (0..1) for control points (x1,y1) (x2,y2). */
    static double bezier(double x1, double y1, double x2, double y2, double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        double u = t;
        for (int i = 0; i < 8; i++) {                     // solve x(u) = t
            double x = 3 * x1 * u * (1 - u) * (1 - u) + 3 * x2 * u * u * (1 - u) + u * u * u - t;
            double dx = 3 * x1 * (1 - u) * (1 - u) + 6 * (x2 - x1) * u * (1 - u) + 3 * (1 - x2) * u * u;
            if (Math.abs(dx) < 1e-6) break;
            u = Math.max(0, Math.min(1, u - x / dx));
        }
        return 3 * y1 * u * (1 - u) * (1 - u) + 3 * y2 * u * u * (1 - u) + u * u * u;
    }

    private void drawNode(GuiGraphics g, Box b, int px, int py, int[] clip, Box hot, List<String> tip, double vx, double vy) {
        UiSpec.Node n = b.n;
        int ax = px + b.x, ay = py + b.y;
        b.ax = ax; b.ay = ay;
        b.clip = clip;
        // background / border
        UiExpr.Ctx c = b.ctx;
        if (!n.screenBg.isEmpty()) {
            // a scrim over the whole window, not just the page (CSS position: fixed; inset: 0): page coordinates of the screen's corners
            int sc0 = color(n.screenBg, 0);
            g.fill(-ox - 1, -oy - 1, (int) Math.ceil(width / scale) - ox + 1, (int) Math.ceil(height / scale) - oy + 1, sc0);
        }
        String bgs = n.bg.isEmpty() ? "" : UiExpr.text(n.bg, c);
        boolean isHot = b == hot && b.enabled && (n.type.equals("button") || n.type.equals("input"));
        if (n.type.equals("button") && !b.enabled && !n.disabled.isEmpty()) bgs = UiExpr.text(n.disabled, c);
        if (isHot && !n.hover.isEmpty()) { String hv = UiExpr.text(n.hover, c); if (!hv.isEmpty()) bgs = hv; }
        if ((bgs.startsWith("tex:") && !bgs.matches("tex:[a-z0-9_/.-]{1,120}[.]png")) || (bgs.startsWith("sprite:") && !bgs.matches("sprite:[a-z0-9_/.-]{1,120}"))) bgs = "";   // data-driven names: never a bad id
        String borderS = n.border.isEmpty() ? "" : UiExpr.text(n.border, c);
        if (isHot && !n.hoverBorder.isEmpty()) { String hb = UiExpr.text(n.hoverBorder, c); if (!hb.isEmpty()) borderS = hb; }
        if (!n.ring.isEmpty()) {
            String[] rp = UiExpr.text(n.ring, c).split(",");
            try {
                int rw = Integer.parseInt(rp[0].trim()), rc = color(rp[1].trim(), 0);
                if (n.ringPulseMs > 0) {
                    // pulsing ring: alpha follows 0 -> 1 -> 0 over ring_pulse ms, for ring_loops loops from the moment it first shows (not drawn while disabled or in calm mode)
                    long nowP = fakeNow >= 0 ? fakeNow : Util.getMillis();
                    String pk = "rp@" + System.identityHashCode(n) + ":" + b.x + ":" + b.y;
                    long[] ps = animState.get(pk);
                    if (ps == null || frameNo - ps[1] > 2) { ps = new long[]{nowP, frameNo}; animState.put(pk, ps); }
                    ps[1] = frameNo;
                    double ph = (nowP - ps[0]) / (double) n.ringPulseMs;
                    boolean live = b.enabled && !"false".equals(UiExpr.text("{vm.anim}", b.ctx)) && (n.ringLoops == 0 || ph < n.ringLoops);
                    double wv = live ? 0.5 - 0.5 * Math.cos(2 * Math.PI * (ph - Math.floor(ph))) : 0;
                    rc = (rc & 0xFFFFFF) | (Math.min(255, (int) Math.round((rc >>> 24) * wv)) << 24);
                }
                if (rw > 0 && (rc >>> 24) != 0) for (int i = 1; i <= rw; i++) outline(g, ax - i, ay - i, b.w + 2 * i, b.h + 2 * i, n.radius + i, rc);
            } catch (RuntimeException ignored) { }
        }
        int[] bsh = n.bshadow.isEmpty() ? null : shadowOf(UiExpr.text(n.bshadow, c));
        if (bsh != null && (bsh[2] >>> 24) != 0) {
            if (n.radii != null) fillRadii(g, ax + bsh[0], ay + bsh[1], b.w, b.h, n.radii, bsh[2]);
            else fillRound(g, ax + bsh[0], ay + bsh[1], b.w, b.h, n.radius, bsh[2]);
        }
        if ((n.cut > 0 || n.cutTl > 0) && !bgs.isEmpty() && !bgs.startsWith("tex:") && !bgs.startsWith("grad:") && !bgs.startsWith("sprite:")) {
            // CSS clip-path panels: the bottom-right (cut) and top-left (cut_tl) corners cut off; solid or vertical-gradient fill
            boolean gv = bgs.startsWith("gradv:");
            int c0, c1;
            if (gv) { String[] gp = bgs.substring(6).split(",", -1); c0 = color(gp.length > 0 ? gp[0] : "", 0); c1 = color(gp.length > 1 ? gp[1] : "", c0); }
            else { c0 = color(bgs, 0); c1 = c0; }
            if (((c0 | c1) >>> 24) != 0) for (int i = 0; i < b.h; i++) {
                double f = b.h <= 1 ? 0 : i / (double) (b.h - 1);
                int col = gv ? (mix(c0, c1, f) & 0xFFFFFF) | ((int) Math.round(((c0 >>> 24) & 255) * (1 - f) + ((c1 >>> 24) & 255) * f) << 24) : c0;
                int li = Math.max(0, n.cutTl - i), ri = Math.max(0, n.cut - (b.h - 1 - i));
                if (b.w - li - ri > 0) g.fill(ax + li, ay + i, ax + b.w - ri, ay + i + 1, col);
            }
            bgs = "";
        }
        if (bgs.startsWith("tex:")) {
            // art rendered from the original page's CSS, shipped in the server's pack: drawn whole, stretched to the box
            ResourceLocation tex = ThemeRegistry.file("textures/gui/pages/" + bgs.substring(4));
            int[] sz = ThemeRegistry.size(tex);
            blend(g).blit(tex, ax, ay, b.w, b.h, 0f, 0f, sz[0], sz[1], sz[0], sz[1]);
        } else if (bgs.startsWith("gradv:")) {
            String[] gp = bgs.substring(6).split(",", -1);
            int c0 = color(gp.length > 0 ? gp[0] : "", 0), c1 = color(gp.length > 1 ? gp[1] : "", c0);   // data not there yet: nothing drawn
            int[] rr = n.radii != null ? n.radii : new int[]{n.radius, n.radius, n.radius, n.radius};
            for (int i = 0; i < b.h; i++) {
                double f = b.h <= 1 ? 0 : i / (double) (b.h - 1);
                int a = (int) Math.round(((c0 >>> 24) & 255) * (1 - f) + ((c1 >>> 24) & 255) * f);
                int col = (mix(c0, c1, f) & 0xFFFFFF) | (a << 24);
                int l = Math.max(insetAt(Math.min(rr[0], b.h / 2), i), insetAt(Math.min(rr[3], b.h / 2), b.h - 1 - i));
                int r = Math.max(insetAt(Math.min(rr[1], b.h / 2), i), insetAt(Math.min(rr[2], b.h / 2), b.h - 1 - i));
                g.fill(ax + l, ay + i, ax + b.w - r, ay + i + 1, col);
            }
        } else if (!bgs.isEmpty() && !bgs.startsWith("grad:") && !bgs.startsWith("sprite:")) {
            int col = color(bgs, 0);
            if ((col >>> 24) != 0) {
                if (n.radii != null) fillRadii(g, ax, ay, b.w, b.h, n.radii, col);
                else fillRound(g, ax, ay, b.w, b.h, n.radius, col);
            }
        } else if (bgs.startsWith("sprite:")) {
            blend(g).blitSprite(ThemeRegistry.sprite(bgs.substring(7)), ax, ay, b.w, b.h);
        } else if (bgs.startsWith("grad:")) {
            if (n.radius > 0) gradHRound(g, ax, ay, b.w, b.h, bgs, n.radius); else gradH(g, ax, ay, b.w, b.h, bgs);
        }
        if ((n.cut > 0 || n.cutTl > 0) && !borderS.isEmpty() && n.bw > 0) {
            int bc = color(borderS, 0x55FFFFFF), c0 = n.cut, t0 = n.cutTl, bw0 = n.bw;
            g.fill(ax + t0, ay, ax + b.w, ay + bw0, bc);                               // top, from the top-left cut
            g.fill(ax, ay + t0, ax + bw0, ay + b.h, bc);                               // left, from the top-left cut
            g.fill(ax + b.w - bw0, ay, ax + b.w, ay + b.h - c0, bc);                   // right, down to the cut
            g.fill(ax, ay + b.h - bw0, ax + b.w - c0, ay + b.h, bc);                   // bottom, up to the cut
            for (int i = 0; i < c0; i++) {                                              // bottom-right diagonal
                int yy = ay + b.h - c0 + i, xx = ax + b.w - i - 1;
                g.fill(xx - bw0 + 1, yy, xx + 1, yy + 1, bc);
            }
            for (int i = 0; i < t0; i++) {                                              // top-left diagonal
                int yy = ay + t0 - 1 - i, xx = ax + i;
                g.fill(xx, yy, xx + bw0, yy + 1, bc);
            }
            borderS = "";
        }
        if (n.bcolors != null && n.bw > 0) {
            // CSS "border-color: top right bottom left" (the bevelled panels of the Minecraft-style pages)
            int ct = color(UiExpr.text(n.bcolors[0], c), 0), cr = color(UiExpr.text(n.bcolors[1], c), 0), cb = color(UiExpr.text(n.bcolors[2], c), 0), cl = color(UiExpr.text(n.bcolors[3], c), 0);
            int w0 = n.bw;
            for (int i = 0; i < w0; i++) {
                g.fill(ax + i, ay + i, ax + b.w - i, ay + i + 1, ct);                       // top (mitred corners like CSS)
                g.fill(ax + i, ay + b.h - i - 1, ax + b.w - i, ay + b.h - i, cb);           // bottom
                g.fill(ax + i, ay + i + 1, ax + i + 1, ay + b.h - i - 1, cl);               // left
                g.fill(ax + b.w - i - 1, ay + i + 1, ax + b.w - i, ay + b.h - i - 1, cr);   // right
            }
        } else if (!borderS.isEmpty() && n.bw > 0) {
            int bc = color(borderS, 0x55FFFFFF);
            if (n.dash) dashed(g, ax, ay, b.w, b.h, n.bw, bc, n.bsides.isEmpty() ? "trbl" : n.bsides);
            else if (n.radii != null || !n.bsides.isEmpty() || !n.btop.isEmpty()) {
                int[] rr = n.radii != null ? n.radii : new int[]{n.radius, n.radius, n.radius, n.radius};
                String bsd = n.bsides.isEmpty() ? "trbl" : n.bsides;
                int tw = n.bw, tc = bc;
                if (!n.btop.isEmpty()) { String[] tp = UiExpr.text(n.btop, c).split(","); try { tw = Integer.parseInt(tp[0].trim()); tc = color(tp[1].trim(), bc); } catch (RuntimeException ignored) { } }
                borderSides(g, ax, ay, b.w, b.h, rr, n.bw, bc, bsd, tw, tc);
            }
            else for (int i = 0; i < n.bw; i++) outline(g, ax + i, ay + i, b.w - 2 * i, b.h - 2 * i, Math.max(0, n.radius - i), bc);
        }

        switch (n.type) {
            case "label" -> {
                int ix = ax + n.pad[3], iy = ay + n.pad[0], iw = b.w - padW(n);
                double sc = n.scale;
                if (b.pick != null) {
                    double lh = lineH(n, b.pick);
                    String shownText = b.text;
                    if (n.ellipsis && !n.wrap && textWidth(shownText, n, b.pick) > iw) {
                        while (shownText.length() > 1 && textWidth(shownText + "...", n, b.pick) > iw) shownText = shownText.substring(0, shownText.length() - 1);
                        shownText = shownText + "...";
                    }
                    final String st0 = shownText;
                    if (n.clip) scissor(g, intersect(clip, new int[]{ax, ay, ax + b.w, ay + b.h}));
                    List<FormattedCharSequence> ls2 = n.wrap ? b.lines : List.of(seq(st0, n, b.pick));
                    double yy = iy;
                    if (b.gstops != null && !n.wrap && !n.rich) { lineStops = b.gstops; lineX0 = ax; lineW = b.w; }
                    for (FormattedCharSequence l : ls2) {
                        double lw = n.wrap ? textWidth(l, n, b.pick) : textWidth(st0, n, b.pick);
                        double lx = switch (n.textAlign) { case "center" -> ix + (iw - lw) / 2; case "right" -> ix + iw - lw; default -> ix; };
                        drawLine(g, l, n.wrap || n.rich ? null : st0, lx, yy, b.color, n, b.pick, c);
                        yy += lh;
                    }
                    lineStops = null;
                    if (n.clip) g.disableScissor();
                    break;
                }
                var pose = g.pose();
                List<FormattedCharSequence> ls = n.wrap ? b.lines : List.of(Component.literal(b.text).getVisualOrderText());
                int y = iy;
                for (FormattedCharSequence l : ls) {
                    int lw = (int) Math.ceil(font.width(l) * sc);
                    int lx = switch (n.textAlign) { case "center" -> ix + (iw - lw) / 2; case "right" -> ix + iw - lw; default -> ix; };
                    pose.pushPose();
                    pose.translate(lx, y, 0);
                    pose.scale((float) sc, (float) sc, 1f);
                    g.drawString(font, l, 0, 0, b.color, n.shadow);
                    pose.popPose();
                    y += (int) Math.ceil(LINE * sc);
                }
            }
            case "button" -> {
                if (b.kids.isEmpty() && !b.text.isEmpty() && b.pick != null) {
                    double tw = textWidth(b.text, n, b.pick), lh = lineH(n, b.pick);
                    int col = b.enabled ? (isHot && !n.hoverColor.isEmpty() ? color(UiExpr.text(n.hoverColor, c), b.color) : b.color) : (b.color & 0x00FFFFFF) | 0x66000000;
                    int iw = b.w - padW(n), ih = b.h - padH(n);
                    double tx = ax + n.pad[3] + switch (n.textAlign) { case "left" -> 0.0; case "right" -> iw - tw; default -> (iw - tw) / 2; };
                    drawLine(g, seq(b.text, n, b.pick), b.text, tx, ay + n.pad[0] + (ih - lh) / 2, col, n, b.pick, c);
                } else if (b.kids.isEmpty() && !b.text.isEmpty()) {
                    int tw = font.width(b.text);
                    int col = b.enabled ? b.color : 0xFF909090;
                    g.drawString(font, b.text, ax + (b.w - tw) / 2, ay + (b.h - 8) / 2, col, n.shadow);
                }
            }
            case "input" -> {
                String val = localText(n.bind);
                boolean f = n.id.equals(focus);
                if (b.pick != null) {
                    var pk = b.pick;
                    double lh = lineH(n, pk), tx0 = ax + n.pad[3] + 2;
                    int innerW = b.w - padW(n) - 4;
                    scissor(g, intersect(clip, new int[]{ax + 2, ay + 2, ax + b.w - 2, ay + b.h - 2}));
                    String hintT = UiExpr.text(n.hint, c);
                    if (n.wrap) {
                        double ty0 = ay + n.pad[0] + 2, lh2 = n.lh > 0 ? n.lh : pk.px() * 1.4;
                        UiSpec.Node tmp = n;
                        if (val.isEmpty() && !f) drawLine(g, seq(hintT, n, pk), hintT, tx0, ty0, n.hintColor.isEmpty() ? 0xFF757575 : color(n.hintColor, 0xFF757575), n, pk, c);
                        else {
                            var parts = splitText(val, innerW, n, pk);
                            int rows = Math.max(1, (int) ((b.h - padH(n)) / lh2)), from = Math.max(0, parts.size() - rows);
                            for (int li = from; li < parts.size(); li++) drawLine(g, parts.get(li), null, tx0, ty0 + (li - from) * lh2, b.color, tmp, pk, c);
                            if (f && (Util.getMillis() / 500) % 2 == 0) {
                                double lw = parts.isEmpty() ? 0 : textWidth(parts.get(parts.size() - 1), n, pk);
                                double cy = ty0 + (Math.max(0, parts.size() - 1) - from) * lh2;
                                g.fill((int) (tx0 + lw), (int) cy + 2, (int) (tx0 + lw) + 2, (int) (cy + lh2) - 2, b.color);
                            }
                        }
                    } else {
                        double ty0 = ay + (b.h - lh) / 2;
                        if (val.isEmpty() && !f) drawLine(g, seq(hintT, n, pk), hintT, tx0, ty0, n.hintColor.isEmpty() ? 0xFF757575 : color(n.hintColor, 0xFF757575), n, pk, c);
                        else {
                            String shown = val;
                            while (shown.length() > 0 && textWidth(shown, n, pk) > innerW) shown = shown.substring(1);
                            drawLine(g, seq(shown, n, pk), shown, tx0, ty0, b.color, n, pk, c);
                            if (f && (Util.getMillis() / 500) % 2 == 0) {
                                double lw = textWidth(shown, n, pk);
                                g.fill((int) (tx0 + lw), (int) ty0 + 2, (int) (tx0 + lw) + 2, (int) (ty0 + lh) - 2, b.color);
                            }
                        }
                    }
                    g.disableScissor();
                    break;
                }
                int tx = ax + 5, ty = ay + (b.h - 8) / 2;
                scissor(g, intersect(clip, new int[]{ax + 2, ay, ax + b.w - 2, ay + b.h}));
                if (val.isEmpty() && !f) g.drawString(font, n.hint, tx, n.wrap ? ay + 4 : ty, 0xFF6C7C99, false);
                else if (n.wrap) {
                    // a multi-line field: the text wraps to the box and the newest lines stay in view
                    var parts = font.split(net.minecraft.network.chat.Component.literal(val), Math.max(10, b.w - 12));
                    int rows = Math.max(1, (b.h - 6) / 10), from = Math.max(0, parts.size() - rows), y0 = ay + 4;
                    for (int li = from; li < parts.size(); li++) g.drawString(font, parts.get(li), tx, y0 + (li - from) * 10, b.color, false);
                    if (f && (Util.getMillis() / 500) % 2 == 0) {
                        int lastW = parts.isEmpty() ? 0 : font.width(parts.get(parts.size() - 1));
                        int cy = y0 + (Math.max(0, parts.size() - 1) - from) * 10;
                        g.fill(tx + lastW, cy - 1, tx + lastW + 1, cy + 9, 0xFFEEF3FB);
                    }
                } else {
                    String shown = val;
                    int over = font.width(shown) - (b.w - 12);
                    if (over > 0) shown = font.plainSubstrByWidth(shown, b.w - 12, true);
                    g.drawString(font, shown, tx, ty, b.color, false);
                    if (f && (Util.getMillis() / 500) % 2 == 0) g.fill(tx + font.width(shown), ty - 1, tx + font.width(shown) + 1, ty + 9, 0xFFEEF3FB);
                }
                g.disableScissor();
                if (f) outline(g, ax, ay, b.w, b.h, n.radius, 0xFF3A7BC0);
            }
            case "bar" -> {
                JsonElement v = n.value.isEmpty() ? null : UiExpr.calc(n.value, c);
                JsonElement m = n.max.isEmpty() ? null : UiExpr.calc(n.max, c);
                double val = v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsDouble() : 0;
                if (n.easeMs > 0) val = ease("bar@" + System.identityHashCode(n) + ":" + b.ctx.hashCode(), val, n.easeMs);
                double mx = m != null && m.isJsonPrimitive() && m.getAsJsonPrimitive().isNumber() ? m.getAsDouble() : 100;
                int fw = mx <= 0 ? 0 : (int) Math.round(b.w * Math.max(0, Math.min(1, val / mx)));
                String fs = n.fill.isEmpty() ? "#4ADE80" : UiExpr.text(n.fill, c);
                if (!n.fillBg.isEmpty()) fillRound(g, ax, ay, b.w, b.h, n.radius, color(UiExpr.text(n.fillBg, c), 0xFF223048));
                if (fw > 0) {
                    if (fs.startsWith("grad:")) gradH(g, ax, ay, fw, b.h, fs, b.w);
                    else fillRound(g, ax, ay, fw, b.h, n.radius, color(fs, 0xFF4ADE80));
                }
            }
            case "pixtext" -> drawPix(g, b, ax + n.pad[3], ay + n.pad[0], c);
            case "burst" -> {
                // purchase celebration (the gem shop's burst()): 120 sparkle squares/stars, an expanding prism ring and 8 rising gems, on a 1000x563 canvas laid over this box.
                // text = "ox,oy" origin in canvas units; fill = restart key (empty = nothing); hint = "tex:..." gem picture. Runs ~2.5 s after the key changes; not drawn in calm mode.
                String key = n.fill.isEmpty() ? "" : UiExpr.text(n.fill, c);
                if (key.isEmpty() || "false".equals(UiExpr.text("{vm.anim}", b.ctx))) break;
                long nowB = fakeNow >= 0 ? fakeNow : Util.getMillis();
                String bk = "burst@" + System.identityHashCode(n) + "|" + key;
                long[] bs = animState.get(bk);
                if (bs == null) { bs = new long[]{nowB, frameNo}; animState.put(bk, bs); }
                int frames = (int) ((nowB - bs[0]) * 60 / 1000);
                if (frames > 150) break;
                String[] op = UiExpr.text(n.text, c).split(",");
                double ox = 500, oy = 280;
                try { ox = Double.parseDouble(op[0].trim()); oy = Double.parseDouble(op[1].trim()); } catch (RuntimeException ignored) { }
                double sx = b.w / 1000.0, sy = b.h / 563.0;
                int[] prism = {0xFFFF9AF0, 0xFFD65AEA, 0xFF8A44F5, 0xFF4C7CFF, 0xFF3DDCFF, 0xFFB4F7FF};
                java.util.Random rnd = new java.util.Random(key.hashCode() * 31L + 7);
                // ring
                int ring = Math.min(frames, 36);
                if (ring > 0 && ring < 36) {
                    double rr = ring * 8, ra = Math.max(0, 1 - ring / 36.0);
                    for (int i = 0; i < 160; i++) {
                        double a = i / 160.0 * 2 * Math.PI, bpx = ox + Math.cos(a) * rr, bpy = oy + Math.sin(a) * rr;
                        double t = Math.max(0, Math.min(1, (bpx - (ox - rr)) / (2 * rr))) * 5; int k0 = Math.min((int) t, 4); double f = t - k0;
                        int c0 = prism[k0], c1 = prism[k0 + 1];
                        int cr = (int) (((c0 >> 16 & 255) * (1 - f)) + ((c1 >> 16 & 255) * f)), cg = (int) (((c0 >> 8 & 255) * (1 - f)) + ((c1 >> 8 & 255) * f)), cb = (int) (((c0 & 255) * (1 - f)) + ((c1 & 255) * f));
                        int col = ((int) (ra * 255) << 24) | (cr << 16) | (cg << 8) | cb;
                        int x0 = (int) Math.round(ax + (bpx - 2.5) * sx), y0 = (int) Math.round(ay + (bpy - 2.5) * sy);
                        g.fill(x0, y0, x0 + Math.max(2, (int) Math.round(5 * sx)), y0 + Math.max(2, (int) Math.round(5 * sy)), col);
                    }
                }
                // sparkles: simulate frame by frame (the page's own physics: gravity .09, drag .985)
                for (int i = 0; i < 120; i++) {
                    double a = rnd.nextDouble() * 6.283, sp = 1.5 + rnd.nextDouble() * 6.5, L = 46 + rnd.nextDouble() * 44, s = 2 + rnd.nextDouble() * 3.5, ct = rnd.nextDouble();
                    boolean star = rnd.nextDouble() < .4;
                    double x = ox, y = oy, bvx = Math.cos(a) * sp, bvy = Math.sin(a) * sp - 1.2;
                    int life = (int) Math.min(frames, L);
                    for (int f = 0; f < life; f++) { x += bvx; y += bvy; bvy += .09; bvx *= .985; }
                    if (frames >= L) continue;
                    double al = Math.max(0, 1 - frames / L);
                    double t = ct * 5; int k0 = Math.min((int) t, 4); double fr = t - k0;
                    int c0 = prism[k0], c1 = prism[k0 + 1];
                    int cr = (int) (((c0 >> 16 & 255) * (1 - fr)) + ((c1 >> 16 & 255) * fr)), cg = (int) (((c0 >> 8 & 255) * (1 - fr)) + ((c1 >> 8 & 255) * fr)), cb = (int) (((c0 & 255) * (1 - fr)) + ((c1 & 255) * fr));
                    int col = ((int) (al * 255) << 24) | (cr << 16) | (cg << 8) | cb;
                    if (star) {
                        fillC(g, ax, ay, sx, sy, x - s * 1.6, y - s * .4, s * 3.2, s * .8, col);
                        fillC(g, ax, ay, sx, sy, x - s * .4, y - s * 1.6, s * .8, s * 3.2, col);
                    } else fillC(g, ax, ay, sx, sy, x - s / 2, y - s / 2, s, s, col);
                }
                // rising gems
                String gs = n.hint.isEmpty() ? "" : UiExpr.text(n.hint, c);
                if (gs.matches("tex:[a-z0-9_/.-]{1,120}[.]png")) {
                    ResourceLocation gt = ThemeRegistry.file("textures/gui/pages/" + gs.substring(4));
                    int[] gz = ThemeRegistry.size(gt);
                    for (int i = 0; i < 8; i++) {
                        double gx0 = ox + (rnd.nextDouble() - .5) * 190, gy0 = oy + 10, bvy = -1 - rnd.nextDouble() * 1.6, ph = rnd.nextDouble() * 6, L = 80 + rnd.nextDouble() * 30, s = 14 + rnd.nextDouble() * 12;
                        if (frames >= L) continue;
                        double gx = gx0, gy = gy0;
                        for (int f = 1; f <= frames; f++) { gy += bvy; gx += Math.sin(f / 9.0 + ph) * .9; }
                        float al = (float) Math.max(0, Math.min(1, (L - frames) / 30.0));
                        g.flush(); RenderSystem.setShaderColor(1f, 1f, 1f, alphaMul * al);
                        blend(g).blit(gt, (int) Math.round(ax + (gx - s / 2) * sx), (int) Math.round(ay + (gy - s / 2) * sy), (int) Math.round(s * sx), (int) Math.round(s * sy), 0f, 0f, gz[0], gz[1], gz[0], gz[1]);
                        g.flush(); RenderSystem.setShaderColor(1f, 1f, 1f, alphaMul);
                    }
                }
            }
            case "sparks", "goldsweep" -> {
                // auction purchase effects. sparks: 18 gold/white squares fly out 140-320 px from text="ox,oy" over 900 ms (ease-out, fade, shrink); goldsweep: a gold band crosses this box in 600 ms.
                // fill = restart key (empty = nothing). Not drawn in calm mode.
                String key = n.fill.isEmpty() ? "" : UiExpr.text(n.fill, c);
                if (key.isEmpty() || "false".equals(UiExpr.text("{vm.anim}", b.ctx))) break;
                long nowS = fakeNow >= 0 ? fakeNow : Util.getMillis();
                String sk = n.type + "@" + System.identityHashCode(n) + ":" + b.x + ":" + b.y + "|" + key;
                long[] ss = animState.get(sk);
                if (ss == null) { ss = new long[]{nowS, frameNo}; animState.put(sk, ss); }
                long el = nowS - ss[0];
                if (n.type.equals("goldsweep")) {
                    if (el > 600) break;
                    double u = el / 600.0, e = u < .5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2;
                    double left = -120 + (b.w * 1.1 + 120) * e;
                    for (int i = 0; i < 24; i++) {
                        double fx0 = left + i * 5.0, prof = 1 - Math.abs((i + .5) / 12.0 - 1);
                        int x0 = (int) Math.max(0, fx0), x1 = (int) Math.min(b.w, fx0 + 5);
                        if (x1 <= x0) continue;
                        g.fill(ax + x0, ay, ax + x1, ay + b.h, ((int) (0.55 * prof * 255) << 24) | 0xFFD24A);
                    }
                } else {
                    if (el > 1300) break;
                    String[] op = UiExpr.text(n.text, c).split(",");
                    double ox = 600, oy = 360;
                    try { ox = Double.parseDouble(op[0].trim()); oy = Double.parseDouble(op[1].trim()); } catch (RuntimeException ignored) { }
                    java.util.Random rnd = new java.util.Random(key.hashCode() * 17L + 3);
                    for (int i = 0; i < 18; i++) {
                        double a = (i / 18.0) * 2 * Math.PI + rnd.nextDouble() * 0.3, r = 140 + rnd.nextDouble() * 180, dly = Math.round(rnd.nextDouble() * 120);
                        double u = Math.max(0, Math.min(1, (el - dly) / 900.0));
                        if (el < dly || u >= 1) continue;
                        double ez = 1 - (1 - u) * (1 - u), sz = 7 * (1.2 - 0.9 * u);
                        double sxp = ox + Math.cos(a) * r * ez, syp = oy + Math.sin(a) * r * ez;
                        int col = ((int) ((1 - u) * 255) << 24) | (i % 3 == 0 ? 0xFFFFFF : 0xFFD24A);
                        int x0 = (int) Math.round(ax + sxp - sz / 2), y0 = (int) Math.round(ay + syp - sz / 2);
                        g.fill(x0, y0, x0 + Math.max(1, (int) Math.round(sz)), y0 + Math.max(1, (int) Math.round(sz)), col);
                    }
                }
            }
            case "capfx" -> {
                // AscensionLib capture reveal flip effects (capture-flip.js): bloom (360 ms, 1350 ms), shockwave (1044 ms, 720 ms) and 28 shards/sparks (from 1044 ms), all round the card centre.
                // text = "cx,cy,designW,rarityIndex,cardW,cardH" (design units, relative to this box); hint = "#rarity|#secondary"; fill = restart key (empty = nothing). Not drawn in calm mode.
                String key = n.fill.isEmpty() ? "" : UiExpr.text(n.fill, c);
                if (key.isEmpty() || "false".equals(UiExpr.text("{vm.anim}", b.ctx))) break;
                long nowF = fakeNow >= 0 ? fakeNow : Util.getMillis();
                String fk = "capfx@" + System.identityHashCode(n) + "|" + key;
                long[] fs = animState.get(fk);
                if (fs == null) { fs = new long[]{nowF, frameNo}; animState.put(fk, fs); }
                long el = nowF - fs[0];
                if (el > 2000) break;
                String[] fp = UiExpr.text(n.text, c).split(",");
                String[] cols = UiExpr.text(n.hint, c).split("\\|");
                double fcx = 236, fcy = 314, fdw = 472, cw = 472, ch = 628;
                int ridx = 0;
                try { fcx = Double.parseDouble(fp[0]); fcy = Double.parseDouble(fp[1]); fdw = Double.parseDouble(fp[2]); ridx = Integer.parseInt(fp[3].trim()); cw = Double.parseDouble(fp[4]); ch = Double.parseDouble(fp[5]); } catch (RuntimeException ignored) { }
                int rcol = color(cols.length > 0 ? cols[0] : "#4ad8f6", 0xFF4AD8F6) & 0xFFFFFF, scol = color(cols.length > 1 ? cols[1] : "#bdf1ff", 0xFFBDF1FF) & 0xFFFFFF;
                double fsc = b.w / fdw, power = .25 + ridx * .15;
                // bloom: radial-gradient(ellipse, aura-soft, transparent 65%) over the card inset -25%, opacity 0 -> .75*power -> 0, scale .6 -> 1.1 -> 1.3
                if (el >= 360 && el < 1710) {
                    double u = (el - 360) / 1350.0, q = bezier(0, 0, .58, 1, u);
                    double op = q < .45 ? .75 * power * (q / .45) : .75 * power * (1 - (q - .45) / .55), scb = q < .45 ? .6 + .5 * (q / .45) : 1.1 + .2 * ((q - .45) / .55);
                    double rx = cw * 1.5 / 2 * Math.sqrt(2) * .65 * scb * fsc, ry = ch * 1.5 / 2 * Math.sqrt(2) * .65 * scb * fsc;
                    int bands = 14;
                    double cum = 0;
                    for (int k = bands; k >= 1; k--) {
                        double f = k / (double) bands, target = op * .5 * (1 - f + .5 / bands), a = Math.max(0, (target - cum) / (1 - cum));
                        cum = target;
                        int col = ((int) Math.round(Math.min(1, a) * 255) << 24) | rcol;
                        if ((col >>> 24) == 0) continue;
                        double erx = rx * f, ery = ry * f, ecx = ax + fcx * fsc, ecy = ay + fcy * fsc;
                        for (double yy = -ery; yy < ery; yy += 3) {
                            double hw = erx * Math.sqrt(Math.max(0, 1 - (yy + 1.5) * (yy + 1.5) / (ery * ery)));
                            g.fill((int) Math.round(ecx - hw), (int) Math.round(ecy + yy), (int) Math.round(ecx + hw), (int) Math.round(ecy + yy + 3), col);
                        }
                    }
                }
                // shockwave: a 310 px square outline (2 px, rarity colour, soft glow), scale .5 -> .8 -> 2.1, rotated 45 -> 60 degrees, opacity 0 -> power -> 0
                if (el >= 1044 && el < 1764) {
                    double u = (el - 1044) / 720.0, q = bezier(0, 0, .58, 1, u);
                    double op = q < .1 ? power * (q / .1) : power * (1 - (q - .1) / .9), scs = q < .1 ? .5 + .3 * (q / .1) : .8 + 1.3 * ((q - .1) / .9), rot = q < .1 ? 45 : 45 + 15 * ((q - .1) / .9);
                    var ps = g.pose();
                    ps.pushPose();
                    ps.translate(ax + fcx * fsc, ay + fcy * fsc, 0);
                    ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) rot));
                    ps.scale((float) (fsc * scs), (float) (fsc * scs), 1f);
                    for (int gl = 0; gl < 3; gl++) {
                        int pad = gl * 3, wd = gl == 0 ? 2 : 3;
                        int col = ((int) Math.round(op * (gl == 0 ? 1 : .18) * 255) << 24) | rcol;
                        int h = 155 + pad;
                        g.fill(-h, -h, h, -h + wd, col); g.fill(-h, h - wd, h, h, col); g.fill(-h, -h + wd, -h + wd, h - wd, col); g.fill(h - wd, -h + wd, h, h - wd, col);
                    }
                    ps.popPose();
                }
                // 28 particles: i < 10 shards (32x92 triangles), the rest 6x6 sparks; flying out along the golden angle
                for (int i = 0; i < 28; i++) {
                    double dl = 1044 + (i % 4) * 29, dur = 540 + (i % 3) * 56, u = (el - dl) / dur;
                    if (u < 0 || u >= 1) continue;
                    double q = bezier(.12, .72, .3, 1, u);
                    boolean shard = i < 10;
                    double w = shard ? 32 : 6, h = shard ? 92 : 6;
                    double peak = power * (shard ? .7 : 1), op = q < .12 ? peak * (q / .12) : peak * (1 - (q - .12) / .88);
                    double ang = i * 2.39996, dist = (230 + i % 5 * 45) * (.55 + ridx * .09);
                    double tx = (-w / 2) + (Math.cos(ang) * dist + w / 2) * q, ty = (-h / 2) + (Math.sin(ang) * dist * .95 + h / 2) * q;
                    double sc2 = .2 + .6 * q, rot = i * 37 + 70 * q;
                    var ps = g.pose();
                    ps.pushPose();
                    ps.translate(ax + (fcx + tx + w / 2) * fsc, ay + (fcy + ty + h / 2) * fsc, 0);
                    ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) rot));
                    ps.scale((float) (fsc * sc2), (float) (fsc * sc2), 1f);
                    if (shard) {
                        int col = ((int) Math.round(Math.min(1, op * 1.2) * 255) << 24) | (i % 2 == 0 ? rcol : scol);
                        for (int yy = 0; yy < 92; yy++) {
                            double hw = yy < 55.2 ? 16 * yy / 55.2 + 0.5 : Math.max(0.5, 16 * (1 - (yy - 55.2) / 36.8) + 0.5);
                            g.fill((int) Math.round(-hw), yy - 46, (int) Math.round(hw), yy - 45, col);
                        }
                    } else {
                        g.fill(-7, -7, 7, 7, ((int) Math.round(op * .25 * 255) << 24) | rcol);
                        g.fill(-3, -3, 3, 3, ((int) Math.round(op * 255) << 24) | rcol);
                    }
                    ps.popPose();
                }
            }
            case "curve" -> {
                // a bezier from (x1,y1) to (x2,y2) with horizontal handles, in 0..100 units of this box (the route map's svg edges); text = "x1,y1,x2,y2,dashed"
                String[] pt = UiExpr.text(n.text, c).split(",");
                if (pt.length >= 4) try {
                    double x1 = Double.parseDouble(pt[0].trim()), y1 = Double.parseDouble(pt[1].trim()), x2 = Double.parseDouble(pt[2].trim()), y2 = Double.parseDouble(pt[3].trim());
                    boolean dashed = pt.length > 4 && pt[4].trim().equals("1");
                    int col = color(n.fill.isEmpty() ? "#ffffff" : UiExpr.text(n.fill, c), 0xFFFFFFFF), lw = Math.max(1, n.bw);
                    boolean straight = pt.length > 5 && pt[5].trim().equals("1");
                    double gx = (x1 + x2) / 2, cpx = x1, cpy = y1;
                    int N = 28;
                    for (int i = 1; i <= N; i++) {
                        double t = i / (double) N, u1 = 1 - t;
                        double qx = straight ? x1 + (x2 - x1) * t : u1 * u1 * u1 * x1 + 3 * u1 * u1 * t * gx + 3 * u1 * t * t * gx + t * t * t * x2;
                        double qy = straight ? y1 + (y2 - y1) * t : u1 * u1 * u1 * y1 + 3 * u1 * u1 * t * y1 + 3 * u1 * t * t * y2 + t * t * t * y2;
                        if (!dashed || i % 4 < 2) {
                            double ax0 = ax + cpx / 100.0 * b.w, ay0 = ay + cpy / 100.0 * b.h, ax1 = ax + qx / 100.0 * b.w, ay1 = ay + qy / 100.0 * b.h;
                            double len = Math.hypot(ax1 - ax0, ay1 - ay0);
                            var ps = g.pose();
                            ps.pushPose();
                            ps.translate(ax0, ay0, 0);
                            ps.mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(ay1 - ay0, ax1 - ax0)));
                            g.fill(0, -lw / 2, (int) Math.ceil(len) + 1, lw - lw / 2, col);
                            ps.popPose();
                        }
                        cpx = qx; cpy = qy;
                    }
                } catch (NumberFormatException ignored) { }
            }
            case "sweep" -> {
                double travel = num(n.travel, c, 1000), good = num(n.good, c, 0), perfect = num(n.perfect, c, 0);
                long start = n.anchor.isEmpty() ? 0 : (long) num(n.anchor, c, 0);
                if (start <= 0) start = sweepStart.computeIfAbsent(System.identityHashCode(n), key -> Util.getMillis());
                double phase = travel <= 0 ? 0 : ((Util.getMillis() - start) % (2 * travel)) / travel;
                double pos = phase <= 1 ? phase : 2 - phase;
                // the capture page's track (capture.css #track .zones): tick marks, the gold "good" and cyan "perfect" zones,
                // the centre line and the white marker that sweeps edge to edge (centred on its position, overhanging the track)
                int mid = ax + b.w / 2;
                double share = Math.max(0.004, 0);
                int gw = (int) Math.max(1, Math.round(b.w * Math.max(0.004, Math.min(1, good / travel)))), pw = (int) Math.max(1, Math.round(b.w * Math.max(0.004, Math.min(1, perfect / travel))));
                int t0 = ay + (int) Math.round(b.h * 0.35), t1 = ay + b.h - (int) Math.round(b.h * 0.35);
                for (int i = 1; i < 16; i++) g.fill(ax + b.w * i / 16, t0, ax + b.w * i / 16 + 1, t1, 0x8046E3FF);
                g.fill(mid - gw / 2, ay, mid - gw / 2 + gw, ay + b.h, 0xFFFFB32F);
                g.fill(mid - pw / 2, ay, mid - pw / 2 + pw, ay + b.h, 0xFF00CDEB);
                int m0 = ay + (int) Math.round(b.h * 0.22), m1 = ay + b.h - (int) Math.round(b.h * 0.22), mw = Math.max(1, Math.round(b.h * 0.07f));
                g.fill(mid - mw / 2, m0, mid - mw / 2 + mw, m1, 0xFFEAFBFF);
                int mk = Math.max(2, Math.round(b.h * 0.16f)), over = mk;           // marker .5cqw wide, .5cqw above and below the zones
                int mx = ax + (int) Math.round(b.w * pos) - mk / 2;
                g.fill(mx - 2, ay - over - 2, mx + mk + 2, ay + b.h + over + 2, 0x40FFFFFF);   // glow
                g.fill(mx, ay - over, mx + mk, ay + b.h + over, 0xFFFFFFFF);
            }
            case "image" -> {
                String t = n.texture.isEmpty() ? "" : UiExpr.text(n.texture, c);
                String sp = n.sprite.isEmpty() ? "" : UiExpr.text(n.sprite, c);
                String pk = n.pokemon.isEmpty() ? "" : UiExpr.text(n.pokemon, c);
                if (!pk.isEmpty()) {
                    var tex = PokemonSprites.get(pk);
                    if (tex != null) {
                        int iw = tex.w(), ih = tex.h();
                        double k = Math.min(b.w / (double) iw, b.h / (double) ih);
                        if (n.natural) k = Math.max(1, Math.floor(Math.min((n.size > 0 ? n.size : Math.max(b.w, b.h)) / (double) Math.max(iw, ih), k)));   // whole multiples, like the page
                        int dw = Math.max(1, (int) (iw * k)), dh = Math.max(1, (int) (ih * k));
                        blend(g).blit(tex.id(), ax + (b.w - dw) / 2, ay + (b.h - dh) / 2, dw, dh, 0f, 0f, iw, ih, iw, ih);
                    }
                } else if (!sp.isEmpty()) blend(g).blitSprite(ThemeRegistry.sprite(sp), ax, ay, b.w, b.h);
                else if (t.startsWith("textures/")) {
                    ResourceLocation tex = ThemeRegistry.file(t);
                    int[] sz = ThemeRegistry.size(tex);
                    blend(g).blit(tex, ax, ay, b.w, b.h, 0f, 0f, sz[0], sz[1], sz[0], sz[1]);
                }
            }
            case "item" -> {
                String id = UiExpr.text(n.item, c);
                var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(id));
                if (item.isPresent()) {
                    ItemStack st = new ItemStack(item.get(), n.count);
                    float s = b.w > 0 ? b.w / 16f : 1f;
                    var pose = g.pose();
                    pose.pushPose();
                    pose.translate(ax, ay, 0);
                    pose.scale(s, s, 1f);
                    g.renderItem(st, 0, 0);
                    pose.popPose();
                }
            }
            default -> { }
        }

        if (b == hot && !n.tooltip.isEmpty() && (!n.tipWait || (animBusy == 0 && !subtreeBusy(b, fakeNow >= 0 ? fakeNow : Util.getMillis())))) { for (String l : n.tooltip) { String tl = UiExpr.text(l, c); if (!tl.isEmpty()) tip.add(tl); } tipAnchor = new int[]{ax, ay, b.w, b.h}; }

        if (n.type.equals("scroll")) {
            int vxx = ax + n.pad[3], vyy = ay + n.pad[0], vw = b.w - padW(n), vh = b.h - padH(n);
            int[] inner = intersect(clip, new int[]{vxx, vyy, vxx + vw, vyy + vh});
            scissor(g, inner);
            for (Box k : b.kids) draw(g, k, ax, ay - (int) b.offset, inner, hot, tip, vx, vy);
            g.disableScissor();
            if (b.contentH > vh) {
                double max = b.contentH - vh;
                int th = Math.max(16, (int) (vh * (vh / (double) b.contentH)));
                int ty = vyy + (int) ((vh - th) * (b.offset / max));
                if (n.thumb.isEmpty()) {
                    g.fill(ax + b.w - 10, vyy, ax + b.w - 2, vyy + vh, 0xFF101827);
                    fillRound(g, ax + b.w - 9, ty, 6, th, 3, 0xFF3B4F78);
                } else {
                    if (!n.track.isEmpty()) g.fill(ax + b.w - 10, vyy, ax + b.w, vyy + vh, color(UiExpr.text(n.track, c), 0));
                    fillRound(g, ax + b.w - 10, ty, 10, th, 5, color(UiExpr.text(n.thumb, c), 0xFFC9B078));
                }
            }
            // "scroll down" hint while there is more below
            if (b.contentH > vh && b.offset < max(b) - 10 && !n.hint.isEmpty()) {
                String h = UiExpr.text(n.hint, c);
                int hw = font.width(h);
                g.fillGradient(vxx, vyy + vh - 22, vxx + vw, vyy + vh, 0x00121A29, 0xF0121A29);
                g.drawString(font, h, vxx + (vw - hw) / 2, vyy + vh - 12, 0xFFFFD166, false);
            }
        } else {
            int z = 0;
            int[] kclip = clip;
            if (n.clip) { kclip = intersect(clip, new int[]{ax, ay, ax + b.w, ay + b.h}); scissor(g, kclip); }
            boolean layered = n.type.equals("stack");
            if (!layered) for (Box k : b.kids) if (k.abs) { layered = true; break; }
            for (Box k : b.kids) {
                if (k.n.tipBox || k.n.cursor) continue;      // hover cards and carried items are drawn last, at the mouse
                // text is batched and flushed after the fills, so a later layer (an overlay) needs a higher depth to cover earlier text
                if (layered && z > 0) { g.pose().pushPose(); g.pose().translate(0, 0, (n.type.equals("stack") ? 50 : 2) * z); }
                draw(g, k, ax, ay, kclip, hot, tip, vx, vy);
                if (layered && z > 0) g.pose().popPose();
                z++;
            }
            if (n.clip) g.disableScissor();
        }
    }

    private double max(Box b) { return Math.max(0, b.contentH - (b.h - padH(b.n))); }

    private static int[] intersect(int[] a, int[] b) {
        return new int[]{Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.min(a[2], b[2]), Math.min(a[3], b[3])};
    }

    private void fillRound(GuiGraphics g, int x, int y, int w, int h, int r, int col) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x, y, x + w, y + h, col); return; }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * (double) r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, col);
        }
        g.fill(x, y + r, x + w, y + h - r, col);
    }

    /** One-pixel border following the rounded corners. */
    private void outline(GuiGraphics g, int x, int y, int w, int h, int r, int col) {
        if (w <= 1 || h <= 1) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + 1, col); g.fill(x, y + h - 1, x + w, y + h, col);
            g.fill(x, y + 1, x + 1, y + h - 1, col); g.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
            return;
        }
        int prev = r;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * (double) r - dy * dy));
            int next = i + 1 < r ? r - (int) Math.round(Math.sqrt(r * (double) r - (r - i - 1.5) * (r - i - 1.5))) : 0;
            // top / bottom edge of this row
            g.fill(x + inset, y + i, x + Math.max(inset + 1, prev == inset ? inset + 1 : prev), y + i + 1, col);
            g.fill(x + w - Math.max(inset + 1, prev == inset ? inset + 1 : prev), y + i, x + w - inset, y + i + 1, col);
            g.fill(x + inset, y + h - i - 1, x + Math.max(inset + 1, prev == inset ? inset + 1 : prev), y + h - i, col);
            g.fill(x + w - Math.max(inset + 1, prev == inset ? inset + 1 : prev), y + h - i - 1, x + w - inset, y + h - i, col);
            prev = inset;
        }
        g.fill(x + r, y, x + w - r, y + 1, col);
        g.fill(x + r, y + h - 1, x + w - r, y + h, col);
        g.fill(x, y + r, x + 1, y + h - r, col);
        g.fill(x + w - 1, y + r, x + w, y + h - r, col);
    }

    /** How far row i (from the top of a corner of radius r) is inset. */
    /** A left-to-right gradient inside a rounded box (CSS border-radius on a gradient background): drawn column by column. */
    private void gradHRound(GuiGraphics g, int x, int y, int w, int h, String spec, int radius) {
        String[] parts = spec.substring(5).split(",");
        if (parts.length < 2) return;
        int[] cols = new int[parts.length];
        for (int i = 0; i < parts.length; i++) cols[i] = color(parts[i], 0xFFFFFFFF);
        int r = Math.min(radius, Math.min(w, h) / 2);
        for (int i = 0; i < w; i++) {
            double t = w <= 1 ? 0 : (double) i / (w - 1);
            double seg = t * (cols.length - 1);
            int a = Math.min(cols.length - 2, (int) seg);
            int edge = Math.min(i, w - 1 - i), ins = edge < r ? insetAt(r, edge) : 0;
            if (h - 2 * ins > 0) g.fill(x + i, y + ins, x + i + 1, y + h - ins, mix(cols[a], cols[a + 1], seg - a));
        }
    }

    private static int insetAt(int r, int i) {
        if (r <= 0 || i >= r) return 0;
        double dy = r - i - 0.5;
        return r - (int) Math.round(Math.sqrt(r * (double) r - dy * dy));
    }

    /** A box with its own radius per corner {tl, tr, br, bl}. */
    private void fillRadii(GuiGraphics g, int x, int y, int w, int h, int[] r, int col) {
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < h; i++) {
            int l = Math.max(insetAt(r[0], i), insetAt(r[3], h - 1 - i));
            int rr = Math.max(insetAt(r[1], i), insetAt(r[2], h - 1 - i));
            if (w - l - rr > 0) g.fill(x + l, y + i, x + w - rr, y + i + 1, col);
        }
    }

    /** A border bw wide along per-corner rounded edges (drawn as the ring between the outer and the inner shape, row by row). */
    private void borderRadii(GuiGraphics g, int x, int y, int w, int h, int[] r, int bw, int col) {
        int[] ri = {Math.max(0, r[0] - bw), Math.max(0, r[1] - bw), Math.max(0, r[2] - bw), Math.max(0, r[3] - bw)};
        for (int i = 0; i < h; i++) {
            int l = Math.max(insetAt(r[0], i), insetAt(r[3], h - 1 - i));
            int rr = Math.max(insetAt(r[1], i), insetAt(r[2], h - 1 - i));
            if (i < bw || i >= h - bw) { g.fill(x + l, y + i, x + w - rr, y + i + 1, col); continue; }
            int ii = i - bw, ih = h - 2 * bw;
            int il = bw + Math.max(insetAt(ri[0], ii), insetAt(ri[3], ih - 1 - ii));
            int ir = bw + Math.max(insetAt(ri[1], ii), insetAt(ri[2], ih - 1 - ii));
            g.fill(x + l, y + i, x + Math.max(l, il), y + i + 1, col);
            g.fill(x + w - Math.max(rr, ir), y + i, x + w - rr, y + i + 1, col);
        }
    }

    /** Borders on chosen sides along per-corner rounded edges; the top band may have its own width and colour (CSS border-top). */
    private void borderSides(GuiGraphics g, int x, int y, int w, int h, int[] r, int bw, int col, String sides, int tw, int tcol) {
        boolean T = sides.indexOf('t') >= 0, Rr = sides.indexOf('r') >= 0, B = sides.indexOf('b') >= 0, L = sides.indexOf('l') >= 0;
        int topW = T ? tw : 0, botW = B ? bw : 0, lw = L ? bw : 0, rw = Rr ? bw : 0;
        for (int i = 0; i < h; i++) {
            int l = Math.max(insetAt(r[0], i), insetAt(r[3], h - 1 - i));
            int rr = Math.max(insetAt(r[1], i), insetAt(r[2], h - 1 - i));
            if (i < topW) { g.fill(x + l, y + i, x + w - rr, y + i + 1, tcol); continue; }
            if (i >= h - botW) { g.fill(x + l, y + i, x + w - rr, y + i + 1, col); continue; }
            int ii = i - topW, ih = h - topW - botW;
            int il = lw + Math.max(insetAt(Math.max(0, r[0] - lw), ii), insetAt(Math.max(0, r[3] - lw), ih - 1 - ii));
            int ir = rw + Math.max(insetAt(Math.max(0, r[1] - rw), ii), insetAt(Math.max(0, r[2] - rw), ih - 1 - ii));
            int rowCol = i < topW + Math.max(0, r[0] - lw) && topW > bw ? tcol : col;
            if (il > l) g.fill(x + l, y + i, x + il, y + i + 1, rowCol);
            if (ir > rr) g.fill(x + w - ir, y + i, x + w - rr, y + i + 1, rowCol);
        }
    }

    /** A dashed border (CSS border-style: dashed): dashes three times the width long. */
    private void dashed(GuiGraphics g, int x, int y, int w, int h, int bw, int col, String sides) {
        int dl = Math.max(3, bw * 3), gap = Math.max(3, bw * 3);
        for (int i = 0; i < w; i += dl + gap) {
            int e = Math.min(w, i + dl);
            if (sides.indexOf('t') >= 0) g.fill(x + i, y, x + e, y + bw, col);
            if (sides.indexOf('b') >= 0) g.fill(x + i, y + h - bw, x + e, y + h, col);
        }
        for (int i = 0; i < h; i += dl + gap) {
            int e = Math.min(h, i + dl);
            if (sides.indexOf('l') >= 0) g.fill(x, y + i, x + bw, y + e, col);
            if (sides.indexOf('r') >= 0) g.fill(x + w - bw, y + i, x + w, y + e, col);
        }
    }

    private void gradH(GuiGraphics g, int x, int y, int w, int h, String spec) { gradH(g, x, y, w, h, spec, w); }

    /** Left-to-right gradient through 2-4 colours; totalW is the width the full gradient spans (so a partly filled bar shows its slice). */
    private void gradH(GuiGraphics g, int x, int y, int w, int h, String spec, int totalW) {
        String[] parts = spec.substring(5).split(",");
        if (parts.length < 2) { if (parts.length == 1) g.fill(x, y, x + w, y + h, color(parts[0], 0)); return; }
        int[] cols = new int[parts.length];
        for (int i = 0; i < parts.length; i++) cols[i] = color(parts[i], 0xFFFFFFFF);
        for (int i = 0; i < w; i += 2) {
            double t = totalW <= 1 ? 0 : (double) i / (totalW - 1);
            double seg = t * (cols.length - 1);
            int a = Math.min(cols.length - 2, (int) seg);
            double f = seg - a;
            g.fill(x + i, y, x + Math.min(w, i + 2), y + h, mix(cols[a], cols[a + 1], f));
        }
    }

    private static int mix(int a, int b, double f) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int gg = (int) Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    // ------------------------------------------------------------------ hooks for the in-process test harness (same code path as the real events)
    public boolean uiTestClick(double mx, double my) { return mouseClicked(mx, my, 0); }
    public boolean uiTestScroll(double mx, double my, double amount) { return mouseScrolled(mx, my, 0, amount); }
    public boolean uiTestChar(char c) { return charTyped(c, 0); }
    public boolean uiTestKey(int key) { return keyPressed(key, 0, 0); }

    // ------------------------------------------------------------------ hit testing and input

    private static boolean inside(int[] r, double x, double y) { return x >= r[0] && x < r[2] && y >= r[1] && y < r[3]; }

    /** Topmost interactive box under the point. A stack child under the point swallows the click even if it has nothing to press (modal overlay). */
    private Box hoverAnc, lastHoverOwner;

    private Box hit(Box b, int px, int py, int[] clip, double vx, double vy) {
        UiSpec.Node n = b.n;
        int ax = px + b.x, ay = py + b.y;
        int[] mine = intersect(clip, new int[]{ax, ay, ax + b.w, ay + b.h});
        if (inside(mine, vx, vy) && !n.hoverSet.isEmpty()) hoverAnc = b;
        if (!inside(mine, vx, vy)) {
            // a box can still hold absolutely placed children that stick out of it
            boolean any = false;
            for (Box k : b.kids) if (k.abs) { any = true; break; }
            if (!any || n.type.equals("scroll")) return null;
            for (int i = b.kids.size() - 1; i >= 0; i--) {
                Box k = b.kids.get(i);
                if (!k.abs || k.n.tipBox) continue;
                Box r = hit(k, ax, ay, clip, vx, vy);
                if (r != null) return r;
            }
            return null;
        }
        if (n.type.equals("scroll")) {
            int[] inner = intersect(clip, new int[]{ax + n.pad[3], ay + n.pad[0], ax + b.w - n.pad[1], ay + b.h - n.pad[2]});
            if (inside(inner, vx, vy)) {
                for (int i = b.kids.size() - 1; i >= 0; i--) {
                    Box r = hit(b.kids.get(i), ax, ay - (int) b.offset, inner, vx, vy);
                    if (r != null) return r;
                }
            }
            return b;
        }
        if (n.type.equals("stack")) {
            for (int i = b.kids.size() - 1; i >= 0; i--) {
                Box k = b.kids.get(i);
                int kx = ax + k.x, ky = ay + k.y;
                if (inside(new int[]{kx, ky, kx + k.w, ky + k.h}, vx, vy)) {
                    Box r = hit(k, ax, ay, mine, vx, vy);
                    return r != null ? r : (isPressable(k) ? k : null);
                }
            }
            return null;
        }
        for (int i = b.kids.size() - 1; i >= 0; i--) {
            Box k = b.kids.get(i);
            if (k.n.tipBox || k.n.cursor) continue;
            Box r = hit(k, ax, ay, k.abs && !n.clip ? clip : mine, vx, vy);
            if (r != null) return r;
        }
        if (n.z > 0) return b;                      // an overlay (modal) swallows the mouse: nothing behind it is hovered or clicked
        return isPressable(b) || !b.n.tooltip.isEmpty() ? b : null;
    }

    private boolean isPressable(Box b) {
        String t = b.n.type;
        return (t.equals("button") && (b.n.msg != null || !b.n.set.isEmpty() || b.n.close)) || t.equals("input");
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tree == null || (button != 0 && button != 1)) return super.mouseClicked(mx, my, button);
        double vx = mx / scale - ox, vy = my / scale - oy;
        Box hot = hit(tree, 0, 0, new int[]{0, 0, spec.width, spec.height}, vx, vy);
        focus = "";
        if (hot == null) return super.mouseClicked(mx, my, button);
        UiSpec.Node n = hot.n;
        if (button == 1 && !n.args.containsKey("btn")) return true;                 // only buttons that declare a "btn" argument answer the right mouse button
        if (n.type.equals("input")) { focus = n.id; return true; }
        if (n.type.equals("scroll")) {
            int track = hot.ax + hot.w - 10;
            if (vx >= track && hot.contentH > hot.h - padH(n)) { dragScroll = hot.key; dragFromY = vy; dragFromOffset = hot.offset; return true; }
            return true;
        }
        if (!isPressable(hot) || !hot.enabled) return true;
        long now = Util.getMillis();
        if (now - lastClickAt < 120) return true;
        lastClickAt = now;
        // local state first, so the message carries the new value
        if (!n.set.isEmpty()) {
            for (var en : n.set.entrySet()) {
                JsonElement v = UiExpr.calc(en.getValue(), hot.ctx);
                if (v != null) root.local.put(en.getKey(), v); else root.local.remove(en.getKey());
            }
            dirty = true;
        }
        clickBtn = button;
        clickShift = hasShiftDown() ? 1 : 0;
        if (n.msg != null) send(hot);
        clickBtn = 0;
        clickShift = 0;
        if (n.close) super.onClose();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragScroll.isEmpty() && tree != null) {
            Box[] found = new Box[1];
            walk(tree, k -> { if (k.key.equals(dragScroll) && k.n.type.equals("scroll")) found[0] = k; });
            Box s = found[0];
            if (s != null) {
                int vh = s.h - padH(s.n);
                double max = Math.max(1, s.contentH - vh);
                int th = Math.max(16, (int) (vh * (vh / (double) s.contentH)));
                double vy = my / scale - oy;
                double per = max / Math.max(1, vh - th);
                scrollOffsets.put(dragScroll, Math.max(0, Math.min(max, dragFromOffset + (vy - dragFromY) * per)));
                dirty = true;
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragScroll = "";
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tree == null) return false;
        double vx = mx / scale - ox, vy = my / scale - oy;
        Box[] best = new Box[1];
        walk(tree, k -> {
            if (k.n.type.equals("scroll") && k.clip != null && vx >= k.ax && vx < k.ax + k.w && vy >= k.ay && vy < k.ay + k.h && inside(k.clip, vx, vy)) best[0] = k;
        });
        if (best[0] == null) {
            // no scroll box under the mouse: the wheel turns pages where the page asks for it (prev = "left", next = "right")
            if (sy != 0 && pressKey(sy < 0 ? "right" : "left")) return true;
            return super.mouseScrolled(mx, my, sx, sy);
        }
        Box s = best[0];
        double max = max(s);
        scrollOffsets.put(s.key, Math.max(0, Math.min(max, s.offset - sy * 28)));
        dirty = true;
        return true;
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (focus.isEmpty() || tree == null) return super.charTyped(ch, mods);
        Box in = findInput();
        if (in == null || ch < 32 || ch == 127) return true;
        String cur = localText(in.n.bind);
        if (cur.length() >= in.n.maxLen) return true;
        root.local.put(in.n.bind, new com.google.gson.JsonPrimitive(cur + ch));
        inputDirtyAt.put(in.n.id, Util.getMillis());
        dirty = true;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!focus.isEmpty()) {
            Box in = findInput();
            if (in != null) {
                if (key == 259) {                                           // backspace
                    String cur = localText(in.n.bind);
                    if (!cur.isEmpty()) {
                        root.local.put(in.n.bind, new com.google.gson.JsonPrimitive(cur.substring(0, cur.length() - 1)));
                        inputDirtyAt.put(in.n.id, Util.getMillis());
                        dirty = true;
                    }
                    return true;
                }
                if (key == 256) { focus = ""; return true; }               // escape leaves the field first
                if (key == 257 || key == 335) { focus = ""; return true; }
            }
        }
        if (tree != null && focus.isEmpty()) {
            String name = key == 256 ? "esc" : key == 263 ? "left" : key == 262 ? "right" : "";
            if (!name.isEmpty() && pressKey(name)) return true;               // e.g. Escape closes an open window before the screen
        }
        if (key == 32 && tree != null) {                              // space presses the visible button that asks for it
            Box[] hit = new Box[1];
            walk(tree, k -> { if (hit[0] == null && k.n.key.equals("space") && k.enabled && isPressable(k)) hit[0] = k; });
            if (hit[0] != null) return mouseClicked((ox + hit[0].ax + hit[0].w / 2.0) * scale, (oy + hit[0].ay + hit[0].h / 2.0) * scale, 0);
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Presses the visible button bound to a key ("esc", "left", "right"). Arrows / wheel do nothing while an overlay window (z > 0) is open. */
    private boolean pressKey(String name) {
        if (tree == null) return false;
        boolean[] overlay = {false};
        walk(tree, k -> { if (k.n.z > 0 && k.clip != null) overlay[0] = true; });
        if (overlay[0] && !name.equals("esc")) return false;
        Box[] hit = new Box[1];
        walk(tree, k -> { if (hit[0] == null && k.n.key.equals(name) && k.enabled && k.clip != null && isPressable(k)) hit[0] = k; });
        if (hit[0] == null) return false;
        return mouseClicked((ox + hit[0].ax + hit[0].w / 2.0) * scale, (oy + hit[0].ay + hit[0].h / 2.0) * scale, 0);
    }

    private Box findInput() {
        Box[] f = new Box[1];
        walk(tree, k -> { if (k.n.type.equals("input") && k.n.id.equals(focus)) f[0] = k; });
        return f[0];
    }


}
