package net.snobblemon.hubclient.ui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Small builders shared by all screens, so every button, label and icon is made the same way. */
public final class Ui {
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    private Ui() {}

    /** An item the server described (as SNBT). Parsed once and cached; unknown or empty text gives an empty stack. */
    public static ItemStack stack(String snbt) {
        if (snbt == null || snbt.isEmpty()) return ItemStack.EMPTY;
        ItemStack cached = STACKS.get(snbt);
        if (cached != null) return cached;
        ItemStack out = ItemStack.EMPTY;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) out = ItemStack.parse(mc.level.registryAccess(), TagParser.parseTag(snbt)).orElse(ItemStack.EMPTY);
        } catch (Exception ignored) { }
        if (!out.isEmpty()) STACKS.put(snbt, out);
        return out;
    }

    public static LabelComponent label(String text, int argb) {
        return Components.label(Component.literal(text)).color(Color.ofArgb(argb)).shadow(true);
    }

    public static FlowLayout row(Sizing w, Sizing h) {
        return Containers.horizontalFlow(w, h);
    }

    public static FlowLayout column(Sizing w, Sizing h) {
        return Containers.verticalFlow(w, h);
    }

    /** Vertically centred row. */
    public static FlowLayout centeredRow(Sizing w, Sizing h) {
        FlowLayout f = Containers.horizontalFlow(w, h);
        f.verticalAlignment(VerticalAlignment.CENTER);
        return f;
    }

    public static FlowLayout rightAligned(Sizing w, Sizing h) {
        FlowLayout f = Containers.horizontalFlow(w, h);
        f.horizontalAlignment(HorizontalAlignment.RIGHT).verticalAlignment(VerticalAlignment.CENTER);
        return f;
    }

    /** A 16x16 icon from textures/gui/icons. */
    public static io.wispforest.owo.ui.core.Component icon(String name) {
        return Components.texture(Theme.icon(name), 0, 0, 16, 16, 16, 16).sizing(Sizing.fixed(16), Sizing.fixed(16));
    }

    /** A flat button with the Snobble look. kind: "" normal, "primary", "gold", "danger". */
    public static ButtonComponent button(String text, String kind, int w, Consumer<ButtonComponent> onPress) {
        String base = kind.isEmpty() ? "btn" : kind.equals("primary") ? "btn_primary_" + Theme.region() : "btn_" + kind;
        ButtonComponent b = Components.button(Component.literal(text), onPress);
        b.sizing(Sizing.fixed(w), Sizing.fixed(16));
        b.renderer((ctx, btn, delta) -> {
            String s = !btn.active ? "btn_off" : btn.isHovered() ? base + "_hover" : base;
            Theme.blit(ctx, Theme.sprite(s), btn.x(), btn.y(), btn.width(), btn.height());
            int color = btn.active ? 0xFFFFFFFF : 0xFFB4BBBE;
            var font = Minecraft.getInstance().font;
            int tw = font.width(btn.getMessage());
            ctx.drawString(font, btn.getMessage(), btn.x() + (btn.width() - tw) / 2, btn.y() + (btn.height() - 8) / 2, color, true);
        });
        return b;
    }

    /** Turns "&6Gold &lbold&r" colour text from the config files into a Component. Dark book colours are lightened to read on the Snobble windows. */
    public static Component legacy(String text) {
        net.minecraft.network.chat.MutableComponent out = Component.literal("");
        StringBuilder cur = new StringBuilder();
        net.minecraft.network.chat.Style style = net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFFF);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char k = Character.toLowerCase(text.charAt(i + 1));
                net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(k);
                if (f != null) {
                    if (cur.length() > 0) { out.append(Component.literal(cur.toString()).setStyle(style)); cur.setLength(0); }
                    if (f == net.minecraft.ChatFormatting.RESET) {
                        style = net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFFF);
                    } else if (f.isColor()) {
                        int rgb = switch (k) {
                            case '0' -> 0xFFFFFF;
                            case '1' -> 0x8FB0FF;
                            case '2' -> 0x6BE08A;
                            case '4' -> 0xFF7F7F;
                            case '5' -> 0xD7A0FF;
                            case '8' -> 0xD0D5DC;
                            default -> f.getColor() == null ? 0xFFFFFF : f.getColor();
                        };
                        style = net.minecraft.network.chat.Style.EMPTY.withColor(rgb);
                    } else {
                        style = style.applyFormat(f);
                    }
                    i++;
                    continue;
                }
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.append(Component.literal(cur.toString()).setStyle(style));
        return out;
    }

    public static Insets pad(int all) {
        return Insets.of(all);
    }
}
