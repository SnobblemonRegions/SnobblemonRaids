package com.cobbleraids.worldboss;

import com.cobblemon.mod.common.Cobblemon;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-validated ward actions. Unfinished channels/tokens are discarded on recovery. */
final class WorldBossWardService {
    private record Channel(ServerPlayer owner, UUID event, WorldBossAdmission.Token slot, int serial, long finishRemaining, double x, double y, double z) {}
    private record Relay(ServerPlayer owner, UUID event, WorldBossAdmission.Token slot, int serial, WorldBossRelay traversal) {}
    private static final Map<UUID, Channel> channels = new HashMap<>();
    private static final Map<UUID, Relay> relays = new HashMap<>();
    private static WorldBossState state;
    private static int announcedSerial;
    private static long hudTicks;
    private WorldBossWardService() {}

    static void started(WorldBossState value) { state = value; channels.clear(); relays.clear(); announcedSerial = 0; hudTicks = 0; }
    static void stopped() { state = null; channels.clear(); relays.clear(); }
    static void invalidate(UUID player) { channels.remove(player); relays.remove(player); }
    static int ready(MinecraftServer server) {
        var queue = WorldBossService.wardAdmission(); if (queue == null || state == null) return 0;
        var slots = queue.slots();
        int ready = 0;
        for (var player : server.getPlayerList().getPlayers()) {
            var id = player.getUUID(); var slot = slots.get(id);
            if (state.clock() != null && state.clock().phase() != WorldBossClock.Phase.PREPARING && !queue.eligibleAt(id, state.clock().remainingMillis())) continue;
            if (player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isVehicle() && !WorldBossService.arrivalPending(id) && !state.isPaused(id) && inside(player) && hasParty(player)
                    && (slot != null && WorldBossService.ownsWardDuel(id, slot.token()) || Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) == null)) ready++;
            if (ready == 50) break;
        }
        return ready;
    }

    static void tick(MinecraftServer server) {
        if (state == null || !state.active() || state.wards() == null || state.clock() == null || !state.clock().combatOpen()
                || state.closeReason() != null || state.pool().current() <= 0) { channels.clear(); relays.clear(); return; }
        var before = state.wards().active();
        state.wards().advance(state.clock().remainingMillis(), ready(server));
        var window = state.wards().active();
        if (before != window) WorldBossService.mechanicsChanged();
        if (window == null) { channels.clear(); relays.clear(); return; }
        if (window.serial() != announcedSerial) {
            announcedSerial = window.serial();
            for (var player : server.getPlayerList().getPlayers()) if (inside(player)) player.sendSystemMessage(Component.literal(
                    "Ward " + window.serial() + ": battle turns charge it. Between duels, stand at the glowing pylon. [Focus Ward]")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr worldboss ward focus"))));
            if (state.wards().route() != null) for (var player : server.getPlayerList().getPlayers()) if (inside(player)) player.sendSystemMessage(Component.literal(
                    "Carry a spark: cache > checkpoint > pylon, then click Deliver Spark and hold still for six seconds. [Collect at Cache]")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr worldboss ward relay"))));
        }
        Map<UUID, Boolean> completed = new HashMap<>();
        for (var entry : java.util.List.copyOf(channels.entrySet())) {
            var player = server.getPlayerList().getPlayer(entry.getKey()); var channel = entry.getValue();
            if (player == null || player != channel.owner() || !channel.event().equals(state.bossId()) || channel.serial() != window.serial() || eligible(player) != null
                    || !WorldBossService.wardAdmission().owns(channel.slot()) || distance(player, channel.x(), channel.y(), channel.z()) > 0.09) {
                channels.remove(entry.getKey()); continue;
            }
            if (state.clock().remainingMillis() <= channel.finishRemaining()) {
                boolean qualified = state.merit().qualified(entry.getKey());
                if (state.focusWard(entry.getKey(), window.serial())) completed.put(entry.getKey(), !qualified && state.merit().qualified(entry.getKey()));
                channels.remove(entry.getKey());
            }
        }
        for (var entry : java.util.List.copyOf(relays.entrySet())) {
            var player = server.getPlayerList().getPlayer(entry.getKey()); var relay = entry.getValue();
            if (player == null || player != relay.owner() || !relay.event().equals(state.bossId()) || relay.serial() != window.serial() || eligible(player, false) != null
                    || !WorldBossService.wardAdmission().owns(relay.slot()) || !routeAvailable(player) || !walking(player)) { relays.remove(entry.getKey()); continue; }
            var previous = relay.traversal().phase();
            var phase = relay.traversal().sample(position(player), state.clock().remainingMillis());
            if (phase == WorldBossRelay.Phase.CANCELLED) {
                relays.remove(entry.getKey()); player.sendSystemMessage(Component.literal("Spark lost. Follow the marked walking route from the cache to try again.").withStyle(ChatFormatting.YELLOW));
            } else if (phase == WorldBossRelay.Phase.COMPLETE) {
                boolean qualified = state.merit().qualified(entry.getKey());
                if (state.relayWard(entry.getKey(), window.serial())) completed.put(entry.getKey(), !qualified && state.merit().qualified(entry.getKey()));
                relays.remove(entry.getKey());
            } else if (phase != previous) player.sendSystemMessage(Component.literal(phase == WorldBossRelay.Phase.TO_PYLON
                    ? "Checkpoint reached. Carry the spark to the ward pylon." : "Pylon reached. Stop moving, then [Deliver Spark] to channel for six seconds.")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr worldboss ward relay"))));
        }
        if (!completed.isEmpty()) {
            WorldBossService.mechanicsChanged();
            if (!WorldBossService.flushMechanics(server)) { WorldBossService.holdMechanics(server, WorldBossClock.Reason.STORAGE_FAILURE); return; }
            completed.forEach((id, qualified) -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) player.sendSystemMessage(Component.literal("Ward contribution saved. Merit " + state.merit().score(id) + "/100"
                        + (qualified ? " - qualified for this event's base reward." : ".")).withStyle(ChatFormatting.AQUA));
            });
        }
        if (++hudTicks % 20 != 0) return;
        var pylon = state.wards().pylon();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!inside(player) || !WorldBossService.audienceViews(player) || state.observing(player.getUUID())) continue;
            long left = Math.max(0, (state.clock().remainingMillis() - window.endRemainingMillis() + 999) / 1000);
            var channel = channels.get(player.getUUID());
            var relay = relays.get(player.getUUID());
            player.displayClientMessage(Component.literal("Ward " + window.serial() + ": " + state.wards().active().units() + "/" + (3 * window.quorum())
                    + " charge | " + state.wards().active().people() + "/" + window.quorum() + " helpers | " + left + "s"
                    + (channel == null ? "" : " | Focus " + Math.max(0, (state.clock().remainingMillis() - channel.finishRemaining() + 999) / 1000) + "s")
                    + relayHud(relay))
                    .withStyle(ChatFormatting.AQUA), true);
            if (pylon != null && player.serverLevel().hasChunkAt(new BlockPos(pylon.x(), pylon.y(), pylon.z()))) player.serverLevel().sendParticles(player,
                    ParticleTypes.END_ROD, true, pylon.x() + 0.5, pylon.y() + 1.2, pylon.z() + 0.5, 8, 0.2, 1.2, 0.2, 0.01);
            var route = state.wards().route();
            if (route != null) for (var point : java.util.List.of(route.cache(), route.checkpoint())) {
                var pos = new BlockPos(point.x(), point.y(), point.z());
                if (player.serverLevel().hasChunkAt(pos)) player.serverLevel().sendParticles(player, ParticleTypes.COMPOSTER, true,
                        point.x() + 0.5, point.y() + 0.8, point.z() + 0.5, 4, 0.15, 0.5, 0.15, 0.01);
            }
        }
    }

    static String focus(ServerPlayer player) {
        WorldBossService.sampleMechanicsClock(player.server);
        if (state != null && state.wards() != null && state.clock() != null && state.clock().combatOpen()) state.wards().advance(state.clock().remainingMillis(), ready(player.server));
        String problem = eligible(player); if (problem != null) return problem;
        var window = state.wards().active();
        if (window.focuses().contains(player.getUUID())) return "You already focused this ward. Your progress is saved.";
        if (channels.containsKey(player.getUUID())) return "Keep still to finish your current focus.";
        if (relays.containsKey(player.getUUID())) return "Deliver your spark before starting a separate focus.";
        if (state.clock().remainingMillis() - window.endRemainingMillis() <= 6_000) return "There is not enough time left to finish a focus.";
        var slot = WorldBossService.wardAdmission().slots().get(player.getUUID());
        channels.put(player.getUUID(), new Channel(player, state.bossId(), slot.token(), window.serial(), state.clock().remainingMillis() - 6_000, player.getX(), player.getY(), player.getZ()));
        player.sendSystemMessage(Component.literal("Focus started. Stand still for six seconds.").withStyle(ChatFormatting.AQUA)); return null;
    }

    static String relay(ServerPlayer player) {
        WorldBossService.sampleMechanicsClock(player.server);
        if (state != null && state.wards() != null && state.clock() != null && state.clock().combatOpen()) state.wards().advance(state.clock().remainingMillis(), ready(player.server));
        String problem = eligible(player, false); if (problem != null) return problem;
        var route = state.wards().route(); var window = state.wards().active();
        if (route == null) return "This encounter has no relay route; its original ward rules are unchanged.";
        if (window.relays().contains(player.getUUID())) return "You already delivered a spark for this ward. Your progress is saved.";
        if (channels.containsKey(player.getUUID())) return "Finish your current ward action first.";
        if (!walking(player)) return "Dismount and walk the relay route.";
        if (!routeAvailable(player)) return "The relay route markers are unavailable.";
        var existing = relays.get(player.getUUID());
        if (existing != null) {
            if (player != existing.owner() || !existing.event().equals(state.bossId()) || existing.serial() != window.serial() || !WorldBossService.wardAdmission().owns(existing.slot())) {
                invalidate(player.getUUID()); return "Your previous spark expired. Collect a new one from the cache.";
            }
            if (state.clock().remainingMillis() - window.endRemainingMillis() <= 6_000) return "There is not enough time left to deliver a spark.";
            if (!existing.traversal().channel(position(player), state.clock().remainingMillis())) return "Follow the checkpoint route and stop at the pylon to deliver your spark.";
            player.sendSystemMessage(Component.literal("Spark channel started. Stand still for six seconds.").withStyle(ChatFormatting.GREEN)); return null;
        }
        if (position(player).distanceSquared(WorldBossRelay.at(route.cache())) > WorldBossRelay.REACH * WorldBossRelay.REACH) return "Move to the relay cache at " + coordinates(route.cache()) + ".";
        if (state.clock().remainingMillis() - window.endRemainingMillis() <= 6_000) return "There is not enough time left to deliver a spark.";
        var slot = WorldBossService.wardAdmission().slots().get(player.getUUID());
        relays.put(player.getUUID(), new Relay(player, state.bossId(), slot.token(), window.serial(), new WorldBossRelay(route, state.wards().pylon(), position(player), state.clock().remainingMillis())));
        player.sendSystemMessage(Component.literal("Spark collected. Walk to the checkpoint at " + coordinates(route.checkpoint()) + ", then the pylon at " + coordinates(state.wards().pylon()) + ". Teleports lose the spark.").withStyle(ChatFormatting.GREEN)); return null;
    }
    private static String relayHud(Relay relay) {
        if (relay == null) return "";
        return switch (relay.traversal().phase()) {
            case TO_CHECKPOINT -> " | Spark > " + coordinates(state.wards().route().checkpoint());
            case TO_PYLON -> " | Spark > " + coordinates(state.wards().pylon());
            case READY -> " | Stop and click Deliver Spark";
            case CHANNEL -> " | Spark " + Math.max(0, (state.clock().remainingMillis() - relay.traversal().finishRemaining() + 999) / 1000) + "s";
            default -> "";
        };
    }
    private static String coordinates(WorldBossWards.Pylon point) { return point.x() + ", " + point.y() + ", " + point.z(); }
    private static WorldBossRelay.Point position(ServerPlayer player) { return new WorldBossRelay.Point(player.getX(), player.getY(), player.getZ()); }
    private static boolean walking(ServerPlayer player) { return !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying; }
    private static boolean routeAvailable(ServerPlayer player) {
        var route = state.wards().route(); if (route == null) return false;
        return markerAvailable(player, route.cache(), com.cobbleraids.worldboss.arena.ArenaBlocks.relayCache)
                && markerAvailable(player, route.checkpoint(), com.cobbleraids.worldboss.arena.ArenaBlocks.relayCheckpoint);
    }
    private static boolean markerAvailable(ServerPlayer player, WorldBossWards.Pylon point, net.minecraft.world.level.block.Block block) {
        var pos = new BlockPos(point.x(), point.y(), point.z()); return player.serverLevel().hasChunkAt(pos) && player.serverLevel().getBlockState(pos).is(block);
    }
    private static String eligible(ServerPlayer player) { return eligible(player, true); }
    private static String eligible(ServerPlayer player, boolean nearPylon) {
        if (WorldBossService.controlCommitPending()) return "The World Boss transition is still being saved. Retry after acknowledgement.";
        if (WorldBossService.arrivalPending(player.getUUID())) return "Your arena arrival is queued; finish or cancel it first.";
        if (state == null || !state.active() || state.merit() == null || state.closeReason() != null || state.clock() == null || !state.clock().combatOpen()
                || state.wards().active() == null) return "No ward needs focus right now.";
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isVehicle() || state.isPaused(player.getUUID()) || !inside(player)) return "You cannot focus this ward right now.";
        if (!hasParty(player)) return "Bring at least one Pokemon to focus the ward.";
        var queue = WorldBossService.wardAdmission(); var slot = queue == null ? null : queue.slots().get(player.getUUID());
        if (slot == null || slot.phase() != WorldBossAdmission.Phase.BREATHER || Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) return "Focus is available to admitted participants between duels.";
        var pylon = state.wards().pylon();
        if (pylon == null) return "This rehearsal has no configured ward pylon.";
        var pos = new BlockPos(pylon.x(), pylon.y(), pylon.z());
        if (!player.serverLevel().hasChunkAt(pos) || !player.serverLevel().getBlockState(pos).is(com.cobbleraids.worldboss.arena.ArenaBlocks.wardPylon)) return "The ward pylon is unavailable.";
        return !nearPylon || distance(player, pylon.x() + 0.5, pylon.y(), pylon.z() + 0.5) <= 9 ? null : "Move closer to the glowing ward pylon.";
    }
    private static boolean inside(ServerPlayer player) {
        return WorldBossService.inRange(player, state, WorldBossConfigManager.get());
    }
    private static double distance(ServerPlayer player, double x, double y, double z) { double dx = player.getX() - x, dy = player.getY() - y, dz = player.getZ() - z; return dx * dx + dy * dy + dz * dz; }
    private static boolean hasParty(ServerPlayer player) { return Cobblemon.INSTANCE.getStorage().getParty(player).iterator().hasNext(); }
}
