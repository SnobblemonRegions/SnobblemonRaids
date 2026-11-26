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
import net.minecraft.server.level.ServerPlayer;

/**
 * The only class that talks to Snobblemon GUI (SnobbleGUIOverhaul's server mod, {@code snobblemon_server}), which shows this mod's pages: every page
 * is a native Snobblemon page, {@code cobbleraids_<page>}, drawn by the Snobblemon Client, through the bridge's public {@code SnobbleUi}.
 *
 * <p>Reached through method handles: Snobblemon GUI is not a compile dependency, and no class of ours links against a type that may be absent. The
 * cost is that an API change is not a compile error, so {@link #bind()} resolves every call once at startup and reports what is missing. At
 * runtime the mods share one namespace, so a {@link ServerPlayer} here is the very class the bridge's methods take.
 */
final class WebGuiBridge {
    private WebGuiBridge() {}

    static final String SNOBBLE_ID = "snobblemon_server";

    private static final String SNOBBLE_API = "net.snobblemon.server.SnobbleUi";
    private static MethodHandle sAvailable;
    private static MethodHandle sOpen;
    private static MethodHandle sOpenPage;
    private static MethodHandle sHudPage;
    private static MethodHandle sEmitJson;
    private static MethodHandle sOnMessage;

    private static boolean bound;
    /** Pages Snobblemon GUI had no layout for, warned about once each. */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    /** Resolves every call this mod makes into Snobblemon GUI. Returns false, and says why, if one is missing. */
    static boolean bind() {
        try {
            bindSnobble();
            bound = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            bound = false;
            RaidLog.error("Snobblemon GUI is installed but its page API is not what this mod expects ({}); raid pages are disabled.", e.toString());
        }
        return bound;
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

    static boolean bound() {
        return bound;
    }

    /** True if an open request can reach this player's client: it has the Snobblemon Client. */
    static boolean clientReady(ServerPlayer player) {
        if (!bound) return false;
        try {
            return (boolean) sAvailable.invokeExact(player);
        } catch (Throwable t) {
            return false;
        }
    }

    static void openGui(ServerPlayer player, String url) {
        if (!bound) return;
        String id = pageId(url);
        boolean shown;
        try {
            shown = (boolean) sOpen.invokeExact(player, id);
        } catch (Throwable t) {
            shown = false;
        }
        if (!shown && MISSING.add(id)) RaidLog.warn("Snobblemon GUI did not show page '{}' ({}) to {}", id, url, player.getGameProfile().getName());
    }

    /** Sends an event to the player's open page (a {@code hud...} event to their overlay when one is up); dropped when nothing is open. */
    static void emit(ServerPlayer player, String event, String json) {
        invoke(() -> {
            String hud = (String) sHudPage.invokeExact(player);
            String page = event.startsWith("hud") && hud != null ? hud : (String) sOpenPage.invokeExact(player);
            if (page != null) sEmitJson.invokeExact(player, page, event, json);
        });
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
                // A page can send anything; a handler bug must not reach Snobblemon GUI's event bus.
                RaidLog.error("web page event '{}' failed: {}", channel, e.toString());
            } finally {
                RaidPerf.stop(section, started);
            }
        };
        invoke(() -> sOnMessage.invoke(channel, guarded));
    }

    private interface Call {
        void run() throws Throwable;
    }

    private static void invoke(Call call) {
        if (!bound) return;
        try {
            call.run();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Page call failed", t);
        }
    }
}
