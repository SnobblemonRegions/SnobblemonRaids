package net.snobblemon.hubclient.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.snobblemon.hubclient.HubClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Gem Shop. Left: categories. Middle: a search box and a scrolling grid of products. Right: the selected product, the
 * balance, and the Buy flow. Everything shown (prices, balances, limits, why an item is locked) comes from the server; the
 * screen only sends a product id to ask for a quote, then the quote's token to confirm. The server re-checks everything and
 * answers with the real result, which replaces what is on screen.
 */
public final class ShopScreen extends SnobbleScreen {
    private static final Gson GSON = new Gson();
    private static final String ALL = "";

    static final class Cat { String id, name, color, parent; int count; }

    static final class Prod {
        String id, name, color, icon, reason, code, limit;
        long price, ends;
        int count;
        boolean ok, featured, pts;
        List<String> desc = new ArrayList<>();
        List<String> cats = new ArrayList<>();
    }

    static final class Model {
        String currency = "Gems", singular = "Gem", pointsName = "Points", title = "Gem Shop";
        long balance, points;
        List<Cat> cats = new ArrayList<>();
        List<Prod> products = new ArrayList<>();
    }

    /** The server's answer to "I would like to buy this": real price and a token, or the reason it cannot be bought. */
    static final class Quote {
        boolean ok, pts;
        String token, product, name, code, message;
        long price, balance, after;
        int seconds;
    }

    private Model model;
    private String cat = ALL;
    private String search = "";
    private String selected;
    private Quote quote;
    private String message = "";
    private boolean messageOk;
    private FlowLayout gridHost;
    private int gridW, gridH;

    public ShopScreen(JsonObject data) {
        super("shop", data);
        this.model = parse(data);
    }

    private static Model parse(JsonObject data) {
        Model m = GSON.fromJson(data, Model.class);
        return m == null ? new Model() : m;
    }

    @Override
    public void update(JsonObject fresh) {
        this.model = parse(fresh);
        if (selected != null && find(selected) == null) { selected = null; quote = null; }
        super.update(fresh);
    }

    /** The server answered a quote request. */
    public void onQuote(JsonObject json) {
        this.quote = GSON.fromJson(json, Quote.class);
        if (quote != null && !quote.ok) {
            this.message = quote.message == null ? "That cannot be bought." : quote.message;
            this.messageOk = false;
            this.quote = null;
        } else {
            this.message = "";
        }
        rebuild();
    }

    /** The server answered a confirm request: the purchase happened or it did not. */
    public void onResult(JsonObject json) {
        this.quote = null;
        boolean ok = json.has("ok") && json.get("ok").getAsBoolean();
        this.message = json.has("message") ? json.get("message").getAsString() : (ok ? "Purchased!" : "That did not work.");
        this.messageOk = ok;
        rebuild();
    }

    @Override
    protected int[] windowSize(int sw, int sh) {
        return new int[]{clamp(sw * 0.84, 310, 364), clamp(sh * 0.90, 212, 236)};
    }

    @Override
    public void removed() {
        super.removed();
        if (quote != null) HubClient.request("shopCancel", "");
    }

    private Prod find(String id) {
        for (Prod p : model.products) if (p.id.equals(id)) return p;
        return null;
    }

    private static int colorOf(String hex, int fallback) {
        try {
            if (hex != null && hex.length() == 7 && hex.charAt(0) == '#') return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException ignored) { }
        return fallback;
    }

    /** What the client draws for a product: an item for item ids, the Gem / ticket pictures for the shop's own icons. */
    private static ItemStack iconStack(String icon) {
        if (icon == null || icon.isEmpty() || icon.startsWith("snobblegems:")) return ItemStack.EMPTY;
        String id = icon.startsWith("ext:") || !icon.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? "minecraft:paper" : icon;
        ItemStack s = Ui.stack("{count:1,id:\"" + id + "\"}");
        return s.isEmpty() ? Ui.stack("{count:1,id:\"minecraft:paper\"}") : s;
    }

    private List<Prod> visible() {
        List<Prod> out = new ArrayList<>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        for (Prod p : model.products) {
            if (!q.isEmpty()) {
                boolean hit = p.name.toLowerCase(Locale.ROOT).contains(q);
                for (String d : p.desc) if (!hit && d.toLowerCase(Locale.ROOT).contains(q)) hit = true;
                if (hit) out.add(p);
            } else if (cat.isEmpty() || inCategory(p, cat)) {
                out.add(p);
            }
        }
        return out;
    }

    private boolean inCategory(Prod p, String id) {
        if (p.cats.contains(id)) return true;
        for (Cat c : model.cats) if (id.equals(c.parent) && p.cats.contains(c.id)) return true;
        return false;
    }

