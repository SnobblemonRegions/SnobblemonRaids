package com.snobblemon.gems.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lets another mod list its own products in the Gem Shop without writing to shop.json: the cosmetics platform registers one
 * category ("Sneelz' Cosmetics") with sub-categories (Hats, Armour...). Gems still does everything that matters: it checks the balance,
 * takes the Gems, records the purchase and refunds if {@link #deliver} throws. A provider only says what is for sale, whether this
 * player may buy it right now, and hands the item over.
 *
 * <p>Register once at server start: {@code ShopProvider.register(provider)}. Everything is called on the server thread.
 */
public interface ShopProvider {
    /** A category. {@code parent} is "" for a top-level one; a sub-category names its parent's id. */
    record Category(String id, String parent, String name, String icon, List<String> description) {}

    /** A product. {@code icon} is an item id, or "ext:&lt;anything&gt;" when {@link #iconPng} can supply a picture for it. */
    record Product(String id, String category, String name, List<String> description, long price, String icon) {}

    /** Short unique name, used to keep the product ids of different providers apart. */
    String id();

    /** Changes whenever {@link #categories()} or {@link #products()} changed: the shop pages refresh and reload icons. */
    long revision();

    List<Category> categories();

    List<Product> products();

    /** null when this player can buy it now, otherwise the short reason shown on the card (for example "Already owned"). */
    String why(ServerPlayer player, String productId, long price);

    /** Called after the Gems were taken. Throw to make Gems refund the player. */
    void deliver(ServerPlayer player, String productId);

    /** PNG bytes (max 60 KB) for an "ext:" icon, or null. */
    default byte[] iconPng(String key) {
        return null;
    }

    List<ShopProvider> REGISTERED = new CopyOnWriteArrayList<>();

    static void register(ShopProvider p) {
        REGISTERED.removeIf(x -> x.id().equals(p.id()));
        REGISTERED.add(p);
    }
}
