package com.cobbleraids.radar;

import com.cobbleraids.RaidLog;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server-side hologram of every player who holds an open radar and has no client companion. Server thread only. A fault in one hologram removes
 * that hologram and logs it; it never reaches the radar map, which keeps working.
 */
final class ServerHologramService {
    /** Operators can switch the hologram off (the flat map is unaffected) with {@code -Dcobbleraids.serverHologram=off}. */
    private static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("cobbleraids.serverHologram", "on"));
    private static final int MOVE_TICKS = 2;
    private static final Map<UUID, ServerHologram> HOLOGRAMS = new HashMap<>();
    private static int tick;

    private ServerHologramService() {}

    static boolean enabled() {
        return ENABLED;
    }

    /** Creates the hologram on first use; replaces it when the player has changed dimension. Returns null if it could not be made. */
    static ServerHologram of(ServerPlayer player) {
        if (!ENABLED) return null;
        ServerHologram existing = HOLOGRAMS.get(player.getUUID());
        if (existing != null && existing.level() != player.serverLevel()) {
            remove(player.getUUID());
            existing = null;
        }
        if (existing != null) return existing;
        try {
            ServerHologram created = new ServerHologram(player);
            HOLOGRAMS.put(player.getUUID(), created);
            return created;
        } catch (RuntimeException ex) {
            RaidLog.error("Could not start the radar hologram for {}", player.getGameProfile().getName(), ex);
            return null;
        }
    }

    /** Called every server tick; the holograms move every {@value #MOVE_TICKS} ticks. */
    static void tick(MinecraftServer server) {
        if (HOLOGRAMS.isEmpty() || ++tick % MOVE_TICKS != 0) return;
        for (Iterator<Map.Entry<UUID, ServerHologram>> it = HOLOGRAMS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, ServerHologram> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.serverLevel() != entry.getValue().level()) {
                entry.getValue().close();
                it.remove();
                continue;
            }
            try {
                entry.getValue().follow(player);
            } catch (RuntimeException ex) {
                RaidLog.error("The radar hologram for {} failed and was removed", player.getGameProfile().getName(), ex);
                entry.getValue().close();
                it.remove();
            }
        }
    }

    static void remove(UUID playerId) {
        ServerHologram hologram = HOLOGRAMS.remove(playerId);
        if (hologram != null) hologram.close();
    }

    /** Keeps only the holograms of players who still have a radar view; closes the rest. */
    static void retainOnly(Set<UUID> viewers) {
        for (Iterator<Map.Entry<UUID, ServerHologram>> it = HOLOGRAMS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, ServerHologram> entry = it.next();
            if (viewers.contains(entry.getKey())) continue;
            entry.getValue().close();
            it.remove();
        }
    }

    static void clear() {
        for (ServerHologram hologram : HOLOGRAMS.values()) hologram.close();
        HOLOGRAMS.clear();
        tick = 0;
    }
}
