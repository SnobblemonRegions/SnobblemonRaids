package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossWorldSaves;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.io.*;
import java.nio.file.Path;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only vanilla's file entry point; custom durable overrides retain their own authority. */
@Mixin(SavedData.class)
public abstract class WorldBossSavedDataSaveMixin {
    @WrapMethod(method="save(Ljava/io/File;Lnet/minecraft/core/HolderLookup$Provider;)V")
    private void cobbleRaids$saveBarrier(File file,HolderLookup.Provider registries,Operation<Void> original){
        WorldBossWorldSaves.Attempt attempt=null;
        try{attempt=WorldBossWorldSaves.beforeSave((SavedData)(Object)this,file.toPath());}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:world-nbt-save-barrier",failure);}
        try{original.call(file,registries);}
        finally{if(attempt!=null)try{attempt.close();}catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:world-nbt-save-close",failure);}}
    }
    @WrapOperation(method="save(Ljava/io/File;Lnet/minecraft/core/HolderLookup$Provider;)V",at=@At(value="INVOKE",target="Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V"))
    private void cobbleRaids$frozenNbt(CompoundTag tag,Path path,Operation<Void> original) throws IOException{
        try{if(WorldBossWorldSaves.defer((SavedData)(Object)this,tag,path))return;}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:world-nbt-capture",failure);}
        WorldBossWorldSaves.beforeDirectWrite(path);
        try{cobbleRaids$writeOriginal(original,tag,path);}
        catch(IOException|RuntimeException failure){WorldBossWorldSaves.directWriteFailed();throw failure;}
    }
    private static void cobbleRaids$writeOriginal(Operation<Void> original,CompoundTag tag,Path path) throws IOException{original.call(tag,path);}
}
