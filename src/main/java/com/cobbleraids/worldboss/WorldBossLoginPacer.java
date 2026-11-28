package com.cobbleraids.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.minecraft.network.Connection;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Native final-login callbacks stay on Main; pending Netty work is awaited without blocking Main. */
public final class WorldBossLoginPacer {
    public interface ChannelAccess {
        CompletableFuture<Void> cobbleRaids$loginBarrier(ProtocolInfo<?> outbound);
        ProtocolInfo<?> cobbleRaids$preparedOutbound();
    }
    private static MinecraftServer server;
    private static Queue queue;
    private WorldBossLoginPacer() {}
    static void started(MinecraftServer current){server=current;queue=new Queue();WorldBossLoginProtocols.started();WorldBossLoginProtocols.compatible();if(current.isDedicatedServer())WorldBossConsoleLogs.install();WorldBossLoginFiles.started();WorldBossProfileCacheIo.started(current);WorldBossLoginWarmup.prepare(current);WorldBossPoiReadAhead.started(current);}
    static void stopping(){if(queue!=null)queue.close();WorldBossPoiReadAhead.stopping();WorldBossProfileCacheIo.stopping();WorldBossLoginFiles.close();}
    static void stopped(){server=null;queue=null;WorldBossPoiReadAhead.stopping();WorldBossProfileCacheIo.stopping();WorldBossLoginFiles.stopped();}
    static void tick(MinecraftServer current){if(current==server&&queue!=null){WorldBossPoiReadAhead.tick(current);queue.tick(System.nanoTime());}}
    static String diagnostics(){return queue==null?"idle":queue.diagnostics()+"; directories "+WorldBossLoginFiles.diagnostics()+"; startup "+WorldBossLoginWarmup.diagnostics()+"; "+WorldBossConsoleLogs.diagnostics()+"; command node maps="+WorldBossCommandNodes.mappings()+", packet node maps="+WorldBossCommandNodes.packetMappings()+"; protocol setup: "+WorldBossLoginProtocols.diagnostics()+"; profile cache IO: "+WorldBossProfileCacheIo.diagnostics()+"; POI read ahead: "+WorldBossPoiReadAhead.diagnostics();}

    /** False leaves the native callback unchanged (integrated server, startup or shutdown). */
    public static boolean defer(MinecraftServer current,Connection connection,Object listener,UUID player,boolean finalJoin,Runnable nativeLogin){
        if(current!=server||queue==null||!current.isDedicatedServer())return false;
        if(!current.isSameThread())throw new IllegalStateException("Login continuation requires Main");
        BooleanSupplier valid=()->server==current&&connection.isConnected()&&connection.getPacketListener()==listener;
        Runnable reject=()->{
            if(server==current&&connection.isConnected())connection.disconnect(Component.literal("Login could not finish. Please reconnect."));
        };
        if(queue.closed){reject.run();return true;}
        var cache=WorldBossProfileCacheIo.prepare();
        var registries=current.registryAccess();
        CompletableFuture<Void> ready;
        try{ready=CompletableFuture.allOf(((ChannelAccess)connection).cobbleRaids$loginBarrier(finalJoin?GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries)):null),WorldBossLoginFiles.prepare(current,player),
                cache==null?CompletableFuture.completedFuture(null):cache.ready());}
        catch(RuntimeException|Error failure){if(cache!=null)cache.close();throw failure;}
        Runnable publish=()->{
            if(current.registryAccess()!=registries)throw new IllegalStateException("Login registries changed during protocol preparation");
            if(cache==null)nativeLogin.run();else cache.use(nativeLogin);
            if(connection.getPacketListener() instanceof ServerGamePacketListenerImpl play)try{WorldBossPoiReadAhead.admitted(play.player);}
            catch(RuntimeException failure){WorldBossPoiReadAhead.disable();RaidFaultBarrier.report("worldboss:poi-login-preparation",failure);}
        };
        Runnable cleanup=cache==null?()->{}:cache::close;
        if(!queue.offer(connection,ready,valid,publish,reject,System.nanoTime(),cleanup)){try{reject.run();}finally{cleanup.run();}}
        return true;
    }

    static final class Queue {
        private final Thread owner=Thread.currentThread();
        private final LinkedHashMap<Object,Entry> pending=new LinkedHashMap<>();
        private record Entry(CompletableFuture<Void> ready,BooleanSupplier valid,Runnable apply,Runnable reject,long at,Runnable cleanup){}
        private long queued,applied,rejected,discarded;
        private boolean closed;
        private void owner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Login queue requires its Main owner");}
        boolean offer(Object key,CompletableFuture<Void> ready,BooleanSupplier valid,Runnable apply,Runnable reject,long now){
            return offer(key,ready,valid,apply,reject,now,()->{});
        }
        boolean offer(Object key,CompletableFuture<Void> ready,BooleanSupplier valid,Runnable apply,Runnable reject,long now,Runnable cleanup){
            owner();
            var duplicate=pending.remove(key);
            if(duplicate!=null){finish(duplicate);rejected++;return false;} // Duplicate finish packets cannot publish twice.
            if(closed||pending.size()>=75){rejected++;return false;}
            pending.put(key,new Entry(Objects.requireNonNull(ready),valid,apply,reject,now,cleanup));queued++;return true;
        }
        void tick(long now){
            owner();if(closed)return;
            Entry selected=null;var refused=new ArrayList<Entry>();var stale=new ArrayList<Entry>();
            var iterator=pending.entrySet().iterator();
            while(iterator.hasNext()){
                var item=iterator.next().getValue();
                boolean valid;
                try{valid=item.valid().getAsBoolean();}catch(RuntimeException failure){valid=false;RaidFaultBarrier.report("worldboss:login-owner",failure);}
                if(!valid){iterator.remove();discarded++;stale.add(item);continue;}
                if(now-item.at()>=20_000_000_000L||item.ready().isCompletedExceptionally()||item.ready().isCancelled()){
                    iterator.remove();refused.add(item);rejected++;continue;
                }
                if(selected==null&&item.ready().isDone()){iterator.remove();selected=item;}
            }
            for(var item:stale)finish(item);
            for(var item:refused)reject(item);
            if(selected!=null){
                try{if(!closed&&selected.valid().getAsBoolean()){selected.apply().run();applied++;}else discarded++;}
                catch(RuntimeException failure){
                    RaidFaultBarrier.report("worldboss:login-apply",failure);
                    try{selected.reject().run();}catch(RuntimeException disconnect){RaidFaultBarrier.report("worldboss:login-apply-reject",disconnect);}
                }
                finally{finish(selected);}
            }
        }
        private void finish(Entry item){try{item.cleanup().run();}catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-cleanup",failure);}}
        private void reject(Entry item){try{if(item.valid().getAsBoolean())item.reject().run();}catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:login-reject",failure);}finally{finish(item);}}
        void close(){owner();closed=true;discarded+=pending.size();var waiting=new ArrayList<>(pending.values());pending.clear();for(var item:waiting)finish(item);}
        String diagnostics(){return "pending="+pending.size()+", queued="+queued+", applied="+applied+", rejected="+rejected+", discarded="+discarded;}
    }
}
