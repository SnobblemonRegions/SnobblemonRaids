package com.cobbleraids.validation.perf;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;

/** Diagnostic only: source-root instrumentation perturbs execution and can never approve capacity. */
public final class NativeBootstrapTrace {
    private static boolean armed, tainted, active, samplingFailed, roots;
    private static final java.lang.management.ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static Thread owner;
    private static ExecutionListener listener;
    private static RootTimings timings;
    private static long started, gcStarted;
    private static long cpuStarted, firstCallback, callbackCpuStarted, callbackCpuNanos;
    private static long attachEnded, firstRoot, lastRoot;
    private static final List<String> CALLBACK_MESSAGES = new ArrayList<>();
    private static long callbackStarted, callbackNanos, maxCallbackNanos, callbackCount;
    private static int callbackDepth;
    private static Map<String, Object> result = Map.of();
    private record SlowRoot(String root, String source, int line, double inclusiveMs, double selfMs, int depth) {}
    private static final PriorityQueue<SlowRoot> SELF = new PriorityQueue<>(Comparator.comparingDouble(SlowRoot::selfMs));
    private static final PriorityQueue<SlowRoot> INCLUSIVE = new PriorityQueue<>(Comparator.comparingDouble(SlowRoot::inclusiveMs));
    private NativeBootstrapTrace() {}

    static void arm() { arm(true); }
    static void armBoundaries() { arm(false); }
    private static void arm(boolean sourceRoots) {
        if (tainted) throw new IllegalStateException("One bootstrap trace per fresh process; prepare another rig");
        roots = sourceRoots;
        gcMillis(); cpuNanos(); // Resolve monitoring metadata before the measured admission.
        armed = true; tainted = true;
        result = Map.of("status", "armed", "scope", "DIAGNOSTIC; no capacity approval", "sourceRoots", roots);
    }
    static boolean tainted() { return tainted; }
    static Map<String, Object> result() { return result; }

    /** Read-only simulator data for an offline runtime comparison; no actor or battle is created. */
    static String exportSpecies() throws ReflectiveOperationException {
        Class<?> api = Class.forName("com.cobblemon.mod.common.battles.runner.ShowdownService");
        Object companion = api.getField("Companion").get(null);
        Object service = companion.getClass().getMethod("getService").invoke(companion);
        Context context = (Context) service.getClass().getMethod("getContext").invoke(service);
        return context.getBindings("js").getMember("getData").execute("species").asString();
    }

