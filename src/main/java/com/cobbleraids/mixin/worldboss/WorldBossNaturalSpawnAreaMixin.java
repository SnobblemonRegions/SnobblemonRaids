package com.cobbleraids.mixin.worldboss;

import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.worldboss.WorldBossArenaProtection;
import com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.Spawner;
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use the native "no usable area" result before constructing an entirely forbidden Pokemon zone. */
@Mixin(BasicSpawner.class)
public abstract class WorldBossNaturalSpawnAreaMixin {
    @Inject(method = "constrainArea", at = @At("HEAD"), cancellable = true, remap = false)
    private void cobbleRaids$skipProtectedArea(SpawningZoneInput area, CallbackInfoReturnable<SpawningZoneInput> result) {
        try {
            if (WorldBossArenaProtection.skipNaturalSpawnArea((Spawner) this, area)) result.setReturnValue(null);
        } catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:spawn-area", failure); }
    }
}
