package com.snobblemon.gems;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Live shop editing: /gemshop admin ... and /snobblegems shop admin ... (same commands). Every change is applied at once (open shop pages update
 * within a second) and written to shop.json. Permissions: snobblegems.shop.edit (products, categories), snobblegems.shop.timer (timers), or
 * snobblegems.admin / operator level 3 / the console.
 */
final class ShopCmds {
    private ShopCmds() {}

    private static Predicate<CommandSourceStack> edit() { return s -> Perms.admin(s, Perms.SHOP_EDIT); }

    private static Predicate<CommandSourceStack> timer() { return s -> Perms.admin(s, Perms.SHOP_TIMER); }

    private static RequiredArgumentBuilder<CommandSourceStack, String> pid() {
        return argument("id", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(SnobbleGems.I.shop.products().stream().map(p -> p.id), b));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> newId() {
        return argument("id", word());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> itemArg() {
        return argument("item", ResourceLocationArgument.id()).suggests((c, b) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), b));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> catArg(String name) {
        return argument(name, word()).suggests((c, b) -> SharedSuggestionProvider.suggest(SnobbleGems.I.shop.categories().stream().map(x -> x.id), b));
    }

    private static void say(CommandSourceStack s, String legacy) {
        s.sendSuccess(() -> Txt.parse("&b◆ &7" + legacy), true);
    }

    private static int result(CommandSourceStack s, String problem, String okText) {
        if (problem != null) {
            s.sendFailure(Txt.parse("&c" + problem));
            return 0;
        }
        say(s, okText);
        return 1;
    }

    /** The "admin" branch, built fresh for each parent command. */
    static LiteralArgumentBuilder<CommandSourceStack> tree(SnobbleGems m) {
        Shop shop = m.shop;
        LiteralArgumentBuilder<CommandSourceStack> a = literal("admin").requires(s -> Perms.admin(s, Perms.SHOP_EDIT) || Perms.admin(s, Perms.SHOP_TIMER));

        a.then(literal("help").executes(c -> help(c.getSource())));
        a.executes(c -> help(c.getSource()));

        a.then(literal("list").requires(edit()).executes(c -> list(shop, c.getSource(), null))
                .then(catArg("category").executes(c -> list(shop, c.getSource(), getString(c, "category")))));
        a.then(literal("info").requires(edit()).then(pid().executes(c -> info(shop, c))));

        // ---- add
        var addItemNode = literal("item").then(newId().then(itemArg().then(argument("price", LongArgumentType.longArg(1))
                .executes(c -> addItem(m, c, 1, null))
                .then(argument("amount", IntegerArgumentType.integer(1, 2304))
                        .executes(c -> addItem(m, c, IntegerArgumentType.getInteger(c, "amount"), null))
                        .then(catArg("category").executes(c -> addItem(m, c, IntegerArgumentType.getInteger(c, "amount"), getString(c, "category"))))))));
        var addTicketNode = literal("ticket").then(newId().then(argument("price", LongArgumentType.longArg(1))
                .then(argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(c -> addTicket(m, c, null))
                        .then(catArg("category").executes(c -> addTicket(m, c, getString(c, "category")))))));
        var addLegendaryNode = literal("legendary").then(newId().then(argument("price", LongArgumentType.longArg(1))
                .then(argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(c -> addLegendary(m, c, null))
                        .then(catArg("category").executes(c -> addLegendary(m, c, getString(c, "category")))))));
        var addSkinNode = literal("skin").then(newId().then(argument("skin", word()).then(argument("price", LongArgumentType.longArg(1))
                .executes(c -> addSkin(m, c, "skins"))
                .then(catArg("category").executes(c -> addSkin(m, c, getString(c, "category")))))));
        var addCommandNode = literal("command").then(newId().then(argument("price", LongArgumentType.longArg(1)).then(catArg("category")
                .then(argument("command", greedyString()).executes(c -> addCommand(m, c))))));
        a.then(literal("add").requires(edit()).then(addItemNode).then(addTicketNode).then(addLegendaryNode).then(addSkinNode).then(addCommandNode));

        a.then(literal("remove").requires(edit()).then(pid().executes(c -> {
            String id = getString(c, "id");
            return result(c.getSource(), shop.removeProduct(id), "Removed &f" + id + "&7 from the shop (shop.json updated).");
        })));

        // ---- edit one field
        a.then(literal("price").requires(edit()).then(pid().then(argument("price", LongArgumentType.longArg(1)).executes(c -> field(shop, c, "price", String.valueOf(LongArgumentType.getLong(c, "price")))))));
        a.then(literal("amount").requires(edit()).then(pid().then(argument("amount", IntegerArgumentType.integer(1, 2304)).executes(c -> field(shop, c, "amount", String.valueOf(IntegerArgumentType.getInteger(c, "amount")))))));
        a.then(literal("enable").requires(edit()).then(pid().executes(c -> field(shop, c, "enabled", "true"))));
        a.then(literal("disable").requires(edit()).then(pid().executes(c -> field(shop, c, "enabled", "false"))));
        a.then(literal("featured").requires(edit()).then(pid().then(argument("on", BoolArgumentType.bool()).executes(c -> field(shop, c, "featured", String.valueOf(BoolArgumentType.getBool(c, "on")))))));
        a.then(literal("move").requires(edit()).then(pid().then(catArg("category").executes(c -> field(shop, c, "category", getString(c, "category"))))));
        a.then(literal("name").requires(edit()).then(pid().then(argument("name", greedyString()).executes(c -> field(shop, c, "name", getString(c, "name"))))));
        a.then(literal("desc").requires(edit()).then(pid().then(argument("text", greedyString()).executes(c -> field(shop, c, "desc", getString(c, "text"))))));
        a.then(literal("icon").requires(edit()).then(pid().then(argument("icon", greedyString()).executes(c -> field(shop, c, "icon", getString(c, "icon"))))));
        a.then(literal("permission").requires(edit()).then(pid().then(argument("node", word()).executes(c -> field(shop, c, "permission", getString(c, "node"))))));
        a.then(literal("limit").requires(edit()).then(pid().then(argument("perPlayer", IntegerArgumentType.integer(0))
                .executes(c -> field(shop, c, "limit", String.valueOf(IntegerArgumentType.getInteger(c, "perPlayer"))))
                .then(argument("perDay", IntegerArgumentType.integer(0)).executes(c -> {
                    int r = field(shop, c, "limit", String.valueOf(IntegerArgumentType.getInteger(c, "perPlayer")));
                    return r == 0 ? 0 : field(shop, c, "limitday", String.valueOf(IntegerArgumentType.getInteger(c, "perDay")));
                })))));

        // ---- categories
        a.then(literal("categories").requires(edit())
                .then(literal("list").executes(c -> {
                    for (Shop.Category k : shop.categories()) c.getSource().sendSuccess(() -> Txt.parse("&8- &f" + k.id + " &7" + Txt.strip(k.name) + (k.enabled ? "" : " &c(off)")), false);
                    return 1;
                }))
                .then(literal("add").then(newId().then(argument("name", greedyString()).executes(c ->
                        result(c.getSource(), shop.addCategory(getString(c, "id"), getString(c, "name"), "minecraft:amethyst_shard"), "Added category &f" + getString(c, "id") + "&7.")))))
                .then(literal("remove").then(catArg("id").executes(c ->
                        result(c.getSource(), shop.removeCategory(getString(c, "id")), "Removed category &f" + getString(c, "id") + "&7.")))));

        // ---- timers
        a.then(literal("timer").requires(timer())
                .then(pid().then(literal("off").executes(c -> {
                            String id = getString(c, "id");
                            return result(c.getSource(), shop.setTimer(id, 0), "Timer of &f" + id + "&7 cleared.");
                        }))
                        .then(argument("duration", word()).executes(c -> {
                            String id = getString(c, "id");
                            long ms = Shop.parseDuration(getString(c, "duration"));
                            if (ms < 0) return result(c.getSource(), "Use a time like 30m, 2h, 1d12h or 90 (minutes). Maximum 366 days.", "");
                            String note = shop.timersOn() ? "" : " &e(timers are OFF - switch them on with /gemshop admin timers on)";
                            return result(c.getSource(), shop.setTimer(id, ms), "&f" + id + "&7 is on sale for &d" + Shop.duration(ms) + "&7." + note);
                        }))));
        a.then(literal("timers").requires(timer())
                .then(literal("on").executes(c -> result(c.getSource(), shop.setTimersEnabled(true), "Offer timers are now &aON&7: countdowns show and ended offers close.")))
                .then(literal("off").executes(c -> result(c.getSource(), shop.setTimersEnabled(false), "Offer timers are now &cOFF&7: no countdowns, nothing closes by time (the times you set are kept).")))
                .then(literal("status").executes(c -> {
                    long n = shop.products().stream().filter(p -> p.endsAt > 0).count();
                    say(c.getSource(), "Timers are " + (shop.timersOn() ? "&aON" : "&cOFF") + "&7; " + n + " product(s) have one; when one ends the product is &f" + shop.expireAction() + "&7.");
                    return 1;
                }))
                .then(literal("expire").then(argument("action", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("DISABLE", "REMOVE"), b))
                        .executes(c -> result(c.getSource(), shop.setExpireAction(getString(c, "action")), "Ended offers will be &f" + getString(c, "action").toUpperCase() + "&7.")))));
        return a;
    }

