package com.cobbleraids.worldboss;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Paces native advancement serialization on Main; never serializes player state on a worker. */
public final class WorldBossAdvancementSaves {
    public interface NativeTarget { Path cobbleRaids$advancementSavePath(); }
    private static volatile Queue queue;
    private static volatile Path directory;
    private static boolean stopping,reported;
    private WorldBossAdvancementSaves(){}
    static void started(Path world){directory=world.toAbsolutePath().normalize().resolve("advancements");queue=new Queue();stopping=false;reported=false;WorldBossAdvancementIo.started(directory);}
    static void stopped(){queue=null;directory=null;WorldBossAdvancementIo.stopped();}
    static boolean failed(){var current=queue;return current!=null&&current.failure!=null||WorldBossAdvancementIo.failure()!=null;}
    static String diagnostics(){var current=queue;return current==null?"idle":current.diagnostics()+"; advancement IO: "+WorldBossAdvancementIo.diagnostics();}
    static Throwable tick(){
        var current=queue;if(current==null)return null;
        current.tick();WorldBossAdvancementIo.poll();
        var failure=current.failure!=null?current.failure:WorldBossAdvancementIo.failure();
        if(failure==null||reported)return null;reported=true;return failure;
    }
    static void drainAll(){var current=queue;try{if(current!=null)current.drainAll();}finally{WorldBossAdvancementIo.drainAll();}}
    static void close(){stopping=true;try{drainAll();}finally{WorldBossAdvancementIo.close();}}
    public static boolean defer(Path file,Runnable nativeSave){
        var current=queue;var root=directory;
        if(current==null||root==null||stopping||!WorldBossAutosaveScope.deferred()||!current.isOwner()||current.running>0||failed())return false;
        file=file.toAbsolutePath().normalize();
        if(!root.equals(file.getParent())||!file.getFileName().toString().endsWith(".json"))return false;
        return current.submit(file,nativeSave);
    }
    public static void direct(Path file,Runnable nativeSave){
        var current=queue;
        if(current==null){nativeSave.run();return;}
        current.direct(file.toAbsolutePath().normalize(),()->{WorldBossAdvancementIo.beforeDirect(file);nativeSave.run();});
    }
    public static void beforeLoad(Path file){var current=queue;if(current!=null)current.flush(file.toAbsolutePath().normalize());WorldBossAdvancementIo.await(file);}

    /** Main-owned jobs; futures expose only completion to a native off-thread file barrier. */
    static final class Queue {
        private final Thread owner=Thread.currentThread();
        private final LinkedHashMap<Path,Job> pending=new LinkedHashMap<>();
        private final ConcurrentHashMap<Path,CompletableFuture<Void>> accepted=new ConcurrentHashMap<>();
        private volatile Throwable failure;
        private long scheduled,processed,superseded;
        private int running;
        private static final class Job {
            Runnable save;final CompletableFuture<Void> done=new CompletableFuture<>();
            Job(Runnable save){this.save=save;}
        }
        boolean isOwner(){return Thread.currentThread()==owner;}
        private void requireOwner(){if(!isOwner())throw new IllegalStateException("Advancement serialization requires Main");}
        boolean submit(Path file,Runnable save){
            requireOwner();Objects.requireNonNull(save);
            if(failure!=null)return false;
            var existing=pending.get(file);
            if(existing!=null){existing.save=save;return true;}
            if(pending.size()>=128)return false;
            var job=new Job(save);pending.put(file,job);accepted.put(file,job.done);scheduled++;return true;
        }
        void tick(){
            requireOwner();if(pending.isEmpty())return;
            var first=pending.firstEntry();pending.remove(first.getKey());
            try{WorldBossAdvancementIo.automatic(first.getKey(),()->run(first.getKey(),first.getValue(),first.getValue().save,false));}catch(RuntimeException ignored){/* surfaced once by tick() */}
        }
        void direct(Path file,Runnable save){
            if(!isOwner()){await(file);save.run();return;}
            var queued=pending.remove(file);
            run(file,queued,save,queued!=null);
        }
        void flush(Path file){
            if(!isOwner()){await(file);return;}
            var job=pending.remove(file);if(job!=null)run(file,job,job.save,false);
        }
        void drainAll(){
            if(!isOwner()){
                for(var file:new ArrayList<>(accepted.keySet()))await(file);
                return;
            }
            RuntimeException error=null;
            while(!pending.isEmpty())try{flush(pending.firstEntry().getKey());}catch(RuntimeException failed){if(error==null)error=failed;}
            if(error!=null)throw error;
        }
        private void run(Path file,Job job,Runnable save,boolean replacement){
            running++;
            try{save.run();if(job!=null){if(replacement)superseded++;else processed++;job.done.complete(null);}}
            catch(RuntimeException|Error failed){if(job!=null){if(failure==null)failure=failed;job.done.completeExceptionally(failed);}throw failed;}
            finally{running--;if(job!=null)accepted.remove(file,job.done);}
        }
        private void await(Path file){
            var future=accepted.get(file);if(future==null)return;
            boolean interrupted=false;
            try{while(true)try{future.get();return;}catch(InterruptedException stop){interrupted=true;}
                catch(ExecutionException failed){throw new IllegalStateException("Queued native advancement save failed",failed.getCause());}}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
        String diagnostics(){requireOwner();return "pending="+pending.size()+", scheduled="+scheduled+", processed="+processed+", superseded="+superseded+", failed="+(failure!=null);}
    }
}
