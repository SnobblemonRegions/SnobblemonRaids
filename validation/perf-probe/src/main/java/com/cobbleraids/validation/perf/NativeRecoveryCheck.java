package com.cobbleraids.validation.perf;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Read-only Main-thread native recovery evidence. Permanently excludes this process from capacity trials. */
final class NativeRecoveryCheck {
    private static boolean used;
    static boolean used(){return used;}
    static void initialize(Path root) throws java.io.IOException {
        var manifest=JsonParser.parseString(Files.readString(root.resolve("rehearsal.json"))).getAsJsonObject();
        used=manifest.has("launch_history")||manifest.has("recovery_only")&&manifest.get("recovery_only").getAsBoolean()
                ||Files.exists(root.resolve("logs/recovery-expected.nbt"));
    }
    private static Object field(Object owner,String name) throws ReflectiveOperationException {
        Class<?> type=owner instanceof Class<?> c?c:owner.getClass();
        var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner instanceof Class<?>?null:owner);
    }
    private static Object call(Object owner,String name) throws ReflectiveOperationException {
        var method=owner.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(owner);
    }
    private static JsonElement json(Tag tag){return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE,tag);}
    static String snapshot(MinecraftServer server,Path root) throws Exception {
        used=true;
        if(!server.isSameThread())throw new IllegalStateException("Recovery inspection requires Main");
        var service=Class.forName("com.cobbleraids.worldboss.WorldBossService");
        Object state=field(service,"state");
        if(state==null||!(Boolean)call(state,"testMode"))throw new IllegalStateException("Isolated TEST state required");
        for(var player:server.getPlayerList().getPlayers())if(!player.getGameProfile().getName().matches("WB(?:5|10|30|50)_[a-f0-9]+_\\d+"))
            throw new IllegalStateException("Only synthetic rehearsal clients may be inspected");
        Object journal=field(state,"journal");var result=new JsonObject();
        result.addProperty("scope","Native process-crash recovery inspection; not a durability or capacity receipt");
        result.addProperty("installation",call(journal,"installation").toString());result.addProperty("sequence",(Long)call(journal,"sequence"));
        result.add("acknowledged",json((CompoundTag)call(journal,"snapshot")));
        result.add("runtime",json(((SavedData)state).save(new CompoundTag(),server.registryAccess())));
        var duels=new JsonArray();
        for(Object duel:((Map<?,?>)field(service,"DUELS")).values())duels.add(call(duel,"encounterId").toString());
        result.add("duels",duels);
        int showpieces=0,owned=0;
        for(var level:server.getAllLevels())for(var entity:level.getAllEntities()){
            if(entity.getTags().contains("cobbleraids_world_boss_showpiece"))showpieces++;
            if(entity.getTags().contains("cobbleraids_owner=cobbleraids:world_boss"))owned++;
        }
        result.addProperty("showpieces",showpieces);result.addProperty("ownedBossEntities",owned);
        var players=new JsonObject();
        Object cobblemon=Class.forName("com.cobblemon.mod.common.Cobblemon").getField("INSTANCE").get(null);
        Object storage=cobblemon.getClass().getMethod("getStorage").invoke(cobblemon);
        for(var player:server.getPlayerList().getPlayers()){
            var data=new JsonObject();data.addProperty("uuid",player.getUUID().toString());
            var tags=new JsonArray();player.getTags().stream().sorted().forEach(tags::add);data.add("tags",tags);
            data.add("inventory",json(player.getInventory().save(new ListTag())));
            var party=new JsonArray();
            Object store=storage.getClass().getMethod("getParty",ServerPlayer.class).invoke(storage,player);
            for(Object pokemon:(Iterable<?>)store){
                var mon=new JsonObject();mon.addProperty("uuid",pokemon.getClass().getMethod("getUuid").invoke(pokemon).toString());
                mon.addProperty("level",(Integer)pokemon.getClass().getMethod("getLevel").invoke(pokemon));
                Object species=pokemon.getClass().getMethod("getSpecies").invoke(pokemon);
                mon.addProperty("species",species.getClass().getMethod("getResourceIdentifier").invoke(species).toString());party.add(mon);
            }
            data.add("party",party);players.add(player.getGameProfile().getName(),data);
        }
        result.add("players",players);
        Path expected=root.resolve("logs/recovery-expected.nbt");
        if(Files.exists(expected))result.add("expected",json(NbtIo.readCompressed(expected,NbtAccounter.create(64L*1024*1024))));
        Path file=root.resolve("logs/recovery-snapshot-"+UUID.randomUUID()+".json");
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(result));
        return "file="+file.getFileName();
    }
}
