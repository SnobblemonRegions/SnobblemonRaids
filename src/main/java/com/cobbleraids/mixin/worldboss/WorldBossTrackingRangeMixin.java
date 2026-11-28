package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossService;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend only the single owned showpiece; vanilla chunk/view-distance gates remain in force. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class WorldBossTrackingRangeMixin {
    @Shadow @Final Entity entity;
    @Inject(method = "getEffectiveRange", at = @At("RETURN"), cancellable = true)
    private void cobbleraids$trackArenaShowpiece(CallbackInfoReturnable<Integer> cir) {
        try {
            if (WorldBossService.isShowpiece(entity)) {
                cir.setReturnValue(WorldBossService.showpieceTrackingRange(cir.getReturnValue()));
            }
        } catch (RuntimeException failure) { RaidFaultBarrier.report("mixin:worldboss-tracking-range", failure); }
    }
}
