package com.cobbleraids.veilclient;

import com.cobbleraids.visual.protocol.VeilPayload;
import com.cobbleraids.visual.protocol.VisualProtocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;

public final class DistortionVeilClient implements ClientModInitializer {
    private static final VeilTimeline CLOCK = new VeilTimeline();
    private static VeilPayload frame;
    private static long received;
    private static Object world;
    private static boolean faultReported;
    static long millis() { return System.nanoTime() / 1_000_000; }
    @Override public void onInitializeClient() {
        WorldBossHud.register();
        VisualProtocol.once("veil", () -> PayloadTypeRegistry.playS2C().register(VeilPayload.TYPE, VeilPayload.CODEC));
        ClientPlayNetworking.registerGlobalReceiver(VeilPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    long now = millis();
                    if (CLOCK.accept(payload.sequence(), now, payload.active(), frame != null && !frame.scene().equals(payload.scene()))) {
                        frame = payload; received = now; world = context.client().level;
                    }
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        WorldRenderEvents.START.register(context -> WingAttachments.beginFrame());
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> WingAttachments.openWorldPass());
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            WingAttachments.closeWorldPass();
            DistortionVeilRenderer.render(context);
        });
    }
    static VeilPayload frame() {
        var client = Minecraft.getInstance();
        if (frame == null) return null;
        if (client.level != world || client.level == null
                || !client.level.dimension().location().toString().equals(frame.dimension())) { clear(); return null; }
        return CLOCK.opacity(millis()) > 0 ? frame : null;
    }
    public static VeilPayload sceneFor(net.minecraft.world.entity.Entity entity) {
        var current = frame;
        if (current == null || current.entityId() != entity.getId() || !current.entityUuid().equals(entity.getUUID())) return null;
        return frame();
    }
    public static void reportFault(String site, RuntimeException failure) {
        if (faultReported) return;
        faultReported = true;
        org.slf4j.LoggerFactory.getLogger("CobbleRaids/DistortionVeil").error("[CobbleRaids] Visual hook failed at " + site, failure);
    }
    static double opacity() { return CLOCK.opacity(millis()); }
    static double seconds() { return frame.ageTicks() / 20.0 + Math.min(6, (millis() - received) / 1000.0); }
    private static void clear() { WingAttachments.clear(); frame = null; world = null; CLOCK.clear(); }
}