    // ------------------------------------------------------------------ handlers

    private static int help(CommandSourceStack s) {
        String[] lines = {
                "&b◆ &fGem Shop editing &8(changes apply at once, no restart)",
                "&7add item <id> <item> <price> [amount] [category]   &7add ticket|legendary <id> <price> <amount> [category]",
                "&7add skin <id> <skinId> <price> [category]   &7add command <id> <price> <category> <command...>",
                "&7remove <id>   price <id> <n>   amount <id> <n>   enable|disable <id>   featured <id> <true|false>",
                "&7move <id> <category>   name|desc|icon <id> <text>   limit <id> <perPlayer> [perDay]   permission <id> <node|->",
                "&7list [category]   info <id>   categories list|add <id> <name>|remove <id>",
                "&7timer <id> <30m|2h|1d12h|off>   timers on|off|status|expire <DISABLE|REMOVE>   &8(permission snobblegems.shop.timer)",
        };
        for (String l : lines) s.sendSuccess(() -> Txt.parse(l), false);
        return 1;
    }

    private static int list(Shop shop, CommandSourceStack s, String category) {
        int n = 0;
        for (Shop.Product p : shop.products()) {
            if (category != null && !category.equals(p.category)) continue;
            n++;
            String timer = shop.timersOn() && p.endsAt > 0 ? " &d⏱" + Shop.duration(p.endsAt - System.currentTimeMillis()) : (p.endsAt > 0 ? " &8(timer off)" : "");
            String state = p.invalid != null ? " &c[" + p.invalid + "]" : p.enabled ? "" : " &8[off]";
            String line = "&8- &f" + p.id + " &7" + Txt.strip(p.name) + " &b" + Txt.num(p.price) + " &7x" + p.amount + " &8" + p.category + timer + state;
            s.sendSuccess(() -> Txt.parse(line), false);
        }
        int total = n;
        s.sendSuccess(() -> Txt.parse("&7" + total + " product(s)."), false);
        return 1;
    }

