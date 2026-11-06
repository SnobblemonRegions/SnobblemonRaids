package net.snobblemon.proto;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Wire protocol between Snobblemon Client and Snobblemon Server. One file, compiled into both jars (the same way HubNet is).
 * Every string has a hard length cap in its codec, so an oversized or forged packet is rejected by the decoder itself.
 * The client only ever ASKS (Hello, PackState, GuiAction); the server decides.
 */
public final class SnobbleProto {
    public static final String NS = "snobblemon";
    /** Bumped when a packet changes shape. Different numbers on the two sides = the server refuses the client's GUIs. */
    public static final int PROTOCOL = 2;
    public static final int MAX_JSON = 262_144;
    private static boolean registered;

    private SnobbleProto() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NS, path));
    }

    /** C2S, sent on join: "I am a Snobblemon client of this version." */
    public record Hello(int protocol, String clientVersion) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = SnobbleProto.mk("hello");
        public static final StreamCodec<FriendlyByteBuf, Hello> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hello::protocol, ByteBufCodecs.stringUtf8(32), Hello::clientVersion, Hello::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: answer to Hello. artVersion is the art version the server's pack carries; the client compares it with what it has loaded. */
    public record Welcome(int protocol, boolean accepted, String reason, String themeNamespace, String artVersion, String serverVersion) implements CustomPacketPayload {
        public static final Type<Welcome> TYPE = SnobbleProto.mk("welcome");
        public static final StreamCodec<FriendlyByteBuf, Welcome> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Welcome::protocol, ByteBufCodecs.BOOL, Welcome::accepted, ByteBufCodecs.stringUtf8(200), Welcome::reason,
                ByteBufCodecs.stringUtf8(64), Welcome::themeNamespace, ByteBufCodecs.stringUtf8(64), Welcome::artVersion, ByteBufCodecs.stringUtf8(32), Welcome::serverVersion,
                Welcome::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: the art version the client has loaded right now ("" = no theme found) and the theme namespace it found. Sent after join and after every resource reload. */
    public record PackState(String loadedArtVersion, String themeNamespace) implements CustomPacketPayload {
        public static final Type<PackState> TYPE = SnobbleProto.mk("pack_state");
        public static final StreamCodec<FriendlyByteBuf, PackState> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), PackState::loadedArtVersion, ByteBufCodecs.stringUtf8(64), PackState::themeNamespace, PackState::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: open a GUI. layoutJson is a declarative definition (see GuiSpec), dataJson the values its {placeholders} read. */
    public record GuiOpen(String guiId, String layoutJson, String dataJson) implements CustomPacketPayload {
        public static final Type<GuiOpen> TYPE = SnobbleProto.mk("gui_open");
        public static final StreamCodec<FriendlyByteBuf, GuiOpen> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(48), GuiOpen::guiId, ByteBufCodecs.stringUtf8(MAX_JSON), GuiOpen::layoutJson, ByteBufCodecs.stringUtf8(MAX_JSON), GuiOpen::dataJson, GuiOpen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: new data (and an optional one-line message) for the GUI that is already open. */
    public record GuiUpdate(String guiId, String dataJson, String message, boolean messageOk) implements CustomPacketPayload {
        public static final Type<GuiUpdate> TYPE = SnobbleProto.mk("gui_update");
        public static final StreamCodec<FriendlyByteBuf, GuiUpdate> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(48), GuiUpdate::guiId, ByteBufCodecs.stringUtf8(MAX_JSON), GuiUpdate::dataJson,
                ByteBufCodecs.stringUtf8(200), GuiUpdate::message, ByteBufCodecs.BOOL, GuiUpdate::messageOk, GuiUpdate::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: close the GUI (if it is the one named). */
    public record GuiClose(String guiId) implements CustomPacketPayload {
        public static final Type<GuiClose> TYPE = SnobbleProto.mk("gui_close");
        public static final StreamCodec<FriendlyByteBuf, GuiClose> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(48), GuiClose::guiId, GuiClose::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: "the player pressed this widget". Carries no arguments the server must trust: it looks the widget up in its own definition. */
    public record GuiAction(String guiId, String widgetId, String value) implements CustomPacketPayload {
        public static final Type<GuiAction> TYPE = SnobbleProto.mk("gui_action");
        public static final StreamCodec<FriendlyByteBuf, GuiAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(48), GuiAction::guiId, ByteBufCodecs.stringUtf8(48), GuiAction::widgetId, ByteBufCodecs.stringUtf8(64), GuiAction::value, GuiAction::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- layout v2 ("UI pages": scrolling, repeats, inputs, live data)

    /** S2C: open a page. layoutJson is a layout-v2 tree (UiSpec); dataJson the values it reads. */
    public record UiOpen(String pageId, String layoutJson, String dataJson) implements CustomPacketPayload {
        public static final Type<UiOpen> TYPE = SnobbleProto.mk("ui_open");
        public static final StreamCodec<FriendlyByteBuf, UiOpen> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(48), UiOpen::pageId, ByteBufCodecs.stringUtf8(MAX_JSON), UiOpen::layoutJson, ByteBufCodecs.stringUtf8(MAX_JSON), UiOpen::dataJson, UiOpen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: show a HUD page (drawn over the world, no mouse). Its data comes in UiUpdate and it goes away with UiClose, by page id. */
    public record UiHud(String pageId, String layoutJson, String dataJson) implements CustomPacketPayload {
        public static final Type<UiHud> TYPE = SnobbleProto.mk("ui_hud");
        public static final StreamCodec<FriendlyByteBuf, UiHud> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(48), UiHud::pageId, ByteBufCodecs.stringUtf8(MAX_JSON), UiHud::layoutJson, ByteBufCodecs.stringUtf8(MAX_JSON), UiHud::dataJson, UiHud::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: replace the page's data (empty = keep) and optionally show a toast. */
    public record UiUpdate(String pageId, String dataJson, String toast, boolean toastOk) implements CustomPacketPayload {
        public static final Type<UiUpdate> TYPE = SnobbleProto.mk("ui_update");
        public static final StreamCodec<FriendlyByteBuf, UiUpdate> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(48), UiUpdate::pageId, ByteBufCodecs.stringUtf8(MAX_JSON), UiUpdate::dataJson,
                ByteBufCodecs.stringUtf8(200), UiUpdate::toast, ByteBufCodecs.BOOL, UiUpdate::toastOk, UiUpdate::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: close the page (if it is the one named). */
    public record UiClose(String pageId) implements CustomPacketPayload {
        public static final Type<UiClose> TYPE = SnobbleProto.mk("ui_close");
        public static final StreamCodec<FriendlyByteBuf, UiClose> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(48), UiClose::pageId, UiClose::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * C2S: "the player used this node". argsJson is a flat object of strings ({"page":"3"}). The server looks the node up in its own copy of
     * the layout, rebuilds the message from the node's template and re-validates every argument; nothing here is trusted.
     */
    public record UiMsg(String pageId, String nodeId, String argsJson) implements CustomPacketPayload {
        public static final Type<UiMsg> TYPE = SnobbleProto.mk("ui_msg");
        public static final StreamCodec<FriendlyByteBuf, UiMsg> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(48), UiMsg::pageId, ByteBufCodecs.stringUtf8(48), UiMsg::nodeId, ByteBufCodecs.stringUtf8(2048), UiMsg::argsJson, UiMsg::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: "send me this small Pokemon picture" (key like "s:abra:0"; the server checks it). */
    public record SpriteReq(String key) implements CustomPacketPayload {
        public static final Type<SpriteReq> TYPE = SnobbleProto.mk("sprite_req");
        public static final StreamCodec<FriendlyByteBuf, SpriteReq> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(48), SpriteReq::key, SpriteReq::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: the picture (a small PNG), or empty when there is none. */
    public record SpriteData(String key, byte[] png) implements CustomPacketPayload {
        public static final Type<SpriteData> TYPE = SnobbleProto.mk("sprite_data");
        public static final StreamCodec<FriendlyByteBuf, SpriteData> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(48), SpriteData::key, ByteBufCodecs.byteArray(65536), SpriteData::png, SpriteData::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Safe to call from both mods (a single-player game loads both). */
    public static synchronized void registerTypes() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.playC2S().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.playS2C().register(Welcome.TYPE, Welcome.CODEC);
        PayloadTypeRegistry.playC2S().register(PackState.TYPE, PackState.CODEC);
        PayloadTypeRegistry.playS2C().register(GuiOpen.TYPE, GuiOpen.CODEC);
        PayloadTypeRegistry.playS2C().register(GuiUpdate.TYPE, GuiUpdate.CODEC);
        PayloadTypeRegistry.playS2C().register(GuiClose.TYPE, GuiClose.CODEC);
        PayloadTypeRegistry.playC2S().register(GuiAction.TYPE, GuiAction.CODEC);
        PayloadTypeRegistry.playC2S().register(SpriteReq.TYPE, SpriteReq.CODEC);
        PayloadTypeRegistry.playS2C().register(SpriteData.TYPE, SpriteData.CODEC);
        PayloadTypeRegistry.playS2C().register(UiOpen.TYPE, UiOpen.CODEC);
        PayloadTypeRegistry.playS2C().register(UiHud.TYPE, UiHud.CODEC);
        PayloadTypeRegistry.playS2C().register(UiUpdate.TYPE, UiUpdate.CODEC);
        PayloadTypeRegistry.playS2C().register(UiClose.TYPE, UiClose.CODEC);
        PayloadTypeRegistry.playC2S().register(UiMsg.TYPE, UiMsg.CODEC);
    }
}
