package com.cobbleraids.telemetry;

import com.cobbleraids.api.encounter.LeaveReason;
import com.cobbleraids.config.RaidDefinition;
import com.cobbleraids.config.RaidDefinitionRegistry;
import com.cobbleraids.raid.AscensionLibArming;
import com.cobbleraids.raid.RaidSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * The raid's gameplay telemetry, written into AscensionLib's log through {@link AscensionTelemetryBridge}: {@code raid_end} for every
 * ending (not only victories) and {@code raid_left} for every player who leaves before the end. Reads the raid and builds one small
 * map; costs nothing more on the server thread. Callers wrap it in a fault barrier, so a failure here never touches a raid.
 */
public final class RaidTelemetry {
    private RaidTelemetry() {}

    /** An ordinary or owned raid ended. Call once per raid, from its finalization, before the boss is cleaned up. */
    public static void raidEnded(RaidSession raid, RaidEndReason reason) {
        AscensionTelemetryBridge.event(RaidEnd.TYPE, endOf(raid, reason).fields());
    }

    /** A player left an ordinary or owned raid that is still going. */
    public static void playerLeft(RaidSession raid, UUID player, LeaveReason why) {
        var definition = RaidDefinitionRegistry.get(raid.getDefinitionId());
        var left = new RaidLeft(encounterId(bossId(raid)), raid.isOwned() ? "owned" : "party", String.valueOf(raid.getDefinitionId()),
                tierOf(definition), why.name().toLowerCase(Locale.ROOT), raid.getElapsedCombatTicks() / 20,
                RaidEnd.fraction(raid.getCurrentHealth(), raid.getMaxHealth()), raid.getActiveParticipants().size(), player);
        AscensionTelemetryBridge.event(RaidLeft.TYPE, left.fields());
    }

    /** A duel raid ended (its own bookkeeping: no {@link RaidSession}). */
    public static void duelEnded(UUID bossPokemonId, ResourceLocation definitionId, String species, int level, RaidEndReason reason,
                                 Collection<UUID> players, int stillIn, int seconds, int windowSeconds, float poolLeft, float poolMax,
                                 Map<UUID, Float> damage) {
        var definition = RaidDefinitionRegistry.get(definitionId);
        var end = new RaidEnd(encounterId(bossPokemonId), "duel", null, String.valueOf(definitionId), tierOf(definition),
                threatOf(definition), species, level, reason, players.size(), stillIn, seconds, windowSeconds,
                RaidEnd.fraction(poolLeft, poolMax), dealers(damage), new ArrayList<>(players));
        AscensionTelemetryBridge.event(RaidEnd.TYPE, end.fields());
    }

    static RaidEnd endOf(RaidSession raid, RaidEndReason reason) {
        var definition = RaidDefinitionRegistry.get(raid.getDefinitionId());
        String species = null;
        int level = 0;
        var boss = raid.getBossEntity();
        if (boss != null) {
            species = boss.getPokemon().getSpecies().getResourceIdentifier().toString();
            level = boss.getPokemon().getLevel();
        }
        var owner = raid.isOwned() ? raid.getOwnership().owner().toString() : null;
        // A duel copy's own session keeps the raid's general limit as a fallback; the duel raid enforces its shorter per-duel limit.
        int timeLimit = raid.isTimed() ? raid.getTimeLimitTicks() / 20 : 0;
        if (raid.isOwned()) timeLimit = com.cobbleraids.duel.RaidDuelService.duelLimitSeconds(raid.getOwnership().encounterId()).orElse(timeLimit);
        return new RaidEnd(encounterId(bossId(raid)), raid.isOwned() ? "owned" : "party", owner, String.valueOf(raid.getDefinitionId()),
                tierOf(definition), raid.isOwned() ? 0 : threatOf(definition), species, level, reason, raid.getParticipants().size(),
                raid.getActiveParticipants().size(), raid.getElapsedCombatTicks() / 20, timeLimit,
                RaidEnd.fraction(raid.getCurrentHealth(), raid.getMaxHealth()), dealers(raid.getContributionSnapshot()),
                new ArrayList<>(raid.getParticipants()));
    }

    /** The boss Pokemon's UUID (its battle actor's, which is the same, once the entity is gone). */
    private static UUID bossId(RaidSession raid) {
        var boss = raid.getBossEntity();
        return boss != null ? boss.getPokemon().getUuid() : raid.getBossActorId();
    }

    /** The id AscensionLib's encounter and settlement events use for this raid, so the events can be joined. */
    static String encounterId(UUID bossPokemonId) { return "raid:" + bossPokemonId; }

    private static String tierOf(RaidDefinition definition) {
        return definition == null ? "unknown" : definition.rarityTier().serializedName();
    }

    /** The Threat band the tier fights at (balance phase 3), whether or not the library has Threat on. */
    private static int threatOf(RaidDefinition definition) {
        return definition == null ? 0 : AscensionLibArming.threatFor(definition.rarityTier());
    }

    private static int dealers(Map<UUID, Float> damage) {
        if (damage == null) return 0;
        int n = 0;
        for (Float d : damage.values()) if (d != null && d > 0f) n++;
        return n;
    }
}
