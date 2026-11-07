package com.cobbleraids.catching;

import com.cobbleraids.RaidLog;
import com.cobbleraids.config.RaidRarityTier;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Balance phase 3: a captured raid boss's AscensionLib rarity is rolled as usual but never below its raid tier's floor (starter
 * Uncommon, powerhouse and legendary Rare, mythical Epic). Reached by reflection like {@code AscensionShopHook}; with an older
 * library, or none, the Pokemon is profiled by the library's ordinary gift roll instead. Never undoes the delivery.
 */
final class CaptureRarityFloor {

    private static final String MOD_ID = "ascensionlib";
    private static final String GRANTS = "com.ascensionlib.AscensionGrants";
    private static final String METHOD = "grantWithRarityFloor";

    private static boolean resolved;
    private static Method grant;

    private CaptureRarityFloor() {}

    /** The lowest rarity a boss of this tier is caught at. */
    static String floorFor(RaidRarityTier tier) {
        return switch (tier) {
            case STARTER -> "uncommon";
            case POWERHOUSE, LEGENDARY -> "rare";
            case MYTHICAL -> "epic";
        };
    }

    /** Call right after the Pokemon is in the player's party or PC, on the server thread. */
    static void apply(Pokemon pokemon, RaidRarityTier tier) {
        if (tier == null) return;
        Method method = resolve();
        if (method == null) return;
        try {
            Object result = method.invoke(null, pokemon, floorFor(tier));
            if (!"GRANTED".equals(String.valueOf(result)) && !"DISABLED".equals(String.valueOf(result))) {
                RaidLog.warn("Captured raid boss " + pokemon.getUuid() + " did not get its " + floorFor(tier) + " rarity floor (" + result + ")");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Captured raid boss " + pokemon.getUuid() + " did not get its rarity floor", ex);
        }
    }

    private static Method resolve() {
        if (resolved) return grant;
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return null;
        try {
            grant = Class.forName(GRANTS).getMethod(METHOD, Pokemon.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            grant = null; // an AscensionLib before balance phase 3: the ordinary gift roll applies
        }
        return grant;
    }
}
