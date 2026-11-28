package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossArenaProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class WorldBossPistonProtectionMixin {
    @Inject(method = "triggerEvent", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$protectArenaPistons(BlockState state, Level level, BlockPos pos, int type, int data, CallbackInfoReturnable<Boolean> cir) {
        // Vanilla moves at most twelve connected blocks; guard the head and boundary from outside too.
        // Stop the complete block event: retraction mutates the base before calling moveBlocks.
        try { if (WorldBossArenaProtection.protects(level, pos, 14)) cir.setReturnValue(false); }
        catch (RuntimeException failure) { RaidFaultBarrier.report("mixin:worldboss-piston", failure); cir.setReturnValue(false); }
    }
}
