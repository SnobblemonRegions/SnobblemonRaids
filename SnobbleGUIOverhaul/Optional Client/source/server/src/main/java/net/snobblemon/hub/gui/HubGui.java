package net.snobblemon.hub.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.snobblemon.hub.Fmt;
import net.snobblemon.hub.HubData;
import net.snobblemon.hub.HubFont;
import net.snobblemon.hub.HubIcons;

import java.util.List;

/**
 * The one menu behind every hub screen: a 9x6 container whose title carries the artwork. The player's own inventory
 * below is the real inventory, so dragging, shift-clicking and the hotbar keys work as usual. Tabs switch the page in
 * place (the title is re-sent), they do not open a new window.
 */
public final class HubGui extends SimpleGui {
    public enum Mode { HUB, WIKI, COSMETICS, CARD, WARPS, GYMS, UPDATES, SHOP }

    /** Tab order on the top row. */
    private static final Mode[] TABS = {Mode.HUB, Mode.WIKI, Mode.COSMETICS, Mode.CARD, Mode.WARPS, Mode.UPDATES};
    private static final String[] TAB_ICONS = {"tab_inventory", "tab_wiki", "tab_cosmetics", "tab_card", "tab_warps", "tab_updates"};
    private static final String[] TAB_NAMES = {"Inventory", "Wiki", "Cosmetics", "Trainer Card", "Warps", "Updates"};
    private static final String[] TAB_HINTS = {"Your items, armor and offhand.", "How the server works.", "Try on and buy cosmetics.",
            "Your profile and balances.", "Teleport destinations and gyms.", "What changed recently."};

    public final ServerPlayer p;
    private Mode mode;
    private String accent;
    private boolean teal;

    private final HubPage hubPage = new HubPage();
    private final WikiPage wikiPage = new WikiPage();
    private final CardPage cardPage = new CardPage();
    private final CosmeticsPage cosPage = new CosmeticsPage();
    private final WarpPage warpPage = new WarpPage();
    private final GymsPage gymsPage = new GymsPage();
    private final UpdatesPage updPage = new UpdatesPage();

    private HubGui(ServerPlayer player, Mode mode) {
        super(MenuType.GENERIC_9x6, player, false);
        this.p = player;
        this.mode = mode;
    }

