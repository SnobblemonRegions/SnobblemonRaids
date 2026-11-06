package net.snobblemon.hub.gui;

import eu.pb4.sgui.api.elements.BookElementBuilder;
import eu.pb4.sgui.api.gui.BookGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.snobblemon.hub.Fmt;
import net.snobblemon.hub.HubConfig;
import net.snobblemon.hub.HubFont;

import java.util.List;

/** Six picture cards from wiki.json. A card opens a written book with its pages, or runs a command (such as /wiki). */
final class WikiPage {
    void draw(HubGui g) {
        g.layer("wiki");
        List<HubConfig.WikiEntry> list = HubConfig.wiki;
        for (int i = 0; i < 6 && i < list.size(); i++) {
            HubConfig.WikiEntry e = list.get(i);
            int cc = (i % 3) * 3, rr = 1 + (i / 3) * 2;
            for (int dx = 0; dx < 3; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    g.area(cc + dx, rr + dy, Fmt.line(e.title, 0xFFFFFF),
                            List.of(Fmt.line(e.command == null || e.command.isBlank() ? "Click to read" : "Click to open", 0xFFD166)),
                            (r, s) -> { g.click(); open(g, e); });
                }
            }
            g.textCenter(8 + 18 * cc + 26, 18 + 18 * rr + 25, HubFont.fit(e.title, 48), 0xFFFFFF);
        }
        g.btn(0, 5, "anvil", Fmt.line("Full wiki", 0xFFFFFF),
                List.of(Fmt.line("Opens the SnobbleWiki, if installed.", 0xAAAAAA)),
                (r, s) -> { g.click(); g.close(); run(g.p, "wiki"); });
    }

    private static void run(ServerPlayer p, String cmd) {
        p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), cmd);
    }

    private void open(HubGui g, HubConfig.WikiEntry e) {
        if (e.command != null && !e.command.isBlank()) { g.close(); run(g.p, e.command); return; }
        BookElementBuilder b = new BookElementBuilder().setTitle(e.title).setAuthor("Snobblemon Regions");
        if (e.pages.isEmpty()) b.addPage(Component.literal("No pages yet."));
        for (String page : e.pages) b.addPage(Fmt.parse(page));
        g.close();
        final ServerPlayer p = g.p;
        final HubGui.Mode back = g.mode();
        new BookGui(p, b) {
            @Override
            public void onClose() {
                p.getServer().execute(() -> HubGui.open(p, back));
            }
        }.open();
    }
}
