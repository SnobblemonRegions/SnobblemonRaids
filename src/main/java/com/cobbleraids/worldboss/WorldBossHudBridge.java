package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import com.cobbleraids.visual.protocol.BossHudPayload;
import com.cobbleraids.visual.protocol.VisualProtocol;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;

/** Optional presentation metadata; health/rewards and native boss packets remain server-owned. */
final class WorldBossHudBridge {
    private record Sent(ServerPlayer player, UUID bar, long tick, int detail, boolean preparing) {}
    private static final Map<UUID, Sent> VIEWERS = new HashMap<>();
    private static long sequence;
    private static boolean failed;
    private WorldBossHudBridge() {}
    static void register() {
        VisualProtocol.once("worldboss-hud", () -> PayloadTypeRegistry.playS2C().register(BossHudPayload.TYPE, BossHudPayload.CODEC));
    }
    static void publish(ServerPlayer player, ServerBossEvent bar, WorldBossState state, boolean visible, String title, String status, boolean preparing) {
        if (failed) return;
        try { send(player,bar,state,visible,title,status,preparing); }
        catch (RuntimeException failure) {
            failed = true; VIEWERS.clear();
            RaidLog.warn("World Boss HUD metadata unavailable; native bars remain active: {}",failure.toString());
        }
    }
    private static void send(ServerPlayer player, ServerBossEvent bar, WorldBossState state, boolean visible, String title, String status, boolean preparing) {
        UUID id = player.getUUID();
        var old = VIEWERS.get(id);
        if (old != null && old.player() != player) { VIEWERS.remove(id); old = null; }
        if (!ServerPlayNetworking.canSend(player, BossHudPayload.TYPE)) { VIEWERS.remove(id); return; }
        if (!visible && old == null) return;
        long tick = player.serverLevel().getGameTime();
        int detail = switch (state.benefitsView().sceneOptions(id).effects()) { case FULL -> 2; case MINIMAL -> 1; case OFF -> 0; };
        if (visible && old != null && old.bar().equals(bar.getId()) && old.detail() == detail && old.preparing() == preparing && tick >= old.tick() && tick - old.tick() < 20) return;
        ServerPlayNetworking.send(player, new BossHudPayload(++sequence, visible, bar.getId(), state.dimension(),
                title.length() > 128 ? title.substring(0,128) : title,
                status.length() > 160 ? status.substring(0,160) : status,
                state.pool().current(), state.pool().max(), detail, preparing));
        if (visible) VIEWERS.put(id, new Sent(player, bar.getId(), tick, detail, preparing)); else VIEWERS.remove(id);
        VIEWERS.entrySet().removeIf(entry -> entry.getValue().player().hasDisconnected());
    }
    static void clear() { VIEWERS.clear(); failed = false; }
}
