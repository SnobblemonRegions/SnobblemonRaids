package com.cobbleraids.web;

import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.network.ShopActionPayload;
import com.cobbleraids.network.ShopEntryPayload;
import com.cobbleraids.network.ShopPagePayload;
import com.cobbleraids.shop.RaidShopGateway;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/**
 * The raid shop as a web page: the same server logic as the native screen, a different view.
 *
 * <p>Nothing here decides anything. A click arrives as {@code {action, page, id}} and is handed to
 * {@link RaidShopGateway#handle}, which validates it exactly as it does a native packet; the page it
 * sends back is turned into JSON here instead of a payload.
 *
 * <p>(icons: Snobblemon GUI draws them from shop_icons.json and the item art; see {@code WebIcons}).
 */
public final class ShopWebPage {
    private ShopWebPage() {}

    public static final String CHANNEL = "cobbleraids.shop";
    public static final String PAGE = "shop/shop.html";


    static void register() {
        WebGuiBridge.onPageEvent(CHANNEL, (player, json) ->
                RaidFaultBarrier.guard("shop-web-event", () -> onEvent(player, json)));
    }

    /** Opens the page; it asks for its first page itself once it is listening. */
    public static boolean open(ServerPlayer player) {
        com.cobbleraids.shop.LegendaryVoucherService.leave(player);   // opening the shop is leaving the voucher chooser
        return WebUi.open(player, PAGE);
    }

    private static void onEvent(ServerPlayer player, String json) {
        JsonObject message = WebJson.object(json);
        if (message == null) return;
        int page = WebJson.integer(message, "page");
        switch (WebJson.string(message, "action")) {
            case "ready" -> {
                RaidShopGateway.handle(player, ShopActionPayload.turnTo(0));
            }
            case "turn" -> RaidShopGateway.handle(player, ShopActionPayload.turnTo(page));
            case "keep" -> RaidShopGateway.handleKeep(player, page, WebJson.bounded(message, "replace", ShopActionPayload.MAX_ID_LENGTH), false);
            case "decline" -> RaidShopGateway.handleKeep(player, page, "", true);
            case "buy" -> {
                String id = WebJson.string(message, "id");
                if (!id.isEmpty() && id.length() <= ShopActionPayload.MAX_ID_LENGTH) {
                    RaidShopGateway.handle(player, ShopActionPayload.buy(page, id));
                }
            }
            default -> RaidLog.warn("Ignoring unknown shop page action from {}", player.getGameProfile().getName());
        }
    }

    private static com.cobbleraids.shop.ShopCurrency currencyOf(ShopEntryPayload entry) {
        com.cobbleraids.shop.ShopEntry listed = com.cobbleraids.shop.ShopCatalogManager.index().get(entry.id());
        return listed == null ? com.cobbleraids.shop.ShopCurrency.RAID_POINTS : listed.currency();
    }

