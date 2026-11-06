package com.snobblemon.gems;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.snobblemon.gems.api.GemsEvents;
import net.minecraft.server.level.ServerPlayer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The Gem Shop as a WebGUI page. WebGUI is a client + server mod: the server sends the page to the player's game, the page runs in the
 * game's embedded browser and talks to this class over WebGUI's page-event channel ("snobblegems").
 *
 * The page is only a view. It never knows a price it could change: it asks for a QUOTE (server answers with the real price, balance after and a
 * single-use token) and then CONFIRMS the token; the purchase itself runs in {@link Shop#buy} exactly like the chest menu. Balances, prices,
 * limits and delivery are decided here, on the server thread.
 *
 * WebGUI's API is looked up by name at run time (it is built with a different Loom), so this mod neither compiles against WebGUI nor fails to
 * load on a server without it.
 */
public final class WebShop {
    public static final String CHANNEL = "snobblegems";

    private final SnobbleGems mod;
    private static MethodHandle openGui, emitToPage, clientKind;
    private boolean active;
    private String reason = "not started";

    private static final class Session {
        long lastSeen = System.currentTimeMillis();
        String lastSent;
        volatile boolean dirty = true;
        int seq;
        int iconRev = -1;
        final Set<String> iconsSent = new HashSet<>();
    }

    private record Quote(String token, String product, long price, long until) {}

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();
    private final Map<String, String> iconCache = new ConcurrentHashMap<>();
    private final SecureRandom rnd = new SecureRandom();
    private int iconCacheRev = -1;
    /** Global, so a page that reconnects never sees an old sequence number again. */
    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger();

    public WebShop(SnobbleGems mod) {
        this.mod = mod;
    }

    // ------------------------------------------------------------------ WebGUI link

    public void start() {
        try {
            Class<?> api = Class.forName("land.webgui.api.WebviewApi");
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            MethodHandle open = l.findStatic(api, "openGui", MethodType.methodType(void.class, ServerPlayer.class, String.class));
            MethodHandle emit = l.findStatic(api, "emitToPage", MethodType.methodType(void.class, ServerPlayer.class, String.class, String.class));
            MethodHandle on = l.findStatic(api, "onPageEvent", MethodType.methodType(void.class, String.class, BiConsumer.class));
            Class<?> net = Class.forName("land.webgui.WebviewNetworking");
            Class<?> kind = Class.forName("land.webgui.WebviewNetworking$ClientKind");
            MethodHandle ck = l.findStatic(net, "clientKind", MethodType.methodType(kind, ServerPlayer.class));
            BiConsumer<ServerPlayer, String> handler = (p, raw) -> {
                var srv = mod.server;
                if (srv != null) srv.execute(() -> handle(p, raw)); // page events arrive on a network thread; all shop logic runs on the server thread
            };
            on.invoke(CHANNEL, handler);
            openGui = open;
            emitToPage = emit;
            clientKind = ck;
            GemsEvents.BALANCE_CHANGED.register((id, name, oldB, newB, why) -> {
                Session s = sessions.get(id);
                if (s != null) s.dirty = true;
            });
            active = true;
            reason = "ok";
            SnobbleGems.LOG.info("[SnobbleGems] WebGUI found: /gemshop opens {} for players who have WebGUI (others get the chest menu)", pageUrl());
        } catch (Throwable ex) {
            active = false;
            reason = ex.toString();
            SnobbleGems.LOG.info("[SnobbleGems] WebGUI not available, /gemshop uses the chest menu ({})", ex.toString());
        }
    }

    public boolean active() { return active; }

    /** The catalog changed (admin command, timer, file edit): every open page gets the new state on the next tick. */
    public void markAllDirty() {
        for (Session s : sessions.values()) s.dirty = true;
    }

    public String status() {
        return active ? "WebGUI linked, " + sessions.size() + " page(s) open" : "WebGUI not available (" + reason + ")";
    }

    /** True when this player's game has a current WebGUI (so the page can open). */
    public boolean clientReady(ServerPlayer p) {
        if (!active || clientKind == null) return false;
        try {
            Object k = clientKind.invoke(p);
            return k != null && "CURRENT".equals(((Enum<?>) k).name());
        } catch (Throwable ex) {
            return false;
        }
    }

    /** Opens the page. @return false when the caller should use the chest menu instead. */
    public boolean open(ServerPlayer p) {
        String mode = mod.cfg.gui.mode == null ? "AUTO" : mod.cfg.gui.mode.toUpperCase();
        if (mode.equals("CHEST") || !active) return false;
        if (!clientReady(p)) return false;
        try {
            openGui.invoke(p, pageUrl());
            Session s = new Session();
            sessions.put(p.getUUID(), s);
            return true;
        } catch (Throwable ex) {
            SnobbleGems.LOG.warn("[SnobbleGems] could not open the shop page for {}: {}", p.getGameProfile().getName(), ex.toString());
            return false;
        }
    }

    /** WebGUI 2.0 refuses "webgui:/x" and wants "webgui://x"; older configs are fixed up here. */
    private String pageUrl() {
        String u = mod.cfg.gui.pageUrl == null ? "" : mod.cfg.gui.pageUrl.trim();
        if (u.startsWith("webgui:/") && !u.startsWith("webgui://")) u = "webgui://" + u.substring("webgui:/".length());
        return u;
    }

    public void leave(UUID id) {
        sessions.remove(id);
        quotes.remove(id);
        lastAction.remove(id);
    }

    private void emit(ServerPlayer p, String event, JsonObject o) throws Throwable {
        emitToPage.invoke(p, event, o.toString());
    }

    // ------------------------------------------------------------------ page -> server

    private void handle(ServerPlayer p, String raw) {
        String kind;
        JsonObject o;
        try {
            o = JsonParser.parseString(raw).getAsJsonObject();
            kind = o.has("kind") && o.get("kind").isJsonPrimitive() ? o.get("kind").getAsString() : "";
        } catch (RuntimeException ex) {
            return; // malformed input from a page is ignored
        }
        UUID id = p.getUUID();
        long now = System.currentTimeMillis();
        switch (kind) {
            case "hello", "refresh" -> {
                Session s = sessions.computeIfAbsent(id, k -> new Session());
                s.lastSeen = now;
                s.lastSent = null;
                s.dirty = true;
                s.iconRev = -1;
                s.iconsSent.clear();
                push(p, s);
            }
            case "ping" -> {
                Session s = sessions.computeIfAbsent(id, k -> new Session());
                s.lastSeen = now;
            }
            case "bye" -> leave(id);
            case "quote" -> {
                if (rateLimited(id, now)) { busy(p, "shop_quote"); return; }
                quote(p, str(o, "product", 64));
            }
            case "confirm" -> {
                if (rateLimited(id, now)) { busy(p, "shop_result"); return; }
                confirm(p, str(o, "token", 32));
            }
            case "cancel" -> quotes.remove(id);
            default -> { }
        }
    }

    private static String str(JsonObject o, String k, int max) {
        if (!o.has(k) || !o.get(k).isJsonPrimitive()) return "";
        String s = o.get(k).getAsString();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** A page that clicks faster than a person can gets an answer anyway, so it never waits for one that will not come. */
    private void busy(ServerPlayer p, String event) {
        JsonObject r = new JsonObject();
        r.addProperty("ok", false);
        r.addProperty("code", "busy");
        r.addProperty("message", "One moment...");
        r.addProperty("balance", mod.ledger.balance(p.getUUID()));
        send(p, event, r);
    }

    private boolean rateLimited(UUID id, long now) {
        Long last = lastAction.put(id, now);
        return last != null && now - last < 200;
    }

    private void quote(ServerPlayer p, String productId) {
        Shop.Product pr = mod.shop.product(productId);
        JsonObject r = new JsonObject();
        r.addProperty("product", productId);
        String problem = mod.shop.quoteProblem(p, pr);
        boolean pts = pr != null && pr.pointsGems();
        long bal = pts ? mod.exchange.points(p.getUUID(), p.getGameProfile().getName()) : mod.ledger.balance(p.getUUID());
        if (pts) r.addProperty("pts", true);
        if (problem != null) {
            r.addProperty("ok", false);
            r.addProperty("code", problem);
            r.addProperty("message", switch (problem) {
                case "funds" -> "Not enough " + (pts ? mod.exchange.pointsName() : mod.cfg.currency.name) + ".";
                case "limit" -> "You have reached the purchase limit for that item.";
                case "permission" -> "You cannot buy that item.";
                default -> "That item is not available.";
            });
            r.addProperty("balance", bal);
            send(p, "shop_quote", r);
            return;
        }
        String token = Long.toString(Math.abs(rnd.nextLong()), 36);
        long until = System.currentTimeMillis() + mod.cfg.gui.confirmSeconds * 1000L;
        quotes.put(p.getUUID(), new Quote(token, productId, pr.price, until));
        mod.sound(p, mod.cfg.sounds.click, 0.8f, 1.4f);
        r.addProperty("ok", true);
        r.addProperty("token", token);
        r.addProperty("name", Txt.strip(pr.name));
        r.addProperty("color", Txt.firstColor(pr.name));
        r.addProperty("price", pr.price);
        r.addProperty("balance", bal);
        r.addProperty("after", Math.max(0, bal - pr.price));
        r.addProperty("seconds", mod.cfg.gui.confirmSeconds);
        send(p, "shop_quote", r);
    }

    private void confirm(ServerPlayer p, String token) {
        Quote q = quotes.remove(p.getUUID());
        JsonObject r = new JsonObject();
        if (q == null || !q.token().equals(token) || System.currentTimeMillis() > q.until()) {
            r.addProperty("ok", false);
            r.addProperty("code", "expired");
            r.addProperty("message", "That confirmation expired. Pick the item again.");
            r.addProperty("balance", mod.ledger.balance(p.getUUID()));
            send(p, "shop_result", r);
            return;
        }
        Shop.Product pr = mod.shop.product(q.product());
        Shop.BuyResult res = pr == null ? new Shop.BuyResult(false, "unavailable", "That item is not available.", false) : mod.shop.buy(p, pr, q.price());
        r.addProperty("ok", res.ok());
        r.addProperty("code", res.code());
        r.addProperty("message", res.message());
        r.addProperty("product", q.product());
        r.addProperty("balance", mod.ledger.balance(p.getUUID()));
        r.addProperty("price", q.price());
        send(p, "shop_result", r);
        Session s = sessions.get(p.getUUID());
        if (s != null) { s.dirty = true; push(p, s); }
    }

    private void send(ServerPlayer p, String event, JsonObject o) {
        try {
            emit(p, event, o);
        } catch (Throwable ex) {
            SnobbleGems.LOG.warn("[SnobbleGems] could not send {} to {}: {}", event, p.getGameProfile().getName(), ex.toString());
            leave(p.getUUID());
        }
    }

    // ------------------------------------------------------------------ server -> page

    /** Called every server tick: expires silent pages, pushes changed state to open ones (at most every half second). */
    public void tick(long tick) {
        if (!active || sessions.isEmpty() || tick % 10 != 0) return;
        long now = System.currentTimeMillis();
        long timeout = mod.cfg.gui.pageTimeoutSeconds * 1000L;
        for (Map.Entry<UUID, Session> e : new ArrayList<>(sessions.entrySet())) {
            Session s = e.getValue();
            if (now - s.lastSeen > timeout) { leave(e.getKey()); continue; }
            ServerPlayer p = mod.server.getPlayerList().getPlayer(e.getKey());
            if (p == null) { leave(e.getKey()); continue; }
            Quote q = quotes.get(e.getKey());
            if (q != null && now > q.until()) quotes.remove(e.getKey());
            if (s.dirty || tick % 40 == 0) push(p, s);
        }
    }

    private void push(ServerPlayer p, Session s) {
        try {
            s.dirty = false;
            Shop.Snapshot snap = mod.shop.snapshot(p);
            long bal = mod.ledger.balance(p.getUUID());
            JsonObject head = new JsonObject();
            head.addProperty("v", 2);
            head.addProperty("player", p.getGameProfile().getName());
            head.addProperty("currency", mod.cfg.currency.name);
            head.addProperty("pointsName", mod.exchange.pointsName());
            head.addProperty("singular", mod.cfg.currency.singular);
            head.addProperty("balance", bal);
            head.addProperty("earnedToday", mod.ledger.earnedToday(p.getUUID()));
            head.addProperty("rev", mod.shop.revision);
            head.addProperty("title", mod.cfg.gui.shopTitle);
            head.addProperty("anim", mod.cfg.gui.animations.toUpperCase());
            head.addProperty("timers", mod.shop.timersOn());
            JsonArray cats = new JsonArray();
            for (Shop.CatView c : snap.cats()) {
                JsonObject j = new JsonObject();
                j.addProperty("id", c.id()); j.addProperty("name", c.name()); j.addProperty("color", c.color()); j.addProperty("count", c.count());
                if (c.parent() != null && !c.parent().isEmpty()) j.addProperty("parent", c.parent());
                JsonArray d = new JsonArray();
                c.desc().forEach(d::add);
                j.add("desc", d);
                cats.add(j);
            }
            head.add("cats", cats);
            List<JsonObject> prods = new ArrayList<>();
            for (Shop.ProdView v : snap.products()) {
                JsonObject j = new JsonObject();
                j.addProperty("id", v.id()); j.addProperty("name", v.name()); j.addProperty("color", v.color());
                j.addProperty("price", v.price()); j.addProperty("count", v.count()); j.addProperty("icon", v.icon());
                j.addProperty("ok", v.ok()); j.addProperty("reason", v.reason()); j.addProperty("code", v.code());
                j.addProperty("limit", v.limit()); j.addProperty("featured", v.featured());
                if (v.pts()) j.addProperty("pts", true);
                if (v.ends() > 0) j.addProperty("ends", v.ends());
                JsonArray d = new JsonArray(); v.desc().forEach(d::add); j.add("desc", d);
                JsonArray c = new JsonArray(); v.cats().forEach(c::add); j.add("cats", c);
                prods.add(j);
            }
            // fingerprint of everything the page shows (change-only push)
            String fp = head + "|" + prods;
            if (fp.equals(s.lastSent)) return;
            s.lastSent = fp;
            s.seq = SEQ.incrementAndGet();
            head.addProperty("now", System.currentTimeMillis()); // server clock, so countdowns are right even if the player's clock is off (not part of the fingerprint)
            // WebGUI allows 32 KB per emit: split the product list so each message stays well under that
            List<List<JsonObject>> chunks = new ArrayList<>();
            List<JsonObject> cur = new ArrayList<>();
            int size = head.toString().length();
            for (JsonObject j : prods) {
                int len = j.toString().length() + 1;
                if (!cur.isEmpty() && size + len > 24000) { chunks.add(cur); cur = new ArrayList<>(); size = 200; }
                cur.add(j);
                size += len;
            }
            chunks.add(cur);
            for (int i = 0; i < chunks.size(); i++) {
                JsonObject m = i == 0 ? head.deepCopy() : new JsonObject();
                m.addProperty("seq", s.seq);
                m.addProperty("chunk", i);
                m.addProperty("chunks", chunks.size());
                JsonArray pa = new JsonArray();
                chunks.get(i).forEach(pa::add);
                m.add("products", pa);
                emit(p, "shop_state", m);
            }
            sendIcons(p, s, snap);
        } catch (Throwable ex) {
            SnobbleGems.LOG.warn("[SnobbleGems] could not update the shop page for {}: {}", p.getGameProfile().getName(), ex.toString());
            leave(p.getUUID());
        }
    }

    private void sendIcons(ServerPlayer p, Session s, Shop.Snapshot snap) throws Throwable {
        if (s.iconRev != mod.shop.revision) { s.iconsSent.clear(); s.iconRev = mod.shop.revision; }
        if (iconCacheRev != mod.shop.revision) { iconCache.clear(); iconCacheRev = mod.shop.revision; }
        for (Shop.ProdView v : snap.products()) {
            String key = v.icon();
            if (key == null || key.startsWith("snobblegems:") || !s.iconsSent.add(key)) continue;
            String data = iconCache.computeIfAbsent(key, this::loadIcon);
            if (data.isEmpty()) {                                  // not drawn yet (e.g. pictures still being generated): ask again on the next push
                iconCache.remove(key);
                if (key.startsWith("ext:")) s.iconsSent.remove(key);
                continue;
            }
            JsonObject m = new JsonObject();
            m.addProperty("key", key);
            m.addProperty("data", data);
            emit(p, "shop_icon", m);
        }
    }

    /** config/snobblegemcurrency/icons/<namespace>/<path>.png  ->  data URI, or "" when there is no such file. */
    private String loadIcon(String key) {
        try {
            if (key.startsWith("ext:")) {                      // a picture supplied by a shop provider (e.g. the cosmetics thumbnails)
                String rest = key.substring(4);
                int k = rest.indexOf(':');
                if (k < 0) return "";
                for (var sp : com.snobblemon.gems.api.ShopProvider.REGISTERED) {
                    if (!sp.id().equals(rest.substring(0, k))) continue;
                    byte[] png = sp.iconPng(rest.substring(k + 1));
                    return png == null || png.length > 60_000 ? "" : "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
                }
                return "";
            }
            int c = key.indexOf(':');
            String ns = c < 0 ? "minecraft" : key.substring(0, c), path = c < 0 ? key : key.substring(c + 1);
            if (!ns.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+") || path.contains("..")) return "";
            Path f = Cfg.dir().resolve("icons").resolve(ns).resolve(path + ".png");
            if (!Files.isRegularFile(f) || Files.size(f) > 20_000) return "";
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(f));
        } catch (Exception e) {
            return "";
        }
    }
}
