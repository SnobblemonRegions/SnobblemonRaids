package net.snobblemon.hub.gui;

import net.minecraft.network.chat.Component;
import net.snobblemon.cosmetics.core.Shop;
import net.snobblemon.cosmetics.gui.CosmeticsApi;
import net.snobblemon.hub.Fmt;
import net.snobblemon.hub.HubFont;

import java.util.ArrayList;
import java.util.List;

/**
 * Cosmetics tab. Everything shown comes from {@link CosmeticsApi}; this class only sends ids back. Ownership, price
 * and balance are checked again on the server inside the API when a button is pressed.
 */
final class CosmeticsPage {
    private static final int[] RARITY = {0xBFC7E6, 0x58D68D, 0x4DA6FF, 0xB07CFF, 0xFFB84D, 0xFF6A9E};

    private int cat;
    private int page;
    private String selected;
    private boolean confirming;
    private String message = "";

    void draw(HubGui g) {
        g.teal(true);
        g.layer("cosgrid");
        List<CosmeticsApi.Category> cats = CosmeticsApi.categories(g.p);
        if (cats.isEmpty()) {
            g.text(14, 40, "NO COSMETICS LOADED", 0xFFFFFF);
            return;
        }
        cat = Math.max(0, Math.min(cat, cats.size() - 1));
        List<CosmeticsApi.Entry> items = CosmeticsApi.items(g.p, cats.get(cat).id());
        int pages = Math.max(1, (items.size() + 19) / 20);
        page = Math.max(0, Math.min(page, pages - 1));

        for (int i = 0; i < 20; i++) {
            int idx = page * 20 + i;
            if (idx >= items.size()) break;
            CosmeticsApi.Entry e = items.get(idx);
            int col = i % 5, row = 1 + i / 5;
            if (!e.owned()) g.layer("lock_" + i);
            if (e.equipped()) g.layer("worn_" + i);
            if (e.id().equals(selected)) g.layer("sel_" + i);
            List<Component> lore = new ArrayList<>();
            lore.add(Fmt.line(e.rarity(), RARITY[Math.max(0, Math.min(5, e.rarityIndex()))]));
            lore.add(e.owned() ? Fmt.line(e.equipped() ? "Worn" : "Owned", 0x55FF55) : Fmt.line("NOT OWNED", 0xFF5555));
            if (!e.owned() && e.price() > 0) lore.add(Fmt.line(e.price() + " " + CosmeticsApi.currencyName(), 0xFFD166));
            g.stackBtn(col, row, e.icon(), e.name(), lore, (r, s) -> {
                g.click();
                selected = e.id();
                confirming = false;
                message = "";
                g.redraw();
            });
        }

        for (int i = 0; i < 6 && i < cats.size(); i++) {
            final int ci = i;
            if (i == cat) g.layer("catsel_" + i);
            g.stackBtn(i, 5, cats.get(i).icon(), Fmt.line(cats.get(i).name(), i == cat ? 0xFFD166 : 0xFFFFFF), null, (r, s) -> {
                g.click();
                cat = ci; page = 0; selected = null; confirming = false; message = "";
                g.redraw();
            });
        }
        if (pages > 1) {
            g.btn(7, 5, "prev", Fmt.line("Previous page", 0xFFFFFF), null, (r, s) -> { g.click(); page = (page + pages - 1) % pages; g.redraw(); });
            g.btn(8, 5, "next", Fmt.line("Next page", 0xFFFFFF), null, (r, s) -> { g.click(); page = (page + 1) % pages; g.redraw(); });
        }

        CosmeticsApi.Entry sel = selected == null ? null : CosmeticsApi.item(g.p, selected);
        if (sel == null) {
            g.text(101, 56, "PICK AN ITEM", 0xBFE9E6);
            return;
        }
        g.stackBtn(7, 1, sel.icon(), sel.name(), null, null);
        if (!sel.owned()) g.layer("padlock");
        g.text(101, 56, HubFont.fit(HubFont.clean(sel.name().getString()), 66), 0xFFFFFF);
        g.text(101, 66, sel.rarity(), RARITY[Math.max(0, Math.min(5, sel.rarityIndex()))]);
        g.text(101, 76, sel.equipped() ? "WORN" : sel.owned() ? "OWNED" : "NOT OWNED", sel.owned() ? 0x55FF55 : 0xFF5555);
        if (!sel.owned()) g.text(101, 86, sel.price() > 0 ? sel.price() + " GEMS" : "NOT FOR SALE", sel.price() > 0 ? 0xFFD166 : 0xAAAAAA);
        if (!message.isEmpty()) g.text(101, 96, HubFont.fit(message, 66), 0xFFD166);

        if (sel.tryable()) {
            g.btn(6, 4, "tryon", Fmt.line("Try on", 0xFFFFFF),
                    List.of(Fmt.line("Shows it on you for " + CosmeticsApi.previewSeconds() + " seconds.", 0xAAAAAA), Fmt.line("Nothing is bought or equipped.", 0xAAAAAA)),
                    (r, s) -> {
                        g.click();
                        if (CosmeticsApi.tryOn(g.p, sel.id())) { g.close(); g.say("Previewing for " + CosmeticsApi.previewSeconds() + " seconds."); }
                    });
        }
        if (confirming && !sel.owned()) {
            g.btn(7, 4, "confirm", Fmt.line("Confirm purchase", 0x55FF55), List.of(Fmt.line(sel.price() + " " + CosmeticsApi.currencyName(), 0xFFD166)), (r, s) -> {
                g.click();
                confirming = false;
                message = text(CosmeticsApi.buy(g.p, sel.id()));
                g.redraw();
            });
            g.btn(8, 4, "cancel", Fmt.line("Cancel", 0xFF5555), null, (r, s) -> { g.click(); confirming = false; g.redraw(); });
        } else if (!sel.owned()) {
            boolean can = sel.price() > 0 && CosmeticsApi.shopAvailable();
            g.btn(7, 4, can ? "buy" : "buy_off", Fmt.line(can ? "Buy" : "Not for sale", can ? 0xFFD166 : 0xAAAAAA),
                    can ? List.of(Fmt.line(sel.price() + " " + CosmeticsApi.currencyName(), 0xFFD166)) : null,
                    (r, s) -> {
                        g.click();
                        if (!can) { message = "NOT FOR SALE"; g.redraw(); return; }
                        confirming = true;
                        g.redraw();
                    });
        } else if (sel.equippable()) {
            g.btn(7, 4, sel.equipped() ? "unequip" : "equip", Fmt.line(sel.equipped() ? "Take off" : "Equip", 0xFFFFFF), null, (r, s) -> {
                g.click();
                boolean ok = sel.equipped() ? CosmeticsApi.unequip(g.p, sel.id()) : CosmeticsApi.equip(g.p, sel.id());
                message = ok ? "" : "FAILED";
                g.redraw();
            });
        } else {
            g.btn(7, 4, "equip", Fmt.line("Open", 0xFFFFFF), List.of(Fmt.line("Opens the item card.", 0xAAAAAA)),
                    (r, s) -> { g.click(); CosmeticsApi.openCard(g.p, sel.id()); });
        }
    }

    private static String text(Shop.Result r) {
        return switch (r) {
            case OK -> "PURCHASED";
            case ALREADY_OWNED -> "ALREADY OWNED";
            case NOT_FOR_SALE -> "NOT FOR SALE";
            case INSUFFICIENT -> "NOT ENOUGH GEMS";
            case BUSY -> "PLEASE WAIT";
            case UNAVAILABLE -> "SHOP OFFLINE";
            default -> "ERROR";
        };
    }
}
