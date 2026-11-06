package com.snobblemon.gems;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * LuckPerms (fabric-permissions-api) bridge. With LuckPerms present, a node decides if it is set for the player; if it is not set the
 * vanilla operator level is the fallback, so a fresh install works for ops and console without any LuckPerms setup.
 * {@code snobblegems.admin} grants every admin node at once.
 */
public final class Perms {
    private Perms() {}

    public static final String ADMIN = "snobblegems.admin";
    public static final String GIVE = "snobblegems.give";
    public static final String TAKE = "snobblegems.take";
    public static final String SET = "snobblegems.set";
    public static final String BALANCE = "snobblegems.balance";
    public static final String BALANCE_OTHERS = "snobblegems.balance.others";
    public static final String SHOP = "snobblegems.shop";
    public static final String EXCHANGE = "snobblegems.exchange";
    public static final String RELOAD = "snobblegems.reload";
    public static final String TICKET_GIVE = "snobblegems.ticket.give";
    public static final String TICKET_USE = "snobblegems.ticket.use";
    public static final String CLAIM = "snobblegems.claim";
    public static final String HISTORY = "snobblegems.history";
    public static final String WINDOW = "snobblegems.window";
    public static final String COMMUNITY = "snobblegems.community";
    /** Add / remove / re-price products and categories while the server runs. */
    public static final String SHOP_EDIT = "snobblegems.shop.edit";
    /** Set or clear offer timers and switch the timer feature on or off. */
    public static final String SHOP_TIMER = "snobblegems.shop.timer";
    public static final String ECONOMY_BYPASS = "snobblegems.shop.bypasslimits";

    private static Boolean present;

    public static boolean available() {
        if (present == null) present = FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0");
        return present;
    }

    /** Admin nodes: the specific node, or snobblegems.admin; fallback operator level 3 (console always passes). */
    public static boolean admin(CommandSourceStack src, String node) {
        return check(src, node, 3) || check(src, ADMIN, 3);
    }

    /** Player-facing nodes: allowed for everyone unless a group denies the node. */
    public static boolean player(CommandSourceStack src, String node) {
        return check(src, node, 0);
    }

    public static boolean check(CommandSourceStack src, String node, int defaultLevel) {
        if (available()) {
            try {
                return Impl.check(src, node, defaultLevel);
            } catch (Throwable ignored) {}
        }
        return src.hasPermission(defaultLevel);
    }

    public static boolean player(ServerPlayer p, String node, int defaultLevel) {
        if (available()) {
            try {
                return Impl.check(p, node, defaultLevel);
            } catch (Throwable ignored) {}
        }
        return p.hasPermissions(defaultLevel);
    }

    private static final class Impl {
        static boolean check(CommandSourceStack s, String node, int level) {
            return Permissions.check(s, node, level);
        }

        static boolean check(ServerPlayer p, String node, int level) {
            return Permissions.check(p, node, level);
        }
    }
}
