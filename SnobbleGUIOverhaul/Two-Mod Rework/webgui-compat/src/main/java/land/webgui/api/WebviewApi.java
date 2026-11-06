package land.webgui.api;

import net.minecraft.server.level.ServerPlayer;
import net.snobblemon.compat.UrlMap;
import net.snobblemon.compat.WebguiCompat;
import net.snobblemon.server.SnobbleUi;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Same public surface as WebGUI's server API (only what other mods call). Every call is answered by Snobblemon's native pages. */
public final class WebviewApi {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private WebviewApi() {}

    public static void openGui(ServerPlayer player, String url) {
        String id = UrlMap.pageId(url);
        if (!SnobbleUi.open(player, id) && WARNED.add("open:" + id)) {
            WebguiCompat.LOGGER.warn("No native page '{}' for {} (or the player has no Snobblemon Client); nothing was shown.", id, url);
        }
    }

    /** A HUD page is drawn over the world (no mouse) next to any full page. */
    public static void openHud(ServerPlayer player, String url) {
        String id = UrlMap.pageId(url);
        if (!SnobbleUi.isHudLayout(id)) { openGui(player, url); return; }       // the Shellpack asks for an overlay but its native screen takes the mouse at once
        if (!SnobbleUi.openHud(player, id) && WARNED.add("hud:" + id)) {
            WebguiCompat.LOGGER.warn("No native HUD page '{}' for {} (or the player has no Snobblemon Client); nothing was shown.", id, url);
        }
    }

    public static void sendMainMenuUrl(ServerPlayer player, String url) {
        // the main menu is a client feature of WebGUI; native menus come from Snobblemon Server itself
    }

    /** The event is delivered to whichever native page the player has open. */
    public static void emitToPage(ServerPlayer player, String event, String json) {
        String hud = SnobbleUi.hudPage(player);
        String page = event.startsWith("hud") && hud != null ? hud : SnobbleUi.openPage(player);
        if (page == null) return;
        SnobbleUi.emitJson(player, page, event, json);
    }

    public static void onPageEvent(String channel, BiConsumer<ServerPlayer, String> handler) {
        SnobbleUi.onMessage(channel, handler);
    }

    public static int maxUrlLength() { return 2048; }
    public static int protocolVersion() { return 2; }
}
