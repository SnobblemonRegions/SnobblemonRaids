package com.cobbleraids.validation.perf;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import org.graalvm.polyglot.Context;

/** Explicit fresh-rig experiment. Never packaged with the gameplay mod or eligible for capacity approval. */
public final class NativeAsyncPrototype {
    private static SerialSimulatorQueue queue;
    private static volatile MinecraftServer server;
    private static volatile boolean stopping;
    private static volatile SimulatorCallbackGate<Output> delivery;
    private static volatile Object service;
    private static final java.util.Set<String> startupIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> liveIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private record Output(Object service, String id, String message) {}
    private NativeAsyncPrototype() {}

    static void initialize(Path root) {
        Path marker = root.resolve("wbperf-async-native.txt");
        if (!Files.exists(marker)) return;
        try {
            String nonce = JsonParser.parseString(Files.readString(root.resolve("rehearsal.json"))).getAsJsonObject().get("nonce").getAsString();
            if (!Files.readString(marker).strip().equals(nonce)) throw new IllegalStateException("Prototype marker is not owned by this fresh rig");
            queue = new SerialSimulatorQueue(1024, problem -> System.err.println("[WB-PROTOTYPE] simulator failed: " + problem));
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot validate prototype identity", failure); }
    }
    public static boolean enabled() { return queue != null; }
    private static boolean onWorker() { return enabled() && queue.onWorker(); }
    static void started(MinecraftServer value) {
        if (!enabled()) return;
        if (stopping || server != null) throw new IllegalStateException("Simulator prototype requires a fresh JVM per server");
        delivery = new SimulatorCallbackGate<>(value::execute, value::isSameThread, () -> queue.failure() == null,
                output -> invoke(output.service, "sendFromShowdown", new Class<?>[] {String.class, String.class}, output.id, output.message), queue::fail);
        server = value;
    }
    static void close() {
        stopping = true;
        SimulatorCallbackGate<Output> gate = delivery;
        if (gate != null) gate.close();
        server = null;
        if (enabled()) queue.close();
    }

    public static boolean gameplay(Object receiver, String method, Class<?>[] types, Object... args) {
        // Cobblemon runs a synchronous dummy battle during BagItems data loading.
        // No live tick loop exists then; preserve that bootstrap contract.
        if (!enabled() || onWorker()) return false;
        if (stopping) throw new java.util.concurrent.RejectedExecutionException("Simulator prototype is stopping");
        service = receiver;
        if (method.equals("startBattle")) {
            try {
                String id = args[0].getClass().getMethod("getBattleId").invoke(args[0]).toString();
                (server == null ? startupIds : liveIds).add(id);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        if (server == null) return false;
        SimulatorCallbackGate<Output> gate = delivery;
        queue.execute(() -> gate.run(() -> invoke(receiver, method, types, args)));
        return true;
    }
    public static boolean callback(Object receiver, String id, String message) {
        SimulatorCallbackGate<Output> gate = delivery;
        return onWorker() && gate != null && gate.capture(new Output(receiver, id, message));
    }
    public static Class<?> battleType(Object receiver) {
        try { return Class.forName("com.cobblemon.mod.common.api.battles.model.PokemonBattle", false, receiver.getClass().getClassLoader()); }
        catch (ClassNotFoundException failure) { throw new IllegalStateException(failure); }
    }
    private static void invoke(Object receiver, String method, Class<?>[] types, Object... args) {
        try { receiver.getClass().getMethod(method, types).invoke(receiver, args); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error fatal) throw fatal;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    static Map<String, Object> stats(boolean querySimulator) {
        var result = new java.util.LinkedHashMap<String, Object>(); result.put("enabled", enabled());
        if (!enabled()) return result;
        result.putAll(queue.stats());
        SimulatorCallbackGate<Output> gate = delivery;
        if (gate != null) result.putAll(gate.stats());
        result.put("stopping", stopping);
        result.put("startup_battle_ids", List.copyOf(startupIds)); result.put("live_battles_started", liveIds.size());
        if (querySimulator && service != null && queue.failure() == null && !stopping) {
            var count = new CompletableFuture<List<String>>();
            queue.execute(() -> {
                try {
                    Context context = (Context) service.getClass().getMethod("getContext").invoke(service);
                    var ids = JsonParser.parseString(context.eval("js", "JSON.stringify(Array.from(battleMap.keys()))").asString()).getAsJsonArray();
                    var values = new ArrayList<String>(); ids.forEach(id -> values.add(id.getAsString())); count.complete(List.copyOf(values));
                } catch (ReflectiveOperationException | RuntimeException failure) { count.completeExceptionally(failure); }
            });
            try {
                List<String> ids = count.get(2, TimeUnit.SECONDS);
                result.put("remaining_simulator_ids", ids);
                result.put("remaining_live_simulator_battles", ids.stream().filter(liveIds::contains).count());
                result.put("remaining_startup_simulator_battles", ids.stream().filter(startupIds::contains).count());
                result.put("unclassified_simulator_battles", ids.stream().filter(id -> !startupIds.contains(id) && !liveIds.contains(id)).count());
            }
            catch (Exception failure) { result.put("query_error", failure.toString()); if (failure instanceof InterruptedException) Thread.currentThread().interrupt(); }
        }
        return result;
    }
}
