package com.cobbleraids.worldboss;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossAdvancementSavesTest {
    private final Path first=Path.of("first.json"),second=Path.of("second.json");
    @Test void fiftyPlayerSerializationIsPacedOnMainAndCapturesCurrentState(){
        var queue=new WorldBossAdvancementSaves.Queue();var main=Thread.currentThread();
        var value=new AtomicInteger(1);var saved=new ArrayList<Integer>();
        for(int i=0;i<51;i++)assertTrue(queue.submit(Path.of(i+".json"),()->{assertSame(main,Thread.currentThread());saved.add(value.get());}));
        assertTrue(saved.isEmpty());value.set(2);queue.tick();assertEquals(List.of(2),saved);
        queue.tick();assertEquals(2,saved.size());queue.drainAll();assertEquals(51,saved.size());
        assertTrue(queue.diagnostics().contains("pending=0, scheduled=51, processed=51, superseded=0, failed=false"));
    }
    @Test void coalescingPreservesFifoAndUsesLatestNativeObjectForTheFile(){
        var queue=new WorldBossAdvancementSaves.Queue();var saved=new ArrayList<Integer>();
        queue.submit(first,()->saved.add(1));queue.submit(second,()->saved.add(2));queue.submit(first,()->saved.add(3));
        queue.tick();queue.tick();assertEquals(List.of(3,2),saved);
        assertTrue(queue.diagnostics().contains("scheduled=2, processed=2"));
    }
    @Test void forcedLatestSaveSupersedesOlderQueuedObjectExactlyOnce(){
        var queue=new WorldBossAdvancementSaves.Queue();var saved=new ArrayList<Integer>();
        queue.submit(first,()->saved.add(1));queue.submit(second,()->saved.add(2));
        queue.direct(first,()->saved.add(3));queue.drainAll();assertEquals(List.of(3,2),saved);
        assertTrue(queue.diagnostics().contains("scheduled=2, processed=1, superseded=1"));
    }
    @Test void reloadBarrierRunsAcceptedSaveBeforeNativeProgressCanBeCleared(){
        var queue=new WorldBossAdvancementSaves.Queue();var progress=new AtomicInteger(7);var disk=new AtomicInteger();
        queue.submit(first,()->disk.set(progress.get()));queue.flush(first);progress.set(0);
        assertEquals(7,disk.get());queue.tick();assertEquals(7,disk.get());
    }
    @Test void oneFailureStillAllowsShutdownToAttemptAllOtherAcceptedFiles(){
        var queue=new WorldBossAdvancementSaves.Queue();var saved=new AtomicInteger();
        queue.submit(first,()->{throw new IllegalStateException("native hook failed");});queue.submit(second,saved::incrementAndGet);
        assertThrows(IllegalStateException.class,queue::drainAll);assertEquals(1,saved.get());
        assertFalse(queue.submit(first,()->{}));assertTrue(queue.diagnostics().contains("pending=0"));assertTrue(queue.diagnostics().contains("failed=true"));
    }
    @Test void capacityRefusesNewFilesWithoutRunningAnyNativeSerialization(){
        var queue=new WorldBossAdvancementSaves.Queue();var saved=new AtomicInteger();
        for(int i=0;i<128;i++)assertTrue(queue.submit(Path.of(i+".json"),saved::incrementAndGet));
        assertFalse(queue.submit(Path.of("overflow.json"),saved::incrementAndGet));assertEquals(0,saved.get());
        queue.drainAll();assertEquals(128,saved.get());
    }
    @Test void offThreadReadWaitsForMainAndRestoresInterruption() throws Exception{
        var queue=new WorldBossAdvancementSaves.Queue();var entered=new CountDownLatch(1);var done=new AtomicBoolean();
        var main=Thread.currentThread();var saved=new AtomicBoolean();
        queue.submit(first,()->{assertSame(main,Thread.currentThread());saved.set(true);});
        var reader=Thread.ofPlatform().start(()->{Thread.currentThread().interrupt();entered.countDown();queue.flush(first);assertTrue(Thread.currentThread().isInterrupted());done.set(true);});
        assertTrue(entered.await(5,TimeUnit.SECONDS));assertFalse(done.get());queue.tick();reader.join(5000);assertFalse(reader.isAlive());assertTrue(done.get());assertTrue(saved.get());
    }
}
