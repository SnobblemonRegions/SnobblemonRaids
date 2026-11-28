package com.cobbleraids.veilclient;

import com.cobbleraids.veilclient.RibbonStrip.V;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Captures animated joints during the owned entity's actual model draw, before shared models are reused. */
public final class WingAttachments {
    public static final int ANCHORS_PER_WING = 8;
    // Authored from the opaque upper silhouette of the inspected 512px Giratina atlas.
    // Cube UV direction was checked against Minecraft's actual vertex output, not its rectangular bounds.
    private static final String[] NAMES = {"bone30", "bone28", "bone29", "bone31", "bone31", "bone31", "bone31", "bone31",
            "bone40", "bone41", "bone43", "bone44", "bone44", "bone44", "bone44", "bone44"};
    private static final double[] TOP = {3.5, 11.25, 22, 36, 28, 21, 10, -11};
    private static final double[] Z = {3, 7, 8, .5, 7.5, 15.5, 22.5, 27.5};
    private record Site(int index, double x, double y, double z) {}
    private record Node(ModelPart part, List<Site> sites, List<Node> children) {}
    private record Binding(Object model, Node root) {}
    record Pose(UUID owner, Object model, List<V> points, V leftUp, V rightUp, V leftNormal, V rightNormal, long millis) {}
    private static final class Capture {
        UUID owner; Object model; Entity entity; boolean lookedUp, sampled; Matrix4f inverse; V offset;
        V[] points = new V[NAMES.length], ups = new V[NAMES.length], normals = new V[NAMES.length];
        Node root;
    }
    private static final List<Capture> STACK = new ArrayList<>();
    private static final WingMotion MOTION = new WingMotion(ANCHORS_PER_WING);
    private static Capture active;
    private static boolean worldPass;
    private static Binding binding;
    private static Pose pose;
    private static Method delegateMethod, modelMethod;
    private WingAttachments() {}

