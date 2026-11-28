package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossPlayerAutosaves;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep vanilla serialization and save hooks on Main; defer only its automatic file operations. */
@Mixin(PlayerDataStorage.class)
public abstract class WorldBossPlayerDataSaveMixin {
    @Shadow @Final private File playerDir;
    @WrapMethod(method="save")
    private void cobbleRaids$capture(Player player,Operation<Void> original) {
        WorldBossPlayerAutosaves.Capture scope=null;
        try{scope=WorldBossPlayerAutosaves.begin((PlayerDataStorage)(Object)this,playerDir.toPath(),player);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:player-autosave-capture",failure);}
        try{original.call(player);}
        finally{if(scope!=null)try{scope.close();}catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:player-autosave-capture-close",failure);}}
    }
    @WrapMethod(method="load(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;")
    private Optional<CompoundTag> cobbleRaids$loadAfterWrites(Player player,Operation<Optional<CompoundTag>> original) {
        try{WorldBossPlayerAutosaves.beforeLoad(playerDir.toPath(),player);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:player-autosave-load-barrier",failure);}
        return original.call(player);
    }
    @Redirect(method="save",at=@At(value="INVOKE",target="Ljava/nio/file/Files;createTempFile(Ljava/nio/file/Path;Ljava/lang/String;Ljava/lang/String;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;"))
    private Path cobbleRaids$temporary(Path directory,String prefix,String suffix,FileAttribute<?>[] attributes) throws IOException {
        try{return WorldBossPlayerAutosaves.temporary(directory,prefix,suffix,attributes);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:player-autosave-temp",failure);throw new IOException("Player temporary file preparation failed",failure);}
    }
    @Redirect(method="save",at=@At(value="INVOKE",target="Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V"))
    private void cobbleRaids$detachedNbt(CompoundTag tag,Path path) throws IOException {
        try{WorldBossPlayerAutosaves.write(tag,path);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:player-autosave-nbt",failure);throw new IOException("Player snapshot preparation failed",failure);}
    }
    @Redirect(method="save",at=@At(value="INVOKE",target="Lnet/minecraft/Util;safeReplaceFile(Ljava/nio/file/Path;Ljava/nio/file/Path;Ljava/nio/file/Path;)V"))
    private void cobbleRaids$orderedReplacement(Path target,Path temporary,Path backup) throws IOException {
        try{WorldBossPlayerAutosaves.replace(target,temporary,backup);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:player-autosave-replace",failure);throw new IOException("Player snapshot replacement failed",failure);}
    }
}
