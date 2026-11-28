package com.cobbleraids.radarclient.mixin;

import com.cobbleraids.veilclient.DistortionVeilClient;
import com.cobbleraids.veilclient.WingAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ModelPart is final; Cobblemon's Bone adapter delegates its actual draw to this five-argument overload. */
@Mixin(value = ModelPart.class, priority = 1100)
public abstract class WorldBossWingPartMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"))
    private void cobbleraids$captureWingJoint(PoseStack poses, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        try { WingAttachments.sample((ModelPart) (Object) this, poses); }
        catch (RuntimeException failure) { DistortionVeilClient.reportFault("capture wing joint", failure); }
    }
}
