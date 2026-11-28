package com.cobbleraids.worldboss;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossConsoleLogsTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;
    @Test void nativeRollingFileWritesOffCallerAndDrainsInOrderAtShutdown()throws Exception{
        rollingFixture(false,true);
    }
    @Test void customRollingFileFilterKeepsItsNativeCallerThread()throws Exception{
        rollingFixture(true,true);
    }
    @Test void programmaticRollingReferenceKeepsItsNativeThreshold()throws Exception{rollingFixture(false,false);}
    private void rollingFixture(boolean customFilter,boolean declared)throws Exception{
        Thread owner=Thread.currentThread();var writingThread=new AtomicReference<Thread>();
        var filterCalls=new java.util.concurrent.atomic.AtomicInteger();
        var layout=new org.apache.logging.log4j.core.layout.AbstractStringLayout(java.nio.charset.StandardCharsets.UTF_8){
            @Override public String toSerializable(org.apache.logging.log4j.core.LogEvent event){
                writingThread.set(Thread.currentThread());return event.getMessage().getFormattedMessage()+"\n";
            }
        };
        var context=new org.apache.logging.log4j.core.LoggerContext("isolated rolling fixture");
        var configuration=new org.apache.logging.log4j.core.config.DefaultConfiguration();
        for(var name:java.util.List.copyOf(configuration.getRootLogger().getAppenders().keySet()))configuration.getRootLogger().removeAppender(name);
        configuration.getRootLogger().setLevel(Level.INFO);
        var path=root.resolve("latest.log");
        var builder=org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender.newBuilder()
                .setName("NativeRollingFile").withFileName(path.toString()).withFilePattern(root.resolve("archive-%i.log").toString())
                .withPolicy(org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy.createPolicy("1MB"))
                .setLayout(layout).setConfiguration(configuration);
        if(customFilter)builder.setFilter(new org.apache.logging.log4j.core.filter.AbstractFilter(){
            @Override public Result filter(org.apache.logging.log4j.core.LogEvent event){assertSame(owner,Thread.currentThread());filterCalls.incrementAndGet();return Result.ACCEPT;}
        });
        var file=builder.build();assertNotNull(file);configuration.addAppender(file);
        var reference=org.apache.logging.log4j.core.config.AppenderRef.createAppenderRef(file.getName(),Level.INFO,null);
        var loggerBuilder=org.apache.logging.log4j.core.config.LoggerConfig.newBuilder().withLoggerName("fixture")
                .withLevel(Level.DEBUG).withAdditivity(false).withConfig(configuration);
        if(declared)loggerBuilder.withRefs(new org.apache.logging.log4j.core.config.AppenderRef[]{reference});
        var logger=loggerBuilder.build();logger.addAppender(file,Level.INFO,null);configuration.addLogger("fixture",logger);
        var actor=new Object(){@Override public String toString(){assertSame(owner,Thread.currentThread());return "frozen";}};
        try{
            context.start(configuration);WorldBossConsoleLogs.install(context);
            context.getLogger("fixture").debug("must remain filtered by the native appender reference");
            for(int i=0;i<64;i++)context.getLogger("fixture").info("event {} {}",i,actor);
        }finally{context.stop();}
        assertEquals(java.util.stream.IntStream.range(0,64).mapToObj(i->"event "+i+" frozen").toList(),java.nio.file.Files.readAllLines(path));
        assertNotNull(writingThread.get());
        if(customFilter||!declared){assertSame(owner,writingThread.get());assertEquals(customFilter?64:0,filterCalls.get());}
        else assertNotSame(owner,writingThread.get(),"Native file I/O must not run on the game caller");
    }
    @Test void messageAndContextAreDetachedOnCallerWhileNativeMetadataIsPreserved(){
        Thread owner=Thread.currentThread();var value=new AtomicReference<>("before");
        var actor=new Object(){@Override public String toString(){assertSame(owner,Thread.currentThread());return value.get();}};
        var context=new SortedArrayStringMap();context.putValue("fixture",actor);
        var source=new StackTraceElement("OriginalCaller","login","Original.java",12);
        var event=Log4jLogEvent.newBuilder().setLoggerName("native.fixture").setLevel(Level.WARN).setThreadName("Server thread")
                .setTimeMillis(1234).setMessage(new ParameterizedMessage("login {}",actor)).setContextData(context).setSource(source).build();
        var frozen=WorldBossConsoleLogs.freeze(event);value.set("after");
        assertEquals("login before",frozen.getMessage().getFormattedMessage());assertEquals("before",frozen.getContextData().getValue("fixture"));
        assertEquals("native.fixture",frozen.getLoggerName());assertEquals(Level.WARN,frozen.getLevel());
        assertEquals("Server thread",frozen.getThreadName());assertEquals(1234,frozen.getTimeMillis());assertEquals(source,frozen.getSource());
    }
    @Test void nativeDispatcherBootstrapCannotInheritGameThreadContext(){
        var context=new InheritableThreadLocal<String>();context.set("must stay on Main");
        try{assertNull(WorldBossConsoleLogs.cleanBootstrap(context::get));assertEquals("must stay on Main",context.get());}
        finally{context.remove();}
    }
}
