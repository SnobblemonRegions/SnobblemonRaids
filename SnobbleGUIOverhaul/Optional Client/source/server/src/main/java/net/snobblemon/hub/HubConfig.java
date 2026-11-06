package net.snobblemon.hub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything staff can edit, as JSON in {@code config/snobblehub/}. A missing file is written with defaults on the first
 * start; an existing file is never overwritten. A file that cannot be parsed keeps the previous values and logs why.
 */
public final class HubConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // ---------------------------------------------------------------- config.json
    public static final class General {
        /** True = players without the Snobble client mod are disconnected on join (staff with snobblehub.bypass_client are let in). */
        public boolean require_client = false;
        /** Shown to a player kept out by require_client. */
        public String require_client_message = "This server needs the Snobblemon client mod (SnobbleHelper). Install the Snobblemon Regions modpack and join again.";
        /** The check waits this long after joining so the game has announced its mods. */
        public int require_client_grace_ticks = 40;
        public boolean f_key_opens_hub = true;
        public boolean sneak_f_swaps_hands = true;
        public int open_cooldown_ms = 400;
        /** The Wiki tab runs this command as the player; it should open the full SnobbleWiki WebGUI. */
        public String wiki_command = "wiki";
        public Teleport teleport = new Teleport();
        /** Highest first. A player gets the first rank whose group they are in; admin groups are simply not listed. */
        public List<Rank> ranks = defaultRanks();
        public String default_rank = "TRAINER";
        public List<Region> regions = defaultRegions();
        /** Where each trainer card number comes from: "%placeholder%" (PlaceholderAPI) or "impactor:<currency id>". */
        public Map<String, String> sources = defaultSources();
    }

    public static final class Teleport {
        public int warmup_seconds = 3;
        public int cooldown_seconds = 10;
        public boolean cancel_on_move = true;
        public boolean block_in_battle = true;
    }

    public static final class Rank {
        public String name;
        public List<String> groups = new ArrayList<>();
        Rank(String name, String... groups) { this.name = name; this.groups = new ArrayList<>(List.of(groups)); }
    }

    public static final class Region {
        public String id, name, group, accent;
        Region(String id, String name, String group, String accent) { this.id = id; this.name = name; this.group = group; this.accent = accent; }
    }

    private static List<Rank> defaultRanks() {
        return new ArrayList<>(List.of(
                new Rank("RADIANT", "radiant"), new Rank("LEGENDARY+", "legendaryplus", "legendary+", "legendary_plus"),
                new Rank("LEGENDARY", "legendary"), new Rank("SHINY+", "shinyplus", "shiny+", "shiny_plus"),
                new Rank("SHINY", "shiny"), new Rank("MVP", "mvp"), new Rank("VIP+", "vipplus", "vip+", "vip_plus"),
                new Rank("VIP", "vip"), new Rank("TRAINER+", "trainerplus", "trainer+", "trainer_plus")));
    }

    private static List<Region> defaultRegions() {
        return new ArrayList<>(List.of(new Region("rootvale", "ROOTVALE", "rootvale", "root"),
                new Region("tideforge", "TIDEFORGE", "tideforge", "tide"), new Region("duskvale", "DUSKVALE", "duskvale", "dusk")));
    }

    private static Map<String, String> defaultSources() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("raid_points", "%cobbleraids:points%");
        m.put("cobbledollars", "impactor:cobbledollars");
        m.put("gems", "%snobblegems:balance%");
        m.put("community_points", "%snobblemoncompanion:snobblepoints%");
        return m;
    }

    // ---------------------------------------------------------------- warps.json / gyms.json
    public static final class Destination {
        public String id, label, description, command;
        public boolean enabled;
        Destination(String id, String label, String description, String command, boolean enabled) {
            this.id = id; this.label = label; this.description = description; this.command = command; this.enabled = enabled;
        }
    }

    public static final class Gym {
        public String id, name, type, command;
        public boolean enabled = true;
        /** Optional LuckPerms node a player needs; without it the gym shows as locked. Leave empty for no lock. */
        public String permission = "";
        Gym(String id, String name, String type, String command) { this.id = id; this.name = name; this.type = type; this.command = command; }
    }

    // ---------------------------------------------------------------- wiki.json / updates.json
    public static final class WikiEntry {
        public String title;
        /** Opens this command instead of showing pages (for example "wiki" or "rules"). Empty = show the pages. */
        public String command = "";
        public List<String> pages = new ArrayList<>();
        WikiEntry(String title, String... pages) { this.title = title; this.pages = new ArrayList<>(List.of(pages)); }
    }

    public static final class Update {
        public String title = "", date = "", version = "";
        public List<String> added = new ArrayList<>(), changed = new ArrayList<>(), fixed = new ArrayList<>();
        public String id() { return date + "|" + title; }
    }

    // ---------------------------------------------------------------- loaded state
    public static volatile General general = new General();
    public static volatile List<Destination> warps = new ArrayList<>();
    public static volatile List<Gym> gyms = new ArrayList<>();
    public static volatile List<WikiEntry> wiki = new ArrayList<>();
    public static volatile List<Update> updates = new ArrayList<>();
    private static final Map<UUID, Set<String>> READ = new ConcurrentHashMap<>();

    private HubConfig() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("snobblehub");
    }

    public static void load() {
        general = read("config.json", General.class, new General(), general);
        warps = read("warps.json", new TypeToken<List<Destination>>() {}.getType(), defaultWarps(), warps);
        gyms = read("gyms.json", new TypeToken<List<Gym>>() {}.getType(), defaultGyms(), gyms);
        wiki = read("wiki.json", new TypeToken<List<WikiEntry>>() {}.getType(), defaultWiki(), wiki);
        updates = read("updates.json", new TypeToken<List<Update>>() {}.getType(), defaultUpdates(), updates);
        loadState();
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(String file, Type type, T defaults, T previous) {
        Path p = dir().resolve(file);
        try {
            Files.createDirectories(dir());
            if (!Files.exists(p)) {
                Files.writeString(p, GSON.toJson(defaults), StandardCharsets.UTF_8);
                return defaults;
            }
            T v = (T) GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), type);
            return v == null ? defaults : v;
        } catch (Exception e) {
            SnobbleHub.LOGGER.error("{} could not be read, keeping the previous values: {}", file, e.toString());
            return previous;
        }
    }

    private static <T> T read(String file, Class<T> type, T defaults, T previous) {
        return read(file, (Type) type, defaults, previous);
    }

    private static List<Destination> defaultWarps() {
        return new ArrayList<>(List.of(
                new Destination("resource", "RESOURCE", "Mine and gather resources.", "warp resource", false),
                new Destination("end", "END", "The End dimension.", "warp end", true),
                new Destination("nether", "NETHER", "The Nether dimension.", "warp nether", false),
                new Destination("wild", "WILD", "The open Overworld.", "warp wild", true),
                new Destination("gyms", "GYMS", "Challenge the gym leaders.", "", true)));
    }

    private static List<Gym> defaultGyms() {
        return new ArrayList<>(List.of(
                new Gym("brock", "Brock", "Rock", "warp Brock"), new Gym("misty", "Misty", "Water", "warp Misty"),
                new Gym("ltsurge", "Lt. Surge", "Electric", "warp LtSurge"), new Gym("gardenia", "Gardenia", "Grass", "warp Gardenia"),
                new Gym("koga", "Koga", "Poison", "warp Koga"), new Gym("sabrina", "Sabrina", "Psychic", "warp Sabrina"),
                new Gym("blaine", "Blaine", "Fire", "warp Blaine")));
    }

    private static List<WikiEntry> defaultWiki() {
        return new ArrayList<>(List.of(
                new WikiEntry("BASICS", "&6Trainer Hub&r\nPress &eF&r to open the hub. &eShift + F&r swaps your hands as usual.\n\nTabs: Inventory, Wiki, Cosmetics, Trainer Card, Warps and Updates.",
                        "&6This is sample text.&r\nEdit &econfig/snobblehub/wiki.json&r to write your own pages. Use the & sign for colour codes."),
                new WikiEntry("REGIONS", "&6Three Regions, One Community&r\n\n&aRootvale&r: Forest and Grass\n&bTideforge&r: Ocean and Water\n&5Duskvale&r: Mountain and Dark"),
                new WikiEntry("RAIDS", "&6Raids&r\nSample page. Describe your raids here in config/snobblehub/wiki.json."),
                new WikiEntry("GYMS", "&6Gyms&r\nOpen the &eWarps&r tab, then &eGyms&r, to visit a gym."),
                new WikiEntry("COSMETIC", "&6Cosmetics&r\nOpen the &eCosmetics&r tab. Click an item to preview it. Click &eTry on&r to see it on yourself, and &eBuy&r to unlock it with Gems."),
                new WikiEntry("RULES", "&6Rules&r\nSample page. Set &ecommand&r to &erules&r in wiki.json to open your rules instead.")));
    }

    private static List<Update> defaultUpdates() {
        Update u = new Update();
        u.title = "Example update";
        u.date = java.time.LocalDate.now().toString();
        u.version = "";
        u.added.add("This is an example entry.");
        u.changed.add("Edit config/snobblehub/updates.json to write real ones.");
        u.fixed.add("Newest entries go first in the file.");
        return new ArrayList<>(List.of(u));
    }

    // ---------------------------------------------------------------- unread updates (per player)
    private static Path stateFile() {
        return dir().resolve("state.json");
    }

    private static void loadState() {
        try {
            if (!Files.exists(stateFile())) return;
            Map<String, List<String>> m = GSON.fromJson(Files.readString(stateFile(), StandardCharsets.UTF_8), new TypeToken<Map<String, List<String>>>() {}.getType());
            READ.clear();
            if (m != null) m.forEach((k, v) -> { Set<String> set = ConcurrentHashMap.newKeySet(); set.addAll(v); READ.put(UUID.fromString(k), set); });
        } catch (Exception e) {
            SnobbleHub.LOGGER.warn("state.json could not be read: {}", e.toString());
        }
    }

    public static synchronized void saveState() {
        try {
            Map<String, List<String>> m = new HashMap<>();
            READ.forEach((k, v) -> m.put(k.toString(), new ArrayList<>(v)));
            Files.createDirectories(dir());
            Files.writeString(stateFile(), GSON.toJson(m), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SnobbleHub.LOGGER.warn("state.json could not be written: {}", e.toString());
        }
    }

    public static boolean isRead(UUID player, Update u) {
        Set<String> s = READ.get(player);
        return s != null && s.contains(u.id());
    }

    public static void markRead(UUID player, Update u) {
        if (READ.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(u.id())) saveState();
    }
}
