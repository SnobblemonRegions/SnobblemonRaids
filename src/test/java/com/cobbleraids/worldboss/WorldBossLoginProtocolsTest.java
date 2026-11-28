package com.cobbleraids.worldboss;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossLoginProtocolsTest {
    @BeforeEach void reset(){WorldBossLoginProtocols.started();}
    @Test void actualSetupMustFinishOnTheEventLoopBeforeMainCanConsumeIt()throws Exception{
        var work=new LinkedBlockingQueue<Runnable>();var protocol=new Object();var switches=new AtomicInteger();var thread=new AtomicReference<Thread>();
        var prepared=WorldBossLoginProtocols.prepare(protocol,work::add,value->{assertSame(protocol,value);thread.set(Thread.currentThread());switches.incrementAndGet();});
        assertFalse(prepared.ready().isDone());assertNull(prepared.protocolForMain());assertFalse(prepared.consume(protocol));
        var loop=Thread.ofPlatform().start(work.take());loop.join(3000);assertFalse(loop.isAlive());prepared.ready().get(2,TimeUnit.SECONDS);
        assertEquals(1,switches.get());assertNotSame(Thread.currentThread(),thread.get());assertSame(protocol,prepared.protocolForMain());
        assertTrue(prepared.consume(protocol));assertNull(prepared.protocolForMain());assertFalse(prepared.consume(protocol));
        assertTrue(WorldBossLoginProtocols.diagnostics().contains("queued=1, prepared=1, consumed=1, failed=0, fallbacks=0"));
    }
    @Test void failedNativeSetupCannotReleaseAnAdmissionCallback(){
        var prepared=WorldBossLoginProtocols.prepare(new Object(),Runnable::run,value->{throw new IllegalStateException("native setup failed");});
        assertTrue(prepared.ready().isCompletedExceptionally());assertNull(prepared.protocolForMain());assertFalse(prepared.consume(new Object()));
        var queue=new WorldBossLoginPacer.Queue();var rejected=new AtomicInteger();
        queue.offer(new Object(),prepared.ready(),()->true,()->fail("failed setup admitted a player"),rejected::incrementAndGet,0);
        queue.tick(1);assertEquals(1,rejected.get());assertTrue(WorldBossLoginProtocols.diagnostics().contains("failed=1"));
    }
    @Test void onlyTheExactPreparedBindingCanBeConsumedOnMain(){
        var protocol=new Object();var prepared=WorldBossLoginProtocols.prepare(protocol,Runnable::run,value->{});
        assertThrows(IllegalStateException.class,()->prepared.consume(new Object()));assertSame(protocol,prepared.protocolForMain());assertTrue(prepared.consume(protocol));
    }
    @Test void preparationDoesNotSkipItsOwnNativeSwitchAndForeignThreadsCannotConsume()throws Exception{
        var protocol=new Object();var work=new ArrayDeque<Runnable>();
        var prepared=WorldBossLoginProtocols.prepare(protocol,work::add,value->{});
        assertFalse(prepared.consume(protocol));work.remove().run();
        var result=new AtomicBoolean(true);var foreign=Thread.ofPlatform().start(()->{assertNull(prepared.protocolForMain());result.set(prepared.consume(protocol));});
        foreign.join(3000);assertFalse(foreign.isAlive());assertFalse(result.get());assertTrue(prepared.consume(protocol));
    }
    @Test void oldServerCompletionsCannotContaminateNewServerCounters(){
        var work=new ArrayDeque<Runnable>();var prepared=WorldBossLoginProtocols.prepare(new Object(),work::add,value->{});
        WorldBossLoginProtocols.started();work.remove().run();assertTrue(prepared.ready().isDone());
        assertTrue(prepared.consume(prepared.protocolForMain()));assertTrue(WorldBossLoginProtocols.diagnostics().startsWith("queued=0, prepared=0, consumed=0, failed=0"));
    }
    @Test void additionalMethodWrappersDisablePreparationInsteadOfDuplicatingTheirEffects(){
        String nativeName="setupOutboundProtocol";
        assertTrue(WorldBossLoginProtocols.singleWrapper(List.of(nativeName,nativeName+"$mixinextras$wrapped$1","mixinextras$bridge$"+nativeName+"$mixinextras$wrapped$1"),nativeName));
        assertFalse(WorldBossLoginProtocols.singleWrapper(List.of(nativeName),nativeName));
        assertFalse(WorldBossLoginProtocols.singleWrapper(List.of(nativeName+"$mixinextras$wrapped$1",nativeName+"$mixinextras$wrapped$2"),nativeName));
    }
    @Test void rejectedEventLoopSubmissionCannotLeaveAReadyAdmission(){
        var prepared=WorldBossLoginProtocols.prepare(new Object(),work->{throw new RejectedExecutionException();},value->fail("rejected work"));
        assertTrue(prepared.ready().isCompletedExceptionally());assertNull(prepared.protocolForMain());
    }
}
