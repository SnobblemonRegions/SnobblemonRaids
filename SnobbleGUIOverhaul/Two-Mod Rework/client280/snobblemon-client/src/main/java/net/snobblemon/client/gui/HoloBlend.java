package net.snobblemon.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * GUI render type with the blend (src * dst + dst * src): a colour of 50% grey leaves the card untouched, brighter hues lift it and darker ones deepen it,
 * which is what the web card's mix-blend-mode: overlay holo layer does (exact for mid-tones, a close stand-in elsewhere).
 */
final class HoloBlend extends RenderStateShard {
    private HoloBlend() { super("snobblemon_holo_unused", () -> { }, () -> { }); }

    static final RenderType TYPE = RenderType.create("snobblemon_holo_overlay", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("snobblemon_holo_overlay_blend", () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.SRC_COLOR, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
                    }, () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    }))
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));
}
