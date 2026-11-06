package net.snobblemon.client.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client half of Snobble Cosmetics: keeps what the server says each nearby player wears and draws it on the player model
 * (see {@link CosmeticLayer}). The server owns ownership, permissions and the equipped selection.
 */
public final class CosmeticsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SnobbleCosmeticsClient");

    public record Loadout(UUID player, String json) implements CustomPacketPayload {
        public static final Type<Loadout> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("snobblemon_cosmetics", "loadout"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Loadout> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Loadout::player, ByteBufCodecs.stringUtf8(4096), Loadout::json, Loadout::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Defs(String json) implements CustomPacketPayload {
        public static final Type<Defs> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("snobblemon_cosmetics", "defs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Defs> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(500_000), Defs::json, Defs::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<String, CosmeticSpec> SPECS = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerCosmetics> PLAYERS = new ConcurrentHashMap<>();
    /** Client-side switch (a player can hide everyone's cosmetics for performance). */
    public static volatile boolean showOthers = true;

    public static PlayerCosmetics get(UUID id) {
        return PLAYERS.get(id);
    }

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(Loadout.TYPE, Loadout.CODEC);
        PayloadTypeRegistry.playS2C().register(Defs.TYPE, Defs.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(Defs.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            try {
                for (JsonElement e : JsonParser.parseString(p.json()).getAsJsonArray()) {
                    CosmeticSpec s = new CosmeticSpec(e.getAsJsonObject());
                    SPECS.put(s.id, s);
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("Ignoring bad cosmetic definitions: {}", ex.toString());
            }
        }));

        ClientPlayNetworking.registerGlobalReceiver(Loadout.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            try {
                JsonObject o = JsonParser.parseString(p.json()).getAsJsonObject();
                PlayerCosmetics pc = new PlayerCosmetics();
                for (String slot : o.keySet()) {
                    CosmeticSpec s = SPECS.get(o.get(slot).getAsString());
                    if (s != null) pc.worn.put(slot, s);
                }
                pc.recompute();
                if (pc.isEmpty()) PLAYERS.remove(p.player()); else PLAYERS.put(p.player(), pc);
            } catch (RuntimeException ex) {
                LOGGER.warn("Ignoring bad cosmetic loadout: {}", ex.toString());
            }
        }));

        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> PLAYERS.clear());

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerRenderer pr) {
                @SuppressWarnings("unchecked")
                RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent = (RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) pr;
                helper.register(new CosmeticLayer(parent));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null || PLAYERS.isEmpty()) return;
            for (Map.Entry<UUID, PlayerCosmetics> e : PLAYERS.entrySet()) {
                PlayerCosmetics pc = e.getValue();
                if (!pc.needsTick()) continue;
                var player = mc.level.getPlayerByUUID(e.getKey());
                if (player instanceof AbstractClientPlayer acp) pc.tick(acp);
            }
        });
    }

    /** Used by the armor mixin: is the real armor piece in this slot covered by a cosmetic? */
    public static boolean replacesArmor(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.EquipmentSlot slot) {
        if (!(entity instanceof AbstractClientPlayer p)) return false;
        PlayerCosmetics pc = PLAYERS.get(p.getUUID());
        return pc != null && visible(p) && pc.replaces(slot);
    }

    /** Used by the player-renderer mixin: draw no body at all because a complete armor cosmetic stands in for it. */
    public static boolean hidesBody(AbstractClientPlayer p) {
        PlayerCosmetics pc = PLAYERS.get(p.getUUID());
        return pc != null && pc.fullSet && visible(p);
    }

    static boolean visible(AbstractClientPlayer p) {
        if (p.isInvisible() || p.isSpectator()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!showOthers && p != mc.player) return false;
        return true;
    }
}
