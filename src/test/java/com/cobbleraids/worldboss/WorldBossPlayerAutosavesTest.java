package com.cobbleraids.worldboss;

import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossPlayerAutosavesTest {
    @TempDir Path directory;
    @BeforeAll static void version(){net.minecraft.SharedConstants.tryDetectVersion();}
    private Path save(UUID id,CompoundTag tag,boolean capture,String name) throws Exception {
        try(var scope=new WorldBossPlayerAutosaves.Capture(new WorldBossPlayerSaves.Key(directory,id),capture)){
            Path temporary=WorldBossPlayerAutosaves.temporary(directory,id+"-",".dat",new FileAttribute<?>[0]);
            assertEquals(!capture,Files.exists(temporary));
            WorldBossPlayerAutosaves.write(tag,temporary);
            tag.putInt("fixture",99); // Captured bytes must not retain mutable NBT.
            Path target=directory.resolve(name+".dat");
            WorldBossPlayerAutosaves.replace(target,temporary,directory.resolve(name+".dat_old"));
            assertFalse(Files.exists(temporary));
            return target;
        }
    }
    private CompoundTag tag(int value){var tag=new CompoundTag();tag.putInt("fixture",value);return tag;}
    private int value(Path path) throws Exception{return NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()).getInt("fixture");}
    @Test void nativeCallsOutsideAutomaticCaptureKeepVanillaFilesAndBackup() throws Exception {
        UUID id=UUID.randomUUID();
        Path target=save(id,tag(1),false,id.toString());
        assertEquals(1,value(target));
        save(id,tag(2),false,id.toString());
        assertEquals(2,value(target));assertEquals(1,value(directory.resolve(id+".dat_old")));
    }
    @Test void refusedAsyncCaptureFallsBackToRealTemporaryWithoutLosingSnapshot() throws Exception {
        UUID id=UUID.randomUUID();Path target=save(id,tag(7),true,id.toString());
        assertEquals(7,value(target));
        save(id,tag(8),true,id.toString());
        assertEquals(8,value(target));assertEquals(7,value(directory.resolve(id+".dat_old")));
    }
    @Test void customReplacementNamesPreserveCapturedPayload() throws Exception {
        assertEquals(3,value(save(UUID.randomUUID(),tag(3),true,"custom")));
    }
    @Test void nestedSavesRestoreTheOuterCapture() throws Exception {
        UUID outer=UUID.randomUUID(),inner=UUID.randomUUID();
        try(var scope=new WorldBossPlayerAutosaves.Capture(new WorldBossPlayerSaves.Key(directory,outer),true)){
            Path temporary=WorldBossPlayerAutosaves.temporary(directory,outer+"-",".dat",new FileAttribute<?>[0]);
            WorldBossPlayerAutosaves.write(tag(4),temporary);
            assertEquals(5,value(save(inner,tag(5),false,inner.toString())));
            WorldBossPlayerAutosaves.replace(directory.resolve(outer+".dat"),temporary,directory.resolve(outer+".dat_old"));
            assertEquals(4,value(directory.resolve(outer+".dat")));
        }
        Path ordinary=WorldBossPlayerAutosaves.temporary(directory,"ordinary-",".dat",new FileAttribute<?>[0]);
        assertTrue(Files.exists(ordinary));
    }
}
