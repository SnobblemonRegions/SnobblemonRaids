package net.snobblemon.hub;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional vs required client. With {@code require_client} off (the default) nothing happens here: players without the client
 * mod keep the chest menus. With it on, a player whose game has not announced the hub's data channel a moment after joining is
 * disconnected with a message; staff holding {@code snobblehub.bypass_client} (or operators) are let in so a broken client can
 * never lock the admins out. The channel is announced by the game itself during login, so a plain bot or vanilla client is refused.
 */
public final class ClientGate {
    private static final Map<UUID, Integer> WAITING = new ConcurrentHashMap<>();

    private ClientGate() {}

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (HubConfig.general.require_client) WAITING.put(handler.getPlayer().getUUID(), Math.max(0, HubConfig.general.require_client_grace_ticks));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WAITING.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (WAITING.isEmpty()) return;
            for (Iterator<Map.Entry<UUID, Integer>> it = WAITING.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Integer> e = it.next();
                int left = e.getValue() - 1;
                if (left > 0) { e.setValue(left); continue; }
                it.remove();
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null || !HubConfig.general.require_client) continue;
                if (ClientBridge.supports(p) || Permissions.check(p, "snobblehub.bypass_client", 3)) continue;
                SnobbleHub.LOGGER.info("{} joined without the Snobble client mod and was disconnected (require_client).", p.getGameProfile().getName());
                p.connection.disconnect(Component.literal(HubConfig.general.require_client_message));
            }
        });
    }
}
