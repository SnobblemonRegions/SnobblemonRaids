package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;

/** Main-thread adapter around vanilla's serialization and file calls, not a replacement player serializer. */
public final class WorldBossPlayerAutosaves {
    private static volatile MinecraftServer server;
    private static volatile WorldBossPlayerSaves writer;
    private static boolean failureReported, snapshotWarning, stopping;
    private static final ThreadLocal<Automatic> AUTOMATIC=new ThreadLocal<>();
    private static final ThreadLocal<Capture> CAPTURE=new ThreadLocal<>();
    private WorldBossPlayerAutosaves() {}
    static void started(MinecraftServer current) {
        if(writer!=null)throw new IllegalStateException("Previous player autosave queue has not been stopped");
        server=current;failureReported=false;snapshotWarning=false;stopping=false;
    }
    static void stopped() { server=null;writer=null;AUTOMATIC.remove();CAPTURE.remove(); }
    static Throwable poll(MinecraftServer current) {
        var queue=writer;
        if(current!=server || queue==null)return null;
        queue.poll();
        if(queue.failure()!=null&&!failureReported){failureReported=true;return queue.failure();}
        return null;
    }
    static boolean failed() { var queue=writer;return queue!=null&&queue.failure()!=null; }
    static String diagnostics() { var queue=writer;return queue==null?"idle":queue.diagnostics(); }
    static void close(MinecraftServer current) throws IOException { if(current==server){stopping=true;if(writer!=null)writer.close();} }
    static void awaitBackup() throws IOException{var queue=writer;if(queue!=null)queue.drainAll();}

