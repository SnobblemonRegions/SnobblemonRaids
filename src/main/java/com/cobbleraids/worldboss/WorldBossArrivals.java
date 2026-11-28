package com.cobbleraids.worldboss;

import com.cobbleraids.worldboss.arena.ArenaMarkers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Single-server bounded arrivals and owned presentation admission. Native world rendering is separate. */
final class WorldBossArrivals {
    private record Landing(ServerPlayer owner, WorldBossTravel.Position point, WorldBossDuelSpace.Box lane) {}
    private static final WorldBossAudience audience = new WorldBossAudience();
    private static final WorldBossArrivalQueue<ServerPlayer> queue = new WorldBossArrivalQueue<>();
    private static final Map<UUID, Landing> landings = new HashMap<>();
    private static final Set<UUID> processing = new HashSet<>();
    private static final ArrayList<Arrival> prepared = new ArrayList<>();
    private static WorldBossArrivalBatch batch;
    private static final Map<UUID, Long> evacuationAt = new HashMap<>();
    private static UUID event;
    private static WorldBossArrivalPlan.Venue venue;
    private static long ticks;
    private WorldBossArrivals() {}

    static void reset() { queue.clear(); audience.clear(); landings.clear(); processing.clear(); prepared.clear(); batch = null; evacuationAt.clear(); event = null; venue = null; ticks = 0; }
    static void stopped(MinecraftServer server) {
        for (var arrival : prepared) if (server.getPlayerList().getPlayer(arrival.player.getUUID()) == arrival.player)
            tell(arrival.player, arrival.moved ? "Arrival acknowledgement stopped. Your original return remains protected; tell staff." : "Arena arrival cancelled. No queued warp was attempted.");
        for (var request : queue.all()) if (server.getPlayerList().getPlayer(request.player()) == request.owner()) tell(request.owner(), "Arena arrival cancelled because the occurrence is unavailable. No queued warp was attempted.");
        reset();
    }
    private static boolean ensure(WorldBossState state) {
        if (state == null || !state.active() || !state.arena() || state.audienceRadius() == null) return false;
        if (!state.bossId().equals(event)) {
            reset(); event = state.bossId(); venue = new WorldBossArrivalPlan.Venue(state.dimension(), state.x(), state.y(), state.z(), state.audienceRadius());
        }
        return true;
    }
    static boolean pending(UUID player) { return queue.pending(player) || processing.contains(player); }
    static boolean saving(UUID player) { return processing.contains(player); }
    static boolean batchActive() { return batch != null; }
    static boolean committing() { return batch != null && batch.waiting(); }
    static void cancel(UUID player) {
        queue.cancel(player); landings.remove(player);
        for (var arrival : prepared) if (arrival.player.getUUID().equals(player)) arrival.cancelled = true;
        // A physical watcher keeps its seat until the next presence reconciliation.
    }
    static boolean spaceAvailable(UUID owner, String dimension, WorldBossDuelSpace.Box lane) {
        for (var entry : landings.entrySet()) if (!entry.getKey().equals(owner) && entry.getValue().point().dimension().equals(dimension) && entry.getValue().lane().overlaps(lane)) return false;
        for (var arrival : prepared) if (arrival.watch && !arrival.player.getUUID().equals(owner) && arrival.target.dimension().equals(dimension) && arrival.body().overlaps(lane)) return false;
        return true;
    }
    private static WorldBossTravel.Position position(ServerPlayer player) {
        if (!Double.isFinite(player.getX()) || !Double.isFinite(player.getY()) || !Double.isFinite(player.getZ())) return null;
        return new WorldBossTravel.Position(player.serverLevel().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), 0, 0);
    }
    private static boolean participant(ServerPlayer player, WorldBossState state) {
        if (state.observing(player.getUUID()) || player.isSpectator() || !player.isAlive()) return false;
        var admission = WorldBossService.wardAdmission();
        if (WorldBossService.hasOwnedDuel(player.getUUID()) || admission != null && admission.admittedBefore(player.getUUID())) return true;
        return !state.isPaused(player.getUUID()) && mayJoin(player, state) && readyIntent(player) && WorldBossZone.inside(venue.x(), venue.y(), venue.z(), venue.radius() + 1, player.getX(), player.getY(), player.getZ());
    }
    private static boolean readyIntent(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isVehicle()
                && Float.isFinite(player.getBbWidth()) && Float.isFinite(player.getBbHeight()) && player.getBbWidth() > 0 && player.getBbWidth() <= 4 && player.getBbHeight() > 0 && player.getBbHeight() <= 8
                && WorldBossService.partySize(player) > 0 && com.cobblemon.mod.common.Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) == null;
    }
    private static boolean mayJoin(ServerPlayer player, WorldBossState state) {
        if (state.pool() == null || state.pool().current() <= 0 || state.pool().isDefeated() || state.closeReason() != null || state.clock() != null && state.clock().terminal()) return false;
        var admission = WorldBossService.wardAdmission();
        return state.clock() == null || state.clock().phase() == WorldBossClock.Phase.PREPARING || admission != null && admission.eligibleAt(player.getUUID(), state.clock().remainingMillis());
    }
    static boolean observer(ServerPlayer player) {
        var state = WorldBossService.collectionState();
        return ensure(state) && venue.contains(position(player)) && !participant(player, state) && audience.contains(player.getUUID());
    }
    static boolean views(ServerPlayer player) {
        var state = WorldBossService.collectionState();
        if (state == null || !state.active() || state.audienceRadius() == null) return true;
        return ensure(state) && venue.contains(position(player)) && (participant(player, state) || audience.contains(player.getUUID()));
    }
    static boolean allowsTeleport(ServerPlayer player, ServerLevel targetLevel, double x, double y, double z) {
        var state = WorldBossService.collectionState(); if (!ensure(state)) return true;
        var target = new WorldBossTravel.Position(targetLevel.dimension().location().toString(), x, y, z, 0, 0);
        if (!venue.contains(target) || target.matches(player.serverLevel().dimension().location().toString(), player.getX(), player.getY(), player.getZ())) return true;
        var admission = WorldBossService.wardAdmission();
        boolean known = !state.observing(player.getUUID()) && player.isAlive() && !player.isSpectator() && (WorldBossService.hasOwnedDuel(player.getUUID()) || admission != null && admission.admittedBefore(player.getUUID()));
        boolean joining = !state.observing(player.getUUID()) && !state.isPaused(player.getUUID()) && mayJoin(player, state) && readyIntent(player)
                && venue.dimension().equals(target.dimension()) && WorldBossZone.inside(venue.x(), venue.y(), venue.z(), venue.radius() + 1, x, y, z);
        if (known || joining) return true;
        return audience.admit(player.getUUID());
    }
    static String request(ServerPlayer player, WorldBossArrivalQueue.Mode requested) {
        if (processing.contains(player.getUUID())) return "Your current arrival is being saved. Wait a moment or use Return to cancel it.";
        var state = WorldBossService.collectionState();
        String problem = WorldBossService.arrivalProblem(player); if (problem != null) return problem;
        if (!ensure(state)) return "This occurrence uses an older arrival policy. Staff must review it; your saved return is unchanged.";
        var mode = requested;
        if (mode == WorldBossArrivalQueue.Mode.WARP && (state.isPaused(player.getUUID()) || WorldBossService.partySize(player) == 0 || !mayJoin(player, state))) mode = WorldBossArrivalQueue.Mode.WATCH;
        if (mode == WorldBossArrivalQueue.Mode.FIGHT && WorldBossService.partySize(player) == 0) return "Bring at least one Pokemon to join the fight. You can still choose Watch.";
        if (mode == WorldBossArrivalQueue.Mode.FIGHT && !mayJoin(player, state)) return "New participant admission is closed for this occurrence. Choose /cr worldboss observe to watch.";
        if (mode != WorldBossArrivalQueue.Mode.WATCH && WorldBossService.inRange(player, state, WorldBossConfigManager.get())) {
            cancel(player.getUUID()); return requested == WorldBossArrivalQueue.Mode.FIGHT ? WorldBossService.join(player) : "You are already inside the battle bubble.";
        }
        if (mode == WorldBossArrivalQueue.Mode.WATCH && state.observing(player.getUUID()) && observer(player) && venue.accepts(position(player), true)) return "You are already watching. Use /cr worldboss fight to join the battle.";
        boolean hadSeat = audience.contains(player.getUUID());
        if (mode == WorldBossArrivalQueue.Mode.WATCH && !audience.admit(player.getUUID())) return "The spectator terrace is full (25 viewers including pending arrivals). Your original return is unchanged.";
        boolean submitted = (queue.pending(player.getUUID()) || queue.all().size() + processing.size() < WorldBossArrivalQueue.CAPACITY)
                && queue.submit(new WorldBossArrivalQueue.Request<>(player.getUUID(), state.bossId(), mode, player, position(player), System.nanoTime()));
        if (!submitted) { if (!hadSeat) audience.release(player.getUUID()); return "The arena arrival queue is full. Try again shortly; no warp was attempted."; }
        tell(player, (mode == WorldBossArrivalQueue.Mode.WATCH ? "Spectator" : "Arena") + " arrival queued: " + queue.position(player.getUUID()) + ". Stand still; /cr worldboss return cancels it.");
        return null;
    }

    static void tick(MinecraftServer server) {
        var state = WorldBossService.collectionState();
        if (!ensure(state)) { if (event != null) stopped(server); return; }
        ticks++;
        if (batch != null) {
            var active = batch;
            try {
                active.tick();
                if (batch == active && active.done()) { batch = null; prepared.clear(); }
            } catch (java.io.IOException | RuntimeException failure) {
                com.cobbleraids.fault.RaidFaultBarrier.report("worldboss:arrival-commit", failure);
                stopped(server); WorldBossService.holdMechanics(server, WorldBossClock.Reason.STORAGE_FAILURE);
            }
            return; // Yield a frame before preparing another batch.
        }
        if (!prepared.isEmpty() && (prepared.size() >= 8 || queue.all().isEmpty())) {
            if (!state.checkpointPending()) {
                try {
                    batch = new WorldBossArrivalBatch(new WorldBossArrivalBatch.Store() {
                        public long checkpoint() throws java.io.IOException { return WorldBossService.checkpointArrivals(server); }
                        public boolean acknowledged(long receipt) { return state.checkpointAcknowledged(receipt); }
                    }, prepared);
                } catch (java.io.IOException | RuntimeException failure) {
                    com.cobbleraids.fault.RaidFaultBarrier.report("worldboss:arrival-capture", failure);
                    stopped(server); WorldBossService.holdMechanics(server, WorldBossClock.Reason.STORAGE_FAILURE);
                }
            }
            return;
        }
        landings.entrySet().removeIf(entry -> !processing.contains(entry.getKey()) && (server.getPlayerList().getPlayer(entry.getKey()) != entry.getValue().owner() || WorldBossService.hasOwnedDuel(entry.getKey())
                || state.observing(entry.getKey()) || !entry.getValue().point().matches(entry.getValue().owner().serverLevel().dimension().location().toString(), entry.getValue().owner().getX(), entry.getValue().owner().getY(), entry.getValue().owner().getZ())));
        var observers = new ArrayList<UUID>(); var online = new HashSet<UUID>();
        for (var player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            if (venue.contains(position(player)) && !participant(player, state)) observers.add(player.getUUID());
        }
        var watchers = new HashSet<>(queue.watchers()); for (var arrival : prepared) if (arrival.watch) watchers.add(arrival.player.getUUID());
        var overflow = audience.reconcile(observers, watchers);
        evacuationAt.keySet().retainAll(online);
        for (var request : queue.take()) {
            processing.add(request.player());
            try {
                var player = server.getPlayerList().getPlayer(request.player());
                if (player != request.owner()) continue;
                var source = request.source(); double dx = player.getX() - source.x(), dy = player.getY() - source.y(), dz = player.getZ() - source.z();
                String problem = request.expired(System.nanoTime()) || !request.event().equals(state.bossId()) ? "Arena arrival expired; no warp was attempted."
                        : !source.dimension().equals(player.serverLevel().dimension().location().toString()) || dx * dx + dy * dy + dz * dz > 0.25 ? "Arena arrival cancelled because you moved. Request it again when ready."
                        : WorldBossService.arrivalProblem(player);
                if (problem == null) problem = arrive(player, request.mode(), state, server);
                if (problem != null) tell(player, problem);
            } catch (RuntimeException failure) { com.cobbleraids.fault.RaidFaultBarrier.report("worldboss:arrival", failure); tell(request.owner(), "Arena arrival could not complete safely. Your saved return is protected; tell staff."); }
            finally {
                if (prepared.stream().noneMatch(arrival -> arrival.player.getUUID().equals(request.player()))) processing.remove(request.player());
                var player = server.getPlayerList().getPlayer(request.player());
                if (!processing.contains(request.player()) && (player == null || !venue.contains(position(player)) || participant(player, state))) audience.release(request.player());
            }
        }
        if (ticks % 20 != 0) return;
        for (var player : server.getPlayerList().getPlayers()) {
            if (pending(player.getUUID())) hud(player, "Arena arrivals: " + queue.position(player.getUUID()) + " | Stand still");
            else if (observer(player)) hud(player, "Watching | " + audience.size() + "/25 spectator places | /cr worldboss fight to join");
            else if (overflow.contains(player.getUUID())) hud(player, "Spectator area full | Finish any battle or dismount to return safely");
        }
        for (UUID id : overflow) {
            var player = server.getPlayerList().getPlayer(id); if (player == null || pending(id)) continue;
            long now = System.nanoTime(); Long previous = evacuationAt.get(id); if (previous != null && now - previous < 10_000_000_000L) continue;
            evacuationAt.put(id, now);
            if (WorldBossService.arrivalProblem(player) != null) continue;
            tell(player, "The spectator area is full. Moving you to a safe exit; any saved original return remains protected.");
            String failure = WorldBossTravelService.evacuate(player, point -> !venue.contains(point));
            if (failure != null) tell(player, failure);
            break; // At most one exceptional evacuation per second, separate from the two-entry budget.
        }
    }

    private static String arrive(ServerPlayer player, WorldBossArrivalQueue.Mode mode, WorldBossState state, MinecraftServer server) {
        boolean watch = mode == WorldBossArrivalQueue.Mode.WATCH;
        if (!watch && WorldBossService.partySize(player) == 0) return "Your party is no longer available. Bring a Pokemon or choose Watch; no warp was attempted.";
        if (!watch && !mayJoin(player, state)) return "Participant admission closed while you waited. Use /cr worldboss observe to watch; no warp was attempted.";
        if (!watch && !landings.containsKey(player.getUUID()) && landings.size() >= WorldBossArrivalQueue.CAPACITY) return "Battle arrival staging is full. Try again shortly; no warp was attempted.";
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(venue.dimension())));
        if (level == null) return "The arena dimension is unavailable. No warp was attempted.";
        var kind = watch ? ArenaMarkers.Kind.OBSERVER : ArenaMarkers.Kind.ENTRANCE;
        var anchors = new ArrayList<WorldBossTravel.Position>();
        for (var marker : ArenaMarkers.get(server).list(kind)) if (marker.dimension().equals(venue.dimension()) && WorldBossService.safeMarker(level, marker.pos(), kind))
            anchors.add(new WorldBossTravel.Position(marker.dimension(), marker.pos().getX() + 0.5, marker.pos().getY(), marker.pos().getZ() + 0.5, 0, 0));
        var occupied = new ArrayList<WorldBossTravel.Position>();
        for (var arrival : prepared) occupied.add(arrival.target);
        for (var other : server.getPlayerList().getPlayers()) if (other != player && venue.contains(position(other))) occupied.add(position(other));
        for (var point : WorldBossArrivalPlan.spread(WorldBossArrivalPlan.candidates(venue, anchors, watch), occupied)) {
            var stand = new Vec3(point.x(), point.y(), point.z()); var look = new Vec3(venue.x() - point.x(), 0, venue.z() - point.z());
            WorldBossDuelPlacement.Pick lane = watch ? null : WorldBossDuelPlacement.preview(player, level, stand, look, WorldBossService.duelSpace());
            if (watch ? !watchClear(player, level, point) : lane == null) continue;
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            var target = new WorldBossTravel.Position(point.dimension(), point.x(), point.y(), point.z(), yaw, 0);
            var operation = UUID.randomUUID();
            String preparation = WorldBossTravelService.prepareArrival(player, target, operation);
            if (preparation != null) return preparation;
            if (watch) state.watch(player.getUUID()); else if (mode == WorldBossArrivalQueue.Mode.FIGHT) state.resume(player.getUUID());
            var admission = WorldBossService.wardAdmission();
            if (admission != null) { if (watch) admission.pause(player.getUUID()); else if (mode == WorldBossArrivalQueue.Mode.FIGHT) admission.resume(player.getUUID(), false); }
            if (!watch) landings.put(player.getUUID(), new Landing(player, point, lane.lane()));
            prepared.add(new Arrival(player, state, target, operation, watch, () -> watch ? watchClear(player, level, point)
                    : WorldBossDuelPlacement.preview(player, level, stand, look, WorldBossService.duelSpace()) != null));
            WorldBossService.mechanicsChanged(); return null;
        }
        return watch ? "No clear, loaded spectator landing is available. Your original return is unchanged; try again shortly."
                : "No clear battle landing is available. Wait a moment or ask staff to review the arena layout. No warp was attempted.";
    }
    private static final class Arrival implements WorldBossArrivalBatch.Entry {
        final ServerPlayer player;
        final WorldBossState state;
        final UUID event, operation;
        final WorldBossTravel.Position source, target;
        final boolean watch;
        final double reservedHalf, reservedHeight;
        final java.util.function.BooleanSupplier clear;
        boolean cancelled, moved;
        Arrival(ServerPlayer player, WorldBossState state, WorldBossTravel.Position target, UUID operation, boolean watch, java.util.function.BooleanSupplier clear) {
            this.player=player; this.state=state; this.event=state.bossId(); this.target=target; this.operation=operation; this.watch=watch; this.clear=clear;
            source=WorldBossTravelService.position(player);
            reservedHalf=Math.max(0.6,player.getBbWidth())/2+0.5; reservedHeight=Math.max(1.8,player.getBbHeight());
        }
        WorldBossDuelSpace.Box body() {
            return new WorldBossDuelSpace.Box(target.x()-reservedHalf,target.y(),target.z()-reservedHalf,target.x()+reservedHalf,target.y()+reservedHeight,target.z()+reservedHalf);
        }
        private boolean connected() { return player.server.getPlayerList().getPlayer(player.getUUID()) == player; }
        public boolean eligible() {
            if (cancelled || !connected() || WorldBossService.collectionState()!=state || !event.equals(state.bossId()) || WorldBossService.arrivalProblem(player)!=null) return false;
            var timing=state.clock(); long now=System.currentTimeMillis();
            if(timing!=null && (now<timing.checkpointMillis() || timing.phase()==WorldBossClock.Phase.ACTIVE && now-timing.checkpointMillis()>=timing.remainingMillis())) return false;
            var current=position(player); if(current==null || !source.dimension().equals(current.dimension())) return false;
            double dx=current.x()-source.x(),dy=current.y()-source.y(),dz=current.z()-source.z();
            var ticket=state.travel().ticket(player.getUUID());
            return dx*dx+dy*dy+dz*dz<=0.25 && ticket!=null && ticket.operation().equals(operation) && ticket.phase()==WorldBossTravel.Phase.OUTBOUND
                    && (watch ? state.observing(player.getUUID()) : !state.observing(player.getUUID()) && !state.isPaused(player.getUUID()) && WorldBossService.partySize(player)>0 && mayJoin(player,state));
        }
        public boolean teleport() {
            try {
                boolean result=WorldBossTravelService.moveArrival(player,target,clear);
                moved=target.matches(player.serverLevel().dimension().location().toString(),player.getX(),player.getY(),player.getZ());
                return result && WorldBossService.collectionState()==state && event.equals(state.bossId());
            } catch(RuntimeException failure) { com.cobbleraids.fault.RaidFaultBarrier.report("worldboss:arrival-move",failure); return false; }
        }
        public void acknowledge() { state.travel(state.travel().arrived(player.getUUID(),operation)); }
        private void release() {
            if(!event.equals(WorldBossArrivals.event)) return;
            processing.remove(player.getUUID());
            if(!connected() || !target.matches(player.serverLevel().dimension().location().toString(),player.getX(),player.getY(),player.getZ())) landings.remove(player.getUUID());
            var current=player.server.getPlayerList().getPlayer(player.getUUID()); var point=current==null ? null : position(current);
            if(current==null || point==null || !venue.contains(point) || participant(current,state)) audience.release(player.getUUID());
        }
        public void completed() {
            release(); if(!connected() || cancelled || WorldBossService.collectionState()!=state || !event.equals(state.bossId())) return;
            tell(player,watch ? "You are watching from the terrace. Battles are paused; earned Merit and rewards are kept. [Join Fight] uses /cr worldboss fight."
                    : "You are in the battle area. Ready players inside the bubble join automatically. Stay near this spot for your next duel.");
            controls(player);
        }
        public void cancelled() { release(); if(connected()) tell(player,"Arena arrival stopped or its landing changed. Any saved original return remains protected; retry when ready."); }
    }
    private static boolean watchClear(ServerPlayer player, ServerLevel level, WorldBossTravel.Position point) {
        if (!WorldBossTravelService.safeLoaded(level, point, player.getBbWidth(), player.getBbHeight())) return false;
        double width = Math.max(0.6, player.getBbWidth());
        var body = new AABB(point.x() - width / 2 - 0.5, point.y(), point.z() - width / 2 - 0.5, point.x() + width / 2 + 0.5, point.y() + Math.max(1.8, player.getBbHeight()), point.z() + width / 2 + 0.5);
        var reserved = new WorldBossDuelSpace.Box(body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ);
        for(var arrival:prepared) if(arrival.player!=player && arrival.target.dimension().equals(point.dimension()) && arrival.body().overlaps(reserved)) return false;
        return level.getEntities(player, body, entity -> entity.isAlive() && (entity instanceof LivingEntity || entity.canBeCollidedWith()) && !entity.isSpectator()
                && !(entity instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pokemon && player.getUUID().equals(pokemon.getOwnerUUID()))).isEmpty();
    }
    private static void controls(ServerPlayer player) {
        var message = Component.literal("[Join Fight] ").withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/cr worldboss fight")));
        message.append(Component.literal("[Watch] ").withStyle(style -> style.withColor(ChatFormatting.AQUA).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/cr worldboss observe"))));
        message.append(Component.literal("[Return]").withStyle(style -> style.withColor(ChatFormatting.YELLOW).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/cr worldboss return"))));
        player.sendSystemMessage(message);
    }
    private static void tell(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal("[World Boss] " + text).withStyle(ChatFormatting.LIGHT_PURPLE)); }
    private static void hud(ServerPlayer player, String text) { player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE), true); }
}
