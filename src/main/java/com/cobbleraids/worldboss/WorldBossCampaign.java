package com.cobbleraids.worldboss;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable seasonal identity and occurrence receipts. Stored atomically with the current fight. */
public record WorldBossCampaign(Map<String, Season> seasons, Season staged, String activeSeason,
                                 boolean dailyEnabled, long enabledAtMillis, long wallHighWaterMillis,
                                 Map<String, Occurrence> occurrences) {
    public static final int REVISION = 1;
    public static final int TEN_MINUTE = 1, FIVE_MINUTE = 2, ONE_MINUTE = 4, ENTRANCE = 8;
    public enum Status { PLANNED, PREPARING, OPEN, VICTORY, CLOSED, SKIPPED }

    public record Season(String id, UUID characterId, String name, String definition, long victories) {
        public Season {
            Objects.requireNonNull(characterId);
            if (id == null || !id.matches("[a-z0-9_.-]{1,64}") || !safeText(name, 64) || name.isBlank()
                    || definition == null || !definition.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || victories < 0 || victories == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid season identity");
        }
        public long awakening() { return victories + 1; }
        Season won() { return new Season(id, characterId, name, definition, Math.addExact(victories, 1)); }
    }

    public record Occurrence(String key, String season, UUID characterId, String name, String definition,
                              long awakening, long combatAtMillis, int offsetSeconds, UUID eventId,
                              Status status, int cues, String reason) {
        public Occurrence {
            Objects.requireNonNull(characterId); Objects.requireNonNull(eventId); Objects.requireNonNull(status);
            if (combatAtMillis <= 0 || awakening < 1 || cues < 0 || cues > 15 || !safeText(reason, 160)) throw new IllegalArgumentException("Invalid occurrence");
            var slot = WorldBossSchedule.next(Instant.ofEpochMilli(combatAtMillis));
            if (slot.combatAt().toEpochMilli() != combatAtMillis || !key.equals(WorldBossCampaign.key(season, slot))
                    || offsetSeconds != slot.combatAt().atZone(WorldBossSchedule.ZONE).getOffset().getTotalSeconds()) throw new IllegalArgumentException("Invalid occurrence clock identity");
            if ((status == Status.CLOSED || status == Status.SKIPPED) && reason.isBlank()) throw new IllegalArgumentException("Missing closed occurrence reason");
        }
        public boolean terminal() { return status == Status.VICTORY || status == Status.CLOSED || status == Status.SKIPPED; }
        public WorldBossSchedule.Occurrence slot() { return WorldBossSchedule.next(Instant.ofEpochMilli(combatAtMillis)); }
        public String displayName() { return name + " " + numeral(awakening); }
        Occurrence change(Status next, int mask, String why) {
            return new Occurrence(key, season, characterId, name, definition, awakening, combatAtMillis, offsetSeconds, eventId, next, mask, why);
        }
    }

    public WorldBossCampaign {
        seasons = Map.copyOf(seasons); occurrences = Map.copyOf(occurrences);
        if (enabledAtMillis < 0 || wallHighWaterMillis < enabledAtMillis || dailyEnabled && (activeSeason == null || enabledAtMillis == 0)
                || activeSeason != null && !seasons.containsKey(activeSeason)) throw new IllegalArgumentException("Invalid active season");
        var characters = new HashSet<UUID>();
        for (var row : seasons.entrySet()) if (!row.getKey().equals(row.getValue().id()) || !characters.add(row.getValue().characterId())) throw new IllegalArgumentException("Duplicate season identity");
        if (staged != null && (seasons.containsKey(staged.id()) || characters.contains(staged.characterId()) || staged.victories() != 0)) throw new IllegalArgumentException("Invalid staged season");
        Map<String, Long> wins = new HashMap<>();
        var eventIds = new HashSet<UUID>();
        var victoryOrdinals = new HashSet<String>();
        int reserved = 0;
        for (var row : occurrences.entrySet()) {
            Occurrence event = row.getValue();
            Season owner = seasons.get(event.season());
            if (!row.getKey().equals(event.key()) || owner == null || !owner.characterId().equals(event.characterId())
                    || !owner.name().equals(event.name()) || !owner.definition().equals(event.definition()) || event.awakening() > owner.awakening()
                    || !eventIds.add(event.eventId())) throw new IllegalArgumentException("Occurrence identity mismatch");
            if (event.status() == Status.VICTORY) {
                if (event.awakening() >= owner.awakening() || !victoryOrdinals.add(event.season() + "|" + event.awakening())) throw new IllegalArgumentException("Invalid victory ordinal");
                wins.merge(event.season(), 1L, Math::addExact);
            }
            if (event.status() == Status.PREPARING || event.status() == Status.OPEN) reserved++;
        }
        if (reserved > 1) throw new IllegalArgumentException("Multiple occurrences reserve the event slot");
        for (Season season : seasons.values()) if (season.victories() != wins.getOrDefault(season.id(), 0L)) throw new IllegalArgumentException("Season victories do not reconcile");
    }

    public static WorldBossCampaign empty() { return new WorldBossCampaign(Map.of(), null, null, false, 0, 0, Map.of()); }
    public boolean configured() { return staged != null || !seasons.isEmpty(); }
    public Season active() { return activeSeason == null ? null : seasons.get(activeSeason); }
    public static String key(String season, WorldBossSchedule.Occurrence slot) { return season + "|r" + REVISION + "|" + slot.key(); }

    public WorldBossCampaign stage(Season candidate, long now) {
        checkWall(now);
        return copy(seasons, candidate, activeSeason, dailyEnabled, enabledAtMillis, now, occurrences);
    }

    public WorldBossCampaign activate(long now) {
        checkWall(now);
        if (staged == null || occurrences.values().stream().anyMatch(event -> !event.terminal())) throw new IllegalStateException("No staged season or an occurrence is still reserved");
        var updated = new LinkedHashMap<>(seasons); updated.put(staged.id(), staged);
        return copy(updated, null, staged.id(), true, now, now, occurrences);
    }

    public WorldBossCampaign daily(boolean enabled, long now) {
        if (enabled) checkWall(now);
        if (enabled && active() == null) throw new IllegalStateException("No active season");
        var updated = new LinkedHashMap<>(occurrences);
        if (!enabled) updated.replaceAll((key, event) -> event.status() == Status.PLANNED ? event.change(Status.SKIPPED, event.cues(), "STAFF_OR_CLOCK_PAUSE") : event);
        return copy(seasons, staged, activeSeason, enabled, enabled ? now : enabledAtMillis, Math.max(now, wallHighWaterMillis), updated);
    }

    public WorldBossCampaign plan(WorldBossSchedule.Occurrence slot, long now) {
        checkWall(now);
        if (!dailyEnabled || active() == null || enabledAtMillis > slot.preparationAt().toEpochMilli()) throw new IllegalStateException("Season was not enabled for full preparation");
        String key = key(activeSeason, slot);
        if (occurrences.containsKey(key)) return this;
        Season season = active();
        var event = new Occurrence(key, season.id(), season.characterId(), season.name(), season.definition(), season.awakening(),
                slot.combatAt().toEpochMilli(), slot.combatAt().atZone(WorldBossSchedule.ZONE).getOffset().getTotalSeconds(), UUID.randomUUID(), Status.PLANNED, 0, "");
        var updated = new LinkedHashMap<>(occurrences); updated.put(key, event);
        return copy(seasons, staged, activeSeason, dailyEnabled, enabledAtMillis, now, updated);
    }

    public WorldBossCampaign cue(String key, int cue, long now) {
        checkWall(now);
        if (cue != TEN_MINUTE && cue != FIVE_MINUTE && cue != ONE_MINUTE && cue != ENTRANCE) throw new IllegalArgumentException("Invalid cue");
        Occurrence event = require(key);
        if (event.terminal() || (event.cues() & cue) != 0) return this;
        return replace(event.change(event.status(), event.cues() | cue, event.reason()), now, seasons);
    }

    public WorldBossCampaign transition(String key, UUID eventId, Status next, String reason, long now) {
        checkWall(now);
        Occurrence event = require(key);
        if (!event.eventId().equals(eventId)) throw new IllegalArgumentException("Wrong event owner");
        if (event.status() == next) return this;
        if (event.terminal()) throw new IllegalStateException("Occurrence is already final");
        boolean valid = next == Status.SKIPPED && event.status() != Status.OPEN
                || next == Status.CLOSED || event.status() == Status.PLANNED && next == Status.PREPARING
                || event.status() == Status.PREPARING && next == Status.OPEN || event.status() == Status.OPEN && next == Status.VICTORY;
        if (!valid) throw new IllegalStateException("Invalid occurrence transition");
        var updated = new LinkedHashMap<>(seasons);
        if (next == Status.VICTORY) {
            Season season = seasons.get(event.season());
            if (event.awakening() != season.awakening()) throw new IllegalStateException("Stale awakening victory");
            updated.put(season.id(), season.won());
        }
        return replace(event.change(next, event.cues(), reason), now, updated);
    }

    public Occurrence require(String key) {
        Occurrence event = occurrences.get(key);
        if (event == null) throw new IllegalArgumentException("Unknown occurrence");
        return event;
    }

    private WorldBossCampaign replace(Occurrence event, long now, Map<String, Season> owners) {
        var updated = new LinkedHashMap<>(occurrences); updated.put(event.key(), event);
        return copy(owners, staged, activeSeason, dailyEnabled, enabledAtMillis, now, updated);
    }
    private void checkWall(long now) { if (now <= 0 || now < wallHighWaterMillis) throw new IllegalStateException("Campaign clock moved backwards"); }
    private static WorldBossCampaign copy(Map<String, Season> seasons, Season staged, String active, boolean enabled, long enabledAt, long wall, Map<String, Occurrence> events) {
        return new WorldBossCampaign(seasons, staged, active, enabled, enabledAt, wall, events);
    }
    private static boolean safeText(String value, int max) { return value != null && value.length() <= max && value.chars().noneMatch(Character::isISOControl) && value.indexOf('\u00a7') < 0; }
    private static String numeral(long value) {
        if (value > 3999) return Long.toString(value);
        int[] numbers = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] letters = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        var text = new StringBuilder();
        for (int i = 0; i < numbers.length; i++) while (value >= numbers[i]) { text.append(letters[i]); value -= numbers[i]; }
        return text.toString();
    }
}
