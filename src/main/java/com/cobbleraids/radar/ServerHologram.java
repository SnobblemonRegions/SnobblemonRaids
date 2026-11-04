package com.cobbleraids.radar;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * One player's server-side radar hologram: a small north-up model of the terrain that floats in front of them, built from vanilla display entities
 * that only this player is sent. No client mod, and no packets from the client.
 *
 * <p>Cost: one root entity follows the player (about ten position updates a second); every terrain column, marker and label is a passenger of that
 * root, so the client carries them along and the server sends them nothing while the player moves. Columns only produce traffic when the terrain
 * snapshot changes (every ten seconds at most). Server thread only.
 */
final class ServerHologram {
    private static final BlockState[] RAMP = {
            Blocks.BLUE_STAINED_GLASS.defaultBlockState(), Blocks.CYAN_STAINED_GLASS.defaultBlockState(),
            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState(),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState()};
    private static final int GLIDE_TICKS = 3, TERRAIN_GLIDE_TICKS = 10;

    private final ServerLevel level;
    private final Rig holder = new Rig();
    private final ItemDisplayElement root = new ItemDisplayElement();
    private final List<BlockDisplayElement> tiles = new ArrayList<>();
    private final TextDisplayElement label = new TextDisplayElement();
    private BlockDisplayElement raid;
    private final BlockDisplayElement you = marker(Blocks.WHITE_CONCRETE, 0xFFFFFF);
    private Vec3 anchor;
    private int appliedRevision = -1;
    private String shownLabel = "";

    /** The holder, which also tells a player who starts watching that the rest ride the root. */
    private final class Rig extends ElementHolder {
        @Override
        protected void startWatchingExtraPackets(ServerGamePacketListenerImpl player, Consumer<Packet<ClientGamePacketListener>> out) {
            out.accept(VirtualEntityUtils.createRidePacket(root.getEntityId(), passengers()));
        }
    }

    /**
     * Where the model's origin is: the {@link #anchor}, which {@link #follow} moves. Polymer's own {@code ManualAttachment} cannot be used: it is a record
     * that registers itself on the holder before its fields are assigned, so its position supplier is still null when Polymer asks for the starting position.
     */
    private final class Anchor implements HolderAttachment {
        @Override public ElementHolder holder() { return holder; }
        @Override public void destroy() { if (holder.getAttachment() == this) holder.setAttachment(null); }
        @Override public Vec3 getPos() { return anchor; }
        @Override public ServerLevel getWorld() { return level; }
        @Override public void updateCurrentlyTracking(Collection<ServerGamePacketListenerImpl> currentlyTracking) {}
        @Override public void updateTracking(ServerGamePacketListenerImpl tracking) {}
    }

    ServerHologram(ServerPlayer player) {
        this.level = player.serverLevel();
        this.anchor = anchorOf(player);
        root.setTeleportDuration(GLIDE_TICKS);
        holder.addElement(root);
        label.setBillboardMode(Display.BillboardConstraints.CENTER);
        label.setBackground(0x66000000);
        label.setScale(new Vector3f(0.16f, 0.16f, 0.16f));
        label.setTranslation(new Vector3f(0f, (float) (HologramLayout.MAX_RELIEF + 0.30), 0f));
        label.setBrightness(Brightness.FULL_BRIGHT);
        label.ignorePositionUpdates();
        holder.addElement(label);
        you.setTranslation(new Vector3f(0f, (float) (HologramLayout.MAX_RELIEF + 0.03), 0f));
        holder.addElement(you);
        holder.setAttachment(new Anchor());
        holder.startWatching(player);
    }

    ServerLevel level() {
        return level;
    }

    private static BlockDisplayElement marker(Block block, int glow) {
        BlockDisplayElement element = new BlockDisplayElement(block.defaultBlockState());
        element.ignorePositionUpdates();
        element.setBrightness(Brightness.FULL_BRIGHT);
        // A coloured outline keeps the model readable against any ground: translucent glass alone blends into stone.
        element.setGlowing(true);
        element.setGlowColorOverride(glow);
        element.setInterpolationDuration(GLIDE_TICKS * 2);
        return element;
    }