    /** Sends a page, preceded by whichever of its icons this open of the page has not been given yet. */
    public static void deliver(ServerPlayer player, ShopPagePayload payload) {
        JsonObject page = new JsonObject();
        page.addProperty("heading", payload.heading());
        page.addProperty("pageIndex", payload.pageIndex());
        page.addProperty("pageCount", payload.pageCount());
        // A page is one currency: the chooser's (vouchers), the Renown Vendor's (coins in the inventory), or Raid Points.
        String currency = com.cobbleraids.shop.LegendaryVoucherService.choosing(player) ? "voucher"
                : !payload.entries().isEmpty() && payload.entries().stream().allMatch(e -> currencyOf(e) == com.cobbleraids.shop.ShopCurrency.RENOWN_COINS)
                ? "renown_coins" : "raid_points";
        page.addProperty("currency", currency);
        page.addProperty("balance", currency.equals("renown_coins") ? com.cobbleraids.item.RenownCoinWallet.count(player) : payload.balance());
        page.addProperty("rotatesInMillis", payload.rotatesInMillis());
        JsonArray entries = new JsonArray();
        String awaiting = RaidShopGateway.awaitingConfirmation(player);
        // Giving up a saved boss is confirmed on that boss's own Replace button.
        String awaitingReplace = awaiting != null && awaiting.startsWith("replace:") ? awaiting.substring("replace:".length()) : null;
        boolean replacing = payload.personal() != null && payload.personal().offer() != null && payload.personal().offer().full();
        // A reroll is confirmed on the cell it belongs to, which carries the personal: id.
        boolean awaitingReroll = awaiting != null && awaiting.startsWith("reroll:");
        String awaitingCell = awaitingReroll ? "personal:" + awaiting.substring("reroll:".length()) : awaiting;
        for (ShopEntryPayload entry : payload.entries()) {
            JsonObject cell = new JsonObject();
            cell.addProperty("id", entry.id());
            boolean awaited = entry.id().equals(awaitingCell);
            cell.addProperty("confirm", awaited && !awaitingReroll);
            cell.addProperty("confirmReroll", awaited && awaitingReroll);
            // A saved boss leaves after its days are up; -1 when the server keeps them for ever.
            if (entry.personal()) {
                cell.addProperty("leavesInMillis", com.cobbleraids.catching.BossShopOffers.millisLeft(player, entry.id()));
                String species = entry.id().substring(entry.id().indexOf(':') + 1);
                cell.addProperty("canReplace", replacing);
                cell.addProperty("replaceId", species);
                cell.addProperty("confirmReplace", replacing && species.equals(awaitingReplace));
            }
            cell.addProperty("cost", entry.cost());
            cell.addProperty("pokemon", entry.pokemon());
            cell.addProperty("count", entry.count());
            cell.addProperty("level", entry.level());
            cell.addProperty("shiny", entry.shiny());
            cell.addProperty("remaining", entry.limit() > 0 ? Math.max(0, entry.remaining()) : 0);
            cell.addProperty("limit", entry.limit());
            cell.addProperty("soldOut", entry.soldOut());
            // when a sold-out listing comes back, in words, for its tooltip
            com.cobbleraids.shop.ShopEntry listed = com.cobbleraids.shop.ShopCatalogManager.index().get(entry.id());
            if (listed != null && listed.isLimited()) cell.addProperty("soldNote", switch (listed.reset()) {
                case NEVER -> "Yours for good: one per player";
                case DAILY -> "Back tomorrow";
                case WEEKLY -> "Back next week";
                case MONTHLY -> "Back next month";
                case ROTATION -> "Back with the next restock";
            });
            cell.addProperty("personal", entry.personal());
            cell.addProperty("canReroll", entry.canReroll());
            cell.addProperty("rerollCost", entry.rerollCost());
            cell.addProperty("ivPercent", entry.ivPercent());
            cell.addProperty("evPercent", entry.evPercent());
            String key;
            if (entry.pokemon()) {
                cell.addProperty("species", entry.species());
                key = WebIcons.speciesKey(entry.species(), entry.shiny());
            } else {
                String namespace = entry.itemId().getNamespace();
                String path = entry.itemId().getPath();
                cell.addProperty("name", WebIcons.itemName(namespace, path));
                key = WebIcons.itemKey(namespace, path);
                String shown = WebIcons.clientItem(entry.itemId(), player);
                if (!shown.isEmpty()) cell.addProperty("clientItem", shown);
            }
            if (WebIcons.dataUrl(key).isEmpty()) key = WebIcons.FALLBACK_ITEM;
            cell.addProperty("icon", key);
            entries.add(cell);
        }
        page.add("entries", entries);
        if (payload.personal() != null) {
            page.addProperty("slotsUsed", payload.personal().slotsUsed());
            page.addProperty("maxSlots", payload.personal().maxSlots());
            var offer = payload.personal().offer();
            if (offer != null) {
                var boss = offer.snapshot();
                JsonObject o = new JsonObject();
                String species = boss.species().getPath();
                o.addProperty("species", species);
                o.addProperty("level", com.cobbleraids.catching.BossSnapshotService.buyBackLevel(boss));
                o.addProperty("shiny", boss.shiny());
                o.addProperty("ivPercent", boss.ivPercent());
                o.addProperty("evPercent", boss.evPercent());
                o.addProperty("expiresInMillis", offer.expiresInMillis());
                o.addProperty("full", offer.full());
                String key = WebIcons.speciesKey(species, boss.shiny());
                if (WebIcons.dataUrl(key).isEmpty()) key = WebIcons.FALLBACK_ITEM;
                o.addProperty("icon", key);
                page.add("offer", o);
            }
        }

        // Icons first: the page paints from its cache when the page message lands.
        WebGuiBridge.emit(player, "shop_page", page.toString());
    }
}
