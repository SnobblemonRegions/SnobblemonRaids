package com.cobbleraids.duel;

import com.cobbleraids.RaidLog;
import com.cobbleraids.api.encounter.EncounterListener;
import com.cobbleraids.api.encounter.EncounterOutcome;
import com.cobbleraids.api.encounter.EncounterPolicy;
import com.cobbleraids.api.encounter.EncounterRequest;
import com.cobbleraids.api.encounter.EncounterResult;
import com.cobbleraids.api.encounter.EncounterRules;
import com.cobbleraids.api.encounter.StartResult;
import com.cobbleraids.catching.RaidCaptureSessionService;
import com.cobbleraids.catching.RaidPlayerRecords;
import com.cobbleraids.config.RaidDefinition;
import com.cobbleraids.config.RaidDefinitionRegistry;
import com.cobbleraids.encounter.EncounterService;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.lifecycle.RaidOutcome;
import com.cobbleraids.lifecycle.RaidRewardEligibility;
import com.cobbleraids.lifecycle.RaidRewardService;
import com.cobbleraids.raid.AscensionLibArming;
import com.cobbleraids.reward.ContributionMath;
import com.cobbleraids.spawn.RaidBossEntityMarker;
import com.cobbleraids.worldboss.WorldBossPool;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Balance phase 3: every ordinary raid is fought as duels. Each of up to {@link RaidDuelRules#MAX_PLAYERS} players fights
 * their own copy of the boss (an owned encounter, like a World Boss duel) and every hit comes off one shared
 * {@link WorldBossPool} sized by {@link RaidDuelRules#poolFor}. Knocking out a duel's boss chains the player into the next
 * one; a player whose party is wiped is out. The raid is won when the pool is empty and lost when the window closes or
 * nobody is left fighting.
 *
 * <p>The lobby's wild boss stays as the raid's face: it is marked owned so nobody can recruit it again, it is the Pokemon a
 * victor may catch, and its UUID keys the raid's AscensionLib encounter. A win pays exactly what a party raid pays (the
 * reward screen, history, the capture offer) plus AscensionLib's share-based pay. Server thread only.
 */
public final class RaidDuelService {

    public static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("cobbleraids", "duel_raid");
    private static final int SECOND = 20;
    /** A short breather between a won duel and the next, so the reward of the last one can be seen. */
    private static final int CHAIN_DELAY_TICKS = 3 * SECOND;
    /** Each duel's boss stands this far in front of its player. */
    private static final double DUEL_DISTANCE = 7.0;

    /** One raid in progress. */
    private static final class DuelRaid {
        final UUID raidId = UUID.randomUUID();
        final RaidDefinition definition;
        final PokemonEntity boss;
        final int bossLevel;
        final WorldBossPool pool;
        final Set<UUID> players;
        final Set<UUID> out = ConcurrentHashMap.newKeySet();
        final Map<UUID, UUID> duelOfPlayer = new ConcurrentHashMap<>();
        final Map<UUID, Long> chainAt = new ConcurrentHashMap<>();
        final long startedTick;
        boolean finished;

        DuelRaid(RaidDefinition definition, PokemonEntity boss, List<ServerPlayer> players, long startedTick) {
            this.definition = definition;
            this.boss = boss;
            this.bossLevel = boss.getPokemon().getLevel();
            this.players = new LinkedHashSet<>(players.stream().map(ServerPlayer::getUUID).toList());
            this.pool = new WorldBossPool(RaidDuelRules.poolFor(definition.rarityTier(), players.size()));
            this.startedTick = startedTick;
        }
    }

    private static final Map<UUID, DuelRaid> RAIDS = new ConcurrentHashMap<>();
    /** Duel encounter id to its raid. */
    private static final Map<UUID, DuelRaid> BY_DUEL = new ConcurrentHashMap<>();
    /** Duel encounter id to its player. */
    private static final Map<UUID, UUID> DUEL_PLAYER = new ConcurrentHashMap<>();

    private RaidDuelService() {}

    /** Starts a duel raid at the lobby's boss. Returns null on success, else why it could not start (nothing is left behind). */
    public static String start(MinecraftServer server, List<ServerPlayer> players, RaidDefinition definition, PokemonEntity boss) {
        if (players.isEmpty()) return "no players";
        if (players.size() > RaidDuelRules.MAX_PLAYERS) return "at most " + RaidDuelRules.MAX_PLAYERS + " players";
        long now = server.overworld().getGameTime();
        DuelRaid raid = new DuelRaid(definition, boss, players, now);
        // Never recruitable again, and never wandering off while its copies are fought.
        RaidBossEntityMarker.markOwned(boss, OWNER);
        boss.setNoAi(true);
        AscensionLibArming.declare(raid.players, boss.getPokemon().getUuid(),
                boss.getPokemon().getSpecies().getResourceIdentifier().toString(), raid.bossLevel,
                AscensionLibArming.threatFor(definition.rarityTier()));
        RAIDS.put(raid.raidId, raid);
        int started = 0;
        for (ServerPlayer player : players) {
            String why = startDuel(raid, player);
            if (why == null) started++;
            else {
                raid.out.add(player.getUUID());
                say(player, "Your duel could not start: " + why, ChatFormatting.RED);
            }
        }
        if (started == 0) {
            RAIDS.remove(raid.raidId);
            AscensionLibArming.endEncounter(boss.getPokemon().getUuid());
            return "no duel could start";
        }
        RaidLog.info("Duel raid {} ({}): {} player(s), pool {}", raid.raidId, definition.id(), players.size(), raid.pool.max());
        return null;
    }

    private static String startDuel(DuelRaid raid, ServerPlayer player) {
        if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) return "finish your current battle first";
        if (!hasFightingPokemon(player)) return "none of your Pokemon can fight";
        UUID encounterId = UUID.randomUUID();
        Vec3 look = player.getLookAngle();
        Vec3 position = new Vec3(player.getX() + look.x * DUEL_DISTANCE, player.getY(), player.getZ() + look.z * DUEL_DISTANCE);
        EncounterRequest request = new EncounterRequest(OWNER, encounterId, List.of(player), raid.definition.id(),
                player.serverLevel(), position, raid.bossLevel, OptionalLong.of(RaidDuelRules.duelHealth(raid.definition.rarityTier())),
                EncounterPolicy.none().withCarryover(true, true), EncounterRules.none());
        // Owned encounters are not armed by the factory, so the duel arms its own player for the raid's declared boss.
        List<UUID> self = List.of(player.getUUID());
        AscensionLibArming.arm(self, raid.boss.getPokemon().getUuid());
        StartResult result;
        try {
            result = EncounterService.start(request, LISTENER);
        } catch (RuntimeException ex) {
            RaidLog.error("A duel of raid {} for {} could not start", raid.raidId, player.getGameProfile().getName(), ex);
            return "tell an administrator";
        } finally {
            AscensionLibArming.disarmAll(self);
        }
        if (result instanceof StartResult.Refused refused) return refused.reason();
        BY_DUEL.put(encounterId, raid);
        DUEL_PLAYER.put(encounterId, player.getUUID());
        raid.duelOfPlayer.put(player.getUUID(), encounterId);
        return null;
    }

    private static final EncounterListener LISTENER = new EncounterListener() {
        @Override
        public void onDamage(UUID encounterId, UUID playerId, float applied) {
            DuelRaid raid = BY_DUEL.get(encounterId);
            if (raid == null || raid.finished) return;
            raid.pool.strike(playerId, applied);
            if (raid.pool.current() <= 0f && raid.pool.claimDefeat()) {
                MinecraftServer server = raid.boss.getServer();
                if (server != null) server.execute(() -> finish(server, raid, true));
            }
        }

        @Override
        public void onEnded(EncounterResult result) {
            DuelRaid raid = BY_DUEL.remove(result.encounterId());
            UUID playerId = DUEL_PLAYER.remove(result.encounterId());
            if (raid == null || playerId == null) return;
            raid.duelOfPlayer.remove(playerId, result.encounterId());
            if (raid.finished) return;
            MinecraftServer server = raid.boss.getServer();
            if (server == null) return;
            if (result.outcome() == EncounterOutcome.VICTORY) {
                // Their copy fell but the pool did not: the next copy, after a breather.
                raid.chainAt.put(playerId, server.overworld().getGameTime() + CHAIN_DELAY_TICKS);
            } else {
                raid.out.add(playerId);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) say(player, "You are out of this raid. Your damage still counts.", ChatFormatting.YELLOW);
            }
        }
    };

    /** Chains players into their next duel, and ends raids whose window closed or that nobody is fighting any more. */
    public static void tick(MinecraftServer server) {
        if (RAIDS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (DuelRaid raid : List.copyOf(RAIDS.values())) {
            if (raid.finished) continue;
            if (raid.boss.isRemoved() || now - raid.startedTick > RaidDuelRules.WINDOW_SECONDS * (long) SECOND) {
                finish(server, raid, false);
                continue;
            }
            for (Map.Entry<UUID, Long> entry : List.copyOf(raid.chainAt.entrySet())) {
                if (entry.getValue() > now) continue;
                raid.chainAt.remove(entry.getKey());
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                String why = player == null ? "offline" : startDuel(raid, player);
                if (why != null) {
                    raid.out.add(entry.getKey());
                    if (player != null) say(player, "Your next duel could not start: " + why, ChatFormatting.YELLOW);
                }
            }
            if (raid.duelOfPlayer.isEmpty() && raid.chainAt.isEmpty()) finish(server, raid, false);
        }
    }

    private static void finish(MinecraftServer server, DuelRaid raid, boolean won) {
        if (raid.finished) return;
        raid.finished = true;
        RAIDS.remove(raid.raidId);
        for (UUID duel : List.copyOf(raid.duelOfPlayer.values())) EncounterService.abort(duel);
        Map<UUID, Float> damage = new LinkedHashMap<>(raid.pool.contributions());
        for (UUID player : raid.players) damage.putIfAbsent(player, 0f);
        int elapsed = (int) Math.min(Integer.MAX_VALUE, server.overworld().getGameTime() - raid.startedTick);
        RaidLog.info("Duel raid {} ({}) {} after {}s: {} of {} pool dealt", raid.raidId, raid.definition.id(),
                won ? "won" : "lost", elapsed / SECOND, raid.pool.max() - raid.pool.current(), raid.pool.max());
        if (won) RaidFaultBarrier.guard("duel-raid:victory", () -> payVictory(server, raid, damage, elapsed));
        else for (UUID id : raid.players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) say(player, "The raid boss got away.", ChatFormatting.RED);
        }
        AscensionLibArming.endEncounter(raid.boss.getPokemon().getUuid());
        if (!raid.boss.isRemoved()) raid.boss.discard();
    }

    /** What a won party raid pays, from the shared pool's contributions. */
    private static void payVictory(MinecraftServer server, DuelRaid raid, Map<UUID, Float> damage, int elapsed) {
        RaidDefinition definition = RaidDefinitionRegistry.get(raid.definition.id());
        if (definition == null) definition = raid.definition;
        Set<UUID> victors = Set.copyOf(raid.players);
        Pokemon bossPokemon = raid.boss.getPokemon();
        Map<UUID, Double> percent = ContributionMath.percentages(damage, victors);
        final RaidDefinition def = definition;
        RaidFaultBarrier.guard("duel-raid:history", () -> RaidPlayerRecords.recordWins(server, def.rarityTier(), def.id(), percent));
        RaidFaultBarrier.guard("duel-raid:capture", () -> RaidCaptureSessionService.offer(server, def, bossPokemon, raid.raidId, victors));
        RaidFaultBarrier.guard("duel-raid:rewards", () -> RaidRewardService.grant(new RaidRewardEligibility(raid.raidId, def.id(),
                RaidOutcome.VICTORY, damage, victors, elapsed, "", ""), server));
        RaidFaultBarrier.guard("duel-raid:ascensionlib", () ->
                AscensionLibArming.settleVictory(bossPokemon.getUuid(), def.rarityTier(), damage, victors));
        for (UUID id : victors) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) say(player, "The raid boss is defeated! You dealt "
                    + Math.round(percent.getOrDefault(id, 0.0) * 10.0) / 10.0 + "% of the damage.", ChatFormatting.GREEN);
        }
    }

    /** Ends every raid without paying (server stopping). */
    public static void abortAll(MinecraftServer server) {
        for (DuelRaid raid : List.copyOf(RAIDS.values())) finish(server, raid, false);
    }

    /** Whether this player is in a duel raid right now (for commands and the lobby). */
    public static boolean isInRaid(UUID player) {
        for (DuelRaid raid : RAIDS.values()) if (raid.players.contains(player) && !raid.out.contains(player)) return true;
        return false;
    }

    private static boolean hasFightingPokemon(ServerPlayer player) {
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) if (!pokemon.isFainted()) return true;
        return false;
    }

    private static void say(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }
}
