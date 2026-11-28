package com.cobbleraids.worldboss;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * The text over the World Boss bar: a gradient boss name, the pool in short form, a percent badge that goes from green to red as the boss weakens,
 * how many players are fighting, and how long it will stay. The pieces that are plain arithmetic are separate methods so they can be tested.
 */
public final class WorldBossBarText {
    static final int NAME_FROM = 0xB57BFF;
    static final int NAME_TO = 0x7BE0A8;
    static final int HEALTHY = 0x7BE0A8;
    static final int HURT = 0xF5D142;
    static final int CRITICAL = 0xFF5A5A;
    private static final int SEPARATOR = 0x6E6580;

    /**
     * The bar's colours. EVENT is the original World Boss look (violet into spectral green, made for the Halloween event); RENOWN is the
     * Daily Boss's burnished gold, to sit on its gold bar.
     */
    public record Palette(int nameFrom, int nameTo, int fighting, int time, int separator) {
        public static final Palette EVENT = new Palette(NAME_FROM, NAME_TO, 0xD9B3FF, 0xB8B0C8, SEPARATOR);
        public static final Palette RENOWN = new Palette(0xFFE7A8, 0xD9963F, 0xF2D29B, 0xC9B79A, 0x7D6A50);
    }

    private WorldBossBarText() {}

    /** 950 is "950", 4317 is "4.3k", 5000 is "5k", 12000 is "12k", 1250000 is "1.3M". One decimal at most, none when it would be .0. */
    static String shortNumber(double value) {
        double abs = Math.abs(value);
        if (abs < 1000) return Long.toString(Math.round(value));
        double scaled = abs >= 1_000_000 ? value / 1_000_000 : value / 1000;
        String suffix = abs >= 1_000_000 ? "M" : "k";
        String text = scaled >= 100 ? Long.toString(Math.round(scaled)) : String.format(Locale.ROOT, "%.1f", scaled);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + suffix;
    }

    /** Green while the boss is healthy, yellow below 60 percent, red below 30. */
    static int percentColor(int percent) {
        if (percent >= 60) return HEALTHY;
        return percent >= 30 ? HURT : CRITICAL;
    }

    /** "1h 57m", "42m", or "under 1m". */
    static String timeLeft(long minutes) {
        if (minutes < 1) return "under 1m";
        if (minutes < 60) return minutes + "m";
        long rest = minutes % 60;
        return minutes / 60 + "h" + (rest == 0 ? "" : " " + rest + "m");
    }

    /** One RGB colour per character, blending {@code from} into {@code to}; the first is exactly {@code from} and the last exactly {@code to}. */
    static int[] gradient(int length, int from, int to) {
        int[] out = new int[Math.max(length, 0)];
        for (int i = 0; i < out.length; i++) {
            double t = out.length == 1 ? 0.0 : (double) i / (out.length - 1);
            int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
            int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
            int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
            out[i] = (r << 16) | (g << 8) | b;
        }
        return out;
    }

    /**
     * @param minutesLeft minutes until the boss leaves, or a negative number when it stays until it is beaten (the time is then left out)
     */
    public static Component build(String species, double current, double max, int percent, int fighting, long minutesLeft) {
        return build(species, current, max, percent, fighting, minutesLeft, Palette.EVENT);
    }

    public static Component build(String species, double current, double max, int percent, int fighting, long minutesLeft, Palette palette) {
        MutableComponent text = name(species, palette);
        text.append(separator(palette)).append(Component.literal(shortNumber(current) + "/" + shortNumber(max)).withColor(0xFFFFFF));
        text.append(separator(palette)).append(Component.literal(percent + "%").withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(percentColor(percent)))));
        text.append(separator(palette)).append(Component.literal(fighting + " fighting").withColor(palette.fighting()));
        if (minutesLeft >= 0) text.append(separator(palette)).append(Component.literal(timeLeft(minutesLeft)).withColor(palette.time()));
        return text;
    }

    /** The bar while players gather: the name, the countdown, and how many are in. */
    public static Component gathering(String species, int secondsLeft, int ready, Palette palette) {
        MutableComponent text = name(species, palette);
        text.append(separator(palette)).append(Component.literal("the fight opens in " + secondsLeft / 60 + ":"
                + String.format(Locale.ROOT, "%02d", secondsLeft % 60)).withColor(0xFFFFFF));
        text.append(separator(palette)).append(Component.literal(ready + " ready").withColor(palette.fighting()));
        return text;
    }

    private static MutableComponent name(String species, Palette palette) {
        MutableComponent text = Component.empty();
        int[] colors = gradient(species.length(), palette.nameFrom(), palette.nameTo());
        for (int i = 0; i < species.length(); i++) {
            text.append(Component.literal(String.valueOf(species.charAt(i))).withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(colors[i]))));
        }
        return text;
    }

    private static Component separator(Palette palette) {
        return Component.literal("  ·  ").withColor(palette.separator());
    }
}
