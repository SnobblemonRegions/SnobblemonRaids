package net.snobblemon.hub;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * The Gem Shop (SnobbleGemCurrency 2.5.0 or newer) reached through reflection, so the hub still loads, with no shop tab,
 * on a server that does not have it. Everything that matters stays in the Gems mod: prices, limits, balances, the single-use
 * quote token and the purchase itself. This class only carries text in and out.
 */
public final class GemShop {
    private GemShop() {}

    private static Object api() {
        try {
            return Class.forName("com.snobblemon.gems.api.GemsApi").getMethod("get").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Looked up on the public interface: the implementation class is package-private and cannot be called through reflection. */
    private static Method method(Object api, String name, Class<?>... args) throws Exception {
        return Class.forName("com.snobblemon.gems.api.GemsApi").getMethod(name, args);
    }

    /** True when the Gems mod is running and is new enough to offer the shop methods. */
    public static boolean available() {
        Object a = api();
        if (a == null) return false;
        try {
            method(a, "shopState", ServerPlayer.class);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String call(String name, ServerPlayer p, String arg, String fallback) {
        try {
            Object a = api();
            if (a == null) return fallback;
            Object r = arg == null ? method(a, name, ServerPlayer.class).invoke(a, p) : method(a, name, ServerPlayer.class, String.class).invoke(a, p, arg);
            return r == null ? fallback : r.toString();
        } catch (Throwable t) {
            SnobbleHub.LOGGER.warn("Gem Shop call {} failed: {}", name, t.toString());
            return fallback;
        }
    }

    private static String failure(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("code", "unavailable");
        o.addProperty("message", message);
        return o.toString();
    }

    public static JsonObject state(ServerPlayer p) {
        try {
            return JsonParser.parseString(call("shopState", p, null, "{}")).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    public static String quote(ServerPlayer p, String productId) {
        return call("shopQuote", p, productId, failure("The Gem Shop is unavailable."));
    }

    public static String confirm(ServerPlayer p, String token) {
        return call("shopConfirm", p, token, failure("The Gem Shop is unavailable."));
    }

    public static void cancel(ServerPlayer p) {
        try {
            Object a = api();
            if (a != null) method(a, "shopCancel", ServerPlayer.class).invoke(a, p);
        } catch (Throwable ignored) { }
    }
}
