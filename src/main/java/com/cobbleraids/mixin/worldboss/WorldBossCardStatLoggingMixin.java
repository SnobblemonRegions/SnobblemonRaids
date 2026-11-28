package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossService;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Optional diagnostic-level correction only; the native bonus calculation executes unchanged. */
@Pseudo
@Mixin(targets="com.howlite.cobblemoncards.util.CardStatUtil",remap=false)
public abstract class WorldBossCardStatLoggingMixin {
    @WrapOperation(method="getPlayerDropBonus",at=@At(value="INVOKE",target="Lorg/slf4j/Logger;info(Ljava/lang/String;)V"),require=0,expect=5,allow=5,remap=false)
    private static void cobbleRaids$statDetails(Logger logger,String message,Operation<Void> original,ServerPlayer player) {
        try{if(WorldBossService.quietCardStatDiagnostics(player)){logger.debug(message);return;}}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:cards-stat-diagnostics",failure);}
        original.call(logger,message);
    }
}
