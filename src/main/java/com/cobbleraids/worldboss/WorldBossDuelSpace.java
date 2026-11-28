package com.cobbleraids.worldboss;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server-thread-owned space for pending and running private duels. Nothing survives a restart. */
final class WorldBossDuelSpace {
    static final double HALF_WIDTH = 3, HEIGHT = 8;
    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Box {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                    || minX >= maxX || minY >= maxY || minZ >= maxZ) throw new IllegalArgumentException("Invalid duel space");
        }
        boolean overlaps(Box other) { return minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY && minZ < other.maxZ && maxZ > other.minZ; }
        boolean contains(Box body) { return minX <= body.minX && minY <= body.minY && minZ <= body.minZ && maxX >= body.maxX && maxY >= body.maxY && maxZ >= body.maxZ; }
    }
    record Lease(WorldBossAdmission.Token token, String dimension, Box box) {}
    private final Map<java.util.UUID, Lease> leases = new LinkedHashMap<>();

    static Box lane(double px, double py, double pz, double bx, double by, double bz) {
        double distance = Math.hypot(bx - px, bz - pz);
        if (!Double.isFinite(distance) || distance < WorldBossDuelSpot.NEAREST - 0.000001 || distance > 10.000001 || Math.abs(py - by) > 1)
            throw new IllegalArgumentException("Duel lane is outside the bounded placement rings");
        return new Box(Math.min(px, bx) - HALF_WIDTH, Math.min(py, by), Math.min(pz, bz) - HALF_WIDTH,
                Math.max(px, bx) + HALF_WIDTH, Math.max(py, by) + HEIGHT, Math.max(pz, bz) + HALF_WIDTH);
    }
    boolean available(String dimension, Box box) {
        return leases.values().stream().noneMatch(lease -> lease.dimension().equals(dimension) && lease.box().overlaps(box));
    }
    boolean reserve(WorldBossAdmission.Token token, String dimension, Box box) {
        if (token == null || token.eventId() == null || token.playerId() == null || token.encounterId() == null || token.generation() <= 0 || dimension == null || dimension.isBlank()) throw new IllegalArgumentException("Invalid duel space owner");
        Lease old = leases.get(token.encounterId());
        if (old != null) return old.equals(new Lease(token, dimension, box));
        if (leases.size() >= 50 || leases.values().stream().anyMatch(lease -> lease.token().playerId().equals(token.playerId())) || !available(dimension, box)) return false;
        leases.put(token.encounterId(), new Lease(token, dimension, box)); return true;
    }
    boolean owns(WorldBossAdmission.Token token) { var lease = token == null ? null : leases.get(token.encounterId()); return lease != null && lease.token().equals(token); }
    boolean release(WorldBossAdmission.Token token) { return owns(token) && leases.remove(token.encounterId()) != null; }
    Lease lease(WorldBossAdmission.Token token) { return owns(token) ? leases.get(token.encounterId()) : null; }
    int size() { return leases.size(); }
    void clear() { leases.clear(); }
}
