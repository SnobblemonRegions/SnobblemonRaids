package net.snobblemon.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.snobblemon.client.cosmetics.CosmeticsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A worn armor cosmetic stands in for the real piece in that slot: the real piece stays equipped, it is just not drawn. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void snobblemon$covered(PoseStack poseStack, MultiBufferSource buffer, T entity, EquipmentSlot slot, int light, A model, CallbackInfo ci) {
        if (CosmeticsClient.replacesArmor(entity, slot)) ci.cancel();
    }
}