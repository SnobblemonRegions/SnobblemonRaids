package com.cobbleraids.worldboss;

import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Bounded native I/O results. No callback runs on completion, and consumers never wait for I/O. */
final class WorldBossPoiReadCache<K,V> {
    private record Entry<V>(CompletableFuture<V> future,long at){}
    private final LinkedHashMap<K,Entry<V>> entries=new LinkedHashMap<>();
    private final int capacity;
    private final long ttl;
    private Entry<V> publishing;
    WorldBossPoiReadCache(int capacity,long ttl){if(capacity<1||ttl<1)throw new IllegalArgumentException();this.capacity=capacity;this.ttl=ttl;}
    synchronized boolean offer(K key,Supplier<CompletableFuture<V>> read,long now){
        prune(now);
        if(entries.containsKey(key)||entries.size()>=capacity)return false;
        // Holding the monitor prevents a concurrent invalidation from preceding publication.
        entries.put(key,new Entry<>(java.util.Objects.requireNonNull(read.get()),now));return true;
    }
    synchronized CompletableFuture<V> takeReady(K key,long now){
        var entry=entries.remove(key);
        return entry!=null&&(entry==publishing||usable(entry,now))?entry.future():null;
    }
    synchronized K firstReady(long now){
        prune(now);
        for(var item:entries.entrySet())if(usable(item.getValue(),now))return item.getKey();
        return null;
    }
    synchronized boolean publish(K key,long now,Runnable nativeLoad){
        var entry=entries.get(key);
        if(!usable(entry,now))return false;
        // Native loading re-enters takeReady on this same Main thread. Invalidation cannot race
        // between checking readiness and the native join of that already-completed future.
        var previous=publishing;publishing=entry;
        try{nativeLoad.run();return true;}finally{publishing=previous;entries.remove(key,entry);}
    }
    private boolean usable(Entry<V> entry,long now){return entry!=null&&now-entry.at()<ttl&&entry.future().isDone()
            &&!entry.future().isCompletedExceptionally()&&!entry.future().isCancelled();}
    synchronized void prune(long now){entries.entrySet().removeIf(item->now-item.getValue().at()>=ttl
            ||item.getValue().future().isCompletedExceptionally()||item.getValue().future().isCancelled());}
    synchronized void invalidate(K key){entries.remove(key);}
    synchronized void clear(){entries.clear();}
    synchronized boolean contains(K key){return entries.containsKey(key);}
    synchronized int size(){return entries.size();}
}
