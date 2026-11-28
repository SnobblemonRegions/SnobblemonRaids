package com.cobbleraids.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Small external recovery anchors. Checksums detect damage; they are not authentication. */
public final class RecoveryWitness {
    private static final int MAGIC = 0x57425231;
    private RecoveryWitness() {}
    public record Head(UUID installation, long sequence, String digest) {
        public Head {
            java.util.Objects.requireNonNull(installation);
            if (sequence < 0 || digest == null || !digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid recovery head");
        }
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String promise(UUID payout, Map<UUID, Integer> amounts) {
        try {
            var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes);
            uuid(output, payout); output.writeInt(amounts.size());
            for (UUID player : amounts.keySet().stream().sorted().toList()) {
                uuid(output, player); int amount = amounts.get(player);
                if (amount < 0) throw new IllegalArgumentException("Negative recovery promise");
                output.writeInt(amount);
            }
            return hash(bytes.toByteArray());
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static Head points(UUID installation, Map<UUID, String> markers) {
        try {
            var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes);
            output.writeInt(markers.size());
            for (UUID payout : markers.keySet().stream().sorted().toList()) {
                uuid(output, payout);
                String digest = markers.get(payout);
                if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid recovery promise digest");
                output.write(HexFormat.of().parseHex(digest));
            }
            return new Head(installation, 0, hash(bytes.toByteArray()));
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static Head read(Path path) throws IOException {
        if (Files.notExists(path)) return null;
        if (Files.size(path) != 92) throw new IOException("Invalid recovery witness length: " + path);
        byte[] bytes = Files.readAllBytes(path);
        if (!hash(Arrays.copyOf(bytes, 60)).equals(HexFormat.of().formatHex(bytes, 60, 92))) throw new IOException("Recovery witness checksum mismatch: " + path);
        var input = new DataInputStream(new ByteArrayInputStream(bytes));
        if (input.readInt() != MAGIC) throw new IOException("Unknown recovery witness format: " + path);
        try { return new Head(new UUID(input.readLong(), input.readLong()), input.readLong(), HexFormat.of().formatHex(input.readNBytes(32))); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid recovery witness: " + path, invalid); }
    }
    public static void verify(Path path, Head expected) throws IOException {
        if (!expected.equals(read(path))) throw new IOException("Recovery witness missing or inconsistent; preserve and reconcile " + path);
    }
    public static void write(Path path, Head head) throws IOException {
        var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes);
        output.writeInt(MAGIC); uuid(output, head.installation()); output.writeLong(head.sequence()); output.write(HexFormat.of().parseHex(head.digest()));
        output.write(HexFormat.of().parseHex(hash(bytes.toByteArray())));
        atomicWrite(path, bytes.toByteArray());
    }
    public static void atomicWrite(Path path, byte[] bytes) throws IOException {
        Path directory = path.toAbsolutePath().getParent(); Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            force(temporary);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    public static void force(Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }
    private static void uuid(DataOutputStream output, UUID id) throws IOException { output.writeLong(id.getMostSignificantBits()); output.writeLong(id.getLeastSignificantBits()); }
}
