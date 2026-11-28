package com.cobbleraids.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.HolderLookup;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

class DurableSavedDataTest {
    @BeforeAll static void gameVersion() { SharedConstants.tryDetectVersion(); }
    @TempDir Path directory;

    private static final class Store extends DurableSavedData {
        int value;
        boolean failSerialization;
        Path pauseBeforeSerialization;
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            if (failSerialization) throw new IllegalStateException("failed serialization");
            if (pauseBeforeSerialization != null) {
                try {
                    Files.writeString(pauseBeforeSerialization, "ready");
                    new java.util.concurrent.CountDownLatch(1).await();
                } catch (java.io.IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            tag.putInt("value", value);
            return tag;
        }
    }

    @Test void realCompressedFileReplacesPreviousGenerationAndClearsDirtyOnlyOnCommit() throws Exception {
        Store store = new Store();
        Path file = directory.resolve("state.dat");
        store.value = 1; store.setDirty(); store.writeStrict(file, null);
        store.value = 2; store.setDirty(); store.writeStrict(file, null);
        assertEquals(2, NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data").getInt("value"));
        assertFalse(store.isDirty());
        try (var children = Files.list(directory)) { assertEquals(1, children.count()); }
    }

    @Test void rejectedSerializationLeavesCommittedBytesAndDirtyIntentUntouched() throws Exception {
        Store store = new Store();
        Path file = directory.resolve("state.dat");
        store.value = 1; store.setDirty(); store.writeStrict(file, null);
        byte[] before = Files.readAllBytes(file);
        store.failSerialization = true; store.setDirty();
        assertThrows(IllegalStateException.class, () -> store.writeStrict(file, null));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertTrue(store.isDirty());
    }

    @Test void failedReplacementKeepsThePriorTargetAndCleansTemporaryFile() throws Exception {
        Path target = directory.resolve("occupied");
        Files.createDirectory(target);
        Files.writeString(target.resolve("keep.txt"), "keep");
        Store store = new Store(); store.setDirty();
        assertThrows(java.io.IOException.class, () -> store.writeStrict(target, null));
        assertEquals("keep", Files.readString(target.resolve("keep.txt")));
        assertTrue(store.isDirty());
        try (var children = Files.list(directory)) { assertEquals(1, children.count()); }
    }

    @Test void forcedJvmTerminationBeforeAndAfterCommitKeepsACompleteGeneration() throws Exception {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            paths.add(Path.of(entry).toString().replace('\\', '/'));
        }
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) {
                    if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()).toString().replace('\\', '/'));
                }
            }
        }
        assertFalse(paths.isEmpty(), "The child must run with the actual test runtime classpath");
        String classpath = String.join(java.io.File.pathSeparator, paths);
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        for (String phase : new String[] {"before", "after"}) {
            Path target = directory.resolve(phase + ".dat"), ready = directory.resolve(phase + ".ready");
            Path args = directory.resolve(phase + ".args");
            String arguments = "-cp\n\"" + classpath + "\"\n" + getClass().getName() + "\n\""
                    + target.toString().replace('\\', '/') + "\"\n\"" + ready.toString().replace('\\', '/') + "\"\n" + phase;
            Files.writeString(args, arguments, StandardCharsets.UTF_8);
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "@" + args)
                    .redirectErrorStream(true).start();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!Files.exists(ready) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
                String diagnostics = child.isAlive() ? "probe still running" : new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(Files.exists(ready), "Crash probe did not reach " + phase + ": " + diagnostics);
                child.destroyForcibly();
                assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Crash probe did not terminate");
                CompoundTag root = NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap());
                assertEquals(phase.equals("before") ? 1 : 2, root.getCompound("data").getInt("value"));
            } finally {
                if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); }
                child.getInputStream().close();
                child.getOutputStream().close();
                child.getErrorStream().close();
            }
        }
    }

    /** Separate-JVM crash probe, invoked only by the termination test above. */
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Path target = Path.of(args[0]), ready = Path.of(args[1]);
        Store store = new Store(); store.value = 1; store.setDirty(); store.writeStrict(target, null);
        store.value = 2;
        if (args[2].equals("before")) store.pauseBeforeSerialization = ready;
        store.setDirty(); store.writeStrict(target, null);
        Files.writeString(ready, "ready");
        new java.util.concurrent.CountDownLatch(1).await();
    }
}
