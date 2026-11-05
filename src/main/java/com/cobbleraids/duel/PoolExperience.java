package com.cobbleraids.duel;

import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.reward.ContributionMath;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Pays {@link PoolExperienceRules}' experience to online victors' parties when a duel raid's or a World Boss's pool falls. */
public final class PoolExperience {

    private static final SidemodExperienceSource SOURCE = new SidemodExperienceSource("cobbleraids");

    private PoolExperience() {}

    /** Offline victors miss it, as they miss a party raid's experience. */
    public static void grant(MinecraftServer server, Pokemon boss, double multiplier, Map<UUID, Float> damage, Set<UUID> victors) {
        if (boss == null) return;
        grant(server, boss.getSpecies().getBaseExperienceYield(), boss.getLevel(), multiplier, damage, victors);
    }

    /** As above from a species' yield and a level (the World Boss, whose showpiece is not the Pokemon fought). */
    public static void grant(MinecraftServer server, int baseYield, int level, double multiplier, Map<UUID, Float> damage, Set<UUID> victors) {
        Map<UUID, Double> percent = ContributionMath.percentages(damage, victors);
        for (UUID id : victors) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            int amount = PoolExperienceRules.perPokemon(baseYield, level, multiplier, percent.getOrDefault(id, 0.0) / 100.0, victors.size());
            if (amount <= 0) continue;
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                if (pokemon == null) continue;
                RaidFaultBarrier.guard("pool-experience", () -> pokemon.addExperienceWithPlayer(player, SOURCE, amount));
            }
            RaidLog.info("Pool experience: {} +{} exp per Pokemon", player.getGameProfile().getName(), amount);
        }
    }
}
