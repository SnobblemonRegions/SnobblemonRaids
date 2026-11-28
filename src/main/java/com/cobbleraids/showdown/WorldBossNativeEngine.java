package com.cobbleraids.showdown;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** All methods, native context access and disposal belong to the private worker. */
final class WorldBossNativeEngine implements WorldBossSimulation.Engine {
    private final Object service;
    private Object context, start, send, end;
    private WorldBossNativeEngine(Object service) { this.service = service; }
    static WorldBossNativeEngine open(Class<?> nativeType, List<WorldBossRegistrySnapshot.Operation> registry,
                                     BiConsumer<String, String> output) throws Exception {
        Object service = nativeType.getConstructor().newInstance();
        var engine = new WorldBossNativeEngine(service);
        WorldBossSimulator.registerPrivate(service, output);
        try {
            // Reuse the version-checked native builder and already patched files; never unbundle/rewrite shared files.
            declared(service, "createContext");
            engine.context = invoke(service, "getContext", new Class<?>[0]);
            declared(service, "boot");
            for (var operation : registry) {
                switch (operation.method()) {
                    case "resetAllRegistries" -> invoke(service, operation.method(), new Class<?>[0]);
                    case "resetRegistryData" -> invoke(service, operation.method(), new Class<?>[] {String.class}, operation.type());
                    case "sendRegistryData" -> invoke(service, operation.method(), new Class<?>[] {java.util.Map.class, String.class}, operation.data(), operation.type());
                    case "sendRegistryEntry" -> invoke(service, operation.method(), new Class<?>[] {String.class, String.class}, operation.entry(), operation.type());
                    default -> throw new IllegalStateException("Unknown private registry operation");
                }
            }
            invoke(service, "indicateSpeciesInitialized", new Class<?>[0]);
            if (!ShowdownBattleWarmup.warm(service)) throw new IllegalStateException("Private simulator startup verification failed");
            Object bindings = invoke(engine.context, "getBindings", new Class<?>[] {String.class}, "js");
            engine.start = invoke(bindings, "getMember", new Class<?>[] {String.class}, "startBattle");
            engine.send = invoke(bindings, "getMember", new Class<?>[] {String.class}, "sendBattleMessage");
            engine.end = invoke(bindings, "getMember", new Class<?>[] {String.class}, "endBattle");
            if (engine.start == null || engine.send == null || engine.end == null) throw new IllegalStateException("Missing private native simulator entry points");
            return engine;
        } catch (Exception | Error failure) {
            try { engine.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public void start(UUID id, String[] messages) throws Exception {
        // Same native Value.execute arguments, but only immutable IDs/messages cross the worker boundary.
        execute(start, service, id.toString(), messages);
        if (!contains(id)) throw new IllegalStateException("Private simulator did not retain its accepted battle");
    }
    @Override public void send(UUID id, String[] messages) throws Exception { execute(send, id.toString(), messages); }
    @Override public void end(UUID id) throws Exception {
        execute(end, id.toString());
        if (contains(id)) throw new IllegalStateException("Private simulator failed to remove a closed battle");
    }
    private boolean contains(UUID id) throws Exception {
        Object result = invoke(context, "eval", new Class<?>[] {String.class, CharSequence.class}, "js", "battleMap.has('" + id + "')");
        return (Boolean) invoke(result, "asBoolean", new Class<?>[0]);
    }
    @Override public void close() throws Exception {
        if (context != null) invoke(service, "closeConnection", new Class<?>[0]);
        WorldBossSimulator.unregisterPrivate(service); context = null;
    }
    private static void declared(Object receiver, String name) throws Exception {
        var method = receiver.getClass().getDeclaredMethod(name); method.setAccessible(true);
        try { method.invoke(receiver); } catch (InvocationTargetException failure) { rethrow(failure); }
    }
    static Object invoke(Object receiver, String name, Class<?>[] types, Object... arguments) throws Exception {
        try { return receiver.getClass().getMethod(name, types).invoke(receiver, arguments); }
        catch (InvocationTargetException failure) { rethrow(failure); return null; }
    }
    private static void execute(Object function, Object... arguments) throws Exception {
        invoke(function, "execute", new Class<?>[] {Object[].class}, (Object) arguments);
    }
    private static void rethrow(InvocationTargetException failure) throws Exception {
        if (failure.getCause() instanceof Error fatal) throw fatal;
        if (failure.getCause() instanceof Exception problem) throw problem;
        throw new IllegalStateException(failure.getCause());
    }
}
