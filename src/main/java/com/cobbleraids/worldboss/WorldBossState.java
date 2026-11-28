package com.cobbleraids.worldboss;

import com.cobbleraids.config.DurableSavedData;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The World Boss that is running, on disk, so a restart picks the fight up where it was.
 *
 * <p>A forced, checksummed journal is authoritative; {@code cobbleraids_world_boss.dat} is its
 * rebuildable SavedData projection. They hold the pool, damage, position and frozen payout plan. Balance/receipt
 * settlement is committed before {@code rewardsPaid} is acknowledged; the same plan can replay
 * without paying twice. Optional listener delivery uses the separate defeat-announcement marker.
 *
 * <p>An empty state ({@link #active} false) is the normal state of a server with no event running.
 */
public final class WorldBossState extends DurableSavedData {
    private static final String FILE_ID = "cobbleraids_world_boss";

    private boolean active;
    private UUID bossId = new UUID(0L, 0L);
    private String definitionId = "";
    private String dimension = "minecraft:overworld";
    private double x;
    private double y;
    private double z;
    private long startedAtMillis;
    private long endsAtMillis;
    private long lastEndedAtMillis;
    private WorldBossPool pool;
    private boolean defeatAnnounced;
    private boolean rewardsPaid;
    /** Players whose defeat reward has been paid (marked and saved just before each payment), so a restart half way through pays only the rest. */
    private final Set<UUID> paidPlayers = new LinkedHashSet<>();
    private boolean arena;
    private boolean testMode;
    private WorldBossClock clock;
    private WorldBossOpeningRules openingRules;
    private WorldBossCampaign campaign = WorldBossCampaign.empty();
    private WorldheartCollection collection = WorldheartCollection.empty();
    private WorldheartCollection publishedCollection = collection;
    private WorldBossBenefits benefits = new WorldBossBenefits();
    private WorldBossBenefits publishedBenefits = benefits;
    private WorldBossSceneLedger scenes = new WorldBossSceneLedger();
    private WorldBossSceneLedger publishedScenes = scenes;
    private WorldBossTravel travel = new WorldBossTravel();
    private WorldBossAdmission.Snapshot admissionCheckpoint;
    private int worldheartPolicy;
    private String eventOccurrence;
    private List<UUID> openingRoster;
    private WorldBossMerit merit;
    private WorldBossWards wards;
    private String closeReason;
    private String closeMessage;
    private Map<UUID, Integer> rewardPlan;
    private final Set<UUID> paused = new LinkedHashSet<>();
    private final Set<UUID> observing = new LinkedHashSet<>();
    private Double audienceRadius;
    private CompoundTag quarantinedData;
    private boolean loadedFromDisk;
    private boolean originChecked;
    private boolean unreadableFile;
    private WorldBossJournal journal;
    private WorldBossCheckpoints checkpoints;
    private record CheckpointViews(WorldheartCollection collection, WorldBossBenefits benefits, WorldBossSceneLedger scenes) {}
    private CheckpointViews checkpointViews;
    private boolean checkpointFailureReported;
    private long nextCheckpointReceipt, pendingCheckpointReceipt, acknowledgedCheckpointReceipt;
    private boolean recoveryBlocked;

    public WorldBossState() {}

    WorldBossState(WorldBossJournal journal) { this.journal = java.util.Objects.requireNonNull(journal); }

    public static SavedData.Factory<WorldBossState> factory() {
        return new SavedData.Factory<>(WorldBossState::new, WorldBossState::load, DataFixTypes.LEVEL);
    }

    private static WorldBossState recover(MinecraftServer server) {
        var directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
        var projectionFile = directory.resolve("data").resolve(FILE_ID + ".dat");
        var journalFile = directory.resolve("data").resolve(FILE_ID + ".journal");
        var witness = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize()
                .resolve("cobbleraids/worldboss-recovery/journal.head");
        CompoundTag projection = null;
        boolean damagedProjection = false;
        try {
            if (witness.startsWith(directory)) throw new IOException("World Boss recovery witnesses must be outside the world save");
            if (!java.nio.file.Files.notExists(projectionFile)) {
                try {
                    projection = net.minecraft.nbt.NbtIo.readCompressed(projectionFile, net.minecraft.nbt.NbtAccounter.create(64L * 1024 * 1024));
                    if (!projection.contains("data", Tag.TAG_COMPOUND)) throw new IOException("Missing World Boss projection data");
                } catch (IOException | RuntimeException invalid) {
                    if (java.nio.file.Files.notExists(journalFile)) throw new IOException("Unreadable World Boss projection has no journal recovery authority", invalid);
                    projection = null; damagedProjection = true;
                }
            }
            var points = com.cobbleraids.catching.RaidPlayerRecords.worldBossRecovery(server);
            if (points.installation() != null && java.nio.file.Files.notExists(witness)) throw new IOException("An established World Boss recovery set is missing its external journal head");
            WorldBossJournal authority = WorldBossJournal.open(journalFile, witness, projection);
            boolean initializing = !authority.initialized();
            if (initializing && points.installation() != null) throw new IOException("The World Boss journal is missing but Raid Points has its recovery identity");
            CompoundTag root = initializing ? projection : authority.snapshot();
            WorldBossState state = root == null ? new WorldBossState(authority) : load(root.getCompound("data"), server.registryAccess());
            if (state.isQuarantined()) throw new IOException("World Boss authority contains invalid domain state");
            if (initializing) {
                if (root == null) { root = new CompoundTag(); root.put("data", state.save(new CompoundTag(), server.registryAccess())); net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root); }
                authority.commit(root, state.recoveryPromises(true));
            }
            if (points.installation() == null) {
                if (!initializing && authority.hasTrackedPromises()) throw new IOException("Raid Points recovery binding is missing after tracked outcomes");
                points = com.cobbleraids.catching.RaidPlayerRecords.bindWorldBossRecovery(server, authority.installation());
            }
            authority.reconcile(points.installation(), points.promises());
            state.journal = authority; state.originChecked = true; state.loadedFromDisk = true;
            if (damagedProjection) {
                var evidence = projectionFile.resolveSibling(projectionFile.getFileName() + ".damaged-" + UUID.randomUUID());
                java.nio.file.Files.copy(projectionFile, evidence); com.cobbleraids.config.RecoveryWitness.force(evidence);
            }
            try {
                if (damagedProjection || projection == null || !projection.equals(authority.projection())) writeRoot(projectionFile, authority.projection());
            } catch (IOException projectionFailure) {
                com.cobbleraids.RaidLog.warn("World Boss journal recovered but its cache could not be refreshed: {}", projectionFailure.getMessage());
            }
            return state;
        } catch (IOException | RuntimeException invalid) {
            com.cobbleraids.RaidLog.error("World Boss recovery set needs operator reconciliation; no automatic settlement or replacement is allowed", invalid);
            WorldBossState blocked = new WorldBossState(); blocked.recoveryBlocked = true; blocked.unreadableFile = true; blocked.originChecked = true;
            return blocked;
        }
    }

    private List<WorldBossJournal.Promise> recoveryPromises(boolean baseline) {
        var promises = new java.util.ArrayList<WorldBossJournal.Promise>();
        if (!testMode && rewardPlan != null) promises.add(new WorldBossJournal.Promise(bossId, rewardPlan, rewardsPaid, !baseline || !rewardsPaid));
        benefits.bonuses().forEach((raid, bonus) -> promises.add(new WorldBossJournal.Promise(WorldBossBenefits.payoutId(raid), bonus.amounts(), bonus.paid(), !baseline || !bonus.paid())));
        return List.copyOf(promises);
    }

    UUID recoveryInstallation(UUID payout, Map<UUID, Integer> amounts) {
        if (journal == null || isQuarantined()) throw new IllegalStateException("World Boss recovery authority is unavailable");
        var promise = journal.promise(payout);
        if (promise == null || !promise.amounts().equals(amounts)) throw new IllegalStateException("Payout is not secured by the World Boss journal");
        return journal.installation();
    }

    String recoveryStatus() {
        if (isQuarantined()) return "Recovery: quarantined; preserve the recovery set and inspect the server log before reconciliation";
        return journal == null ? "Recovery: not initialized" : "Recovery: " + journal.installation() + ", journal sequence " + journal.sequence() + ", retained payout promises " + journal.promiseCount();
    }

    @Override protected boolean deferNativeSave(java.nio.file.Path target, HolderLookup.Provider registries) throws IOException {
        if (!WorldBossAutosaveScope.deferred()) { drainCheckpoints(); return false; }
        pollCheckpoint();
        if (isDirty() && !checkpointPending()) checkpointAsyncSnapshot(registries);
        return true; // An older pending snapshot leaves later mutations dirty for the next capture.
    }

    void awaitNativeCheckpoint() throws IOException { drainCheckpoints(); }

    @Override protected void commitRoot(java.nio.file.Path target, CompoundTag root) throws IOException {
        drainCheckpoints();
        if (isQuarantined()) throw new IOException("Cannot overwrite quarantined World Boss recovery evidence");
        if (journal == null) { super.commitRoot(target, root); return; }
        journal.commit(root, recoveryPromises(false));
        // The journal and external head are the commit. Rebuilding this cache on every damage,
        // admission or scene checkpoint duplicates compression/forced IO on the server thread.
        // Recovery accepts a stale/missing cache; startup and clean shutdown refresh it instead.
    }

    void checkpointProjection(MinecraftServer server) throws IOException {
        if (!server.isSameThread()) throw new IllegalStateException("World Boss cache checkpoints require the server thread");
        checkpointProjection(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(FILE_ID + ".dat"));
    }

    void checkpointProjection(java.nio.file.Path target) throws IOException {
        drainCheckpoints();
        if (isQuarantined() || journal == null) throw new IOException("Cannot project an unavailable World Boss recovery authority");
        writeRoot(target, journal.projection());
    }

    /** Only the forced journal can publish an acknowledged recipient set to optional integrations. */
    Set<UUID> qualifiedPlayers(UUID payout) {
        if (isQuarantined() || journal == null) throw new IllegalStateException("World Boss recovery authority is unavailable");
        var promise = journal.promise(java.util.Objects.requireNonNull(payout));
        if (promise == null || !promise.paid()) throw new IllegalStateException("World Boss payout is not secured");
        return Set.copyOf(promise.amounts().keySet());
    }

    public static WorldBossState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        WorldBossState state = overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> recover(server), (ignored, registries) -> recover(server), DataFixTypes.LEVEL), FILE_ID);
        if (state.journal == null && !state.isQuarantined()) state.recoveryBlocked = true;
        if (!state.originChecked) {
            state.originChecked = true;
            var file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(FILE_ID + ".dat");
            state.unreadableFile = !state.loadedFromDisk && !java.nio.file.Files.notExists(file);
        }
        return state;
    }

    public void flush(MinecraftServer server) throws IOException {
        drainCheckpoints();
        if (isQuarantined()) throw new IOException("World Boss state/recovery needs reconciliation; refusing to replace existing evidence");
        flushStrict(server, FILE_ID);
        publishedCollection = collection;
        publishedBenefits = benefits;
        publishedScenes = scenes;
    }

    boolean checkpointPending() { return checkpoints != null && checkpoints.pending(); }
    boolean checkpointOverdue() { return checkpoints != null && checkpoints.overdue(); }

    long checkpointAsync(MinecraftServer server) throws IOException {
        if (!server.isSameThread()) throw new IllegalStateException("World Boss checkpoint capture requires the server thread");
        return checkpointAsyncSnapshot(server.registryAccess());
    }

    long checkpointAsyncSnapshot(HolderLookup.Provider registries) throws IOException {
        if (isQuarantined() || journal == null) throw new IOException("World Boss checkpoint authority is unavailable");
        if (checkpoints == null) checkpoints = new WorldBossCheckpoints(journal);
        if (checkpoints.pending()) throw new IllegalStateException("A World Boss checkpoint is already outstanding");
        CompoundTag root = new CompoundTag(); root.put("data", save(new CompoundTag(), registries));
        net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root);
        var views = new CheckpointViews(collection, benefits, scenes);
        long receipt = Math.incrementExact(nextCheckpointReceipt);
        checkpoints.submit(root, recoveryPromises(false));
        nextCheckpointReceipt = receipt; pendingCheckpointReceipt = receipt;
        checkpointViews = views;
        setDirty(false); // Later mutations become dirty independently of this captured generation.
        return receipt;
    }

    boolean checkpointAcknowledged(long receipt) { return receipt > 0 && receipt <= acknowledgedCheckpointReceipt; }

    void pollCheckpoint() throws IOException {
        if (checkpointFailureReported) return;
        try { if (checkpoints != null && checkpoints.poll()) publishCheckpoint(); }
        catch (IOException | RuntimeException failure) { checkpointFailureReported = true; throw failure; }
    }

    private void drainCheckpoints() throws IOException {
        if (checkpoints == null) return;
        checkpoints.drain();
        publishCheckpoint();
    }

    private void publishCheckpoint() {
        if (checkpointViews == null) return;
        publishedCollection = checkpointViews.collection();
        publishedBenefits = checkpointViews.benefits();
        publishedScenes = checkpointViews.scenes();
        acknowledgedCheckpointReceipt = pendingCheckpointReceipt;
        pendingCheckpointReceipt = 0;
        checkpointViews = null;
    }

    void closeCheckpoints() throws IOException {
        if (checkpoints != null) { checkpoints.close(); publishCheckpoint(); }
    }

    // ---- reads ----

    public boolean active() { return active; }
    public UUID bossId() { return bossId; }
    public String definitionId() { return definitionId; }
    public String dimension() { return dimension; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public long startedAtMillis() { return startedAtMillis; }
    /** Epoch millis when the event closes by itself, or 0 for never. */
    public long endsAtMillis() { return endsAtMillis; }
    /** Epoch millis when the previous World Boss ended, or 0 if none has. */
    public long lastEndedAtMillis() { return lastEndedAtMillis; }
    /** Null when no World Boss has been started. */
    public WorldBossPool pool() { return pool; }
    public boolean defeatAnnounced() { return defeatAnnounced; }
    public boolean rewardsPaid() { return rewardsPaid; }
    /** True when the boss started at an arena spawn marker: the zone then uses {@code arena.radius} and players can warp in. */
    public boolean arena() { return arena; }
    public boolean testMode() { return testMode; }
    public WorldBossClock clock() { return clock; }
    public WorldBossOpeningRules openingRules() { return openingRules; }
    public WorldBossMerit merit() { return merit; }
    public WorldBossWards wards() { return wards; }
    public String closeReason() { return closeReason; }
    public String closeMessage() { return closeMessage; }
    public void configureWardPylon(WorldBossWards.Pylon pylon) {
        if (wards == null || clock == null || clock.phase() != WorldBossClock.Phase.PREPARING || wards.snapshot().crossed() != 0) throw new IllegalStateException("Ward layout is already live");
        if (pylon != null && !pylon.dimension().equals(dimension)) throw new IllegalArgumentException("Ward pylon dimension mismatch");
        wards = new WorldBossWards(pylon); setDirty();
    }
    void configureWardRelay(WorldBossRelay.Route route) {
        if (wards == null || clock == null || clock.phase() != WorldBossClock.Phase.PREPARING || wards.snapshot().crossed() != 0) throw new IllegalStateException("Ward layout is already live");
        wards = new WorldBossWards(wards.pylon(), route); setDirty();
    }

    public record Damage(WorldBossMerit.Evidence evidence, float applied, boolean qualifiedNow) {}
    /** One authoritative server-thread reducer: damage, Merit, ward evidence, then outcome eligibility. */
    public Damage damageAction(UUID encounter, UUID player, int turn, long sequence, float amount, int ready) {
        if (merit == null || !active || clock == null || !clock.combatOpen() || closeReason != null || !pool.isFixed()
                || !Float.isFinite(amount) || amount <= 0 || pool.fixedCurrentUnits() == 0) return new Damage(WorldBossMerit.Evidence.INVALID, 0, false);
        var evidence = merit.inspect(encounter, player, turn, sequence);
        if (evidence != WorldBossMerit.Evidence.ACCEPT) return new Damage(evidence, 0, false);
        if (ready < 0 || ready > 50) throw new IllegalArgumentException("Invalid ward readiness");
        boolean qualified = merit.qualified(player);
        float applied = pool.strike(player, amount);
        merit.damaged(encounter, player, turn, sequence);
        if (pool.fixedCurrentUnits() == 0) wards.stop();
        else { wards.damaged(pool.fixedMaxUnits(), pool.fixedCurrentUnits(), clock.remainingMillis(), ready); wards.combat(encounter, player, turn); }
        setDirty(); return new Damage(evidence, applied, !qualified && merit.qualified(player));
    }

    public boolean focusWard(UUID player, int serial) {
        if (merit == null || !active || clock == null || !clock.combatOpen() || closeReason != null || pool.current() <= 0) return false;
        if (!wards.focus(player, serial, clock.remainingMillis())) return false;
        if (!merit.focused(player, serial)) throw new IllegalStateException("Focus receipts disagree");
        setDirty(); return true;
    }
    boolean relayWard(UUID player, int serial) {
        if (merit == null || !active || clock == null || !clock.combatOpen() || closeReason != null || pool.current() <= 0) return false;
        if (!wards.relay(player, serial, clock.remainingMillis())) return false;
        if (!merit.relayed(player, serial)) throw new IllegalStateException("Relay receipts disagree");
        setDirty(); return true;
    }

    /** Freeze timeout/cancellation promises before the first keyed settlement; no later hit can win. */
    public void prepareClose(String reason, String message) {
        if (merit == null || pool == null || pool.current() <= 0) throw new IllegalStateException("Not a closable Merit event");
        if (closeReason != null) return;
        if (!reason.equals("TIMEOUT") && !reason.equals("CANCELLED")) throw new IllegalArgumentException("Invalid close reason");
        if (reason.equals("TIMEOUT") && (clock == null || clock.phase() != WorldBossClock.Phase.EXPIRED)) throw new IllegalStateException("Timeout requires an expired active clock");
        java.util.Objects.requireNonNull(message);
        if (message.length() > 512 || message.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid close message");
        int points = reason.equals("TIMEOUT") ? merit.timeoutReward() : merit.cancellationReward();
        Map<UUID, Integer> amounts = new LinkedHashMap<>();
        if (!testMode) merit.qualifiedPlayers().forEach(player -> amounts.put(player, points));
        freezeRewardPlan(amounts);
        closeReason = reason; closeMessage = message; wards.stop(); setDirty();
    }
    public WorldBossCampaign campaign() { return campaign; }
    WorldheartCollection collection() { return collection; }
    WorldheartCollection collectionView() { return publishedCollection; }
    WorldBossBenefits benefits() { return benefits; }
    WorldBossBenefits benefitsView() { return publishedBenefits; }
    WorldBossSceneLedger scenes() { return scenes; }
    WorldBossSceneLedger scenesView() { return publishedScenes; }
    WorldBossTravel travel() { return travel; }
    void travel(WorldBossTravel value) { travel = java.util.Objects.requireNonNull(value); setDirty(); }
    WorldBossAdmission.Snapshot admissionCheckpoint() { return admissionCheckpoint; }
    void admissionCheckpoint(WorldBossAdmission.Snapshot value) {
        if (!bossId.equals(value.event()) || value.capacity() != 50 || value.breatherMillis() != 15_000) throw new IllegalArgumentException("Admission snapshot does not match the event policy");
        admissionCheckpoint = value; setDirty();
    }
    void scenes(WorldBossSceneLedger value) { value.validate(campaign); scenes = value; setDirty(); }
    void recordScene(WorldBossSceneScript.Kind kind, long atMillis) {
        var event = campaignEvent(); if (event == null || testMode) return;
        scenes(scenes.record(event, kind, atMillis, dimension, x, y, z, sceneRadius()));
    }
    double sceneRadius() { return audienceRadius == null ? WorldBossRules.zoneRadius(this, WorldBossConfigManager.get()) : audienceRadius + 16; }
    void benefits(WorldBossBenefits value) { value.validate(collection); benefits = value; setDirty(); }
    boolean collectionAvailable() { return !isQuarantined() && !unreadableFile; }
    int worldheartPolicy() { return worldheartPolicy; }
    void selectLegacy(UUID player, UUID character) {
        if (isQuarantined()) throw new IllegalStateException("Saved World Boss data needs repair");
        if (active && worldheartPolicy > 0 && pool != null && pool.current() <= 0 && collection.victory(bossId) == null) throw new IllegalArgumentException("Victory rewards are being secured. Try changing projects after settlement.");
        if (character != null && (campaign.active() != null && campaign.active().characterId().equals(character)
                || campaign.seasons().values().stream().noneMatch(season -> season.characterId().equals(character)))) throw new IllegalArgumentException("Choose an archived character");
        collection = collection.select(player, character); setDirty();
    }
    void restoreCollection(WorldheartCollection previous) { collection = previous; setDirty(); }
    /** The journal is the entitlement authority; ownership is a read-only projection of this promise. */
    boolean freezeWorldheartVictory(long atMillis) {
        if (worldheartPolicy == 0 || testMode) return false;
        var event = campaignEvent();
        if ((worldheartPolicy != 1 && worldheartPolicy != 2) || event == null || (event.status() != WorldBossCampaign.Status.VICTORY && event.status() != WorldBossCampaign.Status.OPEN)
                || pool == null || !pool.isDefeated() || closeReason != null || merit == null) throw new IllegalStateException("Worldheart requires a qualified natural victory");
        Map<UUID, Integer> amounts = new java.util.LinkedHashMap<>(); merit.qualifiedPlayers().forEach(player -> amounts.put(player, merit.victoryReward()));
        if (rewardPlan != null && !rewardPlan.equals(amounts)) throw new IllegalStateException("Worldheart recipients disagree with the frozen reward plan");
        var nextCampaign = event.status() == WorldBossCampaign.Status.VICTORY ? campaign
                : campaign.transition(event.key(), bossId, WorldBossCampaign.Status.VICTORY, "", atMillis);
        var next = collection.grant(bossId, event.characterId(), event.awakening(), atMillis, amounts.keySet(), worldheartPolicy == 2);
        next.validate(nextCampaign);
        if (next == collection && nextCampaign == campaign && rewardPlan != null) return false;
        // Publish all mandatory promise fields together, after every validation succeeds.
        campaign = nextCampaign; rewardPlan = Map.copyOf(amounts); collection = next; setDirty(); return true;
    }
    public void campaign(WorldBossCampaign value) { campaign = java.util.Objects.requireNonNull(value); setDirty(); }
    public WorldBossCampaign.Occurrence campaignEvent() { return eventOccurrence == null ? null : campaign.require(eventOccurrence); }
    public void bindOccurrence(String key) {
        var event = campaign.require(key);
        if (!active || testMode || !event.eventId().equals(bossId) || !event.definition().equals(definitionId)
                || event.status() != WorldBossCampaign.Status.PREPARING) throw new IllegalStateException("Wrong occurrence owner");
        if (merit != null && wards.pylon() == null) throw new IllegalStateException("Live ward pylon is missing");
        eventOccurrence = key; worldheartPolicy = 2; setDirty();
    }
    public List<UUID> openingRoster() { return openingRoster; }
    public void stageOpeningRules(WorldBossOpeningRules rules) {
        if (!active || openingRules != null || clock == null || clock.phase() != WorldBossClock.Phase.PREPARING
                || !definitionId.equals(rules.calibration().definition())) throw new IllegalStateException("Cannot stage opening rules");
        if (!testMode && rules.calibration().evidence().startsWith("TEST_ONLY_")) throw new IllegalStateException("Unmeasured test references cannot enter a live event");
        openingRules = rules;
        merit = new WorldBossMerit(); wards = new WorldBossWards();
        setDirty();
    }

    /** Roster and health change together before the first ACTIVE transition is committed. */
    public void freezeOpening(List<UUID> readyRoster) {
        if (!active || openingRules == null || openingRoster != null || pool == null || pool.totalDamage() != 0 || pool.isDefeated()
                || clock == null || clock.phase() != WorldBossClock.Phase.PREPARING && clock.phase() != WorldBossClock.Phase.REGROUP) {
            throw new IllegalStateException("Opening cannot be frozen");
        }
        List<UUID> roster = List.copyOf(readyRoster);
        if (roster.size() < (testMode ? 1 : 5) || roster.size() > 50 || new java.util.HashSet<>(roster).size() != roster.size()) throw new IllegalArgumentException("Invalid opening roster");
        WorldBossPool fixed = WorldBossPool.fixed(openingRules.calibration().healthFor(roster.size()));
        openingRoster = roster;
        pool = fixed;
        setDirty();
    }
    public void clock(WorldBossClock clock) { this.clock = java.util.Objects.requireNonNull(clock); setDirty(); }
    public void markTestMode() { testMode = true; setDirty(); }
    public boolean isQuarantined() { return recoveryBlocked || journal != null && journal.blocked() || checkpoints != null && checkpoints.failed() || unreadableFile || quarantinedData != null; }
    public Map<UUID, Integer> rewardPlan() { return rewardPlan; }
    public boolean isPaused(UUID player) { return paused.contains(player); }
    public Set<UUID> pausedPlayers() { return Set.copyOf(paused); }
    Double audienceRadius() { return audienceRadius; }
    boolean observing(UUID player) { return observing.contains(player); }
    void configureAudience(double radius) {
        if (!active || !arena || audienceRadius != null || !Double.isFinite(radius) || radius < 16 || radius > 512) throw new IllegalStateException("Invalid audience geometry transaction");
        audienceRadius = radius; setDirty();
    }
    void watch(UUID player) {
        if (audienceRadius == null) throw new IllegalStateException("This event has no frozen audience geometry");
        if (observing.add(java.util.Objects.requireNonNull(player)) | paused.add(player)) setDirty();
    }

    public boolean pause(UUID player) {
        boolean changed = paused.add(player);
        if (changed) setDirty();
        return changed;
    }

    public boolean resume(UUID player) {
        boolean changed = paused.remove(player) | observing.remove(player);
        if (changed) setDirty();
        return changed;
    }

    /** Freeze recipients and amounts before the first credit, independently of future config reloads. */
    public void freezeRewardPlan(Map<UUID, Integer> amounts) {
        if (rewardPlan != null) throw new IllegalStateException("Reward plan is already frozen");
        amounts.forEach((id, amount) -> {
            if (id == null || amount == null || amount < 0) throw new IllegalArgumentException("Invalid reward plan");
        });
        rewardPlan = Map.copyOf(amounts);
        setDirty();
    }

    // ---- writes (each marks the state dirty) ----

    public void begin(UUID bossId, String definitionId, String dimension, double x, double y, double z,
                      long startedAtMillis, long endsAtMillis, WorldBossPool pool, boolean arena) {
        if (isQuarantined()) throw new IllegalStateException("World Boss data requires operator repair");
        if (pool.isFixed()) throw new IllegalArgumentException("Fixed health must be installed through the opening transaction");
        this.active = true;
        this.bossId = bossId;
        this.definitionId = definitionId;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.startedAtMillis = startedAtMillis;
        this.endsAtMillis = endsAtMillis;
        this.pool = pool;
        this.arena = arena;
        this.defeatAnnounced = false;
        this.rewardsPaid = false;
        this.paidPlayers.clear();
        this.testMode = false;
        this.rewardPlan = null;
        this.clock = null;
        this.openingRules = null;
        this.merit = null; this.wards = null; this.closeReason = null; this.closeMessage = null;
        this.eventOccurrence = null;
        this.admissionCheckpoint = new WorldBossAdmission(this.bossId, 50, 15_000, java.util.Set.of()).snapshot(Math.max(1, startedAtMillis));
        this.worldheartPolicy = 0;
        this.openingRoster = null;
        paused.clear();
        observing.clear(); audienceRadius = null;
        setDirty();
    }

    public void markDefeatAnnounced() { defeatAnnounced = true; setDirty(); }

    public void markRewardsPaid() { rewardsPaid = true; setDirty(); }

    /** Marks this player's reward as being paid. @return false if it already was, in which case it must not be paid again */
    public boolean markPlayerPaid(UUID player) {
        boolean added = paidPlayers.add(player);
        if (added) setDirty();
        return added;
    }

    /** The pool changed (damage or growth); the caller flushes on its own schedule. */
    public void touch() { setDirty(); }

    /** The event is over. The pool and flags stay on disk for the record until the next begin. */
    public void finish(long nowMillis) { active = false; lastEndedAtMillis = nowMillis; setDirty(); }

    /** Failed final persistence must not release the slot while mandatory settlement closure retries. */
    public void keepSettlementOpen(long previousEndedAtMillis) {
        if (merit == null || !rewardsPaid || closeReason == null && !pool.isDefeated()) throw new IllegalStateException("No secured outcome to retry");
        active = true; lastEndedAtMillis = previousEndedAtMillis; setDirty();
    }

    // ---- disk ----

    static WorldBossState load(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            WorldBossState state = decode(tag);
            state.loadedFromDisk = true;
            state.publishedCollection = state.collection;
            state.publishedBenefits = state.benefits;
            state.publishedScenes = state.scenes;
            return state;
        } catch (RuntimeException invalid) {
            WorldBossState quarantined = new WorldBossState();
            quarantined.loadedFromDisk = true;
            quarantined.quarantinedData = tag.copy();
            return quarantined;
        }
    }

    private static WorldBossState decode(CompoundTag tag) {
        WorldBossState state = new WorldBossState();
        int schema = tag.contains("schema") ? tag.getInt("schema") : 1;
        int rootSchema = schema;
        boolean contributionFormat = schema == 5;
        if (schema == 6 || schema == 7 || schema == 8 || schema == 9) {
            require(tag, "collection", Tag.TAG_COMPOUND); require(tag, "worldheart_policy", Tag.TAG_INT);
            state.collection = WorldheartCollectionCodec.read(tag.getCompound("collection")); state.worldheartPolicy = tag.getInt("worldheart_policy");
            if (state.worldheartPolicy < 0 || state.worldheartPolicy > (schema >= 7 ? 2 : 1)) throw new IllegalArgumentException("Unknown Worldheart policy");
        } else if (tag.contains("collection") || tag.contains("worldheart_policy")) throw new IllegalArgumentException("Worldheart data in an old envelope");
        if (schema == 7 || schema == 8 || schema == 9) { require(tag, "benefits", Tag.TAG_COMPOUND); state.benefits = WorldBossBenefits.load(tag.getCompound("benefits")); }
        else if (tag.contains("benefits")) throw new IllegalArgumentException("Benefits in an old envelope");
        if (schema == 8 || schema == 9) { require(tag, "scenes", Tag.TAG_COMPOUND); state.scenes = WorldBossSceneLedger.load(tag.getCompound("scenes")); }
        else if (tag.contains("scenes")) throw new IllegalArgumentException("Scenes in an old envelope");
        if (schema == 9) { require(tag, "travel", Tag.TAG_COMPOUND); state.travel = WorldBossTravel.load(tag.getCompound("travel")); }
        else if (tag.contains("travel") || tag.contains("admission")) throw new IllegalArgumentException("Participation data in an old envelope");
        if (schema == 4 || schema == 5 || schema == 6 || schema == 7 || schema == 8 || schema == 9) {
            require(tag, "campaign", Tag.TAG_COMPOUND);
            state.campaign = WorldBossCampaignCodec.read(tag.getCompound("campaign"));
            require(tag, "event_format", Tag.TAG_INT);
            schema = tag.getInt("event_format");
        }
        if (rootSchema < 6 && (schema == 4 && !contributionFormat || contributionFormat && schema != 4)) throw new IllegalArgumentException("Invalid contribution format envelope");
        state.collection.validate(state.campaign);
        state.benefits.validate(state.collection);
        state.scenes.validate(state.campaign);
        if (state.worldheartPolicy != 0 && schema != 4) throw new IllegalArgumentException("Worldheart policy lacks contribution rules");
        if (schema != 1 && schema != 2 && schema != 3 && schema != 4) throw new IllegalArgumentException("Unknown World Boss schema");
        if (schema >= 2 && !tag.contains("event_clock", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Missing timed-event clock");
        if (schema >= 3 && !tag.contains("opening_rules", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Missing opening rules");
        if (schema >= 3) {
            require(tag, "active", Tag.TAG_BYTE);
            require(tag, "test_mode", Tag.TAG_BYTE);
            require(tag, "pool_defeated", Tag.TAG_BYTE);
        }
        state.active = tag.getBoolean("active");
        state.lastEndedAtMillis = tag.getLong("last_ended");
        if (!tag.hasUUID("boss")) {
            if (tag.contains("audience")) throw new IllegalArgumentException("Audience state has no event");
            if (state.active || schema >= 2 || state.worldheartPolicy != 0) throw new IllegalArgumentException("Timed World Boss has no identity");
            if (state.campaign.occurrences().values().stream().anyMatch(value -> value.status() == WorldBossCampaign.Status.OPEN)) throw new IllegalArgumentException("Open occurrence has no event");
            return state;
        }
        state.bossId = tag.getUUID("boss");
        state.definitionId = tag.getString("definition");
        state.dimension = tag.getString("dimension");
        state.x = tag.getDouble("x");
        state.y = tag.getDouble("y");
        state.z = tag.getDouble("z");
        if (!Double.isFinite(state.x) || !Double.isFinite(state.y) || !Double.isFinite(state.z)) {
            throw new IllegalArgumentException("Invalid World Boss position");
        }
        state.startedAtMillis = tag.getLong("started");
        if (rootSchema == 9) { require(tag, "admission", Tag.TAG_COMPOUND); state.admissionCheckpoint(WorldBossAdmissionCodec.read(tag.getCompound("admission"))); }
        else state.admissionCheckpoint = new WorldBossAdmission(state.bossId, 50, 15_000, state.paused, false).snapshot(Math.max(1, state.startedAtMillis));
        state.endsAtMillis = tag.getLong("ends");
        state.defeatAnnounced = tag.getBoolean("defeat_announced");
        state.rewardsPaid = tag.getBoolean("rewards_paid");
        ListTag paid = tag.getList("paid_players", Tag.TAG_COMPOUND);
        for (int i = 0; i < paid.size(); i++) {
            CompoundTag item = paid.getCompound(i);
            if (item.hasUUID("player")) state.paidPlayers.add(item.getUUID("player"));
        }
        state.arena = tag.getBoolean("arena");
        state.testMode = tag.getBoolean("test_mode");
        if (tag.contains("event_occurrence")) {
            require(tag, "event_occurrence", Tag.TAG_STRING);
            state.eventOccurrence = tag.getString("event_occurrence");
            var event = state.campaign.require(state.eventOccurrence);
            if (state.testMode || !event.eventId().equals(state.bossId) || !event.definition().equals(state.definitionId)
                    || event.status() == WorldBossCampaign.Status.PLANNED
                    || state.active && (event.status() == WorldBossCampaign.Status.CLOSED || event.status() == WorldBossCampaign.Status.SKIPPED)) {
                throw new IllegalArgumentException("Saved event/occurrence mismatch");
            }
        }
        if (tag.contains("opening_rules")) {
            if (schema < 3) throw new IllegalArgumentException("Opening rules require schema 3+");
            CompoundTag rules = tag.getCompound("opening_rules");
            require(rules, "version", Tag.TAG_INT);
            require(rules, "calibration_json", Tag.TAG_STRING);
            require(rules, "duel_level", Tag.TAG_INT);
            require(rules, "duel_health", Tag.TAG_LONG);
            require(rules, "duel_timeout", Tag.TAG_INT);
            if (rules.getInt("version") != 1 || rules.getString("calibration_json").length() > 4096) throw new IllegalArgumentException("Invalid opening rules version/data");
            ListTag bans = compoundList(rules, "boss_bans");
            java.util.ArrayList<String> moves = new java.util.ArrayList<>();
            for (int i = 0; i < bans.size(); i++) {
                require(bans.getCompound(i), "move", Tag.TAG_STRING);
                moves.add(bans.getCompound(i).getString("move"));
            }
            state.openingRules = new WorldBossOpeningRules(WorldBossCalibration.fromJson(
                    com.google.gson.JsonParser.parseString(rules.getString("calibration_json")).getAsJsonObject()),
                    rules.getInt("duel_level"), rules.getLong("duel_health"), rules.getInt("duel_timeout"), moves);
            if (!state.definitionId.equals(state.openingRules.calibration().definition())) throw new IllegalArgumentException("Opening definition mismatch");
            if (!state.testMode && state.openingRules.calibration().evidence().startsWith("TEST_ONLY_")) throw new IllegalArgumentException("Live state contains a test-only reference");
            if (tag.contains("opening_roster")) {
                ListTag roster = compoundList(tag, "opening_roster");
                java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
                for (int i = 0; i < roster.size(); i++) ids.add(roster.getCompound(i).getUUID("player"));
                if (ids.size() < (state.testMode ? 1 : 5) || ids.size() > 50 || new java.util.HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Invalid saved opening roster");
                state.openingRoster = List.copyOf(ids);
            }
        }
        if (tag.contains("event_clock")) {
            if (!tag.contains("event_clock", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid event clock tag");
            CompoundTag timing = tag.getCompound("event_clock");
            if (!timing.contains("version", Tag.TAG_INT) || timing.getInt("version") != 1
                    || !timing.contains("phase", Tag.TAG_STRING) || !timing.contains("reason", Tag.TAG_STRING)
                    || !timing.contains("remaining_ms", Tag.TAG_LONG) || !timing.contains("phase_remaining_ms", Tag.TAG_LONG)
                    || !timing.contains("checkpoint_ms", Tag.TAG_LONG) || !timing.contains("recoveries", Tag.TAG_INT)
                    || !timing.contains("offline_ms", Tag.TAG_LONG) || !timing.contains("clean_stop", Tag.TAG_BYTE)) {
                throw new IllegalArgumentException("Incomplete event clock");
            }
            state.clock = new WorldBossClock(WorldBossClock.Phase.valueOf(timing.getString("phase")),
                    timing.getLong("remaining_ms"), timing.getLong("phase_remaining_ms"), timing.getLong("checkpoint_ms"),
                    timing.getInt("recoveries"), timing.getLong("offline_ms"), timing.getBoolean("clean_stop"),
                    WorldBossClock.Reason.valueOf(timing.getString("reason")));
            if (state.clock.checkpointMillis() < state.startedAtMillis) throw new IllegalArgumentException("Clock predates event");
        }
        ListTag pausedPlayers = tag.getList("paused", Tag.TAG_COMPOUND);
        for (int i = 0; i < pausedPlayers.size(); i++) state.paused.add(pausedPlayers.getCompound(i).getUUID("player"));
        if (tag.contains("audience")) {
            if (rootSchema != 9 || !state.arena) throw new IllegalArgumentException("Audience state has no supported arena envelope");
            require(tag, "audience", Tag.TAG_COMPOUND); var audience = tag.getCompound("audience");
            require(audience, "version", Tag.TAG_INT); require(audience, "radius", Tag.TAG_DOUBLE);
            if (audience.getInt("version") != 1 || !Double.isFinite(audience.getDouble("radius")) || audience.getDouble("radius") < 16 || audience.getDouble("radius") > 512) throw new IllegalArgumentException("Invalid saved audience geometry");
            state.audienceRadius = audience.getDouble("radius");
            for (Tag value : compoundList(audience, "watching")) {
                CompoundTag row = (CompoundTag) value;
                if (!row.hasUUID("player") || !state.paused.contains(row.getUUID("player")) || !state.observing.add(row.getUUID("player"))) throw new IllegalArgumentException("Invalid/duplicate observer intent");
            }
        }
        if (tag.getBoolean("reward_plan_frozen")) {
            Map<UUID, Integer> plan = new LinkedHashMap<>();
            ListTag rewards = tag.getList("reward_plan", Tag.TAG_COMPOUND);
            for (int i = 0; i < rewards.size(); i++) {
                CompoundTag row = rewards.getCompound(i);
                if (!row.hasUUID("player") || !row.contains("amount", Tag.TAG_INT)) {
                    throw new IllegalArgumentException("Incomplete saved reward plan");
                }
                UUID player = row.getUUID("player");
                int amount = row.getInt("amount");
                if (amount < 0 || plan.putIfAbsent(player, amount) != null) throw new IllegalArgumentException("Invalid saved reward plan");
            }
            state.rewardPlan = Map.copyOf(plan);
        }
        Map<UUID, Float> damage = new LinkedHashMap<>();
        ListTag list = tag.getList("damage", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            damage.put(entry.getUUID("player"), entry.getFloat("amount"));
        }
        float max = tag.getFloat("pool_max");
        if (max > 0f && tag.contains("pool_current", Tag.TAG_FLOAT)) {
            state.pool = new WorldBossPool(max, tag.getFloat("pool_current"), tag.getBoolean("pool_defeated"), damage);
        }
        if (state.active && state.pool == null) throw new IllegalArgumentException("Active World Boss has no valid pool");
        if (tag.contains("fixed_health")) {
            if (schema < 3 || state.openingRoster == null) throw new IllegalArgumentException("Fixed health requires a frozen opening");
            CompoundTag fixed = tag.getCompound("fixed_health");
            require(fixed, "version", Tag.TAG_INT);
            require(fixed, "max_units", Tag.TAG_LONG);
            require(fixed, "current_units", Tag.TAG_LONG);
            if (fixed.getInt("version") != 1) throw new IllegalArgumentException("Unknown fixed health version");
            Map<UUID, Long> units = new LinkedHashMap<>();
            Map<UUID, BigDecimal> fractions = new LinkedHashMap<>();
            ListTag entries = compoundList(fixed, "damage_units");
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag row = entries.getCompound(i);
                require(row, "units", Tag.TAG_LONG);
                if (units.putIfAbsent(row.getUUID("player"), row.getLong("units")) != null) throw new IllegalArgumentException("Duplicate fixed damage player");
            }
            ListTag carries = compoundList(fixed, "fractional_units");
            for (int i = 0; i < carries.size(); i++) {
                CompoundTag row = carries.getCompound(i);
                require(row, "carry", Tag.TAG_STRING);
                String raw = row.getString("carry");
                if (raw.length() > 80 || fractions.putIfAbsent(row.getUUID("player"), new BigDecimal(raw)) != null) throw new IllegalArgumentException("Invalid fixed carry player/data");
            }
            var snapshot = new WorldBossFixedHealth.Snapshot(fixed.getLong("max_units"), fixed.getLong("current_units"), units, fractions);
            long expected = state.openingRules.calibration().healthFor(state.openingRoster.size()) * WorldBossFixedHealth.UNITS_PER_HP;
            if (snapshot.maxUnits() != expected) throw new IllegalArgumentException("Opening health does not match its formula");
            state.pool = new WorldBossPool(snapshot, tag.getBoolean("pool_defeated"));
        } else if (state.openingRoster != null) throw new IllegalArgumentException("Frozen roster has no fixed health ledger");
        if (schema == 4) {
            require(tag, "merit", Tag.TAG_COMPOUND); require(tag, "wards", Tag.TAG_COMPOUND);
            state.merit = WorldBossContributionCodec.merit(tag.getCompound("merit")); state.wards = WorldBossContributionCodec.wards(tag.getCompound("wards"));
            if (state.wards.pylon() != null && !state.wards.pylon().dimension().equals(state.dimension)
                    || state.campaignEvent() != null && state.wards.pylon() == null) throw new IllegalArgumentException("Invalid saved ward layout");
            if (tag.contains("close_reason")) {
                require(tag, "close_reason", Tag.TAG_STRING); require(tag, "close_message", Tag.TAG_STRING);
                state.closeReason = tag.getString("close_reason"); state.closeMessage = tag.getString("close_message");
                if ((!state.closeReason.equals("TIMEOUT") && !state.closeReason.equals("CANCELLED")) || state.rewardPlan == null || state.pool.current() <= 0) throw new IllegalArgumentException("Invalid close promise");
                if (state.closeReason.equals("TIMEOUT") && state.clock.phase() != WorldBossClock.Phase.EXPIRED || state.closeMessage.length() > 512
                        || state.closeMessage.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid close timing/message");
            }
            Set<WorldBossMerit.Focus> focusReceipts = new LinkedHashSet<>();
            Set<WorldBossMerit.Relay> relayReceipts = new LinkedHashSet<>();
            var windows = new java.util.ArrayList<>(state.wards.snapshot().completed()); if (state.wards.active() != null) windows.add(state.wards.active());
            for (var window : windows) {
                window.focuses().forEach(player -> focusReceipts.add(new WorldBossMerit.Focus(player, window.serial())));
                window.relays().forEach(player -> relayReceipts.add(new WorldBossMerit.Relay(player, window.serial())));
                for (var turn : window.turns()) { var duel = state.merit.snapshot().duels().get(turn.encounter()); if (duel == null || !duel.player().equals(turn.player()) || duel.lastTurn() < turn.turn()) throw new IllegalArgumentException("Unknown ward combat receipt"); }
            }
            if (!focusReceipts.equals(state.merit.snapshot().focuses())) throw new IllegalArgumentException("Ward/Merit focus receipts disagree");
            if (!relayReceipts.equals(state.merit.snapshot().relays())) throw new IllegalArgumentException("Ward/Merit relay receipts disagree");
            if (!state.testMode && state.pool.isFixed()) {
                Set<UUID> owners = new LinkedHashSet<>(); state.merit.snapshot().duels().values().forEach(duel -> owners.add(duel.player()));
                if (!owners.containsAll(state.pool.fixedSnapshot().damageUnits().keySet()) || !owners.containsAll(state.pool.fixedSnapshot().fractionalUnits().keySet())) throw new IllegalArgumentException("Fixed damage has no encounter receipt");
            }
            if (state.rewardPlan != null) {
                int reward = state.closeReason != null ? (state.closeReason.equals("TIMEOUT") ? state.merit.timeoutReward() : state.merit.cancellationReward()) : state.merit.victoryReward();
                if (state.closeReason == null && state.pool.current() > 0) throw new IllegalArgumentException("Premature outcome reward plan");
                Map<UUID, Integer> expected = new LinkedHashMap<>(); if (!state.testMode) state.merit.qualifiedPlayers().forEach(player -> expected.put(player, reward));
                if (!expected.equals(state.rewardPlan)) throw new IllegalArgumentException("Reward plan contradicts frozen Merit policy");
            } else if (state.rewardsPaid) throw new IllegalArgumentException("Paid outcome lacks a frozen plan");
        }
        if (state.openingRules != null && state.clock.combatOpen() && state.openingRoster == null) throw new IllegalArgumentException("Unfrozen active opening");
        var campaignEvent = state.campaignEvent();
        if (state.worldheartPolicy == 0 && state.collection.victory(state.bossId) != null) throw new IllegalArgumentException("Worldheart grant has no event policy");
        if (state.worldheartPolicy > 0) {
            if (campaignEvent == null || state.testMode || state.merit == null) throw new IllegalArgumentException("Worldheart policy has no live occurrence");
            var grant = state.collection.victory(state.bossId);
            if (grant != null && (grant.blessing() != null) != (state.worldheartPolicy == 2)) throw new IllegalArgumentException("Blessing contradicts frozen policy");
            if (grant != null && (state.rewardPlan == null || !grant.awards().keySet().equals(state.rewardPlan.keySet()))) throw new IllegalArgumentException("Worldheart recipients disagree with the frozen outcome");
            if (campaignEvent.status() == WorldBossCampaign.Status.VICTORY && (state.rewardsPaid || state.defeatAnnounced || !state.active) && grant == null) throw new IllegalArgumentException("Completed victory lacks mandatory Worldheart grant");
        }
        if (campaignEvent != null) {
            if (campaignEvent.status() == WorldBossCampaign.Status.VICTORY
                    && (state.pool == null || state.pool.current() > 0 || !state.active && !state.rewardsPaid)) throw new IllegalArgumentException("Invalid committed campaign victory");
            if (campaignEvent.status() == WorldBossCampaign.Status.OPEN && (!state.active || state.openingRoster == null
                    || state.clock == null || state.clock.phase() == WorldBossClock.Phase.PREPARING)) throw new IllegalArgumentException("Invalid open campaign encounter");
        }
        for (var event : state.campaign.occurrences().values()) {
            if (event.status() == WorldBossCampaign.Status.OPEN && (campaignEvent == null || !event.key().equals(campaignEvent.key()))) {
                throw new IllegalArgumentException("Orphaned open campaign occurrence");
            }
            if (event.status() == WorldBossCampaign.Status.PREPARING && state.active
                    && (campaignEvent == null || !event.key().equals(campaignEvent.key()))) throw new IllegalArgumentException("Reserved occurrence conflicts with active event");
        }
        return state;
    }

    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing/invalid " + key);
    }

    private static ListTag compoundList(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_LIST);
        ListTag list = (ListTag) tag.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid rows in " + key);
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unreadableFile) throw new IllegalStateException("Cannot overwrite unreadable World Boss state");
        if (recoveryBlocked || journal != null && journal.blocked() || checkpoints != null && checkpoints.failed()) throw new IllegalStateException("Cannot serialize a blocked World Boss recovery authority");
        if (quarantinedData != null) return quarantinedData.copy();
        int eventFormat = merit != null ? 4 : openingRules != null ? 3 : clock == null ? 1 : 2;
        tag.putInt("schema", 9); tag.putInt("event_format", eventFormat); tag.put("campaign", WorldBossCampaignCodec.write(campaign));
        tag.put("travel", travel.save());
        tag.put("scenes", scenes.save());
        tag.put("benefits", benefits.save());
        tag.put("collection", WorldheartCollectionCodec.write(collection)); tag.putInt("worldheart_policy", worldheartPolicy);
        tag.putBoolean("active", active);
        tag.putLong("last_ended", lastEndedAtMillis);
        if (pool == null) return tag;
        if (admissionCheckpoint == null) throw new IllegalStateException("Event has no admission checkpoint");
        tag.put("admission", WorldBossAdmissionCodec.write(admissionCheckpoint));
        tag.putUUID("boss", bossId);
        tag.putString("definition", definitionId);
        tag.putString("dimension", dimension);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putLong("started", startedAtMillis);
        tag.putLong("ends", endsAtMillis);
        tag.putBoolean("defeat_announced", defeatAnnounced);
        tag.putBoolean("rewards_paid", rewardsPaid);
        ListTag paid = new ListTag();
        for (UUID id : paidPlayers) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", id);
            paid.add(item);
        }
        tag.put("paid_players", paid);
        tag.putBoolean("arena", arena);
        tag.putBoolean("test_mode", testMode);
        if (merit != null) { tag.put("merit", WorldBossContributionCodec.merit(merit)); tag.put("wards", WorldBossContributionCodec.wards(wards)); }
        if (closeReason != null) { tag.putString("close_reason", closeReason); tag.putString("close_message", closeMessage); }
        if (eventOccurrence != null) tag.putString("event_occurrence", eventOccurrence);
        if (openingRules != null) {
            CompoundTag rules = new CompoundTag();
            rules.putInt("version", 1);
            rules.putString("calibration_json", openingRules.calibration().toJson().toString());
            rules.putInt("duel_level", openingRules.duelLevel());
            rules.putLong("duel_health", openingRules.duelHealth());
            rules.putInt("duel_timeout", openingRules.duelTimeoutSeconds());
            ListTag bans = new ListTag();
            for (String move : openingRules.bossBannedMoves()) { CompoundTag row = new CompoundTag(); row.putString("move", move); bans.add(row); }
            rules.put("boss_bans", bans);
            tag.put("opening_rules", rules);
        }
        if (openingRoster != null) {
            ListTag roster = new ListTag();
            for (UUID player : openingRoster) { CompoundTag row = new CompoundTag(); row.putUUID("player", player); roster.add(row); }
            tag.put("opening_roster", roster);
        }
        var snapshot = pool.fixedSnapshot();
        if (snapshot != null) {
            CompoundTag fixed = new CompoundTag();
            fixed.putInt("version", 1);
            fixed.putLong("max_units", snapshot.maxUnits());
            fixed.putLong("current_units", snapshot.currentUnits());
            ListTag units = new ListTag();
            snapshot.damageUnits().forEach((player, amount) -> { CompoundTag row = new CompoundTag(); row.putUUID("player", player); row.putLong("units", amount); units.add(row); });
            fixed.put("damage_units", units);
            ListTag carries = new ListTag();
            snapshot.fractionalUnits().forEach((player, amount) -> { CompoundTag row = new CompoundTag(); row.putUUID("player", player); row.putString("carry", amount.toPlainString()); carries.add(row); });
            fixed.put("fractional_units", carries);
            tag.put("fixed_health", fixed);
        }
        if (clock != null) {
            CompoundTag timing = new CompoundTag();
            timing.putInt("version", 1);
            timing.putString("phase", clock.phase().name());
            timing.putString("reason", clock.reason().name());
            timing.putLong("remaining_ms", clock.remainingMillis());
            timing.putLong("phase_remaining_ms", clock.phaseRemainingMillis());
            timing.putLong("checkpoint_ms", clock.checkpointMillis());
            timing.putInt("recoveries", clock.recoveries());
            timing.putLong("offline_ms", clock.offlineMillis());
            timing.putBoolean("clean_stop", clock.cleanStop());
            tag.put("event_clock", timing);
        }
        ListTag pausedPlayers = new ListTag();
        for (UUID player : paused) {
            CompoundTag row = new CompoundTag();
            row.putUUID("player", player);
            pausedPlayers.add(row);
        }
        tag.put("paused", pausedPlayers);
        if (audienceRadius != null) {
            CompoundTag audience = new CompoundTag(); audience.putInt("version", 1); audience.putDouble("radius", audienceRadius);
            ListTag viewers = new ListTag(); for (UUID id : observing) { CompoundTag row = new CompoundTag(); row.putUUID("player", id); viewers.add(row); }
            audience.put("watching", viewers); tag.put("audience", audience);
        }
        tag.putBoolean("reward_plan_frozen", rewardPlan != null);
        if (rewardPlan != null) {
            ListTag rewards = new ListTag();
            rewardPlan.forEach((player, amount) -> {
                CompoundTag row = new CompoundTag();
                row.putUUID("player", player);
                row.putInt("amount", amount);
                rewards.add(row);
            });
            tag.put("reward_plan", rewards);
        }
        tag.putFloat("pool_max", pool.max());
        tag.putFloat("pool_current", pool.current());
        tag.putBoolean("pool_defeated", pool.isDefeated());
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Float> entry : pool.contributions().entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", entry.getKey());
            item.putFloat("amount", entry.getValue());
            list.add(item);
        }
        tag.put("damage", list);
        return tag;
    }
}
