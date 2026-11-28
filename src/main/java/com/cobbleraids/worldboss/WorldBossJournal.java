package com.cobbleraids.worldboss;

import com.cobbleraids.config.RecoveryWitness;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Local single-server write-ahead authority, including full projections and immutable payout promises. */
final class WorldBossJournal {
    static final int MAGIC = 0x57424a31, END = 0x57424531, HEADER = 68, TRAILER = 36;
    static final int MAX_PAYLOAD = 16 * 1024 * 1024;
    static final long COMPACT_AT = 64L * 1024 * 1024;
    private static final String ZERO = "0".repeat(64);
    record Promise(UUID payout, Map<UUID, Integer> amounts, boolean paid, boolean tracked) {
        Promise {
            java.util.Objects.requireNonNull(payout); amounts = Map.copyOf(amounts);
            if (amounts.values().stream().anyMatch(amount -> amount < 0)) throw new IllegalArgumentException("Negative journal promise");
        }
        String digest() { return RecoveryWitness.promise(payout, amounts); }
    }
    private final Path file, witness;
    private final long compactAt;
    @FunctionalInterface interface HeadWriter { void write(Path path, RecoveryWitness.Head head) throws IOException; }
    private final HeadWriter headWriter;
    private UUID installation;
    private long sequence, fileSize;
    private String digest = ZERO;
    private CompoundTag snapshot;
    private Map<UUID, Promise> promises = new LinkedHashMap<>();
    private byte[] lastFrame;
    private volatile boolean blocked;
    private record Acknowledged(long sequence, String digest, CompoundTag snapshot, Map<UUID, Promise> promises) {}
    private volatile Acknowledged acknowledged;

    private WorldBossJournal(Path file, Path witness, long compactAt, HeadWriter headWriter) {
        if (compactAt < 1024 || compactAt > COMPACT_AT) throw new IllegalArgumentException("Invalid journal compaction budget");
        this.file = file; this.witness = witness; this.compactAt = compactAt; this.headWriter = java.util.Objects.requireNonNull(headWriter);
    }
    UUID installation() { return installation; }
    long sequence() { Acknowledged value = acknowledged; return value == null ? 0 : value.sequence(); }
    boolean blocked() { return blocked; }
    boolean initialized() { return sequence > 0; }
    CompoundTag snapshot() { Acknowledged value = acknowledged; return value == null ? null : value.snapshot().copy(); }
    Map<UUID, Promise> promises() { Acknowledged value = acknowledged; return value == null ? Map.of() : value.promises(); }
    Promise promise(UUID payout) { return promises().get(payout); }
    int promiseCount() { return promises().size(); }
    boolean hasTrackedPromises() { return promises().values().stream().anyMatch(Promise::tracked); }
    private void publish() { acknowledged = new Acknowledged(sequence, digest, snapshot, Map.copyOf(promises)); }
    private RecoveryWitness.Head head() { return new RecoveryWitness.Head(installation, sequence, digest); }

