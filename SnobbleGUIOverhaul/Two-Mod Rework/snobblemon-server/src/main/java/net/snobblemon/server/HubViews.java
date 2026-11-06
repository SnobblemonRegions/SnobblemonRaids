package net.snobblemon.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.snobblemon.server.AscViews.bool;
import static net.snobblemon.server.AscViews.num;
import static net.snobblemon.server.AscViews.str;

/**
 * The Snobble Hub: one native page with a tab strip. Everything staff may want to change lives in config/snobblemon/hub/ (hub.json = tabs, colours,
 * command, teleport rules; warps.json = the warp list). Both files are re-read when they change, so edits need no restart.
 * Tabs: Inventory (view-only), Cosmetics (compact; the cosmetics mod still owns ownership and equipping), Warps (config list), and link tabs that
 * open another native page or run a command. The layout itself is data/snobblemon_server/ui/hub.json (overridable from config/snobblemon/ui/).
 */
final class HubViews {
    private HubViews() {}

    static final String PAGE = "hub";
    private static final String CHANNEL = "snobblecosmetics";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] RCOLOR = {"#C1C5CF", "#1BD7E8", "#38CCEF", "#AF69FF", "#FFD437", "#FF3448"};
    private static final String[][] CATS = {{"hats", "Hats"}, {"back", "Back"}, {"armor", "Armor"}, {"tools", "Tool Skins"}, {"pokemon", "Pokemon Skins"}, {"balloons", "Balloons"}, {"effects", "Effects"}};
    private static final Map<UUID, JsonObject> COS = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ config
    private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("snobblemon").resolve("hub"); }

    private static JsonObject cfg = new JsonObject();
    private static JsonArray warps = new JsonArray();
    private static long cfgStamp = -1, warpStamp = -1;

    private static JsonObject defaultConfig() {
        JsonObject c = new JsonObject();
        c.addProperty("title", "SNOBBLE HUB");
        c.addProperty("subtitle", "Everything in one place");
        c.addProperty("command", "snobblehub");
        c.addProperty("button_opens_hub", true);
        JsonObject theme = new JsonObject();
        theme.addProperty("accent", "#6ee08a");
        theme.addProperty("background", "#0f1620");
        theme.addProperty("panel", "#131c27");
        c.add("theme", theme);
        JsonObject tp = new JsonObject();
        tp.addProperty("warmup_seconds", 3);
        tp.addProperty("cooldown_seconds", 10);
        tp.addProperty("cancel_on_move", true);
        tp.addProperty("block_in_battle", true);
        c.add("teleport", tp);
        JsonArray tabs = new JsonArray();
        tabs.add(tab("inventory", "Inventory", "minecraft:chest", "inventory", "", true));
        tabs.add(tab("cosmetics", "Cosmetics", "minecraft:diamond_helmet", "cosmetics", "", true));
        tabs.add(tab("warps", "Warps", "minecraft:ender_pearl", "warps", "", true));
        tabs.add(tab("wiki", "Wiki", "minecraft:writable_book", "page", "wiki", true));
        tabs.add(tab("card", "Trainer Card", "minecraft:name_tag", "command", "hub card", false));
        tabs.add(tab("updates", "Updates", "minecraft:paper", "command", "hub updates", false));
        c.add("tabs", tabs);
        JsonObject notes = new JsonObject();
        notes.addProperty("tab types", "inventory | cosmetics | warps (built in), page (open another native page; target = its id), command (run target as the player)");
        notes.addProperty("icon", "any item id, for example minecraft:ender_pearl or cobblemon:poke_ball");
        c.add("_help", notes);
        return c;
    }

    private static JsonObject tab(String id, String label, String icon, String type, String target, boolean enabled) {
        JsonObject t = new JsonObject();
        t.addProperty("id", id);
        t.addProperty("label", label);
        t.addProperty("icon", icon);
        t.addProperty("type", type);
        t.addProperty("target", target);
        t.addProperty("enabled", enabled);
        return t;
    }

    private static JsonArray defaultWarps() {
        JsonArray a = new JsonArray();
        Path old = FabricLoader.getInstance().getConfigDir().resolve("snobblehub").resolve("warps.json");   // start from SnobbleHub's list when there is one
        try {
            if (Files.isRegularFile(old)) {
                for (JsonElement e : JsonParser.parseString(Files.readString(old, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    a.add(warp(str(o, "id"), str(o, "label"), str(o, "description"), str(o, "command"), bool(o, "enabled"), iconFor(str(o, "id"))));
                }
                if (a.size() > 0) return a;
            }
        } catch (IOException | RuntimeException ignored) { }
        a.add(warp("wild", "WILD", "The open Overworld.", "warp wild", true, "minecraft:grass_block"));
        a.add(warp("end", "END", "The End dimension.", "warp end", true, "minecraft:end_stone"));
        a.add(warp("nether", "NETHER", "The Nether dimension.", "warp nether", false, "minecraft:netherrack"));
        a.add(warp("resource", "RESOURCE", "Mine and gather resources.", "warp resource", false, "minecraft:iron_pickaxe"));
        a.add(warp("gyms", "GYMS", "Challenge the gym leaders.", "", true, "minecraft:shield"));
        return a;
    }

    private static String iconFor(String id) {
        return switch (id) {
            case "end" -> "minecraft:end_stone";
            case "nether" -> "minecraft:netherrack";
            case "wild" -> "minecraft:grass_block";
            case "resource" -> "minecraft:iron_pickaxe";
            case "gyms" -> "minecraft:shield";
            default -> "minecraft:ender_pearl";
        };
    }

    private static JsonObject warp(String id, String label, String desc, String command, boolean enabled, String icon) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("label", label);
        o.addProperty("description", desc);
        o.addProperty("command", command);
        o.addProperty("page", "");
        o.addProperty("icon", icon);
        o.addProperty("color", "#6ee08a");
        o.addProperty("requires_op", false);
        o.addProperty("enabled", enabled);
        return o;
    }

    private static long stamp(Path f) { try { return Files.getLastModifiedTime(f).toMillis(); } catch (IOException e) { return -2; } }

    /** Loads (and on first start writes) both files; called on start, on /snobblemon reload and whenever a file's timestamp changed. */
    static synchronized void load() {
        Path d = dir(), c = d.resolve("hub.json"), w = d.resolve("warps.json");
        try {
            Files.createDirectories(d);
            if (!Files.exists(c)) Files.writeString(c, GSON.toJson(defaultConfig()), StandardCharsets.UTF_8);
            if (!Files.exists(w)) Files.writeString(w, GSON.toJson(defaultWarps()), StandardCharsets.UTF_8);
        } catch (IOException e) { SnobblemonServer.LOGGER.warn("Could not write the hub config: {}", e.toString()); }
        try { cfg = JsonParser.parseString(Files.readString(c, StandardCharsets.UTF_8)).getAsJsonObject(); } catch (IOException | RuntimeException e) { SnobblemonServer.LOGGER.warn("hub.json not read (keeping the previous values): {}", e.toString()); if (cfg.size() == 0) cfg = defaultConfig(); }
        try { warps = JsonParser.parseString(Files.readString(w, StandardCharsets.UTF_8)).getAsJsonArray(); } catch (IOException | RuntimeException e) { SnobblemonServer.LOGGER.warn("warps.json not read (keeping the previous list): {}", e.toString()); if (warps.size() == 0) warps = defaultWarps(); }
        cfgStamp = stamp(c);
        warpStamp = stamp(w);
    }

    private static void fresh() { if (stamp(dir().resolve("hub.json")) != cfgStamp || stamp(dir().resolve("warps.json")) != warpStamp) load(); }

    private static JsonObject obj(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject(); }
    private static JsonArray arr(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }
    private static String color(String c, String dflt) { return c != null && c.matches("#[0-9a-fA-F]{6}") ? c : dflt; }

    // ------------------------------------------------------------------ wiring
    static void register() {
        load();
        SnobbleUi.registerView(PAGE, HubViews::apply);
        ServerTickEvents.END_SERVER_TICK.register(HubViews::tick);
        CommandRegistrationCallback.EVENT.register((d, access, env) -> {
            String alias = str(cfg, "command").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
            if (!alias.isEmpty()) d.register(Commands.literal(alias).executes(c -> { ServerPlayer p = c.getSource().getPlayerOrException(); open(p); return 1; }));
            d.register(Commands.literal("snobblemon").then(Commands.literal("hub").executes(c -> { ServerPlayer p = c.getSource().getPlayerOrException(); open(p); return 1; })));
        });
        // the Hub button on the inventory screen (client mod) asks for a page over the hub channel; answer it when SnobbleHub is not installed to do so
        if (!FabricLoader.getInstance().isModLoaded("snobblehub") && !cfg.has("button_opens_hub") || !FabricLoader.getInstance().isModLoaded("snobblehub") && bool(cfg, "button_opens_hub")) {
            net.snobblemon.hubnet.HubNet.registerTypes();
            ServerPlayNetworking.registerGlobalReceiver(net.snobblemon.hubnet.HubNet.Request.TYPE, (payload, ctx) -> ctx.server().execute(() -> {
                if (payload.action().equals("page")) open(ctx.player());
            }));
        }
    }

    static String reload() { load(); return "Hub config reloaded (" + enabledTabs().size() + " tab(s), " + warps.size() + " warp(s))"; }

    static void open(ServerPlayer p) {
        fresh();
        if (!SnobbleUi.open(p, PAGE)) { p.sendSystemMessage(Component.literal("The hub needs the Snobblemon client mod.")); return; }
        SnobbleUi.emit(p, PAGE, "init", new JsonObject());
    }

    private static List<JsonObject> enabledTabs() {
        List<JsonObject> l = new ArrayList<>();
        for (JsonElement e : arr(cfg, "tabs")) if (e.isJsonObject() && !e.getAsJsonObject().has("enabled") || e.isJsonObject() && bool(e.getAsJsonObject(), "enabled")) l.add(e.getAsJsonObject());
        return l.size() > 8 ? l.subList(0, 8) : l;
    }

    private static JsonObject tabById(String id) { for (JsonObject t : enabledTabs()) if (str(t, "id").equals(id)) return t; return null; }

    // ------------------------------------------------------------------ events
    private static void apply(ServerPlayer p, String event, JsonObject s, JsonObject data) {
        fresh();
        JsonObject st = data.has("st") && data.get("st").isJsonObject() ? data.getAsJsonObject("st") : freshState();
        switch (event) {
            case "state" -> { if (s.has("items")) COS.put(p.getUUID(), s); }
            case "result" -> {
                if (!str(s, "message").isEmpty()) toast(data, str(s, "message"), bool(s, "ok"));
            }
            case "msg" -> message(p, st, s, data);
            default -> { }
        }
        data.add("st", st);
        data.add("vm", build(p, st));
    }

    private static JsonObject freshState() {
        JsonObject st = new JsonObject();
        List<JsonObject> t = enabledTabs();
        st.addProperty("tab", t.isEmpty() ? "" : str(t.get(0), "id"));
        st.addProperty("wpage", 0);
        st.addProperty("cpage", 0);
        st.addProperty("ccat", "all");
        return st;
    }

    private static void toast(JsonObject data, String text, boolean ok) {
        JsonObject t = new JsonObject();
        t.addProperty("text", text);
        t.addProperty("ok", ok);
        data.add("_toast", t);
    }

    private static void message(ServerPlayer p, JsonObject st, JsonObject m, JsonObject data) {
        String a = str(m, "action"), k = str(m, "k");
        switch (a) {
            case "tab" -> {
                JsonObject t = tabById(k);
                if (t == null) return;
                switch (str(t, "type")) {
                    case "inventory", "cosmetics", "warps" -> { st.addProperty("tab", k); st.addProperty("wpage", 0); st.addProperty("cpage", 0); }
                    case "page" -> { String target = str(t, "target"); if (SnobbleUi.hasPage(target)) { SnobbleUi.close(p, PAGE); SnobbleUi.open(p, target); } else toast(data, "That page is not available.", false); }
                    case "command" -> { String c = str(t, "target"); if (!c.isBlank()) { SnobbleUi.close(p, PAGE); run(p, c); } }
                    default -> { }
                }
            }
            case "close" -> SnobbleUi.close(p, PAGE);
            case "wpage" -> st.addProperty("wpage", Math.max(0, (int) num(st, "wpage") + (int) Math.signum(num(m, "d"))));
            case "cpage" -> st.addProperty("cpage", Math.max(0, (int) num(st, "cpage") + (int) Math.signum(num(m, "d"))));
            case "ccat" -> { st.addProperty("ccat", k.length() > 24 ? "all" : k); st.addProperty("cpage", 0); }
            case "wardrobe" -> { SnobbleUi.close(p, PAGE); if (!SnobbleUi.open(p, "wardrobe")) toast(data, "The wardrobe is not available.", false); }
            case "cos" -> cosClick(p, k, data);
            case "warp" -> warpClick(p, k, data);
            default -> { }
        }
    }

    private static void act(ServerPlayer p, String action, String id) {
        JsonObject m = new JsonObject();
        m.addProperty("action", action);
        m.addProperty("id", id);
        SnobbleUi.forward(p, CHANNEL, m);
    }

    private static JsonObject cosItem(ServerPlayer p, String id) {
        JsonObject state = COS.get(p.getUUID());
        if (state == null) return null;
        for (JsonElement e : arr(state, "items")) if (str(e.getAsJsonObject(), "id").equals(id)) return e.getAsJsonObject();
        return null;
    }

    private static boolean worn(JsonObject i) { return bool(i, "equipped") || num(i, "wornBy") > 0; }

    private static void cosClick(ServerPlayer p, String id, JsonObject data) {
        JsonObject i = cosItem(p, id);
        if (i == null) return;
        if (!bool(i, "owned")) { toast(data, "You do not own that yet. Get it in the wardrobe.", false); return; }
        if (!bool(i, "wearable")) { toast(data, "Open the full wardrobe for this one.", false); return; }
        act(p, worn(i) ? "unequip" : "equip", id);          // the cosmetics mod answers with a fresh "state" that redraws this tab
    }

    // ------------------------------------------------------------------ warps and teleporting
    private record Pending(String label, String command, Vec3 start, float health, long runAtTick) {}
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static long ticks;

    private static JsonObject warpById(String id) { for (JsonElement e : warps) if (e.isJsonObject() && str(e.getAsJsonObject(), "id").equals(id)) return e.getAsJsonObject(); return null; }

    private static void warpClick(ServerPlayer p, String id, JsonObject data) {
        JsonObject w = warpById(id);
        if (w == null || !bool(w, "enabled")) return;
        if (bool(w, "requires_op") && !p.hasPermissions(2)) { toast(data, "You cannot use that warp.", false); return; }
        String page = str(w, "page"), cmd = str(w, "command");
        if (!page.isEmpty()) { if (SnobbleUi.hasPage(page)) { SnobbleUi.close(p, PAGE); SnobbleUi.open(p, page); } else toast(data, "That page is not available.", false); return; }
        if (cmd.isBlank()) { toast(data, "That destination is not set up yet.", false); return; }
        JsonObject tp = obj(cfg, "teleport");
        int warm = tp.has("warmup_seconds") ? (int) num(tp, "warmup_seconds") : 3;
        long now = System.currentTimeMillis();
        if (PENDING.containsKey(p.getUUID())) { toast(data, "You are already teleporting.", false); return; }
        Long until = COOLDOWN.get(p.getUUID());
        if (until != null && until > now) { toast(data, "Wait " + ((until - now + 999) / 1000) + "s before teleporting again.", false); return; }
        if ((!tp.has("block_in_battle") || bool(tp, "block_in_battle")) && inBattle(p)) { toast(data, "You can't teleport during a battle.", false); return; }
        String label = str(w, "label");
        SnobbleUi.close(p, PAGE);
        if (warm <= 0) { go(p, cmd); return; }
        PENDING.put(p.getUUID(), new Pending(label, cmd, p.position(), p.getHealth(), ticks + warm * 20L));
        p.sendSystemMessage(Component.literal("Teleporting to " + label + " in " + warm + " seconds" + (!tp.has("cancel_on_move") || bool(tp, "cancel_on_move") ? ". Stand still!" : ".")));
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        if (PENDING.isEmpty()) return;
        JsonObject tp = obj(cfg, "teleport");
        boolean still = !tp.has("cancel_on_move") || bool(tp, "cancel_on_move"), battle = !tp.has("block_in_battle") || bool(tp, "block_in_battle");
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Pending pd = e.getValue();
            if (p == null) { it.remove(); continue; }
            if (p.getHealth() < pd.health) { it.remove(); p.sendSystemMessage(Component.literal("Teleport cancelled because you took damage.")); continue; }
            if (still && p.position().distanceToSqr(pd.start) > 0.64) { it.remove(); p.sendSystemMessage(Component.literal("Teleport cancelled because you moved.")); continue; }
            if (battle && inBattle(p)) { it.remove(); p.sendSystemMessage(Component.literal("Teleport cancelled: you are in a battle.")); continue; }
            if (ticks >= pd.runAtTick) { it.remove(); go(p, pd.command); }
        }
    }

    private static void go(ServerPlayer p, String command) {
        JsonObject tp = obj(cfg, "teleport");
        COOLDOWN.put(p.getUUID(), System.currentTimeMillis() + (tp.has("cooldown_seconds") ? num(tp, "cooldown_seconds") : 10) * 1000L);
        run(p, command);
    }

    private static void run(ServerPlayer p, String command) {
        String c = command.startsWith("/") ? command.substring(1) : command;
        p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), c);      // as the player: the target mod's own permission checks still apply
    }

    private static boolean inBattle(ServerPlayer p) {
        try {
            Class<?> c = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object inst = c.getField("INSTANCE").get(null);
            Object reg = c.getMethod("getBattleRegistry").invoke(inst);
            for (var m : reg.getClass().getMethods()) if (m.getName().equals("getBattleByParticipatingPlayer") && m.getParameterCount() == 1) return m.invoke(reg, p) != null;
        } catch (Throwable ignored) { }
        return false;
    }

    // ------------------------------------------------------------------ view model
    private static JsonObject build(ServerPlayer p, JsonObject st) {
        JsonObject theme = obj(cfg, "theme");
        String accent = color(str(theme, "accent"), "#6ee08a");
        JsonObject v = new JsonObject();
        v.addProperty("accent", accent);
        v.addProperty("bg", color(str(theme, "background"), "#0f1620"));
        v.addProperty("panel", color(str(theme, "panel"), "#131c27"));
        v.addProperty("title", str(cfg, "title").isEmpty() ? "SNOBBLE HUB" : str(cfg, "title"));
        v.addProperty("sub", p.getGameProfile().getName() + (str(cfg, "subtitle").isEmpty() ? "" : "   |   " + str(cfg, "subtitle")));
        String cur = str(st, "tab");
        JsonArray tabs = new JsonArray();
        String kind = "";
        for (JsonObject t : enabledTabs()) {
            JsonObject o = new JsonObject();
            boolean on = str(t, "id").equals(cur);
            if (on) kind = str(t, "type");
            o.addProperty("id", str(t, "id"));
            o.addProperty("label", str(t, "label"));
            o.addProperty("icon", str(t, "icon").isEmpty() ? "minecraft:paper" : str(t, "icon"));
            o.addProperty("bg", on ? "#1d3a2b" : "#16202b");
            o.addProperty("hv", "#22323f");
            o.addProperty("bd", on ? accent : "#2d3b4b");
            o.addProperty("col", on ? accent : "#e6edf3");
            tabs.add(o);
        }
        v.add("tabs", tabs);
        v.addProperty("isInv", kind.equals("inventory"));
        v.addProperty("isCos", kind.equals("cosmetics"));
        v.addProperty("isWarps", kind.equals("warps"));
        v.addProperty("eqNames", "Head          Chest         Legs          Feet          Offhand");
        v.add("slots", new JsonArray());
        v.add("stats", new JsonArray());
        v.add("warps", new JsonArray());
        v.addProperty("warpHint", "");
        v.addProperty("warpPage", "");
        v.add("cosCards", new JsonArray());
        v.add("cosCats", new JsonArray());
        v.addProperty("cosMsg", "");
        v.addProperty("cosWorn", "");
        v.addProperty("cosPage", "");
        if (kind.equals("inventory")) inventory(p, v);
        else if (kind.equals("warps")) warpsVm(p, st, v, accent);
        else if (kind.equals("cosmetics")) cosmeticsVm(p, st, v, accent);
        return v;
    }

    private static JsonObject slot(ItemStack s, int x, int y, String accent, boolean sel) {
        JsonObject o = new JsonObject();
        boolean has = !s.isEmpty();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("has", has);
        o.addProperty("bd", sel ? accent : "#2d3b4b");
        o.addProperty("item", has ? BuiltInRegistries.ITEM.getKey(s.getItem()).toString() : "minecraft:air");
        o.addProperty("many", has && s.getCount() > 1);
        o.addProperty("cnt", has ? String.valueOf(s.getCount()) : "");
        o.addProperty("name", has ? s.getHoverName().getString() : "");
        o.addProperty("l1", has && s.getCount() > 1 ? "x" + s.getCount() : "");
        o.addProperty("l2", has && s.isDamageableItem() ? "Durability " + (s.getMaxDamage() - s.getDamageValue()) + "/" + s.getMaxDamage() : "");
        return o;
    }

    private static void inventory(ServerPlayer p, JsonObject v) {
        var inv = p.getInventory();
        JsonArray slots = new JsonArray();
        String accent = v.get("accent").getAsString();
        for (int i = 0; i < 4; i++) slots.add(slot(inv.armor.get(3 - i), i * 72, 44, accent, false));      // head, chest, legs, feet
        slots.add(slot(inv.offhand.get(0), 4 * 72, 44, accent, false));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slots.add(slot(inv.items.get(9 + r * 9 + c), c * 66, 178 + r * 66, accent, false));
        for (int c = 0; c < 9; c++) slots.add(slot(inv.items.get(c), c * 66, 406, accent, c == inv.selected));
        v.add("slots", slots);
        int free = 0;
        for (ItemStack s : inv.items) if (s.isEmpty()) free++;
        JsonArray stats = new JsonArray();
        stats.add(kv("Level", String.valueOf(p.experienceLevel)));
        stats.add(kv("Health", String.format(Locale.ROOT, "%.0f / %.0f", p.getHealth(), p.getMaxHealth())));
        stats.add(kv("Food", String.valueOf(p.getFoodData().getFoodLevel())));
        stats.add(kv("Free slots", free + " / 36"));
        stats.add(kv("Holding", inv.getSelected().isEmpty() ? "nothing" : inv.getSelected().getHoverName().getString()));
        v.add("stats", stats);
    }

    private static JsonObject kv(String k, String val) {
        JsonObject o = new JsonObject();
        o.addProperty("k", k);
        o.addProperty("v", val.length() > 22 ? val.substring(0, 22) : val);
        return o;
    }

    private static void warpsVm(ServerPlayer p, JsonObject st, JsonObject v, String accent) {
        List<JsonObject> list = new ArrayList<>();
        for (JsonElement e : warps) if (e.isJsonObject()) list.add(e.getAsJsonObject());
        int per = 9, pages = Math.max(1, (list.size() + per - 1) / per), page = Math.min((int) num(st, "wpage"), pages - 1);
        st.addProperty("wpage", page);
        JsonArray out = new JsonArray();
        Long until = COOLDOWN.get(p.getUUID());
        long now = System.currentTimeMillis();
        boolean pend = PENDING.containsKey(p.getUUID());
        for (int n = 0; n < per && page * per + n < list.size(); n++) {
            JsonObject w = list.get(page * per + n), o = new JsonObject();
            boolean on = bool(w, "enabled"), locked = bool(w, "requires_op") && !p.hasPermissions(2);
            boolean ready = str(w, "command").isBlank() && str(w, "page").isEmpty();
            String status, sc;
            if (!on) { status = "UNAVAILABLE"; sc = "#ff7b7b"; }
            else if (locked) { status = "LOCKED"; sc = "#ff7b7b"; }
            else if (ready) { status = "NOT SET UP YET"; sc = "#ffd166"; }
            else if (pend) { status = "TELEPORTING..."; sc = "#ffd166"; }
            else if (until != null && until > now && str(w, "page").isEmpty()) { status = "COOLDOWN " + ((until - now + 999) / 1000) + "s"; sc = "#ffd166"; }
            else { status = "CLICK TO GO"; sc = accent; }
            String col = color(str(w, "color"), accent);
            o.addProperty("id", str(w, "id"));
            o.addProperty("x", (n % 3) * 320);
            o.addProperty("y", (n / 3) * 136);
            o.addProperty("label", str(w, "label"));
            o.addProperty("desc", str(w, "description"));
            o.addProperty("icon", str(w, "icon").isEmpty() ? "minecraft:ender_pearl" : str(w, "icon"));
            o.addProperty("col", on ? col : "#8a99a8");
            o.addProperty("status", status);
            o.addProperty("sc", sc);
            o.addProperty("bg", on ? "#16222d" : "#10171f");
            o.addProperty("hv", on ? "#1f3042" : "#10171f");
            o.addProperty("bd", on ? col : "#2d3b4b");
            out.add(o);
        }
        v.add("warps", out);
        v.addProperty("warpHint", "Click a destination. Stand still during the countdown.");
        v.addProperty("warpPage", "Page " + (page + 1) + " / " + pages);
    }

    private static int rank(JsonObject i) { return (int) Math.max(0, Math.min(5, num(i, "rank"))); }

    private static String thumb(JsonObject i) {
        String id = "thumb_" + str(i, "id").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (id.length() > 44) id = id.substring(0, 44);
        return "w:" + id + ":0";
    }

    private static void cosmeticsVm(ServerPlayer p, JsonObject st, JsonObject v, String accent) {
        JsonObject state = COS.get(p.getUUID());
        if (state == null) {
            v.addProperty("cosMsg", FabricLoader.getInstance().isModLoaded("snobblemon_cosmetics") ? "Loading your cosmetics..." : "The cosmetics mod is not installed on this server.");
            return;
        }
        List<JsonObject> owned = new ArrayList<>();
        java.util.LinkedHashSet<String> cats = new java.util.LinkedHashSet<>();
        List<String> wornNames = new ArrayList<>();
        for (JsonElement e : arr(state, "items")) {
            JsonObject i = e.getAsJsonObject();
            if (!bool(i, "owned")) continue;
            owned.add(i);
            cats.add(str(i, "cat"));
            if (worn(i)) wornNames.add(str(i, "name"));
        }
        String cat = str(st, "ccat");
        if (!cat.equals("all") && !cats.contains(cat)) { cat = "all"; st.addProperty("ccat", "all"); }
        JsonArray chips = new JsonArray();
        chips.add(chip("all", "All", cat.equals("all"), accent));
        for (String[] c : CATS) if (cats.contains(c[0])) chips.add(chip(c[0], c[1], cat.equals(c[0]), accent));
        v.add("cosCats", chips);
        List<JsonObject> shown = new ArrayList<>();
        for (JsonObject i : owned) if (cat.equals("all") || str(i, "cat").equals(cat)) shown.add(i);
        shown.sort((a, b) -> { int d = Boolean.compare(worn(b), worn(a)); return d != 0 ? d : (int) (num(b, "rank") - num(a, "rank")); });
        int per = 10, pages = Math.max(1, (shown.size() + per - 1) / per), page = Math.min((int) num(st, "cpage"), pages - 1);
        st.addProperty("cpage", page);
        JsonArray cards = new JsonArray();
        for (int n = 0; n < per && page * per + n < shown.size(); n++) {
            JsonObject i = shown.get(page * per + n), c = new JsonObject();
            boolean w = worn(i), wearable = bool(i, "wearable");
            c.addProperty("id", str(i, "id"));
            c.addProperty("x", (n % 5) * 192);
            c.addProperty("y", (n / 5) * 210);
            c.addProperty("thumb", thumb(i));
            c.addProperty("name", str(i, "name"));
            c.addProperty("nc", RCOLOR[rank(i)]);
            c.addProperty("sub", (str(i, "rarity").isEmpty() ? "" : str(i, "rarity") + "  ") + str(i, "slot"));
            c.addProperty("state", w ? "WORN" : "OWNED");
            c.addProperty("sc", w ? accent : "#8a99a8");
            c.addProperty("go", !wearable ? "OPEN WARDROBE" : w ? "CLICK TO TAKE OFF" : "CLICK TO EQUIP");
            c.addProperty("bg", w ? "#173325" : "#16222d");
            c.addProperty("hv", "#1f3042");
            c.addProperty("bd", w ? accent : "#2d3b4b");
            cards.add(c);
        }
        v.add("cosCards", cards);
        v.addProperty("cosWorn", wornNames.isEmpty() ? "Nothing equipped right now." : "Wearing: " + String.join(", ", wornNames));
        v.addProperty("cosPage", "Page " + (page + 1) + " / " + pages + "   |   " + shown.size() + " owned");
        if (shown.isEmpty()) v.addProperty("cosMsg", "You do not own any cosmetics here yet. Open the full wardrobe to browse and buy.");
    }

    private static JsonObject chip(String id, String label, boolean on, String accent) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("label", label);
        o.addProperty("bg", on ? "#1d3a2b" : "#16202b");
        o.addProperty("bd", on ? accent : "#2d3b4b");
        return o;
    }
}
