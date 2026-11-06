package net.snobblemon.hubclient.ui;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;

/** A picture card: a colour gradient with a "+" mark and a dark title strip, like the old wiki cards. Dimmed when not usable. */
public final class PictureCard extends BaseComponent {
    private final String title;
    private final int top, bottom;
    private final boolean usable, selected;
    private final String sub;

    public PictureCard(String title, String sub, int top, int bottom, boolean usable, boolean selected, int w, int h) {
        this.title = title;
        this.sub = sub;
        this.top = top;
        this.bottom = bottom;
        this.usable = usable;
        this.selected = selected;
        this.sizing(Sizing.fixed(w), Sizing.fixed(h));
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return width;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return height;
    }

    @Override
    public void draw(OwoUIDrawContext ctx, int mx, int my, float pt, float delta) {
        var font = Minecraft.getInstance().font;
        ctx.fill(x, y, x + width, y + height, 0xFF000000);
        ctx.fillGradient(x + 1, y + 1, x + width - 1, y + height - 1, top, bottom);
        int cx = x + width / 2, cy = y + (height - 14) / 2;
        ctx.fill(cx - 1, cy - 6, cx + 1, cy + 6, 0x99FFFFFF);        // plus mark
        ctx.fill(cx - 6, cy - 1, cx + 6, cy + 1, 0x99FFFFFF);
        boolean hasSub = sub != null && !sub.isEmpty();
        int strip = hasSub ? 22 : 13;
        ctx.fill(x + 1, y + height - 1 - strip, x + width - 1, y + height - 1, 0xCC0A0C10);
        String t = font.plainSubstrByWidth(title, width - 6);
        ctx.drawString(font, t, x + (width - font.width(t)) / 2, y + height - strip + (hasSub ? 3 : 2), 0xFFFFFFFF, true);
        if (hasSub) {
            String s = font.plainSubstrByWidth(sub, width - 6);
            ctx.drawString(font, s, x + (width - font.width(s)) / 2, y + height - strip + 13, 0xFFB8C0CC, false);
        }
        if (!usable) ctx.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xAA1A1C20);
        if (usable && isInBoundingBox(mx, my)) ctx.fill(x + 1, y + 1, x + width - 1, y + height - 1 - strip, 0x33FFFFFF);
        if (selected) {
            ctx.fill(x, y, x + width, y + 2, 0xFFFFFFFF);
            ctx.fill(x, y + height - 2, x + width, y + height, 0xFFFFFFFF);
            ctx.fill(x, y, x + 2, y + height, 0xFFFFFFFF);
            ctx.fill(x + width - 2, y, x + width, y + height, 0xFFFFFFFF);
        }
    }
}
