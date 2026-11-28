package com.cobbleraids.worldboss;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.player.adapter.FileBasedPlayerDataStoreBackend;
import com.cobblemon.mod.common.api.storage.player.factory.CachedPlayerDataStoreFactory;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.OneToOneFileStoreAdapter;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobbleraids.mixin.worldboss.WorldBossStoreFactoriesAccessor;
import com.cobbleraids.mixin.worldboss.WorldBossStoreAdapterAccessor;
import com.cobbleraids.mixin.worldboss.WorldBossStoreDirectoryAccessor;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Prepare only native directory parents. Native loading, defaults, backups and caches remain on Main. */
final class WorldBossLoginFiles {
    private static Worker worker;
    private WorldBossLoginFiles(){}
    static void started(){if(worker!=null)throw new IllegalStateException("Previous login filesystem worker is still owned");worker=new Worker(Files::createDirectories);}
    static CompletableFuture<Void> prepare(MinecraftServer server,UUID player){
        var world=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var game=FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        var directories=new LinkedHashSet<Path>();
        for(var factory:Cobblemon.INSTANCE.getPlayerDataManager().getFactories().values()){
            if(factory instanceof CachedPlayerDataStoreFactory<?> cached&&cached.getBackend() instanceof FileBasedPlayerDataStoreBackend<?> files){
                var directory=files.filePath(player).toPath().toAbsolutePath().normalize().getParent();
                if(directory!=null&&(directory.startsWith(world)||directory.startsWith(game)))directories.add(directory);
            }
        }
        for(var factory:((WorldBossStoreFactoriesAccessor)Cobblemon.INSTANCE.getStorage()).cobbleRaids$factories()){
            if(factory instanceof FileBackedPokemonStoreFactory<?>
                    &&((WorldBossStoreAdapterAccessor)factory).cobbleRaids$adapter() instanceof OneToOneFileStoreAdapter<?> adapter){
                var access=(WorldBossStoreDirectoryAccessor)adapter;
                var plan=new StoreDirectories(access.cobbleRaids$rootFolder(),access.cobbleRaids$nestedFolders(),access.cobbleRaids$folderPerClass());
                for(var type:List.of(PlayerPartyStore.class,PCStore.class)){
                    var directory=plan.directory(type.getSimpleName(),player);
                    if(directory.startsWith(world)||directory.startsWith(game))directories.add(directory);
                }
            }
        }
        return worker.submit(List.copyOf(directories));
    }
    /** Native OneToOneFileStoreAdapter.getFile parent calculation; never calls its mkdirs side effect. */
    record StoreDirectories(String root,boolean nested,boolean perClass){
        Path directory(String simpleClassName,UUID player){
            String prefix=(root.endsWith("/")?root:root+"/")
                    +(perClass?simpleClassName.toLowerCase(Locale.ROOT)+"/":"")
                    +(nested?player.toString().substring(0,2)+"/":"");
            return new java.io.File(prefix).toPath().toAbsolutePath().normalize();
        }
    }
    static void close(){if(worker!=null)worker.close();}
    static void stopped(){worker=null;}
    static String diagnostics(){return worker==null?"idle":"prepared="+worker.prepared.get();}

    @FunctionalInterface interface Sink {void create(Path directory)throws IOException;}
    static final class Worker implements AutoCloseable {
        private final Sink sink;
        private final Semaphore slots=new Semaphore(75);
        private final ExecutorService executor=Executors.newSingleThreadExecutor(task->Thread.ofPlatform().daemon(true)
                .inheritInheritableThreadLocals(false).name("World Boss login directories").unstarted(task));
        private final AtomicLong prepared=new AtomicLong();
        private volatile boolean closed;
        Worker(Sink sink){this.sink=sink;}
        CompletableFuture<Void> submit(List<Path> paths){
            var captured=List.copyOf(paths);
            if(closed||paths.size()>64||!slots.tryAcquire())return CompletableFuture.failedFuture(new RejectedExecutionException("Login directory queue unavailable"));
            try{
                return CompletableFuture.runAsync(()->{
                    try{for(var path:captured){sink.create(path);prepared.incrementAndGet();}}
                    catch(IOException failure){throw new CompletionException(failure);}
                },executor).whenComplete((ignored,failure)->slots.release());
            }catch(RejectedExecutionException stopped){slots.release();return CompletableFuture.failedFuture(stopped);}
        }
        @Override public void close(){
            closed=true;executor.shutdown();boolean interrupted=false;
            try{while(!executor.isTerminated())try{executor.awaitTermination(1,TimeUnit.SECONDS);}catch(InterruptedException stop){interrupted=true;}}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
}
