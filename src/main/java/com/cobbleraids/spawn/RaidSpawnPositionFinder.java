package com.cobbleraids.spawn;

import com.cobbleraids.config.CobbleRaidsConfig;
import com.cobbleraids.spawn.claim.LandClaimGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Finds a loaded-chunk surface position around a player without touching Cobblemon's normal spawn pools. A spot must be loaded,
 * inside the world border, within reach of the player's height, clear dry solid ground, natural ground (no roofs, farms or paths;
 * the {@link #NATURAL_GROUND} block tag) and outside any land claim. Every rejection is counted, so the spawn history can say why
 * a search found nothing.
 */
public final class RaidSpawnPositionFinder {
    /** Blocks a wild raid may stand on when natural_ground_only is set. A datapack can add to it. */
    public static final TagKey<Block> NATURAL_GROUND =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("cobbleraids", "raid_spawn_ground"));

    /** How far from a spot that was too cramped for the chosen boss to look for one it fits, and how many tries. */
    private static final int ROOM_RADIUS = 6;
    private static final int ROOM_ATTEMPTS = 12;

    private RaidSpawnPositionFinder() {}

    /** The spot found, if any, and why each rejected spot was turned down. */
    public record Search(Optional<BlockPos> position, EnumMap<RaidSpawnRules.Rejection, Integer> rejections) {}

    public static Search findLand(ServerLevel level, ServerPlayer player, CobbleRaidsConfig.NaturalSpawning config,
                                  CobbleRaidsConfig.LandClaims claims) {
        BlockPos center = player.blockPosition();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double min = config.minDistanceFromPlayer();
        double max = config.maxDistanceFromPlayer();
        EnumMap<RaidSpawnRules.Rejection, Integer> rejections = new EnumMap<>(RaidSpawnRules.Rejection.class);

        for (int attempt = 0; attempt < config.locationAttempts(); attempt++) {
            double angle = random.nextDouble(0.0, Math.PI * 2.0);
            // Area-uniform annulus sampling rather than clustering all candidates near the minimum radius.
            double radius = Math.sqrt(random.nextDouble(min * min, max * max));
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            RaidSpawnRules.Rejection rejection = check(level, player, x, z, config, claims);
            if (rejection == null) return new Search(Optional.of(surface(level, x, z)), rejections);
            rejections.merge(rejection, 1, Integer::sum);
        }
        return new Search(Optional.empty(), rejections);
    }

    /**
     * A spot near {@code near} that passes every rule and where a boss of {@code size} fits without touching a block, or empty.
     * {@code near} itself is tried first.
     */
    public static Optional<BlockPos> roomFor(ServerLevel level, ServerPlayer player, BlockPos near, EntityDimensions size,
                                             CobbleRaidsConfig.NaturalSpawning config, CobbleRaidsConfig.LandClaims claims) {
        if (fits(level, near, size)) return Optional.of(near);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < ROOM_ATTEMPTS; attempt++) {
            int x = near.getX() + random.nextInt(-ROOM_RADIUS, ROOM_RADIUS + 1);
            int z = near.getZ() + random.nextInt(-ROOM_RADIUS, ROOM_RADIUS + 1);
            double distance = Math.hypot(x - player.getX(), z - player.getZ());
            if (distance < config.minDistanceFromPlayer() || distance > config.maxDistanceFromPlayer()) continue;
            if (check(level, player, x, z, config, claims) != null) continue;
            BlockPos candidate = surface(level, x, z);
            if (fits(level, candidate, size)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** Whether a boss of {@code size}, standing at the bottom centre of {@code pos}, overlaps no block, entity or the border. */
    public static boolean fits(ServerLevel level, BlockPos pos, EntityDimensions size) {
        if (size == null) return true;
        // Shrunk a hair so a boss resting exactly on the ground, or touching a wall face, does not count as colliding with it.
        AABB box = size.makeBoundingBox(Vec3.atBottomCenterOf(pos)).deflate(1.0e-3);
        return level.noCollision(box);
    }

    /** Null when the column at (x, z) is a valid spot, otherwise the first rule it fails. */
    private static RaidSpawnRules.Rejection check(ServerLevel level, ServerPlayer player, int x, int z,
                                                  CobbleRaidsConfig.NaturalSpawning config, CobbleRaidsConfig.LandClaims claims) {
        if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
            return RaidSpawnRules.Rejection.UNLOADED;
        }
        BlockPos candidate = surface(level, x, z);
        if (!level.getWorldBorder().isWithinBounds(candidate)) return RaidSpawnRules.Rejection.WORLD_BORDER;
        if (!RaidSpawnRules.heightOk(candidate.getY(), player.getY(), config.maxHeightDifference())) {
            return RaidSpawnRules.Rejection.HEIGHT;
        }
        if (!isSimpleSurfaceSpawn(level, candidate)) return RaidSpawnRules.Rejection.NOT_SIMPLE_SURFACE;
        if (config.naturalGroundOnly() && !level.getBlockState(candidate.below()).is(NATURAL_GROUND)) {
            return RaidSpawnRules.Rejection.NOT_NATURAL_GROUND;
        }
        // Inside a claim, players not trusted on it cannot fight the boss; the next attempt may land outside it.
        if (LandClaimGuard.blocks(level, candidate, claims)) return RaidSpawnRules.Rejection.LAND_CLAIM;
        return null;
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static boolean isSimpleSurfaceSpawn(ServerLevel level, BlockPos pos) {
        BlockPos groundPos = pos.below();
        BlockPos headPos = pos.above();
        var ground = level.getBlockState(groundPos);
        var feet = level.getBlockState(pos);
        var head = level.getBlockState(headPos);

        if (ground.isAir() || ground.getCollisionShape(level, groundPos).isEmpty()) return false;
        if (!feet.getFluidState().isEmpty() || !head.getFluidState().isEmpty()) return false;
        if (!feet.getCollisionShape(level, pos).isEmpty()) return false;
        if (!head.getCollisionShape(level, headPos).isEmpty()) return false;
        return true;
    }
}
