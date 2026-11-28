package com.cobbleraids.veilclient;

import com.cobbleraids.visual.protocol.VeilPayload;
import com.cobbleraids.veilclient.RibbonStrip.V;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Soft, depth-tested world ribbons. No block models, shader replacement, server particles or raw GL. */
public final class DistortionVeilRenderer {
    private static final ResourceLocation SILK = texture("veil_silk"), WISP = texture("umbra_wisp"), MOTE = texture("veil_mote");
    private static final MultiBufferSource.BufferSource DRAW = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 20));
    private static final int VIOLET = 0xA56CFF, LILAC = 0xE2C9FF, SHADOW = 0x342052, SCARLET = 0xE44B86;
    private static boolean failed;
    private DistortionVeilRenderer() {}
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobbleraids_radar", "textures/worldboss/" + name + ".png");
    }
    public static void render(WorldRenderContext context) {
        if (failed) return;
        VeilPayload scene = DistortionVeilClient.frame();
        if (scene == null || scene.detail() == 0) return;
        var poses = context.matrixStack();
        if (poses == null) return;
        var camera = context.camera().getPosition();
        if (camera.distanceToSqr(new Vec3(scene.x(), scene.y(), scene.z())) > Math.pow(scene.radius() + 200, 2)) return;
        poses.pushPose();
        try {
            poses.translate(scene.x() - camera.x, scene.y() - camera.y, scene.z() - camera.z);
            double t = DistortionVeilClient.seconds(), opacity = DistortionVeilClient.opacity();
            Vec3 eye = camera.subtract(scene.x(), scene.y(), scene.z());
            int curtains = scene.detail() == 2 ? 14 : 6;
            VertexConsumer silk = DRAW.getBuffer(RenderType.entityTranslucent(SILK));
            for (int i = 0; i < curtains; i++) {
                double phase = i * 2.3999632297;
                int segments = 56;
                List<V> points = new ArrayList<>(segments + 1);
                double[] widths = new double[segments + 1];
                for (int j = 0; j <= segments; j++) {
                    double u = j / (double) segments;
                    points.add(vector(boundary(scene.radius(), phase, u, t)));
                    widths[j] = (3.8 + 1.5 * Math.sin(phase + 1)) * Math.pow(Math.sin(Math.PI * u), .7) + .2;
                }
                var strip = RibbonStrip.build(points, widths, new V(0, 1, 0));
                double breath = .7 + .3 * Math.sin(t * .55 + phase);
                stroke(poses, silk, strip, i % 4 == 0 ? SCARLET : VIOLET, opacity * .82 * breath,
                        2, -t * .055, false);

            }
            DRAW.endBatch();
            VertexConsumer mist = DRAW.getBuffer(RenderType.entityTranslucent(WISP));
            for (int i = 0; i < (scene.detail() == 2 ? 9 : 4); i++) {
                double phase = i * 2.3999632297;
                for (int j = 0; j < 36; j++) {
                    double u = (j + .5) / 36.0;
                    double radius = scene.radius() * (.06 + .91 * u);
                    double angle = phase + u * 1.65 + .045 * t + .12 * Math.sin(u * 14 - t * .6 + phase);
                    Vec3 at = new Vec3(Math.cos(angle) * radius, .09 + .025 * Math.sin(phase + u * 20), Math.sin(angle) * radius);
                    Vec3 along = new Vec3(Math.cos(angle + .8), 0, Math.sin(angle + .8)).scale(2.6);
                    Vec3 side = new Vec3(-along.z, 0, along.x).normalize().scale(.85 + .35 * Math.sin(t * .4 + phase + u * 8));
                    quad(poses, mist, at.subtract(along).subtract(side), at.subtract(along).add(side),
                            at.add(along).add(side), at.add(along).subtract(side), i % 5 == 0 ? SCARLET : VIOLET,
                            opacity * .58 * Math.sin(Math.PI * u), 0, 1);
                }
            }
            DRAW.endBatch();
            VertexConsumer sparks = DRAW.getBuffer(RenderType.entityTranslucentEmissive(MOTE));
            // Broken, individually textured light motes draw the ground currents; no solid clock disc.
            int currents = scene.detail() == 2 ? 9 : 4;
            for (int i = 0; i < currents; i++) {
                double phase = i * 2.3999632297;
                int count = scene.detail() == 2 ? 100 : 55;
                for (int j = 0; j < count; j++) {
                    double u = j / (double) count;
                    double r = scene.radius() * (.06 + .91 * u);
                    double angle = phase + u * 1.65 + .045 * t + .12 * Math.sin(u * 14 - t * .6 + phase);
                    double pulse = Math.pow(.5 + .5 * Math.sin(u * 38 - t * 1.6 + phase), 3);
                    double size = (.11 + .22 * pulse) * (scene.radius() / 72.0);
                    Vec3 at = new Vec3(Math.cos(angle) * r, .16 + .13 * Math.sin(u * 22 - t + phase), Math.sin(angle) * r);
                    billboard(poses, sparks, at, eye, size, j % 17 == 0 ? SCARLET : LILAC,
                            opacity * (.18 + .7 * pulse) * Math.sin(Math.PI * u));
                }
            }
            // Slowly ascending embers make the boundary legible at long range without filling the arena.
            for (int i = 0; i < (scene.detail() == 2 ? 110 : 32); i++) {
                double phase = i * 2.3999632297, u = fract(i * .61803398875 + t * .025);
                var at = boundary(scene.radius(), phase, u, t);
                billboard(poses, sparks, at, eye, .16 + .28 * Math.sin(Math.PI * u),
                        i % 9 == 0 ? SCARLET : LILAC, opacity * .8 * Math.sin(Math.PI * u));
            }
            DRAW.endBatch();
            mantle(context, poses, scene, eye, t, opacity);
        } catch (RuntimeException failure) {
            failed = true;
            LoggerFactory.getLogger("CobbleRaids/DistortionVeil").error("[CobbleRaids] Disabled Distortion Veil after a rendering failure", failure);
        } finally {
            try { DRAW.endBatch(); }
            catch (RuntimeException failure) {
                failed = true;
                LoggerFactory.getLogger("CobbleRaids/DistortionVeil").error("[CobbleRaids] Could not flush Distortion Veil buffers", failure);
            } finally { poses.popPose(); }
        }
    }
    private static Vec3 boundary(double radius, double phase, double u, double t) {
        // Broad, oblique sweeps occupy different heights; never converge into a wireframe tent.
        double elevation = .16 + .72 * (.5 + .5 * Math.sin(phase * 1.7))
                + .16 * Math.sin(u * Math.PI * 1.4 + phase + t * .09);
        double theta = phase + u * 1.8 + t * .018 + .045 * Math.sin(u * 14 - t * .7 + phase);
        double r = radius * Math.cos(elevation) * (1 + .012 * Math.sin(u * 19 + t * .6 + phase));
        return new Vec3(r * Math.cos(theta), radius * Math.sin(elevation), r * Math.sin(theta));
    }

    private static void mantle(WorldRenderContext context, PoseStack poses, VeilPayload scene, Vec3 eye, double t, double opacity) {
        var attached = WingAttachments.pose(scene.entityUuid());
        if (attached == null) return;
        V origin = new V(scene.x(), scene.y(), scene.z());
        int streams = scene.detail() == 2 ? 3 : 1, segments = scene.detail() == 2 ? 64 : 48;
        List<List<V>> paths = new ArrayList<>(2);
        VertexConsumer wisps = DRAW.getBuffer(RenderType.entityTranslucent(WISP));
        for (int wing = 0; wing < 2; wing++) {
            var anchors = attached.points().subList(wing * WingAttachments.ANCHORS_PER_WING, (wing + 1) * WingAttachments.ANCHORS_PER_WING);
            var edge = RibbonStrip.resample(anchors, segments);
            paths.add(edge);
            V up = wing == 0 ? attached.leftUp() : attached.rightUp();
            V normal = (wing == 0 ? attached.leftNormal() : attached.rightNormal()).mul(wing == 0 ? 1 : -1);
            for (int stream = 0; stream < streams; stream++) {
                List<V> points = new ArrayList<>(segments + 1); double[] widths = new double[segments + 1];
                double phase = stream * 2.1 + wing * 1.3;
                for (int j = 0; j <= segments; j++) {
                    double u = j / (double) segments, fade = Math.sin(Math.PI * u);
                    double lift = scene.bossScale() * (.08 + stream * .035 + .025 * Math.sin(u * 7 - t * .6 + phase));
                    double curl = scene.bossScale() * .07 * fade * Math.sin(u * 6 - t * .55 + phase);
                    points.add(edge.get(j).sub(origin).add(normal.mul(lift)).add(up.mul(curl + stream * scene.bossScale() * .035)));
                    widths[j] = scene.bossScale() * (streams == 1 ? .10 : stream == 0 ? .14 : stream == 1 ? .09 : .055)
                            * Math.pow(Math.max(0, fade), .65);
                }
                var strip = RibbonStrip.build(points, widths, up);
                int color = streams == 1 || stream == 1 ? VIOLET : stream == 0 ? SHADOW : SCARLET;
                double density = streams == 1 ? .65 : stream == 0 ? .62 : stream == 1 ? .72 : .45;
                // Two narrow crossed wisps stay visible from all sides without twisting toward the camera.
                stroke(poses, wisps, strip, color, opacity * density * .65, 1.6, phase - t * .08, false);
                stroke(poses, wisps, strip, color, opacity * density * .65, 1.6, phase - t * .08, true);
            }
        }
        DRAW.endBatch();
        var glow = DRAW.getBuffer(RenderType.entityTranslucentEmissive(MOTE));
        int count = scene.detail() == 2 ? 64 : 18;
        for (int i = 0; i < count; i++) {
            int wing = i % 2; double u = fract(i * .61803398875 + t * .06);
            List<V> path = paths.get(wing); double sample = u * (path.size() - 1); int at = Math.min(path.size() - 2, (int) sample);
            V p = path.get(at).add(path.get(at + 1).sub(path.get(at)).mul(sample - at)).sub(origin);
            V normal = (wing == 0 ? attached.leftNormal() : attached.rightNormal()).mul(wing == 0 ? 1 : -1);
            p = p.add(normal.mul(scene.bossScale() * .1));
            billboard(poses, glow, minecraft(p), eye, .06 + scene.bossScale() * .04,
                    i % 7 == 0 ? SCARLET : VIOLET, opacity * .65 * Math.sin(Math.PI * u));
        }
        DRAW.endBatch();
    }
    private static double fract(double n) { return n - Math.floor(n); }
    private static V vector(Vec3 p) { return new V(p.x, p.y, p.z); }
    private static Vec3 minecraft(V p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static void stroke(PoseStack poses, VertexConsumer out, RibbonStrip.Section[] strip, int color,
            double opacity, double repeats, double flow, boolean crossed) {
        for (int i = 1; i < strip.length; i++) {
            var a = strip[i - 1]; var b = strip[i];
            V sa = (crossed ? a.normal() : a.side()).mul(a.width()), sb = (crossed ? b.normal() : b.side()).mul(b.width());
            int ca = argb(color, opacity * Math.sin(Math.PI * a.u())), cb = argb(color, opacity * Math.sin(Math.PI * b.u()));
            float va = (float) (a.u() * repeats + flow), vb = (float) (b.u() * repeats + flow);
            vertex(poses, out, minecraft(a.center().sub(sa)), ca, 0, va);
            vertex(poses, out, minecraft(a.center().add(sa)), ca, 1, va);
            vertex(poses, out, minecraft(b.center().add(sb)), cb, 1, vb);
            vertex(poses, out, minecraft(b.center().sub(sb)), cb, 0, vb);
        }
    }
    private static int argb(int color, double alpha) {
        return ((int) (Math.clamp(alpha, 0, 1) * 255) << 24) | color;
    }
    private static void billboard(PoseStack poses, VertexConsumer out, Vec3 at, Vec3 eye, double size, int color, double alpha) {
        Vec3 normal = eye.subtract(at).normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(normal);
        if (right.lengthSqr() < 1e-8) right = new Vec3(1, 0, 0);
        right = right.normalize().scale(size);
        Vec3 up = normal.cross(right).normalize().scale(size);
        quad(poses, out, at.subtract(right).subtract(up), at.add(right).subtract(up),
                at.add(right).add(up), at.subtract(right).add(up), color, alpha, 0, 1);
    }
    private static void quad(PoseStack poses, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            int color, double alpha, float v0, float v1) {
        int argb = ((int) (Math.clamp(alpha, 0, 1) * 255) << 24) | color;
        vertex(poses, out, a, argb, 0, v0); vertex(poses, out, b, argb, 1, v0);
        vertex(poses, out, c, argb, 1, v1); vertex(poses, out, d, argb, 0, v1);
    }
    private static void vertex(PoseStack poses, VertexConsumer out, Vec3 p, int color, float u, float v) {
        out.addVertex(poses.last(), (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(poses.last(), 0, 1, 0);
    }
}
