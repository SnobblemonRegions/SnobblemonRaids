package com.cobbleraids.worldboss;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Loaded-terrain checks and native pre-battle validation for the reserved technical footprint. */
final class WorldBossDuelPlacement {
    enum Validation { CLEAR, UNAVAILABLE, UNSUPPORTED_BODY }
    record Pick(Vec3 boss, WorldBossDuelSpace.Box lane) {}
    private WorldBossDuelPlacement() {}

    static Pick find(ServerPlayer player, WorldBossDuelSpace space) {
        if (player.isPassenger() || player.isVehicle() || !safe(player, player.position(), player.getBbWidth(), player.getBbHeight())) return null;
        Vec3 look = player.getLookAngle(); String dimension = player.serverLevel().dimension().location().toString();
        for (double[] point : WorldBossDuelSpot.candidates(player.getX(), player.getZ(), look.x, look.z)) {
                Vec3 boss = new Vec3(point[0], player.getY(), point[1]);
                var lane = WorldBossDuelSpace.lane(player.getX(), player.getY(), player.getZ(), boss.x, boss.y, boss.z);
                if (space.available(dimension, lane) && WorldBossArrivals.spaceAvailable(player.getUUID(), dimension, lane) && safe(player, boss, 4, 8) && clear(player, lane, null)) return new Pick(boss, lane);
        }
        return null;
    }

    static Validation validate(ServerPlayer player, WorldBossDuelSpace.Lease lease, Vec3 requested, PokemonEntity boss, float scale) {
        boss.getPokemon().setScaleModifier(scale); boss.refreshDimensions();
        if (!Float.isFinite(boss.getBbWidth()) || !Float.isFinite(boss.getBbHeight()) || boss.getBbWidth() <= 0 || boss.getBbWidth() > 4 || boss.getBbHeight() <= 0 || boss.getBbHeight() > 8) return Validation.UNSUPPORTED_BODY;
        if (lease == null || !lease.dimension().equals(player.serverLevel().dimension().location().toString()) || boss.level() != player.level()
                || player.isPassenger() || player.isVehicle() || requested.distanceToSqr(boss.position()) > 0.0001
                || Math.hypot(player.getX() - boss.getX(), player.getZ() - boss.getZ()) < WorldBossDuelSpot.NEAREST - 0.000001
                || !safe(player, player.position(), player.getBbWidth(), player.getBbHeight())
                || !safe(player, boss.position(), boss.getBbWidth(), boss.getBbHeight())) return Validation.UNAVAILABLE;
        var playerBox = player.getBoundingBox(); var bossBox = boss.getBoundingBox();
        return lease.box().contains(box(playerBox)) && lease.box().contains(box(bossBox)) && WorldBossArrivals.spaceAvailable(player.getUUID(), lease.dimension(), lease.box()) && clear(player, lease.box(), boss) ? Validation.CLEAR : Validation.UNAVAILABLE;
    }

    static Pick preview(ServerPlayer player, net.minecraft.server.level.ServerLevel level, Vec3 stand, Vec3 look, WorldBossDuelSpace space) {
        if (!safe(level, stand, player.getBbWidth(), player.getBbHeight())) return null;
        var point = WorldBossDuelSpot.candidates(stand.x, stand.z, look.x, look.z).getFirst();
        Vec3 boss = new Vec3(point[0], stand.y, point[1]);
        var lane = WorldBossDuelSpace.lane(stand.x, stand.y, stand.z, boss.x, boss.y, boss.z);
        String dimension = level.dimension().location().toString();
        return space.available(dimension, lane) && WorldBossArrivals.spaceAvailable(player.getUUID(), dimension, lane)
                && safe(level, boss, 4, 8) && clear(player, lane, null, level) ? new Pick(boss, lane) : null;
    }

    private static boolean safe(ServerPlayer player, Vec3 position, double width, double height) {
        return safe(player.serverLevel(), position, width, height);
    }
    private static boolean safe(net.minecraft.server.level.ServerLevel level, Vec3 position, double width, double height) {
        return WorldBossTravelService.safeLoaded(level, new WorldBossTravel.Position(level.dimension().location().toString(), position.x, position.y, position.z, 0, 0), width, height);
    }
    static boolean stillInside(ServerPlayer player, WorldBossDuelSpace.Lease lease, PokemonEntity boss) {
        try { return lease != null && boss != null && !boss.isRemoved() && player.isAlive() && !player.isSpectator()
                && !player.isPassenger() && !player.isVehicle() && boss.level() == player.level()
                && lease.dimension().equals(player.serverLevel().dimension().location().toString())
                && lease.box().contains(box(player.getBoundingBox())) && lease.box().contains(box(boss.getBoundingBox()))
                && Math.hypot(player.getX() - boss.getX(), player.getZ() - boss.getZ()) >= WorldBossDuelSpot.NEAREST - 0.000001;
        } catch (IllegalArgumentException invalidGeometry) { return false; }
    }
    private static WorldBossDuelSpace.Box box(AABB body) { return new WorldBossDuelSpace.Box(body.minX, body.minY, body.minZ, body.maxX, body.maxY, body.maxZ); }
    private static boolean clear(ServerPlayer player, WorldBossDuelSpace.Box lane, PokemonEntity boss) {
        return clear(player, lane, boss, player.serverLevel());
    }
    private static boolean clear(ServerPlayer player, WorldBossDuelSpace.Box lane, PokemonEntity boss, net.minecraft.server.level.ServerLevel level) {
        var area = new AABB(lane.minX(), lane.minY() + 0.01, lane.minZ(), lane.maxX(), lane.maxY() - 0.01, lane.maxZ());
        // Check loaded footprint before collision/terrain queries, including at negative chunk edges.
        for (int x = (int) Math.floor(area.minX / 16); x <= (int) Math.floor(area.maxX / 16); x++)
            for (int z = (int) Math.floor(area.minZ / 16); z <= (int) Math.floor(area.maxZ / 16); z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        if (level.getBlockCollisions(null, area).iterator().hasNext()) return false;
        for (int x = (int) Math.floor(area.minX); x <= (int) Math.floor(area.maxX); x++) {
            for (int z = (int) Math.floor(area.minZ); z <= (int) Math.floor(area.maxZ); z++) {
                double minX = Math.max(area.minX, x) + 0.01, minZ = Math.max(area.minZ, z) + 0.01;
                double maxX = Math.min(area.maxX, x + 1.0) - 0.01, maxZ = Math.min(area.maxZ, z + 1.0) - 0.01;
                if (minX >= maxX || minZ >= maxZ) continue;
                var support = new AABB(minX, lane.minY() - 0.1, minZ, maxX, lane.minY(), maxZ);
                if (!level.getBlockCollisions(null, support).iterator().hasNext()) return false;
            }
        }
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ), BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
            if (!level.getFluidState(pos).isEmpty()) return false;
        }
        return level.getEntities(player, area, entity -> entity != boss && entity.isAlive() && (entity instanceof LivingEntity || entity.canBeCollidedWith())
                && !(entity instanceof Player visitor && visitor.isSpectator())
                && !(entity instanceof PokemonEntity pokemon && player.getUUID().equals(pokemon.getOwnerUUID()))).isEmpty();
    }
}
