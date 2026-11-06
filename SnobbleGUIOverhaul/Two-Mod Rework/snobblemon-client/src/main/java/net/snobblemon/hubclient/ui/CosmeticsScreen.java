package net.snobblemon.hubclient.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;
import net.snobblemon.hubclient.HubClient;

import java.util.ArrayList;
import java.util.List;

/**
 * The wardrobe. Left: categories and a scrolling grid. Right: the live player wearing the selected item, its details,
 * and the Buy / Equip / Reset buttons. Selecting a wearable asks the server for a preview (nothing is bought or
 * equipped); the server answers by dressing the player, which the preview then draws.
 */
public final class CosmeticsScreen extends SnobbleScreen {
    private static final Gson GSON = new Gson();

    public static final class Item {
        String id, name, rarity, icon;
        List<String> desc = new ArrayList<>();
        int ri;
        boolean owned, worn, equippable, back;
        String species, aspect;      // set for Pokemon skins: the preview shows the real Pokemon wearing it
        long price;
    }

    public static final class Cat {
        String id, name, icon;
        List<Item> items = new ArrayList<>();
    }

    public static final class Model {
        String currency = "Gems";
        boolean shop;
        List<Cat> cats = new ArrayList<>();
    }

    private Model model;
    private int cat;
    private String selected;
    private boolean confirming;
    private boolean sessionOpen;

    public CosmeticsScreen(JsonObject data) {
        super("cos", data);
        this.model = GSON.fromJson(data, Model.class);
        if (net.snobblemon.hubclient.HubClientMain.TEST_SELECT != null) this.selected = net.snobblemon.hubclient.HubClientMain.TEST_SELECT;
    }

    @Override
    public void update(JsonObject fresh) {
        this.model = GSON.fromJson(fresh, Model.class);
        this.confirming = false;
        super.update(fresh);
    }

    @Override
    protected int[] windowSize(int sw, int sh) {
        return new int[]{clamp(sw * 0.84, 310, 364), clamp(sh * 0.90, 212, 236)};
    }

