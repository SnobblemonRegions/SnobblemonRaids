package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;
import net.minecraft.Util;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;

/** Native world NBT only: serialization and dirty-state handling remain on Main. */
public final class WorldBossWorldSaves {
    private static volatile Thread owner;
    private static volatile Path world;
    private static volatile Writer writer;
    private static boolean stopping,reported,warned;
    private static final List<Pending> pending=new ArrayList<>();
    private static final ThreadLocal<Attempt> ATTEMPT=new ThreadLocal<>();
    private static final ThreadLocal<Metadata> METADATA=new ThreadLocal<>();
    private record Pending(CompletableFuture<Void> future,Attempt attempt){}
    private WorldBossWorldSaves(){}
    static void started(Path root){
        if(writer!=null)throw new IllegalStateException("Previous native world writer has not stopped");
        owner=Thread.currentThread();world=normalized(root);stopping=false;reported=false;warned=false;
    }
    static void stopped(){writer=null;owner=null;world=null;pending.clear();ATTEMPT.remove();METADATA.remove();}
    static boolean failed(){var queue=writer;return queue!=null&&queue.failure.get()!=null;}
    static String diagnostics(){return writer==null?"idle":writer.diagnostics();}
    static Throwable poll(){
        pollOwners();var queue=writer;
        if(queue==null)return null;queue.poll();
        var error=queue.failure.get();if(error==null||reported)return null;reported=true;return error;
    }
    private static void pollOwners(){
        if(Thread.currentThread()!=owner)return;
        var finished=new ArrayList<Pending>();
        var iterator=pending.iterator();
        while(iterator.hasNext()){
            var item=iterator.next();
            if(item.attempt().finished&&item.future().isDone()){finished.add(item);iterator.remove();}
        }
        for(var item:finished)if(item.future().isCompletedExceptionally()&&!item.attempt().data.isDirty())item.attempt().data.setDirty();
    }
    static void awaitAll() throws IOException{try{if(writer!=null)writer.drainAll();}finally{pollOwners();}}
    static void close() throws IOException{stopping=true;try{if(writer!=null)writer.close();}finally{pollOwners();}}
    private static Path normalized(Path path){return path.toAbsolutePath().normalize();}
    private static boolean inside(Path path){return world!=null&&normalized(path).startsWith(world);}
    private static boolean allowed(){return Thread.currentThread()==owner&&WorldBossAutosaveScope.deferred()&&!stopping&&!failed();}
    private static void await(Path file){
        var queue=writer;if(queue==null)return;
        try{queue.drain(normalized(file));}
        catch(IOException failure){RaidLog.error("Earlier native world NBT I/O finished with a failure before save/load of {}",file,failure);}
        pollOwners();
    }
    private static CompletableFuture<Void> submit(Target target,byte[] snapshot){
        if(!allowed())return null;
        if(writer==null)writer=new Writer(WorldBossWorldSaves::writeAtomic);
        return writer.submit(target,snapshot);
    }
    public static Attempt beforeSave(SavedData data,Path file){
        file=normalized(file);
        if(!allowed())await(file);
        if(Thread.currentThread()!=owner||!inside(file)||!file.getFileName().toString().endsWith(".dat"))return null;
        return new Attempt(data,file);
    }
    public static final class Attempt implements AutoCloseable {
        private final Attempt previous=ATTEMPT.get();
        private final SavedData data;private final Path file;
        private boolean finished,failedDirect;
        private CompletableFuture<Void> accepted;
        private Attempt(SavedData data,Path file){this.data=data;this.file=file;ATTEMPT.set(this);}
        @Override public void close(){
            finished=true;if((failedDirect||accepted!=null&&accepted.isCompletedExceptionally())&&!data.isDirty())data.setDirty();
            if(previous==null)ATTEMPT.remove();else ATTEMPT.set(previous);
        }
    }
    public static boolean defer(SavedData data,CompoundTag tag,Path file){
        var attempt=ATTEMPT.get();
        if(!allowed()||attempt==null||attempt.data!=data||!attempt.file.equals(normalized(file)))return false;
        byte[] snapshot;
        try{snapshot=serialize(tag);}catch(IOException|RuntimeException unsupported){warn(unsupported);return false;}
        var future=submit(new Target(attempt.file,null),snapshot);
        if(future==null)return false;
        attempt.accepted=future;pending.add(new Pending(future,attempt));return true;
    }
    public static void beforeDirectWrite(Path file){await(file);}
    public static void directWriteFailed(){var attempt=ATTEMPT.get();if(attempt!=null)attempt.failedDirect=true;}
    public static void beforeLoad(Path file){await(file);}
    public static void beforeBackup(Path directory) throws IOException{
        if(world==null||!world.equals(normalized(directory)))return;
        IOException failed=null;
        try{WorldBossAdvancementSaves.drainAll();}catch(RuntimeException error){failed=new IOException("Native advancement saves did not drain",error);}
        try{awaitAll();}catch(IOException error){failed=error;}
        try{WorldBossPlayerAutosaves.awaitBackup();}catch(IOException error){if(failed==null)failed=error;}
        if(Thread.currentThread()==owner){
            var saved=WorldBossService.collectionState();
            try{if(saved!=null)saved.awaitNativeCheckpoint();}catch(IOException error){if(failed==null)failed=error;}
        }
        if(failed!=null)throw failed;
    }
    public static Metadata beforeMetadata(Path directory){
        directory=normalized(directory);Path target=directory.resolve("level.dat");
        if(!allowed())await(target);
        if(Thread.currentThread()!=owner||world==null||!world.equals(directory))return null;
        return new Metadata(new Target(target,directory.resolve("level.dat_old")),allowed());
    }
    public static final class Metadata implements AutoCloseable{
        private final Metadata previous=METADATA.get();
        private final Target target;private boolean enabled,finished;
        private Path token,actual;private byte[] snapshot;
        private Metadata(Target target,boolean enabled){this.target=target;this.enabled=enabled;METADATA.set(this);}
        private boolean owns(Path path){return token!=null&&token.equals(normalized(path));}
        private Path actual() throws IOException{if(actual==null)actual=Files.createTempFile(target.file().getParent(),"level",".dat");return actual;}
        @Override public void close(){
            if(snapshot!=null&&!finished)RaidLog.warn("Native level metadata ended before replacement; no detached write was acknowledged");
            if(previous==null)METADATA.remove();else METADATA.set(previous);
        }
    }
    public static Path metadataTemporary(Path directory,String prefix,String suffix,FileAttribute<?>[] attributes) throws IOException{
        var capture=METADATA.get();
        if(capture!=null&&capture.enabled&&attributes.length==0&&normalized(directory).equals(capture.target.file().getParent())
                &&prefix.equals("level")&&".dat".equals(suffix)){
            capture.token=normalized(directory).resolve("level-arena-"+UUID.randomUUID()+".dat");return capture.token;
        }
        return Files.createTempFile(directory,prefix,suffix,attributes);
    }
    public static void metadataWrite(CompoundTag tag,Path temporary) throws IOException{
        var capture=METADATA.get();
        if(capture!=null&&capture.enabled&&capture.owns(temporary)){
            try{capture.snapshot=serialize(tag);return;}
            catch(IOException|RuntimeException unsupported){capture.enabled=false;warn(unsupported);}
        }
        NbtIo.writeCompressed(tag,capture!=null&&capture.owns(temporary)?capture.actual():temporary);
    }
    public static void metadataReplace(Path target,Path temporary,Path backup) throws IOException{
        var capture=METADATA.get();boolean owned=capture!=null&&capture.owns(temporary);
        boolean names=capture!=null&&capture.target.file().equals(normalized(target))&&capture.target.backup().equals(normalized(backup));
        if(owned&&capture.snapshot!=null){
            if(names&&capture.enabled&&submit(capture.target,capture.snapshot)!=null){capture.finished=true;return;}
            if(names)await(target);else awaitAll();
            try(var output=new GZIPOutputStream(Files.newOutputStream(capture.actual()))){output.write(capture.snapshot);}
        }else if(names)await(target);else if(writer!=null)awaitAll();
        Util.safeReplaceFile(target,owned?capture.actual():temporary,backup);
        if(capture!=null)capture.finished=true;
    }
    private static void warn(Exception failure){if(!warned){warned=true;RaidLog.warn("Native world NBT could not use detached capture; retaining native I/O: {}",failure.getClass().getSimpleName());}}
    private static byte[] serialize(CompoundTag tag) throws IOException{
        var bytes=new ByteArrayOutputStream();
        var output=new OutputStream(){
            public void write(int value) throws IOException{if(bytes.size()>=Writer.MAX_SNAPSHOT)throw new IOException("World NBT exceeds capture limit");bytes.write(value);}
            public void write(byte[] data,int offset,int length) throws IOException{if(length>Writer.MAX_SNAPSHOT-bytes.size())throw new IOException("World NBT exceeds capture limit");bytes.write(data,offset,length);}
        };
        NbtIo.write(tag,new DataOutputStream(output));return bytes.toByteArray();
    }
    record Target(Path file,Path backup){
        Target{file=normalized(file);if(backup!=null){backup=normalized(backup);if(file.equals(backup)||!file.getParent().equals(backup.getParent()))throw new IllegalArgumentException("Invalid native NBT backup path");}}
    }
    @FunctionalInterface interface Sink{void write(Target target,byte[] snapshot) throws IOException;}
    static final class Writer implements AutoCloseable {
        static final int MAX_SNAPSHOT=16*1024*1024;
        private final Thread caller=Thread.currentThread();
        private final ExecutorService executor;
        private final ConcurrentHashMap<Path,CompletableFuture<Void>> latest=new ConcurrentHashMap<>();
        private final AtomicInteger count=new AtomicInteger();
        private final AtomicLong bytes=new AtomicLong(),submitted=new AtomicLong(),completed=new AtomicLong();
        private final AtomicReference<Throwable> failure=new AtomicReference<>();
        private final Sink sink;private boolean closed;
        Writer(){this(WorldBossWorldSaves::writeAtomic);}
        Writer(Sink sink){this("World Boss native world NBT",sink);}
        Writer(String threadName,Sink sink){
            this.sink=Objects.requireNonNull(sink);
            executor=Executors.newSingleThreadExecutor(task->Thread.ofPlatform().daemon(true)
                    .inheritInheritableThreadLocals(false).name(threadName).unstarted(task));
        }
        Throwable failure(){return failure.get();}
        CompletableFuture<Void> submit(Target target,byte[] input){
            requireOwner();
            if(closed||failure.get()!=null||input.length==0||input.length>MAX_SNAPSHOT||count.get()>=128||bytes.get()+input.length>64L*1024*1024)return null;
            byte[] snapshot=input.clone();count.incrementAndGet();bytes.addAndGet(snapshot.length);
            try{
                var future=CompletableFuture.runAsync(()->{try{sink.write(target,snapshot);}catch(IOException error){throw new CompletionException(error);}},executor)
                    .whenComplete((ignored,error)->{
                        if(error==null)completed.incrementAndGet();else failure.compareAndSet(null,error instanceof CompletionException&&error.getCause()!=null?error.getCause():error);
                        bytes.addAndGet(-snapshot.length);count.decrementAndGet();
                    });
                latest.put(target.file(),future);submitted.incrementAndGet();return future;
            }catch(RejectedExecutionException full){bytes.addAndGet(-snapshot.length);count.decrementAndGet();return null;}
        }
        void poll(){requireOwner();latest.forEach((key,future)->{if(future.isDone())latest.remove(key,future);});}
        void drain(Path file) throws IOException{var future=latest.get(normalized(file));if(future!=null)awaitFuture(future);}
        void drainAll() throws IOException{
            IOException failed=null;
            for(var future:new ArrayList<>(latest.values()))try{awaitFuture(future);}catch(IOException error){if(failed==null)failed=error;}
            if(failed!=null)throw failed;if(failure.get()!=null)throw new IOException("Native world NBT write failed",failure.get());
        }
        String diagnostics(){return "pending="+count.get()+", bytes="+bytes.get()+", submitted="+submitted.get()+", completed="+completed.get()+", failed="+(failure.get()!=null);}
        private static void awaitFuture(CompletableFuture<Void> future) throws IOException{
            boolean interrupted=false;
            try{while(true)try{future.get();return;}catch(InterruptedException stop){interrupted=true;}catch(ExecutionException failed){throw new IOException("Native world NBT did not commit",failed.getCause());}}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
        @Override public void close() throws IOException{
            requireOwner();closed=true;IOException failed=null;
            try{drainAll();}catch(IOException error){failed=error;}
            finally{
                executor.shutdown();boolean interrupted=false;
                try{while(!executor.isTerminated())try{executor.awaitTermination(1,TimeUnit.SECONDS);}catch(InterruptedException stop){interrupted=true;}}
                finally{if(interrupted)Thread.currentThread().interrupt();}
            }
            if(failed!=null)throw failed;
        }
        private void requireOwner(){if(Thread.currentThread()!=caller)throw new IllegalStateException("Native world NBT capture requires its server thread");}
    }
    private static void writeAtomic(Target target,byte[] snapshot) throws IOException{
        if(snapshot.length<3||snapshot[0]!=10)throw new IOException("Native world snapshot is not a compound root");
        Path temporary=Files.createTempFile(target.file().getParent(),"wb-nbt-",".tmp");
        try{
            try(var output=new GZIPOutputStream(Files.newOutputStream(temporary))){output.write(snapshot);}
            if(target.backup()==null)Files.move(temporary,target.file(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            else{
                Util.safeReplaceFile(target.file(),temporary,target.backup());
                try(var input=new GZIPInputStream(Files.newInputStream(target.file()))){if(!Arrays.equals(snapshot,input.readNBytes(snapshot.length+1)))throw new IOException("Native metadata replacement was not verified");}
            }
        }finally{Files.deleteIfExists(temporary);}
    }
}
