package net.snobblemon.hubclient.ui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;

import java.util.List;
import java.util.function.IntConsumer;

/** The text view shared by Wiki entries and Updates: a back button, the title, scrolling text, and page arrows for multi-page entries. */
final class Reader {
    private Reader() {}

    static FlowLayout build(int w, int h, String title, List<String> pages, int pg, IntConsumer setPage, Runnable back) {
        FlowLayout root = Ui.column(Sizing.fixed(w), Sizing.fixed(h));
        root.gap(3);
        FlowLayout head = Ui.centeredRow(Sizing.fixed(w), Sizing.fixed(18));
        head.gap(5);
        head.child(Ui.button("Back", "", 50, b -> back.run()));
        head.child(Ui.label(title, Theme.TEXT));
        root.child(head);

        boolean multi = pages.size() > 1;
        int textH = h - 21 - (multi ? 21 : 0);
        FlowLayout box = Ui.column(Sizing.fixed(w), Sizing.fixed(textH));
        box.surface((ctx, c) -> Theme.blit(ctx, Theme.sprite("inset"), c.x(), c.y(), c.width(), c.height()));
        box.padding(Insets.of(4));
        FlowLayout text = Ui.column(Sizing.fill(100), Sizing.content());
        String page = pages.isEmpty() ? "No pages yet." : pages.get(Math.max(0, Math.min(pg, pages.size() - 1)));
        for (String line : page.split("\n", -1)) {
            var l = Components.label(Ui.legacy(line.isEmpty() ? " " : line)).shadow(true);
            l.horizontalSizing(Sizing.fill(100));
            text.child(l);
        }
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), text);
        scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xFFFFFFFF)));
        scroll.scrollbarThiccness(3);
        box.child(scroll);
        root.child(box);

        if (multi) {
            FlowLayout nav = Ui.centeredRow(Sizing.fixed(w), Sizing.fixed(18));
            nav.gap(5);
            var prev = Ui.button("Previous", "", 60, b -> setPage.accept(pg - 1));
            prev.active = pg > 0;
            var next = Ui.button("Next", "primary", 60, b -> setPage.accept(pg + 1));
            next.active = pg < pages.size() - 1;
            nav.child(prev);
            nav.child(Ui.label("Page " + (pg + 1) + " of " + pages.size(), Theme.MUTED));
            nav.child(next);
            root.child(nav);
        }
        return root;
    }
}
