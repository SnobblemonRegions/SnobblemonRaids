package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossAdvancementSaves;
import com.cobbleraids.worldboss.WorldBossAdvancementIo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Native periodic saves may wait for a later Main tick; critical saves and reads cannot. */
@Mixin(PlayerAdvancements.class)
public abstract class WorldBossAdvancementSaveMixin implements WorldBossAdvancementSaves.NativeTarget {
    @Shadow @Final private Path playerSavePath;
    @Override public Path cobbleRaids$advancementSavePath(){return playerSavePath;}
    @WrapOperation(method="save",at=@At(value="INVOKE",target="Ljava/nio/file/Files;newBufferedWriter(Ljava/nio/file/Path;Ljava/nio/charset/Charset;[Ljava/nio/file/OpenOption;)Ljava/io/BufferedWriter;"))
    private BufferedWriter cobbleRaids$writer(Path path,Charset charset,OpenOption[] options,Operation<BufferedWriter> original)throws IOException{
        try{var writer=WorldBossAdvancementIo.writer(path,charset,options);return writer!=null?writer:original.call(path,charset,options);}
        catch(IOException failure){throw failure;}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:advancement-writer",failure);throw failure;}
    }
    @WrapMethod(method="save")
    private void cobbleRaids$pace(Operation<Void> original){
        // Never repeat original after an exception: another wrapper may already have performed effects.
        try{WorldBossAdvancementSaves.direct(playerSavePath,()->original.call());}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:advancement-save-direct",failure);throw failure;}
    }
    @WrapMethod(method={"load","reload"})
    private void cobbleRaids$read(ServerAdvancementManager manager,Operation<Void> original){
        try{WorldBossAdvancementSaves.beforeLoad(playerSavePath);}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:advancement-save-read",failure);throw failure;}
        original.call(manager);
    }
}
