package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossArenaProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class WorldBossFireProtectionMixin {
    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$protectArenaFire(Level level, BlockPos target, int chance, RandomSource random, int age, CallbackInfo ci) {
        // Guard consumption of the protected target, not the entire outside fire tick/aging cycle.
        try { if (WorldBossArenaProtection.protects(level, target)) ci.cancel(); }
        catch (RuntimeException failure) { RaidFaultBarrier.report("mixin:worldboss-fire-burn", failure); ci.cancel(); }
    }
}
