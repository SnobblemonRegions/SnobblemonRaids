package net.snobblemon.hubclient.ui;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of coloured text in a fixed-width box. Text that fits is drawn still and centred; text that is too long
 * scrolls sideways by itself (pause, slide to the end, pause, back to the start) so the whole line can be read.
 */
public final class MarqueeText extends BaseComponent {
    private record Seg(String text, int color) {}

    private final List<Seg> segs = new ArrayList<>();
    private final boolean centre;
    private final long phase = System.currentTimeMillis();

    public MarqueeText(int w, int h, boolean centre) {
        this.centre = centre;
        this.sizing(Sizing.fixed(w), Sizing.fixed(h));
    }

    public MarqueeText add(String text, int color) {
        segs.add(new Seg(text, color));
        return this;
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
    public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
        var font = Minecraft.getInstance().font;
        int total = 0;
        for (Seg s : segs) total += font.width(s.text);
        int ty = y + (height - 8) / 2;
        int offset;
        if (total <= width) {
            offset = centre ? (width - total) / 2 : 0;
        } else {
            int overflow = total - width + 6;
            double speed = 28.0;                                       // GUI pixels per second
            double slide = overflow / speed;
            double cycle = slide * 2 + 3.0;                            // 1.5 s still at each end
            double t = ((System.currentTimeMillis() - phase) / 1000.0) % cycle;
            double pos;
            if (t < 1.5) pos = 0;
            else if (t < 1.5 + slide) pos = (t - 1.5) / slide;
            else if (t < 1.5 + slide + 1.5) pos = 1;
            else pos = 1 - (t - 3.0 - slide) / slide;
            offset = -(int) Math.round(pos * overflow);
        }
        ctx.enableScissor(x, y, x + width, y + height);
        int cx = x + offset;
        for (Seg s : segs) {
            ctx.drawString(font, s.text, cx, ty, s.color, true);
            cx += font.width(s.text);
        }
        ctx.disableScissor();
    }
}
