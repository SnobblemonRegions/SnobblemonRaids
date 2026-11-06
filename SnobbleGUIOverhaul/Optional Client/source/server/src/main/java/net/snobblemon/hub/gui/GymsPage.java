package net.snobblemon.hub.gui;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.snobblemon.hub.Fmt;
import net.snobblemon.hub.HubConfig;
import net.snobblemon.hub.HubFont;
import net.snobblemon.hub.Teleports;

import java.util.List;

/** The gym list from gyms.json: eight per page, a badge icon and name each. A gym can be locked by a permission node. */
final class GymsPage {
    private static final int[][] CELLS = {{0, 1}, {5, 1}, {0, 2}, {5, 2}, {0, 3}, {5, 3}, {0, 4}, {5, 4}};
    private int page;
    private int selected = -1;   // index into HubConfig.gyms

    void draw(HubGui g) {
        List<HubConfig.Gym> all = HubConfig.gyms;
        int pages = Math.max(1, (all.size() + 7) / 8);
        page = Math.max(0, Math.min(page, pages - 1));
        g.layer("gyms");
        for (int i = 0; i < 8; i++) {
            int idx = page * 8 + i;
            if (idx >= all.size()) break;
            HubConfig.Gym gym = all.get(idx);
            boolean locked = gym.permission != null && !gym.permission.isBlank() && !Permissions.check(g.p, gym.permission, false);
            boolean usable = gym.enabled && !locked && gym.command != null && !gym.command.isBlank();
            int cc = CELLS[i][0], r = CELLS[i][1];
            if (selected == idx) g.layer("gymsel_" + i);
            String icon = locked ? "badge_locked" : usable ? "badge" : "badge_off";
            g.btn(cc, r, icon, Fmt.line(gym.name + " Gym", locked ? 0xFF5555 : 0xFFFFFF),
                    List.of(Fmt.line(gym.type + " type", 0xAAAAAA),
                            Fmt.line(locked ? "Locked" : usable ? "Click to select" : "Not available", locked ? 0xFF5555 : usable ? 0xFFD166 : 0xAAAAAA)),
                    (rt, sh) -> {
                        g.click();
                        if (!usable) { g.say(locked ? "&cThat gym is still locked." : "&cThat gym is not available."); return; }
                        selected = idx;
                        g.redraw();
                    });
            int tx = cc == 0 ? 28 : 118, w = cc == 0 ? 64 : 52;
            g.text(tx, 18 + 18 * r + 2, HubFont.fit(gym.name, w), usable ? 0xFFFFFF : 0x9AA4D6);
            g.text(tx, 18 + 18 * r + 10, HubFont.fit(gym.type, w), locked ? 0xFF6A72 : 0x9AA4D6);
        }
        g.btn(0, 5, "back", Fmt.line("Back", 0xFFFFFF), List.of(Fmt.line("Warp menu", 0xAAAAAA)), (r, s) -> { g.click(); selected = -1; g.go(HubGui.Mode.WARPS); });
        if (pages > 1) {
            g.btn(5, 5, "prev", Fmt.line("Previous page", 0xFFFFFF), null, (r, s) -> { g.click(); page = (page + pages - 1) % pages; selected = -1; g.redraw(); });
            g.btn(6, 5, "next", Fmt.line("Next page", 0xFFFFFF), null, (r, s) -> { g.click(); page = (page + 1) % pages; selected = -1; g.redraw(); });
        }
        if (selected >= 0 && selected < all.size()) {
            HubConfig.Gym gym = all.get(selected);
            g.layer("confirm");
            g.text(28, 113, HubFont.fit("GO TO " + gym.name.toUpperCase() + "?", 90), 0xFFD166);
            g.btn(7, 5, "confirm", Fmt.line("Teleport", 0x55FF55), List.of(Fmt.line("Starts the warm-up.", 0xAAAAAA)), (r, s) -> {
                g.click();
                HubConfig.Gym gg = HubConfig.gyms.get(selected);
                selected = -1;
                g.close();
                Teleports.request(g.p, gg.name + " Gym", gg.command);
            });
            g.btn(8, 5, "cancel", Fmt.line("Cancel", 0xFF5555), null, (r, s) -> { g.click(); selected = -1; g.redraw(); });
        }
    }
}