    private static int info(Shop shop, CommandContext<CommandSourceStack> c) {
        Shop.Product p = shop.product(getString(c, "id"));
        if (p == null) { c.getSource().sendFailure(Txt.parse("&cThere is no product '" + getString(c, "id") + "'.")); return 0; }
        String[] lines = {
                "&b◆ &f" + p.id + " &7- " + Txt.strip(p.name),
                "&7price &b" + Txt.num(p.price) + "&7, amount &fx" + p.amount + "&7, category &f" + p.category + "&7, featured &f" + p.featured + "&7, enabled &f" + p.enabled,
                "&7reward &f" + p.reward.type + (p.reward.type.equals("item") ? " " + p.reward.item : p.reward.type.equals("command") ? " " + String.join(" ; ", p.reward.commands) : "")
                        + "&7, icon &f" + p.icon + "&7, limit &f" + p.limit.perPlayer + "/" + p.limit.perDay + "&7, permission &f" + (p.permission.isBlank() ? "-" : p.permission),
                "&7timer &f" + (p.endsAt > 0 ? Shop.duration(p.endsAt - System.currentTimeMillis()) + " left" + (shop.timersOn() ? "" : " (timers are off)") : "none"),
        };
        for (String l : lines) c.getSource().sendSuccess(() -> Txt.parse(l), false);
        return 1;
    }

