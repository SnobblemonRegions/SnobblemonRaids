package com.cobbleraids.mixin.battle;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.raid.RaidTeleportLock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player in a raid cannot be teleported out of it.
 *
 * <p>Sits on ServerPlayer's own teleport and dimension methods rather than on any one command, so
 * /tp, home and warp mods, /back, portals and pearls are all refused without naming them. Fails open
 * on a fault: a player who can teleport is the old behaviour, a thrown exception is worse.
 */
@Mixin(ServerPlayer.class)
public abstract class RaidPlayerTeleportLockMixin {

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$lockLevelTeleport(net.minecraft.server.level.ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        try {
            if (cobbleRaids$refuse() || !com.cobbleraids.worldboss.WorldBossService.allowArenaTeleport((ServerPlayer) (Object) this, level, x, y, z)) ci.cancel();
        } catch (Exception ex) {
            RaidFaultBarrier.report("mixin:teleport-lock", ex);
        }
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$lockPositionTeleport(double x, double y, double z, CallbackInfo ci) {
        try {
            var player = (ServerPlayer) (Object) this;
            if (cobbleRaids$refuse() || !com.cobbleraids.worldboss.WorldBossService.allowArenaTeleport(player, player.serverLevel(), x, y, z)) ci.cancel();
        } catch (Exception ex) {
            RaidFaultBarrier.report("mixin:teleport-lock", ex);
        }
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$lockRelativeTeleport(net.minecraft.server.level.ServerLevel level, double x, double y, double z, java.util.Set<net.minecraft.world.entity.RelativeMovement> relative, float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        try {
            var player = (ServerPlayer) (Object) this;
            // Coordinates are absolute; relative flags only encode the outbound client packet.
            if (cobbleRaids$refuse() || !com.cobbleraids.worldboss.WorldBossService.allowArenaTeleport(player, level, x, y, z)) cir.setReturnValue(false);
        } catch (Exception ex) {
            RaidFaultBarrier.report("mixin:teleport-lock", ex);
        }
    }

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$lockDimensionChange(net.minecraft.world.level.portal.DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        try {
            if (cobbleRaids$refuse() || !com.cobbleraids.worldboss.WorldBossService.allowArenaTeleport((ServerPlayer) (Object) this, transition.newLevel(), transition.pos().x, transition.pos().y, transition.pos().z)) cir.setReturnValue(null);
        } catch (Exception ex) {
            RaidFaultBarrier.report("mixin:teleport-lock", ex);
        }
    }

    private boolean cobbleRaids$refuse() {
        com.cobbleraids.worldboss.WorldBossService.invalidateWardAction(((ServerPlayer) (Object) this).getUUID());
        return RaidTeleportLock.refuse((ServerPlayer) (Object) this);
    }
}
