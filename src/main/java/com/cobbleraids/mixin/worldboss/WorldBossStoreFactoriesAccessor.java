package com.cobbleraids.mixin.worldboss;

import com.cobblemon.mod.common.api.PrioritizedList;
import com.cobblemon.mod.common.api.storage.PokemonStoreManager;
import com.cobblemon.mod.common.api.storage.factory.PokemonStoreFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PokemonStoreManager.class)
public interface WorldBossStoreFactoriesAccessor {
    @Accessor("factories") PrioritizedList<PokemonStoreFactory> cobbleRaids$factories();
}
