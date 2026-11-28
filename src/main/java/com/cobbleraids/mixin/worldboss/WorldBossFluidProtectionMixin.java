package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossArenaProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class WorldBossFluidProtectionMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$protectArenaFluidTicks(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        try { if (WorldBossArenaProtection.protects(level, pos)) ci.cancel(); }
        catch (RuntimeException failure) { RaidFaultBarrier.report("mixin:worldboss-fluid-tick", failure); ci.cancel(); }
    }
    @Inject(method = "canSpreadTo", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$protectArenaFlow(BlockGetter world, BlockPos from, BlockState source, Direction direction, BlockPos to, BlockState target, FluidState state, Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        try { if (world instanceof Level level && (WorldBossArenaProtection.protects(level, from) || WorldBossArenaProtection.protects(level, to))) cir.setReturnValue(false); }
        catch (RuntimeException failure) { RaidFaultBarrier.report("mixin:worldboss-fluid-spread", failure); cir.setReturnValue(false); }
    }
}
