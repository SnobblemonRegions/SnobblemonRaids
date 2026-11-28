package com.cobbleraids.validation.perf;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Deliberately re-enters a TEST start from the real native entity-load callback, after timing only. */
final class NativeShowpieceCheck {
    private static boolean used, armed, stopped;
    private static Field spawning, tracking, state;
    private static Method stop;
    private static UUID entity;
    private static String dimension, error;
    static boolean used() { return used; }
    static void initialize() {
        ServerEntityEvents.ENTITY_LOAD.register((loaded, level) -> {
            if (!armed || !loaded.getClass().getName().equals("com.cobblemon.mod.common.entity.pokemon.PokemonEntity")) return;
            try {
                if (!spawning.getBoolean(null)) return;
                armed=false; entity=loaded.getUUID(); dimension=level.dimension().location().toString();
                stop.invoke(null,level.getServer()); stopped=true;
            } catch (ReflectiveOperationException|RuntimeException failure) { armed=false;error=failure.toString(); }
        });
    }
    static void arm() throws ReflectiveOperationException {
        if (used) throw new IllegalStateException("Use a fresh rig for another re-entry fixture");
        Class<?> service=Class.forName("com.cobbleraids.worldboss.WorldBossService");
        state=service.getDeclaredField("state");state.setAccessible(true);
        Object saved=state.get(null);
        if(saved==null || (Boolean)saved.getClass().getMethod("active").invoke(saved)
                || !(Boolean)saved.getClass().getMethod("testMode").invoke(saved))
            throw new IllegalStateException("Stop the previous TEST event before arming this fixture");
        spawning=service.getDeclaredField("spawningShowpiece");spawning.setAccessible(true);
        tracking=service.getDeclaredField("showpieceId");tracking.setAccessible(true);
        stop=service.getMethod("stop",MinecraftServer.class);
        used=true;armed=true;
    }
    static Map<String,Object> result(MinecraftServer server) throws ReflectiveOperationException {
        if(!used) throw new IllegalStateException("Fixture has not been armed");
        var result=new LinkedHashMap<String,Object>();
        result.put("armed",armed);result.put("stopped",stopped);result.put("entity",entity);result.put("error",error);
        var level=dimension==null?null:server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(dimension)));
        result.put("entityPresent",entity!=null&&level!=null&&level.getEntity(entity)!=null);
        result.put("tracked",tracking.get(null));
        Object saved=state.get(null);
        result.put("active",saved!=null&&(Boolean)saved.getClass().getMethod("active").invoke(saved));
        return result;
    }
}
