package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossArenaProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class WorldBossFirePlacementMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$protectArenaFirePlacement(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        try {
            if ((Object) this instanceof ServerLevel level && state.is(BlockTags.FIRE) && WorldBossArenaProtection.protects(level, pos)) {
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                // Existing fire must still age so it can extinguish; never load a chunk to check it.
                if (chunk == null || !chunk.getBlockState(pos).is(BlockTags.FIRE)) cir.setReturnValue(false);
            }
        }
        catch (RuntimeException failure) { RaidFaultBarrier.report("mixin:worldboss-fire-placement", failure); cir.setReturnValue(false); }
    }
}
