package com.cobbleraids.showdown;

import com.cobbleraids.RaidLog;
import com.cobbleraids.worldboss.WorldBossService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;

/** World-boss-only simulation. Ordinary battles retain Cobblemon's original service and timing. */
public final class WorldBossSimulator {
    private static final Map<Object, BiConsumer<String, String>> PRIVATE = new ConcurrentHashMap<>();
    private static final class StartIntent {
        final UUID encounter, boss;
        boolean claimed;
        StartIntent(UUID encounter, UUID boss) { this.encounter = encounter; this.boss = boss; }
    }
    private static final ThreadLocal<StartIntent> STARTING = new ThreadLocal<>();
    private static volatile Object source;
    private static WorldBossRegistrySnapshot registry = new WorldBossRegistrySnapshot();
    private static volatile WorldBossSimulation simulation;
    private static final AtomicReference<Throwable> unavailable = new AtomicReference<>();
    private static final Map<UUID, UUID> encounters = new ConcurrentHashMap<>();
    private static final Map<UUID, Object> services = new ConcurrentHashMap<>();
    private static boolean failureReported, readyReported;
    private static volatile boolean running;
    private WorldBossSimulator() {}

    public static synchronized void opened(Object service) {
        if (PRIVATE.containsKey(service)) return;
        invalidate("Native simulator connection changed"); source = service; registry = new WorldBossRegistrySnapshot();
    }
    public static synchronized void registry(Object service, String method, String type, Map<String, String> data, String entry) {
        if (PRIVATE.containsKey(service)) return;
        if (source != service) throw new IllegalStateException("Multiple native simulator registry owners");
        invalidate("Native simulator registries reloaded; restart required for world bosses");
        registry.record(method, type, data, entry);
    }
    public static synchronized void species(Object service) { if (!PRIVATE.containsKey(service) && source == service) registry.speciesReady(); }
    public static void invalidate(String reason) {
        if (running) failed(new IllegalStateException(reason));
    }
    public static void failed(Throwable problem) {
        unavailable.compareAndSet(null, problem); WorldBossSimulation lane = simulation; if (lane != null) lane.fail(problem);
    }
    static void registerPrivate(Object service, BiConsumer<String, String> output) { PRIVATE.put(service, output); }
    static void unregisterPrivate(Object service) { PRIVATE.remove(service); }
    public static boolean privateService(Object service) { return PRIVATE.containsKey(service); }
    public static void output(Object service, String id, String message) { PRIVATE.get(service).accept(id, message); }

