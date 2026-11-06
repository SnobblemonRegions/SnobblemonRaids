package com.snobblemon.gems;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.snobblemon.gems.api.GemsApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * SnobbleGemCurrency: server-side Gem currency, Tebex rewards, Shiny Boost Tickets and the Gem Shop. Nothing here needs a client mod.
 */
public class SnobbleGems implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("SnobbleGems");
    public static SnobbleGems I;

    public volatile MinecraftServer server;
    public volatile Cfg cfg = new Cfg();
    public Db db;
    public Ledger ledger;
    public final Tickets tickets = new Tickets(this);
    public final Exchange exchange = new Exchange(this);
    public final LegendaryBoost legendary = new LegendaryBoost(this);
    public final Delivery delivery = new Delivery(this);
    public final Community community = new Community(this);
    public final Shop shop = new Shop(this);
    public final WebShop web = new WebShop(this);
    final ShopBridge bridge = new ShopBridge(this);

    private long tick;
    private final List<Object[]> scheduled = new ArrayList<>();

    @Override
    public void onInitialize() {
        I = this;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> Cmds.register(this, dispatcher));
        tickets.register();
        community.register();
        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            try {
                PH.register();
            } catch (Throwable t) {
                LOG.warn("[SnobbleGems] Placeholder API hooks could not be registered: {}", t.toString());
            }
        }

        ServerLifecycleEvents.SERVER_STARTING.register(this::starting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stopping);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            ServerPlayer p = handler.getPlayer();
            ledger.touch(p.getUUID(), p.getGameProfile().getName());
            community.onJoin(p);
            shop.scrub(p);
            later(40, () -> {
                if (p.hasDisconnected()) return;
                delivery.deliver(p, false);
                int left = ledger.pendingFor(p.getUUID(), p.getGameProfile().getName()).size();
                if (left > 0) notify(p, vars("ticket_pending", "amount", String.valueOf(left)));
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            tickets.forget(handler.getPlayer().getUUID());
            shop.forget(handler.getPlayer().getUUID());
            web.leave(handler.getPlayer().getUUID());
            bridge.forget(handler.getPlayer().getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            tick++;
            if (!scheduled.isEmpty()) {
                for (Iterator<Object[]> it = scheduled.iterator(); it.hasNext(); ) {
                    Object[] s = it.next();
                    if ((long) s[0] <= tick) {
                        it.remove();
                        try {
                            ((Runnable) s[1]).run();
                        } catch (Throwable t) {
                            LOG.error("[SnobbleGems] scheduled task failed", t);
                        }
                    }
                }
            }
            if (ledger != null) {
                if (tick % 40 == 21) watchConfig();
                community.tick(tick);
                shop.tick(tick);
                web.tick(tick);
            }
        });
    }

    private void starting(MinecraftServer srv) {
        server = srv;
        List<String> problems = new ArrayList<>();
        cfg = Cfg.load(problems);
        cfgMtime = cfgFileMtime();
        try {
            Path file = FabricLoader.getInstance().getGameDir().resolve("snobblegemcurrency").resolve("gems.db");
            db = new Db(file);
            ledger = new Ledger(db, () -> cfg);
            ledger.load();
            db.start();
        } catch (Throwable t) {
            // a currency mod that cannot store anything must not run "in memory only": stop the server loudly
            LOG.error("[SnobbleGems] FATAL: cannot open the Gem database", t);
            throw new IllegalStateException("SnobbleGemCurrency could not open its database: " + t.getMessage(), t);
        }
        exchange.load();
        shop.load(problems);
        web.start();
        legendary.start();
        GemsApi.Holder.INSTANCE = new GemsApiImpl(this);
        for (String p : problems) LOG.warn("[SnobbleGems] {}", p);
        LOG.info("[SnobbleGems] ready: {} accounts, {} tickets in circulation, {} rewards waiting, shop items: {}, chat rewards: {}, ticket mode: {}",
                ledger.accountCount(), ledger.issuedTicketCount(), ledger.pendingTotal(), shop.productCount(), community.describeSource(), "resolved at first use");
    }

    private void stopping(MinecraftServer srv) {
        GemsApi.Holder.INSTANCE = null;
        if (db != null) {
            db.close();
            LOG.info("[SnobbleGems] database flushed and closed ({} writes, {} failures)", db.written, db.failures);
        }
    }

    /** /gemshop: the WebGUI page for players who have WebGUI (unless the server is set to CHEST), the chest menu for everyone else. */
    public void openShop(ServerPlayer p, boolean forceChest) {
        String mode = cfg.gui.mode == null ? "AUTO" : cfg.gui.mode.toUpperCase();
        if (!forceChest && !mode.equals("CHEST")) {
            if (web.open(p)) return;
            if (mode.equals("WEBGUI")) {
                notify(p, vars("web_required"));
                return;
            }
        }
        shop.open(p);
    }

    /** Reloads config.json and shop.json; returns a short text about anything wrong. */
    public String reload() {
        List<String> problems = new ArrayList<>();
        cfg = Cfg.load(problems, cfg);
        cfgMtime = cfgFileMtime();
        shop.load(problems);
        community.refreshConfig();
        for (String p : problems) LOG.warn("[SnobbleGems] {}", p);
        return problems.isEmpty() ? "" : "(" + problems.size() + " problem(s), see the console)";
    }

    private long cfgMtime;

    private long cfgFileMtime() {
        try {
            Path f = Cfg.dir().resolve("config.json");
            return Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Every 2 s: a hand-edited config.json is applied by itself, exactly like /snobblegems reload. */
    private void watchConfig() {
        long m = cfgFileMtime();
        if (m == cfgMtime) return;
        String r = reload();
        LOG.info("[SnobbleGems] config.json changed on disk: reloaded {}", r);
    }

    // ------------------------------------------------------------------ small helpers used everywhere

    public void later(int ticks, Runnable r) {
        scheduled.add(new Object[]{tick + ticks, r});
    }

    /** A configured message with {currency} {icon} and the given key/value pairs filled in. */
    public Component vars(String key, String... kv) {
        return Txt.parse(Txt.apply(cfg.msg(key), map(kv)));
    }

    public String text(String key, String... kv) {
        return Txt.apply(cfg.msg(key), map(kv));
    }

    private Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        m.put("currency", cfg.currency.name);
        m.put("icon", cfg.currency.chatIcon);
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    public void notify(ServerPlayer p, Component c) {
        p.sendSystemMessage(c);
    }

    public void sound(ServerPlayer p, String id) {
        sound(p, id, 1.0f, 1.0f);
    }

    public void sound(ServerPlayer p, String id, float volume, float pitch) {
        if (!cfg.sounds.enabled || id == null || id.isBlank()) return;
        try {
            SoundEvent ev = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(id));
            if (ev != null) p.playNotifySound(ev, SoundSource.PLAYERS, cfg.sounds.volume * volume, pitch);
        } catch (Throwable ignored) {}
    }

    /** The purchase jingle (config sounds.purchaseSequence) and a burst of sparkles around the buyer. */
    public void purchaseMagic(ServerPlayer p) {
        if (cfg.sounds.enabled) {
            List<Cfg.Step> seq = cfg.sounds.purchaseSequence;
            if (seq == null || seq.isEmpty()) sound(p, cfg.sounds.purchase);
            else for (Cfg.Step st : seq) {
                if (st == null) continue;
                if (st.delay <= 0) sound(p, st.sound, st.volume, st.pitch);
                else later(Math.min(st.delay, 200), () -> { if (!p.hasDisconnected()) sound(p, st.sound, st.volume, st.pitch); });
            }
        }
        if (cfg.effects.purchaseParticles) {
            try {
                var lvl = p.serverLevel();
                lvl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.1, p.getZ(), 48, 0.45, 0.7, 0.45, 0.9);
                lvl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.4, 0.6, 0.4, 0.06);
                later(6, () -> { if (!p.hasDisconnected()) p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.4, p.getZ(), 10, 0.5, 0.5, 0.5, 0.0); });
            } catch (Throwable ignored) {}
        }
    }

    /** A credit from a purchase or admin action reached an online player. */
    public void notifyCredit(ServerPlayer p, long applied, long balance, boolean purchase) {
        if (applied > 0) {
            notify(p, vars(purchase ? "purchase_thanks" : "received", "amount", Txt.num(applied), "balance", Txt.num(balance)));
            sound(p, cfg.sounds.earn);
        } else if (applied < 0) {
            notify(p, vars("lost", "amount", Txt.num(-applied), "balance", Txt.num(balance)));
        }
    }

    public void notifyReward(ServerPlayer p, long amount, String kind) {
        String mode = cfg.chat.notify == null ? "ACTIONBAR" : cfg.chat.notify.toUpperCase();
        Component c = vars("community_reward", "amount", Txt.num(amount), "reason", kind.equals("w") ? "W" : "Welcome");
        if (mode.equals("CHAT")) p.sendSystemMessage(c);
        else if (mode.equals("ACTIONBAR")) p.sendSystemMessage(c, true);
        if (!mode.equals("NONE")) sound(p, cfg.sounds.earn);
    }

    public void broadcast(String legacy) {
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(Txt.parse(legacy), false);
    }

    /**
     * Runs a shop-reward command as the console. Unlike {@link #runConsole}: a command that does not exist or has bad syntax still throws (nothing ran,
     * so the caller refunds), but an exception thrown from INSIDE the running command is only logged and reported through the return value. Such a
     * command usually already did its job (SkiesSkins gives the skin and then crashes while sending its own chat message), so refunding would hand out
     * the reward for free. Set gui.refundOnCommandCrash to refund anyway.
     * @return true if the command finished without an exception
     */
    public boolean runRewardCommand(String command, String who, String product) {
        String c = command.startsWith("/") ? command.substring(1) : command;
        CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
        try {
            server.getCommands().getDispatcher().execute(c, src);
            return true;
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("command failed: " + c + " (" + e.getMessage() + ")", e);
        } catch (RuntimeException e) {
            if (cfg.gui.refundOnCommandCrash) throw e;
            LOG.error("[SnobbleGems] reward command for '{}' ({}) threw an exception AFTER it started - the player is NOT refunded (it has probably already been given): /{}", product, who, c, e);
            return false;
        }
    }

    /** Runs a command as the console. Throws if the command does not exist or its syntax is wrong, so callers can undo what they charged for. */
    public void runConsole(String command) {
        String c = command.startsWith("/") ? command.substring(1) : command;
        CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
        try {
            server.getCommands().getDispatcher().execute(c, src);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("command failed: " + c + " (" + e.getMessage() + ")", e);
        }
    }
}
