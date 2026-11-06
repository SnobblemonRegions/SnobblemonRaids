package net.snobblemon.hubnet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The wire protocol between the Snobble Hub server mod and the owo-ui client mod. This one file is compiled into both
 * jars. The server sends page data as JSON; the client sends only requests ("buy this id"), never results.
 */
public final class HubNet {
    public static final String NS = "snobblehub";
    private static boolean registered;

    private HubNet() {}

    /** Server to client: the data for one page. {@code open} asks the client to open the screen if it is not showing. */
    public record Data(String page, boolean open, String json) implements CustomPacketPayload {
        public static final Type<Data> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NS, "data"));
        public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Data::page,
                ByteBufCodecs.BOOL, Data::open,
                ByteBufCodecs.stringUtf8(900_000), Data::json,
                Data::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: something the player clicked. The server decides what, if anything, happens. */
    public record Request(String action, String arg) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NS, "request"));
        public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), Request::action,
                ByteBufCodecs.stringUtf8(256), Request::arg,
                Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Safe to call from both mods (a single-player game loads both). */
    public static synchronized void registerTypes() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.playS2C().register(Data.TYPE, Data.CODEC);
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
    }
}