    private static FlowLayout panel(int w, int h) {
        FlowLayout f = Ui.column(Sizing.fixed(w), Sizing.fixed(h));
        f.surface((ctx, c) -> Theme.blit(ctx, Theme.sprite("panel"), c.x(), c.y(), c.width(), c.height()));
        f.padding(Insets.of(3));
        return f;
    }

    @Override
    protected void buildBody(FlowLayout body, int bw, int bh) {
        int catW = Math.max(84, (int) (bw * 0.25)), rightW = Math.max(112, (int) (bw * 0.33)), midW = bw - catW - rightW - 6;
        FlowLayout top = Ui.row(Sizing.fixed(bw), Sizing.fixed(bh));
        top.gap(3);
        top.child(categories(catW, bh));
        top.child(middle(midW, bh));
        top.child(details(rightW, bh));
        body.child(top);
    }

    // ------------------------------------------------------------------ left: categories

    private FlowLayout categories(int w, int h) {
        FlowLayout left = panel(w, h);
        left.gap(1);
        FlowLayout list = Ui.column(Sizing.fill(100), Sizing.content());
        list.gap(1);
        list.child(catButton("All", ALL, w - 12, 0, null));
        for (Cat c : model.cats) {
            if (c.parent != null && !c.parent.isEmpty()) continue;
            list.child(catButton(c.name, c.id, w - 12, 0, colorOf(c.color, 0)));
            boolean open = cat.equals(c.id) || isChildOf(cat, c.id);
            if (open) for (Cat k : model.cats) if (c.id.equals(k.parent)) list.child(catButton(k.name, k.id, w - 12, 6, colorOf(k.color, 0)));
        }
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), list);
        scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xFFFFFFFF)));
        scroll.scrollbarThiccness(2);
        left.child(scroll);
        return left;
    }

    private boolean isChildOf(String child, String parent) {
        for (Cat c : model.cats) if (c.id.equals(child) && parent.equals(c.parent)) return true;
        return false;
    }

    private io.wispforest.owo.ui.component.ButtonComponent catButton(String name, String id, int w, int indent, Integer color) {
        final boolean on = cat.equals(id) && search.isEmpty();
        var b = Components.button(Component.empty(), btn -> {
            cat = id;
            search = "";
            quote = null;
            message = "";
            rebuild();
        });
        b.sizing(Sizing.fixed(w), Sizing.fixed(14));
        b.renderer((ctx, btn, delta) -> {
            Theme.blit(ctx, Theme.sprite(on ? "cat_on" : btn.isHovered() ? "cat_hover" : "cat"), btn.x(), btn.y(), btn.width(), btn.height());
            var f = net.minecraft.client.Minecraft.getInstance().font;
            String t = f.plainSubstrByWidth(name, btn.width() - 6 - indent);
            ctx.drawString(f, t, btn.x() + 3 + indent, btn.y() + 3, color != null && color != 0 && !on ? color : 0xFFFFFFFF, true);
        });
        return b;
    }

    // ------------------------------------------------------------------ middle: search + grid

    private FlowLayout middle(int w, int h) {
        FlowLayout mid = Ui.column(Sizing.fixed(w), Sizing.fixed(h));
        mid.surface((ctx, c) -> {
            ctx.fill(c.x(), c.y(), c.x() + c.width(), c.y() + c.height(), 0x30FFFFFF);
            ctx.fill(c.x(), c.y(), c.x() + c.width(), c.y() + 1, 0x99FFFFFF);
            ctx.fill(c.x(), c.y() + c.height() - 1, c.x() + c.width(), c.y() + c.height(), 0x99FFFFFF);
            ctx.fill(c.x(), c.y(), c.x() + 1, c.y() + c.height(), 0x99FFFFFF);
            ctx.fill(c.x() + c.width() - 1, c.y(), c.x() + c.width(), c.y() + c.height(), 0x99FFFFFF);
        });
        mid.padding(Insets.of(3));
        mid.gap(2);
        var box = Components.textBox(Sizing.fill(100), search);
        box.sizing(Sizing.fill(100), Sizing.fixed(14));
        box.setHint(Component.literal("Search..."));
        box.setMaxLength(32);
        box.onChanged().subscribe(text -> {
            search = text == null ? "" : text;
            fillGrid();                       // only the grid is rebuilt, so the box keeps focus while typing
        });
        mid.child(box);
        gridW = w - 6;
        gridH = h - 6 - 14 - 2;
        gridHost = Ui.column(Sizing.fixed(gridW), Sizing.fixed(gridH));
        mid.child(gridHost);
        fillGrid();
        return mid;
    }

    private void fillGrid() {
        if (gridHost == null) return;
        gridHost.clearChildren();
        List<Prod> items = visible();
        int cols = Math.max(2, (gridW - 8) / (ShopTile.W + 2));
        int rows = Math.max(Math.max(3, gridH / (ShopTile.H + 2)), (items.size() + cols - 1) / cols);
        if (items.isEmpty()) {
            gridHost.child(Ui.label(search.isEmpty() ? "Nothing here yet." : "No matches.", Theme.MUTED));
            return;
        }
        GridLayout grid = Containers.grid(Sizing.content(), Sizing.content(), rows, cols);
        for (int n = 0; n < items.size(); n++) {
            Prod p = items.get(n);
            ShopTile tile = new ShopTile(p, p.id.equals(selected));
            tile.margins(Insets.of(1));
            tile.tooltip(tooltip(p));
            tile.mouseDown().subscribe((mx, my, button) -> {
                selected = p.id;
                quote = null;
                message = "";
                rebuild();
                return true;
            });
            grid.child(tile, n / cols, n % cols);
        }
        for (int n = items.size(); n < rows * cols; n++) {
            var cell = Components.box(Sizing.fixed(ShopTile.W), Sizing.fixed(ShopTile.H));
            cell.color(Color.ofArgb(0x30000000)).fill(true);
            cell.margins(Insets.of(1));
            grid.child(cell, n / cols, n % cols);
        }
        ScrollContainer<GridLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), grid);
        scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xFFFFFFFF)));
        scroll.scrollbarThiccness(3);
        gridHost.child(scroll);
    }

    private List<Component> tooltip(Prod p) {
        List<Component> t = new ArrayList<>();
        t.add(Component.literal(p.name).withColor(colorOf(p.color, 0xFFFFFFFF) & 0xFFFFFF));
        t.add(Component.literal(String.format("%,d %s", p.price, p.pts ? model.pointsName : model.currency)).withColor(0x5FD8EA));
        if (!p.ok && p.reason != null && !p.reason.isEmpty()) t.add(Component.literal(p.reason).withColor(0xE0646E));
        return t;
    }

    // ------------------------------------------------------------------ right: details and the buy flow

    private FlowLayout details(int w, int h) {
        FlowLayout right = Ui.column(Sizing.fixed(w), Sizing.fixed(h));
        right.gap(2);
        int balH = 22, btnH = 18, infoH = h - balH - btnH - 4;

        FlowLayout bal = Ui.column(Sizing.fixed(w), Sizing.fixed(balH));
        bal.surface((ctx, c) -> Theme.blit(ctx, Theme.sprite("bar_dark"), c.x(), c.y(), c.width(), c.height()));
        bal.padding(Insets.of(2, 2, 4, 4));
        bal.gap(1);
        FlowLayout r1 = Ui.centeredRow(Sizing.fixed(w - 8), Sizing.fixed(9));
        r1.gap(3);
        r1.child(Ui.icon("gem").sizing(Sizing.fixed(9), Sizing.fixed(9)));
        r1.child(Ui.label(String.format("%,d %s", model.balance, model.currency), Theme.AQUA));
        bal.child(r1);
        FlowLayout r2 = Ui.centeredRow(Sizing.fixed(w - 8), Sizing.fixed(9));
        r2.child(Ui.label(String.format("%,d %s", model.points, model.pointsName), Theme.MUTED));
        bal.child(r2);
        right.child(bal);

        FlowLayout info = Ui.column(Sizing.fill(100), Sizing.content());
        info.gap(2);
        Prod sel = selected == null ? null : find(selected);
        if (sel == null) {
            info.child(Ui.label("Pick an item", Theme.MUTED));
            if (!message.isEmpty()) info.child(wrapped(message, messageOk ? Theme.GREEN : 0xFFFF9AA0, w - 12));
        } else {
            FlowLayout head = Ui.centeredRow(Sizing.fixed(w - 6), Sizing.fixed(20));
            head.gap(3);
            head.child(new ShopIcon(sel));
            head.child(new MarqueeText(w - 6 - 24, 9, true).add(sel.name, colorOf(sel.color, Theme.TEXT)));
            info.child(head);
            FlowLayout priceRow = Ui.centeredRow(Sizing.fixed(w - 6), Sizing.fixed(10));
            priceRow.gap(3);
            priceRow.child(Ui.icon("gem").sizing(Sizing.fixed(9), Sizing.fixed(9)));
            priceRow.child(Ui.label(String.format("%,d %s", sel.price, sel.pts ? model.pointsName : model.currency), Theme.AQUA));
            info.child(priceRow);
            for (String line : sel.desc) {
                if (line.isBlank()) continue;
                info.child(wrapped(line, Theme.MUTED, w - 12));
            }
            if (!sel.limit.isEmpty()) info.child(Ui.label(sel.limit, Theme.GOLD));
            if (!sel.ok && sel.reason != null && !sel.reason.isEmpty()) info.child(wrapped(sel.reason, 0xFFFF9AA0, w - 12));
            if (!message.isEmpty() && (sel.ok || messageOk)) info.child(wrapped(message, messageOk ? Theme.GREEN : 0xFFFF9AA0, w - 12));
            if (quote != null && quote.ok) {
                info.child(wrapped(String.format("Spend %,d, leaving %,d.", quote.price, quote.after), Theme.TEXT, w - 12));
            }
        }
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), info);
        scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xFFFFFFFF)));
        scroll.scrollbarThiccness(2);
        FlowLayout infoBox = panel(w, infoH);
        infoBox.padding(Insets.of(0));
        infoBox.child(scroll);
        right.child(infoBox);

        right.child(buttons(w, btnH, sel));
        return right;
    }

    private static io.wispforest.owo.ui.core.Component wrapped(String text, int color, int width) {
        return Components.label(Component.literal(text)).color(Color.ofArgb(color)).shadow(true).maxWidth(Math.max(30, width));
    }

    private FlowLayout buttons(int w, int h, Prod sel) {
        FlowLayout row = Ui.centeredRow(Sizing.fixed(w), Sizing.fixed(h));
        row.horizontalAlignment(HorizontalAlignment.CENTER);
        row.gap(3);
        if (sel == null) return row;
        int bw = (w - 3) / 2;
        if (quote != null && quote.ok) {
            row.child(Ui.button("Confirm", "gold", bw, b -> {
                String token = quote.token;
                quote = null;
                message = "";
                HubClient.request("shopConfirm", token);
                rebuild();
            }));
            row.child(Ui.button("Cancel", "danger", bw, b -> {
                quote = null;
                HubClient.request("shopCancel", "");
                rebuild();
            }));
            return row;
        }
        var buy = Ui.button(sel.ok ? "Buy" : "Unavailable", "primary", w, b -> HubClient.request("shopQuote", sel.id));
        buy.active = sel.ok;
        row.child(buy);
        return row;
    }

    // ------------------------------------------------------------------ pieces

    /** The product's picture at the top of the detail panel. */
    private static final class ShopIcon extends BaseComponent {
        private final Prod p;

        ShopIcon(Prod p) {
            this.p = p;
            this.sizing(Sizing.fixed(20), Sizing.fixed(20));
        }

        @Override
        protected int determineHorizontalContentSize(Sizing sizing) { return 20; }

        @Override
        protected int determineVerticalContentSize(Sizing sizing) { return 20; }

        @Override
        public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
            Theme.blit(ctx, Theme.sprite("tile"), x, y, 20, 20);
            ShopTile.drawIcon(ctx, p.icon, x + 2, y + 2);
        }
    }

    /** One product in the grid: a beveled tile with its picture, its price along the bottom and a gold outline when selected. */
    static final class ShopTile extends BaseComponent {
        static final int W = 30, H = 34;
        private final Prod p;
        private final boolean selected;

        ShopTile(Prod p, boolean selected) {
            this.p = p;
            this.selected = selected;
            this.sizing(Sizing.fixed(W), Sizing.fixed(H));
        }

        @Override
        protected int determineHorizontalContentSize(Sizing sizing) { return W; }

        @Override
        protected int determineVerticalContentSize(Sizing sizing) { return H; }

        static void drawIcon(OwoUIDrawContext ctx, String icon, int ix, int iy) {
            if (icon != null && icon.startsWith("snobblegems:")) {
                Theme.tex(ctx, Theme.icon(icon.endsWith("ticket") ? "ticket" : "gem"), ix, iy, 16, 16, 0f, 0f, 16, 16, 16, 16);
            } else {
                ItemStack s = iconStack(icon);
                if (!s.isEmpty()) ctx.renderFakeItem(s, ix, iy);
            }
        }

        @Override
        public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
            Theme.blit(ctx, Theme.sprite(p.ok ? "tile" : "tile_locked"), x, y, W, H);
            drawIcon(ctx, p.icon, x + (W - 16) / 2, y + 3);
            if (!p.ok) ctx.fill(x + 3, y + 3, x + W - 3, y + 21, 0x99301418);                         // can't buy it right now: dimmed
            if (p.featured) ctx.fill(x + 2, y + 2, x + 6, y + 6, 0xFFE3B455);                          // featured: a gold corner
            var f = net.minecraft.client.Minecraft.getInstance().font;
            String price = p.price >= 100_000 ? (p.price / 1000) + "k" : String.format("%,d", p.price);
            ctx.drawString(f, price, x + (W - f.width(price)) / 2, y + H - 11, p.ok ? Theme.AQUA : 0xFFB4BBBE, true);
            if (isInBoundingBox(mouseX, mouseY)) Theme.blit(ctx, Theme.sprite("tile_hover"), x, y, W, H);
            if (selected) Theme.blit(ctx, Theme.sprite("tile_selected"), x, y, W, H);
        }
    }
}
