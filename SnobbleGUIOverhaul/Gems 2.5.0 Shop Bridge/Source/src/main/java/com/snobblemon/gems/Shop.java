package com.snobblemon.gems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.snobblemon.gems.api.ShopProvider;

/**
 * The Gem Shop: catalog (config/snobblegemcurrency/shop.json), the purchase transaction, and the classic chest menu (sgui) that is the
 * fallback for players without the WebGUI mod. The web page ({@link WebShop}) asks this class for a catalog snapshot and for purchases;
 * every price, balance, limit and delivery decision is made here, on the server, never by the page.
 * A purchase is: re-validate -> take the Gems -> deliver -> if delivery throws, give the Gems back. All on the server thread.
 */
public final class Shop {
    private final SnobbleGems mod;

    // ------------------------------------------------------------------ catalog model (shop.json)

    public static class Catalog {
        public int shopVersion = 1;
        /**
         * Offer timers (a product's "endsAt"). false = timers are completely off: nothing shows a countdown and nothing ends.
         * Switch live with /snobblegems shop timers on|off (permission snobblegems.shop.timer).
         */
        public boolean timersEnabled = false;
        /** Set once the Community Points "Gem Exchange" category has been added, so deleting it is never undone. */
        public boolean exchangeSeeded = false;
        /** What happens when a timer runs out: DISABLE (product stays in shop.json, switched off) or REMOVE (deleted from shop.json). */
        public String timerExpireAction = "DISABLE";
        /** SkiesSkins skin products ("/snobblegems shop add skin") run this console command. {player} {skin} {amount} are filled in. */
        public String skinCommand = "skiesskins giveskin {player} {skin} {amount}";
        public List<Category> categories = new ArrayList<>();
        public List<Product> products = new ArrayList<>();
    }

    public static class Category {
        public String id = "";
        public String name = "";
        public List<String> description = new ArrayList<>();
        /** "snobblegems:cat_featured" or any item id such as "minecraft:diamond". Only the chest menu uses it. */
        public String icon = "snobblegems:gem";
        public boolean enabled = true;
        /** Id of the parent category for a sub-category ("" = top level). Only categories added by a {@link ShopProvider} use it. */
        public String parent = "";

        Category() {}

        Category(String id, String name, String icon, String... desc) {
            this.id = id; this.name = name; this.icon = icon; this.description = new ArrayList<>(Arrays.asList(desc));
        }
    }

    public static class Product {
        public String id = "";
        public String name = "";
        public List<String> description = new ArrayList<>();
        /**
         * Web shop: "snobblegems:gem", "snobblegems:ticket" (art built into the page) or any item id; for an item id the page shows
         * config/snobblegemcurrency/icons/<namespace>/<path>.png when that file exists (a neutral tile otherwise).
         */
        public String icon = "minecraft:paper";
        public int iconCount = 1;
        public long price = 100;
        /** Category id. Products with "featured": true also appear in the Featured Items category. */
        public String category = "";
        public boolean featured = false;
        public boolean enabled = true;
        /**
         * How many you get per purchase (items, tickets; {amount} in command rewards). One number drives the reward, the "x5" on the card
         * and the chest icon. 0 = take it from the old reward.count / reward.amount fields (shop.json of version 1).
         */
        public int amount = 0;
        /** Offer timer: epoch milliseconds when this product stops being sold (0 = no timer). Only used while the shop's timersEnabled is on. */
        public long endsAt = 0;
        /** Set while loading when the product cannot work (unknown item, bad command...). Never written to shop.json. */
        public transient String invalid;
        /** Permission node a player needs to see and buy it (empty = everyone). */
        public String permission = "";
        public Limit limit = new Limit();
        public Reward reward = new Reward();

        Product() {}

        public boolean usable() { return enabled && invalid == null; }

        /** Bought with Community Points; the reward is {amount} Gems and the price is computed from config.json exchange.pointsPerGem. */
        public boolean pointsGems() { return reward != null && "gems".equalsIgnoreCase(reward.type); }
    }

    public static class Limit {
        /** Lifetime purchases per player (0 = unlimited). */
        public int perPlayer = 0;
        /** Purchases per player per day (0 = unlimited). */
        public int perDay = 0;
    }

    public static class Reward {
        /** ticket | item | command | gems (pay Community Points, receive {amount} Gems) */
        public String type = "item";
        /** ticket: number of Shiny Boost Tickets */
        public int amount = 1;
        /** item: id, count, optional custom name */
        public String item = "minecraft:stone";
        public int count = 1;
        public String itemName = "";
        /** command: run as the console. Variables: {player} {uuid} {product} */
        public List<String> commands = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private volatile Catalog catalog = new Catalog();
    private final Map<String, Product> byId = new LinkedHashMap<>();
    // ---- products of other mods (see api.ShopProvider): never written to shop.json
    private long extraKey = Long.MIN_VALUE;
    private final List<Category> extraCats = new ArrayList<>();
    private final List<Product> extraProducts = new ArrayList<>();
    private final Map<String, ShopProvider> extraOwner = new HashMap<>();
    private final Map<String, String> extraRaw = new HashMap<>();
    private final Map<UUID, long[]> lastBuy = new HashMap<>();
    /** Bumped on every (re)load so open web pages know to fetch icons again. */
    public volatile int revision = 0;

    public Shop(SnobbleGems mod) {
        this.mod = mod;
    }

    // ------------------------------------------------------------------ loading

    private long knownMtime = -1;

    private Path file() { return Cfg.dir().resolve("shop.json"); }

    public void load(List<String> problems) {
        Path f = file();
        Catalog c = null;
        try {
            Files.createDirectories(Cfg.dir());
            if (Files.exists(f)) c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Catalog.class);
            else c = defaults();
            knownMtime = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : -1;
        } catch (Exception e) {
            try { knownMtime = Files.getLastModifiedTime(f).toMillis(); } catch (Exception ignored) {}
            problems.add("shop.json could not be read (" + e.getMessage() + "); keeping the previous shop. Your file was left untouched.");
            return;
        }
        if (c == null) c = defaults();
        if (c.categories == null) c.categories = new ArrayList<>();
        if (c.products == null) c.products = new ArrayList<>();
        if (c.timerExpireAction == null || !(c.timerExpireAction.equalsIgnoreCase("DISABLE") || c.timerExpireAction.equalsIgnoreCase("REMOVE"))) c.timerExpireAction = "DISABLE";
        if (c.skinCommand == null || c.skinCommand.isBlank()) c.skinCommand = "skiesskins giveskin {player} {skin} {amount}";
        boolean seeded = seedExchange(c);
        validate(c, problems);
        try {
            if (!Files.exists(f) || seeded) {
                Cfg.writeAtomic(f, GSON.toJson(c));
                knownMtime = Files.getLastModifiedTime(f).toMillis();
            }
        } catch (Exception e) {
            problems.add("shop.json could not be written: " + e.getMessage());
        }
        install(c);
    }

