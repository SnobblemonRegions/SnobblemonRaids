package com.cobbleraids.worldboss;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Connection;

/** Actual native protocol readiness, not just an empty event-loop marker. No player state crosses threads. */
public final class WorldBossLoginProtocols {
    private static volatile Counters counters=new Counters();
    private WorldBossLoginProtocols(){}
    private static final class Counters {
        final AtomicLong queued=new AtomicLong(),prepared=new AtomicLong(),consumed=new AtomicLong(),failed=new AtomicLong(),fallbacks=new AtomicLong();
    }
    static void started(){counters=new Counters();}
    public static void fallback(){counters.fallbacks.incrementAndGet();}
    public static String diagnostics(){var c=counters;return "queued="+c.queued+", prepared="+c.prepared+", consumed="+c.consumed+", failed="+c.failed+", fallbacks="+c.fallbacks;}
    public static boolean compatible(){return Compatibility.supported;}
    private static final class Compatibility {
        static final boolean supported=inspect();
        private static boolean inspect(){
            try{
                String name=FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary","net.minecraft.class_2535","method_56329","(Lnet/minecraft/class_9127;)V");
                return singleWrapper(Arrays.stream(Connection.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList(),name);
            }catch(RuntimeException|LinkageError unsupported){return false;}
        }
    }
    static boolean singleWrapper(Collection<String> methods,String nativeName){
        return methods.stream().filter(name->name.startsWith(nativeName+"$mixinextras$wrapped$")).count()==1;
    }
    public static <T> Prepared<T> prepare(T protocol,Executor eventLoop,Consumer<T> nativeSetup){
        var result=new Prepared<T>(protocol,counters);result.counts.queued.incrementAndGet();
        try{eventLoop.execute(()->{
            try{nativeSetup.accept(protocol);result.counts.prepared.incrementAndGet();result.ready.complete(null);}
            catch(Throwable failed){result.counts.failed.incrementAndGet();result.ready.completeExceptionally(failed);}
        });}catch(RuntimeException failed){result.counts.failed.incrementAndGet();result.ready.completeExceptionally(failed);}
        return result;
    }
    public static final class Prepared<T> {
        private final Thread main=Thread.currentThread();private final T protocol;private final Counters counts;
        private final CompletableFuture<Void> ready=new CompletableFuture<>();private boolean consumed;
        private Prepared(T protocol,Counters counts){this.protocol=Objects.requireNonNull(protocol);this.counts=counts;}
        public CompletableFuture<Void> ready(){return ready;}
        public T protocolForMain(){return Thread.currentThread()==main&&ready.isDone()&&!ready.isCompletedExceptionally()&&!consumed?protocol:null;}
        public boolean consume(T requested){
            if(protocolForMain()==null)return false;
            if(requested!=protocol)throw new IllegalStateException("Prepared native protocol binding changed before admission");
            consumed=true;counts.consumed.incrementAndGet();return true;
        }
    }
}
