package net.snobblemon.hubclient.ui;

import com.google.gson.JsonObject;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.owo.ui.core.Positioning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.snobblemon.hubclient.HubClient;

/**
 * The shared frame of every Snobble screen: a region-coloured window, the tab row, and a body the page fills.
 * Sizes come from the real scaled screen size, so nothing depends on a fixed resolution.
 */
public abstract class SnobbleScreen extends BaseOwoScreen<FlowLayout> {
    private static final String[] TABS = {"inv", "wiki", "cos", "card", "warp", "upd", "shop"};
    private static final String[] TAB_ICON = {"inventory", "wiki", "cosmetics", "card", "warps", "updates", "shop"};
    private static final String[] TAB_NAME = {"Inventory", "Wiki", "Cosmetics", "Trainer Card", "Warps", "Updates", "Gem Shop"};

    /**
     * Zoom is the game's own GUI scale, stepped one whole level at a time while a hub screen is open (so pixel art stays
     * sharp and clipping / mouse / text all stay correct). The player's setting is put back when the screen closes.
     * The pick is a step relative to the player's own GUI scale and is kept for the game session.
     */
    private static int zoomStep = Integer.getInteger("snobblehub.test.zoom", 0);   // property: test rig only
    private int baseScale = 3;

    private int scaleLimit() {
        var win = Minecraft.getInstance().getWindow();
        // the smallest window (318 x 222 GUI px with margin) must still fit
        return Math.max(baseScale, (int) Math.floor(Math.min(win.getWidth() / 318.0, win.getHeight() / 222.0)));
    }

    private int scaleLow() {
        return Math.max(1, baseScale - 2);
    }

    private int scale() {
        return Mth.clamp(baseScale + zoomStep, scaleLow(), scaleLimit());
    }

    @Override
    protected void init() {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        baseScale = win.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
        int target = scale();
        if ((int) win.getGuiScale() != target) win.setGuiScale(target);
        this.width = win.getGuiScaledWidth();           // the layout is built for the zoomed GUI size
        this.height = win.getGuiScaledHeight();
        super.init();
    }

    @Override
    public void removed() {
        super.removed();
        var mc = Minecraft.getInstance();
        // after the screen switch has finished: back to the player's own GUI scale unless another hub screen took over
        mc.tell(() -> { if (!(mc.screen instanceof SnobbleScreen)) mc.resizeDisplay(); });
    }

