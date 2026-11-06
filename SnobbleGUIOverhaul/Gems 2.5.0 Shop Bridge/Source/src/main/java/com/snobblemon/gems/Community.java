package com.snobblemon.gems;

import com.snobblemon.gems.api.GemsApi;
import com.snobblemon.gems.api.GemsResult;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Community chat rewards ("W", "welcome"). One rule set - kind enabled, cooldown, daily limit, duplicate keys - is applied by
 * {@link #reward}, which is used both by the built-in chat listener and by {@link GemsApi#communityReward}, so a message reported by
 * two sources can never be paid twice: the per-kind cooldown, an optional message key and a hard 3 second same-kind guard all stop it.
 */
public final class Community {
    private final SnobbleGems mod;

    private static final long MIN_GAP_MS = 3000;

    private static final class Window {
        final String kind;
        final UUID source;
        final String sourceName;
        final long until;
        final Set<UUID> paid = new HashSet<>();

        Window(String kind, UUID source, String sourceName, long until) { this.kind = kind; this.source = source; this.sourceName = sourceName; this.until = until; }
    }

    private final List<Window> windows = new ArrayList<>();
    private final Map<String, Long> lastReward = new HashMap<>();
    private final Map<String, Long> recentText = new HashMap<>();
    private final Map<String, Long> seenKeys = new HashMap<>();
    private Pattern wRx, welcomeRx;
    private String wSrc = "", welcomeSrc = "";

    public Community(SnobbleGems mod) {
        this.mod = mod;
    }

    public void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            try {
                onChat(sender, message.signedContent());
            } catch (Throwable t) {
                SnobbleGems.LOG.error("[SnobbleGems] chat reward check failed: {}", t.toString());
            }
        });
    }

    /** True when the built-in listener should stay quiet because SnobblemonCompanion (or another mod) reports community messages. */
    public boolean external() {
        String s = mod.cfg.chat.source == null ? "AUTO" : mod.cfg.chat.source.toUpperCase(Locale.ROOT);
        return switch (s) {
            case "COMPANION" -> true;
            case "OFF", "BUILTIN" -> false;
            default -> !GemsApi.EXTERNAL_COMMUNITY_SOURCES.isEmpty();
        };
    }

    private boolean off() {
        return "OFF".equalsIgnoreCase(mod.cfg.chat.source);
    }

    public String describeSource() {
        if (off()) return "OFF";
        return external() ? "EXTERNAL (" + String.join(",", GemsApi.EXTERNAL_COMMUNITY_SOURCES) + ")" : "BUILTIN";
    }

    /** Called after a config reload: the next chat message recompiles the patterns. */
    public void refreshConfig() {
        wRx = null;
        welcomeRx = null;
    }

    private void compile() {
        Cfg.Chat c = mod.cfg.chat;
        if (wRx == null || !wSrc.equals(c.w.pattern)) {
            wRx = safe(c.w.pattern, "(?i)^\\s*w{1,12}\\s*[!.]*\\s*$");
            wSrc = c.w.pattern;
        }
        if (welcomeRx == null || !welcomeSrc.equals(c.welcome.pattern)) {
            welcomeRx = safe(c.welcome.pattern, "(?i)^\\s*welcome(\\s+[\\w.]{1,24}){0,3}\\s*[!.]*\\s*$");
            welcomeSrc = c.welcome.pattern;
        }
    }

    private static Pattern safe(String rx, String fallback) {
        try {
            return Pattern.compile(rx);
        } catch (Exception e) {
            SnobbleGems.LOG.warn("[SnobbleGems] bad chat pattern '{}': {}; using the default", rx, e.getMessage());
            return Pattern.compile(fallback);
        }
    }

    // ------------------------------------------------------------------ windows

    public void openWindow(String kind, UUID source, String sourceName, int seconds) {
        synchronized (windows) {
            windows.add(new Window(kind.toLowerCase(Locale.ROOT), source, sourceName, System.currentTimeMillis() + seconds * 1000L));
        }
    }

    /** A brand-new player joined: greeters may now be paid for saying welcome. */
    public void onJoin(ServerPlayer p) {
        boolean brandNew;
        try {
            var stats = p.getStats();
            brandNew = stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) == 0 && stats.getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) == 0;
        } catch (Throwable t) {
            brandNew = false;
        }
        if (brandNew && !external() && !off() && mod.cfg.chat.welcome.enabled) {
            openWindow("welcome", p.getUUID(), p.getGameProfile().getName(), mod.cfg.chat.welcome.windowSeconds);
        }
    }

    public void tick(long tick) {
        if (tick % 200 != 0) return;
        long now = System.currentTimeMillis();
        synchronized (windows) { windows.removeIf(w -> w.until < now); }
        recentText.values().removeIf(t -> now - t > 3_600_000L);
        synchronized (this) { // the API path (other threads) uses these two maps under the same lock
            seenKeys.values().removeIf(t -> now - t > 3_600_000L);
            lastReward.values().removeIf(t -> now - t > 86_400_000L);
        }
    }

    private Window claimWindow(String kind, ServerPlayer p, boolean once) {
        long now = System.currentTimeMillis();
        synchronized (windows) {
            for (Window w : windows) {
                if (!w.kind.equals(kind) || w.until < now) continue;
                if (p.getUUID().equals(w.source)) continue; // never paid for your own wave / your own welcome
                if (once && w.paid.contains(p.getUUID())) continue;
                return w;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ built-in listener

    public void onChat(ServerPlayer p, String raw) {
        if (p == null || raw == null || external() || off()) return;
        Cfg.Chat c = mod.cfg.chat;
        String text = raw.trim();
        if (text.isEmpty() || text.length() > c.maxMessageLength) return;
        compile();
        String kind = null;
        Cfg.Kind k = null;
        if (c.w.enabled && wRx.matcher(text).matches()) { kind = "w"; k = c.w; }
        else if (c.welcome.enabled && welcomeRx.matcher(text).matches()) { kind = "welcome"; k = c.welcome; }
        if (kind == null) return;

        // spam guard: the same text again inside the window earns nothing, and keeps resetting while the spam goes on
        long now = System.currentTimeMillis();
        String tkey = p.getUUID() + "|" + text.toLowerCase(Locale.ROOT);
        Long prev = recentText.put(tkey, now);
        if (prev != null && now - prev < c.duplicateMessageSeconds * 1000L) return;

        boolean windowed = kind.equals("w") ? k.mode.equalsIgnoreCase("WINDOW") : k.mode.equalsIgnoreCase("NEW_PLAYER");
        Window w = null;
        if (windowed) {
            w = claimWindow(kind, p, k.oncePerWindow || kind.equals("w"));
            if (w == null) return;
        }
        GemsResult r = reward(p.getUUID(), p.getGameProfile().getName(), kind, null);
        if (r.ok() && w != null) w.paid.add(p.getUUID());
    }

    // ------------------------------------------------------------------ the shared rule set

    public GemsResult reward(UUID id, String name, String kind, String dedupeKey) {
        Cfg.Chat c = mod.cfg.chat;
        Cfg.Kind k = kind.equals("w") ? c.w : kind.equals("welcome") ? c.welcome : null;
        Ledger led = mod.ledger;
        if (k == null || !k.enabled || k.reward <= 0 || off()) return new GemsResult(GemsResult.Status.LIMITED, 0, led.balance(id));
        long now = System.currentTimeMillis();
        String lk = id + "|" + kind;
        synchronized (this) {
            if (dedupeKey != null) {
                if (seenKeys.containsKey(lk + "|" + dedupeKey)) return new GemsResult(GemsResult.Status.LIMITED, 0, led.balance(id));
            }
            Long last = lastReward.get(lk);
            long gap = Math.max(MIN_GAP_MS, k.cooldownSeconds * 1000L);
            if (last != null && now - last < gap) return new GemsResult(GemsResult.Status.LIMITED, 0, led.balance(id));
            long amount = k.reward;
            if (c.dailyLimit.enabled && c.dailyLimit.perPlayer > 0) {
                long left = c.dailyLimit.perPlayer - led.earnedToday(id);
                if (left <= 0) return new GemsResult(GemsResult.Status.LIMITED, 0, led.balance(id));
                amount = Math.min(amount, left);
            }
            GemsResult r = led.adjust(id, name, amount, "community:" + kind, "chat", null);
            if (!r.ok()) return r;
            lastReward.put(lk, now);
            if (dedupeKey != null) seenKeys.put(lk + "|" + dedupeKey, now);
            led.addEarnedToday(id, kind, r.applied());
            ServerPlayer sp = mod.server == null ? null : mod.server.getPlayerList().getPlayer(id);
            if (sp != null) mod.notifyReward(sp, r.applied(), kind);
            if (mod.cfg.logging.chatRewardsToConsole) SnobbleGems.LOG.info("[SnobbleGems] chat reward {} {} +{}", name, kind, r.applied());
            return r;
        }
    }

    public Iterator<Long> debugWindows() {
        synchronized (windows) { return new ArrayList<>(windows.stream().map(w -> w.until).toList()).iterator(); }
    }

    public int windowCount() {
        long now = System.currentTimeMillis();
        synchronized (windows) { return (int) windows.stream().filter(w -> w.until >= now).count(); }
    }
}
