package com.cobbleraids.catching;

import com.cobbleraids.config.SavedDataFlush;
import com.cobbleraids.config.DurableSavedData;
import com.cobbleraids.reward.points.PointCreditReceipt;
import java.io.IOException;
import com.cobbleraids.perf.RaidPerf;
import com.cobbleraids.RaidLog;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.config.RaidRarityTier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Live raid history for every player, persisted to the overworld's data storage.
 *
 * <p>Same shape as PendingRewardStore: a static map is the working copy, and the SavedData below is
 * its mirror on disk. Records must outlive a restart or every catch mechanic built on them resets
 * with the server, which is the one failure that would make the whole idea untrustworthy.
 */
public final class RaidPlayerRecords extends DurableSavedData {
    private static final String FILE_ID = "cobbleraids_player_records";
    private static final Map<UUID, RaidPlayerRecord> LIVE = new ConcurrentHashMap<>();
    private static MinecraftServer activeServer;

    private Map<UUID, RaidPlayerRecord> loaded = new LinkedHashMap<>();
    private Map<UUID, Map<UUID, PointCreditReceipt>> creditReceipts = new LinkedHashMap<>();
    private boolean creditLedgerValid = true;
    private int creditSchema = 1;
    private Tag unreadableReceipts;
    private boolean loadedFromDisk;
    private boolean originChecked;
    private boolean unreadableFile;
    private CompoundTag unreadableRecordData;
    private UUID worldBossRecovery;
    private Map<UUID, String> worldBossPromises = new LinkedHashMap<>();
    private java.nio.file.Path recoveryWitness;
    private boolean advanceRecoveryWitness;
    private boolean recoveryFailed;

    // ---------------------------------------------------------------- live access

    public static RaidPlayerRecord get(UUID playerId) {
        return LIVE.getOrDefault(playerId, RaidPlayerRecord.EMPTY);
    }

    public static Map<UUID, RaidPlayerRecord> all() {
        return Map.copyOf(LIVE);
    }

    /**
     * Records a won raid for every victor and persists once.
     *
     * <p>Deliberately takes the whole party rather than one player at a time. Persisting copies the
     * entire record map, which grows with every distinct player the server has ever seen -- so a
     * four-player victory used to do four full copies of a map that keeps growing all season, on the
     * server thread, inside raid finalization. Once per raid is the same durability for a quarter of
     * the work, and it stays a quarter as the map grows.
     */
    public static void recordWins(MinecraftServer server, RaidRarityTier tier,
                                  ResourceLocation definitionId, Map<UUID, Double> contributionByPlayer) {
        ensureLoaded(server);
        if (contributionByPlayer.isEmpty()) return;
        for (Map.Entry<UUID, Double> entry : contributionByPlayer.entrySet()) {
            double contribution = entry.getValue();
            LIVE.merge(entry.getKey(), RaidPlayerRecord.EMPTY.withWin(tier, definitionId, contribution),
                    (existing, ignored) -> existing.withWin(tier, definitionId, contribution));
        }
        persist(server);
    }

    public static void recordCatch(MinecraftServer server, UUID playerId) {
        ensureLoaded(server);
        LIVE.merge(playerId, RaidPlayerRecord.EMPTY.withCatch(),
                (existing, ignored) -> existing.withCatch());
        persist(server);
    }

    private static void persist(MinecraftServer server) {
        if (server == null) return;
        get(server).update(LIVE);
    }