    public static void begin(Object service, String[] messages) {
        if (!armed) return;
        armed = false; samplingFailed = false; owner = Thread.currentThread(); timings = new RootTimings(512);
        SELF.clear(); INCLUSIVE.clear();
        CALLBACK_MESSAGES.clear(); firstRoot = lastRoot = attachEnded = 0;
        callbackStarted = callbackNanos = maxCallbackNanos = callbackCount = 0; callbackDepth = 0;
        firstCallback = callbackCpuStarted = callbackCpuNanos = 0;
        var output = new LinkedHashMap<String, Object>();
        output.put("scope", "DIAGNOSTIC ONLY; native/host boundaries; no capacity signoff");
        output.put("sourceRoots", roots);
        output.put("nativeMessages", List.of(messages.clone())); // Synthetic rehearsal accounts only.
        output.put("epochMillis", System.currentTimeMillis());
        result = output;
        started = System.nanoTime(); gcStarted = gcMillis();
        cpuStarted = cpuNanos();
        try {
            active = true;
            if (!roots) {
                attachEnded = started; output.put("status", "recording");
                return; // No context access or execution listener in boundary-only mode.
            }
            Context context = (Context) service.getClass().getMethod("getContext").invoke(service);
            listener = ExecutionListener.newBuilder().roots(true)
                    .onEnter(event -> {
                        if (active && !samplingFailed && Thread.currentThread() == owner) {
                            long now = System.nanoTime(); if (firstRoot == 0) firstRoot = now; timings.enter(now);
                        }
                    })
                    .onReturn(NativeBootstrapTrace::returned).attach(context.getEngine());
            attachEnded = System.nanoTime(); output.put("listenerAttachWallMs", (attachEnded - started) / 1e6);
            output.put("status", "recording");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            output.put("error", failure.toString()); finish("attach-failed");
        }
    }
    private static void returned(ExecutionEvent event) {
        if (!active || samplingFailed || Thread.currentThread() != owner) return;
        try { returnedSafely(event); }
        catch (RuntimeException failure) { samplingFailed = true; result.put("listenerError", failure.toString()); }
    }
    private static void returnedSafely(ExecutionEvent event) {
        lastRoot = System.nanoTime();
        var span = timings.exit(lastRoot);
        if (span == null || span.elapsedNanos() < 500_000) return;
        var location = event.getLocation();
        var sample = new SlowRoot(event.getRootName(), location == null ? "<internal>" : location.getSource().getName(),
                location == null || !location.isAvailable() ? -1 : location.getStartLine(),
                span.elapsedNanos() / 1e6, span.selfNanos() / 1e6, span.depth());
        retain(SELF, sample, Comparator.comparingDouble(SlowRoot::selfMs));
        retain(INCLUSIVE, sample, Comparator.comparingDouble(SlowRoot::inclusiveMs));
    }
    private static void retain(PriorityQueue<SlowRoot> queue, SlowRoot sample, Comparator<SlowRoot> order) {
        if (queue.size() < 64) queue.add(sample);
        else if (order.compare(sample, queue.peek()) > 0) { queue.remove(); queue.add(sample); }
    }
    public static void end() { if (active && Thread.currentThread() == owner) finish("complete"); }
    public static void callbackEnter(String message) {
        if (!active || Thread.currentThread() != owner) return;
        if (callbackDepth++ == 0) {
            callbackStarted = System.nanoTime(); callbackCpuStarted = cpuNanos();
            if (firstCallback == 0) firstCallback = callbackStarted;
        }
        callbackCount++;
        if (CALLBACK_MESSAGES.size() < 16) CALLBACK_MESSAGES.add(message.substring(0, Math.min(message.length(), 20000)));
    }
    public static void callbackExit() {
        if (!active || Thread.currentThread() != owner || callbackDepth == 0) return;
        if (--callbackDepth == 0) {
            long elapsed = System.nanoTime() - callbackStarted;
            callbackNanos += elapsed; maxCallbackNanos = Math.max(maxCallbackNanos, elapsed);
            long cpuEnd = cpuNanos();
            if (callbackCpuStarted >= 0 && cpuEnd >= callbackCpuStarted) callbackCpuNanos += cpuEnd - callbackCpuStarted;
        }
    }
    public static void finishAbandoned() { if (active && Thread.currentThread() == owner) finish("native-return-missing"); }
    private static void finish(String status) {
        long nativeEnd = System.nanoTime();
        long cpuEnd = cpuNanos();
        active = false;
        try { if (listener != null) listener.close(); }
        catch (RuntimeException failure) { result.put("closeError", failure.toString()); }
        finally { listener = null; }
        result.put("status", status); result.put("wallMs", (System.nanoTime() - started) / 1e6);
        result.put("nativeCallWallMs", (nativeEnd - started) / 1e6);
        result.put("outsideHostCallbacksWallMs", (nativeEnd - started - callbackNanos) / 1e6);
        result.put("threadCpuAvailable", cpuStarted >= 0 && cpuEnd >= cpuStarted);
        if (cpuStarted >= 0 && cpuEnd >= cpuStarted) {
            result.put("threadCpuMs", (cpuEnd - cpuStarted) / 1e6);
            result.put("hostCallbackCpuMs", callbackCpuNanos / 1e6);
        }
        if (firstCallback != 0) result.put("firstHostCallbackAfterStartMs", (firstCallback - started) / 1e6);
        if (attachEnded != 0) result.put("instrumentedNativeBodyWallMs", (nativeEnd - attachEnded) / 1e6);
        if (firstRoot != 0 && attachEnded != 0) result.put("firstRootAfterAttachMs", (firstRoot - attachEnded) / 1e6);
        if (lastRoot != 0) result.put("afterLastRootMs", (nativeEnd - lastRoot) / 1e6);
        result.put("gcCollectionDeltaMs", gcMillis() - gcStarted);
        result.put("rootCalls", timings.calls()); result.put("depthOverflow", timings.overflow());
        result.put("hostCallbackWallMs", callbackNanos / 1e6); result.put("maxHostCallbackWallMs", maxCallbackNanos / 1e6);
        result.put("hostCallbackCount", callbackCount); result.put("hostCallbacksBalanced", callbackDepth == 0);
        result.put("nativeOutputMessages", new ArrayList<>(CALLBACK_MESSAGES));
        result.put("maxDepth", timings.maxDepth()); result.put("balanced", timings.balanced());
        var self = new ArrayList<>(SELF); self.sort(Comparator.comparingDouble(SlowRoot::selfMs).reversed());
        var inclusive = new ArrayList<>(INCLUSIVE); inclusive.sort(Comparator.comparingDouble(SlowRoot::inclusiveMs).reversed());
        result.put("topSelf", self); result.put("topInclusive", inclusive);
    }
    private static long gcMillis() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum(); }
    private static long cpuNanos() { return THREADS.isCurrentThreadCpuTimeSupported() && THREADS.isThreadCpuTimeEnabled() ? THREADS.getCurrentThreadCpuTime() : -1; }
}
