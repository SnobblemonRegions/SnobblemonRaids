package com.cobbleraids.radarclient.mixin;

import com.cobbleraids.veilclient.DistortionVeilClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned PokemonEntity's non-passenger override delegates to this exact vanilla method. */
@Mixin(Entity.class)
public abstract class WorldBossDistanceMixin {
    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void cobbleraids$showOwnedBoss(double distance, CallbackInfoReturnable<Boolean> cir) {
        try {
            var scene = DistortionVeilClient.sceneFor((Entity) (Object) this);
            if (scene != null && distance >= 0 && distance < Math.pow(scene.radius() + 32, 2)) cir.setReturnValue(true);
        } catch (RuntimeException failure) { DistortionVeilClient.reportFault("boss distance", failure); }
    }
}
