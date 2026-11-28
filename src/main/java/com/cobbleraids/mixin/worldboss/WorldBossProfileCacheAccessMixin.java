package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossProfileCacheIo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import net.minecraft.server.players.GameProfileCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep native profile-cache reads/writes serialized, including startup priming and lookup callbacks. */
@Mixin(GameProfileCache.class)
public abstract class WorldBossProfileCacheAccessMixin {
    @WrapOperation(method="save",at=@At(value="INVOKE",target="Lcom/google/common/io/Files;newWriter(Ljava/io/File;Ljava/nio/charset/Charset;)Ljava/io/BufferedWriter;"))
    private BufferedWriter cobbleRaids$writer(File file,Charset charset,Operation<BufferedWriter> original)throws IOException{
        try{
            var writer=WorldBossProfileCacheIo.writer(this,file,charset);
            return writer!=null?writer:original.call(file,charset);
        }catch(IOException failure){throw failure;} // Native save retains its own IOException behavior.
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:profile-cache-writer",failure);throw failure;}
    }
    @WrapMethod(method="save")
    private void cobbleRaids$save(Operation<Void> original){
        try{synchronized(this){original.call();}}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:profile-cache-save",failure);throw failure;}
    }
    @WrapMethod(method="load")
    private List<?> cobbleRaids$load(Operation<List<?>> original){
        try{synchronized(this){return original.call();}}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:profile-cache-load",failure);throw failure;}
    }
}