    private static int field(Shop shop, CommandContext<CommandSourceStack> c, String field, String value) {
        String id = getString(c, "id");
        String problem = shop.editProduct(id, field, value);
        String shown = field.equals("enabled") ? (value.equals("true") ? "switched on" : "switched off") : field + " = " + value;
        return result(c.getSource(), problem, "&f" + id + "&7: " + shown + ".");
    }

    private static String pretty(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.replace(':', '_').split("_")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }

    private static int addItem(SnobbleGems m, CommandContext<CommandSourceStack> c, int amount, String category) {
        ResourceLocation rl = ResourceLocationArgument.getId(c, "item");
        Item it = BuiltInRegistries.ITEM.get(rl);
        if (it == Items.AIR) { c.getSource().sendFailure(Txt.parse("&cThere is no item '" + rl + "' (is that mod installed?).")); return 0; }
        String name = "&d" + new ItemStack(it).getHoverName().getString();
        String id = getString(c, "id");
        String problem = m.shop.addProduct(id, name, rl.toString(), LongArgumentType.getLong(c, "price"), amount, category == null ? defaultCategory(m) : category, Shop.itemReward(rl.toString(), amount));
        return result(c.getSource(), problem, "Added &f" + id + "&7: " + amount + " x " + rl + " for &b" + Txt.num(LongArgumentType.getLong(c, "price")) + "&7. Rename with &f/gemshop admin name " + id + " <text>&7.");
    }

    private static int addTicket(SnobbleGems m, CommandContext<CommandSourceStack> c, String category) {
        int amount = IntegerArgumentType.getInteger(c, "amount");
        String id = getString(c, "id");
        String problem = m.shop.addProduct(id, "&d✨ Shiny Boost Ticket", "snobblegems:ticket", LongArgumentType.getLong(c, "price"), amount, category == null ? "shiny" : category, Shop.ticketReward(amount));
        return result(c.getSource(), problem, "Added &f" + id + "&7: " + amount + " Shiny Boost Ticket(s).");
    }

    private static int addLegendary(SnobbleGems m, CommandContext<CommandSourceStack> c, String category) {
        int amount = IntegerArgumentType.getInteger(c, "amount");
        String id = getString(c, "id");
        String problem = m.shop.addProduct(id, "&6✨ Legendary Boost Ticket", "snobblegems:ticket", LongArgumentType.getLong(c, "price"), amount, category == null ? "shiny" : category, Shop.legendaryTicketReward(amount));
        return result(c.getSource(), problem, "Added &f" + id + "&7: " + amount + " Legendary Boost Ticket(s).");
    }

    private static int addSkin(SnobbleGems m, CommandContext<CommandSourceStack> c, String category) {
        String id = getString(c, "id"), skin = getString(c, "skin");
        if (!skin.matches("[a-z0-9_.-]{1,64}")) { c.getSource().sendFailure(Txt.parse("&cA skin id uses a-z, 0-9, _ . - only (it is the file name in config/skiesskins/skins).")); return 0; }
        String cmd = m.shop.skinCommand().replace("{skin}", skin);
        String problem = m.shop.addProduct(id, "&d" + pretty(skin), "snobblegems:skin", LongArgumentType.getLong(c, "price"), 1, category, Shop.commandReward(cmd));
        if (problem == null) m.shop.editProduct(id, "limit", "1");
        return result(c.getSource(), problem, "Added skin &f" + id + "&7 (runs &f" + cmd + "&7). Limit 1 per player; change with &f/gemshop admin limit " + id + " 0&7.");
    }

    private static int addCommand(SnobbleGems m, CommandContext<CommandSourceStack> c) {
        String id = getString(c, "id");
        String problem = m.shop.addProduct(id, "&d" + pretty(id), "snobblegems:gem", LongArgumentType.getLong(c, "price"), 1, getString(c, "category"), Shop.commandReward(getString(c, "command")));
        return result(c.getSource(), problem, "Added &f" + id + "&7 (console command reward).");
    }

    private static String defaultCategory(SnobbleGems m) {
        List<Shop.Category> cats = new ArrayList<>(m.shop.categories());
        for (Shop.Category k : cats) if (k.id.equals("rare")) return "rare";
        return cats.isEmpty() ? "" : cats.get(0).id;
    }
}