    /** Just above the player's line of sight and a little over a metre ahead, wherever they look (the model itself stays north-up). */
    private static Vec3 anchorOf(ServerPlayer player) {
        HologramLayout.Point at = HologramLayout.anchor(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
        return new Vec3(at.x(), at.y(), at.z());
    }

    private IntArrayList passengers() {
        IntArrayList ids = new IntArrayList();
        for (BlockDisplayElement tile : tiles) ids.add(tile.getEntityId());
        ids.add(label.getEntityId());
        ids.add(you.getEntityId());
        if (raid != null) ids.add(raid.getEntityId());
        return ids;
    }

    /** Applies a terrain snapshot: only when its revision changed, so a quiet radar sends nothing. */
    void showTerrain(int[] heights, int revision) {
        if (revision == appliedRevision) return;
        appliedRevision = revision;
        HologramPlan plan = HologramPlan.of(heights);
        List<HologramPlan.Column> columns = plan.columns();
        boolean structural = false;
        while (tiles.size() < columns.size()) {
            BlockDisplayElement tile = marker(Blocks.BLUE_STAINED_GLASS, 0x35E6FF);
            tile.setInterpolationDuration(TERRAIN_GLIDE_TICKS);
            holder.addElement(tile);
            tiles.add(tile);
            structural = true;
        }
        while (tiles.size() > columns.size()) {
            holder.removeElement(tiles.remove(tiles.size() - 1));
            structural = true;
        }
        for (int i = 0; i < columns.size(); i++) {
            HologramLayout.Box box = HologramLayout.column(columns.get(i), plan.bands());
            BlockDisplayElement tile = tiles.get(i);
            tile.setBlockState(RAMP[HologramLayout.shade(columns.get(i).band(), plan.bands())]);
            tile.setScale(new Vector3f((float) box.sizeX(), (float) box.sizeY(), (float) box.sizeZ()));
            tile.setTranslation(new Vector3f((float) (box.centerX() - box.sizeX() / 2), 0f, (float) (box.centerZ() - box.sizeZ() / 2)));
            tile.startInterpolationIfDirty();
        }
        if (structural) resendRiders();
    }

    /** The player's own marker, the raid's marker (or none), and the label. Offsets are in blocks from the view centre, east and south positive. */
    void showMarkers(double playerDx, double playerDz, boolean hasTarget, double targetDx, double targetDz, String text) {
        place(you, HologramLayout.marker(playerDx, playerDz), 0.05f, 0.03f);
        if (hasTarget) {
            if (raid == null) {
                raid = marker(Blocks.ORANGE_CONCRETE, 0xFF8A1F);
                holder.addElement(raid);
                resendRiders();
            }
            place(raid, HologramLayout.marker(targetDx, targetDz), 0.22f, 0.04f);
        } else if (raid != null) {
            holder.removeElement(raid);
            raid = null;
            resendRiders();
        }
        if (!text.equals(shownLabel)) {
            shownLabel = text;
            label.setText(Component.literal(text));
        }
    }

    private void place(BlockDisplayElement element, HologramLayout.Marker marker, float height, float width) {
        element.setScale(new Vector3f(width, height, width));
        element.setTranslation(new Vector3f((float) marker.x() - width / 2, (float) (HologramLayout.MAX_RELIEF + 0.02), (float) marker.z() - width / 2));
        element.startInterpolationIfDirty();
    }

    private void resendRiders() {
        holder.sendPacket(VirtualEntityUtils.createRidePacket(root.getEntityId(), passengers()));
    }

    /** Moves the root in front of the player and sends whatever changed. Called every other tick. */
    void follow(ServerPlayer player) {
        anchor = anchorOf(player);
        holder.tick();
    }

    void close() {
        holder.destroy();
    }
}
