package com.snobblemon.gems;

import com.google.gson.reflect.TypeToken;
import com.snobblemon.gems.api.GemsResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/**
 * Community Points (SnobblemonCompanion) -> Gems. Everything is configured in config.json "exchange" and read live on every trade, so
 * /snobblegems reload changes the rate and the limits at once. Points are spent through SnobblemonCompanion's own awardPoints, so its
 * balance, earned totals and save file stay consistent. Server thread only.
 */
public final class Exchange {
    private final SnobbleGems mod;
    private final Map<String, Day> days = new HashMap<>();
    private Method stats, award, save;
    private Field pointsField, instance, dataField, fileField;
    private boolean bridgeTried;

    private static final class Day {
        String day = "";
        long gems, points;
    }

    public Exchange(SnobbleGems mod) {
        this.mod = mod;
    }

    private Path file() {
        return Cfg.dir().resolve("exchange_daily.json");
    }

    public void load() {
        days.clear();
        try {
            if (Files.exists(file())) {
                Map<String, Day> m = Cfg.GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<Map<String, Day>>() {}.getType());
                if (m != null) days.putAll(m);
            }
        } catch (Exception e) {
            SnobbleGems.LOG.warn("[SnobbleGems] exchange_daily.json could not be read: {}", e.toString());
        }
    }

    private void saveDays() {
        try {
            String today = mod.ledger.today();
            days.values().removeIf(d -> !today.equals(d.day));
            Cfg.writeAtomic(file(), Cfg.GSON.toJson(days));
        } catch (Exception e) {
            SnobbleGems.LOG.warn("[SnobbleGems] could not save exchange_daily.json: {}", e.toString());
        }
    }

    private Day day(UUID id) {
        Day d = days.computeIfAbsent(id.toString(), k -> new Day());
        String today = mod.ledger.today();
        if (!today.equals(d.day)) { d.day = today; d.gems = 0; d.points = 0; }
        return d;
    }

    // ------------------------------------------------------------------ the Companion bridge

    private boolean bridge() {
        if (stats != null) return true;
        if (bridgeTried) return false;
        bridgeTried = true;
        try {
            Class<?> c = Class.forName("com.snobblemon.companion.SnobblemonCompanion");
            Class<?> data = Class.forName("com.snobblemon.companion.Data");
            Class<?> ps = Class.forName("com.snobblemon.companion.Data$PlayerStats");
            instance = c.getField("I");
            dataField = c.getField("data");
            fileField = c.getField("dataFile");
            stats = data.getMethod("stats", String.class, String.class);
            save = data.getMethod("save", Path.class);
            award = c.getMethod("awardPoints", UUID.class, String.class, long.class, String.class, boolean.class);
            pointsField = ps.getField("points");
            return true;
        } catch (Throwable t) {
            stats = null;
            SnobbleGems.LOG.warn("[SnobbleGems] Community Points exchange is unavailable (SnobblemonCompanion 10+ not found): {}", t.toString());
            return false;
        }
    }

    public boolean available() {
        if (!mod.cfg.exchange.enabled || !bridge()) return false;
        try {
            return instance.get(null) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Rainbow symbol + name, for chat and chest menus ("&" colour codes). */
    public String pointsLabel() {
        String i = mod.cfg.exchange.pointsIcon;
        return (i == null || i.isBlank() ? "" : i + "&d ") + pointsName();
    }

    public String pointsName() {
        try {
            Object h = instance.get(null);
            Object cfg = h.getClass().getField("cfg").get(h);
            return String.valueOf(cfg.getClass().getField("pointsName").get(cfg));
        } catch (Throwable t) {
            return "Community Points";
        }
    }

    public long points(UUID id, String name) {
        if (!bridge()) return 0;
        try {
            Object h = instance.get(null);
            Object st = stats.invoke(dataField.get(h), id.toString(), name);
            return pointsField.getLong(st);
        } catch (Throwable t) {
            return 0;
        }
    }

    private boolean addPoints(UUID id, String name, long delta, String reason) {
        try {
            Object h = instance.get(null);
            award.invoke(h, id, name, delta, reason, false);
            save.invoke(dataField.get(h), fileField.get(h));
            return true;
        } catch (Throwable t) {
            SnobbleGems.LOG.error("[SnobbleGems] could not change Community Points", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ rules

    public long pointsPerGem() {
        return Math.max(1, mod.cfg.exchange.pointsPerGem);
    }

    public long costOf(long gems) {
        return gems * pointsPerGem();
    }

    public long gemsLeftToday(UUID id) {
        long cap = mod.cfg.exchange.dailyGemLimit;
        long byGems = cap > 0 ? Math.max(0, cap - day(id).gems) : Long.MAX_VALUE;
        long pcap = mod.cfg.exchange.dailyPointsLimit;
        long byPoints = pcap > 0 ? Math.max(0, pcap - day(id).points) / pointsPerGem() : Long.MAX_VALUE;
        return Math.min(byGems, byPoints);
    }

    public long gemsToday(UUID id) {
        return day(id).gems;
    }

    /** null when the trade is allowed, otherwise a plain-text reason. */
    public String why(UUID id, String name, long gems) {
        Cfg.Exchange x = mod.cfg.exchange;
        if (!available()) return "The Gem exchange is not available right now.";
        if (gems < Math.max(1, x.minGems)) return "The smallest trade is " + Math.max(1, x.minGems) + " " + mod.cfg.currency.name + ".";
        if (x.maxGemsPerTrade > 0 && gems > x.maxGemsPerTrade) return "The most you can buy at once is " + Txt.num(x.maxGemsPerTrade) + " " + mod.cfg.currency.name + ".";
        long left = gemsLeftToday(id);
        if (gems > left) return left <= 0 ? "You reached today's exchange limit. Try again tomorrow." : "You can only buy " + Txt.num(left) + " more " + mod.cfg.currency.name + " today.";
        long cost = costOf(gems), have = points(id, name);
        if (have < cost) return "Not enough " + pointsName() + ": you have " + Txt.num(have) + ", you need " + Txt.num(cost) + ".";
        return null;
    }

    /** Charges the points, pays the Gems (refunding the points if that fails). Returns null on success, otherwise the reason. */
    public String trade(ServerPlayer p, long gems) {
        UUID id = p.getUUID();
        String name = p.getGameProfile().getName();
        String reason = why(id, name, gems);
        if (reason != null) return reason;
        long cost = costOf(gems);
        if (!addPoints(id, name, -cost, "gem-exchange")) return "The exchange failed. You were not charged.";
        GemsResult r = mod.ledger.adjust(id, name, gems, "exchange:points", "exchange", null);
        if (!r.ok()) {
            addPoints(id, name, cost, "gem-exchange-refund");
            return "The exchange failed. Your points were returned.";
        }
        Day d = day(id);
        d.gems += gems;
        d.points += cost;
        saveDays();
        SnobbleGems.LOG.info("[SnobbleGems] {} exchanged {} Community Points for {} Gems", name, cost, gems);
        return null;
    }
}