    /**
     * Marks dirty <em>and</em> writes, for the few changes that must not be lost to a crash.
     *
     * <p>update() only sets the dirty flag, which SavedData honours at the next autosave. That is
     * fine for a win count. It is not fine for anything paired with an item already in a player's
     * inventory, because inventories reach disk on logout and this does not -- the same gap that
     * let a consumed reward claim come back.
     */
    private static void persistNow(MinecraftServer server) {
        if (server == null) return;
        persist(server);
        long started = RaidPerf.start();
        RaidFaultBarrier.guard("player-records-flush", () -> {
            try {
                SavedDataFlush.flush(server, get(server), FILE_ID);
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
        RaidPerf.stop("flush:player-records", started);
    }

    // ---------------------------------------------------------------- lifecycle

    public static void onServerStarted(MinecraftServer server) {
        if (activeServer == server) return;
        // Keep the SavedData mirror intact: an autosave during startup must not serialize an
        // emptied transfer map before the first ordinary mutation repopulates it.
        Map<UUID, RaidPlayerRecord> restored = new LinkedHashMap<>(get(server).loaded);
        LIVE.clear();
        LIVE.putAll(restored);
        activeServer = server;
        if (!LIVE.isEmpty()) {
            RaidLog.info("Restored raid records for " + LIVE.size() + " player(s).");
        }
    }

    /** Cleared with everything else per-server; see CobbleRaids' SERVER_STOPPED block. */
    public static void onServerStopped() {
        LIVE.clear();
        activeServer = null;
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (server != null && activeServer != server) onServerStarted(server);
    }

    // ---------------------------------------------------------------- persistence

    /**
     * Records one mega-capable raid claim, and whether it produced a stone.
     *
     * <p>The counter only moves on raids that could have dropped one: counting a Pidgeot raid
     * against a player's bad luck would make the guarantee mean nothing, since 30% of starter
     * spawns and nearly every legendary carry no stone at all.
     */
    public static void recordMegaCapableClaim(MinecraftServer server, UUID playerId, boolean gotStone) {
        ensureLoaded(server);
        LIVE.compute(playerId, (ignored, existing) -> {
            RaidPlayerRecord current = existing == null ? RaidPlayerRecord.EMPTY : existing;
            return new RaidPlayerRecord(current.raidsWon(), current.winsByTier(),
                    current.defeatsBySpecies(), current.totalContribution(), current.bossesCaught(),
                    gotStone ? 0 : current.raidsSinceMegaStone() + 1, current.raidPoints(),
                    current.purchases(), current.unlockedTitles(), current.selectedTitle(), current.pointsEarned());
        });
        persist(server);
    }

    /**
     * Credits Raid Points and persists. Returns the new balance.
     *
     * <p>Public because the shop will need the other half of this, and a currency with no spend
     * path is not a currency -- see RaidPointsStore, which is the surface both sides use.
     */
    public static int addPoints(MinecraftServer server, UUID playerId, int delta) {
        ensureLoaded(server); // Startup callbacks may credit before the ordinary records listener runs.
        RaidPlayerRecord updated = LIVE.compute(playerId, (ignored, existing) ->
                (existing == null ? RaidPlayerRecord.EMPTY : existing).withPoints(delta));
        persist(server);
        return updated.raidPoints();
    }

    /**
     * Credits Raid Points from capped content: as much of {@code amount} as {@code source}'s daily cap
     * still allows, counted against today in the same write as the balance, so a crash cannot pay an
     * award without counting it. Returns what was actually credited, which may be 0.
     */
    public static int addCappedPoints(MinecraftServer server, UUID playerId, int amount,
                                      com.cobbleraids.reward.points.RaidPointsSource source, int cap, long today) {
        int[] credited = { 0 };
        LIVE.compute(playerId, (ignored, existing) -> {
            RaidPlayerRecord current = existing == null ? RaidPlayerRecord.EMPTY : existing;
            int allowed = com.cobbleraids.reward.points.RaidPointsDailyCap.allowance(
                    current.pointsEarnedOn(source, today), cap, amount);
            credited[0] = allowed;
            return allowed <= 0 ? current : current.withPoints(allowed).withPointsEarned(source, today, allowed);
        });
        if (credited[0] > 0) persist(server);
        return credited[0];
    }

    /**
     * Counts Raid Points against {@code source}'s daily cap without crediting them: a won raid's claim fixes its points
     * on the day of the win and pays them when claimed. Returns how much of {@code amount} the cap allowed.
     */
    public static int reserveCappedPoints(MinecraftServer server, UUID playerId, int amount,
                                          com.cobbleraids.reward.points.RaidPointsSource source, int cap, long today) {
        int[] allowed = { 0 };
        LIVE.compute(playerId, (ignored, existing) -> {
            RaidPlayerRecord current = existing == null ? RaidPlayerRecord.EMPTY : existing;
            allowed[0] = com.cobbleraids.reward.points.RaidPointsDailyCap.allowance(
                    current.pointsEarnedOn(source, today), cap, amount);
            return allowed[0] <= 0 ? current : current.withPointsEarned(source, today, allowed[0]);
        });
        if (allowed[0] > 0) persist(server);
        return allowed[0];
    }

    /**
     * Persists the whole payout's balances and deduplication receipts before publishing either.
     * Replaying the same event cannot pay twice. Amounts are frozen by the caller's durable plan.
     * Balance-cap remainders stay in the receipt and can be claimed after points are spent.
     */
    public static Map<UUID, Integer> creditOnce(MinecraftServer server, UUID payoutId,
                                               Map<UUID, Integer> amounts) throws IOException {
        if (!server.isSameThread()) throw new IllegalStateException("Raid Points credits require the server thread");
        ensureLoaded(server);
        return get(server).settleCredits(payoutId, amounts, LIVE,
                snapshot -> snapshot.flushStrict(server, FILE_ID));
    }

    /** Only promises already secured by the world boss authority may enter this scoped path. */
    public static Map<UUID, Integer> creditWorldBossOnce(MinecraftServer server, UUID installation, UUID payoutId,
                                                       Map<UUID, Integer> amounts) throws IOException {
        if (!server.isSameThread()) throw new IllegalStateException("Raid Points credits require the server thread");
        ensureLoaded(server);
        return get(server).settleWorldBossCredits(installation, payoutId, amounts, LIVE,
                snapshot -> snapshot.flushStrict(server, FILE_ID));
    }

    public record WorldBossRecovery(UUID installation, Map<UUID, String> promises) {
        public WorldBossRecovery { promises = Map.copyOf(promises); }
    }
    public static WorldBossRecovery worldBossRecovery(MinecraftServer server) throws IOException {
        ensureLoaded(server); RaidPlayerRecords store = get(server); store.requireCreditLedger();
        if (store.worldBossRecovery != null) {
            store.verifyWorldBossWitness(recoveryWitnessPath());
        } else if (!java.nio.file.Files.notExists(recoveryWitnessPath())) {
            store.recoveryFailed = true;
            throw new IOException("Raid Points recovery identity is missing but its external witness exists");
        }
        return new WorldBossRecovery(store.worldBossRecovery, store.worldBossPromises);
    }
    public static WorldBossRecovery bindWorldBossRecovery(MinecraftServer server, UUID identity) throws IOException {
        WorldBossRecovery old = worldBossRecovery(server);
        if (old.installation() != null) {
            if (!old.installation().equals(identity)) throw new IOException("Raid Points belongs to a different World Boss recovery set");
            return old;
        }
        RaidPlayerRecords store = get(server);
        RaidPlayerRecords candidate = store.creditCandidate(LIVE);
        candidate.worldBossRecovery = identity; candidate.recoveryWitness = recoveryWitnessPath(); candidate.advanceRecoveryWitness = true;
        candidate.setDirty();
        try { candidate.flushStrict(server, FILE_ID); }
        catch (IOException | RuntimeException failure) { store.recoveryFailed = true; throw failure; }
        store.worldBossRecovery = identity; store.recoveryWitness = candidate.recoveryWitness;
        store.loaded = candidate.loaded; store.setDirty(false);
        return new WorldBossRecovery(identity, Map.of());
    }
    private static java.nio.file.Path recoveryWitnessPath() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cobbleraids/worldboss-recovery/points.head");
    }
    void verifyWorldBossWitness(java.nio.file.Path path) throws IOException {
        recoveryWitness = path;
        try { com.cobbleraids.config.RecoveryWitness.verify(path, com.cobbleraids.config.RecoveryWitness.points(worldBossRecovery, worldBossPromises)); }
        catch (IOException failure) { recoveryFailed = true; throw failure; }
    }

    public static int claimPendingCredits(MinecraftServer server, UUID playerId) throws IOException {
        if (!server.isSameThread()) throw new IllegalStateException("Raid Points claims require the server thread");
        ensureLoaded(server);
        RaidPlayerRecords store = get(server);
        store.requireCreditLedger();
        int before = get(playerId).raidPoints();
        for (var event : new LinkedHashMap<>(store.creditReceipts).entrySet()) {
            PointCreditReceipt receipt = event.getValue().get(playerId);
            if (receipt != null && receipt.pending() > 0) {
                store.settleCredits(event.getKey(), Map.of(playerId, receipt.promised()), LIVE,
                        snapshot -> snapshot.flushStrict(server, FILE_ID));
            }
        }
        return get(playerId).raidPoints() - before;
    }

    public static int pendingCredit(MinecraftServer server, UUID payoutId, UUID playerId) {
        ensureLoaded(server);
        RaidPlayerRecords store = get(server);
        store.requireCreditLedger();
        PointCreditReceipt receipt = store.creditReceipts.getOrDefault(payoutId, Map.of()).get(playerId);
        return receipt == null ? 0 : receipt.pending();
    }

    @FunctionalInterface
    interface CreditWriter { void write(RaidPlayerRecords snapshot) throws IOException; }

    Map<UUID, Integer> settleCredits(UUID payoutId, Map<UUID, Integer> amounts,
                                    Map<UUID, RaidPlayerRecord> balances, CreditWriter writer) throws IOException {
        return settle(payoutId, amounts, balances, null, writer);
    }

    Map<UUID, Integer> settleWorldBossCredits(UUID installation, UUID payoutId, Map<UUID, Integer> amounts,
                                             Map<UUID, RaidPlayerRecord> balances, CreditWriter writer) throws IOException {
        if (installation == null || !installation.equals(worldBossRecovery)) throw new IllegalStateException("Unbound World Boss settlement recovery identity");
        return settle(payoutId, amounts, balances, installation, writer);
    }

    private RaidPlayerRecords creditCandidate(Map<UUID, RaidPlayerRecord> balances) {
        RaidPlayerRecords candidate = new RaidPlayerRecords(); candidate.loaded = new LinkedHashMap<>(balances);
        creditReceipts.forEach((event, entries) -> candidate.creditReceipts.put(event, new LinkedHashMap<>(entries)));
        candidate.worldBossRecovery = worldBossRecovery; candidate.worldBossPromises = new LinkedHashMap<>(worldBossPromises);
        candidate.recoveryWitness = recoveryWitness;
        return candidate;
    }

    private Map<UUID, Integer> settle(UUID payoutId, Map<UUID, Integer> amounts,
                                    Map<UUID, RaidPlayerRecord> balances, UUID installation, CreditWriter writer) throws IOException {
        requireCreditLedger();
        java.util.Objects.requireNonNull(payoutId, "payoutId");
        RaidPlayerRecords candidate = creditCandidate(balances);
        Map<UUID, PointCreditReceipt> receipts = candidate.creditReceipts.computeIfAbsent(payoutId,
                ignored -> new LinkedHashMap<>());
        if (candidate.worldBossPromises.containsKey(payoutId) && !receipts.keySet().containsAll(amounts.keySet())) throw new IllegalStateException("Cannot extend a secured World Boss recipient list");
        Map<UUID, Integer> applied = new LinkedHashMap<>();
        boolean changed = false;
        if (installation != null) {
            String digest = com.cobbleraids.config.RecoveryWitness.promise(payoutId, amounts);
            String previous = candidate.worldBossPromises.putIfAbsent(payoutId, digest);
            if (previous != null && !previous.equals(digest)) throw new IllegalStateException("World Boss recipient promise cannot change");
            if (previous == null && !receipts.keySet().stream().allMatch(amounts::containsKey)) throw new IllegalStateException("Existing World Boss receipts contain recipients outside the frozen promise");
            changed = previous == null; candidate.advanceRecoveryWitness = changed;
        }
        for (var entry : amounts.entrySet()) {
            UUID playerId = java.util.Objects.requireNonNull(entry.getKey(), "playerId");
            int amount = java.util.Objects.requireNonNull(entry.getValue(), "credit amount");
            if (amount < 0) throw new IllegalArgumentException("Negative credit promise");
            PointCreditReceipt old = receipts.get(playerId);
            if (old != null && old.promised() != amount) {
                throw new IllegalArgumentException("A saved payout's amount cannot change");
            }
            PointCreditReceipt receipt = old == null ? new PointCreditReceipt(amount, 0) : old;
            RaidPlayerRecord record = candidate.loaded.getOrDefault(playerId, RaidPlayerRecord.EMPTY);
            PointCreditReceipt.Allocation allocation = receipt.allocate(record.raidPoints());
            int credited = allocation.balance() - record.raidPoints();
            candidate.loaded.put(playerId, record.withPoints(credited));
            receipts.put(playerId, allocation.receipt());
            applied.put(playerId, credited);
            changed |= old == null || credited > 0;
        }
        if (changed) {
            candidate.setDirty();
            writer.write(candidate); // Failure leaves this store and the published balances untouched.
            for (UUID playerId : amounts.keySet()) balances.put(playerId, candidate.loaded.get(playerId));
            loaded = candidate.loaded;
            creditReceipts = candidate.creditReceipts;
            worldBossPromises = candidate.worldBossPromises;
            setDirty(false);
        }
        return Map.copyOf(applied);
    }

    private void requireCreditLedger() {
        if (recoveryFailed || !creditLedgerValid || unreadableFile) throw new IllegalStateException("Raid Points receipt/recovery data needs operator repair; balances are preserved");
        if (worldBossRecovery != null && recoveryWitness != null) {
            try { com.cobbleraids.config.RecoveryWitness.verify(recoveryWitness, com.cobbleraids.config.RecoveryWitness.points(worldBossRecovery, worldBossPromises)); }
            catch (IOException failure) { throw new IllegalStateException("Raid Points recovery witness needs reconciliation", failure); }
        }
    }

    /** Prove settlement storage is writable before enabling a new rewarded season/occurrence. */
    public static void verifySettlementStorage(MinecraftServer server) throws IOException {
        ensureLoaded(server);
        RaidPlayerRecords store = get(server);
        store.requireCreditLedger();
        store.update(LIVE);
        store.flushStrict(server, FILE_ID);
    }

    /**
     * Remembers a once-per-player purchase, and flushes immediately.
     *
     * <p>Flushed rather than merely marked dirty, for the reason consuming a reward claim is: a
     * player's inventory reaches disk when they log out, but SavedData waits for an autosave. Buy,
     * log out, crash the server, and the item is in the inventory while the record that says it was
     * bought is not -- which is a once-per-player entry bought twice.
     */
    public static void recordPurchase(MinecraftServer server, UUID playerId, String entryId, long window, java.time.Instant now) {
        ensureLoaded(server);
        LIVE.compute(playerId, (ignored, existing) ->
                (existing == null ? RaidPlayerRecord.EMPTY : existing)
                        .withPurchaseOn(entryId, window, now));
        persist(server);
    }

    /**
     * Adds {@code titleId} to what this player has unlocked. A no-op (still persisted, since a
     * caller only reaches here after deciding the title is newly earned) rather than an error if it
     * was somehow already unlocked -- {@link RaidPlayerRecord#withTitleUnlocked} already treats a
     * repeat as a no-op copy.
     */
    public static void unlockTitle(MinecraftServer server, UUID playerId, String titleId) {
        ensureLoaded(server);
        LIVE.merge(playerId, RaidPlayerRecord.EMPTY.withTitleUnlocked(titleId),
                (existing, ignored) -> existing.withTitleUnlocked(titleId));
        persist(server);
    }

    /**
     * Sets which unlocked title this player wears, or clears it with a {@code null} id. Refuses a
     * title the player has not unlocked -- the command layer already checks this against tab
     * completion, but a hostile or stale client packet must not be able to force a selection the
     * player never earned.
     */
    public static boolean selectTitle(MinecraftServer server, UUID playerId, String titleId) {
        ensureLoaded(server);
        if (titleId != null && !get(playerId).hasTitle(titleId)) return false;
        LIVE.merge(playerId, RaidPlayerRecord.EMPTY.withSelectedTitle(titleId),
                (existing, ignored) -> existing.withSelectedTitle(titleId));
        persist(server);
        return true;
    }

    /**
     * Forces everything written so far to disk.
     *
     * <p>Separate from the writers so a caller making several changes at once pays for one write
     * rather than one each. Used by the shop, where the points spent and the purchase counted are
     * two writes that must land together or not at all.
     */
    public static void flush(MinecraftServer server) {
        persistNow(server);
    }

    public static SavedData.Factory<RaidPlayerRecords> factory() {
        return new SavedData.Factory<>(RaidPlayerRecords::new, RaidPlayerRecords::load, DataFixTypes.LEVEL);
    }

    /** Overworld storage, so one file covers the server rather than one per dimension. */
    public static RaidPlayerRecords get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        RaidPlayerRecords store = overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
        if (!store.originChecked) {
            store.originChecked = true;
            var file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(FILE_ID + ".dat");
            // SavedData can substitute a fresh instance after a parse error. Existing money must
            // never be silently replaced with that empty instance.
            store.unreadableFile |= !store.loadedFromDisk && !java.nio.file.Files.notExists(file);
        }
        if (store.unreadableFile) throw new IllegalStateException("Raid player records could not load; repair the existing file before crediting points");
        if (store.worldBossRecovery != null && store.recoveryWitness == null) store.recoveryWitness = recoveryWitnessPath();
        return store;
    }

    public Map<UUID, RaidPlayerRecord> take() {
        Map<UUID, RaidPlayerRecord> result = loaded;
        loaded = new LinkedHashMap<>();
        return result;
    }

    public void update(Map<UUID, RaidPlayerRecord> live) {
        loaded = new LinkedHashMap<>(live);
        setDirty();
    }

    // Package-private rather than private so a round-trip test can call it directly against a real
    // CompoundTag without bootstrapping Minecraft's registries -- registries is unused by this method,
    // every field here is a primitive, UUID or ResourceLocation, none of it registry-keyed.
    static RaidPlayerRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            return decode(tag);
        } catch (RuntimeException invalid) {
            RaidPlayerRecords quarantined = new RaidPlayerRecords();
            quarantined.loadedFromDisk = true;
            quarantined.unreadableFile = true;
            quarantined.unreadableRecordData = tag.copy();
            return quarantined;
        }
    }

    private static RaidPlayerRecords decode(CompoundTag tag) {
        RaidPlayerRecords store = new RaidPlayerRecords();
        store.loadedFromDisk = true;
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        if (tag.contains("players") && (!tag.contains("players", Tag.TAG_LIST)
                || (players.isEmpty() && !((ListTag) tag.get("players")).isEmpty()))) {
            throw new IllegalArgumentException("Invalid player record list");
        }
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (!playerTag.hasUUID("player") || (playerTag.contains("points")
                    && (!playerTag.contains("points", Tag.TAG_INT) || playerTag.getInt("points") < 0))
                    || store.loaded.containsKey(playerTag.getUUID("player"))) {
                throw new IllegalArgumentException("Invalid or duplicate player record");
            }
            Map<RaidRarityTier, Integer> tiers = new LinkedHashMap<>();
            CompoundTag tierTag = playerTag.getCompound("tiers");
            for (String key : tierTag.getAllKeys()) {
                try {
                    tiers.put(RaidRarityTier.parse(key), tierTag.getInt(key));
                } catch (RuntimeException ignored) {
                    // A tier that no longer exists is dropped rather than failing the whole load.
                }
            }
            Map<ResourceLocation, Integer> species = new LinkedHashMap<>();
            CompoundTag speciesTag = playerTag.getCompound("species");
            for (String key : speciesTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) species.put(id, speciesTag.getInt(key));
            }
            Map<String, RaidPurchaseTally> purchases = new LinkedHashMap<>();
            ListTag purchaseTag = playerTag.getList("purchases", Tag.TAG_COMPOUND);
            for (int p = 0; p < purchaseTag.size(); p++) {
                CompoundTag row = purchaseTag.getCompound(p);
                String entryId = row.getString("id");
                if (!entryId.isEmpty()) {
                    purchases.put(entryId, new RaidPurchaseTally(
                            Math.max(0, row.getInt("count")), row.getLong("day")));
                }
            }
            Set<String> unlockedTitles = new LinkedHashSet<>();
            ListTag titleTag = playerTag.getList("titles", Tag.TAG_STRING);
            for (int t = 0; t < titleTag.size(); t++) unlockedTitles.add(titleTag.getString(t));
            String selectedTitle = playerTag.contains("selected_title")
                    ? playerTag.getString("selected_title") : null;
            Map<String, RaidPurchaseTally> pointsEarned = new LinkedHashMap<>();
            ListTag earnedTag = playerTag.getList("points_earned", Tag.TAG_COMPOUND);
            for (int e = 0; e < earnedTag.size(); e++) {
                CompoundTag row = earnedTag.getCompound(e);
                String source = row.getString("source");
                if (!source.isEmpty()) {
                    pointsEarned.put(source, new RaidPurchaseTally(
                            Math.max(0, row.getInt("count")), row.getLong("day")));
                }
            }

            store.loaded.put(playerTag.getUUID("player"), new RaidPlayerRecord(
                    playerTag.getInt("wins"), tiers, species,
                    playerTag.getDouble("contribution"), playerTag.getInt("caught"),
                    playerTag.getInt("since_mega"), playerTag.getInt("points"), purchases,
                    unlockedTitles, selectedTitle, pointsEarned));
        }
        store.creditSchema = tag.contains("credit_schema") ? tag.getInt("credit_schema") : 1;
        ListTag receipts = tag.getList("credit_receipts", Tag.TAG_COMPOUND);
        try {
            if (store.creditSchema != 1) throw new IllegalArgumentException("Unknown credit receipt schema");
            if (tag.contains("credit_receipts") && (!tag.contains("credit_receipts", Tag.TAG_LIST)
                    || (tag.getList("credit_receipts", Tag.TAG_COMPOUND).isEmpty()
                    && !((ListTag) tag.get("credit_receipts")).isEmpty()))) {
                throw new IllegalArgumentException("Invalid credit receipt list");
            }
            for (int i = 0; i < receipts.size(); i++) {
                CompoundTag row = receipts.getCompound(i);
                if (!row.hasUUID("payout") || !row.hasUUID("player")
                        || !row.contains("promised", Tag.TAG_INT) || !row.contains("credited", Tag.TAG_INT)) {
                    throw new IllegalArgumentException("Incomplete credit receipt");
                }
                UUID event = row.getUUID("payout");
                UUID player = row.getUUID("player");
                PointCreditReceipt receipt = new PointCreditReceipt(row.getInt("promised"), row.getInt("credited"));
                if (store.creditReceipts.computeIfAbsent(event, ignored -> new LinkedHashMap<>()).putIfAbsent(player, receipt) != null) {
                    throw new IllegalArgumentException("Duplicate credit receipt");
                }
            }
        } catch (RuntimeException invalid) {
            store.creditLedgerValid = false;
            store.unreadableReceipts = tag.contains("credit_receipts") ? tag.get("credit_receipts").copy() : receipts.copy();
        }
        if (tag.contains("worldboss_recovery")) {
            if (!tag.contains("worldboss_recovery", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Malformed World Boss recovery identity");
            CompoundTag recovery = tag.getCompound("worldboss_recovery");
            if (!recovery.contains("version", Tag.TAG_INT) || recovery.getInt("version") != 1 || !recovery.hasUUID("installation")
                    || !recovery.contains("promises", Tag.TAG_LIST) || recovery.getList("promises", Tag.TAG_COMPOUND).size() != ((ListTag) recovery.get("promises")).size()) throw new IllegalArgumentException("Malformed World Boss recovery promises");
            store.worldBossRecovery = recovery.getUUID("installation");
            for (Tag value : recovery.getList("promises", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) value;
                if (!row.hasUUID("payout") || !row.contains("digest", Tag.TAG_STRING) || !row.getString("digest").matches("[0-9a-f]{64}")
                        || store.worldBossPromises.putIfAbsent(row.getUUID("payout"), row.getString("digest")) != null) throw new IllegalArgumentException("Malformed/duplicate World Boss recovery payout");
                Map<UUID, Integer> amounts = new LinkedHashMap<>();
                store.creditReceipts.getOrDefault(row.getUUID("payout"), Map.of()).forEach((player, receipt) -> amounts.put(player, receipt.promised()));
                if (!row.getString("digest").equals(com.cobbleraids.config.RecoveryWitness.promise(row.getUUID("payout"), amounts))) throw new IllegalArgumentException("World Boss proof contradicts point receipts");
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unreadableRecordData != null) return unreadableRecordData.copy();
        if (unreadableFile) throw new IllegalStateException("Refusing to overwrite unreadable raid player data");
        ListTag players = new ListTag();
        for (Map.Entry<UUID, RaidPlayerRecord> entry : loaded.entrySet()) {
            RaidPlayerRecord record = entry.getValue();
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("player", entry.getKey());
            playerTag.putInt("wins", record.raidsWon());
            playerTag.putDouble("contribution", record.totalContribution());
            playerTag.putInt("caught", record.bossesCaught());
            playerTag.putInt("since_mega", record.raidsSinceMegaStone());
            playerTag.putInt("points", record.raidPoints());
            CompoundTag tiers = new CompoundTag();
            record.winsByTier().forEach((tier, count) ->
                    tiers.putInt(tier.serializedName().toLowerCase(Locale.ROOT), count));
            playerTag.put("tiers", tiers);
            CompoundTag species = new CompoundTag();
            record.defeatsBySpecies().forEach((id, count) -> species.putInt(id.toString(), count));
            playerTag.put("species", species);
            if (!record.purchases().isEmpty()) {
                ListTag purchases = new ListTag();
                record.purchases().forEach((id, tally) -> {
                    CompoundTag row = new CompoundTag();
                    row.putString("id", id);
                    row.putInt("count", tally.count());
                    row.putLong("day", tally.day());
                    purchases.add(row);
                });
                playerTag.put("purchases", purchases);
            }
            if (!record.unlockedTitles().isEmpty()) {
                ListTag titles = new ListTag();
                record.unlockedTitles().forEach(id -> titles.add(net.minecraft.nbt.StringTag.valueOf(id)));
                playerTag.put("titles", titles);
            }
            if (record.selectedTitle() != null) {
                playerTag.putString("selected_title", record.selectedTitle());
            }
            if (!record.pointsEarned().isEmpty()) {
                ListTag earned = new ListTag();
                record.pointsEarned().forEach((source, tally) -> {
                    CompoundTag row = new CompoundTag();
                    row.putString("source", source);
                    row.putInt("count", tally.count());
                    row.putLong("day", tally.day());
                    earned.add(row);
                });
                playerTag.put("points_earned", earned);
            }
            players.add(playerTag);
        }
        tag.put("players", players);
        tag.putInt("credit_schema", creditSchema);
        ListTag receipts = new ListTag();
        creditReceipts.forEach((event, entries) -> entries.forEach((player, receipt) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("payout", event);
            row.putUUID("player", player);
            row.putInt("promised", receipt.promised());
            row.putInt("credited", receipt.credited());
            receipts.add(row);
        }));
        tag.put("credit_receipts", creditLedgerValid ? receipts : unreadableReceipts.copy());
        if (worldBossRecovery != null) {
            CompoundTag recovery = new CompoundTag(); recovery.putInt("version", 1); recovery.putUUID("installation", worldBossRecovery);
            ListTag proofs = new ListTag(); worldBossPromises.forEach((payout, digest) -> { CompoundTag row = new CompoundTag(); row.putUUID("payout", payout); row.putString("digest", digest); proofs.add(row); });
            recovery.put("promises", proofs); tag.put("worldboss_recovery", recovery);
        }
        return tag;
    }

    @Override protected void commitRoot(java.nio.file.Path target, CompoundTag root) throws IOException {
        if (recoveryFailed) throw new IOException("Cannot overwrite quarantined Raid Points recovery evidence");
        if (worldBossRecovery != null && recoveryWitness != null) {
            var head = com.cobbleraids.config.RecoveryWitness.points(worldBossRecovery, worldBossPromises);
            if (advanceRecoveryWitness) com.cobbleraids.config.RecoveryWitness.write(recoveryWitness, head);
            else com.cobbleraids.config.RecoveryWitness.verify(recoveryWitness, head);
        }
        super.commitRoot(target, root);
        advanceRecoveryWitness = false;
    }
}
