package com.cobbleraids.worldboss;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Server-thread-only, expiring entity-ticking tickets for preloaded FULL chunks. No blocking chunk requests. */
final class WorldBossArenaChunks {
    private static final TicketType<UUID> TICKET = TicketType.create("cobbleraids_worldboss_arena", Comparator.comparing(UUID::toString), 200);
    private static final TicketType<UUID> RECOVERY = TicketType.create("cobbleraids_worldboss_recovery", Comparator.comparing(UUID::toString), 200);
    private static WorldBossChunkLease lease;
    private static ServerLevel owner;

    private WorldBossArenaChunks() {}

    static WorldBossArenaRecovery recover(ServerLevel level,UUID event,double x,double z,double radius,
                                           java.util.function.BooleanSupplier owned,Runnable publish){
        return new WorldBossArenaRecovery(WorldBossChunkLease.plan(x,z,radius),new WorldBossArenaRecovery.Backend(){
            public boolean loaded(WorldBossChunkLease.Chunk chunk){return level.getChunkSource().getChunkNow(chunk.x(),chunk.z())!=null;}
            public java.util.concurrent.CompletableFuture<Boolean> savedFull(WorldBossChunkLease.Chunk chunk){
                return level.getChunkSource().chunkMap.read(new ChunkPos(chunk.x(),chunk.z())).thenApply(saved->saved.map(tag->{
                    var data=tag.contains("Level",10)?tag.getCompound("Level"):tag;
                    String status=data.getString("Status");return status.equals("full")||status.equals("minecraft:full");
                }).orElse(false));
            }
            public void retain(WorldBossChunkLease.Chunk chunk){level.getChunkSource().addRegionTicket(RECOVERY,new ChunkPos(chunk.x(),chunk.z()),2,event);}
            public void release(WorldBossChunkLease.Chunk chunk){level.getChunkSource().removeRegionTicket(RECOVERY,new ChunkPos(chunk.x(),chunk.z()),2,event);}
        },owned,publish,System::nanoTime);
    }

    static String problem(ServerLevel level, double x, double z, double radius) {
        try {
            var plan = WorldBossChunkLease.plan(x, z, radius);
            for (var chunk : plan.required()) {
                if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
                    return "load the full arena, duel margin and two-chunk dependency ring before starting (missing " + chunk.x() + ", " + chunk.z() + "); preflight never generates terrain";
                }
            }
            return null;
        } catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
    }

    static void retain(ServerLevel level, UUID event, double x, double z, double radius) {
        var plan = WorldBossChunkLease.plan(x, z, radius);
        if (lease != null && (owner != level || !lease.matches(event, plan))) release();
        if (lease == null) {
            owner = level;
            lease = new WorldBossChunkLease(event, plan, new WorldBossChunkLease.Backend() {
                public boolean loaded(WorldBossChunkLease.Chunk chunk) { return level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null; }
                public void retain(WorldBossChunkLease.Chunk chunk, UUID id) {
                    // Distance two maps to entity-ticking level 31. Its FULL dependency ring is preflighted.
                    level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(chunk.x(), chunk.z()), 2, id);
                }
                public void release(WorldBossChunkLease.Chunk chunk, UUID id) {
                    level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(chunk.x(), chunk.z()), 2, id);
                }
            });
        }
        lease.renew();
    }

    static void release() {
        if (lease == null) return;
        lease.close();
        lease = null;
        owner = null;
    }
}
