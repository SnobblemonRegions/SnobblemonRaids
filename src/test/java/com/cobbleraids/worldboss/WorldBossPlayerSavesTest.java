package com.cobbleraids.worldboss;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossPlayerSavesTest {
    @TempDir Path directory;
    @BeforeAll static void version() { net.minecraft.SharedConstants.tryDetectVersion(); }
    static byte[] snapshot(int value) throws IOException {
        var tag=new CompoundTag();tag.putInt("fixture",value);
        var bytes=new ByteArrayOutputStream();NbtIo.write(tag,new DataOutputStream(bytes));return bytes.toByteArray();
    }
    @Test void nativeFilesRemainReadableAndKeepThePreviousBackup() throws Exception {
        UUID player=UUID.randomUUID();var key=new WorldBossPlayerSaves.Key(directory,player);
        try(var queue=new WorldBossPlayerSaves()) {
            assertTrue(queue.submit(key,snapshot(1)));queue.drain(key);
            assertTrue(queue.submit(key,snapshot(2)));queue.drainAll();
            assertEquals(2,NbtIo.readCompressed(directory.resolve(player+".dat"),NbtAccounter.unlimitedHeap()).getInt("fixture"));
            assertEquals(1,NbtIo.readCompressed(directory.resolve(player+".dat_old"),NbtAccounter.unlimitedHeap()).getInt("fixture"));
        }
    }
    @Test void detachedBytesAndFifoOrderingProtectLaterCriticalSaves() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var values=new CopyOnWriteArrayList<Integer>();
        var owner=Thread.currentThread();
        var key=new WorldBossPlayerSaves.Key(directory,UUID.randomUUID());
        try(var queue=new WorldBossPlayerSaves((k,bytes)->{
            assertNotSame(owner,Thread.currentThread());entered.countDown();
            try { if(!release.await(5,TimeUnit.SECONDS)) throw new IOException("not released"); }
            catch(InterruptedException stop){throw new IOException(stop);}
            values.add((int)bytes[0]);
        })) {
            byte[] data={1};assertTrue(queue.submit(key,data));assertTrue(entered.await(5,TimeUnit.SECONDS));data[0]=9;
            assertTrue(queue.submit(key,new byte[]{2}));assertEquals(2,queue.pending());
            release.countDown();queue.drain(key);values.add(3);
            assertEquals(List.of(1,2,3),values);
        } finally {release.countDown();}
    }
    @Test void queueCapacityRefusesMoreWorkRatherThanRunningDiskOnTheCaller() throws Exception {
        var release=new CountDownLatch(1);var key=new WorldBossPlayerSaves.Key(directory,UUID.randomUUID());
        var queue=new WorldBossPlayerSaves((k,bytes)->{
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("not released");}
            catch(InterruptedException stop){throw new IOException(stop);}
        });
        try {
            for(int i=0;i<128;i++)assertTrue(queue.submit(key,new byte[]{1}));
            assertFalse(queue.submit(key,new byte[]{2}));assertEquals(128,queue.pending());
        } finally {release.countDown();queue.close();}
    }
    @Test void failedWritesRejectLaterAsyncWorkAndDoNotSkipOtherAcceptedPlayers() throws Exception {
        var saved=new CopyOnWriteArrayList<UUID>();var failed=new WorldBossPlayerSaves.Key(directory,UUID.randomUUID());
        var other=new WorldBossPlayerSaves.Key(directory,UUID.randomUUID());
        var release=new CountDownLatch(1);
        var queue=new WorldBossPlayerSaves((k,bytes)->{
            try {if(!release.await(5,TimeUnit.SECONDS))throw new IOException("not released");}
            catch(InterruptedException stop){throw new IOException(stop);}
            if(k.equals(failed))throw new IOException("disk failed");saved.add(k.player());
        });
        assertTrue(queue.submit(failed,new byte[]{1}));assertTrue(queue.submit(other,new byte[]{2}));release.countDown();
        assertThrows(IOException.class,queue::drainAll);assertEquals(List.of(other.player()),saved);
        assertFalse(queue.submit(other,new byte[]{3}));assertNotNull(queue.failure());assertThrows(IOException.class,queue::close);
    }
    @Test void shutdownDrainsAcceptedSnapshotsAndRejectsLaterSubmission() throws Exception {
        var saved=new CopyOnWriteArrayList<Integer>();var key=new WorldBossPlayerSaves.Key(directory,UUID.randomUUID());
        var queue=new WorldBossPlayerSaves((k,bytes)->saved.add((int)bytes[0]));
        assertTrue(queue.submit(key,new byte[]{1}));assertTrue(queue.submit(key,new byte[]{2}));queue.close();
        assertEquals(List.of(1,2),saved);assertFalse(queue.submit(key,new byte[]{3}));
    }
    @Test void workerDoesNotInheritServerThreadContexts() throws Exception {
        var context=new InheritableThreadLocal<Object>();context.set(new Object());
        try(var queue=new WorldBossPlayerSaves((key,bytes)->assertNull(context.get()))) {
            assertTrue(queue.submit(new WorldBossPlayerSaves.Key(directory,UUID.randomUUID()),new byte[]{1}));queue.drainAll();
        } finally {context.remove();}
    }
    @Test void interruptedShutdownStillWaitsForAcceptedDiskWork() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var saved=new CountDownLatch(1);
        var queue=new WorldBossPlayerSaves((key,bytes)->{
            entered.countDown();
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("not released");}
            catch(InterruptedException stop){throw new IOException(stop);}
            saved.countDown();
        });
        assertTrue(queue.submit(new WorldBossPlayerSaves.Key(directory,UUID.randomUUID()),new byte[]{1}));
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        var releaser=Thread.ofPlatform().start(()->{
            try{Thread.sleep(25);}catch(InterruptedException ignored){}
            release.countDown();
        });
        try{
            Thread.currentThread().interrupt();queue.close();
            assertEquals(0,saved.getCount());assertTrue(Thread.currentThread().isInterrupted());
        } finally{Thread.interrupted();release.countDown();releaser.join();}
    }
}
