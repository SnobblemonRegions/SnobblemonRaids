package com.cobbleraids.raid;

import com.cobbleraids.RaidLog;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Lets AscensionLib's battle effects act in an ordinary raid: the boss is declared as an enemy of its own encounter and the
 * players' battle is armed for it just before the battle starts.
 *
 * <p>Reached by reflection, for the reason {@code AscensionShopHook} is: the library is optional and must not become a build
 * dependency. The contract is four static methods on {@code com.ascensionlib.AscensionEncounters} taking only {@code java.*}
 * types. Nothing here can stop a raid: a failure is logged and the raid simply fights natively.
 *
 * <p>Only an ordinary raid is handled. A raid another mod owns (CobbleTowers) arms its own battle, with its own encounter, so
 * arming it again here would replace that.
 */
public final class AscensionLibArming {

    private static final String MOD_ID = "ascensionlib";
    private static final String CLASS = "com.ascensionlib.AscensionEncounters";
    /** A raid boss is the library's top enemy tier; its reveal is shared with the whole party. */
    private static final String BOSS_TIER = "boss";

    private static boolean resolved;
    private static Method declare;
    private static Method arm;
    private static Method disarm;
    private static Method end;
    /** Optional: an older library without raid attunement still arms battles. */
    private static Method settleAttunement;
    /** Optional (balance phase 1+): Threat bands and share-based raid pay. */
    private static Method threatEnabled;
    private static Method settleRaid;

    private AscensionLibArming() {}

    /** An ordinary raid is always armed; an owned encounter only when its policy asks. */
    static boolean wanted(RaidSession.Ownership ownership) {
        return ownership == null || ownership.policy().ascensionEffects();
    }

    /** One encounter per boss spawn: the boss Pokemon's UUID is unique and never reused. */
    static String encounterId(UUID bossPokemonId) {
        return "raid:" + bossPokemonId;
    }

    /**
     * Balance phase 3: the Threat band a raid tier fights at (starter II, powerhouse IV, legendary V, mythical VI; the world
     * boss is VII, see {@link #WORLD_BOSS_THREAT}).
     */
    public static int threatFor(com.cobbleraids.config.RaidRarityTier tier) {
        return switch (tier) {
            case STARTER -> 2;
            case POWERHOUSE -> 4;
            case LEGENDARY -> 5;
            case MYTHICAL -> 6;
        };
    }

    public static final int WORLD_BOSS_THREAT = 7;

    /** The enemy tier to declare: {@code threat_N} while the library's Threat system is on, the old {@code boss} otherwise. */
    static String enemyTier(int threat) {
        return threat > 0 && threatOn() ? "threat_" + threat : BOSS_TIER;
    }

