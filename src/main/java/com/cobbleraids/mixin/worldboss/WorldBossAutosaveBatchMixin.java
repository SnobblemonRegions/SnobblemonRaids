package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossPlayerAutosaves;
import com.cobbleraids.worldboss.WorldBossAutosaveScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Limit deferred player I/O to the player batch of a native automatic save, not later chunk callbacks. */
@Mixin(MinecraftServer.class)
public abstract class WorldBossAutosaveBatchMixin {
    @WrapMethod(method="saveEverything")
    private boolean cobbleRaids$nativeSaveScope(boolean silent,boolean flush,boolean forced,Operation<Boolean> original) {
        WorldBossAutosaveScope scope=null;
        try{scope=WorldBossAutosaveScope.begin((MinecraftServer)(Object)this,silent,flush,forced);}
        catch(java.io.IOException|RuntimeException failure){scope=WorldBossAutosaveScope.criticalFallback();RaidFaultBarrier.report("worldboss:native-autosave-scope",failure);}
        try{return original.call(silent,flush,forced);}
        finally{if(scope!=null)try{scope.close();}catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:native-autosave-scope-close",failure);}}
    }

    @WrapOperation(method="saveEverything",at=@At(value="INVOKE",target="Lnet/minecraft/server/players/PlayerList;saveAll()V"))
    private void cobbleRaids$playerBatch(PlayerList players,Operation<Void> original,boolean silent,boolean flush,boolean forced) {
        WorldBossPlayerAutosaves.Automatic scope=null;
        try {scope=WorldBossPlayerAutosaves.automatic((MinecraftServer)(Object)this,silent,flush,forced);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:player-autosave-scope",failure);}
        try {original.call(players);}
        finally {if(scope!=null)try{scope.close();}catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:player-autosave-scope-close",failure);}}
    }
}
