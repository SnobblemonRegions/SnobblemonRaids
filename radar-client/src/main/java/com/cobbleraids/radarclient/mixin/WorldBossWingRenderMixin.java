package com.cobbleraids.radarclient.mixin;

import com.cobbleraids.veilclient.DistortionVeilClient;
import com.cobbleraids.veilclient.WingAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class WorldBossWingRenderMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void cobbleraids$beginWingPose(Entity entity, double x, double y, double z, float yaw, float partial,
            PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        try { WingAttachments.begin(entity, x, y, z, partial, poses); }
        catch (RuntimeException failure) { DistortionVeilClient.reportFault("begin wing pose", failure); }
    }
    @Inject(method = "render", at = @At("RETURN"))
    private void cobbleraids$finishWingPose(Entity entity, double x, double y, double z, float yaw, float partial,
            PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        try { WingAttachments.end(); }
        catch (RuntimeException failure) { DistortionVeilClient.reportFault("finish wing pose", failure); }
    }
}
