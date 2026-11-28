package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossArenaProtection;
import com.cobbleraids.worldboss.WorldBossNaturalSpawnEvaluation;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;

/** Resolve protection once per natural player-spawn candidate, not once per biome/pool entry. */
@Mixin(BasicSpawner.class)
public abstract class WorldBossNaturalSpawnMatchingMixin {
    @WrapMethod(method = "getMatchingSpawns", remap = false)
    private List<SpawnDetail> cobbleRaids$matchOutsideArena(String bucket, SpawnablePosition position, Operation<List<SpawnDetail>> original) {
        boolean protectedCell = false;
        try {
            protectedCell = position.getSpawner() instanceof PlayerSpawner
                    && WorldBossArenaProtection.protectsNaturalSpawnCell(position.getWorld(), position.getPosition());
        } catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:spawn-cell", failure); }
        var scope = WorldBossNaturalSpawnEvaluation.open(position, protectedCell);
        // Native matching exceptions propagate normally; never retry or execute the original twice.
        try { return original.call(bucket, position); }
        finally {
            try { if (scope.suppressed()) WorldBossArenaProtection.recordSkippedNaturalSpawnEvaluation(); }
            finally { scope.close(); }
        }
    }
}
