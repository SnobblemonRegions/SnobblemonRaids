package com.snobblemon.gems;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.snobblemon.gems.api.GemsResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /snobblegems (admin + Tebex), /gems and /gemshop (players). Every admin command works from the console. */
final class Cmds {
    private Cmds() {}

    private static Predicate<CommandSourceStack> admin(String node) {
        return s -> Perms.admin(s, node);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return argument("target", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b));
    }

    static void register(SnobbleGems m, CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("snobblegems");

        root.then(literal("help").executes(c -> help(m, c.getSource())));
        root.executes(c -> help(m, c.getSource()));

        // ---- balance
        root.then(literal("balance").requires(s -> Perms.player(s, Perms.BALANCE))
                .executes(c -> balanceSelf(m, c))
                .then(target().requires(s -> Perms.check(s, Perms.BALANCE_OTHERS, 2) || Perms.admin(s, Perms.BALANCE_OTHERS))
                        .executes(c -> balanceOther(m, c))));

        // ---- give / take / set
        root.then(literal("give").requires(admin(Perms.GIVE))
                .then(target().then(argument("amount", LongArgumentType.longArg(1))
                        .executes(c -> give(m, c, false, null))
                        .then(argument("ref", word()).executes(c -> give(m, c, false, getString(c, "ref")))))));
        root.then(literal("take").requires(admin(Perms.TAKE))
                .then(target().then(argument("amount", LongArgumentType.longArg(1))
                        .executes(c -> take(m, c, null))
                        .then(argument("ref", word()).executes(c -> take(m, c, getString(c, "ref")))))));
        root.then(literal("set").requires(admin(Perms.SET))
                .then(target().then(argument("amount", LongArgumentType.longArg(0)).executes(c -> set(m, c)))));

        // ---- tickets
        root.then(literal("ticket").requires(admin(Perms.TICKET_GIVE))
                .then(literal("test").executes(c -> {
                    String mode = m.tickets.resolveMode();
                    boolean ok;
                    try {
                        ok = m.tickets.activate("TicketTest", new UUID(0, 0));
                    } catch (Throwable t) {
                        ok = false;
                        fail(c.getSource(), Component.literal("Activation threw: " + t.getMessage()));
                    }
                    ok(c.getSource(), Component.literal("Ticket activation test (" + mode + "): " + (ok ? "started a shiny boost exactly as a ticket would" : "FAILED - tickets would not be used up, see the console")), true);
                    return ok ? 1 : 0;
                }))
                .then(literal("give").then(target().then(argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(c -> give(m, c, true, null, false))
                        .then(argument("ref", word()).executes(c -> give(m, c, true, getString(c, "ref"), false)))))));

        root.then(literal("legendary").requires(admin(Perms.TICKET_GIVE))
                .then(literal("test").executes(c -> {
                    boolean ok = m.legendary.add();
                    if (ok) ok(c.getSource(), Component.literal("Legendary boost started for " + m.cfg.legendaryTicket.minutes + " min at " + m.cfg.legendaryTicket.multiplier + "x (" + m.legendary.secondsLeft() + " s left in total). /snobblegems legendary stop ends it."), true);
                    else fail(c.getSource(), Component.literal("Cobblemon's shiny hook is not active: see the console. Tickets would not be used up."));
                    return ok ? 1 : 0;
                }))
                .then(literal("status").executes(c -> {
                    ok(c.getSource(), Component.literal(m.legendary.active()
                            ? "Legendary boost: " + m.legendary.multiplier() + "x, " + m.legendary.secondsLeft() + " s left"
                            : "Legendary boost: not running" + (m.legendary.hooked() ? "" : " (Cobblemon hook NOT active)")), false);
                    return 1;
                }))
                .then(literal("roll").then(argument("species", word()).then(argument("tries", IntegerArgumentType.integer(1, 5000)).executes(c -> {
                    int tries = IntegerArgumentType.getInteger(c, "tries");
                    int n = m.legendary.rollShinies(getString(c, "species"), tries);
                    ok(c.getSource(), Component.literal("Rolled " + tries + " x " + getString(c, "species") + " (boost " + (m.legendary.active() ? m.legendary.multiplier() + "x ON" : "off") + "): " + n + " shiny"), false);
                    return n;
                }))))
                .then(literal("stop").executes(c -> {
                    m.legendary.stop();
                    ok(c.getSource(), Component.literal("Legendary boost stopped."), true);
                    return 1;
                }))
                .then(literal("give").then(target().then(argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(c -> give(m, c, true, null, true))
                        .then(argument("ref", word()).executes(c -> give(m, c, true, getString(c, "ref"), true)))))));

        // ---- pending rewards
        root.then(literal("claim").requires(s -> Perms.player(s, Perms.CLAIM)).executes(c -> claim(m, c)));

        // ---- shop
        root.then(literal("shop").requires(s -> Perms.player(s, Perms.SHOP)).executes(c -> shop(m, c, false))
                .then(literal("chest").executes(c -> shop(m, c, true)))
                .then(ShopCmds.tree(m)));

        root.then(exchangeNode(m));

        // ---- admin odds and ends
        root.then(literal("reload").requires(admin(Perms.RELOAD)).executes(c -> {
            String problems = m.reload();
            c.getSource().sendSuccess(() -> m.vars("reloaded", "problems", problems), true);
            return 1;
        }));
        root.then(literal("history").requires(admin(Perms.HISTORY))
                .then(target().executes(c -> history(m, c, 10))
                        .then(argument("count", IntegerArgumentType.integer(1, 50)).executes(c -> history(m, c, IntegerArgumentType.getInteger(c, "count"))))));
        root.then(literal("window").requires(admin(Perms.WINDOW))
                .then(argument("kind", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("w", "welcome"), b))
                        .executes(c -> window(m, c, null, -1))
                        .then(argument("source", word()).executes(c -> window(m, c, getString(c, "source"), -1))
                                .then(argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(c -> window(m, c, getString(c, "source"), IntegerArgumentType.getInteger(c, "seconds")))))));
        root.then(literal("community").requires(admin(Perms.COMMUNITY))
                .then(target().then(argument("kind", word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("w", "welcome"), b))
                        .executes(c -> community(m, c, null))
                        .then(argument("key", word()).executes(c -> community(m, c, getString(c, "key")))))));
        root.then(literal("status").requires(admin(Perms.ADMIN)).executes(c -> status(m, c.getSource())));
        root.then(literal("placeholder").requires(admin(Perms.ADMIN))
                .then(argument("text", StringArgumentType.greedyString()).executes(c -> placeholder(m, c))));

        var built = d.register(root);
        d.register(literal("snobblegem").requires(root.getRequirement()).executes(root.getCommand()).redirect(built));

        // player shortcuts
        d.register(literal("gems").requires(s -> Perms.player(s, Perms.BALANCE))
                .executes(c -> balanceSelf(m, c))
                .then(literal("shop").requires(s -> Perms.player(s, Perms.SHOP)).executes(c -> shop(m, c, false))
                        .then(literal("chest").executes(c -> shop(m, c, true)))
                        .then(ShopCmds.tree(m)))
                .then(exchangeNode(m))
                .then(literal("help").executes(c -> help(m, c.getSource()))));
        d.register(literal("gemshop").requires(s -> Perms.player(s, Perms.SHOP)).executes(c -> shop(m, c, false))
                .then(literal("chest").executes(c -> shop(m, c, true)))
                .then(ShopCmds.tree(m)));
    }

    // ------------------------------------------------------------------ Community Points -> Gems

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> exchangeNode(SnobbleGems m) {
        return literal("exchange").requires(s -> Perms.player(s, Perms.EXCHANGE))
                .executes(c -> exchangeInfo(m, c))
                .then(argument("gems", LongArgumentType.longArg(1, 1_000_000)).executes(c -> exchangeBuy(m, c, LongArgumentType.getLong(c, "gems"))));
    }

    private static int exchangeInfo(SnobbleGems m, CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        var p = s.getPlayer();
        if (p == null) { fail(s, Txt.parse("&cOnly players can use the exchange.")); return 0; }
        if (!m.exchange.available()) { fail(s, Txt.parse("&c[Exchange] The Gem exchange is not available right now.")); return 0; }
        Cfg.Exchange x = m.cfg.exchange;
        String pn = m.exchange.pointsLabel(), gn = m.cfg.currency.name;
        long left = m.exchange.gemsLeftToday(p.getUUID());
        ok(s, Txt.parse("&b◆ &fBuy " + gn + " with " + pn), false);
        ok(s, Txt.parse("&7Rate: &d" + Txt.num(m.exchange.pointsPerGem()) + " " + pn + " &7= &b1 " + gn), false);
        ok(s, Txt.parse("&7You have: &d" + Txt.num(m.exchange.points(p.getUUID(), p.getGameProfile().getName())) + " " + pn + "&7, can buy up to &b"
                + Txt.num(Math.min(left == Long.MAX_VALUE ? Long.MAX_VALUE : left, x.maxGemsPerTrade > 0 ? x.maxGemsPerTrade : Long.MAX_VALUE)) + " &7" + gn + " now."), false);
        ok(s, Txt.parse("&7Type &f/snobblegems exchange <amount of " + gn + ">&7, or use the Gem Shop's Gem Exchange tab."), false);
        return 1;
    }

    private static int exchangeBuy(SnobbleGems m, CommandContext<CommandSourceStack> c, long gems) {
        CommandSourceStack s = c.getSource();
        var p = s.getPlayer();
        if (p == null) { fail(s, Txt.parse("&cOnly players can use the exchange.")); return 0; }
        String why = m.exchange.trade(p, gems);
        if (why != null) { fail(s, Txt.parse("&c[Exchange] " + why)); return 0; }
        ok(s, Txt.parse("&b[Exchange] &7You bought &b" + Txt.num(gems) + " " + m.cfg.currency.name + " &7for &d" + Txt.num(m.exchange.costOf(gems)) + " " + m.exchange.pointsLabel()
                + "&7. Balance: &b" + Txt.num(m.ledger.balance(p.getUUID())) + "&7."), false);
        m.sound(p, m.cfg.sounds.earn);
        return 1;
    }

    // ------------------------------------------------------------------ helpers

    private static void ok(CommandSourceStack s, Component c, boolean log) {
        s.sendSuccess(() -> c, log);
    }

    private static void fail(CommandSourceStack s, Component c) {
        s.sendFailure(c);
    }

    private static String actor(CommandSourceStack s) {
        return s.getTextName();
    }

    // ------------------------------------------------------------------ commands

    private static int help(SnobbleGems m, CommandSourceStack s) {
        String[] lines = {
                "&b◆ &fSnobbleGemCurrency",
                "&7/gems &8- &fyour Gem balance      &7/gemshop &8- &fopen the Gem Shop",
                "&7/snobblegems claim &8- &fcollect rewards that were waiting for you",
        };
        for (String l : lines) ok(s, Txt.parse(l), false);
        if (Perms.admin(s, Perms.GIVE) || Perms.admin(s, Perms.ADMIN)) {
            String[] adm = {
                    "&7/snobblegems give <player|uuid> <amount> [orderId] &8- &fadd Gems (Tebex: use {id} {transaction})",
                    "&7/snobblegems take <player> <amount> [orderId]   &7/snobblegems set <player> <amount>",
                    "&7/snobblegems balance <player>   &7/snobblegems history <player> [n]",
                    "&7/snobblegems ticket give <player|uuid> <amount> [orderId] &8- &fShiny Boost Tickets",
                    "&7/snobblegems legendary give <player|uuid> <amount> [orderId] &8- &fLegendary Boost Tickets (also: test, status, stop)",
                    "&7/snobblegems window <w|welcome> [source] [seconds]   &7/snobblegems community <player> <w|welcome> [key]",
                    "&7/snobblegems reload   &7/snobblegems status   &7/snobblegems placeholder <text>",
                    "&7/gemshop admin help &8- &fadd / remove / re-price shop items and set offer timers while the server runs",
            };
            for (String l : adm) ok(s, Txt.parse(l), false);
        }
        return 1;
    }

    private static int balanceSelf(SnobbleGems m, CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) {
            fail(c.getSource(), Component.literal("Console: use /snobblegems balance <player>"));
            return 0;
        }
        long b = m.ledger.balance(p.getUUID());
        ok(c.getSource(), m.vars("balance_self", "balance", Txt.num(b)), false);
        return 1;
    }

    private static int balanceOther(SnobbleGems m, CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        Resolver.resolve(m.server, m.ledger, getString(c, "target"), who -> {
            if (who == null) { fail(s, m.vars("no_such_player", "player", getString(c, "target"))); return; }
            ok(s, m.vars("balance_other", "player", who.name(), "balance", Txt.num(m.ledger.balance(who.uuid()))), false);
        });
        return 1;
    }

    private static int give(SnobbleGems m, CommandContext<CommandSourceStack> c, boolean ticket, String ref) { return give(m, c, ticket, ref, false); }

    private static int give(SnobbleGems m, CommandContext<CommandSourceStack> c, boolean ticket, String ref, boolean legendary) {
        CommandSourceStack s = c.getSource();
        String targetText = getString(c, "target");
        long amount = ticket ? IntegerArgumentType.getInteger(c, "amount") : LongArgumentType.getLong(c, "amount");
        if (!ticket && amount > m.cfg.tebex.maxGiveAmount) {
            fail(s, m.vars("invalid_amount"));
            return 0;
        }
        final String orderKey = ref == null || !m.cfg.tebex.duplicateProtection ? null : (ticket ? (legendary ? "lticket:" : "ticket:") : "give:") + ref;
        final String actor = actor(s);
        final boolean fromConsole = s.getPlayer() == null;
        Resolver.resolve(m.server, m.ledger, targetText, who -> {
            if (who == null) {
                if (!m.cfg.tebex.holdUnresolvedNames || !targetText.matches("[A-Za-z0-9_.]{1,17}")) {
                    fail(s, m.vars("no_such_player", "player", targetText));
                    return;
                }
                // could not turn the name into a UUID (never joined and Mojang unreachable / unknown): keep it for the day they join
                if (orderKey != null && !m.ledger.claimOrder(orderKey, ticket ? (legendary ? "legendary_ticket" : "ticket") : "gems", null, "held for name " + targetText)) {
                    ok(s, m.vars("duplicate", "ref", ref), true);
                    return;
                }
                m.ledger.addPending(null, targetText, ticket ? (legendary ? "legendary_ticket" : "ticket") : "gems", String.valueOf(amount), ref);
                ok(s, Txt.parse("&e[SnobbleGems] Could not find a UUID for '" + targetText + "'. The " + (ticket ? amount + " ticket(s)" : amount + " Gems") + " will be delivered when a player with that name joins."), true);
                return;
            }
            if (ticket) {
                if (orderKey != null && !m.ledger.claimOrder(orderKey, legendary ? "legendary_ticket" : "ticket", who.uuid(), "tickets x" + amount)) {
                    ok(s, m.vars("duplicate", "ref", ref), true);
                    return;
                }
                m.ledger.touch(who.uuid(), who.name());
                int now = m.tickets.grant(who.uuid(), who.name(), (int) amount, "give:" + actor, ref, legendary ? Tickets.LEGENDARY : Tickets.SHINY);
                String where = m.server.getPlayerList().getPlayer(who.uuid()) != null
                        ? (now >= amount ? "" : " (inventory full: " + (amount - now) + " waiting)") : " (offline: delivered when they join)";
                ok(s, m.vars(legendary ? "lticket_given" : "ticket_given", "amount", String.valueOf(amount), "player", who.name(), "where", where), true);
                return;
            }
            GemsResult r = m.ledger.adjust(who.uuid(), who.name(), amount, "give", actor, orderKey);
            switch (r.status()) {
                case OK -> {
                    ok(s, m.vars("given", "amount", Txt.num(r.applied()), "player", who.name(), "balance", Txt.num(r.balance())), true);
                    if (r.applied() < amount) ok(s, m.vars("capped", "amount", Txt.num(r.applied())), true);
                    ServerPlayer online = m.server.getPlayerList().getPlayer(who.uuid());
                    if (online != null) m.notifyCredit(online, r.applied(), r.balance(), fromConsole && m.cfg.tebex.notifyPlayer);
                }
                case DUPLICATE -> ok(s, m.vars("duplicate", "ref", ref), true);
                default -> fail(s, m.vars("invalid_amount"));
            }
        });
        return 1;
    }

    private static int take(SnobbleGems m, CommandContext<CommandSourceStack> c, String ref) {
        CommandSourceStack s = c.getSource();
        long amount = LongArgumentType.getLong(c, "amount");
        String orderKey = ref == null || !m.cfg.tebex.duplicateProtection ? null : "take:" + ref;
        Resolver.resolve(m.server, m.ledger, getString(c, "target"), who -> {
            if (who == null) { fail(s, m.vars("no_such_player", "player", getString(c, "target"))); return; }
            GemsResult r = m.ledger.adjust(who.uuid(), who.name(), -amount, "take", actor(s), orderKey);
            switch (r.status()) {
                case OK -> {
                    ok(s, m.vars("taken", "amount", Txt.num(-r.applied()), "player", who.name(), "balance", Txt.num(r.balance())), true);
                    ServerPlayer online = m.server.getPlayerList().getPlayer(who.uuid());
                    if (online != null) m.notifyCredit(online, r.applied(), r.balance(), false);
                }
                case INSUFFICIENT -> fail(s, m.vars("insufficient", "balance", Txt.num(r.balance()), "price", Txt.num(amount)));
                case DUPLICATE -> ok(s, m.vars("duplicate", "ref", ref), true);
                default -> fail(s, m.vars("invalid_amount"));
            }
        });
        return 1;
    }

    private static int set(SnobbleGems m, CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        long amount = LongArgumentType.getLong(c, "amount");
        Resolver.resolve(m.server, m.ledger, getString(c, "target"), who -> {
            if (who == null) { fail(s, m.vars("no_such_player", "player", getString(c, "target"))); return; }
            GemsResult r = m.ledger.setBalance(who.uuid(), who.name(), amount, "set", actor(s));
            if (!r.ok()) { fail(s, m.vars("invalid_amount")); return; }
            ok(s, m.vars("set", "player", who.name(), "balance", Txt.num(r.balance())), true);
        });
        return 1;
    }

    private static int claim(SnobbleGems m, CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        m.delivery.deliver(p, true);
        return 1;
    }

    private static int shop(SnobbleGems m, CommandContext<CommandSourceStack> c, boolean chest) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) {
            fail(c.getSource(), Component.literal("The Gem Shop can only be opened by a player."));
            return 0;
        }
        m.openShop(p, chest);
        return 1;
    }

    private static int history(SnobbleGems m, CommandContext<CommandSourceStack> c, int n) {
        CommandSourceStack s = c.getSource();
        Resolver.resolve(m.server, m.ledger, getString(c, "target"), who -> {
            if (who == null) { fail(s, m.vars("no_such_player", "player", getString(c, "target"))); return; }
            m.ledger.history(who.uuid(), n, list -> m.server.execute(() -> {
                ok(s, Txt.parse("&b◆ &fLast " + list.size() + " transactions of &b" + who.name() + " &7(balance " + Txt.num(m.ledger.balance(who.uuid())) + ")"), false);
                for (Ledger.TxView t : list) {
                    String when = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(t.ts()));
                    ok(s, Txt.parse("&8" + when + " " + (t.delta() >= 0 ? "&a+" : "&c") + Txt.num(t.delta()) + " &7-> &f" + Txt.num(t.after()) + " &8" + t.reason() + " by " + t.actor() + (t.ref() == null ? "" : " [" + t.ref() + "]")), false);
                }
            }));
        });
        return 1;
    }

    private static int window(SnobbleGems m, CommandContext<CommandSourceStack> c, String source, int seconds) {
        CommandSourceStack s = c.getSource();
        String kind = getString(c, "kind").toLowerCase();
        if (!kind.equals("w") && !kind.equals("welcome")) { fail(s, Component.literal("kind must be w or welcome")); return 0; }
        int secs = seconds > 0 ? seconds : (kind.equals("w") ? m.cfg.chat.w.windowSeconds : m.cfg.chat.welcome.windowSeconds);
        UUID src = null;
        String srcName = source;
        if (source != null) {
            ServerPlayer online = m.server.getPlayerList().getPlayerByName(source);
            if (online != null) { src = online.getUUID(); srcName = online.getGameProfile().getName(); }
            else { src = m.ledger.uuidByName(source); }
        }
        m.community.openWindow(kind, src, srcName, secs);
        ok(s, m.vars("window_opened", "kind", kind, "seconds", String.valueOf(secs)), true);
        return 1;
    }

    private static int community(SnobbleGems m, CommandContext<CommandSourceStack> c, String key) {
        CommandSourceStack s = c.getSource();
        String kind = getString(c, "kind").toLowerCase();
        Resolver.resolve(m.server, m.ledger, getString(c, "target"), who -> {
            if (who == null) { fail(s, m.vars("no_such_player", "player", getString(c, "target"))); return; }
            GemsResult r = m.community.reward(who.uuid(), who.name(), kind, key);
            ok(s, Component.literal("community " + kind + " for " + who.name() + ": " + r.status() + (r.ok() ? " +" + r.applied() : "")), true);
        });
        return 1;
    }

    private static int status(SnobbleGems m, CommandSourceStack s) {
        String[] lines = {
                "&b◆ &fSnobbleGemCurrency status",
                "&7accounts: &f" + m.ledger.accountCount() + "  &7tickets in circulation: &f" + m.ledger.issuedTicketCount() + "  &7rewards waiting: &f" + m.ledger.pendingTotal(),
                "&7db writes: &f" + m.db.written + "  &7queued: &f" + m.db.queued() + "  &7failures: &f" + m.db.failures,
                "&7chat rewards: &f" + m.community.describeSource() + "  &7open windows: &f" + m.community.windowCount(),
                "&7ticket activation: &f" + m.tickets.resolveMode() + "  &7shop products: &f" + m.shop.productCount(),
                "&7shop gui: &f" + m.cfg.gui.mode + " - " + m.web.status(),
                "&7permissions: &f" + (Perms.available() ? "LuckPerms / permissions API" : "operator levels only"),
        };
        for (String l : lines) ok(s, Txt.parse(l), false);
        return 1;
    }

    private static int placeholder(SnobbleGems m, CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) { fail(c.getSource(), Component.literal("Run this as a player.")); return 0; }
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            fail(c.getSource(), Component.literal("Placeholder API is not installed."));
            return 0;
        }
        ok(c.getSource(), Component.literal(PH.parse(p, getString(c, "text"))), false);
        return 1;
    }
}
