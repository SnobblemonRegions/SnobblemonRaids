package net.snobblemon.hubclient;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.snobblemon.hubclient.ui.Theme;
import net.snobblemon.hubnet.HubNet;

/**
 * A small "Hub" tab on the right edge of the regular inventory. It only adds this one button: nothing in the inventory
 * itself is touched. Clicking it asks the server for the Cosmetics screen (the hub's home page). It is hidden when the
 * server does not have the hub mod, so the button never does nothing.
 */
public final class InventoryTab {
    private static final int W = 26, H = 26;

    private InventoryTab() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            final boolean creative = screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
            if (!(screen instanceof InventoryScreen) && !creative) return;
            Button tab = new Button(0, 0, W, H, Component.literal("Hub"), b -> HubClient.request("page", "cos"), s -> Component.literal("Hub")) {
                @Override
                protected void renderWidget(GuiGraphics ctx, int mx, int my, float delta) {
                    Theme.blit(ctx, Theme.sprite(isHoveredOrFocused() ? "btn_primary_" + Theme.region() + "_hover" : "btn_primary_" + Theme.region()), getX(), getY(), W, H);
                    Theme.tex(ctx, Theme.icon("tab_cos"), getX() + 5, getY() + 5, 16, 16, 0f, 0f, 16, 16, 16, 16);
                }
            };
            tab.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Snobble Hub")));
            Screens.getButtons(screen).add(tab);
            // follow the inventory when the recipe book slides it sideways; hide it when the server has no hub
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, delta) -> {
                if (creative) {
                    tab.setPosition((s.width - 195) / 2 + 195 + 1, (s.height - 136) / 2 + 40);
                } else {
                    int left = ((InventoryScreen) s).getRecipeBookComponent().updateScreenPosition(s.width, 176);
                    tab.setPosition(left + 176 - 1, (s.height - 166) / 2 + 8);
                }
                tab.visible = ClientPlayNetworking.canSend(HubNet.Request.TYPE);
                tab.active = tab.visible;
            });
        });
    }
}
