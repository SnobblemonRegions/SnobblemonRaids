package com.cobbleraids.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.*;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;

/** Process-owned native diagnostic output. Log4j owns dispatcher draining and shutdown ordering. */
final class WorldBossConsoleLogs {
    private static Configuration installed;
    private static int consoles,rollingLogs;
    private WorldBossConsoleLogs(){}
    static void install(){
        try{install((LoggerContext)LogManager.getContext(false));}
        catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:console-context",failure);}
    }
    static void install(LoggerContext context){
        try{
            var configuration=context.getConfiguration();
            if(configuration==installed)return;
            int count=0,files=0;
            var loggers=new ArrayList<>(configuration.getLoggers().values());loggers.add(configuration.getRootLogger());
            for(var appender:new ArrayList<>(configuration.getAppenders().values())){
                if(!(appender instanceof AbstractAppender sink)||sink.getFilter()!=null
                        ||!(sink instanceof ConsoleAppender||sink instanceof RollingRandomAccessFileAppender))continue;
                // Undeclared programmatic controls can carry levels/filters absent from AppenderRef.
                // Keep those links native instead of replacing them with guessed configuration.
                if(loggers.stream().noneMatch(logger->logger.getAppenders().get(sink.getName())==sink
                        &&logger.getAppenderRefs().stream().anyMatch(ref->ref.getRef().equals(sink.getName()))))continue;
                String name=(sink instanceof ConsoleAppender?"CobbleRaidsAsyncConsole-":"CobbleRaidsAsyncRolling-")+sink.getName();
                if(configuration.getAppenders().containsKey(name))continue;
                var async=cleanBootstrap(()->{
                    var value=AsyncAppender.newBuilder().setName(name).setConfiguration(configuration)
                            .setAppenderRefs(new AppenderRef[]{AppenderRef.createAppenderRef(sink.getName(),null,null)})
                            .setBufferSize(2048).setBlocking(true).setIncludeLocation(false).build();
                    if(value==null)throw new IllegalStateException("Native async diagnostic output could not be built");
                    value.start();return value;
                });
                configuration.addAppender(async); // Native shutdown/reconfiguration stops async appenders before their sinks.
                var snapshot=new Snapshot(sink,async);snapshot.start();
                for(var logger:loggers){
                    if(logger.getAppenders().get(sink.getName())!=sink)continue;
                    var ref=logger.getAppenderRefs().stream().filter(r->r.getRef().equals(sink.getName())).findFirst().orElse(null);
                    if(ref==null)continue;
                    logger.removeAppender(sink.getName());logger.addAppender(snapshot,ref.getLevel(),ref.getFilter());
                }
                if(sink instanceof ConsoleAppender)count++;else files++;
            }
            installed=configuration;consoles=count;rollingLogs=files;context.updateLoggers();
        }catch(RuntimeException failure){RaidFaultBarrier.report("worldboss:console-output",failure);}
    }
    static String diagnostics(){return LogManager.getContext(false) instanceof LoggerContext context&&context.getConfiguration()==installed
            ?"managed async consoles="+consoles+", rolling logs="+rollingLogs:"native console configuration";}

    static <T> T cleanBootstrap(Supplier<T> build){
        var value=new CompletableFuture<T>();
        Thread.ofPlatform().daemon(true).inheritInheritableThreadLocals(false).name("World Boss console bootstrap").start(()->{
            try{value.complete(build.get());}catch(Throwable failure){value.completeExceptionally(failure);}
        });
        boolean interrupted=false;
        try{while(true)try{return value.get();}catch(InterruptedException stop){interrupted=true;}
            catch(ExecutionException failure){throw new IllegalStateException("Native console bootstrap failed",failure.getCause());}}
        finally{if(interrupted)Thread.currentThread().interrupt();}
    }
    static LogEvent freeze(LogEvent event){
        var data=new SortedArrayStringMap();event.getContextData().forEach((key,value)->data.putValue(key,value==null?null:String.valueOf((Object)value)));data.freeze();
        return new Log4jLogEvent.Builder(event).setMessage(new SimpleMessage(event.getMessage().getFormattedMessage()))
                .setContextData(data).setContextStack(event.getContextStack().copy()).setSource(event.getSource())
                .setThrownProxy(event.getThrownProxy()).build();
    }
    private static final class Snapshot extends AbstractAppender {
        private final Appender original;
        private final AsyncAppender async;
        private Snapshot(Appender original,AsyncAppender async){super(original.getName(),null,original.getLayout(),original.ignoreExceptions(),Property.EMPTY_ARRAY);this.original=original;this.async=async;}
        @Override public void append(LogEvent event){
            try{async.append(freeze(event));}
            catch(RuntimeException failure){original.append(event);} // Native fallback preserves the log if dispatch is unavailable.
        }
    }
}
