package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossPoiReadAhead;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionStorage.class)
public abstract class WorldBossPoiReadAheadMixin<R> implements WorldBossPoiReadAhead.Storage {
    @Shadow @Final private SimpleRegionStorage simpleRegionStorage;
    @Shadow @Final private Long2ObjectMap<Optional<R>> storage;
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
    @Override public boolean cobbleRaids$hasColumnData(ChunkPos position){
        for(int y=levelHeightAccessor.getMinSection();y<levelHeightAccessor.getMaxSection();y++)
            if(storage.containsKey(SectionPos.asLong(position.x,y,position.z)))return true;
        return false;
    }
    @Override public CompletableFuture<Optional<CompoundTag>> cobbleRaids$startRead(ChunkPos position){return simpleRegionStorage.read(position);}
    @Override @Invoker("readColumn") public abstract void cobbleRaids$loadColumn(ChunkPos position);
    @WrapMethod(method="tryRead")
    private CompletableFuture<Optional<CompoundTag>> cobbleRaids$ready(ChunkPos position,Operation<CompletableFuture<Optional<CompoundTag>>> original){
        if((Object)this instanceof PoiManager)try{
            var ready=WorldBossPoiReadAhead.consume(this,position);if(ready!=null)return ready;
        }catch(RuntimeException failure){WorldBossPoiReadAhead.disable();RaidFaultBarrier.report("worldboss:poi-prefetched-read",failure);}
        return original.call(position); // Pending/failed/expired reads retain native retry and error handling.
    }
    @Inject(method="setDirty",at=@At("HEAD"))
    private void cobbleRaids$dirty(long section,CallbackInfo callback){
        if((Object)this instanceof PoiManager)try{WorldBossPoiReadAhead.invalidate(this,ChunkPos.asLong(SectionPos.x(section),SectionPos.z(section)),false);}
        catch(RuntimeException failure){WorldBossPoiReadAhead.disable();RaidFaultBarrier.report("worldboss:poi-dirty-invalidation",failure);}
    }
    @Inject(method="writeColumn(Lnet/minecraft/world/level/ChunkPos;)V",at=@At("HEAD"))
    private void cobbleRaids$write(ChunkPos position,CallbackInfo callback){
        if((Object)this instanceof PoiManager)try{WorldBossPoiReadAhead.invalidate(this,position.toLong(),true);}
        catch(RuntimeException failure){WorldBossPoiReadAhead.disable();RaidFaultBarrier.report("worldboss:poi-write-invalidation",failure);}
    }
}
