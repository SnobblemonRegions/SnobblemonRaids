package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.BossEvent;

/** Optional server-side skin ownership; the native blue bar is the fallback. */
final class WorldBossBarSkin {
    private static boolean unavailable;

    private WorldBossBarSkin() {}

    static void claim(BossEvent bar) {
        if (unavailable || !FabricLoader.getInstance().isModLoaded("bossbarskins")) return;
        try {
            // BossBarSkins 0.1.0 API, verified against the supplied runtime JAR.
            Class.forName("com.bossbarskins.api.BossBarSkinsApi")
                    .getMethod("claim", BossEvent.class, String.class).invoke(null, bar, "cobbleraids");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            unavailable = true;
            RaidLog.warn("World Boss skin ownership unavailable; using the native blue bar: {}", failure.toString());
        }
    }
}
