package com.cobbleraids.worldboss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Strict event contribution/ward snapshots. Derived qualification is rebuilt from the receipts. */
final class WorldBossContributionCodec {
    private WorldBossContributionCodec() {}

    static CompoundTag merit(WorldBossMerit merit) {
        var tag = new CompoundTag(); tag.putInt("version", 2);
        var snapshot = merit.snapshot();
        tag.putInt("victory", snapshot.victoryReward()); tag.putInt("timeout", snapshot.timeoutReward()); tag.putInt("cancellation", snapshot.cancellationReward());
        var duels = new ListTag();
        snapshot.duels().forEach((id, duel) -> { var row = new CompoundTag(); row.putUUID("encounter", id); row.putUUID("player", duel.player());
            row.putLong("sequence", duel.lastSequence()); row.putInt("turn", duel.lastTurn()); row.putInt("scored", duel.scoredTurns()); duels.add(row); });
        tag.put("duels", duels);
        var focuses = new ListTag(); snapshot.focuses().forEach(focus -> { var row = new CompoundTag(); row.putUUID("player", focus.player()); row.putInt("window", focus.window()); focuses.add(row); });
        tag.put("focuses", focuses);
        var relays = new ListTag(); snapshot.relays().forEach(relay -> { var row = new CompoundTag(); row.putUUID("player", relay.player()); row.putInt("window", relay.window()); relays.add(row); });
        tag.put("relays", relays); return tag;
    }

