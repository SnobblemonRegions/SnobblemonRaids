package com.cobbleraids.radarclient.mixin;

import com.cobbleraids.veilclient.DistortionVeilClient;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PokemonEntity inherits this implementation; expand render bounds without altering collision. */
@Mixin(LivingEntity.class)
public abstract class WorldBossBoundsMixin {
    @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
    private void cobbleraids$fitOwnedModel(CallbackInfoReturnable<AABB> cir) {
        try {
            var entity = (LivingEntity) (Object) this;
            var scene = DistortionVeilClient.sceneFor(entity);
            if (scene == null) return;
            double extent = scene.bossScale() * 6.5;
            var bounds = new AABB(entity.getX() - extent, entity.getY() - scene.bossScale() * .6,
                    entity.getZ() - extent, entity.getX() + extent, entity.getY() + scene.bossScale() * 9.0, entity.getZ() + extent);
            cir.setReturnValue(cir.getReturnValue().minmax(bounds));
        } catch (RuntimeException failure) { DistortionVeilClient.reportFault("boss bounds", failure); }
    }
}