    public static synchronized void started(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Private simulator startup requires the server thread");
        if (running || simulation != null) { failed(new IllegalStateException("Duplicate private simulator startup")); return; }
        if (!com.cobbleraids.worldboss.WorldBossConfigManager.get().enabled()) return;
        running = true; failureReported = readyReported = false;
        try {
            if (unavailable.get() != null) throw new IllegalStateException("Native registry capture unavailable", unavailable.get());
            Object global = Objects.requireNonNull(source, "No supported native Graal simulator");
            Class<?> type = global.getClass(); List<WorldBossRegistrySnapshot.Operation> snapshot = registry.freeze();
            simulation = new WorldBossSimulation(output -> WorldBossNativeEngine.open(type, snapshot, output));
        } catch (RuntimeException failure) { failed(failure); }
    }
    public static boolean ready() { WorldBossSimulation lane = simulation; return unavailable.get() == null && lane != null && lane.ready(); }
    public static String status() {
        if (unavailable.get() != null || simulation != null && simulation.failure() != null) return "unavailable; restart required (inspect server log)";
        return ready() ? "ready (private world-boss simulator)" : "initializing";
    }
    public static String diagnostics() { WorldBossSimulation lane = simulation; return lane == null ? "not started" : lane.summary(); }
    public static Scope starting(UUID encounter) { return new Scope(encounter, null); }
    public static Scope starting(UUID encounter, UUID boss) { return new Scope(encounter, boss); }
    public static boolean starting() { return STARTING.get() != null; }
    public static boolean claimStart(UUID boss) {
        StartIntent intent = STARTING.get();
        if (intent == null || intent.claimed || intent.boss != null && !intent.boss.equals(boss)) return false;
        intent.claimed = true; return true;
    }
    public static final class Scope implements AutoCloseable {
        private final StartIntent previous;
        Scope(UUID encounter, UUID boss) { previous = STARTING.get(); if (encounter == null) STARTING.remove(); else STARTING.set(new StartIntent(encounter, boss)); }
        @Override public void close() { if (previous == null) STARTING.remove(); else STARTING.set(previous); }
    }
    public static void start(Object service, UUID id, String[] messages) {
        try {
            if (encounters.putIfAbsent(id, Objects.requireNonNull(STARTING.get()).encounter) != null) throw new IllegalStateException("Duplicate private native battle identity");
            services.put(id, service);
            if (!ready()) throw new IllegalStateException("Private world-boss simulator unavailable"); simulation.start(id, messages);
        }
        catch (RuntimeException failure) { failed(failure); }
    }
    public static boolean owns(UUID id) { return encounters.containsKey(id); }
    public static boolean queuedEncounter(UUID encounter) { return encounters.containsValue(encounter); }
    public static void send(UUID id, String[] messages) { try { if (running && simulation != null) simulation.send(id, messages); } catch (RuntimeException failure) { failed(failure); } }
    public static void end(UUID id) { try { if (running && simulation != null) simulation.end(id); } catch (RuntimeException failure) { failed(failure); } }
    public static void tick(MinecraftServer server) {
        WorldBossSimulation lane = simulation;
        if (lane != null && lane.overdue()) failed(new IllegalStateException("Private simulator start acknowledgement exceeded ten seconds"));
        Throwable problem = unavailable.get() != null ? unavailable.get() : lane == null ? null : lane.failure();
        if (problem != null) {
            if (lane != null) lane.fail(problem);
            if (!failureReported) { failureReported = true; RaidLog.error("World-boss private simulator unavailable; holding owned combat", problem); }
            WorldBossService.simulatorUnavailable(server);
            return;
        }
        if (lane == null || !lane.ready()) return;
        if (WorldBossService.arrivalCommitPending() || WorldBossService.controlCommitPending()) return;
        if (!readyReported) { readyReported = true; RaidLog.info("World-boss private simulator ready; ordinary native battles remain on their original context"); }
        long deadline = System.nanoTime() + 5_000_000L;
        for (int count = 0; count < 8 && System.nanoTime() < deadline; count++) {
            WorldBossSimulation.Event event = lane.poll(); if (event == null) break;
            if (event.kind() == WorldBossSimulation.Kind.CLOSED) continue;
            UUID encounter = encounters.get(event.id());
            if (encounter == null) { failed(new IllegalStateException("Private simulator output has no encounter owner")); break; }
            if (event.kind() == WorldBossSimulation.Kind.STARTED && !WorldBossService.simulatorStarted(encounter)) {
                WorldBossService.simulatorStartRejected(encounter); lane.end(event.id()); continue;
            }
            try {
                for (String message : event.messages()) {
                    if (lane.failure() != null || lane.phase(event.id()) != WorldBossSimulation.Phase.OPEN) break;
                    WorldBossNativeEngine.invoke(services.get(event.id()), "sendFromShowdown", new Class<?>[] {String.class, String.class}, event.id().toString(), message);
                }
            } catch (Exception failure) { failed(failure); break; }
        }
    }
    public static void stopping() throws Exception {
        running = false; WorldBossSimulation lane = simulation;
        if (lane != null) lane.close();
    }
    public static void stopped() {
        simulation = null; unavailable.set(null); encounters.clear(); services.clear(); STARTING.remove();
        running = false; failureReported = readyReported = false;
    }
}
