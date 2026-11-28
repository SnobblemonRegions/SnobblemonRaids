package com.cobbleraids.showdown;

import com.cobbleraids.RaidLog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Set;

/**
 * Exercises the native context's cold turn-resolution paths before the server admits players.
 * Runs on the species-ready caller, never a background thread or an event's combat tick.
 * The script exercises the real bootstrap and output pump with a private sink. Its bounded
 * simulator-only IDs never enter Cobblemon's native battle registry and are always removed.
 * No entities, players or economic listeners participate.
 */
public final class ShowdownBattleWarmup {
    private static final Map<Object, Boolean> WARMED = new WeakHashMap<>();
    private static final String BRIDGE = "__cobbleraidsStartupBridge";
    private static final String PREFIX = "cobbleraids:startup:";
    private static final ThreadLocal<OutputScope> OUTPUT = new ThreadLocal<>();
    private static final String[] NATIVE_TYPES = {
            "kotlin.collections.builders.ListBuilder",
            "com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket$ActiveBattlePokemonDTO",
            "com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket$ActiveBattlePokemonDTO$Companion",
            "com.cobblemon.mod.common.battles.interpreter.instructions.AbilityInstruction",
            "com.cobbleraids.renown.RaidRenownMarker"
    };
    private ShowdownBattleWarmup() {}

    public static boolean warm(Object service) { return warm(service, true, NATIVE_TYPES); }

    /** Package seam: lifecycle tests use ordinary JDK types without a Minecraft runtime. */
    static boolean warm(Object service, String... preloadNames) { return warm(service, false, preloadNames); }

