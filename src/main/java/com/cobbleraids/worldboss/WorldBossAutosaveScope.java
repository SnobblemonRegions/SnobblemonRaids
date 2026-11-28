package com.cobbleraids.worldboss;

/** Main-thread native-save context, separate from the narrower player serialization batch. */
public final class WorldBossAutosaveScope implements AutoCloseable {
    private static final ThreadLocal<WorldBossAutosaveScope> CURRENT=new ThreadLocal<>();
    private final WorldBossAutosaveScope previous=CURRENT.get();
    private final boolean deferred;
    WorldBossAutosaveScope(boolean deferred){this.deferred=deferred;CURRENT.set(this);}
    public static WorldBossAutosaveScope begin(net.minecraft.server.MinecraftServer server,boolean silent,boolean flush,boolean forced) throws java.io.IOException {
        boolean enabled=WorldBossPlayerAutosaves.eligible(server,silent,flush,forced);
        var saved=WorldBossService.collectionState();
        if(!enabled){
            WorldBossAdvancementSaves.drainAll();
            java.io.IOException failure=null;
            try{WorldBossWorldSaves.awaitAll();}catch(java.io.IOException error){failure=error;}
            try{if(saved!=null)saved.awaitNativeCheckpoint();}catch(java.io.IOException error){if(failure==null)failure=error;}
            if(failure!=null)throw failure;
        }
        return new WorldBossAutosaveScope(enabled);
    }
    public static WorldBossAutosaveScope criticalFallback(){return new WorldBossAutosaveScope(false);}
    static boolean deferred(){var scope=CURRENT.get();return scope!=null&&scope.deferred;}
    @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
