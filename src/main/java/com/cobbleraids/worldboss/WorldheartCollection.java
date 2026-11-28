package com.cobbleraids.worldboss;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Immutable account entitlements derived from once-only natural-victory grants. No physical claims. */
final class WorldheartCollection {
    static final int GUARANTEE = 12, STYLE_ONE = 24, STYLE_TWO = 40, EARLY_PER_TEN_THOUSAND = 200;
    record Award(int step, int roll, UUID legacyCharacter, int legacyStep) {
        Award {
            if (step < 1 || roll < -1 || roll >= 10_000 || (legacyCharacter == null ? legacyStep != 0 : legacyStep < 1)) throw new IllegalArgumentException("Invalid Worldheart award");
        }
    }
    record Victory(UUID event, UUID character, long awakening, long atMillis, Map<UUID, Award> awards, WorldBossBlessing.Window blessing) {
        Victory(UUID event, UUID character, long awakening, long atMillis, Map<UUID, Award> awards) { this(event, character, awakening, atMillis, awards, null); }
        Victory {
            Objects.requireNonNull(event); Objects.requireNonNull(character); awards = Map.copyOf(awards);
            if (awakening < 1 || atMillis <= 0) throw new IllegalArgumentException("Invalid Worldheart victory");
            if (awards.values().stream().anyMatch(award -> character.equals(award.legacyCharacter()))) throw new IllegalArgumentException("Current character cannot be its legacy project");
        }
    }
    record Unlock(UUID event, long awakening, long atMillis, boolean legacy, boolean early) {}
    record Progress(int steps, Unlock unlock) {
        boolean owned() { return unlock != null; }
        int style() { return !owned() ? -1 : steps >= STYLE_TWO ? 2 : steps >= STYLE_ONE ? 1 : 0; }
        int nextMilestone() { return !owned() ? GUARANTEE : steps < STYLE_ONE ? STYLE_ONE : steps < STYLE_TWO ? STYLE_TWO : 0; }
    }
    record Snapshot(String secret, List<Victory> victories, Map<UUID, UUID> legacySelections) {
        Snapshot {
            if (secret == null || !secret.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid Worldheart roll authority");
            victories = List.copyOf(victories); legacySelections = Map.copyOf(legacySelections);
        }
    }
    private record Account(UUID player, UUID character) {}
    private static final Progress EMPTY = new Progress(0, null);
    private final Snapshot snapshot;
    private final Map<Account, Progress> progress = new HashMap<>();
    private final Map<UUID, Victory> events = new HashMap<>();
    private long blessingUntil;

    static WorldheartCollection empty() {
        byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
        return new WorldheartCollection(new Snapshot(HexFormat.of().formatHex(secret), List.of(), Map.of()));
    }
    WorldheartCollection(Snapshot snapshot) {
        this.snapshot = snapshot;
        long lastBlessingStart = 0;
        for (var victory : snapshot.victories()) {
            if (events.putIfAbsent(victory.event(), victory) != null) throw new IllegalArgumentException("Duplicate Worldheart victory");
            if (victory.blessing() != null) {
                if (victory.atMillis() < lastBlessingStart || !victory.blessing().equals(WorldBossBlessing.extend(blessingUntil, victory.atMillis()))) throw new IllegalArgumentException("Blessing bounds disagree with victory history");
                lastBlessingStart = victory.atMillis();
                blessingUntil = victory.blessing().until();
            }
            victory.awards().forEach((player, award) -> {
                var before = progress(player, victory.character());
                if (award.step() != Math.addExact(before.steps(), 1)) throw new IllegalArgumentException("Worldheart progress sequence disagrees");
                boolean eligibleRoll = !before.owned() && award.step() < GUARANTEE;
                if (eligibleRoll ? award.roll() != roll(snapshot.secret(), victory.event(), player) : award.roll() != -1) throw new IllegalArgumentException("Worldheart early roll disagrees");
                advance(player, victory.character(), victory, award.step(), eligibleRoll && award.roll() < EARLY_PER_TEN_THOUSAND, false);
                if (award.legacyCharacter() != null) {
                    if (award.legacyStep() != Math.addExact(progress(player, award.legacyCharacter()).steps(), 1)) throw new IllegalArgumentException("Legacy progress sequence disagrees");
                    advance(player, award.legacyCharacter(), victory, award.legacyStep(), false, true);
                }
            });
        }
    }
    Snapshot snapshot() { return snapshot; }
    Progress progress(UUID player, UUID character) { return progress.getOrDefault(new Account(player, character), EMPTY); }
    UUID selectedLegacy(UUID player) { return snapshot.legacySelections().get(player); }
    Victory victory(UUID event) { return events.get(event); }
    boolean hasBlessings() { return blessingUntil > 0; }
    boolean blessedAt(long instant) { return snapshot.victories().stream().anyMatch(victory -> victory.blessing() != null && victory.blessing().contains(instant)); }
    long blessingUntil() { return blessingUntil; }

