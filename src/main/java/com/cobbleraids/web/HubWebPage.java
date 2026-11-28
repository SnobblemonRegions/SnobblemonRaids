package com.cobbleraids.web;

import com.cobbleraids.RaidLog;
import com.cobbleraids.catching.HallOfLegends;
import com.cobbleraids.catching.LegendGalleryQuery;
import com.cobbleraids.catching.LegendRoomGateway;
import com.cobbleraids.catching.TrophyGalleryQuery;
import com.cobbleraids.catching.TrophyLedger;
import com.cobbleraids.catching.TrophyRoomGateway;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.reward.points.RaidPointsStore;
import com.cobbleraids.shop.RaidShopGateway;
import com.cobbleraids.stats.PlayerStats;
import com.cobbleraids.stats.RaidStats;
import com.cobbleraids.stats.RaidStatsStore;
import com.cobbleraids.stats.StatScope;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/**
 * The raid hub: one page with a door to each of the others, so every raid screen is reachable without
 * remembering a command (/cobbleraids opens it).
 *
 * <p>The hub shows a figure or two from each room and sends nothing but "open this one"; opening goes
 * through the same gateway {@code /cobbleraids shop|trophy|legends|album} uses.
 */
public final class HubWebPage {
    private HubWebPage() {}

    public static final String CHANNEL = "cobbleraids.hub";
    public static final String PAGE = "hub/hub.html";

    static void register() {
        WebGuiBridge.onPageEvent(CHANNEL, (player, json) ->
                RaidFaultBarrier.guard("hub-web-event", () -> onEvent(player, json)));
    }

    public static boolean open(ServerPlayer player) {
        return WebUi.open(player, PAGE);
    }

    private static void onEvent(ServerPlayer player, String json) {
        JsonObject message = WebJson.object(json);
        if (message == null) return;
        switch (WebJson.string(message, "action")) {
            case "ready" -> WebGuiBridge.emit(player, "hub_state", state(player).toString());
            case "open" -> {
                switch (WebJson.string(message, "target")) {
                    case "shop" -> RaidShopGateway.open(player);
                    case "trophy" -> TrophyRoomGateway.open(player, TrophyGalleryQuery.MAX_COLUMNS);
                    case "legends" -> LegendRoomGateway.open(player, LegendGalleryQuery.MAX_COLUMNS);
                    case "leaders" -> LeaderboardWebPage.open(player);
                    case "album" -> AlbumWebPage.open(player);
                    default -> { }
                }
            }
            // The page is closing. Nothing is held per viewer, so there is nothing to release.
            case "bye" -> { }
            default -> RaidLog.warn("Ignoring unknown hub page action from {}", player.getGameProfile().getName());
        }
    }

    private static JsonObject state(ServerPlayer player) {
        JsonObject out = new JsonObject();
        out.addProperty("name", player.getGameProfile().getName());
        out.addProperty("balance", RaidPointsStore.balance(player.getUUID()));
        out.addProperty("trophies", TrophyLedger.forPlayer(player.getUUID()).size());
        out.addProperty("legends", HallOfLegends.all().size());
        PlayerStats stats = RaidStatsStore.book().players().get(player.getUUID());
        out.addProperty("raidsWon", stats == null ? 0 : stats.block(StatScope.ALL_TIME, RaidStats.window()).raidsWon());
        com.cobbleraids.album.RenownAlbum.Progress album = AlbumWebPage.progress(player);
        out.addProperty("albumFilled", album.pressed());
        out.addProperty("albumTotal", album.total());
        return out;
    }
}
