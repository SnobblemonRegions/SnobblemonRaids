package com.cobbleraids.worldboss;

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import com.cobbleraids.fault.RaidFaultBarrier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Ordered origin commit -> native teleport -> observed-position acknowledgement. */
final class WorldBossTravelService {
    private static final TicketType<UUID> RETURN_TICKET = TicketType.create("cobbleraids_worldboss_return", java.util.Comparator.comparing(UUID::toString), 300);
    private static final Map<UUID, Load> loads = new LinkedHashMap<>();
    private static final Map<UUID, Long> requests = new LinkedHashMap<>();
    private static final class Load {
        final ServerPlayer owner;
        final MinecraftServer server;
        final ServerLevel level;
        final WorldBossTravel.Position target, start;
        final UUID token = UUID.randomUUID();
        final long startedNanos = System.nanoTime();
        final Set<ChunkPos> chunks, tickets = new LinkedHashSet<>();
        boolean active;
        volatile boolean cancelled;
        Load(ServerPlayer owner, ServerLevel level, WorldBossTravel.Position target) { this.owner = owner; server = owner.server; this.level = level; this.target = target; start = position(owner); chunks = chunks(target, owner.getBbWidth()); }
    }
    private WorldBossTravelService() {}
    static void reset() { for (var load : java.util.List.copyOf(loads.values())) cancel(load); requests.clear(); }
    private static boolean request(ServerPlayer player) { long now = System.nanoTime(); var previous = requests.get(player.getUUID()); if (previous != null && now - previous < 1_000_000_000L) return false; requests.put(player.getUUID(), now); return true; }
    private static Set<ChunkPos> chunks(WorldBossTravel.Position target, double width) {
        var result = new LinkedHashSet<ChunkPos>(); double half = Math.max(0.6, Math.min(4, width)) / 2;
        for (double x : new double[]{target.x() - half, target.x() + half}) for (double z : new double[]{target.z() - half, target.z() + half}) result.add(new ChunkPos(BlockPos.containing(x, target.y(), z)));
        return result;
    }
    private static boolean loaded(ServerLevel level, WorldBossTravel.Position target, double width) { return chunks(target, width).stream().allMatch(chunk -> level.hasChunkAt(new BlockPos(chunk.x << 4, (int) target.y(), chunk.z << 4))); }
    private static void cancel(Load load) {
        load.cancelled = true; loads.remove(load.owner.getUUID(), load);
        for (var chunk : load.tickets) load.level.getChunkSource().removeRegionTicket(RETURN_TICKET, chunk, 0, load.token); load.tickets.clear();
    }
    private static boolean valid(Load load) {
        long elapsed = System.nanoTime() - load.startedNanos;
        return !load.cancelled && loads.get(load.owner.getUUID()) == load && elapsed >= 0 && elapsed < 15_000_000_000L && load.server.getPlayerList().getPlayer(load.owner.getUUID()) == load.owner
                && load.owner.isAlive() && !load.owner.isSpectator() && !load.owner.isPassenger() && load.start.matches(load.owner.level().dimension().location().toString(), load.owner.getX(), load.owner.getY(), load.owner.getZ())
                && com.cobblemon.mod.common.Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(load.owner) == null;
    }
    private static void schedule(Load load, Runnable action) {
        if (load.cancelled) return;
        try { load.server.execute(() -> RaidFaultBarrier.guard("worldboss:return-load-completion", () -> { if (!valid(load)) { cancel(load); return; } action.run(); })); }
        catch (RuntimeException failure) { if (!load.cancelled) RaidFaultBarrier.report("worldboss:return-load-schedule", failure); }
    }
    static void tick(MinecraftServer server) {
        int active = (int) loads.values().stream().filter(load -> load.active).count(), started = 0;
        for (var load : java.util.List.copyOf(loads.values())) {
            if (!valid(load)) { if (server.getPlayerList().getPlayer(load.owner.getUUID()) == load.owner) load.owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("Return preparation stopped. Stand still and retry; your original return remains saved.")); cancel(load); continue; }
            if (load.active || active >= 4 || started >= 2) continue;
            load.active = true; active++; started++;
            var reads = load.chunks.stream().map(chunk -> load.level.getChunkSource().chunkMap.read(chunk)).toList();
            CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> schedule(load, () -> {
                if (failure != null || reads.stream().anyMatch(read -> read.join().isEmpty() || ChunkStatus.byName(read.join().orElseThrow().getString("Status")) != ChunkStatus.FULL)) {
                    load.owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your return area has no complete saved chunk data. No terrain was generated and your origin is retained; tell staff.")); cancel(load); return;
                }
                for (var chunk : load.chunks) { load.level.getChunkSource().addRegionTicket(RETURN_TICKET, chunk, 0, load.token); load.tickets.add(chunk); }
                var ready = load.chunks.stream().map(chunk -> load.level.getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true)).toList();
                CompletableFuture.allOf(ready.toArray(CompletableFuture[]::new)).whenComplete((result, loadFailure) -> schedule(load, () -> {
                    try {
                        String problem = loadFailure != null || !loaded(load.level, load.target, load.owner.getBbWidth()) ? "The return area could not be loaded. Your origin remains saved." : completeReturn(load.owner, load.target);
                        load.owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(problem == null ? "You have returned safely." : problem));
                    } finally { cancel(load); }
                }));
            }));
        }
    }
    static WorldBossTravel.Position position(ServerPlayer player) { return new WorldBossTravel.Position(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()); }
    static ServerLevel level(MinecraftServer server, WorldBossTravel.Position position) { return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(position.dimension()))); }
    static WorldBossTravel.Position spawn(MinecraftServer server) { var level = server.overworld(); var pos = level.getSharedSpawnPos(); return new WorldBossTravel.Position(level.dimension().location().toString(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getSharedSpawnAngle(), 0); }
    static boolean safeLoaded(ServerLevel level, WorldBossTravel.Position target, double width, double height) {
        if (level == null || !level.dimension().location().toString().equals(target.dimension()) || !Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || width > 4 || height <= 0 || height > 8) return false;
        width = Math.max(0.6, width); height = Math.max(1.8, height);
        if (target.y() - 0.1 < level.getMinBuildHeight() || target.y() + height >= level.getMaxBuildHeight()) return false;
        var body = new AABB(target.x() - width / 2, target.y(), target.z() - width / 2, target.x() + width / 2, target.y() + height, target.z() + width / 2);
        var border = level.getWorldBorder();
        if (body.minX < border.getMinX() || body.maxX > border.getMaxX() || body.minZ < border.getMinZ() || body.maxZ > border.getMaxZ()) return false;
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY - 0.1, body.minZ), BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            var block = level.getBlockState(pos);
            if (!block.getFluidState().isEmpty() || block.is(Blocks.MAGMA_BLOCK) || block.is(Blocks.CACTUS) || block.is(Blocks.FIRE) || block.is(Blocks.SOUL_FIRE)
                    || block.is(Blocks.CAMPFIRE) || block.is(Blocks.SOUL_CAMPFIRE) || block.is(Blocks.POWDER_SNOW) || block.is(Blocks.SWEET_BERRY_BUSH) || block.is(Blocks.WITHER_ROSE)) return false;
        }
        if (level.getBlockCollisions(null, body.deflate(0.0001)).iterator().hasNext()) return false;
        var support = new AABB(body.minX + width * 0.1, target.y() - 0.1, body.minZ + width * 0.1, body.maxX - width * 0.1, target.y(), body.maxZ - width * 0.1);
        return level.getBlockCollisions(null, support).iterator().hasNext();
    }
    private static boolean safe(ServerPlayer player, WorldBossTravel.Position target) { return safeLoaded(level(player.server, target), target, player.getBbWidth(), player.getBbHeight()); }
    private static boolean commit(ServerPlayer player, WorldBossTravel next) {
        var state = WorldBossService.collectionState(); if (state == null) return false;
        var before = state.travel(); state.travel(next);
        if (WorldBossService.flushMechanics(player.server)) return true;
        state.travel(before); return false;
    }
    static String prepareArrival(ServerPlayer player, WorldBossTravel.Position target, UUID operation) {
        var loading = loads.get(player.getUUID()); if (loading != null) cancel(loading);
        var state = WorldBossService.collectionState();
        if (state == null || !safe(player, target)) return "The arrival destination is unavailable or unsafe. No warp was attempted.";
        if (!request(player)) return "Please wait a moment before retrying your arena travel.";
        state.travel(state.travel().outbound(player.getUUID(), position(player), target, operation));
        return null;
    }
    static boolean moveArrival(ServerPlayer player, WorldBossTravel.Position target, java.util.function.BooleanSupplier clear) {
        return safe(player, target) && clear.getAsBoolean() && move(player, target) && clear.getAsBoolean();
    }
    static String warp(ServerPlayer player, WorldBossTravel.Position target) {
        return warp(player, target, () -> true);
    }
    static String warp(ServerPlayer player, WorldBossTravel.Position target, java.util.function.BooleanSupplier destinationReady) {
        var loading = loads.get(player.getUUID()); if (loading != null) cancel(loading);
        var state = WorldBossService.collectionState(); if (state == null) return "Saved arena returns are unavailable; tell an administrator.";
        var event = state.bossId();
        if (!safe(player, target) || !destinationReady.getAsBoolean()) return "The arena arrival is not loaded, clear or safe for your player. Your position is unchanged.";
        if (!request(player)) return "Please wait a moment before retrying your arena travel.";
        var operation = UUID.randomUUID(); var next = state.travel().outbound(player.getUUID(), position(player), target, operation);
        if (!commit(player, next)) return "Your return location could not be saved. No warp was attempted.";
        WorldBossService.sampleMechanicsClock(player.server);
        if (!event.equals(state.bossId()) || !state.active() || state.closeReason() != null || state.pool() == null || state.pool().current() <= 0) return "The event closed or changed while preparing your warp. Your original return remains saved.";
        if (!safe(player, target) || !destinationReady.getAsBoolean() || !move(player, target) || !destinationReady.getAsBoolean()) return "The arena warp was refused, redirected or its space changed. Your original return remains saved.";
        if (!event.equals(state.bossId())) return "The event changed during arrival. Your original return remains protected.";
        if (!commit(player, state.travel().arrived(player.getUUID(), operation))) return "You reached the arena, but the arrival acknowledgement could not save. Your original return remains protected.";
        return null;
    }
    static String evacuate(ServerPlayer player, java.util.function.Predicate<WorldBossTravel.Position> outside) {
        var state = WorldBossService.collectionState(); if (state == null) return "Saved returns are unavailable; tell staff.";
        var ticket = state.travel().ticket(player.getUUID());
        if (ticket != null && outside.test(ticket.origin()) && safe(player, ticket.origin())) return returnHome(player, true);
        var fallback = spawn(player.server);
        if (!outside.test(fallback) || !safe(player, fallback)) return "The spectator exit is unavailable. Your saved return remains protected; tell staff.";
        // A saved but currently unavailable/in-venue origin stays claimable after emergency relocation.
        return ticket == null ? completeReturn(player, fallback) : warp(player, fallback);
    }
    static String returnHome(ServerPlayer player, boolean inArena) {
        if (WorldBossArrivals.saving(player.getUUID())) return "Your arena arrival is still being saved. Please wait a moment before returning.";
        var state = WorldBossService.collectionState(); if (state == null) return "Saved arena returns are unavailable; tell an administrator.";
        var previousLoad = loads.get(player.getUUID());
        if (previousLoad != null && previousLoad.owner == player) return "Your return is already being prepared. Keep still.";
        if (previousLoad != null) cancel(previousLoad);
        var ticket = state.travel().ticket(player.getUUID());
        if (ticket == null && !inArena) return "You do not have a saved arena return.";
        if (!request(player)) return "Please wait a moment before retrying your saved return.";
        if (ticket != null && ticket.phase() == WorldBossTravel.Phase.RETURNING && matches(player, ticket.destination()) && safe(player, ticket.destination())) {
            return commit(player, state.travel().returned(player.getUUID(), ticket.operation())) ? null : "You are at the return destination; its acknowledgement is still pending. Try again shortly.";
        }
        var target = ticket == null ? spawn(player.server) : ticket.origin();
        var targetLevel = level(player.server, target);
        if (ticket != null && targetLevel != null && target.y() >= targetLevel.getMinBuildHeight() && target.y() + Math.max(1.8, player.getBbHeight()) < targetLevel.getMaxBuildHeight()
                && targetLevel.getWorldBorder().isWithinBounds(BlockPos.containing(target.x(), target.y(), target.z())) && !loaded(targetLevel, target, player.getBbWidth())) {
            if (loads.containsKey(player.getUUID())) return "Your return is already being prepared. Keep still.";
            if (loads.size() >= 75) return "The return queue is busy. Your origin is safe; try again shortly.";
            loads.put(player.getUUID(), new Load(player, targetLevel, target));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Preparing your saved return area. Stand still; your origin remains saved.")); return null;
        }
        return completeReturn(player, target);
    }
    private static String completeReturn(ServerPlayer player, WorldBossTravel.Position target) {
        var state = WorldBossService.collectionState(); if (state == null) return "Saved returns are unavailable; your origin has not been consumed.";
        var requested = target;
        if (!safe(player, target)) target = spawn(player.server);
        if (!safe(player, target)) return "The saved origin is unavailable and the server's return spawn is not loaded/safe. Your origin remains saved; tell staff.";
        if (!target.equals(requested)) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your saved origin is unavailable; returning to the safe server spawn."));
        var operation = UUID.randomUUID();
        if (!commit(player, state.travel().returning(player.getUUID(), state.travel().ticket(player.getUUID()) == null ? target : position(player), target, operation))) return "Your return could not be prepared durably. No teleport was attempted.";
        if (!safe(player, target) || !move(player, target)) return "The return was refused or redirected. Your original return remains saved.";
        if (!commit(player, state.travel().returned(player.getUUID(), operation))) return "You returned safely, but its acknowledgement could not save. Your origin remains protected; retry return to confirm.";
        return null;
    }
    private static boolean move(ServerPlayer player, WorldBossTravel.Position target) {
        var level = level(player.server, target); if (level == null) return false;
        boolean moved = player.teleportTo(level, target.x(), target.y(), target.z(), Set.of(), target.yaw(), target.pitch());
        return moved && matches(player, target) && safe(player, target);
    }
    private static boolean matches(ServerPlayer player, WorldBossTravel.Position target) { return target.matches(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ()); }
}
