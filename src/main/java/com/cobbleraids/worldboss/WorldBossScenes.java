package com.cobbleraids.worldboss;

import com.cobblemon.mod.common.Cobblemon;
import com.cobbleraids.fault.RaidFaultBarrier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Optional per-viewer presentation. No path here can grant or transition an economic outcome. */
final class WorldBossScenes {
    private static final class Playback {
        final WorldBossSceneLedger.Receipt receipt;
        final ServerPlayer viewer;
        final long startedNanos, offsetMillis;
        final boolean replay;
        final String season;
        final WorldBossSceneTimeline timeline;
        final int volume;
        ResourceLocation voice;
        Playback(WorldBossSceneLedger.Receipt receipt, ServerPlayer viewer, long offset, boolean replay, String season, int volume) {
            this.receipt = receipt; this.viewer = viewer; this.offsetMillis = offset; this.replay = replay; this.season = season; this.volume = volume;
            startedNanos = System.nanoTime(); timeline = new WorldBossSceneTimeline(receipt.script());
        }
        long elapsed(long now) { long delta = now - startedNanos; return delta < 0 ? Long.MAX_VALUE : offsetMillis + delta / 1_000_000; }
    }
    private static final Map<UUID, Playback> playing = new HashMap<>();
    private static final Set<WorldBossSceneLedger.Key> consumed = new HashSet<>();
    private static final Map<UUID, Long> replayAt = new HashMap<>();
    private static long ticks;
    private WorldBossScenes() {}
    static void reset() { playing.clear(); replayAt.clear(); consumed.clear(); ticks = 0; }
    static void started(WorldBossState state) { playing.clear(); replayAt.clear(); consumed.clear(); consumed.addAll(state.scenes().receipts().keySet()); ticks = 0; }
    static void stopped(MinecraftServer server) { for (var player : server.getPlayerList().getPlayers()) skip(player); playing.clear(); replayAt.clear(); consumed.clear(); }
    static boolean ducksMusic(ServerPlayer player) { var current = playing.get(player.getUUID()); return current != null && current.viewer == player && current.voice != null; }
    static void dispatch(MinecraftServer server, WorldBossSceneLedger.Key key) {
        var state = WorldBossService.collectionState(); if (state == null) return;
        var receipt = state.scenesView().receipts().get(key); if (receipt == null || !consumed.add(key)) return;
        int audience = 0;
        for (var player : server.getPlayerList().getPlayers()) {
            if (!eligible(player, receipt)) continue; if (audience++ >= 75) break;
            RaidFaultBarrier.guard("worldboss:scene-viewer", () -> start(player, receipt, false));
        }
    }
    private static void start(ServerPlayer player, WorldBossSceneLedger.Receipt receipt, boolean replay) {
        start(player, receipt, replay, replay ? "scene replay" : null);
    }
    private static void start(ServerPlayer player, WorldBossSceneLedger.Receipt receipt, boolean replay, String label) {
        var state = WorldBossService.collectionState(); if (state == null) return;
        skip(player);
        var options = state.benefitsView().sceneOptions(player.getUUID());
        if (options.voiceVolume() == 0 && !options.subtitles() && options.effects() == WorldBossBenefits.Effects.OFF) return;
        long offset = replay || receipt.key().kind() != WorldBossSceneScript.Kind.ENTRANCE ? 0 : Math.max(0, System.currentTimeMillis() - receipt.atMillis());
        if (offset >= receipt.script().durationMillis()) return;
        var playback = new Playback(receipt, player, offset, replay, state.campaign().activeSeason(), options.voiceVolume());
        playing.put(player.getUUID(), playback);
        player.sendSystemMessage(Component.literal("[World Boss] " + receipt.name() + " - awakening " + receipt.awakening() + (label == null ? "" : " (" + label + ")") + " [Skip scene]")
                .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr worldboss scene skip"))));
        var recording = WorldBossVoicePack.recording(receipt.script());
        if (!WorldBossVoicePack.compatible(recording, receipt.script(), receipt.voiceHash())) recording = null;
        if (options.voiceVolume() > 0) {
            if (recording != null && offset <= 1000 && recording.durationMillis() <= receipt.script().durationMillis() - offset) {
                playback.voice = ResourceLocation.parse(recording.soundId());
                WorldBossMusic.silenceOwned(player);
                player.connection.send(new ClientboundSoundEntityPacket(Holder.direct(SoundEvent.createVariableRangeEvent(playback.voice)), SoundSource.VOICE,
                        player, options.voiceVolume() / 100f, 1f, player.getRandom().nextLong()));
            } else player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.VOICE, options.voiceVolume() / 500f, receipt.key().kind() == WorldBossSceneScript.Kind.VICTORY ? 1.2f : 0.7f);
        }
    }
    static void skip(ServerPlayer player) { var playback = playing.remove(player.getUUID()); if (playback != null) stopVoice(playback); }
    private static void stopVoice(Playback playback) {
        if (playback.voice != null) { playback.viewer.connection.send(new ClientboundStopSoundPacket(playback.voice, SoundSource.VOICE)); playback.voice = null; }
    }
    static void tick(MinecraftServer server) {
        var state = WorldBossService.collectionState(); if (state == null) { for (var player : server.getPlayerList().getPlayers()) skip(player); return; }
        long now = System.nanoTime(); int particleBudget = 128; ticks++;
        var viewers = java.util.List.copyOf(playing.entrySet()); int first = viewers.isEmpty() ? 0 : (int) Math.floorMod(ticks, viewers.size());
        for (int viewerIndex = 0; viewerIndex < viewers.size(); viewerIndex++) {
            var entry = viewers.get((first + viewerIndex) % viewers.size());
            var playback = entry.getValue(); var viewer = server.getPlayerList().getPlayer(entry.getKey());
            if (viewer != playback.viewer || !eligible(viewer, playback.receipt) || !phaseValid(playback, state)) { playing.remove(entry.getKey()); stopVoice(playback); continue; }
            long elapsed = playback.elapsed(now);
            if (playback.timeline.done(elapsed)) { skip(viewer); continue; }
            var options = state.benefitsView().sceneOptions(viewer.getUUID());
            if (options.voiceVolume() != playback.volume) stopVoice(playback);
            var beat = playback.timeline.advance(elapsed);
            if (beat != null && options.subtitles()) viewer.sendSystemMessage(Component.literal(playback.receipt.name() + ": " + beat.text()).withStyle(ChatFormatting.LIGHT_PURPLE));
            int interval = options.effects() == WorldBossBenefits.Effects.MINIMAL ? 20 : 10;
            if (options.effects() == WorldBossBenefits.Effects.OFF || Math.floorMod(ticks + viewer.getUUID().hashCode(), interval) != 0) continue;
            int count = options.effects() == WorldBossBenefits.Effects.MINIMAL ? 2 : 8;
            for (int point = 0; point < count && particleBudget > 0; point++, particleBudget--) {
                double angle = point * Math.PI * 2 / count + elapsed * 0.0003;
                viewer.serverLevel().sendParticles(viewer, ParticleTypes.ENCHANT, false, playback.receipt.x() + 2 * Math.cos(angle), playback.receipt.y() + 0.5,
                        playback.receipt.z() + 2 * Math.sin(angle), 1, 0, 0.05, 0, 0.01);
            }
        }
    }
    private static boolean phaseValid(Playback playback, WorldBossState state) {
        if (!Objects.equals(playback.season, state.campaign().activeSeason())) return false;
        if (playback.replay) return !state.active();
        if (!playback.receipt.key().event().equals(state.bossId())) return false;
        if (playback.receipt.key().kind() == WorldBossSceneScript.Kind.ENTRANCE) return state.active() && state.closeReason() == null && state.clock() != null && state.clock().phase() == WorldBossClock.Phase.PREPARING;
        return state.pool() != null && (state.pool().isDefeated() || state.closeReason() != null) && (!state.active() || state.rewardsPaid());
    }
    private static boolean eligible(ServerPlayer player, WorldBossSceneLedger.Receipt receipt) {
        return player != null && player.isAlive() && WorldBossService.audienceViews(player)
                && (receipt.inside(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ())
                || WorldBossService.audienceObserver(player) && WorldBossService.collectionState() != null && receipt.key().event().equals(WorldBossService.collectionState().bossId()))
                && Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) == null;
    }
    static String options(ServerPlayer player, Integer volume, Boolean subtitles, WorldBossBenefits.Effects effects) {
        var state = WorldBossService.collectionState(); if (state == null) return "World Boss scenes are unavailable.";
        var old = state.benefitsView().sceneOptions(player.getUUID());
        var options = new WorldBossBenefits.SceneOptions(volume == null ? old.voiceVolume() : volume, subtitles == null ? old.subtitles() : subtitles, effects == null ? old.effects() : effects);
        String problem = WorldBossService.saveBenefits(player.server, player.getUUID(), state.benefits().sceneOptions(player.getUUID(), options));
        if (problem == null && options.voiceVolume() != old.voiceVolume()) { var playback = playing.get(player.getUUID()); if (playback != null) stopVoice(playback); }
        return problem;
    }
    static String history(ServerPlayer player, String season, WorldBossSceneScript.Kind kind, boolean replay, Long awakening) {
        var state = WorldBossService.collectionState(); if (state == null) return "World Boss history is unavailable.";
        var chosen = state.campaign().seasons().get(season); if (chosen == null) return "Unknown boss season.";
        var receipt = state.scenesView().find(chosen.characterId(), kind, awakening);
        if (receipt == null) return "No recorded " + kind.name().toLowerCase(java.util.Locale.ROOT) + " scene exists for this season.";
        if (!replay) {
            player.sendSystemMessage(Component.literal(receipt.name() + " - awakening " + receipt.awakening() + " / " + kind).withStyle(ChatFormatting.GOLD));
            receipt.script().beats().forEach(beat -> player.sendSystemMessage(Component.literal(beat.text()).withStyle(ChatFormatting.LIGHT_PURPLE))); return null;
        }
        return localPlayback(player, receipt, "scene replay");
    }
    static String preview(ServerPlayer player, WorldBossSceneScript.Kind kind, long awakening) {
        var state = WorldBossService.collectionState(); if (state == null || state.pool() == null || !state.arena()) return "Prepare an isolated arena rehearsal first; no saved arena is available.";
        var receipt = new WorldBossSceneLedger.Receipt(new WorldBossSceneLedger.Key(UUID.randomUUID(), kind), UUID.randomUUID(), "Scene rehearsal", awakening,
                System.currentTimeMillis(), state.dimension(), state.x(), state.y(), state.z(), state.sceneRadius(), WorldBossSceneScript.select(state.definitionId(), awakening, kind));
        return localPlayback(player, receipt, "staff scene rehearsal; no rewards or history");
    }
    private static String localPlayback(ServerPlayer player, WorldBossSceneLedger.Receipt receipt, String label) {
        var state = WorldBossService.collectionState(); if (state == null || state.active() || !eligible(player, receipt)) return "Local playback is available while free in this boss's arena, between events.";
        long now = System.nanoTime(); var previous = replayAt.get(player.getUUID());
        if (previous != null && now - previous < 15_000_000_000L) return "Please wait fifteen seconds between scene replays.";
        if (playing.size() >= 75 && !playing.containsKey(player.getUUID())) return "The viewing area is busy. Please try again shortly.";
        replayAt.put(player.getUUID(), now); start(player, receipt, true, label); return null;
    }
}
