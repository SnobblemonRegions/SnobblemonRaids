package com.cobbleraids.worldboss;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossAdvancementIoTest {
    @TempDir Path root;
    private void save(WorldBossAdvancementIo.Session session,Path file,String text)throws IOException{
        try(var writer=session.open(file,StandardCharsets.UTF_8,new OpenOption[0])){assertNotNull(writer);writer.write(text);}
    }
    @Test void nativeTextIsDetachedOnMainAndWorkerWritesExactUtf8()throws Exception{
        var main=Thread.currentThread();var worker=new AtomicReference<Thread>();
        try(var session=new WorldBossAdvancementIo.Session(root,(target,bytes)->{worker.set(Thread.currentThread());Files.write(target.file(),bytes);})){ 
            var file=root.resolve("progress.json");save(session,file,"{\"é\":\"❄\"}");session.drainAll();
            assertEquals("{\"é\":\"❄\"}",Files.readString(file));assertNotSame(main,worker.get());
        }
    }
    @Test void slowDiskDoesNotBlockAutomaticWriterCloseAndReadBarrierWaits()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new AtomicBoolean();
        var session=new WorldBossAdvancementIo.Session(root,(target,bytes)->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("fixture timeout");}catch(InterruptedException error){throw new IOException(error);}Files.write(target.file(),bytes);});
        try{
            var file=root.resolve("progress.json");save(session,file,"{\"value\":1}");assertTrue(entered.await(2,TimeUnit.SECONDS));
            var reader=Thread.ofPlatform().start(()->{Thread.currentThread().interrupt();session.await(file);assertTrue(Thread.currentThread().isInterrupted());finished.set(true);});
            assertFalse(finished.get());release.countDown();reader.join(5000);assertFalse(reader.isAlive());assertTrue(finished.get());
            assertEquals("{\"value\":1}",Files.readString(file));
        }finally{release.countDown();session.close();}
    }
    @Test void successiveAutomaticSnapshotsKeepTheirFileOrder()throws Exception{
        var values=new java.util.ArrayList<String>();
        try(var session=new WorldBossAdvancementIo.Session(root,(target,bytes)->{values.add(new String(bytes,StandardCharsets.UTF_8));Files.write(target.file(),bytes);})){ 
            var file=root.resolve("progress.json");save(session,file,"first");save(session,file,"second");session.await(file);
            assertEquals(java.util.List.of("first","second"),values);assertEquals("second",Files.readString(file));
        }
    }
    @Test void unsupportedWritersStayNativeAndClosedSessionsCannotCapture()throws Exception{
        var session=new WorldBossAdvancementIo.Session(root);var file=root.resolve("progress.json");
        assertNull(session.open(root.resolve("other/progress.json"),StandardCharsets.UTF_8,new OpenOption[0]));
        assertNull(session.open(file,StandardCharsets.UTF_16,new OpenOption[0]));
        assertNull(session.open(file,StandardCharsets.UTF_8,new OpenOption[]{StandardOpenOption.APPEND}));
        assertNull(session.open(root.resolve("wrong.dat"),StandardCharsets.UTF_8,new OpenOption[0]));
        session.close();assertNull(session.open(file,StandardCharsets.UTF_8,new OpenOption[0]));assertFalse(Files.exists(file));
    }
    @Test void oversizedSnapshotFallsBackToNativeBytesWithoutLosingPrefix()throws Exception{
        try(var session=new WorldBossAdvancementIo.Session(root)){
            var file=root.resolve("large.json");String value="é".repeat(WorldBossWorldSaves.Writer.MAX_SNAPSHOT/2+1);
            save(session,file,value);assertEquals(value,Files.readString(file));
        }
    }
    @Test void workerErrorsRemainVisibleAndDisableFurtherCapture()throws Exception{
        var session=new WorldBossAdvancementIo.Session(root,(target,bytes)->{throw new IOException("fixture failure");});
        var file=root.resolve("progress.json");save(session,file,"data");assertThrows(IllegalStateException.class,session::drainAll);
        assertNull(session.open(file,StandardCharsets.UTF_8,new OpenOption[0]));assertThrows(IllegalStateException.class,session::close);
    }
    @Test void malformedUtf8RetainsTheNativeFilesWriterErrorPolicy()throws Exception{
        try(var session=new WorldBossAdvancementIo.Session(root)){
            assertThrows(java.nio.charset.MalformedInputException.class,()->save(session,root.resolve("bad.json"),"\uD800"));
        }
    }
    @Test void forcedSaveAndReloadDrainEarlierAutomaticBytesBeforeNativeWork()throws Exception{
        Path directory=Files.createDirectories(root.resolve("advancements")),file=directory.resolve("progress.json");
        WorldBossAdvancementSaves.started(root);
        try{
            Runnable automatic=()->WorldBossAdvancementSaves.direct(file,()->{
                try(var writer=WorldBossAdvancementIo.writer(file,StandardCharsets.UTF_8,new OpenOption[0])){assertNotNull(writer);writer.write("old");}
                catch(IOException error){throw new UncheckedIOException(error);}
            });
            try(var scope=new WorldBossAutosaveScope(true)){assertTrue(WorldBossAdvancementSaves.defer(file,automatic));}
            WorldBossAdvancementSaves.tick();WorldBossAdvancementSaves.beforeLoad(file);assertEquals("old",Files.readString(file));
            WorldBossAdvancementSaves.direct(file,()->{try{
                assertEquals("old",Files.readString(file));assertNull(WorldBossAdvancementIo.writer(file,StandardCharsets.UTF_8,new OpenOption[0]));
                Files.writeString(file,"forced latest");
            }catch(IOException error){throw new UncheckedIOException(error);}});
            WorldBossAdvancementSaves.drainAll();assertEquals("forced latest",Files.readString(file));
        }finally{try{WorldBossAdvancementSaves.close();}finally{WorldBossAdvancementSaves.stopped();}}
    }
}
