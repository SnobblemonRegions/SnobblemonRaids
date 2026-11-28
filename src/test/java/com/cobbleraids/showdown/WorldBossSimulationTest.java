package com.cobbleraids.showdown;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

class WorldBossSimulationTest {
    @Test void privateEngineFactoryDoesNotInheritServerThreadContexts() throws Exception {
        var context=new InheritableThreadLocal<Object>();context.set(new Object());
        var inherited=new AtomicReference<Object>();var loader=new AtomicReference<ClassLoader>();
        try(var simulation=new WorldBossSimulation(output->{inherited.set(context.get());loader.set(Thread.currentThread().getContextClassLoader());return new Engine(output);})){ 
            simulation.awaitReady();assertNull(inherited.get());assertSame(Thread.currentThread().getContextClassLoader(),loader.get());
        }finally{context.remove();}
    }

    @Test void simulationAndItsCloseNeverRunOnTheServerCaller() throws Exception {
        Thread caller = Thread.currentThread(); var closed = new AtomicBoolean(); var started = new CountDownLatch(1);
        try (var simulation = new WorldBossSimulation(output -> new WorldBossSimulation.Engine() {
            public void start(UUID id, String[] messages) { assertNotSame(caller, Thread.currentThread()); output.accept(id.toString(), "initial request"); started.countDown(); }
            public void send(UUID id, String[] messages) { assertNotSame(caller, Thread.currentThread()); }
            public void end(UUID id) { assertNotSame(caller, Thread.currentThread()); }
            public void close() { assertNotSame(caller, Thread.currentThread()); closed.set(true); }
        })) {
            simulation.awaitReady(); simulation.start(UUID.randomUUID(), new String[] {"start"});
            assertTrue(started.await(5, TimeUnit.SECONDS));
        }
        assertTrue(closed.get());
    }

