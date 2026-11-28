package com.cobbleraids.worldboss;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossCardLogsTest {
    private static final org.slf4j.Logger LOGGER=NOPLogger.NOP_LOGGER;
    @Test void slowInfoOutputRunsOffMainInOrderWithoutInheritingServerContexts() throws Exception {
        var context=new InheritableThreadLocal<Object>();context.set(new Object());
        var owner=Thread.currentThread();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var messages=new CopyOnWriteArrayList<String>();
        var writer=new WorldBossCardLogs.Writer((logger,message)->{
            assertNotSame(owner,Thread.currentThread());assertNull(context.get());entered.countDown();
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("not released");}
            catch(InterruptedException stop){throw new IllegalStateException(stop);}
            messages.add(message);
        });
        try{
            assertTrue(writer.offer(LOGGER,"first"));assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertTrue(writer.offer(LOGGER,"second"));assertTrue(messages.isEmpty());
            release.countDown();writer.close();assertEquals(List.of("first","second"),messages);
        }finally{release.countDown();writer.close();context.remove();}
    }
    @Test void capacityRefusesWithoutCallerRunsAndShutdownDrainsAcceptedInfo() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var delivered=new AtomicInteger();
        var writer=new WorldBossCardLogs.Writer((logger,message)->{
            entered.countDown();
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("not released");}
            catch(InterruptedException stop){throw new IllegalStateException(stop);}
            delivered.incrementAndGet();
        });
        try{
            assertTrue(writer.offer(LOGGER,"first"));assertTrue(entered.await(5,TimeUnit.SECONDS));
            for(int i=0;i<127;i++)assertTrue(writer.offer(LOGGER,"queued"));
            assertFalse(writer.offer(LOGGER,"native fallback"));assertEquals(0,delivered.get());
        }finally{release.countDown();writer.close();}
        assertEquals(128,delivered.get());assertFalse(writer.offer(LOGGER,"closed"));
    }
    @Test void failureIsReportedOnceAndRejectsLaterSubmissionWithoutSkippingAcceptedMessages() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var delivered=new CopyOnWriteArrayList<String>();
        var failure=new IllegalStateException("output failed");
        var writer=new WorldBossCardLogs.Writer((logger,message)->{
            entered.countDown();
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("not released");}
            catch(InterruptedException stop){throw new IllegalStateException(stop);}
            if(message.equals("bad"))throw failure;delivered.add(message);
        });
        assertTrue(writer.offer(LOGGER,"bad"));assertTrue(entered.await(5,TimeUnit.SECONDS));
        assertTrue(writer.offer(LOGGER,"good"));release.countDown();writer.close();
        assertEquals(List.of("good"),delivered);assertSame(failure,writer.pollFailure());assertNull(writer.pollFailure());
        assertFalse(writer.offer(LOGGER,"native fallback"));
    }
    @Test void interruptedCloseWaitsForAcceptedOutputAndRestoresInterruption() throws Exception {
        var release=new CountDownLatch(1);var delivered=new CountDownLatch(1);
        var writer=new WorldBossCardLogs.Writer((logger,message)->{
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("not released");}
            catch(InterruptedException stop){throw new IllegalStateException(stop);}
            delivered.countDown();
        });
        assertTrue(writer.offer(LOGGER,"accepted"));
        var releaser=Thread.ofPlatform().start(()->{try{Thread.sleep(25);}catch(InterruptedException ignored){}release.countDown();});
        try{
            Thread.currentThread().interrupt();writer.close();
            assertEquals(0,delivered.getCount());assertTrue(Thread.currentThread().isInterrupted());
        }finally{Thread.interrupted();release.countDown();releaser.join();}
    }
}
