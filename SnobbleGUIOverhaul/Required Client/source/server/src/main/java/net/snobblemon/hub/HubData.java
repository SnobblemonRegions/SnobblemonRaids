package net.snobblemon.hub;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * What the Trainer Card shows, read from the real systems each time the card opens: LuckPerms groups for rank and
 * region, PlaceholderAPI tokens (Raid Points, Gems, Community Points) and Impactor (CobbleDollars).
 */
public final class HubData {
    public record Profile(String name, String rank, String regionName, String accent, String raid, String dollars, String gems, String community) {}

    private HubData() {}

    public static Profile profile(ServerPlayer p) {
        HubConfig.General g = HubConfig.general;
        String rank = g.default_rank;
        for (HubConfig.Rank r : g.ranks) {
            if (inAny(p, r.groups)) { rank = r.name; break; }
        }
        String region = "NONE", accent = "dusk";
        for (HubConfig.Region r : g.regions) {
            if (Permissions.check(p, "group." + r.group, false)) { region = r.name; accent = r.accent; break; }
        }
        return new Profile(p.getGameProfile().getName(), rank, region, accent,
                source(p, g.sources.get("raid_points")), source(p, g.sources.get("cobbledollars")),
                source(p, g.sources.get("gems")), source(p, g.sources.get("community_points")));
    }

    /** The region accent colour name (root, tide, dusk) for menu frames. */
    public static String accent(ServerPlayer p) {
        for (HubConfig.Region r : HubConfig.general.regions) {
            if (Permissions.check(p, "group." + r.group, false)) return r.accent;
        }
        return "dusk";
    }

    private static boolean inAny(ServerPlayer p, java.util.List<String> groups) {
        for (String g : groups) if (Permissions.check(p, "group." + g, false)) return true;
        return false;
    }

    // ---------------------------------------------------------------- number sources
    private static String source(ServerPlayer p, String src) {
        if (src == null || src.isBlank()) return "-";
        String out;
        try {
            out = src.startsWith("impactor:") ? impactor(p.getUUID(), src) : Ph.parse(p, src);
        } catch (Throwable t) {
            out = "-";
        }
        out = HubFont.clean(out == null ? "" : out).trim();
        return out.isEmpty() ? "-" : out;
    }

    /** CobbleDollars live in Impactor's economy. Reflection keeps Impactor an optional dependency. */
    private static String impactor(UUID id, String src) throws Exception {
        Class<?> es = Class.forName("net.impactdev.impactor.api.economy.EconomyService");
        Object svc = es.getMethod("instance").invoke(null);
        Object currencies = es.getMethod("currencies").invoke(svc);
        Object currency = null;
        String key = src.substring("impactor:".length());
        for (Method m : currencies.getClass().getMethods()) {
            if (m.getName().equals("currency") && m.getParameterCount() == 1) {
                try {
                    Class<?> k = Class.forName("net.kyori.adventure.key.Key");
                    Object keyObj = k.getMethod("key", String.class, String.class).invoke(null, "impactor", key);
                    Object r = m.invoke(currencies, keyObj);
                    currency = r instanceof java.util.Optional<?> o ? o.orElse(null) : r;
                    if (currency != null) break;
                } catch (Throwable ignored) { }
            }
        }
        if (currency == null) {
            for (Method m : currencies.getClass().getMethods()) {
                if (m.getName().equals("primary") && m.getParameterCount() == 0) { currency = m.invoke(currencies); break; }
            }
        }
        if (currency == null) return "-";
        Method acc = null;
        for (Method m : es.getMethods()) {
            if (m.getName().equals("account") && m.getParameterCount() == 2 && m.getParameterTypes()[1] == UUID.class) { acc = m; break; }
        }
        if (acc == null) return "-";
        Object account = ((CompletableFuture<?>) acc.invoke(svc, currency, id)).get(250, TimeUnit.MILLISECONDS);
        Object bal = account.getClass().getMethod("balance").invoke(account);
        if (bal instanceof BigDecimal d) return java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(d.longValue());
        return String.valueOf(bal);
    }

    /** Kept in its own class so a missing PlaceholderAPI only disables these lines. */
    private static final class Ph {
        static String parse(ServerPlayer p, String token) {
            var ctx = eu.pb4.placeholders.api.PlaceholderContext.of(p);
            var res = eu.pb4.placeholders.api.Placeholders.parseText(net.minecraft.network.chat.Component.literal(token), ctx);
            String s = res.getString();
            return s.equals(token) ? "-" : s;
        }
    }
}
