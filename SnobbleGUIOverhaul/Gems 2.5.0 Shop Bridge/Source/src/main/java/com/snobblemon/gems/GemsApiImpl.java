package com.snobblemon.gems;

import com.snobblemon.gems.api.GemsApi;
import com.snobblemon.gems.api.GemsResult;

import java.util.UUID;

/** The live {@link GemsApi}. Thin on purpose: every call lands in {@link Ledger} or {@link Community}. */
final class GemsApiImpl implements GemsApi {
    private final SnobbleGems mod;

    GemsApiImpl(SnobbleGems mod) {
        this.mod = mod;
    }

    @Override
    public long getBalance(UUID player) {
        return mod.ledger.balance(player);
    }

    @Override
    public GemsResult deposit(UUID player, long amount, String reason, String ref) {
        if (amount <= 0) return new GemsResult(GemsResult.Status.INVALID, 0, mod.ledger.balance(player));
        return mod.ledger.adjust(player, null, amount, clean(reason), "api", ref == null ? null : "api:" + ref);
    }

    @Override
    public GemsResult withdraw(UUID player, long amount, String reason, String ref) {
        if (amount <= 0) return new GemsResult(GemsResult.Status.INVALID, 0, mod.ledger.balance(player));
        return mod.ledger.adjust(player, null, -amount, clean(reason), "api", ref == null ? null : "api:" + ref);
    }

    @Override
    public GemsResult set(UUID player, long balance, String reason) {
        return mod.ledger.setBalance(player, null, balance, clean(reason), "api");
    }

    @Override
    public GemsResult communityReward(UUID player, String kind, String dedupeKey) {
        if (kind == null) return new GemsResult(GemsResult.Status.INVALID, 0, mod.ledger.balance(player));
        return mod.community.reward(player, mod.ledger.nameOf(player), kind.toLowerCase(java.util.Locale.ROOT), dedupeKey);
    }

    @Override
    public boolean chatRewardsExternal() {
        return mod.community.external();
    }

    @Override
    public void openWindow(String kind, UUID source, String sourceName, int seconds) {
        mod.community.openWindow(kind, source, sourceName, seconds);
    }

    @Override
    public String format(long amount) {
        return Txt.num(amount);
    }

    @Override
    public String currencyName() {
        return mod.cfg.currency.name;
    }

    private static String clean(String reason) {
        return reason == null || reason.isBlank() ? "api" : reason.length() > 120 ? reason.substring(0, 120) : reason;
    }

    @Override
    public String shopState(net.minecraft.server.level.ServerPlayer player) {
        return mod.bridge.state(player);
    }

    @Override
    public String shopQuote(net.minecraft.server.level.ServerPlayer player, String productId) {
        return mod.bridge.quote(player, productId);
    }

    @Override
    public String shopConfirm(net.minecraft.server.level.ServerPlayer player, String token) {
        return mod.bridge.confirm(player, token);
    }

    @Override
    public void shopCancel(net.minecraft.server.level.ServerPlayer player) {
        mod.bridge.forget(player.getUUID());
    }
}
