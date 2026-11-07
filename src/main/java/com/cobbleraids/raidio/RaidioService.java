package com.cobbleraids.raidio;

import com.cobbleraids.config.RaidRarityTier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Plays Raidio to the players who have it open. The radar service says, every half second, which raid each of them is tuned to; this service reads their
 * yaw every other tick, asks that player's {@link RaidioListener} what they should hear and sends it to them alone as ordinary note-block sounds. Nothing
 * is sent to anyone else, nothing comes back from the client, and the sounds play on the "Jukebox/Note Blocks" volume, so players can turn Raidio down in
 * their own sound settings. Server thread only; registered in {@code CobbleRaids}.
 */
public final class RaidioService {
    /** The raid a player's Raidio is tuned to: where it is, in which dimension, and its tier (which picks the instrument). */
    public record Target(ResourceLocation dimension, double x, double z, RaidRarityTier tier) {}

    private static final int STEP_TICKS = 2;
    private static final Map<UUID, Listening> LISTENING = new HashMap<>();
    private static int ticks;

    private static final class Listening {
        final RaidioListener listener = new RaidioListener();
        Target target;
    }

    private RaidioService() {}

    /** Tunes a player's Raidio to {@code target}, or to nothing. Called by the radar service whenever it refreshes who is tracking what. */
    public static void publish(UUID player, Target target) {
        LISTENING.computeIfAbsent(player, ignored -> new Listening()).target = target;
    }

    /** Drops everyone who no longer has a Raidio open, so a new open starts from silence. */
    public static void retainOnly(Set<UUID> open) {
        LISTENING.keySet().retainAll(open);
    }

    public static void forget(UUID player) {
        LISTENING.remove(player);
    }

    public static void clear() {
        LISTENING.clear();
        ticks = 0;
    }

    public static void tick(MinecraftServer server) {
        if (!RaidioMode.enabled() || LISTENING.isEmpty() || ++ticks % STEP_TICKS != 0) return;
        for (Iterator<Map.Entry<UUID, Listening>> it = LISTENING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Listening> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.connection == null) {
                it.remove();
                continue;
            }
            Listening listening = entry.getValue();
            Target target = listening.target;
            // A target from the dimension the player has just left is no target at all, until the radar service catches up.
            boolean here = target != null && target.dimension().equals(player.serverLevel().dimension().location());
            for (RaidioCue cue : listening.listener.step(here, player.getYRot(), here ? target.x() - player.getX() : 0,
                    here ? target.z() - player.getZ() : 0, here ? target.tier() : null)) {
                play(player, cue);
            }
        }
    }

    /** Sends one cue to one player, from where their head is, so it is heard centred and the direction comes from the tuning and not from the speakers. */
    private static void play(ServerPlayer player, RaidioCue cue) {
        player.connection.send(new ClientboundSoundPacket(sound(cue.voice()), SoundSource.RECORDS, player.getX(), player.getEyeY(), player.getZ(),
                cue.volume(), RaidioTuning.pitch(cue.note()), player.getRandom().nextLong()));
    }

    private static Holder<SoundEvent> sound(RaidioVoice voice) {
        return switch (voice) {
            case BIT -> SoundEvents.NOTE_BLOCK_BIT;
            case PLING -> SoundEvents.NOTE_BLOCK_PLING;
            case BASS -> SoundEvents.NOTE_BLOCK_BASS;
            case HAT -> SoundEvents.NOTE_BLOCK_HAT;
        };
    }
}
