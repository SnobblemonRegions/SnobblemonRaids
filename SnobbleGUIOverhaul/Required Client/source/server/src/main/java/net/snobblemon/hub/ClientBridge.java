package net.snobblemon.hub;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.snobblemon.cosmetics.core.Shop;
import net.snobblemon.cosmetics.gui.CosmeticsApi;
import net.snobblemon.hub.gui.HubGui;
import net.snobblemon.hubnet.HubNet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to players who have the Snobble Hub client mod (owo-ui screens). Everything here is authoritative: the client
 * sends ids, this class checks them with the same code the chest menus use and answers with fresh data. Players without
 * the client mod never reach this class; they get the chest menu.
 */
public final class ClientBridge {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private ClientBridge() {}

    public static void init() {
        HubNet.registerTypes();
        ServerPlayNetworking.registerGlobalReceiver(HubNet.Request.TYPE, (payload, ctx) -> handle(ctx.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> LAST.remove(h.getPlayer().getUUID()));
    }

    /** True when this player's game has the client mod (it announced the data channel). */
    public static boolean supports(ServerPlayer p) {
        return ServerPlayNetworking.canSend(p, HubNet.Data.TYPE);
    }

    /** The pages the client mod draws itself. Everything else still opens the chest menu. */
    public static String pageOf(HubGui.Mode m) {
        return switch (m) {
            case COSMETICS, HUB -> "cos";
            case CARD -> "card";
            case WIKI -> "wiki";
            case WARPS, GYMS -> "warp";
            case UPDATES -> "upd";
            case SHOP -> GemShop.available() ? "shop" : null;
            default -> null;
        };
    }

    public static boolean open(ServerPlayer p, HubGui.Mode mode) {
        String page = pageOf(mode);
        if (page == null || !supports(p)) return false;
        if (mode == HubGui.Mode.WIKI) { openWiki(p); return true; }
        send(p, page, true);
        return true;
    }

    private static void send(ServerPlayer p, String page, boolean open) {
        String json = switch (page) {
            case "cos" -> cosmetics(p);
            case "card" -> card(p);
            case "wiki" -> wiki(p);
            case "warp" -> warp(p);
            case "upd" -> updates(p);
            case "shop" -> shop(p);
            default -> "{}";
        };
        ServerPlayNetworking.send(p, new HubNet.Data(page, open, json));
    }

    private static void toast(ServerPlayer p, String text, boolean ok) {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        o.addProperty("ok", ok);
        ServerPlayNetworking.send(p, new HubNet.Data("toast", false, o.toString()));
    }

    // -------------------------------------------------------------------------------- requests

    private static void handle(ServerPlayer p, HubNet.Request r) {
        long now = System.currentTimeMillis();
        Long last = LAST.put(p.getUUID(), now);
        if (last != null && now - last < 60) return;   // flood guard
        String id = r.arg();
        switch (r.action()) {
            case "page" -> {
                HubGui.Mode m = switch (id) {
                    case "inv" -> HubGui.Mode.HUB;
                    case "wiki" -> HubGui.Mode.WIKI;
                    case "cos" -> HubGui.Mode.COSMETICS;
                    case "card" -> HubGui.Mode.CARD;
                    case "warp" -> HubGui.Mode.WARPS;
                    case "upd" -> HubGui.Mode.UPDATES;
                    case "shop" -> HubGui.Mode.SHOP;
                    default -> null;
                };
                if (m == null) return;
                if (m == HubGui.Mode.WIKI) { openWiki(p); return; }
                if (!open(p, m)) HubGui.open(p, m);   // not a client page yet: the chest menu
            }
            case "session" -> {
                if ("start".equals(id)) CosmeticsApi.beginSession(p); else CosmeticsApi.endSession(p);
            }
            case "try" -> { if (!CosmeticsApi.tryOn(p, id)) toast(p, "That cannot be previewed.", false); }
            case "reset" -> CosmeticsApi.clearPreview(p, id);
            case "equip" -> { toast(p, CosmeticsApi.equip(p, id) ? "Equipped." : "You do not own that.", CosmeticsApi.item(p, id) != null && CosmeticsApi.item(p, id).owned()); send(p, "cos", false); }
            case "unequip" -> { CosmeticsApi.unequip(p, id); send(p, "cos", false); }
            case "buy" -> {
                Shop.Result res = CosmeticsApi.buy(p, id);
                toast(p, switch (res) {
                    case OK -> "Purchased!";
                    case ALREADY_OWNED -> "You already own that.";
                    case NOT_FOR_SALE -> "That is not for sale.";
                    case INSUFFICIENT -> "Not enough " + CosmeticsApi.currencyName() + ".";
                    case BUSY -> "Please wait a moment.";
                    case UNAVAILABLE -> "The shop is unavailable.";
                    default -> "Something went wrong.";
                }, res == Shop.Result.OK);
                send(p, "cos", false);
            }
            case "applySkin" -> { var sk = CosmeticsApi.applySkin(p, id); toast(p, sk.text(), sk.ok()); send(p, "cos", false); }
            case "removeSkin" -> { var sk = CosmeticsApi.removeSkin(p, id); toast(p, sk.text(), sk.ok()); send(p, "cos", false); }
            case "openCard" -> CosmeticsApi.openCard(p, id);
            case "wikiRun" -> {
                var list = HubConfig.wiki;
                int i = parse(id);
                if (i >= 0 && i < list.size() && list.get(i).command != null && !list.get(i).command.isBlank()) { closeScreen(p); run(p, list.get(i).command); }
            }
            case "wikiFull" -> openWiki(p);
            case "warpGo" -> {
                for (HubConfig.Destination d : HubConfig.warps) {
                    if (d.id.equals(id) && d.enabled && d.command != null && !d.command.isBlank()) { closeScreen(p); Teleports.request(p, d.label, d.command); return; }
                }
            }
            case "gymGo" -> {
                int i = parse(id);
                if (i >= 0 && i < HubConfig.gyms.size()) {
                    HubConfig.Gym g = HubConfig.gyms.get(i);
                    if (gymUsable(p, g)) { closeScreen(p); Teleports.request(p, g.name + " Gym", g.command); }
                }
            }
            case "updRead" -> {
                var l = sortedUpdates();
                int i = parse(id);
                if (i >= 0 && i < l.size()) HubConfig.markRead(p.getUUID(), l.get(i));
            }
            case "shopQuote" -> ServerPlayNetworking.send(p, new HubNet.Data("shopQuote", false, GemShop.quote(p, id)));
            case "shopConfirm" -> {
                ServerPlayNetworking.send(p, new HubNet.Data("shopResult", false, GemShop.confirm(p, id)));
                send(p, "shop", false);   // fresh balances, limits and ownership after the purchase
            }
            case "shopCancel" -> GemShop.cancel(p);
            default -> { }
        }
    }

    // -------------------------------------------------------------------------------- page data

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    private static void run(ServerPlayer p, String cmd) {
        p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), cmd);
    }

    /** The Wiki tab is a command: close the hub screen and run the configured wiki command (the SnobbleWiki WebGUI) as the player. */
    private static void openWiki(ServerPlayer p) {
        String cmd = HubConfig.general.wiki_command == null ? "" : HubConfig.general.wiki_command.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isBlank() || cmd.equals("hub") || cmd.startsWith("hub wiki")) return;   // would loop back into this screen
        closeScreen(p);
        run(p, cmd);
    }

    private static void closeScreen(ServerPlayer p) {
        ServerPlayNetworking.send(p, new HubNet.Data("close", false, "{}"));
    }

    private static boolean gymLocked(ServerPlayer p, HubConfig.Gym g) {
        return g.permission != null && !g.permission.isBlank() && !me.lucko.fabric.api.permissions.v0.Permissions.check(p, g.permission, false);
    }

    private static boolean gymUsable(ServerPlayer p, HubConfig.Gym g) {
        return g.enabled && !gymLocked(p, g) && g.command != null && !g.command.isBlank();
    }

    private static java.util.List<HubConfig.Update> sortedUpdates() {
        java.util.List<HubConfig.Update> l = new java.util.ArrayList<>(HubConfig.updates);
        l.sort(java.util.Comparator.comparing((HubConfig.Update u) -> u.date == null ? "" : u.date).reversed());
        return l;
    }

    private static JsonArray strings(java.util.List<String> l) {
        JsonArray a = new JsonArray();
        if (l != null) for (String s : l) a.add(s);
        return a;
    }

    private static String wiki(ServerPlayer p) {
        JsonObject root = new JsonObject();
        root.addProperty("region", HubData.accent(p));
        JsonArray arr = new JsonArray();
        for (HubConfig.WikiEntry e : HubConfig.wiki) {
            JsonObject o = new JsonObject();
            o.addProperty("title", e.title);
            o.addProperty("cmd", e.command != null && !e.command.isBlank());
            o.add("pages", strings(e.pages));
            arr.add(o);
        }
        root.add("entries", arr);
        return GSON.toJson(root);
    }

    private static String warp(ServerPlayer p) {
        JsonObject root = new JsonObject();
        root.addProperty("region", HubData.accent(p));
        JsonArray dests = new JsonArray();
        for (HubConfig.Destination d : HubConfig.warps) {
            boolean gyms = "gyms".equals(d.id);
            JsonObject o = new JsonObject();
            o.addProperty("id", d.id);
            o.addProperty("label", d.label);
            o.addProperty("desc", d.description);
            o.addProperty("gyms", gyms);
            o.addProperty("usable", d.enabled && (gyms || (d.command != null && !d.command.isBlank())));
            dests.add(o);
        }
        root.add("dests", dests);
        JsonArray gyms = new JsonArray();
        for (int i = 0; i < HubConfig.gyms.size(); i++) {
            HubConfig.Gym g = HubConfig.gyms.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("i", i);
            o.addProperty("name", g.name);
            o.addProperty("type", g.type);
            o.addProperty("locked", gymLocked(p, g));
            o.addProperty("usable", gymUsable(p, g));
            gyms.add(o);
        }
        root.add("gyms", gyms);
        return GSON.toJson(root);
    }

    private static String updates(ServerPlayer p) {
        JsonObject root = new JsonObject();
        root.addProperty("region", HubData.accent(p));
        JsonArray arr = new JsonArray();
        var l = sortedUpdates();
        for (int i = 0; i < l.size(); i++) {
            HubConfig.Update u = l.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("i", i);
            o.addProperty("title", u.title);
            o.addProperty("date", u.date);
            o.addProperty("version", u.version);
            o.addProperty("fresh", !HubConfig.isRead(p.getUUID(), u));
            o.add("added", strings(u.added));
            o.add("changed", strings(u.changed));
            o.add("fixed", strings(u.fixed));
            arr.add(o);
        }
        root.add("updates", arr);
        return GSON.toJson(root);
    }

    private static final String POKE_BALL = "{count:1,id:\"cobblemon:poke_ball\"}";

    private static String stack(ServerPlayer p, ItemStack s) {
        try {
            if (s.is(net.minecraft.world.item.Items.EGG)) return POKE_BALL;   // Pokemon skins show a Cobblemon Poke Ball, not an egg
            ItemStack client = PolymerItemUtils.getPolymerItemStack(s, xyz.nucleoid.packettweaker.PacketContext.of(p));   // what a client without the server mods sees
            return client.save(p.registryAccess()).toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String cosmetics(ServerPlayer p) {
        JsonObject root = new JsonObject();
        root.addProperty("currency", CosmeticsApi.currencyName());
        root.addProperty("shop", CosmeticsApi.shopAvailable());
        root.addProperty("region", HubData.accent(p));
        JsonArray cats = new JsonArray();
        for (CosmeticsApi.Category c : CosmeticsApi.categories(p)) {
            JsonObject co = new JsonObject();
            co.addProperty("id", c.id());
            co.addProperty("name", c.name());
            co.addProperty("icon", stack(p, c.icon()));
            JsonArray items = new JsonArray();
            for (CosmeticsApi.Entry e : CosmeticsApi.items(p, c.id())) {
                JsonObject io = new JsonObject();
                io.addProperty("id", e.id());
                io.addProperty("name", e.name().getString());
                JsonArray desc = new JsonArray();
                for (var l : e.lore()) desc.add(l.getString());
                io.add("desc", desc);
                io.addProperty("rarity", e.rarity());
                io.addProperty("ri", e.rarityIndex());
                io.addProperty("owned", e.owned());
                io.addProperty("worn", e.equipped());
                io.addProperty("price", e.price());
                io.addProperty("equippable", e.equippable());
                var def = net.snobblemon.cosmetics.core.CosmeticRegistry.get(e.id());
                io.addProperty("back", def != null && def.type == net.snobblemon.cosmetics.core.CosmeticType.BACK);   // the client turns the preview to show the back
                if (def != null && def.type == net.snobblemon.cosmetics.core.CosmeticType.POKEMON_SKIN && def.species != null) {
                    io.addProperty("species", def.species);
                    io.addProperty("aspect", def.aspect);
                }
                io.addProperty("icon", stack(p, e.icon()));
                items.add(io);
            }
            co.add("items", items);
            cats.add(co);
        }
        root.add("cats", cats);
        return GSON.toJson(root);
    }

    /** The Gem Shop page: the Gems mod's own JSON, with the player's region added so the screen takes the matching colours. */
    private static String shop(ServerPlayer p) {
        JsonObject o = GemShop.state(p);
        o.addProperty("region", HubData.accent(p));
        return o.toString();
    }

    private static String card(ServerPlayer p) {
        HubData.Profile pr = HubData.profile(p);
        JsonObject o = new JsonObject();
        o.addProperty("name", pr.name());
        o.addProperty("rank", pr.rank());
        o.addProperty("region", pr.regionName());
        o.addProperty("accent", pr.accent());
        o.addProperty("raid", pr.raid());
        o.addProperty("dollars", pr.dollars());
        o.addProperty("gems", pr.gems());
        o.addProperty("community", pr.community());
        return o.toString();
    }
}
