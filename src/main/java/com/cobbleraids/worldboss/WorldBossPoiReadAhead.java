package com.cobbleraids.worldboss;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.spawning.prospecting.SaccharineLogSlatheredDetector;
import com.cobbleraids.fault.RaidFaultBarrier;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Native POI I/O is read ahead; native decoding/publication and all spawn decisions remain on Main. */
public final class WorldBossPoiReadAhead {
    public interface Storage {
        boolean cobbleRaids$hasColumnData(ChunkPos position);
        CompletableFuture<Optional<CompoundTag>> cobbleRaids$startRead(ChunkPos position);
        void cobbleRaids$loadColumn(ChunkPos position);
    }
    private record Key(Storage owner,long column){
        @Override public boolean equals(Object other){return other instanceof Key key&&owner==key.owner&&column==key.column;}
        @Override public int hashCode(){return 31*System.identityHashCode(owner)+Long.hashCode(column);}
    }
    private static final class Run {
        final MinecraftServer server;final Thread owner=Thread.currentThread();
        final LinkedHashSet<Key> queued=new LinkedHashSet<>();
        final WorldBossPoiReadCache<Key,Optional<CompoundTag>> cache=new WorldBossPoiReadCache<>(32,10_000_000_000L);
        final AtomicInteger outstanding=new AtomicInteger();final AtomicLong invalidations=new AtomicLong(),writeInvalidations=new AtomicLong(),readFailures=new AtomicLong();
        long reads,published,hits,pendingFallbacks,skipped;volatile boolean failed;
        Run(MinecraftServer server){this.server=server;}
    }
    private static volatile Run run;
    private WorldBossPoiReadAhead(){}
    static void started(MinecraftServer server){run=server.isDedicatedServer()?new Run(server):null;}
    static void stopping(){var current=run;run=null;if(current!=null){current.queued.clear();current.cache.clear();}}
    private static boolean owns(Run current){return current!=null&&current.owner==Thread.currentThread()&&!current.failed;}
    static int radius(float maximumDistance,int diameter){
        if(!Float.isFinite(maximumDistance)||maximumDistance<0||diameter<1)return -1;
        // Native POI scanning loads floor(queryRadius/16)+1 chunk rings, including the
        // outer ring later excluded by its geometric predicate. Account for those reads too.
        double queryRadius=SaccharineLogSlatheredDetector.RANGE+Math.ceil(Math.hypot(diameter,diameter));
        double radius=Math.ceil(maximumDistance)+16*(Math.floor(queryRadius/16)+1)+2;
        return radius<=128?(int)radius:-1; // Larger/custom configurations retain the ordinary native path.
    }
    static void admitted(ServerPlayer player){
        var current=run;if(!owns(current))return;
        var config=Cobblemon.INSTANCE.getConfig();int radius=radius(config.getMaximumSpawningZoneDistanceFromPlayer(),config.getSpawningZoneDiameter());
        if(radius<0){current.skipped++;return;}
        var storage=(Storage)player.serverLevel().getPoiManager();var position=player.blockPosition();
        int minX=(int)Math.floorDiv((long)position.getX()-radius,16),maxX=(int)Math.floorDiv((long)position.getX()+radius,16);
        int minZ=(int)Math.floorDiv((long)position.getZ()-radius,16),maxZ=(int)Math.floorDiv((long)position.getZ()+radius,16);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            if(storage.cobbleRaids$hasColumnData(new ChunkPos(x,z)))continue;
            var key=new Key(storage,ChunkPos.asLong(x,z));
            if(current.queued.contains(key)||current.cache.contains(key))continue;
            if(current.queued.size()>=1024){current.skipped++;return;}
            current.queued.add(key);
        }
    }
    static void tick(MinecraftServer server){
        var current=run;if(!owns(current)||current.server!=server)return;
        long start=System.nanoTime();current.cache.prune(start);
        try{
            for(int count=0;count<4&&System.nanoTime()-start<2_000_000L;count++){
                var key=current.cache.firstReady(System.nanoTime());if(key==null)break;
                current.cache.publish(key,System.nanoTime(),()->{
                    var position=new ChunkPos(key.column());
                    if(!key.owner().cobbleRaids$hasColumnData(position)){
                        key.owner().cobbleRaids$loadColumn(position);current.published++;
                    }
                });
            }
            for(int count=0;count<8&&current.outstanding.get()<4&&current.cache.size()<32&&!current.queued.isEmpty();count++){
                var iterator=current.queued.iterator();var key=iterator.next();iterator.remove();
                var position=new ChunkPos(key.column());
                if(key.owner().cobbleRaids$hasColumnData(position))continue;
                current.cache.offer(key,()->{
                    var outstanding=current.outstanding;outstanding.incrementAndGet();
                    try{
                        var future=key.owner().cobbleRaids$startRead(position);
                        // Completion touches only this run's detached counter, never a world or player.
                        var errors=current.readFailures;
                        future.whenComplete((ignored,failure)->{outstanding.decrementAndGet();if(failure!=null)errors.incrementAndGet();});current.reads++;return future;
                    }catch(RuntimeException failure){outstanding.decrementAndGet();throw failure;}
                },System.nanoTime());
            }
        }catch(RuntimeException failure){disable(current);RaidFaultBarrier.report("worldboss:poi-read-ahead",failure);}
    }
    public static CompletableFuture<Optional<CompoundTag>> consume(Storage storage,ChunkPos position){
        var current=run;if(!owns(current))return null;
        var key=new Key(storage,position.toLong());boolean present=current.cache.contains(key);
        var future=current.cache.takeReady(key,System.nanoTime());
        if(future!=null)current.hits++;else if(present)current.pendingFallbacks++;
        return future;
    }
    public static void invalidate(Storage storage,long column,boolean write){
        var current=run;if(current!=null){current.cache.invalidate(new Key(storage,column));current.invalidations.incrementAndGet();if(write)current.writeInvalidations.incrementAndGet();}
    }
    public static void disable(){var current=run;if(current!=null)disable(current);}
    private static void disable(Run current){current.failed=true;current.cache.clear();if(current.owner==Thread.currentThread())current.queued.clear();}
    static String diagnostics(){var current=run;return current==null?"idle":"queued="+current.queued.size()+", cached="+current.cache.size()
            +", outstanding="+current.outstanding.get()+", reads="+current.reads+", published="+current.published+", hits="+current.hits
            +", pendingFallbacks="+current.pendingFallbacks+", readFailures="+current.readFailures.get()+", invalidations="+current.invalidations.get()+", writeInvalidations="+current.writeInvalidations.get()+", skipped="+current.skipped+", failed="+current.failed;}
}
