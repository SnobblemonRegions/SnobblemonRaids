package net.snobblemon.hubclient.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.snobblemon.hubclient.HubClient;

import java.util.ArrayList;
import java.util.List;

/** Updates: newest first, a gold NEW tag on unread ones; clicking one reads it here and tells the server it was read. */
public final class UpdatesScreen extends SnobbleScreen {
    private static final Gson GSON = new Gson();

    static final class Upd { int i; String title = "", date = "", version = ""; boolean fresh; List<String> added = new ArrayList<>(), changed = new ArrayList<>(), fixed = new ArrayList<>(); }
    static final class Model { List<Upd> updates = new ArrayList<>(); }

    private Model model;
    private int open = -1;

    public UpdatesScreen(JsonObject data) {
        super("upd", data);
        this.model = GSON.fromJson(data, Model.class);
    }

    @Override
    protected int[] windowSize(int sw, int sh) {
        return new int[]{clamp(sw * 0.84, 310, 364), clamp(sh * 0.90, 212, 236)};
    }

    private static void section(StringBuilder sb, String heading, List<String> items) {
        if (items == null || items.isEmpty()) return;
        sb.append(heading).append("&r\n");
        for (String s : items) sb.append("- ").append(s).append('\n');
        sb.append('\n');
    }

    @Override
    protected void buildBody(FlowLayout body, int bw, int bh) {
        if (open >= 0 && open < model.updates.size()) {
            Upd u = model.updates.get(open);
            StringBuilder sb = new StringBuilder("&7" + u.date + (u.version.isBlank() ? "" : "  " + u.version) + "&r\n\n");
            section(sb, "&aNew", u.added);
            section(sb, "&bChanges", u.changed);
            section(sb, "&6Fixes", u.fixed);
            body.child(Reader.build(bw, bh, u.title, List.of(sb.toString()), 0, p -> { }, () -> { open = -1; rebuild(); }));
            return;
        }
        FlowLayout list = Ui.column(Sizing.fill(100), Sizing.content());
        list.gap(2);
        for (Upd u : model.updates) {
            FlowLayout row = Ui.centeredRow(Sizing.fill(100), Sizing.fixed(24));
            row.surface((ctx, c) -> Theme.blit(ctx, Theme.sprite("bar"), c.x(), c.y(), c.width(), c.height()));
            row.padding(Insets.of(0, 0, 5, 12));
            row.gap(6);
            row.child(Ui.icon("updates"));
            FlowLayout text = Ui.column(Sizing.expand(), Sizing.content());
            text.child(Ui.label(u.title, Theme.TEXT));
            text.child(Ui.label(u.date + (u.version.isBlank() ? "" : "  " + u.version), Theme.MUTED));
            row.child(text);
            if (u.fresh) row.child(Ui.label("NEW", Theme.GOLD));
            row.mouseDown().subscribe((mx, my, b) -> {
                open = model.updates.indexOf(u);
                if (u.fresh) { u.fresh = false; HubClient.request("updRead", String.valueOf(u.i)); }
                rebuild();
                return true;
            });
            list.child(row);
        }
        if (model.updates.isEmpty()) list.child(Ui.label("No updates yet.", Theme.MUTED));
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fixed(bw), Sizing.fixed(bh), list);
        scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xFFFFFFFF)));
        scroll.scrollbarThiccness(3);
        body.child(scroll);
    }
}
