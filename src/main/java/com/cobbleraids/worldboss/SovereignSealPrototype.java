package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import com.mojang.math.Transformation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Opt-in TEST-only seal: five bounded packet displays, never added to the server world. */
final class SovereignSealPrototype {
    static final String PROPERTY = "cobbleraids.worldboss.sovereignPrototype";
    private static final int INTERPOLATION_TICKS = 8; // WorldBossService's border update interval.
    private record Scene(UUID boss, ServerLevel level, double x, double y, double z, double radius) {}
    private static Scene scene;
    private static Display.ItemDisplay[] layers = new Display.ItemDisplay[0];
    private record Viewer(ServerPlayer player, WorldBossBenefits.Effects effects) {}
    private static long lastParticleTick = Long.MIN_VALUE;
    private static long sceneStarted;
    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();

    private SovereignSealPrototype() {}

    static boolean enabled(WorldBossState state) {
        return state.testMode() && Boolean.getBoolean(PROPERTY);
    }

    static void tick(MinecraftServer server, WorldBossState state, WorldBossConfig config) {
        var dimension = ResourceLocation.tryParse(state.dimension());
        var level = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || !config.dome().enabled() || !enabled(state)) { clear(server); return; }
        var next = new Scene(state.bossId(), level, state.x(), state.y(), state.z(), WorldBossRules.zoneRadius(state, config));
        long tick = level.getGameTime();
        if (!next.equals(scene)) {
            clear(server);
            scene = next;
            sceneStarted = tick;
            try {
                var transforms = transforms(next.radius(), tick, 0);
                ItemStack[] items = {SovereignSealMaterial.band(), SovereignSealMaterial.crest(),
                        SovereignSealMaterial.halo(), SovereignSealMaterial.energy(), SovereignSealMaterial.ground()};
                layers = new Display.ItemDisplay[items.length];
                for (int i = 0; i < layers.length; i++) layers[i] = make(items[i], transforms[i], i >= 2);
            } catch (RuntimeException failure) {
                reset();
                throw failure;
            }
            RaidLog.info("Sovereign Seal prototype for TEST World Boss {}: band, crest, aureole, energy arcs and ritual circle, radius {}", state.bossId(), next.radius());
        }
        var transforms = transforms(scene.radius(), tick + INTERPOLATION_TICKS, tick - sceneStarted + INTERPOLATION_TICKS);
        var updates = new ClientboundSetEntityDataPacket[layers.length];
        // Consume each layer's dirty state once, then broadcast it to eligible existing viewers.
        for (int i = 0; i < layers.length; i++) {
            pose(layers[i], transforms[i]);
            var data = layers[i].getEntityData().packDirty();
            if (data != null) updates[i] = new ClientboundSetEntityDataPacket(layers[i].getId(), data);
        }
        var online = new HashSet<UUID>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID(); online.add(id);
            Viewer old = VIEWERS.get(id);
            if (old != null && old.player() != player) { VIEWERS.remove(id); old = null; }
            boolean eligible = player.level() == level && WorldBossService.audienceViews(player);
            if (!eligible) {
                if (old != null) remove(player);
                VIEWERS.remove(id);
                continue;
            }
            var effects = state.benefitsView().sceneOptions(id).effects();
            for (int i = 0; i < layers.length; i++) {
                boolean before = old != null && visible(i, old.effects());
                boolean after = visible(i, effects);
                if (before && !after) player.connection.send(new ClientboundRemoveEntitiesPacket(layers[i].getId()));
                else if (after && !before) add(player, layers[i]);
                else if (after && updates[i] != null) player.connection.send(updates[i]);
            }
            if (old == null || old.effects() != effects) VIEWERS.put(id, new Viewer(player, effects));
        }
        VIEWERS.keySet().retainAll(online);
        particles(state, tick);
    }

    static boolean visible(int layer, WorldBossBenefits.Effects effects) {
        return switch (layer) {
            case 0, 1 -> true;
            case 2 -> effects != WorldBossBenefits.Effects.OFF;
            case 3, 4 -> effects == WorldBossBenefits.Effects.FULL;
            default -> throw new IllegalArgumentException("Unknown Sovereign Seal layer");
        };
    }

    private static SovereignSealGeometry.Pose[] transforms(double radius, long tick, long age) {
        return new SovereignSealGeometry.Pose[]{SovereignSealGeometry.ring(radius, tick), SovereignSealGeometry.crest(radius, tick),
                SovereignSealGeometry.reveal(SovereignSealGeometry.halo(radius, tick), age), SovereignSealGeometry.energy(radius, tick),
                SovereignSealGeometry.reveal(SovereignSealGeometry.ground(radius, tick), age)};
    }

    private static void particles(WorldBossState state, long tick) {
        if (!SovereignSealEffects.due(tick, lastParticleTick) || VIEWERS.isEmpty()) return;
        lastParticleTick = tick;
        var full = SovereignSealEffects.frame(scene.radius(), tick, false);
        var minimal = SovereignSealEffects.frame(scene.radius(), tick, true);
        float size = full.getFirst().size();
        var gold = new DustParticleOptions(new Vector3f(1.0f, 0.68f, 0.16f), size);
        var scarlet = new DustParticleOptions(new Vector3f(0.8f, 0.04f, 0.12f), size);
        var viewers = java.util.List.copyOf(VIEWERS.values());
        int first = (int) Math.floorMod(tick / SovereignSealEffects.INTERVAL_TICKS, (long) viewers.size());
        int budget = SovereignSealEffects.PACKET_BUDGET;
        for (int i = 0; i < viewers.size() && budget > 0; i++) {
            var viewer = viewers.get((first + i) % viewers.size()).player();
            var effects = state.benefitsView().sceneOptions(viewer.getUUID()).effects();
            if (effects == WorldBossBenefits.Effects.OFF) continue;
            var points = effects == WorldBossBenefits.Effects.MINIMAL ? minimal : full;
            for (var point : points) {
                double x = scene.x() + point.x(), y = scene.y() + point.y(), z = scene.z() + point.z();
                if (viewer.distanceToSqr(x, y, z) > SovereignSealEffects.VIEW_DISTANCE * SovereignSealEffects.VIEW_DISTANCE) continue;
                scene.level().sendParticles(viewer, point.scarlet() ? scarlet : gold, true, x, y, z,
                        3, 0.8, 1.0, 0.8, 0);
                if (--budget == 0) break;
            }
        }
    }

    private static Display.ItemDisplay make(ItemStack item, SovereignSealGeometry.Pose transform, boolean emissive) {
        Display.ItemDisplay display = EntityType.ITEM_DISPLAY.create(scene.level());
        if (display == null) throw new IllegalStateException("Could not create prototype display");
        display.setItemStack(item);
        display.setItemTransform(ItemDisplayContext.NONE);
        if (emissive) display.setBrightnessOverride(Brightness.FULL_BRIGHT);
        display.setViewRange((float) Math.max(4, scene.radius() * 3 / 64));
        display.setWidth((float) Math.max(40, scene.radius() * 2.3));
        display.setHeight((float) (scene.radius() * 2 + 48));
        display.setPos(scene.x(), scene.y(), scene.z());
        display.setTransformationInterpolationDuration(INTERPOLATION_TICKS);
        pose(display, transform);
        display.getEntityData().packDirty();
        return display;
    }

    private static void pose(Display.ItemDisplay display, SovereignSealGeometry.Pose p) {
        display.setTransformation(new Transformation(new Vector3f((float) p.x(), (float) p.y(), (float) p.z()),
                new Quaternionf((float) p.qx(), (float) p.qy(), (float) p.qz(), (float) p.qw()),
                new Vector3f((float) p.scale()), new Quaternionf()));
        display.setTransformationInterpolationDelay(0); // Native setter force-marks the restart field dirty.
    }

    private static void add(ServerPlayer player, Display.ItemDisplay display) {
        player.connection.send(new ClientboundAddEntityPacket(display.getId(), display.getUUID(), display.getX(), display.getY(), display.getZ(),
                0, 0, display.getType(), 0, Vec3.ZERO, 0));
        var data = display.getEntityData().getNonDefaultValues();
        if (data != null) player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), data));
    }

    private static void remove(ServerPlayer player) {
        if (layers.length != 0) player.connection.send(new ClientboundRemoveEntitiesPacket(
                java.util.Arrays.stream(layers).filter(java.util.Objects::nonNull).mapToInt(Display.ItemDisplay::getId).toArray()));
    }

    static void clear(MinecraftServer server) {
        if (scene == null && VIEWERS.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var viewer = VIEWERS.get(player.getUUID());
            if (viewer != null && viewer.player() == player) remove(player);
        }
        reset();
    }

    static void reset() {
        VIEWERS.clear(); scene = null; layers = new Display.ItemDisplay[0];
        lastParticleTick = Long.MIN_VALUE;
        sceneStarted = 0;
    }
}
