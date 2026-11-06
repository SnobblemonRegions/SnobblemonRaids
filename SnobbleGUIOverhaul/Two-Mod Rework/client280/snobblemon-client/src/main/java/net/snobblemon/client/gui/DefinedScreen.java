package net.snobblemon.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.snobblemon.client.theme.ThemeRegistry;
import net.snobblemon.proto.GuiSpec;
import net.snobblemon.proto.SnobbleProto;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a server-defined GUI (a validated GuiSpec) from the server's art. Pure rendering and input: a click sends "widget X of GUI Y
 * was pressed" and nothing else; the server decides what that means and answers with new data. The layout is scaled down to fit the
 * window if needed, so every GUI scale and resolution works.
 */
public final class DefinedScreen extends Screen {
    private final GuiSpec spec;
    private final String error;
    private JsonObject data;
    private String message = "";
    private boolean messageOk;
    private final long opened = Util.getMillis();
    private long lastClick;
    private float scale = 1f;
    private int ox, oy;

    public DefinedScreen(GuiSpec spec, String dataJson) {
        super(Component.literal(spec.title));
        this.spec = spec;
        this.error = null;
        this.data = parseData(dataJson);
    }

    private DefinedScreen(String id, String error) {
        super(Component.literal("Menu unavailable"));
        this.spec = null;
        this.error = error;
        this.data = new JsonObject();
        this.errorId = id;
    }

    private String errorId = "";

    /** Shown instead of a definition that failed validation: never a crash, never a blank screen. */
    public static DefinedScreen error(String guiId, String why) {
        return new DefinedScreen(guiId, why);
    }

    public String guiId() { return spec != null ? spec.id : errorId; }
    public GuiSpec spec() { return spec; }
    public JsonObject data() { return data; }
    public String message() { return message; }