    /** Whether the library is new enough to have Threat and has it switched on. */
    public static boolean threatOn() {
        if (!resolve() || threatEnabled == null) return false;
        try {
            return Boolean.TRUE.equals(threatEnabled.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return false;
        }
    }

    /** Declares the boss and arms the battle for it. Pair with {@link #disarm}. Returns whether the battle is armed. */
    static boolean declareAndArm(Collection<UUID> players, UUID bossPokemonId, String speciesId, int level) {
        return declareAndArm(players, bossPokemonId, speciesId, level, 0);
    }

    /** As above at a Threat band (0: the legacy boss tier). */
    static boolean declareAndArm(Collection<UUID> players, UUID bossPokemonId, String speciesId, int level, int threat) {
        if (!resolve()) return false;
        String encounter = encounterId(bossPokemonId);
        try {
            Object status = declare.invoke(null, encounter, players, 0, enemyTier(threat), true, null, speciesId, level);
            if (!"DECLARED".equals(String.valueOf(status))) return false; // DISABLED (no world) or unknown species: fight natively
            arm.invoke(null, players, encounter);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Arming the raid battle of " + speciesId + " for ascension effects failed; it fights natively", ex);
            return false;
        }
    }

    static void disarm(Collection<UUID> players) {
        if (!resolve()) return;
        try {
            disarm.invoke(null, players);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Disarming the raid battle for ascension effects failed", ex);
        }
    }

    /**
     * A won ordinary raid: every victor's party earns attunement toward promotion. The library pays no materials for it (Raids keeps
     * its own rewards) and counts it once per raid and Pokemon, so a repeat changes nothing. Players who are offline miss it.
     */
    public static void settleVictory(UUID bossPokemonId, Collection<UUID> victors) {
        if (!resolve() || settleAttunement == null) return;
        try {
            settleAttunement.invoke(null, encounterId(bossPokemonId), "VICTORY", victors);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Awarding raid attunement through AscensionLib failed", ex);
        }
    }

    /**
     * Balance phase 3: a won raid paid by damage share (Apex Shards, attunement and dust from the library's raid bands). Falls back
     * to {@link #settleVictory(UUID, Collection)} when the library has no {@code settleRaid}. {@code damage} is each victor's
     * contribution; it is turned into shares of 1 here.
     */
    public static void settleVictory(String encounterId, String tierId, Map<UUID, ? extends Number> damage, Collection<UUID> victors,
                                     Runnable fallback) {
        if (!resolve()) return;
        if (settleRaid == null) {
            fallback.run();
            return;
        }
        Map<UUID, Double> shares = new java.util.LinkedHashMap<>();
        com.cobbleraids.reward.ContributionMath.percentages(damage, victors).forEach((id, pct) -> shares.put(id, pct / 100.0));
        try {
            settleRaid.invoke(null, encounterId, "VICTORY", tierId, shares);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Paying the raid through AscensionLib failed", ex);
        }
    }

    /** The ordinary raid's form of the above. */
    public static void settleVictory(UUID bossPokemonId, com.cobbleraids.config.RaidRarityTier tier, Map<UUID, ? extends Number> damage,
                                     Collection<UUID> victors) {
        settleVictory(encounterId(bossPokemonId), tier.serializedName(), damage, victors, () -> settleVictory(bossPokemonId, victors));
    }

    /**
     * Balance phase 3, for duel raids: declares the raid's boss once for every player, keyed by the lobby boss's Pokemon. Each duel
     * then arms its one player with {@link #arm} just before its battle starts. Returns whether the boss was declared.
     */
    public static boolean declare(Collection<UUID> players, UUID bossPokemonId, String speciesId, int level, int threat) {
        if (!resolve()) return false;
        try {
            Object status = declare.invoke(null, encounterId(bossPokemonId), players, 0, enemyTier(threat), true, null, speciesId, level);
            return "DECLARED".equals(String.valueOf(status));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Declaring the duel raid of " + speciesId + " to AscensionLib failed; it fights natively", ex);
            return false;
        }
    }

    /** Arms the next battle of {@code players} for a boss already {@link #declare declared}. Pair with {@link #disarmAll}. */
    public static void arm(Collection<UUID> players, UUID bossPokemonId) {
        if (!resolve()) return;
        try {
            arm.invoke(null, players, encounterId(bossPokemonId));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Arming a duel for ascension effects failed; it fights natively", ex);
        }
    }

    public static void disarmAll(Collection<UUID> players) { disarm(players); }

    public static void endEncounter(UUID bossPokemonId) { end(bossPokemonId); }

    /** The raid is over: the library forgets the boss and every reveal. Safe to repeat. */
    static void end(UUID bossPokemonId) {
        if (!resolve()) return;
        try {
            end.invoke(null, encounterId(bossPokemonId));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            RaidLog.error("Ending the raid's ascension encounter failed", ex);
        }
    }

    private static boolean resolve() {
        if (!resolved) {
            resolved = true;
            if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return false;
            try {
                Class<?> encounters = Class.forName(CLASS);
                declare = encounters.getMethod("declareEnemy", String.class, Collection.class, int.class, String.class,
                        boolean.class, String.class, String.class, int.class);
                arm = encounters.getMethod("armBattle", Collection.class, String.class);
                disarm = encounters.getMethod("disarmBattle", Collection.class);
                end = encounters.getMethod("end", String.class);
                try {
                    settleAttunement = Class.forName("com.ascensionlib.AscensionRewards")
                            .getMethod("settleRaidAttunement", String.class, String.class, Collection.class);
                } catch (ReflectiveOperationException ignored) {
                    settleAttunement = null;
                }
                try {
                    threatEnabled = encounters.getMethod("threatEnabled");
                    settleRaid = Class.forName("com.ascensionlib.AscensionRewards")
                            .getMethod("settleRaid", String.class, String.class, String.class, Map.class);
                } catch (ReflectiveOperationException ignored) {
                    threatEnabled = null;
                    settleRaid = null;
                }
            } catch (ReflectiveOperationException | LinkageError ex) {
                declare = null;
                arm = null;
                disarm = null;
                end = null;
                RaidLog.error("AscensionLib is installed but " + CLASS + " does not match what CobbleRaids expects, "
                        + "so raid bosses fight natively", ex);
            }
        }
        return declare != null;
    }
}
