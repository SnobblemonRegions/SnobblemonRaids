package net.snobblemon.hubclient.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.snobblemon.hubclient.HubClient;

import java.util.ArrayList;
import java.util.List;

/** Warps: destination cards, a confirm bar, and the gym list behind the Gyms card. The server does the teleport (cooldown, warm-up). */
public final class WarpScreen extends SnobbleScreen {
    private static final Gson GSON = new Gson();
    private static final int[][] COLORS = {{0xFFB9B2A8, 0xFF6E6A60}, {0xFF3A2B5E, 0xFF8A6AD0}, {0xFFC8442A, 0xFF5A1A12},
            {0xFF7FD4F5, 0xFF4EAA5A}, {0xFFF2C760, 0xFFC96A3A}};

    static final class Dest { String id, label, desc; boolean gyms, usable; }
    static final class Gym { int i; String name, type; boolean locked, usable; }
    static final class Model { List<Dest> dests = new ArrayList<>(); List<Gym> gyms = new ArrayList<>(); }

    private Model model;
    private int selected = -1;
    private boolean gymView;
    private int gymSel = -1;

    public WarpScreen(JsonObject data) {
        super("warp", data);
        this.model = GSON.fromJson(data, Model.class);
    }

    @Override
    protected int[] windowSize(int sw, int sh) {
        return new int[]{clamp(sw * 0.84, 310, 364), clamp(sh * 0.90, 212, 236)};
    }

    @Override
    protected void buildBody(FlowLayout body, int bw, int bh) {
        FlowLayout col = Ui.column(Sizing.fixed(bw), Sizing.fixed(bh));
        col.gap(3);
        int gridH = bh - 21;
        if (gymView) {
            int cols = 4, cw = (bw - 3 * (cols - 1)) / cols, rows = 2, ch = (gridH - 3) / rows;
            GridLayout grid = Containers.grid(Sizing.fixed(bw), Sizing.fixed(gridH), rows, cols);
            for (int n = 0; n < 8 && n < model.gyms.size(); n++) {
                Gym g = model.gyms.get(n);
                int[] c = g.locked ? new int[]{0xFF8A3A44, 0xFF3A1A20} : COLORS[n % COLORS.length];
                PictureCard card = new PictureCard(g.name + " Gym", g.locked ? "Locked" : g.type, c[0], c[1], g.usable, gymSel == g.i, cw, ch);
                card.margins(Insets.of(0, 3, 0, n % cols == cols - 1 ? 0 : 3));
                card.mouseDown().subscribe((mx, my, b) -> { if (g.usable) { gymSel = g.i; rebuild(); } return true; });
                grid.child(card, n / cols, n % cols);
            }
            col.child(grid);
            FlowLayout bar = Ui.centeredRow(Sizing.fixed(bw), Sizing.fixed(18));
            bar.gap(4);
            bar.child(Ui.button("Back", "", 50, b -> { gymView = false; gymSel = -1; rebuild(); }));
            if (gymSel >= 0) {
                String name = model.gyms.stream().filter(g -> g.i == gymSel).map(g -> g.name).findFirst().orElse("");
                bar.child(Ui.label("Go to " + name + "?", Theme.TEXT));
                bar.child(Ui.button("Teleport", "primary", 60, b -> HubClient.request("gymGo", String.valueOf(gymSel))));
                bar.child(Ui.button("Cancel", "danger", 50, b -> { gymSel = -1; rebuild(); }));
            }
            col.child(bar);
            body.child(col);
            return;
        }
        int cw = (bw - 6) / 3, ch = (gridH - 3) / 2;
        GridLayout grid = Containers.grid(Sizing.fixed(bw), Sizing.fixed(gridH), 2, 3);
        for (int i = 0; i < model.dests.size() && i < 6; i++) {
            final int idx = i;
            Dest d = model.dests.get(i);
            int[] c = COLORS[i % COLORS.length];
            PictureCard card = new PictureCard(d.label, d.usable ? d.desc : "Not set up yet", c[0], c[1], d.usable, selected == i, cw, ch);
            card.margins(Insets.of(0, 3, 0, i % 3 == 2 ? 0 : 3));
            card.mouseDown().subscribe((mx, my, b) -> {
                if (!d.usable) return true;
                if (d.gyms) { gymView = true; gymSel = -1; rebuild(); } else { selected = idx; rebuild(); }
                return true;
            });
            grid.child(card, i / 3, i % 3);
        }
        col.child(grid);
        FlowLayout bar = Ui.centeredRow(Sizing.fixed(bw), Sizing.fixed(18));
        bar.gap(4);
        if (selected >= 0 && selected < model.dests.size()) {
            Dest d = model.dests.get(selected);
            bar.child(Ui.label("Teleport to " + d.label + "?", Theme.TEXT));
            bar.child(Ui.button("Teleport", "primary", 60, b -> HubClient.request("warpGo", d.id)));
            bar.child(Ui.button("Cancel", "danger", 50, b -> { selected = -1; rebuild(); }));
        } else {
            bar.child(Ui.label("Pick a destination", Theme.MUTED));
        }
        col.child(bar);
        body.child(col);
    }
}
