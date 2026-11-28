package com.cobbleraids.worldboss;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossWorldSavesTest {
    @TempDir Path directory;
    @BeforeAll static void version(){net.minecraft.SharedConstants.tryDetectVersion();}
    @BeforeEach void start(){WorldBossWorldSaves.started(directory);}
    @AfterEach void stop(){try{WorldBossWorldSaves.close();}catch(IOException ignored){}finally{WorldBossWorldSaves.stopped();}}
    private static class Data extends SavedData {
        final Thread thread=Thread.currentThread();int value=1;
        public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){assertSame(thread,Thread.currentThread());tag.putInt("fixture",value);return tag;}
    }
    private static CompoundTag root(int value){var root=new CompoundTag();var data=new CompoundTag();data.putInt("fixture",value);root.put("data",data);NbtUtils.addCurrentDataVersion(root);return root;}
    private static byte[] bytes(int value) throws IOException{var out=new ByteArrayOutputStream();NbtIo.write(root(value),new DataOutputStream(out));return out.toByteArray();}
    private static int value(Path file) throws IOException{return NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()).getCompound("data").getInt("fixture");}
    private static void capture(Data data,Path file){
        data.setDirty();
        try(var scope=new WorldBossAutosaveScope(true);var attempt=WorldBossWorldSaves.beforeSave(data,file)){
            var tag=new CompoundTag();tag.put("data",data.save(new CompoundTag(),null));NbtUtils.addCurrentDataVersion(tag);
            assertNotNull(attempt);assertTrue(WorldBossWorldSaves.defer(data,tag,file));
            data.setDirty(false); // Native SavedData clears this immediately after the file invocation.
        }
    }
    @Test void nativeSnapshotIsDetachedAndLaterDirtyChangesSurviveAcknowledgement() throws Exception {
        var data=new Data();Path file=directory.resolve("raids.dat");data.setDirty();capture(data,file);
        data.value=2;data.setDirty();WorldBossWorldSaves.beforeLoad(file);WorldBossWorldSaves.poll();
        assertEquals(1,value(file));assertTrue(data.isDirty());
        try(var attempt=WorldBossWorldSaves.beforeSave(data,file)){
            assertFalse(WorldBossWorldSaves.defer(data,root(2),file));
            NbtIo.writeCompressed(root(2),file);data.setDirty(false);
        }
        assertEquals(2,value(file));assertFalse(data.isDirty());
    }
    @Test void failedReplacementKeepsPriorEvidenceAndRestoresDirtyIntentOnMain() throws Exception {
        var data=new Data();Path blocked=directory.resolve("blocked.dat");Files.createDirectory(blocked);
        Files.writeString(blocked.resolve("keep"),"previous");capture(data,blocked);
        assertThrows(IOException.class,WorldBossWorldSaves::awaitAll);
        assertTrue(data.isDirty());assertTrue(WorldBossWorldSaves.failed());assertNotNull(WorldBossWorldSaves.poll());assertNull(WorldBossWorldSaves.poll());
        assertEquals("previous",Files.readString(blocked.resolve("keep")));
        try(var scope=new WorldBossAutosaveScope(true);var attempt=WorldBossWorldSaves.beforeSave(data,directory.resolve("later.dat"))){
            assertFalse(WorldBossWorldSaves.defer(data,root(2),directory.resolve("later.dat")));
        }
    }
    @Test void nativeSynchronousFailureCannotClearDirtyIntentAtMethodReturn() throws Exception {
        var data=new Data();Path file=directory.resolve("native.dat");
        try(var attempt=WorldBossWorldSaves.beforeSave(data,file)){
            WorldBossWorldSaves.directWriteFailed();data.setDirty(false);
        }
        assertTrue(data.isDirty());
    }
    @Test void detachedWriterPreservesFifoAndDoesNotInheritCallerContexts() throws Exception {
        var context=new InheritableThreadLocal<Object>();context.set(new Object());var thread=Thread.currentThread();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var values=new CopyOnWriteArrayList<Byte>();
        Path file=directory.resolve("state.dat");
        var writer=new WorldBossWorldSaves.Writer((target,snapshot)->{
            assertNotSame(thread,Thread.currentThread());assertNull(context.get());entered.countDown();
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("not released");}catch(InterruptedException stop){throw new IOException(stop);}
            values.add(snapshot[0]);
        });
        try{
            byte[] mutable={1};assertNotNull(writer.submit(new WorldBossWorldSaves.Target(file,null),mutable));
            assertTrue(entered.await(5,TimeUnit.SECONDS));mutable[0]=9;
            assertNotNull(writer.submit(new WorldBossWorldSaves.Target(file,null),new byte[]{2}));
            release.countDown();writer.drain(file);values.add((byte)3);assertEquals(List.of((byte)1,(byte)2,(byte)3),values);
        }finally{release.countDown();writer.close();context.remove();}
    }
    @Test void nativeWriterCapacityUsesRefusalAndDrainsEveryAcceptedWrite() throws Exception {
        var release=new CountDownLatch(1);var saved=new java.util.concurrent.atomic.AtomicInteger();
        var writer=new WorldBossWorldSaves.Writer((target,data)->{
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("not released");}catch(InterruptedException stop){throw new IOException(stop);}
            saved.incrementAndGet();
        });
        var target=new WorldBossWorldSaves.Target(directory.resolve("bounded.dat"),null);
        try{for(int i=0;i<128;i++)assertNotNull(writer.submit(target,new byte[]{1}));assertNull(writer.submit(target,new byte[]{2}));assertEquals(0,saved.get());}
        finally{release.countDown();writer.close();}
        assertEquals(128,saved.get());assertNull(writer.submit(target,new byte[]{3}));
    }
    @Test void oneFailedKeyDoesNotDiscardOtherAcceptedWorldFiles() throws Exception {
        var release=new CountDownLatch(1);var saved=new CopyOnWriteArrayList<Path>();
        var bad=new WorldBossWorldSaves.Target(directory.resolve("bad.dat"),null);
        var good=new WorldBossWorldSaves.Target(directory.resolve("good.dat"),null);
        var writer=new WorldBossWorldSaves.Writer((target,data)->{
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("not released");}catch(InterruptedException stop){throw new IOException(stop);}
            if(target.equals(bad))throw new IOException("failed device write");saved.add(target.file());
        });
        assertNotNull(writer.submit(bad,new byte[]{1}));assertNotNull(writer.submit(good,new byte[]{2}));release.countDown();
        assertThrows(IOException.class,writer::drainAll);assertEquals(List.of(good.file()),saved);
        assertNull(writer.submit(good,new byte[]{3}));assertThrows(IOException.class,writer::close);
    }
    @Test void interruptedWriterCloseDoesNotAbandonAcceptedFileOutput() throws Exception {
        var release=new CountDownLatch(1);var saved=new CountDownLatch(1);
        var writer=new WorldBossWorldSaves.Writer((target,data)->{
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("not released");}catch(InterruptedException stop){throw new IOException(stop);}
            saved.countDown();
        });
        assertNotNull(writer.submit(new WorldBossWorldSaves.Target(directory.resolve("close.dat"),null),bytes(1)));
        var releaser=Thread.ofPlatform().start(()->{try{Thread.sleep(25);}catch(InterruptedException ignored){}release.countDown();});
        try{Thread.currentThread().interrupt();writer.close();assertEquals(0,saved.getCount());assertTrue(Thread.currentThread().isInterrupted());}
        finally{Thread.interrupted();release.countDown();releaser.join();}
    }
    @Test void metadataUsesNativeBackupOrderAndDetachedBytes() throws Exception {
        for(int i=1;i<=2;i++)try(var scope=new WorldBossAutosaveScope(true);var metadata=WorldBossWorldSaves.beforeMetadata(directory)){
            var tag=root(i);Path temporary=WorldBossWorldSaves.metadataTemporary(directory,"level",".dat",new FileAttribute<?>[0]);
            assertFalse(Files.exists(temporary));WorldBossWorldSaves.metadataWrite(tag,temporary);tag.getCompound("data").putInt("fixture",99);
            WorldBossWorldSaves.metadataReplace(directory.resolve("level.dat"),temporary,directory.resolve("level.dat_old"));
            WorldBossWorldSaves.awaitAll();
        }
        assertEquals(2,value(directory.resolve("level.dat")));assertEquals(1,value(directory.resolve("level.dat_old")));
    }
    @Test void metadataOutsideDeferredScopeRetainsNativeTemporaryAndReplacement() throws Exception {
        try(var metadata=WorldBossWorldSaves.beforeMetadata(directory)){
            Path temporary=WorldBossWorldSaves.metadataTemporary(directory,"level",".dat",new FileAttribute<?>[0]);assertTrue(Files.exists(temporary));
            WorldBossWorldSaves.metadataWrite(root(5),temporary);
            WorldBossWorldSaves.metadataReplace(directory.resolve("level.dat"),temporary,directory.resolve("level.dat_old"));
            assertEquals(5,value(directory.resolve("level.dat")));
        }
    }
    @Test void metadataCustomNamesUseOrderedNativeFallback() throws Exception {
        try(var scope=new WorldBossAutosaveScope(true);var metadata=WorldBossWorldSaves.beforeMetadata(directory)){
            Path temporary=WorldBossWorldSaves.metadataTemporary(directory,"level",".dat",new FileAttribute<?>[0]);
            WorldBossWorldSaves.metadataWrite(root(7),temporary);
            WorldBossWorldSaves.metadataReplace(directory.resolve("custom.dat"),temporary,directory.resolve("custom.dat_old"));
        }
        assertEquals(7,value(directory.resolve("custom.dat")));
    }
}
