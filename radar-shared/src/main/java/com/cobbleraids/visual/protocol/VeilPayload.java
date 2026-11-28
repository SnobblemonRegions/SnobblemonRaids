package com.cobbleraids.visual.protocol;

import java.util.UUID;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded presentation state only; never accepts client combat or reward instructions. */
public record VeilPayload(long sequence, boolean active, UUID scene, String dimension,
        double x, double y, double z, float radius, float bossScale, int entityId, UUID entityUuid, int detail,
        long ageTicks) implements CustomPacketPayload {
    public static final Type<VeilPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobbleraids", "distortion_veil_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VeilPayload> CODEC = StreamCodec.of(
            (b, p) -> p.write(b), VeilPayload::read);
    public VeilPayload {
        Objects.requireNonNull(scene); Objects.requireNonNull(entityUuid);
        if (sequence < 0 || ageTicks < 0 || detail < 0 || detail > 2 || dimension.length() > 96
                || ResourceLocation.tryParse(dimension) == null
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 4096
                || !Float.isFinite(bossScale) || bossScale < .1f || bossScale > 30
                || !Float.isFinite(radius) || radius < 1 || radius > 1024) {
            throw new IllegalArgumentException("Invalid Distortion Veil scene");
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeVarLong(sequence); b.writeBoolean(active); b.writeUUID(scene); b.writeUtf(dimension, 96);
        b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeFloat(radius); b.writeFloat(bossScale);
        b.writeInt(entityId); b.writeUUID(entityUuid); b.writeByte(detail); b.writeVarLong(ageTicks);
    }
    private static VeilPayload read(RegistryFriendlyByteBuf b) {
        return new VeilPayload(b.readVarLong(), b.readBoolean(), b.readUUID(), b.readUtf(96),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readInt(), b.readUUID(),
                b.readUnsignedByte(), b.readVarLong());
    }
}
