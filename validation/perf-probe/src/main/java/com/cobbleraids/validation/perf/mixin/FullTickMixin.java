package com.cobbleraids.validation.perf.mixin;

import com.cobbleraids.validation.perf.PerfProbe;
import com.cobbleraids.validation.perf.NativeBootstrapTrace;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wraps the caller, including Fabric HEAD/RETURN tick listeners, without replacing dispatch. */
@Mixin(MinecraftServer.class)
abstract class FullTickMixin {
    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"), require = 1, allow = 1)
    private void beforeFullTick(CallbackInfo ci) {
        PerfProbe.begin((MinecraftServer) (Object) this);
    }

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V", shift = At.Shift.AFTER), require = 1, allow = 1)
    private void afterFullTick(CallbackInfo ci) {
        NativeBootstrapTrace.finishAbandoned();
        PerfProbe.end();
    }
}
