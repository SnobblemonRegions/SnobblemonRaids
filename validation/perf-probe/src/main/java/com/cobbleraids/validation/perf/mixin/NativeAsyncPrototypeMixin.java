package com.cobbleraids.validation.perf.mixin;

import com.cobbleraids.validation.perf.NativeAsyncPrototype;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService", remap = false, priority = 2000)
abstract class NativeAsyncPrototypeMixin {
    @Inject(method = "startBattle", at = @At("HEAD"), cancellable = true)
    private void start(@Coerce Object battle, String[] messages, CallbackInfo ci) {
        if (NativeAsyncPrototype.enabled() && NativeAsyncPrototype.gameplay(this, "startBattle", new Class<?>[] {NativeAsyncPrototype.battleType(this), String[].class}, battle, messages.clone())) ci.cancel();
    }
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void send(UUID id, String[] messages, CallbackInfo ci) {
        if (NativeAsyncPrototype.enabled() && NativeAsyncPrototype.gameplay(this, "send", new Class<?>[] {UUID.class, String[].class}, id, messages.clone())) ci.cancel();
    }
    @Inject(method = "endBattle", at = @At("HEAD"), cancellable = true)
    private void end(@Coerce Object battle, CallbackInfo ci) {
        if (NativeAsyncPrototype.enabled() && NativeAsyncPrototype.gameplay(this, "endBattle", new Class<?>[] {NativeAsyncPrototype.battleType(this)}, battle)) ci.cancel();
    }
    @Inject(method = "sendFromShowdown", at = @At("HEAD"), cancellable = true)
    private void output(String id, String message, CallbackInfo ci) {
        if (NativeAsyncPrototype.callback(this, id, message)) ci.cancel();
    }
}
