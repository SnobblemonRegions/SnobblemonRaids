package com.cobbleraids.worldboss;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.Util;

/** Detached vanilla player NBT only. Actor serialization and publication decisions stay on the server thread. */
final class WorldBossPlayerSaves implements AutoCloseable {
    static final int MAX_SNAPSHOT=16*1024*1024;
    private static final long MAX_BYTES=64L*1024*1024;
    record Key(Path directory,UUID player) {
        Key { directory=directory.toAbsolutePath().normalize();java.util.Objects.requireNonNull(player); }
    }
    @FunctionalInterface interface Sink { void write(Key key,byte[] snapshot) throws IOException; }
    private final Thread owner=Thread.currentThread();
    private final ExecutorService executor=Executors.newSingleThreadExecutor(task->Thread.ofPlatform()
            .daemon(true).inheritInheritableThreadLocals(false).name("World Boss player autosave").unstarted(task));
    private final ConcurrentHashMap<Key,CompletableFuture<Void>> latest=new ConcurrentHashMap<>();
    private final AtomicInteger pending=new AtomicInteger();
    private final AtomicLong bytes=new AtomicLong(),submitted=new AtomicLong(),completed=new AtomicLong();
    private final AtomicReference<Throwable> failure=new AtomicReference<>();
    private final Sink sink;
    private volatile boolean closed;
    WorldBossPlayerSaves() { this(WorldBossPlayerSaves::writeNative); }
    WorldBossPlayerSaves(Sink sink) { this.sink=java.util.Objects.requireNonNull(sink); }
    boolean submit(Key key,byte[] data) {
        requireOwner();
        if(closed || failure.get()!=null || data.length==0 || data.length>MAX_SNAPSHOT
                || pending.get()>=128 || bytes.get()+data.length>MAX_BYTES) return false;
        byte[] snapshot=data.clone();
        pending.incrementAndGet();bytes.addAndGet(snapshot.length);
        try {
            var future=CompletableFuture.runAsync(()->{
                try { sink.write(key,snapshot); }
                catch(IOException error) { throw new CompletionException(error); }
            },executor).whenComplete((ignored,error)->{
                if(error!=null) failure.compareAndSet(null,error instanceof CompletionException&&error.getCause()!=null?error.getCause():error);
                else completed.incrementAndGet();
                bytes.addAndGet(-snapshot.length);pending.decrementAndGet();
            });
            latest.put(key,future);submitted.incrementAndGet();return true;
        } catch(RejectedExecutionException rejected) {
            bytes.addAndGet(-snapshot.length);pending.decrementAndGet();return false;
        }
    }
    int pending() { return pending.get(); }
    Throwable failure() { return failure.get(); }
    void poll() {
        requireOwner();
        latest.forEach((key,future)->{if(future.isDone())latest.remove(key,future);});
    }
    void drain(Key key) throws IOException {
        var future=latest.get(key);if(future!=null)await(future);
    }
    void drainAll() throws IOException {
        IOException first=null;
        for(var future:new ArrayList<>(latest.values())) {
            try{await(future);}catch(IOException error){if(first==null)first=error;}
        }
        if(first!=null)throw first;
        if(failure.get()!=null)throw new IOException("A queued player autosave failed",failure.get());
    }
    String diagnostics() {
        return "pending="+pending.get()+", bytes="+bytes.get()+", submitted="+submitted.get()+", completed="+completed.get()+", failed="+(failure.get()!=null);
    }
    private static void await(CompletableFuture<Void> future) throws IOException {
        boolean interrupted=false;
        try {
            while(true) {
                try {future.get();return;}
                catch(InterruptedException stop){interrupted=true;}
                catch(ExecutionException error){throw new IOException("Queued player autosave did not complete",error.getCause());}
            }
        } finally {if(interrupted)Thread.currentThread().interrupt();}
    }
    @Override public void close() throws IOException {
        requireOwner();closed=true;
        IOException failed=null;
        try{drainAll();}catch(IOException error){failed=error;}
        finally {
            executor.shutdown();boolean interrupted=false;
            try {
                while(!executor.isTerminated()) {
                    try{executor.awaitTermination(1,TimeUnit.SECONDS);}
                    catch(InterruptedException stop){interrupted=true;}
                }
            } finally {if(interrupted)Thread.currentThread().interrupt();}
        }
        if(failed!=null)throw failed;
    }
    private void requireOwner() {
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Player autosave submission requires its owning server thread");
    }
    private static void writeNative(Key key,byte[] snapshot) throws IOException {
        if(snapshot.length<3 || snapshot[0]!=10)throw new IOException("Player autosave is not a compound NBT root");
        Path temporary=Files.createTempFile(key.directory(),key.player()+"-",".dat");
        try(var output=new GZIPOutputStream(Files.newOutputStream(temporary))) {output.write(snapshot);}
        Path target=key.directory().resolve(key.player()+".dat");
        Util.safeReplaceFile(target,temporary,key.directory().resolve(key.player()+".dat_old"));
        // Vanilla replacement can log and return on failure: never acknowledge an unchanged/partial target.
        try(var input=new GZIPInputStream(Files.newInputStream(target))) {
            if(!Arrays.equals(snapshot,input.readNBytes(snapshot.length+1)))throw new IOException("Player autosave replacement was not verified");
        }
    }
}
