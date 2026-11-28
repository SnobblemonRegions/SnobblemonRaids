package com.cobbleraids.worldboss;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Background music for the World Boss: one track while a player is in a duel (or its breather), another while they stand in the zone.
 *
 * <p>The server cannot ask a client how long a sound is or whether it ended, so each player's track is remembered with the tick it began and
 * sent again when the configured length has passed. The sound is bound to the player (no distance falloff) and sent on the MUSIC channel, so
 * the player's Music slider controls it. A client without AscensionLib's resource pack plays nothing.
 *
 * <p>{@link #wanted} and {@link #due} are pure so the rules are tested without a server; the rest is packet plumbing, on the server thread.
 */
final class WorldBossMusic {

    enum Track { NONE, ZONE, BOSS }

    /** What a player is playing and when it began (game ticks). */
    private record Playing(Track track, long startedTick, ResourceLocation asset, ServerPlayer viewer) {}

    private static final Map<UUID, Playing> PLAYING = new HashMap<>();

    private WorldBossMusic() {}

    /** The duel track wins over the zone track, and the breather between chained duels counts as the duel so the music does not flicker. */
    static Track wanted(boolean inDuelOrBreather, boolean inZone) {
        if (inDuelOrBreather) return Track.BOSS;
        return inZone ? Track.ZONE : Track.NONE;
    }

    /** Whether to (re)send: nothing playing yet, a different track wanted, or the current one has run its length. */
    static boolean due(Track wanted, Track current, long startedTick, long nowTick, int lengthSeconds) {
        if (wanted == Track.NONE) return false;
        if (wanted != current) return true;
        return nowTick - startedTick >= (long) lengthSeconds * 20L;
    }

    /** Called once a second while a boss is active. */
    static void tick(MinecraftServer server, WorldBossState state, WorldBossConfig config, Set<UUID> duelling, Set<UUID> chained) {
        WorldBossConfig.Music music = config.music();
        if (!music.enabled()) {
            stopAll(server);
            return;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(state.dimension());
        Set<UUID> online = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            boolean inZone = dimension != null && WorldBossService.audienceViews(player) && (WorldBossService.inRange(player, state, config) || WorldBossService.audienceObserver(player));
            Track wanted = wanted(duelling.contains(player.getUUID()) || chained.contains(player.getUUID()), inZone);
            if (!WorldBossService.audienceViews(player)) wanted = Track.NONE;
            if (WorldBossScenes.ducksMusic(player)) wanted = Track.NONE;
            Playing now = PLAYING.get(player.getUUID());
            if (now != null && now.viewer() != player) { PLAYING.remove(player.getUUID()); now = null; }
            Track current = now == null ? Track.NONE : now.track();
            long tick = player.serverLevel().getGameTime();
            if (wanted == Track.NONE) {
                if (current != Track.NONE) stop(player, now);
                continue;
            }
            var asset = ResourceLocation.parse(sound(music, wanted));
            boolean replaced = now != null && !now.asset().equals(asset);
            if (!replaced && !due(wanted, current, now == null ? 0L : now.startedTick(), tick, seconds(music, wanted))) continue;
            if (current != Track.NONE && (current != wanted || replaced)) stop(player, now);
            player.connection.send(new ClientboundSoundEntityPacket(
                    Holder.direct(SoundEvent.createVariableRangeEvent(asset)),
                    SoundSource.MUSIC, player, music.volume(), 1.0f, player.getRandom().nextLong()));
            PLAYING.put(player.getUUID(), new Playing(wanted, tick, asset, player));
        }
        // Players who logged off: nothing to stop, just forget them so a return starts the track fresh.
        for (Iterator<UUID> it = PLAYING.keySet().iterator(); it.hasNext();) {
            if (!online.contains(it.next())) it.remove();
        }
    }

    /** Ends the music for everyone: the boss left or was beaten, the system was turned off, or the server is stopping. */
    static void stopAll(MinecraftServer server) {
        if (PLAYING.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Playing now = PLAYING.get(player.getUUID());
            if (now != null && now.viewer() == player) stop(player, now);
        }
        PLAYING.clear();
    }

    static void clear() {
        PLAYING.clear();
    }

    static void silenceOwned(ServerPlayer player) { var playing = PLAYING.get(player.getUUID()); if (playing != null && playing.viewer() == player) stop(player, playing); else PLAYING.remove(player.getUUID()); }
    private static void stop(ServerPlayer player, Playing playing) {
        player.connection.send(new ClientboundStopSoundPacket(playing.asset(), SoundSource.MUSIC));
        PLAYING.remove(player.getUUID());
    }

    private static String sound(WorldBossConfig.Music music, Track track) {
        return track == Track.BOSS ? music.bossSound() : music.zoneSound();
    }

    private static int seconds(WorldBossConfig.Music music, Track track) {
        return track == Track.BOSS ? music.bossSeconds() : music.zoneSeconds();
    }
}
