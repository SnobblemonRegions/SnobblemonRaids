package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossWorldSaves;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.File;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.*;

@Mixin(DimensionDataStorage.class)
public abstract class WorldBossSavedDataLoadMixin {
    @Shadow @Final private File dataFolder;
    @WrapMethod(method="readTagFromDisk")
    private CompoundTag cobbleRaids$loadAfterWrites(String name,DataFixTypes type,int version,Operation<CompoundTag> original){
        try{WorldBossWorldSaves.beforeLoad(new File(dataFolder,name+".dat").toPath());}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:world-nbt-load-barrier",failure);}
        return original.call(name,type,version);
    }
}
