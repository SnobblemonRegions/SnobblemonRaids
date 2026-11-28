package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossService;
import com.cobbleraids.worldboss.WorldBossCardLogs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Keep successful drop messages and economic code intact; only verbose chance diagnostics change level. */
@Pseudo
@Mixin(targets="com.howlite.cobblemoncards.event.ModEvents",remap=false)
public abstract class WorldBossCardDropLoggingMixin {
    @WrapOperation(method="handlePokemonDrop",at=@At(value="INVOKE",target="Lorg/slf4j/Logger;info(Ljava/lang/String;)V"),require=0,expect=1,allow=1,remap=false)
    private static void cobbleRaids$cardGenerated(Logger logger,String message,Operation<Void> original,ServerPlayer player,Pokemon pokemon) {
        try{if(WorldBossService.arenaCardLogScope(player)&&WorldBossCardLogs.defer(logger,message))return;}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:cards-generated-info",failure);}
        original.call(logger,message);
    }
    @WrapOperation(method="handlePokemonDrop",at=@At(value="INVOKE",target="Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V"),require=0,expect=1,allow=1,remap=false)
    private static void cobbleRaids$cardGeneration(Logger logger,String message,Object argument,Operation<Void> original,ServerPlayer player,Pokemon pokemon) {
        try{if(argument instanceof String&&WorldBossService.arenaCardLogScope(player)
                &&WorldBossCardLogs.defer(logger,org.slf4j.helpers.MessageFormatter.format(message,argument).getMessage()))return;}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:cards-generating-info",failure);}
        original.call(logger,message,argument);
    }
    @WrapOperation(method="handlePokemonDrop",at=@At(value="INVOKE",target="Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V"),require=0,expect=1,allow=1,remap=false)
    private static void cobbleRaids$chanceDetails(Logger logger,String message,Object[] arguments,Operation<Void> original,ServerPlayer player,Pokemon pokemon) {
        try{if(WorldBossService.quietCardStatDiagnostics(player)){logger.debug(message,arguments);return;}}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:cards-chance-diagnostics",failure);}
        original.call(logger,message,arguments);
    }
}
