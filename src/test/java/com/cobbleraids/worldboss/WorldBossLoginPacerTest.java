package com.cobbleraids.worldboss;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossLoginPacerTest {
    @Test void admissionResourcesReleaseOnStaleOwnerTimeoutDuplicateAndStop(){
        var queue=new WorldBossLoginPacer.Queue();var done=new AtomicInteger();var ready=CompletableFuture.<Void>completedFuture(null);
        queue.offer(new Object(),ready,()->false,()->fail("stale"),()->{},0,done::incrementAndGet);queue.tick(1);assertEquals(1,done.get());
        var duplicate=new Object();queue.offer(duplicate,ready,()->true,()->{},()->{},0,done::incrementAndGet);
        assertFalse(queue.offer(duplicate,ready,()->true,()->{},()->{},0,()->{}));assertEquals(2,done.get());
        queue.offer(new Object(),new CompletableFuture<>(),()->true,()->fail("expired"),()->{},0,done::incrementAndGet);
        queue.tick(20_000_000_000L);assertEquals(3,done.get());
        queue.offer(new Object(),ready,()->true,()->{},()->{},20_000_000_000L,done::incrementAndGet);queue.close();assertEquals(4,done.get());
    }
    @Test void rejectedCallbackClosingQueueStillReleasesSelectedAndPendingResources(){
        var queue=new WorldBossLoginPacer.Queue();var done=new AtomicInteger();
        queue.offer(new Object(),CompletableFuture.failedFuture(new IllegalStateException()),()->true,()->{},queue::close,0,done::incrementAndGet);
        for(int i=0;i<2;i++)queue.offer(new Object(),CompletableFuture.completedFuture(null),()->true,()->fail("closed"),()->{},0,done::incrementAndGet);
        queue.tick(1);assertEquals(3,done.get());
    }
    @Test void unfinishedNetworkWorkNeverRunsOrWaitsOnMainAndReadyLoginsArePaced(){
        var queue=new WorldBossLoginPacer.Queue();var blocked=new CompletableFuture<Void>();var applied=new AtomicInteger();
        queue.offer(new Object(),blocked,()->true,applied::incrementAndGet,()->fail("unexpected rejection"),0);
        queue.offer(new Object(),CompletableFuture.completedFuture(null),()->true,applied::incrementAndGet,()->fail("unexpected rejection"),0);
        queue.tick(1);assertEquals(1,applied.get());queue.tick(2);assertEquals(1,applied.get());
        blocked.complete(null);assertEquals(1,applied.get());queue.tick(3);assertEquals(2,applied.get());queue.tick(4);assertEquals(2,applied.get());
    }
    @Test void readyBurstRunsOneCallbackPerTick(){
        var queue=new WorldBossLoginPacer.Queue();var applied=new AtomicInteger();
        for(int i=0;i<51;i++)assertTrue(queue.offer(new Object(),CompletableFuture.completedFuture(null),()->true,applied::incrementAndGet,()->{},0));
        for(int i=1;i<=51;i++){queue.tick(i);assertEquals(i,applied.get());}
    }
    @Test void staleOwnersAndShutdownCannotPublishLateCompletions(){
        var queue=new WorldBossLoginPacer.Queue();var valid=new AtomicBoolean(true);var ready=new CompletableFuture<Void>();
        queue.offer(new Object(),ready,valid::get,()->fail("stale callback"),()->fail("stale disconnect"),0);
        valid.set(false);ready.complete(null);queue.tick(1);
        var other=new CompletableFuture<Void>();queue.offer(new Object(),other,()->true,()->fail("closed callback"),()->{},0);
        queue.close();other.complete(null);queue.tick(2);assertFalse(queue.offer(new Object(),other,()->true,()->{},()->{},3));
    }
    @Test void failedBarriersTimeoutsDuplicatesAndCapacityRefuseWithoutFallback(){
        var queue=new WorldBossLoginPacer.Queue();var rejected=new AtomicInteger();var key=new Object();
        var future=new CompletableFuture<Void>();queue.offer(key,future,()->true,()->fail("duplicate"),rejected::incrementAndGet,0);
        assertFalse(queue.offer(key,future,()->true,()->fail("duplicate"),rejected::incrementAndGet,0));future.complete(null);queue.tick(1);
        queue.offer(new Object(),CompletableFuture.failedFuture(new IllegalStateException()),()->true,()->fail("failed"),rejected::incrementAndGet,0);
        queue.offer(new Object(),new CompletableFuture<>(),()->true,()->fail("expired"),rejected::incrementAndGet,0);
        queue.tick(20_000_000_000L);assertEquals(2,rejected.get());
        for(int i=0;i<75;i++)assertTrue(queue.offer(new Object(),new CompletableFuture<>(),()->true,()->fail("not ready"),()->{},0));
        assertFalse(queue.offer(new Object(),CompletableFuture.completedFuture(null),()->true,()->fail("no fallback"),()->{},0));
    }
    @Test void nativeCallbacksCanCloseTheQueueWithoutIteratorCorruption(){
        var queue=new WorldBossLoginPacer.Queue();
        queue.offer(new Object(),CompletableFuture.completedFuture(null),()->true,queue::close,()->{},0);
        queue.offer(new Object(),CompletableFuture.completedFuture(null),()->true,()->fail("closed"),()->{},0);
        queue.tick(1);queue.tick(2);
    }
    @Test void rejectionCallbacksCanStopTheQueueBeforeASelectedLoginRuns(){
        var queue=new WorldBossLoginPacer.Queue();
        queue.offer(new Object(),CompletableFuture.failedFuture(new IllegalStateException()),()->true,()->{},queue::close,0);
        queue.offer(new Object(),CompletableFuture.completedFuture(null),()->true,()->fail("must respect stop"),()->{},0);
        queue.tick(1);
    }
}
