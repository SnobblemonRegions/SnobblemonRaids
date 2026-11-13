package com.cobbleraids.duel;

import com.cobbleraids.RaidLog;
import com.cobbleraids.api.encounter.EncounterListener;
import com.cobbleraids.api.encounter.EncounterOutcome;
import com.cobbleraids.api.encounter.EncounterPolicy;
import com.cobbleraids.api.encounter.EncounterRequest;
import com.cobbleraids.api.encounter.EncounterResult;
import com.cobbleraids.api.encounter.EncounterRules;
import com.cobbleraids.api.encounter.StartResult;
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

    /** Owns each player's hidden copy. */
    public static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("cobbleraids", "duel_raid");
    /** Owns the raid boss the players walked up to, which stays visible and is never recruitable again. */
    public static final ResourceLocation FACE_OWNER = ResourceLocation.fromNamespaceAndPath("cobbleraids", "duel_raid_face");
    private static final int SECOND = 20;
    /** A short breather between a won duel and the next, so the reward of the last one can be seen. */
    private static final int CHAIN_DELAY_TICKS = 3 * SECOND;
    /** Closer than this to the raid boss and a player's copy stands on a ground-checked spot beside them instead. */
    private static final double MIN_COPY_DISTANCE = 4.0;

    /** One raid in progress. */
    private static final class DuelRaid {
        final UUID raidId = UUID.randomUUID();
        final RaidDefinition definition;
        final PokemonEntity boss;
        final int bossLevel;
        final WorldBossPool pool;
        final Set<UUID> players;
        final Set<UUID> out = ConcurrentHashMap.newKeySet();
        /** Players who had at least one duel start, and players who gave the raid up; see {@link RaidDuelRules#victors}. */
        final Set<UUID> fought = ConcurrentHashMap.newKeySet();
        final Set<UUID> forfeited = ConcurrentHashMap.newKeySet();
        final Map<UUID, UUID> duelOfPlayer = new ConcurrentHashMap<>();
        final Map<UUID, Long> chainAt = new ConcurrentHashMap<>();
        /** Game tick each live duel began, and the ticks its player spent offline (reconnect grace), which do not count against it. */
        final Map<UUID, Long> duelStarted = new ConcurrentHashMap<>();
        final Map<UUID, Long> pausedTicks = new ConcurrentHashMap<>();
        /** Players whose duel ran out of time: they chain to a fresh copy instead of being out. */
        final Set<UUID> timedOut = ConcurrentHashMap.newKeySet();
        long lastTick;
        final long startedTick;
        final String renownTitle;
        final String renownBoon;
        /** The stat a renowned boss's epithet focuses, or null; every copy is given the same focus. */
        final com.cobbleraids.renown.RenownBoon statFocus;
        /** Both clocks, read once at the start so a config reload cannot move a raid's deadline under its players. */
        final long windowTicks;
        final long duelLimitTicks;
        boolean lastMinuteWarned;
        boolean finished;

        DuelRaid(RaidDefinition definition, PokemonEntity boss, List<ServerPlayer> players, long startedTick) {
            this.definition = definition;
            this.boss = boss;
            this.bossLevel = boss.getPokemon().getLevel();
            this.players = new LinkedHashSet<>(players.stream().map(ServerPlayer::getUUID).toList());
            this.startedTick = startedTick;
            this.lastTick = startedTick;
            var clocks = com.cobbleraids.config.CobbleRaidsConfigManager.get().duelRaid();
            this.windowTicks = clocks.raidTimeLimitSeconds() * (long) SECOND;
            this.duelLimitTicks = clocks.duelTimeLimitSeconds() * (long) SECOND;
            var renown = com.cobbleraids.renown.RaidRenownMarker.read(boss);
            this.statFocus = renown.map(com.cobbleraids.renown.RaidRenown::boon)
                    .filter(b -> b.kind() == com.cobbleraids.renown.RenownBoon.Kind.STAT_FOCUS).orElse(null);
            // A renowned boss has a larger shared pool, and a larger one again with the hp_pool boon, as its party raid had.
            var renownConfig = com.cobbleraids.config.CobbleRaidsConfigManager.get().renown();
            long basePool = RaidDuelRules.poolFor(definition.rarityTier(), players.size());
            this.pool = new WorldBossPool(renown.isEmpty() ? basePool : com.cobbleraids.raid.RaidScalingPolicy.renownPool(basePool,
                    renownConfig.baseHealthBonus(), renown.get().boon().kind() == com.cobbleraids.renown.RenownBoon.Kind.HP_POOL,
                    renownConfig.healthBonus()));
            this.renownTitle = renown.map(com.cobbleraids.renown.RaidRenown::title).orElse("");
            this.renownBoon = renown.map(r -> r.boon().encode()).orElse("");
        }
    }

    private static final Map<UUID, DuelRaid> RAIDS = new ConcurrentHashMap<>();
    /** Entity UUIDs of the raid bosses duel raids stand on, so the despawn timers leave them alone (see {@link #isFace}). */
    private static final Set<UUID> FACES = ConcurrentHashMap.newKeySet();
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
        // One raid charge each, spent before anything exists and handed back if the raid does not begin. A party raid spent it in
        // RaidFactory, which an owned duel never reaches for charges.
        com.cobbleraids.charges.RaidChargeStore.Reservation charges;
        try {
            charges = com.cobbleraids.charges.RaidChargeStore.get(server)
                    .reserve(server, players.stream().map(ServerPlayer::getUUID).toList());
        } catch (RuntimeException ex) {
            return ex.getMessage() == null ? "raid charges could not be spent" : ex.getMessage();
        }
        try (charges) {
            String refused = start(server, players, definition, boss, now, charges);
            if (refused == null) charges.commit();
            return refused;
        }
    }

    private static String start(MinecraftServer server, List<ServerPlayer> players, RaidDefinition definition, PokemonEntity boss,
                                long now, com.cobbleraids.charges.RaidChargeStore.Reservation charges) {
        DuelRaid raid = new DuelRaid(definition, boss, players, now);
        // Never recruitable again, and never wandering off while its copies are fought.
        boolean hadNoAi = boss.isNoAi();
        RaidBossEntityMarker.markOwned(boss, FACE_OWNER);
        boss.setNoAi(true);
        FACES.add(boss.getUUID());
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
                // They take no part in this raid, so it does not cost them a charge.
                RaidFaultBarrier.guard("duel-raid:charge-release", () -> charges.release(player.getUUID()));
            }
        }
        if (started == 0) {
            RAIDS.remove(raid.raidId);
            FACES.remove(boss.getUUID());
            AscensionLibArming.endEncounter(boss.getPokemon().getUuid());
            // The lobby tells its players the boss remains available, and an owned boss refuses every lobby.
            RaidBossEntityMarker.clearOwned(boss);
            boss.setNoAi(hadNoAi);
            return "no duel could start";
        }
        RaidLog.info("Duel raid {} ({}): {} player(s), pool {}", raid.raidId, definition.id(), players.size(), raid.pool.max());
        for (ServerPlayer player : players) {
            if (!raid.out.contains(player.getUUID())) say(player, "The raid has begun. You have "
                    + com.cobbleraids.presentation.CommandFormat.duration(raid.windowTicks / SECOND) + " to defeat the boss.", ChatFormatting.GOLD);
        }
        return null;
    }

    private static String startDuel(DuelRaid raid, ServerPlayer player) {
        if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) return "finish your current battle first";
        if (!hasFightingPokemon(player)) return "none of your Pokemon can fight";
        UUID encounterId = UUID.randomUUID();
        Vec3 position = copyPosition(raid, player);
        if (position == null) return "there is no room for the duel where you are standing";
        EncounterRequest request = new EncounterRequest(OWNER, encounterId, List.of(player), raid.definition.id(),
                player.serverLevel(), position, raid.bossLevel, OptionalLong.of(RaidDuelRules.duelHealth(raid.definition.rarityTier())),
                EncounterPolicy.none().withCarryover(true, true), EncounterRules.none());
        // Owned encounters are not armed by the factory, so the duel arms its own player for the raid's declared boss.
        List<UUID> self = List.of(player.getUUID());
        AscensionLibArming.arm(self, raid.boss.getPokemon().getUuid());
        StartResult result;
        try {
            result = EncounterService.start(request, LISTENER, copy -> applyStatFocus(raid, copy));
        } catch (RuntimeException ex) {
            RaidLog.error("A duel of raid {} for {} could not start", raid.raidId, player.getGameProfile().getName(), ex);
            return "tell an administrator";
        } finally {
            AscensionLibArming.disarmAll(self);
        }
        if (result instanceof StartResult.Refused refused) return refused.reason();
        // Hidden, so the player sees their Pokemon fight the raid boss they walked up to (re-applied by the attribute-reset mixin).
        PokemonEntity copy = EncounterService.bossOf(encounterId);
        if (copy != null) com.cobbleraids.worldboss.WorldBossService.hideDuelBoss(copy);
        BY_DUEL.put(encounterId, raid);
        DUEL_PLAYER.put(encounterId, player.getUUID());
        raid.duelOfPlayer.put(player.getUUID(), encounterId);
        raid.fought.add(player.getUUID());
        raid.duelStarted.put(player.getUUID(), raid.boss.level().getGameTime());
        raid.pausedTicks.remove(player.getUUID());
        return null;
    }

    /**
     * Gives a duel's copy the stat focus of the renowned boss it stands for. The copy is a fresh spawn of the definition, so without
     * this the boss a player actually fights has none of the boon its title promises. Maximum IV, and the same EVs the raid boss was
     * given at spawn, read from it rather than recomputed so the copy can never end up stronger than the boss it copies.
     */
    private static void applyStatFocus(DuelRaid raid, PokemonEntity copy) {
        if (raid.statFocus == null) return;
        var stat = com.cobbleraids.pokemon.PokemonStatNames.statFor(raid.statFocus.stat());
        Pokemon pokemon = copy.getPokemon();
        pokemon.setIV(stat, com.cobblemon.mod.common.pokemon.IVs.MAX_VALUE);
        pokemon.setEV(stat, raid.boss.getPokemon().getEvs().getOrDefault(stat));
    }

    /**
     * Where a player's hidden copy stands: on the raid boss itself, so the illusion holds and the battle camera faces the real boss.
     * A player standing within a few blocks of it gets the World Boss's ground-checked spot instead, because a copy on top of its
     * player makes Cobblemon divide by a zero distance (see {@code WorldBossService.duelPosition}).
     */
    private static Vec3 copyPosition(DuelRaid raid, ServerPlayer player) {
        Vec3 face = raid.boss.position();
        if (player.position().distanceTo(face) >= MIN_COPY_DISTANCE) return face;
        return com.cobbleraids.worldboss.WorldBossService.duelPosition(player);
    }

    private static final EncounterListener LISTENER = new EncounterListener() {
        @Override
        public void onDamage(UUID encounterId, UUID playerId, float applied) {
            DuelRaid raid = BY_DUEL.get(encounterId);
            if (raid == null || raid.finished) return;
            raid.pool.strike(playerId, applied);
            if (raid.pool.current() <= 0f && raid.pool.claimDefeat()) {
                MinecraftServer server = raid.boss.getServer();
                if (server != null) server.execute(() -> finish(server, raid, com.cobbleraids.telemetry.RaidEndReason.VICTORY));
            }
        }

        @Override
        public void onParticipantLeft(UUID encounterId, UUID playerId, com.cobbleraids.api.encounter.LeaveReason reason) {
            // Withdrew, fled, or never came back from a disconnect: all three give the raid up, as they did a party raid.
            DuelRaid raid = BY_DUEL.get(encounterId);
            if (raid != null) raid.forfeited.add(playerId);
        }

        @Override
        public void onEnded(EncounterResult result) {
            DuelRaid raid = BY_DUEL.remove(result.encounterId());
            UUID playerId = DUEL_PLAYER.remove(result.encounterId());
            if (raid == null || playerId == null) return;
            raid.duelOfPlayer.remove(playerId, result.encounterId());
            raid.duelStarted.remove(playerId);
            raid.pausedTicks.remove(playerId);
            boolean timedOut = raid.timedOut.remove(playerId);
            if (raid.finished) return;
            MinecraftServer server = raid.boss.getServer();
            if (server == null) return;
            if (result.outcome() == EncounterOutcome.VICTORY || timedOut) {
                // Their copy fell but the pool did not: the next copy, after a breather.
                raid.chainAt.put(playerId, server.overworld().getGameTime() + CHAIN_DELAY_TICKS);
            } else {
                raid.out.add(playerId);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                // A player who gave the raid up has already been told so, and their damage earns them nothing.
                if (player != null && !raid.forfeited.contains(playerId)) {
                    say(player, "You are out of this raid. Your damage still counts.", ChatFormatting.YELLOW);
                }
            }
        }
    };

    /** Chains players into their next duel, and ends raids whose window closed or that nobody is fighting any more. */
    public static void tick(MinecraftServer server) {
        if (RAIDS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (DuelRaid raid : List.copyOf(RAIDS.values())) {
            if (raid.finished) continue;
            long left = raid.windowTicks - (now - raid.startedTick);
            if (bossDestroyed(raid.boss) || left < 0) {
                finish(server, raid, bossDestroyed(raid.boss) ? com.cobbleraids.telemetry.RaidEndReason.ABORTED : com.cobbleraids.telemetry.RaidEndReason.TIMEOUT);
                continue;
            }
            if (!raid.lastMinuteWarned && left <= 60L * SECOND && raid.windowTicks > 60L * SECOND) {
                raid.lastMinuteWarned = true;
                for (UUID id : raid.players) {
                    ServerPlayer player = raid.out.contains(id) ? null : server.getPlayerList().getPlayer(id);
                    if (player != null) say(player, "One minute left to defeat the raid boss!", ChatFormatting.RED);
                }
            }
            long delta = Math.max(0L, now - raid.lastTick);
            raid.lastTick = now;
            for (Map.Entry<UUID, UUID> duel : List.copyOf(raid.duelOfPlayer.entrySet())) enforceDuel(server, raid, duel.getKey(), duel.getValue(), now, delta);
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
            if (raid.duelOfPlayer.isEmpty() && raid.chainAt.isEmpty()) finish(server, raid, com.cobbleraids.telemetry.RaidEndReason.WIPE);
        }
    }

    /**
     * One live duel's rules: a player who walked off (or left the dimension) is out; a duel past its own time limit is ended and the
     * player chains to a fresh copy. A player who is offline is under reconnect grace, so their duel's clock stands still.
     */
    private static void enforceDuel(MinecraftServer server, DuelRaid raid, UUID playerId, UUID duel, long now, long delta) {
        // A duel that is already decided cannot be aborted, so enforcing here would repeat the message every tick until it is finalized.
        if (!EncounterService.isFighting(duel)) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            raid.pausedTicks.merge(playerId, delta, Long::sum);
            return;
        }
        if (player.level() != raid.boss.level() || player.distanceToSqr(raid.boss) > RaidDuelRules.LEASH_BLOCKS * RaidDuelRules.LEASH_BLOCKS) {
            say(player, "You walked away from the raid and forfeited its rewards.", ChatFormatting.YELLOW);
            raid.forfeited.add(playerId);
            EncounterService.abort(duel);
            return;
        }
        long used = now - raid.duelStarted.getOrDefault(playerId, now) - raid.pausedTicks.getOrDefault(playerId, 0L);
        if (used > raid.duelLimitTicks) {
            raid.timedOut.add(playerId);
            say(player, "Your duel ran out of time. Your damage still counts; a fresh duel is coming.", ChatFormatting.YELLOW);
            EncounterService.abort(duel);
        }
    }

    /**
     * Whether the raid boss is really gone. An entity also reports itself removed while its chunk is merely unloaded, which happens
     * when every player of the raid is offline under reconnect grace; ending the raid on that would take their held duels with it.
     */
    private static boolean bossDestroyed(PokemonEntity boss) {
        var reason = boss.getRemovalReason();
        return reason != null && reason.shouldDestroy();
    }

    /** Whether this entity is the raid boss of a duel raid in progress (it is never battling itself: its players fight hidden copies). */
    public static boolean isFace(PokemonEntity boss) {
        return boss != null && FACES.contains(boss.getUUID());
    }

    /** As {@link #isFace(PokemonEntity)}, for a caller that has only the entity's id. */
    public static boolean isFace(UUID bossId) {
        return bossId != null && FACES.contains(bossId);
    }

    private static void finish(MinecraftServer server, DuelRaid raid, com.cobbleraids.telemetry.RaidEndReason reason) {
        if (raid.finished) return;
        raid.finished = true;
        boolean won = reason == com.cobbleraids.telemetry.RaidEndReason.VICTORY;
        RAIDS.remove(raid.raidId);
        FACES.remove(raid.boss.getUUID());
        for (UUID duel : List.copyOf(raid.duelOfPlayer.values())) EncounterService.abort(duel);
        Map<UUID, Float> damage = new LinkedHashMap<>(raid.pool.contributions());
        for (UUID player : raid.players) damage.putIfAbsent(player, 0f);
        int elapsed = (int) Math.min(Integer.MAX_VALUE, server.overworld().getGameTime() - raid.startedTick);
        RaidLog.info("Duel raid {} ({}) {} after {}s: {} of {} pool dealt", raid.raidId, raid.definition.id(),
                won ? "won" : "lost", elapsed / SECOND, raid.pool.max() - raid.pool.current(), raid.pool.max());
        Set<UUID> victors = RaidDuelRules.victors(raid.players, raid.fought, raid.forfeited);
        RaidFaultBarrier.guard("duel-raid:telemetry", () -> com.cobbleraids.telemetry.RaidTelemetry.duelEnded(raid.boss.getPokemon().getUuid(),
                raid.definition.id(), raid.boss.getPokemon().getSpecies().getResourceIdentifier().toString(), raid.bossLevel, reason,
                raid.players, raid.players.size() - raid.out.size(), elapsed / SECOND, (int) (raid.windowTicks / SECOND),
                raid.pool.current(), raid.pool.max(), damage));
        // A win is recorded for the players it pays; a loss for everybody who joined.
        RaidFaultBarrier.guard("duel-raid:stats", () -> com.cobbleraids.stats.RaidStats.onDuelRaidEnd(server, raid.definition.id(), won,
                won ? victors : raid.players, damage, elapsed / SECOND, !raid.renownTitle.isEmpty()));
        if (won) RaidFaultBarrier.guard("duel-raid:victory", () -> payVictory(server, raid, victors, damage, elapsed));
        else for (UUID id : raid.players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) say(player, "The raid boss got away.", ChatFormatting.RED);
        }
        AscensionLibArming.endEncounter(raid.boss.getPokemon().getUuid());
        // Explicitly, as every party raid's cleanup does: the discard below releases the slot only while the boss is loaded.
        com.cobbleraids.spawn.RaidSpawnScheduler.forget(raid.boss.getUUID());
        if (!raid.boss.isRemoved()) raid.boss.discard();
    }

    /** What a won party raid pays, from the shared pool's contributions. */
    private static void payVictory(MinecraftServer server, DuelRaid raid, Set<UUID> victors, Map<UUID, Float> damage, int elapsed) {
        for (UUID id : raid.players) {
            if (victors.contains(id)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) say(player, "The raid boss is defeated, but you had left the raid.", ChatFormatting.YELLOW);
        }
        if (victors.isEmpty()) return;
        RaidDefinition definition = RaidDefinitionRegistry.get(raid.definition.id());
        if (definition == null) definition = raid.definition;
        Pokemon bossPokemon = raid.boss.getPokemon();
        Map<UUID, Double> percent = ContributionMath.percentages(damage, victors);
        final RaidDefinition def = definition;
        // Experience first, as a party raid pays it before the reward screen, so level-ups reach chat ahead of it.
        RaidFaultBarrier.guard("duel-raid:experience", () -> PoolExperience.grant(server, bossPokemon,
                PoolExperienceRules.tierMultiplier(def.rarityTier()), damage, victors));
        // Wins, titles, trophy room, Hall of Legends for a renown boss, and the capture offer: exactly the party raid's path.
        RaidFaultBarrier.guard("duel-raid:history", () -> com.cobbleraids.lifecycle.RaidLifecycleCoordinator.recordAndOfferCatch(server, def,
                bossPokemon, raid.raidId, victors, damage, raid.renownTitle, raid.renownBoon, true, true));
        RaidFaultBarrier.guard("duel-raid:rewards", () -> RaidRewardService.grant(new RaidRewardEligibility(raid.raidId, def.id(),
                RaidOutcome.VICTORY, damage, victors, elapsed, raid.renownTitle, raid.renownBoon), server));
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
        for (DuelRaid raid : List.copyOf(RAIDS.values())) finish(server, raid, com.cobbleraids.telemetry.RaidEndReason.SERVER_STOP);
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
