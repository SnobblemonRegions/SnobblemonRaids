package com.snobblemon.gems.api;

import java.util.UUID;

/**
 * Public API of SnobbleGemCurrency. Other server-side mods use this instead of keeping their own Gem numbers.
 * Every method goes through the same authoritative ledger as the commands, so balances, limits, audit rows and
 * {@link GemsEvents} always apply. Safe to call from the server thread; the other threads are serialised internally.
 *
 * <pre>{@code
 * GemsApi gems = GemsApi.get();            // null until the server has started, or when the mod is absent
 * long balance = gems.getBalance(uuid);
 * gems.deposit(uuid, 25, "my-mod:daily-quest", "quest-17-" + uuid);   // ref makes it idempotent
 * }</pre>
 */
public interface GemsApi {
    /** Stable currency identifier for registries, saves and other mods. */
    String CURRENCY_ID = "snobblegemcurrency:gems";
    /** The Placeholder API id (use it as %snobblegems:balance%). */
    String PLACEHOLDER_NAMESPACE = "snobblegems";

    /** Mod ids that report community messages themselves. While this is non-empty (and chat.source is AUTO) the built-in chat listener stays off. */
    java.util.Set<String> EXTERNAL_COMMUNITY_SOURCES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * A mod that decides itself what counts as a W / welcome message (SnobblemonCompanion) calls this once, before or after the server starts, then reports
     * eligible messages through {@link #communityReward}. Works without the Gem mod being loaded yet, because it is a plain static set.
     */
    static void registerCommunitySource(String modId) {
        EXTERNAL_COMMUNITY_SOURCES.add(modId);
    }

    /** The live API, or null when the server is not running yet. Never cache it across server restarts. */
    static GemsApi get() {
        return Holder.INSTANCE;
    }

    final class Holder {
        private Holder() {}
        /** Set by SnobbleGemCurrency itself. */
        public static volatile GemsApi INSTANCE;
    }

    /** Balance of any player, online or offline. 0 for a player who has never been seen. */
    long getBalance(UUID player);

    /** Adds Gems. {@code reason} is stored in the audit log ("my-mod:something"). {@code ref} (nullable) makes the call idempotent: the same ref is applied once. */
    GemsResult deposit(UUID player, long amount, String reason, String ref);

    /** Removes Gems; fails with {@link GemsResult.Status#INSUFFICIENT} instead of going below zero. Prefer the Gem Shop for spending; mods should only withdraw for their own listed purchases. */
    GemsResult withdraw(UUID player, long amount, String reason, String ref);

    /** Sets the balance (admin tooling). */
    GemsResult set(UUID player, long balance, String reason);

    /**
     * Reports that a player sent an eligible community message ("w" or "welcome"). SnobbleGemCurrency applies its own cooldown,
     * daily limit, duplicate and kind settings and pays the configured reward. {@code dedupeKey} should identify the message
     * (e.g. the wave id); the same key for the same player is paid once, so two mods reporting the same message never double pay.
     */
    GemsResult communityReward(UUID player, String kind, String dedupeKey);

    /** True when the built-in chat listener is turned off because another mod (SnobblemonCompanion) reports community messages. */
    boolean chatRewardsExternal();

    /** Opens a W / welcome window for the built-in chat listener (ignored when it is off). {@code source} is the player who earned the wave; they cannot be paid for it. */
    void openWindow(String kind, UUID source, String sourceName, int seconds);

    /** "1,250" */
    String format(long amount);

    /** Display name of the currency, e.g. "Gems". */
    String currencyName();

    // ---- Gem Shop for native client screens (added in 2.5.0). The first three return JSON text; the caller owns the transport. Server thread only.

    /** The player's whole shop view: balances, categories, products with this player's affordability and limits. */
    String shopState(net.minecraft.server.level.ServerPlayer player);

    /** Step 1 of a purchase: names a product, gets the real price and a single-use token (or the reason it cannot be bought). Charges nothing. */
    String shopQuote(net.minecraft.server.level.ServerPlayer player, String productId);

    /** Step 2: buys what the last quote named, at the quoted price. A missing, used or expired token buys nothing. */
    String shopConfirm(net.minecraft.server.level.ServerPlayer player, String token);

    /** Drops this player's open quote. */
    void shopCancel(net.minecraft.server.level.ServerPlayer player);
}