    @Test void cancellationBeforeNativeStartFinishesDropsItsLateOutputAndEndsInOrder() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var ended = new CountDownLatch(1);
        var calls = new CopyOnWriteArrayList<String>(); UUID id = UUID.randomUUID();
        var simulation = new WorldBossSimulation(output -> new WorldBossSimulation.Engine() {
            public void start(UUID battle, String[] messages) throws Exception {
                assertNotSame(Thread.currentThread(), SERVER.get()); calls.add("start"); entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS)); output.accept(battle.toString(), "late request");
            }
            public void send(UUID battle, String[] messages) { calls.add("send"); }
            public void end(UUID battle) { calls.add("end"); ended.countDown(); }
            public void close() {}
        });
        try {
            SERVER.set(Thread.currentThread()); simulation.awaitReady(); simulation.start(id, new String[] {"native"});
            assertTrue(entered.await(5, TimeUnit.SECONDS)); simulation.end(id); release.countDown();
            assertTrue(ended.await(5, TimeUnit.SECONDS));
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (simulation.phase(id) != WorldBossSimulation.Phase.CLOSED && System.nanoTime() < deadline) { simulation.poll(); Thread.yield(); }
            assertEquals(WorldBossSimulation.Phase.CLOSED, simulation.phase(id)); assertEquals(List.of("start", "end"), calls);
            assertNull(simulation.poll());
        } finally { release.countDown(); simulation.close(); SERVER.set(null); }
    }
    private static final AtomicReference<Thread> SERVER = new AtomicReference<>();

    private static class Engine implements WorldBossSimulation.Engine {
        final BiConsumer<String, String> output;
        Engine(BiConsumer<String, String> output) { this.output = output; }
        public void start(UUID id, String[] messages) throws Exception { output.accept(id.toString(), "initial request"); }
        public void send(UUID id, String[] messages) throws Exception { output.accept(id.toString(), "turn request"); }
        public void end(UUID id) throws Exception {}
        public void close() throws Exception {}
    }
    private static WorldBossSimulation.Event next(WorldBossSimulation simulation) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        WorldBossSimulation.Event event;
        while ((event = simulation.poll()) == null && simulation.failure() == null && System.nanoTime() < deadline) Thread.sleep(1);
        assertNotNull(event, () -> "No event; failure=" + simulation.failure()); return event;
    }
    @Test void copiedMessagesAndStartAcknowledgementBelongToTheExactTicket() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var received = new AtomicReference<String>();
        var simulation = new WorldBossSimulation(output -> new Engine(output) {
            @Override public void start(UUID id, String[] messages) throws Exception {
                entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); received.set(messages[0]); super.start(id, messages);
            }
        });
        try {
            simulation.awaitReady(); UUID id = UUID.randomUUID(); String[] messages = {"original"}; simulation.start(id, messages);
            assertTrue(entered.await(5, TimeUnit.SECONDS)); messages[0] = "mutated";
            assertEquals(WorldBossSimulation.Phase.PENDING, simulation.phase(id)); assertNull(simulation.poll());
            release.countDown(); var event = next(simulation);
            assertEquals(id, event.id()); assertEquals(WorldBossSimulation.Kind.STARTED, event.kind());
            assertEquals(List.of("initial request"), event.messages()); assertEquals("original", received.get());
            assertEquals(WorldBossSimulation.Phase.OPEN, simulation.phase(id));
        } finally { release.countDown(); simulation.close(); }
    }
    @Test void partialFailedOperationInvalidatesItsOutputAndEarlierUndeliveredOutput() throws Exception {
        var disposed = new CountDownLatch(1); var cause = new IllegalStateException("native engine failed");
        var simulation = new WorldBossSimulation(output -> new Engine(output) {
            @Override public void send(UUID id, String[] messages) { output.accept(id.toString(), "partial"); throw cause; }
            @Override public void close() { disposed.countDown(); }
        });
        simulation.awaitReady(); UUID id = UUID.randomUUID(); simulation.start(id, new String[] {"start"}); simulation.send(id, new String[] {"move"});
        assertTrue(disposed.await(5, TimeUnit.SECONDS)); assertSame(cause, simulation.failure()); assertNull(simulation.poll());
        assertThrows(IllegalStateException.class, simulation::close); assertTrue(simulation.terminated());
    }
    @Test void inputSaturationFailsClosedWithoutCallerFallbackOrPendingMovesRunning() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var moves = new AtomicInteger();
        var simulation = new WorldBossSimulation(output -> new Engine(output) {
            @Override public void start(UUID id, String[] messages) throws Exception { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); super.start(id, messages); }
            @Override public void send(UUID id, String[] messages) { moves.incrementAndGet(); }
        });
        try {
            simulation.awaitReady(); UUID id = UUID.randomUUID(); simulation.start(id, new String[] {"start"}); assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int n = 0; n < 512; n++) simulation.send(id, new String[] {"move"});
            assertThrows(RejectedExecutionException.class, () -> simulation.send(id, new String[] {"overflow"}));
            assertNotNull(simulation.failure()); release.countDown();
            assertThrows(IllegalStateException.class, simulation::close); assertEquals(0, moves.get());
        } finally { release.countDown(); if (!simulation.terminated()) assertThrows(Exception.class, simulation::close); }
    }
    @Test void foreignOutputFailsWithoutPublishingARequestForAnotherBattle() throws Exception {
        var disposed = new CountDownLatch(1);
        var simulation = new WorldBossSimulation(output -> new Engine(output) {
            @Override public void start(UUID id, String[] messages) { output.accept(UUID.randomUUID().toString(), "foreign"); }
            @Override public void close() { disposed.countDown(); }
        });
        simulation.awaitReady(); simulation.start(UUID.randomUUID(), new String[] {"start"});
        assertTrue(disposed.await(5, TimeUnit.SECONDS)); assertNotNull(simulation.failure()); assertNull(simulation.poll());
        assertThrows(IllegalStateException.class, simulation::close);
    }
    @Test void closedTicketCannotConsumeFurtherMovesOrBeReused() throws Exception {
        var moves = new AtomicInteger();
        try (var simulation = new WorldBossSimulation(output -> new Engine(output) {
            @Override public void send(UUID id, String[] messages) { moves.incrementAndGet(); }
        })) {
            simulation.awaitReady(); UUID id = UUID.randomUUID(); simulation.start(id, new String[] {"start"}); next(simulation);
            simulation.end(id); assertEquals(WorldBossSimulation.Kind.CLOSED, next(simulation).kind());
            simulation.send(id, new String[] {"late"}); assertEquals(0, moves.get());
            assertThrows(IllegalStateException.class, () -> simulation.start(id, new String[] {"reuse"}));
        }
    }
    @Test void neutralNestedStartScopeCannotRouteAnOrdinaryBattleAsAWorldBoss() {
        UUID encounter = UUID.randomUUID(); assertFalse(WorldBossSimulator.starting());
        try (var outer = WorldBossSimulator.starting(encounter)) {
            assertTrue(WorldBossSimulator.starting());
            try (var ordinary = WorldBossSimulator.starting(null)) { assertFalse(WorldBossSimulator.starting()); }
            assertTrue(WorldBossSimulator.starting());
        }
        assertFalse(WorldBossSimulator.starting());
    }
    @Test void startScopeCanClaimOnlyItsExactBossAndOnlyOnce() {
        UUID boss = UUID.randomUUID();
        try (var scope = WorldBossSimulator.starting(UUID.randomUUID(), boss)) {
            assertFalse(WorldBossSimulator.claimStart(UUID.randomUUID()));
            assertTrue(WorldBossSimulator.claimStart(boss));
            assertFalse(WorldBossSimulator.claimStart(boss));
        }
    }
    @Test void undeliveredOutputBudgetFailsClosedAndDisposesThePrivateEngine() throws Exception {
        var sent = new AtomicInteger(); var disposed = new CountDownLatch(1);
        var simulation = new WorldBossSimulation(output -> new Engine(output) {
            @Override public void send(UUID id, String[] messages) { output.accept(id.toString(), "request"); sent.incrementAndGet(); }
            @Override public void close() { disposed.countDown(); }
        });
        try {
            simulation.awaitReady(); UUID id = UUID.randomUUID(); simulation.start(id, new String[] {"start"}); next(simulation);
            for (int n = 1; n <= 513 && simulation.failure() == null; n++) {
                simulation.send(id, new String[] {"move"});
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (sent.get() < n && simulation.failure() == null && System.nanoTime() < deadline) Thread.sleep(1);
            }
            assertTrue(disposed.await(5, TimeUnit.SECONDS)); assertNotNull(simulation.failure());
            assertTrue(simulation.failure().getMessage().contains("output queue full")); assertNull(simulation.poll());
        } finally { assertThrows(IllegalStateException.class, simulation::close); }
    }
}
