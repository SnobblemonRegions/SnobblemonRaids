package land.webgui.server;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

/** Settings other mods read. There is no token support and nothing secret here. */
public final class WebviewServerConfig {
    private WebviewServerConfig() {}

    public static void load() {}
    public static boolean enableTokens() { return false; }
    public static boolean autoHudOnJoin() { return false; }
    public static String autoHudUrl() { return ""; }
    public static String mainMenuUrl() { return ""; }
    public static String deathScreenUrl() { return ""; }
    public static boolean serveBundledPages() { return false; }
    /** Where bundle installers drop their html; kept so they do not fail, nothing reads it. */
    public static Path bundledPagesRoot() { return FabricLoader.getInstance().getConfigDir().resolve("webgui").resolve("web"); }
    public static boolean requireClientMod() { return false; }
    public static int pageEventsPerSecond() { return 14; }
    public static List<String> trustedCommandOrigins() { return List.of(); }
    public static String reload() { return net.snobblemon.server.SnobbleUi.reload(); }
}
