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

/** Wiki: picture cards from wiki.json. A card with pages opens the reader here; a card with a command asks the server to run it. */
public final class WikiScreen extends SnobbleScreen {
    private static final Gson GSON = new Gson();
    static final int[][] COLORS = {{0xFF7CCBFF, 0xFF3B86D0}, {0xFF5FD36F, 0xFF2FB0A0}, {0xFF5B3BA8, 0xFF9A5BE0},
            {0xFFF2C760, 0xFFC98A3A}, {0xFFF08ACB, 0xFFA066E0}, {0xFFF28A4A, 0xFFF5D37A}};

    static final class Entry { String title; boolean cmd; List<String> pages = new ArrayList<>(); }
    static final class Model { List<Entry> entries = new ArrayList<>(); }

    private Model model;
    private int open = -1, pg;

    public WikiScreen(JsonObject data) {
        super("wiki", data);
        this.model = GSON.fromJson(data, Model.class);
    }

    @Override
    protected int[] windowSize(int sw, int sh) {
        return new int[]{clamp(sw * 0.84, 310, 364), clamp(sh * 0.90, 212, 236)};
    }

    @Override
    protected void buildBody(FlowLayout body, int bw, int bh) {
        if (open >= 0 && open < model.entries.size()) {
            Entry e = model.entries.get(open);
            body.child(Reader.build(bw, bh, e.title, e.pages, pg, p -> { pg = p; rebuild(); }, () -> { open = -1; pg = 0; rebuild(); }));
            return;
        }
        FlowLayout col = Ui.column(Sizing.fixed(bw), Sizing.fixed(bh));
        col.gap(3);
        int cw = (bw - 6) / 3, ch = (bh - 18 - 6) / 2;
        GridLayout grid = Containers.grid(Sizing.fixed(bw), Sizing.fixed(ch * 2 + 3), 2, 3);
        for (int i = 0; i < 6 && i < model.entries.size(); i++) {
            final int idx = i;
            Entry e = model.entries.get(i);
            int[] c = COLORS[i % COLORS.length];
            PictureCard card = new PictureCard(e.title, e.cmd ? "Opens a command" : "Click to read", c[0], c[1], true, false, cw, ch);
            card.margins(Insets.of(0, 3, 0, i % 3 == 2 ? 0 : 3));
            card.mouseDown().subscribe((mx, my, b) -> {
                if (e.cmd) HubClient.request("wikiRun", String.valueOf(idx)); else { open = idx; pg = 0; rebuild(); }
                return true;
            });
            grid.child(card, i / 3, i % 3);
        }
        col.child(grid);
        col.child(Ui.button("Full wiki", "primary", 90, b -> HubClient.request("wikiFull", "")));
        body.child(col);
    }
}
