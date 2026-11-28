package com.cobbleraids.radar;

import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnGenericBedrockPacket;
import com.cobbleraids.item.RaidRadarItem;
import com.cobbleraids.spawn.RaidSpawnScheduler;
import com.mojang.math.Transformation;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A viewer-only projector and readout. Their entities are never added to the server world. */
final class RaidProjector {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("cobbleraids", "radar_projector");
    private static final int SIDE = ProjectorContours.SIDE;
    // A zero-sized box prevents Cobblemon from drawing the model. Keep the pick target small.
    private static final float COLLIDER_SIZE = 0.095f;
    private static final float MODEL_SCALE = 1.23f;
    private static final double MODEL_UNITS_PER_BLOCK = ProjectorContours.STEP / 32;
    private static final double MARKER_RADIUS = 5.5;
    private final ServerPlayer owner;
    private final InteractionHand hand;
    private final MapId mapId;
    private GenericBedrockEntity entity;
    private Display.ItemDisplay carrier;
    private FollowPose pose;
    private Display.TextDisplay readout;
    private int terrainRevision = -1;
    private int[] lastBands;
    private List<ProjectorContours.Segment> lastContours = List.of();
    private List<String> lastMarkers = List.of();

    RaidProjector(ServerPlayer owner, InteractionHand hand, MapId mapId) {
        this.owner = owner;
        this.hand = hand;
        this.mapId = mapId;
    }

    ItemStack held() {
        return owner.getItemInHand(hand);
    }

    static boolean canShow(ServerPlayer player) {
        return PolymerResourcePackUtils.hasMainPack(player)
                && ServerPlayNetworking.canSend(player, SpawnGenericBedrockPacket.Companion.getID())
                && ServerPlayNetworking.canSend(player, PlayPosableAnimationPacket.Companion.getID());
    }

    boolean valid(ServerPlayer player) {
        return player == owner && owner.isAlive() && !owner.isSpectator()
                && held().is(RaidRadarItem.INSTANCE) && mapId.equals(held().get(DataComponents.MAP_ID))
                && canShow(owner);
    }

