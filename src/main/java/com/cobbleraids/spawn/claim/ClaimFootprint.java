package com.cobbleraids.spawn.claim;

/**
 * The square of block columns a boss needs clear of land claims: its spawn column plus {@code buffer} blocks on every side, so a
 * scaled boss standing just over a claim border cannot reach into it. Also the chunks a claim lookup must read to see every claim
 * that could overlap the square. Minecraft-free so the chunk arithmetic is tested.
 */
public record ClaimFootprint(int minX, int minZ, int maxX, int maxZ) {

    /** Keeps a lookup to at most 9x9 chunks. */
    public static final int MAX_BUFFER = 64;

    public static ClaimFootprint around(int x, int z, int buffer) {
        if (buffer < 0 || buffer > MAX_BUFFER) throw new IllegalArgumentException("claim buffer must be 0.." + MAX_BUFFER + ", was " + buffer);
        return new ClaimFootprint(x - buffer, z - buffer, x + buffer, z + buffer);
    }

    public int minChunkX() { return minX >> 4; }
    public int maxChunkX() { return maxX >> 4; }
    public int minChunkZ() { return minZ >> 4; }
    public int maxChunkZ() { return maxZ >> 4; }
}
