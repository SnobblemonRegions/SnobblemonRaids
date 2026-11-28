package com.cobbleraids.worldboss;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossLoginFilesTest {
    @TempDir Path root;
    @Test void detachedParentsMatchNativeAdapterForEveryLayoutWithoutCreatingAnything()throws Exception{
        var player=java.util.UUID.fromString("ab123456-1234-5678-9012-123456789012");
        var previous=java.util.Locale.getDefault();
        try{
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            for(boolean nested:new boolean[]{false,true})for(boolean perClass:new boolean[]{false,true})
                for(boolean trailingSlash:new boolean[]{false,true}){
                    var base=root.resolve("native paths "+nested+perClass+trailingSlash);
                    String folder=base.toString()+(trailingSlash?"/":"");
                    var plan=new WorldBossLoginFiles.StoreDirectories(folder,nested,perClass);
                    var adapter=new com.cobblemon.mod.common.api.storage.adapter.flatfile.NBTStoreAdapter(folder,nested,perClass);
                    for(var type:List.of(com.cobblemon.mod.common.api.storage.party.PlayerPartyStore.class,com.cobblemon.mod.common.api.storage.pc.PCStore.class)){
                        var calculated=plan.directory(type.getSimpleName(),player);
                        if(!Files.exists(base))assertFalse(Files.exists(calculated));
                        var nativePath=adapter.getFile(type,player).toPath().toAbsolutePath().normalize().getParent();
                        assertEquals(nativePath,calculated);
                        try(var children=Files.list(calculated)){assertEquals(0,children.count());}
                    }
                }
        }finally{java.util.Locale.setDefault(previous);}
    }
    @Test void preparationCreatesParentsWithoutChangingExistingPlayerFiles()throws Exception{
        var existing=root.resolve("pokedex/existing.dat");Files.createDirectories(existing.getParent());Files.writeString(existing,"original player data");
        try(var worker=new WorldBossLoginFiles.Worker(Files::createDirectories)){
            var directory=root.resolve("tm_moves/ab");worker.submit(List.of(existing.getParent(),directory)).get(3,TimeUnit.SECONDS);
            assertTrue(Files.isDirectory(directory));assertEquals("original player data",Files.readString(existing));
            try(var siblings=Files.list(directory.getParent());var children=Files.list(directory)){
                assertEquals(1,siblings.count());assertEquals(0,children.count());
            }
        }
    }
    @Test void slowFilesystemDoesNotRunOnCallerAndBoundedShutdownDrainsAcceptedWork()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var worker=new WorldBossLoginFiles.Worker(path->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new java.io.IOException(e);}});
            var closer=Executors.newSingleThreadExecutor()){
            var first=worker.submit(List.of(root));
            try{
                assertTrue(entered.await(2,TimeUnit.SECONDS));assertFalse(first.isDone());
                for(int i=1;i<75;i++)assertFalse(worker.submit(List.of(root)).isCompletedExceptionally());
                assertTrue(worker.submit(List.of(root)).isCompletedExceptionally());
                var stopped=closer.submit(worker::close);assertThrows(TimeoutException.class,()->stopped.get(100,TimeUnit.MILLISECONDS));
                release.countDown();stopped.get(3,TimeUnit.SECONDS);first.get();
                assertTrue(worker.submit(List.of(root)).isCompletedExceptionally());
            }finally{release.countDown();}
        }
    }
    @Test void failedPreparationDoesNotBecomeAReadyLoginAndCanRetry()throws Exception{
        var fail=new AtomicBoolean(true);
        try(var worker=new WorldBossLoginFiles.Worker(path->{if(fail.getAndSet(false))throw new java.io.IOException("injected refusal");Files.createDirectories(path);})){
            assertThrows(ExecutionException.class,()->worker.submit(List.of(root.resolve("new"))).get(3,TimeUnit.SECONDS));
            assertFalse(Files.exists(root.resolve("new")));worker.submit(List.of(root.resolve("new"))).get(3,TimeUnit.SECONDS);
            assertTrue(Files.isDirectory(root.resolve("new")));
        }
    }
}
