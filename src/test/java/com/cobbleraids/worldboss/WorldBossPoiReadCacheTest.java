package com.cobbleraids.worldboss;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossPoiReadCacheTest {
    @Test void expiryCannotRaceTheNativeCompletedFutureJoin(){
        var cache=new WorldBossPoiReadCache<String,String>(1,10);var future=CompletableFuture.completedFuture("native bytes");
        cache.offer("column",()->future,0);
        assertTrue(cache.publish("column",9,()->assertSame(future,cache.takeReady("column",11))));
        assertEquals(0,cache.size());
    }
    @Test void pendingNativeReadIsNeverPublishedJoinedOrCancelled(){
        var cache=new WorldBossPoiReadCache<String,String>(2,100);var future=new CompletableFuture<String>();
        assertTrue(cache.offer("column",()->future,0));assertNull(cache.firstReady(1));
        assertFalse(cache.publish("column",1,()->fail("Pending I/O reached native publication")));
        assertNull(cache.takeReady("column",1));assertFalse(future.isCancelled());
        future.complete("disk result");assertNull(cache.takeReady("column",2));
    }
    @Test void completedReadIsConsumedExactlyOnceInsideNativePublication(){
        var cache=new WorldBossPoiReadCache<String,String>(2,100);var future=CompletableFuture.completedFuture("native bytes");
        cache.offer("column",()->future,0);assertEquals("column",cache.firstReady(1));
        assertTrue(cache.publish("column",1,()->assertSame(future,cache.takeReady("column",1))));
        assertNull(cache.takeReady("column",2));assertEquals(0,cache.size());
    }
    @Test void writesAndDirtyDataInvalidateCompletedReads(){
        var cache=new WorldBossPoiReadCache<String,String>(2,100);
        cache.offer("column",()->CompletableFuture.completedFuture("old"),0);cache.invalidate("column");
        assertNull(cache.takeReady("column",1));
        cache.offer("column",()->CompletableFuture.completedFuture("new"),2);
        assertEquals("new",cache.takeReady("column",3).join());
    }
    @Test void failuresCancellationAndExpiryFallBackWithoutInventingMissingData(){
        var cache=new WorldBossPoiReadCache<String,String>(3,10);var cancelled=new CompletableFuture<String>();cancelled.cancel(false);
        cache.offer("failed",()->CompletableFuture.failedFuture(new java.io.IOException("fixture")),0);
        cache.offer("cancelled",()->cancelled,0);cache.offer("expired",()->CompletableFuture.completedFuture("old"),0);
        assertNull(cache.takeReady("failed",1));assertNull(cache.takeReady("cancelled",1));assertNull(cache.takeReady("expired",10));
    }
    @Test void duplicateAndOverflowOffersDoNotStartMoreIo(){
        var cache=new WorldBossPoiReadCache<String,String>(1,100);var reads=new AtomicInteger();
        java.util.function.Supplier<CompletableFuture<String>> read=()->{reads.incrementAndGet();return new CompletableFuture<>();};
        assertTrue(cache.offer("first",read,0));assertFalse(cache.offer("first",read,1));assertFalse(cache.offer("second",read,1));assertEquals(1,reads.get());
        cache.clear();assertTrue(cache.offer("second",read,2));assertEquals(2,reads.get());
    }
    @Test void shutdownClearCannotPublishLateCompletions(){
        var cache=new WorldBossPoiReadCache<String,String>(1,100);var future=new CompletableFuture<String>();
        cache.offer("column",()->future,0);cache.clear();future.complete("late");assertNull(cache.firstReady(1));assertEquals(0,cache.size());
    }
    @Test void concurrentInvalidationCannotResurrectAnInFlightOffer()throws Exception{
        var cache=new WorldBossPoiReadCache<String,String>(1,100);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var threads=Executors.newFixedThreadPool(2)){
            var offer=threads.submit(()->cache.offer("column",()->{
                entered.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}
                return CompletableFuture.completedFuture("old");
            },0));
            try{
                assertTrue(entered.await(2,TimeUnit.SECONDS));var invalidated=threads.submit(()->cache.invalidate("column"));
                assertThrows(TimeoutException.class,()->invalidated.get(50,TimeUnit.MILLISECONDS));
                release.countDown();assertTrue(offer.get(2,TimeUnit.SECONDS));invalidated.get(2,TimeUnit.SECONDS);
                assertNull(cache.takeReady("column",1));
            }finally{release.countDown();}
        }
    }
}
