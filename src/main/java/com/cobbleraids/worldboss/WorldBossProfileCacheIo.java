package com.cobbleraids.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;

/** Prepare/pin native cache handles before admission; native serialization and writes remain synchronous. */
public final class WorldBossProfileCacheIo {
    private static volatile Session session;
    private static final ThreadLocal<Ticket> CURRENT=new ThreadLocal<>();
    private WorldBossProfileCacheIo(){}
    private static boolean windows(){return System.getProperty("os.name","").startsWith("Windows");}
    static void started(MinecraftServer server){
        stopping();var cache=server.getProfileCache();
        // NOSHARE_DELETE pins the pathname until worker release. Other platforms keep native I/O.
        if(windows()&&server.isDedicatedServer()&&cache!=null&&cache.getClass()==GameProfileCache.class)session=new Session(cache);
    }
    static Ticket prepare(){var current=session;return current==null?null:current.prepare();}
    public static BufferedWriter writer(Object cache,File file,Charset charset)throws IOException{
        var current=session;if(current==null||current.owner!=cache)return null;
        current.register(file.toPath());var ticket=CURRENT.get();
        if(ticket!=null&&ticket.session==current)return ticket.writer(file.toPath(),charset);
        current.fallbacks.incrementAndGet();return null;
    }
    static void stopping(){var current=session;session=null;if(current!=null)current.close();}
    public static String diagnostics(){var current=session;return current==null?"native":current.diagnostics();}
    @FunctionalInterface interface Opener{FileChannel open(Path file)throws IOException;}
    @FunctionalInterface interface Closer{void close(FileChannel channel)throws IOException;}

    static final class Session implements AutoCloseable {
        final Object owner;private final Thread main=Thread.currentThread();
        private final ExecutorService worker=Executors.newSingleThreadExecutor(task->Thread.ofPlatform().daemon(true)
                .inheritInheritableThreadLocals(false).name("World Boss profile cache handles").unstarted(task));
        private final Set<Ticket> tickets=ConcurrentHashMap.newKeySet();
        private final AtomicLong prepared=new AtomicLong(),opened=new AtomicLong(),used=new AtomicLong(),released=new AtomicLong(),fallbacks=new AtomicLong(),errors=new AtomicLong();
        private final Opener opener;private final Closer closer;
        private volatile Path path;private volatile boolean closed;
        Session(Object owner){this(owner,file->windows()?FileChannel.open(file,StandardOpenOption.WRITE,com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)
                :FileChannel.open(file,StandardOpenOption.WRITE),FileChannel::close);}
        Session(Object owner,Opener opener,Closer closer){this.owner=owner;this.opener=opener;this.closer=closer;}
        private void requireMain(){if(Thread.currentThread()!=main)throw new IllegalStateException("Cache handle admission requires Main");}
        void register(Path file){if(path==null)path=file.toAbsolutePath().normalize();} // Original startup saves discover the actual target.
        Ticket prepare(){
            requireMain();var file=path;if(file==null||closed)return null;
            if(tickets.size()>=75){
                var refused=new Ticket(this,file);refused.finished.set(true);
                refused.ready.completeExceptionally(new IllegalStateException("Profile-cache handle admission is full"));return refused;
            }
            var ticket=new Ticket(this,file);tickets.add(ticket);prepared.incrementAndGet();worker.execute(ticket::open);return ticket;
        }
        String diagnostics(){return "prepared="+prepared.get()+", opened="+opened.get()+", used="+used.get()+", released="+released.get()
                +", pending="+tickets.size()+", fallbacks="+fallbacks.get()+", errors="+errors.get()+", pinned=true";}
        @Override public void close(){
            requireMain();synchronized(this){if(closed)return;closed=true;for(var ticket:tickets)ticket.close();worker.shutdown();}
            boolean interrupted=false;
            try{while(!worker.isTerminated())try{worker.awaitTermination(1,TimeUnit.SECONDS);}catch(InterruptedException stop){interrupted=true;}}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    static final class Ticket implements AutoCloseable {
        final Session session;private final Path path;
        private final CompletableFuture<Void> ready=new CompletableFuture<>();
        private final AtomicBoolean cancelled=new AtomicBoolean(),finished=new AtomicBoolean(),openDone=new AtomicBoolean();
        private volatile FileChannel channel;private boolean claimed;
        Ticket(Session session,Path path){this.session=session;this.path=path;}
        CompletableFuture<Void> ready(){return ready;}
        private void open(){
            try{
                if(!cancelled.get()){channel=session.opener.open(path);session.opened.incrementAndGet();}
                openDone.set(true);
                if(cancelled.get()||session.closed){ready.cancel(false);release();}else ready.complete(null);
            }catch(IOException|UnsupportedOperationException failure){
                session.errors.incrementAndGet();openDone.set(true);ready.complete(null);release(); // Retain native cache creation/error handling.
            }catch(Throwable failure){session.errors.incrementAndGet();openDone.set(true);ready.completeExceptionally(failure);release();}
        }
        void use(Runnable nativeLogin){
            session.requireMain();
            if(!ready.isDone()||ready.isCompletedExceptionally()||cancelled.get())throw new IllegalStateException("Cache handle is not ready for native admission");
            var previous=CURRENT.get();CURRENT.set(this);
            try{nativeLogin.run();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);close();}
        }
        BufferedWriter writer(Path requested,Charset charset)throws IOException{
            session.requireMain();
            if(!Thread.holdsLock(session.owner)||claimed||cancelled.get()||channel==null||!path.equals(requested.toAbsolutePath().normalize())){
                session.fallbacks.incrementAndGet();return null;
            }
            claimed=true;session.used.incrementAndGet();
            try{channel.position(0);channel.truncate(0);}catch(IOException failure){session.errors.incrementAndGet();throw failure;}
            var output=new FilterOutputStream(Channels.newOutputStream(channel)){
                @Override public void write(byte[] data,int offset,int length)throws IOException{try{out.write(data,offset,length);}catch(IOException failure){session.errors.incrementAndGet();throw failure;}}
                @Override public void write(int value)throws IOException{try{out.write(value);}catch(IOException failure){session.errors.incrementAndGet();throw failure;}}
                @Override public void close()throws IOException{flush();} // Native buffer flushes Main bytes; worker owns handle close.
            };
            return new BufferedWriter(new OutputStreamWriter(output,charset));
        }
        @Override public void close(){synchronized(session){if(!finished.get()&&cancelled.compareAndSet(false,true)&&openDone.get())session.worker.execute(this::release);}}
        private void release(){
            if(!finished.compareAndSet(false,true))return;
            try{var opened=channel;channel=null;if(opened!=null)session.closer.close(opened);}
            catch(IOException failure){session.errors.incrementAndGet();RaidFaultBarrier.report("worldboss:profile-cache-close",failure);}
            finally{session.released.incrementAndGet();session.tickets.remove(this);}
        }
    }
}
