package com.cobbleraids.worldboss;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleraids.config.RecoveryWitness;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldBossJournalTest {
    @TempDir Path directory;
    private static final UUID PAYOUT = new UUID(1, 1), PLAYER = new UUID(2, 2);
    private Path file() { return directory.resolve("world/state.journal"); }
    private Path head() { return directory.resolve("config/journal.head"); }
    private WorldBossJournal open(CompoundTag projection) throws IOException { return WorldBossJournal.open(file(), head(), projection); }
    private static CompoundTag root(int value) { var root = new CompoundTag(); var data = new CompoundTag(); data.putInt("value", value); root.put("data", data); return root; }
    private static WorldBossJournal.Promise promise(boolean paid) { return new WorldBossJournal.Promise(PAYOUT, Map.of(PLAYER, 200), paid, true); }

    @Test void completeFramesRecoverLatestProjectionAndRetainHistoricalPromises() throws Exception {
        var journal = open(null); journal.commit(root(1), List.of(promise(false))); var stale = journal.projection();
        journal.commit(root(2), List.of(promise(true))); journal.commit(root(3), List.of());
        var recovered = open(stale);
        assertEquals(root(3), recovered.snapshot()); assertEquals(3, recovered.sequence());
        assertEquals(promise(true), recovered.promise(PAYOUT));
        recovered.reconcile(recovered.installation(), Map.of(PAYOUT, promise(true).digest()));
        var independent = recovered.snapshot(); independent.getCompound("data").putInt("value", 99);
        assertEquals(root(3), recovered.snapshot());
        assertThrows(IOException.class, () -> recovered.reconcile(recovered.installation(), Map.of()));
    }

    @Test void pendingPlanAcceptsPreCreditOrAlreadyCreditedStoreButNotAnotherRecoverySet() throws Exception {
        var journal = open(null); journal.commit(root(1), List.of(promise(false)));
        journal.reconcile(journal.installation(), Map.of());
        journal.reconcile(journal.installation(), Map.of(PAYOUT, promise(false).digest()));
        assertThrows(IOException.class, () -> journal.reconcile(UUID.randomUUID(), Map.of()));
        assertThrows(IOException.class, () -> journal.reconcile(journal.installation(), Map.of(UUID.randomUUID(), promise(false).digest())));
        assertThrows(IOException.class, () -> journal.reconcile(journal.installation(), Map.of(PAYOUT, "0".repeat(64))));
    }

    @Test void legacyPaidBaselineDoesNotInventMissingReceiptsOrRetroactivelyPay() throws Exception {
        var journal = open(null);
        journal.commit(root(1), List.of(new WorldBossJournal.Promise(PAYOUT, Map.of(PLAYER, 200), true, false)));
        journal.commit(root(2), List.of(promise(true)));
        var restored = open(null); assertFalse(restored.promise(PAYOUT).tracked());
        restored.reconcile(restored.installation(), Map.of());
    }

    @Test void alteredPromiseOrPaidRollbackPoisonsWriterWithoutReplacingCommittedHead() throws Exception {
        var journal = open(null); journal.commit(root(1), List.of(promise(true))); byte[] before = Files.readAllBytes(file());
        assertThrows(IOException.class, () -> journal.commit(root(2), List.of(promise(false))));
        assertTrue(journal.blocked()); assertArrayEquals(before, Files.readAllBytes(file()));
        assertThrows(IOException.class, () -> journal.commit(root(2), List.of(promise(true))));
        assertEquals(root(1), open(null).snapshot());
        var fresh = open(null);
        assertThrows(IOException.class, () -> fresh.commit(root(2), List.of(new WorldBossJournal.Promise(PAYOUT, Map.of(PLAYER, 201), true, true))));
    }

    @Test void incompleteUnacknowledgedTailIsCopiedBeforeTruncationAndNeverReplayed() throws Exception {
        var journal = open(null); journal.commit(root(1), List.of(promise(false))); byte[] complete = Files.readAllBytes(file());
        byte[] partialHeader = ByteBuffer.allocate(17).putInt(WorldBossJournal.MAGIC).putInt(1).putInt(400).putInt(0).put((byte) 1).array();
        Files.write(file(), partialHeader, StandardOpenOption.APPEND);
        byte[] damaged = Files.readAllBytes(file()); var restored = open(null);
        assertEquals(root(1), restored.snapshot()); assertArrayEquals(complete, Files.readAllBytes(file()));
        try (var evidence = Files.list(file().getParent())) {
            var copies = evidence.filter(path -> path.getFileName().toString().contains(".incomplete-")).toList();
            assertEquals(1, copies.size()); assertArrayEquals(damaged, Files.readAllBytes(copies.getFirst()));
        }
        restored.commit(root(2), List.of(promise(true))); assertEquals(2, open(null).sequence());
    }

    @Test void everyTruncationOfAnExternallyAcknowledgedSecondFrameRefusesAndPreservesBytes() throws Exception {
        var journal = open(null); journal.commit(root(1), List.of()); int boundary = (int) Files.size(file());
        journal.commit(root(2), List.of(promise(true))); byte[] complete = Files.readAllBytes(file());
        int length = complete.length - boundary;
        for (int cut : new int[]{0, 1, 8, WorldBossJournal.HEADER - 1, WorldBossJournal.HEADER, length / 2, length - 1}) {
            byte[] truncated = Arrays.copyOf(complete, boundary + cut); Files.write(file(), truncated);
            assertThrows(IOException.class, () -> open(null), "cut " + cut);
            assertArrayEquals(truncated, Files.readAllBytes(file()));
        }
        Files.write(file(), complete); assertEquals(root(2), open(null).snapshot());
    }

    @Test void completeChecksumDamageIsQuarantinedRatherThanTreatedAsATruncatedTail() throws Exception {
        var journal = open(null); journal.commit(root(1), List.of()); byte[] bytes = Files.readAllBytes(file());
        for (int offset : new int[]{0, 7, 12, 25, 40, WorldBossJournal.HEADER, bytes.length - 1}) {
            byte[] damaged = bytes.clone(); damaged[offset] ^= 1; Files.write(file(), damaged);
            assertThrows(IOException.class, () -> open(null)); assertArrayEquals(damaged, Files.readAllBytes(file()));
        }
    }

    @Test void incompleteUnacknowledgedPayloadRecoversOnlyItsPrecedingCommittedGeneration() throws Exception {
        var journal = open(null); journal.commit(root(1), List.of(promise(false))); long boundary = Files.size(file());
        var interrupted = WorldBossJournal.open(file(), head(), null, WorldBossJournal.COMPACT_AT, (path, anchor) -> { throw new IOException("head interrupted"); });
        assertThrows(IOException.class, () -> interrupted.commit(root(2), List.of(promise(true))));
        try (var channel = java.nio.channels.FileChannel.open(file(), StandardOpenOption.WRITE)) { channel.truncate(boundary + WorldBossJournal.HEADER + 3); }
        var restored = open(null); assertEquals(root(1), restored.snapshot()); assertFalse(restored.promise(PAYOUT).paid());
        assertEquals(boundary, Files.size(file()));
    }

    @Test void oldExternalHeadCanCatchUpAfterForcedFrameButFailedHeadCommit() throws Exception {
        AtomicBoolean fail = new AtomicBoolean();
        var journal = WorldBossJournal.open(file(), head(), null, WorldBossJournal.COMPACT_AT, (path, anchor) -> {
            if (fail.get()) throw new IOException("head device failure after journal force");
            RecoveryWitness.write(path, anchor);
        });
        journal.commit(root(1), List.of()); fail.set(true);
        assertThrows(IOException.class, () -> journal.commit(root(2), List.of(promise(false))));
        assertTrue(journal.blocked()); assertEquals(1, RecoveryWitness.read(head()).sequence());
        assertThrows(IOException.class, () -> journal.commit(root(99), List.of()));
        var restored = open(null); assertEquals(2, restored.sequence()); assertEquals(root(2), restored.snapshot());
        assertEquals(2, RecoveryWitness.read(head()).sequence());
    }

    @Test void missingHeadMissingJournalForeignIdentityAndFutureProjectionAllFailClosed() throws Exception {
        var journal = open(null); journal.commit(root(1), List.of()); journal.commit(root(2), List.of());
        byte[] journalBytes = Files.readAllBytes(file()), anchor = Files.readAllBytes(head());
        Files.delete(head()); assertThrows(IOException.class, () -> open(null)); Files.write(head(), anchor);
        Files.delete(file()); assertThrows(IOException.class, () -> open(null)); Files.write(file(), journalBytes);
        var projection = journal.projection(); projection.getCompound("worldboss_journal").putLong("sequence", 3);
        assertThrows(IOException.class, () -> open(projection));
        var foreign = journal.projection(); foreign.getCompound("worldboss_journal").putUUID("installation", UUID.randomUUID());
        assertThrows(IOException.class, () -> open(foreign));
        var old = RecoveryWitness.read(head()); RecoveryWitness.write(head(), new RecoveryWitness.Head(UUID.randomUUID(), old.sequence(), old.digest()));
        assertThrows(IOException.class, () -> open(null));
    }

    @Test void compactionRetainsAChainedCheckpointAndNewestFullProjection() throws Exception {
        var journal = WorldBossJournal.open(file(), head(), null, 1024, RecoveryWitness::write);
        var random = new java.util.Random(7); byte[] payload = new byte[700];
        for (int value = 0; value < 8; value++) {
            random.nextBytes(payload); var root = root(value); root.getCompound("data").putByteArray("payload", payload);
            journal.commit(root, List.of(promise(value > 0)));
        }
        assertTrue(Files.size(file()) < 6000); var restored = open(null);
        assertEquals(8, restored.sequence()); assertEquals(7, restored.snapshot().getCompound("data").getInt("value"));
        assertTrue(restored.promise(PAYOUT).paid()); restored.reconcile(restored.installation(), Map.of(PAYOUT, promise(true).digest()));
    }

    @Test void duplicateCurrentPayoutsCannotSilentlyMerge() throws Exception {
        var journal = open(null);
        assertThrows(IOException.class, () -> journal.commit(root(1), List.of(promise(false), promise(false))));
        assertFalse(Files.exists(file()));
    }

    @Test void forcedChildJvmStopAfterJournalForceBeforeHeadPublicationRecoversTheCompletePromise() throws Exception {
        var paths = new java.util.LinkedHashSet<String>();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) paths.add(Path.of(entry).toString().replace('\\', '/'));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof java.net.URLClassLoader urls) for (var url : urls.getURLs()) {
                if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()).toString().replace('\\', '/'));
            }
        }
        Path ready = directory.resolve("ready"), arguments = directory.resolve("child.args");
        String args = "-cp\n\"" + String.join(java.io.File.pathSeparator, paths) + "\"\n" + getClass().getName()
                + "\n\"" + file().toString().replace('\\', '/') + "\"\n\"" + head().toString().replace('\\', '/') + "\"\n\"" + ready.toString().replace('\\', '/') + "\"\n";
        Files.writeString(arguments, args);
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "@" + arguments).redirectErrorStream(true).start();
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            while (!Files.exists(ready) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
            String diagnostic = child.isAlive() ? "probe running" : new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(Files.exists(ready), diagnostic); assertEquals(1, RecoveryWitness.read(head()).sequence());
            child.destroyForcibly(); assertTrue(child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
            var restored = open(null); assertEquals(2, restored.sequence()); assertEquals(root(2), restored.snapshot());
            assertEquals(promise(false), restored.promise(PAYOUT)); assertEquals(2, RecoveryWitness.read(head()).sequence());
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS); }
            child.getInputStream().close(); child.getOutputStream().close(); child.getErrorStream().close();
        }
    }

    /** Narrow process-kill probe; this does not start Minecraft or simulate physical power failure. */
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        Path file = Path.of(args[0]), head = Path.of(args[1]), ready = Path.of(args[2]);
        var journal = WorldBossJournal.open(file, head, null); journal.commit(root(1), List.of());
        journal = WorldBossJournal.open(file, head, null, WorldBossJournal.COMPACT_AT, (path, anchor) -> {
            Files.writeString(ready, "journal forced; external head still old");
            try { new java.util.concurrent.CountDownLatch(1).await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
        });
        journal.commit(root(2), List.of(promise(false)));
    }

    @Test void fullAuthorityRestoresWorldheartBlessingAndLegacyOnceAfterStartingAnotherEvent() throws Exception {
        var state = victory(); state.freezeWorldheartVictory(WorldBossCampaignTest.COMBAT + 1);
        UUID ordinaryRaid = new UUID(8, 8);
        state.benefits(state.benefits().promise(ordinaryRaid, WorldBossCampaignTest.COMBAT + 2, 199, java.util.Set.of(PLAYER), true));
        var journal = open(null); var first = new CompoundTag(); first.put("data", state.save(new CompoundTag(), null));
        var promise = new WorldBossJournal.Promise(state.bossId(), state.rewardPlan(), false, true);
        var bonus = new WorldBossJournal.Promise(WorldBossBenefits.payoutId(ordinaryRaid), Map.of(PLAYER, 19), false, true);
        journal.commit(first, List.of(promise, bonus)); var oldProjection = journal.projection();
        var character = state.campaignEvent().characterId(); var granted = state.collection().snapshot();
        state.markRewardsPaid(); state.finish(WorldBossCampaignTest.COMBAT + 2);
        state.benefits(state.benefits().secured(ordinaryRaid));
        state.begin(UUID.randomUUID(), "cobbleraids:giratina", "minecraft:overworld", 0, 64, 0, WorldBossCampaignTest.COMBAT + 3, 0, new WorldBossPool(1), true);
        var next = new CompoundTag(); next.put("data", state.save(new CompoundTag(), null));
        journal.commit(next, List.of(new WorldBossJournal.Promise(promise.payout(), promise.amounts(), true, true), new WorldBossJournal.Promise(bonus.payout(), bonus.amounts(), true, true)));
        var recovered = open(oldProjection); var loaded = WorldBossState.load(recovered.snapshot().getCompound("data"), null);
        assertFalse(loaded.isQuarantined()); assertEquals(granted, loaded.collection().snapshot());
        assertEquals(2, loaded.campaign().active().awakening()); assertEquals(character, loaded.campaign().active().characterId());
        assertNotNull(loaded.collection().victory(promise.payout()).blessing());
        assertTrue(recovered.promise(promise.payout()).paid());
        assertTrue(loaded.benefits().bonuses().get(ordinaryRaid).paid());
        recovered.reconcile(recovered.installation(), Map.of(promise.payout(), promise.digest(), bonus.payout(), bonus.digest()));
    }

    private static WorldBossState victory() {
        var campaign = WorldBossCampaignTest.planned(); var event = campaign.require(WorldBossCampaignTest.KEY);
        campaign = campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.PREPARING, "", WorldBossCampaignTest.PREP);
        var state = new WorldBossState(); state.campaign(campaign);
        state.begin(event.eventId(), event.definition(), "minecraft:overworld", 0, 64, 0, WorldBossCampaignTest.PREP, 0, new WorldBossPool(1), true);
        state.clock(WorldBossClock.begin(0, WorldBossCampaignTest.PREP));
        state.stageOpeningRules(WorldBossOpeningRules.capture(WorldBossConfig.defaults(), new WorldBossCalibration(event.definition(), "a".repeat(64), BigDecimal.valueOf(100), "fixture")));
        state.configureWardPylon(new WorldBossWards.Pylon("minecraft:overworld", 2, 64, 2)); state.bindOccurrence(event.key());
        state.freezeOpening(List.of(PLAYER, new UUID(0, 2), new UUID(0, 3), new UUID(0, 4), new UUID(0, 5)));
        state.clock(state.clock().advance(0, WorldBossCampaignTest.COMBAT, true));
        state.campaign(state.campaign().transition(event.key(), event.eventId(), WorldBossCampaign.Status.OPEN, "", WorldBossCampaignTest.COMBAT));
        for (int duelNumber = 0; duelNumber < 2; duelNumber++) {
            var duel = UUID.randomUUID(); state.merit().register(duel, PLAYER);
            for (int turn = 1; turn <= 5; turn++) state.damageAction(duel, PLAYER, turn, turn, 1, 5);
        }
        var duel = UUID.randomUUID(); state.merit().register(duel, PLAYER); state.damageAction(duel, PLAYER, 1, 1, Float.MAX_VALUE, 5); state.pool().claimDefeat();
        return state;
    }
}
