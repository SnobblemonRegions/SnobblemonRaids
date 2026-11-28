package com.cobbleraids.worldboss;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossProfileCacheIoTest {
    @TempDir Path root;
    private Path file()throws IOException{return Files.writeString(root.resolve("cache.json"),"original");}
    private void write(WorldBossProfileCacheIo.Ticket ticket,Object owner,Path file,String text){
        ticket.use(()->{synchronized(owner){try(var writer=ticket.writer(file,StandardCharsets.UTF_8)){
            assertNotNull(writer);writer.write(text);
        }catch(IOException failure){throw new UncheckedIOException(failure);}
        try{assertEquals(text,Files.readString(file),"Native bytes must be visible before admission returns");}catch(IOException error){throw new UncheckedIOException(error);}}});
    }
    @Test void fiftyAdmissionsOpenAndCloseOnWorkerWhileEachNativeSaveIsSynchronous()throws Exception{
        var owner=new Object();var file=file();var main=Thread.currentThread();var openThread=new AtomicReference<Thread>();var closeThread=new AtomicReference<Thread>();
        try(var session=new WorldBossProfileCacheIo.Session(owner,path->{openThread.set(Thread.currentThread());return FileChannel.open(path,StandardOpenOption.WRITE);},channel->{closeThread.set(Thread.currentThread());channel.close();})){
            session.register(file);var tickets=new ArrayList<WorldBossProfileCacheIo.Ticket>();
            for(int i=0;i<51;i++)tickets.add(session.prepare());
            for(var ticket:tickets)ticket.ready().get(3,TimeUnit.SECONDS);
            for(int i=0;i<tickets.size();i++)write(tickets.get(i),owner,file,"snapshot "+i+" é ❄");
            session.close();assertEquals("snapshot 50 é ❄",Files.readString(file));assertNotSame(main,openThread.get());assertNotSame(main,closeThread.get());
            assertTrue(session.diagnostics().contains("prepared=51, opened=51, used=51, released=51, pending=0, fallbacks=0, errors=0"));
        }
    }
    @Test void windowsPathCannotBeReplacedUntilPreparedHandleIsReleased()throws Exception{
        Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var owner=new Object();var file=file();var backup=root.resolve("old.json");
        try(var session=new WorldBossProfileCacheIo.Session(owner)){
            session.register(file);var ticket=session.prepare();ticket.ready().get(2,TimeUnit.SECONDS);
            assertThrows(IOException.class,()->Files.move(file,backup));write(ticket,owner,file,"complete old");
        }
        Files.move(file,backup);Files.writeString(file,"replacement");
        try(var session=new WorldBossProfileCacheIo.Session(owner)){
            session.register(file);var ticket=session.prepare();ticket.ready().get(2,TimeUnit.SECONDS);write(ticket,owner,file,"complete new");
        }
        assertEquals("complete old",Files.readString(backup));assertEquals("complete new",Files.readString(file));
    }
    @Test void slowNativeCloseDoesNotBlockTheMainAdmissionCallback()throws Exception{
        var owner=new Object();var file=file();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var session=new WorldBossProfileCacheIo.Session(owner,path->FileChannel.open(path,StandardOpenOption.WRITE),channel->{
            entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("fixture timeout");}catch(InterruptedException failure){throw new IOException(failure);}finally{channel.close();}
        });
        try{
            session.register(file);var ticket=session.prepare();ticket.ready().get(2,TimeUnit.SECONDS);write(ticket,owner,file,"complete");
            assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals("complete",Files.readString(file));
        }finally{release.countDown();session.close();}
        assertTrue(session.diagnostics().contains("released=1, pending=0"));
    }
    @Test void cancelledAdmissionsReleaseLateOpenWithoutWritingAnything()throws Exception{
        var owner=new Object();var file=file();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var session=new WorldBossProfileCacheIo.Session(owner,path->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("fixture timeout");}catch(InterruptedException error){throw new IOException(error);}return FileChannel.open(path,StandardOpenOption.WRITE);},FileChannel::close);
        try{
            session.register(file);var ticket=session.prepare();assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class,()->ticket.use(()->fail("not ready")));ticket.close();release.countDown();
        }finally{release.countDown();session.close();}
        assertEquals("original",Files.readString(file));Files.move(file,root.resolve("closed.json"));
        assertTrue(session.diagnostics().contains("released=1, pending=0"));
    }
    @Test void missingFilePreservesOriginalNativeCreationInsteadOfRejectingLogin()throws Exception{
        var owner=new Object();var file=root.resolve("missing.json");
        try(var session=new WorldBossProfileCacheIo.Session(owner)){
            session.register(file);var ticket=session.prepare();ticket.ready().get(2,TimeUnit.SECONDS);
            ticket.use(()->{synchronized(owner){try{
                assertNull(ticket.writer(file,StandardCharsets.UTF_8));Files.writeString(file,"native creation");
            }catch(IOException error){throw new UncheckedIOException(error);}}});
            assertEquals("native creation",Files.readString(file));
        }
    }
    @Test void handleBudgetAndShutdownAreBoundedAndNeverTruncateUnusedTickets()throws Exception{
        var owner=new Object();var file=file();var session=new WorldBossProfileCacheIo.Session(owner);session.register(file);
        for(int i=0;i<75;i++)session.prepare();assertTrue(session.prepare().ready().isCompletedExceptionally());
        session.close();assertNull(session.prepare());assertEquals("original",Files.readString(file));
        assertTrue(session.diagnostics().contains("released=75, pending=0"));Files.move(file,root.resolve("closed.json"));
    }
    @Test void writerRequiresTheNativeMonitorAndExactPathAndIsClaimedOnce()throws Exception{
        var owner=new Object();var file=file();var other=Files.writeString(root.resolve("other.json"),"untouched");
        try(var session=new WorldBossProfileCacheIo.Session(owner)){
            session.register(file);var ticket=session.prepare();ticket.ready().get(2,TimeUnit.SECONDS);
            ticket.use(()->{try{
                assertNull(ticket.writer(file,StandardCharsets.UTF_8));
                synchronized(owner){assertNull(ticket.writer(other,StandardCharsets.UTF_8));try(var writer=ticket.writer(file,StandardCharsets.UTF_8)){writer.write("[]");}assertNull(ticket.writer(file,StandardCharsets.UTF_8));}
            }catch(IOException error){throw new UncheckedIOException(error);}});
        }
        assertEquals("[]",Files.readString(file));assertEquals("untouched",Files.readString(other));
    }
}
