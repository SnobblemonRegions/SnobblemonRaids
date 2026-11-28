package com.cobbleraids.worldboss;

/** Horizontal protection extends through build height; the player enrollment bubble stays spherical. */
record WorldBossArenaRegion(String dimension, double x, double z, double radius) {
    WorldBossArenaRegion {
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !Double.isFinite(x) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || !Double.isFinite(radius) || radius <= 0 || radius > 1024) throw new IllegalArgumentException("Invalid arena protection region");
    }
    boolean intersects(String world, double minX, double minZ, double maxX, double maxZ, double padding) {
        if (!dimension.equals(world) || !Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || minX > maxX || minZ > maxZ || !Double.isFinite(padding) || padding < 0 || padding > 32) return false;
        double dx = Math.max(minX - x, Math.max(0, x - maxX)), dz = Math.max(minZ - z, Math.max(0, z - maxZ));
        return dx * dx + dz * dz <= (radius + padding) * (radius + padding);
    }
    boolean contains(String world, double minX, double minZ, double maxX, double maxZ) {
        if (!dimension.equals(world) || !Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || minX > maxX || minZ > maxZ) return false;
        double dx = Math.max(Math.abs(minX - x), Math.abs(maxX - x)), dz = Math.max(Math.abs(minZ - z), Math.abs(maxZ - z));
        return dx * dx + dz * dz <= radius * radius;
    }
    boolean containsSpawnArea(String world, int baseX, int baseZ, int length, int width) {
        return length > 0 && width > 0 && contains(world, baseX, baseZ, baseX + (double) length, baseZ + (double) width);
    }
}
