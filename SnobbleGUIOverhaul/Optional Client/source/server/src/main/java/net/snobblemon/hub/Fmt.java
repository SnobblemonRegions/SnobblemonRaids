package net.snobblemon.hub;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Small text helpers: legacy &amp; colour codes, and item-name styles without the default italics. */
public final class Fmt {
    private Fmt() {}

    public static MutableComponent parse(String in) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY.withItalic(false);
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '&' && i + 1 < in.length()) {
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(in.charAt(i + 1)));
                if (f != null) {
                    if (buf.length() > 0) { out.append(Component.literal(buf.toString()).withStyle(style)); buf.setLength(0); }
                    style = f == ChatFormatting.RESET ? Style.EMPTY.withItalic(false) : style.applyFormat(f);
                    i++;
                    continue;
                }
            }
            buf.append(c);
        }
        if (buf.length() > 0) out.append(Component.literal(buf.toString()).withStyle(style));
        return out;
    }

    public static MutableComponent line(String text, int rgb) {
        return Component.literal(text).withStyle(Style.EMPTY.withItalic(false).withColor(rgb));
    }

    public static MutableComponent line(Component text) {
        return text.copy().withStyle(s -> s.withItalic(false));
    }
}
