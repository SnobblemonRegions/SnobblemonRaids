package com.cobbleraids.worldboss;

import com.cobbleraids.config.SavedDataFlush;
import com.cobbleraids.renown.RaidRenown;
import com.cobbleraids.renown.RenownAffix;
import com.cobbleraids.renown.RenownBoon;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
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
 * <p>One file ({@code cobbleraids_world_boss.dat}) in the overworld's data storage, like the
 * pending-reward store. Holds the pool and every player's damage, where the boss stands, and two
 * flags that make the end of the fight safe to repeat: {@code defeatAnnounced} (listeners were told)
 * and {@code rewardsPaid} (Raid Points were paid). Each is saved <i>before</i> the step it guards is
 * repeated, so a crash between them replays only the step that did not finish.
 *
 * <p>An empty state ({@link #active} false) is the normal state of a server with no event running.
 */
public final class WorldBossState extends SavedData {
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
    private WorldBossKind kind = WorldBossKind.EVENT;
    /** The daily boss's renown, rolled once at its start so the standing boss, every duel copy and a restart all carry the same one; null otherwise. */
    private RaidRenown renown;
    /** The affix id that renown rolled, or empty. */
    private String affix = "";
    /** How much bigger the World Pool is for the renown (1 for none). */
    private float poolMultiplier = 1f;
    /** Everyone who has joined this boss; the daily boss's player cap counts them, and a place once taken is kept. */
    private final Set<UUID> participants = new LinkedHashSet<>();
    /** The last day (in the daily zone, {@code 2026-07-01}) a daily boss started; never cleared, so a day cannot run twice. */
    private String dailyLastDay = "";

    public static SavedData.Factory<WorldBossState> factory() {
        return new SavedData.Factory<>(WorldBossState::new, WorldBossState::load, DataFixTypes.LEVEL);
    }

    public static WorldBossState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public void flush(MinecraftServer server) throws IOException {
        SavedDataFlush.flush(server, this, FILE_ID);
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
    public WorldBossKind kind() { return kind; }
    public Optional<RaidRenown> renown() { return Optional.ofNullable(renown); }
    public Optional<RenownAffix> affix() { return RenownAffix.byId(affix); }
    public float poolMultiplier() { return poolMultiplier; }
    public Set<UUID> participants() { return Collections.unmodifiableSet(participants); }
    public String dailyLastDay() { return dailyLastDay; }

    // ---- writes (each marks the state dirty) ----

    public void begin(UUID bossId, String definitionId, String dimension, double x, double y, double z,
                      long startedAtMillis, long endsAtMillis, WorldBossPool pool, boolean arena) {
        begin(WorldBossKind.EVENT, bossId, definitionId, dimension, x, y, z, startedAtMillis, endsAtMillis, pool, arena);
    }

    public void begin(WorldBossKind kind, UUID bossId, String definitionId, String dimension, double x, double y, double z,
                      long startedAtMillis, long endsAtMillis, WorldBossPool pool, boolean arena) {
        this.active = true;
        this.kind = kind;
        this.renown = null;
        this.affix = "";
        this.poolMultiplier = 1f;
        this.participants.clear();
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
        setDirty();
    }

    /** The renown the standing boss rolled, and how much bigger it makes the pool. */
    public void setRenown(RaidRenown renown, RenownAffix affix, float poolMultiplier) {
        this.renown = renown;
        this.affix = affix == null ? "" : affix.id();
        this.poolMultiplier = poolMultiplier;
        setDirty();
    }

    /** Counts a player in. @return true if they were not counted before */
    public boolean addParticipant(UUID player) {
        boolean added = participants.add(player);
        if (added) setDirty();
        return added;
    }

    /** Marks a day's daily boss as started, before it is spawned, so a crash during the start cannot run the day again. */
    public void markDailyDay(String day) { dailyLastDay = day; setDirty(); }

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

    // ---- disk ----

    private static WorldBossState load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldBossState state = new WorldBossState();
        state.active = tag.getBoolean("active");
        state.lastEndedAtMillis = tag.getLong("last_ended");
        state.dailyLastDay = tag.getString("daily_last_day");
        if (!tag.contains("boss")) return state;
        state.kind = WorldBossKind.parse(tag.getString("kind"));
        if (tag.contains("renown_name")) {
            try {
                RaidRenown loaded = RenownBoon.decode(tag.getString("renown_boon"))
                        .map(boon -> new RaidRenown(tag.getString("renown_name"), tag.getString("renown_epithet"), boon))
                        .orElse(null);
                state.renown = loaded;
            } catch (IllegalArgumentException ex) {
                state.renown = null;   // a hand-edited or outdated title: the boss carries on without renown
            }
        }
        state.affix = tag.getString("affix");
        state.poolMultiplier = tag.contains("pool_multiplier") ? tag.getFloat("pool_multiplier") : 1f;
        ListTag joined = tag.getList("participants", Tag.TAG_COMPOUND);
        for (int i = 0; i < joined.size(); i++) {
            CompoundTag item = joined.getCompound(i);
            if (item.hasUUID("player")) state.participants.add(item.getUUID("player"));
        }
        state.bossId = tag.getUUID("boss");
        state.definitionId = tag.getString("definition");
        state.dimension = tag.getString("dimension");
        state.x = tag.getDouble("x");
        state.y = tag.getDouble("y");
        state.z = tag.getDouble("z");
        state.startedAtMillis = tag.getLong("started");
        state.endsAtMillis = tag.getLong("ends");
        state.defeatAnnounced = tag.getBoolean("defeat_announced");
        state.rewardsPaid = tag.getBoolean("rewards_paid");
        ListTag paid = tag.getList("paid_players", Tag.TAG_COMPOUND);
        for (int i = 0; i < paid.size(); i++) {
            CompoundTag item = paid.getCompound(i);
            if (item.hasUUID("player")) state.paidPlayers.add(item.getUUID("player"));
        }
        state.arena = tag.getBoolean("arena");
        Map<UUID, Float> damage = new LinkedHashMap<>();
        ListTag list = tag.getList("damage", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            damage.put(entry.getUUID("player"), entry.getFloat("amount"));
        }
        float max = tag.getFloat("pool_max");
        if (max > 0f) {
            state.pool = new WorldBossPool(max, tag.getFloat("pool_current"), tag.getBoolean("pool_defeated"), damage);
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("active", active);
        tag.putLong("last_ended", lastEndedAtMillis);
        tag.putString("daily_last_day", dailyLastDay);
        if (pool == null) return tag;
        tag.putString("kind", kind.serializedName());
        if (renown != null) {
            tag.putString("renown_name", renown.name());
            tag.putString("renown_epithet", renown.epithet());
            tag.putString("renown_boon", renown.boon().encode());
        }
        tag.putString("affix", affix);
        tag.putFloat("pool_multiplier", poolMultiplier);
        ListTag joined = new ListTag();
        for (UUID id : participants) {
            CompoundTag item = new CompoundTag();
            item.putUUID("player", id);
            joined.add(item);
        }
        tag.put("participants", joined);
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
