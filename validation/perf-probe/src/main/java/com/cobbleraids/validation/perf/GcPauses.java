package com.cobbleraids.validation.perf;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.ManagementFactory;
import java.util.*;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;

/** Off-thread VM notifications only; no allocation or clock query on the measured tick path. */
final class GcPauses {
    private static final List<Pause> events=new ArrayList<>();
    private static int dropped;
    private record Pause(long startEpochMillis,long durationMillis,String collector,String action,String cause){}
    static void initialize(){
        long boot=ManagementFactory.getRuntimeMXBean().getStartTime();
        for(var collector:ManagementFactory.getGarbageCollectorMXBeans())if(collector instanceof NotificationEmitter emitter){
            emitter.addNotificationListener((notification,handback)->{
                if(!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType()))return;
                var info=GarbageCollectionNotificationInfo.from((CompositeData)notification.getUserData());
                var gc=info.getGcInfo();
                synchronized(events){
                    if(events.size()<1024)events.add(new Pause(boot+gc.getStartTime(),gc.getDuration(),info.getGcName(),info.getGcAction(),info.getGcCause()));
                    else dropped++;
                }
            },null,null);
        }
    }
    static Map<String,Object> snapshot(){
        synchronized(events){return Map.of("events",new ArrayList<>(events),"dropped",dropped);}
    }
}
