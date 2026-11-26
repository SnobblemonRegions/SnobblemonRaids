package com.cobbleraids.web;

import com.cobbleraids.RaidLog;
import com.cobbleraids.network.RequestThrottle;
import com.cobbleraids.perf.RaidPerf;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only class that talks to whatever shows this mod's pages, and it does so by name rather than by type.
 *
 * <p>Snobblemon GUI (SnobbleGUIOverhaul's server mod, {@code snobblemon_server}) is used when it is installed: every page is a native
 * Snobblemon page, {@code cobbleraids_<page>}, drawn by the Snobblemon Client, through the bridge's public {@code SnobbleUi}. Each call does
 * what WebGUI's compat jar ({@code WebguiCompat}, which stood in for WebGUI with the same bridge) did with the same request, so a server that
 * ran that jar sees no change and no longer needs it. Otherwise WebGUI itself is used, through its public {@code land.webgui.api.WebviewApi}
 * (and, for the asset rescan and the main-menu setting, {@code land.webgui.server.WebviewAssets} and {@code WebviewServerConfig}).
 *
 * <p>Both are reached through method handles: neither is a compile dependency (WebGUI's jar is built with a newer Loom than Gradle here can
 * remap), and no class of ours links against a type that may be absent. The cost is that an API change is not a compile error, so
 * {@link #bind()} resolves every call once at startup and reports exactly which one is missing. At runtime the mods share one namespace, so a
 * {@link ServerPlayer} here is the very class the other mod's methods take.
 */
final class WebGuiBridge {
    private WebGuiBridge() {}

    /** What shows the pages, in order of preference. */
    enum Backend { SNOBBLE, WEBGUI, NONE }

    static final String SNOBBLE_ID = "snobblemon_server";
    static final String WEBGUI_ID = "webgui";

    private static final String SNOBBLE_API = "net.snobblemon.server.SnobbleUi";
    private static final String API = "land.webgui.api.WebviewApi";
    private static final String ASSETS = "land.webgui.server.WebviewAssets";
    private static final ResourceLocation OPEN_CHANNEL = ResourceLocation.fromNamespaceAndPath("webgui", "open_web");

    // WebGUI
    private static MethodHandle openGui;
    private static MethodHandle emitToPage;
    private static MethodHandle onPageEvent;
    private static MethodHandle reloadAssets;
    private static MethodHandle sendMainMenu;
    private static MethodHandle configuredMainMenu;
    // Snobblemon GUI
    private static MethodHandle sAvailable;
    private static MethodHandle sOpen;
    private static MethodHandle sOpenPage;
    private static MethodHandle sHudPage;
    private static MethodHandle sEmitJson;
    private static MethodHandle sOnMessage;

    private static Backend backend = Backend.NONE;
    /** Pages Snobblemon GUI had no layout for, warned about once each. */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    /** Snobblemon GUI when installed, else WebGUI when installed, else nothing. Pure, for the tests. */
    static Backend choose(boolean snobbleInstalled, boolean webguiInstalled) {
        if (snobbleInstalled) return Backend.SNOBBLE;
        return webguiInstalled ? Backend.WEBGUI : Backend.NONE;
    }

    /**
     * Resolves every call this mod makes into the chosen backend. Snobblemon GUI falls back to WebGUI when it is installed but its API cannot
     * be bound. Returns false, and says why, when nothing is left.
     */
    static boolean bind(boolean snobbleInstalled, boolean webguiInstalled) {
        backend = choose(snobbleInstalled, webguiInstalled);
        if (backend == Backend.SNOBBLE) {
            try {
                bindSnobble();
                return true;
            } catch (ReflectiveOperationException | LinkageError e) {
                RaidLog.error("Snobblemon GUI is installed but its page API is not what this mod expects ({}); {}", e.toString(),
                        webguiInstalled ? "using WebGUI instead." : "pages are disabled.");
                backend = choose(false, webguiInstalled);
            }
        }
        if (backend == Backend.WEBGUI) {
            try {
                bindWebGui();
                return true;
            } catch (ReflectiveOperationException | LinkageError e) {
                RaidLog.error("WebGUI is installed but its API is not what this mod expects ({}); web pages are disabled. "
                        + "Update CobbleRaids or use WebGUI 1.8.x.", e.toString());
                backend = Backend.NONE;
            }
        }
        return false;
    }

    private static void bindSnobble() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> api = Class.forName(SNOBBLE_API);
        MethodHandle available = lookup.findStatic(api, "available", MethodType.methodType(boolean.class, ServerPlayer.class));
        MethodHandle open = lookup.findStatic(api, "open", MethodType.methodType(boolean.class, ServerPlayer.class, String.class));
        MethodHandle openPage = lookup.findStatic(api, "openPage", MethodType.methodType(String.class, ServerPlayer.class));
        MethodHandle hudPage = lookup.findStatic(api, "hudPage", MethodType.methodType(String.class, ServerPlayer.class));
        MethodHandle emitJson = lookup.findStatic(api, "emitJson",
                MethodType.methodType(void.class, ServerPlayer.class, String.class, String.class, String.class));
        MethodHandle onMessage = lookup.findStatic(api, "onMessage", MethodType.methodType(void.class, String.class, BiConsumer.class));
        sAvailable = available; sOpen = open; sOpenPage = openPage; sHudPage = hudPage; sEmitJson = emitJson; sOnMessage = onMessage;
    }

    private static void bindWebGui() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> api = Class.forName(API);
        Class<?> assets = Class.forName(ASSETS);
        openGui = lookup.findStatic(api, "openGui",
                MethodType.methodType(void.class, ServerPlayer.class, String.class));
        emitToPage = lookup.findStatic(api, "emitToPage",
                MethodType.methodType(void.class, ServerPlayer.class, String.class, String.class));
        onPageEvent = lookup.findStatic(api, "onPageEvent",
                MethodType.methodType(void.class, String.class, BiConsumer.class));
        reloadAssets = lookup.findStatic(assets, "reload", MethodType.methodType(void.class));
        sendMainMenu = lookup.findStatic(api, "sendMainMenuUrl",
                MethodType.methodType(void.class, ServerPlayer.class, String.class));
        configuredMainMenu = lookup.findStatic(Class.forName("land.webgui.server.WebviewServerConfig"),
                "mainMenuUrl", MethodType.methodType(String.class));
    }

    static boolean bound() {
        return backend != Backend.NONE;
    }

    static Backend backend() {
        return backend;
    }

    /** Rescans WebGUI's page folder, so files this mod has just written are served. Snobblemon GUI has its own layouts: nothing to do. */
    static void reloadPages() {
        if (backend != Backend.WEBGUI) return;
        invoke(() -> reloadAssets.invoke());
    }

    /** True if an open request can reach this player's client: the Snobblemon Client, or WebGUI's channel. */
    static boolean clientReady(ServerPlayer player) {
        return switch (backend) {
            case SNOBBLE -> {
                try {
                    yield (boolean) sAvailable.invokeExact(player);
                } catch (Throwable t) {
                    yield false;
                }
            }
            case WEBGUI -> ServerPlayNetworking.canSend(player, OPEN_CHANNEL);
            case NONE -> false;
        };
    }

    static void openGui(ServerPlayer player, String url) {
        if (backend == Backend.SNOBBLE) {
            String id = pageId(url);
            boolean shown;
            try {
                shown = (boolean) sOpen.invokeExact(player, id);
            } catch (Throwable t) {
                shown = false;
            }
            if (!shown && MISSING.add(id)) RaidLog.warn("Snobblemon GUI did not show page '{}' ({}) to {}", id, url, player.getGameProfile().getName());
            return;
        }
        invoke(() -> openGui.invoke(player, url));
    }

    /** Sends an event to the player's open page (a {@code hud...} event to their overlay when one is up); dropped when nothing is open. */
    static void emit(ServerPlayer player, String event, String json) {
        if (backend == Backend.SNOBBLE) {
            invoke(() -> {
                String hud = (String) sHudPage.invokeExact(player);
                String page = event.startsWith("hud") && hud != null ? hud : (String) sOpenPage.invokeExact(player);
                if (page != null) sEmitJson.invokeExact(player, page, event, json);
            });
            return;
        }
        invoke(() -> emitToPage.invoke(player, event, json));
    }

    /** Sets the page this player's main-menu key (F6 by default) opens. A WebGUI client feature; Snobblemon GUI has its own menus. */
    static void sendMainMenuUrl(ServerPlayer player, String url) {
        if (backend != Backend.WEBGUI) return;
        invoke(() -> sendMainMenu.invoke(player, url));
    }

    /** The main-menu page the server operator has set in WebGUI's own config; blank when they have not, or without WebGUI. */
    static String configuredMainMenuUrl() {
        if (backend != Backend.WEBGUI) return "";
        try {
            String url = (String) configuredMainMenu.invoke();
            return url == null ? "" : url;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * The native page id for a page address: {@code webgui://cobbleraids/hub/hub.html} is {@code cobbleraids_hub}. The compat jar's UrlMap
     * rule exactly: scheme, query and fragment dropped, {@code .html} dropped, each part lower case and limited to {@code a-z 0-9 _ -}, a part
     * that repeats the one before dropped, a trailing {@code index} dropped, parts joined by {@code _}.
     */
    static String pageId(String url) {
        if (url == null) return "";
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h >= 0) u = u.substring(0, h);
        if (u.startsWith("webgui://")) u = u.substring(9);
        else if (u.startsWith("webgui:/")) u = u.substring(8);
        List<String> parts = new ArrayList<>();
        for (String seg : u.split("/")) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) continue;
            if (seg.endsWith(".html")) seg = seg.substring(0, seg.length() - 5);
            seg = seg.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
            if (seg.isEmpty()) continue;
            if (!parts.isEmpty() && parts.get(parts.size() - 1).equals(seg)) continue;
            parts.add(seg);
        }
        if (parts.size() > 1 && parts.get(parts.size() - 1).equals("index")) parts.remove(parts.size() - 1);
        return String.join("_", parts);
    }

    /**
     * One flood budget per player across every page, applied here because every page's events pass
     * through {@link #onPageEvent}. A page event is a few dozen bytes but can make the server rebuild a
     * page and resend its icons, so a client that fires them in a loop would otherwise cost far more to
     * answer than to send. Far above anything a person does: pages send one event at a time and wait.
     * Not thread-safe by design, like {@link RequestThrottle}: events arrive on the server thread.
     */
    private static final RequestThrottle EVENT_BUDGET = new RequestThrottle(30, 1000);

    /** Drops a disconnected player's budget so the map cannot grow without bound. */
    static void forget(UUID player) {
        EVENT_BUDGET.forget(player);
    }

    static void onPageEvent(String channel, BiConsumer<ServerPlayer, String> handler) {
        // Built once per channel rather than per event, so timing a page event allocates nothing.
        String section = "web:" + channel;
        BiConsumer<ServerPlayer, String> guarded = (player, json) -> {
            if (!EVENT_BUDGET.allow(player.getUUID(), System.currentTimeMillis())) return;
            long started = RaidPerf.start();
            try {
                handler.accept(player, json);
            } catch (RuntimeException e) {
                // A page can send anything; a handler bug must not reach the other mod's event bus.
                RaidLog.error("web page event '{}' failed: {}", channel, e.toString());
            } finally {
                RaidPerf.stop(section, started);
            }
        };
        if (backend == Backend.SNOBBLE) invoke(() -> sOnMessage.invoke(channel, guarded));
        else invoke(() -> onPageEvent.invoke(channel, guarded));
    }

    private interface Call {
        void run() throws Throwable;
    }

    private static void invoke(Call call) {
        if (backend == Backend.NONE) return;
        try {
            call.run();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Page call failed", t);
        }
    }
}