    static WorldBossJournal open(Path file, Path witness, CompoundTag projection) throws IOException {
        return open(file, witness, projection, COMPACT_AT, RecoveryWitness::write);
    }
    static WorldBossJournal open(Path file, Path witness, CompoundTag projection, long compactAt, HeadWriter headWriter) throws IOException {
        WorldBossJournal journal = new WorldBossJournal(file, witness, compactAt, headWriter);
        RecoveryWitness.Head anchor = RecoveryWitness.read(witness);
        if (Files.notExists(file)) {
            if (anchor != null || projection != null && projection.contains("worldboss_journal")) throw new IOException("World Boss journal is missing from the recovery set");
            journal.installation = UUID.randomUUID();
            return journal;
        }
        boolean incomplete = false, anchorSeen = false;
        long validEnd = 0;
        try (var input = new RandomAccessFile(file.toFile(), "r")) {
            long size = input.length();
            if (size > COMPACT_AT + MAX_PAYLOAD + HEADER + TRAILER) throw new IOException("World Boss journal exceeds its bounded format size");
            while (input.getFilePointer() < size) {
                long start = input.getFilePointer();
                if (size - start < HEADER) { incomplete = true; break; }
                byte[] header = new byte[HEADER]; input.readFully(header);
                var fields = new DataInputStream(new ByteArrayInputStream(header));
                if (fields.readInt() != MAGIC || fields.readInt() != 1) throw new IOException("Unknown World Boss journal frame");
                int length = fields.readInt();
                long next = fields.readLong();
                UUID identity = new UUID(fields.readLong(), fields.readLong());
                String previous = HexFormat.of().formatHex(fields.readNBytes(32));
                if (length <= 0 || length > MAX_PAYLOAD || next <= 0) throw new IOException("Invalid World Boss journal frame bounds");
                if (size - input.getFilePointer() < (long) length + TRAILER) { incomplete = true; break; }
                byte[] payload = new byte[length]; input.readFully(payload);
                byte[] hash = new byte[32]; input.readFully(hash);
                if (input.readInt() != END || !RecoveryWitness.hash(concat(header, payload)).equals(HexFormat.of().formatHex(hash))) throw new IOException("World Boss journal checksum/commit marker mismatch");
                if (journal.initialized()) {
                    if (!identity.equals(journal.installation) || next != journal.sequence + 1 || !previous.equals(journal.digest)) throw new IOException("Broken World Boss journal ordering/chain");
                } else if (next == 1 ? !previous.equals(ZERO) : anchor == null || anchor.sequence() < next) {
                    throw new IOException("Unanchored World Boss journal checkpoint");
                }
                CompoundTag envelope = decode(payload);
                Map<UUID, Promise> restored = readPromises(envelope);
                validateProgress(journal.promises, restored);
                journal.installation = identity; journal.sequence = next; journal.digest = HexFormat.of().formatHex(hash);
                journal.snapshot = envelope.getCompound("snapshot").copy(); journal.promises = restored;
                journal.lastFrame = concat(concat(header, payload), concat(hash, ByteBuffer.allocate(4).putInt(END).array()));
                validEnd = input.getFilePointer();
                if (anchor != null && anchor.sequence() == next) {
                    if (!anchor.equals(journal.head())) throw new IOException("World Boss external head contradicts its journal");
                    anchorSeen = true;
                }
            }
        }
        if (!journal.initialized()) throw new IOException("No complete World Boss journal generation; preserve and reconcile the initial write");
        if (anchor == null && journal.sequence != 1 || anchor != null && !anchorSeen) throw new IOException("World Boss external head is missing or newer than the available recovery set");
        verifyProjection(projection, journal);
        if (incomplete) {
            // Never erase a potentially acknowledged frame: a newer external head was rejected above.
            Path evidence = file.resolveSibling(file.getFileName() + ".incomplete-" + UUID.randomUUID());
            Files.copy(file, evidence, StandardCopyOption.COPY_ATTRIBUTES); RecoveryWitness.force(evidence);
            try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.truncate(validEnd); channel.force(true); }
        }
        journal.fileSize = validEnd;
        if (!journal.head().equals(anchor)) RecoveryWitness.write(witness, journal.head());
        journal.publish();
        return journal;
    }

    private static void verifyProjection(CompoundTag projection, WorldBossJournal journal) throws IOException {
        if (projection == null || !projection.contains("worldboss_journal")) return;
        if (!projection.contains("worldboss_journal", Tag.TAG_COMPOUND)) throw new IOException("Malformed World Boss projection provenance");
        var meta = projection.getCompound("worldboss_journal");
        if (!meta.contains("version", Tag.TAG_INT) || meta.getInt("version") != 1 || !meta.hasUUID("installation") || !meta.contains("sequence", Tag.TAG_LONG) || !meta.contains("digest", Tag.TAG_STRING)
                || !meta.getUUID("installation").equals(journal.installation) || meta.getLong("sequence") <= 0
                || meta.getLong("sequence") > journal.sequence || !meta.getString("digest").matches("[0-9a-f]{64}")
                || meta.getLong("sequence") == journal.sequence && !meta.getString("digest").equals(journal.digest)) {
            throw new IOException("World Boss projection is ahead of or belongs to a different journal recovery set");
        }
    }

    void commit(CompoundTag root, List<Promise> current) throws IOException {
        if (blocked) throw new IOException("World Boss journal is blocked until restart/reconciliation");
        try {
            if (initialized()) { RecoveryWitness.verify(witness, head()); verifyFileHead(); }
            Map<UUID, Promise> next = new LinkedHashMap<>(promises);
            var seen = new java.util.HashSet<UUID>();
            for (Promise promise : current) {
                if (!seen.add(promise.payout())) throw new IOException("Duplicate current World Boss payout identity");
                Promise old = next.get(promise.payout());
                if (old != null && (!old.amounts().equals(promise.amounts()) || old.paid() && !promise.paid())) throw new IOException("An acknowledged World Boss promise cannot change or become unpaid");
                next.put(promise.payout(), old == null ? promise : new Promise(promise.payout(), promise.amounts(), promise.paid(), old.tracked()));
            }
            if (snapshot != null && snapshot.equals(root) && promises.equals(next)) return;
            CompoundTag envelope = new CompoundTag(); envelope.putInt("version", 1); envelope.put("snapshot", root.copy());
            ListTag rows = new ListTag();
            for (Promise promise : next.values()) {
                CompoundTag row = new CompoundTag(); row.putUUID("payout", promise.payout()); row.putBoolean("paid", promise.paid()); row.putBoolean("tracked", promise.tracked());
                ListTag amounts = new ListTag();
                promise.amounts().forEach((player, amount) -> { CompoundTag value = new CompoundTag(); value.putUUID("player", player); value.putInt("amount", amount); amounts.add(value); });
                row.put("amounts", amounts); rows.add(row);
            }
            envelope.put("promises", rows);
            var bytes = new PayloadBytes(); NbtIo.writeCompressed(envelope, bytes); byte[] payload = bytes.toByteArray();
            if (payload.length == 0 || payload.length > MAX_PAYLOAD) throw new IOException("World Boss journal snapshot exceeds its payload budget");
            decode(payload); // A committed snapshot must also fit the recovery reader's NBT allocation limit.
            long nextSequence = Math.incrementExact(sequence);
            var headerBytes = new ByteArrayOutputStream(); var output = new DataOutputStream(headerBytes);
            output.writeInt(MAGIC); output.writeInt(1); output.writeInt(payload.length); output.writeLong(nextSequence);
            output.writeLong(installation.getMostSignificantBits()); output.writeLong(installation.getLeastSignificantBits()); output.write(HexFormat.of().parseHex(digest));
            byte[] body = concat(headerBytes.toByteArray(), payload); String nextDigest = RecoveryWitness.hash(body);
            byte[] frame = concat(body, concat(HexFormat.of().parseHex(nextDigest), ByteBuffer.allocate(4).putInt(END).array()));
            Files.createDirectories(file.toAbsolutePath().getParent());
            if (initialized() && fileSize + frame.length > compactAt) {
                // The preceding acknowledged frame and complete new snapshot are one atomic replacement.
                byte[] compacted = concat(lastFrame, frame); RecoveryWitness.atomicWrite(file, compacted); fileSize = compacted.length;
            } else {
                try (var channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                    if (channel.size() != fileSize) throw new IOException("World Boss journal changed outside this server");
                    channel.position(fileSize); ByteBuffer buffer = ByteBuffer.wrap(frame);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true); fileSize += frame.length;
                }
            }
            sequence = nextSequence; digest = nextDigest; snapshot = root.copy(); promises = next; lastFrame = frame;
            headWriter.write(witness, head());
            publish();
        } catch (IOException | RuntimeException failure) {
            blocked = true; // A forced frame may already exist. Never overwrite it with a rolled-back UI state.
            if (failure instanceof IOException io) throw io;
            throw new IOException("Could not commit World Boss journal", failure);
        }
    }

    private void verifyFileHead() throws IOException {
        try (var input = new RandomAccessFile(file.toFile(), "r")) {
            if (input.length() != fileSize) throw new IOException("World Boss journal size changed outside this server");
            input.seek(fileSize - TRAILER); byte[] hash = new byte[32]; input.readFully(hash);
            if (!digest.equals(HexFormat.of().formatHex(hash)) || input.readInt() != END) throw new IOException("World Boss journal head changed outside this server");
        }
    }

    CompoundTag projection() {
        Acknowledged value = acknowledged;
        if (value == null) throw new IllegalStateException("World Boss journal has no acknowledged projection");
        CompoundTag root = value.snapshot().copy(), meta = new CompoundTag();
        meta.putInt("version", 1); meta.putUUID("installation", installation); meta.putLong("sequence", value.sequence()); meta.putString("digest", value.digest());
        root.put("worldboss_journal", meta); return root;
    }

    void reconcile(UUID pointsInstallation, Map<UUID, String> markers) throws IOException {
        if (!installation.equals(pointsInstallation)) throw new IOException("World Boss and Raid Points recovery identities differ");
        for (var marker : markers.entrySet()) {
            Promise promise = promises.get(marker.getKey());
            if (promise == null || !promise.digest().equals(marker.getValue())) throw new IOException("Raid Points has a settlement absent from or contradicting the World Boss journal");
        }
        for (Promise promise : promises.values()) {
            if (promise.tracked() && promise.paid() && !markers.containsKey(promise.payout())) throw new IOException("A secured World Boss payout is missing from Raid Points; partial restore requires reconciliation");
        }
    }

    private static CompoundTag decode(byte[] payload) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(payload), NbtAccounter.create(64L * 1024 * 1024));
        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1 || !tag.contains("snapshot", Tag.TAG_COMPOUND)
                || !tag.getCompound("snapshot").contains("data", Tag.TAG_COMPOUND) || !tag.contains("promises", Tag.TAG_LIST)
                || tag.getList("promises", Tag.TAG_COMPOUND).size() != ((ListTag) tag.get("promises")).size()) throw new IOException("Malformed World Boss journal payload");
        return tag;
    }
    private static Map<UUID, Promise> readPromises(CompoundTag tag) throws IOException {
        Map<UUID, Promise> promises = new LinkedHashMap<>();
        for (Tag value : tag.getList("promises", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            if (!row.hasUUID("payout") || !row.contains("paid", Tag.TAG_BYTE) || !row.contains("tracked", Tag.TAG_BYTE) || !row.contains("amounts", Tag.TAG_LIST)
                    || row.getList("amounts", Tag.TAG_COMPOUND).size() != ((ListTag) row.get("amounts")).size()) throw new IOException("Malformed journal promise");
            Map<UUID, Integer> amounts = new LinkedHashMap<>();
            for (Tag item : row.getList("amounts", Tag.TAG_COMPOUND)) {
                var amount = (CompoundTag) item;
                if (!amount.hasUUID("player") || !amount.contains("amount", Tag.TAG_INT) || amount.getInt("amount") < 0
                        || amounts.putIfAbsent(amount.getUUID("player"), amount.getInt("amount")) != null) throw new IOException("Malformed/duplicate journal recipient");
            }
            var promise = new Promise(row.getUUID("payout"), amounts, row.getBoolean("paid"), row.getBoolean("tracked"));
            if (promises.putIfAbsent(promise.payout(), promise) != null) throw new IOException("Duplicate journal payout");
        }
        return promises;
    }
    private static void validateProgress(Map<UUID, Promise> before, Map<UUID, Promise> after) throws IOException {
        for (var old : before.values()) {
            var next = after.get(old.payout());
            if (next == null || !old.amounts().equals(next.amounts()) || old.tracked() != next.tracked() || old.paid() && !next.paid()) throw new IOException("World Boss journal promise history changed");
        }
    }
    private static byte[] concat(byte[] left, byte[] right) { byte[] result = Arrays.copyOf(left, left.length + right.length); System.arraycopy(right, 0, result, left.length, right.length); return result; }
    private static final class PayloadBytes extends ByteArrayOutputStream {
        @Override public synchronized void write(int value) {
            if (count >= MAX_PAYLOAD) throw new IllegalStateException("Journal compressed payload budget exceeded");
            super.write(value);
        }
        @Override public synchronized void write(byte[] bytes, int offset, int length) {
            if ((long) count + length > MAX_PAYLOAD) throw new IllegalStateException("Journal compressed payload budget exceeded");
            super.write(bytes, offset, length);
        }
    }
}
