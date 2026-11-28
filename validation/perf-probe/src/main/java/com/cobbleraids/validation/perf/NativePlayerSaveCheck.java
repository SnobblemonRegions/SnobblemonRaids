package com.cobbleraids.validation.perf;

import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Post-timing fixture through the real native save batch and forced-save barrier. */
final class NativePlayerSaveCheck {
    private static boolean used;
    static boolean used(){return used;}
    private static String diagnostics() throws ReflectiveOperationException {
        var method=Class.forName("com.cobbleraids.worldboss.WorldBossPlayerAutosaves").getDeclaredMethod("diagnostics");
        method.setAccessible(true);return (String)method.invoke(null);
    }
    private static long submitted(String diagnostics){
        var match=Pattern.compile("submitted=(\\d+)").matcher(diagnostics);
        return match.find()?Long.parseLong(match.group(1)):0;
    }
    private static String advancements() throws ReflectiveOperationException {
        var method=Class.forName("com.cobbleraids.worldboss.WorldBossAdvancementSaves").getDeclaredMethod("diagnostics");
        method.setAccessible(true);return (String)method.invoke(null);
    }
    static String automatic(MinecraftServer server) throws Exception {
        var method=Class.forName("com.cobbleraids.worldboss.WorldBossService").getDeclaredMethod("collectionState");
        method.setAccessible(true);Object state=method.invoke(null);
        if(state==null||((Boolean)state.getClass().getMethod("active").invoke(state)
                &&!(Boolean)state.getClass().getMethod("testMode").invoke(state)))throw new IllegalStateException("An idle fixture or active TEST event is required");
        int players=server.getPlayerCount();
        if(players<1||server.getPlayerList().getPlayers().stream().anyMatch(p->!p.getGameProfile().getName().matches("WB(?:5|10|30|50)_[a-f0-9]+_\\d+")))
            throw new IllegalStateException("Only connected synthetic pilot accounts may exercise native autosave");
        long count=submitted(diagnostics()),start=System.nanoTime();
        PerfProbe.sampleAutomaticSave(server);
        double elapsed=(System.nanoTime()-start)/1_000_000.0;
        long delta=submitted(diagnostics())-count;
        if(delta!=players)throw new IllegalStateException("Expected "+players+" accepted player snapshots, got "+delta);
        return "PASS players="+players+" submitted="+delta+" mainMillis="+elapsed+" "+diagnostics()+" advancements: "+advancements();
    }
    static String run(MinecraftServer server,ServerPlayer left,ServerPlayer right) throws Exception {
        used=true;
        String before="wbperf_before_"+UUID.randomUUID(),after="wbperf_after_"+UUID.randomUUID();
        long count=submitted(diagnostics());
        try{
            left.addTag(before);right.addTag(before);
            server.saveEverything(true,false,false);
            if(submitted(diagnostics())<count+2)throw new IllegalStateException("Native automatic save did not queue both online players");
            if(!advancements().contains("pending=2,"))throw new IllegalStateException("Native advancement serialization was not paced: "+advancements());
            left.getAdvancements().reload(server.getAdvancements());
            if(!advancements().contains("pending=1,"))throw new IllegalStateException("Native advancement reload did not flush its accepted predecessor");
            left.addTag(after);right.addTag(after);
            server.saveEverything(true,true,true);
            String status=diagnostics();
            if(!status.contains("pending=0,")||!status.contains("failed=false"))throw new IllegalStateException("Forced save did not drain accepted player writes: "+status);
            if(!advancements().contains("pending=0,")||!advancements().contains("failed=false"))throw new IllegalStateException("Forced save did not drain native advancement serialization");
            Path directory=server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            for(var player:new ServerPlayer[]{left,right}){
                var current=NbtIo.readCompressed(directory.resolve(player.getUUID()+".dat"),NbtAccounter.unlimitedHeap()).getList("Tags",8);
                var backup=NbtIo.readCompressed(directory.resolve(player.getUUID()+".dat_old"),NbtAccounter.unlimitedHeap()).getList("Tags",8);
                if(!current.contains(StringTag.valueOf(before))||!current.contains(StringTag.valueOf(after)))throw new IllegalStateException("Forced save lost current player state");
                if(!backup.contains(StringTag.valueOf(before))||backup.contains(StringTag.valueOf(after)))throw new IllegalStateException("Automatic/forced player save ordering or backup changed");
            }
            return "PASS native automatic queue / forced drain / latest state / previous backup (2 players) / advancement pace/read/force";
        } finally{left.removeTag(before);left.removeTag(after);right.removeTag(before);right.removeTag(after);}
    }
}
