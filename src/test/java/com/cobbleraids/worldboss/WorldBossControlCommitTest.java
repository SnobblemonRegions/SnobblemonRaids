package com.cobbleraids.worldboss;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossControlCommitTest {
    static class Store implements WorldBossControlCommit.Store {
        boolean available=true, acknowledged, fail;
        int writes;
        public boolean available() { return available; }
        public long checkpoint() throws IOException { writes++; if(fail) throw new IOException("witness failed"); return 7; }
        public boolean acknowledged(long receipt) { assertEquals(7,receipt); return acknowledged; }
    }
    @Test void nativePublicationWaitsForDurabilityAndRunsOnce() {
        var store=new Store(); var calls=new AtomicInteger();
        var commit=new WorldBossControlCommit(store,()->true,calls::incrementAndGet,()->fail("cancelled"),e->fail(e));
        commit.tick(); assertEquals(0,calls.get()); assertFalse(commit.done());
        commit.tick(); assertEquals(1,store.writes); assertEquals(0,calls.get());
        store.acknowledged=true; commit.tick(); commit.tick();
        assertTrue(commit.done()); assertEquals(1,calls.get());
    }
    @Test void waitsForEarlierCheckpointWithoutBlockingOrSubmittingAnotherWrite() {
        var store=new Store(); store.available=false;
        var commit=new WorldBossControlCommit(store,()->true,()->fail("published"),()->{},e->fail(e));
        commit.tick(); commit.tick(); assertEquals(0,store.writes);
        store.available=true; commit.tick(); assertEquals(1,store.writes); assertFalse(commit.done());
    }
    @Test void changedEventOrHoldCannotPublishAnOldTransitionAfterItsSave() {
        var store=new Store(); var valid=new java.util.concurrent.atomic.AtomicBoolean(true); var cancelled=new AtomicInteger();
        var commit=new WorldBossControlCommit(store,valid::get,()->fail("stale publication"),cancelled::incrementAndGet,e->fail(e));
        commit.tick(); valid.set(false); store.acknowledged=true; commit.tick(); commit.tick();
        assertTrue(commit.done()); assertEquals(1,cancelled.get());
    }
    @Test void failedSaveCannotPublishAndCannotRetryItself() {
        var store=new Store(); store.fail=true; var failures=new AtomicInteger();
        var commit=new WorldBossControlCommit(store,()->true,()->fail("published"),()->{},e->failures.incrementAndGet());
        commit.tick(); commit.tick(); assertTrue(commit.done()); assertEquals(1,store.writes); assertEquals(1,failures.get());
    }
    @Test void cancellationAndCallbackFailureNeverReplayNativeWork() {
        var store=new Store(); var calls=new AtomicInteger(); var failures=new AtomicInteger();
        var commit=new WorldBossControlCommit(store,()->true,()->{calls.incrementAndGet();throw new IllegalStateException("native setup failed");},()->{},e->failures.incrementAndGet());
        commit.tick(); store.acknowledged=true; commit.tick(); commit.tick();
        assertEquals(1,calls.get()); assertEquals(1,failures.get()); assertTrue(commit.done());
        var cancelled=new WorldBossControlCommit(store,()->true,()->fail("cancelled work ran"),()->{},e->fail(e));
        int before=store.writes; cancelled.cancel(); cancelled.tick(); assertEquals(before,store.writes);
    }
}
