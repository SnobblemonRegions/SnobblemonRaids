package net.snobblemon.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a player's cosmetics straight onto the player model, the same way vanilla draws a head item or armor: every
 * piece is placed in the coordinate frame of a model bone (head, body, arm, leg), so it follows the pose, the walk
 * cycle, sneaking, swimming, riding and any emote exactly. No entity, no item in a slot.
 *
 * Frame used for placement: after {@code bone.translateAndRotate} the stack is in the entity-model space (x and y flipped).
 * Scaling by (-1,-1,1) turns that into an upright frame whose origin is the bone pivot, +Y up, +X the player's right and
 * -Z the direction the player faces - the same frame in which the cosmetic models were authored.
 */
public final class CosmeticLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int FULL_BRIGHT = LightTexture.pack(15, 15);
    /** Pivot heights above the feet of the standing player, in blocks. */
    private static final float PIVOT_Y = 1.5f;

    public CosmeticLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        PlayerCosmetics pc = CosmeticsClient.get(player.getUUID());
        if (pc == null || pc.isEmpty() || !CosmeticsClient.visible(player)) return;
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        if (cam != null && cam.distanceToSqr(player) > 64 * 64) return;

        PlayerModel<AbstractClientPlayer> m = getParentModel();
        float age = player.tickCount + partialTick;

        CosmeticSpec hat = pc.get("hat");
        if (hat != null) hat(ps, buf, light, player, m.head, hat, age);

        CosmeticSpec back = pc.get("back");
        if (back != null && !player.isFallFlying()) back(ps, buf, light, player, m.body, back, pc, partialTick);

        for (String slot : new String[]{"armor_head", "armor_chest", "armor_legs", "armor_feet"}) {
            CosmeticSpec a = pc.get(slot);
            if (a != null) armor(ps, buf, light, player, m, a, age);
        }

        CosmeticSpec balloon = pc.get("balloon");
        if (balloon != null) balloon(ps, buf, light, player, balloon, pc, partialTick);
    }

    // -------------------------------------------------------------------------------------------- helpers

    private static void frame(PoseStack ps, ModelPart bone) {
        bone.translateAndRotate(ps);
        ps.scale(-1f, -1f, 1f);
    }

    private static void item(PoseStack ps, MultiBufferSource buf, ItemStack stack, ItemDisplayContext ctx, int light, AbstractClientPlayer player) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ctx, light, OverlayTexture.NO_OVERLAY, ps, buf, player.level(), player.getId());
    }

    private static float base(CosmeticSpec s) {
        return s.headMount ? 0.625f * s.scale : s.scale;
    }

    private static ItemDisplayContext ctx(CosmeticSpec s) {
        return s.headMount ? ItemDisplayContext.HEAD : ItemDisplayContext.NONE;
    }

    // ------------------------------------------------------------------------------------------------ hats

    private void hat(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer player, ModelPart head, CosmeticSpec s, float age) {
        CosmeticSpec.Hat a = s.hat;
        ItemStack stack = s.model;
        if (a != null && !a.frames().isEmpty()) stack = a.frames().get((int) (age / a.frameTicks()) % a.frames().size());

        ps.pushPose();
        frame(ps, head);
        // Head-mounted hats keep the exact look they had as a vanilla head item: 0.25 up, 0.625 scale, definition scale/offset ignored.
        float sc;
        if (s.headMount) {
            ps.translate(0, 0.25f, 0);
            sc = 0.625f;
        } else {
            ps.translate(s.offX, 0.29f + s.offY, -s.offZ);
            ps.mulPose(Axis.YP.rotationDegrees(-s.rotY));
            sc = s.scale;
        }
        float f = 1f;
        if (a != null && a.pulse() > 0) f += a.pulse() * (float) Math.sin(age * 2 * Math.PI / a.pulseTicks());
        ps.scale(sc * f, sc * f, sc * f);
        item(ps, buf, stack, ctx(s), light, player);
        if (a != null && a.spin() != null) {
            ps.pushPose();
            if (a.bob() != 0) ps.translate(0, a.bob() * (float) Math.sin(age * 2 * Math.PI / 60), 0);
            float turn = a.ticks() == 0 ? 0 : (float) (age * 2 * Math.PI / a.ticks());
            ps.mulPose(Axis.YP.rotation(turn));
            item(ps, buf, a.spin(), ctx(s), light, player);
            ps.popPose();
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------------------------------------ back

    private void backPlace(PoseStack ps, CosmeticSpec s) {
        if (s.headMount) ps.translate(s.offX, 1.8f - PIVOT_Y + 1.6885f * s.scale + s.offY, -s.offZ);
        else ps.translate(s.offX, 1.05f - PIVOT_Y + s.offY, 0.28f - s.offZ);
        ps.mulPose(Axis.YP.rotationDegrees(-s.rotY));
    }

    private void back(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer player, ModelPart body, CosmeticSpec s, PlayerCosmetics pc, float partial) {
        float e = base(s);
        CosmeticSpec.Wings w = s.wings;
        if (w == null) {
            ps.pushPose();
            frame(ps, body);
            backPlace(ps, s);
            ps.scale(e, e, e);
            item(ps, buf, s.model, ctx(s), light, player);
            ps.popPose();
            return;
        }

        if (w.base() != null) {
            ps.pushPose();
            frame(ps, body);
            backPlace(ps, s);
            ps.scale(e, e, e);
            item(ps, buf, w.base(), ctx(s), light, player);
            ps.popPose();
        }

        float phase = pc.flapPhase + pc.flapStep * partial;
        float f = (float) Math.sin(phase);
        float fold = player.isCrouching() ? 1f : 0f;
        float yaw = w.swing() * (pc.flapAmp * f - 0.8f * fold);
        float roll = w.lift() * (pc.flapAmp * f - 0.8f * fold);
        Vector3f h = hinge(w).mul(e);
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? 1f : -1f;
            Quaternionf q = new Quaternionf().rotationYXZ((float) Math.toRadians(yaw * sg), 0f, (float) Math.toRadians(-roll * sg));
            Vector3f t = new Vector3f(h).sub(q.transform(new Vector3f(h)));
            ps.pushPose();
            frame(ps, body);
            backPlace(ps, s);
            ps.translate(t.x, t.y, t.z);
            ps.mulPose(q);
            ps.scale(e, e, e);
            item(ps, buf, side == 0 ? w.left() : w.right(), ctx(s), light, player);
            ps.popPose();
        }
    }

    private static Vector3f hinge(CosmeticSpec.Wings w) {
        Vector3f p = new Vector3f(w.hinge().x / 16f - 0.5f, w.hinge().y / 16f - 0.5f, w.hinge().z / 16f - 0.5f);
        p.mul(w.dispS().x, w.dispS().y, w.dispS().z);
        Quaternionf r = new Quaternionf().rotationXYZ((float) Math.toRadians(w.dispR().x), (float) Math.toRadians(w.dispR().y), (float) Math.toRadians(w.dispR().z));
        r.transform(p);
        return p.add(w.dispT().x / 16f, w.dispT().y / 16f, w.dispT().z / 16f);
    }

    // ----------------------------------------------------------------------------------------------- armor

    private void armor(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> m, CosmeticSpec s, float age) {
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        int t2 = ((int) age) & -2;
        for (CosmeticSpec.Part part : s.parts) {
            ModelPart bone = switch (part.anchor()) {
                case HEAD -> m.head;
                case BODY -> m.body;
                case ARM_R -> m.rightArm;
                case ARM_L -> m.leftArm;
                case LEG_R -> m.rightLeg;
                case LEG_L -> m.leftLeg;
            };
            ps.pushPose();
            frame(ps, bone);
            float sx = 1f;
            switch (part.anchor()) {
                // the model is centred on the arm box, which sits 1 px (0.5 px slim) outside the pivot
                case ARM_R -> { ps.translate(slim ? 0.03125f : 0.0625f, 0f, 0f); sx = slim ? 0.75f : 1f; }
                case ARM_L -> { ps.translate(slim ? -0.03125f : -0.0625f, 0f, 0f); sx = slim ? 0.75f : 1f; }
                default -> { }
            }
            float pu = 1f;
            float bob = 0f;
            if (part.pulseScale() > 0) pu += part.pulseScale() * (0.5f + 0.5f * (float) Math.sin(Math.PI * 2 * t2 / part.pulseTicks()));
            if (part.bobAmount() > 0) bob = part.bobAmount() * (float) Math.sin(Math.PI * 2 * t2 / part.bobTicks());
            if (bob != 0) ps.translate(0, bob, 0);
            ps.scale(sx * pu, pu, pu);
            item(ps, buf, part.stack(), ItemDisplayContext.NONE, part.glow() ? FULL_BRIGHT : light, player);
            ps.popPose();
        }
    }

    // --------------------------------------------------------------------------------------------- balloon

    private void balloon(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer player, CosmeticSpec s, PlayerCosmetics pc, float partial) {
        if (pc.balloonPos == null) return;
        Vec3 prev = pc.balloonPrev == null ? pc.balloonPos : pc.balloonPrev;
        Vec3 pos = prev.lerp(pc.balloonPos, partial);
        Vec3 feet = new Vec3(Mth.lerp(partial, player.xo, player.getX()), Mth.lerp(partial, player.yo, player.getY()), Mth.lerp(partial, player.zo, player.getZ()));
        Vec3 rel = pos.subtract(feet);
        double rad = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
        Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        Vec3 right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        // world offset -> upright body-aligned frame (+X right, +Y up, -Z forward)
        Vec3 fr = new Vec3(rel.dot(right), rel.y, -rel.dot(fwd));

        ps.pushPose();
        ps.translate(0, 1.501f, 0);
        ps.scale(-1f, -1f, 1f);

        // string from the hand to the bottom of the balloon
        double side = Math.signum(s.offX == 0f ? 1f : s.offX) * 0.38;
        Vec3 start = new Vec3(side, 1.0, 0.05);
        Vec3 end = fr.add(0, -0.31 * s.scale, 0);
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        Vec3 d = end.subtract(start);
        double len = d.length();
        if (len > 0.01) {
            float nx = (float) (d.x / len), ny = (float) (d.y / len), nz = (float) (d.z / len);
            PoseStack.Pose pose = ps.last();
            lines.addVertex(pose, (float) start.x, (float) start.y, (float) start.z).setColor(0, 0, 0, 255).setNormal(pose, nx, ny, nz);
            lines.addVertex(pose, (float) end.x, (float) end.y, (float) end.z).setColor(0, 0, 0, 255).setNormal(pose, nx, ny, nz);
        }

        ps.translate(fr.x, fr.y, fr.z);
        Vec3 vel = pc.balloonVel;
        float lean = (float) Mth.clamp(vel.dot(right) * 40.0, -25.0, 25.0);
        ps.mulPose(Axis.ZP.rotationDegrees(lean));
        ps.mulPose(Axis.YP.rotationDegrees(-s.rotY));
        ps.scale(s.scale, s.scale, s.scale);
        item(ps, buf, s.model, ItemDisplayContext.NONE, light, player);
        ps.popPose();
    }
}
