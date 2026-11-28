package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossArenaProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class WorldBossEntityProtectionMixin {
    @Inject(method = "ignoreExplosion", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$protectArenaEntities(Explosion explosion, CallbackInfoReturnable<Boolean> cir) {
        try { if (WorldBossArenaProtection.protects((Entity) (Object) this)) cir.setReturnValue(true); }
        catch (RuntimeException failure) { RaidFaultBarrier.report("mixin:worldboss-explosion-entity", failure); cir.setReturnValue(true); }
    }
}
