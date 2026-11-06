package land.webgui;

import net.minecraft.server.level.ServerPlayer;
import net.snobblemon.server.SnobbleUi;

public final class WebviewNetworking {
    public enum ClientKind { CURRENT, OUTDATED, ABSENT }

    private WebviewNetworking() {}

    public static ClientKind clientKind(ServerPlayer player) {
        return SnobbleUi.available(player) ? ClientKind.CURRENT : ClientKind.ABSENT;
    }
}
