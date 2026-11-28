package com.cobbleraids.validation.perf.mixin;

import com.cobbleraids.validation.perf.NativeBootstrapTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService", remap = false)
abstract class NativeBootstrapMixin {
    @Inject(method = "startBattle", at = @At("HEAD"), require = 1)
    private void traceBefore(@Coerce Object battle, String[] messages, CallbackInfo ci) {
        NativeBootstrapTrace.begin(this, messages);
    }
    @Inject(method = "startBattle", at = @At("RETURN"), require = 1)
    private void traceAfter(@Coerce Object battle, String[] messages, CallbackInfo ci) {
        NativeBootstrapTrace.end();
    }
    @Inject(method = "sendFromShowdown", at = @At("HEAD"), require = 1)
    private void callbackBefore(String battle, String message, CallbackInfo ci) {
        NativeBootstrapTrace.callbackEnter(message);
    }
    @Inject(method = "sendFromShowdown", at = @At("RETURN"), require = 1)
    private void callbackAfter(String battle, String message, CallbackInfo ci) {
        NativeBootstrapTrace.callbackExit();
    }
}