    public static void beginFrame() {
        active = null; STACK.clear(); pose = null; worldPass = false;
        if (DistortionVeilClient.frame() == null) clear();
    }
    public static void openWorldPass() { worldPass = true; }
    public static void closeWorldPass() { worldPass = false; active = null; STACK.clear(); }
    public static void begin(Entity entity, double x, double y, double z, float partial, PoseStack stack) {
        if (!worldPass) return;
        STACK.add(active); active = null;
        var scene = DistortionVeilClient.sceneFor(entity);
        if (scene == null || scene.detail() == 0) return;
        var origin = entity.getPosition(partial);
        var next = new Capture(); next.owner = entity.getUUID(); next.entity = entity;
        next.inverse = new Matrix4f(stack.last().pose()).invert();
        next.offset = new V(origin.x - x, origin.y - y, origin.z - z);
        active = next;
    }
    private static void resolve(Capture capture) {
        capture.lookedUp = true;
        capture.root = null;
        try {
            Entity entity = capture.entity;
            if (delegateMethod == null || !delegateMethod.getDeclaringClass().isInstance(entity)) {
                delegateMethod = entity.getClass().getMethod("getDelegate"); modelMethod = null;
            }
            Object delegate = delegateMethod.invoke(entity);
            if (modelMethod == null || !modelMethod.getDeclaringClass().isInstance(delegate)) {
                modelMethod = delegate.getClass().getMethod("getCurrentModel");
            }
            Object model = modelMethod.invoke(delegate);
            if (model == null) return;
            if (binding == null || binding.model() != model) {
                binding = bind(model); MOTION.clear();
            }
            capture.model = model; capture.root = binding.root();
        } catch (ReflectiveOperationException failure) {
            DistortionVeilClient.reportFault("wing pose binding", new IllegalStateException(failure));
        }
    }
    private static Binding bind(Object model) throws ReflectiveOperationException {
        Object value = model.getClass().getMethod("getRelevantPartsByName").invoke(model);
        Map<String, ModelPart> names = new HashMap<>();
        if (value instanceof Map<?, ?> parts) for (var entry : parts.entrySet()) {
            if (entry.getKey() instanceof String name && entry.getValue() instanceof ModelPart part) names.put(name, part);
        }
        if (!Arrays.stream(NAMES).allMatch(names::containsKey)) {
            Object root = model.getClass().getMethod("getRootPart").invoke(model);
            collect(root, names, new IdentityHashMap<>(), 0);
        }
        Map<ModelPart, List<Site>> result = new IdentityHashMap<>();
        if (!Arrays.stream(NAMES).allMatch(names::containsKey)) return new Binding(model, null);
        for (int i = 0; i < NAMES.length; i++) {
            int at = i % ANCHORS_PER_WING; double side = i < ANCHORS_PER_WING ? 1 : -1;
            result.computeIfAbsent(names.get(NAMES[i]), unused -> new ArrayList<>())
                    .add(new Site(i, at >= 2 ? side * .25 / 16 : 0, -TOP[at] / 16, Z[at] / 16));
        }
        Object root = model.getClass().getMethod("getRootPart").invoke(model);
        return new Binding(model, root instanceof ModelPart part ? tree(part, result, new IdentityHashMap<>(), 0) : null);
    }
    private static Node tree(ModelPart part, Map<ModelPart, List<Site>> sites, Map<Object, Boolean> seen, int depth)
            throws ReflectiveOperationException {
        if (depth > 128 || seen.size() > 1024 || seen.put(part, true) != null) return null;
        var children = new ArrayList<Node>();
        Object value = part.getClass().getMethod("getChildren").invoke(part);
        if (value instanceof Map<?, ?> map) for (var child : map.values()) {
            if (child instanceof ModelPart modelPart) {
                Node node = tree(modelPart, sites, seen, depth + 1);
                if (node != null) children.add(node);
            }
        }
        var own = sites.getOrDefault(part, List.of());
        return own.isEmpty() && children.isEmpty() ? null : new Node(part, own, List.copyOf(children));
    }
    private static void collect(Object bone, Map<String, ModelPart> names, Map<Object, Boolean> seen, int depth)
            throws ReflectiveOperationException {
        if (depth > 128 || seen.size() > 1024 || seen.put(bone, true) != null) return;
        Object value = bone.getClass().getMethod("getChildren").invoke(bone);
        if (value instanceof Map<?, ?> children) for (var child : children.entrySet()) {
            if (child.getValue() instanceof ModelPart part && child.getKey() instanceof String name) names.put(name, part);
            if (child.getValue() != null) collect(child.getValue(), names, seen, depth + 1);
        }
    }
    public static void sample(ModelPart part, PoseStack stack) {
        Capture capture = active;
        if (capture == null || capture.sampled || !part.visible) return;
        if (!capture.lookedUp) resolve(capture);
        if (capture.root == null || capture.root.part() != part) return;
        capture.sampled = true;
        // Evaluate the posed subtree once at root draw, so render batching cannot skip child samples.
        sampleTree(capture.root, stack, capture);
    }
    private static void sampleTree(Node node, PoseStack stack, Capture capture) {
        if (!node.part().visible) return;
        stack.pushPose();
        try {
            node.part().translateAndRotate(stack);
            if (!node.sites().isEmpty()) {
                Matrix4f transform = new Matrix4f(capture.inverse).mul(stack.last().pose());
                var up = transform.transformDirection(new Vector3f(0, -1, 0));
                var normal = transform.transformDirection(new Vector3f(1, 0, 0));
                if (Float.isFinite(up.lengthSquared()) && Float.isFinite(normal.lengthSquared())
                        && up.lengthSquared() > 1e-12 && normal.lengthSquared() > 1e-12) {
                    up.normalize(); normal.normalize();
                    for (Site site : node.sites()) {
                        var p = transform.transformPosition(new Vector3f((float) site.x(), (float) site.y(), (float) site.z()));
                        if (!Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.z)) continue;
                        capture.points[site.index()] = new V(p.x, p.y, p.z).add(capture.offset);
                        capture.ups[site.index()] = new V(up.x, up.y, up.z);
                        capture.normals[site.index()] = new V(normal.x, normal.y, normal.z);
                    }
                }
            }
            for (Node child : node.children()) sampleTree(child, stack, capture);
        } finally { stack.popPose(); }
    }
    public static void end() {
        if (!worldPass) return;
        Capture capture = active;
        if (capture != null && Arrays.stream(capture.points).allMatch(p -> p != null)) {
            long now = DistortionVeilClient.millis();
            var smoothed = MOTION.update(capture.owner, capture.model, Arrays.asList(capture.points), now);
            pose = new Pose(capture.owner, capture.model, smoothed, capture.ups[0], capture.ups[ANCHORS_PER_WING],
                    capture.normals[0], capture.normals[ANCHORS_PER_WING], now);
        }
        active = STACK.isEmpty() ? null : STACK.removeLast();
    }
    static Pose pose(UUID owner) { return pose != null && pose.owner().equals(owner) ? pose : null; }
    public static String diagnostics() {
        return pose == null ? "captured=0 binding=" + (binding == null ? "none" : binding.root() == null ? "unsupported" : "ready") : "captured=" + NAMES.length + " owner=" + pose.owner() + " points=" + pose.points();
    }
    static void clear() { worldPass = false; active = null; STACK.clear(); pose = null; binding = null; MOTION.clear(); }
}