    /** Adds the "Gem Exchange" category with ready-made packs, once (exchange.addToShop). Returns true when shop.json needs saving. */
    private boolean seedExchange(Catalog c) {
        if (c.exchangeSeeded || !mod.cfg.exchange.addToShop) return false;
        c.exchangeSeeded = true;
        if (c.products.stream().anyMatch(Product::pointsGems)) return true;
        c.categories.add(new Category("exchange", "Gem Exchange", "snobblegems:gem", "&7Trade Community Points for Gems.", "&7Earn points in chat events, then buy Gems here."));
        for (int n : mod.cfg.exchange.shopPacks) {
            if (n < 1) continue;
            Product pr = product("exchange_gems_" + n, "&b◆ " + n + " Gems", "snobblegems:gem", 1, "exchange", false, gemsReward(), "&7Pay Community Points, get &b" + n + " Gems&7.");
            pr.amount = n;
            pr.iconCount = n;
            c.products.add(pr);
        }
        return true;
    }

    private static Reward gemsReward() {
        Reward r = new Reward();
        r.type = "gems";
        return r;
    }

    private void install(Catalog c) {
        Map<String, Product> map = new LinkedHashMap<>();
        for (Product p : c.products) map.put(p.id, p);
        synchronized (byId) {
            byId.clear();
            byId.putAll(map);
        }
        catalog = c;
        revision++;
        mod.web.markAllDirty();
    }

    /** Writes the live catalog back to shop.json (after an admin command). Validation notes are transient, so they never end up in the file. */
    private void save() {
        try {
            Path f = file();
            Cfg.writeAtomic(f, GSON.toJson(catalog));
            knownMtime = Files.getLastModifiedTime(f).toMillis();
        } catch (Exception e) {
            SnobbleGems.LOG.error("[SnobbleGems] could not save shop.json", e);
        }
        revision++;
        mod.web.markAllDirty();
    }

    private static final int MAX_ITEMS = 2304, MAX_TICKETS = 64;

