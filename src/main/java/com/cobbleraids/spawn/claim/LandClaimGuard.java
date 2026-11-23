package com.cobbleraids.spawn.claim;

import com.cobbleraids.RaidLog;
import com.cobbleraids.config.CobbleRaidsConfig;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Says whether a boss spawn spot is in, or within the buffer of, a land claim. Inside a claim, players who are not trusted on it
 * cannot fight the boss, so wild raids and surface World Bosses are kept out of claims.
 *
 * <p>Flan is the supported claim mod. It is optional and reached by reflection, like the AscensionLib hooks, so this mod builds and
 * runs without it, and without it every spot is allowed. If Flan's API stops resolving or throws, the check logs once and switches
 * itself off rather than stopping every raid from spawning.
 */
public final class LandClaimGuard {

    private static final String FLAN_ID = "flan";
    private static final String CLAIM_STORAGE = "io.github.flemmli97.flan.claim.ClaimStorage";
    private static final String CLAIM = "io.github.flemmli97.flan.claim.Claim";

    private static boolean resolved;
    private static boolean broken;
    private static Method storageGet;
    private static Method claimsAt;
    private static Method intersects;

    private LandClaimGuard() {}

    /** True when a boss standing at {@code pos} would be in a claim or within the configured buffer of one. Server thread only. */
    public static boolean blocks(ServerLevel level, BlockPos pos, CobbleRaidsConfig.LandClaims config) {
        if (config == null || !config.avoid() || !resolve()) return false;
        ClaimFootprint footprint = ClaimFootprint.around(pos.getX(), pos.getZ(), config.bufferBlocks());
        // Full build height: a claim's own lower bound decides whether it reaches the boss, not this box.
        AABB box = new AABB(footprint.minX(), level.getMinBuildHeight(), footprint.minZ(),
                footprint.maxX() + 1, level.getMaxBuildHeight(), footprint.maxZ() + 1);
        try {
            Object storage = storageGet.invoke(null, level);
            if (storage == null) return false;
            for (int cx = footprint.minChunkX(); cx <= footprint.maxChunkX(); cx++) {
                for (int cz = footprint.minChunkZ(); cz <= footprint.maxChunkZ(); cz++) {
                    List<?> claims = (List<?>) claimsAt.invoke(storage, cx, cz);
                    if (claims == null) continue;
                    for (Object claim : claims) {
                        if (claim != null && Boolean.TRUE.equals(intersects.invoke(claim, box))) return true;
                    }
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            broken = true;
            RaidLog.error("Flan land-claim check failed; raids will spawn without checking claims until restart", ex);
            return false;
        }
    }

    /** Whether a supported claim mod is present and its API resolved. */
    public static boolean active() {
        return resolve();
    }

    private static boolean resolve() {
        if (broken) return false;
        if (resolved) return storageGet != null;
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded(FLAN_ID)) return false;
        try {
            Class<?> storageClass = Class.forName(CLAIM_STORAGE);
            Class<?> claimClass = Class.forName(CLAIM);
            Method get = storageClass.getMethod("get", ServerLevel.class);
            Method at = storageClass.getMethod("getClaimsAt", int.class, int.class);
            Method hit = claimClass.getMethod("intersects", AABB.class);
            storageGet = get;
            claimsAt = at;
            intersects = hit;
            RaidLog.info("Flan detected: raids and World Bosses will not spawn in land claims");
        } catch (ReflectiveOperationException | LinkageError ex) {
            RaidLog.warn("Flan is installed but its claim API did not resolve ({}); raids will spawn without checking claims", ex.toString());
        }
        return storageGet != null;
    }
}
