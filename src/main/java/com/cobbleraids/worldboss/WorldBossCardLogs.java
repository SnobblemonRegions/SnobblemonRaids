package com.cobbleraids.worldboss;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.slf4j.Logger;

/** Bounded INFO output only, not an economic receipt. No actor references or inherited server contexts. */
public final class WorldBossCardLogs {
    private static Writer writer;
    private static Thread serverThread;
    private WorldBossCardLogs(){}
    static void started(){if(writer!=null)throw new IllegalStateException("Previous Cards log writer has not stopped");serverThread=Thread.currentThread();}
    static void stopped(){writer=null;serverThread=null;}
    public static boolean defer(Logger logger,String message){
        if(Thread.currentThread()!=serverThread)return false;
        if(writer==null)writer=new Writer(Logger::info);
        return writer.offer(logger,message);
    }
    static String diagnostics(){return writer==null?"idle":writer.diagnostics();}
    static RuntimeException pollFailure(){return writer==null?null:writer.pollFailure();}
    static void close(){if(writer!=null)writer.close();}
    @FunctionalInterface interface Sink{void info(Logger logger,String message);}
    static final class Writer implements AutoCloseable {
        private final Thread owner=Thread.currentThread();
        private final Sink sink;
        private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(127),task->Thread.ofPlatform().daemon(true).inheritInheritableThreadLocals(false)
                .name("World Boss Cards info output").unstarted(task),new ThreadPoolExecutor.AbortPolicy());
        private final AtomicInteger pending=new AtomicInteger();
        private final AtomicLong accepted=new AtomicLong(),delivered=new AtomicLong();
        private final AtomicReference<RuntimeException> failure=new AtomicReference<>();
        private boolean closed,failureReported;
        Writer(Sink sink){this.sink=java.util.Objects.requireNonNull(sink);}
        boolean offer(Logger logger,String message){
            requireOwner();
            if(closed||failure.get()!=null||message==null||message.length()>65536)return false;
            pending.incrementAndGet();
            try{
                executor.execute(()->{
                    try{sink.info(logger,message);delivered.incrementAndGet();}
                    catch(RuntimeException error){failure.compareAndSet(null,error);}
                    finally{pending.decrementAndGet();}
                });
                accepted.incrementAndGet();return true;
            }catch(RejectedExecutionException full){pending.decrementAndGet();return false;}
        }
        String diagnostics(){return "pending="+pending.get()+", accepted="+accepted.get()+", delivered="+delivered.get()+", failed="+(failure.get()!=null);}
        RuntimeException pollFailure(){requireOwner();var error=failure.get();if(error==null||failureReported)return null;failureReported=true;return error;}
        @Override public void close(){
            requireOwner();closed=true;executor.shutdown();boolean interrupted=false;
            try{
                while(!executor.isTerminated()){
                    try{executor.awaitTermination(1,TimeUnit.SECONDS);}
                    catch(InterruptedException stop){interrupted=true;}
                }
            }finally{if(interrupted)Thread.currentThread().interrupt();}
        }
        private void requireOwner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Cards info submission requires its owning server thread");}
    }
}
