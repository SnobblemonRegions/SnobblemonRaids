package com.snobblemon.gems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Text helpers: {variable} substitution, &-codes, &#RRGGBB colours and number formatting. */
public final class Txt {
    private Txt() {}

    private static final DecimalFormat INT = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));

    public static String num(long v) {
        synchronized (INT) { return INT.format(v); }
    }

    public static String apply(String template, Map<String, String> vars) {
        if (template == null) return "";
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** "1.2K", "3.4M" style for tight spaces. */
    public static String shortNum(long v) {
        if (v < 10_000) return String.valueOf(v);
        if (v < 1_000_000) return trim(v / 1000.0) + "K";
        if (v < 1_000_000_000) return trim(v / 1_000_000.0) + "M";
        return trim(v / 1_000_000_000.0) + "B";
    }

    private static String trim(double d) {
        String s = String.format(Locale.US, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)&#[0-9a-f]{6}", "").replaceAll("(?i)&[0-9a-fk-or]", "");
    }

    public static MutableComponent parse(String s) {
        MutableComponent root = Component.empty();
        if (s == null || s.isEmpty()) return root;
        StringBuilder buf = new StringBuilder();
        Style style = Style.EMPTY.withItalic(false);
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '&' && i + 1 < s.length()) {
                char c = Character.toLowerCase(s.charAt(i + 1));
                if (c == '#' && isHex(s, i + 2)) {
                    flush(root, buf, style);
                    style = style.withColor(TextColor.fromRgb(Integer.parseInt(s.substring(i + 2, i + 8), 16)));
                    i += 8;
                    continue;
                }
                ChatFormatting f = ChatFormatting.getByCode(c);
                if (f != null) {
                    flush(root, buf, style);
                    if (f == ChatFormatting.RESET) style = Style.EMPTY.withItalic(false);
                    else if (f.isColor()) style = Style.EMPTY.withItalic(false).withColor(f);
                    else style = style.applyFormat(f);
                    i += 2;
                    continue;
                }
            }
            buf.append(ch);
            i++;
        }
        flush(root, buf, style);
        return root;
    }

    private static boolean isHex(String s, int at) {
        if (s.length() < at + 6) return false;
        for (int k = 0; k < 6; k++) if (Character.digit(s.charAt(at + k), 16) < 0) return false;
        return true;
    }

    private static void flush(MutableComponent root, StringBuilder buf, Style style) {
        if (buf.length() == 0) return;
        root.append(Component.literal(buf.toString()).withStyle(style));
        buf.setLength(0);
    }

    /** Hex colour (#rrggbb) of the first colour code in a legacy-formatted string, or "" when there is none. */
    public static String firstColor(String s) {
        if (s == null) return "";
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '&') continue;
            char c = Character.toLowerCase(s.charAt(i + 1));
            if (c == '#' && i + 8 <= s.length() && isHex(s, i + 2)) return "#" + s.substring(i + 2, i + 8).toLowerCase();
            ChatFormatting f = ChatFormatting.getByCode(c);
            if (f != null && f.isColor() && f.getColor() != null) return String.format("#%06x", f.getColor());
        }
        return "";
    }

    public static List<Component> parseAll(List<String> lines, Map<String, String> vars) {
        List<Component> out = new ArrayList<>();
        if (lines == null) return out;
        for (String l : lines) out.add(parse(apply(l, vars)));
        return out;
    }
}
