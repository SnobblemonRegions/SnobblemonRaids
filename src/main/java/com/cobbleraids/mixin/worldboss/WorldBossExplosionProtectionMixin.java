package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossArenaProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class WorldBossExplosionProtectionMixin {
    @Shadow @Final private Level level;
    @Shadow public abstract List<BlockPos> getToBlow();
    @Inject(method = "finalizeExplosion", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$protectArenaBlocks(boolean particles, CallbackInfo ci) {
        try { getToBlow().removeIf(pos -> WorldBossArenaProtection.protects(level, pos)); }
        catch (RuntimeException failure) { RaidFaultBarrier.report("mixin:worldboss-explosion", failure); ci.cancel(); }
    }
}
