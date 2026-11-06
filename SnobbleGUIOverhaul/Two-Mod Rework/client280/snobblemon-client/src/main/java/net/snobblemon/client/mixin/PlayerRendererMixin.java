package net.snobblemon.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.snobblemon.client.cosmetics.CosmeticsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When a complete armor cosmetic is worn, the plain player body is not drawn under it. Only for the world render, never the first-person hand. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Unique private boolean snobblemon$hideBody;

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void snobblemon$begin(AbstractClientPlayer player, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci) {
        snobblemon$hideBody = CosmeticsClient.hidesBody(player);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void snobblemon$end(AbstractClientPlayer player, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci) {
        snobblemon$hideBody = false;
    }

    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void snobblemon$hide(AbstractClientPlayer player, CallbackInfo ci) {
        if (snobblemon$hideBody) {
            PlayerModel<AbstractClientPlayer> m = ((LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) this).getModel();
            m.setAllVisible(false);
        }
    }
}