package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import com.cobbleraids.visual.protocol.VeilPayload;
import com.cobbleraids.visual.protocol.VisualProtocol;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** TEST-only compact scene bridge. All ribbons, motion and motes are drawn by the optional client. */
public final class DistortionVeilPrototype {
    private static final UUID NONE = new UUID(0, 0);
    private record Viewer(ServerPlayer player, VeilPayload frame, long sentAt) {}
    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();
    private static UUID scene;
    private static long started, sequence;
    private DistortionVeilPrototype() {}
    public static void register() {
        WorldBossHudBridge.register();
        VisualProtocol.once("veil", () -> PayloadTypeRegistry.playS2C().register(VeilPayload.TYPE, VeilPayload.CODEC));
    }
    static boolean enabled(WorldBossState state) {
        return state.testMode() && Boolean.getBoolean("cobbleraids.worldboss.distortionPrototype");
    }
    static void tick(MinecraftServer server, WorldBossState state, WorldBossConfig config) {
        var dimension = ResourceLocation.tryParse(state.dimension());
        var level = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || !enabled(state)) { clear(server); return; }
        long now = level.getGameTime();
        if (!state.bossId().equals(scene)) {
            clear(server); scene = state.bossId(); started = now;
            RaidLog.info("Distortion Veil prototype for TEST World Boss {}: client ribbons, ground currents and Umbra Mantle", scene);
        }
        UUID boss = WorldBossService.showpieceId();
        var entity = boss == null ? null : level.getEntity(boss);
        var online = new HashSet<UUID>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID(); online.add(id);
            var old = VIEWERS.get(id);
            if (old != null && old.player() != player) { VIEWERS.remove(id); old = null; }
            boolean eligible = player.level() == level && WorldBossService.audienceViews(player)
                    && ServerPlayNetworking.canSend(player, VeilPayload.TYPE);
            if (!eligible) {
                if (old != null) stop(old);
                VIEWERS.remove(id); continue;
            }
            int detail = switch (state.benefitsView().sceneOptions(id).effects()) {
                case FULL -> 2; case MINIMAL -> 1; case OFF -> 0;
            };
            int entityId = entity == null ? -1 : entity.getId();
            if (old != null && now - old.sentAt() < 20 && now >= old.sentAt()
                    && old.frame().detail() == detail && old.frame().entityId() == entityId) continue;
            var frame = new VeilPayload(++sequence, true, scene, state.dimension(), state.x(), state.y(), state.z(),
                    (float) WorldBossRules.zoneRadius(state, config), config.bossScale(), entityId, boss == null ? NONE : boss,
                    detail, Math.max(0, now - started));
            ServerPlayNetworking.send(player, frame);
            VIEWERS.put(id, new Viewer(player, frame, now));
        }
        VIEWERS.keySet().retainAll(online);
    }
    private static void stop(Viewer viewer) {
        var p = viewer.frame();
        if (ServerPlayNetworking.canSend(viewer.player(), VeilPayload.TYPE)) {
            ServerPlayNetworking.send(viewer.player(), new VeilPayload(++sequence, false, p.scene(), p.dimension(),
                    p.x(), p.y(), p.z(), p.radius(), p.bossScale(), -1, NONE, 0, p.ageTicks()));
        }
    }
    static void clear(MinecraftServer server) {
        for (var viewer : VIEWERS.values()) {
            if (server.getPlayerList().getPlayer(viewer.player().getUUID()) == viewer.player()) stop(viewer);
        }
        reset();
    }
    static void reset() { VIEWERS.clear(); scene = null; }
}
