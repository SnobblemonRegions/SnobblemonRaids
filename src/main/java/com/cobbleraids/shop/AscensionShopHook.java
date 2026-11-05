package com.cobbleraids.shop;

import com.cobbleraids.RaidLog;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Gives a shop Pokemon its operator-set AscensionLib rarity.
 *
 * <p>Reached by reflection rather than a compile-time dependency, for the reason
 * {@code CobbleDollarsCurrencyBackend} is: AscensionLib is optional, and adding it to the build would make it a
 * required artifact for anyone building CobbleRaids (CI included). The contract is the single static method
 * {@code com.ascensionlib.AscensionGrants.grantWithRarity(Pokemon, String)}, which returns a result enum whose
 * {@code GRANTED} constant means the Pokemon now has its profile. When the mod is not installed a rarity on a
 * shop entry is ignored, said once. A failure of any kind is logged and never undoes the purchase.
 */
final class AscensionShopHook {

    private static final String MOD_ID = "ascensionlib";
    private static final String GRANTS = "com.ascensionlib.AscensionGrants";
    private static final String GRANT = "grantWithRarity";

    private static boolean resolved;
    private static Method grant;
    private static boolean saidMissing;

    private static boolean materialResolved;
    private static Method grantMaterial;

    private AscensionShopHook() {}

    /**
     * Balance phase 3: {@code ascensionlib:scouter} and {@code ascensionlib:resonance_dust} are not items but AscensionLib wallet
     * materials. An entry selling one is listed only while the library can credit it, and a purchase credits the wallet.
     */
    static boolean isMaterial(String itemId) {
        return itemId != null && (itemId.equals("ascensionlib:scouter") || itemId.equals("ascensionlib:resonance_dust"));
    }

    /** Whether a material entry can be sold right now. */
    static boolean canSellMaterial() {
        return resolveMaterial() != null;
    }

    /** Credits the material after the points were taken; a failure is logged for an operator to settle, like a missed rarity. */
    static void creditMaterial(java.util.UUID player, String itemId, int count, String entryId) {
        Method method = resolveMaterial();
        String material = itemId.substring(itemId.indexOf(':') + 1);
        if (method == null) {
            RaidLog.error("Shop entry " + entryId + ": " + player + " paid for " + count + " " + material + " but AscensionLib is not usable");
            return;
        }
        try {
            Object result = method.invoke(null, java.util.UUID.randomUUID(), player, material, (long) count, "raid_shop:" + entryId);
            if (!"GRANTED".equals(String.valueOf(result))) {
                RaidLog.error("Shop entry " + entryId + ": " + player + " paid for " + count + " " + material + " but was not credited (" + result + ")");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Shop entry " + entryId + ": " + player + " paid for " + count + " " + material + " but was not credited", ex);
        }
    }

    private static Method resolveMaterial() {
        if (materialResolved) return grantMaterial;
        materialResolved = true;
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return null;
        try {
            grantMaterial = Class.forName(GRANTS).getMethod("grantMaterial", java.util.UUID.class, java.util.UUID.class, String.class,
                    long.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            grantMaterial = null; // an AscensionLib before balance phase 3: material entries stay hidden
        }
        return grantMaterial;
    }

    /**
     * Gives the just-added Pokemon its rarity, if the entry has one. The points are already taken and the Pokemon
     * already delivered, so a failure here is a line in the log naming the Pokemon and the rarity it should have
     * had, for an operator to settle by hand.
     */
    static void apply(Pokemon pokemon, ShopPokemonGift gift, String entryId) {
        if (gift.rarity() == null) return;
        Method method = resolve();
        if (method == null) {
            if (!saidMissing) {
                saidMissing = true;
                RaidLog.warn("Shop entry " + entryId + " sets a rarity but AscensionLib is not usable; rarities are ignored.");
            }
            return;
        }
        try {
            Object result = method.invoke(null, pokemon, gift.rarity());
            if (!"GRANTED".equals(String.valueOf(result))) {
                RaidLog.error("Shop entry " + entryId + ": Pokemon " + pokemon.getUuid() + " was delivered without its "
                        + gift.rarity() + " ascension profile (" + result + ").");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Shop entry " + entryId + ": Pokemon " + pokemon.getUuid() + " was delivered without its "
                    + gift.rarity() + " ascension profile.", ex);
        }
    }

    /** Resolved once; null when the mod is absent or its contract is not what this expects (said in the log). */
    private static Method resolve() {
        if (resolved) return grant;
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return null;
        try {
            grant = Class.forName(GRANTS).getMethod(GRANT, Pokemon.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            RaidLog.error("AscensionLib is installed but " + GRANTS + "." + GRANT + " could not be resolved", ex);
        }
        return grant;
    }
}
