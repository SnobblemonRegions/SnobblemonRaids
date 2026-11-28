package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import com.cobbleraids.worldboss.arena.ArenaMarkers;
import com.cobbleraids.api.encounter.EncounterListener;
import com.cobbleraids.api.encounter.EncounterPolicy;
import com.cobbleraids.api.encounter.EncounterRequest;
import com.cobbleraids.api.encounter.EncounterResult;
import com.cobbleraids.api.encounter.EncounterRules;
import com.cobbleraids.api.encounter.StartResult;
import com.cobbleraids.catching.RaidPlayerRecords;
import com.cobbleraids.api.worldboss.WorldBossListener;
import com.cobbleraids.config.CobbleRaidsConfigManager;
import com.cobbleraids.config.RaidDefinition;
import com.cobbleraids.config.RaidDefinitionRegistry;
import com.cobbleraids.encounter.EncounterService;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.renown.RenownRequest;
import com.cobbleraids.spawn.RaidBossSpawner;
import com.cobbleraids.spawn.RaidSpawnAnnouncementService;
import com.cobbleraids.spawn.claim.LandClaimGuard;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The World Boss event: one shared health pool that every player's solo duel chips away at.
 *
 * <p>This is the only place that knows how the pieces fit. A duel is an ordinary owned encounter
 * (see {@link EncounterService}); the pool, scaling and chain rules are in the pure classes next
 * to this one; the state lives in {@link WorldBossState}. The service gathers facts from the
 * server (where players stand, what their party looks like), hands them to those rules, and acts
 * on the answer.
 *
 * <p>All entry points run on the server thread, as do the encounter callbacks, so the per-player
 * maps are not locked; they are concurrent maps only so a command reading them mid-tick is safe.
 *
 * <p>The ordinary raid system is untouched: nothing here runs unless {@link WorldBossConfig#enabled}
 * is set, and the only edits outside this package are the {@code onDamage} callback on
 * {@link EncounterListener}, the call that reports it, and the registration of this service.
 */
public final class WorldBossService {

    public static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("cobbleraids", "world_boss");

    /** Marks the standing boss, so a copy left in the world by an earlier session or event can be recognised and removed when it loads. */
    public static final String SHOWPIECE_TAG = "cobbleraids_world_boss_showpiece";

    private static final int BAR_UPDATE_TICKS = 10;
    private static final int SECOND = 20;
    private static final int FLUSH_TICKS = 100;
    private static final long BREATHER_MILLIS = 15_000L;
    /** How often the zone wall is redrawn; each pass sends a few particle packets only to players near the edge. */
    private static final int BORDER_TICKS = 8;
    private static final double BORDER_SPACING = 4.0;

    private static final CopyOnWriteArrayList<WorldBossListener> LISTENERS = new CopyOnWriteArrayList<>();

    /** A running duel: which encounter it is and when it began (game ticks). */
    private record Duel(UUID encounterId, UUID bossId, long startedTick, WorldBossAdmission.Token admission,
                        Set<UUID> partyIds) {}

    private static final Map<UUID, Duel> DUELS = new ConcurrentHashMap<>();
    private static final WorldBossDuelSpace DUEL_SPACE = new WorldBossDuelSpace();
    private static final Map<UUID, UUID> DUEL_PLAYER = new ConcurrentHashMap<>();
    /** Chained players waiting out a breather: the game tick their last duel ended. */
    private static final Map<UUID, Long> CHAIN = new ConcurrentHashMap<>();
    /** Wall-clock millis each player last started a duel; sizes the pool. */
    private static final Map<UUID, Long> LAST_DUEL_START = new ConcurrentHashMap<>();
    private static final Set<UUID> PRESENT = new HashSet<>();
    private static boolean combatAnnounced;
    private static final WorldBossStartPacer START_PACER = new WorldBossStartPacer();
    /** Players last seen inside the zone, so crossing the wall can be announced once. Only touched on the server thread. */
    private static final Map<UUID, Boolean> LAST_INSIDE = new HashMap<>();

    private static volatile MinecraftServer server;
    private static volatile WorldBossState state;
    private static volatile ServerBossEvent bar;
    private static volatile UUID showpieceId;
    private static volatile boolean barDirty;
    private static long quietCardStatCalls;
    /** True while this class is creating its own showpiece, so the load hook does not mistake it for a leftover. */
    private static volatile boolean spawningShowpiece;
    private static volatile boolean stateDirty;
    private static long tickCounter;
    private static WorldBossAdmission admissions;
    private static WorldBossAdmission.Snapshot recoveryAdmission;
    private static long admissionRevision = -1;
    private static WorldBossTicker clockTicker;
    private static WorldBossControlCommit controlCommit;
    private record ArenaRestore(WorldBossState state, UUID event, WorldBossArenaRecovery work) {}
    private static ArenaRestore arenaRecovery;
    private static String controlOperation;
    private static String runtimeSignature;
    private static WorldBossOpeningRules verifiedRules;
    private static RaidDefinition verifiedDefinition;
    private static com.cobbleraids.config.CobbleRaidsConfig verifiedServerConfig;
    private static WorldBossConfig verifiedWorldBossConfig;
    private static boolean verifiedMatch;
    private static final Map<UUID, Long> BENEFIT_SETTINGS_AT = new HashMap<>();

    private WorldBossService() {}

    // ---- API for other mods ----

    public static void addListener(WorldBossListener listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(WorldBossListener listener) {
        LISTENERS.remove(listener);
    }

    public static Set<UUID> qualifiedPlayers(UUID bossId) {
        if (server == null || !server.isSameThread() || state == null) throw new IllegalStateException("World Boss eligibility requires the running server thread");
        return state.qualifiedPlayers(bossId);
    }

    public static boolean isActive() {
        WorldBossState current = state;
        return current != null && !current.isQuarantined() && current.active();
    }

    public static UUID activeBossId() {
        WorldBossState current = state;
        return current != null && current.active() ? current.bossId() : null;
    }

    /**
     * True when a point is inside the zone of a World Boss that is running and the config asks for no glow there. The raid glow service asks this
     * for every raid boss it tracks, so it must stay cheap: a few field reads and one distance test.
     */
    public static boolean suppressesGlowAt(ResourceLocation dimension, double x, double z) {
        WorldBossState current = state;
        if (current == null || !current.active() || current.pool() == null || current.pool().isDefeated()) return false;
        WorldBossConfig config = WorldBossConfigManager.get();
        if (!config.look().noGlowInZone() || !current.dimension().equals(dimension.toString())) return false;
        return WorldBossBorder.inside(current.x(), current.z(), WorldBossRules.zoneRadius(current, config), x, z);
    }

    static UUID showpieceId() { return showpieceId; }

    /** Called only after the native tracker has matched the owned showpiece UUID. */
    public static int showpieceTrackingRange(int nativeRange) {
        WorldBossState current = state;
        return current == null ? nativeRange : WorldBossVisibility.trackingRange(nativeRange,
                WorldBossRules.zoneRadius(current, WorldBossConfigManager.get()));
    }

    public static boolean isShowpiece(Entity entity) {
        UUID id = showpieceId;
        return id != null && entity != null && id.equals(entity.getUUID());
    }

    // ---- lifecycle ----

    /** After the stale-boss sweep in RaidSpawnScheduler: resumes an event that survived a restart. */
    public static void onServerStarted(MinecraftServer started) {
        WorldBossLoginPacer.started(started);
        WorldBossPlayerAutosaves.started(started);
        WorldBossCardLogs.started();
        WorldBossWorldSaves.started(started.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        WorldBossAdvancementSaves.started(started.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        quietCardStatCalls = 0;
        WorldBossArrivals.reset();
        WorldheartPresentation.reset();
        BENEFIT_SETTINGS_AT.clear();
        server = started;
        runtimeSignature = runtimeSignature();
        state = WorldBossState.get(started);
        DUEL_SPACE.clear();
        refreshArenaProtection(started);
        clockTicker = new WorldBossTicker(System.nanoTime());
        showpieceId = null;
        WorldBossState current = state;
        if (current.isQuarantined()) {
            RaidLog.error("World Boss saved data is quarantined; events will not start until it is repaired");
            return;
        }
        WorldBossDailyService.started(started, current);
        WorldBossWardService.started(current);
        WorldBossScenes.started(current);
        if (!current.active() || current.pool() == null) return;
        admissions = current.admissionCheckpoint() == null ? new WorldBossAdmission(current.bossId(), 50, BREATHER_MILLIS, current.pausedPlayers())
                : WorldBossAdmission.intent(current.admissionCheckpoint(), current.pausedPlayers());
        recoveryAdmission = current.admissionCheckpoint(); admissionRevision = -1;
        WorldBossPool pool = current.pool();
        if (current.closeReason() != null) { end(started, current.closeMessage()); return; }
        if (pool.isDefeated() || pool.current() <= 0f) {
            RaidLog.info("World Boss {} was defeated before the restart; finishing its rewards.", current.bossId());
            if (pool.isDefeated()) finishDefeat(started);
            else handleDefeat(started);
            return;
        }
        if (current.clock() != null) {
            current.clock(current.clock().recover(System.currentTimeMillis()));
            if (!current.clock().terminal() && !openingRulesMatch(current)) current.clock(current.clock().hold(WorldBossClock.Reason.RULES_CHANGED));
            stateDirty = true;
            if (!flush(started)) current.clock(current.clock().hold(WorldBossClock.Reason.STORAGE_FAILURE));
            if (current.clock().terminal()) {
                end(started, current.clock().phase() == WorldBossClock.Phase.CANCELLED
                        ? "The interrupted World Boss preparation was skipped." : "The World Boss has escaped.");
                return;
            }
            RaidLog.info("World Boss {} recovery: {}, reason {}, {} active ms remain, recoveries {}, conservative outage {} ms",
                    current.bossId(), current.clock().phase(), current.clock().reason(), current.clock().remainingMillis(),
                    current.clock().recoveries(), current.clock().offlineMillis());
        }
        RaidLog.info("Resuming World Boss {} ({}): pool {}/{}", current.bossId(), current.definitionId(),
                Math.round(pool.current()), Math.round(pool.max()));
        if (current.arena()) {
            UUID event = current.bossId();
            try { beginArenaRecovery(started, current); }
            catch (RuntimeException failure) { arenaRecoveryFailed(started, current, event, failure); }
            return;
        }
        // No showpiece is tracked yet, so any boss at the spot is a stale copy of the one about to be recreated (a restart used to duplicate it).
        try { retainArenaChunks(started); sweepLeftovers(started); spawnShowpiece(started); }
        catch (RuntimeException failure) {
            RaidLog.error("World Boss {} showpiece could not recover; holding combat", current.bossId(), failure);
            removeShowpiece(started);
            if (current.clock() != null) current.clock(current.clock().hold(WorldBossClock.Reason.PRESENTATION_FAILURE));
            flush(started);
        }
        ensureBar(started);
        clockTicker = new WorldBossTicker(System.nanoTime());
        if (current.clock() != null && current.clock().phase() == WorldBossClock.Phase.REGROUP && !current.testMode()) {
            broadcast(started, Component.literal("[World Boss] The encounter is recovering. Two-minute regroup; at least five ready players are needed.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    public static void onServerStopping(MinecraftServer stopping) {
        WorldBossLoginPacer.stopping();
        try { cancelArenaRecovery(); }
        catch (RuntimeException failure) { RaidLog.error("Arena recovery tickets could not all release during shutdown", failure); }
        try { WorldBossAdvancementSaves.close(); }
        catch (RuntimeException failure) { RaidLog.error("Queued native advancement saves failed during shutdown", failure); }
        try { WorldBossCardLogs.close(); }
        catch (RuntimeException failure) { RaidLog.error("Cards INFO output could not drain normally during shutdown", failure); }
        try { WorldBossPlayerAutosaves.close(stopping); }
        catch (java.io.IOException | RuntimeException failure) { RaidLog.error("Accepted player autosaves drained with a failure; checking native player data is required", failure); }
        try { WorldBossWorldSaves.close(); }
        catch (java.io.IOException | RuntimeException failure) { RaidLog.error("Native world NBT shutdown reported an I/O failure; inspect world data before restart", failure); }
        cancelControlCommit();
        WorldBossTravelService.reset();
        WorldBossScenes.stopped(stopping);
        advanceClock(stopping);
        cancelControlCommit();
        if (state != null && state.active() && state.clock() != null) {
            state.clock(state.clock().stopped(System.currentTimeMillis()));
            stateDirty = true;
        }
        if (flush(stopping) && state != null) {
            try { state.checkpointProjection(stopping); }
            catch (java.io.IOException | RuntimeException failure) {
                RaidLog.warn("World Boss journal committed; its rebuildable shutdown cache could not be refreshed: {}", failure.getMessage());
            }
        }
        if (state != null) {
            try { state.closeCheckpoints(); }
            catch (java.io.IOException | RuntimeException failure) { RaidLog.error("World Boss checkpoint worker could not drain cleanly", failure); }
        }
        try { com.cobbleraids.showdown.WorldBossSimulator.stopping(); }
        catch (Exception failure) { RaidLog.error("World Boss private simulator did not close cleanly", failure); }
        removeShowpiece(stopping);
        WorldBossArenaChunks.release();
        if (bar != null) bar.removeAllPlayers();
    }

    public static void onServerStopped() {
        WorldBossLoginPacer.stopped();
        arenaRecovery = null;
        WorldBossPlayerAutosaves.stopped();
        WorldBossCardLogs.stopped();
        WorldBossWorldSaves.stopped();
        WorldBossAdvancementSaves.stopped();
        com.cobbleraids.showdown.WorldBossSimulator.stopped();
        WorldBossArrivals.reset();
        DUEL_SPACE.clear();
        WorldBossArenaProtection.reset();
        WorldBossArenaChunks.release();
        DUELS.clear();
        DUEL_PLAYER.clear();
        CHAIN.clear();
        LAST_DUEL_START.clear();
        PRESENT.clear();
        combatAnnounced = false;
        START_PACER.reset();
        LAST_INSIDE.clear();
        WorldBossMusic.clear();
        WorldBossDome.reset();
        server = null;
        state = null;
        bar = null;
        showpieceId = null;
        barDirty = false;
        stateDirty = false;
        tickCounter = 0L;
        admissions = null;
        recoveryAdmission = null; admissionRevision = -1;
        clockTicker = null;
        cancelControlCommit();
        runtimeSignature = null;
        WorldBossDailyService.stopped();
        WorldBossWardService.stopped();
        WorldBossScenes.reset();
        WorldheartPresentation.reset();
        BENEFIT_SETTINGS_AT.clear();
        WorldBossTravelService.reset();
        verifiedRules = null;
        verifiedDefinition = null;
        verifiedServerConfig = null;
        verifiedWorldBossConfig = null;
    }

    /** Called every server tick; does its real work once a second. */
    public static void tick(MinecraftServer current) {
        WorldBossLoginPacer.tick(current);
        Throwable advancementSaveFailure = WorldBossAdvancementSaves.tick();
        if (advancementSaveFailure != null) {
            RaidLog.error("Paced native advancement save failed; stopping world-boss combat", advancementSaveFailure);
            cancelControlCommit();
            if (state != null && state.active() && state.clock() != null) {
                state.clock(state.clock().hold(WorldBossClock.Reason.STORAGE_FAILURE));
                stateDirty = true; barDirty = true; abortDuels();
            }
        }
        var cardLogFailure = WorldBossCardLogs.pollFailure();
        if (cardLogFailure != null) RaidLog.error("Queued Cards INFO output failed; subsequent native messages use their original logging path", cardLogFailure);
        Throwable playerSaveFailure = WorldBossPlayerAutosaves.poll(current);
        if (playerSaveFailure != null) {
            RaidLog.error("Arena player autosave failed; stopping world-boss combat", playerSaveFailure);
            cancelControlCommit();
            if (state != null && state.active() && state.clock() != null) {
                state.clock(state.clock().hold(WorldBossClock.Reason.STORAGE_FAILURE));
                stateDirty = true; barDirty = true; abortDuels();
            }
        }
        Throwable worldSaveFailure = WorldBossWorldSaves.poll();
        if (worldSaveFailure != null) {
            RaidLog.error("Native world NBT autosave failed; stopping world-boss combat", worldSaveFailure);
            cancelControlCommit();
            if (state != null && state.active() && state.clock() != null) {
                state.clock(state.clock().hold(WorldBossClock.Reason.STORAGE_FAILURE));
                stateDirty = true; barDirty = true; abortDuels();
            }
        }
        if (state == null) return;
        try { state.pollCheckpoint(); }
        catch (java.io.IOException | RuntimeException failure) {
            RaidLog.error("World Boss periodic checkpoint failed; stopping combat", failure);
            cancelControlCommit();
            if (state.clock() != null) state.clock(state.clock().hold(WorldBossClock.Reason.STORAGE_FAILURE));
            stateDirty = true;
            abortDuels();
        }
        tickCounter++;
        if (tickCounter % SECOND == 0) refreshArenaProtection(current);
        if (state.isQuarantined()) {
            cancelControlCommit();
            if (tickCounter % SECOND == 0) { abortDuels(); clearBar(); WorldBossMusic.stopAll(current); WorldBossDome.clear(current); WorldBossArrivals.stopped(current); WorldBossArenaChunks.release(); }
            return;
        }
        if (state.checkpointOverdue()) {
            cancelControlCommit();
            if (state.active() && state.clock() != null && state.clock().phase() != WorldBossClock.Phase.HELD) {
                RaidLog.warn("World Boss checkpoint is still awaiting storage after two seconds; holding combat");
                state.clock(state.clock().hold(WorldBossClock.Reason.STORAGE_FAILURE));
                stateDirty = true;
                barDirty = true;
                abortDuels();
            }
            // Arrival/return and economic tick adapters may commit critically; defer them until this writer completes.
            return;
        }
        if (controlCommit != null) {
            var active = controlCommit;
            RaidFaultBarrier.guard("worldboss:control-commit", active::tick);
            if (controlCommit == active && active.done()) { controlCommit = null; controlOperation = null; }
            return; // Let a complete frame finish before automatic adapters resume.
        }
        if (arenaRecovery != null) {
            var recovering = arenaRecovery;
            try { if (recovering.work().tick() && arenaRecovery == recovering) arenaRecovery = null; }
            catch (RuntimeException failure) {
                try { cancelArenaRecovery(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                arenaRecoveryFailed(current, recovering.state(), recovering.event(), failure);
            }
            return; // Regroup timing, arrivals and combat begin after the arena is actually ready.
        }
        boolean servicedArrival = WorldBossArrivals.batchActive();
        if (servicedArrival) {
            RaidFaultBarrier.safeTick("worldboss-arrival-commit", current, WorldBossArrivals::tick);
            if (WorldBossArrivals.batchActive()) return;
        }
        tickDuelSpace(current);
        RaidFaultBarrier.safeTick("worldheart-effects", current, WorldheartPresentation::tick);
        if (tickCounter % SECOND == 0) RaidFaultBarrier.safeTick("worldboss-blessing", current, WorldBossService::settleBlessingBonuses);
        if (tickCounter % SECOND == 0) tickArenaChunks(current);
        advanceClock(current);
        if (controlCommitPending()) return;
        if (!servicedArrival) RaidFaultBarrier.safeTick("worldboss-arrivals", current, WorldBossArrivals::tick);
        RaidFaultBarrier.safeTick("worldboss-returns", current, WorldBossTravelService::tick);
        WorldBossDailyService.tick(current);
        RaidFaultBarrier.safeTick("worldboss-scenes", current, WorldBossScenes::tick);
        WorldBossWardService.tick(current);
        if (stateDirty && (state.clock() != null && clockTicker != null && clockTicker.checkpointDue()
                || state.clock() == null && tickCounter % FLUSH_TICKS == 0)) {
            if (!state.checkpointPending()) {
                try {
                    checkpointAdmissions(state);
                    state.checkpointAsync(current);
                    stateDirty = false;
                    if (clockTicker != null) clockTicker.checkpointed();
                } catch (java.io.IOException | RuntimeException failure) {
                    RaidLog.error("Could not schedule World Boss periodic checkpoint", failure);
                    stateDirty = true;
                    if (clockTicker != null) clockTicker.checkpointed();
                    if (state.clock() != null && state.active()) {
                        state.clock(state.clock().hold(WorldBossClock.Reason.STORAGE_FAILURE));
                        abortDuels();
                    }
                }
            }
        }
        if (tickCounter % BAR_UPDATE_TICKS == 0 && barDirty) refreshBar(current);
        if (tickCounter % BORDER_TICKS == 0) {
            WorldBossState live = state;
            WorldBossConfig visual = WorldBossConfigManager.get();
            boolean running = live.active() && live.pool() != null && !live.pool().isDefeated();
            if (running) WorldBossDome.tick(current, live, visual);
            else WorldBossDome.clear(current);
            if (running && (visual.borderParticles() || visual.dome().enabled())) drawBorder(current, live, visual);
            else LAST_INSIDE.clear();
        }
        tickAdmissions(current);
        if (admissions != null && admissionRevision != admissions.revision()) { admissionRevision = admissions.revision(); mechanicsChanged(); }
        if (tickCounter % SECOND != 0) return;

        WorldBossConfig config = WorldBossConfigManager.get();
        long now = System.currentTimeMillis();
        WorldBossState s = state;
        tickDuels(current, config); // Old duels still need bounded cleanup after their World Pool ends.

        // The fall is noticed inside a battle's damage callback and the state is closed in the same call, so the other players' copies
        // are cleaned up here, a second later and outside any battle: nobody keeps fighting a clone of a boss that is already dead.
        if (!s.active() || (s.pool() != null && s.pool().isDefeated())) {
            if (!DUELS.isEmpty() || !CHAIN.isEmpty()) {
                abortDuels();
                CHAIN.clear();
            }
        }

        if (!s.active()) {
            WorldBossMusic.stopAll(current);
            return;
        }
        if (s.closeReason() != null) { end(current, s.closeMessage()); return; }
        if (s.pool() != null && s.pool().current() <= 0f && !s.pool().isDefeated()) { handleDefeat(current); return; }
        if (s.pool() != null && s.pool().isDefeated()) {
            WorldBossMusic.stopAll(current);
            finishDefeat(current); // Retry pending durable settlement rather than strand a defeated event.
            return;
        }
        if (s.clock() != null && s.clock().terminal()) {
            end(current, s.clock().phase() == WorldBossClock.Phase.CANCELLED
                    ? "The gathering could not open safely. This World Boss occurrence was skipped." : "The World Boss has escaped.");
            return;
        }
        if (s.clock() == null && s.endsAtMillis() > 0L && now >= s.endsAtMillis()) {
            end(current, "The World Boss has left.");
            return;
        }
        if (s.clock() != null && s.clock().phase() == WorldBossClock.Phase.HELD) WorldBossMusic.stopAll(current);
        else WorldBossMusic.tick(current, s, config, DUELS.keySet(), CHAIN.keySet());
        refreshBar(current);
    }

    // ---- starting and ending ----

    private static String runtimeSignature() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().stream()
                .map(mod -> mod.getMetadata().getId() + "@" + mod.getMetadata().getVersion().getFriendlyString())
                .sorted().collect(java.util.stream.Collectors.joining("\n"));
    }

    private static boolean openingRulesMatch(WorldBossState s) {
        if (s.openingRules() == null) return true;
        ResourceLocation id = ResourceLocation.tryParse(s.definitionId());
        RaidDefinition definition = id == null ? null : RaidDefinitionRegistry.get(id);
        var global = com.cobbleraids.config.CobbleRaidsConfigManager.get();
        var currentWorldBossConfig = WorldBossConfigManager.get();
        if (s.openingRules() == verifiedRules && definition == verifiedDefinition && global == verifiedServerConfig && currentWorldBossConfig == verifiedWorldBossConfig) return verifiedMatch;
        verifiedRules = s.openingRules();
        verifiedDefinition = definition;
        verifiedServerConfig = global;
        verifiedWorldBossConfig = currentWorldBossConfig;
        verifiedMatch = definition != null && s.openingRules().calibration().rulesFingerprint()
                .equals(s.merit() == null ? s.openingRules().legacyFingerprint(definition, calibrationRuntime())
                        : s.wards().route() == null ? s.openingRules().focusOnlyFingerprint(definition, calibrationRuntime())
                        : s.admissionCheckpoint() != null && !s.admissionCheckpoint().cutoffEnabled() ? s.openingRules().relayOnlyFingerprint(definition, calibrationRuntime()) : s.openingRules().fingerprint(definition, calibrationRuntime()));
        // Synthetic, non-economic rehearsals may omit the route; live starts always require it.
        if (!verifiedMatch && definition != null && s.testMode() && s.merit() != null && s.wards().route() == null) verifiedMatch = s.openingRules().calibration().rulesFingerprint().equals(
                s.admissionCheckpoint() != null && !s.admissionCheckpoint().cutoffEnabled() ? s.openingRules().relayOnlyFingerprint(definition, calibrationRuntime()) : s.openingRules().fingerprint(definition, calibrationRuntime()));
        return verifiedMatch;
    }

    private static String calibrationRuntime() {
        var config = WorldBossConfigManager.get();
        return runtimeSignature + "\nserver.json=" + WorldBossOpeningRules.canonicalText(com.cobbleraids.config.CobbleRaidsConfigManager.get().toJson())
                + "\narena-placement-v5:radius=" + config.arena().radius() + ";duelScale=" + config.duelBossScale() + ";showpieceScale=" + config.bossScale();
    }

    private static boolean checkOpeningRules(MinecraftServer current) {
        if (openingRulesMatch(state)) return true;
        state.clock(state.clock().hold(WorldBossClock.Reason.RULES_CHANGED));
        stateDirty = true;
        barDirty = true;
        flush(current);
        abortDuels();
        CHAIN.clear();
        return false;
    }

    static String livePreflight(MinecraftServer current, WorldBossConfig config) {
        if (config.raidDefinitions().size() != 1) return "live World Bosses require exactly one designated raid_definitions entry";
        ResourceLocation id = ResourceLocation.tryParse(config.raidDefinitions().getFirst());
        RaidDefinition definition = id == null ? null : RaidDefinitionRegistry.get(id);
        if (definition == null) return "the designated boss definition is not loaded";
        String fingerprint = WorldBossOpeningRules.fingerprint(config, definition, calibrationRuntime());
        boolean calibrated = config.calibrations().stream().anyMatch(value -> value.definition().equals(definition.id().toString())
                && value.rulesFingerprint().equals(fingerprint));
        if (!calibrated) return "measured calibration is missing or its rules fingerprint changed; use /cr worldboss preflight";
        try {
            WorldBossOpeningRules.capture(config, config.calibrations().stream().filter(value -> value.definition().equals(definition.id().toString())).findFirst().orElseThrow());
        } catch (RuntimeException invalid) { return "invalid battle rules: " + invalid.getMessage(); }
        return liveArenaProblem(current, config);
    }

    /** Read-only basic start gates and the fingerprint needed by an independently measured report. */
    public static List<String> preflight(MinecraftServer current) {
        WorldBossConfig config = WorldBossConfigManager.get();
        List<String> lines = new ArrayList<>();
        lines.add("World Boss enabled: " + config.enabled());
        lines.add("Arena safety: scoped native protection and reserved duel lanes; direct mod/command world writes and native party placement require separate verification.");
        if (state != null) lines.add(state.recoveryStatus());
        lines.add("World Boss simulator: " + com.cobbleraids.showdown.WorldBossSimulator.status());
        lines.add("World Boss simulator tickets: " + com.cobbleraids.showdown.WorldBossSimulator.diagnostics());
        lines.add("World Boss login continuations: " + WorldBossLoginPacer.diagnostics());
        lines.add("World Boss player autosaves: " + WorldBossPlayerAutosaves.diagnostics());
        lines.add("World Boss world NBT autosaves: " + WorldBossWorldSaves.diagnostics());
        lines.add("World Boss advancement autosaves: " + WorldBossAdvancementSaves.diagnostics());
        lines.add("World Boss card stat diagnostics routed to DEBUG: " + quietCardStatCalls);
        lines.add("World Boss Cards INFO output: " + WorldBossCardLogs.diagnostics());
        lines.add("World Boss control save: " + (controlCommitPending() ? "pending " + controlOperation : "none"));
        lines.add("World Boss arena recovery: " + (arenaRecovery == null ? "ready" : arenaRecovery.work().status()));
        lines.add("Recorded voice assets: " + WorldBossVoicePack.available() + "/" + WorldBossSceneScript.catalogue().size() + "; missing recordings use scene text and vanilla cues. Voice quality signoff remains separate.");
        for (String name : config.raidDefinitions()) {
            ResourceLocation id = ResourceLocation.tryParse(name);
            RaidDefinition definition = id == null ? null : RaidDefinitionRegistry.get(id);
            lines.add(definition == null ? name + ": definition missing" : name + " rules_fingerprint: "
                    + WorldBossOpeningRules.fingerprint(config, definition, calibrationRuntime()));
        }
        String problem = state == null || state.isQuarantined() ? "saved World Boss data is unavailable or quarantined" : livePreflight(current, config);
        lines.add(problem == null ? "Basic live start gates: ready" : "Live start blocked: " + problem);
        String arenaProblem = liveArenaProblem(current, config);
        lines.add(arenaProblem == null ? "Arena basic geometry/arrival checks: ready" : "Arena: " + arenaProblem);
        lines.addAll(WorldBossDailyService.status());
        lines.add("Full release verification remains incomplete.");
        return lines;
    }

    private static String liveArenaProblem(MinecraftServer current, WorldBossConfig config) {
        if (!config.arena().enabled()) return "a designated arena is required for live World Bosses";
        var markers = ArenaMarkers.get(current);
        var spots = markers.list(ArenaMarkers.Kind.SPAWN);
        if (spots.size() != 1) return "exactly one registered arena spawn marker is required";
        var spot = spots.getFirst();
        ResourceLocation id = ResourceLocation.tryParse(spot.dimension());
        ServerLevel level = id == null ? null : current.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) return "the designated arena dimension does not exist";
        String chunkProblem = WorldBossArenaChunks.problem(level, spot.pos().getX() + 0.5, spot.pos().getZ() + 0.5, config.arena().radius());
        if (chunkProblem != null) return chunkProblem;
        if (!safeMarker(level, spot.pos(), ArenaMarkers.Kind.SPAWN)) return "the spawn marker is stale or lacks safe ground/clearance";
        double radius = config.arena().radius();
        if (spot.pos().getY() - radius < level.getMinBuildHeight() || spot.pos().getY() + radius >= level.getMaxBuildHeight()) return "the boss sphere crosses the arena's build-height boundary";
        int extent = (int) Math.ceil(radius);
        for (int dx : new int[]{-extent, extent}) for (int dz : new int[]{-extent, extent}) {
            if (!level.getWorldBorder().isWithinBounds(spot.pos().offset(dx, 0, dz))) return "the boss sphere crosses the world border";
        }
        boolean entrance = markers.list(ArenaMarkers.Kind.ENTRANCE).stream().anyMatch(entry -> entry.dimension().equals(spot.dimension())
                && WorldBossZone.inside(spot.pos().getX() + 0.5, spot.pos().getY(), spot.pos().getZ() + 0.5, radius,
                        entry.pos().getX() + 0.5, entry.pos().getY(), entry.pos().getZ() + 0.5)
                && safeMarker(level, entry.pos(), ArenaMarkers.Kind.ENTRANCE));
        if (!entrance) return "a loaded, safe entrance marker inside the designated bubble is required";
        if (markers.list(ArenaMarkers.Kind.ENTRANCE).size() > 50) return "at most fifty entrance markers are supported";
        var venue = new WorldBossArrivalPlan.Venue(spot.dimension(), spot.pos().getX() + 0.5, spot.pos().getY(), spot.pos().getZ() + 0.5, radius);
        var viewers = markers.list(ArenaMarkers.Kind.OBSERVER);
        if (viewers.isEmpty() || viewers.size() > 25 || viewers.stream().noneMatch(entry -> entry.dimension().equals(spot.dimension())
                && safeMarker(level, entry.pos(), ArenaMarkers.Kind.OBSERVER) && venue.accepts(new WorldBossTravel.Position(entry.dimension(), entry.pos().getX() + 0.5, entry.pos().getY(), entry.pos().getZ() + 0.5, 0, 0), true)))
            return "place one to twenty-five safe spectator terrace markers outside radius R+2 and within R+12";
        var fallback = WorldBossTravelService.spawn(current);
        if (venue.contains(fallback)) return "the shared safe spawn must be outside the arena audience volume (R+16)";
        if (!WorldBossTravelService.safeLoaded(current.overworld(), fallback, 0.6, 1.8)) return "the shared world spawn must provide a loaded, safe return fallback";
        var pylons = markers.list(ArenaMarkers.Kind.WARD_PYLON);
        if (pylons.size() != 1) return "exactly one registered ward pylon is required";
        var pylon = pylons.getFirst();
        if (!(pylon.dimension().equals(spot.dimension()) && safeMarker(level, pylon.pos(), ArenaMarkers.Kind.WARD_PYLON)
                && WorldBossZone.inside(spot.pos().getX() + 0.5, spot.pos().getY(), spot.pos().getZ() + 0.5, radius,
                        pylon.pos().getX() + 0.5, pylon.pos().getY(), pylon.pos().getZ() + 0.5))) return "the ward pylon must be safe, loaded and inside the arena bubble";
        return relayLayoutProblem(level, markers, pylon, spot.pos().getX() + 0.5, spot.pos().getY(), spot.pos().getZ() + 0.5, radius);
    }

    private static WorldBossWards.Pylon wardPoint(ArenaMarkers.Spot spot) {
        return new WorldBossWards.Pylon(spot.dimension(), spot.pos().getX(), spot.pos().getY(), spot.pos().getZ());
    }

    private static String relayLayoutProblem(ServerLevel level, ArenaMarkers markers, ArenaMarkers.Spot pylon, double x, double y, double z, double radius) {
        var caches = markers.list(ArenaMarkers.Kind.RELAY_CACHE); var checkpoints = markers.list(ArenaMarkers.Kind.RELAY_CHECKPOINT);
        if (caches.size() != 1 || checkpoints.size() != 1) return "exactly one relay cache and one relay checkpoint are required";
        var cache = caches.getFirst(); var checkpoint = checkpoints.getFirst();
        for (var entry : List.of(cache, checkpoint)) if (!entry.dimension().equals(pylon.dimension())
                || !WorldBossZone.inside(x, y, z, radius, entry.pos().getX() + 0.5, entry.pos().getY(), entry.pos().getZ() + 0.5)
                || !safeMarker(level, entry.pos(), entry == cache ? ArenaMarkers.Kind.RELAY_CACHE : ArenaMarkers.Kind.RELAY_CHECKPOINT)) return "relay markers must be safe, loaded and inside the arena bubble";
        try { new WorldBossRelay.Route(wardPoint(cache), wardPoint(checkpoint)).validate(wardPoint(pylon)); }
        catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
        // Check the bounded walking line; this never loads or generates distant chunks.
        for (var leg : List.of(List.of(cache.pos(), checkpoint.pos()), List.of(checkpoint.pos(), pylon.pos()))) {
            var from = leg.getFirst(); var to = leg.getLast(); int steps = (int) Math.ceil(Math.sqrt(from.distSqr(to)) * 2);
            for (int step = 0; step <= steps; step++) {
                double fraction = step / (double) steps;
                var pos = BlockPos.containing(from.getX() + 0.5 + (to.getX() - from.getX()) * fraction, from.getY(), from.getZ() + 0.5 + (to.getZ() - from.getZ()) * fraction);
                if (!safeWalkingSurface(level, pos)) return "relay walking lines must be loaded, clear and have safe solid ground";
            }
        }
        return null;
    }

    static boolean safeMarker(ServerLevel level, BlockPos pos, ArenaMarkers.Kind kind) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || pos.getY() <= level.getMinBuildHeight()
                || pos.getY() + 1 >= level.getMaxBuildHeight()) return false;
        if (!(level.getBlockState(pos).getBlock() instanceof com.cobbleraids.worldboss.arena.ArenaMarkerBlock marker) || marker.kind() != kind) return false;
        return isSimpleSurface(level, pos) && safeWalkingBlocks(level, pos);
    }

    private static boolean safeWalkingSurface(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !isSimpleSurface(level, pos)
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) return false;
        return safeWalkingBlocks(level, pos);
    }

    private static boolean safeWalkingBlocks(ServerLevel level, BlockPos pos) {
        for (BlockPos at : List.of(pos.below(), pos, pos.above())) {
            var block = level.getBlockState(at);
            if (block.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || block.is(net.minecraft.world.level.block.Blocks.CACTUS)
                    || block.is(net.minecraft.world.level.block.Blocks.FIRE) || block.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                    || block.is(net.minecraft.world.level.block.Blocks.CAMPFIRE) || block.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                    || block.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW) || block.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                    || block.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)) return false;
        }
        return true;
    }

    private static boolean combatOpen(WorldBossState s) {
        if (controlCommitPending()) return false;
        if (!s.active() || s.closeReason() != null || s.pool() == null || s.pool().isDefeated() || s.pool().current() <= 0f) return false;
        if (s.openingRules() != null && (s.openingRoster() == null || !s.pool().isFixed())) return false;
        return s.clock() != null ? s.clock().combatOpen()
                : s.endsAtMillis() <= 0 || System.currentTimeMillis() < s.endsAtMillis();
    }

    /** Sample before admissions and damage as well as on tick: slow running servers lose real time. */
    private static void advanceClock(MinecraftServer current) {
        if (controlCommitPending()) return;
        WorldBossState s = state;
        if (s == null || !s.active() || s.closeReason() != null || s.clock() == null || clockTicker == null
                || s.pool() == null || s.pool().isDefeated() || s.pool().current() <= 0f) return;
        WorldBossClock previous = s.clock();
        long elapsed = clockTicker.sample(System.nanoTime());
        long nowWall = System.currentTimeMillis();
        var scheduled = previous.phase() == WorldBossClock.Phase.PREPARING ? s.campaignEvent() : null;
        boolean openingDue = scheduled != null ? nowWall >= scheduled.combatAtMillis() : elapsed >= previous.phaseRemainingMillis();
        boolean enough = true;
        List<UUID> readyRoster = List.of();
        if ((previous.phase() == WorldBossClock.Phase.PREPARING || previous.phase() == WorldBossClock.Phase.REGROUP)
                && openingDue) {
            WorldBossConfig config = WorldBossConfigManager.get();
            List<UUID> ready = new ArrayList<>();
            for (ServerPlayer player : current.getPlayerList().getPlayers()) {
                if (!s.isPaused(player.getUUID()) && cannotDuel(player, s, config) == null) ready.add(player.getUUID());
            }
            enough = ready.size() >= (s.testMode() ? 1 : 5);
            if (admissions != null) {
                if (recoveryAdmission != null && recoveryAdmission.event().equals(s.bossId()) && nowWall >= recoveryAdmission.checkpointMillis()) {
                    var merged = recoveryAdmission.mergeRegroup(admissions.snapshot(nowWall), nowWall);
                    admissions = WorldBossAdmission.recover(merged, ready, s.pausedPlayers(), nowWall); recoveryAdmission = null; admissionRevision = -1;
                }
                admissions.reconcileReady(ready);
                readyRoster = admissions.waiting().stream().limit(50).toList();
            }
        }
        WorldBossClock next = nowWall < s.campaign().wallHighWaterMillis() ? previous.hold(WorldBossClock.Reason.CLOCK_UNCERTAIN)
                : elapsed < 0 ? previous.hold(WorldBossClock.Reason.MONOTONIC_REWIND)
                : scheduled != null ? previous.scheduledPreparation(scheduled.slot(), elapsed, nowWall, enough)
                : previous.advance(elapsed, nowWall, enough);
        if (next.phase() == WorldBossClock.Phase.ACTIVE && previous.phase() != WorldBossClock.Phase.ACTIVE && s.openingRules() != null) {
            if (!openingRulesMatch(s)) next = next.hold(WorldBossClock.Reason.RULES_CHANGED);
            else if (s.openingRoster() == null) {
                try { s.freezeOpening(readyRoster); }
                catch (RuntimeException invalid) {
                    RaidLog.error("World Boss {} could not freeze its opening", s.bossId(), invalid);
                    next = next.hold(WorldBossClock.Reason.CALIBRATION_INVALID);
                }
            }
        }
        if (next.phase() == WorldBossClock.Phase.ACTIVE && previous.phase() != WorldBossClock.Phase.ACTIVE && s.campaignEvent() != null
                && s.campaignEvent().status() == WorldBossCampaign.Status.PREPARING) {
            var event = s.campaignEvent();
            s.campaign(s.campaign().transition(event.key(), event.eventId(), WorldBossCampaign.Status.OPEN, "", Math.max(nowWall, s.campaign().wallHighWaterMillis())));
        }
        s.clock(next);
        stateDirty = true;
        if (next.phase() != previous.phase()) {
            barDirty = true;
            RaidLog.info("World Boss {} timing: {} -> {}, reason {}, active ms remaining {}", s.bossId(),
                    previous.phase(), next.phase(), next.reason(), next.remainingMillis());
            if (next.phase() == WorldBossClock.Phase.ACTIVE) {
                queueControlCommit(current, s, "combat opening", WorldBossClock.Phase.ACTIVE, () -> {
                    if (!openingRulesMatch(s)) {
                        s.clock(s.clock().hold(WorldBossClock.Reason.RULES_CHANGED));
                        stateDirty = true; barDirty = true;
                        return;
                    }
                    if (scheduled != null && System.currentTimeMillis() > scheduled.combatAtMillis() + WorldBossSchedule.START_TOLERANCE.toMillis()) {
                        end(current, "The gathering could not open on time. This World Boss occurrence was skipped.");
                        return;
                    }
                    clockTicker = new WorldBossTicker(System.nanoTime());
                });
                return;
            }
            if (!flush(current)) {
                s.clock(next.hold(WorldBossClock.Reason.STORAGE_FAILURE));
                next = s.clock();
            }
            if (next.phase() == WorldBossClock.Phase.HELD || next.terminal()) {
                abortDuels();
                CHAIN.clear();
            }
        }
    }

    /** Operator hold never changes shared HP, contributions or the remaining active budget. */
    public static String holdEvent(MinecraftServer current) {
        if (!current.isSameThread()) throw new IllegalStateException("World Boss control requires the server thread");
        WorldBossState s = state;
        if (s == null || !s.active() || s.isQuarantined() || s.clock() == null) return "no controllable timed World Boss is active";
        if (s.closeReason() != null) return "the outcome is already committed; reward settlement is pending";
        cancelControlCommit();
        advanceClock(current);
        cancelControlCommit(); // Sampling may stage an opening exactly at the countdown boundary.
        if (s.clock().terminal() || s.pool().isDefeated()) return "the World Boss outcome is already decided";
        s.clock(s.clock().hold(WorldBossClock.Reason.STAFF));
        stateDirty = true;
        abortDuels();
        CHAIN.clear();
        barDirty = true;
        if (state != s || !s.active() || s.clock() == null || s.clock().phase() != WorldBossClock.Phase.HELD
                || controlCommitPending()) return "the World Boss changed while its duels were closing; check status";
        queueControlCommit(current, s, "staff hold", WorldBossClock.Phase.HELD, () ->
                RaidLog.info("World Boss {} staff hold acknowledged, active ms remaining {}", s.bossId(), s.clock().remainingMillis()));
        return null;
    }

    public static String recoverEvent(MinecraftServer current) {
        if (!current.isSameThread()) throw new IllegalStateException("World Boss control requires the server thread");
        if (arenaRecovery != null) return "the arena is still restoring; wait for arena recovery to finish";
        if (WorldBossPlayerAutosaves.failed()) return "player autosave storage failed; check player data and restart before recovery";
        if (WorldBossWorldSaves.failed()) return "world NBT autosave storage failed; check world data and restart before recovery";
        if (WorldBossAdvancementSaves.failed()) return "native advancement saving failed; check advancements and restart before recovery";
        if (controlCommitPending()) return "a World Boss control save is already pending; wait for its acknowledgement";
        if (!com.cobbleraids.showdown.WorldBossSimulator.ready()) return "World Boss simulator is " + com.cobbleraids.showdown.WorldBossSimulator.status() + ".";
        WorldBossState s = state;
        if (s == null || !s.active() || s.isQuarantined() || s.clock() == null) return "no controllable timed World Boss is active";
        if (s.closeReason() != null) return "the outcome is already committed; reward settlement is pending";
        advanceClock(current);
        if (s.clock().phase() != WorldBossClock.Phase.HELD || s.pool().isDefeated()) return "the World Boss is not on staff hold";
        UUID recoveringEvent = s.bossId();
        long now = System.currentTimeMillis();
        if (s.campaignEvent() != null && s.openingRoster() == null) {
            return "scheduled preparation cannot resume through regroup; cancel it and wait for the next occurrence";
        }
        if (now < s.clock().checkpointMillis()) return "the wall clock is behind the saved checkpoint; correct it before recovery";
        try {
            retainArenaChunks(current);
            if (s.arena() && showpieceId == null) { sweepLeftovers(current); spawnShowpiece(current); }
        } catch (RuntimeException unavailable) {
            RaidLog.warn("World Boss {} arena is not ready for staff recovery: {}", s.bossId(), unavailable.getMessage());
            return "the arena chunks or standing boss are unavailable; load the arena and its dependency ring before retrying";
        }
        if (!sameActiveEvent(s, recoveringEvent) || s.clock() == null || s.clock().phase() != WorldBossClock.Phase.HELD)
            return "the World Boss changed while preparing recovery; the old recovery was cancelled";
        if (s.campaignEvent() != null && showpieceId == null) return "restore the standing boss before recovering this encounter";
        s.clock(s.clock().resumeByStaff(now));
        stateDirty = true;
        queueControlCommit(current, s, "recovery", WorldBossClock.Phase.REGROUP, () -> {
            barDirty = true;
            clockTicker = new WorldBossTicker(System.nanoTime());
            RaidLog.info("World Boss {} staff recovery committed; two-minute regroup, {} active ms remain", s.bossId(), s.clock().remainingMillis());
            if (!s.testMode()) broadcast(current, Component.literal("[World Boss] Two-minute regroup. Combat resumes with at least five ready players.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        });
        return null;
    }

    /** Legacy manual-live-start entry point; only reserved daily occurrences may start live events. */
    public static String start(MinecraftServer current, ServerPlayer near) {
        return "live World Bosses launch from activated seasons at noon/20:00 America/New_York; use test start for rehearsals";
    }

    public static String startTest(MinecraftServer current, ServerPlayer near) {
        return start(current, near, true, null);
    }

    static String startScheduled(MinecraftServer current, WorldBossCampaign.Occurrence event) { return start(current, null, false, event); }
    static boolean hasOwnedDuels() { return !DUELS.isEmpty(); }
    static boolean flushCampaign(MinecraftServer current) { stateDirty = true; return flush(current); }
    static WorldBossState collectionState() { var value = state; return value == null || !value.collectionAvailable() ? null : value; }
    static String saveBenefits(MinecraftServer current, UUID actor, WorldBossBenefits next) {
        var value = collectionState(); if (value == null) return "Worldheart benefits are unavailable.";
        var before = value.benefits(); if (next == before) return null;
        long now = System.nanoTime(); var last = BENEFIT_SETTINGS_AT.get(actor);
        if (last != null && now - last < 1_000_000_000L) return "Please wait a moment before changing Worldheart settings.";
        BENEFIT_SETTINGS_AT.put(actor, now); value.benefits(next);
        if (!flush(current)) { value.benefits(before); return "The change could not be saved. Your previous settings remain active."; }
        return null;
    }
    /** Ordinary native raids only; the lifecycle calls this after the committed victory snapshot. */
    public static void ordinaryVictory(MinecraftServer current, com.cobbleraids.lifecycle.RaidRewardEligibility victory) {
        if (!current.isSameThread()) throw new IllegalStateException("Blessing promises require the server thread");
        var value = collectionState();
        if (value == null || victory.outcome() != com.cobbleraids.lifecycle.RaidOutcome.VICTORY || victory.committedAtMillis() <= 0 || !value.collection().hasBlessings()) return;
        var definition = RaidDefinitionRegistry.get(victory.definitionId()); if (definition == null) return;
        var points = com.cobbleraids.config.CobbleRaidsConfigManager.get().raidPoints();
        int base = points.enabled() && !points.isNoOp() ? points.pointsFor(definition.rarityTier()) : 0;
        value.benefits(value.benefits().promise(victory.raidId(), victory.committedAtMillis(), base, victory.participants(), value.collection().blessedAt(victory.committedAtMillis())));
        mechanicsChanged();
        if (flush(current)) settleBlessingBonuses(current);
    }
    private static void settleBlessingBonuses(MinecraftServer current) {
        var value = collectionState(); if (value == null) return;
        int budget = 2;
        for (var entry : value.benefits().pendingBonuses().entrySet()) {
            var promise = entry.getValue(); if (promise.paid()) continue;
            if (budget-- <= 0) break;
            // The entire immutable promise must be durable before its shared-currency effect.
            if (!flush(current)) return;
            try { var payout = WorldBossBenefits.payoutId(entry.getKey()); RaidPlayerRecords.creditWorldBossOnce(current, value.recoveryInstallation(payout, promise.amounts()), payout, promise.amounts()); }
            catch (java.io.IOException | RuntimeException failure) { RaidLog.error("Ordinary raid {} blessing credit remains pending", entry.getKey(), failure); return; }
            value.benefits(value.benefits().secured(entry.getKey()));
            if (!flush(current)) return;
            promise.amounts().forEach((id, amount) -> { var player = current.getPlayerList().getPlayer(id);
                if (player != null && amount > 0) say(player, "World Boss blessing: earned +" + amount + " Raid Points. Any balance overflow remains claimable with /cr worldboss claim.", ChatFormatting.GOLD);
            });
        }
    }
    static String selectLegacy(MinecraftServer current, UUID player, UUID character) {
        if (!current.isSameThread()) throw new IllegalStateException("Worldheart selection requires the server thread");
        var value = collectionState(); if (value == null) return "Worldheart collection is unavailable while saved data needs repair.";
        var previous = value.collection();
        try { value.selectLegacy(player, character); }
        catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
        if (value.collection() == previous) return null;
        if (!flush(current)) { value.restoreCollection(previous); return "Your project change could not be saved. The previous selection remains active."; }
        return null;
    }
    static WorldBossAdmission wardAdmission() { return admissions; }
    static boolean ownsWardDuel(UUID player, WorldBossAdmission.Token token) { var duel = DUELS.get(player); return duel != null && duel.admission().equals(token); }
    static void mechanicsChanged() { if (state != null) state.touch(); stateDirty = true; barDirty = true; }
    static boolean flushMechanics(MinecraftServer server) { return flush(server); }
    static long checkpointArrivals(MinecraftServer current) throws java.io.IOException {
        checkpointAdmissions(state);
        long receipt = state.checkpointAsync(current);
        stateDirty = false;
        if (clockTicker != null) clockTicker.checkpointed();
        return receipt;
    }
    public static boolean arrivalCommitPending() { return WorldBossArrivals.committing(); }
    public static boolean controlCommitPending() { return controlCommit != null && !controlCommit.done(); }
    private static void cancelControlCommit() {
        // An unpublished ACTIVE snapshot has never started its combat budget.
        if (controlCommitPending() && state != null && state.clock() != null
                && state.clock().phase() == WorldBossClock.Phase.ACTIVE && clockTicker != null)
            clockTicker = new WorldBossTicker(System.nanoTime());
        if (controlCommit != null) controlCommit.cancel();
        controlCommit = null; controlOperation = null;
    }
    private static void queueControlCommit(MinecraftServer current, WorldBossState saved, String operation,
            WorldBossClock.Phase phase, Runnable publish) {
        if (controlCommit != null) throw new IllegalStateException("A World Boss control continuation already exists");
        UUID event = saved.bossId();
        controlOperation = operation;
        stateDirty = true;
        controlCommit = new WorldBossControlCommit(new WorldBossControlCommit.Store() {
            public boolean available() { return !saved.checkpointPending(); }
            public long checkpoint() throws java.io.IOException {
                checkpointAdmissions(saved);
                long receipt = saved.checkpointAsync(current);
                stateDirty = false;
                if (clockTicker != null) clockTicker.checkpointed();
                return receipt;
            }
            public boolean acknowledged(long receipt) { return saved.checkpointAcknowledged(receipt); }
        }, () -> state == saved && event.equals(saved.bossId()) && saved.active() && !saved.isQuarantined()
                && saved.closeReason() == null && saved.clock() != null && saved.clock().phase() == phase
                && saved.pool() != null && saved.pool().current() > 0 && !saved.pool().isDefeated()
                && (phase == WorldBossClock.Phase.HELD || com.cobbleraids.showdown.WorldBossSimulator.ready()), publish,
                () -> RaidLog.warn("World Boss {} pending {} superseded; its native publication was cancelled", event, operation),
                failure -> {
                    RaidLog.error("World Boss " + event + " control " + operation + " could not complete", failure);
                    if (state == saved && event.equals(saved.bossId()) && saved.active() && saved.clock() != null) {
                        var reason = failure instanceof java.io.IOException || saved.isQuarantined()
                                ? WorldBossClock.Reason.STORAGE_FAILURE : WorldBossClock.Reason.PRESENTATION_FAILURE;
                        saved.clock(saved.clock().hold(reason));
                        stateDirty = true; barDirty = true; abortDuels(); CHAIN.clear();
                    }
                });
    }
    static void sampleMechanicsClock(MinecraftServer server) { advanceClock(server); }
    /** Teleport attempts cancel unfinished ward work, including same-position corrections. */
    public static void invalidateWardAction(UUID player) { WorldBossWardService.invalidate(player); }
    static void holdMechanics(MinecraftServer server, WorldBossClock.Reason reason) {
        if (state != null && state.clock() != null) state.clock(state.clock().hold(reason));
        mechanicsChanged(); flush(server); abortDuels();
    }

    public static void simulatorUnavailable(MinecraftServer current) {
        if (state != null && state.active() && state.clock() != null && state.clock().phase() != WorldBossClock.Phase.HELD) holdMechanics(current, WorldBossClock.Reason.SIMULATOR_FAILURE);
    }

    public static boolean simulatorStarted(UUID encounter) {
        UUID player = DUEL_PLAYER.get(encounter); Duel duel = player == null ? null : DUELS.get(player);
        if (duel == null || !duel.encounterId().equals(encounter) || state == null || !combatOpen(state)
                || !duel.bossId().equals(state.bossId()) || admissions == null || !EncounterService.isActive(encounter)) return false;
        admissions.confirm(duel.admission()); mechanicsChanged(); return true;
    }

    public static void simulatorStartRejected(UUID encounter) {
        UUID player = DUEL_PLAYER.get(encounter); Duel duel = player == null ? null : DUELS.get(player);
        if (duel != null && duel.encounterId().equals(encounter)) abortDuel(player, duel);
        else if (EncounterService.isActive(encounter)) EncounterService.abort(encounter);
    }

    private static String start(MinecraftServer current, ServerPlayer near, boolean testMode, WorldBossCampaign.Occurrence occurrence) {
        if (!current.isSameThread()) throw new IllegalStateException("World Boss control requires the server thread");
        if (WorldBossPlayerAutosaves.failed()) return "player autosave storage failed; check player data and restart before starting another event";
        if (WorldBossWorldSaves.failed()) return "world NBT autosave storage failed; check world data and restart before starting another event";
        if (WorldBossAdvancementSaves.failed()) return "native advancement saving failed; check advancements and restart before starting another event";
        if (!com.cobbleraids.showdown.WorldBossSimulator.ready()) return "World Boss simulator is " + com.cobbleraids.showdown.WorldBossSimulator.status() + ".";
        WorldBossConfig config = WorldBossConfigManager.get();
        if (!config.enabled()) return "the World Boss system is turned off (enabled=false in "
                + WorldBossConfigManager.path().getFileName() + ")";
        if (state == null) return "the World Boss system has not started yet";
        if (state.isQuarantined()) return "saved World Boss data needs operator repair; no event can be started";
        if (state.active()) return "a World Boss is already active";
        if (!DUELS.isEmpty()) return "the previous World Boss's duels are still finishing";
        if (testMode && state.campaign().occurrences().values().stream().anyMatch(value -> value.status() == WorldBossCampaign.Status.PREPARING
                || value.status() == WorldBossCampaign.Status.OPEN)) return "a pending live occurrence reserves the event slot";
        if (!testMode) {
            if (occurrence == null || occurrence.status() != WorldBossCampaign.Status.PREPARING
                    || !state.campaign().require(occurrence.key()).equals(occurrence) || state.campaign().active() == null
                    || !state.campaign().activeSeason().equals(occurrence.season())) return "a reserved active-season occurrence is required";
            long observed = System.currentTimeMillis();
            if (observed < occurrence.slot().preparationAt().toEpochMilli()
                    || observed > occurrence.slot().preparationAt().plus(WorldBossSchedule.START_TOLERANCE).toEpochMilli()) return "preparation could not start within its tick tolerance";
            String problem = livePreflight(current, config);
            if (problem != null) return problem;
        }
        List<String> loaded = new ArrayList<>();
        for (String id : config.raidDefinitions()) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null && RaidDefinitionRegistry.get(parsed) != null) loaded.add(id);
            else RaidLog.warn("World Boss config names raid definition {}, which is not loaded; skipping it.", id);
        }
        String pickedId = WorldBossRules.pickDefinition(loaded, state.definitionId(), ThreadLocalRandom.current());
        if (pickedId == null) return "none of the configured raid definitions is loaded: " + config.raidDefinitions();
        RaidDefinition definition = RaidDefinitionRegistry.get(ResourceLocation.parse(pickedId));
        String fingerprint = WorldBossOpeningRules.fingerprint(config, definition, calibrationRuntime());
        WorldBossCalibration calibration = config.calibrations().stream().filter(value -> value.definition().equals(pickedId)
                && value.rulesFingerprint().equals(fingerprint)).findFirst().orElse(null);
        if (calibration == null && testMode) {
            try { calibration = new WorldBossCalibration(pickedId, fingerprint, new java.math.BigDecimal(Float.toString(config.damagePerPlayerMinute())), "TEST_ONLY_UNCALIBRATED"); }
            catch (RuntimeException invalid) { return "test reference DPM is outside the supported numerical envelope"; }
        }
        if (calibration == null) return "a measured calibration for the selected boss/rules is required";
        WorldBossOpeningRules openingRules;
        try { openingRules = WorldBossOpeningRules.capture(config, calibration); }
        catch (RuntimeException invalid) { return "invalid battle rules: " + invalid.getMessage(); }
        // With spawn markers placed in an arena, the boss stands at one of them; otherwise it appears at a random surface spot near a player.
        ArenaPick arenaPick = config.arena().enabled() ? pickArena(current) : null;
        if (arenaPick != null && arenaPick.problem() != null) return arenaPick.problem();
        boolean arenaMode = arenaPick != null;
        ServerLevel level;
        BlockPos spot;
        if (arenaMode) {
            level = arenaPick.level();
            spot = arenaPick.pos();
        } else {
            ResourceLocation dimensionId = ResourceLocation.tryParse(config.spawnDimension());
            level = dimensionId == null ? null : current.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (level == null) return "dimension " + config.spawnDimension() + " does not exist";
            ServerPlayer anchor = near != null && near.level() == level ? near : randomPlayerIn(level);
            if (anchor == null) return "nobody is online in " + config.spawnDimension() + " to place the boss around";
            spot = findLand(level, anchor.blockPosition(), config);
            if (spot == null) return "no suitable surface" + (LandClaimGuard.active() && CobbleRaidsConfigManager.get().landClaims().avoid() ? " outside land claims" : "")
                    + " was found after " + config.spawnAttempts() + " attempts";
        }

        long now = System.currentTimeMillis();
        float pool = 1f; // Preparation placeholder; no combat opens before the fixed roster/health commit.
        WorldBossState s = state;
        s.begin(occurrence == null ? UUID.randomUUID() : occurrence.eventId(), definition.id().toString(), level.dimension().location().toString(),
                spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, now, 0L,
                new WorldBossPool(pool), arenaMode);
        if (arenaMode) s.configureAudience(config.arena().radius());
        s.clock(WorldBossClock.begin(occurrence == null ? config.joinCountdownSeconds() * 1000L : Math.max(0, occurrence.combatAtMillis() - now), now));
        if (testMode) s.markTestMode();
        s.stageOpeningRules(openingRules);
        var pylons = ArenaMarkers.get(current).list(ArenaMarkers.Kind.WARD_PYLON);
        if (pylons.size() == 1 && pylons.getFirst().dimension().equals(s.dimension()) && safeMarker(level, pylons.getFirst().pos(), ArenaMarkers.Kind.WARD_PYLON)) {
            var p = pylons.getFirst(); s.configureWardPylon(new WorldBossWards.Pylon(p.dimension(), p.pos().getX(), p.pos().getY(), p.pos().getZ()));
            var markers = ArenaMarkers.get(current);
            if (relayLayoutProblem(level, markers, p, s.x(), s.y(), s.z(), WorldBossRules.zoneRadius(s, config)) == null) {
                s.configureWardRelay(new WorldBossRelay.Route(wardPoint(markers.list(ArenaMarkers.Kind.RELAY_CACHE).getFirst()), wardPoint(markers.list(ArenaMarkers.Kind.RELAY_CHECKPOINT).getFirst())));
            }
        }
        if (occurrence != null) s.bindOccurrence(occurrence.key());
        WorldBossWardService.started(s);
        clockTicker = new WorldBossTicker(System.nanoTime());
        admissions = new WorldBossAdmission(s.bossId(), 50, BREATHER_MILLIS, s.pausedPlayers());
        recoveryAdmission = null; admissionRevision = -1;
        DUELS.clear();
        DUEL_PLAYER.clear();
        CHAIN.clear();
        PRESENT.clear();
        combatAnnounced = false;
        START_PACER.reset();
        LAST_DUEL_START.clear();
        UUID preparingEvent = s.bossId();
        queueControlCommit(current, s, "start", WorldBossClock.Phase.PREPARING, () -> {
            String problem = completeStart(current, s, preparingEvent, definition, level, spot, arenaMode, occurrence, testMode, pool);
            if (problem != null) RaidLog.warn("World Boss {} acknowledged preparation could not open: {}", s.bossId(), problem);
        });
        return null;
    }

    private static boolean sameActiveEvent(WorldBossState saved, UUID event) {
        return state == saved && saved.active() && event.equals(saved.bossId());
    }
    private static boolean preparing(WorldBossState saved, UUID event) {
        return sameActiveEvent(saved, event) && !saved.isQuarantined() && saved.closeReason() == null
                && saved.clock() != null && saved.clock().phase() == WorldBossClock.Phase.PREPARING;
    }
    private static String completeStart(MinecraftServer current, WorldBossState s, UUID event, RaidDefinition definition,
            ServerLevel level, BlockPos spot, boolean arenaMode, WorldBossCampaign.Occurrence occurrence, boolean testMode, float pool) {
        if (occurrence != null && System.currentTimeMillis() > occurrence.slot().preparationAt().plus(WorldBossSchedule.START_TOLERANCE).toEpochMilli()) {
            end(current, "The gathering could not be prepared on time. This World Boss occurrence was skipped.");
            return "preparation acknowledgement exceeded its allowed delay";
        }
        DUEL_SPACE.clear();
        refreshArenaProtection(current);
        try {
            retainArenaChunks(current);
            if (!preparing(s, event)) return "the occurrence changed while retaining its arena";
            spawnShowpiece(current);
        } catch (RuntimeException ex) {
            RaidLog.error("World Boss {} started but its showpiece could not be spawned", s.bossId(), ex);
            if (sameActiveEvent(s, event)) end(current, "The boss could not be prepared safely. This occurrence was skipped.");
            return "the standing boss could not be spawned";
        }
        if (!preparing(s, event)) return "the occurrence changed during standing boss creation";
        if (showpieceId == null) {
            end(current, "The boss could not be prepared safely. This occurrence was skipped.");
            return "the standing boss is unavailable";
        }
        ensureBar(current);
        if (occurrence != null && System.currentTimeMillis() > occurrence.slot().preparationAt().plus(WorldBossSchedule.START_TOLERANCE).toEpochMilli()) {
            end(current, "The gathering could not be prepared on time. This World Boss occurrence was skipped.");
            return "preparation setup exceeded its allowed tick delay";
        }
        if (!testMode) announceStart(current, definition, level, spot, arenaMode);
        if (!preparing(s, event)) return "the occurrence changed during its announcement";
        RaidLog.info("World Boss {} started{}: {} at {} {} {} in {}, pool {}", s.bossId(), arenaMode ? " at an arena marker" : "", definition.id(),
                spot.getX(), spot.getY(), spot.getZ(), level.dimension().location(), Math.round(pool));
        clockTicker = new WorldBossTicker(System.nanoTime());
        return null;
    }

    /** What {@link #stop} did: whether an event was running, and how many leftover bosses it removed at the boss's last spot. */
    public record StopResult(boolean wasActive, int swept) {}

    /**
     * Ends the active World Boss without a defeat, and removes every boss standing at its last spot: not only the showpiece this server is
     * tracking but copies an earlier run left behind (an old build, a crash, a restart that respawned the showpiece). Run it with nothing
     * active to clear leftovers from an event that already ended.
     */
    public static StopResult stop(MinecraftServer current) {
        if (state == null) return new StopResult(false, 0);
        cancelControlCommit();
        if (state.active() && state.pool() != null && (state.pool().isDefeated() || state.pool().current() <= 0)) {
            if (state.pool().isDefeated()) finishDefeat(current); else handleDefeat(current);
            return new StopResult(true, 0);
        }
        if (state.active()) return new StopResult(true, end(current, "The World Boss has been called off."));
        return new StopResult(false, sweepLeftovers(current));
    }

    /** Ends the event; returns how many leftover bosses it swept up at the boss's spot besides the showpiece. */
    private static int end(MinecraftServer current, String message) {
        WorldBossState s = state;
        if (s.merit() != null) {
            if (s.closeReason() == null) s.prepareClose(s.clock() != null && s.clock().phase() == WorldBossClock.Phase.EXPIRED ? "TIMEOUT" : "CANCELLED", message);
            if (admissions != null) admissions.close();
            abortDuels();
            WorldBossMusic.stopAll(current);
            if (!flush(current)) return 0;
            if (!s.rewardsPaid()) {
                try { if (!s.testMode()) RaidPlayerRecords.creditWorldBossOnce(current, s.recoveryInstallation(s.bossId(), s.rewardPlan()), s.bossId(), s.rewardPlan()); }
                catch (java.io.IOException | IllegalStateException failure) { RaidLog.error("World Boss {} close settlement is pending", s.bossId(), failure); return 0; }
                s.markRewardsPaid();
                if (!flush(current)) return 0;
                s.rewardPlan().forEach((id, amount) -> { var player = current.getPlayerList().getPlayer(id); if (player != null) say(player,
                        "Your qualified " + s.closeReason().toLowerCase(java.util.Locale.ROOT) + " reward of " + amount + " Raid Points is secured. Overflow can be claimed with /cr worldboss claim.", ChatFormatting.AQUA); });
            }
            message = s.closeMessage();
        }
        WorldBossPool pool = s.pool();
        UUID bossId = s.bossId();
        String definitionId = s.definitionId();
        Map<UUID, Float> damage = pool == null ? Map.of() : Map.copyOf(pool.contributions());
        float total = pool == null ? 0f : pool.max();
        if (admissions != null) admissions.close();
        abortDuels();
        var priorCampaign = s.campaign();
        var priorScenes = s.scenes();
        long priorEnded = s.lastEndedAtMillis();
        var event = s.campaignEvent();
        if (event != null && !event.terminal()) s.campaign(s.campaign().transition(event.key(), event.eventId(),
                event.status() == WorldBossCampaign.Status.OPEN ? WorldBossCampaign.Status.CLOSED : WorldBossCampaign.Status.SKIPPED,
                s.closeReason() == null ? "ESCAPE_CANCELLATION_OR_INTERRUPTED_PREPARATION" : s.closeReason(), Math.max(System.currentTimeMillis(), s.campaign().wallHighWaterMillis())));
        if (!s.testMode() && "TIMEOUT".equals(s.closeReason()) && s.campaignEvent() != null && s.campaignEvent().status() == WorldBossCampaign.Status.CLOSED) s.recordScene(WorldBossSceneScript.Kind.ESCAPE, Math.max(System.currentTimeMillis(), s.campaign().wallHighWaterMillis()));
        s.finish(System.currentTimeMillis());
        if (!flush(current) && s.merit() != null) { s.campaign(priorCampaign); s.scenes(priorScenes); s.keepSettlementOpen(priorEnded); stateDirty = true; return 0; }
        if (!s.testMode() && "TIMEOUT".equals(s.closeReason())) WorldBossScenes.dispatch(current, new WorldBossSceneLedger.Key(s.bossId(), WorldBossSceneScript.Kind.ESCAPE));
        removeShowpiece(current);
        int swept = sweepLeftovers(current);
        WorldBossArenaChunks.release();
        for (UUID warped : s.travel().tickets().keySet()) {
            ServerPlayer visitor = current.getPlayerList().getPlayer(warped);
            if (visitor != null) say(visitor, "The fight is over. /cobbleraids worldboss return takes you back.", ChatFormatting.YELLOW);
        }
        clearBar();
        if (!s.testMode()) {
            broadcast(current, Component.literal("[World Boss] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal(message).withStyle(ChatFormatting.YELLOW)));
            for (WorldBossListener listener : LISTENERS) {
                RaidFaultBarrier.guard("worldboss:listener-ended",
                        () -> listener.onEnded(bossId, definitionId, damage, total));
            }
        }
        CHAIN.clear();
        PRESENT.clear();
        RaidLog.info("World Boss {} ended undefeated: {} damage of {}; {} leftover boss(es) swept", bossId,
                Math.round(pool == null ? 0f : pool.totalDamage()), Math.round(total), swept);
        return swept;
    }

    // ---- joining and duelling ----

    /** Starts a duel for the player and begins their chain. Returns null on success, else why not. */
    public static String join(ServerPlayer player) {
        if (arenaRecovery != null) return "The World Boss arena is still restoring. Please wait for the regroup.";
        if (controlCommitPending()) return "World Boss preparation is still being saved. Combat will open after acknowledgement.";
        WorldBossConfig config = WorldBossConfigManager.get();
        WorldBossState s = state;
        if (s == null || s.isQuarantined() || !s.active() || s.pool() == null) return "There is no available World Boss right now.";
        if (s.closeReason() != null) return "This encounter is closing and its rewards are being secured.";
        if (s.pool().isDefeated() || s.pool().current() <= 0f) return "The World Boss has already fallen.";
        if (DUELS.containsKey(player.getUUID())) return "You are already in a duel.";
        if (s.openingRules() == null && !WorldBossRules.hasRoom(fighters(s), player.getUUID()))
            return "The World Boss already has " + WorldBossRules.MAX_PLAYERS + " challengers.";
        String why = cannotDuel(player, s, config);
        if (why != null) return why;
        if (s.resume(player.getUUID()) && !flush(player.server)) {
            s.pause(player.getUUID());
            return "Your change could not be acknowledged. Combat is held until storage recovers; tell staff.";
        }
        if (admissions == null) return "The World Boss admission queue is not available.";
        admissions.resume(player.getUUID(), true);
        int gatherLeft = joinSecondsLeft(s, config);
        if (gatherLeft > 0) {
            barDirty = true;
            say(player, "You are in. The fight opens in " + gatherLeft + " s, so everyone can join.", ChatFormatting.LIGHT_PURPLE);
            return null;
        }
        int position = admissions.queuePosition(player.getUUID());
        if (position > 0) say(player, (s.testMode() ? "TEST encounter: no live rewards. " : "")
                + "Queued for the World Boss. Ready queue position: " + position + ".", ChatFormatting.LIGHT_PURPLE);
        return null;
    }

    /** Seconds left before duels open for this boss (0 once open). Counted from when the boss appeared, so a restart does not restart it. */
    private static int joinSecondsLeft(WorldBossState s, WorldBossConfig config) {
        if (s.clock() != null) return s.clock().phase() == WorldBossClock.Phase.PREPARING
                ? (int) ((s.clock().phaseRemainingMillis() + 999) / 1000) : 0;
        return WorldBossRules.joinSecondsLeft(s.startedAtMillis(), config.joinCountdownSeconds(), System.currentTimeMillis());
    }

    /** Leaves the chain: no more automatic duels. Damage already dealt is kept. */
    public static boolean leave(ServerPlayer player) {
        WorldBossArrivals.cancel(player.getUUID());
        WorldBossState s = state;
        if (s == null || !s.active()) return false;
        if (recoveryAdmission != null) recoveryAdmission = recoveryAdmission.without(player.getUUID());
        boolean waiting = CHAIN.remove(player.getUUID()) != null;
        waiting |= admissions != null && admissions.queuePosition(player.getUUID()) > 0;
        waiting |= DUELS.containsKey(player.getUUID());
        if (admissions != null) admissions.pause(player.getUUID());
        s.pause(player.getUUID());
        if (s.isDirty()) flush(player.server);
        return waiting || s.isPaused(player.getUUID());
    }

    public static boolean controlIsSaved() { return state != null && !state.isDirty(); }

    private static String cannotDuel(ServerPlayer player, WorldBossState s, WorldBossConfig config) {
        if (arrivalPending(player.getUUID())) return "Your arena arrival is queued; stand still or cancel it first.";
        if (!player.isAlive() || player.isSpectator()) return "You cannot duel right now.";
        if (player.isPassenger() || player.isVehicle()) return "Dismount and stand on clear ground before joining a duel.";
        if (!Float.isFinite(player.getBbWidth()) || !Float.isFinite(player.getBbHeight()) || player.getBbWidth() > 4 || player.getBbHeight() > 8) return "Your player dimensions do not fit the supported battle space. Tell an administrator.";
        if (!inRange(player, s, config)) return "You are too far from the World Boss (limit "
                + Math.round(WorldBossRules.zoneRadius(s, config)) + " blocks).";
        if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) {
            return "Finish your current battle first.";
        }
        if (partySize(player) == 0) return "Bring at least one Pokemon to join the World Boss.";
        if (s.clock() != null && s.clock().phase() != WorldBossClock.Phase.PREPARING && admissions != null && !admissions.eligibleAt(player.getUUID(), s.clock().remainingMillis())) return "New participant admission closes during the final active minute; previously admitted players may continue.";
        return null;
    }

    private static String startDuel(ServerPlayer player, WorldBossState s, WorldBossConfig config,
                                    WorldBossAdmission.Token reservation) {
        try { return startDuelReserved(player, s, config, reservation); }
        catch (RuntimeException failure) {
            RaidLog.error("World Boss {} duel preparation failed for {}", s.bossId(), player.getUUID(), failure);
            var binding = DUELS.get(player.getUUID());
            if (binding != null && binding.admission().equals(reservation)) abortDuel(player.getUUID(), binding);
            if (!EncounterService.isActive(reservation.encounterId()) && admissions != null) admissions.failedStart(reservation, System.currentTimeMillis(), 5_000L);
            return "Your duel could not start safely. Tell an administrator; admission will retry.";
        }
        finally { if (!EncounterService.isActive(reservation.encounterId())) DUEL_SPACE.release(reservation); }
    }

    private static String startDuelReserved(ServerPlayer player, WorldBossState s, WorldBossConfig config,
                                            WorldBossAdmission.Token reservation) {
        advanceClock(player.server);
        if (!combatOpen(s)) return "World Boss combat is not open.";
        if (s.clock() != null && !admissions.eligibleAt(player.getUUID(), s.clock().remainingMillis())) return "New participant admission is closed.";
        if (!checkOpeningRules(player.server)) return "World Boss content changed; combat is held for staff.";
        ResourceLocation definitionId = ResourceLocation.tryParse(s.definitionId());
        if (definitionId == null) return "The World Boss definition is invalid.";
        long nowMillis = System.currentTimeMillis();
        UUID encounterId = reservation.encounterId();
        var placement = WorldBossDuelPlacement.find(player, DUEL_SPACE);
        if (placement == null || !DUEL_SPACE.reserve(reservation, player.serverLevel().dimension().location().toString(), placement.lane()))
            return "Your duel needs more clear, level space. Spread out or recall nearby Pokemon; you will retry automatically.";
        Vec3 position = placement.boss();
        EncounterPolicy policy = EncounterPolicy.none().withCarryover(true, true).withAscensionEffects(true);
        WorldBossOpeningRules rules = s.openingRules();
        EncounterRequest request = new EncounterRequest(OWNER, encounterId, List.of(player), definitionId,
                player.serverLevel(), position, rules == null ? config.duelBossLevel() : rules.duelLevel(),
                OptionalLong.of(rules == null ? config.duelBossHealth() : rules.duelHealth()),
                policy, rules == null ? EncounterRules.none() : EncounterRules.none().withItems(false));
        StartResult result;
        if (admissions == null || !admissions.started(reservation)) return "Your battle admission was cancelled.";
        if (s.merit() != null) { s.merit().register(encounterId, player.getUUID()); s.touch(); stateDirty = true; }
        Set<UUID> partyIds = new HashSet<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) partyIds.add(pokemon.getUuid());
        Duel binding = new Duel(encounterId, s.bossId(), player.server.overworld().getGameTime(), reservation, Set.copyOf(partyIds));
        DUELS.put(player.getUUID(), binding);
        DUEL_PLAYER.put(encounterId, player.getUUID());
        try {
            healParty(player, binding.partyIds());
            advanceClock(player.server);
            if (!combatOpen(s) || !checkOpeningRules(player.server) || s.wards() != null && s.wards().active() != null
                    || s.clock() != null && !admissions.eligibleAt(player.getUUID(), s.clock().remainingMillis())) {
                DUELS.remove(player.getUUID(), binding);
                DUEL_PLAYER.remove(encounterId, player.getUUID());
                admissions.failedStart(reservation, System.currentTimeMillis(), 5_000L);
                return "World Boss combat closed before your duel could begin.";
            }
            result = com.cobbleraids.spawn.RaidBossSpawner.withoutMoves(rules == null ? config.bossBannedMoves() : rules.bossBannedMoves(),
                    () -> EncounterService.startChecked(request, DUEL_LISTENER, boss -> {
                        var validation = WorldBossDuelPlacement.validate(player, DUEL_SPACE.lease(reservation), position, boss, config.duelBossScale());
                        if (validation == WorldBossDuelPlacement.Validation.UNSUPPORTED_BODY) {
                            RaidLog.error("World Boss {} native boss dimensions exceed the supported placement footprint: {} x {}", s.bossId(), boss.getBbWidth(), boss.getBbHeight());
                            holdMechanics(player.server, WorldBossClock.Reason.CALIBRATION_INVALID);
                        }
                        return validation == WorldBossDuelPlacement.Validation.CLEAR;
                    }));
        } catch (RuntimeException ex) {
            abortDuel(player.getUUID(), binding);
            if (!EncounterService.isActive(encounterId)) admissions.failedStart(reservation, nowMillis, 5_000L);
            RaidLog.error("World Boss duel for {} could not start", player.getGameProfile().getName(), ex);
            return "The duel could not start. Tell an administrator.";
        }
        if (result instanceof StartResult.Refused refused) {
            DUELS.remove(player.getUUID(), binding);
            DUEL_PLAYER.remove(encounterId, player.getUUID());
            admissions.failedStart(reservation, System.currentTimeMillis(), 5_000L);
            return refused.reason();
        }
        if (!com.cobbleraids.showdown.WorldBossSimulator.queuedEncounter(encounterId)) admissions.confirm(reservation);
        advanceClock(player.server);
        if (!combatOpen(s)) {
            abortDuel(player.getUUID(), binding);
            if (!EncounterService.isActive(encounterId)) admissions.failedStart(reservation, System.currentTimeMillis(), 5_000L);
            return "World Boss combat closed while your duel was starting.";
        }
        if (rules == null) {
            LAST_DUEL_START.put(player.getUUID(), nowMillis);
            int active = WorldBossRules.activePlayers(LAST_DUEL_START, nowMillis, config.activeWindowSeconds() * 1000L);
            s.pool().growTo(WorldBossScaling.poolSize(config.poolBase(), config.damagePerPlayerMinute(), active,
                    config.minutesPerPlayer(), config.maxMinutes(), config.poolMax()));
        }
        s.touch();
        stateDirty = true;
        barDirty = true;
        PokemonEntity duelBoss = EncounterService.bossOf(encounterId);
        if (duelBoss != null) duelBoss.getPokemon().setScaleModifier(config.duelBossScale());
        if (duelBoss != null && config.look().duelBossInvisible()) hideDuelBoss(duelBoss);
        // Written for the one case that matters: a duel that is followed by a frozen server leaves this line as the last thing it said.
        RaidLog.debug("World Boss duel {}: player {} at ({}, {}, {}), boss asked for ({}, {}, {}) and now at {}, size x{}, party of {}",
                encounterId, player.getGameProfile().getName(), Math.round(player.getX()), Math.round(player.getY()), Math.round(player.getZ()),
                Math.round(position.x), Math.round(position.y), Math.round(position.z),
                duelBoss == null ? "?" : Math.round(duelBoss.getX()) + ", " + Math.round(duelBoss.getY()) + ", " + Math.round(duelBoss.getZ()),
                config.duelBossScale(), partySize(player));
        say(player, "A duel against the World Boss begins! Stay in place until your duel ends.", ChatFormatting.LIGHT_PURPLE);
        player.sendSystemMessage(Component.literal("[Pause Battles]").withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr worldboss pause"))));
        return null;
    }

    /**
     * Where the duel's private boss stands: the first of {@link WorldBossDuelSpot#candidates} (6 blocks and more round the player, in front first)
     * that has loaded ground within a few blocks of the player's height and nothing solid or watery at its feet. Never the player's own spot: a boss
     * on top of the player makes Cobblemon divide by a zero distance, and the NaN that follows freezes the server thread in a block raycast
     * (found in two thread dumps). With no good ground at all (the player is flying, or in a cave) the nearest candidate at the player's own
     * height is used, which is still at least 6 blocks away.
     */
    public static Vec3 duelPosition(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        var spots = WorldBossDuelSpot.candidates(player.getX(), player.getZ(), look.x, look.z);
        for (double[] spot : spots) {
            BlockPos column = BlockPos.containing(spot[0], player.getY(), spot[1]);
            if (!level.hasChunkAt(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            if (Math.abs(y - player.getY()) > 6.0) continue;   // a cliff or a roof, not somewhere to stand
            BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) continue;
            return new Vec3(spot[0], y, spot[1]);
        }
        double[] fallback = spots.isEmpty() ? null : spots.get(0);
        return fallback == null ? null : new Vec3(fallback[0], player.getY(), fallback[1]);
    }

    private static Set<UUID> fighters(WorldBossState s) {
        Set<UUID> all = new HashSet<>(s.pool().contributions().keySet());
        all.addAll(DUELS.keySet()); all.addAll(CHAIN.keySet()); return all;
    }

    private static final EncounterListener DUEL_LISTENER = new EncounterListener() {
        @Override
        public void onDamage(UUID encounterId, UUID playerId, float applied) {
            strike(encounterId, playerId, applied, 0, 0);
        }

        @Override
        public void onDamageAction(UUID encounterId, UUID playerId, float applied, int turn, long sequence) {
            strike(encounterId, playerId, applied, turn, sequence);
        }

        @Override
        public void onEnded(EncounterResult result) {
            duelEnded(result);
        }
    };

    private static void strike(UUID encounterId, UUID playerId, float applied, int turn, long sequence) {
        WorldBossState s = state;
        MinecraftServer current = server;
        if (s == null || current == null || !s.active() || s.pool() == null) return;
        advanceClock(current);
        if (!combatOpen(s)) return;
        if (!checkOpeningRules(current)) return;
        Duel binding = DUELS.get(playerId);
        if (binding == null || !binding.encounterId().equals(encounterId) || !binding.bossId().equals(s.bossId())) return;
        if (admissions == null || !admissions.owns(binding.admission())) return;
        float dealt;
        if (s.merit() != null) {
            s.wards().advance(s.clock().remainingMillis(), WorldBossWardService.ready(current));
            var action = s.damageAction(encounterId, playerId, turn, sequence, applied, WorldBossWardService.ready(current));
            if (action.evidence() == WorldBossMerit.Evidence.DUPLICATE) return;
            if (action.evidence() == WorldBossMerit.Evidence.INVALID) { holdMechanics(current, WorldBossClock.Reason.ACTION_EVIDENCE_UNAVAILABLE); return; }
            admissions.confirm(binding.admission());
            dealt = action.applied();
            if (action.qualifiedNow()) {
                if (!flush(current)) { holdMechanics(current, WorldBossClock.Reason.STORAGE_FAILURE); return; }
                var player = current.getPlayerList().getPlayer(playerId);
                if (player != null) say(player, "Merit 100/100. You are qualified for this event's base reward.", ChatFormatting.AQUA);
            }
        } else dealt = s.pool().strike(playerId, applied);
        s.touch();
        stateDirty = true;
        if (dealt <= 0f) return; // A valid sub-milli-HP carry is still persisted.
        barDirty = true;
        if (s.pool().current() <= 0f) handleDefeat(current);
    }

    private static void duelEnded(EncounterResult result) {
        com.cobbleraids.raid.AscensionLibArming.endEncounter(result.encounterId());   // the duel's own Threat declaration; harmless if it had none
        UUID playerId = DUEL_PLAYER.remove(result.encounterId());
        if (playerId == null) return;
        Duel binding = DUELS.get(playerId);
        if (binding == null || !binding.encounterId().equals(result.encounterId()) || !DUELS.remove(playerId, binding)) return;
        DUEL_SPACE.release(binding.admission());
        WorldBossState s = state;
        MinecraftServer current = server;
        ServerPlayer player = current == null ? null : current.getPlayerList().getPlayer(playerId);
        boolean keep = s != null && current != null && s.active() && binding.bossId().equals(s.bossId())
                && s.closeReason() == null && s.pool() != null && !s.pool().isDefeated() && !s.isPaused(playerId)
                && (s.clock() == null || s.clock().combatOpen())
                && player != null && inRange(player, s, WorldBossConfigManager.get()) && (s.openingRules() != null || WorldBossConfigManager.get().chain());
        if (admissions != null) admissions.ended(binding.admission(), System.currentTimeMillis(), keep);
        if (player != null) healCompletedParty(player, binding.partyIds());
        if (keep) CHAIN.put(playerId, System.currentTimeMillis());
    }

    private static void tickDuels(MinecraftServer current, WorldBossConfig config) {
        long limitTicks = (state != null && state.openingRules() != null ? state.openingRules().duelTimeoutSeconds() : config.duelTimeLimitSeconds()) * (long) SECOND;
        for (Map.Entry<UUID, Duel> entry : DUELS.entrySet()) {
            if (!EncounterService.isActive(entry.getValue().encounterId())) {
                releaseEndedDuel(entry.getKey(), entry.getValue());
                continue;
            }
            long now = current.overworld().getGameTime();
            if (now - entry.getValue().startedTick() > limitTicks || state == null || !state.active()
                    || state.clock() != null && !state.clock().combatOpen()) {
                abortDuel(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void tickDuelSpace(MinecraftServer current) {
        for (var entry : List.copyOf(DUELS.entrySet())) {
            var duel = entry.getValue();
            if (!EncounterService.isActive(duel.encounterId())) { releaseEndedDuel(entry.getKey(), duel); continue; }
            var player = current.getPlayerList().getPlayer(entry.getKey());
            // A disconnected owner keeps the reserved space through the existing scoped reconnect grace.
            if (player != null && !WorldBossDuelPlacement.stillInside(player, DUEL_SPACE.lease(duel.admission()), EncounterService.bossOf(duel.encounterId()))) {
                abortDuel(entry.getKey(), duel);
                say(player, "Your duel ended because its reserved space changed. Stand on clear ground; ready players retry after the breather.", ChatFormatting.YELLOW);
            }
        }
    }

    private static void tickAdmissions(MinecraftServer current) {
        WorldBossState s = state;
        WorldBossAdmission queue = admissions;
        if (s == null || queue == null || !s.active() || s.pool() == null || s.pool().isDefeated()) return;
        WorldBossConfig config = WorldBossConfigManager.get();
        long now = System.currentTimeMillis();
        if (s.clock() == null && s.endsAtMillis() > 0 && now >= s.endsAtMillis()) return;
        List<UUID> readyOrder = new ArrayList<>();
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            boolean inside = inRange(player, s, config);
            if (inside) {
                present.add(id);
                if (!PRESENT.contains(id)) {
                    player.sendSystemMessage(Component.literal(s.isPaused(id)
                            ? "World Boss battles are paused. [Resume Battles]"
                            : "World Boss: you are enrolled automatically while ready inside the bubble. [Pause Battles]")
                            .withStyle(style -> style.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND, s.isPaused(id) ? "/cr worldboss resume" : "/cr worldboss pause"))));
                }
            }
            if (player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isVehicle() && !arrivalPending(id) && inside && !s.isPaused(id)
                    && partySize(player) > 0 && Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) == null) {
                if (s.clock() == null || s.clock().phase() == WorldBossClock.Phase.PREPARING || queue.eligibleAt(id, s.clock().remainingMillis())) readyOrder.add(id);
                else if (tickCounter % SECOND == 0) player.displayClientMessage(Component.literal("New participant admission is closed for the final minute. Earlier admitted players may continue; you can observe.").withStyle(ChatFormatting.AQUA), true);
            }
            if (recoveryAdmission != null && !readyOrder.contains(id) && !DUELS.containsKey(id)) recoveryAdmission = recoveryAdmission.without(id);
        }
        if (recoveryAdmission != null) for (var previous : PRESENT) if (!present.contains(previous)) recoveryAdmission = recoveryAdmission.without(previous);
        PRESENT.clear();
        PRESENT.addAll(present);
        queue.reconcileReady(readyOrder);
        if (joinSecondsLeft(s, config) > 0) return;
        if (!combatOpen(s)) return;
        if (s.wards() != null && s.wards().active() != null) { queue.admitWardWaiters(2, now, new HashSet<>(readyOrder)); return; }
        if (!combatAnnounced) {
            combatAnnounced = true;
            if (!s.testMode() && config.joinCountdownSeconds() > 0) broadcast(current,
                    Component.literal("[World Boss] The fight is open! Ready players inside the bubble enter automatically.")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        Set<UUID> ready = new HashSet<>(readyOrder);
        for (WorldBossAdmission.Token reservation : queue.reserve(START_PACER.budget(System.nanoTime()), now, true, ready)) {
            if (s.wards() != null && s.wards().active() != null) {
                if (s.clock() == null || queue.eligibleAt(reservation.playerId(), s.clock().remainingMillis())) queue.defer(reservation, now); else queue.refused(reservation);
                continue;
            }
            ServerPlayer player = current.getPlayerList().getPlayer(reservation.playerId());
            String problem = player == null ? "Player went offline" : cannotDuel(player, s, config);
            if (problem == null) {
                CHAIN.remove(player.getUUID());
                START_PACER.attempted(System.nanoTime());
                problem = startDuel(player, s, config, reservation);
            }
            if (problem != null) {
                if (!EncounterService.isActive(reservation.encounterId())) queue.failedStart(reservation, now, 5_000L);
                if (player != null) say(player, problem, ChatFormatting.RED);
            }
        }
        if (tickCounter % SECOND == 0) {
            int position = 0;
            for (UUID id : queue.waiting()) {
                position++;
                ServerPlayer waiting = current.getPlayerList().getPlayer(id);
                if (waiting != null) waiting.displayClientMessage(Component.literal("World Boss queue: " + position
                        + " | " + queue.occupied() + "/50 participant slots occupied").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            Set<UUID> occupying = queue.slots().keySet();
            for (var entry : queue.slots().entrySet()) {
                if (entry.getValue().phase() != WorldBossAdmission.Phase.BREATHER) continue;
                ServerPlayer player = current.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                long left = Math.max(0, (entry.getValue().availableAtMillis() - now + 999) / 1000);
                player.displayClientMessage(Component.literal("Next duel in " + left + "s | World Boss "
                        + percentLeft(s.pool()) + "% left").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            CHAIN.keySet().removeIf(id -> !occupying.contains(id));
        }
    }

    // ---- defeat and rewards ----

    private static void handleDefeat(MinecraftServer current) {
        WorldBossState s = state;
        if (s == null || s.pool() == null) return;
        if (!s.pool().claimDefeat()) return;
        if (s.wards() != null) s.wards().stop();
        if (admissions != null) admissions.close();
        s.touch();
        if (s.worldheartPolicy() == 0 && !flush(current)) return;
        finishDefeat(current);
    }

    /**
     * Admin test tool: finishes an explicitly isolated test. Live events refuse forced wins;
     * tests never issue Raid Points or notify economic/legacy outcome listeners.
     *
     * @return null on success, otherwise why nothing happened
     */
    public static String forceWin(MinecraftServer current, ServerPlayer credit) {
        WorldBossState s = state;
        if (s == null || !s.active() || s.pool() == null) return "no World Boss is active";
        if (!s.testMode()) return "forced wins are disabled for live events; start an isolated test with /cr worldboss test start";
        advanceClock(current);
        if (!combatOpen(s)) return "the test is not in active combat";
        if (s.pool().isDefeated()) return "the World Boss is already defeated";
        float left = s.pool().current();
        // Split the rest across everyone taking part. Crediting it all to one player would make the average huge and push everybody else under
        // the pay threshold (a fraction of the average), so a test with several players would pay only the admin.
        Set<UUID> takingPart = new LinkedHashSet<>(s.pool().contributions().keySet());
        takingPart.add(credit.getUUID());
        if (admissions != null) takingPart.addAll(admissions.waiting());
        WorldBossConfig config = WorldBossConfigManager.get();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            if (inRange(player, s, config)) takingPart.add(player.getUUID());
        }
        if (left > 0f) {
            float each = left / takingPart.size();
            for (UUID id : takingPart) s.pool().strike(id, each);
            if (s.pool().current() > 0f) s.pool().strike(credit.getUUID(), s.pool().current());
        }
        RaidLog.warn("World Boss {} was won by admin command, the last {} damage split across {} player(s)", s.bossId(), Math.round(left),
                takingPart.size());
        handleDefeat(current);
        return null;
    }

    /**
     * Freezes the promise before settlement. Credit receipts share the balances' durable commit,
     * so a crash before the event's completion flag is harmless: the same payout replays once.
     * Optional listeners still use the boss id as an idempotency key.
     */
    private static void finishDefeat(MinecraftServer current) {
        WorldBossState s = state;
        WorldBossConfig config = WorldBossConfigManager.get();
        WorldBossPool pool = s.pool();
        Map<UUID, Float> damage = Map.copyOf(pool.contributions());
        float total = pool.max();
        try { s.freezeWorldheartVictory(Math.max(System.currentTimeMillis(), s.campaign().wallHighWaterMillis())); }
        catch (RuntimeException invalid) { RaidLog.error("World Boss {} mandatory Worldheart grant is pending", s.bossId(), invalid); return; }
        var event = s.campaignEvent();
        if (event != null && event.status() != WorldBossCampaign.Status.VICTORY) {
            s.campaign(s.campaign().transition(event.key(), event.eventId(), WorldBossCampaign.Status.VICTORY, "",
                    Math.max(System.currentTimeMillis(), s.campaign().wallHighWaterMillis())));
            if (!flush(current)) return;
        }

        if (!s.rewardsPaid()) {
            if (s.rewardPlan() == null) {
                Map<UUID, Integer> amounts = new HashMap<>();
                if (s.merit() != null) {
                    if (!s.testMode()) s.merit().qualifiedPlayers().forEach(player -> amounts.put(player, s.merit().victoryReward()));
                } else {
                    float average = WorldBossRules.averageDamage(damage);
                    damage.forEach((player, dealt) -> { if (!s.testMode() && WorldBossRules.qualifies(dealt, average, config.minOfAverage())) amounts.put(player, config.rewardRaidPoints()); });
                }
                s.freezeRewardPlan(amounts);
            }
            if (!flush(current)) return;
            try {
                if (!s.testMode()) RaidPlayerRecords.creditWorldBossOnce(current, s.recoveryInstallation(s.bossId(), s.rewardPlan()), s.bossId(), s.rewardPlan());
            } catch (java.io.IOException | IllegalStateException failure) {
                RaidLog.error("World Boss {} reward settlement is pending; no completion was acknowledged", s.bossId(), failure);
                return;
            }
            s.markRewardsPaid(); // Every promise is now secured, including cap-overflow remainders.
            if (!flush(current)) return;
            s.rewardPlan().forEach((playerId, amount) -> {
                ServerPlayer online = current.getPlayerList().getPlayer(playerId);
                if (online == null) return;
                int pending = RaidPlayerRecords.pendingCredit(current, s.bossId(), playerId);
                say(online, "You earned " + amount + " Raid Points."
                        + (pending > 0 ? " " + pending + " wait for balance space; use /cr worldboss claim after spending points." : ""),
                        ChatFormatting.GREEN);
                WorldheartCollectionMenu.announce(online, s);
            });
            RaidLog.info("World Boss {} rewards secured for {} player(s)", s.bossId(), s.rewardPlan().size());
        }
        if (!s.defeatAnnounced()) {
            if (!s.testMode() && s.campaignEvent() != null) {
                s.recordScene(WorldBossSceneScript.Kind.VICTORY, Math.max(System.currentTimeMillis(), s.campaign().wallHighWaterMillis()));
                if (!flush(current)) return;
                WorldBossScenes.dispatch(current, new WorldBossSceneLedger.Key(s.bossId(), WorldBossSceneScript.Kind.VICTORY));
            }
            if (!s.testMode()) announceDefeat(current, damage, total);
            UUID bossId = s.bossId();
            String definitionId = s.definitionId();
            for (WorldBossListener listener : s.testMode() ? List.<WorldBossListener>of() : LISTENERS) {
                RaidFaultBarrier.guard("worldboss:listener-defeated",
                        () -> listener.onDefeated(bossId, definitionId, damage, total));
            }
            s.markDefeatAnnounced();
        }
        long priorEnded = s.lastEndedAtMillis();
        s.finish(System.currentTimeMillis());
        if (!flush(current)) { if (s.merit() != null) s.keepSettlementOpen(priorEnded); return; }
        removeShowpiece(current);
        sweepLeftovers(current);
        WorldBossArenaChunks.release();
        clearBar();
        CHAIN.clear();
    }

    private static void announceDefeat(MinecraftServer current, Map<UUID, Float> damage, float total) {
        List<Map.Entry<UUID, Float>> top = new ArrayList<>(damage.entrySet());
        top.sort(Map.Entry.<UUID, Float>comparingByValue(Comparator.reverseOrder()));
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            ServerPlayer player = current.getPlayerList().getPlayer(top.get(i).getKey());
            if (player == null) continue;
            if (!names.isEmpty()) names.append(", ");
            names.append(player.getGameProfile().getName());
        }
        var message = Component.literal("[World Boss] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(bossName(state) + " has fallen! ").withStyle(ChatFormatting.GREEN));
        var grant = state.collectionView().victory(state.bossId());
        if (grant != null && grant.blessing() != null) message.append(Component.literal("Community blessing: +10% base Raid Points on ordinary raid victories, "
                + Math.max(0, (grant.blessing().until() - System.currentTimeMillis() + 59_999) / 60_000) + " minutes remaining. ").withStyle(ChatFormatting.GOLD));
        if (state.campaignEvent() != null) message.append(Component.literal("The finale is recorded. /cr worldboss scene transcript " + state.campaignEvent().season() + " victory ").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!names.isEmpty()) {
            message.append(Component.literal("Top fighters online: " + names + ".").withStyle(ChatFormatting.YELLOW));
        }
        broadcast(current, message);
    }

    // ---- state, spawning, announcements ----

    private static boolean flush(MinecraftServer current) {
        WorldBossState s = state;
        if (s == null) return false;
        try {
            checkpointAdmissions(s);
            s.flush(current);
            stateDirty = false;
            if (clockTicker != null) clockTicker.checkpointed();
            return true;
        } catch (java.io.IOException | RuntimeException ex) {
            RaidLog.error("Could not save the World Boss state", ex);
            stateDirty = true;
            if (clockTicker != null) clockTicker.checkpointed(); // Bound disk-failure retries to the heartbeat interval.
            return false;
        }
    }

    private static void checkpointAdmissions(WorldBossState s) {
        if (admissions == null) return;
        var snapshot = admissions.snapshot(Math.max(1, System.currentTimeMillis()));
        if (snapshot.event().equals(s.bossId())) {
            if (recoveryAdmission != null && recoveryAdmission.event().equals(s.bossId())) {
                recoveryAdmission = recoveryAdmission.mergeRegroup(snapshot, snapshot.checkpointMillis()); snapshot = recoveryAdmission;
            }
            s.admissionCheckpoint(snapshot);
        }
    }

    private static void beginArenaRecovery(MinecraftServer current, WorldBossState saved) {
        cancelArenaRecovery();
        ResourceLocation id = ResourceLocation.tryParse(saved.dimension());
        ServerLevel level = id == null ? null : current.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) throw new IllegalStateException("The arena dimension is unavailable");
        UUID event = saved.bossId();
        WorldBossConfig config = WorldBossConfigManager.get();
        java.util.function.BooleanSupplier owned = () -> server == current && state == saved && saved.active()
                && event.equals(saved.bossId()) && !saved.isQuarantined() && saved.closeReason() == null
                && (saved.clock() == null || !saved.clock().terminal()) && WorldBossConfigManager.get() == config;
        arenaRecovery = new ArenaRestore(saved, event, WorldBossArenaChunks.recover(level, event, saved.x(), saved.z(), WorldBossRules.zoneRadius(saved, config), owned, () -> {
            if (!owned.getAsBoolean()) return;
            retainArenaChunks(current);
            if (!owned.getAsBoolean()) return;
            sweepLeftovers(current);
            if (!owned.getAsBoolean()) return;
            PokemonEntity standing = spawnShowpiece(current);
            if (!owned.getAsBoolean()) return;
            if (standing == null) throw new IllegalStateException("The recovered standing boss did not spawn");
            ensureBar(current); clockTicker = new WorldBossTicker(System.nanoTime());
            if (saved.clock() != null && saved.clock().phase() == WorldBossClock.Phase.REGROUP && !saved.testMode()) {
                broadcast(current, Component.literal("[World Boss] The encounter is recovering. Two-minute regroup; at least five ready players are needed.").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }));
    }

    private static void cancelArenaRecovery() {
        var recovering = arenaRecovery;
        if (recovering != null) { recovering.work().close(); if (arenaRecovery == recovering) arenaRecovery = null; }
    }

    private static void arenaRecoveryFailed(MinecraftServer current, WorldBossState saved, UUID event, RuntimeException failure) {
        RaidLog.error("World Boss arena restoration failed; holding combat", failure);
        if (saved == null || !sameActiveEvent(saved, event)) return;
        removeShowpiece(current);
        if (!sameActiveEvent(saved, event)) return;
        try { WorldBossArenaChunks.release(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
        if (!sameActiveEvent(saved, event)) return;
        if (saved.clock() != null) saved.clock(saved.clock().hold(WorldBossClock.Reason.PRESENTATION_FAILURE));
        stateDirty = true; flush(current);
    }

    private static void retainArenaChunks(MinecraftServer current) {
        WorldBossState s = state;
        if (s == null || !s.active() || !s.arena()) { WorldBossArenaChunks.release(); return; }
        ResourceLocation id = ResourceLocation.tryParse(s.dimension());
        ServerLevel level = id == null ? null : current.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) throw new IllegalStateException("The arena dimension is unavailable");
        WorldBossArenaChunks.retain(level, s.bossId(), s.x(), s.z(), WorldBossRules.zoneRadius(s, WorldBossConfigManager.get()));
    }

    private static void tickArenaChunks(MinecraftServer current) {
        try { retainArenaChunks(current); }
        catch (RuntimeException unavailable) {
            WorldBossState s = state;
            if (s != null && s.active() && s.clock() != null && s.clock().phase() != WorldBossClock.Phase.HELD) {
                RaidLog.error("World Boss {} arena chunk retention failed; holding combat", s.bossId(), unavailable);
                holdMechanics(current, WorldBossClock.Reason.PRESENTATION_FAILURE);
            }
        }
    }

    private static PokemonEntity spawnShowpiece(MinecraftServer current) {
        WorldBossState s = state;
        UUID event = s.bossId();
        ResourceLocation definitionId = ResourceLocation.tryParse(s.definitionId());
        RaidDefinition definition = definitionId == null ? null : RaidDefinitionRegistry.get(definitionId);
        ResourceLocation dimensionId = ResourceLocation.tryParse(s.dimension());
        ServerLevel level = dimensionId == null ? null
                : current.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (definition == null || level == null) return null;
        // Arena recovery must wait for operator-loaded terrain; it never generates terrain here.
        if (s.arena()) {
            if (level.getChunkSource().getChunkNow((int) Math.floor(s.x() / 16), (int) Math.floor(s.z() / 16)) == null)
                throw new IllegalStateException("The arena showpiece chunk is not fully loaded");
        } else level.getChunk(SectionPos.blockToSectionCoord((int) s.x()), SectionPos.blockToSectionCoord((int) s.z()), ChunkStatus.FULL, true);
        PokemonEntity boss;
        spawningShowpiece = true;
        try {
            boss = RaidBossSpawner.spawnAt(level, new Vec3(s.x(), s.y(), s.z()), definition, RenownRequest.NONE);
        } finally {
            spawningShowpiece = false;
        }
        if (boss == null) return null;
        if (!sameActiveEvent(s, event)) {
            com.cobbleraids.spawn.RaidDeferredDiscard.discardSoon(boss, level, candidate -> candidate == boss && !sameActiveEvent(s, event));
            return null;
        }
        boss.addTag(SHOWPIECE_TAG);
        showpieceId = boss.getUUID(); // Track ownership before visual hooks can throw.
        if (s.campaignEvent() != null) {
            boss.setCustomName(Component.literal(s.campaignEvent().displayName()));
            boss.setCustomNameVisible(true);
        }
        boss.getPokemon().setScaleModifier(WorldBossConfigManager.get().bossScale());
        if (!sameActiveEvent(s, event)) {
            if (boss.getUUID().equals(showpieceId)) showpieceId = null;
            com.cobbleraids.spawn.RaidDeferredDiscard.discardSoon(boss, level, candidate -> candidate == boss && !sameActiveEvent(s, event));
            return null;
        }
        return boss;
    }

    /**
     * Removes a standing boss this server no longer owns when its chunk loads. The startup sweep in RaidSpawnScheduler only sees entities in chunks
     * that are loaded at that moment, and a boss is saved with its chunk, so one whose event ended (or whose server stopped) while nobody was near it
     * would otherwise reappear later beside the new boss. The live boss keeps its own id and is left alone.
     */
    public static void onEntityLoaded(Entity entity, ServerLevel level) {
        if (spawningShowpiece || !(entity instanceof PokemonEntity pokemon) || !pokemon.getTags().contains(SHOWPIECE_TAG)) return;
        if (isShowpiece(pokemon)) return;
        RaidLog.info("Removed a World Boss left over from an earlier event ({} in {})", pokemon.getUUID(), level.dimension().location());
        com.cobbleraids.spawn.RaidDeferredDiscard.discardSoon(pokemon, level,
                candidate -> candidate instanceof PokemonEntity boss && boss.getTags().contains(SHOWPIECE_TAG)
                        && !isShowpiece(boss) && !boss.isBattling());
    }

    /** Applies the current visual settings (the boss's size) to the standing boss; the wall reads them on every pass anyway. */
    public static void refreshVisuals(MinecraftServer current) {
        UUID id = showpieceId;
        if (id == null) return;
        float scale = WorldBossConfigManager.get().bossScale();
        for (ServerLevel level : current.getAllLevels()) {
            if (level.getEntity(id) instanceof PokemonEntity boss && !boss.isRemoved()) boss.getPokemon().setScaleModifier(scale);
        }
    }

    private static void removeShowpiece(MinecraftServer current) {
        UUID id = showpieceId;
        showpieceId = null;
        if (id == null) return;
        for (ServerLevel level : current.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null && !entity.isRemoved()) entity.discard();
        }
    }

    /** How far from the boss's spot (blocks, each way) a raid boss counts as a leftover of it. */
    private static final double LEFTOVER_RADIUS = 6.0;

    /**
     * Removes tagged showpieces within {@link #LEFTOVER_RADIUS}. Arena cleanup inspects loaded chunks only;
     * legacy roaming cleanup retains its existing central chunk-loading behavior.
     * {@link #removeShowpiece} only knows the one it is tracking; a copy from an earlier build (before the showpiece had a tag), a showpiece a
     * restart duplicated, or one whose chunk was unloaded when the event ended is invisible to it, and would stay through every restart.
     * Bosses elsewhere are not touched, so ordinary raids are safe.
     *
     * @return how many were removed
     */
    private static int sweepLeftovers(MinecraftServer current) {
        WorldBossState s = state;
        if (s == null || s.definitionId().isEmpty()) return 0;   // no World Boss ever started here
        ResourceLocation dimensionId = ResourceLocation.tryParse(s.dimension());
        ServerLevel level = dimensionId == null ? null
                : current.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) return 0;
        int chunkX = (int) Math.floor(s.x() / 16);
        int chunkZ = (int) Math.floor(s.z() / 16);
        if (!s.arena()) for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) level.getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, true);
        }
        var area = new net.minecraft.world.phys.AABB(s.x() - LEFTOVER_RADIUS, level.getMinBuildHeight(), s.z() - LEFTOVER_RADIUS,
                s.x() + LEFTOVER_RADIUS, level.getMaxBuildHeight(), s.z() + LEFTOVER_RADIUS);
        int removed = 0;
        for (PokemonEntity boss : level.getEntitiesOfClass(PokemonEntity.class, area,
                entity -> entity.getTags().contains(SHOWPIECE_TAG) && !isShowpiece(entity))) {
            boss.discard();
            removed++;
        }
        if (removed > 0) RaidLog.info("Swept {} leftover boss(es) at the World Boss spot ({}, {}, {})", removed,
                Math.round(s.x()), Math.round(s.y()), Math.round(s.z()));
        return removed;
    }

    /**
     * Puts the hidden look back on a duel copy after Cobblemon wipes its effects. Called from RaidBossAttributeResetMixin, which runs on the
     * entity's first tick and whenever its level changes, and removeAllEffects() there takes the Invisibility with it.
     */
    public static void restoreDuelLook(PokemonEntity boss) {
        // Balance phase 3: a duel raid's copies are always hidden, so its players see only the raid boss they walked up to.
        if (com.cobbleraids.spawn.RaidBossEntityMarker.isOwnedBy(boss, com.cobbleraids.duel.RaidDuelService.OWNER)) {
            hideDuelBoss(boss);
            return;
        }
        if (!com.cobbleraids.spawn.RaidBossEntityMarker.isOwnedBy(boss, OWNER)) return;
        if (WorldBossConfigManager.get().look().duelBossInvisible()) hideDuelBoss(boss);
    }

    /**
     * An infinite, particle-free effect survives living-entity visibility updates. The attribute-reset hook above restores it after
     * Cobblemon clears effects. Only encounter-owned copies call this; the shared showpiece keeps its visible model.
     */
    public static void hideDuelBoss(PokemonEntity boss) {
        boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,
                net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
    }

    private static void abortDuels() {
        for (var entry : List.copyOf(DUELS.entrySet())) abortDuel(entry.getKey(), entry.getValue());
    }

    private static void abortDuel(UUID playerId, Duel duel) {
        try {
            EncounterService.abort(duel.encounterId());
        } catch (RuntimeException failure) {
            RaidLog.error("World Boss duel {} could not abort; retaining ownership for retry", duel.encounterId(), failure);
        }
        if (!EncounterService.isActive(duel.encounterId())) releaseEndedDuel(playerId, duel);
    }

    /** A missing/delayed end callback cannot strand a slot; a stale callback cannot release its successor. */
    private static void releaseEndedDuel(UUID playerId, Duel duel) {
        if (!DUELS.remove(playerId, duel)) return;
        DUEL_SPACE.release(duel.admission());
        DUEL_PLAYER.remove(duel.encounterId(), playerId);
        if (admissions != null) admissions.ended(duel.admission(), System.currentTimeMillis(), false);
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) healCompletedParty(player, duel.partyIds());
    }

    private static ServerPlayer randomPlayerIn(ServerLevel level) {
        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        return players.isEmpty() ? null : players.get(ThreadLocalRandom.current().nextInt(players.size()));
    }

    public static void refreshArenaProtection(MinecraftServer current) {
        RaidFaultBarrier.guard("worldboss:arena-protection", () -> WorldBossArenaProtection.refresh(current, state));
    }

    private static BlockPos findLand(ServerLevel level, BlockPos center, WorldBossConfig config) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double min = config.spawnMinDistance();
        double max = config.spawnMaxDistance();
        for (int attempt = 0; attempt < config.spawnAttempts(); attempt++) {
            double angle = random.nextDouble(0.0, Math.PI * 2.0);
            double radius = min >= max ? max : Math.sqrt(random.nextDouble(min * min, max * max));
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, level.getSeaLevel(), z))) continue;
            // Loads (and if needed generates) the chunk: the boss is placed far from every player.
            level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z),
                    ChunkStatus.FULL, true);
            BlockPos candidate = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!isSimpleSurface(level, candidate)) continue;
            // A boss inside a land claim cannot be fought by players the claim does not trust.
            if (LandClaimGuard.blocks(level, candidate, CobbleRaidsConfigManager.get().landClaims())) continue;
            return candidate;
        }
        return null;
    }

    private static boolean isSimpleSurface(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockPos above = pos.above();
        var ground = level.getBlockState(below);
        if (ground.isAir() || ground.getCollisionShape(level, below).isEmpty()) return false;
        if (!ground.getFluidState().isEmpty()) return false;
        var feet = level.getBlockState(pos);
        var head = level.getBlockState(above);
        if (!feet.getFluidState().isEmpty() || !head.getFluidState().isEmpty()) return false;
        return feet.getCollisionShape(level, pos).isEmpty() && head.getCollisionShape(level, above).isEmpty();
    }

    private static void announceStart(MinecraftServer current, RaidDefinition definition, ServerLevel level, BlockPos spot, boolean arena) {
        var species = definition.species();
        var message = Component.literal("[World Boss] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("A World Boss, ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(state.campaignEvent() == null ? speciesName(species) : state.campaignEvent().displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (arena) {
            message.append(Component.literal(", has appeared in the arena! Choose your place in the community encounter. ")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("[Join Fight] ").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr worldboss fight"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Queue a clear battle landing and resume participation. Your original return is protected.")))))
                    .append(Component.literal("[Watch]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr worldboss observe"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Watch from the spectator terrace without starting battles.")))));
        } else {
            message.append(Component.literal(", has appeared! Near X " + WorldBossRules.coordinateHint(spot.getX())
                            + ", Z " + WorldBossRules.coordinateHint(spot.getZ()) + " ("
                            + level.dimension().location().getPath() + "). Enter the bubble to join automatically and bring it down together.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        int countdown = joinSecondsLeft(state, WorldBossConfigManager.get());
        if (countdown > 0) {
            message.append(Component.literal(" The fight opens in " + countdown + " s. Ready players inside the bubble enter automatically.")
                    .withStyle(ChatFormatting.GREEN));
        }
        broadcast(current, message);
    }

    // ---- the arena: choosing the marker, warping in and back ----

    /** The marker the boss starts at, or why there is none. A null pick means "no markers placed": use the open-world placement. */
    private record ArenaPick(ServerLevel level, BlockPos pos, String problem) {}

    private static ArenaPick pickArena(MinecraftServer current) {
        List<ArenaMarkers.Spot> spots = new ArrayList<>(ArenaMarkers.get(current).list(ArenaMarkers.Kind.SPAWN));
        if (spots.isEmpty()) return null;
        java.util.Collections.shuffle(spots);
        for (ArenaMarkers.Spot spot : spots) {
            ResourceLocation id = ResourceLocation.tryParse(spot.dimension());
            ServerLevel level = id == null ? null : current.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (level != null) return new ArenaPick(level, spot.pos(), null);
        }
        return new ArenaPick(null, null, "the arena's spawn markers are in a dimension that is not loaded (" + spots.get(0).dimension() + ")");
    }

    /** Queued, bounded arena entry; return state is committed immediately before native travel. */
    public static String warp(ServerPlayer player) { return requestArrival(player, WorldBossArrivalQueue.Mode.WARP); }
    public static String fight(ServerPlayer player) { return requestArrival(player, WorldBossArrivalQueue.Mode.FIGHT); }
    public static String observe(ServerPlayer player) { return requestArrival(player, WorldBossArrivalQueue.Mode.WATCH); }
    private static String requestArrival(ServerPlayer player, WorldBossArrivalQueue.Mode mode) {
        return arenaRecovery != null ? "The World Boss arena is still restoring. Please wait for the regroup." : WorldBossArrivals.request(player, mode);
    }
    public static boolean allowArenaTeleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        try { return WorldBossArrivals.allowsTeleport(player, level, x, y, z); }
        catch (RuntimeException failure) { RaidFaultBarrier.report("worldboss:teleport-admission", failure); return false; }
    }
    static boolean audienceViews(ServerPlayer player) { return WorldBossArrivals.views(player); }
    /** Optional Cards compatibility: retain verbose stat diagnostics at DEBUG for this arena's audience. */
    public static boolean quietCardStatDiagnostics(ServerPlayer player) {
        boolean quiet = arenaCardLogScope(player);
        if (quiet) quietCardStatCalls++;
        return quiet;
    }
    public static boolean arenaCardLogScope(ServerPlayer player) {
        var saved = state;
        return player != null && player.server == server && player.server.isSameThread()
                && saved != null && saved.active() && saved.arena() && saved.audienceRadius() != null
                && WorldBossArrivals.views(player);
    }
    public static long quietCardStatCalls() { return quietCardStatCalls; }
    static boolean audienceObserver(ServerPlayer player) { return WorldBossArrivals.observer(player); }
    static boolean arrivalPending(UUID player) { return WorldBossArrivals.pending(player); }
    static boolean hasOwnedDuel(UUID player) { return DUELS.containsKey(player); }
    static WorldBossDuelSpace duelSpace() { return DUEL_SPACE; }
    static String arrivalProblem(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("World Boss arrivals require the server thread");
        var s = state;
        if (s == null || s.isQuarantined() || !s.active() || s.pool() == null) return "There is no available World Boss right now.";
        if (!s.arena()) return "This occurrence is not in a designated arena.";
        if (s.pool().isDefeated() || s.pool().current() <= 0 || s.closeReason() != null || s.clock() != null && s.clock().terminal()) return "This World Boss occurrence is closing.";
        if (!player.isAlive() || player.isSpectator()) return "You cannot use arena travel right now.";
        if (!Double.isFinite(player.getX()) || !Double.isFinite(player.getY()) || !Double.isFinite(player.getZ())) return "Your position is invalid. Tell staff before retrying.";
        if (!Float.isFinite(player.getBbWidth()) || !Float.isFinite(player.getBbHeight()) || player.getBbWidth() <= 0 || player.getBbWidth() > 4 || player.getBbHeight() <= 0 || player.getBbHeight() > 8) return "Your player dimensions do not fit supported arena travel. Tell staff before retrying.";
        if (player.isPassenger() || player.isVehicle()) return "Dismount before arena travel.";
        if (DUELS.containsKey(player.getUUID())) return "Finish your duel before changing arena areas.";
        if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) return "Finish your current battle first.";
        return null;
    }

    /** Sends a player who warped in back to where they were (or to the world spawn if that is unknown). Returns null on success. */
    public static String returnHome(ServerPlayer player) {
        WorldBossArrivals.cancel(player.getUUID());
        if (!player.server.isSameThread()) throw new IllegalStateException("World Boss returns require the server thread");
        if (server == null) return "The server is not ready.";
        if (!player.isAlive() || player.isSpectator()) return "You cannot do that right now.";
        if (player.isPassenger()) return "Dismount before returning from the arena.";
        if (DUELS.containsKey(player.getUUID())) return "Finish your duel first.";
        if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) return "Finish your current battle first.";
        boolean inside = state != null && inRange(player, state, WorldBossConfigManager.get());
        if (state != null && state.active() && inside && !state.isPaused(player.getUUID())) { leave(player); if (!controlIsSaved()) return "Battles could not be paused durably. Your saved return remains intact; try again shortly."; }
        return WorldBossTravelService.returnHome(player, inside);
    }

    /** The boss as players read it: its species ("Phantump"), not the raid definition id, which for a skin is "worldboss_halloween_phantump". */
    private static String bossName(WorldBossState s) {
        if (s.campaignEvent() != null) return s.campaignEvent().displayName();
        ResourceLocation id = ResourceLocation.parse(s.definitionId());
        RaidDefinition definition = RaidDefinitionRegistry.get(id);
        return speciesName(definition != null ? definition.species() : id);
    }

    private static String speciesName(ResourceLocation species) {
        String path = species.getPath();
        return path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static void broadcast(MinecraftServer current, Component message) {
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            if (!RaidSpawnAnnouncementService.isMuted(player)) player.sendSystemMessage(message);
        }
    }

    private static void say(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[World Boss] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(color)));
    }

    // ---- the zone wall ----

    /**
     * Draws the edge of the zone as a wall of soul particles for each player near it, and says once when a player crosses it. The wall is the
     * circle {@link WorldBossConfig#radius} blocks round the boss, the same test a duel needs, so walking through it is walking into range.
     * Particles go only to the player who is near the wall (one packet per column), alternating two kinds so a pass stays cheap.
     */
    private static void drawBorder(MinecraftServer current, WorldBossState s, WorldBossConfig config) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(s.dimension());
        if (dimensionId == null) return;
        boolean flame = (tickCounter / BORDER_TICKS) % 2 == 0;
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            if (!audienceViews(player)) { LAST_INSIDE.remove(player.getUUID()); continue; }
            if (!player.level().dimension().location().equals(dimensionId)) {
                LAST_INSIDE.remove(player.getUUID());
                continue;
            }
            double px = player.getX();
            double pz = player.getZ();
            double ring = WorldBossZone.horizontalRadius(s.y(), WorldBossRules.zoneRadius(s, config), player.getY());
            var columns = WorldBossBorder.columns(s.x(), s.z(), ring, px, pz, config.borderViewDistance(), BORDER_SPACING);
            boolean inside = inRange(player, s, config);
            Boolean was = LAST_INSIDE.put(player.getUUID(), inside);
            if (was != null && was != inside) {
                if (config.dome().enabled()) WorldBossDome.ripple(player, inside);
                player.displayClientMessage(Component.literal(inside
                        ? s.observing(player.getUUID()) ? "You are watching. Use /cr worldboss fight to join the battle." : "You entered the World Boss bubble. Ready players enroll automatically."
                        : "You left the World Boss zone.")
                        .withStyle(inside ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
            }
            if (columns.isEmpty() || !config.borderParticles() || config.dome().enabled()) continue;   // the dome replaces the particle wall
            ServerLevel level = player.serverLevel();
            double y = player.getY();
            // The potion swirl takes an ARGB colour whose alpha is honoured, which is what makes a translucent tint possible (dust has no alpha).
            var tint = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, config.wallColorArgb());
            for (double[] column : columns) {
                level.sendParticles(player, tint, true, column[0], y, column[1], flame ? 4 : 3, 0.25, 7.0, 0.25, 0.0);
            }
        }
        LAST_INSIDE.keySet().retainAll(online);
    }

    // ---- the boss bar ----

    private static void ensureBar(MinecraftServer current) {
        if (bar == null) {
            // Logical slot only: BossBarSkins allocates an independent texture for this owner.
            // Without that optional mod this stays a vanilla blue bar, outside the haunted pink theme.
            bar = new ServerBossEvent(Component.literal("World Boss"), BossEvent.BossBarColor.BLUE,
                    BossEvent.BossBarOverlay.PROGRESS);
            WorldBossBarSkin.claim(bar);
        }
        refreshBar(current);
    }

    private static void clearBar() {
        WorldBossHudBridge.clear();
        ServerBossEvent current = bar;
        if (current != null) current.removeAllPlayers();
        bar = null;
    }

    private static void refreshBar(MinecraftServer current) {
        ServerBossEvent event = bar;
        WorldBossState s = state;
        if (event == null || s == null || s.pool() == null) return;
        barDirty = false;
        WorldBossPool pool = s.pool();
        WorldBossConfig config = WorldBossConfigManager.get();
        event.setProgress(pool.max() <= 0f ? 0f : Math.max(0f, Math.min(1f, pool.current() / pool.max())));
        Set<UUID> fighting = new HashSet<>(DUELS.keySet());
        fighting.addAll(CHAIN.keySet());
        long minutesLeft = s.clock() != null ? (s.clock().remainingMillis() + 59_999L) / 60_000L
                : s.endsAtMillis() > 0L ? Math.max(0L, (s.endsAtMillis() - System.currentTimeMillis() + 59_999L) / 60_000L) : -1L;
        int gatherLeft = joinSecondsLeft(s, config);
        String hudStatus;
        if (s.closeReason() != null) {
            hudStatus = "SECURING REWARDS";
            event.setName(Component.literal(bossName(s) + " - securing rewards"));
        } else if (s.clock() != null && s.clock().phase() == WorldBossClock.Phase.HELD) {
            hudStatus = "STAFF HOLD";
            event.setName(Component.literal(bossName(s) + " - STAFF HOLD (" + s.clock().reason() + ")"));
        } else if (s.clock() != null && s.clock().phase() == WorldBossClock.Phase.REGROUP) {
            hudStatus = "REGROUP  /  " + (s.clock().phaseRemainingMillis() + 999) / 1000 + "s";
            event.setName(Component.literal(bossName(s) + " - regroup " + (s.clock().phaseRemainingMillis() + 999) / 1000 + "s"));
        } else if (gatherLeft > 0) {
            hudStatus = "GATHERING  /  OPENS IN " + gatherLeft / 60 + ":" + String.format(java.util.Locale.ROOT,"%02d",gatherLeft % 60)
                    + "  /  " + (admissions == null ? 0 : admissions.waiting().size()) + " READY";
            event.setName(Component.literal(bossName(s) + " - gathering, the fight opens in "
                    + gatherLeft / 60 + ":" + String.format("%02d", gatherLeft % 60) + " ("
                    + (admissions == null ? 0 : admissions.waiting().size()) + " ready)"));
        } else {
            hudStatus = fighting.size() + " FIGHTING" + (minutesLeft >= 0 ? "  /  " + WorldBossBarText.timeLeft(minutesLeft) + " REMAINING" : "");
            event.setName(WorldBossBarText.build(bossName(s), pool.current(), pool.max(),
                    percentLeft(pool), fighting.size(), minutesLeft));
        }
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            boolean show = !pool.isDefeated() && (DUELS.containsKey(player.getUUID())
                    || CHAIN.containsKey(player.getUUID()) || inRange(player, s, config) || audienceObserver(player)) && audienceViews(player);
            if (show) event.addPlayer(player);
            else event.removePlayer(player);
            WorldBossHudBridge.publish(player,event,s,show,bossName(s),hudStatus,
                    gatherLeft > 0 || s.openingRules() != null && s.openingRoster() == null);
        }
    }

    private static int percentLeft(WorldBossPool pool) {
        if (pool == null || pool.max() <= 0f) return 0;
        return Math.round(100f * pool.current() / pool.max());
    }

    // ---- facts about a player ----

    static boolean inRange(ServerPlayer player, WorldBossState s, WorldBossConfig config) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(s.dimension());
        if (dimensionId == null || !player.level().dimension().location().equals(dimensionId)) return false;
        boolean previous = PRESENT.contains(player.getUUID()) || recoveryAdmission != null && (recoveryAdmission.waiting().contains(player.getUUID()) || recoveryAdmission.slots().stream().anyMatch(slot -> slot.token().playerId().equals(player.getUUID())));
        return WorldBossZone.present(s.x(), s.y(), s.z(), WorldBossRules.zoneRadius(s, config), player.getX(), player.getY(), player.getZ(), previous);
    }

    static int partySize(ServerPlayer player) {
        int size = 0;
        for (Pokemon ignored : Cobblemon.INSTANCE.getStorage().getParty(player)) size++;
        return size;
    }

    private static void healParty(ServerPlayer player, Set<UUID> admittedPokemon) {
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (admittedPokemon.contains(pokemon.getUuid())) pokemon.heal();
        }
    }

    private static void healCompletedParty(ServerPlayer player, Set<UUID> admittedPokemon) {
        try {
            healParty(player, admittedPokemon);
        } catch (RuntimeException failure) {
            RaidLog.error("World Boss post-duel heal failed for {}; admission cleanup has completed", player.getUUID(), failure);
        }
    }

    // ---- reads for commands ----

    /** One line per fact about the current event, for {@code /cr worldboss status}. */
    public static List<String> status() {
        WorldBossState s = state;
        List<String> lines = new ArrayList<>();
        if (s == null) {
            lines.add("The World Boss system has not started.");
            return lines;
        }
        WorldBossConfig config = WorldBossConfigManager.get();
        lines.add("Enabled: " + config.enabled() + "; live launches require an activated daily season.");
        lines.add("World Boss control save: " + (controlCommitPending() ? "pending " + controlOperation : "none"));
        lines.add("World Boss arena recovery: " + (arenaRecovery == null ? "ready" : arenaRecovery.work().status()));
        lines.addAll(WorldBossDailyService.status());
        if (!s.active() || s.pool() == null) {
            lines.add("No World Boss is active.");
            return lines;
        }
        WorldBossPool pool = s.pool();
        long minutesLeft = s.clock() != null ? (s.clock().remainingMillis() + 59_999L) / 60_000L
                : Math.max(0L, (s.endsAtMillis() - System.currentTimeMillis()) / 60_000L);
        lines.add("Boss " + s.definitionId() + " (" + s.bossId() + ")");
        if (s.testMode()) lines.add("TEST encounter: live rewards and world-boss outcome listeners are disabled.");
        lines.add("Pool " + Math.round(pool.current()) + " / " + Math.round(pool.max()) + " ("
                + percentLeft(pool) + "% left)");
        if (s.openingRules() != null) lines.add(s.openingRoster() == null ? "Opening health: awaiting ready roster"
                : "Opening roster locked: " + s.openingRoster().size() + ", fixed HP: " + s.openingRules().calibration().healthFor(s.openingRoster().size())
                + ", calibration: " + s.openingRules().calibration().evidence());
        lines.add("At about X " + Math.round(s.x()) + ", Z " + Math.round(s.z()) + " in " + s.dimension()
                + ", " + minutesLeft + (s.clock() != null ? " active min remaining" : " min until departure"));
        if (s.clock() != null) lines.add("Timing: " + s.clock().phase() + ", reason: " + s.clock().reason()
                + ", recovery count: " + s.clock().recoveries() + "/2, conservative offline ms: " + s.clock().offlineMillis());
        lines.add("Players who hit it: " + pool.contributions().size() + ", duelling now: " + DUELS.size()
                + ", chained: " + CHAIN.size());
        if (admissions != null) lines.add("Participant slots: " + admissions.occupied()
                + "/50, ready queue: " + admissions.waiting().size() + ", paused: " + admissions.pausedPlayers().size());
        return lines;
    }

    /** A player's own damage and share, or null when there is no event. */
    public static String myShare(UUID playerId) {
        WorldBossState s = state;
        if (s == null || s.pool() == null || !s.active()) return null;
        String merit = s.merit() == null ? "" : "Merit " + s.merit().score(playerId) + "/100"
                + (s.merit().qualified(playerId) ? " - qualified for this event's base reward. " : ". ");
        return merit + Math.round(s.pool().contributionOf(playerId)) + " damage ("
                + Math.round(s.pool().shareOf(playerId) * 1000f) / 10f + "% of the pool so far)";
    }
}
