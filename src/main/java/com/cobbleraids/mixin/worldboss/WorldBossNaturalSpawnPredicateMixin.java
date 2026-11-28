package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossNaturalSpawnEvaluation;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject only Pokémon candidates in the scoped protected player-spawner matching pass. */
@Mixin(SpawnDetail.class)
public abstract class WorldBossNaturalSpawnPredicateMixin {
    @Inject(method = "isSatisfiedBy", at = @At("HEAD"), cancellable = true, remap = false)
    private void cobbleRaids$rejectProtectedNaturalPokemon(SpawnablePosition position, CallbackInfoReturnable<Boolean> result) {
        try {
            if (((Object) this instanceof PokemonSpawnDetail || (Object) this instanceof PokemonHerdSpawnDetail)
                    && WorldBossNaturalSpawnEvaluation.blocks(position)) result.setReturnValue(false);
        } catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:spawn-predicate", failure); }
    }
}
