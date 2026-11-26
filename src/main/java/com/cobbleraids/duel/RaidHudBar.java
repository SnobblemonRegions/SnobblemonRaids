package com.cobbleraids.duel;

import com.cobbleraids.worldboss.WorldBossBarText;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * A renowned duel raid's health bar: a boss bar at the top of each fighter's screen, worded and coloured like the World Boss's
 * (PINK with 20 notches is the slot AscensionLib's resource pack reskins), because a renowned boss is an event boss too. Shown to
 * the raid's players who are online and not out; nobody else. Server thread only.
 */
final class RaidHudBar implements DuelBossBar {
    private final ServerBossEvent event = new ServerBossEvent(Component.literal("Raid Boss"), BossEvent.BossBarColor.PINK,
            BossEvent.BossBarOverlay.NOTCHED_20);
    private final String name;
    private final Set<UUID> players;
    private final Set<UUID> out;
    private final Function<UUID, ServerPlayer> online;

    /**
     * @param players every player in the raid, read live; {@code out} those who are out of it, read live
     * @param online  a player by id, or null when they are offline
     */
    RaidHudBar(String name, Set<UUID> players, Set<UUID> out, Function<UUID, ServerPlayer> online) {
        this.name = name;
        this.players = players;
        this.out = out;
        this.online = online;
    }

    @Override
    public void update(float current, float max, int fighters, long secondsLeft, long tick) {
        event.setProgress(max <= 0f ? 0f : Math.max(0f, Math.min(1f, current / max)));
        int percent = max <= 0f ? 0 : Math.round(100f * Math.max(0f, current) / max);
        long minutesLeft = Math.max(0L, (secondsLeft + 59L) / 60L);
        event.setName(WorldBossBarText.build(name, Math.max(0f, current), max, percent, fighters, minutesLeft));
        for (UUID id : players) {
            ServerPlayer player = online.apply(id);
            if (player == null) continue;
            if (out.contains(id)) event.removePlayer(player);
            else event.addPlayer(player);
        }
    }

    @Override
    public void remove() {
        event.removeAllPlayers();
        event.setVisible(false);
    }
}