    private static JsonObject parseData(String json) {
        try {
            JsonElement e = JsonParser.parseString(json == null ? "{}" : json);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    public void update(String dataJson, String msg, boolean ok) {
        if (dataJson != null && !dataJson.isEmpty()) this.data = parseData(dataJson);
        this.message = msg == null ? "" : msg;
        this.messageOk = ok;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        if (spec == null) return;
        scale = Math.min(1f, Math.min((width - 8f) / spec.width, (height - 8f) / spec.height));
        if (scale <= 0.1f) scale = 0.1f;
        ox = Math.round((width / scale - spec.width) / 2f);
        oy = Math.round((height / scale - spec.height) / 2f);
    }

    @Override
    public void removed() {
        // tell the server this GUI is no longer open, so it stops accepting presses for it
        if (spec != null && net.minecraft.client.Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(SnobbleProto.GuiAction.TYPE))
            ClientPlayNetworking.send(new SnobbleProto.GuiAction(spec.id, "__close", ""));
    }

    // ------------------------------------------------------------------ drawing

    private String fill(String text) {
        if (text.indexOf('{') < 0) return text;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int a = text.indexOf('{', i);
            int b = a < 0 ? -1 : text.indexOf('}', a);
            if (a < 0 || b < 0) { out.append(text, i, text.length()); break; }
            out.append(text, i, a);
            String key = text.substring(a + 1, b);
            JsonElement v = key.matches("[a-z0-9_]{1,48}") ? data.get(key) : null;
            out.append(v != null && v.isJsonPrimitive() ? v.getAsString() : "");
            i = b + 1;
        }
        return out.toString();
    }

    private boolean enabled(GuiSpec.Widget w) {
        if (w.enabledIf.isEmpty()) return true;
        JsonElement v = data.get(w.enabledIf);
        return v != null && v.isJsonPrimitive() && (v.getAsJsonPrimitive().isBoolean() ? v.getAsBoolean() : !v.getAsString().isEmpty() && !v.getAsString().equals("0"));
    }

    private static int color(String hex) { return 0xFF000000 | Integer.parseInt(hex.substring(1), 16); }

    private boolean inside(GuiSpec.Widget w, double vx, double vy) { return vx >= w.x && vx < w.x + w.w && vy >= w.y && vy < w.y + w.h; }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(ctx);
        if (spec == null) {
            ctx.drawCenteredString(font, Component.literal("This menu could not be displayed."), width / 2, height / 2 - 10, 0xFFFF9AA0);
            ctx.drawCenteredString(font, Component.literal("(" + error + ")"), width / 2, height / 2 + 4, 0xFFB0B0B0);
            return;
        }
        float fade = spec.fadeMs <= 0 ? 1f : Math.min(1f, (Util.getMillis() - opened) / (float) spec.fadeMs);
        double vx = mouseX / (double) scale - ox, vy = mouseY / (double) scale - oy;
        var pose = ctx.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1f);
        pose.translate(ox, oy, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, fade);
        if (!spec.background.isEmpty()) ctx.blitSprite(ThemeRegistry.sprite(spec.background), 0, 0, spec.width, spec.height);
        GuiSpec.Widget hover = null;
        for (GuiSpec.Widget w : spec.widgets) {
            switch (w.type) {
                case "panel" -> { if (!w.sprite.isEmpty()) ctx.blitSprite(ThemeRegistry.sprite(w.sprite), w.x, w.y, w.w, w.h); }
                case "image" -> {
                    if (!w.sprite.isEmpty()) ctx.blitSprite(ThemeRegistry.sprite(w.sprite), w.x, w.y, w.w, w.h);
                    else if (w.texture.startsWith("textures/")) {
                        ResourceLocation t = ThemeRegistry.file(w.texture);
                        int[] sz = ThemeRegistry.size(t);
                        ctx.blit(t, w.x, w.y, w.w, w.h, 0f, 0f, sz[0], sz[1], sz[0], sz[1]);
                    }
                }
                case "button" -> {
                    boolean on = enabled(w) && !w.action.isEmpty();
                    boolean hot = on && inside(w, vx, vy);
                    String sp = !on && !w.disabledSprite.isEmpty() ? w.disabledSprite : hot && !w.hoverSprite.isEmpty() ? w.hoverSprite : w.sprite;
                    if (!sp.isEmpty()) ctx.blitSprite(ThemeRegistry.sprite(sp), w.x, w.y, w.w, w.h);
                    if (!w.text.isEmpty()) {
                        Component c = styled(fill(w.text), w);
                        ctx.drawString(font, c, w.x + (w.w - font.width(c)) / 2, w.y + (w.h - 8) / 2, on ? color(w.color) : 0xFF909090, w.shadow);
                    }
                    if (inside(w, vx, vy)) hover = w;
                }
                case "label" -> {
                    Component c = styled(fill(w.text), w);
                    int tw = font.width(c);
                    int x = switch (w.align) { case "center" -> w.x - tw / 2; case "right" -> w.x - tw; default -> w.x; };
                    ctx.drawString(font, c, x, w.y, color(w.color), w.shadow);
                }
                case "item" -> {
                    var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(w.item));
                    if (item.isPresent()) {
                        ItemStack st = new ItemStack(item.get(), w.count);
                        float s = w.w > 0 ? w.w / 16f : 1f;
                        pose.pushPose();
                        pose.translate(w.x, w.y, 0);
                        pose.scale(s, s, 1f);
                        ctx.renderItem(st, 0, 0);
                        ctx.renderItemDecorations(font, st, 0, 0);
                        pose.popPose();
                    }
                    if (inside(w, vx, vy) && !w.tooltip.isEmpty()) hover = w;
                }
                default -> { }
            }
            if (!w.type.equals("button") && !w.type.equals("item") && !w.tooltip.isEmpty() && inside(w, vx, vy)) hover = w;
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (!message.isEmpty()) ctx.drawCenteredString(font, Component.literal(message), spec.width / 2, spec.height - 12, messageOk ? 0xFF5FD38A : 0xFFFF9AA0);
        pose.popPose();
        if (hover != null && !hover.tooltip.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : hover.tooltip) lines.add(Component.literal(fill(l)));
            ctx.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private Component styled(String s, GuiSpec.Widget w) {
        var c = Component.literal(s);
        if (!w.font.isEmpty()) {
            String ns = ThemeRegistry.namespace().isEmpty() ? ThemeRegistry.DEFAULT_NS : ThemeRegistry.namespace();
            c = c.withStyle(Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath(ns, w.font)));
        }
        return c;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (spec == null || button != 0) return super.mouseClicked(mx, my, button);
        double vx = mx / scale - ox, vy = my / scale - oy;
        for (int i = spec.widgets.size() - 1; i >= 0; i--) {
            GuiSpec.Widget w = spec.widgets.get(i);
            if (!w.type.equals("button") || w.action.isEmpty() || !inside(w, vx, vy)) continue;
            if (!enabled(w)) return true;
            long now = Util.getMillis();
            if (now - lastClick < 150) return true;          // the server rate-limits too; this just avoids sending double clicks
            lastClick = now;
            if (ClientPlayNetworking.canSend(SnobbleProto.GuiAction.TYPE)) ClientPlayNetworking.send(new SnobbleProto.GuiAction(spec.id, w.id, ""));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
