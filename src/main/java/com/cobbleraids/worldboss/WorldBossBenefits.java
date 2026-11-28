package com.cobbleraids.worldboss;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Account presentation choices and the ordinary-victory blessing outbox, in the event authority. */
final class WorldBossBenefits {
    enum Effects { FULL, MINIMAL, OFF }
    record SceneOptions(int voiceVolume, boolean subtitles, Effects effects) {
        static final SceneOptions DEFAULT = new SceneOptions(70, true, Effects.FULL);
        SceneOptions { java.util.Objects.requireNonNull(effects); if (voiceVolume < 0 || voiceVolume > 100) throw new IllegalArgumentException("Invalid scene voice volume"); }
    }
    record Attunement(UUID pokemon, UUID character, String species, String appearance, int style) {
        Attunement {
            java.util.Objects.requireNonNull(pokemon); java.util.Objects.requireNonNull(character);
            if (species == null || !species.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || appearance == null || !appearance.matches("[a-z0-9_.-]{1,64}") || style < 0 || style > 2) throw new IllegalArgumentException("Invalid Worldheart attunement");
        }
        boolean validFor(UUID account, UUID observedOwner, UUID observedPokemon, String observedSpecies) {
            return account.equals(observedOwner) && pokemon.equals(observedPokemon) && species.equals(observedSpecies) && WorldheartEffects.supported(species, appearance);
        }
    }
    record Bonus(long atMillis, int base, boolean blessed, Map<UUID, Integer> amounts, boolean paid) {
        Bonus {
            amounts = Map.copyOf(amounts);
            if (atMillis <= 0 || base < 0 || amounts.values().stream().anyMatch(amount -> amount != (blessed ? WorldBossBlessing.bonus(base) : 0))) throw new IllegalArgumentException("Invalid blessing point promise");
        }
        Bonus secured() { return new Bonus(atMillis, base, blessed, amounts, true); }
    }
    private final Map<UUID, Attunement> attunements;
    private final Map<UUID, Effects> preferences;
    private final Map<UUID, Bonus> bonuses;
    private final Map<UUID, Bonus> pendingBonuses;
    private final Map<UUID, SceneOptions> sceneOptions;
    WorldBossBenefits() { this(Map.of(), Map.of(), Map.of()); }
    WorldBossBenefits(Map<UUID, Attunement> attunements, Map<UUID, Effects> preferences, Map<UUID, Bonus> bonuses) {
        this(attunements, preferences, bonuses, Map.of());
    }
    private WorldBossBenefits(Map<UUID, Attunement> attunements, Map<UUID, Effects> preferences, Map<UUID, Bonus> bonuses, Map<UUID, SceneOptions> sceneOptions) {
        this.attunements = Map.copyOf(attunements); this.preferences = Map.copyOf(preferences); this.bonuses = Map.copyOf(bonuses);
        this.sceneOptions = Map.copyOf(sceneOptions);
        var pending = new LinkedHashMap<UUID, Bonus>(); bonuses.forEach((raid, bonus) -> { if (!bonus.paid()) pending.put(raid, bonus); }); pendingBonuses = Map.copyOf(pending);
        var pokemon = new HashSet<UUID>(); for (var value : attunements.values()) if (!pokemon.add(value.pokemon())) throw new IllegalArgumentException("Pokemon attuned by two accounts");
    }
    Map<UUID, Attunement> attunements() { return attunements; }
    Map<UUID, Bonus> bonuses() { return bonuses; }
    Map<UUID, Bonus> pendingBonuses() { return pendingBonuses; }
    Effects effects(UUID player) { return preferences.getOrDefault(player, Effects.FULL); }
    SceneOptions sceneOptions(UUID player) { return sceneOptions.getOrDefault(player, SceneOptions.DEFAULT); }
    WorldBossBenefits sceneOptions(UUID player, SceneOptions options) {
        if (sceneOptions(player).equals(options)) return this;
        var updated = new LinkedHashMap<>(sceneOptions); updated.put(player, options); return new WorldBossBenefits(attunements, preferences, bonuses, updated);
    }
    WorldBossBenefits attune(UUID owner, Attunement attunement) {
        if (java.util.Objects.equals(attunements.get(owner), attunement)) return this;
        var updated = new LinkedHashMap<>(attunements);
        if (attunement == null) updated.remove(owner);
        else { updated.entrySet().removeIf(entry -> entry.getValue().pokemon().equals(attunement.pokemon())); updated.put(owner, attunement); }
        return new WorldBossBenefits(updated, preferences, bonuses, sceneOptions);
    }
    WorldBossBenefits effects(UUID owner, Effects effects) { if (effects(owner) == effects) return this; var updated = new LinkedHashMap<>(preferences); updated.put(owner, effects); return new WorldBossBenefits(attunements, updated, bonuses, sceneOptions); }
    WorldBossBenefits promise(UUID raid, long atMillis, int base, Set<UUID> players, boolean blessed) {
        var old = bonuses.get(raid);
        if (old != null) { if (!old.amounts().keySet().equals(players) || old.atMillis() != atMillis) throw new IllegalStateException("Ordinary raid blessing outcome changed"); return this; }
        var amounts = new LinkedHashMap<UUID, Integer>(); int bonus = blessed ? WorldBossBlessing.bonus(base) : 0; players.forEach(player -> amounts.put(player, bonus));
        var updated = new LinkedHashMap<>(bonuses); updated.put(raid, new Bonus(atMillis, base, blessed, amounts, bonus == 0));
        return new WorldBossBenefits(attunements, preferences, updated, sceneOptions);
    }
    WorldBossBenefits secured(UUID raid) {
        var old = bonuses.get(raid); if (old == null) throw new IllegalStateException("Unknown blessing promise"); if (old.paid()) return this;
        var updated = new LinkedHashMap<>(bonuses); updated.put(raid, old.secured()); return new WorldBossBenefits(attunements, preferences, updated, sceneOptions);
    }
    void validate(WorldheartCollection collection) {
        attunements.forEach((player, bond) -> { var progress = collection.progress(player, bond.character()); if (!progress.owned() || progress.style() < bond.style()) throw new IllegalArgumentException("Attunement lacks its account entitlement"); });
        bonuses.values().forEach(bonus -> { if (bonus.blessed() && !collection.blessedAt(bonus.atMillis())) throw new IllegalArgumentException("Bonus has no committed blessing"); });
    }
    static UUID payoutId(UUID raid) { return UUID.nameUUIDFromBytes(("cobbleraids:worldboss-blessing:" + raid).getBytes(StandardCharsets.UTF_8)); }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("version", 2);
        var scenes = new ListTag(); sceneOptions.forEach((player, options) -> {
            var row = new CompoundTag(); row.putUUID("player", player); row.putInt("voice_volume", options.voiceVolume()); row.putBoolean("subtitles", options.subtitles()); row.putString("effects", options.effects().name()); scenes.add(row);
        }); tag.put("scenes", scenes);
        var bonds = new ListTag(); attunements.forEach((player, bond) -> {
            var row = new CompoundTag(); row.putUUID("player", player); row.putUUID("pokemon", bond.pokemon()); row.putUUID("character", bond.character()); row.putString("species", bond.species()); row.putString("appearance", bond.appearance()); row.putInt("style", bond.style()); bonds.add(row);
        }); tag.put("attunements", bonds);
        var prefs = new ListTag(); preferences.forEach((player, mode) -> { var row = new CompoundTag(); row.putUUID("player", player); row.putString("mode", mode.name()); prefs.add(row); }); tag.put("preferences", prefs);
        var plans = new ListTag(); bonuses.forEach((raid, bonus) -> {
            var row = new CompoundTag(); row.putUUID("raid", raid); row.putLong("at", bonus.atMillis()); row.putInt("base", bonus.base()); row.putBoolean("blessed", bonus.blessed()); row.putBoolean("paid", bonus.paid());
            var players = new ListTag(); bonus.amounts().forEach((player, amount) -> { var at = new CompoundTag(); at.putUUID("player", player); at.putInt("amount", amount); players.add(at); }); row.put("players", players); plans.add(row);
        }); tag.put("bonuses", plans); return tag;
    }
    static WorldBossBenefits load(CompoundTag tag) {
        int version = integer(tag, "version"); if (version != 1 && version != 2) throw new IllegalArgumentException("Unknown benefits version");
        var scenes = new LinkedHashMap<UUID, SceneOptions>();
        if (version == 2) { var entries = rows(tag, "scenes"); for (int i = 0; i < entries.size(); i++) {
            var row = entries.getCompound(i); require(row, "subtitles", Tag.TAG_BYTE);
            if (scenes.putIfAbsent(uuid(row, "player"), new SceneOptions(integer(row, "voice_volume"), row.getBoolean("subtitles"), Effects.valueOf(text(row, "effects")))) != null) throw new IllegalArgumentException("Duplicate scene preference");
        } } else if (tag.contains("scenes")) throw new IllegalArgumentException("Legacy benefits contain scene preferences");
        var bonds = new LinkedHashMap<UUID, Attunement>(); var prefs = new LinkedHashMap<UUID, Effects>(); var plans = new LinkedHashMap<UUID, Bonus>();
        var rows = rows(tag, "attunements");
        for (int i = 0; i < rows.size(); i++) { var at = rows.getCompound(i); if (bonds.putIfAbsent(uuid(at, "player"), new Attunement(uuid(at, "pokemon"), uuid(at, "character"), text(at, "species"), text(at, "appearance"), integer(at, "style"))) != null) throw new IllegalArgumentException("Duplicate account attunement"); }
        rows = rows(tag, "preferences"); for (int i = 0; i < rows.size(); i++) { var at = rows.getCompound(i); if (prefs.putIfAbsent(uuid(at, "player"), Effects.valueOf(text(at, "mode"))) != null) throw new IllegalArgumentException("Duplicate effects preference"); }
        rows = rows(tag, "bonuses"); for (int i = 0; i < rows.size(); i++) {
            var at = rows.getCompound(i); var amounts = new LinkedHashMap<UUID, Integer>(); var players = rows(at, "players");
            for (int j = 0; j < players.size(); j++) { var player = players.getCompound(j); if (amounts.putIfAbsent(uuid(player, "player"), integer(player, "amount")) != null) throw new IllegalArgumentException("Duplicate bonus recipient"); }
            require(at, "blessed", Tag.TAG_BYTE); require(at, "paid", Tag.TAG_BYTE);
            if (plans.putIfAbsent(uuid(at, "raid"), new Bonus(number(at, "at"), integer(at, "base"), at.getBoolean("blessed"), amounts, at.getBoolean("paid"))) != null) throw new IllegalArgumentException("Duplicate ordinary bonus");
        }
        return new WorldBossBenefits(bonds, prefs, plans, scenes);
    }
    private static void require(CompoundTag tag, String key, int type) { if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing benefits " + key); }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static String text(CompoundTag tag, String key) { require(tag, key, Tag.TAG_STRING); return tag.getString(key); }
    private static UUID uuid(CompoundTag tag, String key) { if (!tag.hasUUID(key)) throw new IllegalArgumentException("Invalid benefits UUID"); return tag.getUUID(key); }
    private static ListTag rows(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LIST); var rows = (ListTag) tag.get(key); if (!rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid benefits rows"); return rows; }
}
