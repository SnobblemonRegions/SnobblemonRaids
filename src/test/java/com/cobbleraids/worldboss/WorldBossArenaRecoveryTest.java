package com.cobbleraids.worldboss;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossArenaRecoveryTest {
    private final List<WorldBossChunkLease.Chunk> chunks=java.util.stream.IntStream.range(0,6).mapToObj(x->new WorldBossChunkLease.Chunk(x,0)).toList();
    private final WorldBossChunkLease.Plan plan=new WorldBossChunkLease.Plan(chunks.subList(0,2),chunks);
    private static final class Backend implements WorldBossArenaRecovery.Backend {
        final Map<WorldBossChunkLease.Chunk,CompletableFuture<Boolean>> reads=new LinkedHashMap<>();
        final Set<WorldBossChunkLease.Chunk> tickets=new HashSet<>();
        boolean loaded,failAdd,failRelease;
        Runnable onLoaded=()->{};
        public boolean loaded(WorldBossChunkLease.Chunk chunk){onLoaded.run();return loaded;}
        public CompletableFuture<Boolean> savedFull(WorldBossChunkLease.Chunk chunk){var future=new CompletableFuture<Boolean>();reads.put(chunk,future);return future;}
        public void retain(WorldBossChunkLease.Chunk chunk){tickets.add(chunk);if(failAdd)throw new IllegalStateException("ticket add failed after side effect");}
        public void release(WorldBossChunkLease.Chunk chunk){if(failRelease)throw new IllegalStateException("release failed");tickets.remove(chunk);}
        void complete(){reads.values().forEach(f->f.complete(true));}
    }
    @Test void coldStartupWaitsWithoutPublishingThenRestoresExactlyOnce(){
        var backend=new Backend();var published=new AtomicInteger();var now=new AtomicLong();
        var recovery=new WorldBossArenaRecovery(plan,backend,()->true,published::incrementAndGet,now::get);
        assertFalse(recovery.tick());assertEquals(2,backend.reads.size());assertTrue(backend.tickets.isEmpty());assertEquals(0,published.get());
        for(int i=0;i<4;i++){backend.complete();assertFalse(recovery.tick());}
        assertEquals(2,backend.tickets.size());assertEquals(0,published.get());
        backend.loaded=true;assertTrue(recovery.tick());assertEquals(1,published.get());assertTrue(backend.tickets.isEmpty());
        assertTrue(recovery.tick());assertEquals(1,published.get());
    }
    @Test void atMostFourReadsAreInFlightAndNoTickStartsMoreThanTwo(){
        var backend=new Backend();var recovery=new WorldBossArenaRecovery(plan,backend,()->true,()->fail("early publish"),()->0L);
        recovery.tick();assertEquals(2,backend.reads.size());recovery.tick();assertEquals(4,backend.reads.size());
        recovery.tick();assertEquals(4,backend.reads.size());assertTrue(backend.tickets.isEmpty());recovery.close();
    }
    @Test void missingSavedDependencyNeverRequestsTerrainLoading(){
        var backend=new Backend();var recovery=new WorldBossArenaRecovery(plan,backend,()->true,()->fail("early publish"),()->0L);
        recovery.tick();backend.reads.values().iterator().next().complete(false);
        assertThrows(IllegalStateException.class,recovery::tick);assertTrue(backend.tickets.isEmpty());
    }
    @Test void timeoutAndCancellationReleaseEveryTemporaryTicket(){
        var backend=new Backend();var owned=new AtomicBoolean(true);var now=new AtomicLong();
        var recovery=new WorldBossArenaRecovery(plan,backend,owned::get,()->fail("early publish"),now::get);
        for(int i=0;i<5;i++){recovery.tick();backend.complete();}assertFalse(backend.tickets.isEmpty());
        now.set(TimeUnit.SECONDS.toNanos(120));assertThrows(IllegalStateException.class,recovery::tick);assertTrue(backend.tickets.isEmpty());
        var second=new WorldBossArenaRecovery(plan,backend,owned::get,()->fail("early publish"),now::get);
        owned.set(false);assertTrue(second.tick());assertTrue(backend.tickets.isEmpty());
    }
    @Test void sideEffectingAddFailureRollsBackAndReleaseFailuresRetry(){
        var backend=new Backend();var recovery=new WorldBossArenaRecovery(plan,backend,()->true,()->fail("early publish"),()->0L);
        recovery.tick();backend.complete();recovery.tick();backend.complete();recovery.tick();backend.complete();
        backend.failAdd=true;backend.failRelease=true;
        assertThrows(IllegalStateException.class,recovery::tick);assertFalse(backend.tickets.isEmpty());
        backend.failRelease=false;assertTrue(recovery.tick());assertTrue(backend.tickets.isEmpty());
    }
    @Test void replacedOwnerCannotPublishEvenIfReadinessCheckReenters(){
        var backend=new Backend();var owned=new AtomicBoolean(true);var published=new AtomicInteger();backend.loaded=true;
        backend.onLoaded=()->owned.set(false);
        var recovery=new WorldBossArenaRecovery(plan,backend,owned::get,published::incrementAndGet,()->0L);
        assertTrue(recovery.tick());assertEquals(0,published.get());assertTrue(backend.tickets.isEmpty());
    }
}
