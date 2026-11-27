package com.cobbleraids.web;

import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server's side of the raid pages: opens them for players whose client can show them.
 *
 * <p>Each page is a native Snobblemon GUI page ({@code cobbleraids_<page>}, see {@link WebGuiBridge}), drawn by
 * the Snobblemon Client. A page is still named by its old address, {@code webgui://cobbleraids/<page>}, from
 * which the native page id is derived; without Snobblemon GUI there are no raid pages.
 */
public final class WebUi {
    private WebUi() {}

    private static final String PAGE_FOLDER = "cobbleraids";

    /** True once Snobblemon GUI is installed and every call this mod makes into it has been resolved. */
    public static boolean available() {
        return WebGuiBridge.bound();
    }

    /** The address of one of this mod's pages. */
    public static String pageUrl(String page) {
        return "webgui://" + PAGE_FOLDER + "/" + page;
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded(WebGuiBridge.SNOBBLE_ID)) {
            RaidLog.info("Snobblemon GUI is not installed; the raid pages are disabled.");
            return;
        }
        if (!WebGuiBridge.bind()) return;
        RaidLog.info("Raid pages are shown by Snobblemon GUI (native pages; players need the Snobblemon Client).");
        ShopWebPage.register();
        AlbumWebPage.register();
        TrophyWebPage.register();
        LegendWebPage.register();
        RewardWebPage.register();
        CaptureWebPage.register();
        LeaderboardWebPage.register();
        HubWebPage.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                RaidFaultBarrier.guard("player-disconnect:web-icons",
                        () -> {
                            WebGuiBridge.forget(handler.getPlayer().getUUID());
                            RewardWebPage.forget(handler.getPlayer().getUUID());
                            CaptureWebPage.forget(handler.getPlayer().getUUID());
                            LeaderboardWebPage.forget(handler.getPlayer().getUUID());
                        }));
    }

    /** Whether this player can be shown a page at all. */
    public static boolean canShow(ServerPlayer player) {
        return available() && WebGuiBridge.clientReady(player);
    }

    /** Opens a page full-screen. Returns false, sending nothing, if the client cannot show it. */
    public static boolean open(ServerPlayer player, String page) {
        if (!canShow(player)) return false;
        WebGuiBridge.openGui(player, pageUrl(page));
        return true;
    }
}
