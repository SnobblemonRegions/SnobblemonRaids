package com.snobblemon.gems;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Gem Shop for native client screens (the Snobble Hub client mod). Same rules as {@link WebShop}: the client only names a product,
 * the server answers with the real price and a single-use token, and the purchase itself is {@link Shop#buy} with the quoted price, so
 * a changed price aborts. This class has no network code; whoever owns the transport (SnobbleHub) sends the JSON it returns.
 * Server thread only.
 */
final class ShopBridge {
    private record Quote(String token, String product, long price, long until) {}

    private final SnobbleGems mod;
    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();
    private final SecureRandom rnd = new SecureRandom();

    ShopBridge(SnobbleGems mod) {
        this.mod = mod;
    }

    void forget(UUID id) {
        quotes.remove(id);
    }

    /** Everything the player's shop screen shows: header, categories, products with this player's affordability and limits. */
    String state(ServerPlayer p) {
        Shop.Snapshot snap = mod.shop.snapshot(p);
        JsonObject head = new JsonObject();
        head.addProperty("currency", mod.cfg.currency.name);
        head.addProperty("singular", mod.cfg.currency.singular);
        head.addProperty("pointsName", mod.exchange.pointsName());
        head.addProperty("balance", mod.ledger.balance(p.getUUID()));
        head.addProperty("points", mod.exchange.points(p.getUUID(), p.getGameProfile().getName()));
        head.addProperty("earnedToday", mod.ledger.earnedToday(p.getUUID()));
        head.addProperty("title", mod.cfg.gui.shopTitle);
        head.addProperty("rev", mod.shop.revision);
        head.addProperty("now", System.currentTimeMillis());
        JsonArray cats = new JsonArray();
        for (Shop.CatView c : snap.cats()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", c.id());
            j.addProperty("name", c.name());
            j.addProperty("color", c.color());
            j.addProperty("count", c.count());
            j.addProperty("parent", c.parent() == null ? "" : c.parent());
            cats.add(j);
        }
        head.add("cats", cats);
        JsonArray prods = new JsonArray();
        for (Shop.ProdView v : snap.products()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", v.id());
            j.addProperty("name", v.name());
            j.addProperty("color", v.color());
            j.addProperty("price", v.price());
            j.addProperty("count", v.count());
            j.addProperty("icon", v.icon());
            j.addProperty("ok", v.ok());
            j.addProperty("reason", v.reason());
            j.addProperty("code", v.code());
            j.addProperty("limit", v.limit());
            j.addProperty("featured", v.featured());
            j.addProperty("pts", v.pts());
            j.addProperty("ends", v.ends());
            JsonArray d = new JsonArray();
            v.desc().forEach(d::add);
            j.add("desc", d);
            JsonArray c = new JsonArray();
            v.cats().forEach(c::add);
            j.add("cats", c);
            prods.add(j);
        }
        head.add("products", prods);
        return head.toString();
    }

    /** Step 1 of a purchase. Never charges anything. */
    String quote(ServerPlayer p, String productId) {
        Shop.Product pr = productId == null || productId.length() > 64 ? null : mod.shop.product(productId);
        JsonObject r = new JsonObject();
        r.addProperty("product", productId == null ? "" : productId);
        String problem = mod.shop.quoteProblem(p, pr);
        boolean pts = pr != null && pr.pointsGems();
        long bal = pts ? mod.exchange.points(p.getUUID(), p.getGameProfile().getName()) : mod.ledger.balance(p.getUUID());
        r.addProperty("pts", pts);
        r.addProperty("balance", bal);
        if (problem != null) {
            r.addProperty("ok", false);
            r.addProperty("code", problem);
            r.addProperty("message", switch (problem) {
                case "funds" -> "Not enough " + (pts ? mod.exchange.pointsName() : mod.cfg.currency.name) + ".";
                case "limit" -> "You have reached the purchase limit for that item.";
                case "permission" -> "You cannot buy that item.";
                default -> "That item is not available.";
            });
            quotes.remove(p.getUUID());
            return r.toString();
        }
        String token = Long.toString(Math.abs(rnd.nextLong()), 36);
        long until = System.currentTimeMillis() + mod.cfg.gui.confirmSeconds * 1000L;
        quotes.put(p.getUUID(), new Quote(token, pr.id, pr.price, until));
        mod.sound(p, mod.cfg.sounds.click, 0.8f, 1.4f);
        r.addProperty("ok", true);
        r.addProperty("token", token);
        r.addProperty("name", Txt.strip(pr.name));
        r.addProperty("color", Txt.firstColor(pr.name));
        r.addProperty("price", pr.price);
        r.addProperty("after", Math.max(0, bal - pr.price));
        r.addProperty("seconds", mod.cfg.gui.confirmSeconds);
        return r.toString();
    }

    /** Step 2: the token is consumed whatever happens, so one quote can never buy twice. */
    String confirm(ServerPlayer p, String token) {
        Quote q = quotes.remove(p.getUUID());
        JsonObject r = new JsonObject();
        if (q == null || token == null || !q.token().equals(token) || System.currentTimeMillis() > q.until()) {
            r.addProperty("ok", false);
            r.addProperty("code", "expired");
            r.addProperty("message", "That confirmation expired. Pick the item again.");
            r.addProperty("balance", mod.ledger.balance(p.getUUID()));
            return r.toString();
        }
        Shop.Product pr = mod.shop.product(q.product());
        Shop.BuyResult res = pr == null ? new Shop.BuyResult(false, "unavailable", "That item is not available.", false) : mod.shop.buy(p, pr, q.price());
        r.addProperty("ok", res.ok());
        r.addProperty("code", res.code());
        r.addProperty("message", res.message());
        r.addProperty("product", q.product());
        r.addProperty("price", q.price());
        r.addProperty("balance", mod.ledger.balance(p.getUUID()));
        return r.toString();
    }
}
