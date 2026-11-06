package net.snobblemon.hub.gui;

import eu.pb4.sgui.api.elements.BookElementBuilder;
import eu.pb4.sgui.api.gui.BookGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.snobblemon.hub.Fmt;
import net.snobblemon.hub.HubConfig;
import net.snobblemon.hub.HubFont;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The update list (newest first, four per page). An entry opens a written book; opening it marks it as read. */
final class UpdatesPage {
    private int page;

    private static List<HubConfig.Update> sorted() {
        List<HubConfig.Update> l = new ArrayList<>(HubConfig.updates);
        l.sort(Comparator.comparing((HubConfig.Update u) -> u.date == null ? "" : u.date).reversed());
        return l;
    }

    void draw(HubGui g) {
        List<HubConfig.Update> all = sorted();
        int pages = Math.max(1, (all.size() + 3) / 4);
        page = Math.max(0, Math.min(page, pages - 1));
        g.layer("upd");
        for (int i = 0; i < 4; i++) {
            int idx = page * 4 + i;
            if (idx >= all.size()) break;
            HubConfig.Update u = all.get(idx);
            boolean fresh = !HubConfig.isRead(g.p.getUUID(), u);
            int row = 1 + i;
            g.btn(0, row, "paper", Fmt.line(u.title, 0xFFFFFF),
                    List.of(Fmt.line(u.date + (u.version.isBlank() ? "" : "  " + u.version), 0xAAAAAA), Fmt.line("Click to read", 0xFFD166)),
                    (r, s) -> { g.click(); read(g, u); });
            int y = 18 + 18 * row;
            g.text(28, y + 1, HubFont.fit(u.title, fresh ? 100 : 138), 0xFFFFFF);
            g.text(28, y + 9, HubFont.fit(u.date + (u.version.isBlank() ? "" : "  " + u.version), 138), 0x9AA4D6);
            if (fresh) g.textRight(168, y + 5, "NEW", 0xFFD166);
        }
        if (all.isEmpty()) g.text(28, 40, "NO UPDATES YET", 0x9AA4D6);
        if (pages > 1) {
            g.btn(1, 5, "prev", Fmt.line("Newer", 0xFFFFFF), null, (r, s) -> { g.click(); page = (page + pages - 1) % pages; g.redraw(); });
            g.btn(2, 5, "next", Fmt.line("Older", 0xFFFFFF), null, (r, s) -> { g.click(); page = (page + 1) % pages; g.redraw(); });
            g.text(52, 113, "PAGE " + (page + 1) + " OF " + pages, 0x9AA4D6);
        }
    }

    private void read(HubGui g, HubConfig.Update u) {
        HubConfig.markRead(g.p.getUUID(), u);
        BookElementBuilder b = new BookElementBuilder().setTitle(u.title).setAuthor("Snobblemon Regions");
        List<String> lines = new ArrayList<>();
        lines.add("&l" + u.title + "&r");
        lines.add("&8" + u.date + (u.version.isBlank() ? "" : "  " + u.version) + "&r");
        add(lines, "&2New", u.added);
        add(lines, "&1Changes", u.changed);
        add(lines, "&6Fixes", u.fixed);
        // about 12 wrapped lines fit on a page
        StringBuilder page = new StringBuilder();
        int used = 0;
        for (String line : lines) {
            int cost = Math.max(1, (line.replaceAll("&.", "").length() + 17) / 18) + (line.startsWith("&") && !line.startsWith("&l") && !line.startsWith("&8") ? 1 : 0);
            if (used + cost > 12 && page.length() > 0) {
                b.addPage(Fmt.parse(page.toString()));
                page.setLength(0);
                used = 0;
            }
            page.append(line).append('\n');
            used += cost;
        }
        if (page.length() > 0) b.addPage(Fmt.parse(page.toString()));
        final ServerPlayer p = g.p;
        g.close();
        new BookGui(p, b) {
            @Override
            public void onClose() {
                p.getServer().execute(() -> HubGui.open(p, HubGui.Mode.UPDATES));
            }
        }.open();
    }

    private static void add(List<String> out, String heading, List<String> items) {
        if (items == null || items.isEmpty()) return;
        out.add(heading + "&r");
        for (String s : items) out.add("- " + s);
    }
}
