package land.webgui.server;

/** Pages are native layouts now; there are no web assets to serve. */
public final class WebviewAssets {
    private WebviewAssets() {}

    public static boolean enabled() { return false; }

    public static void reload() { net.snobblemon.server.SnobbleUi.reload(); }
}