    /** Only automatic, non-flushing/non-forced dedicated-server player batches may defer I/O. */
    public static Automatic automatic(MinecraftServer current,boolean silent,boolean flush,boolean forced) {
        boolean enabled=eligible(current,silent,flush,forced);
        if(!enabled)drainAll();
        return new Automatic(current,enabled);
    }
    static boolean eligible(MinecraftServer current,boolean silent,boolean flush,boolean forced) {
        return current==server&&current.isSameThread()&&current.isDedicatedServer()&&silent&&!flush&&!forced
                &&!stopping&&!failed()&&!WorldBossWorldSaves.failed()&&!WorldBossAdvancementSaves.failed();
    }
    public static final class Automatic implements AutoCloseable {
        private final Automatic previous=AUTOMATIC.get();
        private final MinecraftServer owner;
        private final boolean enabled;
        private Automatic(MinecraftServer owner,boolean enabled){this.owner=owner;this.enabled=enabled;AUTOMATIC.set(this);}
        @Override public void close(){if(previous==null)AUTOMATIC.remove();else AUTOMATIC.set(previous);}
    }
    public static Capture begin(PlayerDataStorage storage,Path directory,Player player) {
        var key=new WorldBossPlayerSaves.Key(directory,player.getUUID());
        var context=AUTOMATIC.get();
        boolean enabled=context!=null&&context.enabled&&context.owner==server&&server.isSameThread()
                &&storage.getClass()==PlayerDataStorage.class&&CAPTURE.get()==null
                &&player instanceof ServerPlayer online&&online.server==server&&online.connection!=null&&online.connection.isAcceptingMessages();
        if(!enabled)drain(key);
        if(enabled&&writer==null)writer=new WorldBossPlayerSaves();
        return new Capture(key,enabled&&!failed());
    }
    public static void beforeLoad(Path directory,Player player){drain(new WorldBossPlayerSaves.Key(directory,player.getUUID()));}
    public static final class Capture implements AutoCloseable {
        private final Capture previous=CAPTURE.get();
        private final WorldBossPlayerSaves.Key key;
        private boolean enabled, finished;
        private Path token, actual;
        private String prefix,suffix;
        private byte[] snapshot;
        Capture(WorldBossPlayerSaves.Key key,boolean enabled){this.key=key;this.enabled=enabled;CAPTURE.set(this);}
        private boolean owns(Path path){return token!=null&&token.equals(path.toAbsolutePath().normalize());}
        private Path actual() throws IOException {
            if(actual==null)actual=Files.createTempFile(key.directory(),prefix,suffix);
            return actual;
        }
        @Override public void close(){
            if(snapshot!=null&&!finished)RaidLog.warn("An arena player save ended before its file replacement; vanilla recovery evidence is unchanged");
            if(previous==null)CAPTURE.remove();else CAPTURE.set(previous);
        }
    }
    public static Path temporary(Path directory,String prefix,String suffix,FileAttribute<?>[] attributes) throws IOException {
        var capture=CAPTURE.get();
        if(capture!=null&&capture.enabled&&attributes.length==0
                &&directory.toAbsolutePath().normalize().equals(capture.key.directory())&&prefix.equals(capture.key.player()+"-")){
            capture.prefix=prefix;capture.suffix=suffix;
            capture.token=capture.key.directory().resolve(prefix+"arena-"+UUID.randomUUID()+suffix);
            return capture.token; // Correlation token only. The worker creates the real native temporary file.
        }
        return Files.createTempFile(directory,prefix,suffix,attributes);
    }
    public static void write(CompoundTag tag,Path temporary) throws IOException {
        var capture=CAPTURE.get();
        if(capture!=null&&capture.enabled&&capture.owns(temporary)){
            try{capture.snapshot=serialize(tag);return;}
            catch(IOException|RuntimeException unsupported){
                capture.enabled=false;
                if(!snapshotWarning){snapshotWarning=true;RaidLog.warn("An arena player snapshot exceeded the async capture envelope; keeping vanilla I/O: {}",unsupported.getClass().getSimpleName());}
            }
        }
        NbtIo.writeCompressed(tag,capture!=null&&capture.owns(temporary)?capture.actual():temporary);
    }
    public static void replace(Path target,Path temporary,Path backup) throws IOException {
        var capture=CAPTURE.get();
        boolean owned=capture!=null&&capture.owns(temporary);
        boolean nativeNames=capture!=null&&target.toAbsolutePath().normalize().equals(capture.key.directory().resolve(capture.key.player()+".dat"))
                &&backup.toAbsolutePath().normalize().equals(capture.key.directory().resolve(capture.key.player()+".dat_old"));
        if(owned&&capture.snapshot!=null){
            var queue=writer;
            if(nativeNames&&capture.enabled&&queue!=null&&queue.submit(capture.key,capture.snapshot)){capture.finished=true;return;}
            // A bounded-queue refusal keeps the original synchronous save and its ordering.
            if(nativeNames)drain(capture.key);else drainAll();
            try(var output=new GZIPOutputStream(Files.newOutputStream(capture.actual()))){output.write(capture.snapshot);}
        } else if(capture!=null){if(nativeNames)drain(capture.key);else drainAll();}
        Util.safeReplaceFile(target,owned?capture.actual():temporary,backup);
        if(capture!=null)capture.finished=true;
    }
    private static byte[] serialize(CompoundTag tag) throws IOException {
        var bytes=new ByteArrayOutputStream();
        var bounded=new OutputStream(){
            public void write(int value) throws IOException {
                if(bytes.size()>=WorldBossPlayerSaves.MAX_SNAPSHOT)throw new IOException("Player NBT exceeds 16 MiB");
                bytes.write(value);
            }
            public void write(byte[] data,int offset,int length) throws IOException {
                if(length>WorldBossPlayerSaves.MAX_SNAPSHOT-bytes.size())throw new IOException("Player NBT exceeds 16 MiB");
                bytes.write(data,offset,length);
            }
        };
        NbtIo.write(tag,new DataOutputStream(bounded));
        return bytes.toByteArray();
    }
    private static void drain(WorldBossPlayerSaves.Key key){
        var queue=writer;if(queue==null)return;
        try{queue.drain(key);}catch(IOException failure){RaidLog.error("Earlier player autosave failed; its I/O has finished before the critical native save/load",failure);}
    }
    private static void drainAll(){
        var queue=writer;if(queue==null)return;
        try{queue.drainAll();}catch(IOException failure){RaidLog.error("Earlier player autosaves failed; accepted I/O drained before the critical native save",failure);}
    }
}