    private void validate(Catalog c, List<String> problems) {
        Set<String> catIds = new HashSet<>();
        for (Category cat : c.categories) if (cat.id != null && !cat.id.isBlank()) catIds.add(cat.id);
        Set<String> seen = new HashSet<>();
        for (Product p : c.products) {
            p.invalid = null;
            if (p.id == null || p.id.isBlank()) { problems.add("a shop product has no id and was ignored"); p.id = "invalid-" + seen.size(); p.invalid = "no id"; }
            if (!seen.add(p.id)) { problems.add("duplicate shop product id '" + p.id + "': the later one was ignored"); p.invalid = "duplicate id"; }
            if (p.reward != null && p.pointsGems()) p.price = Math.max(1, p.amount) * mod.exchange.pointsPerGem(); // priced in Community Points, always from the live rate
            if (p.price <= 0) { problems.add("product '" + p.id + "' has a price of " + p.price + " and was ignored"); p.invalid = "bad price"; }
            if (p.reward == null) { p.invalid = "no reward"; continue; }
            if (p.limit == null) p.limit = new Limit();
            if (p.description == null) p.description = new ArrayList<>();
            String type = p.reward.type == null ? "" : p.reward.type.toLowerCase();
            // one amount drives everything; version-1 files (amount missing) keep their reward numbers
            if (p.amount <= 0) p.amount = type.endsWith("ticket") ? p.reward.amount : type.equals("item") ? p.reward.count : Math.max(1, p.iconCount);
            if (p.amount < 1) p.amount = 1;
            switch (type) {
                case "ticket", "legendary_ticket" -> {
                    if (p.amount > MAX_TICKETS) { problems.add("product '" + p.id + "': ticket amount must be 1-" + MAX_TICKETS); p.invalid = "bad amount"; }
                    p.reward.amount = p.amount;
                }
                case "item" -> {
                    Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(p.reward.item));
                    if (it == Items.AIR) { problems.add("product '" + p.id + "': item '" + p.reward.item + "' does not exist (is that mod installed?); ignored"); p.invalid = "unknown item"; }
                    if (p.amount > MAX_ITEMS) { problems.add("product '" + p.id + "': amount must be 1-" + MAX_ITEMS); p.invalid = "bad amount"; }
                    p.reward.count = p.amount;
                }
                case "gems" -> {
                    if (!mod.cfg.exchange.enabled) p.invalid = "exchange off";
                    else if (p.amount > Math.max(1, mod.cfg.exchange.maxGemsPerTrade == 0 ? Integer.MAX_VALUE : mod.cfg.exchange.maxGemsPerTrade)) { problems.add("product '" + p.id + "': amount is above exchange.maxGemsPerTrade; ignored"); p.invalid = "bad amount"; }
                    p.price = (long) p.amount * mod.exchange.pointsPerGem();
                }
                case "command" -> {
                    if (p.reward.commands == null || p.reward.commands.isEmpty()) { problems.add("product '" + p.id + "': no commands"); p.invalid = "no commands"; }
                    else if (!mod.cfg.gui.allowCommandRewards) { problems.add("product '" + p.id + "': command rewards are switched off in config.json (gui.allowCommandRewards); ignored"); p.invalid = "commands off"; }
                }
                default -> { problems.add("product '" + p.id + "': unknown reward type '" + p.reward.type + "'"); p.invalid = "bad type"; }
            }
            p.iconCount = p.amount;
            if (p.category != null && !p.category.isBlank() && !catIds.contains(p.category) && !p.category.equals("featured")) {
                problems.add("product '" + p.id + "' uses unknown category '" + p.category + "'; it only shows if it is featured");
            }
        }
    }

    private Catalog defaults() {
        Catalog c = new Catalog();
        c.categories.add(new Category("featured", "Featured Items", "snobblegems:gem", "&7Hand-picked deals.", "&7Any product marked &efeatured &7shows here."));
        c.categories.add(new Category("shiny", "Shiny Boosts", "snobblegems:ticket", "&7Shiny Boost Tickets.", "&7Use one to start a server-wide shiny boost."));
        c.categories.add(new Category("skins", "Pokémon Skins", "minecraft:amethyst_shard", "&7Cosmetic skins for your Pokémon.", "&7Applied from the SkiesSkins menu after you buy."));
        c.categories.add(new Category("cosmetics", "Cosmetics", "minecraft:leather_helmet", "&7Show off your style."));
        c.categories.add(new Category("tickets", "Special Tickets", "minecraft:name_tag", "&7Event and prize tickets."));
        c.categories.add(new Category("rare", "Rare Items", "minecraft:diamond", "&7Items that are hard to come by."));
        c.categories.add(new Category("event", "Event Items", "minecraft:firework_rocket", "&7Limited-time goodies."));

        c.products.add(product("shiny_ticket_1", "&d✨ Shiny Boost Ticket", "snobblegems:ticket", 250, "shiny", true,
                ticket(1), "&7Right-click to start a server-wide", "&7shiny boost for everyone.", "", "&8Each ticket is one use."));
        c.products.add(product("shiny_ticket_3", "&d✨ Shiny Boost Ticket &7x3", "snobblegems:ticket", 675, "shiny", false,
                ticket(3), "&7Three tickets. A small saving."));
        c.products.get(1).iconCount = 3;
        c.products.add(product("shiny_ticket_10", "&d✨ Shiny Boost Ticket &7x10", "snobblegems:ticket", 2200, "shiny", false,
                ticket(10), "&7Ten tickets for a community event."));
        c.products.get(2).iconCount = 10;
        c.products.add(product("legendary_ticket_1", "&6✨ Legendary Boost Ticket", "snobblegems:ticket", 400, "shiny", false,
                legendaryTicket(1), "&7Right-click: legendary Pokémon are 2x as", "&7likely to be shiny for 5 minutes,", "&7for everyone.", "", "&8Each ticket is one use."));
        c.products.add(product("legendary_ticket_3", "&6✨ Legendary Boost Ticket &7x3", "snobblegems:ticket", 1080, "shiny", false,
                legendaryTicket(3), "&7Three tickets. A small saving."));
        c.products.get(4).iconCount = 3;
        c.products.add(product("legendary_ticket_10", "&6✨ Legendary Boost Ticket &7x10", "snobblegems:ticket", 3500, "shiny", false,
                legendaryTicket(10), "&7Ten tickets for a community event."));
        c.products.get(5).iconCount = 10;

        Product ability = product("ability_capsule", "&bAbility Capsule", "cobblemon:ability_capsule", 400, "rare", false, item("cobblemon:ability_capsule", 1),
                "&7Switch a Pokémon between its", "&7regular abilities.");
        Product patch = product("ability_patch", "&dAbility Patch", "cobblemon:ability_patch", 1200, "rare", false, item("cobblemon:ability_patch", 1),
                "&7Unlocks a Hidden Ability.");
        Product candy = product("rare_candy", "&dRare Candy &7x5", "cobblemon:rare_candy", 300, "rare", true, item("cobblemon:rare_candy", 5), "&7Level a Pokémon up instantly.");
        candy.iconCount = 5;
        Product ball = product("master_ball", "&5Master Ball", "cobblemon:master_ball", 2500, "rare", true, item("cobblemon:master_ball", 1),
                "&7Never fails.");
        ball.limit.perPlayer = 3;
        c.products.add(candy);
        c.products.add(ability);
        c.products.add(patch);
        c.products.add(ball);

        // examples, switched off so an empty category does not show: edit, enable, done
        Product hat = product("cosmetic_example", "&bExample Cosmetic", "snobblegems:gem", 500, "cosmetics", false,
                command("lp user {player} permission set cosmetics.example true"), "&7Console-command reward example:", "&7this one grants a LuckPerms node.");
        hat.enabled = false;
        hat.limit.perPlayer = 1;
        Product tix = product("special_ticket_example", "&6Example Special Ticket", "snobblegems:ticket", 800, "tickets", false,
                command("give {player} minecraft:diamond 1"), "&7Runs any console command.", "&7{player} and {uuid} are filled in.");
        tix.enabled = false;
        Product ev = product("event_example", "&aExample Event Item", "minecraft:firework_rocket", 300, "event", false,
                item("minecraft:firework_rocket", 16), "&7Limited-time item example.");
        ev.enabled = false;
        ev.limit.perDay = 2;
        c.products.add(hat);
        c.products.add(tix);
        c.products.add(ev);

        // Pokémon skins (SkiesSkins): the skin ids are the file names in config/skiesskins/skins/ (without .json). Switched off until you point them at your real skins.
        String[][] skins = {{"skin_pikachu", "Pikachu Skin", "pikachu_example", "cobblemon:poke_ball", "900"}, {"skin_charmander", "Charmander Skin", "charmander_example", "cobblemon:poke_ball", "900"},
                {"skin_bulbasaur", "Bulbasaur Skin", "bulbasaur_example", "cobblemon:poke_ball", "900"}};
        for (String[] k : skins) {
            Product sk = product(k[0], "&d" + k[1], k[3], Long.parseLong(k[4]), "skins", false,
                    command(c.skinCommand.replace("{skin}", k[2])), "&7A cosmetic skin for your " + k[1].replace(" Skin", "") + ".", "&7Open the skin menu with &f/skiesskins&7 to apply it.");
            sk.enabled = false;
            sk.limit.perPlayer = 1;
            c.products.add(sk);
        }
        return c;
    }

    private static Product product(String id, String name, String icon, long price, String cat, boolean featured, Reward r, String... desc) {
        Product p = new Product();
        p.id = id; p.name = name; p.icon = icon; p.price = price; p.category = cat; p.featured = featured; p.reward = r;
        p.description = new ArrayList<>(Arrays.asList(desc));
        return p;
    }

    private static Reward legendaryTicket(int n) {
        Reward r = new Reward();
        r.type = "legendary_ticket"; r.amount = n;
        return r;
    }

    public static Reward legendaryTicketReward(int n) { return legendaryTicket(n); }

    private static Reward ticket(int n) {
        Reward r = new Reward();
        r.type = "ticket"; r.amount = n;
        return r;
    }

    private static Reward item(String id, int count) {
        Reward r = new Reward();
        r.type = "item"; r.item = id; r.count = count;
        return r;
    }

    private static Reward command(String... cmds) {
        Reward r = new Reward();
        r.type = "command"; r.commands = new ArrayList<>(Arrays.asList(cmds));
        return r;
    }

    // ------------------------------------------------------------------ queries

    public boolean timersOn() { return catalog.timersEnabled; }

    /** Seconds-resolution test: is this product's offer timer over? (always false while timers are switched off) */
    public boolean timedOut(Product p) {
        return catalog.timersEnabled && p.endsAt > 0 && System.currentTimeMillis() >= p.endsAt;
    }

    /** Sold right now: switched on, valid, and not past its timer. */
    private boolean live(Product p) { return p.usable() && !timedOut(p); }

    public int productCount() {
        int n = 0;
        for (Product p : catalog.products) if (live(p)) n++;
        return n;
    }

    public void forget(UUID id) {
        lastBuy.remove(id);
        states.remove(id);
    }

    private List<Category> visibleCategories(ServerPlayer p) {
        syncExternal();
        List<Category> out = new ArrayList<>();
        for (Category c : catalog.categories) {
            if (!c.enabled || c.id == null || c.id.isBlank()) continue;
            if (!productsOf(c, p).isEmpty()) out.add(c);
            if (out.size() == 7) break;
        }
        for (Category c : extraCats) {                      // top-level categories of providers (their sub-categories are listed with them)
            if (!c.parent.isEmpty()) continue;
            if (!productsOf(c, p).isEmpty()) out.add(c);
        }
        return out;
    }

    private List<Category> visibleChildren(Category parent, ServerPlayer p) {
        List<Category> out = new ArrayList<>();
        for (Category c : extraCats) if (c.parent.equals(parent.id) && !productsOf(c, p).isEmpty()) out.add(c);
        return out;
    }

    private String parentOf(String categoryId) {
        for (Category c : extraCats) if (c.id.equals(categoryId)) return c.parent;
        return "";
    }

    /** Pulls the categories and products of registered {@link ShopProvider}s into the shop, only when one of them says it changed. */
    private synchronized void syncExternal() {
        long key = 17;
        for (ShopProvider sp : ShopProvider.REGISTERED) key = key * 31 + sp.revision() * 7 + sp.id().hashCode();
        if (key == extraKey) return;
        extraKey = key;
        synchronized (byId) { for (Product old : extraProducts) byId.remove(old.id); }
        extraCats.clear(); extraProducts.clear(); extraOwner.clear(); extraRaw.clear();
        for (ShopProvider sp : ShopProvider.REGISTERED) {
            try {
                for (ShopProvider.Category c : sp.categories()) {
                    Category k = new Category();
                    k.id = sp.id() + ":" + c.id();
                    k.parent = c.parent() == null || c.parent().isEmpty() ? "" : sp.id() + ":" + c.parent();
                    k.name = c.name(); k.icon = c.icon(); k.description = new ArrayList<>(c.description());
                    extraCats.add(k);
                }
                for (ShopProvider.Product x : sp.products()) {
                    Product p = new Product();
                    p.id = sp.id() + ":" + x.id(); p.name = x.name(); p.description = new ArrayList<>(x.description());
                    p.icon = x.icon(); p.price = x.price(); p.category = sp.id() + ":" + x.category();
                    p.reward = new Reward(); p.reward.type = "provider";
                    extraProducts.add(p); extraOwner.put(p.id, sp); extraRaw.put(p.id, x.id());
                }
            } catch (RuntimeException e) {
                SnobbleGems.LOG.warn("[SnobbleGems] shop provider '{}' failed: {}", sp.id(), e.toString());
            }
        }
        synchronized (byId) { for (Product p : extraProducts) byId.put(p.id, p); }
        revision++;
    }

    private boolean allowed(ServerPlayer p, Product pr) {
        return pr.permission == null || pr.permission.isBlank() || Perms.player(p, pr.permission, 4);
    }

    private List<Product> productsOf(Category c, ServerPlayer p) {
        List<Product> out = new ArrayList<>();
        for (Product pr : catalog.products) {
            if (!live(pr)) continue;
            boolean in = c.id.equals(pr.category) || (c.id.equals("featured") && pr.featured);
            if (!in) continue;
            if (!allowed(p, pr)) continue;
            out.add(pr);
        }
        for (Product pr : extraProducts) {                  // a provider's top-level category shows the products of all its sub-categories
            if (!c.id.equals(pr.category) && !c.id.equals(parentOf(pr.category))) continue;
            out.add(pr);
        }
        return out;
    }

    // ------------------------------------------------------------------ snapshot for the web page

    public record CatView(String id, String name, String color, List<String> desc, int count, String parent) {}

    public record ProdView(String id, String name, String color, List<String> desc, long price, int count, String icon, List<String> cats,
                           boolean ok, String reason, String code, String limit, boolean featured, long ends, boolean pts) {}

    public record Snapshot(List<CatView> cats, List<ProdView> products) {}

    /** What this player may see, with per-player affordability and limits. Server thread only. */
    public Snapshot snapshot(ServerPlayer p) {
        long bal = mod.ledger.balance(p.getUUID());
        List<CatView> cats = new ArrayList<>();
        Map<String, List<String>> catsOf = new HashMap<>();
        List<Category> shown = new ArrayList<>();
        for (Category c : visibleCategories(p)) {
            shown.add(c);
            shown.addAll(visibleChildren(c, p));
        }
        for (Category c : shown) {
            List<Product> ps = productsOf(c, p);
            cats.add(new CatView(c.id, Txt.strip(c.name), Txt.firstColor(c.name), plainLines(c.description), ps.size(), c.parent == null ? "" : c.parent));
            for (Product pr : ps) catsOf.computeIfAbsent(pr.id, k -> new ArrayList<>()).add(c.id);
        }
        List<ProdView> prods = new ArrayList<>();
        List<Product> every = new ArrayList<>(catalog.products);
        every.addAll(extraProducts);
        for (Product pr : every) {
            if (!live(pr) || !catsOf.containsKey(pr.id)) continue;
            Purchaseability pa = check(p, pr, bal);
            String limit = "";
            if (pr.limit != null && pr.limit.perPlayer > 0) limit = pa.boughtTotal + "/" + pr.limit.perPlayer + " bought";
            else if (pr.limit != null && pr.limit.perDay > 0) limit = pa.boughtToday + "/" + pr.limit.perDay + " today";
            prods.add(new ProdView(pr.id, Txt.strip(pr.name), Txt.firstColor(pr.name), plainLines(pr.description), pr.price, Math.max(1, pr.amount), pr.icon,
                    catsOf.get(pr.id), pa.reason == null, pa.reason == null ? "" : Txt.strip(pa.reason), pa.code, limit, pr.featured,
                    catalog.timersEnabled ? Math.max(0, pr.endsAt) : 0, pr.pointsGems()));
        }
        return new Snapshot(cats, prods);
    }

    private static List<String> plainLines(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in != null) for (String s : in) out.add(Txt.strip(s));
        return out;
    }

    public Product product(String id) {
        syncExternal();
        synchronized (byId) { return byId.get(id); }
    }

    // ------------------------------------------------------------------ icons (chest menu)

    /** CustomModelData numbers in the v2 resource pack: only the Gem and the ticket have art; everything else is a vanilla item. */
    private static final int CMD_GEM = 7420001, CMD_TICKET = 7420002;

    private ItemStack icon(String id, int count) {
        if (id == null || id.isBlank() || id.startsWith("ext:")) id = id != null && id.startsWith("ext:") ? "minecraft:golden_helmet" : "minecraft:paper";
        if (id.startsWith("snobblegems:")) {
            boolean ticket = id.contains("ticket") || id.contains("cat_tickets");
            if (mod.cfg.gui.customIcons) {
                ItemStack s = new ItemStack(Items.TRIAL_KEY, Math.max(1, count));
                s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(ticket ? CMD_TICKET : CMD_GEM));
                return s;
            }
            return new ItemStack(ticket ? Items.NAME_TAG : Items.DIAMOND, Math.max(1, count));
        }
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return new ItemStack(it == Items.AIR ? Items.PAPER : it, Math.max(1, Math.min(64, count)));
    }

    private static final String MARK = "snobblegemcurrency";

    /**
     * Every icon the menu shows is tagged as display-only. Gems are a balance, never an item: if anything (a glitch, another mod) ever let one
     * of these leave the menu, {@link #scrub} takes it out of the player's inventory again.
     */
    private GuiElementBuilder el(String icon, int count, String name, String... lore) {
        ItemStack s = icon(icon, count);
        GuiElementBuilder b = new GuiElementBuilder(s.getItem());
        CustomModelData cmd = s.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) b.setCustomModelData(cmd.value());
        b.setCount(s.getCount());
        b.setName(Txt.parse(name));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Txt.parse(x));
        b.setLore(l);
        CompoundTag root = new CompoundTag();
        CompoundTag mine = new CompoundTag();
        mine.putBoolean("display", true);
        root.put(MARK, mine);
        b.setComponent(DataComponents.CUSTOM_DATA, CustomData.of(root));
        b.hideDefaultTooltip();
        return b;
    }

    public static boolean isDisplayOnly(ItemStack s) {
        if (s.isEmpty()) return false;
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return false;
        CompoundTag t = d.copyTag();
        return t.contains(MARK, 10) && t.getCompound(MARK).getBoolean("display");
    }

    /** Removes any display-only shop icon from the player's inventory and cursor. @return how many stacks were removed */
    public int scrub(ServerPlayer p) {
        int removed = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isDisplayOnly(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
                removed++;
            }
        }
        if (isDisplayOnly(p.containerMenu.getCarried())) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
            removed++;
        }
        if (removed > 0) SnobbleGems.LOG.warn("[SnobbleGems] removed {} display-only shop icon(s) from {}'s inventory (Gems are virtual and cannot become items)", removed, p.getGameProfile().getName());
        return removed;
    }

    // ------------------------------------------------------------------ chest menu (fallback)

    private static final class State {
        int category, page;
    }

    private final Map<UUID, State> states = new HashMap<>();

    public void open(ServerPlayer p) {
        List<Category> cats = visibleCategories(p);
        if (cats.isEmpty()) {
            mod.notify(p, mod.vars("shop_disabled"));
            return;
        }
        State st = states.computeIfAbsent(p.getUUID(), k -> new State());
        if (st.category >= cats.size()) st.category = 0;
        showCategory(p, st);
    }

    private SimpleGui newGui(ServerPlayer p) {
        SimpleGui g = new SimpleGui(MenuType.GENERIC_9x6, p, false) {
            @Override
            public void onClose() {
                scrub(p);
            }
        };
        g.setLockPlayerInventory(true);
        g.setTitle(Component.literal(mod.cfg.gui.shopTitle + "  -  " + Txt.num(mod.ledger.balance(p.getUUID())) + " " + mod.cfg.currency.name));
        return g;
    }

    private void showCategory(ServerPlayer p, State st) {
        List<Category> cats = visibleCategories(p);
        if (cats.isEmpty()) { p.closeContainer(); return; }
        if (st.category >= cats.size()) st.category = 0;
        Category cur = cats.get(st.category);
        List<Product> prods = productsOf(cur, p);
        int perPage = 28;
        int pages = Math.max(1, (prods.size() + perPage - 1) / perPage);
        if (st.page >= pages) st.page = pages - 1;
        long bal = mod.ledger.balance(p.getUUID());
        SimpleGui g = newGui(p);

        g.setSlot(0, el("snobblegems:gem", 1, "&b" + mod.cfg.currency.name,
                "&7Balance: &b" + Txt.num(bal),
                "",
                "&7Earn " + mod.cfg.currency.name + " by helping the community",
                "&7and supporting the server."));

        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            final int idx = i;
            List<String> lore = new ArrayList<>(c.description);
            lore.add("");
            lore.add(i == st.category ? "&aViewing" : "&eClick to browse");
            GuiElementBuilder b = el(c.icon, 1, (i == st.category ? "&b▶ " : "&f") + c.name, lore.toArray(new String[0]));
            if (i == st.category) b.glow();
            b.setCallback((s, t, a, gg) -> {
                mod.sound(p, mod.cfg.sounds.click);
                st.category = idx;
                st.page = 0;
                showCategory(p, st);
            });
            g.setSlot(1 + i, b);
        }
        g.setSlot(8, el("minecraft:barrier", 1, "&cClose").setCallback((s, t, a, gg) -> {
            mod.sound(p, mod.cfg.sounds.click);
            gg.close();
        }));

        int from = st.page * perPage;
        for (int k = 0; k < perPage && from + k < prods.size(); k++) {
            Product pr = prods.get(from + k);
            int slot = 9 + (k / 7) * 9 + 1 + (k % 7);
            g.setSlot(slot, productElement(p, pr, bal, st));
        }

        if (st.page > 0) g.setSlot(45, el("minecraft:arrow", 1, "&bPrevious page", "&7Page " + st.page + " of " + pages).setCallback((s, t, a, gg) -> {
            mod.sound(p, mod.cfg.sounds.click);
            st.page--;
            showCategory(p, st);
        }));
        if (pages > 1) g.setSlot(49, el("minecraft:paper", 1, "&fPage " + (st.page + 1) + " / " + pages, "&7" + prods.size() + " items in " + Txt.strip(cur.name)));
        if (st.page < pages - 1) g.setSlot(53, el("minecraft:arrow", 1, "&bNext page", "&7Page " + (st.page + 2) + " of " + pages).setCallback((s, t, a, gg) -> {
            mod.sound(p, mod.cfg.sounds.click);
            st.page++;
            showCategory(p, st);
        }));
        g.open();
    }

    private GuiElementBuilder productElement(ServerPlayer p, Product pr, long bal, State st) {
        Purchaseability pa = check(p, pr, bal);
        List<String> lore = new ArrayList<>(pr.description);
        lore.add("");
        if (pr.pointsGems()) lore.add("&7Price: &d" + Txt.num(pr.price) + " &7" + mod.exchange.pointsLabel());
        else lore.add("&7Price: " + mod.cfg.currency.chatIcon + " " + (pa.affordable ? "&a" : "&c") + Txt.num(pr.price) + " &7" + mod.cfg.currency.name);
        if (pr.limit != null && pr.limit.perPlayer > 0) lore.add("&8Limit: " + pa.boughtTotal + " / " + pr.limit.perPlayer + " per player");
        if (pr.limit != null && pr.limit.perDay > 0) lore.add("&8Today: " + pa.boughtToday + " / " + pr.limit.perDay);
        if (catalog.timersEnabled && pr.endsAt > 0) lore.add("&dEnds in &f" + duration(pr.endsAt - System.currentTimeMillis()));
        lore.add("");
        lore.add(pa.reason == null ? "&e▶ Click to buy" : pa.reason);
        GuiElementBuilder b = el(pr.icon, Math.max(1, Math.min(64, pr.amount)), pr.name, lore.toArray(new String[0]));
        if (pa.reason == null) b.setCallback((s, t, a, gg) -> {
            mod.sound(p, mod.cfg.sounds.click);
            confirm(p, pr, st);
        });
        else b.setCallback((s, t, a, gg) -> {
            mod.sound(p, mod.cfg.sounds.error);
            mod.notify(p, Txt.parse(pa.message));
        });
        return b;
    }

    private static final class Purchaseability {
        boolean affordable;
        String reason;   // lore / page line when it cannot be bought
        String message;  // chat message when clicked
        String code = "";
        int boughtTotal;
        long boughtToday;
    }

    private Purchaseability check(ServerPlayer p, Product pr, long bal) {
        Purchaseability r = new Purchaseability();
        Ledger.Purchase rec = mod.ledger.purchase(p.getUUID(), pr.id);
        r.boughtTotal = (int) rec.total;
        r.boughtToday = rec.dayCount;
        ShopProvider sp = extraOwner.get(pr.id);
        if (sp != null) {                                  // a provider can refuse for its own reasons (already owned, not for sale)
            String why = sp.why(p, extraRaw.get(pr.id), pr.price);
            if (why != null) {
                r.reason = "&c✖ " + why;
                r.message = "&c[Shop] " + why;
                r.code = "limit";
                return r;
            }
        }
        if (pr.pointsGems()) {
            String why = mod.exchange.why(p.getUUID(), p.getGameProfile().getName(), pr.amount);
            r.affordable = why == null;
            if (why != null) {
                r.reason = "&c✖ " + (why.startsWith("Not enough") ? "Not enough " + mod.exchange.pointsName() : why.length() > 40 ? "Not available now" : why);
                r.message = "&c[Exchange] " + why;
                r.code = why.startsWith("Not enough") ? "funds" : "limit";
            }
            return r;
        }
        r.affordable = bal >= pr.price;
        if (pr.limit != null && pr.limit.perPlayer > 0 && rec.total >= pr.limit.perPlayer) {
            r.reason = "&c✖ Purchase limit reached";
            r.message = mod.text("buy_limit");
            r.code = "limit";
        } else if (pr.limit != null && pr.limit.perDay > 0 && rec.dayCount >= pr.limit.perDay) {
            r.reason = "&c✖ Daily limit reached";
            r.message = mod.text("buy_limit");
            r.code = "limit";
        } else if (!r.affordable) {
            r.reason = "&c✖ Not enough " + mod.cfg.currency.name;
            r.message = mod.text("insufficient", "balance", Txt.num(bal), "price", Txt.num(pr.price));
            r.code = "funds";
        }
        return r;
    }

    private void confirm(ServerPlayer p, Product pr, State st) {
        long bal = mod.ledger.balance(p.getUUID());
        SimpleGui g = newGui(p);
        long opened = System.currentTimeMillis();
        g.setSlot(13, el(pr.icon, Math.max(1, Math.min(64, pr.amount)), pr.name, pr.description.toArray(new String[0])));
        if (pr.pointsGems()) g.setSlot(22, el("snobblegems:gem", 1, "&dPrice: &f" + Txt.num(pr.price) + " " + mod.exchange.pointsLabel(),
                "&7You receive: &b" + Txt.num(pr.amount) + " " + mod.cfg.currency.name));
        else g.setSlot(22, el("snobblegems:gem", 1, "&bPrice: &f" + Txt.num(pr.price) + " " + mod.cfg.currency.name,
                "&7Your balance: &b" + Txt.num(bal),
                "&7After purchase: &b" + Txt.num(Math.max(0, bal - pr.price))));
        for (int s : new int[]{28, 29, 30}) {
            g.setSlot(s, el("minecraft:lime_dye", 1, "&a✔ Confirm purchase", "&7Buy &f" + Txt.strip(pr.name) + " &7for &b" + Txt.num(pr.price) + " " + mod.cfg.currency.name + "&7.")
                    .setCallback((sl, t, a, gg) -> {
                        if (System.currentTimeMillis() - opened > mod.cfg.gui.confirmSeconds * 1000L) {
                            mod.notify(p, Txt.parse("&cThat confirmation expired."));
                            showCategory(p, st);
                            return;
                        }
                        gg.close();
                        buy(p, pr, pr.price);
                        mod.later(1, () -> { if (!p.hasDisconnected()) showCategory(p, st); });
                    }));
        }
        for (int s : new int[]{32, 33, 34}) {
            g.setSlot(s, el("minecraft:red_dye", 1, "&c✖ Cancel", "&7Go back without buying.").setCallback((sl, t, a, gg) -> {
                mod.sound(p, mod.cfg.sounds.click);
                showCategory(p, st);
            }));
        }
        g.setSlot(49, el("minecraft:arrow", 1, "&7Back to the shop").setCallback((sl, t, a, gg) -> showCategory(p, st)));
        g.open();
    }

    // ------------------------------------------------------------------ live editing (commands) + timers + file watch

    /** Every 2 s: pick up edits made to shop.json by hand, and end offers whose timer ran out. */
    public void tick(long tick) {
        if (tick % 40 != 7) return;
        try {
            Path f = file();
            if (Files.exists(f)) {
                long m = Files.getLastModifiedTime(f).toMillis();
                if (m != knownMtime) {
                    List<String> problems = new ArrayList<>();
                    load(problems);
                    SnobbleGems.LOG.info("[SnobbleGems] shop.json changed on disk: reloaded, {} product(s) for sale", productCount());
                    for (String pr : problems) SnobbleGems.LOG.warn("[SnobbleGems] {}", pr);
                }
            }
        } catch (Exception e) {
            SnobbleGems.LOG.warn("[SnobbleGems] could not check shop.json: {}", e.toString());
        }
        if (catalog.timersEnabled) sweepTimers();
    }

    /** DISABLE or REMOVE products whose timer ran out (writes shop.json once if anything changed). */
    private void sweepTimers() {
        boolean remove = "REMOVE".equalsIgnoreCase(catalog.timerExpireAction);
        long now = System.currentTimeMillis();
        List<Product> done = new ArrayList<>();
        for (Product p : catalog.products) if (p.endsAt > 0 && now >= p.endsAt) done.add(p);
        if (done.isEmpty()) return;
        for (Product p : done) {
            if (remove) { catalog.products.remove(p); synchronized (byId) { byId.remove(p.id); } }
            else { p.enabled = false; p.endsAt = 0; }
            SnobbleGems.LOG.info("[SnobbleGems] offer timer ended: '{}' was {}", p.id, remove ? "removed from the shop" : "switched off");
        }
        save();
    }

    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return s + "s";
    }

    /** "90", "30m", "2h", "1d12h", "1h30m15s". A bare number is minutes. @return milliseconds, or -1 if it cannot be read */
    public static long parseDuration(String t) {
        if (t == null) return -1;
        t = t.trim().toLowerCase();
        if (t.matches("\\d{1,7}")) return Long.parseLong(t) * 60_000L;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,7})([dhms])").matcher(t);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            end = m.end();
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) { case "d" -> 86_400_000L; case "h" -> 3_600_000L; case "m" -> 60_000L; default -> 1000L; };
        }
        if (end != t.length() || total <= 0 || total > 366L * 86_400_000L) return -1;
        return total;
    }

    private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[a-z0-9_]{1,40}");

    public List<Product> products() { return new ArrayList<>(catalog.products); }

    public List<Category> categories() { return new ArrayList<>(catalog.categories); }

    public String skinCommand() { return catalog.skinCommand; }

    public String expireAction() { return catalog.timerExpireAction; }

    /** Each admin method returns null on success or a short problem text. All run on the server thread and write shop.json at once. */
    public String addProduct(String id, String name, String icon, long price, int amount, String category, Reward reward) {
        if (!ID.matcher(id).matches()) return "The product id may only use a-z, 0-9 and _ (max 40 characters).";
        if (product(id) != null) return "There is already a product called '" + id + "'. Remove it first or pick another id.";
        if (price < 1 || price > 1_000_000_000L) return "The price must be at least 1.";
        if (category != null && !category.isBlank() && catalog.categories.stream().noneMatch(c -> c.id.equals(category))) return "There is no category '" + category + "'. See /snobblegems shop category list.";
        Product p = new Product();
        p.id = id; p.name = name; p.icon = icon; p.price = price; p.amount = Math.max(1, amount); p.iconCount = p.amount;
        p.category = category == null ? "" : category; p.reward = reward; p.enabled = true;
        List<String> problems = new ArrayList<>();
        Catalog probe = new Catalog();
        probe.categories = catalog.categories;
        probe.products = new ArrayList<>(List.of(p));
        validate(probe, problems);
        if (p.invalid != null) return problems.isEmpty() ? "That product is not valid." : problems.get(0);
        catalog.products.add(p);
        synchronized (byId) { byId.put(p.id, p); }
        save();
        return null;
    }

    public String removeProduct(String id) {
        Product p = product(id);
        if (p == null) return "There is no product '" + id + "'.";
        catalog.products.remove(p);
        synchronized (byId) { byId.remove(id); }
        save();
        return null;
    }

    /** Changes one field of an existing product. field: price | amount | enabled | featured | category | name | icon | permission | limit | limitday | desc */
    public String editProduct(String id, String field, String value) {
        Product p = product(id);
        if (p == null) return "There is no product '" + id + "'.";
        try {
            switch (field) {
                case "price" -> {
                    long v = Long.parseLong(value);
                    if (v < 1 || v > 1_000_000_000L) return "The price must be at least 1.";
                    p.price = v;
                }
                case "amount" -> {
                    int v = Integer.parseInt(value);
                    String type = p.reward.type.toLowerCase();
                    int max = type.endsWith("ticket") ? MAX_TICKETS : MAX_ITEMS;
                    if (v < 1 || v > max) return "The amount must be 1-" + max + " for this product.";
                    p.amount = v; p.iconCount = v;
                    if (type.endsWith("ticket")) p.reward.amount = v; else if (type.equals("item")) p.reward.count = v;
                    if ("bad amount".equals(p.invalid)) p.invalid = null;
                }
                case "enabled" -> p.enabled = Boolean.parseBoolean(value);
                case "featured" -> p.featured = Boolean.parseBoolean(value);
                case "category" -> {
                    if (!value.isBlank() && catalog.categories.stream().noneMatch(c -> c.id.equals(value))) return "There is no category '" + value + "'.";
                    p.category = value;
                }
                case "name" -> p.name = value;
                case "permission" -> p.permission = value.equals("-") ? "" : value;
                case "limit" -> p.limit.perPlayer = Math.max(0, Integer.parseInt(value));
                case "limitday" -> p.limit.perDay = Math.max(0, Integer.parseInt(value));
                case "icon" -> p.icon = value;
                case "desc" -> p.description = value.equals("-") ? new ArrayList<>() : new ArrayList<>(Arrays.asList(value.split("\\|")));
                default -> { return "Unknown field " + field; }
            }
        } catch (NumberFormatException e) {
            return "'" + value + "' is not a number.";
        }
        save();
        return null;
    }

    /** @param ms from now; 0 or negative clears the timer */
    public String setTimer(String id, long ms) {
        Product p = product(id);
        if (p == null) return "There is no product '" + id + "'.";
        p.endsAt = ms > 0 ? System.currentTimeMillis() + ms : 0;
        save();
        return null;
    }

    public String setTimersEnabled(boolean on) {
        catalog.timersEnabled = on;
        save();
        return null;
    }

    public String setExpireAction(String action) {
        if (!action.equalsIgnoreCase("DISABLE") && !action.equalsIgnoreCase("REMOVE")) return "Use DISABLE or REMOVE.";
        catalog.timerExpireAction = action.toUpperCase();
        save();
        return null;
    }

    public String addCategory(String id, String name, String icon) {
        if (!ID.matcher(id).matches()) return "The category id may only use a-z, 0-9 and _.";
        if (catalog.categories.stream().anyMatch(c -> c.id.equals(id))) return "There is already a category '" + id + "'.";
        catalog.categories.add(new Category(id, name, icon, "&7" + Txt.strip(name)));
        save();
        return null;
    }

    public String removeCategory(String id) {
        Category c = catalog.categories.stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
        if (c == null) return "There is no category '" + id + "'.";
        long n = catalog.products.stream().filter(p -> id.equals(p.category)).count();
        if (n > 0) return n + " product(s) are still in '" + id + "'. Move or remove them first.";
        catalog.categories.remove(c);
        save();
        return null;
    }

    public static Reward itemReward(String item, int count) { return item(item, count); }

    public static Reward ticketReward(int n) { return ticket(n); }

    public static Reward commandReward(String... cmds) { return command(cmds); }

    // ------------------------------------------------------------------ the purchase transaction

    /** Outcome of {@link #buy}. {@code message} is plain text for the page; the player also gets the usual chat line. */
    public record BuyResult(boolean ok, String code, String message, boolean waiting) {}

    private BuyResult fail(ServerPlayer p, String code, String key, String... kv) {
        Component c = mod.vars(key, kv);
        mod.notify(p, c);
        mod.sound(p, mod.cfg.sounds.error);
        return new BuyResult(false, code, c.getString().replaceAll("^\\[.\\] ", ""), false);
    }

    /**
     * Re-checks everything, charges, delivers, and refunds if delivery throws.
     * @param expectedPrice price the player was quoted (-1 = none): a changed price aborts so nobody pays more than they saw
     */
    public BuyResult buy(ServerPlayer p, Product pr, long expectedPrice) {
        long now = System.currentTimeMillis();
        long[] last = lastBuy.computeIfAbsent(p.getUUID(), k -> new long[1]);
        if (now - last[0] < 400) return new BuyResult(false, "busy", "One moment...", false); // a double click must never buy twice
        last[0] = now;

        syncExternal();
        Product live;
        synchronized (byId) { live = byId.get(pr.id); }
        if (live == null || !live(live)) return fail(p, "unavailable", "buy_unavailable");
        pr = live;
        if (!allowed(p, pr)) return fail(p, "permission", "buy_no_permission");
        if (expectedPrice >= 0 && expectedPrice != pr.price) return fail(p, "price_changed", "price_changed");
        Purchaseability pa = check(p, pr, mod.ledger.balance(p.getUUID()));
        if (pa.reason != null) {
            Component c = Txt.parse(pa.message);
            mod.notify(p, c);
            mod.sound(p, mod.cfg.sounds.error);
            return new BuyResult(false, pa.code, c.getString().replaceAll("^\\[.\\] ", ""), false);
        }

        if (pr.pointsGems()) {
            String why = mod.exchange.trade(p, pr.amount);
            if (why != null) {
                mod.notify(p, Txt.parse("&c[Exchange] " + why));
                mod.sound(p, mod.cfg.sounds.error);
                return new BuyResult(false, "failed", why, false);
            }
            long gb = mod.ledger.balance(p.getUUID());
            mod.notify(p, Txt.parse("&b[Exchange] &7You bought &b" + Txt.num(pr.amount) + " " + mod.cfg.currency.name + " &7for &d" + Txt.num(pr.price) + " " + mod.exchange.pointsLabel() + "&7. Balance: &b" + Txt.num(gb) + "&7."));
            mod.purchaseMagic(p);
            return new BuyResult(true, "ok", "Bought " + Txt.num(pr.amount) + " " + mod.cfg.currency.name + " for " + Txt.num(pr.price) + " " + mod.exchange.pointsName() + ".", false);
        }

        // 1) take the Gems
        com.snobblemon.gems.api.GemsResult paid = mod.ledger.adjust(p.getUUID(), p.getGameProfile().getName(), -pr.price, "shop:" + pr.id, "shop", null);
        if (!paid.ok()) return fail(p, "funds", "insufficient", "balance", Txt.num(paid.balance()), "price", Txt.num(pr.price));
        mod.ledger.recordPurchase(p.getUUID(), pr.id, 1);

        // 2) deliver; any failure gives the Gems (and the purchase count) back
        boolean later;
        try {
            later = deliver(p, pr);
        } catch (Throwable t) {
            SnobbleGems.LOG.error("[SnobbleGems] delivery of '{}' to {} failed, refunding {}", pr.id, p.getGameProfile().getName(), pr.price, t);
            mod.ledger.adjust(p.getUUID(), p.getGameProfile().getName(), pr.price, "refund:" + pr.id, "shop", null);
            mod.ledger.unrecordPurchase(p.getUUID(), pr.id, 1);
            return fail(p, "failed", "buy_failed");
        }
        long bal = mod.ledger.balance(p.getUUID());
        Component ok = mod.vars("buy_ok", "product", Txt.strip(pr.name), "price", Txt.num(pr.price), "balance", Txt.num(bal));
        mod.notify(p, ok);
        if (later) mod.notify(p, mod.vars("buy_delivered_later", "product", Txt.strip(pr.name)));
        mod.purchaseMagic(p);
        SnobbleGems.LOG.info("[SnobbleGems] {} bought {} for {}", p.getGameProfile().getName(), pr.id, pr.price);
        return new BuyResult(true, later ? "waiting" : "ok", Txt.strip(pr.name) + " purchased for " + Txt.num(pr.price) + " " + mod.cfg.currency.name + "." + (later ? " Your inventory is full: type /snobblegems claim after freeing space." : ""), later);
    }

    /** @return true if part of the reward is waiting for inventory room */
    private boolean deliver(ServerPlayer p, Product pr) {
        Reward r = pr.reward;
        switch (r.type.toLowerCase()) {
            case "ticket" -> {
                int now = mod.tickets.grant(p.getUUID(), p.getGameProfile().getName(), pr.amount, "shop:" + pr.id, null);
                return now < pr.amount;
            }
            case "legendary_ticket" -> {
                int now = mod.tickets.grant(p.getUUID(), p.getGameProfile().getName(), pr.amount, "shop:" + pr.id, null, Tickets.LEGENDARY);
                return now < pr.amount;
            }
            case "item" -> {
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.item));
                if (it == Items.AIR) throw new IllegalStateException("item vanished: " + r.item);
                boolean waiting = false;
                int left = Math.max(1, pr.amount);
                // amounts above one stack are handed over as several stacks; whatever does not fit waits in /snobblegems claim
                while (left > 0) {
                    int n = Math.min(left, new ItemStack(it).getMaxStackSize());
                    left -= n;
                    ItemStack s = new ItemStack(it, n);
                    if (r.itemName != null && !r.itemName.isBlank()) s.set(DataComponents.CUSTOM_NAME, Txt.parse(r.itemName));
                    p.getInventory().add(s);
                    if (!s.isEmpty()) {
                        mod.ledger.addPending(p.getUUID(), p.getGameProfile().getName(), "item", Delivery.encodeItem(mod.server.registryAccess(), s), "shop:" + pr.id);
                        waiting = true;
                    }
                }
                return waiting;
            }
            case "command" -> {
                Map<String, String> v = Map.of("player", p.getGameProfile().getName(), "uuid", p.getUUID().toString(), "product", pr.id, "amount", String.valueOf(Math.max(1, pr.amount)));
                boolean clean = true;
                for (String c : r.commands) clean &= mod.runRewardCommand(Txt.apply(c, v), p.getGameProfile().getName(), pr.id);
                if (!clean) mod.notify(p, Txt.parse("&e[SnobbleGems] Your purchase went through, but the server reported a problem while delivering it. If it did not arrive, tell the staff (they have the details)."));
                return false;
            }
            case "provider" -> {
                ShopProvider sp = extraOwner.get(pr.id);
                if (sp == null) throw new IllegalStateException("provider gone for " + pr.id);
                sp.deliver(p, extraRaw.get(pr.id));
                return false;
            }
            default -> throw new IllegalStateException("bad reward type " + r.type);
        }
    }

    /** Chest-menu purchase quote check for tests/tools. */
    public String whyNot(ServerPlayer p, Product pr) {
        Purchaseability pa = check(p, pr, mod.ledger.balance(p.getUUID()));
        return pa.reason == null ? null : pa.code;
    }

    /** Price/limit state for the quote step. */
    public String quoteProblem(ServerPlayer p, Product pr) {
        if (pr == null || !live(pr)) return "unavailable";
        if (!allowed(p, pr)) return "permission";
        return whyNot(p, pr);
    }
}