    private static synchronized boolean warm(Object service, boolean nativeCodec, String... preloadNames) {
        try {
            Object context = service.getClass().getMethod("getContext").invoke(service);
            if (context == null) throw new IllegalStateException("Missing native simulator context");
            if (WARMED.containsKey(context)) return Boolean.TRUE.equals(WARMED.get(context));
            WARMED.put(context, Boolean.FALSE); // Never retry expensive failed startup work during a live data reload.
            String script;
            try (var stream = ShowdownBattleWarmup.class.getResourceAsStream("/assets/cobbleraids/showdown/battle-warmup.js")) {
                if (stream == null) throw new IOException("Missing simulator startup warmup resource");
                script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            long started = System.nanoTime();
            // Only types observed loading in native first-admission stalls. Loading
            // without initialization runs no packet/static registry constructors.
            preload(service.getClass().getClassLoader(), preloadNames);
            if (nativeCodec) {
                var loader = service.getClass().getClassLoader();
                // Observed on the first native output, after simulator-only warmup.
                // Verified 1.8.2 initializers create only Regex/companion objects;
                // no message parsing, battle dispatch or entity switching occurs.
                Class.forName("com.cobblemon.mod.common.api.battles.interpreter.BattleMessage", true, loader);
                Class.forName("com.cobblemon.mod.common.battles.interpreter.instructions.SwitchInstruction", true, loader);
                prepareRequestCodec(Class.forName("com.cobblemon.mod.common.battles.BattleRegistry", true, loader),
                        Class.forName("com.cobblemon.mod.common.battles.ShowdownActionRequest", false, loader));
            }
            var evaluate = context.getClass().getMethod("eval", String.class, CharSequence.class);
            String[][][] packets = messages();
            try (var output = new OutputScope(service); var binding = bindMessages(context, service, packets)) {
                Object statusFunction = evaluate.invoke(context, "js", script);
                Object bindings = context.getClass().getMethod("getBindings", String.class).invoke(context, "js");
                var member = bindings.getClass().getMethod("getMember", String.class);
                Object start = member.invoke(bindings, "startBattle"), send = member.invoke(bindings, "sendBattleMessage"), end = member.invoke(bindings, "endBattle");
                if (start == null || send == null || end == null) throw new IllegalStateException("Missing native simulator functions");
                for (int run = 0; run < 3; run++) {
                    String id = PREFIX + run;
                    try {
                        execute(start, service, id, packets[0][run]);
                        for (int turn = 0; turn < 8 && status(statusFunction, run) > 0; turn++) execute(send, id, packets[1][run * 8 + turn]);
                        if (Math.abs(status(statusFunction, run)) < 2) throw new IllegalStateException("Native startup warmup made no progress");
                    } finally { execute(end, id); }
                }
                if (output.count < 3 || output.error != null) throw new IllegalStateException("Native startup output failed: " + output.error);
            }
            WARMED.put(context, Boolean.TRUE);
            RaidLog.info("Simulator startup turn warmup completed in {} ms; no native battle or reward callback was registered", (System.nanoTime() - started) / 1_000_000);
            return true;
        } catch (ReflectiveOperationException | IOException | RuntimeException failure) {
            // Optimization failure must not take down Cobblemon's own startup or ordinary battles.
            Throwable cause = failure instanceof java.lang.reflect.InvocationTargetException invocation ? invocation.getCause() : failure;
            if (cause instanceof Error fatal) throw fatal;
            RaidLog.warn("Simulator startup warmup unavailable; first-combat performance needs verification", cause);
            return false;
        }
    }

    /** Match native String[] arguments without enabling Graal's deliberately absent Java namespace. */
    static String[][][] messages() { return new String[][][] {new String[3][3], new String[24][1]}; }

    /** Resolve the real decoder's metadata; never deserialize, sanitize or dispatch an actor request. */
    static void prepareRequestCodec(Class<?> registryType, Class<?> requestType) throws ReflectiveOperationException {
        Object registry = registryType.getField("INSTANCE").get(null);
        var gson = (com.google.gson.Gson) registryType.getMethod("getGson").invoke(registry);
        gson.getAdapter(requestType);
    }

    static void preload(ClassLoader loader, String... names) throws ClassNotFoundException {
        for (String name : names) Class.forName(name, false, loader);
    }

    private static Object execute(Object function, Object... arguments) throws ReflectiveOperationException {
        return function.getClass().getMethod("execute", Object[].class).invoke(function, (Object) arguments);
    }
    private static int status(Object function, int run) throws ReflectiveOperationException {
        Object result = execute(function, run);
        return (Integer) result.getClass().getMethod("asInt").invoke(result);
    }

    /** Only the owning startup thread/service and three non-UUID simulator IDs may consume output. */
    public static boolean consumeWarmupOutput(Object service, String id, String message) {
        OutputScope scope = OUTPUT.get();
        if (scope == null || scope.service != service || id == null || !scope.ids.contains(id)) return false;
        scope.count++;
        if (message == null || message.contains("|error|")) scope.error = message == null ? "null message" : message.substring(0, Math.min(512, message.length()));
        return true;
    }
    private static final class OutputScope implements AutoCloseable {
        final Object service;
        final Set<String> ids = Set.of(PREFIX + 0, PREFIX + 1, PREFIX + 2);
        int count; String error;
        OutputScope(Object service) {
            if (OUTPUT.get() != null) throw new IllegalStateException("Nested simulator startup output scope");
            this.service = service; OUTPUT.set(this);
        }
        @Override public void close() { OUTPUT.remove(); }
    }

    private static Binding bindMessages(Object context, Object service, String[][][] packets) throws ReflectiveOperationException {
        Object bindings = context.getClass().getMethod("getBindings", String.class).invoke(context, "js");
        Class<?> type = bindings.getClass();
        if (Boolean.TRUE.equals(type.getMethod("hasMember", String.class).invoke(bindings, BRIDGE))) {
            throw new IllegalStateException("Simulator startup message binding already exists");
        }
        var remove = type.getMethod("removeMember", String.class);
        type.getMethod("putMember", String.class, Object.class).invoke(bindings, BRIDGE, new Object[] {packets, service});
        return new Binding(bindings, remove);
    }
    private record Binding(Object value, java.lang.reflect.Method remove) implements AutoCloseable {
        @Override public void close() throws ReflectiveOperationException { remove.invoke(value, BRIDGE); }
    }
}
