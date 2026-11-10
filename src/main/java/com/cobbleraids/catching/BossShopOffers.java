package com.cobbleraids.catching;

import com.cobbleraids.RaidLog;
import com.cobbleraids.config.CobbleRaidsConfig;
import com.cobbleraids.config.CobbleRaidsConfigManager;
import com.cobbleraids.presentation.CommandFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A victor's choice to keep the boss they just beat in their personal shop.
 *
 * <p>Winning a raid used to save the boss for every victor automatically. Slots are now capped
 * ({@link BossShopSlots}) and each saved boss leaves after a number of days, so the save is offered instead:
 * the player is told what they beat and clicks to keep it, and when every slot is used they pick which saved
 * boss to give up for it, or let the offer go.
 *
 * <p>An offer is the latest boss a player beat and lasts {@link #OFFER_MILLIS}. It is held in memory only:
 * unlike a Pending Reward it is a choice about something not yet earned into storage, and a restart inside
 * those few minutes costs the choice, never anything already saved. Server thread only.
 */
public final class BossShopOffers {
    /** How long a victor has to decide. */
    static final long OFFER_MILLIS = 10 * 60_000L;
    private static final String SAVE_COMMAND = "/cobbleraids bossshop save";

    private record Offer(BossSnapshot snapshot, long expiresAt) {}

    private static final Map<UUID, Offer> OFFERS = new ConcurrentHashMap<>();

    private BossShopOffers() {}

    private static CobbleRaidsConfig.PersonalBossShop config() {
        return CobbleRaidsConfigManager.get().personalBossShop();
    }

    /** Tells a victor they may keep this boss. Replaces an earlier offer they had not answered. */
    public static void offer(MinecraftServer server, ServerPlayer player, BossSnapshot snapshot) {
        OFFERS.put(player.getUUID(), new Offer(snapshot, System.currentTimeMillis() + OFFER_MILLIS));
        pruneExpired(player);
        int used = DefeatedBossSnapshots.forPlayer(player.getUUID()).size();
        MutableComponent line = Component.literal("You may keep " + name(snapshot) + " in your boss shop ("
                + used + "/" + config().maxSlots() + " slots used"
                + (config().keepDays() > 0 ? ", kept " + config().keepDays() + " days" : "") + "). ")
                .withStyle(ChatFormatting.GOLD);
        player.sendSystemMessage(line.append(button("[Keep it]", SAVE_COMMAND, "Save this boss to buy later with Raid Points")));
    }

    /**
     * Saves the offered boss, giving up {@code replace} for it when every slot is used. With the shop full
     * and nothing named, lists the saved bosses to choose from instead.
     *
     * @return whether the boss was saved
     */
    public static boolean accept(ServerPlayer player, ResourceLocation replace) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        Offer offer = OFFERS.get(playerId);
        if (offer == null || now > offer.expiresAt()) {
            OFFERS.remove(playerId);
            say(player, "There is no boss waiting to be saved. Defeat a raid to be offered one.", ChatFormatting.RED);
            return false;
        }
        pruneExpired(player);
        MinecraftServer server = player.getServer();
        Map<ResourceLocation, BossSnapshot> saved = DefeatedBossSnapshots.forPlayer(playerId);
        ResourceLocation species = offer.snapshot().species();
        BossShopSlots.Verdict verdict = BossShopSlots.verdict(saved.keySet(), species, config().maxSlots());
        if (verdict == BossShopSlots.Verdict.FULL) {
            if (replace == null || !saved.containsKey(replace)) {
                askWhichToReplace(player, offer.snapshot(), saved);
                return false;
            }
            DefeatedBossSnapshots.remove(server, playerId, replace);
        }
        DefeatedBossSnapshots.put(server, playerId, offer.snapshot());
        DefeatedBossSnapshots.flush(server);
        OFFERS.remove(playerId);
        String kept = config().keepDays() > 0 ? " It stays for " + config().keepDays() + " days." : "";
        say(player, switch (verdict) {
            case ROOM -> name(offer.snapshot()) + " was added to your boss shop." + kept;
            case SAME_SPECIES -> name(offer.snapshot()) + " replaced the one you had saved." + kept;
            case FULL -> name(offer.snapshot()) + " took the place of " + titleCase(replace.getPath()) + "." + kept;
        }, ChatFormatting.GREEN);
        RaidLog.info("Boss shop: {} saved {} ({}{})", player.getGameProfile().getName(), species, verdict,
                verdict == BossShopSlots.Verdict.FULL ? ", gave up " + replace : "");
        return true;
    }

    private static void askWhichToReplace(ServerPlayer player, BossSnapshot offered, Map<ResourceLocation, BossSnapshot> saved) {
        say(player, "Your boss shop is full. Pick one to give up for " + name(offered) + ", or ignore this to keep what you have:",
                ChatFormatting.YELLOW);
        for (BossSnapshot snapshot : sorted(saved)) {
            player.sendSystemMessage(Component.literal("  ").append(describe(snapshot)).append(Component.literal("  "))
                    .append(button("[Replace]", SAVE_COMMAND + " " + snapshot.species(),
                            "Give up " + name(snapshot) + " for " + name(offered))));
        }
    }

    /** What a player has saved and, if any, what they are being offered. For {@code /cobbleraids bossshop}. */
    public static void describeTo(ServerPlayer player) {
        pruneExpired(player);
        Map<ResourceLocation, BossSnapshot> saved = DefeatedBossSnapshots.forPlayer(player.getUUID());
        say(player, "Your boss shop: " + saved.size() + "/" + config().maxSlots() + " slots used.", ChatFormatting.GOLD);
        for (BossSnapshot snapshot : sorted(saved)) player.sendSystemMessage(Component.literal("  ").append(describe(snapshot)));
        Offer offer = OFFERS.get(player.getUUID());
        if (offer != null && System.currentTimeMillis() <= offer.expiresAt()) {
            player.sendSystemMessage(Component.literal("Waiting to be saved: " + name(offer.snapshot()) + " ")
                    .withStyle(ChatFormatting.YELLOW).append(button("[Keep it]", SAVE_COMMAND, "Save this boss")));
        }
    }

    /** Removes this player's saved bosses whose days are up, telling them which left. */
    public static void pruneExpired(ServerPlayer player) {
        List<ResourceLocation> gone = DefeatedBossSnapshots.pruneExpired(player.getServer(), player.getUUID(),
                System.currentTimeMillis(), config().keepDays());
        for (ResourceLocation species : gone) {
            say(player, titleCase(species.getPath()) + " left your boss shop: it was saved more than "
                    + config().keepDays() + " days ago.", ChatFormatting.YELLOW);
        }
        if (!gone.isEmpty()) {
            RaidLog.info("Boss shop: {} saved boss(es) of {} expired: {}", gone.size(), player.getGameProfile().getName(), gone);
        }
    }

    /** Milliseconds until the saved boss behind a {@code personal:<species>} shop entry leaves; -1 if it never does. */
    public static long millisLeft(ServerPlayer player, String entryId) {
        int colon = entryId.indexOf(':');
        ResourceLocation species = colon < 0 ? null : ResourceLocation.tryParse(entryId.substring(colon + 1));
        BossSnapshot snapshot = species == null ? null : DefeatedBossSnapshots.get(player.getUUID(), species);
        if (snapshot == null) return -1L;
        return BossShopSlots.millisLeft(snapshot.capturedAtEpochMs(), System.currentTimeMillis(), config().keepDays());
    }

    public static void onServerStopped() { OFFERS.clear(); }

    private static List<BossSnapshot> sorted(Map<ResourceLocation, BossSnapshot> saved) {
        List<BossSnapshot> list = new ArrayList<>(saved.values());
        list.sort(Comparator.comparing(snapshot -> snapshot.species().toString()));
        return list;
    }

    private static Component describe(BossSnapshot snapshot) {
        long left = BossShopSlots.millisLeft(snapshot.capturedAtEpochMs(), System.currentTimeMillis(), config().keepDays());
        return Component.literal(name(snapshot) + "  IV " + snapshot.ivPercent() + "%  EV " + snapshot.evPercent() + "%"
                + (left < 0 ? "" : "  leaves in " + CommandFormat.duration(left / 1000L))).withStyle(ChatFormatting.WHITE);
    }

    private static String name(BossSnapshot snapshot) {
        return (snapshot.shiny() ? "Shiny " : "") + titleCase(snapshot.species().getPath());
    }

    /** "iron_valiant" as "Iron Valiant". */
    private static String titleCase(String path) {
        StringBuilder out = new StringBuilder(path.length());
        boolean start = true;
        for (char c : path.toCharArray()) {
            if (c == '_' || c == '-') { out.append(' '); start = true; continue; }
            out.append(start ? Character.toUpperCase(c) : c);
            start = false;
        }
        return out.toString();
    }

    private static MutableComponent button(String label, String command, String hover) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static void say(ServerPlayer player, String text, ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(text).withStyle(colour));
    }
}
