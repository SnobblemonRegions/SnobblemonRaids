package com.cobbleraids.mixin.showdown;

import com.cobbleraids.showdown.WorldBossSimulator;
import com.cobbleraids.showdown.ShowdownBattleWarmup;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GraalShowdownService.class, remap = false, priority = 2000)
public abstract class WorldBossSimulatorMixin {
    @Inject(method = "openConnection", at = @At("RETURN"))
    private void cobbleRaids$registryOwner(CallbackInfo ci) {
        try { WorldBossSimulator.opened(this); }
        catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:connection", failure); }
    }
    @Inject(method = "sendRegistryData", at = @At("RETURN"))
    private void cobbleRaids$registryData(Map<String, String> data, String type, CallbackInfo ci) {
        try { WorldBossSimulator.registry(this, "sendRegistryData", type, data, null); }
        catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:registry-data", failure); }
    }
    @Inject(method = "sendRegistryEntry", at = @At("RETURN"))
    private void cobbleRaids$registryEntry(String data, String type, CallbackInfo ci) {
        try { WorldBossSimulator.registry(this, "sendRegistryEntry", type, null, data); }
        catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:registry-entry", failure); }
    }
    @Inject(method = "resetRegistryData", at = @At("RETURN"))
    private void cobbleRaids$registryReset(String type, CallbackInfo ci) {
        try { WorldBossSimulator.registry(this, "resetRegistryData", type, null, null); }
        catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:registry-reset", failure); }
    }
    @Inject(method = "resetAllRegistries", at = @At("RETURN"))
    private void cobbleRaids$registryResetAll(CallbackInfo ci) {
        try { WorldBossSimulator.registry(this, "resetAllRegistries", null, null, null); }
        catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:registry-reset-all", failure); }
    }
    @Inject(method = "indicateSpeciesInitialized", at = @At("RETURN"))
    private void cobbleRaids$registryReady(CallbackInfo ci) {
        try { WorldBossSimulator.species(this); }
        catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:species-ready", failure); }
    }
    @Inject(method = "startBattle", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$privateStart(PokemonBattle battle, String[] messages, CallbackInfo ci) {
        boolean owned = false;
        try {
            if (WorldBossSimulator.starting() && "raid".equals(battle.getFormat().getBattleType().getName()) && battle.getSide2().getActors().length == 1)
                owned = WorldBossSimulator.claimStart(battle.getSide2().getActors()[0].getUuid());
            if (owned) WorldBossSimulator.start(this, battle.getBattleId(), messages);
        }
        catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:start", failure); }
        finally { if (owned) ci.cancel(); } // An owned failure must never retry on the ordinary simulator.
    }
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$privateSend(UUID id, String[] messages, CallbackInfo ci) {
        boolean owned = false;
        try { owned = WorldBossSimulator.owns(id); if (owned) WorldBossSimulator.send(id, messages); }
        catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:send", failure); }
        finally { if (owned) ci.cancel(); }
    }
    @Inject(method = "endBattle", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$privateEnd(PokemonBattle battle, CallbackInfo ci) {
        boolean owned = false;
        try { owned = WorldBossSimulator.owns(battle.getBattleId()); if (owned) WorldBossSimulator.end(battle.getBattleId()); }
        catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:end", failure); }
        finally { if (owned) ci.cancel(); }
    }
    @Inject(method = "sendFromShowdown", at = @At("HEAD"), cancellable = true)
    private void cobbleRaids$privateOutput(String id, String message, CallbackInfo ci) {
        boolean owned = false;
        try {
            owned = WorldBossSimulator.privateService(this);
            if (owned && !ShowdownBattleWarmup.consumeWarmupOutput(this, id, message)) WorldBossSimulator.output(this, id, message);
        } catch (Exception failure) { WorldBossSimulator.failed(failure); RaidFaultBarrier.report("simulator:output", failure); }
        finally { if (owned) ci.cancel(); } // Never interpret worker output against actors off the server thread.
    }
}
