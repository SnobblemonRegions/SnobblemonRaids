package com.cobbleraids.showdown;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ShowdownBattleWarmupTest {
    private static boolean warm(Object service) { return ShowdownBattleWarmup.warm(service, "java.lang.String"); }
    public static class Context {
        int calls, starts, sends, ends; boolean fail, failStart, errorOutput; Object boundService;
        final Bindings bindings = new Bindings(this);
        public Bindings getBindings(String language) { assertEquals("js", language); return bindings; }
        public Object eval(String language, CharSequence script) {
            calls++;
            assertEquals("js", language);
            assertFalse(script.toString().isBlank());
            Object[] bridge = (Object[]) bindings.values.get("__cobbleraidsStartupBridge");
            assertEquals(2, bridge.length);
            assertInstanceOf(String[][][].class, bridge[0]);
            boundService = bridge[1];
            if (fail) throw new IllegalStateException("context unavailable");
            return new Function(this, "status");
        }
    }
    public static class Bindings {
        final Context context;
        Bindings(Context context) { this.context = context; }
        final java.util.Map<String, Object> values = new java.util.HashMap<>();
        public boolean hasMember(String name) { return values.containsKey(name); }
        public void putMember(String name, Object value) { values.put(name, value); }
        public boolean removeMember(String name) { return values.remove(name) != null; }
        public Object getMember(String name) { return new Function(context, name); }
    }
    public static class Result { public int asInt() { return 2; } }
    public static class Function {
        final Context context; final String name;
        Function(Context context, String name) { this.context = context; this.name = name; }
        public Object execute(Object... args) {
            switch (name) {
                case "startBattle" -> {
                    context.starts++;
                    assertSame(context.boundService, args[0]);
                    assertInstanceOf(String[].class, args[2]);
                    assertFalse(ShowdownBattleWarmup.consumeWarmupOutput(new Object(), (String) args[1], "update"));
                    assertFalse(ShowdownBattleWarmup.consumeWarmupOutput(args[0], "00000000-0000-4000-8000-000000000001", "|error|live"));
                    assertTrue(ShowdownBattleWarmup.consumeWarmupOutput(args[0], (String) args[1], context.errorOutput ? "|error|fixture" : "update"));
                    if (context.failStart) throw new IllegalStateException("start failed");
                }
                case "sendBattleMessage" -> { context.sends++; assertInstanceOf(String[].class, args[1]); }
                case "endBattle" -> context.ends++;
                case "status" -> { return new Result(); }
                default -> throw new AssertionError(name);
            }
            return null;
        }
    }
    public static class Service {
        final Context context = new Context();
        public Context getContext() { return context; }
    }
    @Test void warmsEachContextOnceAndDoesNotRetainAServiceAsItsIdentity() throws Exception {
        var first = new Service(); var second = new Service();
        assertTrue(warm(first)); assertTrue(warm(first));
        assertTrue(java.util.concurrent.CompletableFuture.supplyAsync(() -> warm(first)).get(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(1, first.context.calls);
        assertEquals(3, first.context.starts); assertEquals(24, first.context.sends); assertEquals(3, first.context.ends);
        assertFalse(ShowdownBattleWarmup.consumeWarmupOutput(first, "cobbleraids:startup:0", "update"));
        assertSame(first, first.context.boundService);
        assertTrue(first.context.bindings.values.isEmpty());
        assertTrue(warm(second)); assertEquals(1, second.context.calls);
    }
    @Test void nativeStartFailureAlwaysEndsThePrivateBattleAndClearsScope() {
        var service = new Service(); service.context.failStart = true;
        assertFalse(warm(service)); assertEquals(1, service.context.ends);
        assertTrue(service.context.bindings.values.isEmpty());
        assertFalse(ShowdownBattleWarmup.consumeWarmupOutput(service, "cobbleraids:startup:0", "update"));
        assertFalse(warm(service)); assertEquals(1, service.context.starts);
    }
    @Test void privateSimulatorErrorsCannotPublishWarmupSuccess() {
        var service = new Service(); service.context.errorOutput = true;
        assertFalse(warm(service)); assertEquals(3, service.context.ends);
        assertTrue(service.context.bindings.values.isEmpty());
        assertFalse(ShowdownBattleWarmup.consumeWarmupOutput(service, "cobbleraids:startup:0", "update"));
    }
    @Test void failedStartupIsContainedAndNeverRetriedDuringALiveReload() throws Exception {
        var service = new Service(); service.context.fail = true;
        assertFalse(warm(service));
        service.context.fail = false;
        assertFalse(java.util.concurrent.CompletableFuture.supplyAsync(() -> warm(service)).get(5, java.util.concurrent.TimeUnit.SECONDS));
        assertFalse(warm(service)); assertEquals(1, service.context.calls);
        assertTrue(service.context.bindings.values.isEmpty());
        assertTrue(warm(new Service()));
    }
    @Test void unsupportedServiceDoesNotEscapeIntoNativeStartup() {
        assertFalse(warm(new Object()));
    }
    @Test void aForeignStartupBindingIsNeverOverwrittenOrRemoved() {
        var service = new Service();
        Object foreign = new Object();
        service.context.bindings.values.put("__cobbleraidsStartupBridge", foreign);
        assertFalse(warm(service));
        assertSame(foreign, service.context.bindings.values.get("__cobbleraidsStartupBridge"));
        assertEquals(0, service.context.calls);
    }
    public static class BrokenContext extends Context {
        @Override public Object eval(String language, CharSequence script) { throw new NoSuchMethodError("native simulator linkage"); }
    }
    public static class BrokenService { public BrokenContext getContext() { return new BrokenContext(); } }
    @Test void reflectionCannotHideFatalNativeLinkageErrors() {
        assertThrows(NoSuchMethodError.class, () -> warm(new BrokenService()));
    }
    @Test void nativeMessageBatchesAreBoundedAndIndependentWithoutExportingHostMethods() {
        var packets = ShowdownBattleWarmup.messages();
        assertEquals(String[][][].class, packets.getClass());
        assertEquals(3, packets[0].length); assertEquals(24, packets[1].length);
        assertEquals(3, packets[0][0].length); assertEquals(1, packets[1][0].length);
        packets[0][0][0] = "first header"; packets[1][0][0] = "first move";
        assertNull(packets[0][1][0]); assertNull(packets[1][1][0]);
    }
    private static class InitializationState { static boolean initialized; }
    private static class LoadOnly { static { InitializationState.initialized = true; } }
    @Test void preloadingDoesNotRunStaticInitializers() throws Exception {
        assertFalse(InitializationState.initialized);
        String name = LoadOnly.class.getName();
        var loader = getClass().getClassLoader();
        ShowdownBattleWarmup.preload(loader, name);
        assertFalse(InitializationState.initialized);
        Class.forName(name, true, loader);
        assertTrue(InitializationState.initialized);
    }
    @Test void aMissingPreloadTypeIsReportedRatherThanSilentlyClaimingWarmup() {
        var service = new Service();
        assertFalse(ShowdownBattleWarmup.warm(service, "missing.cobblemon.CallbackType"));
        assertFalse(warm(service));
        assertEquals(0, service.context.calls);
        assertTrue(service.context.bindings.values.isEmpty());
    }
    public static class CodecModel {}
    public static class CodecRegistry {
        public static final CodecRegistry INSTANCE = new CodecRegistry();
        int adapterCreations;
        private final com.google.gson.Gson gson = new com.google.gson.GsonBuilder().registerTypeAdapterFactory(new com.google.gson.TypeAdapterFactory() {
            @Override @SuppressWarnings("unchecked") public <T> com.google.gson.TypeAdapter<T> create(com.google.gson.Gson owner, com.google.gson.reflect.TypeToken<T> type) {
                if (type.getRawType() != CodecModel.class) return null;
                adapterCreations++;
                return (com.google.gson.TypeAdapter<T>) new com.google.gson.TypeAdapter<CodecModel>() {
                    @Override public CodecModel read(com.google.gson.stream.JsonReader in) { throw new AssertionError("Warmup must not deserialize requests"); }
                    @Override public void write(com.google.gson.stream.JsonWriter out, CodecModel model) { throw new AssertionError("Warmup must not serialize requests"); }
                };
            }
        }).create();
        public com.google.gson.Gson getGson() { return gson; }
    }
    @Test void preparesTheSharedNativeCodecWithoutReadingOrWritingARequest() throws Exception {
        ShowdownBattleWarmup.prepareRequestCodec(CodecRegistry.class, CodecModel.class);
        assertEquals(1, CodecRegistry.INSTANCE.adapterCreations);
        ShowdownBattleWarmup.prepareRequestCodec(CodecRegistry.class, CodecModel.class);
        assertEquals(1, CodecRegistry.INSTANCE.adapterCreations);
    }
}
