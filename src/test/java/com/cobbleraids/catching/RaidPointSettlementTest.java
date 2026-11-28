package com.cobbleraids.catching;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

class RaidPointSettlementTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private static final UUID EVENT = new UUID(1, 1), ALICE = new UUID(2, 2), BOB = new UUID(3, 3);

    private RaidPlayerRecords bound(UUID installation) throws Exception {
        var store = new RaidPlayerRecords(); var data = store.save(new CompoundTag(), null);
        CompoundTag recovery = new CompoundTag(); recovery.putInt("version", 1); recovery.putUUID("installation", installation); recovery.put("promises", new ListTag());
        data.put("worldboss_recovery", recovery); store = RaidPlayerRecords.load(data, null);
        var path = directory.resolve("points.head");
        com.cobbleraids.config.RecoveryWitness.write(path, com.cobbleraids.config.RecoveryWitness.points(installation, Map.of()));
        store.verifyWorldBossWitness(path); return store;
    }

    @Test void scopedSettlementCommitsRecoveryProofAlongsideBalancesAndReplaysAfterRealFileReload() throws Exception {
        UUID installation = new UUID(9, 1); var store = bound(installation);
        Map<UUID, RaidPlayerRecord> balances = new LinkedHashMap<>(); var file = directory.resolve("records.dat");
        store.settleWorldBossCredits(installation, EVENT, Map.of(ALICE, 200), balances, snapshot -> {
            CompoundTag root = new CompoundTag(); root.put("data", snapshot.save(new CompoundTag(), null)); snapshot.commitRoot(file, root);
        });
        var data = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("data");
        var loaded = RaidPlayerRecords.load(data, null); loaded.verifyWorldBossWitness(directory.resolve("points.head")); var restored = loaded.take();
        loaded.settleWorldBossCredits(installation, EVENT, Map.of(ALICE, 200), restored, snapshot -> fail("Already-secured replay must not write"));
        assertEquals(200, restored.get(ALICE).raidPoints());
        assertThrows(IllegalStateException.class, () -> loaded.settleWorldBossCredits(UUID.randomUUID(), EVENT, Map.of(ALICE, 200), restored, snapshot -> fail()));
        assertThrows(IllegalStateException.class, () -> loaded.settleCredits(EVENT, Map.of(BOB, 200), restored, snapshot -> fail()));
    }

    @Test void failedPointFileAfterExternalProofDoesNotPublishAndBlocksAutomaticReplay() throws Exception {
        UUID installation = new UUID(9, 2); var store = bound(installation);
        Map<UUID, RaidPlayerRecord> balances = new LinkedHashMap<>(Map.of(ALICE, RaidPlayerRecord.EMPTY.withPoints(7)));
        CompoundTag before = store.save(new CompoundTag(), null);
        var occupied = directory.resolve("records.dat"); java.nio.file.Files.createDirectory(occupied); java.nio.file.Files.writeString(occupied.resolve("keep"), "prior evidence");
        assertThrows(IOException.class, () -> store.settleWorldBossCredits(installation, EVENT, Map.of(ALICE, 200), balances, snapshot -> {
            CompoundTag root = new CompoundTag(); root.put("data", snapshot.save(new CompoundTag(), null)); snapshot.commitRoot(occupied, root);
        }));
        assertEquals(7, balances.get(ALICE).raidPoints()); assertEquals(before, store.save(new CompoundTag(), null));
        assertThrows(IllegalStateException.class, () -> store.settleWorldBossCredits(installation, EVENT, Map.of(ALICE, 200), balances, snapshot -> fail()));
        var restored = RaidPlayerRecords.load(before, null);
        assertThrows(IOException.class, () -> restored.verifyWorldBossWitness(directory.resolve("points.head")));
    }

    @Test void exactRecipientProofRejectsTamperedReceiptsAndPreservesRawEvidence() throws Exception {
        UUID installation = new UUID(9, 3); var store = bound(installation);
        Map<UUID, RaidPlayerRecord> balances = new LinkedHashMap<>();
        store.settleWorldBossCredits(installation, EVENT, Map.of(ALICE, 200), balances, snapshot -> {});
        CompoundTag data = store.save(new CompoundTag(), null);
        data.getList("credit_receipts", 10).getCompound(0).putInt("promised", 201);
        var invalid = RaidPlayerRecords.load(data, null); assertEquals(data, invalid.save(new CompoundTag(), null));
        assertThrows(IllegalStateException.class, () -> invalid.settleCredits(EVENT, Map.of(ALICE, 201), balances, snapshot -> fail()));
    }

    @Test void zeroRecipientPlanStillHasARecoveryProofAndCannotGainRecipientsLater() throws Exception {
        UUID installation = new UUID(9, 4); var store = bound(installation);
        Map<UUID, RaidPlayerRecord> balances = new LinkedHashMap<>(); CompoundTag[] disk = {null};
        store.settleWorldBossCredits(installation, EVENT, Map.of(), balances, snapshot -> disk[0] = snapshot.save(new CompoundTag(), null));
        assertNotNull(disk[0]); assertEquals(1, disk[0].getCompound("worldboss_recovery").getList("promises", 10).size());
        var restored = RaidPlayerRecords.load(disk[0], null);
        restored.settleWorldBossCredits(installation, EVENT, Map.of(), balances, snapshot -> fail());
        assertThrows(IllegalStateException.class, () -> restored.settleWorldBossCredits(installation, EVENT, Map.of(ALICE, 200), balances, snapshot -> fail()));
    }

    @Test void scopedCappedPromiseKeepsTheSameRecoveryProofWhenItsRemainderIsClaimed() throws Exception {
        UUID installation = new UUID(9, 5); var store = bound(installation);
        Map<UUID, RaidPlayerRecord> balances = new LinkedHashMap<>(Map.of(ALICE, RaidPlayerRecord.EMPTY.withPoints(Integer.MAX_VALUE - 20)));
        var file = directory.resolve("records.dat");
        RaidPlayerRecords.CreditWriter writer = snapshot -> { var root = new CompoundTag(); root.put("data", snapshot.save(new CompoundTag(), null)); snapshot.commitRoot(file, root); };
        store.settleWorldBossCredits(installation, EVENT, Map.of(ALICE, 200), balances, writer);
        byte[] proof = java.nio.file.Files.readAllBytes(directory.resolve("points.head"));
        balances.put(ALICE, balances.get(ALICE).withPoints(-300));
        assertEquals(180, store.settleCredits(EVENT, Map.of(ALICE, 200), balances, writer).get(ALICE));
        assertArrayEquals(proof, java.nio.file.Files.readAllBytes(directory.resolve("points.head")));
        var loaded = RaidPlayerRecords.load(net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("data"), null);
        loaded.verifyWorldBossWitness(directory.resolve("points.head")); assertEquals(Integer.MAX_VALUE - 120, loaded.take().get(ALICE).raidPoints());
    }

    @Test void malformedPlayerDataCannotBecomeAnEmptyPayableStore() {
        CompoundTag tag = new CompoundTag();
        tag.putString("players", "corrupt existing balances");
        RaidPlayerRecords loaded = RaidPlayerRecords.load(tag, null);
        assertThrows(IllegalStateException.class,
                () -> loaded.settleCredits(EVENT, Map.of(ALICE, 200), new LinkedHashMap<>(), snapshot -> fail()));
        assertEquals(tag, loaded.save(new CompoundTag(), null));
    }

    @Test void committedCreditsReplayAfterReloadWithoutDoublePayment() throws Exception {
        RaidPlayerRecords store = new RaidPlayerRecords();
        Map<UUID, RaidPlayerRecord> balances = new LinkedHashMap<>();
        CompoundTag[] disk = {null};
        Map<UUID, Integer> amounts = Map.of(ALICE, 200, BOB, 50);
        store.settleCredits(EVENT, amounts, balances, snapshot -> disk[0] = snapshot.save(new CompoundTag(), null));
        RaidPlayerRecords reloaded = RaidPlayerRecords.load(disk[0], null);
        Map<UUID, RaidPlayerRecord> restoredBalances = reloaded.take();
        assertEquals(Map.of(ALICE, 0, BOB, 0), reloaded.settleCredits(EVENT, amounts, restoredBalances,
                snapshot -> fail("A completed payout replay must not write or pay again")));
        assertEquals(200, restoredBalances.get(ALICE).raidPoints());
        assertEquals(50, restoredBalances.get(BOB).raidPoints());
    }

    @Test void failedWriteDoesNotPublishCreditsOrReceiptsAndRetryPaysOnce() throws Exception {
        RaidPlayerRecords store = new RaidPlayerRecords();
        Map<UUID, RaidPlayerRecord> balances = new LinkedHashMap<>(Map.of(ALICE, RaidPlayerRecord.EMPTY.withPoints(7)));
        assertThrows(IOException.class, () -> store.settleCredits(EVENT, Map.of(ALICE, 200), balances,
                snapshot -> { throw new IOException("disk full"); }));
        assertEquals(7, balances.get(ALICE).raidPoints());
        assertEquals(0, store.save(new CompoundTag(), null).getList("credit_receipts", 10).size());
        store.settleCredits(EVENT, Map.of(ALICE, 200), balances, snapshot -> {});
        store.settleCredits(EVENT, Map.of(ALICE, 200), balances, snapshot -> fail("Replay wrote again"));
        assertEquals(207, balances.get(ALICE).raidPoints());
    }

    @Test void cappedRemainderSurvivesReloadAndSpendingWithoutRepeatingEarlierCredits() throws Exception {
        RaidPlayerRecords store = new RaidPlayerRecords();
        Map<UUID, RaidPlayerRecord> balances = new LinkedHashMap<>(Map.of(ALICE,
                RaidPlayerRecord.EMPTY.withPoints(Integer.MAX_VALUE - 20)));
        CompoundTag[] disk = {null};
        assertEquals(20, store.settleCredits(EVENT, Map.of(ALICE, 200), balances,
                snapshot -> disk[0] = snapshot.save(new CompoundTag(), null)).get(ALICE));
        RaidPlayerRecords reloaded = RaidPlayerRecords.load(disk[0], null);
        Map<UUID, RaidPlayerRecord> restored = reloaded.take();
        restored.put(ALICE, restored.get(ALICE).withPoints(-300));
        assertEquals(180, reloaded.settleCredits(EVENT, Map.of(ALICE, 200), restored, snapshot -> {}).get(ALICE));
        assertEquals(Integer.MAX_VALUE - 120, restored.get(ALICE).raidPoints());
        assertEquals(0, reloaded.settleCredits(EVENT, Map.of(ALICE, 200), restored, snapshot -> fail()).get(ALICE));
    }

    @Test void ordinaryProgressAndSpendingArePreservedByASecondPayout() throws Exception {
        RaidPlayerRecords store = new RaidPlayerRecords();
        Map<UUID, RaidPlayerRecord> balances = new LinkedHashMap<>();
        store.settleCredits(EVENT, Map.of(ALICE, 200), balances, snapshot -> {});
        balances.put(ALICE, balances.get(ALICE).withPoints(-75).withTitleUnlocked("first_clear"));
        store.settleCredits(new UUID(9, 9), Map.of(ALICE, 50), balances, snapshot -> {});
        assertEquals(175, balances.get(ALICE).raidPoints());
        assertTrue(balances.get(ALICE).hasTitle("first_clear"));
        assertThrows(IllegalArgumentException.class,
                () -> store.settleCredits(EVENT, Map.of(ALICE, 999), balances, snapshot -> fail()));
    }

    @Test void legacyRecordsWithoutReceiptsAreStillReadable() throws Exception {
        RaidPlayerRecords store = new RaidPlayerRecords();
        store.update(Map.of(ALICE, RaidPlayerRecord.EMPTY.withPoints(42)));
        CompoundTag legacy = store.save(new CompoundTag(), null);
        legacy.remove("credit_schema");
        legacy.remove("credit_receipts");
        RaidPlayerRecords reloaded = RaidPlayerRecords.load(legacy, null);
        Map<UUID, RaidPlayerRecord> balances = reloaded.take();
        reloaded.settleCredits(EVENT, Map.of(ALICE, 200), balances, snapshot -> {});
        assertEquals(242, balances.get(ALICE).raidPoints());
    }

    @Test void malformedReceiptDataQuarantinesCreditsWithoutErasingBalancesOrRawEvidence() {
        RaidPlayerRecords store = new RaidPlayerRecords();
        store.update(Map.of(ALICE, RaidPlayerRecord.EMPTY.withPoints(42)));
        CompoundTag tag = store.save(new CompoundTag(), null);
        ListTag invalid = new ListTag();
        invalid.add(StringTag.valueOf("bad receipt type"));
        tag.put("credit_receipts", invalid);
        RaidPlayerRecords loaded = RaidPlayerRecords.load(tag, null);
        Map<UUID, RaidPlayerRecord> balances = loaded.take();
        assertEquals(42, balances.get(ALICE).raidPoints());
        assertThrows(IllegalStateException.class,
                () -> loaded.settleCredits(EVENT, Map.of(ALICE, 200), balances, snapshot -> fail()));
        loaded.update(balances);
        assertEquals(invalid, loaded.save(new CompoundTag(), null).get("credit_receipts"));
    }
}