    static WorldBossMerit merit(CompoundTag tag) {
        int version = version(tag); Map<UUID, WorldBossMerit.Duel> duels = new LinkedHashMap<>(); Set<WorldBossMerit.Focus> focuses = new LinkedHashSet<>(); Set<WorldBossMerit.Relay> relays = new LinkedHashSet<>();
        ListTag rows = rows(tag, "duels");
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i); var duel = new WorldBossMerit.Duel(uuid(row, "player"), number(row, "sequence"), integer(row, "turn"), integer(row, "scored"));
            if (duels.putIfAbsent(uuid(row, "encounter"), duel) != null) throw new IllegalArgumentException("Duplicate Merit duel");
        }
        rows = rows(tag, "focuses");
        for (int i = 0; i < rows.size(); i++) { var row = rows.getCompound(i); if (!focuses.add(new WorldBossMerit.Focus(uuid(row, "player"), integer(row, "window")))) throw new IllegalArgumentException("Duplicate focus"); }
        if (version >= 2) {
            rows = rows(tag, "relays");
            for (int i = 0; i < rows.size(); i++) { var row = rows.getCompound(i); if (!relays.add(new WorldBossMerit.Relay(uuid(row, "player"), integer(row, "window")))) throw new IllegalArgumentException("Duplicate relay"); }
        } else if (tag.contains("relays")) throw new IllegalArgumentException("Legacy Merit contains relay data");
        return new WorldBossMerit(new WorldBossMerit.Snapshot(duels, focuses, relays, integer(tag, "victory"), integer(tag, "timeout"), integer(tag, "cancellation")));
    }

    static CompoundTag wards(WorldBossWards wards) {
        var tag = new CompoundTag(); var snapshot = wards.snapshot(); tag.putInt("version", 2); tag.putInt("crossed", snapshot.crossed()); tag.putBoolean("stopped", snapshot.stopped());
        if (snapshot.pylon() != null) { var pylon = new CompoundTag(); pylon.putString("dimension", snapshot.pylon().dimension()); pylon.putInt("x", snapshot.pylon().x()); pylon.putInt("y", snapshot.pylon().y()); pylon.putInt("z", snapshot.pylon().z()); tag.put("pylon", pylon); }
        tag.putBoolean("relay_enabled", snapshot.route() != null);
        if (snapshot.route() != null) { var route = new CompoundTag(); route.put("cache", point(snapshot.route().cache())); route.put("checkpoint", point(snapshot.route().checkpoint())); tag.put("route", route); }
        var history = new ListTag(); snapshot.completed().forEach(window -> history.add(window(window))); tag.put("completed", history);
        if (snapshot.active() != null) tag.put("active", window(snapshot.active())); return tag;
    }

    static WorldBossWards wards(CompoundTag tag) {
        int version = version(tag); WorldBossWards.Pylon pylon = null; WorldBossRelay.Route route = null;
        if (tag.contains("pylon")) { require(tag, "pylon", Tag.TAG_COMPOUND); var at = tag.getCompound("pylon"); pylon = new WorldBossWards.Pylon(text(at, "dimension"), integer(at, "x"), integer(at, "y"), integer(at, "z")); }
        var history = new ArrayList<WorldBossWards.Window>(); var rows = rows(tag, "completed");
        if (version >= 2) {
            require(tag, "relay_enabled", Tag.TAG_BYTE);
            if (tag.getBoolean("relay_enabled")) { require(tag, "route", Tag.TAG_COMPOUND); var at = tag.getCompound("route"); route = new WorldBossRelay.Route(point(at, "cache"), point(at, "checkpoint")); }
            else if (tag.contains("route")) throw new IllegalArgumentException("Disabled relay contains route");
        } else if (tag.contains("route") || tag.contains("relay_enabled")) throw new IllegalArgumentException("Legacy ward contains relay data");
        for (int i = 0; i < rows.size(); i++) history.add(window(rows.getCompound(i), version));
        WorldBossWards.Window active = null; if (tag.contains("active")) { require(tag, "active", Tag.TAG_COMPOUND); active = window(tag.getCompound("active"), version); }
        require(tag, "stopped", Tag.TAG_BYTE);
        return new WorldBossWards(new WorldBossWards.Snapshot(pylon, route, integer(tag, "crossed"), tag.getBoolean("stopped"), history, active));
    }

    private static CompoundTag window(WorldBossWards.Window window) {
        var tag = new CompoundTag(); tag.putInt("serial", window.serial()); tag.putInt("quorum", window.quorum()); tag.putLong("end_remaining", window.endRemainingMillis()); tag.putString("status", window.status().name());
        var turns = new ListTag(); window.turns().forEach(turn -> { var row = new CompoundTag(); row.putUUID("encounter", turn.encounter()); row.putUUID("player", turn.player()); row.putInt("turn", turn.turn()); turns.add(row); }); tag.put("turns", turns);
        var focuses = new ListTag(); window.focuses().forEach(player -> { var row = new CompoundTag(); row.putUUID("player", player); focuses.add(row); }); tag.put("focuses", focuses);
        var relays = new ListTag(); window.relays().forEach(player -> { var row = new CompoundTag(); row.putUUID("player", player); relays.add(row); }); tag.put("relays", relays); return tag;
    }
    private static WorldBossWards.Window window(CompoundTag tag, int version) {
        Set<WorldBossWards.CombatTurn> turns = new LinkedHashSet<>(); Set<UUID> focuses = new LinkedHashSet<>(), relays = new LinkedHashSet<>(); var rows = rows(tag, "turns");
        for (int i = 0; i < rows.size(); i++) { var row = rows.getCompound(i); if (!turns.add(new WorldBossWards.CombatTurn(uuid(row, "encounter"), uuid(row, "player"), integer(row, "turn")))) throw new IllegalArgumentException("Duplicate ward turn"); }
        rows = rows(tag, "focuses"); for (int i = 0; i < rows.size(); i++) if (!focuses.add(uuid(rows.getCompound(i), "player"))) throw new IllegalArgumentException("Duplicate ward focus");
        if (version >= 2) { rows = rows(tag, "relays"); for (int i = 0; i < rows.size(); i++) if (!relays.add(uuid(rows.getCompound(i), "player"))) throw new IllegalArgumentException("Duplicate ward relay"); }
        else if (tag.contains("relays")) throw new IllegalArgumentException("Legacy ward window contains relay data");
        return new WorldBossWards.Window(integer(tag, "serial"), integer(tag, "quorum"), number(tag, "end_remaining"), WorldBossWards.Status.valueOf(text(tag, "status")), turns, focuses, relays);
    }
    private static CompoundTag point(WorldBossWards.Pylon point) { var tag = new CompoundTag(); tag.putString("dimension", point.dimension()); tag.putInt("x", point.x()); tag.putInt("y", point.y()); tag.putInt("z", point.z()); return tag; }
    private static WorldBossWards.Pylon point(CompoundTag tag, String key) { require(tag, key, Tag.TAG_COMPOUND); var at = tag.getCompound(key); return new WorldBossWards.Pylon(text(at, "dimension"), integer(at, "x"), integer(at, "y"), integer(at, "z")); }
    private static int version(CompoundTag tag) { int version = integer(tag, "version"); if (version != 1 && version != 2) throw new IllegalArgumentException("Unknown contribution version"); return version; }
    private static void require(CompoundTag tag, String key, int type) { if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing contribution " + key); }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static String text(CompoundTag tag, String key) { require(tag, key, Tag.TAG_STRING); return tag.getString(key); }
    private static UUID uuid(CompoundTag tag, String key) { if (!tag.hasUUID(key)) throw new IllegalArgumentException("Invalid contribution UUID"); return tag.getUUID(key); }
    private static ListTag rows(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LIST); var rows = (ListTag) tag.get(key); if (!rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid contribution rows"); return rows; }
}
