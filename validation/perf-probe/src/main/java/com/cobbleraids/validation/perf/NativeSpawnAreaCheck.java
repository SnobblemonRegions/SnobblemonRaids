package com.cobbleraids.validation.perf;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Private post-timing fixture: native routing only, no entity creation or global spawn-pool mutation. */
final class NativeSpawnAreaCheck {
    private static Class<?> type(String suffix) throws ClassNotFoundException {
        return Class.forName("com.cobblemon.mod.common.api.spawning." + suffix);
    }
    static String run(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> poolType=type("detail.SpawnPool"), spawnerType=type("spawner.Spawner"), basicType=type("spawner.BasicSpawner");
        Class<?> causeType=type("SpawnCause"), areaType=type("spawner.SpawningZoneInput");
        Object pool=poolType.getConstructor(String.class).newInstance("wbperf-area-"+UUID.randomUUID());
        @SuppressWarnings("unchecked") List<Object> details=(List<Object>)poolType.getMethod("getDetails").invoke(pool);
        details.add(type("detail.PokemonSpawnDetail").getConstructor().newInstance());
        details.add(type("detail.PokemonHerdSpawnDetail").getConstructor().newInstance());
        details.add(type("detail.EmptySpawnDetail").getConstructor().newInstance());
        Object natural=type("spawner.PlayerSpawner").getConstructor(ServerPlayer.class,poolType).newInstance(player,pool);
        var constrain=basicType.getMethod("constrainArea",areaType);
        Object inside=area(causeType,spawnerType,areaType,natural,player,-8);
        Object outside=area(causeType,spawnerType,areaType,natural,player,192);
        require(constrain.invoke(natural,inside)==null,"wholly protected Pokemon-only zone not rejected");
        require(constrain.invoke(natural,outside)!=null,"outside zone was rejected");
        details.add(type("detail.NPCSpawnDetail").getConstructor().newInstance());
        require(constrain.invoke(natural,inside)!=null,"mixed pool did not retain native routing");
        details.removeLast();
        require(constrain.invoke(natural,inside)==null,"live pool change was not rechecked");
        Object basic=basicType.getConstructor(String.class,poolType,float.class,Map.class).newInstance("wbperf-basic",pool,0f,Map.of("common",1f));
        require(constrain.invoke(basic,area(causeType,spawnerType,areaType,basic,player,-8))!=null,"non-player spawner was rejected");
        Class<?> generatorType=type("SpawningZoneGenerator");
        Object generator=basicType.getMethod("getGenerator").invoke(natural);
        Object extension=Proxy.newProxyInstance(generatorType.getClassLoader(),new Class<?>[]{generatorType},(proxy,method,args)->{throw new AssertionError("Generator must not run in constrain fixture");});
        basicType.getMethod("setGenerator",generatorType).invoke(natural,extension);
        require(constrain.invoke(natural,inside)!=null,"custom generator was rejected");
        basicType.getMethod("setGenerator",generatorType).invoke(natural,generator);
        return "PASS protected/outside/mixed/reloaded/non-player/custom-generator native area routing (6 checks)";
    }
    private static Object area(Class<?> causeType,Class<?> spawnerType,Class<?> areaType,Object spawner,ServerPlayer player,int x) throws ReflectiveOperationException {
        Object cause=causeType.getConstructor(spawnerType,Entity.class).newInstance(spawner,player);
        return areaType.getConstructor(causeType,ServerLevel.class,int.class,int.class,int.class,int.class,int.class,int.class)
                .newInstance(cause,player.serverLevel(),x,57,-8,16,16,16);
    }
    private static void require(boolean valid,String message) { if(!valid) throw new IllegalStateException(message); }
}
