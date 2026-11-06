package net.snobblemon.hubclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.snobblemon.hubclient.ui.CardScreen;
import net.snobblemon.hubclient.ui.CosmeticsScreen;
import net.snobblemon.hubclient.ui.ShopScreen;
import net.snobblemon.hubclient.ui.SnobbleScreen;
import net.snobblemon.hubclient.ui.UpdatesScreen;
import net.snobblemon.hubclient.ui.WarpScreen;
import net.snobblemon.hubclient.ui.WikiScreen;
import net.snobblemon.hubnet.HubNet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Receives page data from the server and opens or refreshes the matching screen. The client never decides anything itself. */
public final class HubClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SnobbleHubClient");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(HubNet.Data.TYPE, (payload, ctx) -> ctx.client().execute(() -> handle(payload)));
        InventoryTab.register();
    }

    private static void handle(HubNet.Data d) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject json;
        try {
            json = JsonParser.parseString(d.json()).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warn("Bad data for page {}: {}", d.page(), e.toString());
            return;
        }
        if (d.page().equals("close")) {
            if (mc.screen instanceof SnobbleScreen) mc.setScreen(null);
            return;
        }
        if (d.page().equals("toast")) {
            if (mc.screen instanceof SnobbleScreen s) s.toast(json.get("text").getAsString(), json.get("ok").getAsBoolean());
            return;
        }
        if (d.page().equals("shopQuote") || d.page().equals("shopResult")) {
            if (mc.screen instanceof ShopScreen shop) { if (d.page().equals("shopQuote")) shop.onQuote(json); else shop.onResult(json); }
            return;
        }
        SnobbleScreen current = mc.screen instanceof SnobbleScreen s ? s : null;
        if (current != null && current.page().equals(d.page())) {
            current.update(json);          // same page: refresh in place (a purchase, an equip)
            return;
        }
        if (current == null && !d.open()) return;   // a refresh for a screen the player already closed
        SnobbleScreen next = switch (d.page()) {
            case "cos" -> new CosmeticsScreen(json);
            case "card" -> new CardScreen(json);
            case "wiki" -> new WikiScreen(json);
            case "warp" -> new WarpScreen(json);
            case "upd" -> new UpdatesScreen(json);
            case "shop" -> new ShopScreen(json);
            default -> null;
        };
        if (next != null) mc.setScreen(next);
    }

    public static void request(String action, String arg) {
        if (Minecraft.getInstance().getConnection() == null) return;
        ClientPlayNetworking.send(new HubNet.Request(action, arg));
    }
}