    private void setScale(int s) {
        zoomStep = Mth.clamp(s, scaleLow(), scaleLimit()) - baseScale;
        var mc = Minecraft.getInstance();
        mc.getWindow().setGuiScale(scale());
        this.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    /** A small box with just [-] and [+] in the bottom-right of the frame. */
    private FlowLayout zoomBar(int wx, int wy, int w, int h) {
        FlowLayout box = Containers.horizontalFlow(Sizing.fixed(54), Sizing.fixed(12));
        box.verticalAlignment(VerticalAlignment.CENTER);
        box.horizontalAlignment(HorizontalAlignment.CENTER);
        box.gap(1);
        box.positioning(Positioning.absolute(wx + w - 66, wy + h - 13));
        final int rc = Theme.regionColor(accent);
        box.surface((ctx, c) -> {
            ctx.fill(c.x(), c.y(), c.x() + c.width(), c.y() + c.height(), 0xFF000000 | rc);
            ctx.fill(c.x() + 1, c.y() + 1, c.x() + c.width() - 1, c.y() + c.height() - 1, 0xFF15171B);
        });
        final int sc = scale();
        box.child(zoomButton("-", sc > scaleLow(), () -> setScale(sc - 1)));
        var pct = Components.button(Component.empty(), btn -> { });          // percentage of the player's own GUI scale, small text, not clickable
        pct.sizing(Sizing.fixed(28), Sizing.fixed(10));
        pct.active = false;
        final String t = Math.round(sc * 100f / baseScale) + "%";
        pct.renderer((ctx, btn, delta) -> {
            var f = Minecraft.getInstance().font;
            float k = 0.75f;
            ctx.pose().pushPose();
            ctx.pose().translate(btn.x() + 14 - f.width(t) * k / 2f, btn.y() + 5 - 4 * k, 0);
            ctx.pose().scale(k, k, 1f);
            ctx.drawString(f, t, 0, 0, 0xFFFFFFFF, true);
            ctx.pose().popPose();
        });
        box.child(pct);
        box.child(zoomButton("+", sc < scaleLimit(), () -> setScale(sc + 1)));
        return box;
    }

    private io.wispforest.owo.ui.component.ButtonComponent zoomButton(String glyph, boolean enabled, Runnable press) {
        var b = Components.button(Component.empty(), btn -> press.run());
        b.sizing(Sizing.fixed(10), Sizing.fixed(10));
        b.active = enabled;
        b.renderer((ctx, btn, delta) -> {
            if (enabled && btn.isHovered()) ctx.fill(btn.x(), btn.y(), btn.x() + 10, btn.y() + 10, 0xFF2F353C);
            var f = Minecraft.getInstance().font;
            ctx.drawString(f, glyph, btn.x() + 5 - f.width(glyph) / 2, btn.y() + 1, enabled ? 0xFFFFFFFF : 0xFF626A72, false);
        });
        b.tooltip(Component.literal((glyph.equals("+") ? "Zoom in" : "Zoom out") + " (now " + scale() + ")"));
        return b;
    }

    private final String page;
    protected JsonObject data;
    protected String accent = "dusk";
    protected String toastText = "";
    protected boolean toastOk;

    protected SnobbleScreen(String page, JsonObject data) {
        super(Component.literal("Snobblemon Regions"));
        this.page = page;
        this.data = data;
        readAccent();
    }

    private void readAccent() {
        if (data.has("region") && !data.get("region").getAsString().isEmpty()) this.accent = data.get("region").getAsString();
        if (data.has("accent")) this.accent = data.get("accent").getAsString();
        String forced = System.getProperty("snobblehub.test.accent");   // test rig only
        if (forced != null) this.accent = forced;
    }

    public String page() {
        return page;
    }

    /** Window size in GUI pixels for the current screen size. */
    protected abstract int[] windowSize(int screenW, int screenH);

    /** Fills the body: bodyW x bodyH GUI pixels, already inside the frame. */
    protected abstract void buildBody(FlowLayout body, int bodyW, int bodyH);

    public void update(JsonObject fresh) {
        this.data = fresh;
        readAccent();
        rebuild();
    }

    public void toast(String text, boolean ok) {
        this.toastText = text;
        this.toastOk = ok;
        rebuild();
    }

    protected void rebuild() {
        if (this.uiAdapter == null) return;
        FlowLayout root = this.uiAdapter.rootComponent;
        root.clearChildren();
        build(root);
        this.uiAdapter.inflateAndMount();
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    /** True for the bright wardrobe-style screen, false for the dark card-style screen. */
    protected boolean brightInterior() {
        return true;
    }

    @Override
    protected void build(FlowLayout root) {
        Theme.accent = accent;
        int[] size = windowSize(this.width, this.height);
        int w = Math.min(size[0], this.width - 8), h = Math.min(size[1], this.height - 8);
        root.surface(Surface.VANILLA_TRANSLUCENT).horizontalAlignment(HorizontalAlignment.CENTER).verticalAlignment(VerticalAlignment.CENTER);

        final String r = Theme.region();
        final int interior = Theme.interior(accent, brightInterior());
        FlowLayout window = Containers.verticalFlow(Sizing.fixed(w), Sizing.fixed(h));
        window.padding(Insets.of(PAD));
        window.gap(3);
        window.surface((ctx, c) -> {
            int x = c.x(), y = c.y(), ww = c.width(), hh = c.height();
            ctx.fill(x + 8, y + 8, x + ww - 8, y + hh - 8, interior);                 // the screen; the frame's cut corners cover the rest
            ctx.fill(x + PAD - 1, y + PAD - 1, x + ww - PAD + 1, y + hh - PAD + 1, interior);
            Theme.blit(ctx, Theme.frame(accent), x, y, ww, hh);
            if (brightInterior()) Theme.glow(ctx, x + PAD - 1, y + PAD - 1, x + ww - PAD + 1, y + hh - PAD + 1, interior);
            Theme.scanlines(ctx, x + PAD - 1, y + PAD - 1, x + ww - PAD + 1, y + hh - PAD + 1);
            Theme.tex(ctx, Theme.id("textures/gui/vent_out.png"), x - 4, y + 30, 4, 8, 0f, 0f, 4, 8, 4, 8);
            Theme.tex(ctx, Theme.id("textures/gui/vent_in_" + r + ".png"), x + 1, y + hh - 40, 8, 8, 0f, 0f, 8, 8, 8, 8);
            Theme.tex(ctx, Theme.id("textures/gui/vent_in_" + r + ".png"), x + ww - 9, y + hh - 40, 8, 8, 0f, 0f, 8, 8, 8, 8);
        });

        int innerW = w - 2 * PAD, innerH = h - 2 * PAD;
        window.child(header(innerW));
        int bodyH = innerH - HEAD_H - 3;
        FlowLayout body = Containers.horizontalFlow(Sizing.fixed(innerW), Sizing.fixed(bodyH));
        body.gap(3);
        buildBody(body, innerW, bodyH);
        window.child(body);
        root.child(window);
        root.child(zoomBar((this.width - w) / 2, (this.height - h) / 2, w, h));
    }

    private static final int PAD = 14;
    private static final int HEAD_H = 18;
    private static final String[] TAB_GLYPH = {"tab_inv", "tab_wiki", "tab_cos", "tab_card", "tab_warp", "tab_upd", "tab_shop"};

    private FlowLayout header(int innerW) {
        FlowLayout head = Containers.horizontalFlow(Sizing.fixed(innerW), Sizing.fixed(HEAD_H));
        head.verticalAlignment(VerticalAlignment.CENTER);
        head.gap(2);
        for (int i = 0; i < TABS.length; i++) {
            final String target = TABS[i];
            final String glyph = TAB_GLYPH[i];
            final boolean on = target.equals(page);
            var tab = Components.button(Component.empty(), b -> {
                if (target.equals("inv")) {                      // the first tab is the player's regular inventory
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                } else if (target.equals("wiki")) {              // the Wiki tab is a command: the server opens the full SnobbleWiki
                    HubClient.request("wikiFull", "");
                } else if (!on) {
                    HubClient.request("page", target);
                }
            });
            tab.sizing(Sizing.fixed(18), Sizing.fixed(18));
            tab.renderer((ctx, btn, delta) -> {
                Theme.blit(ctx, Theme.sprite(on ? "tab_on" : btn.isHovered() ? "tab_hover" : "tab"), btn.x(), btn.y(), 18, 18);
                Theme.tex(ctx, Theme.icon(glyph), btn.x() + 1, btn.y() + 1, 16, 16, 0f, 0f, 16, 16, 16, 16);
            });
            tab.tooltip(Component.literal(TAB_NAME[i]));
            head.child(tab);
        }
        // title bar: dark, region-coloured outline, page name on the left and the region on the right
        FlowLayout bar = Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(16));
        bar.verticalAlignment(VerticalAlignment.CENTER);
        bar.padding(Insets.of(0, 0, 4, 4));
        bar.gap(4);
        final int rc = Theme.regionColor(accent);
        bar.surface((ctx, c) -> {
            ctx.fill(c.x(), c.y(), c.x() + c.width(), c.y() + c.height(), 0xFF000000 | rc);
            ctx.fill(c.x() + 1, c.y() + 1, c.x() + c.width() - 1, c.y() + c.height() - 1, 0xFF15171B);
        });
        String regionName = switch (accent) {
            case "root" -> "ROOTVALE";
            case "tide" -> "TIDEFORGE";
            default -> "DUSKVALE";
        };
        int idx = java.util.Arrays.asList(TABS).indexOf(page);
        bar.child(Ui.label(idx >= 0 ? TAB_NAME[idx] : "Snobblemon", 0xFFFFFFFF));
        FlowLayout rightBox = Containers.horizontalFlow(Sizing.expand(), Sizing.fill(100));
        rightBox.horizontalAlignment(HorizontalAlignment.RIGHT).verticalAlignment(VerticalAlignment.CENTER);
        rightBox.gap(3);
        rightBox.child(Ui.icon("region_" + accent).sizing(Sizing.fixed(12), Sizing.fixed(12)));
        rightBox.child(Ui.label(regionName, rc));
        bar.child(rightBox);
        head.child(bar);
        var close = Components.button(Component.empty(), b -> this.onClose());
        close.sizing(Sizing.fixed(18), Sizing.fixed(18));
        close.renderer((ctx, btn, delta) -> {
            if (btn.isHovered()) ctx.fill(btn.x() + 1, btn.y() + 1, btn.x() + 17, btn.y() + 17, 0x33FFFFFF);
            Theme.tex(ctx, Theme.icon("back"), btn.x() + 1, btn.y() + 1, 16, 16, 0f, 0f, 16, 16, 16, 16);
        });
        close.tooltip(Component.literal("Close"));
        head.child(close);
        return head;
    }

    protected static int clamp(double v, int lo, int hi) {
        return (int) Mth.clamp(v, lo, hi);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