    public static void open(ServerPlayer player, Mode mode) {
        if (mode == Mode.SHOP) {   // the Gem Shop has its own chest menu; players without the client mod get that one
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "gemshop");
            return;
        }
        HubGui g = new HubGui(player, mode);
        g.redraw();
        g.open();
    }

    // ------------------------------------------------------------------------------------------ page plumbing

    public void go(Mode m) {
        this.mode = m;
        redraw();
    }

    public Mode mode() {
        return mode;
    }

    public String accent() {
        return accent;
    }

    /** Marks the content panel as the lighter teal one (used by the cosmetics page, like the reference). */
    public void teal(boolean v) {
        this.teal = v;
    }

    public void redraw() {
        for (int i = 0; i < 54; i++) clearSlot(i);
        accent = HubData.accent(p);
        teal = false;
        ops.clear();
        String header;
        switch (mode) {
            case HUB -> { header = "TRAINER HUB"; hubPage.draw(this); }
            case WIKI -> { header = "WIKI"; wikiPage.draw(this); }
            case CARD -> { header = "TRAINER CARD"; cardPage.draw(this); }
            case COSMETICS -> { header = "COSMETICS"; cosPage.draw(this); }
            case WARPS -> { header = "WARP MENU"; warpPage.draw(this); }
            case GYMS -> { header = "GYMS"; gymsPage.draw(this); }
            default -> { header = "SERVER UPDATES"; updPage.draw(this); }
        }
        tabs();
        // layers first (later glyphs draw on top), then every piece of text
        HubFont.Title title = HubFont.title();
        title.layer("base_" + accent + "_" + (teal ? "teal" : "navy"));
        for (Object[] o : ops) if (o[0].equals("layer")) title.layer((String) o[1]);
        title.layer("tabsel_" + tabIndex());
        title.text(8, 7, header, 0xFFFFFF);
        for (Object[] o : ops) {
            switch ((String) o[0]) {
                case "text" -> title.text((int) o[1], (int) o[2], (String) o[3], (int) o[4]);
                case "right" -> title.textRight((int) o[1], (int) o[2], (String) o[3], (int) o[4]);
                case "center" -> title.textCenter((int) o[1], (int) o[2], (String) o[3], (int) o[4]);
                default -> { }
            }
        }
        ops.clear();
        setTitle(title.build());
        fillEmpty();
    }

    /** Layers and text queued by the current page. */
    private final java.util.List<Object[]> ops = new java.util.ArrayList<>();

    public void layer(String name) {
        ops.add(new Object[]{"layer", name});
    }

    public void text(int x, int y, String s, int rgb) {
        ops.add(new Object[]{"text", x, y, s, rgb});
    }

    public void textRight(int rightX, int y, String s, int rgb) {
        ops.add(new Object[]{"right", rightX, y, s, rgb});
    }

    public void textCenter(int cx, int y, String s, int rgb) {
        ops.add(new Object[]{"center", cx, y, s, rgb});
    }

    private int tabIndex() {
        Mode m = mode == Mode.GYMS ? Mode.WARPS : mode;
        for (int i = 0; i < TABS.length; i++) if (TABS[i] == m) return i;
        return 0;
    }

    // ------------------------------------------------------------------------------------------ elements

    public static int slot(int col, int row) {
        return row * 9 + col;
    }

    public interface Click {
        void on(boolean right, boolean shift);
    }

    /** A button: an icon, a tooltip and an action. */
    public void btn(int col, int row, String icon, Component name, List<Component> lore, Click action) {
        GuiElementBuilder b = new GuiElementBuilder(HubIcons.stack(icon)).hideDefaultTooltip();
        b.setName(name);
        if (lore != null) for (Component l : lore) b.addLoreLine(l);
        b.setCallback((idx, type, act, gui) -> {
            if (action != null) action.on(type.isRight, type.shift);
        });
        setSlot(slot(col, row), b);
    }

    /** A button that shows an existing item stack (a cosmetic's model) instead of an icon. */
    public void stackBtn(int col, int row, net.minecraft.world.item.ItemStack stack, Component name, List<Component> lore, Click action) {
        GuiElementBuilder b = new GuiElementBuilder(stack.copy()).hideDefaultTooltip();
        b.setName(name);
        if (lore != null) for (Component l : lore) b.addLoreLine(l);
        b.setCallback((idx, type, act, gui) -> {
            if (action != null) action.on(type.isRight, type.shift);
        });
        setSlot(slot(col, row), b);
    }

    /** A transparent, tooltip-less button area (the artwork is in the background). */
    public void area(int col, int row, Component name, List<Component> lore, Click action) {
        btn(col, row, "blank", name, lore, action);
    }

    private void fillEmpty() {
        for (int i = 0; i < 54; i++) {
            if (getSlot(i) == null && getSlotRedirect(i) == null) {
                setSlot(i, new GuiElementBuilder(HubIcons.stack("blank")).setName(Component.empty()).hideTooltip());
            }
        }
    }

    /** The six tabs and the close button. */
    public void tabs() {
        int active = tabIndex();
        for (int i = 0; i < TABS.length; i++) {
            final Mode target = TABS[i];
            btn(i, 0, TAB_ICONS[i], Fmt.line(TAB_NAMES[i], i == active ? 0xFFD166 : 0xFFFFFF),
                    List.of(Fmt.line(TAB_HINTS[i], 0xAAAAAA), Fmt.line(i == active ? "You are here" : "Click to open", 0xFFD166)),
                    (r, s) -> { click(); if (target != mode) go(target); });
        }
        btn(8, 0, "close", Fmt.line("Close", 0xFF5555), List.of(Fmt.line("Back to the game", 0xAAAAAA)), (r, s) -> { click(); close(); });
    }

    public void click() {
        p.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.5f, 1f);
    }

    public void say(String msg) {
        p.sendSystemMessage(Fmt.parse("&8[&bHub&8] &7" + msg));
    }
}
