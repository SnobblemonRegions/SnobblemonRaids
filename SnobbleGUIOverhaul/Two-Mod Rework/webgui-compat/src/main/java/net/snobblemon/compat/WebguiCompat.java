package net.snobblemon.compat;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebguiCompat implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("webgui-compat");

    @Override
    public void onInitialize() {
        LOGGER.info("WebGUI compatibility layer active: pages of mods that call WebviewApi are shown as native Snobblemon pages.");
    }
}
