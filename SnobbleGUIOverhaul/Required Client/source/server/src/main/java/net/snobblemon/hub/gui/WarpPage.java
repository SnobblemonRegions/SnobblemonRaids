package net.snobblemon.hub.gui;

import net.snobblemon.hub.Fmt;
import net.snobblemon.hub.HubConfig;
import net.snobblemon.hub.Teleports;

import java.util.List;

/**
 * Five destination cards (positions are fixed by the artwork, behaviour by warps.json). Choosing a card shows a
 * confirm bar; the server then applies cooldown, warm-up and the warp command's own checks (see {@link Teleports}).
 */
final class WarpPage {
    /** id -> artwork index and the card's top-left slot. */
    private static final String[] IDS = {"resource", "end", "nether", "wild", "gyms"};
    private static final int[][] POS = {{0, 1}, {3, 1}, {6, 1}, {0, 3}, {3, 3}};

    private int selected = -1;

    void draw(HubGui g) {
        g.layer("warp");
        for (int i = 0; i < IDS.length; i++) {
            HubConfig.Destination d = find(IDS[i]);
            boolean isGyms = IDS[i].equals("gyms");
            boolean usable = d != null && d.enabled && (isGyms || (d.command != null && !d.command.isBlank()));
            if (!usable) g.layer("warpdim_" + i);
            if (selected == i) g.layer("warpsel_" + i);
            final int idx = i;
            for (int dx = 0; dx < 3; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    g.area(POS[i][0] + dx, POS[i][1] + dy, Fmt.line(d == null ? IDS[i] : d.label, usable ? 0xFFFFFF : 0xAAAAAA),
                            List.of(Fmt.line(d == null ? "" : d.description, 0xAAAAAA),
                                    usable ? Fmt.line(isGyms ? "Click to see the gyms" : "Click to select", 0xFFD166) : Fmt.line("Not set up yet", 0xFF5555)),
                            (r, s) -> {
                                g.click();
                                if (!usable) { g.say("&cThat destination is not set up yet. Ask a staff member."); return; }
                                if (isGyms) { g.go(HubGui.Mode.GYMS); return; }
                                selected = idx;
                                g.redraw();
                            });
                }
            }
        }
        if (selected >= 0) {
            HubConfig.Destination d = find(IDS[selected]);
            g.layer("confirm");
            g.text(28, 113, "TELEPORT TO " + (d == null ? "" : d.label) + "?", 0xFFD166);
            g.btn(7, 5, "confirm", Fmt.line("Teleport", 0x55FF55), List.of(Fmt.line("Starts the warm-up.", 0xAAAAAA)), (r, s) -> {
                g.click();
                HubConfig.Destination dd = find(IDS[selected]);
                selected = -1;
                g.close();
                if (dd != null) Teleports.request(g.p, dd.label, dd.command);
            });
            g.btn(8, 5, "cancel", Fmt.line("Cancel", 0xFF5555), null, (r, s) -> { g.click(); selected = -1; g.redraw(); });
        }
    }

    private static HubConfig.Destination find(String id) {
        for (HubConfig.Destination d : HubConfig.warps) if (id.equals(d.id)) return d;
        return null;
    }
}
