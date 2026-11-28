package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossWorldSaves;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Original world-tag creation stays on Main; only detached native file work is deferred. */
@Mixin(LevelStorageSource.LevelStorageAccess.class)
public abstract class WorldBossLevelDataSaveMixin {
    private Path cobbleRaids$root(){return ((LevelStorageSource.LevelStorageAccess)(Object)this).getLevelPath(LevelResource.ROOT);}
    @WrapMethod(method="saveLevelData")
    private void cobbleRaids$metadata(CompoundTag tag,Operation<Void> original){
        WorldBossWorldSaves.Metadata capture=null;
        try{capture=WorldBossWorldSaves.beforeMetadata(cobbleRaids$root());}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:level-metadata-capture",failure);}
        try{original.call(tag);}
        finally{if(capture!=null)try{capture.close();}catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:level-metadata-close",failure);}}
    }
    @Redirect(method="saveLevelData",at=@At(value="INVOKE",target="Ljava/nio/file/Files;createTempFile(Ljava/nio/file/Path;Ljava/lang/String;Ljava/lang/String;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;"))
    private Path cobbleRaids$temporary(Path directory,String prefix,String suffix,FileAttribute<?>[] attributes) throws IOException{
        try{return WorldBossWorldSaves.metadataTemporary(directory,prefix,suffix,attributes);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:level-metadata-temporary",failure);throw new IOException("Native metadata temporary preparation failed",failure);}
    }
    @Redirect(method="saveLevelData",at=@At(value="INVOKE",target="Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V"))
    private void cobbleRaids$nbt(CompoundTag tag,Path temporary) throws IOException{
        try{WorldBossWorldSaves.metadataWrite(tag,temporary);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:level-metadata-nbt",failure);throw new IOException("Native metadata snapshot failed",failure);}
    }
    @Redirect(method="saveLevelData",at=@At(value="INVOKE",target="Lnet/minecraft/Util;safeReplaceFile(Ljava/nio/file/Path;Ljava/nio/file/Path;Ljava/nio/file/Path;)V"))
    private void cobbleRaids$replace(Path target,Path temporary,Path backup) throws IOException{
        try{WorldBossWorldSaves.metadataReplace(target,temporary,backup);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:level-metadata-replace",failure);throw new IOException("Native metadata replacement failed",failure);}
    }
    @WrapMethod(method="getDataTag(Z)Lcom/mojang/serialization/Dynamic;")
    private Dynamic<?> cobbleRaids$read(boolean fallback,Operation<Dynamic<?>> original){
        try{WorldBossWorldSaves.beforeLoad(cobbleRaids$root().resolve("level.dat"));}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:level-metadata-read",failure);}
        return original.call(fallback);
    }
    @WrapMethod(method="makeWorldBackup")
    private long cobbleRaids$backup(Operation<Long> original) throws IOException{
        try{WorldBossWorldSaves.beforeBackup(cobbleRaids$root());}
        catch(IOException|RuntimeException failure){RaidFaultBarrier.report("worldboss:world-nbt-backup-barrier",failure);throw new IOException("Native world snapshots did not drain before backup",failure);}
        return original.call();
    }
}
