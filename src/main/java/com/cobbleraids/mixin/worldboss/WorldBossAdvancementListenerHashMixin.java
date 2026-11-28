package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossAdvancementListeners;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.advancements.CriterionTrigger;
import org.spongepowered.asm.mixin.Mixin;

/** Avoid deep predicate hashing per player; equality, callbacks and listener ownership remain native. */
@Mixin(CriterionTrigger.Listener.class)
public abstract class WorldBossAdvancementListenerHashMixin {
    @WrapMethod(method="hashCode")
    private int cobbleRaids$bucket(Operation<Integer> original){
        try{return WorldBossAdvancementListeners.hash((CriterionTrigger.Listener<?>)(Object)this);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:advancement-listener-hash",failure);throw failure;}
    }
}
