package com.cobbleraids.validation.perf;

import com.mojang.serialization.Dynamic;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelStorageSource;

/** Post-timing native entry points only; no economy, drop event or production state. */
final class NativeWorldSaveCheck {
    private static boolean used;
    static boolean used(){return used;}
    private static final class Data extends SavedData {
        final MinecraftServer server;int value=1,serializations;
        Data(MinecraftServer server){this.server=server;setDirty();}
        public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
            if(!server.isSameThread())throw new IllegalStateException("Native SavedData serialization left Main");
            serializations++;tag.putInt("fixture",value);return tag;
        }
    }
    private static String diagnostics() throws ReflectiveOperationException{
        var method=Class.forName("com.cobbleraids.worldboss.WorldBossWorldSaves").getDeclaredMethod("diagnostics");
        method.setAccessible(true);return (String)method.invoke(null);
    }
    private static long submitted(String status){var match=Pattern.compile("submitted=(\\d+)").matcher(status);return match.find()?Long.parseLong(match.group(1)):0;}
    private static LevelStorageSource.LevelStorageAccess access(MinecraftServer server) throws ReflectiveOperationException{
        for(Class<?> type=server.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields())
            if(field.getType()==LevelStorageSource.LevelStorageAccess.class){field.setAccessible(true);return (LevelStorageSource.LevelStorageAccess)field.get(server);}
        throw new IllegalStateException("Native level access unavailable");
    }
    private static String daylight(Dynamic<?> value){
        var root=(CompoundTag)value.getValue();if(root.contains("Data",10))root=root.getCompound("Data");
        return root.getCompound("GameRules").getString("doDaylightCycle");
    }
    static String run(MinecraftServer server) throws Exception{
        used=true;
        var method=Class.forName("com.cobbleraids.worldboss.WorldBossService").getDeclaredMethod("collectionState");
        method.setAccessible(true);Object state=method.invoke(null);
        if(state==null||!(Boolean)state.getClass().getMethod("active").invoke(state)||!(Boolean)state.getClass().getMethod("testMode").invoke(state))
            throw new IllegalStateException("Active isolated TEST fixture required");
        if(server.getPlayerCount()<1||server.getPlayerList().getPlayers().stream().anyMatch(p->!p.getGameProfile().getName().startsWith("WBCompat_")))
            throw new IllegalStateException("Connected synthetic compatibility clients required");
        var storage=server.overworld().getDataStorage();var level=access(server);
        var data=new Data(server);String name="wbperf_native_world_"+UUID.randomUUID();storage.set(name,data);
        var rule=server.getGameRules().getRule(GameRules.RULE_DAYLIGHT);boolean original=rule.get();
        long before=submitted(diagnostics());
        try{
            rule.set(!original,server);server.saveEverything(true,false,false);
            if(submitted(diagnostics())<before+2)throw new IllegalStateException("Native SavedData and metadata did not queue");
            if(data.isDirty())throw new IllegalStateException("Native captured generation did not clear its original dirty flag");
            data.value=2;data.setDirty();rule.set(original,server);
            int version=SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            var captured=storage.readTagFromDisk(name,DataFixTypes.LEVEL,version);
            if(captured.getCompound("data").getInt("fixture")!=1||!data.isDirty())throw new IllegalStateException("Native read barrier or later dirty mutation was lost");
            if(!daylight(level.getDataTag()).equals(Boolean.toString(!original)))throw new IllegalStateException("Native metadata read did not wait for the captured generation");
            server.saveEverything(true,true,true);
            if(storage.readTagFromDisk(name,DataFixTypes.LEVEL,version).getCompound("data").getInt("fixture")!=2)throw new IllegalStateException("Forced save lost latest SavedData");
            if(!daylight(level.getDataTag()).equals(Boolean.toString(original))||!daylight(level.getDataTagFallback()).equals(Boolean.toString(!original)))
                throw new IllegalStateException("Native metadata current/backup ordering changed");
            String status=diagnostics();
            if(!status.contains("pending=0,")||!status.contains("bytes=0,")||!status.contains("failed=false")||data.serializations<2)
                throw new IllegalStateException("Native world snapshot queue did not drain: "+status);
            return "PASS Main serialization / native read barriers / later dirty state / forced latest / level.dat_old";
        }finally{rule.set(original,server);}
    }
}
