package com.cobbleraids.validation.perf;

import java.util.regex.Pattern;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;

/** Post-timing fixture in the owned flat world. Temporary POI records are restored in finally. */
final class NativePoiReadAheadCheck {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static String status()throws ReflectiveOperationException{
        var method=Class.forName("com.cobbleraids.worldboss.WorldBossPoiReadAhead").getDeclaredMethod("diagnostics");
        method.setAccessible(true);return (String)method.invoke(null);
    }
    private static long value(String status,String key){
        var match=Pattern.compile("\\b"+key+"=(\\d+)").matcher(status);
        if(!match.find())throw new IllegalStateException("Missing POI counter "+key);return Long.parseLong(match.group(1));
    }
    static String run(MinecraftServer server)throws ReflectiveOperationException{
        require(server.isSameThread(),"POI fixture requires Main");String before=status();
        require(value(before,"queued")==0&&value(before,"cached")==0&&value(before,"outstanding")==0,"POI read-ahead has not drained");
        require(value(before,"reads")>0&&value(before,"published")>0&&value(before,"hits")>0,"Native POI read-ahead was not exercised");
        require(before.contains("failed=false")&&value(before,"skipped")==0&&value(before,"readFailures")==0,"POI preparation failed or coverage was skipped");
        var pokemon=Class.forName("com.cobblemon.mod.common.entity.pokemon.PokemonEntity");int wild=0;
        for(var level:server.getAllLevels())for(var entity:level.getAllEntities())
            if(pokemon.isInstance(entity)&&entity instanceof OwnableEntity owned&&owned.getOwnerUUID()==null
                    &&entity.getTags().stream().noneMatch(tag->tag.startsWith("cobbleraids_owner=")))wild++;
        require(wild>0,"No unowned natural Pokemon remain while native clients are connected");
        var persisted=new java.util.HashMap<java.util.UUID,String>();
        var profileCache=server.getProfileCache();require(profileCache!=null,"Native profile cache is absent");
        synchronized(profileCache){
            java.util.List<?> stored=profileCache.load();
            for(Object entry:stored){
                var methods=java.util.Arrays.stream(entry.getClass().getDeclaredMethods())
                        .filter(method->method.getReturnType()==GameProfile.class&&method.getParameterCount()==0).toList();
                require(methods.size()==1,"Native profile entry signature changed");var getter=methods.getFirst();getter.setAccessible(true);
                var profile=(GameProfile)getter.invoke(entry);persisted.put(profile.getId(),profile.getName());
            }
        }
        int verifiedProfiles=0;
        for(var player:server.getPlayerList().getPlayers()){
            require(player.getGameProfile().getName().equals(persisted.get(player.getUUID())),"Connected native profile was not persisted");verifiedProfiles++;
        }
        require(verifiedProfiles>=6,"Native profile fixture requires the connected pilot cohort");
        String profileIo=(String)Class.forName("com.cobbleraids.worldboss.WorldBossProfileCacheIo").getMethod("diagnostics").invoke(null);
        require(value(profileIo,"prepared")==verifiedProfiles&&value(profileIo,"opened")==verifiedProfiles
                &&value(profileIo,"used")==verifiedProfiles&&value(profileIo,"released")==verifiedProfiles
                &&value(profileIo,"pending")==0&&value(profileIo,"fallbacks")==2&&value(profileIo,"errors")==0
                &&profileIo.contains("pinned=true"),"Native profile-cache handle admission/release was not healthy");
        var level=server.overworld();var position=new BlockPos(-150,65,-150);
        require(level.hasChunkAt(position),"Prepared fixture column is not loaded");
        var poi=level.getPoiManager();require(poi.getType(position).isEmpty(),"Fixture would overwrite an existing POI");
        var home=server.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME);
        try{
            poi.add(position,home);require(poi.existsAtPosition(PoiTypes.HOME,position),"Native POI add/read lost its type");
            poi.flush(new ChunkPos(position));
            String added=status();require(value(added,"invalidations")>value(before,"invalidations"),"Native dirty invalidation did not run");
            require(value(added,"writeInvalidations")>value(before,"writeInvalidations"),"Native write invalidation did not run");
        }finally{poi.remove(position);poi.flush(new ChunkPos(position));}
        require(poi.getType(position).isEmpty(),"Native POI fixture was not restored");require(status().contains("failed=false"),"Native POI fixture disabled read-ahead");
        return "PASS wildPokemon="+wild+" persistedProfiles="+verifiedProfiles+" profileIo={"+profileIo+"} dirtyAndWriteInvalidation=true restored=true "+before;
    }
}
