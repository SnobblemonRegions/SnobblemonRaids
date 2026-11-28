package com.cobbleraids.worldboss;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** One account-owned original return with separately acknowledged outbound/return transactions. */
final class WorldBossTravel {
    private static final java.util.regex.Pattern DIMENSION = java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    enum Phase { OUTBOUND, ARRIVED, RETURNING }
    record Position(String dimension, double x, double y, double z, float yaw, float pitch) {
        Position { if (dimension == null || !DIMENSION.matcher(dimension).matches() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Invalid saved return location"); }
        boolean matches(String dimension, double x, double y, double z) {
            double dx = x - this.x, dy = y - this.y, dz = z - this.z; return this.dimension.equals(dimension) && dx * dx + dy * dy + dz * dz <= 0.0001;
        }
    }
    record Ticket(Position origin, UUID operation, Position destination, Phase phase) {
        Ticket { java.util.Objects.requireNonNull(origin); java.util.Objects.requireNonNull(operation); java.util.Objects.requireNonNull(destination); java.util.Objects.requireNonNull(phase); }
    }
    private final Map<UUID, Ticket> tickets;
    WorldBossTravel() { this(Map.of()); }
    WorldBossTravel(Map<UUID, Ticket> tickets) { this.tickets = Map.copyOf(tickets); }
    Map<UUID, Ticket> tickets() { return tickets; }
    Ticket ticket(UUID player) { return tickets.get(player); }
    WorldBossTravel outbound(UUID player, Position current, Position destination, UUID operation) {
        var previous = tickets.get(player); var updated = new LinkedHashMap<>(tickets);
        updated.put(player, new Ticket(previous == null ? current : previous.origin(), operation, destination, Phase.OUTBOUND)); return new WorldBossTravel(updated);
    }
    WorldBossTravel arrived(UUID player, UUID operation) {
        var previous = tickets.get(player); if (previous == null || !previous.operation().equals(operation) || previous.phase() != Phase.OUTBOUND) return this;
        var updated = new LinkedHashMap<>(tickets); updated.put(player, new Ticket(previous.origin(), operation, previous.destination(), Phase.ARRIVED)); return new WorldBossTravel(updated);
    }
    WorldBossTravel returning(UUID player, Position current, Position destination, UUID operation) {
        var previous = tickets.get(player); var updated = new LinkedHashMap<>(tickets);
        updated.put(player, new Ticket(previous == null ? current : previous.origin(), operation, destination, Phase.RETURNING)); return new WorldBossTravel(updated);
    }
    WorldBossTravel returned(UUID player, UUID operation) {
        var previous = tickets.get(player); if (previous == null || previous.phase() != Phase.RETURNING || !previous.operation().equals(operation)) return this;
        var updated = new LinkedHashMap<>(tickets); updated.remove(player); return new WorldBossTravel(updated);
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("version", 1); var rows = new ListTag();
        tickets.forEach((player, ticket) -> { var row = new CompoundTag(); row.putUUID("player", player); row.putUUID("operation", ticket.operation()); row.putString("phase", ticket.phase().name()); row.put("origin", position(ticket.origin())); row.put("destination", position(ticket.destination())); rows.add(row); }); tag.put("tickets", rows); return tag;
    }
    static WorldBossTravel load(CompoundTag tag) {
        require(tag, "version", Tag.TAG_INT); if (tag.getInt("version") != 1) throw new IllegalArgumentException("Unknown return store version");
        require(tag, "tickets", Tag.TAG_LIST); var rows = (ListTag) tag.get("tickets"); if (!rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid return rows");
        var tickets = new LinkedHashMap<UUID, Ticket>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i); if (!row.hasUUID("player") || !row.hasUUID("operation")) throw new IllegalArgumentException("Invalid return owner"); require(row, "phase", Tag.TAG_STRING);
            if (tickets.putIfAbsent(row.getUUID("player"), new Ticket(position(row, "origin"), row.getUUID("operation"), position(row, "destination"), Phase.valueOf(row.getString("phase")))) != null) throw new IllegalArgumentException("Duplicate saved return");
        }
        return new WorldBossTravel(tickets);
    }
    private static CompoundTag position(Position position) { var tag = new CompoundTag(); tag.putString("dimension", position.dimension()); tag.putDouble("x", position.x()); tag.putDouble("y", position.y()); tag.putDouble("z", position.z()); tag.putFloat("yaw", position.yaw()); tag.putFloat("pitch", position.pitch()); return tag; }
    private static Position position(CompoundTag root, String key) {
        require(root, key, Tag.TAG_COMPOUND); var tag = root.getCompound(key); require(tag, "dimension", Tag.TAG_STRING);
        for (var coordinate : new String[]{"x", "y", "z"}) require(tag, coordinate, Tag.TAG_DOUBLE); require(tag, "yaw", Tag.TAG_FLOAT); require(tag, "pitch", Tag.TAG_FLOAT);
        return new Position(tag.getString("dimension"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
    }
    private static void require(CompoundTag tag, String key, int type) { if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing return " + key); }
}
