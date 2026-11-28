package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbleraids.config.RecoveryWitness;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.nbt.CompoundTag;

class WorldBossCheckpointsTest {
    @org.junit.jupiter.api.BeforeAll static void initializeVersion() { net.minecraft.SharedConstants.tryDetectVersion(); }
    @TempDir Path directory;
    private Path file() { return directory.resolve("state.journal"); }
    private Path head() { return directory.resolve("journal.head"); }
    private static CompoundTag root(int value) {
        var root = new CompoundTag(); var data = new CompoundTag(); data.putInt("value", value); root.put("data", data); return root;
    }

    @Test void nativeAutomaticSaveUsesTheSameAcknowledgedWriterAndRetainsLaterDirtyChanges() throws Exception {
        Thread caller=Thread.currentThread();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        var journal=WorldBossJournal.open(file(),head(),null,WorldBossJournal.COMPACT_AT,(path,anchor)->{
            if(attempts.getAndIncrement()==0){
                assertNotSame(caller,Thread.currentThread(),"Native autosave must not force the journal on Main");
                entered.countDown();
                try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("not released");}
                catch(InterruptedException stop){throw new IOException(stop);}
            }
            RecoveryWitness.write(path,anchor);
        });
        var state=new WorldBossState(journal);UUID player=UUID.randomUUID();
        try{
            state.benefits(state.benefits().effects(player,WorldBossBenefits.Effects.MINIMAL));
            try(var scope=new WorldBossAutosaveScope(true)){
                state.save(directory.resolve("projection.dat").toFile(),null);
                assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(state.checkpointPending());
                assertEquals(WorldBossBenefits.Effects.FULL,state.benefitsView().effects(player));
                state.benefits(state.benefits().effects(player,WorldBossBenefits.Effects.OFF));
                state.save(directory.resolve("projection.dat").toFile(),null);
                assertTrue(state.isDirty(),"A pending older snapshot cannot clear newer mutations");
            }
            release.countDown();state.closeCheckpoints();
            assertEquals(WorldBossBenefits.Effects.MINIMAL,state.benefitsView().effects(player));
            state.save(directory.resolve("projection.dat").toFile(),null);
            assertFalse(state.isDirty());
            var recovered=WorldBossState.load(WorldBossJournal.open(file(),head(),null).snapshot().getCompound("data"),null);
            assertEquals(WorldBossBenefits.Effects.OFF,recovered.benefits().effects(player));
        }finally{release.countDown();state.closeCheckpoints();}
    }

    @Test void directNativeSaveDrainsAcceptedAutomaticSnapshotEvenWhenNotDirty() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var journal=WorldBossJournal.open(file(),head(),null,WorldBossJournal.COMPACT_AT,(path,anchor)->{
            entered.countDown();
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("not released");}
            catch(InterruptedException stop){throw new IOException(stop);}
            RecoveryWitness.write(path,anchor);
        });
        var state=new WorldBossState(journal);state.touch();
        try(var scope=new WorldBossAutosaveScope(true)){state.save(directory.resolve("projection.dat").toFile(),null);}
        assertTrue(entered.await(5,TimeUnit.SECONDS));assertFalse(state.isDirty());
        var releaser=Thread.ofPlatform().start(()->{try{Thread.sleep(25);}catch(InterruptedException ignored){}release.countDown();});
        try{
            state.save(directory.resolve("projection.dat").toFile(),null);
            assertEquals(1,journal.sequence());assertFalse(state.checkpointPending());
        }finally{release.countDown();releaser.join();state.closeCheckpoints();}
    }

    @Test void journalWorkerDoesNotInheritServerThreadContexts() throws Exception {
        var context=new InheritableThreadLocal<Object>();context.set(new Object());
        var inherited=new java.util.concurrent.atomic.AtomicReference<Object>();
        var journal=WorldBossJournal.open(file(),head(),null,WorldBossJournal.COMPACT_AT,(path,anchor)->{
            inherited.set(context.get());RecoveryWitness.write(path,anchor);
        });
        try(var writer=new WorldBossCheckpoints(journal)){
            writer.submit(root(1),List.of());writer.drain();assertNull(inherited.get());
        }finally{context.remove();}
    }

    @Test void slowWitnessRunsOffCallerAndCannotPublishBeforeDurability() throws Exception {
        Thread caller = Thread.currentThread();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var journal = WorldBossJournal.open(file(), head(), null, WorldBossJournal.COMPACT_AT, (path, anchor) -> {
            assertNotSame(caller, Thread.currentThread(), "periodic disk force must leave the tick thread");
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test writer not released"); }
            catch (InterruptedException stop) { Thread.currentThread().interrupt(); throw new IOException(stop); }
            RecoveryWitness.write(path, anchor);
        });
        var writer = new WorldBossCheckpoints(journal);
        try {
            var input = root(1); writer.submit(input, List.of());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            input.getCompound("data").putInt("value", 99);
            assertTrue(writer.pending()); assertFalse(writer.poll());
            assertEquals(0, journal.sequence()); assertNull(journal.snapshot());
            assertThrows(IllegalStateException.class, () -> writer.submit(root(2), List.of()));
            release.countDown(); writer.drain();
            assertFalse(writer.pending()); assertEquals(1, journal.sequence());
            assertEquals(root(1), journal.snapshot());
            assertEquals(root(1), WorldBossJournal.open(file(), head(), null).snapshot());
        } finally { release.countDown(); writer.close(); }
    }

    @Test void failedWitnessNeverPublishesPromisesAndPreventsAnyLaterWrite() throws Exception {
        var journal = WorldBossJournal.open(file(), head(), null, WorldBossJournal.COMPACT_AT,
                (path, anchor) -> { throw new IOException("device failed after journal force"); });
        var writer = new WorldBossCheckpoints(journal);
        var payout = UUID.randomUUID(); var promise = new WorldBossJournal.Promise(payout, Map.of(UUID.randomUUID(), 10), false, true);
        writer.submit(root(1), List.of(promise));
        assertThrows(IOException.class, writer::drain);
        assertTrue(journal.blocked()); assertEquals(0, journal.sequence()); assertNull(journal.promise(payout));
        assertThrows(IOException.class, () -> writer.submit(root(2), List.of()));
        assertThrows(IOException.class, writer::close);
        var recovered = WorldBossJournal.open(file(), head(), null);
        assertEquals(1, recovered.sequence()); assertEquals(promise, recovered.promise(payout));
    }

    @Test void closeDrainsAcceptedCheckpointAndRejectsFutureWorkWithoutReordering() throws Exception {
        var journal = WorldBossJournal.open(file(), head(), null);
        var writer = new WorldBossCheckpoints(journal);
        writer.submit(root(1), List.of()); writer.drain();
        journal.commit(root(2), List.of());
        writer.submit(root(3), List.of()); writer.close();
        assertEquals(3, journal.sequence());
        assertEquals(root(3), WorldBossJournal.open(file(), head(), null).snapshot());
        assertThrows(IOException.class, () -> writer.submit(root(4), List.of()));
    }

    @Test void slowStorageBecomesOverdueWithoutBlockingPollOrAcceptingMoreWork() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var now = new java.util.concurrent.atomic.AtomicLong(100);
        var journal = WorldBossJournal.open(file(), head(), null, WorldBossJournal.COMPACT_AT, (path, anchor) -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("not released"); }
            catch (InterruptedException stop) { throw new IOException(stop); }
            RecoveryWitness.write(path, anchor);
        });
        var writer = new WorldBossCheckpoints(journal, now::get);
        try {
            writer.submit(root(1), List.of()); assertTrue(entered.await(5, TimeUnit.SECONDS));
            now.addAndGet(TimeUnit.SECONDS.toNanos(2) - 1); assertFalse(writer.overdue());
            now.incrementAndGet(); assertTrue(writer.overdue()); assertFalse(writer.poll());
            assertThrows(IllegalStateException.class, () -> writer.submit(root(2), List.of()));
            release.countDown(); writer.drain(); assertFalse(writer.overdue());
        } finally { release.countDown(); writer.close(); }
    }

    @Test void statePublishesOnlyCapturedViewsAndKeepsLaterChangesDirty() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var journal = WorldBossJournal.open(file(), head(), null, WorldBossJournal.COMPACT_AT, (path, anchor) -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("not released"); }
            catch (InterruptedException stop) { throw new IOException(stop); }
            RecoveryWitness.write(path, anchor);
        });
        var state = new WorldBossState(journal); UUID player = UUID.randomUUID();
        try {
            state.benefits(state.benefits().effects(player, WorldBossBenefits.Effects.MINIMAL));
            long receipt = state.checkpointAsyncSnapshot(null); assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(state.checkpointAcknowledged(receipt));
            state.benefits(state.benefits().effects(player, WorldBossBenefits.Effects.OFF));
            state.pollCheckpoint();
            assertEquals(WorldBossBenefits.Effects.FULL, state.benefitsView().effects(player));
            release.countDown(); state.closeCheckpoints();
            assertTrue(state.checkpointAcknowledged(receipt));
            assertEquals(WorldBossBenefits.Effects.MINIMAL, state.benefitsView().effects(player));
            assertEquals(WorldBossBenefits.Effects.OFF, state.benefits().effects(player));
            assertTrue(state.isDirty());
            var recovered = WorldBossState.load(WorldBossJournal.open(file(), head(), null).snapshot().getCompound("data"), null);
            assertFalse(recovered.isQuarantined());
            assertEquals(WorldBossBenefits.Effects.MINIMAL, recovered.benefits().effects(player));
        } finally { release.countDown(); state.closeCheckpoints(); }
    }

    @Test void failedWitnessCannotAcknowledgeATravelContinuationReceipt() throws Exception {
        var journal=WorldBossJournal.open(file(),head(),null,WorldBossJournal.COMPACT_AT,(path,anchor)->{throw new IOException("head failed");});
        var state=new WorldBossState(journal);
        long receipt=state.checkpointAsyncSnapshot(null);
        assertThrows(IOException.class,state::closeCheckpoints);
        assertFalse(state.checkpointAcknowledged(receipt)); assertTrue(state.isQuarantined());
    }

    @Test void interruptedDrainStillWaitsForAcceptedIoBeforeClosing() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var closing = new CountDownLatch(1); var closed = new CountDownLatch(1);
        var journal = WorldBossJournal.open(file(), head(), null, WorldBossJournal.COMPACT_AT, (path, anchor) -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("not released"); }
            catch (InterruptedException stop) { throw new IOException(stop); }
            RecoveryWitness.write(path, anchor);
        });
        var owner = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var result = owner.submit(() -> {
                var writer = new WorldBossCheckpoints(journal);
                writer.submit(root(1), List.of()); assertTrue(entered.await(5, TimeUnit.SECONDS));
                Thread.currentThread().interrupt();
                assertThrows(IOException.class, writer::drain);
                closing.countDown();
                assertThrows(IOException.class, writer::close); closed.countDown();
                assertTrue(Thread.interrupted());
                return null;
            });
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            assertFalse(closed.await(100, TimeUnit.MILLISECONDS), "close must not abandon an accepted journal write");
            release.countDown(); result.get(5, TimeUnit.SECONDS);
            assertEquals(1, RecoveryWitness.read(head()).sequence());
        } finally { release.countDown(); owner.shutdown(); assertTrue(owner.awaitTermination(5, TimeUnit.SECONDS)); }
    }
}