    WorldheartCollection select(UUID player, UUID character) {
        Objects.requireNonNull(player);
        if (Objects.equals(selectedLegacy(player), character)) return this;
        var selections = new LinkedHashMap<>(snapshot.legacySelections());
        if (character == null) selections.remove(player); else selections.put(player, character);
        return new WorldheartCollection(new Snapshot(snapshot.secret(), snapshot.victories(), selections));
    }
    WorldheartCollection grant(UUID event, UUID character, long awakening, long atMillis, Set<UUID> qualified) {
        return grant(event, character, awakening, atMillis, qualified, false);
    }
    WorldheartCollection grant(UUID event, UUID character, long awakening, long atMillis, Set<UUID> qualified, boolean blessing) {
        Objects.requireNonNull(event); Objects.requireNonNull(character); qualified = Set.copyOf(qualified);
        var existing = events.get(event);
        if (existing != null) {
            if (!existing.character().equals(character) || existing.awakening() != awakening || !existing.awards().keySet().equals(qualified) || (existing.blessing() != null) != blessing) throw new IllegalStateException("Worldheart outcome changed");
            return this; // Selection changes and retry time cannot rewrite this grant.
        }
        Map<UUID, Award> awards = new LinkedHashMap<>();
        for (var player : qualified) {
            var before = progress(player, character); int step = Math.addExact(before.steps(), 1);
            var legacy = selectedLegacy(player);
            if (character.equals(legacy)) throw new IllegalStateException("Current character is selected as legacy");
            int roll = !before.owned() && step < GUARANTEE ? roll(snapshot.secret(), event, player) : -1;
            awards.put(player, new Award(step, roll, legacy, legacy == null ? 0 : Math.addExact(progress(player, legacy).steps(), 1)));
        }
        var victories = new ArrayList<>(snapshot.victories()); victories.add(new Victory(event, character, awakening, atMillis, awards, blessing ? WorldBossBlessing.extend(blessingUntil, atMillis) : null));
        return new WorldheartCollection(new Snapshot(snapshot.secret(), victories, snapshot.legacySelections()));
    }
    private void advance(UUID player, UUID character, Victory victory, int steps, boolean early, boolean legacy) {
        Unlock unlock = progress(player, character).unlock();
        if (unlock == null && (steps >= GUARANTEE || early)) unlock = new Unlock(victory.event(), victory.awakening(), victory.atMillis(), legacy, early);
        progress.put(new Account(player, character), new Progress(steps, unlock));
    }
    void validate(WorldBossCampaign campaign) {
        Map<UUID, WorldBossCampaign.Occurrence> outcomes = new HashMap<>(); campaign.occurrences().values().forEach(event -> outcomes.put(event.eventId(), event));
        Set<UUID> characters = campaign.seasons().values().stream().map(WorldBossCampaign.Season::characterId).collect(java.util.stream.Collectors.toSet());
        for (var victory : snapshot.victories()) {
            var event = outcomes.get(victory.event());
            if (event == null || event.status() != WorldBossCampaign.Status.VICTORY || !event.characterId().equals(victory.character())
                    || event.awakening() != victory.awakening() || victory.atMillis() < event.combatAtMillis()) throw new IllegalArgumentException("Worldheart grant has no matching natural campaign victory");
            for (var award : victory.awards().values()) if (award.legacyCharacter() != null && !characters.contains(award.legacyCharacter())) throw new IllegalArgumentException("Unknown archived Worldheart");
        }
        for (UUID character : snapshot.legacySelections().values()) if (!characters.contains(character)
                || campaign.active() != null && campaign.active().characterId().equals(character)) throw new IllegalArgumentException("Worldheart legacy selection is not archived");
    }
    /** Secret-keyed, event/player-stable roll with rejection sampling to avoid modulo bias. */
    private static int roll(String secret, UUID event, UUID player) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(HexFormat.of().parseHex(secret), "HmacSHA256"));
            var input = ByteBuffer.allocate(40).putInt(1).putLong(event.getMostSignificantBits()).putLong(event.getLeastSignificantBits())
                    .putLong(player.getMostSignificantBits()).putLong(player.getLeastSignificantBits());
            for (int counter = 0; counter < 1024; counter++) {
                input.putInt(36, counter); var digest = ByteBuffer.wrap(mac.doFinal(input.array()));
                while (digest.remaining() >= 4) { long value = Integer.toUnsignedLong(digest.getInt()); if (value < 4_294_960_000L) return (int) (value % 10_000); }
            }
            throw new IllegalStateException("Worldheart roll could not be sampled");
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("Worldheart roll authority unavailable", failure); }
    }
}
