package com.cobbleraids.validation.perf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Rehearsal-only sidecar. No disk writes on the recorded tick path. Never include in production. */
public final class PerfProbe implements ModInitializer {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Frame active;
    private static volatile Frame automaticSave;
    private static long operationId;
    private static volatile boolean recording;
    private static volatile boolean running;
    private static volatile boolean jfrTainted;
    private static boolean postTimingFixtureUsed;
    private static String phase = "setup";
    private static TickSamples samples = new TickSamples(24000);
    private static final ConcurrentLinkedQueue<Stall> STALLS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger stallCount = new AtomicInteger();
    private static final AtomicInteger stallDropped = new AtomicInteger();
    private static long id, previousStart;
    private static final java.lang.management.ThreadMXBean CPU=java.lang.management.ManagementFactory.getThreadMXBean();
    private static final List<CpuTime> CPU_TIMES=new ArrayList<>();
    private static String label;
    private record Frame(long id, long epochMillis, long startNanos, long interval, String phase, int players, Thread thread,long cpuStart) {}
    private record CpuTime(long id,long cpuNanos){}
    private record Stall(long tick, long epochMillis, double elapsedMs, List<String> stack, String operation) {}

    @Override public void onInitialize() {
        // Refuse accidental use without the launcher's fresh disposable-world identity.
        var root = FabricLoader.getInstance().getGameDir();
        if (!Files.isRegularFile(root.resolve("rehearsal.json"))) throw new IllegalStateException("Performance probe requires a disposable rehearsal rig");
        GcPauses.initialize();
        jdk.jfr.FlightRecorder.addListener(new jdk.jfr.FlightRecorderListener() {
            @Override public void recordingStateChanged(jdk.jfr.Recording recording) {
                if (recording.getState() == jdk.jfr.RecordingState.RUNNING) jfrTainted = true;
            }
        });
        for (var recording : jdk.jfr.FlightRecorder.getFlightRecorder().getRecordings())
            if (recording.getState() == jdk.jfr.RecordingState.RUNNING) jfrTainted = true;
        NativeAsyncPrototype.initialize(root);
        NativeShowpieceCheck.initialize();
        try{NativeRecoveryCheck.initialize(root);}catch(java.io.IOException failure){throw new IllegalStateException("Recovery fixture identity unavailable",failure);}
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
            Commands.literal("wbperf").requires(s -> s.hasPermission(4) && s.getEntity() == null)
                .then(Commands.literal("poi-read-ahead-check").executes(ctx->{
                    if(recording)throw new IllegalStateException("POI fixture is forbidden during timing");
                    postTimingFixtureUsed=true;
                    try{
                        String result=NativePoiReadAheadCheck.run(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(()->Component.literal("WBPERF POI read ahead "+result),false);return 1;
                    }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native POI fixture failed",failure);}
                }))
                .then(Commands.literal("command-node-check").executes(ctx->{
                    if(recording)throw new IllegalStateException("Command fixture is forbidden during timing");
                    postTimingFixtureUsed=true;
                    try{
                        String result=NativeCommandNodesCheck.run(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(()->Component.literal("WBPERF command nodes "+result),false);return 1;
                    }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native command fixture failed",failure);}
                }))
                .then(Commands.literal("listener-hash-check").executes(ctx->{
                    if(recording)throw new IllegalStateException("Listener fixture is forbidden during timing");
                    postTimingFixtureUsed=true;
                    String result=NativeAdvancementHashCheck.run(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(()->Component.literal("WBPERF listener hash "+result),false);return 1;
                }))
                .then(Commands.literal("recovery-snapshot").executes(ctx->{
                    if(recording)throw new IllegalStateException("Recovery inspection is forbidden during timing");
                    try{
                        String result=NativeRecoveryCheck.snapshot(ctx.getSource().getServer(),root);
                        ctx.getSource().sendSuccess(()->Component.literal("WBPERF recovery "+result),false);return 1;
                    }catch(Exception failure){throw new IllegalStateException("Native recovery inspection failed",failure);}
                }))
                .then(Commands.literal("tick-id").executes(ctx->{
                    ctx.getSource().sendSuccess(()->Component.literal("WBPERF tick-id="+id),false);return 1;
                }))
                .then(Commands.literal("trace-next").executes(ctx -> {
                    NativeBootstrapTrace.arm();
                    ctx.getSource().sendSuccess(() -> Component.literal("WBPERF next native bootstrap armed; diagnostic only"), false); return 1;
                }))
                .then(Commands.literal("boundaries-next").executes(ctx -> {
                    NativeBootstrapTrace.armBoundaries();
                    ctx.getSource().sendSuccess(() -> Component.literal("WBPERF next native boundary timing armed; no JS listener; diagnostic only"), false); return 1;
                }))
                .then(Commands.literal("export-species").executes(ctx -> {
                    try {
                        String data = NativeBootstrapTrace.exportSpecies();
                        if (data.length() > 32_000_000) throw new IllegalStateException("Species export exceeds diagnostic budget");
                        int count = com.google.gson.JsonParser.parseString(data).getAsJsonArray().size();
                        var out = root.resolve("logs").resolve("wbperf-species-" + UUID.randomUUID() + ".json");
                        Files.writeString(out, data, StandardCharsets.UTF_8);
                        ctx.getSource().sendSuccess(() -> Component.literal("WBPERF read-only species export count=" + count + " file=" + out.getFileName()), false); return 1;
                    } catch (ReflectiveOperationException | java.io.IOException | RuntimeException failure) {
                        ctx.getSource().sendFailure(Component.literal("WBPERF species export failed: " + failure)); return 0;
                    }
                }))
                .then(Commands.literal("simulator-stats").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("WBPERF simulator " + new Gson().toJson(NativeAsyncPrototype.stats(true))), false); return 1;
                }))
                .then(Commands.literal("spawn-area-check").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
                    String name=StringArgumentType.getString(ctx,"player");
                    if(!name.startsWith("WBCompat_")) throw new IllegalArgumentException("Synthetic compatibility account required");
                    var player=ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                    if(player==null) throw new IllegalArgumentException("Compatibility client is not connected");
                    try {
                        String result=NativeSpawnAreaCheck.run(player);
                        ctx.getSource().sendSuccess(()->Component.literal("WBPERF spawn area "+result),false); return 1;
                    } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Native spawn area fixture failed",failure); }
                })))
                .then(Commands.literal("automatic-player-save").executes(ctx->{
                    if(!recording)throw new IllegalStateException("Use only inside an explicit native pilot recording");
                    try{
                        String result=NativePlayerSaveCheck.automatic(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(()->Component.literal("WBPERF automatic saves "+result),false);return 1;
                    }catch(Exception failure){throw new IllegalStateException("Native automatic player-save batch failed",failure);}
                }))
                .then(Commands.literal("world-save-check").executes(ctx->{
                    if(recording)throw new IllegalStateException("World save fixture is forbidden during timing");
                    try{
                        String result=NativeWorldSaveCheck.run(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(()->Component.literal("WBPERF world saves "+result),false);return 1;
                    }catch(Exception failure){throw new IllegalStateException("Native world save fixture failed",failure);}
                }))
                .then(Commands.literal("card-stat-check").then(Commands.argument("player",StringArgumentType.word()).executes(ctx->{
                    if(recording)throw new IllegalStateException("Card fixture is forbidden during timing");
                    String name=StringArgumentType.getString(ctx,"player");
                    var player=ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                    if(!name.startsWith("WBCompat_")||player==null)throw new IllegalArgumentException("Connected synthetic compatibility client required");
                    try{
                        String result=NativeCardStatCheck.run(player);
                        ctx.getSource().sendSuccess(()->Component.literal("WBPERF native card stat "+result),false);return 1;
                    }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native Cards stat fixture failed",failure);}
                })))
                .then(Commands.literal("arm-showpiece-stop").executes(ctx -> {
                    if(recording) throw new IllegalStateException("Re-entry fixture is forbidden during timing");
                    try {
                        NativeShowpieceCheck.arm();
                        ctx.getSource().sendSuccess(()->Component.literal("WBPERF ARMED post-timing TEST showpiece stop"),false);return 1;
                    } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Could not arm native re-entry fixture",failure); }
                }))
                .then(Commands.literal("player-save-check").then(Commands.argument("left",StringArgumentType.word())
                    .then(Commands.argument("right",StringArgumentType.word()).executes(ctx->{
                        if(recording)throw new IllegalStateException("Player save fixture is forbidden during timing");
                        String left=StringArgumentType.getString(ctx,"left"),right=StringArgumentType.getString(ctx,"right");
                        if(!left.startsWith("WBCompat_")||!right.startsWith("WBCompat_")||left.equals(right))throw new IllegalArgumentException("Two synthetic compatibility clients required");
                        var server=ctx.getSource().getServer();
                        var a=server.getPlayerList().getPlayerByName(left);var b=server.getPlayerList().getPlayerByName(right);
                        if(a==null||b==null)throw new IllegalArgumentException("Compatibility clients must be online");
                        try{
                            String result=NativePlayerSaveCheck.run(server,a,b);
                            ctx.getSource().sendSuccess(()->Component.literal("WBPERF player saves "+result),false);return 1;
                        }catch(Exception failure){throw new IllegalStateException("Native player save fixture failed",failure);}
                    }))))
                .then(Commands.literal("showpiece-stop-result").executes(ctx -> {
                    try {
                        String result=new GsonBuilder().serializeNulls().create().toJson(NativeShowpieceCheck.result(ctx.getSource().getServer()));
                        ctx.getSource().sendSuccess(()->Component.literal("WBPERF showpiece "+result),false);return 1;
                    } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Could not inspect native re-entry fixture",failure); }
                }))
                .then(Commands.literal("ordinary-duel").then(Commands.argument("left", StringArgumentType.word())
                    .then(Commands.argument("right", StringArgumentType.word()).executes(ctx -> {
                        String leftName = StringArgumentType.getString(ctx, "left"), rightName = StringArgumentType.getString(ctx, "right");
                        if (!leftName.startsWith("WBCompat_") || !rightName.startsWith("WBCompat_") || leftName.equals(rightName)) throw new IllegalArgumentException("Synthetic compatibility accounts only");
                        var left = ctx.getSource().getServer().getPlayerList().getPlayerByName(leftName);
                        var right = ctx.getSource().getServer().getPlayerList().getPlayerByName(rightName);
                        if (left == null || right == null) throw new IllegalArgumentException("Compatibility clients are not connected");
                        try {
                            Class<?> builder = Class.forName("com.cobblemon.mod.common.battles.BattleBuilder");
                            Object result = builder.getMethod("pvp1v1", net.minecraft.server.level.ServerPlayer.class, net.minecraft.server.level.ServerPlayer.class)
                                    .invoke(builder.getField("INSTANCE").get(null), left, right);
                            Object battle = result.getClass().getMethod("getBattle").invoke(result);
                            var id = (UUID) battle.getClass().getMethod("getBattleId").invoke(battle);
                            boolean owned = (Boolean) Class.forName("com.cobbleraids.showdown.WorldBossSimulator").getMethod("owns", UUID.class).invoke(null, id);
                            ctx.getSource().sendSuccess(() -> Component.literal("WBPERF ordinary duel id=" + id + " worldBossOwned=" + owned), false); return 1;
                        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native ordinary duel failed", failure); }
                    }))))
                .then(Commands.literal("start").then(Commands.argument("label", StringArgumentType.word()).executes(ctx -> {
                    if(postTimingFixtureUsed)throw new IllegalStateException("Post-timing node fixture already ran; use a fresh server process");
                    if (recording) throw new IllegalStateException("Recording already active");
                    if (NativeRecoveryCheck.used()) throw new IllegalStateException("Native recovery inspection used; a fresh process is required for timing");
                    if (NativeShowpieceCheck.used()) throw new IllegalStateException("Native re-entry fixture used; a fresh rig is required for timing");
                    if (NativePlayerSaveCheck.used()) throw new IllegalStateException("Native player save fixture used; a fresh rig is required for timing");
                    if (NativeCardStatCheck.used()) throw new IllegalStateException("Native card stat fixture used; a fresh rig is required for timing");
                    if (NativeWorldSaveCheck.used()) throw new IllegalStateException("Native world save fixture used; a fresh rig is required for timing");
                    label = StringArgumentType.getString(ctx, "label");
                    if (!label.matches("[a-zA-Z0-9_-]{1,40}")) throw new IllegalArgumentException("Invalid recording label");
                    synchronized (STALLS) {
                        active = null; samples = new TickSamples(24000); CPU_TIMES.clear(); STALLS.clear(); stallCount.set(0); stallDropped.set(0);
                        previousStart = 0; phase = "setup"; recording = true;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("WBPERF recording " + label), false); return 1;
                })))
                .then(Commands.literal("mark").then(Commands.argument("phase", StringArgumentType.word()).executes(ctx -> {
                    phase = StringArgumentType.getString(ctx, "phase");
                    ctx.getSource().sendSuccess(() -> Component.literal("WBPERF phase " + phase), false); return 1;
                })))
                .then(Commands.literal("stop").executes(ctx -> {
                    if (!recording) throw new IllegalStateException("No recording active");
                    recording = false; active = null;
                    var out = root.resolve("logs").resolve("wbperf-" + label + "-" + UUID.randomUUID() + ".json");
                    Map<String, Object> result = new LinkedHashMap<>(); result.put("scope", "tickServer call wall time including Fabric callbacks; intervals also include outer dispatch and idle");
                    result.put("diagnosticJavascriptTrace", NativeBootstrapTrace.tainted());
                    result.put("diagnosticRuntimePrototype", NativeAsyncPrototype.enabled());
                    result.put("diagnosticJfrRecording", jfrTainted);
                    result.put("gcNotifications", GcPauses.snapshot());
                    if (NativeAsyncPrototype.enabled()) result.put("prototypeStats", NativeAsyncPrototype.stats(false));
                    if (NativeBootstrapTrace.tainted()) result.put("bootstrapTrace", NativeBootstrapTrace.result());
                    result.put("summary", samples.summary(null));
                    result.put("tickCpuTimes",CPU_TIMES);
                    result.put("cpuTimePolicy","Current Main-thread CPU time, -1 when unavailable; OS accounting granularity applies; wall-time gates unchanged");
                    Map<String, Object> phases = new LinkedHashMap<>();
                    samples.ticks().stream().map(TickSamples.Tick::phase).distinct().forEach(p -> phases.put(p, samples.summary(p)));
                    result.put("phases", phases); result.put("ticks", samples.ticks());
                    result.put("stallSamplingPolicy", "up to 3 stacks per slow tick or explicit automatic-save call; initial >=100ms, spacing >=200ms; negative IDs identify operations outside tick recording");
                    synchronized (STALLS) { result.put("stalls", new ArrayList<>(STALLS)); result.put("stallSamplesDropped", stallDropped.get()); }
                    try { Files.createDirectories(out.getParent()); Files.writeString(out, JSON.toJson(result), StandardCharsets.UTF_8); }
                    catch (java.io.IOException failure) { ctx.getSource().sendFailure(Component.literal("WBPERF write failed: " + failure)); return 0; }
                    ctx.getSource().sendSuccess(() -> Component.literal("WBPERF " + new Gson().toJson(samples.summary(null)) + " file=" + out.getFileName()), false); return 1;
                }))));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            NativeAsyncPrototype.started(server);
            running = true;
            Thread watchdog = new Thread(PerfProbe::watch, "WorldBoss rehearsal stack sampler"); watchdog.setDaemon(true); watchdog.start();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { recording = false; active = null; running = false; NativeAsyncPrototype.close(); });
    }

    public static void begin(MinecraftServer server) {
        if (!recording) return;
        long now = System.nanoTime();
        active = new Frame(++id, System.currentTimeMillis(), now, previousStart == 0 ? 0 : now - previousStart, phase, server.getPlayerCount(), Thread.currentThread(),cpuNow());
        previousStart = now;
    }
    public static void end() {
        NativeBootstrapTrace.finishAbandoned();
        Frame frame = active;
        if (frame == null) return;
        active = null;
        samples.add(new TickSamples.Tick(frame.id(), frame.epochMillis(), frame.phase(), frame.players(), System.nanoTime() - frame.startNanos(), frame.interval()));
        long cpu=cpuNow();
        if(CPU_TIMES.size()<24000)CPU_TIMES.add(new CpuTime(frame.id(),frame.cpuStart()<0||cpu<0?-1:Math.max(0,cpu-frame.cpuStart())));
    }
    private static long cpuNow(){return CPU.isCurrentThreadCpuTimeSupported()&&CPU.isThreadCpuTimeEnabled()?CPU.getCurrentThreadCpuTime():-1;}
    static void sampleAutomaticSave(MinecraftServer server){
        if(!server.isSameThread()||automaticSave!=null)throw new IllegalStateException("Automatic-save probe requires non-nested Main execution");
        automaticSave=new Frame(--operationId,System.currentTimeMillis(),System.nanoTime(),0,"automatic-player-save",server.getPlayerCount(),Thread.currentThread(),cpuNow());
        try{server.saveEverything(true,false,false);}finally{automaticSave=null;}
    }
    private static void watch() {
        var budget = new StallSampleBudget();
        while (running) {
            try { Thread.sleep(25); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); return; }
            Frame frame = active;
            if(frame==null)frame=automaticSave;
            if (frame == null || System.nanoTime() - frame.startNanos() < 100_000_000) continue;
            if (!budget.allow(frame.id(), System.nanoTime())) continue;
            if (stallCount.get() >= 512) { stallDropped.incrementAndGet(); continue; }
            var frames = frame.thread().getStackTrace();
            List<String> stack = new ArrayList<>();
            for (int i = 0; i < Math.min(frames.length, 128); i++) stack.add(frames[i].toString());
            if (frames.length > 128) {
                stack.add("... middle frames omitted; outer callers retained ...");
                for (int i = Math.max(128, frames.length - 32); i < frames.length; i++) stack.add(frames[i].toString());
            }
            synchronized (STALLS) {
                if (!recording || (active != frame&&automaticSave!=frame)) continue; // Never attribute a stale sample to a later tick/operation.
                if (stallCount.incrementAndGet() <= 512) STALLS.add(new Stall(frame.id(), System.currentTimeMillis(), (System.nanoTime() - frame.startNanos()) / 1e6, stack,frame.id()<0?frame.phase():null));
            }
        }
    }
}
