package com.cobbleraids.worldboss;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;

/** Captures only the native JSON writer of a paced automatic save; workers receive detached bytes. */
public final class WorldBossAdvancementIo {
    private static volatile Session session;
    private static final ThreadLocal<Path> AUTOMATIC=new ThreadLocal<>();
    private WorldBossAdvancementIo(){}
    static void started(Path directory){if(session!=null)throw new IllegalStateException("Previous advancement I/O still active");session=new Session(directory);}
    static void stopped(){session=null;AUTOMATIC.remove();}
    static void automatic(Path file,Runnable save){
        var previous=AUTOMATIC.get();AUTOMATIC.set(normalized(file));
        try{save.run();}finally{if(previous==null)AUTOMATIC.remove();else AUTOMATIC.set(previous);}
    }
    private static Path normalized(Path file){return file.toAbsolutePath().normalize();}
    public static BufferedWriter writer(Path file,Charset charset,OpenOption[] options)throws IOException{
        var current=session;
        if(current==null||!normalized(file).equals(AUTOMATIC.get()))return null;
        var writer=current.open(file,charset,options);
        if(writer==null)await(file); // An unsupported native writer must follow any earlier detached write.
        return writer;
    }
    static void beforeDirect(Path file){
        if(normalized(file).equals(AUTOMATIC.get()))return;
        await(file);
    }
    static void await(Path file){var current=session;if(current!=null)try{current.await(file);}
        catch(IllegalStateException failure){com.cobbleraids.RaidLog.error("Earlier advancement I/O finished with a failure before native save/load; native recovery may proceed",failure);}}
    static void drainAll(){var current=session;if(current!=null)current.drainAll();}
    static void close(){var current=session;if(current!=null)current.close();}
    static void poll(){var current=session;if(current!=null)current.writes.poll();}
    static Throwable failure(){var current=session;return current==null?null:current.writes.failure();}
    static String diagnostics(){var current=session;return current==null?"idle":current.writes.diagnostics();}

    static final class Session implements AutoCloseable {
        private final Thread owner=Thread.currentThread();
        private final Path directory;
        private final WorldBossWorldSaves.Writer writes;
        private boolean closed;
        Session(Path directory){this(directory,(target,bytes)->Files.write(target.file(),bytes));}
        Session(Path directory,WorldBossWorldSaves.Sink sink){
            this.directory=normalized(directory);writes=new WorldBossWorldSaves.Writer("World Boss advancement JSON",sink);
        }
        BufferedWriter open(Path file,Charset charset,OpenOption[] options){
            file=normalized(file);
            if(Thread.currentThread()!=owner||closed||writes.failure()!=null||!directory.equals(file.getParent())
                    ||!file.getFileName().toString().endsWith(".json")||!StandardCharsets.UTF_8.equals(charset)||options.length!=0)return null;
            return new BufferedWriter(new OutputStreamWriter(new Capture(file),charset.newEncoder()));
        }
        private final class Capture extends OutputStream {
            private final Path file;
            private ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            private OutputStream direct;
            private boolean finished;
            Capture(Path file){this.file=file;}
            private void requireOpen()throws IOException{
                if(Thread.currentThread()!=owner||finished||closed)throw new IOException("Advancement capture is closed or outside Main");
            }
            @Override public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
            @Override public void write(byte[] data,int offset,int length)throws IOException{
                requireOpen();java.util.Objects.checkFromIndexSize(offset,length,data.length);
                if(direct==null&&length>WorldBossWorldSaves.Writer.MAX_SNAPSHOT-bytes.size()){
                    await(file);direct=Files.newOutputStream(file);bytes.writeTo(direct);bytes=null;
                }
                if(direct!=null)direct.write(data,offset,length);else bytes.write(data,offset,length);
            }
            @Override public void flush()throws IOException{requireOpen();if(direct!=null)direct.flush();}
            @Override public void close()throws IOException{
                if(finished)return;requireOpen();finished=true;
                if(direct!=null){direct.close();return;}
                byte[] snapshot=bytes.toByteArray();bytes=null;
                if(writes.submit(new WorldBossWorldSaves.Target(file,null),snapshot)==null){
                    await(file);Files.write(file,snapshot); // Bounded queue/full snapshot fallback preserves per-file ordering.
                }
            }
        }
        void await(Path file){try{writes.drain(normalized(file));}catch(IOException failure){throw new IllegalStateException("Queued advancement write failed before native save/load",failure);}}
        void drainAll(){try{writes.drainAll();}catch(IOException failure){throw new IllegalStateException("Queued advancement writes failed",failure);}}
        @Override public void close(){closed=true;try{writes.close();}catch(IOException failure){throw new IllegalStateException("Advancement writer shutdown failed",failure);}}
    }
}