    @Override
    protected void init() {
        super.init();
        if (!sessionOpen) {
            sessionOpen = true;
            HubClient.request("session", "start");   // the server holds previews while this screen is open
            if (net.snobblemon.hubclient.HubClientMain.TEST_SELECT != null) {
                // the server's flood guard drops requests closer than 60 ms, so the test hook waits
                net.minecraft.Util.backgroundExecutor().execute(() -> {
                    try { Thread.sleep(400); } catch (InterruptedException ignored) { }
                    net.minecraft.client.Minecraft.getInstance().execute(() -> HubClient.request("try", selected));
                });
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (sessionOpen) {
            sessionOpen = false;
            HubClient.request("session", "stop");
        }
    }

    private Item selectedItem() {
        if (selected == null || model.cats.isEmpty()) return null;
        for (Cat c : model.cats) for (Item i : c.items) if (i.id.equals(selected)) return i;
        return null;
    }

    private static FlowLayout roundPanel(int w, int h) {
        FlowLayout f = Ui.column(Sizing.fixed(w), Sizing.fixed(h));
        f.surface((ctx, c) -> Theme.blit(ctx, Theme.sprite("panel"), c.x(), c.y(), c.width(), c.height()));
        f.padding(Insets.of(3));
        return f;
    }

    /** A 22 px square button: a lit slot with an item on it, optionally outlined green when worn or white when selected. */
    private static io.wispforest.owo.ui.component.ButtonComponent slotButton(net.minecraft.world.item.ItemStack stack, boolean on, boolean worn, boolean ghost, Runnable press) {
        var b = Components.button(Component.empty(), btn -> press.run());
        b.sizing(Sizing.fixed(22), Sizing.fixed(22));
        b.renderer((ctx, btn, delta) -> {
            Theme.blit(ctx, Theme.sprite(on ? "cat_on" : btn.isHovered() ? "cat_hover" : "cat"), btn.x(), btn.y(), 22, 22);
            if (!stack.isEmpty()) {
                ctx.renderFakeItem(stack, btn.x() + 3, btn.y() + 3);
                if (ghost) ctx.fill(btn.x() + 2, btn.y() + 2, btn.x() + 20, btn.y() + 20, 0x88202428);   // empty slot: dimmed silhouette
            }
            if (worn) Theme.blit(ctx, Theme.sprite("slot_worn"), btn.x(), btn.y(), 22, 22);
        });
        return b;
    }

    @Override
    protected void buildBody(FlowLayout body, int bw, int bh) {
        // three columns: categories | item grid | the player (with what is worn beside it), details and buttons
        if (model.cats.isEmpty()) {
            body.child(Ui.label("No cosmetics are available.", Theme.MUTED));
            return;
        }
        cat = Math.max(0, Math.min(cat, model.cats.size() - 1));
        Item sel = selectedItem();
        int catW = 28, rightW = Math.max(120, (int) (bw * 0.38)), gridW = bw - catW - rightW - 6;

        FlowLayout top = Ui.row(Sizing.fixed(bw), Sizing.fixed(bh));
        top.gap(3);

        // left: category buttons
        FlowLayout left = roundPanel(catW, bh);
        left.gap(2);
        left.horizontalAlignment(io.wispforest.owo.ui.core.HorizontalAlignment.CENTER);
        for (int i = 0; i < model.cats.size(); i++) {
            final int ci = i;
            Cat c = model.cats.get(i);
            var b = slotButton(Ui.stack(c.icon), ci == cat, false, false, () -> { cat = ci; confirming = false; rebuild(); });
            b.tooltip(Component.literal(c.name));
            left.child(b);
        }
        top.child(left);

        // middle: the item grid
        FlowLayout gridBox = Ui.column(Sizing.fixed(gridW), Sizing.fixed(bh));
        gridBox.surface((ctx, c) -> {                                           // a lit screen, not a dark well
            ctx.fill(c.x(), c.y(), c.x() + c.width(), c.y() + c.height(), 0x30FFFFFF);
            ctx.fill(c.x(), c.y(), c.x() + c.width(), c.y() + 1, 0x99FFFFFF);
            ctx.fill(c.x(), c.y() + c.height() - 1, c.x() + c.width(), c.y() + c.height(), 0x99FFFFFF);
            ctx.fill(c.x(), c.y(), c.x() + 1, c.y() + c.height(), 0x99FFFFFF);
            ctx.fill(c.x() + c.width() - 1, c.y(), c.x() + c.width(), c.y() + c.height(), 0x99FFFFFF);
        });
        gridBox.padding(Insets.of(3));
        gridBox.gap(2);
        List<Item> items = model.cats.get(cat).items;
        long ownedN = items.stream().filter(i -> i.owned).count();
        gridBox.child(Ui.label(model.cats.get(cat).name + "  -  Owned " + ownedN + " / " + items.size(), Theme.TEXT));
        int cols = Math.max(3, (gridW - 6 - 6) / (CosmeticTile.SIZE + 2));
        int rows = Math.max(Math.max(5, (bh - 22) / (CosmeticTile.SIZE + 2)), (items.size() + cols - 1) / cols);          // empty cells fill the screen like the reference grid
        GridLayout grid = Containers.grid(Sizing.content(), Sizing.content(), rows, cols);
        for (int n = 0; n < items.size(); n++) {
            Item it = items.get(n);
            CosmeticTile tile = new CosmeticTile(Ui.stack(it.icon), it.owned, it.worn,
                    Theme.RARITY[Math.max(0, Math.min(5, it.ri))], it.id.equals(selected));
            tile.margins(Insets.of(1));
            tile.tooltip(tooltip(it));
            tile.mouseDown().subscribe((mx, my, button) -> {
                selected = it.id;
                confirming = false;
                toastText = "";
                if (it.equippable) HubClient.request("try", it.id);   // preview only: the server decides, nothing is bought
                rebuild();
                return true;
            });
            grid.child(tile, n / cols, n % cols);
        }
        for (int n = items.size(); n < rows * cols; n++) {
            var cell = Components.box(Sizing.fixed(CosmeticTile.SIZE), Sizing.fixed(CosmeticTile.SIZE));
            cell.color(Color.ofArgb(0x30000000)).fill(true);
            cell.margins(Insets.of(1));
            grid.child(cell, n / cols, n % cols);
        }
        ScrollContainer<GridLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), grid);
        scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xFFFFFFFF)));
        scroll.scrollbarThiccness(3);
        gridBox.child(scroll);
        top.child(gridBox);

        // right: the player and what is worn, then details, then buttons
        FlowLayout right = Ui.column(Sizing.fixed(rightW), Sizing.fixed(bh));
        right.gap(2);
        int infoH = 22, btnH = 18;
        int stageH = bh - infoH - btnH - 4;

        FlowLayout stage = Ui.row(Sizing.fixed(rightW), Sizing.fixed(stageH));
        if (sel != null && sel.species != null) {
            var mon = new PokemonPreview(sel.species, sel.aspect);
            mon.sizing(Sizing.expand(), Sizing.fill(100));
            stage.child(mon);
        } else {
            PlayerPreview preview = new PlayerPreview(true, true);
            preview.sizing(Sizing.expand(), Sizing.fill(100));
            if (sel != null && sel.back) preview.turnTo(PlayerPreview.BACK_YAW);
            stage.child(preview);
        }
        FlowLayout worn = Ui.column(Sizing.fixed(24), Sizing.fill(100));
        worn.padding(Insets.of(2, 0, 1, 1));
        worn.gap(1);
        worn.surface((ctx, c) -> ctx.fill(c.x(), c.y(), c.x() + c.width(), c.y() + c.height(), 0x55000000));
        for (Cat c : model.cats) {
            Item w = null;
            for (Item it : c.items) if (it.worn) { w = it; break; }
            final Item wi = w;
            var stack = wi != null ? Ui.stack(wi.icon) : Ui.stack(c.icon);
            var b = slotButton(stack, wi != null && wi.id.equals(selected), wi != null, wi == null, () -> {
                if (wi != null) { selected = wi.id; confirming = false; toastText = ""; rebuild(); }
            });
            b.tooltip(Component.literal(wi != null ? wi.name : c.name + " (nothing worn)"));
            worn.child(b);
        }
        stage.child(worn);
        right.child(stage);

        FlowLayout info = Ui.column(Sizing.fixed(rightW), Sizing.fixed(infoH));
        info.surface((ctx, c) -> Theme.blit(ctx, Theme.sprite("bar_dark"), c.x(), c.y(), c.width(), c.height()));
        info.padding(Insets.of(2, 2, 4, 4));
        info.gap(1);
        if (sel == null) {
            info.child(Ui.label("Pick a cosmetic", Theme.MUTED));
        } else {
            // long names scroll by themselves instead of being cut off
            info.child(new MarqueeText(rightW - 8, 9, true)
                    .add(sel.name, Theme.TEXT).add("  " + sel.rarity, Theme.RARITY[Math.max(0, Math.min(5, sel.ri))]));
            FlowLayout l2 = Ui.centeredRow(Sizing.fixed(rightW - 8), Sizing.fixed(9));
            l2.gap(3);
            if (!toastText.isEmpty()) {
                l2.child(new MarqueeText(rightW - 8, 9, true).add(toastText, toastOk ? Theme.GREEN : 0xFFFF9AA0));
            } else if (sel.owned) {
                l2.child(Ui.label(sel.worn ? "WORN" : "OWNED", Theme.GREEN));
            } else {
                l2.child(Ui.label("LOCKED", 0xFFFF9AA0));
                if (sel.price > 0) {
                    l2.child(Ui.icon("gem").sizing(Sizing.fixed(9), Sizing.fixed(9)));
                    l2.child(Ui.label(String.format("%,d", sel.price), Theme.AQUA));
                }
            }
            info.child(l2);
        }
        right.child(info);
        right.child(buttons(rightW, btnH, sel));
        top.child(right);
        body.child(top);
    }

    private FlowLayout buttons(int w, int h, Item sel) {
        FlowLayout row = Ui.centeredRow(Sizing.fixed(w), Sizing.fixed(h));
        row.gap(3);
        if (sel == null) return row;
        int bw = (w - 6) / 2;
        if (confirming && !sel.owned) {
            row.child(Ui.button("Confirm " + String.format("%,d", sel.price), "gold", bw, b -> {
                confirming = false;
                HubClient.request("buy", sel.id);
            }));
            row.child(Ui.button("Cancel", "danger", bw, b -> {
                confirming = false;
                rebuild();
            }));
            return row;
        }
        if (!sel.owned) {
            boolean can = sel.price > 0 && model.shop;
            var buy = Ui.button(can ? "Purchase" : "Not for sale", "", bw, b -> {
                confirming = true;
                rebuild();
            });
            buy.active = can;
            row.child(buy);
        } else if (sel.equippable) {
            row.child(Ui.button(sel.worn ? "Take off" : "Equip", "primary", bw, b -> HubClient.request(sel.worn ? "unequip" : "equip", sel.id)));
        } else if (sel.species != null) {
            row.child(Ui.button(sel.worn ? "Take off" : "Apply", "primary", bw, b -> HubClient.request(sel.worn ? "removeSkin" : "applySkin", sel.id)));
        } else {
            row.child(Ui.button("Open", "primary", bw, b -> HubClient.request("openCard", sel.id)));
        }
        var reset = Ui.button("Reset", "", bw, b -> {
            HubClient.request("reset", sel.id);
            toastText = "";
        });
        reset.active = sel.equippable;
        row.child(reset);
        return row;
    }

    private static List<Component> tooltip(Item it) {
        List<Component> t = new ArrayList<>();
        t.add(Component.literal(it.name));
        t.add(Component.literal(it.rarity).withColor(Theme.RARITY[Math.max(0, Math.min(5, it.ri))] & 0xFFFFFF));
        t.add(it.owned ? Component.literal(it.worn ? "Owned, worn" : "Owned").withColor(0x5FD38A) : Component.literal("NOT OWNED").withColor(0xE0646E));
        if (!it.owned && it.price > 0) t.add(Component.literal(String.format("%,d Gems", it.price)).withColor(0x5FD8EA));
        return t;
    }
}
