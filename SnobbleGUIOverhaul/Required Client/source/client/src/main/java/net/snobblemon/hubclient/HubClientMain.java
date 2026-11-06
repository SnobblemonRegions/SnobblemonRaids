package net.snobblemon.hubclient;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.snobblemon.hubnet.HubNet;

public final class HubClientMain implements ModInitializer {
    /** Test rig only: -Dsnobblehub.test.select=<cosmetic id> opens the wardrobe on that item; -Dsnobblehub.test.notoasts=1 clears toasts. */
    public static final String TEST_SELECT = System.getProperty("snobblehub.test.select");
    private static final boolean TEST_NO_TOASTS = System.getProperty("snobblehub.test.notoasts") != null;

    @Override
    public void onInitialize() {
        HubNet.registerTypes();
        if (TEST_NO_TOASTS) {
            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (mc.getToasts() != null) mc.getToasts().clear();
            });
        }
    }
}
