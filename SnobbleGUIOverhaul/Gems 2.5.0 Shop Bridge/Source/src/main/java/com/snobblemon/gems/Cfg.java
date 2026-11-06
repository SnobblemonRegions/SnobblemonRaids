package com.snobblemon.gems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config/snobblegemcurrency/config.json. Every field has a default, so a missing or half-written file still loads;
 * the file is rewritten with all fields (and the user's values) after loading so new options show up.
 */
public class Cfg {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int configVersion = 3;
    public Currency currency = new Currency();
    public Logging logging = new Logging();
    public Sounds sounds = new Sounds();
    public Effects effects = new Effects();
    public Chat chat = new Chat();
    public Exchange exchange = new Exchange();
    public Tebex tebex = new Tebex();
    public Ticket ticket = new Ticket();
    public LegendaryTicket legendaryTicket = new LegendaryTicket();
    public Gui gui = new Gui();
    public Placeholders placeholders = new Placeholders();
    public Map<String, String> messages = defaultMessages();

    public static class Currency {
        /** Plural display name. */
        public String name = "Gems";
        public String singular = "Gem";
        /** Used in chat messages. A normal character on purpose: it shows for players who declined the resource pack, too. */
        public String chatIcon = "&b◆";
        public long startingBalance = 0;
        /** Hard cap per player. A credit that would pass it is cut down to fit (and logged). */
        public long maxBalance = 1_000_000_000L;
    }

    public static class Logging {
        /** One console line per balance change. The database keeps the full audit trail either way. */
        public boolean transactionsToConsole = true;
        public boolean chatRewardsToConsole = false;
    }

    public static class Sounds {
        public boolean enabled = true;
        public String earn = "minecraft:entity.experience_orb.pickup";
        public String purchase = "minecraft:entity.player.levelup";
        public String error = "minecraft:block.note_block.bass";
        public String click = "minecraft:ui.button.click";
        public String ticket = "minecraft:entity.player.levelup";
        public float volume = 0.7f;
        /**
         * The "magical" purchase jingle: each step plays one sound after {delay} ticks (20 ticks = 1 second) at its own volume and pitch.
         * Empty list = just the single "purchase" sound above. Any sound id works, vanilla or from a mod.
         */
        public List<Step> purchaseSequence = defaultSequence();
    }

    public static class Step {
        public String sound = "minecraft:block.amethyst_block.chime";
        public int delay = 0;
        public float volume = 1.0f;
        public float pitch = 1.0f;

        public Step() {}

        public Step(String sound, int delay, float volume, float pitch) {
            this.sound = sound; this.delay = delay; this.volume = volume; this.pitch = pitch;
        }
    }

    public static List<Step> defaultSequence() {
        return new ArrayList<>(Arrays.asList(
                new Step("minecraft:block.enchantment_table.use", 0, 0.7f, 1.3f),
                new Step("minecraft:block.amethyst_block.chime", 0, 1.0f, 1.0f),
                new Step("minecraft:block.amethyst_block.chime", 2, 1.0f, 1.26f),
                new Step("minecraft:block.amethyst_block.chime", 4, 1.0f, 1.5f),
                new Step("minecraft:block.amethyst_block.chime", 6, 1.0f, 2.0f),
                new Step("minecraft:entity.firework_rocket.twinkle", 5, 0.8f, 1.0f),
                new Step("minecraft:block.amethyst_block.resonate", 8, 0.8f, 1.2f),
                new Step("minecraft:block.beacon.power_select", 9, 0.5f, 1.8f)));
    }

    public static class Effects {
        /** Sparkles around the player when a purchase goes through. */
        public boolean purchaseParticles = true;
    }

    public static class Chat {
        /** AUTO: SnobblemonCompanion decides what counts when it is installed, otherwise the built-in listener does. BUILTIN / COMPANION / OFF force it. */
        public String source = "AUTO";
        public WKind w = new WKind();
        public WelcomeKind welcome = new WelcomeKind();
        /** The same player sending the same text again within this many seconds earns nothing. */
        public int duplicateMessageSeconds = 60;
        /** Messages longer than this are never community messages. */
        public int maxMessageLength = 40;
        public Daily dailyLimit = new Daily();
        /** How an earned reward is shown: ACTIONBAR, CHAT or NONE. */
        public String notify = "ACTIONBAR";

    }

    public static class Kind {
        public boolean enabled = true;
        public long reward;
        public int cooldownSeconds;
        /** ANY: counts whenever typed. WINDOW (W): only counts while a window is open (see /snobblegems window). NEW_PLAYER (welcome): only counts while a brand-new player's welcome window is open. */
        public String mode;
        public int windowSeconds = 60;
        public String pattern;
        /** Welcome windows: each greeter can be paid once per new player. */
        public boolean oncePerWindow = true;

        public Kind() {}

        public Kind(long reward, int cooldown, String mode, String pattern) {
            this.reward = reward;
            this.cooldownSeconds = cooldown;
            this.mode = mode;
            this.pattern = pattern;
        }
    }

    /** Separate classes so a half-written config.json (only "reward" given, say) still starts from this kind's own defaults. */
    public static class WKind extends Kind {
        public WKind() {
            super(2, 300, "ANY", "(?i)^\\s*w{1,12}\\s*[!.]*\\s*$");
            windowSeconds = 90;
        }
    }

    public static class WelcomeKind extends Kind {
        public WelcomeKind() {
            super(5, 300, "NEW_PLAYER", "(?i)^\\s*welcome(\\s+[\\w.]{1,24}){0,3}\\s*[!.]*\\s*$");
            windowSeconds = 60;
        }
    }

    public static class Daily {
        public boolean enabled = true;
        /** Most Gems one player can earn from chat rewards per day. 0 = no limit. */
        public long perPlayer = 100;
        /** Day boundary. */
        public String timezone = "UTC";
    }

    /** Spend SnobblemonCompanion Community Points to buy Gems. All of it is read live; /snobblegems reload applies a change. */
    public static class Exchange {
        public boolean enabled = true;
        /** The Community Points symbol shown in chat and menus (a rainbow of coloured dots). Any & colour codes / symbol you like. */
        public String pointsIcon = "&c●&6●&e●&a●&9●";
        /** Community Points a player pays for ONE Gem. 10 = 100 Gems cost 1,000 points. */
        public long pointsPerGem = 10;
        /** Smallest / largest number of Gems in one trade (0 = no maximum). */
        public long minGems = 1;
        public long maxGemsPerTrade = 100;
        /** Most Gems one player may buy per day (0 = unlimited). */
        public long dailyGemLimit = 500;
        /** Most Community Points one player may spend per day (0 = unlimited). */
        public long dailyPointsLimit = 0;
        /** Gem amounts for the ready-made shop buttons (added to shop.json once). Edit shop.json afterwards. */
        public List<Integer> shopPacks = new ArrayList<>(List.of(10, 25, 50, 100));
        /** Add the "Gem Exchange" category to the shop (once). */
        public boolean addToShop = true;
    }

    public static class Tebex {
        /** Credit commands that carry an order id ({transaction}) are applied once; a repeat is ignored and logged. */
        public boolean duplicateProtection = true;
        public long maxGiveAmount = 10_000_000L;
        /** Tell the player (if online) about a purchase credit. */
        public boolean notifyPlayer = true;
        /** If a name cannot be turned into a UUID (never joined, Mojang unreachable), hold the reward under that name and deliver it when someone with that name joins. */
        public boolean holdUnresolvedNames = true;
    }

    public static class Ticket {
        public String displayName = "&d✨ Shiny Boost Ticket";
        public List<String> lore = new ArrayList<>(Arrays.asList(
                "&7A ticket for a server-wide shiny boost.",
                "",
                "&e▶ &fHow to use",
                "&7 Hold this ticket and &aRight-Click&7.",
                "&7 The Shiny Boost starts for &eeveryone&7.",
                "",
                "&8Ticket ID: {short}",
                "&8One use. Cannot be copied."));
        /** Trial key: no crafting recipe uses it (picking one up never shows the "New Recipes Unlocked" toast) and nothing in the Polymer pack retextures it. The resource pack changes how it looks. Tickets issued earlier (echo shards in 1.0, breeze rods in 2.0) keep working and keep their look. */
        public String baseItem = "minecraft:trial_key";
        public int customModelData = 7420002;
        public boolean glint = true;
        /** Ask for a second right-click so a stray click never burns a ticket. */
        public boolean confirmClick = true;
        public int confirmSeconds = 8;
        /** AUTO picks SnobblemonCompanion (stacking boosts) if installed, otherwise Cobblemon Boosters directly. COMPANION / BOOSTERS / COMMANDS force one. */
        public String activation = "AUTO";
        public String companionCommand = "companion shiny add";
        /** BOOSTERS mode. Cobblemon Boosters queues boosts, it does not stack them. */
        public List<String> boostersCommands = new ArrayList<>(List.of("boosters shiny start {multiplier} {seconds} seconds"));
        public double multiplier = 5;
        public int minutes = 5;
        /** COMMANDS mode (or extra things to run after any activation). Variables: {player} {uuid} {multiplier} {seconds} {minutes} */
        public List<String> commands = new ArrayList<>();
        public String broadcast = "&d✨ &f{player} &7used a &dShiny Boost Ticket&7!";
        public boolean broadcastEnabled = true;
    }

    /** The Legendary Boost Ticket: our own server-wide shiny-chance boost (see LegendaryBoost), independent of Cobblemon Boosters. */
    public static class LegendaryTicket {
        public String displayName = "&6✨ Legendary Boost Ticket";
        public List<String> lore = new ArrayList<>(Arrays.asList(
                "&7A ticket for a server-wide legendary boost.",
                "",
                "&e▶ &fHow to use",
                "&7 Hold this ticket and &aRight-Click&7.",
                "&7 Legendary Pokémon are &e{multiplier}x &7more likely",
                "&7 to be shiny for &e{minutes} minutes&7, for &eeveryone&7.",
                "",
                "&8Ticket ID: {short}",
                "&8One use. Cannot be copied."));
        public String baseItem = "minecraft:trial_key";
        public int customModelData = 7420003;
        public boolean glint = true;
        public boolean confirmClick = true;
        public int confirmSeconds = 8;
        /** Shiny chance multiplier while the boost runs. */
        public double multiplier = 2;
        /** Minutes one ticket lasts. Using another while it runs adds the minutes (the multiplier does not stack). */
        public double minutes = 5;
        /** true: only Pokemon carrying one of {@link #labels} get the boost. false: every Pokemon (a plain server-wide shiny boost). */
        public boolean legendaryOnly = true;
        /** Cobblemon species labels that count as legendary. */
        public List<String> labels = new ArrayList<>(List.of("legendary", "mythical", "ultra_beast", "restricted", "paradox"));
        public String broadcast = "&6✨ &f{player} &7used a &6Legendary Boost Ticket&7! &e{multiplier}x &7shiny chance on legendaries for &e{minutes} minutes&7.";
        public boolean broadcastEnabled = true;
    }

    public static class Gui {
        public String shopTitle = "Gem Shop";
        /**
         * AUTO: players whose game has the WebGUI mod get the web Gem Shop, everyone else the classic chest menu.
         * WEBGUI: web shop only (players without WebGUI are told). CHEST: always the chest menu.
         */
        public String mode = "AUTO";
        /** Where the page lives on the server: config/webgui/web/snobblegems/shop.html. WebGUI 2.0 needs the double slash (webgui://); the old webgui:/ form is corrected automatically. */
        public String pageUrl = "webgui://snobblegems/shop.html";
        /** The page pings every 4 s while open; silence for this long closes its session. */
        public int pageTimeoutSeconds = 14;
        /** Seconds a purchase quote stays valid (page: Confirm button; chest: confirm screen). */
        public int confirmSeconds = 30;
        /** Chest menu only: Gem / ticket icons from the v2 resource pack (false = plain vanilla icons, no pack needed). */
        public boolean customIcons = true;
        /**
         * Page animations: PURCHASE (default: entrance glide + the purchase celebration, nothing moves while idle), FULL (adds a slow prismatic shimmer and
         * twinkles; the embedded browser copies every repaint to the game and in a real-client test this made the page slow to react, so only use it on
         * strong PCs), OFF (completely still).
         */
        public String animations = "PURCHASE";
        /** Shop products may run console commands. These live only in shop.json on the server disk. false = command products are disabled. */
        public boolean allowCommandRewards = true;
        /**
         * A shop command that throws an exception while it is running (not a bad command: that always refunds) has usually already done its job - SkiesSkins gives
         * the skin and then crashes sending its own message. false (default): no refund, the problem is logged for the staff. true: refund the Gems anyway.
         */
        public boolean refundOnCommandCrash = false;
    }

    public static class Placeholders {
        /** %snobblegems:balance% prints 1,250 (true) or 1250 (false). balance_raw is always plain. */
        public boolean formatBalance = true;
    }

    public static Map<String, String> defaultMessages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("prefix", "&8[&b◆&8] ");
        m.put("balance_self", "{prefix}&7You have &b{balance} &7{currency}.");
        m.put("balance_other", "{prefix}&f{player} &7has &b{balance} &7{currency}.");
        m.put("given", "{prefix}&7Gave &b{amount} &7{currency} to &f{player}&7. New balance: &b{balance}");
        m.put("taken", "{prefix}&7Took &b{amount} &7{currency} from &f{player}&7. New balance: &b{balance}");
        m.put("set", "{prefix}&7Set &f{player}&7's balance to &b{balance}&7.");
        m.put("received", "{prefix}&7You received &b+{amount} &7{currency}. Balance: &b{balance}");
        m.put("purchase_thanks", "{prefix}&7Thank you for your purchase! &b+{amount} {currency}&7 added. Balance: &b{balance}");
        m.put("lost", "{prefix}&b-{amount} &7{currency} were removed. Balance: &b{balance}");
        m.put("insufficient", "{prefix}&cNot enough {currency}. You have {balance}, you need {price}.");
        m.put("duplicate", "{prefix}&eIgnored: order {ref} was already processed.");
        m.put("no_such_player", "{prefix}&cCould not find a player called &f{player}&c.");
        m.put("invalid_amount", "{prefix}&cThat amount is not valid.");
        m.put("capped", "{prefix}&eBalance limit reached: only {amount} {currency} were added.");
        m.put("no_permission", "{prefix}&cYou do not have permission to do that.");
        m.put("reloaded", "{prefix}&7Configuration reloaded. {problems}");
        m.put("community_reward", "&b◆ &7+{amount} {currency} &8({reason})");
        m.put("ticket_given", "{prefix}&7Gave &d{amount} Shiny Boost Ticket(s) &7to &f{player}&7{where}.");
        m.put("ticket_received", "{prefix}&dYou received {amount} ✨ Shiny Boost Ticket(s)! &7Hold one and right-click to use it.");
        m.put("ticket_pending", "{prefix}&eYou have {amount} reward(s) waiting but your inventory is full. Free some space and type &f/snobblegems claim&e.");
        m.put("ticket_confirm", "{prefix}&eRight-click again within {seconds} seconds to use this &dShiny Boost Ticket&e.");
        m.put("ticket_used", "{prefix}&dShiny Boost Ticket used! &7The boost is starting.");
        m.put("ticket_invalid", "{prefix}&cThis ticket is not valid or was already used.");
        m.put("ticket_unavailable", "{prefix}&cShiny boosts are not available right now. Your ticket was not used.");
        m.put("lticket_given", "{prefix}&7Gave &6{amount} Legendary Boost Ticket(s) &7to &f{player}&7{where}.");
        m.put("lticket_received", "{prefix}&6You received {amount} ✨ Legendary Boost Ticket(s)! &7Hold one and right-click to use it.");
        m.put("lticket_confirm", "{prefix}&eRight-click again within {seconds} seconds to use this &6Legendary Boost Ticket&e.");
        m.put("lticket_used", "{prefix}&6Legendary Boost Ticket used! &7The boost is running.");
        m.put("lticket_unavailable", "{prefix}&cThe legendary boost is not available right now. Your ticket was not used.");
        m.put("claimed", "{prefix}&7Delivered &f{amount} &7waiting reward(s).");
        m.put("nothing_to_claim", "{prefix}&7You have nothing waiting.");
        m.put("buy_ok", "{prefix}&aPurchased &f{product}&a for &b{price} &a{currency}. Balance: &b{balance}");
        m.put("buy_delivered_later", "{prefix}&eYour inventory is full. &f{product} &eis waiting: free space and type &f/snobblegems claim&e.");
        m.put("buy_failed", "{prefix}&cThat purchase could not be completed. You were not charged.");
        m.put("buy_unavailable", "{prefix}&cThat item is not available.");
        m.put("buy_limit", "{prefix}&cYou have reached the purchase limit for that item.");
        m.put("buy_no_permission", "{prefix}&cYou cannot buy that item.");
        m.put("window_opened", "{prefix}&7Opened a &f{kind}&7 window for {seconds}s.");
        m.put("shop_disabled", "{prefix}&cThe Gem Shop has no items yet.");
        m.put("web_required", "{prefix}&eThe Gem Shop page needs the WebGUI mod. Type &f/gemshop chest&e for the classic menu.");
        m.put("quote_expired", "{prefix}&eThat confirmation expired. Pick the item again.");
        m.put("price_changed", "{prefix}&eThe price changed. Please confirm again.");
        return m;
    }

    // ------------------------------------------------------------------ loading

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("snobblegemcurrency");
    }

    public static Cfg load(List<String> problems) {
        return load(problems, null);
    }

    /** Like load, but a file that cannot be read keeps {@code previous} active instead of falling back to the defaults. */
    public static Cfg load(List<String> problems, Cfg previous) {
        Path f = dir().resolve("config.json");
        Cfg c = null;
        try {
            Files.createDirectories(dir());
            if (Files.exists(f)) {
                String txt = Files.readString(f, StandardCharsets.UTF_8);
                c = GSON.fromJson(txt, Cfg.class);
                // keep user-added message keys but fill any missing defaults
                if (c != null) {
                    Map<String, String> merged = defaultMessages();
                    if (c.messages != null) merged.putAll(c.messages);
                    c.messages = merged;
                }
            }
        } catch (Exception e) {
            problems.add("config.json could not be read (" + e.getMessage() + "); keeping " + (previous != null ? "the previous settings" : "the defaults") + ". Your file was left untouched.");
            return previous != null ? previous : new Cfg();
        }
        if (c == null) c = new Cfg();
        // version 1 -> 2: the ticket base item default changed from echo_shard (recipe-book toast) to breeze_rod. Only an untouched old default is moved.
        if (c.configVersion < 2) {
            if (c.ticket != null && "minecraft:echo_shard".equals(c.ticket.baseItem)) c.ticket.baseItem = "minecraft:breeze_rod";
            c.configVersion = 2;
        }
        // version 2 -> 3: breeze rods turned out to unlock the wind-charge recipe (toast), so untouched defaults move to the trial key
        if (c.configVersion < 3) {
            if (c.ticket != null && ("minecraft:breeze_rod".equals(c.ticket.baseItem) || "minecraft:echo_shard".equals(c.ticket.baseItem))) c.ticket.baseItem = "minecraft:trial_key";
            c.configVersion = 3;
        }
        c.sanitize();
        try {
            writeAtomic(f, GSON.toJson(c));
        } catch (IOException e) {
            problems.add("config.json could not be written: " + e.getMessage());
        }
        return c;
    }

    void sanitize() {
        if (currency == null) currency = new Currency();
        if (currency.maxBalance <= 0) currency.maxBalance = 1_000_000_000L;
        if (currency.startingBalance < 0) currency.startingBalance = 0;
        if (logging == null) logging = new Logging();
        if (sounds == null) sounds = new Sounds();
        if (sounds.purchaseSequence == null) sounds.purchaseSequence = defaultSequence();
        if (effects == null) effects = new Effects();
        if (tebex == null) tebex = new Tebex();
        if (ticket == null) ticket = new Ticket();
        if (legendaryTicket == null) legendaryTicket = new LegendaryTicket();
        if (legendaryTicket.confirmSeconds < 2) legendaryTicket.confirmSeconds = 8;
        if (legendaryTicket.baseItem == null || legendaryTicket.baseItem.isBlank()) legendaryTicket.baseItem = "minecraft:trial_key";
        if (legendaryTicket.multiplier <= 0) legendaryTicket.multiplier = 2;
        if (legendaryTicket.minutes <= 0) legendaryTicket.minutes = 5;
        if (legendaryTicket.labels == null) legendaryTicket.labels = new ArrayList<>();
        if (gui == null) gui = new Gui();
        if (gui.animations == null || !java.util.List.of("FULL", "PURCHASE", "OFF").contains(gui.animations.toUpperCase())) gui.animations = "PURCHASE";
        if (placeholders == null) placeholders = new Placeholders();
        if (chat == null) chat = new Chat();
        if (exchange == null) exchange = new Exchange();
        if (exchange.pointsPerGem < 1) exchange.pointsPerGem = 1;
        if (exchange.minGems < 1) exchange.minGems = 1;
        if (exchange.maxGemsPerTrade < 0) exchange.maxGemsPerTrade = 0;
        if (exchange.dailyGemLimit < 0) exchange.dailyGemLimit = 0;
        if (exchange.dailyPointsLimit < 0) exchange.dailyPointsLimit = 0;
        if (exchange.shopPacks == null) exchange.shopPacks = new ArrayList<>();
        if (chat.w == null) chat.w = new WKind();
        if (chat.welcome == null) chat.welcome = new WelcomeKind();
        for (Kind k : new Kind[]{chat.w, chat.welcome}) {
            if (k.reward < 0) k.reward = 0;
            if (k.cooldownSeconds < 0) k.cooldownSeconds = 0;
            if (k.windowSeconds < 1) k.windowSeconds = 60;
            if (k.mode == null) k.mode = "ANY";
            if (k.pattern == null || k.pattern.isBlank()) k.pattern = k == chat.w ? new WKind().pattern : new WelcomeKind().pattern;
        }
        if (chat.dailyLimit == null) chat.dailyLimit = new Daily();
        if (ticket.confirmSeconds < 2) ticket.confirmSeconds = 8;
        if (ticket.baseItem == null || ticket.baseItem.isBlank()) ticket.baseItem = "minecraft:trial_key";
    }

    public static void writeAtomic(Path f, String text) throws IOException {
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------------ helpers

    public String msg(String key) {
        String s = messages.get(key);
        return s == null ? key : s.replace("{prefix}", messages.getOrDefault("prefix", ""));
    }
}
