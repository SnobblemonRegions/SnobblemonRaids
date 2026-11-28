package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossAdvancementSaves;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Defer the native invocation itself, so advancement save hooks run once on their actual Main tick. */
@Mixin(PlayerList.class)
public abstract class WorldBossAdvancementBatchMixin {
    @WrapOperation(method="save",at=@At(value="INVOKE",target="Lnet/minecraft/server/PlayerAdvancements;save()V"))
    private void cobbleRaids$pace(PlayerAdvancements advancements,Operation<Void> original){
        try{
            if(advancements instanceof WorldBossAdvancementSaves.NativeTarget target
                    &&WorldBossAdvancementSaves.defer(target.cobbleRaids$advancementSavePath(),()->original.call(advancements)))return;
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:advancement-save-enrollment",failure);}
        original.call(advancements);
    }
}
