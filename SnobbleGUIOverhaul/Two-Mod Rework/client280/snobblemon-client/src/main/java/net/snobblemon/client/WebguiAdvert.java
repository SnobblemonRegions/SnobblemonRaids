package net.snobblemon.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Mods that used WebGUI decide "can this player see my web page?" by checking whether the client listens on WebGUI's network channels
 * ({@code webgui:hello}, {@code webgui:open_web}). With the WebGUI compatibility jar on the server those pages are native, so this client
 * listens on the same two channels and ignores whatever arrives on them. Skipped when the real WebGUI client is installed (it registers them itself).
 */
final class WebguiAdvert {
    private WebguiAdvert() {}

    record Ignored(CustomPacketPayload.Type<Ignored> type) implements CustomPacketPayload {
    }

    static void register() {
        if (FabricLoader.getInstance().isModLoaded("webgui")) return;
        for (String path : new String[]{"hello", "open_web"}) {
            CustomPacketPayload.Type<Ignored> type = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("webgui", path));
            StreamCodec<RegistryFriendlyByteBuf, Ignored> codec = new StreamCodec<>() {
                @Override public Ignored decode(RegistryFriendlyByteBuf buf) { io.netty.buffer.ByteBuf raw = buf; raw.skipBytes(raw.readableBytes()); return new Ignored(type); }
                @Override public void encode(RegistryFriendlyByteBuf buf, Ignored v) {}
            };
            PayloadTypeRegistry.playS2C().register(type, codec);
            ClientPlayNetworking.registerGlobalReceiver(type, (p, ctx) -> { });
        }
    }
}
