package net.snobblemon.hubclient.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import com.mojang.math.Axis;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * The player model shared by every screen: the real local player (skin, armour, head item) plus the Snobble cosmetics
 * the server attached to them as display entities. Drag to turn it, scroll to zoom. It never leaves its own rectangle.
 */
public final class PlayerPreview extends BaseComponent {
    private float yaw = 24f;
    private float zoom = System.getProperty("snobblehub.test.noscissor") != null ? 0.5f : 1f;
    private long lastLog;
    /** Turn that shows the player's back, for wings and back items. */
    public static final float BACK_YAW = 160f;

    public void turnTo(float degrees) {
        this.yaw = degrees;
    }
    private final boolean interactive;
    private final boolean stage;

    public PlayerPreview(boolean interactive, boolean stage) {
        this.interactive = interactive;
        this.stage = stage;
        if (interactive) {
            mouseDown().subscribe((mx, my, button) -> {
                // the two turn arrows in the bottom corners of the stage
                if (my >= this.y + this.height - 16) {
                    if (mx < this.x + 18) { yaw -= 30f; return true; }
                    if (mx > this.x + this.width - 18) { yaw += 30f; return true; }
                }
                return true;
            });
            mouseDrag().subscribe((x, y, dx, dy, button) -> {
                yaw += (float) dx * 1.4f;
                return true;
            });
            mouseScroll().subscribe((x, y, amount) -> {
                zoom = Mth.clamp(zoom + (float) amount * 0.08f, 0.7f, 1.6f);
                return true;
            });
        }
    }

    /** owo only delivers drag events to the component that took focus on the click. */
    @Override
    public boolean canFocus(FocusSource source) {
        return interactive && source == FocusSource.MOUSE_CLICK;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return 64;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return 96;
    }

    @Override
    public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        int x = this.x, y = this.y, w = this.width, h = this.height;
        if (stage) {
            String r = Theme.region();
            Theme.blit(ctx, Theme.sprite("stage_" + r), x, y, w, h);
            int d = Math.min(w, h) - 8;
            int d2 = Math.max(32, (int) (d * 0.95f));
            ctx.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
            Theme.tex(ctx, Theme.id("textures/gui/stage_disc_" + r + ".png"), x + (w - d2) / 2, y + (h - d2) / 2 - h / 14, d2, d2, 0f, 0f, 96, 96, 96, 96);
            ctx.disableScissor();
            int pw = Math.max(28, (int) (w * 0.62f));
            int ph = Math.max(7, pw / 4);
            Theme.tex(ctx, Theme.id("textures/gui/platform_" + r + ".png"), x + (w - pw) / 2, y + (int) (h * 0.80f), pw, ph, 0f, 0f, 56, 14, 56, 14);
        }
        if (interactive && stage) {
            Theme.tex(ctx, Theme.icon("arrow_left"), x + 2, y + h - 16, 14, 14, 0f, 0f, 16, 16, 16, 16);
            Theme.tex(ctx, Theme.icon("arrow_right"), x + w - 16, y + h - 16, 14, 14, 0f, 0f, 16, 16, 16, 16);
        }
        if (player == null) return;

        boolean dbg = System.getProperty("snobblehub.test.noscissor") != null;
        if (!dbg) ctx.enableScissor(x, y, x + w, y + h);
        PoseStack ps = ctx.pose();
        ps.pushPose();
        float scale = (h * (stage ? 0.74f : 0.60f) / 1.8f) * zoom;                        // pixels per block: a 1.8-block player fills 60% of the panel
        ps.translate(x + w / 2f, y + h * 0.52f, 150.0);                  // the middle of the player sits here
        ps.scale(scale, scale, -scale);
        ps.translate(0.0, player.getBbHeight() / 2.0, 0.0);              // origin is at the feet; same set-up as the vanilla inventory
        ps.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        // Face the viewer whatever way the player was looking when the screen opened, then add the drag turn.
        ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot) + yaw));

        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        List<Entity> parts = new ArrayList<>(player.getPassengers());
        if (net.snobblemon.hubclient.HubClientMain.TEST_SELECT != null && System.currentTimeMillis() - lastLog > 3000) {
            lastLog = System.currentTimeMillis();
            net.snobblemon.hubclient.HubClient.LOGGER.info("[preview] passengers={} displaysNear={}", parts.size(), mc.level.getEntitiesOfClass(net.minecraft.world.entity.Display.class, player.getBoundingBox().inflate(4)).size());
            for (Entity e : parts) {
                String extra = "";
                if (e instanceof net.minecraft.world.entity.Display d && d.renderState() != null) {
                    var t = d.renderState().transformation().get(partialTicks);
                    extra = " scale=" + t.getScale() + " trans=" + t.getTranslation() + " item=" + (e instanceof net.minecraft.world.entity.Display.ItemDisplay id ? id.itemRenderState() : "-") + " yRot=" + e.getYRot() + " vr=" + ((e instanceof net.minecraft.world.entity.Display dd) ? dd.getViewRange() : 0f);
                }
                net.snobblemon.hubclient.HubClient.LOGGER.info("[preview] passenger {} at {},{},{} (player {},{},{}){}", e.getClass().getSimpleName(), e.getX(), e.getY(), e.getZ(), player.getX(), player.getY(), player.getZ(), extra);
            }
        }
        RenderSystem.runAsFancy(() -> {
            dispatcher.render(player, 0.0, 0.0, 0.0, 0.0f, partialTicks, ps, ctx.bufferSource(), LightTexture.FULL_BRIGHT);
            for (Entity e : parts) {
                double dx = Mth.lerp(partialTicks, e.xo, e.getX()) - Mth.lerp(partialTicks, player.xo, player.getX());
                double dy = Mth.lerp(partialTicks, e.yo, e.getY()) - Mth.lerp(partialTicks, player.yo, player.getY());
                double dz = Mth.lerp(partialTicks, e.zo, e.getZ()) - Mth.lerp(partialTicks, player.zo, player.getZ());
                // This frame is already y-up (the vanilla Z flip); the player's own renderer faces its model with
                // 180 - bodyYaw, which display entities do not, so a half turn is added for them.
                ps.pushPose();
                ps.translate(dx, dy, dz);
                dispatcher.render(e, 0.0, 0.0, 0.0, 0.0f, partialTicks, ps, ctx.bufferSource(), LightTexture.FULL_BRIGHT);
                ps.popPose();
            }
        });
        ctx.flush();
        dispatcher.setRenderShadow(true);
        ps.popPose();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        if (!dbg) ctx.disableScissor();
    }
}
