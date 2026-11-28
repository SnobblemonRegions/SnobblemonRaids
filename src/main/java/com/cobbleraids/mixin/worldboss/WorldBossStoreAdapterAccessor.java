package com.cobbleraids.mixin.worldboss;

import com.cobblemon.mod.common.api.storage.adapter.flatfile.FileStoreAdapter;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FileBackedPokemonStoreFactory.class)
public interface WorldBossStoreAdapterAccessor {
    @Accessor("adapter") FileStoreAdapter<?> cobbleRaids$adapter();
}
