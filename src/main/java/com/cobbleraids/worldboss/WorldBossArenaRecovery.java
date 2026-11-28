package com.cobbleraids.worldboss;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded startup restoration of an already-authored arena; never blocks on chunk I/O. */
final class WorldBossArenaRecovery implements AutoCloseable {
    interface Backend {
        boolean loaded(WorldBossChunkLease.Chunk chunk);
        CompletableFuture<Boolean> savedFull(WorldBossChunkLease.Chunk chunk);
        void retain(WorldBossChunkLease.Chunk chunk);
        void release(WorldBossChunkLease.Chunk chunk);
    }
    private final WorldBossChunkLease.Plan plan;
    private final Backend backend;
    private final BooleanSupplier owned;
    private final Runnable publish;
    private final LongSupplier nanoTime;
    private final long started;
    private final Map<WorldBossChunkLease.Chunk,CompletableFuture<Boolean>> reads=new LinkedHashMap<>();
    private final LinkedHashSet<WorldBossChunkLease.Chunk> tickets=new LinkedHashSet<>();
    private int nextRead,verified,nextTicket,ticks;
    private boolean closed,closing;
    WorldBossArenaRecovery(WorldBossChunkLease.Plan plan,Backend backend,BooleanSupplier owned,Runnable publish,LongSupplier nanoTime){
        this.plan=Objects.requireNonNull(plan);this.backend=Objects.requireNonNull(backend);this.owned=Objects.requireNonNull(owned);
        this.publish=Objects.requireNonNull(publish);this.nanoTime=Objects.requireNonNull(nanoTime);started=nanoTime.getAsLong();
    }
    String status(){return closed?"finished":"loading: saved FULL checks "+verified+"/"+plan.required().size()+", tickets "+tickets.size()+"/"+plan.retained().size();}
    boolean tick(){
        if(closed){close();return true;}
        try{
            if(!owned.getAsBoolean()){close();return true;}
            if(nanoTime.getAsLong()-started>=TimeUnit.SECONDS.toNanos(120))throw new IllegalStateException("Arena recovery exceeded 120 seconds");
            boolean loaded=true;
            for(var chunk:plan.required())if(!backend.loaded(chunk)){loaded=false;break;}
            if(loaded){if(owned.getAsBoolean())publish.run();close();return true;}
            for(var iterator=reads.entrySet().iterator();iterator.hasNext();){
                var item=iterator.next();if(!item.getValue().isDone())continue;
                if(!item.getValue().join())throw new IllegalStateException("No saved FULL arena chunk at "+item.getKey()+"; recovery will not generate terrain");
                verified++;iterator.remove();
            }
            int requested=0;
            while(nextRead<plan.required().size()&&reads.size()<4&&requested++<2){
                var chunk=plan.required().get(nextRead++);reads.put(chunk,backend.savedFull(chunk));
                if(!owned.getAsBoolean()){close();return true;}
            }
            // Verify the entire dependency ring before any ticket may initiate loading.
            if(verified==plan.required().size()){
                int added=0;
                while(nextTicket<plan.retained().size()&&added++<2){
                    var chunk=plan.retained().get(nextTicket++);tickets.add(chunk);backend.retain(chunk);
                    if(!owned.getAsBoolean()){close();return true;}
                }
                if(++ticks%20==0)for(var chunk:List.copyOf(tickets)){
                    backend.retain(chunk);
                    if(!owned.getAsBoolean()){close();return true;}
                }
            }
            return false;
        }catch(RuntimeException failure){try{close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    @Override public void close(){
        closed=true;if(closing)return;closing=true;
        try{
            RuntimeException failure=null;
            for(var iterator=tickets.iterator();iterator.hasNext();){
                var chunk=iterator.next();
                try{backend.release(chunk);iterator.remove();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            }
            if(failure!=null)throw failure;
        }finally{closing=false;}
    }
}
