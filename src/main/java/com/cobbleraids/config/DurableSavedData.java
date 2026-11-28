package com.cobbleraids.config;

import com.cobbleraids.RaidLog;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Critical SavedData writes with observable failure and no truncation of the previous generation.
 * Autosaves use the same writer as explicit commits, so a later ordinary save cannot undo that
 * protection. Atomic replacement is required; an unsupported filesystem fails closed.
 * File forcing does not establish a guarantee against every device or directory-metadata failure.
 */
public abstract class DurableSavedData extends SavedData {
    public final void flushStrict(MinecraftServer server, String fileId) throws IOException {
        if (!server.isSameThread()) throw new IllegalStateException("Saved data commits require the server thread");
        writeStrict(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(fileId + ".dat"),
                server.registryAccess());
    }

    final void writeStrict(Path target, HolderLookup.Provider registries) throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag(), registries));
        NbtUtils.addCurrentDataVersion(root);
        commitRoot(target, root);
        setDirty(false);
    }

    protected void commitRoot(Path target, CompoundTag root) throws IOException { writeRoot(target, root); }

    /** Overrides must own the snapshot/dirty/acknowledgement lifecycle; explicit flushStrict always bypasses this hook. */
    protected boolean deferNativeSave(Path target, HolderLookup.Provider registries) throws IOException { return false; }

    protected static void writeRoot(Path target, CompoundTag root) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            NbtIo.writeCompressed(root, temporary);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Minecraft's autosave must preserve dirty state on failure rather than publish a false commit. */
    @Override
    public final void save(File file, HolderLookup.Provider registries) {
        try {
            if (deferNativeSave(file.toPath(), registries)) return;
            if (!isDirty()) return;
            writeStrict(file.toPath(), registries);
        } catch (IOException | RuntimeException failure) {
            setDirty();
            RaidLog.error("Could not atomically save critical data {}", file, failure);
        }
    }
}
