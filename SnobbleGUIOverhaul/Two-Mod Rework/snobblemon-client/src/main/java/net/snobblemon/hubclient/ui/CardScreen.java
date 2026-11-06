package net.snobblemon.hubclient.ui;

import com.google.gson.JsonObject;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;

/**
 * Trainer Card: identity and balances on the left, the live player on the right. The only screen that lists balances.
 * Every value is read from the server's data; nothing is calculated here.
 */
public final class CardScreen extends SnobbleScreen {
    public CardScreen(JsonObject data) {
        super("card", data);
    }

    @Override
    protected boolean brightInterior() {
        return false;
    }

    @Override
    protected int[] windowSize(int sw, int sh) {
        return new int[]{clamp(sw * 0.70, 300, 360), clamp(sh * 0.90, 212, 236)};
    }

    private String s(String key) {
        return data.has(key) ? data.get(key).getAsString() : "-";
    }

    @Override
    protected void buildBody(FlowLayout body, int bw, int bh) {
        int rightW = Math.max(110, (int) (bw * 0.40));
        int leftW = bw - rightW - 3;

        FlowLayout left = Ui.column(Sizing.fixed(leftW), Sizing.fixed(bh));
        left.gap(2);

        String[][] rows = {
                {"region_" + accent, "Name", s("name"), ""},
                {"rank", "Rank", s("rank"), "green"},
                {"region_" + accent, "Region", s("region"), "region"},
                {"raid", "Raid Points", s("raid"), ""},
                {"coin", "CobbleDollars", s("dollars"), ""},
                {"gem", "SnobbleGems", s("gems"), "aqua"},
                {"community", "Community", s("community"), ""},
        };
        int rowH = Math.min(22, (bh - 2 * (rows.length - 1)) / rows.length);
        for (String[] r : rows) {
            FlowLayout row = Ui.centeredRow(Sizing.fixed(leftW), Sizing.fixed(rowH));
            row.surface((ctx, c) -> Theme.blit(ctx, Theme.sprite("bar"), c.x(), c.y(), c.width(), c.height()));
            row.padding(Insets.of(0, 0, 3, 5));
            row.gap(4);
            row.child(Ui.icon(r[0]));
            row.child(Ui.label(r[1], Theme.TEXT));
            FlowLayout valueBox = Ui.rightAligned(Sizing.expand(), Sizing.fill(100));
            int color = switch (r[3]) {
                case "green" -> Theme.GREEN;
                case "aqua" -> Theme.AQUA;
                case "region" -> Theme.regionColor(accent);
                default -> Theme.TEXT;
            };
            valueBox.child(Ui.label(r[2], color));
            row.child(valueBox);
            left.child(row);
        }
        body.child(left);

        FlowLayout right = Ui.column(Sizing.fixed(rightW), Sizing.fixed(bh));
        PlayerPreview preview = new PlayerPreview(true, true);
        preview.sizing(Sizing.fixed(rightW), Sizing.fixed(bh));
        right.child(preview);
        body.child(right);
    }
}