    void send(int revision, int[] terrain, int centerX, int centerZ,
              RaidSpawnScheduler.RadarContact target, String species, String distance, String tier) {
        var values = new ArrayList<String>();
        if (entity == null) {
            pose = FollowPose.of(owner);
            carrier = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, owner.serverLevel());
            carrier.setPosRotInterpolationDuration(2);
            carrier.setPos(pose.origin());
            spawnDisplay(carrier);

            entity = new GenericBedrockEntity(owner.serverLevel());
            entity.setPos(pose.origin());
            Vec3 at = pose.origin();
            var spawn = new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(),
                    at.x, at.y, at.z, 0, 0, entity.getType(), 0, Vec3.ZERO, 0);
            new SpawnGenericBedrockPacket(MODEL, Set.of(), PoseType.NONE, MODEL_SCALE, COLLIDER_SIZE, COLLIDER_SIZE, 0, spawn)
                    .sendToPlayer(owner);
            // Cobblemon's generic entity snaps to packet coordinates. The display interpolates,
            // and the passenger follows the display's position on the client.
            entity.startRiding(carrier, true);
            owner.connection.send(new ClientboundSetPassengersPacket(carrier));
            values.add(pose.orientation());
        }
        showReadout(target, species, distance, tier);
        if (revision != terrainRevision) {
            int[] bands = terrainBands(terrain);
            for (int i = 0; i < bands.length; i++) {
                if (lastBands == null || lastBands[i] != bands[i]) values.add("v.h" + i + "=" + bands[i] + ";");
            }
            if (!Arrays.equals(lastBands, bands)) updateContours(values, ProjectorContours.build(bands));
            lastBands = bands;
            terrainRevision = revision;
        }

        Vec3 you = point(owner.position(), centerX, centerZ);
        Vec3 raid = target == null ? you : clipBearing(you, point(target.position(), centerX, centerZ));
        var markers = new ArrayList<String>();
        putPoint(markers, "you", you, lastBands);
        putPoint(markers, "raid", raid, lastBands);
        markers.add("v.target=" + (target == null ? 0 : 1) + ";");
        if (!markers.equals(lastMarkers)) {
            values.addAll(markers);
            lastMarkers = markers;
        }
        if (!values.isEmpty()) new PlayPosableAnimationPacket(entity.getId(), Set.of(), values).sendToPlayer(owner);
    }

    private void showReadout(RaidSpawnScheduler.RadarContact target, String species, String distance, String tier) {
        Component title = target == null
                ? Component.literal("NO ACTIVE RAID").withColor(0xADCBD4)
                : Component.literal(species.toUpperCase(Locale.ROOT)).withColor(0xF5FBFF).append(
                        Component.literal("  " + (target.lastKnown() ? "~" : "") + distance).withColor(0xFFCA6D));
        var text = Component.empty().append(title).append("\n")
                .append(Component.literal("● YOU").withColor(0xD9FEFF));
        if (target != null) {
            text.append("    ").append(Component.literal("◆ RAID").withColor(0xFFCA6D));
            if (!tier.isBlank()) text.append(Component.literal("   " + tier).withColor(0x8AA9BB));
        }

        boolean spawn = readout == null;
        if (spawn) {
            readout = new Display.TextDisplay(EntityType.TEXT_DISPLAY, owner.serverLevel());
            readout.setTransformation(new Transformation(null, null, new Vector3f(0.21f), null));
            readout.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            readout.setBrightnessOverride(new Brightness(15, 15));
            readout.setBackgroundColor(0xF0162632);
            readout.setWidth(1.5f);
            readout.setHeight(0.45f);
            readout.setLineWidth(220);
            readout.setPosRotInterpolationDuration(2);
            readout.setPos(pose.readout());
        }
        if (!spawn && readout.getText().equals(text)) return;
        readout.setText(text);
        if (spawn) spawnDisplay(readout);
        else sendDisplayData(readout);
    }

    private void spawnDisplay(Display display) {
        owner.connection.send(new ClientboundAddEntityPacket(display.getId(), display.getUUID(),
                display.getX(), display.getY(), display.getZ(), 0, 0, display.getType(), 0, Vec3.ZERO, 0));
        sendDisplayData(display);
    }

    private void sendDisplayData(Display display) {
        var data = display.getEntityData().getNonDefaultValues();
        if (data != null) owner.connection.send(new ClientboundSetEntityDataPacket(display.getId(), data));
    }

    void follow() {
        if (entity == null || readout == null || !valid(owner) || owner.serverLevel() != entity.level()) return;
        FollowPose next = FollowPose.of(owner);
        boolean turned = Math.abs(Mth.wrapDegrees(next.yaw() - pose.yaw())) >= 2
                || Math.abs(next.pitch() - pose.pitch()) >= 2;
        if (!turned && next.eye().distanceToSqr(pose.eye()) < 0.035 * 0.035) return;

        String oldOrientation = pose.orientation();
        pose = next;
        carrier.setPos(pose.origin());
        readout.setPos(pose.readout());
        owner.connection.send(new ClientboundBundlePacket(List.of(
                new ClientboundTeleportEntityPacket(carrier), new ClientboundTeleportEntityPacket(readout))));
        String orientation = pose.orientation();
        if (!orientation.equals(oldOrientation)) {
            new PlayPosableAnimationPacket(entity.getId(), Set.of(), List.of(orientation)).sendToPlayer(owner);
        }
    }

    void recenterTerrain() {
        terrainRevision = -1;
    }

    private record FollowPose(Vec3 eye, Vec3 origin, Vec3 readout, float yaw, float pitch) {
        static FollowPose of(ServerPlayer player) {
            float yaw = Mth.wrapDegrees(player.getYRot());
            float pitch = Mth.clamp(player.getXRot(), -80, 80);
            Vec3 forward = Vec3.directionFromRotation(0, yaw);
            Vec3 eye = player.getEyePosition();
            double horizontal = Math.cos(Math.toRadians(pitch));
            Vec3 look = Vec3.directionFromRotation(pitch, yaw);
            Vec3 origin = eye.add(look.scale(1.66)).add(0, -0.35 * horizontal, 0);
            // Bring the readout forward on steep views so the terrain bezel does not cover it.
            double clearance = Math.min(0.22, Math.abs(pitch - 14) * 0.011);
            Vec3 readout = origin.subtract(forward.scale(0.60 * horizontal))
                    .subtract(look.scale(clearance)).add(0, 0.19 - clearance * 0.22, 0);
            return new FollowPose(eye, origin, readout, yaw, pitch);
        }

        String orientation() {
            int heading = Math.round(Mth.wrapDegrees(180 - yaw) / 2) * 2;
            int tilt = Math.round(Mth.clamp(pitch - 43, -80, -5) / 2) * 2;
            return "v.survey_heading=" + heading + ";v.survey_tilt=" + tilt + ";";
        }
    }

    private void updateContours(List<String> values, List<ProjectorContours.Segment> contours) {
        for (int i = 0; i < Math.max(contours.size(), lastContours.size()); i++) {
            String key = "v.c" + i;
            if (i >= contours.size()) {
                values.add(key + "l=0;");
                continue;
            }
            ProjectorContours.Segment segment = contours.get(i);
            if (i < lastContours.size() && segment.equals(lastContours.get(i))) continue;
            values.add(key + "x=" + precision(segment.x()) + ";" + key + "z=" + precision(segment.z()) + ";"
                    + key + "y=" + precision(0.32 + segment.elevation() * 0.16) + ";"
                    + key + "r=" + precision(segment.rotation()) + ";"
                    + key + "l=" + precision(segment.length() + 0.055) + ";");
        }
        lastContours = contours;
    }

    private static double precision(double value) {
        return Math.round(value * 64) / 64.0;
    }

    private static int[] terrainBands(int[] terrain) {
        int[] bands = new int[SIDE * SIDE];
        int min = Arrays.stream(terrain).filter(height -> height != RadarRenderer.UNKNOWN).min().orElse(0);
        int max = Arrays.stream(terrain).max().orElse(min);
        for (int row = 0; row < SIDE; row++) {
            for (int column = 0; column < SIDE; column++) {
                int sample = terrain[(row * 2 + 1) * RadarRenderer.GRID + column * 2 + 1];
                if (sample != RadarRenderer.UNKNOWN && ProjectorContours.inside(column, row)) {
                    bands[row * SIDE + column] = 1 + (sample - min) * 11 / Math.max(16, max - min);
                }
            }
        }
        return bands;
    }

    private static Vec3 point(Vec3 world, int centerX, int centerZ) {
        return new Vec3((world.x - centerX) * MODEL_UNITS_PER_BLOCK, 0, (world.z - centerZ) * MODEL_UNITS_PER_BLOCK);
    }

    private static Vec3 clipBearing(Vec3 from, Vec3 to) {
        if (to.lengthSqr() <= MARKER_RADIUS * MARKER_RADIUS) return to;
        Vec3 direction = to.subtract(from);
        double a = direction.lengthSqr();
        if (a == 0) return from;
        double b = from.dot(direction);
        double c = from.lengthSqr() - MARKER_RADIUS * MARKER_RADIUS;
        return from.add(direction.scale((-b + Math.sqrt(Math.max(0, b * b - a * c))) / a));
    }

    private static void putPoint(List<String> values, String name, Vec3 point, int[] bands) {
        double step = ProjectorContours.STEP;
        int column = Math.clamp((int) Math.round(point.x / step + 7.5), 0, SIDE - 1);
        int row = Math.clamp((int) Math.round(point.z / step + 7.5), 0, SIDE - 1);
        values.add("v." + name + "_x=" + precision(point.x) + ";");
        values.add("v." + name + "_z=" + precision(point.z) + ";");
        values.add("v." + name + "_surface=" + precision(bands[row * SIDE + column] * 0.095) + ";");
    }

    void close() {
        if (readout != null) {
            owner.connection.send(new ClientboundRemoveEntitiesPacket(entity.getId(), readout.getId(), carrier.getId()));
        } else if (entity != null) {
            owner.connection.send(new ClientboundRemoveEntitiesPacket(entity.getId(), carrier.getId()));
        }
    }
}
