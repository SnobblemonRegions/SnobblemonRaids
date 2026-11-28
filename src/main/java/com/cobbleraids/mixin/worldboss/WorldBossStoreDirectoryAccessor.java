package com.cobbleraids.mixin.worldboss;

import com.cobblemon.mod.common.api.storage.adapter.flatfile.OneToOneFileStoreAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OneToOneFileStoreAdapter.class)
public interface WorldBossStoreDirectoryAccessor {
    @Accessor("rootFolder") String cobbleRaids$rootFolder();
    @Accessor("useNestedFolders") boolean cobbleRaids$nestedFolders();
    @Accessor("folderPerClass") boolean cobbleRaids$folderPerClass();
}
