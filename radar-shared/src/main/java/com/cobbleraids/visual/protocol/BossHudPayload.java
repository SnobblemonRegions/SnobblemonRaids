package com.cobbleraids.visual.protocol;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owns one specific native boss event. Other mods' bars never match by name or colour. */
public record BossHudPayload(long sequence, boolean active, UUID bar, String dimension,
        String title, String status, double current, double maximum, int detail, boolean preparing) implements CustomPacketPayload {
    public static final Type<BossHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobbleraids", "worldboss_hud_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossHudPayload> CODEC = StreamCodec.of((b,p) -> p.write(b), BossHudPayload::read);
    public BossHudPayload {
        Objects.requireNonNull(bar); Objects.requireNonNull(dimension); Objects.requireNonNull(title); Objects.requireNonNull(status);
        if (sequence < 0 || dimension.length() > 96 || ResourceLocation.tryParse(dimension) == null
                || title.length() > 128 || status.length() > 160 || detail < 0 || detail > 2
                || !Double.isFinite(current) || !Double.isFinite(maximum) || current < 0 || maximum < 0 || current > maximum)
            throw new IllegalArgumentException("Invalid World Boss HUD state");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeVarLong(sequence); b.writeBoolean(active); b.writeUUID(bar); b.writeUtf(dimension,96);
        b.writeUtf(title,128); b.writeUtf(status,160); b.writeDouble(current); b.writeDouble(maximum); b.writeByte(detail); b.writeBoolean(preparing);
    }
    private static BossHudPayload read(RegistryFriendlyByteBuf b) {
        return new BossHudPayload(b.readVarLong(),b.readBoolean(),b.readUUID(),b.readUtf(96),b.readUtf(128),b.readUtf(160),
                b.readDouble(),b.readDouble(),b.readUnsignedByte(),b.readBoolean());
    }
}
